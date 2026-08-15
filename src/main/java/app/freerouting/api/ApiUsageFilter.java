package app.freerouting.api;

import app.freerouting.analytics.FRAnalytics;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Emits one canonical {@code API Usage} analytics row per HTTP request/response cycle.
 *
 * <p>This filter is the billing-grade source of truth for API traffic: it captures status, latency,
 * caller identity, a hashed API key, and a normalized route template on every tracked path
 * (including early 4xx/5xx aborts that never reach a controller).
 *
 * <p>Excluded paths match telemetry/docs endpoints ({@code /v1/analytics/*}, OpenAPI, Swagger UI,
 * {@code /.well-known/*}, {@code /dev/*}). {@code /v1/system/*} is tracked.
 *
 * <h2>Priority</h2>
 *
 * <p>Runs at {@link Priorities#USER} (5000), after authentication and environment-host validation
 * request filters.
 */
@Provider
@Priority(Priorities.USER)
public class ApiUsageFilter implements ContainerRequestFilter, ContainerResponseFilter {

  static final String PROP_START_NANO = "app.freerouting.api.usage.startNano";
  static final String PROP_HTTP_METHOD = "app.freerouting.api.usage.method";
  static final String PROP_API_PATH = "app.freerouting.api.usage.path";
  static final String PROP_API_ROUTE = "app.freerouting.api.usage.route";
  static final String PROP_API_KEY_HASH = "app.freerouting.api.usage.apiKeyHash";
  static final String PROP_PROFILE_ID = "app.freerouting.api.usage.profileId";
  static final String PROP_PROFILE_EMAIL = "app.freerouting.api.usage.profileEmail";
  static final String PROP_ENVIRONMENT_HOST = "app.freerouting.api.usage.environmentHost";
  static final String PROP_REQUEST_BYTES = "app.freerouting.api.usage.requestBytes";

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";
  private static final String PROFILE_ID_HEADER = "Freerouting-Profile-ID";
  private static final String PROFILE_EMAIL_HEADER = "Freerouting-Profile-Email";

  static String hashBearerToken(String authorizationHeader) {
    if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
      return null;
    }
    String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
    if (token.isEmpty()) {
      return null;
    }
    return sha256Hex(token);
  }

  static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private static UUID parseProfileUuid(String profileId) {
    if (profileId == null) {
      return null;
    }
    try {
      return UUID.fromString(profileId);
    } catch (IllegalArgumentException _) {
      return null;
    }
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static Long parseContentLength(String contentLengthHeader) {
    if (contentLengthHeader == null || contentLengthHeader.isBlank()) {
      return null;
    }
    try {
      long value = Long.parseLong(contentLengthHeader.trim());
      return value >= 0 ? value : null;
    } catch (NumberFormatException _) {
      return null;
    }
  }

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String path = requestContext.getUriInfo().getPath();
    if (ApiUsagePaths.isUsageTrackingExcluded(path)) {
      return;
    }

    requestContext.setProperty(PROP_START_NANO, System.nanoTime());
    requestContext.setProperty(PROP_HTTP_METHOD, requestContext.getMethod());
    requestContext.setProperty(PROP_API_PATH, requestContext.getUriInfo().getPath(true));
    requestContext.setProperty(
        PROP_API_ROUTE,
        ApiUsagePaths.normalizeRoute(
            requestContext.getMethod(), requestContext.getUriInfo().getPath(true)));
    requestContext.setProperty(
        PROP_API_KEY_HASH, hashBearerToken(requestContext.getHeaderString(AUTHORIZATION_HEADER)));
    requestContext.setProperty(
        PROP_PROFILE_ID, trimToNull(requestContext.getHeaderString(PROFILE_ID_HEADER)));
    requestContext.setProperty(
        PROP_PROFILE_EMAIL, trimToNull(requestContext.getHeaderString(PROFILE_EMAIL_HEADER)));
    requestContext.setProperty(
        PROP_ENVIRONMENT_HOST,
        trimToNull(requestContext.getHeaderString(EnvironmentHostValidationFilter.HEADER_NAME)));
    requestContext.setProperty(
        PROP_REQUEST_BYTES,
        parseContentLength(requestContext.getHeaders().getFirst("Content-Length")));
  }

  @Override
  public void filter(
      ContainerRequestContext requestContext, ContainerResponseContext responseContext)
      throws IOException {
    Object startNano = requestContext.getProperty(PROP_START_NANO);
    if (startNano == null) {
      return;
    }

    long durationMs = (System.nanoTime() - (long) startNano) / 1_000_000L;
    String httpMethod = (String) requestContext.getProperty(PROP_HTTP_METHOD);
    String apiPath = (String) requestContext.getProperty(PROP_API_PATH);
    String apiRoute = (String) requestContext.getProperty(PROP_API_ROUTE);
    String apiKeyHash = (String) requestContext.getProperty(PROP_API_KEY_HASH);
    String profileId = (String) requestContext.getProperty(PROP_PROFILE_ID);
    String profileEmail = (String) requestContext.getProperty(PROP_PROFILE_EMAIL);
    String environmentHost = (String) requestContext.getProperty(PROP_ENVIRONMENT_HOST);
    Long requestBytes = (Long) requestContext.getProperty(PROP_REQUEST_BYTES);
    Long responseBytes = parseContentLength(responseContext.getHeaderString("Content-Length"));

    UUID profileUuid = parseProfileUuid(profileId);

    FRAnalytics.apiUsageRecorded(
        httpMethod,
        apiPath,
        apiRoute,
        responseContext.getStatus(),
        durationMs,
        apiKeyHash,
        profileId,
        profileEmail,
        environmentHost,
        requestBytes,
        responseBytes,
        profileUuid);
  }
}
