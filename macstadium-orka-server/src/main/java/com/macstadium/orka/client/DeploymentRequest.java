package com.macstadium.orka.client;

import java.util.Map;

public class DeploymentRequest {
    private String vmConfig;
    private Map<String, String> customMetadata;

    public DeploymentRequest(String vmConfig, Map<String, String> customMetadata) {
        this.vmConfig = vmConfig;
        this.customMetadata = customMetadata;
    }
}
