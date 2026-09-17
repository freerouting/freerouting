package app.freerouting.autoroute;

/**
 * Represents the outcome of an autoroute attempt, including its state, detail message, and an
 * optional {@link FailureReason} that explains why the maze search gave up.
 */
public class AutorouteAttemptResult {

  public AutorouteAttemptState state;
  public String details;

  /** When non-null, explains why the maze search failed. Null on success or non-failure states. */
  public FailureReason failureReason;

  /** Constructs an AutorouteAttemptResult with the specified state and empty details. */
  public AutorouteAttemptResult(AutorouteAttemptState state) {
    this(state, "");
  }

  /**
   * Constructs an AutorouteAttemptResult with the specified state, detail message, and optional
   * failure reason.
   */
  public AutorouteAttemptResult(
      AutorouteAttemptState state, String details, FailureReason failureReason) {
    this.state = state;
    this.details = details;
    this.failureReason = failureReason;
  }

  /** Constructs an AutorouteAttemptResult with the specified state and detail message. */
  public AutorouteAttemptResult(AutorouteAttemptState state, String details) {
    this(state, details, null);
  }

  @Override
  public String toString() {
    return this.state.toString().toUpperCase() + ": " + this.details;
  }
}
