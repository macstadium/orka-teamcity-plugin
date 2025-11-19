package com.macstadium.orka;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import jetbrains.buildServer.serverSide.AgentDescription;

import org.testng.annotations.Test;

@Test
public class OrkaCloudInstanceTest {
  public void when_contains_agent_with_correct_values_should_return_true() throws IOException {
    String instanceId = "id";
    String imageId = "orka-image";
    AgentDescription agentDescriptionMock = this.getAgentDescriptionMock(instanceId, imageId);
    OrkaCloudInstance instance = this.getInstance(instanceId, imageId);

    assertTrue(instance.containsAgent(agentDescriptionMock));
  }

  public void when_contains_agent_with_wrong_instance_id_should_return_false() throws IOException {
    String instanceId = "id";
    String imageId = "orka-image";
    AgentDescription agentDescriptionMock = this.getAgentDescriptionMock("wrong-id", imageId);
    OrkaCloudInstance instance = this.getInstance(instanceId, imageId);

    assertFalse(instance.containsAgent(agentDescriptionMock));
  }

  public void when_contains_agent_with_wrong_image_id_should_return_false() throws IOException {
    String instanceId = "id";
    String imageId = "orka-image";
    AgentDescription agentDescriptionMock = this.getAgentDescriptionMock(instanceId, "wrong-id");
    OrkaCloudInstance instance = this.getInstance(instanceId, imageId);

    assertFalse(instance.containsAgent(agentDescriptionMock));
  }

  private OrkaCloudInstance getInstance(String instanceId, String imageId) {
    OrkaCloudImage image = new OrkaCloudImage(imageId, "orka-default", "user", "password", "0", 0, null);
    return new OrkaCloudInstance(image, instanceId, "orka-default");
  }

  private AgentDescription getAgentDescriptionMock(String instanceId, String imageId) {
    Map<String, String> params = new HashMap<String, String>();
    params.put(OrkaConstants.INSTANCE_ID_PARAM_NAME, instanceId);
    params.put(OrkaConstants.IMAGE_ID_PARAM_NAME, imageId);

    AgentDescription mock = mock(AgentDescription.class);
    when(mock.getConfigurationParameters()).thenReturn(params);

    return mock;
  }
}
