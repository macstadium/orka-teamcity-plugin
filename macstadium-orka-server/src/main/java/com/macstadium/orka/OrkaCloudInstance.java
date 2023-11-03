package com.macstadium.orka;

import com.intellij.openapi.diagnostic.Logger;

import java.util.Date;
import java.util.Map;

import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.AgentDescription;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OrkaCloudInstance implements CloudInstance {
    private static final Logger LOG = Logger.getInstance(Loggers.CLOUD_CATEGORY_ROOT + OrkaConstants.TYPE);

    @NotNull
    private String id;
    @NotNull
    private String namespace;
    @NotNull
    private final OrkaCloudImage image;
    @NotNull
    private final Date startDate;
    @NotNull
    private String host;
    private int sshPort;
    @NotNull
    private volatile InstanceStatus status;
    @Nullable
    private volatile CloudErrorInfo errorInfo;
    private boolean markedForTermination;

    public OrkaCloudInstance(@NotNull final OrkaCloudImage image, @NotNull final String instanceId,
            @NotNull final String namespace) {
        this.image = image;
        this.namespace = namespace;
        this.status = InstanceStatus.SCHEDULED_TO_START;
        this.id = instanceId;
        this.startDate = new Date();
    }

    public boolean isMarkedForTermination() {
        return markedForTermination;
    }

    public void setMarkedForTermination(boolean markedForTermination) {
        this.markedForTermination = markedForTermination;
    }

    @NotNull
    public String getInstanceId() {
        return this.id;
    }

    public void setInstanceId(String id) {
        this.image.removeInstance(this.getInstanceId());
        this.id = id;
        this.image.addInstance(this);
    }

    @NotNull
    public String getName() {
        return this.id;
    }

    @NotNull
    public String getImageId() {
        return this.image.getId();
    }

    @NotNull
    public OrkaCloudImage getImage() {
        return this.image;
    }

    @NotNull
    public Date getStartedTime() {
        return this.startDate;
    }

    public String getNetworkIdentity() {
        return "";
    }

    @NotNull
    public InstanceStatus getStatus() {
        return this.status;
    }

    public void setStatus(InstanceStatus status) {
        this.status = status;
    }

    @NotNull
    public String getHost() {
        return this.host;
    }

    @NotNull
    public String getNamespace() {
        return this.namespace;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return this.sshPort;
    }

    public void setPort(int sshPort) {
        this.sshPort = sshPort;
    }

    @Nullable
    public CloudErrorInfo getErrorInfo() {
        return this.errorInfo;
    }

    public void setErrorInfo(CloudErrorInfo errorInfo) {
        this.errorInfo = errorInfo;
    }

    public boolean containsAgent(@NotNull final AgentDescription agentDescription) {
        final Map<String, String> configParams = agentDescription.getConfigurationParameters();
        String instanceId = configParams.get(OrkaConstants.INSTANCE_ID_PARAM_NAME);
        String imageId = configParams.get(OrkaConstants.IMAGE_ID_PARAM_NAME);

        LOG.debug(String.format("containsAgent with instanceId: %s and imageId: %s", instanceId, imageId));

        return this.id.equals(instanceId) && getImageId().equals(imageId);
    }
}
