package com.macstadium.orka;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import jetbrains.buildServer.clouds.CloudRegistrar;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.web.openapi.PluginDescriptor;

import org.testng.annotations.Test;

@Test
public class OrkaCloudClientFactoryTest {
    public void when_can_be_agent_of_type_with_correct_values_should_return_true() throws IOException {
        Map<String, String> params = new HashMap<String, String>();
        params.put(OrkaConstants.INSTANCE_ID_PARAM_NAME, "instanceId");
        params.put(OrkaConstants.IMAGE_ID_PARAM_NAME, "imageId");

        OrkaCloudClientFactory factory = new OrkaCloudClientFactory(mock(CloudRegistrar.class),
                mock(PluginDescriptor.class), mock(ExecutorServices.class));

        AgentDescription agentDescription = mock(AgentDescription.class);
        when(agentDescription.getConfigurationParameters()).thenReturn(params);
        assertTrue(factory.canBeAgentOfType(agentDescription));
    }

    public void when_can_be_agent_of_type_with_missing_image_id_should_return_false() throws IOException {
        OrkaCloudClientFactory factory = new OrkaCloudClientFactory(mock(CloudRegistrar.class),
                mock(PluginDescriptor.class), mock(ExecutorServices.class));
        Map<String, String> params = new HashMap<String, String>();
        params.put(OrkaConstants.INSTANCE_ID_PARAM_NAME, "instanceId");

        AgentDescription agentDescription = mock(AgentDescription.class);
        when(agentDescription.getConfigurationParameters()).thenReturn(params);
        assertFalse(factory.canBeAgentOfType(agentDescription));
    }

    public void when_can_be_agent_of_type_with_missing_instance_id_should_return_false() throws IOException {
        OrkaCloudClientFactory factory = new OrkaCloudClientFactory(mock(CloudRegistrar.class),
                mock(PluginDescriptor.class), mock(ExecutorServices.class));
        Map<String, String> params = new HashMap<String, String>();
        params.put(OrkaConstants.IMAGE_ID_PARAM_NAME, "imageId");

        AgentDescription agentDescription = mock(AgentDescription.class);
        when(agentDescription.getConfigurationParameters()).thenReturn(params);
        assertFalse(factory.canBeAgentOfType(agentDescription));
    }

    public void when_can_be_agent_of_type_with_missing_values_should_return_false() throws IOException {
        OrkaCloudClientFactory factory = new OrkaCloudClientFactory(mock(CloudRegistrar.class),
                mock(PluginDescriptor.class), mock(ExecutorServices.class));
        Map<String, String> params = new HashMap<String, String>();

        AgentDescription agentDescription = mock(AgentDescription.class);
        when(agentDescription.getConfigurationParameters()).thenReturn(params);
        assertFalse(factory.canBeAgentOfType(agentDescription));
    }
}