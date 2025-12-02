package com.macstadium.orka;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.macstadium.orka.client.AwsEksTokenProvider;
import com.macstadium.orka.client.CapacityInfo;
import com.macstadium.orka.client.DeletionResponse;
import com.macstadium.orka.client.DeploymentResponse;
import com.macstadium.orka.client.OrkaClient;
import com.macstadium.orka.client.OrkaVM;
import com.macstadium.orka.client.VMsResponse;
import com.macstadium.orka.client.StaticTokenProvider;
import com.macstadium.orka.client.TokenProvider;
import com.macstadium.orka.client.VMResponse;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jetbrains.buildServer.Used;
import jetbrains.buildServer.clouds.CloudClientEx;
import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudException;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudImageParameters;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.CanStartNewInstanceResult;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.QuotaException;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OrkaCloudClient extends BuildServerAdapter implements CloudClientEx {
  private static final Logger LOG = Logger.getInstance(Loggers.CLOUD_CATEGORY_ROOT + OrkaConstants.TYPE);

  // Capacity check cache settings
  private static final long CAPACITY_CACHE_TTL_MS = 30_000; // 30 seconds
  private static final long CAPACITY_FAILURE_BACKOFF_MS = 30_000; // 30 seconds backoff after failure

  // Pattern to extract profile name from description: "profile 'NAME'{id=ID}"
  private static final Pattern PROFILE_NAME_PATTERN = Pattern.compile("profile\\s+'([^']+)'");

  @NotNull
  private final List<OrkaCloudImage> images = new ArrayList<OrkaCloudImage>();

  private final String agentDirectory;
  private final String serverUrl;
  private final String profileId;
  private final String profileName;
  private OrkaClient orkaClient;
  private final ScheduledExecutorService scheduledExecutorService;
  private ScheduledFuture<?> removedFailedInstancesScheduledTask;
  private CloudErrorInfo errorInfo;
  private final RemoteAgent remoteAgent;
  private final SSHUtil sshUtil;
  private Map<String, String> nodeMappings;

  // Capacity check cache - single volatile object for atomic read/write
  private volatile CapacityCacheEntry capacityCache = CapacityCacheEntry.empty();

  // Lock for serializing capacity check + deployment to prevent race conditions
  private final Object deploymentLock = new Object();

  public OrkaCloudClient(@NotNull final CloudClientParameters params, ExecutorServices executorServices,
      @NotNull final String profileId) {
    this.profileId = profileId;
    // Extract profile name from description (format: "profile 'NAME'{id=ID}")
    String description = params.getProfileDescription();
    this.profileName = extractProfileName(description, profileId);
    this.initializeOrkaClient(params);
    this.agentDirectory = params.getParameter(OrkaConstants.AGENT_DIRECTORY);
    // Read serverUrl from Cloud Profile parameters (user-configured)
    this.serverUrl = params.getParameter(OrkaConstants.SERVER_URL);

    if (this.agentDirectory == null || this.agentDirectory.trim().isEmpty()) {
      LOG.debug(String.format("[%s] Agent Directory not configured - using default from VM image", profileId));
    }
    if (this.serverUrl == null || this.serverUrl.trim().isEmpty()) {
      LOG.debug(String.format("[%s] Server URL not configured - agent will use serverUrl from VM image", profileId));
    }

    LOG.info(String.format("[%s] Initializing OrkaCloudClient", profileId));
    this.images.add(this.createImage(params));
    this.scheduledExecutorService = executorServices.getNormalExecutorService();
    this.remoteAgent = new RemoteAgent();
    this.sshUtil = new SSHUtil();
    this.nodeMappings = this.getNodeMappings(params.getParameter(OrkaConstants.NODE_MAPPINGS));

    this.initializeBackgroundTasks();

    // Recover existing VMs from Orka (async to not block startup)
    this.scheduledExecutorService.submit(this::recoverExistingInstances);
  }

  @Used("Tests")
  public OrkaCloudClient(CloudClientParameters params, OrkaClient client,
      ScheduledExecutorService scheduledExecutorService, RemoteAgent remoteAgent, SSHUtil sshUtil) {
    this(params, client, scheduledExecutorService, remoteAgent, sshUtil, "test-profile", "Test Profile");
  }

  @Used("Tests")
  public OrkaCloudClient(CloudClientParameters params, OrkaClient client,
      ScheduledExecutorService scheduledExecutorService, RemoteAgent remoteAgent, SSHUtil sshUtil,
      String profileId, String profileName) {
    this.profileId = profileId;
    this.profileName = profileName;
    this.agentDirectory = params.getParameter(OrkaConstants.AGENT_DIRECTORY);
    // For test constructor, serverUrl will be null (buildAgent.properties update
    // will be skipped)
    this.serverUrl = null;
    this.initializeOrkaClient(params);
    this.images.add(this.createImage(params));
    this.scheduledExecutorService = scheduledExecutorService;
    this.orkaClient = client;
    this.remoteAgent = remoteAgent;
    this.sshUtil = sshUtil;
    this.nodeMappings = this.getNodeMappings(params.getParameter(OrkaConstants.NODE_MAPPINGS));
  }

  private Map<String, String> getNodeMappings(String mappingsData) {
    if (StringUtil.isNotEmpty(mappingsData)) {
      String[] mappings = mappingsData.split("\\r?\\n|\\r");
      return Arrays.stream(mappings).map(m -> m.split(";"))
          .collect(Collectors.toMap(pair -> pair[0], pair -> pair[1]));
    }
    return new HashMap<String, String>();
  }

  private void initializeOrkaClient(CloudClientParameters params) {
    String endpoint = params.getParameter(OrkaConstants.ORKA_ENDPOINT);
    String useAwsIamParam = params.getParameter(OrkaConstants.USE_AWS_IAM);
    boolean useAwsIam = "true".equalsIgnoreCase(useAwsIamParam);

    try {
      TokenProvider tokenProvider;

      if (useAwsIam) {
        String clusterName = params.getParameter(OrkaConstants.AWS_EKS_CLUSTER_NAME);
        String region = params.getParameter(OrkaConstants.AWS_REGION);

        if (StringUtil.isEmpty(clusterName) || StringUtil.isEmpty(region)) {
          String errorMsg = String.format(
              "[%s] AWS IAM auth requires cluster name and region. Got: cluster='%s', region='%s'",
              this.profileId, clusterName, region);
          LOG.error(errorMsg);
          this.errorInfo = new CloudErrorInfo("Invalid AWS IAM configuration", errorMsg);
          return;
        }

        tokenProvider = new AwsEksTokenProvider(clusterName, region);
        LOG.info(String.format("[%s] OrkaClient initialized with AWS IAM auth (cluster: %s, region: %s)",
            this.profileId, clusterName, region));
      } else {
        String token = params.getParameter(OrkaConstants.TOKEN);
        tokenProvider = new StaticTokenProvider(token);
        LOG.info(String.format("[%s] OrkaClient initialized with static token", this.profileId));
      }

      this.orkaClient = new OrkaClient(endpoint, tokenProvider);
    } catch (Exception e) {
      LOG.error(String.format("[%s] Failed to initialize Orka client", this.profileId), e);
      this.errorInfo = new CloudErrorInfo("Cannot initialize Orka client", e.toString(), e);
    }
  }

  private void initializeBackgroundTasks() {
    RemoveFailedInstancesTask removeFailedInstancesTask = new RemoveFailedInstancesTask(this);
    int initialDelay = 60 * 1000;
    int delay = 5 * initialDelay;
    this.removedFailedInstancesScheduledTask = this.scheduledExecutorService
        .scheduleWithFixedDelay(removeFailedInstancesTask, initialDelay, delay, TimeUnit.MILLISECONDS);
  }

  private OrkaCloudImage createImage(CloudClientParameters params) {
    String namespace = params.getParameter(OrkaConstants.NAMESPACE);
    String vm = params.getParameter(OrkaConstants.VM_NAME);
    String vmUser = params.getParameter(OrkaConstants.VM_USER);
    String vmPassword = params.getParameter(OrkaConstants.VM_PASSWORD);
    String agentPoolId = params.getParameter(CloudImageParameters.AGENT_POOL_ID_FIELD);
    String instanceLimit = params.getParameter(OrkaConstants.INSTANCE_LIMIT);
    String vmMetadata = params.getParameter(OrkaConstants.VM_METADATA);
    int limit = StringUtil.isEmpty(instanceLimit) ? OrkaConstants.UNLIMITED_INSTANCES
        : Integer.parseInt(instanceLimit);

    LOG.debug(String.format("[%s] createImage: vm='%s', namespace='%s', poolId='%s', limit=%s",
        this.profileId, vm, namespace, agentPoolId, instanceLimit));

    // Validate required parameters
    if (StringUtil.isEmpty(vm)) {
      throw new IllegalArgumentException("VM Config must be specified in Cloud Profile settings");
    }
    if (StringUtil.isEmpty(namespace)) {
      namespace = "orka-default";
    }
    if (StringUtil.isEmpty(vmUser)) {
      throw new IllegalArgumentException("VM User must be specified");
    }
    if (StringUtil.isEmpty(vmPassword)) {
      throw new IllegalArgumentException("VM Password must be specified");
    }

    return new OrkaCloudImage(this.profileId, this.profileName, vm, namespace, vmUser, vmPassword, agentPoolId, limit,
        vmMetadata);
  }

  public boolean isInitialized() {
    return true;
  }

  @Nullable
  public OrkaCloudImage findImageById(@NotNull final String imageId) throws CloudException {
    for (final OrkaCloudImage image : this.images) {
      if (image.getId().equals(imageId)) {
        return image;
      }
    }
    return null;
  }

  @Nullable
  public OrkaCloudInstance findInstanceByAgent(@NotNull final AgentDescription agentDescription) {
    final OrkaCloudImage image = this.findImage(agentDescription);
    if (image == null) {
      return null;
    }
    final String instanceId = this.findInstanceId(agentDescription);
    if (instanceId == null) {
      return null;
    }

    OrkaCloudInstance existingInstance = image.findInstanceById(instanceId);
    if (existingInstance != null) {
      return existingInstance;
    }
    return this.createInstanceFromExistingAgent(image, instanceId);
  }

  @Nullable
  private OrkaCloudInstance createInstanceFromExistingAgent(OrkaCloudImage image, String instanceId) {
    try {
      VMResponse vmResponse = this.getVM(instanceId, image.getNamespace());
      if (vmResponse != null && vmResponse.isSuccessful()) {
        int sshPort = vmResponse.getSSH() > 0 ? vmResponse.getSSH() : 22;
        LOG.info(String.format("[%s] Recovered existing VM: %s (IP: %s, SSH: %d)",
            this.profileId, instanceId, vmResponse.getIP(), sshPort));
        OrkaCloudInstance cloudInstance = image.startNewInstance(instanceId);
        cloudInstance.setStatus(InstanceStatus.RUNNING);
        cloudInstance.setHost(this.getRealHost(vmResponse.getIP()));
        cloudInstance.setPort(sshPort);
        return cloudInstance;
      }
    } catch (IOException | NumberFormatException e) {
      LOG.debug(String.format("[%s] Failed to recover VM %s: %s",
          this.profileId, instanceId, e.getMessage()));
    }
    return null;
  }

  @NotNull
  public Collection<? extends CloudImage> getImages() throws CloudException {
    return Collections.unmodifiableList(images);
  }

  @Nullable
  public CloudErrorInfo getErrorInfo() {
    return this.errorInfo;
  }

  public boolean canStartNewInstance(@NotNull final CloudImage image) {
    return checkCanStartInstance((OrkaCloudImage) image).isPositive();
  }

  @Override
  @NotNull
  public CanStartNewInstanceResult canStartNewInstanceWithDetails(@NotNull CloudImage image) {
    return checkCanStartInstance((OrkaCloudImage) image);
  }

  /**
   * Core capacity check logic - used by both canStartNewInstance methods.
   */
  private CanStartNewInstanceResult checkCanStartInstance(OrkaCloudImage orkaImage) {
    // 1. Check TeamCity instance limit
    if (!orkaImage.canStartNewInstance()) {
      LOG.debug(String.format("[%s] Instance limit reached", this.profileId));
      return CanStartNewInstanceResult.no("Instance limit reached");
    }

    // 2. Get current cache state (atomic read)
    CapacityCacheEntry cache = this.capacityCache;

    // 3. Check if we're in backoff period
    if (cache.isInBackoff(CAPACITY_FAILURE_BACKOFF_MS)) {
      long remainingSec = cache.getRemainingBackoffSeconds(CAPACITY_FAILURE_BACKOFF_MS);
      String reason = cache.getFailureReason() != null
          ? String.format("%s (retry in %ds)", cache.getFailureReason(), remainingSec)
          : String.format("No capacity (retry in %ds)", remainingSec);
      return CanStartNewInstanceResult.no(reason);
    }

    // 4. Check cached capacity (if valid)
    if (cache.isCacheValid(CAPACITY_CACHE_TTL_MS)) {
      if (cache.hasCapacity()) {
        return CanStartNewInstanceResult.yes();
      } else {
        CapacityInfo info = cache.getCapacityInfo();
        return CanStartNewInstanceResult.no(info != null ? info.getMessage() : "No capacity");
      }
    }

    // 5. No valid cache - return yes, setUpVM will do actual check
    return CanStartNewInstanceResult.yes();
  }

  /**
   * Updates capacity cache after check. Thread-safe via volatile.
   */
  private void updateCapacityCache(CapacityInfo capacityInfo) {
    if (capacityInfo.hasCapacity()) {
      this.capacityCache = CapacityCacheEntry.success(capacityInfo);
    } else {
      this.capacityCache = CapacityCacheEntry.failure(capacityInfo);
    }
  }

  /**
   * Invalidates cache after resource changes (deploy/terminate).
   */
  private void invalidateCapacityCache() {
    this.capacityCache = CapacityCacheEntry.empty();
  }

  /**
   * Clears backoff and cache when VM terminated (resources freed).
   */
  private void clearCapacityBackoff() {
    this.capacityCache = this.capacityCache.withClearedBackoff();
  }

  @Nullable
  public String generateAgentName(@NotNull final AgentDescription agent) {
    String instanceId = agent.getConfigurationParameters().get(CommonConstants.INSTANCE_ID_PARAM_NAME);
    // Return null if instanceId is not available - agent is not an Orka agent
    return instanceId;
  }

  @NotNull
  public synchronized CloudInstance startNewInstance(@NotNull final CloudImage image,
      @NotNull final CloudInstanceUserData data) throws QuotaException {
    OrkaCloudImage cloudImage = (OrkaCloudImage) image;

    // 1. Check cached capacity/backoff first (fast)
    CanStartNewInstanceResult canStart = checkCanStartInstance(cloudImage);
    if (!canStart.isPositive()) {
      String reason = canStart.getReason() != null ? canStart.getReason() : "No capacity";
      LOG.info(String.format("[%s] Cannot start instance (cached): %s", this.profileId, reason));
      throw new QuotaException(reason);
    }

    // 2. Do real capacity check before creating instance (prevents "fake"
    // instances)
    try {
      CapacityInfo capacityInfo = this.checkCapacity(cloudImage.getVmConfigName(), cloudImage.getNamespace());
      this.updateCapacityCache(capacityInfo);

      if (!capacityInfo.hasCapacity()) {
        LOG.info(String.format("[%s] Cannot start instance: %s", this.profileId, capacityInfo.getMessage()));
        throw new QuotaException(capacityInfo.getMessage());
      }
    } catch (IOException e) {
      LOG.warn(String.format("[%s] Capacity check failed: %s", this.profileId, e.getMessage()));
      // If capacity check fails, let it through and handle in setUpVM
    }

    String instanceId = UUID.randomUUID().toString();
    OrkaCloudInstance instance = cloudImage.startNewInstance(instanceId);

    this.scheduledExecutorService.submit(() -> this.setUpVM(cloudImage, instance, data));

    return instance;
  }

  private void setUpVM(OrkaCloudImage image, OrkaCloudInstance instance, @NotNull final CloudInstanceUserData data) {
    DeploymentResponse response;
    String deployedInstanceId;

    // Synchronized block to prevent race conditions:
    // Multiple threads checking capacity simultaneously would all see "1 slot
    // available"
    // and try to deploy, causing Orka errors. Lock ensures sequential
    // check+deploy+result.
    synchronized (this.deploymentLock) {
      try {
        // Check capacity before deployment
        CapacityInfo capacityInfo = this.checkCapacity(image.getVmConfigName(), image.getNamespace());
        this.updateCapacityCache(capacityInfo);

        if (!capacityInfo.hasCapacity()) {
          LOG.warn(String.format("[%s] No capacity for '%s' (backoff %.0fs): %s",
              this.profileId, image.getVmConfigName(),
              CAPACITY_FAILURE_BACKOFF_MS / 1000.0, capacityInfo.getMessage()));
          instance.setStatus(InstanceStatus.ERROR);
          instance.setErrorInfo(new CloudErrorInfo("No capacity available", capacityInfo.getMessage()));
          image.terminateInstance(instance.getInstanceId());
          return;
        }

        LOG.info(String.format("[%s] Capacity OK for '%s': %s",
            this.profileId, image.getVmConfigName(), capacityInfo.getMessage()));

        String vmMetadata = image.getVmMetadata();
        if (StringUtil.isNotEmpty(vmMetadata) && !isValidMetadataFormat(vmMetadata)) {
          LOG.warn(String.format("[%s] Invalid VM metadata format: %s. Expected: key1=value1,key2=value2",
              this.profileId, vmMetadata));
          vmMetadata = null;
        }

        // Add TeamCity tracking metadata for recovery after profile reload
        vmMetadata = addTeamCityMetadata(vmMetadata, image.getId());

        // Generate custom VM name based on project metadata
        String vmName = generateVmName(vmMetadata);

        LOG.info(String.format("[%s] Deploying VM: name=%s, config=%s",
            this.profileId, vmName, image.getVmConfigName()));
        response = this.deployVM(vmName, image.getVmConfigName(), image.getNamespace(), vmMetadata);

        // Check deployment result INSIDE synchronized block to set backoff before other
        // threads can proceed
        if (!response.isSuccessful()) {
          String errorMsg = response.getMessage();
          LOG.warn(String.format("[%s] VM deployment failed: %s", this.profileId, errorMsg));

          // If deployment failed due to capacity issue, set backoff immediately
          if (errorMsg != null && errorMsg.contains("Cannot deploy more than")) {
            CapacityInfo failedCapacity = CapacityInfo.noCapacity(
                String.format("Deployment failed: %s", errorMsg));
            this.updateCapacityCache(failedCapacity);
            LOG.warn(String.format("[%s] Setting capacity backoff (%.0fs) due to deployment failure",
                this.profileId, CAPACITY_FAILURE_BACKOFF_MS / 1000.0));
          }

          instance.setStatus(InstanceStatus.ERROR);
          instance.setErrorInfo(new CloudErrorInfo("Deployment failed", errorMsg));
          image.terminateInstance(instance.getInstanceId());
          return;
        }

        deployedInstanceId = response.getName();
        LOG.info(String.format("[%s] VM deployed: %s (IP: %s, SSH port: %d)",
            this.profileId, deployedInstanceId, response.getIP(), response.getSSH()));

        // Invalidate cache after successful deployment (resources changed)
        this.invalidateCapacityCache();

      } catch (Exception e) {
        LOG.warn(String.format("[%s] Error during capacity check/deployment: %s", this.profileId, e.getMessage()));
        instance.setStatus(InstanceStatus.ERROR);
        instance.setErrorInfo(new CloudErrorInfo("Deployment error", e.getMessage()));
        image.terminateInstance(instance.getInstanceId());
        return;
      }
    } // End synchronized - release lock after deployment result processed

    try {
      // Verify VM exists in Orka
      VMResponse vmStatus = this.getVM(deployedInstanceId, image.getNamespace());
      if (!vmStatus.isSuccessful()) {
        LOG.warn(String.format("[%s] VM %s not found in Orka after deployment: %s",
            this.profileId, deployedInstanceId, vmStatus.getMessage()));
        image.terminateInstance(instance.getInstanceId());
        return;
      }

      String host = this.getRealHost(response.getIP());
      int sshPort = response.getSSH();

      if (sshPort == 0) {
        LOG.debug(String.format("[%s] SSH port is 0, using default port 22 for VM %s",
            this.profileId, deployedInstanceId));
        sshPort = 22;
      }

      instance.setStatus(InstanceStatus.STARTING);
      instance.setInstanceId(deployedInstanceId);
      instance.setHost(host);
      instance.setPort(sshPort);

      LOG.debug(String.format("[%s] Waiting for SSH on %s:%d...", this.profileId, host, sshPort));
      this.waitForVM(host, sshPort);

      LOG.debug(String.format("[%s] Configuring agent on VM %s (user: %s, agentDir: %s)",
          this.profileId, deployedInstanceId, image.getUser(), this.agentDirectory));

      // Update buildAgent.properties ONCE, then start agent ONCE
      this.updateBuildAgentPropertiesOnce(host, sshPort, image.getUser(), image.getPassword(), deployedInstanceId);

      // Start agent without retry - if it fails, entire VM setup fails
      this.remoteAgent.startAgent(deployedInstanceId, image.getId(), host, sshPort, image.getUser(),
          image.getPassword(), this.agentDirectory, data);

      instance.setStatus(InstanceStatus.RUNNING);
      LOG.info(String.format("[%s] VM %s setup completed", this.profileId, deployedInstanceId));
    } catch (IOException | InterruptedException e) {
      LOG.warnAndDebugDetails(String.format("[%s] VM setup failed for %s",
          this.profileId, instance.getInstanceId()), e);
      instance.setStatus(InstanceStatus.ERROR);
      instance.setErrorInfo(new CloudErrorInfo(e.getMessage(), e.toString(), e));

      LOG.warn(String.format("[%s] Terminating failed VM %s", this.profileId, instance.getInstanceId()));
      this.terminateNonInitilizedInstance(instance);
    }
  }

  private void terminateNonInitilizedInstance(@NotNull final OrkaCloudInstance instance) {
    if (StringUtil.isEmpty(instance.getHost()) || instance.getPort() <= 0) {
      LOG.debug(String.format("[%s] Terminating non-initialized instance: %s",
          this.profileId, instance.getInstanceId()));

      instance.setStatus(InstanceStatus.STOPPED);
      OrkaCloudImage image = (OrkaCloudImage) instance.getImage();
      image.terminateInstance(instance.getInstanceId());
    } else {
      this.terminateInstance(instance);
    }
  }

  private DeploymentResponse deployVM(String vmName, String vmConfig, String namespace, String vmMetadata)
      throws IOException {
    return this.orkaClient.deployVM(vmName, vmConfig, namespace, vmMetadata);
  }

  DeletionResponse deleteVM(String vmId, String namespace) throws IOException {
    return this.orkaClient.deleteVM(vmId, namespace);
  }

  VMResponse getVM(String vmName, String namespace) throws IOException {
    return this.orkaClient.getVM(vmName, namespace);
  }

  CapacityInfo checkCapacity(String vmConfigName, String namespace) throws IOException {
    return this.orkaClient.checkCapacity(vmConfigName, namespace);
  }

  private void waitForVM(String host, int sshPort) throws InterruptedException, IOException {
    int retries = 12;
    int secondsBetweenRetries = 10;
    this.sshUtil.waitForSSH(host, sshPort, retries, secondsBetweenRetries);
  }

  private void updateBuildAgentPropertiesOnce(String host, int sshPort, String sshUser, String sshPassword,
      String instanceId) throws IOException {
    // Update buildAgent.properties if agentDirectory is configured
    // Always update agent name, update serverUrl only if configured
    if (this.agentDirectory == null || this.agentDirectory.trim().isEmpty()) {
      return; // Skip silently - already logged at init
    }

    String buildAgentPropertiesPath = String.format("%s/conf/buildAgent.properties", this.agentDirectory);
    String agentName = "vm-mac-" + instanceId;
    boolean updateServerUrl = (this.serverUrl != null && !this.serverUrl.trim().isEmpty());

    try (net.schmizz.sshj.SSHClient ssh = new net.schmizz.sshj.SSHClient()) {
      // Connect via SSH
      ssh.setConnectTimeout(60 * 1000);
      ssh.setTimeout(60 * 1000);
      ssh.addHostKeyVerifier(new net.schmizz.sshj.transport.verification.PromiscuousVerifier());
      ssh.connect(host, sshPort);
      ssh.authPassword(sshUser, sshPassword);

      try (net.schmizz.sshj.connection.channel.direct.Session session = ssh.startSession()) {
        String updateCommand;

        if (updateServerUrl) {
          // Update both serverUrl and name
          updateCommand = String.format(
              "sed -i.bak -e 's|^serverUrl=.*|serverUrl=%s|' -e 's|^name=.*|name=%s|' %s && " +
                  "if ! grep -q '^name=' %s; then echo 'name=%s' >> %s; fi",
              this.serverUrl, agentName, buildAgentPropertiesPath,
              buildAgentPropertiesPath, agentName, buildAgentPropertiesPath);
        } else {
          // Update only name
          updateCommand = String.format(
              "sed -i.bak 's|^name=.*|name=%s|' %s && " +
                  "if ! grep -q '^name=' %s; then echo 'name=%s' >> %s; fi",
              agentName, buildAgentPropertiesPath,
              buildAgentPropertiesPath, agentName, buildAgentPropertiesPath);
        }

        net.schmizz.sshj.connection.channel.direct.Session.Command cmd = session.exec(updateCommand);
        cmd.join(60 * 1000, java.util.concurrent.TimeUnit.MILLISECONDS);
        String output = net.schmizz.sshj.common.IOUtils.readFully(cmd.getInputStream()).toString();
        Integer exitStatus = cmd.getExitStatus();

        if (exitStatus == null || exitStatus != 0) {
          LOG.warn(String.format("[%s] Failed to update buildAgent.properties. Exit: %s, Output: %s",
              this.profileId, exitStatus, output));
        }
      }
    }
  }

  public void restartInstance(@NotNull final CloudInstance instance) {
  }

  public void terminateInstance(@NotNull final CloudInstance instance) {
    OrkaCloudInstance orkaInstance = (OrkaCloudInstance) instance;
    OrkaCloudImage image = (OrkaCloudImage) instance.getImage();

    LOG.info(String.format("[%s] Terminating instance %s...", this.profileId, instance.getInstanceId()));
    orkaInstance.setStatus(InstanceStatus.SCHEDULED_TO_STOP);

    this.scheduledExecutorService.submit(() -> {
      try {
        // 1. Stop agent gracefully via SSH
        this.remoteAgent.stopAgent(orkaInstance, image.getId(), orkaInstance.getHost(), orkaInstance.getPort(),
            image.getUser(), image.getPassword(), this.agentDirectory);

        // 2. Delete VM from Orka
        DeletionResponse response = this.deleteVM(instance.getInstanceId(), orkaInstance.getNamespace());
        if (response.isSuccessful()) {
          LOG.info(String.format("[%s] VM %s deleted from Orka", this.profileId, instance.getInstanceId()));
          // Clear capacity backoff since resources are now freed
          this.clearCapacityBackoff();
        } else {
          LOG.warn(String.format("[%s] Failed to delete VM %s from Orka: %s",
              this.profileId, instance.getInstanceId(), response.getMessage()));
        }

        // 3. Remove instance from tracking (TeamCity will remove agent from UI)
        orkaInstance.setStatus(InstanceStatus.STOPPED);
        image.terminateInstance(instance.getInstanceId());
        LOG.info(String.format("[%s] Instance %s terminated", this.profileId, instance.getInstanceId()));

      } catch (IOException e) {
        LOG.warn(String.format("[%s] VM termination failed for %s: %s",
            this.profileId, instance.getInstanceId(), e.getMessage()));
        orkaInstance.setStatus(InstanceStatus.ERROR);
        orkaInstance.setErrorInfo(new CloudErrorInfo(e.getMessage(), e.toString(), e));
        orkaInstance.setMarkedForTermination(true);
      }
    });
  }

  private void setInstanceForDeletion(OrkaCloudInstance instance, CloudErrorInfo errorInfo) {
    instance.setErrorInfo(errorInfo);
    instance.setMarkedForTermination(true);
  }

  public void dispose() {
    if (this.removedFailedInstancesScheduledTask != null) {
      this.removedFailedInstancesScheduledTask.cancel(false);
    }

    for (final OrkaCloudImage image : this.images) {
      image.dispose();
    }
    this.images.clear();
  }

  @Nullable
  private OrkaCloudImage findImage(@NotNull final AgentDescription agentDescription) {
    final String imageId = agentDescription.getConfigurationParameters().get(CommonConstants.IMAGE_ID_PARAM_NAME);
    LOG.debug(String.format("[%s] findImage: imageId=%s", this.profileId, imageId));
    return imageId == null ? null : this.findImageById(imageId);
  }

  @Nullable
  private String findInstanceId(@NotNull final AgentDescription agentDescription) {
    return agentDescription.getConfigurationParameters().get(CommonConstants.INSTANCE_ID_PARAM_NAME);
  }

  private String getRealHost(String host) {
    return this.nodeMappings.keySet().stream().filter(k -> k.equalsIgnoreCase(host)).findFirst()
        .map(k -> this.nodeMappings.get(k)).orElse(host);
  }

  private boolean isValidMetadataFormat(String metadata) {
    if (StringUtil.isEmpty(metadata)) {
      return true; // Empty metadata is valid
    }

    // Validate format: key1=value1,key2=value2
    // Keys: alphanumeric, underscore, hyphen
    // Values: anything except comma and equals
    return metadata.matches(CommonConstants.VM_METADATA_VALIDATION_PATTERN);
  }

  // TeamCity metadata keys for VM tracking and recovery
  private static final String TC_PROFILE_ID_KEY = "tc_profile_id";
  private static final String TC_IMAGE_ID_KEY = "tc_image_id";

  /**
   * Recovers existing VMs from Orka that belong to this profile.
   * Called at startup to restore state after profile reload.
   */
  private void recoverExistingInstances() {
    if (this.orkaClient == null || this.images.isEmpty()) {
      return;
    }

    OrkaCloudImage image = this.images.get(0);
    try {
      VMsResponse vmsResponse = this.orkaClient.getVMs(image.getNamespace());
      java.util.List<OrkaVM> vms = vmsResponse.getVMs();
      if (vms == null || vms.isEmpty()) {
        LOG.debug(String.format("[%s] No VMs found in namespace '%s'", this.profileId, image.getNamespace()));
        return;
      }

      int recoveredCount = 0;
      for (OrkaVM vm : vms) {
        if (!vm.isRunning()) {
          continue;
        }

        // Check if VM belongs to this profile via metadata
        if (!vmBelongsToThisProfile(vm)) {
          continue;
        }

        // Check if instance already exists
        if (image.findInstanceById(vm.getName()) != null) {
          continue;
        }

        // Recover instance
        OrkaCloudInstance instance = image.startNewInstance(vm.getName());
        instance.setStatus(InstanceStatus.RUNNING);
        instance.setHost(this.getRealHost(vm.getIp()));
        instance.setPort(vm.getSsh() > 0 ? vm.getSsh() : 22);

        LOG.info(String.format("[%s] Recovered VM: %s (IP: %s, SSH: %d)",
            this.profileId, vm.getName(), vm.getIp(), instance.getPort()));
        recoveredCount++;
      }

      if (recoveredCount > 0) {
        LOG.info(String.format("[%s] Recovered %d existing VM(s)", this.profileId, recoveredCount));
      }
    } catch (Exception e) {
      LOG.warn(String.format("[%s] Failed to recover existing VMs: %s", this.profileId, e.getMessage()));
    }
  }

  /**
   * Adds TeamCity tracking metadata to VM for recovery after profile reload.
   */
  private String addTeamCityMetadata(String existingMetadata, String imageId) {
    String tcMetadata = String.format("%s=%s,%s=%s",
        TC_PROFILE_ID_KEY, this.profileId,
        TC_IMAGE_ID_KEY, imageId);

    if (StringUtil.isEmpty(existingMetadata)) {
      return tcMetadata;
    }
    return existingMetadata + "," + tcMetadata;
  }

  /**
   * Checks if VM belongs to this profile based on metadata.
   */
  private boolean vmBelongsToThisProfile(OrkaVM vm) {
    if (vm == null || vm.getMetadata() == null) {
      return false;
    }
    String vmProfileId = vm.getMetadata().get(TC_PROFILE_ID_KEY);
    return this.profileId.equals(vmProfileId);
  }

  /**
   * Generates VM name in format: {project}-tc-{random} or vm-tc-{random} if no
   * project.
   */
  private String generateVmName(String vmMetadata) {
    String project = extractProjectFromMetadata(vmMetadata);
    String suffix = generateRandomSuffix(5);

    String vmName;
    if (StringUtil.isNotEmpty(project)) {
      // Sanitize project name: lowercase, replace invalid chars with hyphen
      String sanitizedProject = project.toLowerCase()
          .replaceAll("[^a-z0-9-]", "-") // Replace invalid chars with hyphen
          .replaceAll("-+", "-") // Collapse multiple hyphens
          .replaceAll("^-|-$", ""); // Remove leading/trailing hyphens

      // Limit project part to keep total name <= 63 chars (k8s limit)
      // Format: {project}-tc-{suffix} where suffix is 5 chars
      int maxProjectLen = 63 - 4 - 5; // 54 chars for project
      if (sanitizedProject.length() > maxProjectLen) {
        sanitizedProject = sanitizedProject.substring(0, maxProjectLen);
      }

      vmName = sanitizedProject + "-tc-" + suffix;
    } else {
      vmName = "vm-tc-" + suffix;
    }

    LOG.debug(String.format("[%s] Generated VM name: %s (project: %s)", this.profileId, vmName, project));
    return vmName;
  }

  /**
   * Extracts target_project value from metadata string.
   */
  private String extractProjectFromMetadata(String vmMetadata) {
    if (StringUtil.isEmpty(vmMetadata)) {
      return null;
    }

    String[] pairs = vmMetadata.split(",");
    for (String pair : pairs) {
      String[] keyValue = pair.trim().split("=", 2);
      if (keyValue.length == 2 && "target_project".equalsIgnoreCase(keyValue[0].trim())) {
        return keyValue[1].trim();
      }
    }
    return null;
  }

  /**
   * Generates random alphanumeric suffix.
   */
  private String generateRandomSuffix(int length) {
    String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
    SecureRandom random = new SecureRandom();
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(chars.charAt(random.nextInt(chars.length())));
    }
    return sb.toString();
  }

  /**
   * Extracts profile name from TeamCity's profile description format.
   * Input format: "profile 'NAME'{id=ID}"
   * Returns: "NAME" or fallback if parsing fails
   */
  private static String extractProfileName(String description, String fallback) {
    if (StringUtil.isEmpty(description)) {
      return fallback;
    }
    Matcher matcher = PROFILE_NAME_PATTERN.matcher(description);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return fallback;
  }
}
