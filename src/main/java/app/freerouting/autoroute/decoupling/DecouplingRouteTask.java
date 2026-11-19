package app.freerouting.autoroute.decoupling;

/**
 * Resolved routing task assigned to a decoupling capacitor candidate.
 *
 * @param candidate the identified capacitor candidate
 * @param supplyPinId item ID of the capacitor's supply pad pin
 * @param groundPinId item ID of the capacitor's ground pad pin
 * @param targetSupplyItemId item ID of the target supply pin/pad to connect to (0 if none)
 * @param targetGroundItemId item ID of the target ground pin/pad (0 if plane via)
 * @param role assigned functional role (PRIMARY_IC_STRAP, SECONDARY_PARALLEL_SIBLING,
 *     PLANE_ONLY_FALLBACK)
 * @param pairedPrimaryCapPinId item ID of the paired primary capacitor's supply pad (0 if none)
 */
public record DecouplingRouteTask(
    CapacitorCandidate candidate,
    int supplyPinId,
    int groundPinId,
    int targetSupplyItemId,
    int targetGroundItemId,
    TaskRole role,
    int pairedPrimaryCapPinId) {

  /** Returns {@code true} if this task routes a primary surface strap to an IC supply pin. */
  public boolean isPrimary() {
    return role == TaskRole.PRIMARY_IC_STRAP;
  }

  /** Returns {@code true} if this task routes a secondary parallel sibling capacitor. */
  public boolean isSecondary() {
    return role == TaskRole.SECONDARY_PARALLEL_SIBLING;
  }

  /** Returns {@code true} if this task is a fallback to dedicated plane vias without a strap. */
  public boolean isPlaneOnly() {
    return role == TaskRole.PLANE_ONLY_FALLBACK;
  }

  /** Returns {@code true} if a valid target supply item is assigned. */
  public boolean hasTargetSupply() {
    return targetSupplyItemId > 0;
  }
}
