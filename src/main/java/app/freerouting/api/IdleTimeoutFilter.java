package app.freerouting.api;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
public class IdleTimeoutFilter implements ContainerRequestFilter {

  private static volatile long lastActivityTime = System.currentTimeMillis();

  @Override
  public void filter(ContainerRequestContext requestContext) {
    lastActivityTime = System.currentTimeMillis();
  }

  public static long getLastActivityTime() {
    return lastActivityTime;
  }
}
