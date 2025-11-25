package com.macstadium.orka.web;

import com.intellij.openapi.diagnostic.Logger;
import com.macstadium.orka.OrkaConstants;
import com.macstadium.orka.client.AwsEksTokenProvider;
import com.macstadium.orka.client.OrkaClient;
import com.macstadium.orka.client.OrkaVMConfig;
import com.macstadium.orka.client.StaticTokenProvider;
import com.macstadium.orka.client.TokenProvider;
import com.macstadium.orka.client.VMConfigResponse;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.crypt.RSACipher;

import org.jdom.Element;

public class VmHandler implements RequestHandler {
  private static final Logger LOG = Logger.getInstance(Loggers.CLOUD_CATEGORY_ROOT + OrkaConstants.TYPE);
  private static final String ORKA_ENDPOINT = "orkaEndpoint";
  private static final String ORKA_TOKEN = "token";
  private static final String USE_AWS_IAM = "useAwsIam";
  private static final String AWS_CLUSTER_NAME = "awsClusterName";
  private static final String AWS_REGION = "awsRegion";

  public Element handle(Map<String, String> params) {
    String endpoint = params.get(ORKA_ENDPOINT);
    boolean useAwsIam = Boolean.parseBoolean(params.get(USE_AWS_IAM));

    LOG.debug(String.format("Loading VM configs: endpoint='%s', useAwsIam=%s", endpoint, useAwsIam));

    TokenProvider tokenProvider;
    try {
      if (useAwsIam) {
        String clusterName = params.get(AWS_CLUSTER_NAME);
        String region = params.get(AWS_REGION);
        tokenProvider = new AwsEksTokenProvider(clusterName, region);
      } else {
        String token = RSACipher.decryptWebRequestData(params.get(ORKA_TOKEN));
        tokenProvider = new StaticTokenProvider(token);
      }
    } catch (Exception e) {
      LOG.error(String.format("Failed to create token provider: %s", e.getMessage()), e);
      Element result = new Element("vms");
      result.setAttribute("error", "Authentication failed: " + e.getMessage());
      return result;
    }

    List<OrkaVMConfig> vmResponse = Collections.emptyList();
    try {
      OrkaClient client = new OrkaClient(endpoint, tokenProvider);
      VMConfigResponse response = client.getVMConfigs();

      if (!response.isSuccessful()) {
        LOG.error(String.format("Orka API error: HTTP %d - %s",
            response.getHttpResponse().getCode(),
            response.getMessage()));
        Element result = new Element("vms");
        result.setAttribute("error", "API error: " + response.getMessage());
        return result;
      }

      vmResponse = response.getConfigs();
      LOG.debug(String.format("Loaded %d VM configs", vmResponse.size()));
    } catch (IOException e) {
      LOG.error(String.format("Failed to get VMs from Orka API: %s", e.getMessage()), e);
      Element result = new Element("vms");
      result.setAttribute("error", "Connection failed: " + e.getMessage());
      return result;
    }

    Element result = new Element("vms");
    vmResponse.forEach(r -> result.addContent(new Element("vm").addContent(r.getName())));

    return result;
  }
}
