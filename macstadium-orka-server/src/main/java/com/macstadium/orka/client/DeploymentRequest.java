package com.macstadium.orka.client;

import java.util.Map;

public class DeploymentRequest {
    private String vmConfig;
    private String userdata;
    private Map<String, String> customMetadata;

    public DeploymentRequest(String vmConfig) {
        this(vmConfig, null, null);
    }

    public DeploymentRequest(String vmConfig, String userdata, Map<String, String> customMetadata) {
        this.vmConfig = vmConfig;
        this.userdata = userdata;
        this.customMetadata = customMetadata;
    }
}
