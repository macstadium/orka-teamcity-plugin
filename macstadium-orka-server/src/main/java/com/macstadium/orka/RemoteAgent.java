package com.macstadium.orka;

import com.intellij.openapi.diagnostic.Logger;

import java.io.File;
import java.io.IOException;

import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.FileUtil;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Command;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

public class RemoteAgent {
    private static final Logger LOG = Logger.getInstance(Loggers.CLOUD_CATEGORY_ROOT + OrkaConstants.TYPE);
    private static final int SSH_TIMEOUT = 60 * 1000;

    private static final String START_COMMAND_FORMAT = "%s/bin/agent.sh start";
    private static final String STOP_COMMAND_FORMAT = "%s/bin/agent.sh stop";

    public void startAgent(String instanceId, String imageId, String host, int sshPort, String sshUser,
            String sshPassword, String agentDirectory) throws IOException {

        File tempFile = File.createTempFile(CommonConstants.METADATA_FILE_PREFIX, ".tmp");
        FileUtil.writeFileAndReportErrors(tempFile, instanceId + System.lineSeparator() + imageId);

        LOG.debug("startAgentOnVM stating...");

        try (SSHClient ssh = new SSHClient()) {
            this.initSSHClient(ssh, host, sshPort, sshUser, sshPassword);
            ssh.newSCPFileTransfer().upload(tempFile.getAbsolutePath(), "/tmp");
            Session session = ssh.startSession();
            Command command = session.exec(String.format(START_COMMAND_FORMAT, agentDirectory));
            IOUtils.readFully(command.getInputStream()).toString();
        }

        LOG.debug("startAgentOnVM completed.");
    }

    public void stopAgent(OrkaCloudInstance orkaInstance, String imageId, String host, int sshPort, String sshUser,
            String sshPassword, String agentDirectory) {

        LOG.debug("stopAgentOnVM stating...");

        try (SSHClient ssh = new SSHClient()) {
            this.initSSHClient(ssh, host, sshPort, sshUser, sshPassword);
            orkaInstance.setStatus(InstanceStatus.STOPPING);
            Session session = ssh.startSession();
            Command command = session.exec(String.format(STOP_COMMAND_FORMAT, agentDirectory));
            IOUtils.readFully(command.getInputStream()).toString();
        } catch (IOException e) {
            LOG.debug("stopAgentOnVM error", e);
        }

        LOG.debug("stopAgentOnVM completed.");
    }

    private void initSSHClient(SSHClient ssh, String host, int sshPort, String sshUser, String sshPassword)
            throws IOException {
        ssh.setConnectTimeout(SSH_TIMEOUT);
        ssh.setTimeout(SSH_TIMEOUT);
        ssh.addHostKeyVerifier(new PromiscuousVerifier());
        ssh.connect(host, sshPort);
        ssh.authPassword(sshUser, sshPassword);
    }
}