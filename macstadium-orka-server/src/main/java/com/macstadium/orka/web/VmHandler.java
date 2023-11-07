package com.macstadium.orka.web;

import com.intellij.openapi.diagnostic.Logger;
import com.macstadium.orka.client.OrkaClient;
import com.macstadium.orka.client.OrkaVMConfig;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jetbrains.buildServer.serverSide.crypt.RSACipher;

import org.jdom.Element;

public class VmHandler implements RequestHandler {
    private static final Logger LOG = Logger.getInstance(VmHandler.class.getName());
    private static final String ORKA_ENDPOINT = "orkaEndpoint";
    private static final String ORKA_TOKEN = "token";

    public Element handle(Map<String, String> params) {
        String endpoint = params.get(ORKA_ENDPOINT);
        String token = RSACipher.decryptWebRequestData(params.get(ORKA_TOKEN));

        LOG.debug(String.format("Get VMs with endpoint: %s", endpoint));

        List<OrkaVMConfig> vmResponse = Collections.emptyList();
        try {
            OrkaClient client = new OrkaClient(endpoint, token);
            vmResponse = client.getVMConfigs().getConfigs();
            LOG.debug(String.format("VMs size received: %s", vmResponse.size()));
        } catch (IOException e) {
            LOG.debug("Get VMs error", e);
            return new Element("vms");
        }

        Element result = new Element("vms");
        vmResponse.forEach(r -> {
            String vmName = r.getName();
            LOG.debug(String.format("VMs name: %s", vmName));
            result.addContent(new Element("vm").addContent(vmName));
        });

        return result;
    }
}
