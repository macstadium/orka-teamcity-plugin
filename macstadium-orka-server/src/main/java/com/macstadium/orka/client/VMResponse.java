package com.macstadium.orka.client;

import com.google.gson.annotations.SerializedName;

import java.util.Arrays;

public class VMResponse {

    @SerializedName("virtual_machine_name")
    private String vmName;

    @SerializedName("vm_status")
    private String deploymentStatus;

    @SerializedName("cpu")
    private int cpuCount;

    @SerializedName("base_image")
    private String baseImage;

    @SerializedName("image")
    private String image;

    @SerializedName("configuration_template")
    private String configurationTemplate;

    @SerializedName("status")
    private VMInstance[] instances;

    public VMResponse(String vmName, String deploymentStatus, int cpuCount, String baseImage, String image,
            String configurationTemplate, VMInstance[] instances) {
        this.vmName = vmName;
        this.deploymentStatus = deploymentStatus;
        this.cpuCount = cpuCount;
        this.baseImage = baseImage;
        this.image = image;
        this.configurationTemplate = configurationTemplate;
        this.instances = instances;
    }

    public String getVMName() {
        return this.vmName;
    }

    public String getDeploymentStatus() {
        return this.deploymentStatus;
    }

    public int getCPUCount() {
        return this.cpuCount;
    }

    public String getBaseImage() {
        return this.baseImage;
    }

    public String getImage() {
        return this.image;
    }

    public String getConfigurationTemplate() {
        return this.configurationTemplate;
    }

    public VMInstance[] getInstances() {
        return this.instances == null ? new VMInstance[0] : this.instances.clone();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((baseImage == null) ? 0 : baseImage.hashCode());
        result = prime * result + ((configurationTemplate == null) ? 0 : configurationTemplate.hashCode());
        result = prime * result + cpuCount;
        result = prime * result + ((deploymentStatus == null) ? 0 : deploymentStatus.hashCode());
        result = prime * result + ((image == null) ? 0 : image.hashCode());
        result = prime * result + ((vmName == null) ? 0 : vmName.hashCode());
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
        VMResponse other = (VMResponse) obj;
        if (baseImage == null) {
            if (other.baseImage != null) {
                return false;
            }
        } else if (!baseImage.equals(other.baseImage)) {
            return false;
        }
        if (configurationTemplate == null) {
            if (other.configurationTemplate != null) {
                return false;
            }
        } else if (!configurationTemplate.equals(other.configurationTemplate)) {
            return false;
        }
        if (cpuCount != other.cpuCount) {
            return false;
        }
        if (deploymentStatus == null) {
            if (other.deploymentStatus != null) {
                return false;
            }
        } else if (!deploymentStatus.equals(other.deploymentStatus)) {
            return false;
        }
        if (image == null) {
            if (other.image != null) {
                return false;
            }
        } else if (!image.equals(other.image)) {
            return false;
        }
        if (!Arrays.equals(instances, other.instances)) {
            return false;
        }
        if (vmName == null) {
            if (other.vmName != null) {
                return false;
            }
        } else if (!vmName.equals(other.vmName)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "VMResponse [baseImage=" + baseImage + ", configurationTemplate=" + configurationTemplate + ", cpuCount="
                + cpuCount + ", deploymentStatus=" + deploymentStatus + ", image=" + image + ", instances="
                + Arrays.toString(instances) + ", vmName=" + vmName + "]";
    }
}
