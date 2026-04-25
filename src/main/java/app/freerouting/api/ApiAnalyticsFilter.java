package app.freerouting.api;

import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.management.gson.GsonProvider;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.UUID;

/**
 * JAX-RS dual filter (request + response) that tracks API calls in analytics for
 * <strong>error responses (HTTP 4xx and 5xx)</strong>.
 *
 * <h2>Rationale</h2>
 * Successful responses (2xx) are already tracked with full request/response bodies directly
 * inside each controller method. This filter fills the remaining gap: early-return error paths
 * (invalid session, job not found, auth failures, etc.) that previously produced no analytics
 * event at all.
 *
 * <h2>Request phase</h2>
 * Captures the HTTP method, URI path, and caller {@code Freerouting-Profile-ID} UUID into
 * named request properties so they are available to the response phase filter, which has no
 * direct access to the original request URI.
 *
 * <h2>Response phase</h2>
 * When the response status is &ge; 400, emits a single {@code "API Endpoint Called"} analytics
 * event carrying the HTTP method + path as {@code api_method}, an empty request body (the error
 * paths do not produce a meaningful request echo), and the serialised error entity as
 * {@code api_response}.
 *
 * <h2>Double-tracking guard</h2>
 * Responses with status &lt; 400 are intentionally skipped — those paths delegate analytics to
 * the individual controller methods, which include richer request/response payloads.
 *
 * <h2>Priority</h2>
 * Runs at {@link Priorities#USER} (5000), after the {@link app.freerouting.api.security.ApiKeyValidationFilter}
 * at {@link Priorities#AUTHENTICATION} (1000). This ensures that 401 Unauthorized responses
 * produced by the security filter are also captured.
 */
@Provider
@Priority(Priorities.USER)
public class ApiAnalyticsFilter implements ContainerRequestFilter, ContainerResponseFilter {

  // Request-property keys used to pass state from the request phase to the response phase.
  private static final String PROP_METHOD = "app.freerouting.analytics.method";
  private static final String PROP_PATH = "app.freerouting.analytics.path";
  private static final String PROP_USER_ID = "app.freerouting.analytics.userId";

  // -------------------------------------------------------------------------
  // ContainerRequestFilter — runs before the controller
  // -------------------------------------------------------------------------

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    // Store method and decoded path so the response filter can reconstruct the
    // canonical "METHOD v1/..." string used throughout FRAnalytics.
    requestContext.setProperty(PROP_METHOD, requestContext.getMethod());
    requestContext.setProperty(PROP_PATH, requestContext.getUriInfo().getPath(true));

    // Resolve the caller's UUID from the standard profile header (mirrors
    // BaseController.AuthenticateUser() without throwing on failure).
    String userIdHeader = requestContext.getHeaderString("Freerouting-Profile-ID");
    if (userIdHeader != null && !userIdHeader.isBlank()) {
      try {
        requestContext.setProperty(PROP_USER_ID, UUID.fromString(userIdHeader.trim()));
      } catch (IllegalArgumentException ignored) {
        // Malformed UUID — leave property absent; analytics will use null.
      }
    }
  }

  // -------------------------------------------------------------------------
  // ContainerResponseFilter — runs after the controller
  // -------------------------------------------------------------------------

  @Override
  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
      throws IOException {

    int status = responseContext.getStatus();

    // Only track error responses.  2xx / 3xx paths are already tracked by the
    // individual controller methods with full request/response payloads.
    if (status < 400) {
      return;
    }

    String method = (String) requestContext.getProperty(PROP_METHOD);
    String path = (String) requestContext.getProperty(PROP_PATH);
    UUID userId = (UUID) requestContext.getProperty(PROP_USER_ID);

    // Guard: properties should always be set by the request phase, but handle
    // the edge-case where a very early framework error bypasses our request filter.
    if (method == null || path == null) {
      return;
    }

    // Canonical format: "GET v1/jobs/550e8400-..." (matches existing controller calls)
    String apiMethod = method + " " + path;

    // Serialise the error entity.  The ContainerResponseFilter runs before the
    // MessageBodyWriter commits the entity to the wire, so getEntity() is still set.
    String errorBody = serializeEntity(responseContext.getEntity());

    FRAnalytics.apiEndpointCalled(apiMethod, "", errorBody, userId);
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private static String serializeEntity(Object entity) {
    if (entity == null) {
      return "";
    }
    if (entity instanceof String s) {
      return s;
    }
    try {
      return GsonProvider.GSON.toJson(entity);
    } catch (Exception ignored) {
      return entity.toString();
    }
  }
}

