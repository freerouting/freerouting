package app.freerouting.gui.support;

/**
 * Immutable value describing one update of a progress surface.
 *
 * @param status translated high-level status text
 * @param phase translated current phase text
 * @param completed completed work units
 * @param total total work units, or zero when the work is indeterminate
 * @param indeterminate whether the progress bar should animate without a percentage
 * @param cancelEnabled whether cancellation is currently available
 */
public record ProgressSnapshot(
    String status,
    String phase,
    long completed,
    long total,
    boolean indeterminate,
    boolean cancelEnabled) {

  /** Creates a determinate snapshot with cancellation enabled. */
  public ProgressSnapshot(String status, String phase, long completed, long total) {
    this(status, phase, completed, total, false, true);
  }

  /** Creates a snapshot with cancellation enabled. */
  public ProgressSnapshot(
      String status, String phase, long completed, long total, boolean indeterminate) {
    this(status, phase, completed, total, indeterminate, true);
  }

  /** Normalizes optional text and validates progress-counter invariants. */
  public ProgressSnapshot {
    status = status == null ? "" : status;
    phase = phase == null ? "" : phase;
    if (completed < 0) {
      throw new IllegalArgumentException("Completed progress must not be negative");
    }
    if (total < 0) {
      throw new IllegalArgumentException("Total progress must not be negative");
    }
    if (!indeterminate && total == 0 && completed != 0) {
      throw new IllegalArgumentException(
          "Determinate progress with a zero total must have zero completed work");
    }
  }
}
