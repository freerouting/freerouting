package app.freerouting.api.mcp;

import app.freerouting.api.ApiExceptionMapper;
import app.freerouting.api.ApiUsageFilter;
import app.freerouting.api.CorrelationIdFilter;
import app.freerouting.api.EnvironmentHostValidationFilter;
import app.freerouting.api.GsonMessageBodyHandler;
import app.freerouting.api.JsonStringMessageBodyWriter;
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
 * <p>This class is the single source of truth for all providers registered in the MCP server. It is
 * wired into server startup via the {@code jakarta.ws.rs.Application} init parameter in {@link
 * app.freerouting.Freerouting#initializeMCP}.
 */
@ApplicationPath("/")
public class McpApplication extends Application {

  @Override
  public Set<Class<?>> getClasses() {
    Set<Class<?>> classes =
        new HashSet<>(
            Set.of(
                McpControllerV1.class,
                AgentCardController.class,
                McpApiKeyValidationFilter.class,
                McpRateLimitFilter.class,
                CorrelationIdFilter.class,
                EnvironmentHostValidationFilter.class,
                ApiUsageFilter.class,
                ApiExceptionMapper.class,
                NotFoundExceptionMapper.class,
                JsonStringMessageBodyWriter.class,
                GsonMessageBodyHandler.class,
                SseFeature.class));
    return classes;
  }
}
