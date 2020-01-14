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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OrkaCloudImage implements CloudImage {
    private static final Logger LOG = Logger.getInstance(OrkaCloudImage.class.getName());
    @NotNull
    private final String id;
    @NotNull
    private final String user;
    @NotNull
    private final String password;
    private final int agentPoolId;
    private final int instanceLimit;
    @NotNull
    private final Map<String, OrkaCloudInstance> instances = new ConcurrentHashMap<String, OrkaCloudInstance>();

    public OrkaCloudImage(@NotNull final String imageId, @NotNull final String user, @NotNull final String password,
            @NotNull final String agentPoolId, int instanceLimit) {
        this.id = imageId;
        this.user = user;
        this.password = password;
        this.agentPoolId = Integer.parseInt(agentPoolId);
        this.instanceLimit = instanceLimit;
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
        LOG.warn(String.format("canStartNewInstance this.instanceLimit: %s and instances: %s", this.instanceLimit,
                this.instances.size()));
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
        return new OrkaCloudInstance(this, instanceId);
    }

    public void terminateInstance(String instanceId) {
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