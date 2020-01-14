package com.macstadium.orka;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import jetbrains.buildServer.clouds.QuotaException;

import org.testng.annotations.Test;

@Test
public class OrkaCloudImageTest {
    public void when_can_start_new_instance_with_no_instances_should_return_true() throws IOException {
        int maximumInstances = 5;
        OrkaCloudImage image = new OrkaCloudImage("imageId", "user", "password", "0", maximumInstances);

        assertTrue(image.canStartNewInstance());
    }

    public void when_can_start_new_instance_with_free_slots_should_return_true() throws IOException {
        int maximumInstances = 5;
        OrkaCloudImage image = new OrkaCloudImage("imageId", "user", "password", "0", maximumInstances);
        image.startNewInstance("firstInstance");
        image.startNewInstance("secondInstance");

        assertTrue(image.canStartNewInstance());
    }

    public void when_can_start_new_instance_with_unlimited_slots_should_return_true() throws IOException {
        int maximumInstances = OrkaConstants.UNLIMITED_INSTANCES;
        OrkaCloudImage image = new OrkaCloudImage("imageId", "user", "password", "0", maximumInstances);
        image.startNewInstance("firstInstance");
        image.startNewInstance("secondInstance");
        image.startNewInstance("thirdInstance");

        assertTrue(image.canStartNewInstance());
    }

    public void when_can_start_new_instance_with_no_slots_should_return_false() throws IOException {
        int maximumInstances = 0;
        OrkaCloudImage image = new OrkaCloudImage("imageId", "user", "password", "0", maximumInstances);

        assertFalse(image.canStartNewInstance());
    }

    public void when_can_start_new_instance_with_no_slots_left_should_return_false() throws IOException {
        int maximumInstances = 2;
        OrkaCloudImage image = new OrkaCloudImage("imageId", "user", "password", "0", maximumInstances);
        image.startNewInstance("firstInstance");
        image.startNewInstance("secondInstance");

        assertFalse(image.canStartNewInstance());
    }

    public void when_start_new_instance_with_no_instances_should_return_new_instance() throws IOException {
        int maximumInstances = 2;
        String instanceId = "firstInstance";
        OrkaCloudImage image = new OrkaCloudImage("imageId", "user", "password", "0", maximumInstances);
        OrkaCloudInstance instance = image.startNewInstance("firstInstance");

        assertEquals(instanceId, instance.getInstanceId());
        assertEquals(image.getId(), instance.getImageId());
        assertEquals(image, instance.getImage());
    }

    @Test(expectedExceptions = QuotaException.class)
    public void when_start_new_instance_with_no_instances_left_should_return_throw() throws IOException {
        int maximumInstances = 1;
        OrkaCloudImage image = new OrkaCloudImage("imageId", "user", "password", "0", maximumInstances);
        image.startNewInstance("firstInstance");
        image.startNewInstance("secondInstance");
    }
}