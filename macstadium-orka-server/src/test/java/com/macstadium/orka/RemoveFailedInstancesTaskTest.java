package com.macstadium.orka;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.macstadium.orka.client.DeletionResponse;
import com.macstadium.orka.client.HttpResponse;
import com.macstadium.orka.client.OrkaClient;
import com.macstadium.orka.client.VMResponse;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;

import org.testng.annotations.Test;

@Test
public class RemoveFailedInstancesTaskTest {
    private String existingRunningVMId = "existing";
    private String failedRunningVMId = "failed";

    public void when_run_task_and_no_instances_to_terminate_should_keep_running_instances() throws IOException {
        OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock("imageId"),
                mock(OrkaClient.class), mock(ScheduledExecutorService.class),
                mock(RemoteAgent.class), mock(SSHUtil.class));
        OrkaCloudImage image = (OrkaCloudImage) client.getImages().toArray()[0];
        OrkaCloudInstance instance = image.startNewInstance("instanceId");

        RemoveFailedInstancesTask task = new RemoveFailedInstancesTask(client);
        task.run();

        assertEquals(1, image.getInstances().size());
        assertSame(instance, image.getInstances().toArray()[0]);
    }

    public void when_run_task_and_one_instance_to_terminate_with_no_vm_should_terminate_instance() throws IOException {
        OrkaClient orkaClient = getOrkaClientMock(existingRunningVMId, false);
        OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock("imageId"), orkaClient,
                mock(ScheduledExecutorService.class), mock(RemoteAgent.class), mock(SSHUtil.class));
        OrkaCloudImage image = (OrkaCloudImage) client.getImages().toArray()[0];
        OrkaCloudInstance instance = image.startNewInstance(failedRunningVMId);
        instance.setMarkedForTermination(true);

        RemoveFailedInstancesTask task = new RemoveFailedInstancesTask(client);
        task.run();

        assertEquals(0, image.getInstances().size());
    }

    public void when_run_task_and_one_instance_to_terminate_with_vm_should_terminate_instance_and_vm()
            throws IOException {
        OrkaClient orkaClient = getOrkaClientMock(failedRunningVMId, true);
        DeletionResponse deletionResponse = new DeletionResponse("Success");
        deletionResponse.setHttpResponse(new HttpResponse("", 200, true));
        when(orkaClient.deleteVM(failedRunningVMId, "orka-default")).thenReturn(deletionResponse);
        OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock("imageId"), orkaClient,
                mock(ScheduledExecutorService.class), mock(RemoteAgent.class), mock(SSHUtil.class));
        OrkaCloudImage image = (OrkaCloudImage) client.getImages().toArray()[0];
        OrkaCloudInstance failedInstance = image.startNewInstance(failedRunningVMId);
        failedInstance.setMarkedForTermination(true);

        RemoveFailedInstancesTask task = new RemoveFailedInstancesTask(client);
        task.run();

        assertEquals(0, image.getInstances().size());
        verify(orkaClient).deleteVM(failedRunningVMId, "orka-default");
    }

    @SuppressWarnings("checkstyle:linelength")
    public void when_run_task_and_one_instance_to_terminate_with_vm_should_terminate_instance_and_vm_and_keep_running_instances()
            throws IOException {
        OrkaClient orkaClient = getOrkaClientMock(failedRunningVMId, true);
        DeletionResponse deletionResponse = new DeletionResponse("Success");
        deletionResponse.setHttpResponse(new HttpResponse("", 200, true));
        when(orkaClient.deleteVM(failedRunningVMId, "orka-default")).thenReturn(deletionResponse);

        OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock("imageId"), orkaClient,
                mock(ScheduledExecutorService.class), mock(RemoteAgent.class), mock(SSHUtil.class));
        OrkaCloudImage image = (OrkaCloudImage) client.getImages().toArray()[0];
        final OrkaCloudInstance runningInstance = image.startNewInstance(existingRunningVMId);
        OrkaCloudInstance failedInstance = image.startNewInstance(failedRunningVMId);
        failedInstance.setMarkedForTermination(true);

        RemoveFailedInstancesTask task = new RemoveFailedInstancesTask(client);
        task.run();

        assertEquals(1, image.getInstances().size());
        verify(orkaClient).deleteVM(failedRunningVMId, "orka-default");
        assertSame(runningInstance, image.getInstances().toArray()[0]);
    }

    public void when_run_task_and_one_instance_to_terminate_with_vm_should_and_delete_throws_should_keep_instance()
            throws IOException {
        OrkaClient orkaClient = getOrkaClientMock(failedRunningVMId, true);
        when(orkaClient.deleteVM(failedRunningVMId, "orka-default")).thenThrow(new IOException());

        OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock("imageId"), orkaClient,
                mock(ScheduledExecutorService.class), mock(RemoteAgent.class), mock(SSHUtil.class));
        OrkaCloudImage image = (OrkaCloudImage) client.getImages().toArray()[0];
        OrkaCloudInstance failedInstance = image.startNewInstance(failedRunningVMId);
        failedInstance.setMarkedForTermination(true);

        RemoveFailedInstancesTask task = new RemoveFailedInstancesTask(client);
        task.run();

        assertEquals(1, image.getInstances().size());
        verify(orkaClient).deleteVM(failedRunningVMId, "orka-default");
        assertSame(failedInstance, image.getInstances().toArray()[0]);
    }

    @SuppressWarnings("checkstyle:linelength")
    public void when_run_task_and_one_instance_to_terminate_with_vm_should_and_delete_returns_error_should_keep_instance()
            throws IOException {
        OrkaClient orkaClient = getOrkaClientMock(failedRunningVMId, true);
        DeletionResponse deletionResponse = new DeletionResponse("Error");
        deletionResponse.setHttpResponse(new HttpResponse("", 400, false));
        when(orkaClient.deleteVM(failedRunningVMId, "orka-default")).thenReturn(deletionResponse);

        OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock("imageId"), orkaClient,
                mock(ScheduledExecutorService.class), mock(RemoteAgent.class), mock(SSHUtil.class));
        OrkaCloudImage image = (OrkaCloudImage) client.getImages().toArray()[0];
        OrkaCloudInstance failedInstance = image.startNewInstance(failedRunningVMId);
        failedInstance.setMarkedForTermination(true);

        RemoveFailedInstancesTask task = new RemoveFailedInstancesTask(client);
        task.run();

        assertEquals(1, image.getInstances().size());
        verify(orkaClient).deleteVM(failedRunningVMId, "orka-default");
        assertSame(failedInstance, image.getInstances().toArray()[0]);
    }

    private OrkaClient getOrkaClientMock(String vmID, boolean shouldExist) throws IOException {
        OrkaClient orkaClient = mock(OrkaClient.class);
        VMResponse response = new VMResponse(vmID, 12, "10.211.188.11", null);
        response.setHttpResponse(new HttpResponse("existingRunningVMId", 200, shouldExist));
        when(orkaClient.getVM(any(), any())).thenReturn(response);

        return orkaClient;
    }
}
