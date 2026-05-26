package app.freerouting.api;

import app.freerouting.logger.FRLogger;
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
 * Assigns and propagates correlation IDs for cross-service tracing.
 */
@Provider
@Priority(Priorities.AUTHENTICATION - 100)
public class CorrelationIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

  public static final String HEADER_NAME = "X-Correlation-ID";
  public static final String PROPERTY_NAME = "correlation-id";

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String correlationId = resolveOrCreate(requestContext.getHeaderString(HEADER_NAME));
    requestContext.setProperty(PROPERTY_NAME, correlationId);

    FRLogger.debug("[cid=" + correlationId + "] "
        + requestContext.getMethod() + " "
        + requestContext.getUriInfo().getPath());
  }

  @Override
  public void filter(
      ContainerRequestContext requestContext,
      ContainerResponseContext responseContext) throws IOException {
    Object value = requestContext.getProperty(PROPERTY_NAME);
    String correlationId = value instanceof String ? (String) value : resolveOrCreate(null);

    responseContext.getHeaders().putSingle(HEADER_NAME, correlationId);
  }

  public static String resolveOrCreate(String value) {
    if (value == null || value.isBlank()) {
      return UUID.randomUUID().toString();
    }
    return value;
  }
}