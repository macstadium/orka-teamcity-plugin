package com.macstadium.orka;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.macstadium.orka.client.DeploymentResponse;
import com.macstadium.orka.client.OrkaClient;
import com.macstadium.orka.client.OrkaError;
import com.macstadium.orka.client.VMInstance;
import com.macstadium.orka.client.VMResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import javax.swing.border.StrokeBorder;

import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudImageParameters;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.serverSide.AgentDescription;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.Test;

@Test
public class OrkaCloudClientTest {
    public void when_find_image_by_id_with_correct_id_should_return_image() throws IOException {
        String imageId = "imageId";
        OrkaCloudClient client = new OrkaCloudClient(this.getCloudClientParametersMock(imageId), mock(OrkaClient.class),
                mock(AsyncExecutor.class), mock(RemoteAgent.class), mock(SSHUtil.class));

        OrkaCloudImage image = client.findImageById(imageId);
        assertNotNull(image);
    }

    public void when_find_image_by_id_with_wrong_id_should_return_null() throws IOException {
        String imageId = "imageId";
        String anotherId = "anotherId";
        OrkaCloudClient client = new OrkaCloudClient(this.getCloudClientParametersMock(imageId), mock(OrkaClient.class),
                mock(AsyncExecutor.class), mock(RemoteAgent.class), mock(SSHUtil.class));

        OrkaCloudImage image = client.findImageById(anotherId);
        assertNull(image);
    }

    public void when_find_instance_by_agent_with_correct_ids_should_return_instance() throws IOException {
        String imageId = "imageId";
        String instanceId = "instanceId";
        OrkaCloudClient client = new OrkaCloudClient(this.getCloudClientParametersMock(imageId), mock(OrkaClient.class),
                mock(AsyncExecutor.class), mock(RemoteAgent.class), mock(SSHUtil.class));

        OrkaCloudImage image = client.findImageById(imageId);
        image.startNewInstance(instanceId);

        AgentDescription agentDescription = this.getAgentDescriptionMock(instanceId, imageId);

        OrkaCloudInstance instance = client.findInstanceByAgent(agentDescription);
        assertNotNull(instance);
    }

    public void when_find_instance_by_agent_with_wrong_image_id_should_return_null() throws IOException {
        String imageId = "imageId";
        String instanceId = "instanceId";
        OrkaCloudClient client = new OrkaCloudClient(this.getCloudClientParametersMock(imageId), mock(OrkaClient.class),
                mock(AsyncExecutor.class), mock(RemoteAgent.class), mock(SSHUtil.class));

        AgentDescription agentDescription = this.getAgentDescriptionMock(instanceId, "wrong-image-id");

        OrkaCloudInstance instance = client.findInstanceByAgent(agentDescription);
        assertNull(instance);
    }

    public void when_find_instance_by_agent_with_no_instance_id_should_return_null() throws IOException {
        String imageId = "imageId";
        OrkaCloudClient client = new OrkaCloudClient(this.getCloudClientParametersMock(imageId), mock(OrkaClient.class),
                mock(AsyncExecutor.class), mock(RemoteAgent.class), mock(SSHUtil.class));

        AgentDescription agentDescription = this.getAgentDescriptionMock(null, imageId);

        OrkaCloudInstance instance = client.findInstanceByAgent(agentDescription);
        assertNull(instance);
    }

    public void when_find_instance_by_agent_with_correct_ids_no_instance_and_existing_orka_vm_should_create_instance()
            throws IOException {
        String imageId = "imageId";
        String instanceId = "instanceId";

        OrkaClient orkaClient = mock(OrkaClient.class);
        VMInstance vmInstance = new VMInstance(instanceId, "host", "22", imageId);
        VMResponse response = new VMResponse("first", "deployed", 12, "Mojave.img", "firstImage", "default",
                new VMInstance[] { vmInstance });
        when(orkaClient.getVM(anyString())).thenReturn(response);

        OrkaCloudClient client = new OrkaCloudClient(this.getCloudClientParametersMock(imageId), orkaClient,
                mock(AsyncExecutor.class), mock(RemoteAgent.class), mock(SSHUtil.class));

        AgentDescription agentDescription = this.getAgentDescriptionMock(instanceId, imageId);

        OrkaCloudInstance instance = client.findInstanceByAgent(agentDescription);
        assertNotNull(instance);
        assertEquals(instanceId, instance.getInstanceId());
        assertEquals(imageId, instance.getImageId());
    }

