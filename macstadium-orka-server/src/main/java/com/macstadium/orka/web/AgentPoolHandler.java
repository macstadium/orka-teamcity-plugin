package com.macstadium.orka.web;

import com.intellij.openapi.diagnostic.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import jetbrains.buildServer.BuildProject;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager;

import org.jdom.Element;

public class AgentPoolHandler implements RequestHandler {
    private static final Logger LOG = Logger.getInstance(AgentPoolHandler.class.getName());

    private final AgentPoolManager manager;

    private static final String DEFAULT_POOL_ID = "-2";

    public AgentPoolHandler(AgentPoolManager manager) {
        this.manager = manager;
    }

    public Element handle(Map<String, String> params) {
        String projectId = params.get("projectId");
        LOG.info(String.format("AgentPoolHandler: Getting pools for projectId: %s", projectId));

        Map<String, String> pools = this.getAgentPools(projectId);
        LOG.info(String.format("AgentPoolHandler: Found %d pools for projectId %s: %s", 
            pools.size(), projectId, pools));
        
        Element result = new Element("agentPools");
        pools.entrySet().forEach(r -> {
            result.addContent(this.getPool(r.getKey(), r.getValue()));
        });

        LOG.info(String.format("AgentPoolHandler: Returning XML: %s", result));
        return result;
    }

    private Map<String, String> getAgentPools(String projectId) {
        Map<String, String> pools = new LinkedHashMap<String, String>();
        if (projectId != BuildProject.ROOT_PROJECT_ID) {
            pools.put(DEFAULT_POOL_ID, "<Project Pool>");
        }
        if (projectId != null && projectId != BuildProject.ROOT_PROJECT_ID) {
            pools.putAll(this.getProjectPools(projectId));
        }

        return pools;
    }

    private Map<String, String> getProjectPools(String projectId) {
        return this.manager.getProjectOwnedAgentPools(projectId).stream()
                .collect(Collectors.toMap(k -> Integer.toString(k.getAgentPoolId()), k -> k.getName()));
    }

    private Element getPool(String id, String name) {
        return new Element("agentPool").setAttribute("id", id).addContent(name);
    }
}