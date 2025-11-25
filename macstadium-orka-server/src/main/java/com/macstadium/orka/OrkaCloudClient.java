package com.macstadium.orka;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.macstadium.orka.client.AwsEksTokenProvider;
import com.macstadium.orka.client.DeletionResponse;
import com.macstadium.orka.client.DeploymentResponse;
import com.macstadium.orka.client.OrkaClient;
import com.macstadium.orka.client.StaticTokenProvider;
import com.macstadium.orka.client.TokenProvider;
import com.macstadium.orka.client.VMResponse;

import java.io.IOException;
import java.util.ArrayList;
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

  @NotNull
  private final List<OrkaCloudImage> images = new ArrayList<OrkaCloudImage>();

  private final String agentDirectory;
  private final String serverUrl;
  private OrkaClient orkaClient;
  private final ScheduledExecutorService scheduledExecutorService;
  private ScheduledFuture<?> removedFailedInstancesScheduledTask;
  private CloudErrorInfo errorInfo;
  private final RemoteAgent remoteAgent;
  private final SSHUtil sshUtil;
  private Map<String, String> nodeMappings;

  public OrkaCloudClient(@NotNull final CloudClientParameters params, ExecutorServices executorServices,
      @Nullable final String unusedWebLinksServerUrl) {
    this.initializeOrkaClient(params);
    this.agentDirectory = params.getParameter(OrkaConstants.AGENT_DIRECTORY);
    // Read serverUrl from Cloud Profile parameters (user-configured)
    this.serverUrl = params.getParameter(OrkaConstants.SERVER_URL);

    if (this.agentDirectory == null || this.agentDirectory.trim().isEmpty()) {
      LOG.debug("Agent Directory not configured - using default from VM image");
    }
    if (this.serverUrl == null || this.serverUrl.trim().isEmpty()) {
      LOG.debug("Server URL not configured - agent will use serverUrl from VM image");
    }

    this.images.add(this.createImage(params));
    this.scheduledExecutorService = executorServices.getNormalExecutorService();
    this.remoteAgent = new RemoteAgent();
    this.sshUtil = new SSHUtil();
    this.nodeMappings = this.getNodeMappings(params.getParameter(OrkaConstants.NODE_MAPPINGS));

    this.initializeBackgroundTasks();
  }

  @Used("Tests")
  public OrkaCloudClient(CloudClientParameters params, OrkaClient client,
      ScheduledExecutorService scheduledExecutorService, RemoteAgent remoteAgent, SSHUtil sshUtil) {
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
              "AWS IAM authentication requires both cluster name and region. Got: cluster='%s', region='%s'",
              clusterName, region);
          LOG.error(errorMsg);
          this.errorInfo = new CloudErrorInfo("Invalid AWS IAM configuration", errorMsg);
          return;
        }

        tokenProvider = new AwsEksTokenProvider(clusterName, region);
        LOG.info(String.format("OrkaClient initialized with AWS IAM auth (cluster: %s, region: %s)",
            clusterName, region));
      } else {
        String token = params.getParameter(OrkaConstants.TOKEN);
        tokenProvider = new StaticTokenProvider(token);
        LOG.info("OrkaClient initialized with static token");
      }

      this.orkaClient = new OrkaClient(endpoint, tokenProvider);
    } catch (Exception e) {
      LOG.error("Failed to initialize Orka client", e);
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

    LOG.debug(String.format("createImage: vm='%s', namespace='%s', poolId='%s', limit=%s",
        vm, namespace, agentPoolId, instanceLimit));

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

    return new OrkaCloudImage(vm, namespace, vmUser, vmPassword, agentPoolId, limit, vmMetadata);
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
    LOG.debug("findInstanceByAgent");
    final OrkaCloudImage image = this.findImage(agentDescription);
    if (image == null) {
      return null;
    }
    final String instanceId = this.findInstanceId(agentDescription);
    LOG.debug(String.format("findInstanceByAgent with instanceId: %s", instanceId));
    if (instanceId == null) {
      return null;
    }

    OrkaCloudInstance existingInstance = image.findInstanceById(instanceId);
    if (existingInstance != null) {
      LOG.debug(String.format("findInstanceByAgent existing instance found"));
      return existingInstance;
    }
    return this.createInstanceFromExistingAgent(image, instanceId);
  }

  @Nullable
  private OrkaCloudInstance createInstanceFromExistingAgent(OrkaCloudImage image, String instanceId) {
    try {
      LOG.debug(String.format("createInstanceFromExistingAgent searching for vm: %s.", image.getName()));

      VMResponse vmResponse = this.getVM(instanceId, image.getNamespace());
      if (vmResponse != null && vmResponse.isSuccessful()) {
        LOG.debug(String.format("createInstanceFromExistingAgent vm found %s.", vmResponse));

        LOG.debug(String.format("createInstanceFromExistingAgent instance found %s.", vmResponse));
        OrkaCloudInstance cloudInstance = image.startNewInstance(instanceId);
        cloudInstance.setStatus(InstanceStatus.RUNNING);
        cloudInstance.setHost(this.getRealHost(vmResponse.getIP()));
        cloudInstance.setPort(vmResponse.getSSH());
        return cloudInstance;
      }

    } catch (IOException | NumberFormatException e) {
      LOG.debug(String.format("Failed to create instance from existing agent: %s", e.getMessage()));
    }
    LOG.debug("createInstanceFromExistingAgent: no matching agent found");
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
    return ((OrkaCloudImage) image).canStartNewInstance();
  }

  @Nullable
  public String generateAgentName(@NotNull final AgentDescription agent) {
    String instanceId = agent.getConfigurationParameters().get(CommonConstants.INSTANCE_ID_PARAM_NAME);
    // Return null if instanceId is not available - agent is not an Orka agent
    return instanceId;
  }

  @NotNull
  public CloudInstance startNewInstance(@NotNull final CloudImage image, @NotNull final CloudInstanceUserData data)
      throws QuotaException {
    String instanceId = UUID.randomUUID().toString();
    OrkaCloudImage cloudImage = (OrkaCloudImage) image;
    OrkaCloudInstance instance = cloudImage.startNewInstance(instanceId);
    LOG.debug(String.format("startNewInstance with temp id: %s", instanceId));

    this.scheduledExecutorService.submit(() -> this.setUpVM(cloudImage, instance, data));

    return instance;
  }

  private void setUpVM(OrkaCloudImage image, OrkaCloudInstance instance, @NotNull final CloudInstanceUserData data) {
    try {
      LOG.debug(String.format("Setting up VM for image '%s' (namespace: %s)",
          image.getName(), image.getNamespace()));

      String vmMetadata = image.getVmMetadata();
      if (StringUtil.isNotEmpty(vmMetadata) && !isValidMetadataFormat(vmMetadata)) {
        LOG.warn(String.format("Invalid VM metadata format: %s. Expected: key1=value1,key2=value2", vmMetadata));
        vmMetadata = null;
      }

      DeploymentResponse response = this.deployVM(image.getName(), image.getNamespace(), vmMetadata);
      if (!response.isSuccessful()) {
        LOG.warn(String.format("VM deployment failed: %s", response.getMessage()));
        image.terminateInstance(instance.getInstanceId());
        return;
      }

      String instanceId = response.getName();
      LOG.info(
          String.format("VM deployed: %s (IP: %s, SSH port: %d)", instanceId, response.getIP(), response.getSSH()));

      // Verify VM exists in Orka
      VMResponse vmStatus = this.getVM(instanceId, image.getNamespace());
      if (!vmStatus.isSuccessful()) {
        LOG.warn(String.format("VM %s not found in Orka after deployment: %s", instanceId, vmStatus.getMessage()));
        image.terminateInstance(instance.getInstanceId());
        return;
      }

      String host = this.getRealHost(response.getIP());
      int sshPort = response.getSSH();

      if (sshPort == 0) {
        LOG.debug(String.format("SSH port is 0, using default port 22 for VM %s", instanceId));
        sshPort = 22;
      }

      instance.setStatus(InstanceStatus.STARTING);
      instance.setInstanceId(instanceId);
      instance.setHost(host);
      instance.setPort(sshPort);

      LOG.debug(String.format("Waiting for SSH on %s:%d...", host, sshPort));
      this.waitForVM(host, sshPort);

      LOG.debug(String.format("Configuring and starting agent on VM %s (user: %s, agentDir: %s)",
          instanceId, image.getUser(), this.agentDirectory));

      // Update buildAgent.properties ONCE, then start agent ONCE
      this.updateBuildAgentPropertiesOnce(host, sshPort, image.getUser(), image.getPassword(), instanceId);

      // Start agent without retry - if it fails, entire VM setup fails
      this.remoteAgent.startAgent(instanceId, image.getId(), host, sshPort, image.getUser(),
          image.getPassword(), this.agentDirectory, data);

      instance.setStatus(InstanceStatus.RUNNING);
      LOG.info(String.format("VM %s setup completed - agent will auto-start", instanceId));
    } catch (IOException | InterruptedException e) {
      LOG.warnAndDebugDetails("VM setup failed for " + instance.getInstanceId(), e);
      instance.setStatus(InstanceStatus.ERROR);
      instance.setErrorInfo(new CloudErrorInfo(e.getMessage(), e.toString(), e));

      LOG.warn(String.format("Terminating failed VM %s after all retries", instance.getInstanceId()));
      this.terminateNonInitilizedInstance(instance);
    }
  }

  private void terminateNonInitilizedInstance(@NotNull final OrkaCloudInstance instance) {
    if (StringUtil.isEmpty(instance.getHost()) || instance.getPort() <= 0) {
      LOG.debug(String.format("terminating not initialized instance id: %s", instance.getInstanceId()));

      instance.setStatus(InstanceStatus.STOPPED);
      OrkaCloudImage image = (OrkaCloudImage) instance.getImage();
      image.terminateInstance(instance.getInstanceId());
    } else {
      this.terminateInstance(instance);
    }
  }

  private DeploymentResponse deployVM(String vmName, String namespace, String vmMetadata) throws IOException {
    return this.orkaClient.deployVM(vmName, namespace, vmMetadata);
  }

  DeletionResponse deleteVM(String vmId, String namespace) throws IOException {
    return this.orkaClient.deleteVM(vmId, namespace);
  }

  VMResponse getVM(String vmName, String namespace) throws IOException {
    return this.orkaClient.getVM(vmName, namespace);
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
      LOG.debug("Skipping buildAgent.properties update - agentDirectory not configured");
      return;
    }

    String buildAgentPropertiesPath = String.format("%s/conf/buildAgent.properties", this.agentDirectory);
    String agentName = "orka-mac-" + instanceId;
    boolean updateServerUrl = (this.serverUrl != null && !this.serverUrl.trim().isEmpty());

    if (updateServerUrl) {
      LOG.debug(String.format("Updating buildAgent.properties: name=%s, serverUrl=%s", agentName, this.serverUrl));
    } else {
      LOG.debug(String.format("Updating buildAgent.properties: name=%s (serverUrl not changed)", agentName));
    }

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

        if (exitStatus != null && exitStatus == 0) {
          LOG.debug(String.format("buildAgent.properties updated: name=%s%s",
              agentName, updateServerUrl ? ", serverUrl=" + this.serverUrl : ""));
        } else {
          LOG.warn(String.format("Failed to update buildAgent.properties at %s. Exit code: %s. Output: %s",
              buildAgentPropertiesPath, exitStatus, output));
        }
      }
    }
  }

  public void restartInstance(@NotNull final CloudInstance instance) {
  }

  public void terminateInstance(@NotNull final CloudInstance instance) {
    OrkaCloudInstance orkaInstance = (OrkaCloudInstance) instance;
    this.scheduledExecutorService.submit(() -> {
      try {
        LOG.info(String.format("Terminating VM %s...", instance.getInstanceId()));
        OrkaCloudImage image = (OrkaCloudImage) instance.getImage();

        orkaInstance.setStatus(InstanceStatus.SCHEDULED_TO_STOP);

        this.remoteAgent.stopAgent(orkaInstance, image.getId(), orkaInstance.getHost(), orkaInstance.getPort(),
            image.getUser(), image.getPassword(), this.agentDirectory);

        DeletionResponse response = this.deleteVM(instance.getInstanceId(), orkaInstance.getNamespace());
        if (response.isSuccessful()) {
          orkaInstance.setStatus(InstanceStatus.STOPPED);
          image.terminateInstance(instance.getInstanceId());
          LOG.info(String.format("VM %s terminated successfully", instance.getInstanceId()));
        } else {
          LOG.warn(String.format("Failed to delete VM %s: %s", instance.getInstanceId(), response.getMessage()));
          this.setInstanceForDeletion(orkaInstance, new CloudErrorInfo("Error deleting VM", response.getMessage()));
        }
      } catch (IOException e) {
        LOG.warnAndDebugDetails("VM termination failed for " + instance.getInstanceId(), e);
        orkaInstance.setStatus(InstanceStatus.ERROR);
        this.setInstanceForDeletion(orkaInstance, new CloudErrorInfo(e.getMessage(), e.toString(), e));
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
    LOG.debug(String.format("findImage with imageId: %s", imageId));
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
}
