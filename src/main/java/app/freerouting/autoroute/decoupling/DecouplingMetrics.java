package app.freerouting.autoroute.decoupling;

/**
 * Diagnostic and physical routing metrics for an individual decoupling capacitor.
 *
 * @param componentId unique ID of the decoupling capacitor component
 * @param componentName reference designator or name of the capacitor (e.g. "C1")
 * @param ownerPinId ID of the paired IC supply pin
 * @param ownerName reference designator and pin name of the owner IC pin (e.g. "U1-VCC")
 * @param strapLengthMm physical length of the surface strap in millimetres (0.0 if not routed)
 * @param viaCount number of dedicated vias placed for this decoupling capacitor
 * @param groundViaDistanceMm distance in millimetres from ground pad center to dedicated ground via
 *     (0.0 if no via)
 * @param routed whether the decoupling strap or connection was successfully completed
 * @param status diagnostic outcome status (e.g. ROUTED, ALREADY_CONNECTED, LAYER_MISMATCH,
 *     DISTANCE_EXCEEDED, NO_ROUTE_FOUND, DRC_ROLLBACK, NO_OWNER_IN_RADIUS)
 */
public record DecouplingMetrics(
    int componentId,
    String componentName,
    int ownerPinId,
    String ownerName,
    double strapLengthMm,
    int viaCount,
    double groundViaDistanceMm,
    boolean routed,
    String status) {

  /** Backward-compatible constructor defaulting status to ROUTED or FAILED. */
  public DecouplingMetrics(
      int componentId,
      String componentName,
      int ownerPinId,
      String ownerName,
      double strapLengthMm,
      int viaCount,
      double groundViaDistanceMm,
      boolean routed) {
    this(
        componentId,
        componentName,
        ownerPinId,
        ownerName,
        strapLengthMm,
        viaCount,
        groundViaDistanceMm,
        routed,
        routed ? "ROUTED" : "FAILED");
  }
}
