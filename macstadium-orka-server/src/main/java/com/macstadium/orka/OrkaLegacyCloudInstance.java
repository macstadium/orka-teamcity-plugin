package com.macstadium.orka;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.AgentDescription;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Represents a legacy cloud instance that was created with old configuration.
 * Legacy instances are displayed in TeamCity but marked for graceful shutdown.
 * They don't accept new builds and are terminated after current builds complete.
 */
public class OrkaLegacyCloudInstance extends OrkaCloudInstance {
    private static final Logger LOG = Logger.getInstance(Loggers.CLOUD_CATEGORY_ROOT + OrkaConstants.TYPE);

    // The original image ID from old configuration (for agent matching)
    @Nullable
    private final String originalImageId;

    // Flag indicating this instance is pending graceful shutdown
    private volatile boolean pendingGracefulShutdown;

    public OrkaLegacyCloudInstance(@NotNull OrkaCloudImage image, @NotNull String instanceId,
            @NotNull String namespace, @Nullable String originalImageId) {
        super(image, instanceId, namespace);
        this.originalImageId = originalImageId;
        this.pendingGracefulShutdown = true;
        LOG.info(String.format("Created legacy instance: %s (originalImageId=%s)", instanceId, originalImageId));
    }

    /**
     * Returns true - this is a legacy instance.
     */
    public boolean isLegacy() {
        return true;
    }

    /**
     * Returns the original image ID from old configuration.
     * Used for matching agents that report old image ID.
     */
    @Nullable
    public String getOriginalImageId() {
        return originalImageId;
    }

    /**
     * Returns true if this instance is pending graceful shutdown.
     */
    public boolean isPendingGracefulShutdown() {
        return pendingGracefulShutdown;
    }

    /**
     * Sets whether this instance is pending graceful shutdown.
     */
    public void setPendingGracefulShutdown(boolean pending) {
        this.pendingGracefulShutdown = pending;
    }

    /**
     * Checks if this instance contains the given agent.
     * Overridden to also match agents reporting original image ID.
     */
    @Override
    public boolean containsAgent(@NotNull AgentDescription agentDescription) {
        Map<String, String> configParams = agentDescription.getConfigurationParameters();
        String agentInstanceId = configParams.get(OrkaConstants.INSTANCE_ID_PARAM_NAME);
        String agentImageId = configParams.get(OrkaConstants.IMAGE_ID_PARAM_NAME);

        // First try standard matching
        if (super.containsAgent(agentDescription)) {
            return true;
        }

        // For legacy instances, also try matching with original image ID
        if (originalImageId != null && getInstanceId().equals(agentInstanceId)
                && originalImageId.equals(agentImageId)) {
            LOG.debug(String.format("Legacy agent match: VM %s matched via originalImageId=%s",
                    getInstanceId(), originalImageId));
            return true;
        }

        return false;
    }

    @Override
    public String toString() {
        return String.format("OrkaLegacyCloudInstance{id='%s', status=%s, originalImageId='%s', pendingShutdown=%b}",
                getInstanceId(), getStatus(), originalImageId, pendingGracefulShutdown);
    }
}

