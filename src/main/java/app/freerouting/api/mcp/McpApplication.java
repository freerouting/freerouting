package app.freerouting.api.mcp;

import app.freerouting.api.ApiExceptionMapper;
import app.freerouting.api.CorrelationIdFilter;
import app.freerouting.api.EnvironmentHostValidationFilter;
import app.freerouting.api.NotFoundExceptionMapper;
import app.freerouting.api.v1.McpControllerV1;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;
import org.glassfish.jersey.media.sse.SseFeature;

/**
 * Dedicated JAX-RS application for MCP endpoints.
 *
 * <p>This class is the single source of truth for all providers registered in the MCP server.
 * It is wired into server startup via the {@code jakarta.ws.rs.Application} init parameter in
 * {@link app.freerouting.Freerouting#InitializeMCP}.</p>
 */
@ApplicationPath("/")
public class McpApplication extends Application {

  @Override
  public Set<Class<?>> getClasses() {
    Set<Class<?>> classes = new HashSet<>();
    classes.add(McpControllerV1.class);
    classes.add(AgentCardController.class);
    classes.add(McpApiKeyValidationFilter.class);
    classes.add(McpRateLimitFilter.class);
    classes.add(CorrelationIdFilter.class);
    classes.add(EnvironmentHostValidationFilter.class);
    classes.add(ApiExceptionMapper.class);
    classes.add(NotFoundExceptionMapper.class);
    classes.add(SseFeature.class);
    return classes;
  }
}