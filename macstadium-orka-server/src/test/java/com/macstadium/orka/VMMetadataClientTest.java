package com.macstadium.orka;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.testng.annotations.Test;

@Test
public class VMMetadataClientTest {
    public void when_parse_value_should_return_the_value() {
        assertEquals("vm-abc123", VMMetadataClient.parseValue("{\"value\":\"vm-abc123\"}"));
    }

    public void when_parse_value_with_whitespace_should_return_the_value() {
        assertEquals("vm-abc123", VMMetadataClient.parseValue("{\"value\" : \"vm-abc123\"}"));
    }

    public void when_parse_error_response_should_return_null() {
        // The metadata service answers 200 with this body when the key is unknown.
        assertNull(VMMetadataClient.parseValue("{\"error\":\"Unable to find metadata value by provided key x\"}"));
    }

    public void when_parse_empty_value_should_return_empty() {
        assertEquals("", VMMetadataClient.parseValue("{\"value\":\"\"}"));
    }

    public void when_parse_null_should_return_null() {
        assertNull(VMMetadataClient.parseValue(null));
    }
}
