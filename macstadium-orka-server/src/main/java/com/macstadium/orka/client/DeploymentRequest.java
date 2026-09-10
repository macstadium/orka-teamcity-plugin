package com.macstadium.orka.client;

public class DeploymentRequest {
    private String vmConfig;
    private String userdata;

    public DeploymentRequest(String vmConfig) {
        this(vmConfig, null);
    }

    public DeploymentRequest(String vmConfig, String userdata) {
        this.vmConfig = vmConfig;
        this.userdata = userdata;
    }
}
