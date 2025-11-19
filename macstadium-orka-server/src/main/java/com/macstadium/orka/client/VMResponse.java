package com.macstadium.orka.client;

import com.google.gson.annotations.SerializedName;

public class VMResponse extends ResponseBase {
  private String name;

  @SerializedName("ssh_port")
  private int sshPort;

  private String ip;

  public VMResponse(String name, int sshPort, String ip, String message) {
    super(message);
    this.name = name;
    this.sshPort = sshPort;
    this.ip = ip;
  }

  public String getName() {
    return this.name;
  }

  public int getSSH() {
    return this.sshPort;
  }

  public String getIP() {
    return this.ip;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((name == null) ? 0 : name.hashCode());
    result = prime * result + sshPort;
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
    if (sshPort != other.sshPort) {
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
