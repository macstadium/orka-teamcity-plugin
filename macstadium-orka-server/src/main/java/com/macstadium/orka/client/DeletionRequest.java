package com.macstadium.orka.client;

import com.google.gson.annotations.SerializedName;

public class DeletionRequest {
    @SerializedName("orka_vm_name")
    private String vmName;

    public DeletionRequest(String vmName) {
        this.vmName = vmName;
    }
}
