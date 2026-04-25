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
   * Records one analytics delivery failure.
   *
   * <p>If this is the first failure in the current window it is logged immediately at {@code WARN}.
   * All subsequent failures in the same window are silently counted until the next hourly flush.
   *
   * @param endpoint the URL that was being called when the failure occurred
   * @param e        the exception that caused the failure
   */
  static void recordFailure(String endpoint, Exception e) {
    String key = normaliseKey(endpoint, e);
    errorCounts.computeIfAbsent(key, _ -> new AtomicLong(0)).incrementAndGet();
    windowTotal.incrementAndGet();

    // Log the very first failure in this window immediately so operators do not have
    // to wait up to FLUSH_INTERVAL_MINUTES to discover that analytics delivery is broken.
    if (firstFailureLogged.compareAndSet(false, true)) {
      FRLogger.warn(
          "Analytics tracking: first delivery failure in this window — "
              + key
              + ". Further failures will be aggregated and reported every "
              + FLUSH_INTERVAL_MINUTES
              + " minutes.");
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
        .forEach(entry -> sb
            .append("  • ")
            .append(entry.getKey())
            .append(": ")
            .append(entry.getValue())
            .append("×\n"));

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
    if (message.length() > 100) {
      message = message.substring(0, 100) + "…";
    }

    return path + " → " + exceptionType + ": " + message;
  }
}




