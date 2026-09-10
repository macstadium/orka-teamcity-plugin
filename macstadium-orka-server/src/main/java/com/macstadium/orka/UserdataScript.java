package com.macstadium.orka;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import jetbrains.buildServer.clouds.CloudInstanceUserData;

import org.jetbrains.annotations.Nullable;

public class UserdataScript {
    private static final String METADATA_URL = "http://169.254.169.254/metadata/orka_vm_name";

    public String build(String imageId, String vmUser, String agentDirectory,
            @Nullable final CloudInstanceUserData data) {
        String startingInstanceId = data == null ? null
                : data.getCustomAgentConfigurationParameters().get(CommonConstants.STARTING_INSTANCE_ID_CONFIG_PARAM);

        StringBuilder script = new StringBuilder();
        script.append("#!/bin/sh\n");
        // The metadata service is served by orka-vm-tools, which also runs this script, but its
        // link-local listener may not be up yet on first boot.
        script.append("name=''\n");
        script.append("attempt=0\n");
        script.append("while [ $attempt -lt 60 ]; do\n");
        script.append("    name=$(curl -fsS ").append(METADATA_URL)
                .append(" 2>/dev/null | sed -n 's/.*\"value\"[[:space:]]*:[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p')\n");
        script.append("    if [ -n \"$name\" ]; then\n");
        script.append("        break\n");
        script.append("    fi\n");
        script.append("    attempt=$((attempt + 1))\n");
        script.append("    sleep 2\n");
        script.append("done\n");
        script.append("if [ -z \"$name\" ]; then\n");
        script.append("    echo 'Could not read orka_vm_name from the metadata service' >&2\n");
        script.append("    exit 1\n");
        script.append("fi\n");

        if (startingInstanceId == null) {
            script.append("printf '%s\\n%s\\n' \"$name\" ").append(quote(imageId));
        } else {
            script.append("printf '%s\\n%s\\n%s\\n' \"$name\" ").append(quote(imageId)).append(' ')
                    .append(quote(startingInstanceId));
        }
        script.append(" > /tmp/").append(CommonConstants.METADATA_FILE_PREFIX).append('\n');
        script.append("chmod 644 /tmp/").append(CommonConstants.METADATA_FILE_PREFIX).append('\n');
        script.append("su - ").append(quote(vmUser)).append(" -c ")
                .append(quote(String.format("%s/bin/agent.sh start", agentDirectory))).append('\n');

        return Base64.getEncoder().encodeToString(script.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
