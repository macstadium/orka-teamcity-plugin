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
        LOG.info("No metadata file found. Stopping initialization...");
      }
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
    String instanceId = contents.get(0);
    String imageId = contents.get(1);

    // Check if parameters are already set to avoid triggering buildAgent.properties changes
    String existingInstanceId = configuration.getConfigurationParameters().get(CommonConstants.INSTANCE_ID_PARAM_NAME);
    String existingImageId = configuration.getConfigurationParameters().get(CommonConstants.IMAGE_ID_PARAM_NAME);

    if (instanceId.equals(existingInstanceId) && imageId.equals(existingImageId)) {
      LOG.info(String.format("Orka agent parameters already configured: instanceId=%s, imageId=%s (no changes needed)",
          instanceId, imageId));
      return; // No changes needed - avoid buildAgent.properties modification
    }

    LOG.info(String.format("Orka agent configuring: instanceId=%s, imageId=%s", instanceId, imageId));

    configuration.addConfigurationParameter(CommonConstants.INSTANCE_ID_PARAM_NAME, instanceId);
    configuration.addConfigurationParameter(CommonConstants.IMAGE_ID_PARAM_NAME, imageId);
    if (contents.size() > 2) {
      String startingInstanceId = contents.get(2);
      LOG.debug(String.format("Setting startingInstanceId=%s", startingInstanceId));
      configuration.addConfigurationParameter(CommonConstants.STARTING_INSTANCE_ID_CONFIG_PARAM, startingInstanceId);
    }
  }
}
