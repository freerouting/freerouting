package app.freerouting.autoroute;

/** Represents the outcome of an autoroute attempt, including its state and detail message. */
public class AutorouteAttemptResult {

  public AutorouteAttemptState state;
  public String details;

  /** Constructs an AutorouteAttemptResult with the specified state and empty details. */
  public AutorouteAttemptResult(AutorouteAttemptState state) {
    this.state = state;
    this.details = "";
  }

  /** Constructs an AutorouteAttemptResult with the specified state and detail message. */
  public AutorouteAttemptResult(AutorouteAttemptState state, String details) {
    this.state = state;
    this.details = details;
  }

  @Override
  public String toString() {
    return this.state.toString().toUpperCase() + ": " + this.details;
  }
}
