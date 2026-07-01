package app.freerouting.core.scoring;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * Value object representing an estimated time of arrival (ETA) for the routing job.
 * Immutable once created; thread-safe by design.
 */
public class RoutingEta implements Serializable {

  /**
   * Confidence level of the ETA estimate.
   */
  public enum Confidence {
    /** Not enough data to estimate yet (first 1-2 passes). */
    NONE,
    /** Rough estimate based on limited data. */
    LOW,
    /** Reasonable estimate with moderate confidence. */
    MEDIUM,
    /** High confidence estimate (many passes completed, stable trend). */
    HIGH
  }

  /** Estimated seconds remaining until routing completes. */
  @SerializedName("eta_seconds")
  public final double etaSeconds;

  /** Confidence level of this estimate. */
  @SerializedName("confidence")
  public final Confidence confidence;

  /** Estimated number of remaining passes (autoroute phase). */
  @SerializedName("estimated_remaining_passes")
  public final int estimatedRemainingPasses;

  /** Current phase description for display ("fanout", "autoroute", "optimizer", "completing"). */
  @SerializedName("phase")
  public final String phase;

  /** Total wall-clock seconds elapsed so far. */
  @SerializedName("total_elapsed_seconds")
  public final double totalElapsedSeconds;

  /** Number of incomplete connections remaining. */
  @SerializedName("incomplete_count")
  public final int incompleteCount;

  /** Reason routing stopped, for display (e.g. "max passes reached", "stagnation", "user stop"). Null if not applicable. */
  @SerializedName("stop_reason")
  public final String stopReason;

  /**
   * Human-readable progress fallback ("Pass 3/8 - 12 remaining") for use when confidence is
   * too low to show a trustworthy time estimate. Null if not applicable (e.g. fanout/optimizer
   * phases, or frozen terminal states).
   */
  @SerializedName("progress_text")
  public String progressText;

  /** Lower bound of the statistical prediction band, in seconds. -1 if not available. */
  @SerializedName("eta_seconds_low")
  public double etaSecondsLow = -1;

  /** Upper bound of the statistical prediction band, in seconds. -1 if not available. */
  @SerializedName("eta_seconds_high")
  public double etaSecondsHigh = -1;

  public RoutingEta(double etaSeconds, Confidence confidence, int estimatedRemainingPasses,
                    String phase, double totalElapsedSeconds, int incompleteCount) {
    this(etaSeconds, confidence, estimatedRemainingPasses, phase, totalElapsedSeconds, incompleteCount, null);
  }

  public RoutingEta(double etaSeconds, Confidence confidence, int estimatedRemainingPasses,
                    String phase, double totalElapsedSeconds, int incompleteCount, String stopReason) {
    this.etaSeconds = etaSeconds;
    this.confidence = confidence;
    this.estimatedRemainingPasses = estimatedRemainingPasses;
    this.phase = phase;
    this.totalElapsedSeconds = totalElapsedSeconds;
    this.incompleteCount = incompleteCount;
    this.stopReason = stopReason;
  }

  /**
   * Returns a readable ETA string for display in the status bar.
   */
  public String toDisplayString() {
    // Pre-start state: no board is currently being routed. Distinct from
    // "Estimating..." (routing in progress, not enough data yet) so a board
    // change can't visually look like an in-progress or completed run.
    if ("idle".equals(phase)) {
      return "";
    }

    // Handle frozen terminal states
    if ("completed".equals(phase)) {
      return "Completed";
    }
    if ("stopped".equals(phase)) {
      String reasonSuffix = (stopReason != null && !stopReason.isEmpty()) ? " — " + stopReason : "";
      if (incompleteCount > 0) {
        return "Stopped (" + incompleteCount + " unrouted)" + reasonSuffix;
      }
      return "Stopped" + reasonSuffix;
    }

    // Not enough data yet for a trustworthy time — show progress instead of a number.
    if (confidence == Confidence.NONE) {
      if (progressText != null && !progressText.isEmpty()) {
        return progressText;
      }
      if (etaSeconds < 0) {
        return "Estimating...";
      }
      return "ETA: ~" + formatDuration(etaSeconds);
    }

    // All done or finishing
    if (incompleteCount == 0 && !"optimizer".equals(phase)) {
      return "Completing...";
    }

    // Low confidence: still show progress text if we have it, since a wide/uncertain
    // band can be more honest than a single number the person will over-trust.
    if (confidence == Confidence.LOW && progressText != null && !progressText.isEmpty()) {
      return progressText;
    }

    // Medium/high confidence with a real statistical band: show the range instead of
    // false precision on a single number.
    if (etaSecondsLow >= 0 && etaSecondsHigh > etaSecondsLow
        && (etaSecondsHigh - etaSecondsLow) > Math.max(5, etaSeconds * 0.15)) {
      return "ETA: " + formatDuration(etaSecondsLow) + " - " + formatDuration(etaSecondsHigh);
    }

    // Show the ETA with appropriate precision based on confidence
    return "ETA: " + formatDuration(etaSeconds);
  }

  /**
   * Formats a duration in seconds into a readable string with decreasing
   * precision as the duration increases.
   */
  private static String formatDuration(double etaSeconds) {
    if (etaSeconds <= 0) {
      return "0s";
    }
    if (etaSeconds > 3600) {
      return ">1h";
    }
    if (etaSeconds < 60) {
      return (int) etaSeconds + "s";
    }
    int minutes = (int) (etaSeconds / 60);
    int seconds = (int) (etaSeconds % 60);
    if (minutes < 60) {
      return minutes + "m " + seconds + "s";
    }
    int hours = minutes / 60;
    minutes = minutes % 60;
    return hours + "h " + minutes + "m";
  }
}