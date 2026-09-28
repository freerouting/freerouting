package app.freerouting.settings;

/** Selects the optimizer-board score formula independently from router scoring. */
public enum OptimizerScoringVersion {
  /** Existing combined score retained for compatibility. */
  V1_LEGACY,

  /** Excess length, vias, and bends relative to stored lower bounds. */
  V2_LOWER_BOUND
}
