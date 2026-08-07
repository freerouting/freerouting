package app.freerouting.api;

import java.util.regex.Pattern;

/**
 * Shared path helpers for {@link ApiUsageFilter}.
 */
public final class ApiUsagePaths {

  private static final Pattern UUID_SEGMENT = Pattern.compile(
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

  private ApiUsagePaths() {
  }

  /**
   * Returns {@code true} for paths that must not emit {@code API Usage} rows (docs, telemetry
   * ingestion, agent discovery).
   */
  public static boolean isUsageTrackingExcluded(String path) {
    if (path == null) {
      return true;
    }
    String normalized = path.startsWith("/") ? path.substring(1) : path;
    return normalized.startsWith("v1/analytics/")
        || normalized.startsWith("dev/")
        || normalized.startsWith(".well-known/")
        || normalized.startsWith("openapi/")
        || "swagger-ui".equals(normalized)
        || normalized.startsWith("swagger-ui/");
  }

  /**
   * Builds a stable route key for aggregation, replacing UUID path segments with {@code {id}}.
   *
   * @param httpMethod e.g. {@code GET}
   * @param path       decoded JAX-RS path, with or without a leading {@code /}
   * @return e.g. {@code GET v1/jobs/{id}/output}
   */
  public static String normalizeRoute(String httpMethod, String path) {
    String route = path == null ? "" : path.trim();
    if (route.startsWith("/")) {
      route = route.substring(1);
    }
    route = UUID_SEGMENT.matcher(route).replaceAll("{id}");
    return httpMethod + " " + route;
  }
}
