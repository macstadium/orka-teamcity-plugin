package com.macstadium.orka;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.CloudImageParameters;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class Utils {
    public static CloudClientParameters getCloudClientParametersMock(String imageId) {
        return getCloudClientParametersMock(imageId, null);
    }

    public static CloudClientParameters getCloudClientParametersMock(String imageId, String nodeMappings) {
        final Map<String, String> params = new HashMap<String, String>();
        params.put(OrkaConstants.AGENT_DIRECTORY, "dir");
        params.put(OrkaConstants.ORKA_ENDPOINT, "endpoint");
        params.put(OrkaConstants.ORKA_USER, "user");
        params.put(OrkaConstants.ORKA_PASSWORD, "password");
        params.put(OrkaConstants.VM_NAME, imageId);
        params.put(OrkaConstants.VM_USER, "vm_user");
        params.put(OrkaConstants.VM_PASSWORD, "vm_pass");
        params.put(CloudImageParameters.AGENT_POOL_ID_FIELD, "100");
        params.put(OrkaConstants.INSTANCE_LIMIT, "100");
        params.put(OrkaConstants.NODE_MAPPINGS, nodeMappings);

        CloudClientParameters mock = mock(CloudClientParameters.class);
        when(mock.getParameter(anyString())).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                return params.get(args[0]);
            }
        });

        return mock;
    }
}