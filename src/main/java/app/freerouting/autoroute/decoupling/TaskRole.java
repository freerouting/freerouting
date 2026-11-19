package app.freerouting.autoroute.decoupling;

/** Functional role assigned to a decoupling routing task by {@link DecouplingAssignmentPlanner}. */
public enum TaskRole {
  /**
   * Primary low-inductance surface strap connecting a capacitor supply pad directly to a dedicated
   * IC supply pin.
   */
  PRIMARY_IC_STRAP,

  /**
   * Secondary parallel capacitor in a multi-capacitor bank (ReasonCode.PARALLEL_SIBLING),
   * connecting via V-star to the IC supply pin or flanking strap to the primary capacitor supply
   * pad.
   */
  SECONDARY_PARALLEL_SIBLING,

  /**
   * Decoupling capacitor without an assigned IC pin (or exceeding maximum owner radius), receiving
   * dedicated ground and power plane vias without a surface strap.
   */
  PLANE_ONLY_FALLBACK
}
