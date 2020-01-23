package com.macstadium.orka;

import jetbrains.buildServer.agent.Constants;
import jetbrains.buildServer.clouds.CloudImageParameters;

import org.jetbrains.annotations.NotNull;

public class OrkaConstants {
    @NotNull
    public static final String TYPE = "orka";
    @NotNull
    public static final String IMAGES_PROFILE_SETTING = "images";
    @NotNull
    public static final String IMAGE_ID_PARAM_NAME = "cloud.orka.image.id";
    @NotNull
    public static final String INSTANCE_ID_PARAM_NAME = "cloud.orka.instance.id";
    @NotNull
    public static final String ORKA_ENDPOINT = "cloud.orka.endpoint";
    @NotNull
    public static final String ORKA_USER = "cloud.orka.user";
    @NotNull
    public static final String ORKA_PASSWORD = Constants.SECURE_PROPERTY_PREFIX + "cloud.orka.password";
    @NotNull
    public static final String VM_NAME = "cloud.orka.vm.name";
    @NotNull
    public static final String VM_USER = "cloud.orka.vm.user";
    @NotNull
    public static final String VM_PASSWORD = Constants.SECURE_PROPERTY_PREFIX + "cloud.orka.vm.password";
    @NotNull
    public static final String AGENT_DIRECTORY = "cloud.orka.vm.agent.directory";
    @NotNull
    public static final String INSTANCE_LIMIT = "cloud.orka.vm.limit";
    @NotNull
    public static final String NODE_MAPPINGS = "cloud.orka.node.mappings";
    @NotNull
    public static final int UNLIMITED_INSTANCES = -1;

    public String getOrkaEndpoint() {
        return ORKA_ENDPOINT;
    }

    public String getOrkaUser() {
        return ORKA_USER;
    }

    public String getOrkaPassword() {
        return ORKA_PASSWORD;
    }

    public String getVmName() {
        return VM_NAME;
    }

    public String getVmUser() {
        return VM_USER;
    }

    public String getVmPassword() {
        return VM_PASSWORD;
    }

    public String getAgentPoolId() {
        return CloudImageParameters.AGENT_POOL_ID_FIELD;
    }

    public String getAgentDirectory() {
        return AGENT_DIRECTORY;
    }

    public String getInstanceLimit() {
        return INSTANCE_LIMIT;
    }

    public String getNodeMappings() {
        return NODE_MAPPINGS;
    }
}