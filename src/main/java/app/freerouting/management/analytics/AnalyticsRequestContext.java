package app.freerouting.management.analytics;

/**
 * Request-scoped analytics context for API threads. Populated by {@link app.freerouting.api.ApiAnalyticsFilter}
 * and read when emitting {@code API Endpoint Called} events.
 */
public final class AnalyticsRequestContext {

  private static final ThreadLocal<String> environmentHost = new ThreadLocal<>();

  private AnalyticsRequestContext() {
  }

  public static void setEnvironmentHost(String host) {
    if (host == null || host.isBlank()) {
      environmentHost.remove();
    } else {
      environmentHost.set(host.trim());
    }
  }

  public static String getEnvironmentHost() {
    return environmentHost.get();
  }

  public static void clear() {
    environmentHost.remove();
  }
}
