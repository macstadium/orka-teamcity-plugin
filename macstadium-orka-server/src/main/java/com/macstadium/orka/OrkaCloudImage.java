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
