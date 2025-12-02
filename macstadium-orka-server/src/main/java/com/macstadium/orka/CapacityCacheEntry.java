package com.macstadium.orka;

import com.macstadium.orka.client.CapacityInfo;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable cache entry for capacity check results.
 * Using a single immutable object ensures atomic reads/writes with volatile.
 */
public final class CapacityCacheEntry {
    private final CapacityInfo capacityInfo;
    private final long checkTime;
    private final long failureTime;
    private final String failureReason;

    private CapacityCacheEntry(@Nullable CapacityInfo capacityInfo, long checkTime,
                               long failureTime, @Nullable String failureReason) {
        this.capacityInfo = capacityInfo;
        this.checkTime = checkTime;
        this.failureTime = failureTime;
        this.failureReason = failureReason;
    }

    /**
     * Creates a new cache entry for successful capacity check.
     */
    public static CapacityCacheEntry success(CapacityInfo capacityInfo) {
        return new CapacityCacheEntry(capacityInfo, System.currentTimeMillis(), 0, null);
    }

    /**
     * Creates a new cache entry for failed capacity check.
     */
    public static CapacityCacheEntry failure(CapacityInfo capacityInfo) {
        long now = System.currentTimeMillis();
        return new CapacityCacheEntry(capacityInfo, now, now, capacityInfo.getMessage());
    }

    /**
     * Creates empty cache entry (no data).
     */
    public static CapacityCacheEntry empty() {
        return new CapacityCacheEntry(null, 0, 0, null);
    }

    /**
     * Creates a copy with cleared failure backoff.
     */
    public CapacityCacheEntry withClearedBackoff() {
        return new CapacityCacheEntry(null, 0, 0, null);
    }

    @Nullable
    public CapacityInfo getCapacityInfo() {
        return capacityInfo;
    }

    public long getCheckTime() {
        return checkTime;
    }

    public long getFailureTime() {
        return failureTime;
    }

    @Nullable
    public String getFailureReason() {
        return failureReason;
    }

    public boolean hasCapacity() {
        return capacityInfo != null && capacityInfo.hasCapacity();
    }

    public boolean isInBackoff(long backoffMs) {
        if (failureTime <= 0) {
            return false;
        }
        return (System.currentTimeMillis() - failureTime) < backoffMs;
    }

    public long getRemainingBackoffSeconds(long backoffMs) {
        if (failureTime <= 0) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - failureTime;
        return Math.max(0, (backoffMs - elapsed) / 1000);
    }

    public boolean isCacheValid(long ttlMs) {
        if (capacityInfo == null || checkTime <= 0) {
            return false;
        }
        return (System.currentTimeMillis() - checkTime) < ttlMs;
    }
}

