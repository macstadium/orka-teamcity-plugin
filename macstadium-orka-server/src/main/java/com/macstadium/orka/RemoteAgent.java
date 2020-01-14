package com.macstadium.orka;

import com.intellij.openapi.diagnostic.Logger;

import java.io.File;
import java.io.IOException;

import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.util.FileUtil;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Command;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

public class RemoteAgent {
    private static final Logger LOG = Logger.getInstance(RemoteAgent.class.getName());

    private static final String START_COMMAND_FORMAT = "%s/bin/agent.sh start";
    private static final String STOP_COMMAND_FORMAT = "%s/bin/agent.sh stop";

    public void startAgent(String instanceId, String imageId, String host, int sshPort, String sshUser,
            String sshPassword, String agentDirectory) throws IOException {

        File tempFile = File.createTempFile(CommonConstants.METADATA_FILE_PREFIX, ".tmp");
        FileUtil.writeFileAndReportErrors(tempFile, instanceId + System.lineSeparator() + imageId);

        LOG.debug("startAgentOnVM");

        try (SSHClient ssh = new SSHClient()) {
            ssh.addHostKeyVerifier(new PromiscuousVerifier());
            ssh.connect(host, sshPort);
            ssh.authPassword(sshUser, sshPassword);
            ssh.newSCPFileTransfer().upload(tempFile.getAbsolutePath(), "/tmp");
            Session session = ssh.startSession();
            Command command = session.exec(String.format(START_COMMAND_FORMAT, agentDirectory));
            IOUtils.readFully(command.getInputStream()).toString();
        }
    }

    public void stopAgent(OrkaCloudInstance orkaInstance, String imageId, String host, int sshPort, String sshUser,
            String sshPassword, String agentDirectory) {
        try (SSHClient ssh = new SSHClient()) {
            ssh.addHostKeyVerifier(new PromiscuousVerifier());
            ssh.connect(host, sshPort);
            ssh.authPassword(sshUser, sshPassword);
            orkaInstance.setStatus(InstanceStatus.STOPPING);
            Session session = ssh.startSession();
            Command command = session.exec(String.format(STOP_COMMAND_FORMAT, agentDirectory));
            IOUtils.readFully(command.getInputStream()).toString();
        } catch (IOException e) {
            LOG.debug("stopAgentOnVM error", e);
        }
    }
}