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
    private String token;

    public OrkaClient(String endpoint, String token) throws IOException {
        this.endpoint = endpoint;
        this.token = token;
    }

    public VMConfigResponse getVMConfigs() throws IOException {
        HttpResponse httpResponse = this.get(String.format("%s/%s", this.endpoint, VM_CONFIG_PATH));

        VMConfigResponse response = JsonHelper.fromJson(httpResponse.getBody(), VMConfigResponse.class);
        response.setHttpResponse(httpResponse);
        return response;
    }

    public VMResponse getVM(String vmName, String namespace) throws IOException {
        HttpResponse httpResponse = this
                .get(String.format("%s/%s/%s/%s/%s", this.endpoint, RESOURCE_PATH, namespace, VM_PATH, vmName));

        VMResponse response = JsonHelper.fromJson(httpResponse.getBody(), VMResponse.class);
        response.setHttpResponse(httpResponse);
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

    public DeploymentResponse deployVM(String vmConfig, String namespace) throws IOException {
        DeploymentRequest deploymentRequest = new DeploymentRequest(vmConfig);
        String deploymentRequestJson = new Gson().toJson(deploymentRequest);

        HttpResponse httpResponse = this.post(
                String.format("%s/%s/%s/%s", this.endpoint, RESOURCE_PATH, namespace, VM_PATH), deploymentRequestJson);
        DeploymentResponse response = JsonHelper.fromJson(httpResponse.getBody(), DeploymentResponse.class);
        response.setHttpResponse(httpResponse);

        return response;
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
        return new Request.Builder().addHeader(AUTHORIZATION_HEADER, BEARER + this.token).url(url);
    }

    private HttpResponse executeCall(Request request) throws IOException {
        LOG.debug("Executing request to Orka API: " + '/' + request.method() + ' ' + request.url());
        try (Response response = client.newCall(request).execute()) {
            ResponseBody body = response.body();
            return new HttpResponse(body != null ? body.string() : null, response.code(), response.isSuccessful());
        }
    }
}
