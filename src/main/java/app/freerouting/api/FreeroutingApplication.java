package app.freerouting.api;

import app.freerouting.api.v1.AnalyticsControllerV1;
import app.freerouting.api.v1.JobInputResource;
import app.freerouting.api.v1.JobOutputResource;
import app.freerouting.api.v1.JobProgressResource;
import app.freerouting.api.v1.SessionControllerV1;
import app.freerouting.api.v1.SystemControllerV1;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;
import org.glassfish.jersey.media.sse.SseFeature;

/**
 * Jersey JAX-RS application entry point.
 *
 * <p>Registers all controllers, exception mappers, and filters for the Freerouting API. The
 * request-filter chain executes in ascending {@link jakarta.annotation.Priority} order:
 *
 * <ol>
 *   <li><b>Priority 1000</b> — {@link app.freerouting.api.security.ApiKeyValidationFilter}:
 *       validates {@code Authorization: Bearer} tokens; aborts with 401 on failure.
 *   <li><b>Priority 1050</b> — {@link EnvironmentHostValidationFilter}: validates the {@code
 *       Freerouting-Environment-Host} header format; aborts with 400 on failure.
 *   <li><b>Priority 5000</b> — {@link ApiAnalyticsFilter}: captures HTTP ≥ 400 responses for
 *       analytics; 2xx paths are tracked individually by each controller method.
 *   <li><b>Priority 5000</b> — {@link ApiUsageFilter}: emits one canonical {@code API Usage} row
 *       per request (billing-grade usage metrics).
 * </ol>
 */
@ApplicationPath("/")
public class FreeroutingApplication extends Application {

  @Override
  public Set<Class<?>> getClasses() {
    Set<Class<?>> classes =
        new HashSet<>(
            Set.of(
                AnalyticsControllerV1.class,
                JobInputResource.class,
                JobOutputResource.class,
                JobProgressResource.class,
                SessionControllerV1.class,
                SystemControllerV1.class,
                ApiExceptionMapper.class,
                NotFoundExceptionMapper.class,
                CorrelationIdFilter.class,
                ApiRateLimitFilter.class,
                app.freerouting.api.security.ApiKeyValidationFilter.class,
                // Enforces the mandatory Freerouting-Environment-Host header on every protected
                // endpoint.
                EnvironmentHostValidationFilter.class,
                // Tracks all error (4xx/5xx) responses centrally; 2xx paths remain tracked
                // individually by the controller methods with full request/response payloads.
                ApiAnalyticsFilter.class,
                ApiUsageFilter.class,
                SseFeature.class));
    return classes;
  }
}
