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

    public void startAgent(String instanceId, String imageId, String host, int sshPort, String sshUser,
            String sshPassword, String agentDirectory, @NotNull final CloudInstanceUserData data) throws IOException {

        File tempFile = File.createTempFile(CommonConstants.METADATA_FILE_PREFIX, ".tmp");
        String text = instanceId + System.lineSeparator() + imageId;
        if (data.getCustomAgentConfigurationParameters()
                .containsKey(CommonConstants.STARTING_INSTANCE_ID_CONFIG_PARAM)) {
            text = text + System.lineSeparator() + data.getCustomAgentConfigurationParameters()
                    .get(CommonConstants.STARTING_INSTANCE_ID_CONFIG_PARAM);
        }

        FileUtil.writeFileAndReportErrors(tempFile, text);

        LOG.debug("startAgentOnVM starting...");

        try (SSHClient ssh = new SSHClient()) {
            this.initSSHClient(ssh, host, sshPort, sshUser, sshPassword);
            ssh.newSCPFileTransfer().upload(tempFile.getAbsolutePath(), "/tmp");
            try (Session session = ssh.startSession()) {
                LOG.debug("Executing SSH start command...");

                Command command = session.exec(String.format(START_COMMAND_FORMAT, agentDirectory));
                command.join(SSH_TIMEOUT, TimeUnit.MILLISECONDS);
                IOUtils.readFully(command.getInputStream()).toString();
            }
        }

        LOG.debug("startAgentOnVM completed.");
    }

    public void stopAgent(OrkaCloudInstance orkaInstance, String imageId, String host, int sshPort, String sshUser,
            String sshPassword, String agentDirectory) {

        LOG.debug("stopAgentOnVM starting...");

        try (SSHClient ssh = new SSHClient()) {
            this.initSSHClient(ssh, host, sshPort, sshUser, sshPassword);
            orkaInstance.setStatus(InstanceStatus.STOPPING);
            try (Session session = ssh.startSession()) {
                LOG.debug("Executing SSH stop command...");

                Command command = session.exec(String.format(STOP_COMMAND_FORMAT, agentDirectory));
                command.join(SSH_TIMEOUT, TimeUnit.MILLISECONDS);
                IOUtils.readFully(command.getInputStream()).toString();
            }
        } catch (IOException e) {
            LOG.debug("stopAgentOnVM error", e);
        }

        LOG.debug("stopAgentOnVM completed.");
    }

    private void initSSHClient(SSHClient ssh, String host, int sshPort, String sshUser, String sshPassword)
            throws IOException {
        LOG.debug("Initializing SSH Client...");

        ssh.setConnectTimeout(SSH_TIMEOUT);
        ssh.setTimeout(SSH_TIMEOUT);
        ssh.addHostKeyVerifier(new PromiscuousVerifier());
        ssh.connect(host, sshPort);
        ssh.authPassword(sshUser, sshPassword);

        LOG.debug("SSH Client initialized.");
    }
}
