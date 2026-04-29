package app.freerouting.api;

import app.freerouting.logger.FRLogger;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

/**
 * JAX-RS request filter that enforces the presence and format of the
 * {@code Freerouting-Environment-Host} header on every protected API request.
 *
 * <h2>Purpose</h2>
 * The header identifies the calling EDA tool and its version (e.g.
 * {@code KiCad/10.0}, {@code EasyEDA/1.0}). It is required so that the server
 * can track which integrations are in use, correlate analytics events, and
 * provide better diagnostics.
 *
 * <h2>Required format</h2>
 * {@code <ToolName>/<Version>}, containing exactly one {@code /} separator.
 * Examples: {@code KiCad/10.0}, {@code EasyEDA/2.1}, {@code Postman/11.14}.
 *
 * <h2>Excluded endpoints (public access)</h2>
 * The same paths that are excluded from API-key validation are also excluded
 * here:
 * <ul>
 *   <li>{@code /v1/system/*} — health-check and status endpoints</li>
 *   <li>{@code /v1/analytics/*} — analytics ingestion endpoints</li>
 *   <li>{@code /dev/*} — development / testing endpoints</li>
 *   <li>{@code /openapi/*} — OpenAPI specification endpoints</li>
 *   <li>{@code /swagger-ui} and {@code /swagger-ui/*} — Swagger UI</li>
 * </ul>
 *
 * <h2>Error response</h2>
 * Returns HTTP {@code 400 Bad Request} with a JSON body when the header is
 * absent or does not match the required format.
 *
 * <h2>Priority</h2>
 * Runs at {@link Priorities#AUTHENTICATION} + 50 (1050), i.e. after
 * {@link app.freerouting.api.security.ApiKeyValidationFilter} (1000) so that
 * unauthenticated requests receive a 401 rather than a 400.
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 50)
public class EnvironmentHostValidationFilter implements ContainerRequestFilter {

  static final String HEADER_NAME = "Freerouting-Environment-Host";

  private static final String MISSING_HEADER_MESSAGE =
      "The '" + HEADER_NAME + "' request header is required. "
      + "It must identify the calling EDA tool and its version using the format '<ToolName>/<Version>'. "
      + "Examples: 'KiCad/10.0', 'EasyEDA/1.0', 'Postman/11.14'. "
      + "See https://github.com/freerouting/freerouting/blob/master/docs/API/API_v1.md for details.";

  private static final String INVALID_FORMAT_MESSAGE =
      "The '" + HEADER_NAME + "' header value '%s' is invalid. "
      + "It must use the format '<ToolName>/<Version>' with exactly one '/' separator. "
      + "Examples: 'KiCad/10.0', 'EasyEDA/1.0', 'Postman/11.14'.";

  private boolean isExcludedPath(String path) {
    if (path == null) {
      return false;
    }
    String p = path.startsWith("/") ? path.substring(1) : path;
    return p.startsWith("v1/system/")
        || p.startsWith("v1/analytics/")
        || p.startsWith("dev/")
        || p.startsWith("openapi/")
        || p.equals("swagger-ui")
        || p.startsWith("swagger-ui/");
  }

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String path = requestContext.getUriInfo().getPath();

    if (isExcludedPath(path)) {
      return;
    }

    String host = requestContext.getHeaderString(HEADER_NAME);

    if (host == null || host.isBlank()) {
      FRLogger.warn("Request to '" + path + "' rejected: missing " + HEADER_NAME + " header.");
      abortWithBadRequest(requestContext, MISSING_HEADER_MESSAGE);
      return;
    }

    // Validate the format: exactly one '/' separating a non-empty name from a non-empty version
    String[] parts = host.split("/", -1);
    if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
      FRLogger.warn("Request to '" + path + "' rejected: invalid " + HEADER_NAME + " value: '" + host + "'.");
      abortWithBadRequest(requestContext, String.format(INVALID_FORMAT_MESSAGE, host));
    }
  }

  private void abortWithBadRequest(ContainerRequestContext requestContext, String message) {
    String jsonBody = "{\"error\":\"" + message.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    requestContext.abortWith(
        Response.status(Response.Status.BAD_REQUEST)
            .entity(jsonBody)
            .type("application/json")
            .build());
  }
}


