package com.macstadium.orka.client;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OrkaNode {
    private String name;

    @SerializedName("ip")
    private String nodeIP;

    private int allocatableCpu;

    private int availableCpu;

    private String allocatableMemory;

    private String availableMemory;

    private String phase;

    @SerializedName("orkaTags")
    private List<String> tags;

    public OrkaNode(String name, String nodeIP, int allocatableCpu, int availableCpu, String allocatableMemory,
            String availableMemory, String phase) {
        this(name, nodeIP, allocatableCpu, availableCpu, allocatableMemory, availableMemory, phase, null);
    }

    public OrkaNode(String name, String nodeIP, int allocatableCpu, int availableCpu, String allocatableMemory,
            String availableMemory, String phase, List<String> tags) {
        this.name = name;
        this.nodeIP = nodeIP;
        this.allocatableCpu = allocatableCpu;
        this.availableCpu = availableCpu;
        this.allocatableMemory = allocatableMemory;
        this.availableMemory = availableMemory;
        this.phase = phase;
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
    }

    public String getName() {
        return this.name;
    }

    public String getNodeIP() {
        return this.nodeIP;
    }

    public int getAllocatableCpu() {
        return this.allocatableCpu;
    }

    public int getAvailableCpu() {
        return this.availableCpu;
    }

    public String getAllocatableMemory() {
        return this.allocatableMemory;
    }

    public String getAvailableMemory() {
        return this.availableMemory;
    }

    /**
     * Returns available memory as float in GB.
     * Parses strings like "128.00G" or "28.00G" to float.
     */
    public float getAvailableMemoryAsFloat() {
        return MemoryUtils.parseMemoryToGb(this.availableMemory);
    }

    public String getPhase() {
        return this.phase;
    }

    public List<String> getTags() {
        return this.tags != null ? Collections.unmodifiableList(this.tags) : Collections.emptyList();
    }

    /**
     * Checks if node has a specific tag.
     */
    public boolean hasTag(String tag) {
        return this.tags != null && this.tags.contains(tag);
    }

    /**
     * Checks if node is ready (status == "READY" or phase == "Ready").
     */
    public boolean isReady() {
        return "READY".equalsIgnoreCase(this.phase) || "Ready".equalsIgnoreCase(this.phase);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((nodeIP == null) ? 0 : nodeIP.hashCode());
        result = prime * result + availableCpu;
        result = prime * result + ((availableMemory == null) ? 0 : availableMemory.hashCode());
        result = prime * result + ((phase == null) ? 0 : phase.hashCode());
        result = prime * result + allocatableCpu;
        result = prime * result + ((allocatableMemory == null) ? 0 : allocatableMemory.hashCode());
        result = prime * result + ((tags == null) ? 0 : tags.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        OrkaNode other = (OrkaNode) obj;
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        if (nodeIP == null) {
            if (other.nodeIP != null) {
                return false;
            }
        } else if (!nodeIP.equals(other.nodeIP)) {
            return false;
        }
        if (availableCpu != other.availableCpu) {
            return false;
        }
        if (availableMemory == null) {
            if (other.availableMemory != null) {
                return false;
            }
        } else if (!availableMemory.equals(other.availableMemory)) {
            return false;
        }
        if (phase == null) {
            if (other.phase != null) {
                return false;
            }
        } else if (!phase.equals(other.phase)) {
            return false;
        }
        if (allocatableCpu != other.allocatableCpu) {
            return false;
        }
        if (allocatableMemory == null) {
            if (other.allocatableMemory != null) {
                return false;
            }
        } else if (!allocatableMemory.equals(other.allocatableMemory)) {
            return false;
        }
        if (tags == null) {
            if (other.tags != null) {
                return false;
            }
        } else if (!tags.equals(other.tags)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return String.format("OrkaNode[name=%s, ip=%s, cpu=%d/%d, memory=%s/%s, status=%s, tags=%s]",
                name, nodeIP, availableCpu, allocatableCpu, availableMemory, allocatableMemory, phase, tags);
    }
}
