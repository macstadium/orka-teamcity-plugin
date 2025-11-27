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

  public NodeResponse getNodes(String namespace) throws IOException {
    HttpResponse httpResponse = this
        .get(String.format("%s/%s/%s/%s", this.endpoint, RESOURCE_PATH, namespace, NODE_PATH));
    NodeResponse response = JsonHelper.fromJson(httpResponse.getBody(), NodeResponse.class);
    response.setHttpResponse(httpResponse);
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
      LOG.info(String.format("Deploying VM: name=%s, config=%s, namespace=%s, metadata=%s",
          vmName, vmConfig, namespace, deploymentRequest.getCustomMetadata()));
    } else {
      deploymentRequest = new DeploymentRequest(vmName, vmConfig);
      LOG.info(String.format("Deploying VM: name=%s, config=%s, namespace=%s", vmName, vmConfig, namespace));
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
