package com.macstadium.orka.web;

import com.intellij.openapi.diagnostic.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;

public class Controller extends BaseFormXmlController {
  private static final Logger LOG = Logger.getInstance(Controller.class.getName());

  private final PluginDescriptor pluginDescriptor;
  private final String jspPath;
  private final String htmlPath;

  private Map<String, RequestHandler> handlers = new HashMap<String, RequestHandler>();

  public Controller(@NotNull final SBuildServer server, @NotNull final WebControllerManager webControllerManager,
      @NotNull final PluginDescriptor pluginDescriptor, @NotNull final AgentPoolManager poolManager) {
    super(server);
    this.pluginDescriptor = pluginDescriptor;
    this.jspPath = pluginDescriptor.getPluginResourcesPath("settings.jsp");
    this.htmlPath = pluginDescriptor.getPluginResourcesPath("settings.html");
    webControllerManager.registerController(this.htmlPath, this);

    LOG.debug("jspPath: " + this.jspPath);
    LOG.debug("htmlPath: " + this.htmlPath);

    handlers.put("vms", new VmHandler());
    handlers.put("agentPools", new AgentPoolHandler(poolManager));
  }

  @Override
  protected ModelAndView doGet(HttpServletRequest request, HttpServletResponse response) {
    ModelAndView modelAndView = new ModelAndView(this.jspPath);
    modelAndView.getModel().put("basePath", this.htmlPath);
    modelAndView.getModel().put("resPath", this.pluginDescriptor.getPluginResourcesPath());

    String projectId = request.getParameter("projectId");
    if (projectId == null || projectId.trim().isEmpty()) {
      projectId = "_Root"; // Fallback to root project
      LOG.debug("projectId parameter is null, using _Root");
    }
    modelAndView.getModel().put("projectId", projectId);
    LOG.debug(String.format("doGet with projectId: %s", projectId));

    return modelAndView;
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response, Element xmlResponse) {
    String resource = request.getParameterValues("resource")[0];
    LOG.debug(String.format("doPost with resource: %s", resource));
    Map<String, String> parameters = request.getParameterMap().keySet().stream()
        .collect(Collectors.toMap(k -> k, k -> request.getParameterValues(k)[0]));

    Element result = handlers.get(resource).handle(parameters);

    xmlResponse.addContent(result);
  }
}