package com.macstadium.orka.client;

import com.google.gson.annotations.SerializedName;

public class OrkaVMConfig {
    private String name;

    private int cpu;

    private String image;

    private String memory;

    private String tag;

    @SerializedName("tagRequired")
    private boolean tagRequired;

    private String nodeName;

    public OrkaVMConfig(String name, int cpu, String image, String memory) {
        this(name, cpu, image, memory, null, false, null);
    }

    public OrkaVMConfig(String name, int cpu, String image, String memory, String tag, boolean tagRequired) {
        this(name, cpu, image, memory, tag, tagRequired, null);
    }

    public OrkaVMConfig(String name, int cpu, String image, String memory, String tag, boolean tagRequired,
            String nodeName) {
        this.name = name;
        this.cpu = cpu;
        this.image = image;
        this.memory = memory;
        this.tag = tag;
        this.tagRequired = tagRequired;
        this.nodeName = nodeName;
    }

    public String getName() {
        return this.name;
    }

    public int getCPU() {
        return this.cpu;
    }

    public String getImage() {
        return this.image;
    }

    public String getMemory() {
        return this.memory;
    }

    /**
     * Returns memory as float in GB.
     * Parses string like "30" or "30G" to float.
     */
    public float getMemoryAsFloat() {
        return MemoryUtils.parseMemoryToGb(this.memory);
    }

    public String getTag() {
        return this.tag;
    }

    public boolean isTagRequired() {
        return this.tagRequired;
    }

    public String getNodeName() {
        return this.nodeName;
    }

    /**
     * Returns true if VM config is pinned to a specific node.
     */
    public boolean isPinnedToNode() {
        return this.nodeName != null && !this.nodeName.isEmpty();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + cpu;
        result = prime * result + ((image == null) ? 0 : image.hashCode());
        result = prime * result + ((memory == null) ? 0 : memory.hashCode());
        result = prime * result + ((tag == null) ? 0 : tag.hashCode());
        result = prime * result + (tagRequired ? 1231 : 1237);
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
        OrkaVMConfig other = (OrkaVMConfig) obj;
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        if (cpu != other.cpu) {
            return false;
        }
        if (image == null) {
            if (other.image != null) {
                return false;
            }
        } else if (!image.equals(other.image)) {
            return false;
        }
        if (memory == null) {
            if (other.memory != null) {
                return false;
            }
        } else if (!memory.equals(other.memory)) {
            return false;
        }
        if (tag == null) {
            if (other.tag != null) {
                return false;
            }
        } else if (!tag.equals(other.tag)) {
            return false;
        }
        if (tagRequired != other.tagRequired) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return String.format("OrkaVMConfig[name=%s, cpu=%d, memory=%s, tag=%s, tagRequired=%s]",
                name, cpu, memory, tag, tagRequired);
    }
}
