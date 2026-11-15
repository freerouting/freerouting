package app.freerouting.settings;

/** Preset profiles for post-routing optimizer termination and candidate sweep patience. */
public enum OptimizerPatience {
  /**
   * Fast interactive optimization with early canary stops (default: 2.50% improvement threshold, 12
   * consecutive failures in pass 1, 50 in subsequent passes).
   */
  BALANCED("Balanced (Fast)", 2.50f, 12, 50),

  /**
   * Patient optimization for congested designs (0.50% improvement threshold, 100 consecutive
   * failures in pass 1, 250 in subsequent passes).
   */
  PATIENT("Patient (Congested)", 0.50f, 100, 250),

  /**
   * Exhaustive optimization evaluating all candidate items across every pass (0.00% threshold, 0 /
   * unlimited consecutive failures). Runs until max passes or timeout.
   */
  EXHAUSTIVE("Exhaustive (All Items)", 0.00f, 0, 0);

  private final String displayName;
  private final float defaultImprovementThreshold;
  private final int defaultMaxConsecutiveFailuresPass1;
  private final int defaultMaxConsecutiveFailures;

  OptimizerPatience(
      String displayName,
      float defaultImprovementThreshold,
      int defaultMaxConsecutiveFailuresPass1,
      int defaultMaxConsecutiveFailures) {
    this.displayName = displayName;
    this.defaultImprovementThreshold = defaultImprovementThreshold;
    this.defaultMaxConsecutiveFailuresPass1 = defaultMaxConsecutiveFailuresPass1;
    this.defaultMaxConsecutiveFailures = defaultMaxConsecutiveFailures;
  }

  public String getDisplayName() {
    return displayName;
  }

  public float getDefaultImprovementThreshold() {
    return defaultImprovementThreshold;
  }

  public int getDefaultMaxConsecutiveFailuresPass1() {
    return defaultMaxConsecutiveFailuresPass1;
  }

  public int getDefaultMaxConsecutiveFailures() {
    return defaultMaxConsecutiveFailures;
  }
}
