package com.macstadium.orka.client;

public class OrkaVMConfig {
    private String name;

    private int cpu;

    private String image;

    private float memory;

    public OrkaVMConfig(String name, int cpu, String image, float memory) {
        this.name = name;
        this.cpu = cpu;
        this.image = image;
        this.memory = memory;
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

    public float getMemory() {
        return this.memory;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + cpu;
        result = prime * result + ((image == null) ? 0 : image.hashCode());
        result = prime * result + Float.floatToIntBits(memory);
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
        if (Float.floatToIntBits(memory) != Float.floatToIntBits(other.memory)) {
            return false;
        }
        return true;
    }
}
