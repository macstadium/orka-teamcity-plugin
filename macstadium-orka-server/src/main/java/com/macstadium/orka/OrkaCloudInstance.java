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
    private volatile boolean markedForTermination;
    private volatile boolean agentConnectedOnce = false;

    public OrkaCloudInstance(@NotNull final OrkaCloudImage image, @NotNull final String instanceId,
            @NotNull final String namespace) {
        this.image = image;
        this.namespace = namespace;
        this.status = InstanceStatus.SCHEDULED_TO_START;
        this.id = instanceId;
        this.startDate = new Date();
        this.host = "";  // Initialize to empty string to avoid NPE
    }

    public boolean isMarkedForTermination() {
        return markedForTermination;
    }

    public void setMarkedForTermination(boolean markedForTermination) {
        this.markedForTermination = markedForTermination;
    }

    public boolean hasAgentConnected() {
        return agentConnectedOnce;
    }

    public void markAgentConnected() {
        this.agentConnectedOnce = true;
        LOG.debug(String.format("Agent connected for instance %s", this.id));
    }

    @NotNull
    public String getInstanceId() {
        return this.id;
    }

    public void setInstanceId(String id) {
        final String oldId = this.id;
        this.image.removeInstance(this.getInstanceId());
        this.id = id;
        this.image.addInstance(this);
        LOG.debug(String.format("Instance ID updated: %s -> %s", oldId, id));
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
        String agentInstanceId = configParams.get(OrkaConstants.INSTANCE_ID_PARAM_NAME);
        String agentImageId = configParams.get(OrkaConstants.IMAGE_ID_PARAM_NAME);

        boolean matches = this.id.equals(agentInstanceId) && getImageId().equals(agentImageId);

        if (matches && !this.agentConnectedOnce) {
            this.markAgentConnected();
        }

        LOG.debug(String.format("Agent match check for VM %s: agent(instance=%s, image=%s) -> %s",
                this.id, agentInstanceId, agentImageId, matches ? "MATCH" : "NO MATCH"));

        return matches;
    }
}
