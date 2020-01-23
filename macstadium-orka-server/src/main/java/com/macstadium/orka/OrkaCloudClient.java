package com.macstadium.orka;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.macstadium.orka.client.DeploymentResponse;
import com.macstadium.orka.client.OrkaClient;
import com.macstadium.orka.client.VMInstance;
import com.macstadium.orka.client.VMResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.BuildServerAdapter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OrkaCloudClient extends BuildServerAdapter implements CloudClientEx {
    private static final Logger LOG = Logger.getInstance(OrkaCloudClient.class.getName());

    @NotNull
    private final List<OrkaCloudImage> images = new ArrayList<OrkaCloudImage>();

    private final String agentDirectory;
    private OrkaClient orkaClient;
    private final AsyncExecutor asyncExecutor;
    private CloudErrorInfo errorInfo;
    private final RemoteAgent remoteAgent;
    private final SSHUtil sshUtil;
    private Map<String, String> nodeMappings;

    public OrkaCloudClient(@NotNull final CloudClientParameters params) {
        this.initializeOrkaClient(params);
        this.agentDirectory = params.getParameter(OrkaConstants.AGENT_DIRECTORY);
        this.images.add(this.createImage(params));
        this.asyncExecutor = new AsyncExecutor("OrkaCloudClient");
        this.remoteAgent = new RemoteAgent();
        this.sshUtil = new SSHUtil();
        this.nodeMappings = this.getNodeMappings(params.getParameter(OrkaConstants.NODE_MAPPINGS));
    }

    @Used("Tests")
    public OrkaCloudClient(CloudClientParameters params, OrkaClient client, AsyncExecutor executor,
            RemoteAgent remoteAgent, SSHUtil sshUtil) {
        this.agentDirectory = params.getParameter(OrkaConstants.AGENT_DIRECTORY);
        this.images.add(this.createImage(params));
        this.asyncExecutor = executor;
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

    public Map<String, String> getH() {
        return nodeMappings;
    }

    private void initializeOrkaClient(CloudClientParameters params) {
        String endpoint = params.getParameter(OrkaConstants.ORKA_ENDPOINT);
        String user = params.getParameter(OrkaConstants.ORKA_USER);
        String password = params.getParameter(OrkaConstants.ORKA_PASSWORD);

        LOG.debug(String.format("OrkaCloudClient with endpoint: %s, user: %s, agentDirectory: %s", endpoint, user,
                this.agentDirectory));

        try {
            this.orkaClient = new OrkaClient(endpoint, user, password);
        } catch (IOException e) {
            this.errorInfo = new CloudErrorInfo("Cannot initialize Orka client", e.toString(), e);
        }
    }

    private OrkaCloudImage createImage(CloudClientParameters params) {
        String vm = params.getParameter(OrkaConstants.VM_NAME);
        String vmUser = params.getParameter(OrkaConstants.VM_USER);
        String vmPassword = params.getParameter(OrkaConstants.VM_PASSWORD);
        String agentPoolId = params.getParameter(CloudImageParameters.AGENT_POOL_ID_FIELD);
        String instanceLimit = params.getParameter(OrkaConstants.INSTANCE_LIMIT);
        int limit = StringUtil.isEmpty(instanceLimit) ? OrkaConstants.UNLIMITED_INSTANCES
                : Integer.parseInt(instanceLimit);

        LOG.debug(String.format("OrkaCloudClient createImage with vm: %s, user: %s, poolId: %s, instanceLimit: %s", vm,
                vmUser, agentPoolId, instanceLimit));

        return new OrkaCloudImage(vm, vmUser, vmPassword, agentPoolId, limit);
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

            VMResponse vmResponse = this.orkaClient.getVM(image.getName());
            if (vmResponse != null) {
                LOG.debug(String.format("createInstanceFromExistingAgent vm found %s.", vmResponse));

                Optional<VMInstance> instance = Arrays.stream(vmResponse.getInstances())
                        .filter(i -> i.getId().equalsIgnoreCase(instanceId)).findFirst();
                if (instance.isPresent()) {
                    LOG.debug(String.format("createInstanceFromExistingAgent instance found %s.", instance.get()));
                    OrkaCloudInstance cloudInstance = image.startNewInstance(instanceId);
                    cloudInstance.setStatus(InstanceStatus.RUNNING);
                    cloudInstance.setHost(this.getRealHost(instance.get().getHost()));
                    cloudInstance.setPort(Integer.parseInt(instance.get().getSSHPort()));
                    return cloudInstance;
                }
            }

        } catch (IOException | NumberFormatException e) {
            LOG.debug(String.format("createInstanceFromExistingAgent error", e));
        }
        LOG.debug("createInstanceFromExistingAgent nothing found.");
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
        return agent.getConfigurationParameters().get(CommonConstants.INSTANCE_ID_PARAM_NAME);
    }

    @NotNull
    public CloudInstance startNewInstance(@NotNull final CloudImage image, @NotNull final CloudInstanceUserData data)
            throws QuotaException {
        String instanceId = UUID.randomUUID().toString();
        OrkaCloudImage cloudImage = (OrkaCloudImage) image;
        OrkaCloudInstance instance = cloudImage.startNewInstance(instanceId);
        LOG.debug(String.format("startNewInstance with temp id: %s", instanceId));

        this.asyncExecutor.submit("Boot Orka Vm", () -> this.setUpVM(cloudImage, instance));

        return instance;
    }

    private void setUpVM(OrkaCloudImage image, OrkaCloudInstance instance) {
        try {
            LOG.debug(String.format("setUpVM deploying vm: %s", image.getName()));
            DeploymentResponse response = this.deployVM(image.getName());
            if (response.hasErrors()) {
                LOG.debug(String.format("setUpVM deployment errors: %s", Arrays.toString(response.getErrors())));
                instance.setErrorInfo(new CloudErrorInfo(Arrays.toString(response.getErrors())));
            }
            String instanceId = response.getId();
            String host = this.getRealHost(response.getHost());
            int sshPort = response.getSSHPort();

            LOG.debug(String.format("setUpVM instanceId: %s, host: %s, port: %s", instanceId, host, sshPort));

            instance.setStatus(InstanceStatus.STARTING);
            instance.setInstanceId(instanceId);
            instance.setHost(host);
            instance.setPort(sshPort);

            LOG.debug("setUpVM waiting for SSH to be enabled");
            this.waitForVM(host, sshPort);
            this.remoteAgent.startAgent(instanceId, image.getId(), host, sshPort, image.getUser(), image.getPassword(),
                    this.agentDirectory);
            instance.setStatus(InstanceStatus.RUNNING);
        } catch (IOException | InterruptedException e) {
            LOG.debug("setUpVM error", e);
            instance.setErrorInfo(new CloudErrorInfo(e.getMessage(), e.toString(), e));
        }
    }

    private DeploymentResponse deployVM(String vmName) throws IOException {
        DeploymentResponse response = this.orkaClient.deployVM(vmName);

        return response;
    }

    private void waitForVM(String host, int sshPort) throws InterruptedException, IOException {
        int retries = 12;
        int secondsBetweenRetries = 10;
        this.sshUtil.waitForSSH(host, sshPort, retries, secondsBetweenRetries);
    }

    public void restartInstance(@NotNull final CloudInstance instance) {
    }

    public void terminateInstance(@NotNull final CloudInstance instance) {
        OrkaCloudInstance orkaInstance = (OrkaCloudInstance) instance;
        this.asyncExecutor.submit("Terminate Orka Vm", () -> {
            try {
                LOG.debug(String.format("terminateInstance id: %s", instance.getInstanceId()));
                OrkaCloudImage image = (OrkaCloudImage) instance.getImage();

                LOG.debug(String.format("terminateInstance with image id: %s", image.getId()));

                orkaInstance.setStatus(InstanceStatus.SCHEDULED_TO_STOP);

                this.remoteAgent.stopAgent(orkaInstance, image.getId(), orkaInstance.getHost(), orkaInstance.getPort(),
                        image.getUser(), image.getPassword(), this.agentDirectory);

                LOG.debug("terminateInstance deleting vm");
                this.orkaClient.deleteVM(instance.getInstanceId());
                orkaInstance.setStatus(InstanceStatus.STOPPED);
                image.terminateInstance(instance.getInstanceId());
            } catch (IOException e) {
                LOG.debug("terminateInstance error", e);
                orkaInstance.setErrorInfo(new CloudErrorInfo(e.getMessage(), e.toString(), e));
            }
        });
    }

    public void dispose() {
        this.asyncExecutor.dispose();

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
}