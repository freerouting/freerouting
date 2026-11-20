package app.freerouting.board.model.items;

import java.io.Serializable;

/**
 * How the host CAD system connects a pad to the fill of a plane of its net, when the design file
 * does not say it: solidly, through the spokes of a thermal relief, or not at all. Lengths are in
 * board units, positions and angles in the frame of the board (angles in degrees,
 * counter-clockwise).
 *
 * @param mode the connection set on the pad or its footprint; INHERITED leaves it to the plane
 * @param throughHole whether the pad is a plated through-hole pad
 * @param centerX the centre of the copper shape of the pad
 * @param centerY the centre of the copper shape of the pad
 * @param halfWidth half the width of the shape before rotation
 * @param halfHeight half the height of the shape before rotation
 * @param orientationDeg the orientation of the pad on the board
 * @param spokeAngleDeg the angle of the first thermal spoke in the frame of the pad
 * @param circular whether the shape is a circle
 * @param thermalGap the thermal gap set on the pad; 0 leaves it to the plane
 * @param spokeWidth the spoke width set on the pad; 0 leaves it to the plane
 */
public record PadPlaneConnection(
    Mode mode,
    boolean throughHole,
    double centerX,
    double centerY,
    double halfWidth,
    double halfHeight,
    double orientationDeg,
    double spokeAngleDeg,
    boolean circular,
    int thermalGap,
    int spokeWidth)
    implements Serializable {

  /** The kinds of connection between a pad and the fill of a plane. */
  public enum Mode {
    /** Not set here: the plane decides. */
    INHERITED,
    /** The fill keeps its clearance to the pad: the plane never connects it. */
    NONE,
    /** The fill stops at the thermal gap; up to four spokes cross it. */
    THERMAL,
    /** The fill covers the pad. */
    SOLID,
    /** A thermal relief for a through-hole pad, a solid connection for any other pad. */
    THT_THERMAL
  }

  /** One spoke of a thermal relief: its centre line from the edge of the pad to its far end. */
  public record Spoke(double startX, double startY, double endX, double endY, double halfWidth) {}

  /** The connection of this pad to a plane whose own setting is {@code planeMode}. */
  public Mode resolve(Mode planeMode) {
    Mode result = this.mode != Mode.INHERITED ? this.mode : planeMode;
    if (result == Mode.THT_THERMAL) {
      result = this.throughHole ? Mode.THERMAL : Mode.SOLID;
    }
    return result;
  }

  /** The thermal gap of this pad on a plane whose gap is {@code planeGap}. */
  public double gapOn(double planeGap) {
    return this.thermalGap > 0 ? this.thermalGap : planeGap;
  }

  /**
   * The four spokes of the thermal relief of this pad, as the zone filler of KiCad builds them: one
   * every 90 degrees from the spoke angle, from the centre of the pad to the bounding box of its
   * shape enlarged by the thermal gap and half the minimum width of the fill. The fill keeps a
   * spoke only if its far end lies in copper. No spoke is built when the spoke would be narrower
   * than the minimum width of the fill, the width of a spoke being limited to the narrow side of
   * the pad.
   *
   * @param planeGap the thermal gap of the plane
   * @param planeSpokeWidth the spoke width of the plane
   * @param minWidth the minimum width of the fill of the plane
   * @param endSlack the small length by which KiCad pushes the far end further out
   */
  public Spoke[] spokes(double planeGap, double planeSpokeWidth, double minWidth, double endSlack) {
    double width = this.spokeWidth > 0 ? Math.max(this.spokeWidth, minWidth) : planeSpokeWidth;
    if (width <= 0) {
      width = minWidth;
    }
    width = Math.min(width, 2 * Math.min(this.halfWidth, this.halfHeight));
    if (width < minWidth || width <= 0) {
      return new Spoke[0];
    }
    double reach = gapOn(planeGap) + minWidth / 2.0 + endSlack;
    Spoke[] result = new Spoke[4];
    for (int i = 0; i < 4; i++) {
      double inPad = Math.toRadians(this.spokeAngleDeg + 90.0 * i);
      double cos = Math.cos(inPad);
      double sin = Math.sin(inPad);
      double start;
      double end;
      if (this.circular) {
        start = this.halfWidth;
        end = this.halfWidth + reach;
      } else {
        start = toBoxEdge(cos, sin, this.halfWidth, this.halfHeight);
        end = toBoxEdge(cos, sin, this.halfWidth + reach, this.halfHeight + reach);
      }
      double onBoard = Math.toRadians(this.orientationDeg + this.spokeAngleDeg + 90.0 * i);
      double dx = Math.cos(onBoard);
      double dy = Math.sin(onBoard);
      result[i] =
          new Spoke(
              this.centerX + dx * start,
              this.centerY + dy * start,
              this.centerX + dx * end,
              this.centerY + dy * end,
              width / 2.0);
    }
    return result;
  }

  /** The distance from the centre of a box to its edge along the direction (cos, sin). */
  private static double toBoxEdge(double cos, double sin, double halfWidth, double halfHeight) {
    double absCos = Math.abs(cos);
    double absSin = Math.abs(sin);
    if (absCos < 1e-9) {
      return halfHeight;
    }
    if (absSin < 1e-9) {
      return halfWidth;
    }
    return Math.min(halfWidth / absCos, halfHeight / absSin);
  }
}
