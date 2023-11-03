package com.macstadium.orka.client;

import com.google.gson.annotations.SerializedName;

public class DeploymentRequest {
    private String vmConfig;

    public DeploymentRequest(String vmConfig) {
        this.vmConfig = vmConfig;
    }
}
