package app.freerouting.management.analytics;

import app.freerouting.logger.FRLogger;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Aggregates analytics delivery failures and emits periodic log summaries.
 *
 * <h2>Design goals</h2>
 * <ul>
 *   <li>Never flood the log file. When the analytics endpoint is unreachable every outbound event
 *       fails, which can mean hundreds of failures per minute. Writing a log line per failure would
 *       drown all other output.</li>
 *   <li>Never be completely silent. Operators must be able to tell that analytics delivery is
 *       broken without waiting an indefinitely long time.</li>
 * </ul>
 *
 * <h2>Behaviour</h2>
 * <ol>
 *   <li><b>Immediate first-failure log (WARN):</b> The very first failure in each window is
 *       logged right away, before any aggregation delay. This gives immediate signal on a fresh
 *       deployment with a misconfigured key or on the first failure after a successful
 *       recovery.</li>
 *   <li><b>Silent aggregation:</b> Every subsequent failure in the same window increments an
 *       in-memory counter keyed by a normalised error signature. No further log lines are
 *       produced until the window closes.</li>
 *   <li><b>Hourly flush:</b> A daemon thread runs every {@value #FLUSH_INTERVAL_MINUTES} minutes.
 *       If any failures occurred, it logs a one-line summary per distinct error type, sorted by
 *       frequency. The log level is {@code WARN} when the total is &le; {@value #ERROR_THRESHOLD}
 *       (occasional blip) and {@code ERROR} when it exceeds that threshold (sustained outage that
 *       requires operator attention).</li>
 *   <li><b>Window reset:</b> After each flush the first-failure flag is cleared, so the first
 *       failure in the next window is again logged immediately.</li>
 * </ol>
 *
 * <h2>Thread safety</h2>
 * All mutable state is either {@link java.util.concurrent.atomic atomic} or stored in a
 * {@link ConcurrentHashMap}. The flush uses an atomic read-and-zero ({@link AtomicLong#getAndSet})
 * so counts that arrive between the snapshot and the map cleanup are not lost — they stay in the
 * map and are captured by the next flush.
 */
final class AnalyticsErrorAggregator {

  /** How often (in minutes) the aggregated error summary is flushed to the log. */
  static final int FLUSH_INTERVAL_MINUTES = 60;

  /**
   * Total failures in a window above this count → flush at {@code ERROR} level rather than
   * {@code WARN}, signalling a sustained outage that warrants operator action.
   */
  static final int ERROR_THRESHOLD = 50;

  // -------------------------------------------------------------------------
  // Aggregation state
  // -------------------------------------------------------------------------

  /**
   * Per-signature failure counters for the current window.
   * Key: normalised error signature (see {@link #normaliseKey}).
   * Value: number of times that error occurred since the last flush.
   */
  private static final ConcurrentHashMap<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();

  /**
   * Latest server-side error-body snippet per error key, populated only for HTTP-level failures
   * (i.e. when the remote server replied with a 4xx/5xx and we could read its response body).
   * Stored as a bounded string (max {@value #MAX_BODY_LENGTH} characters) so keys remain stable.
   */
  private static final ConcurrentHashMap<String, String> serverResponseBodies = new ConcurrentHashMap<>();

  /** Maximum number of characters kept from a server error-body for display. */
  private static final int MAX_BODY_LENGTH = 250;

  /**
   * {@code true} once the first failure in the current window has been logged immediately.
   * Reset to {@code false} after each hourly flush.
   */
  private static final AtomicBoolean firstFailureLogged = new AtomicBoolean(false);

  /**
   * Running total of failures in the current window.
   * Used to choose the flush log level without re-summing the map.
   */
  private static final AtomicLong windowTotal = new AtomicLong(0);

  // -------------------------------------------------------------------------
  // Flush scheduler
  // -------------------------------------------------------------------------

  static {
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "analytics-error-reporter");
      t.setDaemon(true); // must not prevent JVM shutdown
      return t;
    });
    scheduler.scheduleAtFixedRate(
        AnalyticsErrorAggregator::flushErrorSummary,
        FLUSH_INTERVAL_MINUTES,
        FLUSH_INTERVAL_MINUTES,
        TimeUnit.MINUTES);
  }

  private AnalyticsErrorAggregator() {
  }

  // -------------------------------------------------------------------------
  // Public API
  // -------------------------------------------------------------------------

  /**
   * Records one analytics delivery failure (network-level: DNS, connect, SSL, reset, …).
   *
   * <p>If this is the first failure in the current window it is logged immediately at {@code WARN}.
   * All subsequent failures in the same window are silently counted until the next hourly flush.
   *
   * @param endpoint the URL that was being called when the failure occurred
   * @param e        the exception that caused the failure
   */
  static void recordFailure(String endpoint, Exception e) {
    recordFailure(endpoint, e, null);
  }

  /**
   * Records one analytics delivery failure with an optional server-side error body.
   *
   * <p>Use this overload when the server replied with an HTTP error (4xx/5xx) and you were
   * able to read its response body — it will be included in the hourly summary so that
   * operators can see the exact server-side error message without having to inspect the
   * server logs separately.
   *
   * @param endpoint           the URL that was being called when the failure occurred
   * @param e                  the exception that caused the failure
   * @param serverResponseBody the raw HTTP error body returned by the server, or {@code null}
   */
  static void recordFailure(String endpoint, Exception e, String serverResponseBody) {
    String key = normaliseKey(endpoint, e);

    // Keep the latest server response body for this key (bounded to MAX_BODY_LENGTH).
    if (serverResponseBody != null && !serverResponseBody.isBlank()) {
      serverResponseBodies.put(key, truncate(serverResponseBody, MAX_BODY_LENGTH));
    }

    errorCounts.computeIfAbsent(key, _ -> new AtomicLong(0)).incrementAndGet();
    windowTotal.incrementAndGet();

    // Log the very first failure in this window immediately so operators do not have
    // to wait up to FLUSH_INTERVAL_MINUTES to discover that analytics delivery is broken.
    if (firstFailureLogged.compareAndSet(false, true)) {
      String body = serverResponseBodies.get(key);
      String hint = actionableHint(key);
      StringBuilder msg = new StringBuilder("Analytics tracking: first delivery failure in this window - ")
          .append(key);
      if (body != null) {
        msg.append(" | Server response: ").append(body);
      }
      msg.append(". Further failures will be aggregated and reported every ")
          .append(FLUSH_INTERVAL_MINUTES)
          .append(" minutes.");
      if (hint != null) {
        msg.append(' ').append(hint);
      }
      FRLogger.warn(msg.toString());
    }
  }

  // -------------------------------------------------------------------------
  // Flush (package-visible so tests can trigger it directly)
  // -------------------------------------------------------------------------

  /**
   * Snapshots the current window's counters, resets all state, and writes the summary to the log.
   * Called automatically by the scheduled executor; may also be called from tests.
   */
  static void flushErrorSummary() {
    // Reset the first-failure flag for the next window so that a new failure after a
    // recovery is also logged immediately.
    firstFailureLogged.set(false);

    // Atomically drain each counter: read-and-zero, then remove zeroed entries.
    // Failures that arrive between getAndSet(0) and removeIf will leave their counter
    // at > 0, so removeIf leaves the entry in place — those counts are captured next flush.
    Map<String, Long> snapshot = new HashMap<>();
    errorCounts.forEach((key, counter) -> {
      long count = counter.getAndSet(0);
      if (count > 0) {
        snapshot.put(key, count);
      }
    });
    errorCounts.entrySet().removeIf(entry -> entry.getValue().get() == 0);

    long total = windowTotal.getAndSet(0);
    if (total == 0) {
      return; // Clean window — nothing to report.
    }

    StringBuilder sb = new StringBuilder();
    sb.append("Analytics tracking: ")
        .append(total)
        .append(" event(s) failed to deliver in the last ")
        .append(FLUSH_INTERVAL_MINUTES)
        .append(" minutes. Breakdown by error:\n");

    snapshot.entrySet().stream()
        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
        .forEach(entry -> {
          String key = entry.getKey();
          sb.append("  • ").append(key).append(": ").append(entry.getValue()).append("×\n");

          // Append the server-side error body if we captured one for this key.
          String body = serverResponseBodies.get(key);
          if (body != null) {
            sb.append("      Server response: ").append(body).append('\n');
          }

          // Append a per-error-type actionable hint to guide operators.
          String hint = actionableHint(key);
          if (hint != null) {
            sb.append("      ").append(hint).append('\n');
          }
        });

    // Drain stale server-response-body entries that no longer have a matching counter.
    serverResponseBodies.keySet().retainAll(errorCounts.keySet());

    if (total > ERROR_THRESHOLD) {
      // Sustained outage: use ERROR so that log-based alerting rules can fire.
      FRLogger.error(sb.toString(), null);
    } else {
      // Occasional blip: WARN is sufficient.
      FRLogger.warn(sb.toString());
    }
  }

  // -------------------------------------------------------------------------
  // Key normalisation
  // -------------------------------------------------------------------------

  /**
   * Produces a stable, bounded key from the endpoint URL and exception.
   *
   * <p>Only the {@code /v1/...} path suffix of the endpoint is kept (not the full origin) to avoid
   * environment-specific noise in the key. The exception message is truncated to 100 characters
   * to keep the key bounded regardless of how verbose the underlying runtime message is.
   *
   * @param endpoint the full URL of the call that failed
   * @param e        the exception
   * @return a normalised key suitable for use in a frequency map
   */
  private static String normaliseKey(String endpoint, Exception e) {
    // Keep only the path part starting at "/v1/" to exclude origin (scheme + host + port).
    String path = endpoint;
    int v1Index = endpoint.indexOf("/v1/");
    if (v1Index >= 0) {
      path = endpoint.substring(v1Index);
    }

    String exceptionType = e.getClass().getSimpleName();
    String message = (e.getMessage() != null) ? e.getMessage() : "(no message)";
    if (message.length() > 200) {
      message = message.substring(0, 200) + "…";
    }

    return path + " - " + exceptionType + ": " + message;
  }

  /**
   * Returns a concise, actionable hint for operators based on the normalised error key.
   * Returns {@code null} if no specific guidance is available for this error pattern.
   */
  static String actionableHint(String key) {
    // ---- Cloudflare proxy errors (never reach the origin application) ----
    // 520–527: generic Cloudflare "origin returned an unexpected response" family.
    // 530 + body "error code: 1033": Cloudflare Argo Tunnel is down between the edge
    //   and the origin server.  The application itself is not involved.
    if (key.contains("IOException") && (key.contains("code: 530") || key.contains(" 530"))) {
      return "→ Action: HTTP 530 is a Cloudflare proxy error — the request never reached the "
          + "origin server. Error 1033 in the response body means the Cloudflare Argo Tunnel "
          + "to the origin is down or misconfigured. Check the Cloudflare dashboard (Zero Trust → "
          + "Access → Tunnels) and verify that the tunnel connector on the origin host is running "
          + "(e.g. `cloudflared tunnel run <name>`).";
    }
    if (key.contains("IOException") && (key.contains("code: 52") || key.contains(" 52"))) {
      return "→ Action: HTTP 52x is a Cloudflare error meaning the origin server returned an "
          + "unexpected or empty response to Cloudflare. Check origin server health and Cloudflare "
          + "error logs in the dashboard.";
    }
    // ---- Application-level HTTP errors ----
    if (key.contains("IOException") && (key.contains(" 500") || key.contains("code: 500"))) {
      return "→ Action: The analytics server returned HTTP 500. Check the server logs and verify that the "
          + "FREEROUTING__USAGE_AND_DIAGNOSTIC_DATA__BIGQUERY_SERVICE_ACCOUNT_KEY environment variable "
          + "is set to a valid service-account JSON on the analytics server.";
    }
    if (key.contains("IOException") && (key.contains(" 401") || key.contains("code: 401"))) {
      return "→ Action: HTTP 401 Unauthorized. The write key or service-account credentials used by this "
          + "instance are invalid or expired. Check FREEROUTING__USAGE_AND_DIAGNOSTIC_DATA__BIGQUERY_SERVICE_ACCOUNT_KEY.";
    }
    // ---- Network / transport errors ----
    if (key.contains("UnknownHostException")) {
      return "→ Action: DNS resolution failed. Verify that this host has network access and that "
          + "api.freerouting.app is reachable (try: nslookup api.freerouting.app).";
    }
    if (key.contains("ConnectException")) {
      return "→ Action: Connection refused or timed out. Check network/firewall rules between this "
          + "host and the analytics endpoint, or verify that the server is running.";
    }
    if (key.contains("SocketException") && key.contains("reset")) {
      return "→ Action: Server closed the connection unexpectedly. This may indicate server overload, "
          + "an intermediate proxy resetting idle connections, or a server-side crash.";
    }
    if (key.contains("SSLHandshakeException")) {
      return "→ Action: TLS handshake failed. Verify that the JRE trust store contains the server "
          + "certificate's CA, that the system clock is correct, and that TLS 1.2+ is enabled.";
    }
    return null;
  }

  /** Truncates {@code s} to at most {@code maxLen} characters, appending "…" if cut. */
  private static String truncate(String s, int maxLen) {
    if (s == null) {
      return null;
    }
    return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
  }
}




