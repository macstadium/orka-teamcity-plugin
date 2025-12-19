package com.macstadium.orka;

import com.intellij.openapi.diagnostic.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
    private static final String IMAGE_ID_SEPARATOR = "_";
    private static final String DISPLAY_NAME_FORMAT = "%s (%s)"; // profileName (vmConfig)

    @NotNull
    private final String id;
    @NotNull
    private final String displayName;
    @NotNull
    private final String vmConfigName;
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
    
    // Legacy instances from old configuration, pending graceful shutdown
    @NotNull
    private final Map<String, OrkaLegacyCloudInstance> legacyInstances = new ConcurrentHashMap<>();

    /**
     * Creates OrkaCloudImage with unique ID based on profileId and vmConfigName.
     * Image ID format: {profileId}_{vmConfigName}
     * Display name format: {profileName} ({vmConfigName})
     * This ensures uniqueness when multiple Cloud Profiles use the same VM Config.
     */
    public OrkaCloudImage(@NotNull final String profileId, @NotNull final String profileName,
            @NotNull final String vmConfigName,
            @NotNull final String namespace, @NotNull final String user,
            @NotNull final String password,
            @Nullable final String agentPoolId, int instanceLimit, @Nullable final String vmMetadata) {
        // Create unique image ID by combining profileId and vmConfigName
        this.id = profileId + IMAGE_ID_SEPARATOR + vmConfigName;
        // Display name: "profileName (vmConfig)"
        this.displayName = String.format(DISPLAY_NAME_FORMAT, profileName, vmConfigName);
        this.vmConfigName = vmConfigName;
        this.namespace = namespace;
        this.user = user;
        this.password = password;
        // Default to 0 if agentPoolId is not specified (will use default pool)
        this.agentPoolId = (agentPoolId != null && !agentPoolId.isEmpty()) ? Integer.parseInt(agentPoolId) : 0;
        this.instanceLimit = instanceLimit;
        this.vmMetadata = vmMetadata;

        LOG.info(String.format("Created OrkaCloudImage: id='%s', name='%s' (profileId='%s', profileName='%s', vmConfig='%s')",
                this.id, this.displayName, profileId, profileName, vmConfigName));
    }

    @NotNull
    public String getId() {
        return this.id;
    }

    /**
     * Returns the display name for UI in EC2 style: "vmConfig (profileId)".
     * This distinguishes between multiple Cloud Profiles using the same VM Config.
     */
    @NotNull
    public String getName() {
        return this.displayName;
    }

    /**
     * Returns the VM Config name used in Orka API calls.
     */
    @NotNull
    public String getVmConfigName() {
        return this.vmConfigName;
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

    /**
     * Returns all instances (both regular and legacy).
     * Legacy instances are included so they appear in TeamCity UI.
     */
    @NotNull
    public Collection<? extends CloudInstance> getInstances() {
        List<CloudInstance> allInstances = new ArrayList<>(this.instances.values());
        allInstances.addAll(this.legacyInstances.values());
        return Collections.unmodifiableCollection(allInstances);
    }

    /**
     * Returns only regular (non-legacy) instances.
     */
    @NotNull
    public Collection<OrkaCloudInstance> getRegularInstances() {
        return Collections.unmodifiableCollection(this.instances.values());
    }

    /**
     * Returns only legacy instances pending graceful shutdown.
     */
    @NotNull
    public Collection<OrkaLegacyCloudInstance> getLegacyInstances() {
        return Collections.unmodifiableCollection(this.legacyInstances.values());
    }

    @Nullable
    public OrkaCloudInstance findInstanceById(@NotNull final String instanceId) {
        LOG.debug("findInstanceById with instanceId: " + instanceId);
        // First check regular instances
        OrkaCloudInstance instance = this.instances.get(instanceId);
        if (instance != null) {
            return instance;
        }
        // Then check legacy instances
        return this.legacyInstances.get(instanceId);
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

    /**
     * Returns the instance limit for this image.
     */
    public int getInstanceLimit() {
        return this.instanceLimit;
    }

    /**
     * Checks if a new instance can be started.
     * Only counts regular instances against the limit, legacy instances don't block new ones.
     */
    public synchronized boolean canStartNewInstance() {
        return this.instanceLimit == OrkaConstants.UNLIMITED_INSTANCES || this.instanceLimit > this.instances.size();
    }

    /**
     * Returns the total instance count (regular + legacy) for display purposes.
     */
    public int getTotalInstanceCount() {
        return this.instances.size() + this.legacyInstances.size();
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

    /**
     * Adds a legacy instance to this image.
     * Legacy instances are displayed but pending graceful shutdown.
     */
    public void addLegacyInstance(@NotNull OrkaLegacyCloudInstance instance) {
        LOG.info(String.format("Adding legacy instance: %s (originalImageId=%s)",
                instance.getInstanceId(), instance.getOriginalImageId()));
        this.legacyInstances.put(instance.getInstanceId(), instance);
    }

    /**
     * Removes a legacy instance from this image.
     */
    public void removeLegacyInstance(@NotNull String instanceId) {
        OrkaLegacyCloudInstance removed = this.legacyInstances.remove(instanceId);
        if (removed != null) {
            LOG.info(String.format("Removed legacy instance: %s", instanceId));
        }
    }

    /**
     * Finds a legacy instance by ID.
     */
    @Nullable
    public OrkaLegacyCloudInstance findLegacyInstanceById(@NotNull String instanceId) {
        return this.legacyInstances.get(instanceId);
    }

    public void terminateInstance(String instanceId) {
        LOG.debug(String.format("Terminate instance with id: %s", instanceId));
        this.removeInstance(instanceId);
    }

    void addInstance(OrkaCloudInstance instance) {
        this.instances.put(instance.getInstanceId(), instance);
    }

    void removeInstance(String instanceId) {
        // Remove from both regular and legacy instances
        this.instances.remove(instanceId);
        this.legacyInstances.remove(instanceId);
    }

    void dispose() {
        this.instances.clear();
        this.legacyInstances.clear();
    }

    /**
     * Returns all regular instances as PersistedInstanceData for storage.
     * Called before dispose to save running instances.
     */
    @NotNull
    List<PersistedInstanceData> getInstancesForPersistence() {
        List<PersistedInstanceData> result = new ArrayList<>();
        for (OrkaCloudInstance instance : this.instances.values()) {
            if (instance.getStatus() == jetbrains.buildServer.clouds.InstanceStatus.RUNNING
                    || instance.getStatus() == jetbrains.buildServer.clouds.InstanceStatus.STARTING) {
                result.add(new PersistedInstanceData(
                        instance.getInstanceId(),
                        this.id,
                        instance.getNamespace(),
                        instance.getHost(),
                        instance.getPort()));
            }
        }
        return result;
    }
}
