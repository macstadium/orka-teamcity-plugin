package com.macstadium.orka;

import com.intellij.openapi.diagnostic.Logger;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.List;

import jetbrains.buildServer.agent.BuildAgentConfigurationEx;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.FileUtil;

import org.jetbrains.annotations.NotNull;

public class OrkaAgent {
    private static final Logger LOG = Loggers.AGENT;
    // Nothing else identifies a daemon-mode agent, so it is worth waiting out an orka-vm-tools
    // listener that is still coming up rather than registering with no identity at all.
    private static final long METADATA_WAIT = 20 * 1000L;
    private static final long METADATA_RETRY_INTERVAL = 1000L;
    // One retry, and only for a request that failed outright: a request that threw reported
    // nothing about the key, which is the one case worth repeating. A response that did not carry
    // the key is an answer rather than a gap, since every key is written before the VM boots and
    // asking again cannot change what the service holds.
    private static final int METADATA_ATTEMPTS = 2;

    private final VMMetadataClient metadataClient = new VMMetadataClient();

    public OrkaAgent(@NotNull final BuildAgentConfigurationEx configuration) throws IOException {
        super();
        LOG.info("OrkaAgent plugin initializing...");

        FileFilter filter = this.getFilter(CommonConstants.METADATA_FILE_PREFIX);
        File currentDir = new File("").getAbsoluteFile();

        LOG.info("OrkaAgent plugin check temp dir");
        File tempMetadataFile = FileUtil.findFile(filter, new File("/tmp/"));
        File metadataFile = tempMetadataFile;
        if (metadataFile == null) {
            LOG.info("OrkaAgent plugin check current dir");
            metadataFile = FileUtil.findFile(filter, currentDir);
        }

        // SSH mode uploads this file and only then runs agent.sh start, so an agent that finds one
        // was started that way. The daemon wrapper deletes any copy an image capture left behind,
        // which is what leaves the absent case an unambiguous daemon deploy.
        if (metadataFile != null) {
            this.updateConfiguration(metadataFile, configuration);
            if (tempMetadataFile != null) {
                FileUtil.copy(tempMetadataFile, new File(currentDir, CommonConstants.METADATA_FILE_PREFIX));
            }
            return;
        }

        this.updateConfigurationFromMetadataService(configuration);
    }

    private void updateConfigurationFromMetadataService(BuildAgentConfigurationEx configuration) {
        String instanceId = this.waitForValue(CommonConstants.VM_NAME_METADATA_KEY);
        String imageId = this.metadataClient.getValue(CommonConstants.IMAGE_ID_METADATA_KEY, METADATA_ATTEMPTS);

        if (isEmpty(instanceId) || isEmpty(imageId)) {
            // The VM this runs on is deleted shortly after, so this line is the only account of
            // why no agent ever matched the instance. Server-side all that survives is an instance
            // that was reaped without ever being matched, which says nothing about the cause.
            LOG.warn(String.format(
                    "No %s on disk, so this is a daemon deploy, but the metadata service did not supply "
                            + "%s (got '%s') and %s (got '%s'). Check that orka-vm-tools is installed and "
                            + "running in this image. Leaving the agent unconfigured...",
                    CommonConstants.METADATA_FILE_PREFIX, CommonConstants.VM_NAME_METADATA_KEY, instanceId,
                    CommonConstants.IMAGE_ID_METADATA_KEY, imageId));
            return;
        }

        configuration.addConfigurationParameter(CommonConstants.INSTANCE_ID_PARAM_NAME, instanceId);
        configuration.addConfigurationParameter(CommonConstants.IMAGE_ID_PARAM_NAME, imageId);

        String startingInstanceId = this.metadataClient
                .getValue(CommonConstants.STARTING_INSTANCE_ID_METADATA_KEY, METADATA_ATTEMPTS);
        if (!isEmpty(startingInstanceId)) {
            configuration.addConfigurationParameter(CommonConstants.STARTING_INSTANCE_ID_CONFIG_PARAM,
                    startingInstanceId);
        }

        LOG.info(String.format("OrkaAgent configured from the metadata service with instance id: %s",
                instanceId));
    }

    private String waitForValue(String key) {
        try {
            return this.metadataClient.waitForFirstResponse(key, METADATA_WAIT, METADATA_RETRY_INTERVAL);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private FileFilter getFilter(final String prefix) {
        return new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.getName().startsWith(prefix);
            }
        };
    }

    private void updateConfiguration(File metadataFile, BuildAgentConfigurationEx configuration) throws IOException {
        List<String> contents = FileUtil.readFile(metadataFile);
        configuration.addConfigurationParameter(CommonConstants.INSTANCE_ID_PARAM_NAME, contents.get(0));
        configuration.addConfigurationParameter(CommonConstants.IMAGE_ID_PARAM_NAME, contents.get(1));
        if (contents.size() > 2) {
            configuration.addConfigurationParameter(CommonConstants.STARTING_INSTANCE_ID_CONFIG_PARAM, contents.get(2));
        }

        LOG.info(String.format("OrkaAgent configured from the local metadata file %s with instance id: %s",
                metadataFile.getAbsolutePath(), contents.get(0)));
    }
}
