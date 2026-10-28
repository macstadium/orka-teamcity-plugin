package com.macstadium.orka;

import static org.junit.Assert.assertEquals;

import org.testng.annotations.Test;

@Test
public class RemoteAgentTest {
    public void when_starting_instance_id_is_absent_should_keep_profile_id_on_the_fourth_line() {
        String text = RemoteAgent.getMetadataFileText("vm-1", "image-1", null, "profile-a");

        assertEquals(String.join(System.lineSeparator(), "vm-1", "image-1", "", "profile-a"), text);
    }

    public void when_all_values_are_present_should_write_them_in_order() {
        String text = RemoteAgent.getMetadataFileText("vm-1", "image-1", "starting-1", "profile-a");

        assertEquals(String.join(System.lineSeparator(), "vm-1", "image-1", "starting-1", "profile-a"), text);
    }
}
