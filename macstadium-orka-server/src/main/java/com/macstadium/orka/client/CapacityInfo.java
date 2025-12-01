package com.macstadium.orka.client;

/**
 * Holds capacity information from Orka cluster.
 * Used to determine if there are enough resources to deploy a new VM.
 */
public class CapacityInfo {
    private final int totalAvailableCpu;
    private final long totalAvailableMemoryMb;
    private final int nodeCount;
    private final int readyNodeCount;
    private final boolean hasCapacity;
    private final String message;

    public CapacityInfo(int totalAvailableCpu, long totalAvailableMemoryMb, 
                        int nodeCount, int readyNodeCount, boolean hasCapacity, String message) {
        this.totalAvailableCpu = totalAvailableCpu;
        this.totalAvailableMemoryMb = totalAvailableMemoryMb;
        this.nodeCount = nodeCount;
        this.readyNodeCount = readyNodeCount;
        this.hasCapacity = hasCapacity;
        this.message = message;
    }

    /**
     * Creates a CapacityInfo indicating no capacity available.
     */
    public static CapacityInfo noCapacity(String reason) {
        return new CapacityInfo(0, 0, 0, 0, false, reason);
    }

    /**
     * Creates a CapacityInfo indicating capacity check failed.
     */
    public static CapacityInfo checkFailed(String error) {
        return new CapacityInfo(0, 0, 0, 0, false, "Capacity check failed: " + error);
    }

    public int getTotalAvailableCpu() {
        return totalAvailableCpu;
    }

    public long getTotalAvailableMemoryMb() {
        return totalAvailableMemoryMb;
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public int getReadyNodeCount() {
        return readyNodeCount;
    }

    public boolean hasCapacity() {
        return hasCapacity;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return String.format("CapacityInfo[hasCapacity=%s, availableCpu=%d, availableMemoryMb=%d, " +
                "nodes=%d/%d ready, message='%s']",
                hasCapacity, totalAvailableCpu, totalAvailableMemoryMb, 
                readyNodeCount, nodeCount, message);
    }
}


