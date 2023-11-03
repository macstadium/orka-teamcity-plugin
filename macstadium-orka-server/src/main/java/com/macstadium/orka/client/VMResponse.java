package com.macstadium.orka.client;

public class VMResponse extends ResponseBase {
    private String name;

    private int ssh;

    private String ip;

    public VMResponse(String name, int ssh, String ip, String message) {
        super(message);
        this.name = name;
        this.ssh = ssh;
        this.ip = ip;
    }

    public String getName() {
        return this.name;
    }

    public int getSSH() {
        return this.ssh;
    }

    public String getIP() {
        return this.ip;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ssh;
        result = prime * result + ((ip == null) ? 0 : ip.hashCode());
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
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        if (ssh != other.ssh) {
            return false;
        }
        if (ip == null) {
            if (other.ip != null) {
                return false;
            }
        } else if (!ip.equals(other.ip)) {
            return false;
        }
        return true;
    }
}
