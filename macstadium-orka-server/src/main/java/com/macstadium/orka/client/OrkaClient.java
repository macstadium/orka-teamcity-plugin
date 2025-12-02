package com.macstadium.orka.client;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.macstadium.orka.OrkaConstants;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import jetbrains.buildServer.log.Loggers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class OrkaClient {
  private static final OkHttpClient client = new OkHttpClient.Builder().readTimeout(15, TimeUnit.MINUTES).build();
  private static final Logger LOG = Logger.getInstance(Loggers.CLOUD_CATEGORY_ROOT + OrkaConstants.TYPE);

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER = "Bearer ";
  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
  private static final String RESOURCE_PATH = "api/v1/namespaces";
  private static final String VM_CONFIG_PATH = RESOURCE_PATH + "/orka-default/vmconfigs";
  private static final String VM_PATH = "vms";
  private static final String NODE_PATH = "nodes";
  private static final String IMAGE_PATH = RESOURCE_PATH + "/orka-default/images";

  private String endpoint;
  private TokenProvider tokenProvider;

  /**
   * Creates OrkaClient with a TokenProvider for flexible authentication.
   *
   * @param endpoint      Orka API endpoint URL
   * @param tokenProvider Provider for authentication tokens
   */
  public OrkaClient(String endpoint, TokenProvider tokenProvider) {
    this.endpoint = endpoint;
    this.tokenProvider = tokenProvider;
  }

  /**
   * Creates OrkaClient with a static token (backward compatibility).
   *
   * @param endpoint Orka API endpoint URL
   * @param token    Static Bearer token
   */
  public OrkaClient(String endpoint, String token) throws IOException {
    this(endpoint, new StaticTokenProvider(token));
  }

  public VMConfigResponse getVMConfigs() throws IOException {
    String url = String.format("%s/%s", this.endpoint, VM_CONFIG_PATH);
    LOG.debug(String.format("Fetching VM configs from: %s", url));

    HttpResponse httpResponse = this.get(url);

    if (!httpResponse.getIsSuccessful()) {
      LOG.warn(String.format("Failed to get VM configs: HTTP %d - %s",
          httpResponse.getCode(), httpResponse.getBody()));
    } else {
      LOG.debug(String.format("VM configs loaded: HTTP %d", httpResponse.getCode()));
    }

    VMConfigResponse response = JsonHelper.fromJson(httpResponse.getBody(), VMConfigResponse.class);
    response.setHttpResponse(httpResponse);
    return response;
  }

  /**
   * Gets a specific VM config by name.
   *
   * @param vmConfigName Name of the VM config
   * @param namespace    Namespace to search in
   * @return OrkaVMConfig or null if not found
   */
  public OrkaVMConfig getVMConfig(String vmConfigName, String namespace) throws IOException {
    String url = String.format("%s/%s/%s/vmconfigs/%s", this.endpoint, RESOURCE_PATH, namespace, vmConfigName);
    LOG.debug(String.format("Fetching VM config '%s' from namespace '%s'", vmConfigName, namespace));

    HttpResponse httpResponse = this.get(url);

    if (!httpResponse.getIsSuccessful()) {
      LOG.warn(String.format("Failed to get VM config '%s': HTTP %d - %s",
          vmConfigName, httpResponse.getCode(), httpResponse.getBody()));
      return null;
    }

    OrkaVMConfig vmConfig = JsonHelper.fromJson(httpResponse.getBody(), OrkaVMConfig.class);
    LOG.debug(String.format("VM config '%s': cpu=%d, memory=%s, tag=%s, tagRequired=%s, nodeName=%s",
        vmConfigName,
        vmConfig != null ? vmConfig.getCPU() : 0,
        vmConfig != null ? vmConfig.getMemory() : "null",
        vmConfig != null ? vmConfig.getTag() : "null",
        vmConfig != null ? vmConfig.isTagRequired() : false,
        vmConfig != null ? vmConfig.getNodeName() : "null"));
    return vmConfig;
  }

  /**
   * Checks if there is capacity to deploy a VM with the given config.
   * Takes into account:
   * - Required CPU and memory
   * - Tag requirements (if tagRequired is true, only nodes with matching tag are considered)
   * - Node status (only READY nodes)
   *
   * @param vmConfigName Name of the VM config
   * @param namespace    Namespace to check
   * @return CapacityInfo with result and details
   */
  public CapacityInfo checkCapacity(String vmConfigName, String namespace) throws IOException {
    LOG.debug(String.format("Checking capacity for VM config '%s' in namespace '%s'", vmConfigName, namespace));

    // 1. Get VM Config
    OrkaVMConfig vmConfig = this.getVMConfig(vmConfigName, namespace);
    if (vmConfig == null) {
      return CapacityInfo.checkFailed(String.format("VM config '%s' not found in namespace '%s'",
          vmConfigName, namespace));
    }

    int requiredCpu = vmConfig.getCPU();
    float requiredMemoryGb = vmConfig.getMemoryAsFloat();
    String requiredTag = vmConfig.getTag();
    boolean tagRequired = vmConfig.isTagRequired();
    String pinnedNodeName = vmConfig.getNodeName();

    LOG.debug(String.format("VM config requirements: cpu=%d, memory=%.1fG, tag='%s', tagRequired=%s, nodeName='%s'",
        requiredCpu, requiredMemoryGb, requiredTag, tagRequired, pinnedNodeName));

    // 1.5. Get running VMs to check VM count per node (ARM limit: 2 VMs per host)
    java.util.Map<String, Integer> vmCountPerNode = new java.util.HashMap<>();
    try {
      vmCountPerNode = this.countVMsPerNode(namespace);
    } catch (Exception e) {
      LOG.warn(String.format("Failed to get VMs list: %s", e.getMessage()));
    }

    // 2. Get Nodes
    NodeResponse nodeResponse = this.getNodes(namespace);
    if (!nodeResponse.isSuccessful() || nodeResponse.getNodes() == null) {
      return CapacityInfo.checkFailed("Failed to get nodes: " + nodeResponse.getMessage());
    }

    java.util.List<OrkaNode> allNodes = nodeResponse.getNodes();
    if (allNodes.isEmpty()) {
      return CapacityInfo.noCapacity("No nodes available in namespace " + namespace);
    }

    LOG.debug(String.format("Total nodes fetched: %d", allNodes.size()));

    // ARM hosts have a limit of 2 VMs
    final int MAX_VMS_PER_ARM_NODE = 2;

    // 2.5. If VM config is pinned to specific node, check only that node
    if (vmConfig.isPinnedToNode()) {
      LOG.debug(String.format("VM config is pinned to node '%s', checking only that node", pinnedNodeName));
      OrkaNode pinnedNode = null;
      for (OrkaNode node : allNodes) {
        if (pinnedNodeName.equals(node.getName())) {
          pinnedNode = node;
          break;
        }
      }
      if (pinnedNode == null) {
        return CapacityInfo.noCapacity(String.format("Pinned node '%s' not found", pinnedNodeName));
      }
      if (!pinnedNode.isReady()) {
        return CapacityInfo.noCapacity(String.format("Pinned node '%s' is not ready (status=%s)",
            pinnedNodeName, pinnedNode.getPhase()));
      }
      // Check VM limit on pinned node
      int vmsOnPinnedNode = vmCountPerNode.getOrDefault(pinnedNodeName, 0);
      LOG.debug(String.format("Pinned node '%s' has %d VMs (limit: %d)", 
          pinnedNodeName, vmsOnPinnedNode, MAX_VMS_PER_ARM_NODE));
      if (vmsOnPinnedNode >= MAX_VMS_PER_ARM_NODE) {
        return CapacityInfo.noCapacity(String.format("Pinned node '%s' has reached VM limit (%d/%d VMs)",
            pinnedNodeName, vmsOnPinnedNode, MAX_VMS_PER_ARM_NODE));
      }
      if (pinnedNode.getAvailableCpu() < requiredCpu) {
        return CapacityInfo.noCapacity(String.format("Pinned node '%s' has not enough CPU (%d available, %d required)",
            pinnedNodeName, pinnedNode.getAvailableCpu(), requiredCpu));
      }
      float availableMemoryGb = pinnedNode.getAvailableMemoryAsFloat();
      if (availableMemoryGb < requiredMemoryGb) {
        return CapacityInfo.noCapacity(String.format("Pinned node '%s' has not enough memory (%.1fG available, %.1fG required)",
            pinnedNodeName, availableMemoryGb, requiredMemoryGb));
      }
      // Pinned node is OK
      LOG.debug(String.format("Pinned node '%s' has capacity: cpu=%d, memory=%.1fG, vms=%d/%d",
          pinnedNodeName, pinnedNode.getAvailableCpu(), availableMemoryGb, vmsOnPinnedNode, MAX_VMS_PER_ARM_NODE));
      return new CapacityInfo(pinnedNode.getAvailableCpu(), (long) (availableMemoryGb * 1024),
          allNodes.size(), 1, true, String.format("Pinned node '%s' has capacity", pinnedNodeName));
    }

    // 3. Filter nodes
    java.util.List<OrkaNode> eligibleNodes = new java.util.ArrayList<>();
    int totalReadyNodes = 0;
    int nodesWithMatchingTag = 0;
    int nodesWithEnoughCpu = 0;
    int nodesWithEnoughMemory = 0;
    int nodesWithVmSlots = 0;

    for (OrkaNode node : allNodes) {
      // Check if node is ready
      if (!node.isReady()) {
        LOG.debug(String.format("Node %s skipped: not ready (status=%s)", node.getName(), node.getPhase()));
        continue;
      }
      totalReadyNodes++;

      // Check tag requirement
      if (tagRequired && requiredTag != null && !requiredTag.isEmpty()) {
        if (!node.hasTag(requiredTag)) {
          LOG.debug(String.format("Node %s skipped: missing tag '%s' (has: %s)",
              node.getName(), requiredTag, node.getTags()));
          continue;
        }
        nodesWithMatchingTag++;
      } else {
        nodesWithMatchingTag++;
      }

      // Check VM limit (ARM hosts: max 2 VMs)
      int vmsOnNode = vmCountPerNode.getOrDefault(node.getName(), 0);
      if (vmsOnNode >= MAX_VMS_PER_ARM_NODE) {
        LOG.debug(String.format("Node %s skipped: VM limit reached (%d/%d)", 
            node.getName(), vmsOnNode, MAX_VMS_PER_ARM_NODE));
        continue;
      }
      nodesWithVmSlots++;

      // Check CPU
      if (node.getAvailableCpu() < requiredCpu) {
        LOG.debug(String.format("Node %s skipped: not enough CPU (%d/%d)",
            node.getName(), node.getAvailableCpu(), requiredCpu));
        continue;
      }
      nodesWithEnoughCpu++;

      // Check Memory
      float availableMemoryGb = node.getAvailableMemoryAsFloat();
      if (availableMemoryGb < requiredMemoryGb) {
        LOG.debug(String.format("Node %s skipped: not enough memory (%.1fG/%.1fG)",
            node.getName(), availableMemoryGb, requiredMemoryGb));
        continue;
      }
      nodesWithEnoughMemory++;

      // Node is eligible
      eligibleNodes.add(node);
      LOG.debug(String.format("Node %s is eligible: cpu=%d, memory=%.1fG, vms=%d/%d",
          node.getName(), node.getAvailableCpu(), availableMemoryGb, vmsOnNode, MAX_VMS_PER_ARM_NODE));
    }

    // 4. Build result
    if (eligibleNodes.isEmpty()) {
      String reason = buildNoCapacityReason(allNodes.size(), totalReadyNodes, nodesWithMatchingTag,
          nodesWithVmSlots, nodesWithEnoughCpu, nodesWithEnoughMemory, 
          requiredTag, tagRequired, requiredCpu, requiredMemoryGb, MAX_VMS_PER_ARM_NODE);
      return CapacityInfo.noCapacity(reason);
    }

    // Calculate total available resources from eligible nodes
    int totalAvailableCpu = eligibleNodes.stream().mapToInt(OrkaNode::getAvailableCpu).sum();
    long totalAvailableMemoryMb = (long) (eligibleNodes.stream()
        .map(OrkaNode::getAvailableMemoryAsFloat)
        .reduce(0f, Float::sum) * 1024);

    String message = String.format("Found %d eligible node(s)", eligibleNodes.size());
    LOG.debug(message);

    return new CapacityInfo(totalAvailableCpu, totalAvailableMemoryMb,
        allNodes.size(), eligibleNodes.size(), true, message);
  }

  private String buildNoCapacityReason(int totalNodes, int readyNodes, int nodesWithTag,
      int nodesWithVmSlots, int nodesWithCpu, int nodesWithMemory, 
      String requiredTag, boolean tagRequired,
      int requiredCpu, float requiredMemoryGb, int maxVmsPerNode) {
    if (readyNodes == 0) {
      return String.format("No ready nodes (total: %d)", totalNodes);
    }
    if (tagRequired && requiredTag != null && nodesWithTag == 0) {
      return String.format("No nodes with required tag '%s' (ready nodes: %d)", requiredTag, readyNodes);
    }
    if (nodesWithVmSlots == 0) {
      return String.format("All nodes with tag '%s' have reached VM limit (%d VMs per node)", 
          requiredTag, maxVmsPerNode);
    }
    if (nodesWithCpu == 0) {
      return String.format("No nodes with enough CPU (required: %d, nodes with VM slots: %d)",
          requiredCpu, nodesWithVmSlots);
    }
    if (nodesWithMemory == 0) {
      return String.format("No nodes with enough memory (required: %.1fG, nodes with CPU: %d)",
          requiredMemoryGb, nodesWithCpu);
    }
    return "No eligible nodes found";
  }

  public VMResponse getVM(String vmName, String namespace) throws IOException {
    LOG.debug(String.format("Getting VM status for %s in namespace %s", vmName, namespace));

    HttpResponse httpResponse = this
        .get(String.format("%s/%s/%s/%s/%s", this.endpoint, RESOURCE_PATH, namespace, VM_PATH, vmName));

    LOG.debug(String.format("VM status API response: %s", httpResponse.getBody()));

    VMResponse response = JsonHelper.fromJson(httpResponse.getBody(), VMResponse.class);
    response.setHttpResponse(httpResponse);

    // Only log successful responses, not authorization errors
    if (response.isSuccessful() && response.getIP() != null) {
      LOG.debug(String.format("VM %s found: IP=%s, SSH port=%d",
          vmName, response.getIP(), response.getSSH()));
    } else if (!response.isSuccessful()) {
      LOG.warn(String.format("Failed to get VM %s status: %s (HTTP %d)",
          vmName, response.getMessage(),
          httpResponse.getCode()));
    }

    return response;
  }

  /**
   * Gets list of all running VMs in a namespace.
   * Used to count VMs per node for capacity checking (ARM hosts have 2 VM limit).
   */
  public VMsResponse getVMs(String namespace) throws IOException {
    String url = String.format("%s/%s/%s/%s", this.endpoint, RESOURCE_PATH, namespace, VM_PATH);
    
    HttpResponse httpResponse = this.get(url);
    String body = httpResponse.getBody();
    
    VMsResponse response = JsonHelper.fromJson(body, VMsResponse.class);
    response.setHttpResponse(httpResponse);
    
    if (response.getVMs() != null) {
      LOG.debug(String.format("Fetched %d VMs from namespace '%s'", response.getVMs().size(), namespace));
    }
    
    return response;
  }
  
  /**
   * Counts running VMs per node.
   * @return Map of nodeName -> VM count
   */
  public java.util.Map<String, Integer> countVMsPerNode(String namespace) throws IOException {
    VMsResponse response = this.getVMs(namespace);
    java.util.Map<String, Integer> vmCountPerNode = new java.util.HashMap<>();
    
    for (OrkaVM vm : response.getVMs()) {
      if (vm.isRunning() && vm.getNode() != null) {
        vmCountPerNode.merge(vm.getNode(), 1, Integer::sum);
      }
    }
    
    LOG.debug(String.format("VM count per node: %s", vmCountPerNode));
    return vmCountPerNode;
  }

  public NodeResponse getNodes(String namespace) throws IOException {
    String url = String.format("%s/%s/%s/%s", this.endpoint, RESOURCE_PATH, namespace, NODE_PATH);
    LOG.debug(String.format("Fetching nodes from namespace '%s'", namespace));
    
    HttpResponse httpResponse = this.get(url);
    String body = httpResponse.getBody();
    
    NodeResponse response;
    // Orka 3 API returns array directly, not {"items": [...]}
    if (body != null && body.trim().startsWith("[")) {
      // Parse as array
      OrkaNode[] nodesArray = JsonHelper.fromJson(body, OrkaNode[].class);
      response = new NodeResponse(
          nodesArray != null ? java.util.Arrays.asList(nodesArray) : java.util.Collections.emptyList(),
          null);
    } else {
      // Parse as object with "items" field
      response = JsonHelper.fromJson(body, NodeResponse.class);
    }
    response.setHttpResponse(httpResponse);
    
    if (response.getNodes() != null) {
      LOG.debug(String.format("Fetched %d nodes from namespace '%s'", response.getNodes().size(), namespace));
    }
    
    return response;
  }

  public ImageResponse getImages() throws IOException {
    HttpResponse httpResponse = this.get(String.format("%s/%s", this.endpoint, IMAGE_PATH));

    ImageResponse response = JsonHelper.fromJson(httpResponse.getBody(), ImageResponse.class);
    response.setHttpResponse(httpResponse);

    return response;
  }

  public DeploymentResponse deployVM(String vmName, String vmConfig, String namespace, String vmMetadata)
      throws IOException {
    DeploymentRequest deploymentRequest;

    if (StringUtil.isNotEmpty(vmMetadata)) {
      deploymentRequest = new DeploymentRequest(vmName, vmConfig, vmMetadata);
    } else {
      deploymentRequest = new DeploymentRequest(vmName, vmConfig);
    }

    String deploymentRequestJson = new Gson().toJson(deploymentRequest);
    LOG.debug(String.format("Request JSON: %s", deploymentRequestJson));

    HttpResponse httpResponse = this.post(
        String.format("%s/%s/%s/%s", this.endpoint, RESOURCE_PATH, namespace, VM_PATH), deploymentRequestJson);

    LOG.debug(String.format("API response: %s", httpResponse.getBody()));

    DeploymentResponse response = JsonHelper.fromJson(httpResponse.getBody(), DeploymentResponse.class);
    response.setHttpResponse(httpResponse);

    return response;
  }

  public DeploymentResponse deployVM(String vmName, String vmConfig, String namespace) throws IOException {
    return deployVM(vmName, vmConfig, namespace, null);
  }

  public DeletionResponse deleteVM(String vmName, String namespace) throws IOException {
    HttpResponse httpResponse = this
        .delete(String.format("%s/%s/%s/%s/%s", this.endpoint, RESOURCE_PATH, namespace, VM_PATH, vmName));
    DeletionResponse response;
    String body = httpResponse.getBody();
    if (StringUtil.isEmptyOrSpaces(body)) {
      response = new DeletionResponse(null);
    } else {
      response = JsonHelper.fromJson(httpResponse.getBody(), DeletionResponse.class);
    }
    response.setHttpResponse(httpResponse);

    return response;
  }

  @VisibleForTesting
  HttpResponse post(String url, String body) throws IOException {
    RequestBody requestBody = RequestBody.create(JSON, body);
    Request request = this.getAuthenticatedBuilder(url).post(requestBody).build();
    return executeCall(request);
  }

  @VisibleForTesting
  HttpResponse get(String url) throws IOException {
    Request request = this.getAuthenticatedBuilder(url).get().build();
    return this.executeCall(request);
  }

  @VisibleForTesting
  HttpResponse delete(String url) throws IOException {
    Request request = this.getAuthenticatedBuilder(url).delete().build();
    return executeCall(request);
  }

  private Builder getAuthenticatedBuilder(String url) throws IOException {
    return new Request.Builder()
        .addHeader(AUTHORIZATION_HEADER, BEARER + this.tokenProvider.getToken())
        .url(url);
  }

  private HttpResponse executeCall(Request request) throws IOException {
    LOG.debug("Executing request to Orka API: " + '/' + request.method() + ' ' + request.url());
    try (Response response = client.newCall(request).execute()) {
      ResponseBody body = response.body();
      return new HttpResponse(body != null ? body.string() : null, response.code(), response.isSuccessful());
    }
  }
}
