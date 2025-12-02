package com.macstadium.orka.client;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

/**
 * Represents a running VM in Orka.
 * Used for capacity checking and instance recovery.
 */
public class OrkaVM {
    private String name;
    private String node;
    private String ip;
    private int cpu;
    private String memory;
    private String status;
    private String type;
    private int ssh;

    @SerializedName("customMetadata")
    private Map<String, String> metadata;

    public OrkaVM() {
    }

    public OrkaVM(String name, String node, String status, String type) {
        this.name = name;
        this.node = node;
        this.status = status;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getNode() {
        return node;
    }

    public String getIp() {
        return ip;
    }

    public int getCpu() {
        return cpu;
    }

    public String getMemory() {
        return memory;
    }

    public String getStatus() {
        return status;
    }

    public String getType() {
        return type;
    }

    public int getSsh() {
        return ssh;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public boolean isRunning() {
        return "Running".equalsIgnoreCase(status);
    }

    public boolean isArm() {
        return "arm64".equalsIgnoreCase(type);
    }

    @Override
    public String toString() {
        return String.format("OrkaVM{name='%s', node='%s', status='%s', ip='%s', ssh=%d}",
            name, node, status, ip, ssh);
    }
}

