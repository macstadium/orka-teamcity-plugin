package com.macstadium.orka;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import jetbrains.buildServer.clouds.QuotaException;

import org.testng.annotations.Test;

@Test
public class OrkaCloudImageTest {
    private static final String TEST_PROFILE_ID = "test-profile";
    private static final String TEST_VM_CONFIG = "imageId";

    public void when_can_start_new_instance_with_no_instances_should_return_true() throws IOException {
        int maximumInstances = 5;
        OrkaCloudImage image = new OrkaCloudImage(TEST_PROFILE_ID, TEST_VM_CONFIG, "orka-default", "user", "password", "0", maximumInstances, null);

        assertTrue(image.canStartNewInstance());
    }

    public void when_can_start_new_instance_with_free_slots_should_return_true() throws IOException {
        int maximumInstances = 5;
        OrkaCloudImage image = new OrkaCloudImage(TEST_PROFILE_ID, TEST_VM_CONFIG, "orka-default", "user", "password", "0", maximumInstances, null);
        image.startNewInstance("firstInstance");
        image.startNewInstance("secondInstance");

        assertTrue(image.canStartNewInstance());
    }

    public void when_can_start_new_instance_with_unlimited_slots_should_return_true() throws IOException {
        int maximumInstances = OrkaConstants.UNLIMITED_INSTANCES;
        OrkaCloudImage image = new OrkaCloudImage(TEST_PROFILE_ID, TEST_VM_CONFIG, "orka-default", "user", "password", "0", maximumInstances, null);
        image.startNewInstance("firstInstance");
        image.startNewInstance("secondInstance");
        image.startNewInstance("thirdInstance");

        assertTrue(image.canStartNewInstance());
    }

    public void when_can_start_new_instance_with_no_slots_should_return_false() throws IOException {
        int maximumInstances = 0;
        OrkaCloudImage image = new OrkaCloudImage(TEST_PROFILE_ID, TEST_VM_CONFIG, "orka-default", "user", "password", "0", maximumInstances, null);

        assertFalse(image.canStartNewInstance());
    }

    public void when_can_start_new_instance_with_no_slots_left_should_return_false() throws IOException {
        int maximumInstances = 2;
        OrkaCloudImage image = new OrkaCloudImage(TEST_PROFILE_ID, TEST_VM_CONFIG, "orka-default", "user", "password", "0", maximumInstances, null);
        image.startNewInstance("firstInstance");
        image.startNewInstance("secondInstance");

        assertFalse(image.canStartNewInstance());
    }

    public void when_start_new_instance_with_no_instances_should_return_new_instance() throws IOException {
        int maximumInstances = 2;
        String instanceId = "firstInstance";
        OrkaCloudImage image = new OrkaCloudImage(TEST_PROFILE_ID, TEST_VM_CONFIG, "orka-default", "user", "password", "0", maximumInstances, null);
        OrkaCloudInstance instance = image.startNewInstance("firstInstance");

        assertEquals(instanceId, instance.getInstanceId());
        assertEquals(image.getId(), instance.getImageId());
        assertEquals(image, instance.getImage());
    }

    @Test(expectedExceptions = QuotaException.class)
    public void when_start_new_instance_with_no_instances_left_should_return_throw() throws IOException {
        int maximumInstances = 1;
        OrkaCloudImage image = new OrkaCloudImage(TEST_PROFILE_ID, TEST_VM_CONFIG, "orka-default", "user", "password", "0", maximumInstances, null);
        image.startNewInstance("firstInstance");
        image.startNewInstance("secondInstance");
    }

    public void when_image_created_should_have_unique_id() throws IOException {
        String profileId1 = "profile-1";
        String profileId2 = "profile-2";
        String vmConfig = "same-vm-config";

        OrkaCloudImage image1 = new OrkaCloudImage(profileId1, vmConfig, "orka-default", "user", "password", "0", 5, null);
        OrkaCloudImage image2 = new OrkaCloudImage(profileId2, vmConfig, "orka-default", "user", "password", "0", 5, null);

        // IDs should be different
        assertFalse(image1.getId().equals(image2.getId()));

        // Names should also be different (display names include profileId)
        assertFalse(image1.getName().equals(image2.getName()));

        // vmConfigName should be the same
        assertEquals(vmConfig, image1.getVmConfigName());
        assertEquals(vmConfig, image2.getVmConfigName());

        // IDs should contain profileId
        assertTrue(image1.getId().contains(profileId1));
        assertTrue(image2.getId().contains(profileId2));

        // Names should be in format "profileId (vmConfig)"
        assertEquals(profileId1 + " (" + vmConfig + ")", image1.getName());
        assertEquals(profileId2 + " (" + vmConfig + ")", image2.getName());
    }

    public void when_image_created_should_return_correct_vm_config_name() throws IOException {
        String vmConfig = "my-vm-config";
        OrkaCloudImage image = new OrkaCloudImage(TEST_PROFILE_ID, vmConfig, "orka-default", "user", "password", "0", 5, null);

        // getName() returns display name: "profileId (vmConfig)"
        assertEquals(TEST_PROFILE_ID + " (" + vmConfig + ")", image.getName());
        // getId() returns unique ID
        assertEquals(TEST_PROFILE_ID + "_" + vmConfig, image.getId());
        // getVmConfigName() returns the original vmConfig
        assertEquals(vmConfig, image.getVmConfigName());
    }
}
