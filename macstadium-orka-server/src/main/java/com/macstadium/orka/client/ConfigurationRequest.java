package com.macstadium.orka.client;

import com.google.gson.annotations.SerializedName;

public class ConfigurationRequest {

    @SerializedName("orka_vm_name")
    private String vmName;

    @SerializedName("orka_image")
    private String image;

    @SerializedName("orka_base_image")
    private String baseImage;

    @SerializedName("orka_vm_config_template_name")
    private String configTemplate;

    @SerializedName("orka_cpu_core")
    private int cpuCount;

    public ConfigurationRequest(String vmName, String image, String baseImage, String configTemplate, int cpuCount) {
        this.vmName = vmName;
        this.image = image;
        this.baseImage = baseImage;
        this.configTemplate = configTemplate;
        this.cpuCount = cpuCount;
    }
}
