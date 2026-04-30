package app.freerouting.api;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
public class IdleTimeoutFilter implements ContainerRequestFilter {

  private static volatile long lastActivityTime = System.currentTimeMillis();

  private static final String[] IGNORED_PATH_PREFIXES = {
      "/v1/system/", "/dev/system/", "/openapi/", "/swagger-ui"
  };

  @Override
  public void filter(ContainerRequestContext requestContext) {
    if ("OPTIONS".equalsIgnoreCase(requestContext.getMethod())) {
      return;
    }

    String path = requestContext.getUriInfo().getPath();
    for (String prefix : IGNORED_PATH_PREFIXES) {
      if (path.startsWith(prefix)) {
        return;
      }
    }

    lastActivityTime = System.currentTimeMillis();
  }

  public static long getLastActivityTime() {
    return lastActivityTime;
  }
}
