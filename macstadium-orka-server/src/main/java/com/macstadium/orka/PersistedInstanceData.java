package com.macstadium.orka;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Data class for storing instance information for persistence.
 * Used to recover instances after profile settings change or server restart.
 */
public class PersistedInstanceData {
    @NotNull
    private final String instanceId;
    @NotNull
    private final String imageId;
    @NotNull
    private final String namespace;
    @Nullable
    private final String host;
    private final int port;
    private final boolean isLegacy;
    @Nullable
    private final String originalImageId;

    public PersistedInstanceData(@NotNull String instanceId, @NotNull String imageId,
            @NotNull String namespace, @Nullable String host, int port) {
        this(instanceId, imageId, namespace, host, port, false, null);
    }

    public PersistedInstanceData(@NotNull String instanceId, @NotNull String imageId,
            @NotNull String namespace, @Nullable String host, int port,
            boolean isLegacy, @Nullable String originalImageId) {
        this.instanceId = instanceId;
        this.imageId = imageId;
        this.namespace = namespace;
        this.host = host;
        this.port = port;
        this.isLegacy = isLegacy;
        this.originalImageId = originalImageId;
    }

    @NotNull
    public String getInstanceId() {
        return instanceId;
    }

    @NotNull
    public String getImageId() {
        return imageId;
    }

    @NotNull
    public String getNamespace() {
        return namespace;
    }

    @Nullable
    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isLegacy() {
        return isLegacy;
    }

    @Nullable
    public String getOriginalImageId() {
        return originalImageId;
    }

    /**
     * Creates a legacy copy of this instance data with the given new image ID.
     * The original image ID is preserved for agent matching.
     */
    public PersistedInstanceData asLegacy(@NotNull String newImageId) {
        return new PersistedInstanceData(
                this.instanceId,
                newImageId,
                this.namespace,
                this.host,
                this.port,
                true,
                this.imageId // Original imageId becomes originalImageId
        );
    }

    /**
     * Serializes this instance data to a string for storage.
     * Format: instanceId|imageId|namespace|host|port|isLegacy|originalImageId
     */
    public String serialize() {
        return String.format("%s|%s|%s|%s|%d|%b|%s",
                instanceId,
                imageId,
                namespace,
                host != null ? host : "",
                port,
                isLegacy,
                originalImageId != null ? originalImageId : "");
    }

    /**
     * Deserializes instance data from a string.
     * 
     * @param data serialized string
     * @return PersistedInstanceData or null if parsing fails
     */
    @Nullable
    public static PersistedInstanceData deserialize(@Nullable String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        String[] parts = data.split("\\|", -1); // -1 to keep empty strings
        if (parts.length < 5) {
            return null;
        }

        try {
            String instanceId = parts[0];
            String imageId = parts[1];
            String namespace = parts[2];
            String host = parts[3].isEmpty() ? null : parts[3];
            int port = Integer.parseInt(parts[4]);
            boolean isLegacy = parts.length > 5 && Boolean.parseBoolean(parts[5]);
            String originalImageId = parts.length > 6 && !parts[6].isEmpty() ? parts[6] : null;

            return new PersistedInstanceData(instanceId, imageId, namespace, host, port, isLegacy, originalImageId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return String.format("PersistedInstanceData{id='%s', image='%s', ns='%s', host='%s', port=%d, legacy=%b, origImage='%s'}",
                instanceId, imageId, namespace, host, port, isLegacy, originalImageId);
    }
}

