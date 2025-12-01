package com.macstadium.orka;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.macstadium.orka.client.DeploymentResponse;
import com.macstadium.orka.client.DeletionResponse;
import com.macstadium.orka.client.HttpResponse;
import com.macstadium.orka.client.OrkaClient;
import com.macstadium.orka.client.VMResponse;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

import jetbrains.buildServer.clouds.CloudImage;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.Test;

@Test
public class CapacityCheckTest {

    public void when_can_start_new_instance_with_slots_should_return_true() throws IOException {
        String vmConfigName = "testImage";
        String fullImageId = Utils.getFullImageId(vmConfigName);
        OrkaClient orkaClient = mock(OrkaClient.class);
        setupDeploymentMock(orkaClient, "host", 22, "instanceId");

        OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName),
            orkaClient, getScheduledExecutorService(), mock(RemoteAgent.class), mock(SSHUtil.class));

        OrkaCloudImage image = client.findImageById(fullImageId);
        assertNotNull("Image should be found by full ID", image);
        
        assertTrue("Should be able to start new instance with available slots", 
            client.canStartNewInstance(image));
    }

    public void when_image_created_should_have_correct_ids() throws IOException {
        String vmConfigName = "testImage";
        String fullImageId = Utils.getFullImageId(vmConfigName);
        String displayName = Utils.getDisplayName(vmConfigName);
        OrkaClient orkaClient = mock(OrkaClient.class);
        setupDeploymentMock(orkaClient, "host", 22, "instanceId");

        OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName),
            orkaClient, getScheduledExecutorService(), mock(RemoteAgent.class), mock(SSHUtil.class));

        OrkaCloudImage image = client.findImageById(fullImageId);
        
        assertNotNull("Image should be found by full ID", image);
        assertEquals("Image ID should be profileId_vmConfigName", fullImageId, image.getId());
        assertEquals("Image name should be vmConfig (profileId)", displayName, image.getName());
        assertEquals("vmConfigName should be original config name", vmConfigName, image.getVmConfigName());
    }

    public void when_multiple_profiles_use_same_vm_config_should_have_different_image_ids() throws IOException {
        String vmConfigName = "same-vm-config";
        String profileId1 = "profile-1";
        String profileId2 = "profile-2";
        
        OrkaCloudImage image1 = new OrkaCloudImage(profileId1, vmConfigName, "orka-default", 
            "user", "password", "0", 5, null);
        OrkaCloudImage image2 = new OrkaCloudImage(profileId2, vmConfigName, "orka-default", 
            "user", "password", "0", 5, null);
        
        // Image IDs should be different
        assertTrue("Image IDs should be different for different profiles", 
            !image1.getId().equals(image2.getId()));
        
        // Display names should also be different
        assertTrue("Image names should be different for different profiles", 
            !image1.getName().equals(image2.getName()));
        
        // vmConfigName should be the same
        assertEquals("Both images should have same vmConfigName", 
            image1.getVmConfigName(), image2.getVmConfigName());
        assertEquals("vmConfigName should match", vmConfigName, image1.getVmConfigName());
        
        // IDs should contain their respective profile IDs
        assertTrue("Image1 ID should contain profile1", image1.getId().contains(profileId1));
        assertTrue("Image2 ID should contain profile2", image2.getId().contains(profileId2));
        
        // Names should be in format: "profileId (vmConfig)"
        assertEquals("Image1 name format", profileId1 + " (" + vmConfigName + ")", image1.getName());
        assertEquals("Image2 name format", profileId2 + " (" + vmConfigName + ")", image2.getName());
    }

    public void when_find_image_by_wrong_id_should_return_null() throws IOException {
        String vmConfigName = "testImage";
        OrkaClient orkaClient = mock(OrkaClient.class);
        setupDeploymentMock(orkaClient, "host", 22, "instanceId");

        OrkaCloudClient client = new OrkaCloudClient(Utils.getCloudClientParametersMock(vmConfigName),
            orkaClient, getScheduledExecutorService(), mock(RemoteAgent.class), mock(SSHUtil.class));

        // Try to find by old-style ID (just vmConfigName without profileId)
        OrkaCloudImage imageByOldId = client.findImageById(vmConfigName);
        assertTrue("Should NOT find image by vmConfigName alone (old format)", 
            imageByOldId == null);
        
        // Find by new full ID
        String fullImageId = Utils.getFullImageId(vmConfigName);
        OrkaCloudImage imageByFullId = client.findImageById(fullImageId);
        assertNotNull("Should find image by full ID (profileId_vmConfigName)", imageByFullId);
    }

    private void setupDeploymentMock(OrkaClient orkaClient, String host, int sshPort, 
            String instanceId) throws IOException {
        DeploymentResponse deploymentResponse = new DeploymentResponse(host, sshPort, instanceId, null);
        deploymentResponse.setHttpResponse(new HttpResponse("instanceId", 200, true));
        when(orkaClient.deployVM(any(), any(), any())).thenReturn(deploymentResponse);
        when(orkaClient.deployVM(any(), any(), any(), any())).thenReturn(deploymentResponse);
        
        DeletionResponse deletionResponse = new DeletionResponse("Success");
        deletionResponse.setHttpResponse(new HttpResponse("instanceId", 200, true));
        when(orkaClient.deleteVM(any(), any())).thenReturn(deletionResponse);
        
        VMResponse vmResponse = new VMResponse(instanceId, sshPort, host, null);
        vmResponse.setHttpResponse(new HttpResponse("instanceId", 200, true));
        when(orkaClient.getVM(any(), any())).thenReturn(vmResponse);
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
