package app.freerouting.api;

import app.freerouting.api.v1.AnalyticsControllerV1;
import app.freerouting.api.v1.JobControllerV1;
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
 * <p>Registers all controllers, exception mappers, and filters for the Freerouting API.
 * The request-filter chain executes in ascending {@link jakarta.annotation.Priority} order:</p>
 *
 * <ol>
 *   <li><b>Priority 1000</b> — {@link app.freerouting.api.security.ApiKeyValidationFilter}:
 *       validates {@code Authorization: Bearer} tokens; aborts with 401 on failure.</li>
 *   <li><b>Priority 1050</b> — {@link EnvironmentHostValidationFilter}:
 *       validates the {@code Freerouting-Environment-Host} header format; aborts with 400 on
 *       failure.</li>
 *   <li><b>Priority 5000</b> — {@link ApiAnalyticsFilter}:
 *       captures HTTP ≥ 400 responses for analytics; 2xx paths are tracked individually by each
 *       controller method.</li>
 * </ol>
 */
@ApplicationPath("/")
public class FreeroutingApplication extends Application {

  @Override
  public Set<Class<?>> getClasses() {
    Set<Class<?>> classes = new HashSet<>();
    classes.add(AnalyticsControllerV1.class);
    classes.add(JobControllerV1.class);
    classes.add(SessionControllerV1.class);
    classes.add(SystemControllerV1.class);
    classes.add(ApiExceptionMapper.class);
    classes.add(NotFoundExceptionMapper.class);
    classes.add(CorrelationIdFilter.class);
    classes.add(ApiRateLimitFilter.class);
    classes.add(app.freerouting.api.security.ApiKeyValidationFilter.class);
    // Enforces the mandatory Freerouting-Environment-Host header on every protected endpoint.
    classes.add(EnvironmentHostValidationFilter.class);
    // Tracks all error (4xx/5xx) responses centrally; 2xx paths remain tracked
    // individually by the controller methods with full request/response payloads.
    classes.add(ApiAnalyticsFilter.class);
    classes.add(SseFeature.class);
    return classes;
  }
}