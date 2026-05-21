package app.freerouting.api.mcp;

import app.freerouting.api.ApiExceptionMapper;
import app.freerouting.api.EnvironmentHostValidationFilter;
import app.freerouting.api.NotFoundExceptionMapper;
import app.freerouting.api.v1.McpControllerV1;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;

/**
 * Dedicated JAX-RS application for MCP endpoints.
 */
@ApplicationPath("/")
public class McpApplication extends Application {

  @Override
  public Set<Class<?>> getClasses() {
    Set<Class<?>> classes = new HashSet<>();
    classes.add(McpControllerV1.class);
    classes.add(McpApiKeyValidationFilter.class);
    classes.add(EnvironmentHostValidationFilter.class);
    classes.add(ApiExceptionMapper.class);
    classes.add(NotFoundExceptionMapper.class);
    return classes;
  }
}