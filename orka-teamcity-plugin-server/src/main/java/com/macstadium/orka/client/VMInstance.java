package com.macstadium.orka.client;

import com.google.gson.annotations.SerializedName;

public class VMInstance {
    @SerializedName("virtual_machine_id")
    private String id;

    @SerializedName("virtual_machine_ip")
    private String host;

    @SerializedName("ssh_port")
    private String sshPort;

    private String image;

    public VMInstance(String id, String host, String sshPort, String image) {
        this.id = id;
        this.host = host;
        this.sshPort = sshPort;
        this.image = image;
    }

    public String getId() {
        return this.id;
    }

    public String getHost() {
        return this.host;
    }

    public String getImage() {
        return this.image;
    }

    public String getSSHPort() {
        return this.sshPort;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((host == null) ? 0 : host.hashCode());
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((image == null) ? 0 : image.hashCode());
        result = prime * result + ((sshPort == null) ? 0 : image.hashCode());;
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
        VMInstance other = (VMInstance) obj;
        if (host == null) {
            if (other.host != null) {
                return false;
            }
        } else if (!host.equals(other.host)) {
            return false;
        }
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!id.equals(other.id)) {
            return false;
        }
        if (image == null) {
            if (other.image != null) {
                return false;
            }
        } else if (!image.equals(other.image)) {
            return false;
        }
        if (sshPort == null) {
            if (other.sshPort != null) {
                return false;
            }
        } else if (!sshPort.equals(other.sshPort)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "VMInstance [host=" + host + ", id=" + id + ", image=" + image + ", sshPort=" + sshPort + "]";
    }
}