    public void when_start_new_instance_should_return_new_instance() throws IOException {
        String imageId = "imageId";
        String instanceId = "instanceId";
        String host = "10.10.10.1";
        int sshPort = 8822;

        OrkaClient orkaClient = this.getOrkaClientMock(host, sshPort, instanceId);

        OrkaCloudClient client = new OrkaCloudClient(this.getCloudClientParametersMock(imageId), orkaClient,
                this.getAsyncExecutorMock(), mock(RemoteAgent.class), mock(SSHUtil.class));

        OrkaCloudInstance instance = (OrkaCloudInstance) client.startNewInstance(this.getImage(client), null);

        assertEquals(instanceId, instance.getInstanceId());
        assertEquals(imageId, instance.getImageId());
        assertEquals(host, instance.getHost());
        assertEquals(sshPort, instance.getPort());
        assertEquals(InstanceStatus.RUNNING, instance.getStatus());
    }

    public void when_start_new_instance_with_failing_vm_should_terminate_instance() throws IOException,
            InterruptedException {
        String imageId = "imageId";
        String instanceId = "instanceId";
        String host = "10.10.10.1";
        int sshPort = 8822;

        OrkaClient orkaClient = this.getOrkaClientMock(host, sshPort, instanceId);
        SSHUtil sshUtilMock = mock(SSHUtil.class);
        when(sshUtilMock.waitForSSH(anyString(), anyInt(), anyInt(), anyInt())).thenThrow(new IOException("Error"));

        OrkaCloudClient client = new OrkaCloudClient(this.getCloudClientParametersMock(imageId), orkaClient,
                this.getAsyncExecutorMock(), mock(RemoteAgent.class), sshUtilMock);

        client.startNewInstance(this.getImage(client), null);

        assertEquals(0, this.getImage(client).getInstances().size());
    }

    public void when_terminate_instance_should_return_remove_instance() throws IOException {
        String imageId = "imageId";

        OrkaCloudClient client = new OrkaCloudClient(this.getCloudClientParametersMock(imageId),
                this.getOrkaClientMock("host", 22, "instanceId"), this.getAsyncExecutorMock(), mock(RemoteAgent.class),
                mock(SSHUtil.class));

        OrkaCloudInstance instance = (OrkaCloudInstance) client.startNewInstance(this.getImage(client), null);

        assertEquals(1, this.getImage(client).getInstances().size());

        client.terminateInstance(instance);

        assertEquals(0, this.getImage(client).getInstances().size());
    }

    public void when_private_host_is_overridden_should_connect_to_public_host() throws IOException {
        String imageId = "imageId";
        String privateHost = "10.10.10.4";
        String publicHost = "100.100.100.4";
        String nodeMappings = String.format("10.10.10.3;100.100.100.3\r%s;%s\r10.10.10.5;100.100.100.5", privateHost,
                publicHost);

        OrkaCloudClient client = new OrkaCloudClient(this.getCloudClientParametersMock(imageId, nodeMappings),
                this.getOrkaClientMock(privateHost, 22, "instanceId"), this.getAsyncExecutorMock(),
                mock(RemoteAgent.class), mock(SSHUtil.class));

        OrkaCloudInstance instance = (OrkaCloudInstance) client.startNewInstance(this.getImage(client), null);

        assertEquals(publicHost, instance.getHost());
    }

    public void when_private_host_is_not_overridden_should_connect_to_private_host() throws IOException {
        String imageId = "imageId";
        String privateHost = "10.10.10.4";
        String nodeMappings = "10.10.10.3;100.100.100.3\r10.10.10.5;100.100.100.5";

        OrkaCloudClient client = new OrkaCloudClient(this.getCloudClientParametersMock(imageId, nodeMappings),
                this.getOrkaClientMock(privateHost, 22, "instanceId"), this.getAsyncExecutorMock(),
                mock(RemoteAgent.class), mock(SSHUtil.class));

        OrkaCloudInstance instance = (OrkaCloudInstance) client.startNewInstance(this.getImage(client), null);

        assertEquals(privateHost, instance.getHost());
    }

    private CloudImage getImage(OrkaCloudClient client) {
        return client.getImages().stream().findFirst().get();
    }

    private CloudClientParameters getCloudClientParametersMock(String imageId) {
        return this.getCloudClientParametersMock(imageId, null);
    }

    private CloudClientParameters getCloudClientParametersMock(String imageId, String nodeMappings) {
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
        DeploymentResponse response = new DeploymentResponse(host, sshPort, instanceId, new OrkaError[] {}, null);
        when(orkaClient.deployVM(anyString())).thenReturn(response);
        return orkaClient;
    }

    private AsyncExecutor getAsyncExecutorMock() {
        AsyncExecutor asyncExecutor = mock(AsyncExecutor.class);
        when(asyncExecutor.submit(anyString(), any())).thenAnswer(new Answer<Future<?>>() {
            @Override
            public Future<?> answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                Runnable r = (Runnable) args[1];
                r.run();
                return CompletableFuture.completedFuture(null);
            }
        });

        return asyncExecutor;
    }
}