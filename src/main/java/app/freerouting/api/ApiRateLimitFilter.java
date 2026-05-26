package app.freerouting.api;

import app.freerouting.Freerouting;
import app.freerouting.settings.RateLimitSettings;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

/**
 * API request rate limiting filter.
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 10)
public class ApiRateLimitFilter implements ContainerRequestFilter {

  private final FixedWindowRateLimiter limiter = new FixedWindowRateLimiter();

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    RateLimitSettings cfg = Freerouting.globalSettings != null
        && Freerouting.globalSettings.apiServerSettings != null
        ? Freerouting.globalSettings.apiServerSettings.rateLimit
        : null;

    if (cfg == null || !Boolean.TRUE.equals(cfg.enabled)) {
      return;
    }

    String key = buildKey(requestContext);
    FixedWindowRateLimiter.Decision decision = limiter.check(
        key,
        cfg.requestsPerWindow == null ? 120 : cfg.requestsPerWindow,
        cfg.windowSeconds == null ? 60 : cfg.windowSeconds);

    if (!decision.allowed()) {
      String body = "{\"error\":\"Rate limit exceeded\",\"retry_after_seconds\":"
          + decision.retryAfterSeconds() + "}";
      requestContext.abortWith(Response.status(429)
          .header("Retry-After", String.valueOf(decision.retryAfterSeconds()))
          .entity(body)
          .type("application/json")
          .build());
    }
  }

  private static String buildKey(ContainerRequestContext requestContext) {
    String profileId = requestContext.getHeaderString("Freerouting-Profile-ID");
    String profileEmail = requestContext.getHeaderString("Freerouting-Profile-Email");
    String identity = profileId != null && !profileId.isBlank()
        ? profileId
        : (profileEmail != null && !profileEmail.isBlank() ? profileEmail : "anonymous");

    return requestContext.getMethod() + ":" + identity;
  }
}