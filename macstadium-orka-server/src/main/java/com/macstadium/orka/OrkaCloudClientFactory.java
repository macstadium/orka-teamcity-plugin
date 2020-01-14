package com.macstadium.orka;

import com.intellij.openapi.diagnostic.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import jetbrains.buildServer.clouds.CloudClientFactory;
import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.CloudRegistrar;
import jetbrains.buildServer.clouds.CloudState;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.web.openapi.PluginDescriptor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OrkaCloudClientFactory implements CloudClientFactory {
    private static final Logger LOG = Logger.getInstance(OrkaCloudClientFactory.class.getName());
    @NotNull
    private final String jspPath;

    public OrkaCloudClientFactory(@NotNull final CloudRegistrar cloudRegistrar,
            @NotNull final PluginDescriptor pluginDescriptor) {
        this.jspPath = pluginDescriptor.getPluginResourcesPath("settings.html");
        cloudRegistrar.registerCloudFactory(this);
    }

    @NotNull
    public String getCloudCode() {
        return OrkaConstants.TYPE;
    }

    @NotNull
    public String getDisplayName() {
        return "Orka Cloud";
    }

    @Nullable
    public String getEditProfileUrl() {
        return this.jspPath;
    }

    @NotNull
    public Map<String, String> getInitialParameterValues() {
        return Collections.emptyMap();
    }

    @NotNull
    public PropertiesProcessor getPropertiesProcessor() {
        return new PropertiesProcessor() {
            @NotNull
            public Collection<InvalidProperty> process(@NotNull final Map<String, String> properties) {
                return Collections.emptyList();
            }
        };
    }

    public boolean canBeAgentOfType(@NotNull final AgentDescription agentDescription) {
        final Map<String, String> configParams = agentDescription.getConfigurationParameters();
        boolean hasOrkaImageId = configParams.containsKey(CommonConstants.IMAGE_ID_PARAM_NAME);
        boolean hasOrkaInstanceId = configParams.containsKey(CommonConstants.INSTANCE_ID_PARAM_NAME);
        LOG.debug(String.format("canBeAgentOfType with hasOrkaImageId: %s and hasOrkaInstanceId: %s", hasOrkaImageId,
                hasOrkaInstanceId));
        return hasOrkaImageId && hasOrkaInstanceId;
    }

    @NotNull
    public OrkaCloudClient createNewClient(@NotNull final CloudState state,
            @NotNull final CloudClientParameters params) {
        return new OrkaCloudClient(params);
    }
}
