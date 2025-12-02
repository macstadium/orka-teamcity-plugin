package com.macstadium.orka.client;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

/**
 * Response from Orka API for list of VMs.
 */
public class VMsResponse extends ResponseBase {
    @SerializedName("items")
    private List<OrkaVM> vms;

    public VMsResponse() {
        super(null);
    }

    public VMsResponse(List<OrkaVM> vms, String message) {
        super(message);
        this.vms = vms;
    }

    public List<OrkaVM> getVMs() {
        return this.vms != null ? Collections.unmodifiableList(this.vms) : Collections.emptyList();
    }
}

