package com.macstadium.orka.client;

import com.google.gson.annotations.SerializedName;

import java.util.HashMap;
import java.util.Map;

public class DeploymentRequest {
  private String name;
  private String vmConfig;

  @SerializedName("customMetadata")
  private Map<String, String> customMetadata;

  public DeploymentRequest(String name, String vmConfig) {
    this.name = name;
    this.vmConfig = vmConfig;
  }

  public DeploymentRequest(String name, String vmConfig, String vmMetadataString) {
    this.name = name;
    this.vmConfig = vmConfig;
    this.customMetadata = parseMetadata(vmMetadataString);
  }

  public String getName() {
    return name;
  }

  public String getVmConfig() {
    return vmConfig;
  }

  public Map<String, String> getCustomMetadata() {
    return customMetadata;
  }

  public void setCustomMetadata(Map<String, String> customMetadata) {
    this.customMetadata = customMetadata;
  }

  /**
   * Parses metadata string in format "key1=value1,key2=value2" into a Map
   */
  private Map<String, String> parseMetadata(String metadataString) {
    Map<String, String> metadata = new HashMap<>();
    if (metadataString != null && !metadataString.trim().isEmpty()) {
      String[] pairs = metadataString.split(",");
      for (String pair : pairs) {
        String[] keyValue = pair.trim().split("=", 2);
        if (keyValue.length == 2) {
          metadata.put(keyValue[0].trim(), keyValue[1].trim());
        }
      }
    }
    return metadata.isEmpty() ? null : metadata;
  }
}
