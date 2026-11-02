package app.freerouting.autoroute.decoupling;

/** Explainable decision bitmask reasons for decoupling capacitor candidate gating and scoring. */
public enum ReasonCode {
  // Hard gates
  NOT_TWO_PIN_SMD(1L << 0),
  SAME_OR_NO_NET(1L << 1),
  ASYMMETRIC_PADS(1L << 2),
  PITCH_OUT_OF_RANGE(1L << 3),
  USER_EXCLUDED(1L << 4),

  // Positive scored evidence
  STRONG_GROUND(1L << 5),
  OWNER_IN_RADIUS(1L << 6),
  MULTI_IC_RAIL(1L << 7),
  PARALLEL_SIBLING(1L << 8),
  SHUNT_CAP_DEGREE(1L << 9),

  // Negative scored penalties
  XTAL_PENALTY(1L << 10),
  CONNECTOR_PENALTY(1L << 11),
  FEW_PINS_PENALTY(1L << 12),
  AMBIGUOUS_GROUND(1L << 13),

  // Pass B bonus
  FILTERED_ISLAND(1L << 14);

  private final long mask;

  ReasonCode(long mask) {
    this.mask = mask;
  }

  /** Bitmask flag corresponding to this reason code. */
  public long mask() {
    return mask;
  }

  /** Returns true if the provided bitmask contains this reason code. */
  public boolean isSet(long bitmask) {
    return (bitmask & mask) != 0;
  }
}

