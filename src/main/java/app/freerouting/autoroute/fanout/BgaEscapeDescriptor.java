package app.freerouting.autoroute.fanout;

import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.structure.Component;
import java.util.List;

/**
 * Geometric, topological, and net disposition descriptor for an area-array BGA package.
 * Encapsulates component-local (u, v) pin geometry, rotation angle θ, pitch P, and ring depths.
 *
 * @param component the board component
 * @param rotationDeg component placement rotation in degrees
 * @param pitchUm nominal pitch in micrometers
 * @param pitchBoardUnits nominal pitch in internal board units
 * @param latticeClass classified ball array lattice topology
 * @param pins list of pin descriptors belonging to this BGA
 * @param minX minimum local u-coordinate bounding BGA balls
 * @param maxX maximum local u-coordinate bounding BGA balls
 * @param minY minimum local v-coordinate bounding BGA balls
 * @param maxY maximum local v-coordinate bounding BGA balls
 * @param maxRing maximum concentric Chebyshev ring depth
 */
public record BgaEscapeDescriptor(
    Component component,
    double rotationDeg,
    double pitchUm,
    double pitchBoardUnits,
    LatticeClass latticeClass,
    List<BgaPinDescriptor> pins,
    double minX,
    double maxX,
    double minY,
    double maxY,
    int maxRing) {

  /** Lattice topology classification. */
  public enum LatticeClass {
    ORTHOGONAL_GRID,
    DEPOPULATED_CAVITY_GRID
  }

  /** Disposition category for each BGA ball. */
  public enum PinDisposition {
    /** Mechanical / unrouted NC balls or nets belonging to ignore_net_classes. */
    PASSIVE_OBSTACLE,
    /** Power/ground return pads on plane nets (eligible for plane drops or shared vias). */
    PLANE_DROP,
    /** Standard signal pins that require an escape stub to the perimeter. */
    SIGNAL_ESCAPE
  }

  /**
   * Per-pin descriptor within the component-local coordinate system.
   *
   * @param pin the original board pin
   * @param localU local X/U coordinate rotated around component centroid
   * @param localV local Y/V coordinate rotated around component centroid
   * @param ring concentric ring index (0 = outermost perimeter)
   * @param canonicalIndex unique sequential pin index (0 ... N_pins - 1)
   * @param disposition routing disposition category
   * @param netNo net number or 0 if netless
   * @param clearanceClassIdx clearance class index
   */
  public record BgaPinDescriptor(
      Pin pin,
      double localU,
      double localV,
      int ring,
      int canonicalIndex,
      PinDisposition disposition,
      int netNo,
      int clearanceClassIdx,
      int targetLayer,
      int breakoutSector) {

    /** Backwards-compatible constructor defaulting targetLayer and breakoutSector to 0. */
    public BgaPinDescriptor(
        Pin pin,
        double localU,
        double localV,
        int ring,
        int canonicalIndex,
        PinDisposition disposition,
        int netNo,
        int clearanceClassIdx) {
      this(pin, localU, localV, ring, canonicalIndex, disposition, netNo, clearanceClassIdx, 0, 0);
    }
  }
}
