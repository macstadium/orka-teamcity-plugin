package com.macstadium.orka;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jetbrains.buildServer.clouds.CloudInstanceUserData;

import org.testng.annotations.Test;

@Test
public class UserdataScriptTest {
    public void when_build_should_return_base64_shell_script() {
        String script = decode(new UserdataScript().build("imageId", "vm_user", "/Users/vm_user/agent", null));

        assertTrue("starts with a shebang", script.startsWith("#!/bin/sh\n"));
        assertTrue("reads the vm name from the metadata service",
                script.contains("http://169.254.169.254/metadata/orka_vm_name"));
        assertTrue("writes the metadata file the agent plugin looks for",
                script.contains("> /tmp/" + CommonConstants.METADATA_FILE_PREFIX));
        assertTrue("starts the agent as the vm user",
                script.contains("su - 'vm_user' -c '/Users/vm_user/agent/bin/agent.sh start'"));
    }

    public void when_build_without_starting_instance_id_should_write_two_lines() {
        String script = decode(new UserdataScript().build("imageId", "vm_user", "dir", null));

        assertTrue("writes name and image id",
                script.contains("printf '%s\\n%s\\n' \"$name\" 'imageId' > /tmp/"));
    }

    public void when_build_with_starting_instance_id_should_write_three_lines() {
        Map<String, String> params = new HashMap<String, String>();
        params.put(CommonConstants.STARTING_INSTANCE_ID_CONFIG_PARAM, "starting-id");
        CloudInstanceUserData data = mock(CloudInstanceUserData.class);
        when(data.getCustomAgentConfigurationParameters()).thenReturn(params);

        String script = decode(new UserdataScript().build("imageId", "vm_user", "dir", data));

        assertTrue("writes name, image id and starting instance id",
                script.contains("printf '%s\\n%s\\n%s\\n' \"$name\" 'imageId' 'starting-id' > /tmp/"));
    }

    public void when_build_with_quote_in_value_should_escape_it() {
        CloudInstanceUserData data = mock(CloudInstanceUserData.class);
        when(data.getCustomAgentConfigurationParameters()).thenReturn(Collections.<String, String>emptyMap());

        String script = decode(new UserdataScript().build("imageId", "o'brien", "dir", data));

        assertTrue("escapes the single quote", script.contains("su - 'o'\\''brien' -c"));
    }

    public void when_build_should_produce_a_syntactically_valid_shell_script() throws Exception {
        File scriptFile = File.createTempFile("userdata", ".sh");
        scriptFile.deleteOnExit();
        Files.write(scriptFile.toPath(),
                decode(new UserdataScript().build("imageId", "vm_user", "dir", null)).getBytes(StandardCharsets.UTF_8));

        Process process = new ProcessBuilder("/bin/sh", "-n", scriptFile.getAbsolutePath()).redirectErrorStream(true)
                .start();
        String output = new String(readAll(process.getInputStream()), StandardCharsets.UTF_8);
        process.waitFor();

        assertEquals("sh -n reported: " + output, 0, process.exitValue());
    }

    public void when_build_should_be_within_the_orka_userdata_size_limit() {
        String userdata = new UserdataScript().build("imageId", "vm_user", "/Users/vm_user/agent", null);

        assertEquals("well below the 65536 byte api limit", true, userdata.length() < 65536);
    }

    private byte[] readAll(InputStream stream) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private String decode(String userdata) {
        return new String(Base64.getDecoder().decode(userdata), StandardCharsets.UTF_8);
    }
}
