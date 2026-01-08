package app.freerouting.api.security;

import app.freerouting.Freerouting;
import app.freerouting.logger.FRLogger;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

/**
 * JAX-RS request filter that validates API keys for protected endpoints.
 * <p>
 * This filter intercepts all incoming requests and validates the API key
 * provided in the {@code Authorization: Bearer} header. Certain endpoints are
 * excluded from validation and remain publicly accessible.
 * </p>
 *
 * <h2>Excluded Endpoints (Public Access)</h2>
 * The following endpoints do NOT require API key validation:
 * <ul>
 * <li><b>/v1/system/*</b> - System monitoring and health check endpoints</li>
 * <li><b>/v1/analytics/*</b> - Analytics tracking endpoints</li>
 * <li><b>/dev/*</b> - Development and testing endpoints</li>
 * <li><b>/openapi/*</b> - OpenAPI specification endpoints</li>
 * <li><b>/swagger-ui</b> - Swagger UI documentation</li>
 * </ul>
 *
 * <h2>Protected Endpoints</h2>
 * All other endpoints require a valid API key in the
 * {@code Authorization: Bearer <API_KEY>} header.
 *
 * <h2>Error Responses</h2>
 * <ul>
 * <li><b>401 Unauthorized</b> - Missing or invalid API key</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * The API key provider is configured via the
 * {@code FREEROUTING__API_SERVER__KEYS_LOCATION__GOOGLE_SHEETS}
 * environment variable. If not configured, all protected endpoints will be
 * denied access.
 *
 * @see ApiKeyProvider
 * @see GoogleSheetsApiKeyProvider
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class ApiKeyValidationFilter implements ContainerRequestFilter {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";
  private static ApiKeyProvider apiKeyProvider;
  private static boolean isInitialized = false;

  /**
   * Initializes the API key provider. This is called lazily on the first request
   * to avoid initialization issues.
   */
  private static synchronized void initializeProvider() {
    if (isInitialized) {
      return;
    }

    String googleSheetsUrl = Freerouting.globalSettings.apiServerSettings.keysLocation.googleSheets;
    String googleApiKey = Freerouting.globalSettings.apiServerSettings.keysLocation.googleApiKey;

    if (googleSheetsUrl != null && !googleSheetsUrl.trim().isEmpty()
        && googleApiKey != null && !googleApiKey.trim().isEmpty()) {
      try {
        apiKeyProvider = new GoogleSheetsApiKeyProvider(googleSheetsUrl, googleApiKey);
        FRLogger.info("API key validation enabled with Google Sheets provider");
      } catch (Exception e) {
        FRLogger.error(
            "Failed to initialize Google Sheets API key provider. API key validation will deny all requests.",
            null, e);
        apiKeyProvider = null;
      }
    } else {
      FRLogger.warn(
          "Google Sheets URL or API key not configured (FREEROUTING__API_SERVER__KEYS_LOCATION__GOOGLE_SHEETS and FREEROUTING__API_SERVER__KEYS_LOCATION__GOOGLE_API_KEY). API key validation will deny all requests to protected endpoints.");
      apiKeyProvider = null;
    }

    isInitialized = true;
  }

  /**
   * Gets the current API key provider (for testing purposes).
   *
   * @return The API key provider, or {@code null} if not initialized
   */
  static ApiKeyProvider getApiKeyProvider() {
    return apiKeyProvider;
  }

  /**
   * Resets the provider initialization state (for testing purposes).
   */
  static void resetForTesting() {
    isInitialized = false;
    apiKeyProvider = null;
  }

  /**
   * Checks if the request path should be excluded from API key validation.
   *
   * @param path The request path
   * @return {@code true} if the path is excluded, {@code false} otherwise
   */
  private boolean isExcludedPath(String path) {
    if (path == null) {
      return false;
    }

    // Normalize path (remove leading slash for consistent comparison)
    String normalizedPath = path.startsWith("/") ? path.substring(1) : path;

    // Check excluded paths
    return normalizedPath.startsWith("v1/system/")
        || normalizedPath.startsWith("v1/analytics/")
        || normalizedPath.startsWith("dev/")
        || normalizedPath.startsWith("openapi/")
        || normalizedPath.equals("swagger-ui")
        || normalizedPath.startsWith("swagger-ui/");
  }

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String path = requestContext.getUriInfo().getPath();

    // Skip validation for excluded endpoints
    if (isExcludedPath(path)) {
      FRLogger.debug("API key validation skipped for excluded path: " + path);
      return;
    }

    // Initialize provider on first request
    if (!isInitialized) {
      initializeProvider();
    }

    // Get API key from Authorization header
    String authHeader = requestContext.getHeaderString(AUTHORIZATION_HEADER);
    String apiKey = null;

    if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
      apiKey = authHeader.substring(BEARER_PREFIX.length()).trim();
    }

    // Validate API key
    if (apiKey == null || apiKey.isEmpty()) {
      FRLogger.warn("API key validation failed: missing or invalid Authorization header for path " + path);
      abortWithUnauthorized(requestContext,
          "Missing API key. Please provide a valid API key in the Authorization header using Bearer scheme (Authorization: Bearer <API_KEY>).");
      return;
    }

    // Check if provider is available
    if (apiKeyProvider == null) {
      FRLogger.error("API key validation failed: provider not initialized for path " + path, null, null);
      abortWithUnauthorized(requestContext, "API key validation is not properly configured.");
      return;
    }

    // Validate the API key
    if (!apiKeyProvider.validateApiKey(apiKey)) {
      FRLogger.warn("API key validation failed: invalid or unauthorized API key for path " + path);
      abortWithUnauthorized(requestContext, "Invalid or unauthorized API key.");
      return;
    }

    // API key is valid, allow the request to proceed
    FRLogger.debug("API key validation successful for path: " + path);
  }

  /**
   * Aborts the request with a 401 Unauthorized response.
   *
   * @param requestContext The request context
   * @param message        The error message
   */
  private void abortWithUnauthorized(ContainerRequestContext requestContext, String message) {
    Response response = Response
        .status(Response.Status.UNAUTHORIZED)
        .entity("{\"error\":\"" + message + "\"}")
        .type("application/json")
        .build();
    requestContext.abortWith(response);
  }
}