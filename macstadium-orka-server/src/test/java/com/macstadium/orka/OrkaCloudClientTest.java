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
import com.macstadium.orka.client.VMResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.serverSide.AgentDescription;

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
