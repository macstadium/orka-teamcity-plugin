package com.macstadium.orka;

import com.intellij.openapi.diagnostic.Logger;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.FileUtil;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Command;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

import org.jetbrains.annotations.NotNull;

public class RemoteAgent {
  private static final Logger LOG = Logger.getInstance(Loggers.CLOUD_CATEGORY_ROOT + OrkaConstants.TYPE);
  private static final int SSH_TIMEOUT = 60 * 1000;

  private static final String START_COMMAND_FORMAT = "%s/bin/agent.sh start";
  private static final String STOP_COMMAND_FORMAT = "%s/bin/agent.sh stop";
  private static final String BUILD_AGENT_PROPERTIES_PATH = "%s/conf/buildAgent.properties";

  public void startAgent(String instanceId, String imageId, String host, int sshPort, String sshUser,
      String sshPassword, String agentDirectory, @NotNull final CloudInstanceUserData data)
      throws IOException {

    // Validate agentDirectory
    if (agentDirectory == null || agentDirectory.trim().isEmpty()) {
      String errorMsg = String.format(
          "Agent Directory is not configured! Please set 'Agent Directory' field in TeamCity Cloud Profile settings. " +
              "InstanceId=%s, ImageId=%s",
          instanceId, imageId);
      LOG.warn(errorMsg);
      throw new IOException(errorMsg);
    }

    // serverUrl comes from Cloud Profile settings - use as is (can be null)

    // Prepare metadata file with instanceId and imageId
    File tempFile = File.createTempFile(CommonConstants.METADATA_FILE_PREFIX, ".tmp");
    String text = instanceId + System.lineSeparator() + imageId;
    if (data.getCustomAgentConfigurationParameters()
        .containsKey(CommonConstants.STARTING_INSTANCE_ID_CONFIG_PARAM)) {
      text = text + System.lineSeparator() + data.getCustomAgentConfigurationParameters()
          .get(CommonConstants.STARTING_INSTANCE_ID_CONFIG_PARAM);
    }
    FileUtil.writeFileAndReportErrors(tempFile, text);

    LOG.debug(String.format("Starting agent on VM %s: instanceId=%s, imageId=%s", host, instanceId, imageId));

    try (SSHClient ssh = new SSHClient()) {
      this.initSSHClient(ssh, host, sshPort, sshUser, sshPassword);

      // Upload metadata file
      ssh.newSCPFileTransfer().upload(tempFile.getAbsolutePath(), "/tmp");
      LOG.debug(String.format("Metadata uploaded to %s:/tmp/%s", host, tempFile.getName()));

      // Note: Agent auto-starts on VM boot (configured in VM image)
      // We don't call agent.sh start here - agent will read metadata on auto-start
      LOG.debug(String.format("Metadata configured for VM %s (agent will auto-start and read it)", instanceId));
    }
  }

  public void stopAgent(OrkaCloudInstance orkaInstance, String imageId, String host, int sshPort, String sshUser,
      String sshPassword, String agentDirectory) {

    LOG.debug("stopAgentOnVM starting...");

    if (agentDirectory == null || agentDirectory.trim().isEmpty()) {
      LOG.warn(String.format("Agent Directory is not configured! Cannot stop agent on VM %s",
          orkaInstance.getInstanceId()));
      return;
    }

    try (SSHClient ssh = new SSHClient()) {
      this.initSSHClient(ssh, host, sshPort, sshUser, sshPassword);
      orkaInstance.setStatus(InstanceStatus.STOPPING);

      try (Session session = ssh.startSession()) {
        String stopCommand = String.format(STOP_COMMAND_FORMAT, agentDirectory);
        LOG.debug(String.format("Executing agent.sh stop: %s", stopCommand));

        Command command = session.exec(stopCommand);
        command.join(SSH_TIMEOUT, TimeUnit.MILLISECONDS);
        String output = IOUtils.readFully(command.getInputStream()).toString();
        Integer exitStatus = command.getExitStatus();

        LOG.debug(String.format("Agent stop completed. Exit status: %s, Output: %s",
            exitStatus, output));
      }
    } catch (IOException e) {
      // SSH errors during stop are not critical - VM will be deleted anyway
      // But log as WARN so we can see if there are connectivity issues
      LOG.warn(String.format("Could not stop agent on VM %s via SSH: %s (VM will still be deleted)",
          orkaInstance.getInstanceId(), e.getMessage()));
    }

    LOG.debug("stopAgentOnVM completed.");
  }

  // Package-private for OrkaCloudClient to call directly
  void initSSHClient(SSHClient ssh, String host, int sshPort, String sshUser, String sshPassword)
      throws IOException {
    LOG.debug(String.format("Connecting to %s:%d (user: %s)", host, sshPort, sshUser));

    ssh.setConnectTimeout(SSH_TIMEOUT);
    ssh.setTimeout(SSH_TIMEOUT);
    ssh.addHostKeyVerifier(new PromiscuousVerifier());
    ssh.connect(host, sshPort);
    ssh.authPassword(sshUser, sshPassword);

    LOG.debug("SSH connection established");
  }

  // Package-private for OrkaCloudClient to call directly
  void updateBuildAgentProperties(SSHClient ssh, String agentDirectory, String instanceId,
      String serverUrl) throws IOException {
    String buildAgentPropertiesPath = String.format(BUILD_AGENT_PROPERTIES_PATH, agentDirectory);
    String agentName = "orka-mac-" + instanceId;

    // Always update agent name for uniqueness
    // Update serverUrl only if provided
    boolean updateServerUrl = (serverUrl != null && !serverUrl.trim().isEmpty());

    if (updateServerUrl) {
      LOG.info(String.format("Updating buildAgent.properties: name=%s, serverUrl=%s", agentName, serverUrl));
    } else {
      LOG.info(String.format("Updating buildAgent.properties: name=%s (serverUrl not changed)", agentName));
    }

    try (Session session = ssh.startSession()) {
      String updateCommand;

      if (updateServerUrl) {
        // Update both serverUrl and name
        updateCommand = String.format(
            "sed -i.bak -e 's|^serverUrl=.*|serverUrl=%s|' -e 's|^name=.*|name=%s|' %s && " +
                "if ! grep -q '^name=' %s; then echo 'name=%s' >> %s; fi",
            serverUrl, agentName, buildAgentPropertiesPath,
            buildAgentPropertiesPath, agentName, buildAgentPropertiesPath);
      } else {
        // Update only name
        updateCommand = String.format(
            "sed -i.bak 's|^name=.*|name=%s|' %s && " +
                "if ! grep -q '^name=' %s; then echo 'name=%s' >> %s; fi",
            agentName, buildAgentPropertiesPath,
            buildAgentPropertiesPath, agentName, buildAgentPropertiesPath);
      }

      Command cmd = session.exec(updateCommand);
      cmd.join(SSH_TIMEOUT, TimeUnit.MILLISECONDS);
      String output = IOUtils.readFully(cmd.getInputStream()).toString();
      Integer exitStatus = cmd.getExitStatus();

      if (exitStatus != null && exitStatus == 0) {
        LOG.info(String.format("buildAgent.properties updated: name=%s%s",
            agentName, updateServerUrl ? ", serverUrl=" + serverUrl : ""));
      } else {
        LOG.warn(String.format("Failed to update buildAgent.properties at %s. Exit code: %s. Output: %s",
            buildAgentPropertiesPath, exitStatus, output));
      }
    }
  }
}
