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
    private static final int METADATA_RETRIES = 60;
    private static final int METADATA_RETRY_INTERVAL = 2 * 1000;

    public OrkaAgent(@NotNull final BuildAgentConfigurationEx configuration) throws IOException {
        super();
        LOG.info("OrkaAgent plugin initializing...");
        LOG.info("OrkaAgent plugin check temp dir");

        File currentDir = new File("").getAbsoluteFile();
        File dir = new File("/tmp/");
        File tempMetadataFile = FileUtil.findFile(this.getFilter(CommonConstants.METADATA_FILE_PREFIX), dir);
        if (tempMetadataFile != null) {
            this.updateConfiguration(tempMetadataFile, configuration);
            FileUtil.copy(tempMetadataFile, new File(currentDir, CommonConstants.METADATA_FILE_PREFIX));
        } else {
            LOG.info("OrkaAgent plugin check current dir");
            File metadataFile = FileUtil.findFile(this.getFilter(CommonConstants.METADATA_FILE_PREFIX), currentDir);
            if (metadataFile != null) {
                this.updateConfiguration(metadataFile, configuration);
            } else {
                LOG.info("No metadata file found. Falling back to the Orka metadata service...");
                this.updateConfigurationFromMetadataService(configuration);
            }
        }
    }

    private void updateConfigurationFromMetadataService(BuildAgentConfigurationEx configuration) {
        VMMetadataClient metadataClient = new VMMetadataClient();
        try {
            String instanceId = metadataClient.waitForValue(CommonConstants.VM_NAME_METADATA_KEY,
                    METADATA_RETRIES, METADATA_RETRY_INTERVAL);
            if (instanceId == null) {
                LOG.info("No VM name in the metadata service. Stopping initialization...");
                return;
            }

            String imageId = metadataClient.getValue(CommonConstants.IMAGE_ID_METADATA_KEY);
            if (imageId == null || imageId.isEmpty()) {
                LOG.info("No TeamCity image id in the metadata service. Stopping initialization...");
                return;
            }

            configuration.addConfigurationParameter(CommonConstants.INSTANCE_ID_PARAM_NAME, instanceId);
            configuration.addConfigurationParameter(CommonConstants.IMAGE_ID_PARAM_NAME, imageId);

            String startingInstanceId = metadataClient
                    .getValue(CommonConstants.STARTING_INSTANCE_ID_METADATA_KEY);
            if (startingInstanceId != null && !startingInstanceId.isEmpty()) {
                configuration.addConfigurationParameter(CommonConstants.STARTING_INSTANCE_ID_CONFIG_PARAM,
                        startingInstanceId);
            }

            LOG.info(String.format("OrkaAgent configured from the metadata service with instance id: %s", instanceId));
        } catch (IOException e) {
            LOG.warn("Failed to read the Orka metadata service", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
    }
}
