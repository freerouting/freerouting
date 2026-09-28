package app.freerouting.analytics;

/**
 * Request-scoped analytics context for API threads. Populated by {@link
 * app.freerouting.api.ApiAnalyticsFilter} and read when emitting {@code API Endpoint Called}
 * events.
 */
public final class AnalyticsRequestContext {

  private static final ThreadLocal<String> environmentHost = new ThreadLocal<>();

  private AnalyticsRequestContext() {}

  /**
   * Returns the environment host stored for the current request.
   *
   * @return the request environment host, or {@code null} when none is set
   */
  public static String getEnvironmentHost() {
    return environmentHost.get();
  }

  /**
   * Stores the environment host for the current request.
   *
   * @param host the host value supplied by the request, or {@code null} to clear it
   */
  public static void setEnvironmentHost(String host) {
    if (host == null || host.isBlank()) {
      environmentHost.remove();
    } else {
      environmentHost.set(host.trim());
    }
  }

  /** Clears the environment host stored for the current request. */
  public static void clear() {
    environmentHost.remove();
  }
}
