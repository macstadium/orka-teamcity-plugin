# How Orka TeamCity Plugin Works - Internal Mechanics

## VM Startup Triggers

### When does TeamCity start a new VM?

TeamCity **independently** decides to start a VM through the Cloud Plugin API. The plugin **does not control** this decision - it only reacts to requests from TeamCity.

### Main VM Startup Triggers

#### 1. **Build in Queue (Main Trigger)**

```
Build added to queue
    ↓
TeamCity searches for available agent in required pool
    ↓
No free agents?
    ↓
TeamCity calls canStartNewInstance()
    ↓
If true → calls startNewInstance()
    ↓
VM is created
```

**Conditions:**

- There is a build in queue
- Build requires an agent from a specific pool
- No free agents in that pool
- Instance limit not reached (`instanceLimit`)

#### 2. **Profile Testing After Creation**

When creating/saving a Cloud Profile, TeamCity may:

- Check connectivity to Orka endpoint
- **Attempt to create a test VM** to validate configuration
- Verify API availability

**This explains why a VM may start immediately after profile creation without any load!**

#### 3. **Agent Reconnects**

If an agent with an Orka VM already exists (e.g., VM created manually in Orka) and connects to TeamCity, the plugin can register it through the `findInstanceByAgent()` method.

#### 4. **Periodic TeamCity Checks**

TeamCity may periodically call plugin methods to:

- Update instance status
- Check cloud provider availability
- Collect metrics

### VM Creation Code Path

```java
// 1. TeamCity calls
OrkaCloudClient.canStartNewInstance(image)
    ↓
// 2. Check limit
OrkaCloudImage.canStartNewInstance()
    return instanceLimit > currentInstances.size()
    ↓
// 3. If true, TeamCity calls
OrkaCloudClient.startNewInstance(image, userData)
    ↓
// 4. Create instance object
OrkaCloudInstance instance = cloudImage.startNewInstance(instanceId)
    ↓
// 5. Asynchronously start setup
scheduledExecutorService.submit(() -> setUpVM(...))
    ↓
// 6. Deploy in Orka
orkaClient.deployVM(vmName, namespace, metadata)
    ↓
// 7. Check and wait for VM
orkaClient.getVM(instanceId, namespace)
sshUtil.waitForSSH(host, port)
    ↓
// 8. Configure agent
updateBuildAgentPropertiesOnce(...)
remoteAgent.startAgent(...)
    ↓
// 9. Instance status → RUNNING
```

## Why Does VM Start Without Load?

### Possible Reasons

1. **TeamCity validates profile after saving**
   - This is normal TeamCity behavior
   - VM is created to validate configuration
   - After validation it may be deleted or left idle

2. **"Test connection" settings enabled**
   - When saving profile, TeamCity may do a test run
   - Verifies entire path: Orka API → VM deploy → SSH → Agent start

3. **Agent Pool requires minimum agents**
   - In some TeamCity versions you can configure minimum number of agents in pool
   - TeamCity will maintain this minimum count

4. **Existing VM in Orka**
   - If a VM with the same name already exists in Orka
   - Plugin may "pick it up" through `findInstanceByAgent()`

## How to Control VM Startup?

### 1. **Instance Limit (Main Control)**

```
Maximum instances count = 0  → VMs won't be created at all
Maximum instances count = 1  → Maximum 1 VM at a time
Maximum instances count = 10 → Up to 10 VMs in parallel
```

Check in code:

```java
public synchronized boolean canStartNewInstance() {
    return this.instanceLimit == OrkaConstants.UNLIMITED_INSTANCES 
        || this.instanceLimit > this.instances.size();
}
```

### 2. **Agent Pool Assignment**

- VMs are created only for builds requiring agents from **specific pool**
- If builds don't require this pool → VMs are not created

### 3. **Pause/Disable Profile**

- In TeamCity you can pause a Cloud Profile
- Profile will not create new VMs
- Existing VMs will continue running

### 4. **Build Configuration Requirements**

- Configure agent requirements in build configuration
- VMs will be created only if requirements match

## Automatic VM Cleanup

### When is VM Deleted?

1. **Build completed + idle timeout expired**
   - After build completes, agent becomes idle
   - TeamCity waits for configured idle timeout
   - Then calls `terminateInstance()`

2. **VM failed during setup**
   - Deployment error in Orka
   - SSH unavailable
   - Agent didn't start
   - → VM automatically deleted

3. **Periodic cleanup of failed instances**

   ```java
   RemoveFailedInstancesTask - runs every 5 minutes
   ```

   Deletes VMs in ERROR status or with problems

4. **Manual termination**
   - Through TeamCity UI: Agents → Cloud → Stop
   - Calls `terminateInstance()`

## Diagnostics

### Logs for Debugging Triggers

1. **TeamCity Server logs:**

   ```
   <TeamCity_data_directory>/logs/teamcity-clouds.log
   ```

   Shows all Cloud Plugin API calls

2. **Orka Plugin logs:**
   Look for in `teamcity-clouds.log`:

   ```
   [Orka Cloud] startNewInstance with temp id: <uuid>
   [Orka Cloud] Setting up VM for image '<vm_name>'
   [Orka Cloud] VM deployed: <instanceId>
   ```

3. **Check canStartNewInstance:**

   ```
   [Orka Cloud] Quota exceeded. Number of instances: X and limit: Y
   ```

   Shows when limit is reached

### Useful Commands for Verification

```bash
# Check existing VMs in Orka
curl -X GET "https://<orka-endpoint>/resources/vm/list" \
  -H "Authorization: Bearer <token>"

# Check number of agents in pool via TeamCity API
curl "http://<teamcity>/app/rest/agentPools/id:<poolId>/agents" \
  -u "username:password"
```

## Best Practices

### Avoid Unexpected VMs

1. **Start with small limit**

   ```
   Maximum instances count = 1
   ```

   Increase after testing

2. **Monitor Orka dashboard**
   - Check number of active VMs
   - Set up alerts for limit violations

3. **Use separate Agent Pool**
   - Create dedicated pool only for Orka agents
   - Don't use Default pool

4. **Configure build requirements**
   - Explicitly specify agent requirements
   - VMs will be created only for matching builds

5. **Test on staging environment**
   - First test profile on test Orka environment
   - Verify VM creation/deletion behavior

6. **Regularly check logs**

   ```bash
   tail -f <TeamCity_data_directory>/logs/teamcity-clouds.log | grep "Orka"
   ```

## FAQ

**Q: Why did VM create immediately after profile creation?**
A: TeamCity tests the profile after saving. This is normal behavior.

**Q: How to prevent automatic VM creation?**
A: Set `Maximum instances count = 0` or pause the profile.

**Q: VM is created but builds don't run on it**
A: Check:

- Agent Pool assignment
- Build requirements
- Agent authorization in TeamCity

**Q: VM is not deleted after build**
A: Check idle timeout settings in Cloud Profile.

**Q: How to create VMs in advance (pre-warming)?**
A: TeamCity doesn't support explicit pre-warming in Cloud Plugin API. VMs are created only on-demand. But you can:

- Create dummy build that runs periodically
- Set higher instance limit so VMs remain idle

## Summary

**Key Points:**

1. ✅ **TeamCity** controls when to create VMs, not the plugin
2. ✅ Main trigger: **build in queue + no free agents**
3. ✅ VM may be created when **testing profile**
4. ✅ Control through **Instance Limit** and **Agent Pool**
5. ✅ Automatic cleanup of failed instances every 5 minutes
