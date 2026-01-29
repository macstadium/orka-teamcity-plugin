package com.macstadium.orka;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.macstadium.orka.client.CapacityInfo;
import com.macstadium.orka.client.DeletionResponse;
import com.macstadium.orka.client.DeploymentResponse;
import com.macstadium.orka.client.HttpResponse;
import com.macstadium.orka.client.OrkaClient;
import com.macstadium.orka.client.OrkaVM;
import com.macstadium.orka.client.VMResponse;
import com.macstadium.orka.client.VMsResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.serverSide.AgentDescription;

import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.Test;

@Test
public class OrkaCloudClientTest {
  public void when_find_image_by_id_with_correct_id_should_return_image() throws IOException {
    String vmConfigName = "imageId";
    String fullImageId = Utils.getFullImageId(vmConfigName);
    String displayName = Utils.getDisplayName(vmConfigName);
    OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName),
        mock(OrkaClient.class), mock(ScheduledExecutorService.class), mock(RemoteAgent.class),
        mock(SSHUtil.class));

    OrkaCloudImage image = client.findImageById(fullImageId);
    assertNotNull(image);
    assertEquals(fullImageId, image.getId());
    assertEquals(displayName, image.getName()); // getName() returns "vmConfig (profileId)"
    assertEquals(vmConfigName, image.getVmConfigName());
  }

  public void when_find_image_by_id_with_wrong_id_should_return_null() throws IOException {
    String vmConfigName = "imageId";
    String anotherId = "anotherId";
    OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName),
        mock(OrkaClient.class), mock(ScheduledExecutorService.class), mock(RemoteAgent.class),
        mock(SSHUtil.class));

    OrkaCloudImage image = client.findImageById(anotherId);
    assertNull(image);
  }

  public void when_find_instance_by_agent_with_correct_ids_should_return_instance() throws IOException {
    String vmConfigName = "imageId";
    String fullImageId = Utils.getFullImageId(vmConfigName);
    String instanceId = "instanceId";
    OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName),
        mock(OrkaClient.class), mock(ScheduledExecutorService.class), mock(RemoteAgent.class),
        mock(SSHUtil.class));

    OrkaCloudImage image = client.findImageById(fullImageId);
    assertNotNull("Image should be found", image);
    image.startNewInstance(instanceId);

    AgentDescription agentDescription = this.getAgentDescriptionMock(instanceId, fullImageId);

    OrkaCloudInstance instance = client.findInstanceByAgent(agentDescription);
    assertNotNull(instance);
  }

  public void when_find_instance_by_agent_with_wrong_image_id_should_return_null() throws IOException {
    String vmConfigName = "imageId";
    String instanceId = "instanceId";
    OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName),
        mock(OrkaClient.class), mock(ScheduledExecutorService.class), mock(RemoteAgent.class),
        mock(SSHUtil.class));

    AgentDescription agentDescription = this.getAgentDescriptionMock(instanceId, "wrong-image-id");

    OrkaCloudInstance instance = client.findInstanceByAgent(agentDescription);
    assertNull(instance);
  }

  public void when_find_instance_by_agent_with_no_instance_id_should_return_null() throws IOException {
    String vmConfigName = "imageId";
    String fullImageId = Utils.getFullImageId(vmConfigName);
    OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName),
        mock(OrkaClient.class), mock(ScheduledExecutorService.class), mock(RemoteAgent.class),
        mock(SSHUtil.class));

    AgentDescription agentDescription = this.getAgentDescriptionMock(null, fullImageId);

    OrkaCloudInstance instance = client.findInstanceByAgent(agentDescription);
    assertNull(instance);
  }

  @Test
  public void when_find_instance_by_agent_with_correct_ids_no_instance_and_existing_orka_vm_should_create_instance()
      throws IOException {
    String vmConfigName = "imageId";
    String fullImageId = Utils.getFullImageId(vmConfigName);
    String instanceId = "instanceId";

    OrkaClient orkaClient = mock(OrkaClient.class);
    VMResponse vmInstance = new VMResponse(instanceId, 22, "host", null);
    vmInstance.setHttpResponse(new HttpResponse("instanceId", 200, true));
    when(orkaClient.getVM(any(), any())).thenReturn(vmInstance);

    OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName), orkaClient,
        mock(ScheduledExecutorService.class), mock(RemoteAgent.class), mock(SSHUtil.class));

    AgentDescription agentDescription = this.getAgentDescriptionMock(instanceId, fullImageId);
    OrkaCloudInstance instance = client.findInstanceByAgent(agentDescription);
    assertNotNull(instance);
    assertEquals(instanceId, instance.getInstanceId());
    assertEquals(fullImageId, instance.getImageId());
  }

  public void when_start_new_instance_should_return_new_instance() throws IOException, InterruptedException {
    String vmConfigName = "imageId";
    String fullImageId = Utils.getFullImageId(vmConfigName);
    String instanceId = "instanceId";
    String host = "10.10.10.1";
    int sshPort = 8822;

    OrkaClient orkaClient = this.getOrkaClientMock(host, sshPort, instanceId);
    RemoteAgent remoteAgentMock = mock(RemoteAgent.class);
    SSHUtil sshUtilMock = mock(SSHUtil.class);

    OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName), orkaClient,
        this.getScheduledExecutorService(), remoteAgentMock, sshUtilMock);

    OrkaCloudInstance instance = (OrkaCloudInstance) client.startNewInstance(this.getImage(client), null);

    assertEquals(instanceId, instance.getInstanceId());
    assertEquals(fullImageId, instance.getImageId());
    assertEquals(host, instance.getHost());
    assertEquals(sshPort, instance.getPort());
    assertEquals(InstanceStatus.RUNNING, instance.getStatus());
  }

  public void when_start_new_instance_with_failing_vm_should_terminate_instance()
      throws IOException, InterruptedException {
    String vmConfigName = "imageId";
    String instanceId = "instanceId";
    String host = "10.10.10.1";
    int sshPort = 8822;

    OrkaClient orkaClient = this.getOrkaClientMock(host, sshPort, instanceId);
    SSHUtil sshUtilMock = mock(SSHUtil.class);
    when(sshUtilMock.waitForSSH(anyString(), anyInt(), anyInt(), anyInt())).thenThrow(new IOException("Error"));

    OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName), orkaClient,
        this.getScheduledExecutorService(), mock(RemoteAgent.class), sshUtilMock);

    client.startNewInstance(this.getImage(client), null);

    assertEquals(0, this.getImage(client).getInstances().size());
  }

  public void when_start_new_instance_with_failing_deploy_should_terminate_instance()
      throws IOException, InterruptedException {
    String vmConfigName = "imageId";
    String instanceId = "instanceId";
    String host = "10.10.10.1";
    int sshPort = 8822;

    OrkaClient orkaClient = this.getOrkaClientMock(host, sshPort, instanceId);
    when(orkaClient.deployVM(any(), any(), any())).thenThrow(new IOException("Error"));
    when(orkaClient.deployVM(any(), any(), any(), any())).thenThrow(new IOException("Error"));
    OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName), orkaClient,
        this.getScheduledExecutorService(), mock(RemoteAgent.class), mock(SSHUtil.class));

    client.startNewInstance(this.getImage(client), null);

    assertEquals(0, this.getImage(client).getInstances().size());
  }

  public void when_terminate_instance_should_return_remove_instance() throws IOException {
    String vmConfigName = "imageId";

    OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName),
        this.getOrkaClientMock("host", 22, "instanceId"), this.getScheduledExecutorService(),
        mock(RemoteAgent.class), mock(SSHUtil.class));

    OrkaCloudInstance instance = (OrkaCloudInstance) client.startNewInstance(this.getImage(client), null);

    assertEquals(1, this.getImage(client).getInstances().size());

    client.terminateInstance(instance);

    assertEquals(0, this.getImage(client).getInstances().size());
  }

  public void when_terminate_instance_throws_should_mark_instance() throws IOException {
    String vmConfigName = "imageId";
    OrkaClient orkaClient = this.getOrkaClientMock("host", 22, "instanceId");
    when(orkaClient.deleteVM(any(), any())).thenThrow(new IOException("Error"));
    OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName), orkaClient,
        this.getScheduledExecutorService(), mock(RemoteAgent.class), mock(SSHUtil.class));

    OrkaCloudInstance instance = (OrkaCloudInstance) client.startNewInstance(this.getImage(client), null);

    assertEquals(1, this.getImage(client).getInstances().size());

    client.terminateInstance(instance);

    assertEquals(1, this.getImage(client).getInstances().size());
    assertTrue("Instance marked for termination", instance.isMarkedForTermination());
  }

  public void when_terminate_instance_and_delete_vm_returns_error_should_mark_instance() throws IOException {
    String vmConfigName = "imageId";
    OrkaClient orkaClient = this.getOrkaClientMock("host", 22, "instanceId");
    DeletionResponse deletionResponse = new DeletionResponse("Error");
    deletionResponse.setHttpResponse(new HttpResponse("imageId", 400, false));
    when(orkaClient.deleteVM(any(), any())).thenReturn(deletionResponse);
    OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName), orkaClient,
        this.getScheduledExecutorService(), mock(RemoteAgent.class), mock(SSHUtil.class));

    OrkaCloudInstance instance = (OrkaCloudInstance) client.startNewInstance(this.getImage(client), null);

    assertEquals(1, this.getImage(client).getInstances().size());

    client.terminateInstance(instance);

    assertEquals(1, this.getImage(client).getInstances().size());
    assertTrue("Instance marked for termination", instance.isMarkedForTermination());
  }

  public void when_private_host_is_overridden_should_connect_to_public_host() throws IOException {
    String vmConfigName = "imageId";
    String privateHost = "10.10.10.4";
    String publicHost = "100.100.100.4";
    String nodeMappings = String.format("10.10.10.3;100.100.100.3\r%s;%s\r10.10.10.5;100.100.100.5", privateHost,
        publicHost);

    OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName, nodeMappings),
        this.getOrkaClientMock(privateHost, 22, "instanceId"), this.getScheduledExecutorService(),
        mock(RemoteAgent.class), mock(SSHUtil.class));

    OrkaCloudInstance instance = (OrkaCloudInstance) client.startNewInstance(this.getImage(client), null);

    assertEquals(publicHost, instance.getHost());
  }

  public void when_private_host_is_not_overridden_should_connect_to_private_host() throws IOException {
    String vmConfigName = "imageId";
    String privateHost = "10.10.10.4";
    String nodeMappings = "10.10.10.3;100.100.100.3\r10.10.10.5;100.100.100.5";

    OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName, nodeMappings),
        this.getOrkaClientMock(privateHost, 22, "instanceId"), this.getScheduledExecutorService(),
        mock(RemoteAgent.class), mock(SSHUtil.class));

    OrkaCloudInstance instance = (OrkaCloudInstance) client.startNewInstance(this.getImage(client), null);

    assertEquals(privateHost, instance.getHost());
  }

  // Tests for malformed node mappings - should not crash
  public void when_node_mappings_malformed_without_semicolon_should_not_crash() throws IOException {
    String vmConfigName = "imageId";
    String privateHost = "10.10.10.4";
    // Malformed mapping without semicolon
    String nodeMappings = "10.10.10.3;100.100.100.3\rmalformed_no_semicolon\r10.10.10.5;100.100.100.5";

    // This should not throw ArrayIndexOutOfBoundsException
    OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName, nodeMappings),
        this.getOrkaClientMock(privateHost, 22, "instanceId"), this.getScheduledExecutorService(),
        mock(RemoteAgent.class), mock(SSHUtil.class));

    OrkaCloudInstance instance = (OrkaCloudInstance) client.startNewInstance(this.getImage(client), null);

    // Should still work with valid mappings
    assertEquals(privateHost, instance.getHost());
  }

  public void when_node_mappings_empty_entries_should_not_crash() throws IOException {
    String vmConfigName = "imageId";
    String privateHost = "10.10.10.4";
    // Mappings with empty entries
    String nodeMappings = "10.10.10.3;100.100.100.3\r\r\r10.10.10.5;100.100.100.5";

    OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName, nodeMappings),
        this.getOrkaClientMock(privateHost, 22, "instanceId"), this.getScheduledExecutorService(),
        mock(RemoteAgent.class), mock(SSHUtil.class));

    OrkaCloudInstance instance = (OrkaCloudInstance) client.startNewInstance(this.getImage(client), null);
    assertNotNull(instance);
  }

  public void when_node_mappings_only_semicolon_should_not_crash() throws IOException {
    String vmConfigName = "imageId";
    String privateHost = "10.10.10.4";
    // Mapping with only semicolon (empty key and value)
    String nodeMappings = ";\r10.10.10.5;100.100.100.5";

    OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName, nodeMappings),
        this.getOrkaClientMock(privateHost, 22, "instanceId"), this.getScheduledExecutorService(),
        mock(RemoteAgent.class), mock(SSHUtil.class));

    OrkaCloudInstance instance = (OrkaCloudInstance) client.startNewInstance(this.getImage(client), null);
    assertNotNull(instance);
  }

  // Tests for location extraction from node name
  public void when_vm_not_found_in_vms_list_should_not_crash() throws IOException {
    String vmConfigName = "imageId";
    String privateHost = "10.10.10.4";

    OrkaClient orkaClient = this.getOrkaClientMock(privateHost, 22, "instanceId");
    // Override getVMs to return empty list - VM not found scenario
    VMsResponse emptyResponse = new VMsResponse(java.util.Collections.emptyList(), null);
    emptyResponse.setHttpResponse(new HttpResponse("vms", 200, true));
    when(orkaClient.getVMs(any())).thenReturn(emptyResponse);

    OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName),
        orkaClient, this.getScheduledExecutorService(), mock(RemoteAgent.class), mock(SSHUtil.class));

    OrkaCloudInstance instance = (OrkaCloudInstance) client.startNewInstance(this.getImage(client), null);
    assertNotNull(instance);
  }

  public void when_getVMs_throws_exception_should_not_crash() throws IOException {
    String vmConfigName = "imageId";
    String privateHost = "10.10.10.4";

    OrkaClient orkaClient = this.getOrkaClientMock(privateHost, 22, "instanceId");
    // Override getVMs to throw IOException
    when(orkaClient.getVMs(any())).thenThrow(new IOException("API error"));

    OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName),
        orkaClient, this.getScheduledExecutorService(), mock(RemoteAgent.class), mock(SSHUtil.class));

    OrkaCloudInstance instance = (OrkaCloudInstance) client.startNewInstance(this.getImage(client), null);
    assertNotNull(instance);
  }

  public void when_node_name_has_no_hyphen_should_use_full_name_as_location() throws IOException {
    String vmConfigName = "imageId";
    String privateHost = "10.10.10.4";
    String instanceId = "instanceId";

    OrkaClient orkaClient = this.getOrkaClientMock(privateHost, 22, instanceId);
    // Node name without hyphen
    OrkaVM orkaVM = new OrkaVM(instanceId, "localhost", "Running", "arm64");
    VMsResponse vmsResponse = new VMsResponse(java.util.Arrays.asList(orkaVM), null);
    vmsResponse.setHttpResponse(new HttpResponse("vms", 200, true));
    when(orkaClient.getVMs(any())).thenReturn(vmsResponse);

    OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName),
        orkaClient, this.getScheduledExecutorService(), mock(RemoteAgent.class), mock(SSHUtil.class));

    OrkaCloudInstance instance = (OrkaCloudInstance) client.startNewInstance(this.getImage(client), null);
    assertNotNull(instance);
  }

  public void when_node_name_is_null_should_not_crash() throws IOException {
    String vmConfigName = "imageId";
    String privateHost = "10.10.10.4";
    String instanceId = "instanceId";

    OrkaClient orkaClient = this.getOrkaClientMock(privateHost, 22, instanceId);
    // Node name is null
    OrkaVM orkaVM = new OrkaVM(instanceId, null, "Running", "arm64");
    VMsResponse vmsResponse = new VMsResponse(java.util.Arrays.asList(orkaVM), null);
    vmsResponse.setHttpResponse(new HttpResponse("vms", 200, true));
    when(orkaClient.getVMs(any())).thenReturn(vmsResponse);

    OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName),
        orkaClient, this.getScheduledExecutorService(), mock(RemoteAgent.class), mock(SSHUtil.class));

    OrkaCloudInstance instance = (OrkaCloudInstance) client.startNewInstance(this.getImage(client), null);
    assertNotNull(instance);
  }

  // Tests for agent connection timeout (orphan VM detection)

  /**
   * Test that agent connection check is scheduled after VM setup.
   */
  public void when_vm_started_should_schedule_agent_connection_check() throws IOException {
    String vmConfigName = "imageId";
    String privateHost = "10.10.10.4";

    OrkaClient orkaClient = this.getOrkaClientMock(privateHost, 22, "instanceId");
    ScheduledExecutorService scheduledExecutor = mock(ScheduledExecutorService.class);

    // Capture the scheduled task
    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    ArgumentCaptor<Long> delayCaptor = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<TimeUnit> timeUnitCaptor = ArgumentCaptor.forClass(TimeUnit.class);

    when(scheduledExecutor.submit(any(Runnable.class))).thenAnswer(new Answer<Future<?>>() {
      @Override
      public Future<?> answer(InvocationOnMock invocation) throws Throwable {
        Runnable r = (Runnable) invocation.getArguments()[0];
        r.run();
        return CompletableFuture.completedFuture(null);
      }
    });
    when(scheduledExecutor.schedule(runnableCaptor.capture(), delayCaptor.capture(), timeUnitCaptor.capture()))
        .thenReturn(mock(ScheduledFuture.class));

    OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName),
        orkaClient, scheduledExecutor, mock(RemoteAgent.class), mock(SSHUtil.class));

    client.startNewInstance(this.getImage(client), null);

    // Verify schedule was called with 20 minutes delay
    assertEquals(Long.valueOf(20), delayCaptor.getValue());
    assertEquals(TimeUnit.MINUTES, timeUnitCaptor.getValue());
  }

  /**
   * Test that when agent has connected, the timeout check does not mark instance for termination.
   */
  public void when_agent_connected_before_timeout_should_not_mark_for_termination() throws IOException {
    String vmConfigName = "imageId";
    String fullImageId = Utils.getFullImageId(vmConfigName);
    String privateHost = "10.10.10.4";
    String instanceId = "instanceId";

    OrkaClient orkaClient = this.getOrkaClientMock(privateHost, 22, instanceId);

    // Capture the scheduled Runnable so we can execute it manually
    final Runnable[] capturedRunnable = new Runnable[1];
    ScheduledExecutorService scheduledExecutor = mock(ScheduledExecutorService.class);
    when(scheduledExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
      Runnable r = (Runnable) invocation.getArguments()[0];
      r.run();
      return CompletableFuture.completedFuture(null);
    });
    when(scheduledExecutor.schedule(any(Runnable.class), any(Long.class), any(TimeUnit.class)))
        .thenAnswer(invocation -> {
          capturedRunnable[0] = (Runnable) invocation.getArguments()[0];
          return mock(ScheduledFuture.class);
        });

    OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName),
        orkaClient, scheduledExecutor, mock(RemoteAgent.class), mock(SSHUtil.class));

    OrkaCloudInstance instance = (OrkaCloudInstance) client.startNewInstance(this.getImage(client), null);
    assertNotNull(instance);
    assertEquals(InstanceStatus.RUNNING, instance.getStatus());

    // Simulate agent connection via containsAgent callback
    AgentDescription agentDescription = this.getAgentDescriptionMock(instanceId, fullImageId);
    assertTrue("Agent should match", instance.containsAgent(agentDescription));
    assertTrue("Agent should be marked as connected", instance.hasAgentConnected());

    // Now run the timeout check
    assertNotNull("Scheduled runnable should be captured", capturedRunnable[0]);
    capturedRunnable[0].run();

    // Instance should still be RUNNING, not marked for termination
    assertEquals(InstanceStatus.RUNNING, instance.getStatus());
    assertTrue("Instance should NOT be marked for termination", !instance.isMarkedForTermination());
  }

  /**
   * Test that when agent has NOT connected within timeout, instance is marked for termination.
   */
  public void when_agent_not_connected_after_timeout_should_mark_for_termination() throws IOException {
    String vmConfigName = "imageId";
    String privateHost = "10.10.10.4";
    String instanceId = "instanceId";

    OrkaClient orkaClient = this.getOrkaClientMock(privateHost, 22, instanceId);

    // Capture the scheduled Runnable
    final Runnable[] capturedRunnable = new Runnable[1];
    ScheduledExecutorService scheduledExecutor = mock(ScheduledExecutorService.class);
    when(scheduledExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
      Runnable r = (Runnable) invocation.getArguments()[0];
      r.run();
      return CompletableFuture.completedFuture(null);
    });
    when(scheduledExecutor.schedule(any(Runnable.class), any(Long.class), any(TimeUnit.class)))
        .thenAnswer(invocation -> {
          capturedRunnable[0] = (Runnable) invocation.getArguments()[0];
          return mock(ScheduledFuture.class);
        });

    OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName),
        orkaClient, scheduledExecutor, mock(RemoteAgent.class), mock(SSHUtil.class));

    OrkaCloudInstance instance = (OrkaCloudInstance) client.startNewInstance(this.getImage(client), null);
    assertNotNull(instance);
    assertEquals(InstanceStatus.RUNNING, instance.getStatus());

    // Do NOT simulate agent connection - agent never connected

    // Run the timeout check
    assertNotNull("Scheduled runnable should be captured", capturedRunnable[0]);
    capturedRunnable[0].run();

    // Instance should be marked for termination with ERROR status
    assertEquals(InstanceStatus.ERROR, instance.getStatus());
    assertTrue("Instance should be marked for termination", instance.isMarkedForTermination());
    assertNotNull("Error info should be set", instance.getErrorInfo());
    assertTrue("Error message should mention timeout",
        instance.getErrorInfo().getMessage().contains("timeout"));
  }

  /**
   * Test that when instance is already terminated before timeout, check is skipped.
   */
  public void when_instance_terminated_before_timeout_should_skip_check() throws IOException {
    String vmConfigName = "imageId";
    String privateHost = "10.10.10.4";
    String instanceId = "instanceId";

    OrkaClient orkaClient = this.getOrkaClientMock(privateHost, 22, instanceId);

    // Capture the scheduled Runnable
    final Runnable[] capturedRunnable = new Runnable[1];
    ScheduledExecutorService scheduledExecutor = mock(ScheduledExecutorService.class);
    when(scheduledExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
      Runnable r = (Runnable) invocation.getArguments()[0];
      r.run();
      return CompletableFuture.completedFuture(null);
    });
    when(scheduledExecutor.schedule(any(Runnable.class), any(Long.class), any(TimeUnit.class)))
        .thenAnswer(invocation -> {
          capturedRunnable[0] = (Runnable) invocation.getArguments()[0];
          return mock(ScheduledFuture.class);
        });

    OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName),
        orkaClient, scheduledExecutor, mock(RemoteAgent.class), mock(SSHUtil.class));

    OrkaCloudInstance instance = (OrkaCloudInstance) client.startNewInstance(this.getImage(client), null);
    assertNotNull(instance);

    // Terminate the instance before timeout fires
    client.terminateInstance(instance);

    // Run the timeout check
    assertNotNull("Scheduled runnable should be captured", capturedRunnable[0]);
    capturedRunnable[0].run();

    // Check should be skipped - instance already terminated, no double-marking
    // The instance was already removed from image, so check should bail out early
  }

  /**
   * Test that when instance is already marked for termination, timeout check is skipped.
   */
  public void when_instance_already_marked_for_termination_should_skip_check() throws IOException {
    String vmConfigName = "imageId";
    String privateHost = "10.10.10.4";
    String instanceId = "instanceId";

    OrkaClient orkaClient = this.getOrkaClientMock(privateHost, 22, instanceId);

    // Capture the scheduled Runnable
    final Runnable[] capturedRunnable = new Runnable[1];
    ScheduledExecutorService scheduledExecutor = mock(ScheduledExecutorService.class);
    when(scheduledExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
      Runnable r = (Runnable) invocation.getArguments()[0];
      r.run();
      return CompletableFuture.completedFuture(null);
    });
    when(scheduledExecutor.schedule(any(Runnable.class), any(Long.class), any(TimeUnit.class)))
        .thenAnswer(invocation -> {
          capturedRunnable[0] = (Runnable) invocation.getArguments()[0];
          return mock(ScheduledFuture.class);
        });

    OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName),
        orkaClient, scheduledExecutor, mock(RemoteAgent.class), mock(SSHUtil.class));

    OrkaCloudInstance instance = (OrkaCloudInstance) client.startNewInstance(this.getImage(client), null);
    assertNotNull(instance);

    // Pre-mark instance for termination (simulating another path marked it)
    instance.setMarkedForTermination(true);

    // Run the timeout check
    assertNotNull("Scheduled runnable should be captured", capturedRunnable[0]);
    capturedRunnable[0].run();

    // Status should remain RUNNING (not changed to ERROR)
    assertEquals(InstanceStatus.RUNNING, instance.getStatus());
  }

  private CloudImage getImage(OrkaCloudClient client) {
    return client.getImages().stream().findFirst().get();
  }

  private AgentDescription getAgentDescriptionMock(String instanceId, String imageId) {
    Map<String, String> params = new HashMap<String, String>();
    params.put(OrkaConstants.INSTANCE_ID_PARAM_NAME, instanceId);
    params.put(OrkaConstants.IMAGE_ID_PARAM_NAME, imageId);

    AgentDescription mock = mock(AgentDescription.class);
    when(mock.getConfigurationParameters()).thenReturn(params);

    return mock;
  }

    private OrkaClient getOrkaClientMock(String host, int sshPort, String instanceId) throws IOException {
        OrkaClient orkaClient = mock(OrkaClient.class);
        DeploymentResponse deploymentResponse = new DeploymentResponse(host, sshPort, instanceId,
                null);
        deploymentResponse.setHttpResponse(new HttpResponse("instanceId", 200, true));
        when(orkaClient.deployVM(any(), any(), any())).thenReturn(deploymentResponse);
        when(orkaClient.deployVM(any(), any(), any(), any())).thenReturn(deploymentResponse);
        DeletionResponse deletionResponse = new DeletionResponse("Success");
        deletionResponse.setHttpResponse(new HttpResponse("instanceId", 200, true));
        when(orkaClient.deleteVM(any(), any())).thenReturn(deletionResponse);
        VMResponse vmResponse = new VMResponse(instanceId, sshPort, host, null);
        vmResponse.setHttpResponse(new HttpResponse("instanceId", 200, true));
        when(orkaClient.getVM(any(), any())).thenReturn(vmResponse);
        // Mock checkCapacity to return success by default
        CapacityInfo capacityInfo = new CapacityInfo(24, 128000, 5, 5, true, "Capacity available");
        when(orkaClient.checkCapacity(any(), any())).thenReturn(capacityInfo);
        // Mock getVMs for location extraction - return VM with node name
        OrkaVM orkaVM = new OrkaVM(instanceId, "dub-h-tc-mac-78", "Running", "arm64");
        VMsResponse vmsResponse = new VMsResponse(java.util.Arrays.asList(orkaVM), null);
        vmsResponse.setHttpResponse(new HttpResponse("vms", 200, true));
        when(orkaClient.getVMs(any())).thenReturn(vmsResponse);
        return orkaClient;
    }

    private ScheduledExecutorService getScheduledExecutorService() {
        ScheduledExecutorService scheduledExecutorService = mock(ScheduledExecutorService.class);
        when(scheduledExecutorService.submit(any(Runnable.class))).thenAnswer(new Answer<Future<?>>() {
            @Override
            public Future<?> answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                Runnable r = (Runnable) args[0];
                r.run();
                return CompletableFuture.completedFuture(null);
            }
        });

        return scheduledExecutorService;
    }
}
