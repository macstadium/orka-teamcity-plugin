package com.macstadium.orka.client;

import com.google.gson.annotations.SerializedName;

public class DeploymentResponse extends ResponseBase {
  private String ip;

  @SerializedName("ssh_port")
  private int sshPort;

  private String name;

  public DeploymentResponse(String ip, int sshPort, String name, String message) {
    super(message);
    this.ip = ip;
    this.sshPort = sshPort;
    this.name = name;
  }

  public String getIP() {
    return this.ip;
  }

  public int getSSH() {
    return this.sshPort;
  }

  public String getName() {
    return this.name;
  }

  @Override
  public String toString() {
    return "DeploymentResponse [Name=" + name + ", IP=" + ip + ", SSH=" + sshPort + "]";
  }
}
