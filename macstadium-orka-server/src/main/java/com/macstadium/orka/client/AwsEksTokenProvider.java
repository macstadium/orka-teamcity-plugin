package com.macstadium.orka.client;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.macstadium.orka.OrkaConstants;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import jetbrains.buildServer.log.Loggers;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4PresignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;

/**
 * Token provider that generates EKS authentication tokens using AWS IAM
 * credentials.
 * This implementation uses IRSA (IAM Roles for Service Accounts) when running
 * in EKS.
 * 
 * The token generation process mirrors the `aws eks get-token` CLI command:
 * 1. Get AWS credentials from the environment (IRSA, instance profile, etc.)
 * 2. Create a presigned STS GetCallerIdentity request with x-k8s-aws-id header
 * 3. Base64 encode the presigned URL with "k8s-aws-v1." prefix
 */
public class AwsEksTokenProvider implements TokenProvider {
  private static final Logger LOG = Logger.getInstance(Loggers.CLOUD_CATEGORY_ROOT + OrkaConstants.TYPE);

  private static final String TOKEN_PREFIX = "k8s-aws-v1.";
  private static final String K8S_AWS_ID_HEADER = "x-k8s-aws-id";
  private static final String STS_SERVICE = "sts";
  private static final String STS_ACTION = "GetCallerIdentity";
  private static final String STS_VERSION = "2011-06-15";

  // Token is valid for 15 minutes, refresh at 14 minutes to avoid edge cases
  private static final Duration TOKEN_REFRESH_THRESHOLD = Duration.ofMinutes(14);
  private static final Duration PRESIGN_DURATION = Duration.ofMinutes(15);

  // IRSA environment variables
  private static final String AWS_WEB_IDENTITY_TOKEN_FILE = "AWS_WEB_IDENTITY_TOKEN_FILE";
  private static final String AWS_ROLE_ARN = "AWS_ROLE_ARN";

  private final String clusterName;
  private final Region region;
  private final AwsCredentialsProvider credentialsProvider;

  // volatile ensures visibility across threads for cached token state
  private volatile String cachedToken;
  private volatile Instant tokenExpiry;

  /**
   * Creates a new AWS EKS token provider.
   *
   * @param clusterName EKS cluster name (used in x-k8s-aws-id header)
   * @param regionName  AWS region where the cluster is located
   * @throws IllegalArgumentException if clusterName or regionName is null or
   *                                  empty
   */
  public AwsEksTokenProvider(String clusterName, String regionName) {
    if (StringUtil.isEmpty(clusterName)) {
      throw new IllegalArgumentException("EKS cluster name must not be null or empty");
    }
    if (StringUtil.isEmpty(regionName)) {
      throw new IllegalArgumentException("AWS region must not be null or empty");
    }

    this.clusterName = clusterName;
    this.region = Region.of(regionName);
    this.credentialsProvider = createCredentialsProvider();

    LOG.debug(String.format("AwsEksTokenProvider initialized: cluster='%s', region='%s'",
        clusterName, regionName));
  }

  /**
   * Creates the appropriate credentials provider based on available environment.
   * Prefers IRSA (Web Identity Token) if available, falls back to default chain.
   */
  private AwsCredentialsProvider createCredentialsProvider() {
    String tokenFile = System.getenv(AWS_WEB_IDENTITY_TOKEN_FILE);
    String roleArn = System.getenv(AWS_ROLE_ARN);

    LOG.debug(String.format("AWS environment: %s=%s, %s=%s",
        AWS_WEB_IDENTITY_TOKEN_FILE, tokenFile != null ? "SET" : "NOT SET",
        AWS_ROLE_ARN, roleArn != null ? "SET" : "NOT SET"));

    if (tokenFile != null && roleArn != null) {
      if (Files.exists(Paths.get(tokenFile))) {
        try {
          LOG.debug("Using IRSA credentials provider");
          return WebIdentityTokenFileCredentialsProvider.create();
        } catch (Exception e) {
          LOG.warn(String.format("Failed to create IRSA provider: %s, using default", e.getMessage()));
        }
      } else {
        LOG.warn(String.format("IRSA token file not found: %s", tokenFile));
      }
    }

    return DefaultCredentialsProvider.builder()
        .asyncCredentialUpdateEnabled(false)
        .build();
  }

  @Override
  public synchronized String getToken() throws IOException {
    if (isTokenExpired()) {
      refreshToken();
    }
    return cachedToken;
  }

  @Override
  public boolean isValid() {
    if (StringUtil.isEmpty(clusterName)) {
      LOG.warn("AWS EKS cluster name is not configured");
      return false;
    }

    try {
      AwsCredentials credentials = credentialsProvider.resolveCredentials();
      LOG.debug(String.format("AWS credentials resolved successfully (access key: %s...)",
          credentials.accessKeyId().substring(0, Math.min(8, credentials.accessKeyId().length()))));
      return true;
    } catch (Exception e) {
      LOG.warn("AWS credentials not available: " + e.getMessage());
      return false;
    }
  }

  private boolean isTokenExpired() {
    return cachedToken == null
        || tokenExpiry == null
        || Instant.now().isAfter(tokenExpiry);
  }

  private void refreshToken() throws IOException {
    try {
      LOG.debug(String.format("Refreshing EKS token for cluster '%s'", clusterName));

      // Resolve AWS credentials
      AwsCredentials credentials = credentialsProvider.resolveCredentials();

      // Build STS endpoint URL
      String stsHost = String.format("sts.%s.amazonaws.com", region.id());
      URI stsUri = new URI("https", stsHost, "/", null);

      // Create the request to be presigned
      SdkHttpFullRequest request = SdkHttpFullRequest.builder()
          .method(SdkHttpMethod.GET)
          .uri(stsUri)
          .appendRawQueryParameter("Action", STS_ACTION)
          .appendRawQueryParameter("Version", STS_VERSION)
          .putHeader(K8S_AWS_ID_HEADER, clusterName)
          .putHeader("Host", stsHost)
          .build();

      // Set up presigning parameters
      Instant signTime = Instant.now();
      Aws4PresignerParams presignerParams = Aws4PresignerParams.builder()
          .awsCredentials(credentials)
          .signingRegion(region)
          .signingName(STS_SERVICE)
          .signingClockOverride(Clock.fixed(signTime, ZoneOffset.UTC))
          .expirationTime(signTime.plus(PRESIGN_DURATION))
          .build();

      // Presign the request
      Aws4Signer signer = Aws4Signer.create();
      SdkHttpFullRequest signedRequest = signer.presign(request, presignerParams);

      // Generate the token
      String presignedUrl = signedRequest.getUri().toString();
      cachedToken = TOKEN_PREFIX + Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(presignedUrl.getBytes());
      tokenExpiry = Instant.now().plus(TOKEN_REFRESH_THRESHOLD);

      LOG.info(String.format("EKS token refreshed for cluster '%s', valid until %s", clusterName, tokenExpiry));

    } catch (URISyntaxException e) {
      LOG.error(String.format("Invalid STS URI for region '%s': %s", region.id(), e.getMessage()), e);
      throw new IOException("Failed to construct STS URI: " + e.getMessage(), e);
    } catch (Exception e) {
      LOG.error(String.format("Failed to refresh EKS token for cluster '%s': %s", clusterName, e.getMessage()), e);
      throw new IOException("Failed to get EKS token: " + e.getMessage(), e);
    }
  }
}
