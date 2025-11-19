# Orka by MacStadium TeamCity Plugin Usage

This guide explains how to use the Orka TeamCity plugin.

The plugin allows you to configure TeamCity, so it can spin up on demand ephemeral agents that run in an Orka environment.

How to set up the plugin, see [here](setup.md).

## Configure the MacStadium Orka plugin

The plugin allows you to configure a cloud profile per project.

In your TeamCity server:

1. Go to `Projects` and click the project you want to configure with a cloud profile.
2. Click `Edit Project Settings`.
3. Go to `Cloud Profiles` and click `Create new profile`.
4. Specify a name and select `Orka Cloud` for the `Cloud type` drop-down menu.
5. Specify the required parameters:

### Required Parameters

- **Orka endpoint** - The Orka endpoint URL. For more information, see your Orka [IP Plan][ip-plan]
- **Orka token** - Authentication token used to connect to the Orka environment. Created using the [CLI][cli-create-user] or the [REST API][rest-create-user]
- **VM template** - The name of the VM configuration you created [here](setup.md#set-up-an-orka-vm-base-image)
- **VM user** - Username used to SSH to the VM
- **VM SSH password** - User password used to SSH to the VM
- **Maximum instances count** - Maximum number of VM instances that can be created simultaneously
- **Agent Pool** - The TeamCity agent pool which will be used for new agents
- **Agent directory** - The installation directory of the build agent on the VM (e.g., `/Users/admin/BuildAgent/`)

### Optional Advanced Parameters

- **Server URL** - TeamCity server URL to be configured in `buildAgent.properties` on VMs. If not specified, VMs will use the serverUrl already configured in their base image.
- **Namespace** - Orka namespace for VM deployment. Default: `orka-default`
- **VM Metadata** - Additional metadata to pass during VM deployment (format: `key1=value1,key2=value2`)
- **Node Mappings** - Map private node IPs to public IPs for connectivity (format: `privateIP;publicIP`, one per line). Useful when TeamCity server needs to connect to VMs via different IPs than reported by Orka.

6. Click `Create`.

### Important Notes

- **Agent Push is not supported** - The plugin manages agent lifecycle automatically
- **Bidirectional connectivity required** - TeamCity server and Orka VMs must be able to reach each other
- **SSH must be enabled** - VMs must allow password-based SSH authentication
- **Agent directory is critical** - Must match the actual installation path on the VM image

## Using the MacStadium Orka plugin

Once the cloud profile is successfully configured, TeamCity will automatically spin up and destroy agents in the respective Orka environment.

### How it works

1. **On-demand provisioning** - When a build is queued and no agents are available, TeamCity triggers the plugin to create a new VM
2. **VM deployment** - The plugin calls Orka API to deploy a VM based on the configured template
3. **Agent setup** - The plugin connects via SSH to configure and start the TeamCity agent
4. **Build execution** - The agent connects to TeamCity server and executes the build
5. **Cleanup** - After the build completes and idle timeout expires, the VM is automatically terminated

> **Important:** TeamCity controls when VMs are created, not the plugin. VMs may be created:
>
> - When a build is queued and no agents are available (main trigger)
> - When testing the cloud profile after creation (validation)
> - When TeamCity performs connectivity checks
>
> For detailed explanation of VM triggers, see [How It Works - Internal Mechanics](how-it-works.md)

### Monitoring agents

To monitor Orka agents:

1. Go to `Agents` → `Cloud` in your TeamCity server
2. You'll see all Orka agents with their status:
   - **Starting** - VM is being deployed and configured
   - **Running** - Agent is connected and ready for builds
   - **Stopping** - VM is being terminated
   - **Stopped** - VM has been terminated
   - **Error** - Something went wrong during VM setup

### Troubleshooting

#### Agent fails to start

If agents fail to start, check:

- **Orka connectivity** - Verify TeamCity server can reach the Orka endpoint
- **Credentials** - Ensure the Orka token is valid and has proper permissions
- **VM template** - Confirm the VM template name is correct and exists in Orka
- **SSH access** - Verify SSH is enabled on the base image and credentials are correct
- **Agent directory** - Ensure the path matches the actual installation directory

Check TeamCity server logs at `<TeamCity_data_directory>/logs/teamcity-clouds.log` for detailed error messages.

#### Agent connects but doesn't run builds

If agents connect but don't execute builds:

- **Agent pool** - Verify the cloud profile uses the correct agent pool
- **Build configuration compatibility** - Check that build configurations target the correct agent pool
- **Agent requirements** - Ensure build requirements match the agent capabilities

#### Network connectivity issues

If you have connectivity issues:

- **Node mappings** - Configure node mappings if TeamCity needs to connect via different IPs than Orka reports
- **Firewall rules** - Ensure firewall allows traffic between TeamCity and Orka VMs
- **Bidirectional access** - Both TeamCity → VM and VM → TeamCity connections must work

#### Performance issues

If VMs take too long to start:

- **Base image optimization** - Ensure the TeamCity agent in the base image is up-to-date
- **Agent auto-start disabled** - The agent should NOT auto-start on boot (plugin will start it)
- **SSH timeout** - Plugin waits up to 2 minutes for SSH to become available

### Best practices

- **Pre-warm agents** - Keep 1-2 idle agents running during peak hours by adjusting instance limits
- **Base image maintenance** - Regularly update the base image with latest TeamCity agent and OS updates
- **Monitor costs** - Set appropriate instance limits to control Orka resource usage
- **Use namespaces** - Organize VMs using Orka namespaces for better management
- **Agent naming** - Agents are automatically named as `orka-mac-<instanceId>` for easy identification

### Advanced: Failed instance cleanup

The plugin automatically attempts to clean up failed VMs. Failed instances are checked every 5 minutes and removed if:

- VM deployment failed
- SSH connection failed
- Agent failed to start
- VM is in error state

This ensures your Orka environment doesn't accumulate orphaned VMs.

[ip-plan]: https://orkadocs.macstadium.com/docs/orka-glossary#section-ip-plan
[cli-create-user]: https://orkadocs.macstadium.com/docs/quick-start#section--setting-up-a-user-
[rest-create-user]: https://documenter.getpostman.com/view/6574930/S1ETRGzt?version=latest#55dcdc47-c542-4e85-88f4-e5b2c1734d50
