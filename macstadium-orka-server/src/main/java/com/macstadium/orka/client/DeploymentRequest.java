package com.macstadium.orka.client;

import com.google.gson.annotations.SerializedName;

public class DeploymentRequest {

    @SerializedName("orka_vm_name")
    private String vmName;

    public DeploymentRequest(String vmName) {
        this.vmName = vmName;
    }
}
