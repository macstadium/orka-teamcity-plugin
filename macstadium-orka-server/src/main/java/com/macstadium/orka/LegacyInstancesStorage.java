package com.macstadium.orka;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.log.Loggers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.NotNull;

/**
 * Singleton storage for legacy instances that need graceful shutdown.
 * When CloudClient is disposed (due to settings change), running instances
 * are stored here. The new CloudClient retrieves them and marks for graceful shutdown.
 * 
 * This is an in-memory storage that survives profile reloads but not server restarts.
 * For full persistence across server restarts, CloudState should be used.
 */
public class LegacyInstancesStorage {
    private static final Logger LOG = Logger.getInstance(Loggers.CLOUD_CATEGORY_ROOT + OrkaConstants.TYPE);

    private static final LegacyInstancesStorage INSTANCE = new LegacyInstancesStorage();

    // Map: profileId -> list of persisted instance data
    private final Map<String, List<PersistedInstanceData>> storage = new ConcurrentHashMap<>();

    private LegacyInstancesStorage() {
    }

    public static LegacyInstancesStorage getInstance() {
        return INSTANCE;
    }

    /**
     * Stores instances for a profile. Called when CloudClient is disposed.
     * Overwrites any previously stored instances for this profile.
     * 
     * @param profileId the profile ID
     * @param instances list of instances to store
     */
    public void store(@NotNull String profileId, @NotNull List<PersistedInstanceData> instances) {
        if (instances.isEmpty()) {
            storage.remove(profileId);
            LOG.debug(String.format("[%s] No instances to store in LegacyInstancesStorage", profileId));
        } else {
            storage.put(profileId, new ArrayList<>(instances));
            LOG.info(String.format("[%s] Stored %d legacy instance(s) in LegacyInstancesStorage",
                    profileId, instances.size()));
            for (PersistedInstanceData data : instances) {
                LOG.debug(String.format("[%s] Stored legacy instance: %s", profileId, data));
            }
        }
    }

    /**
     * Retrieves and removes stored instances for a profile.
     * Called when new CloudClient is created to recover legacy instances.
     * 
     * @param profileId the profile ID
     * @return list of stored instances (empty if none), instances are removed from storage
     */
    @NotNull
    public List<PersistedInstanceData> retrieveAndClear(@NotNull String profileId) {
        List<PersistedInstanceData> instances = storage.remove(profileId);
        if (instances != null && !instances.isEmpty()) {
            LOG.info(String.format("[%s] Retrieved %d legacy instance(s) from LegacyInstancesStorage",
                    profileId, instances.size()));
            return instances;
        }
        return Collections.emptyList();
    }

    /**
     * Checks if there are stored instances for a profile.
     */
    public boolean hasInstances(@NotNull String profileId) {
        List<PersistedInstanceData> instances = storage.get(profileId);
        return instances != null && !instances.isEmpty();
    }

    /**
     * Gets the count of stored instances for a profile (for debugging).
     */
    public int getInstanceCount(@NotNull String profileId) {
        List<PersistedInstanceData> instances = storage.get(profileId);
        return instances != null ? instances.size() : 0;
    }

    /**
     * Clears all stored instances (for testing).
     */
    public void clear() {
        storage.clear();
    }
}

