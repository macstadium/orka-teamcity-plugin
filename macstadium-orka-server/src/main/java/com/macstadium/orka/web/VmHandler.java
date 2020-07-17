package com.macstadium.orka.web;

import com.intellij.openapi.diagnostic.Logger;
import com.macstadium.orka.client.OrkaClient;
import com.macstadium.orka.client.VMResponse;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jetbrains.buildServer.serverSide.crypt.RSACipher;

import org.jdom.Element;

public class VmHandler implements RequestHandler {
    private static final Logger LOG = Logger.getInstance(VmHandler.class.getName());
    private static final String ORKA_ENDPOINT = "orkaEndpoint";
    private static final String ORKA_USER = "orkaUser";
    private static final String ORKA_PASSWORD = "orkaPassword";

    public Element handle(Map<String, String> params) {
        String endpoint = params.get(ORKA_ENDPOINT);
        String user = params.get(ORKA_USER);
        String password = RSACipher.decryptWebRequestData(params.get(ORKA_PASSWORD));

        LOG.debug(String.format("Get VMs with endpoint: %s, and user email: %s", endpoint, user));

        List<VMResponse> vmResponse = Collections.emptyList();
        try (OrkaClient client = new OrkaClient(endpoint, user, password)) {
            vmResponse = client.getVMs();
            LOG.debug(String.format("VMs size received: %s", vmResponse.size()));
        } catch (IOException e) {
            LOG.debug("Get VMs error", e);
            return new Element("vms");
        }

        Element result = new Element("vms");
        vmResponse.forEach(r -> {
            String vmName = r.getVMName();
            LOG.debug(String.format("VMs name: %s", vmName));
            result.addContent(new Element("vm").addContent(vmName));
        });

        return result;
    }
}