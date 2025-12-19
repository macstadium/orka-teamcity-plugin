package com.macstadium.orka;

import com.intellij.openapi.diagnostic.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import jetbrains.buildServer.clouds.CloudClientFactory;
import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.CloudRegistrar;
import jetbrains.buildServer.clouds.CloudState;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.web.openapi.PluginDescriptor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OrkaCloudClientFactory implements CloudClientFactory {
  private static final Logger LOG = Logger.getInstance(Loggers.CLOUD_CATEGORY_ROOT + OrkaConstants.TYPE);
  @NotNull
  private final String jspPath;
  private final ExecutorServices executorServices;

  public OrkaCloudClientFactory(@NotNull final CloudRegistrar cloudRegistrar,
      @NotNull final PluginDescriptor pluginDescriptor, @NotNull final ExecutorServices executorServices) {
    this.jspPath = pluginDescriptor.getPluginResourcesPath("settings.html");
    this.executorServices = executorServices;
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
    String profileId = state.getProfileId();
    LOG.debug(String.format("Creating new OrkaCloudClient for profile: %s", profileId));
    return new OrkaCloudClient(params, executorServices, profileId, state);
  }
}
