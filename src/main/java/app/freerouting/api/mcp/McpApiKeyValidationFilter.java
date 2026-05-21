package app.freerouting.api.mcp;

import app.freerouting.logger.FRLogger;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

/**
 * API key filter for MCP endpoints.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class McpApiKeyValidationFilter implements ContainerRequestFilter {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    McpApiKeyValidationService validationService = McpApiKeyValidationService.getInstance();
    if (!validationService.isAuthenticationEnabled()) {
      return;
    }

    String authHeader = requestContext.getHeaderString(AUTHORIZATION_HEADER);
    String apiKey = null;
    if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
      apiKey = authHeader.substring(BEARER_PREFIX.length()).trim();
    }

    if (apiKey == null || apiKey.isEmpty() || !validationService.validateApiKey(apiKey)) {
      FRLogger.warn("MCP API key validation failed for path " + requestContext.getUriInfo().getPath());
      String jsonBody = "{\"error\":\"Invalid or missing API key.\"}";
      requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
          .entity(jsonBody)
          .type("application/json")
          .build());
    }
  }
}