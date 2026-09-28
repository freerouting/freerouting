package app.freerouting.settings;

/** Selects the router-board score formula independently from optimizer scoring. */
public enum RouterScoringVersion {
  /** Existing combined score retained for compatibility. */
  V1_LEGACY,

  /** Completion plus discrete and continuous clearance penalties. */
  V2_CONTINUOUS
}
