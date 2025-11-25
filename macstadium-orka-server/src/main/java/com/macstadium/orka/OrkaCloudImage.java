package com.macstadium.orka;

import com.intellij.openapi.diagnostic.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.QuotaException;
import jetbrains.buildServer.log.Loggers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OrkaCloudImage implements CloudImage {
    private static final Logger LOG = Logger.getInstance(Loggers.CLOUD_CATEGORY_ROOT + OrkaConstants.TYPE);
    @NotNull
    private final String id;
    @NotNull
    private final String user;
    @NotNull
    private final String password;
    @NotNull
    private final String namespace;
    private final int agentPoolId;
    private final int instanceLimit;
    @Nullable
    private final String vmMetadata;
    @NotNull
    private final Map<String, OrkaCloudInstance> instances = new ConcurrentHashMap<String, OrkaCloudInstance>();

    public OrkaCloudImage(@NotNull final String imageId, @NotNull final String namespace, @NotNull final String user,
            @NotNull final String password,
            @Nullable final String agentPoolId, int instanceLimit, @Nullable final String vmMetadata) {
        this.id = imageId;
        this.namespace = namespace;
        this.user = user;
        this.password = password;
        // Default to 0 if agentPoolId is not specified (will use default pool)
        this.agentPoolId = (agentPoolId != null && !agentPoolId.isEmpty()) ? Integer.parseInt(agentPoolId) : 0;
        this.instanceLimit = instanceLimit;
        this.vmMetadata = vmMetadata;
    }

    @NotNull
    public String getId() {
        return this.id;
    }

    @NotNull
    public String getName() {
        return this.id;
    }

    @NotNull
    public String getUser() {
        return this.user;
    }

    @NotNull
    public String getPassword() {
        return this.password;
    }

    @NotNull
    public String getNamespace() {
        return this.namespace;
    }

    @Nullable
    public String getVmMetadata() {
        return this.vmMetadata;
    }

    @NotNull
    public Collection<? extends CloudInstance> getInstances() {
        return Collections.unmodifiableCollection(this.instances.values());
    }

    @Nullable
    public OrkaCloudInstance findInstanceById(@NotNull final String instanceId) {
        LOG.debug("findInstanceById with instanceId: " + instanceId);
        return this.instances.get(instanceId);
    }

    @Nullable
    @Override
    public Integer getAgentPoolId() {
        return this.agentPoolId;
    }

    @Nullable
    public CloudErrorInfo getErrorInfo() {
        return null;
    }

    public synchronized boolean canStartNewInstance() {
        return this.instanceLimit == OrkaConstants.UNLIMITED_INSTANCES || this.instanceLimit > this.instances.size();
    }

    @NotNull
    public synchronized OrkaCloudInstance startNewInstance(@NotNull final String instanceId) {
        if (!this.canStartNewInstance()) {
            LOG.debug(String.format("Quota exceeded. Number of instances: %s and limit: %s", this.instances.size(),
                    this.instanceLimit));
            throw new QuotaException("Maximum number of instances already launched." + this.getName());
        }
        LOG.debug(String.format("Starting new instance with id: %s", instanceId));
        final OrkaCloudInstance instance = this.createInstance(instanceId);
        this.instances.put(instanceId, instance);
        return instance;
    }

    protected OrkaCloudInstance createInstance(String instanceId) {
        return new OrkaCloudInstance(this, instanceId, this.getNamespace());
    }

    public void terminateInstance(String instanceId) {
        LOG.debug(String.format("Terminate instance with id: %s", instanceId));
        this.removeInstance(instanceId);
    }

    void addInstance(OrkaCloudInstance instance) {
        this.instances.put(instance.getInstanceId(), instance);
    }

    void removeInstance(String instanceId) {
        this.instances.remove(instanceId);
    }

    void dispose() {
        this.instances.clear();
    }
}
