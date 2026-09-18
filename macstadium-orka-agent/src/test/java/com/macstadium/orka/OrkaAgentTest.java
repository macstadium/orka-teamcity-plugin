package com.macstadium.orka;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import jetbrains.buildServer.agent.BuildAgentConfigurationEx;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class OrkaAgentTest {
    private BuildAgentConfigurationEx configuration;
    private VMMetadataClient metadataClient;
    private File tempDir;
    private File currentDir;

    @BeforeMethod
    public void setUp() throws Exception {
        this.configuration = mock(BuildAgentConfigurationEx.class);
        this.metadataClient = mock(VMMetadataClient.class);
        this.tempDir = Files.createTempDirectory("orka-agent-tmp").toFile();
        this.currentDir = Files.createTempDirectory("orka-agent-cwd").toFile();
    }

    public void when_metadata_file_in_temp_dir_should_configure_from_it_and_copy_it() throws Exception {
        this.writeMetadataFile(this.tempDir, "vm-1", "image-1", "starting-1");

        new OrkaAgent(this.configuration, this.metadataClient, this.tempDir, this.currentDir);

        verify(this.configuration).addConfigurationParameter(CommonConstants.INSTANCE_ID_PARAM_NAME, "vm-1");
        verify(this.configuration).addConfigurationParameter(CommonConstants.IMAGE_ID_PARAM_NAME, "image-1");
        verify(this.configuration).addConfigurationParameter(CommonConstants.STARTING_INSTANCE_ID_CONFIG_PARAM,
                "starting-1");
        assertTrue(new File(this.currentDir, CommonConstants.METADATA_FILE_PREFIX).exists());
        verify(this.metadataClient, never()).waitForFirstResponse(anyString(), anyLong(), anyLong());
    }

    public void when_metadata_file_in_current_dir_should_configure_from_it() throws Exception {
        this.writeMetadataFile(this.currentDir, "vm-1", "image-1");

        new OrkaAgent(this.configuration, this.metadataClient, this.tempDir, this.currentDir);

        verify(this.configuration).addConfigurationParameter(CommonConstants.INSTANCE_ID_PARAM_NAME, "vm-1");
        verify(this.configuration).addConfigurationParameter(CommonConstants.IMAGE_ID_PARAM_NAME, "image-1");
        verify(this.configuration, never()).addConfigurationParameter(
                eq(CommonConstants.STARTING_INSTANCE_ID_CONFIG_PARAM), anyString());
        verify(this.metadataClient, never()).waitForFirstResponse(anyString(), anyLong(), anyLong());
    }

    public void when_no_metadata_file_should_configure_from_the_metadata_service() throws Exception {
        this.stubMetadataService("vm-1", "image-1", "starting-1");

        new OrkaAgent(this.configuration, this.metadataClient, this.tempDir, this.currentDir);

        verify(this.configuration).addConfigurationParameter(CommonConstants.INSTANCE_ID_PARAM_NAME, "vm-1");
        verify(this.configuration).addConfigurationParameter(CommonConstants.IMAGE_ID_PARAM_NAME, "image-1");
        verify(this.configuration).addConfigurationParameter(CommonConstants.STARTING_INSTANCE_ID_CONFIG_PARAM,
                "starting-1");
    }

    public void when_metadata_service_has_no_starting_instance_id_should_skip_it() throws Exception {
        this.stubMetadataService("vm-1", "image-1", null);

        new OrkaAgent(this.configuration, this.metadataClient, this.tempDir, this.currentDir);

        verify(this.configuration).addConfigurationParameter(CommonConstants.INSTANCE_ID_PARAM_NAME, "vm-1");
        verify(this.configuration, never()).addConfigurationParameter(
                eq(CommonConstants.STARTING_INSTANCE_ID_CONFIG_PARAM), anyString());
    }

    public void when_metadata_service_never_answers_should_leave_the_agent_unconfigured() throws Exception {
        this.stubMetadataService("vm-1", null, null);

        new OrkaAgent(this.configuration, this.metadataClient, this.tempDir, this.currentDir);

        verify(this.configuration, never()).addConfigurationParameter(anyString(), anyString());
    }

    public void when_metadata_service_lacks_the_vm_name_should_leave_the_agent_unconfigured() throws Exception {
        this.stubMetadataService(null, "image-1", null);

        new OrkaAgent(this.configuration, this.metadataClient, this.tempDir, this.currentDir);

        verify(this.configuration, never()).addConfigurationParameter(anyString(), anyString());
    }

    private void stubMetadataService(String instanceId, String imageId, String startingInstanceId)
            throws Exception {
        when(this.metadataClient.waitForFirstResponse(anyString(), anyLong(), anyLong())).thenReturn(imageId);
        when(this.metadataClient.getValue(eq(CommonConstants.VM_NAME_METADATA_KEY), anyInt()))
                .thenReturn(instanceId);
        when(this.metadataClient.getValue(eq(CommonConstants.STARTING_INSTANCE_ID_METADATA_KEY), anyInt()))
                .thenReturn(startingInstanceId);
    }

    private void writeMetadataFile(File dir, String... lines) throws IOException {
        Files.write(new File(dir, CommonConstants.METADATA_FILE_PREFIX).toPath(), Arrays.asList(lines),
                StandardCharsets.UTF_8);
    }
}
