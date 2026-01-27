package com.macstadium.orka;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
  private static final String TEST_PROFILE_ID = "test-profile";
  private static final String TEST_PROFILE_NAME = "Test Profile";

  // Test that host is never null after instance creation
  public void when_instance_created_host_should_not_be_null() throws IOException {
    String instanceId = "id";
    String vmConfigName = "orka-image";
    OrkaCloudInstance instance = this.getInstance(instanceId, vmConfigName);

    // host should be initialized (not null) even before setHost is called
    assertNotNull("Host should not be null after instance creation", instance.getHost());
  }

  public void when_instance_created_and_host_set_should_return_host() throws IOException {
    String instanceId = "id";
    String vmConfigName = "orka-image";
    String expectedHost = "10.10.10.1";
    OrkaCloudInstance instance = this.getInstance(instanceId, vmConfigName);

    instance.setHost(expectedHost);
    assertEquals(expectedHost, instance.getHost());
  }

  public void when_instance_created_port_should_be_zero() throws IOException {
    String instanceId = "id";
    String vmConfigName = "orka-image";
    OrkaCloudInstance instance = this.getInstance(instanceId, vmConfigName);

    // port should be 0 (default) before setPort is called
    assertEquals(0, instance.getPort());
  }

  public void when_contains_agent_with_correct_values_should_return_true() throws IOException {
    String instanceId = "id";
    String vmConfigName = "orka-image";
    String fullImageId = getFullImageId(vmConfigName);
    AgentDescription agentDescriptionMock = this.getAgentDescriptionMock(instanceId, fullImageId);
    OrkaCloudInstance instance = this.getInstance(instanceId, vmConfigName);

    assertTrue(instance.containsAgent(agentDescriptionMock));
  }

  public void when_contains_agent_with_wrong_instance_id_should_return_false() throws IOException {
    String instanceId = "id";
    String vmConfigName = "orka-image";
    String fullImageId = getFullImageId(vmConfigName);
    AgentDescription agentDescriptionMock = this.getAgentDescriptionMock("wrong-id", fullImageId);
    OrkaCloudInstance instance = this.getInstance(instanceId, vmConfigName);

    assertFalse(instance.containsAgent(agentDescriptionMock));
  }

  public void when_contains_agent_with_wrong_image_id_should_return_false() throws IOException {
    String instanceId = "id";
    String vmConfigName = "orka-image";
    AgentDescription agentDescriptionMock = this.getAgentDescriptionMock(instanceId, "wrong-id");
    OrkaCloudInstance instance = this.getInstance(instanceId, vmConfigName);

    assertFalse(instance.containsAgent(agentDescriptionMock));
  }

  // Agent connection tracking tests

  public void when_instance_created_agent_should_not_be_connected() throws IOException {
    String instanceId = "id";
    String vmConfigName = "orka-image";
    OrkaCloudInstance instance = this.getInstance(instanceId, vmConfigName);

    assertFalse("Agent should not be marked as connected initially", instance.hasAgentConnected());
  }

  public void when_agent_matches_should_mark_agent_connected() throws IOException {
    String instanceId = "id";
    String vmConfigName = "orka-image";
    String fullImageId = getFullImageId(vmConfigName);
    AgentDescription agentDescriptionMock = this.getAgentDescriptionMock(instanceId, fullImageId);
    OrkaCloudInstance instance = this.getInstance(instanceId, vmConfigName);

    assertFalse("Agent should not be connected before containsAgent call", instance.hasAgentConnected());

    boolean matches = instance.containsAgent(agentDescriptionMock);

    assertTrue("containsAgent should return true for matching agent", matches);
    assertTrue("Agent should be marked as connected after matching", instance.hasAgentConnected());
  }

  public void when_agent_does_not_match_should_not_mark_agent_connected() throws IOException {
    String instanceId = "id";
    String vmConfigName = "orka-image";
    AgentDescription agentDescriptionMock = this.getAgentDescriptionMock("wrong-id", "wrong-image");
    OrkaCloudInstance instance = this.getInstance(instanceId, vmConfigName);

    boolean matches = instance.containsAgent(agentDescriptionMock);

    assertFalse("containsAgent should return false for non-matching agent", matches);
    assertFalse("Agent should not be marked as connected when no match", instance.hasAgentConnected());
  }

  public void when_mark_agent_connected_called_should_return_true() throws IOException {
    String instanceId = "id";
    String vmConfigName = "orka-image";
    OrkaCloudInstance instance = this.getInstance(instanceId, vmConfigName);

    assertFalse(instance.hasAgentConnected());

    instance.markAgentConnected();

    assertTrue("hasAgentConnected should return true after markAgentConnected", instance.hasAgentConnected());
  }

  private OrkaCloudInstance getInstance(String instanceId, String vmConfigName) {
    OrkaCloudImage image = new OrkaCloudImage(TEST_PROFILE_ID, TEST_PROFILE_NAME, vmConfigName, "orka-default", "user", "password", "0", 0, null);
    return new OrkaCloudInstance(image, instanceId, "orka-default");
  }

  private String getFullImageId(String vmConfigName) {
    return TEST_PROFILE_ID + "_" + vmConfigName;
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
