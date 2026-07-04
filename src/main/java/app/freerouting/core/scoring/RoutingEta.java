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

  /** Current phase description for display ("fanout", "autoroute", "optimizer", "completed", "stopped"). */
  @SerializedName("phase")
  public final String phase;

  /** Total wall-clock seconds elapsed so far. */
  @SerializedName("total_elapsed_seconds")
  public final double totalElapsedSeconds;

  /** Number of items still unrouted. */
  @SerializedName("incomplete_count")
  public final int incompleteCount;

  /** Optional message if routing was stopped early. */
  @SerializedName("stop_reason")
  public final String stopReason;

  /** Human-readable progress text used when confidence is LOW/NONE or in terminal states. */
  public transient String progressText;

  public transient double etaSecondsLow = -1;
  public transient double etaSecondsHigh = -1;

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
   * Generates the appropriate text for the UI Status Panel based on the current phase,
   * confidence, and numerical range.
   */
  public String toDisplayString() {
    // 1. Hard terminal states always win over numeric ETAs.
    if ("completed".equals(phase)) {
      return "Completed.";
    }
    if ("stopped".equals(phase)) {
      return "Stopped.";
    }
    if ("completing".equals(phase)) {
      return "Completing...";
    }

    // 2. Low confidence or explicit fallback text (like Fanout phase)
    if (confidence == Confidence.NONE || confidence == Confidence.LOW) {
      if (progressText != null && !progressText.isEmpty()) {
        return progressText;
      }
      return "Calculating ETA...";
    }

    // 3. Medium/high confidence with a real statistical band: show the range instead of false precision
    if (etaSecondsLow >= 0 && etaSecondsHigh > etaSecondsLow
        && (etaSecondsHigh - etaSecondsLow) > Math.max(5, etaSeconds * 0.15)) {
      return "ETA: " + formatDuration(etaSecondsLow) + " - " + formatDuration(etaSecondsHigh);
    }

    // 4. High confidence single precise number
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
    return minutes + "m " + seconds + "s";
  }

  @Override
  public String toString() {
    return toDisplayString();
  }
}