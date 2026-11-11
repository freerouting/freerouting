package app.freerouting.io.specctra.parser;

import app.freerouting.board.model.structure.Unit;
import app.freerouting.io.CoordinateTransform;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Repairs the rounded-rectangle pad polygons written by the Specctra exporter of KiCad 10.0 and
 * earlier.
 *
 * <p>Specctra has no rounded-rectangle primitive, so KiCad exports such pads as polygons and names
 * the padstack {@code RoundRect[<layers>]Pad_<W>x<H>_<R>_um_<chamfer ratio>_<chamfer corners>}. Up
 * to KiCad 10.0 the exporter enlarged the pad by {@code e = round(r * (1 - cos(PI / 36)))} on every
 * side, wrote the enlarged radius {@code R = r + e} in the name (the size stays nominal), and
 * approximated the enlarged corners with chords inscribed in the arcs. The compensation assumes 36
 * segments per circle, but the polygon uses far fewer (four per quarter for a 0603 pad), so the
 * exported shape is wrong in both directions: its straight edges lie {@code e} outside the real pad
 * (false clearance violations between pads placed exactly at clearance) while the middle of each
 * chord lies several micrometres inside the real corner (a via or trace can be placed closer than
 * the clearance to the real copper). KiCad issue 25751, fixed in KiCad 11 (commit 601a6fd4ed),
 * which exports the nominal size with arcs approximated from outside.
 *
 * <p>For such a legacy padstack the polygon is replaced by one with the straight edges at the
 * nominal positions and the corners circumscribed around the real radius, so it always contains the
 * real pad. Padstacks written by a fixed KiCad, pads with chamfered corners and anything that does
 * not match the expected geometry are left untouched.
 */
final class KiCadRoundRectPad {

  private static final Pattern NAME =
      Pattern.compile(
          "RoundRect\\[[^\\]]*](?:\\[[^\\]]*])?Pad_"
              + "(\\d+(?:\\.\\d*)?)x(\\d+(?:\\.\\d*)?)_(\\d+(?:\\.\\d*)?)_um_"
              + "(\\d+(?:\\.\\d*)?)_([0-9A-Fa-f]+)");

  /** {@code 1 - cos(PI / 36)}, the radius compensation of the legacy KiCad exporter. */
  private static final double LEGACY_COMPENSATION = 1.0 - Math.cos(Math.PI / 36.0);

  /**
   * Largest excess of the circumscribed corner over the real arc, in micrometres. A coarser corner
   * reports copper laid along the real arc at the clearance as a violation; 1 um already does so on
   * existing KiCad boards, 0.5 um reports what an exact arc reports on them (twelve segments per
   * quarter for r = 200 um).
   */
  private static final double MAX_CORNER_EXCESS_UM = 0.5;

  private static final int MAX_SEGMENTS_PER_QUARTER = 32;

  private KiCadRoundRectPad() {}

  /**
   * Returns the corrected shape of a padstack, or {@code shape} itself if the padstack is not a
   * rounded-rectangle pad of a legacy KiCad export.
   *
   * @param padstackName the padstack name as read from the DSN file
   * @param shape a shape of that padstack, in DSN coordinates relative to the padstack origin
   * @param dsnUnit the unit of the DSN coordinates
   * @param coordinateTransform the DSN to board transformation, used to keep the corners outside
   *     the real pad after rounding to board coordinates
   */
  static Shape correct(
      String padstackName, Shape shape, Unit dsnUnit, CoordinateTransform coordinateTransform) {
    if (!(shape instanceof Polygon polygon)
        || padstackName == null
        || dsnUnit == null
        || coordinateTransform == null
        || polygon.coor.length < 6) {
      return shape;
    }
    Matcher matcher = NAME.matcher(padstackName);
    if (!matcher.matches() || Long.parseLong(matcher.group(5), 16) != 0) {
      // Not a KiCad rounded-rectangle pad, or one with chamfered corners. A chamfer ratio without
      // chamfered corners (a chamfered-rectangle pad with no corner selected) is a plain one.
      return shape;
    }
    double widthUm = Double.parseDouble(matcher.group(1));
    double heightUm = Double.parseDouble(matcher.group(2));
    double radiusUm = legacyRealRadiusUm(Double.parseDouble(matcher.group(3)));
    double extraUm = Double.parseDouble(matcher.group(3)) - radiusUm;
    if (radiusUm <= 0 || extraUm <= 0 || 2 * radiusUm > Math.min(widthUm, heightUm)) {
      return shape;
    }

    double minX = Double.POSITIVE_INFINITY;
    double minY = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY;
    double maxY = Double.NEGATIVE_INFINITY;
    for (int i = 0; i + 1 < polygon.coor.length; i += 2) {
      minX = Math.min(minX, polygon.coor[i]);
      maxX = Math.max(maxX, polygon.coor[i]);
      minY = Math.min(minY, polygon.coor[i + 1]);
      maxY = Math.max(maxY, polygon.coor[i + 1]);
    }
    // A legacy export is enlarged by extraUm on every side, a fixed one is not.
    double excessX = (Unit.scale(maxX - minX, dsnUnit, Unit.UM) - widthUm) / 2;
    double excessY = (Unit.scale(maxY - minY, dsnUnit, Unit.UM) - heightUm) / 2;
    if (Math.abs(excessX - extraUm) > extraUm / 2 || Math.abs(excessY - extraUm) > extraUm / 2) {
      return shape;
    }

    double centreX = (minX + maxX) / 2;
    double centreY = (minY + maxY) / 2;
    double halfWidth = Unit.scale(widthUm / 2, Unit.UM, dsnUnit);
    double halfHeight = Unit.scale(heightUm / 2, Unit.UM, dsnUnit);
    double radius = Unit.scale(radiusUm, Unit.UM, dsnUnit);
    // The straight edges go to the nearest board coordinate outside the real pad (no change for a
    // pad whose edges fall on the board grid, as they do for KiCad exports at the usual
    // resolutions), and the corner vertices one board unit further out than the tangent polygon,
    // which covers their rounding to board coordinates.
    return new Polygon(
        polygon.layer,
        corners(
            outwards(centreX - halfWidth, -1, coordinateTransform),
            outwards(centreY - halfHeight, -1, coordinateTransform),
            outwards(centreX + halfWidth, 1, coordinateTransform),
            outwards(centreY + halfHeight, 1, coordinateTransform),
            radius,
            segmentsPerQuarter(radiusUm),
            coordinateTransform.boardToDsn(1)));
  }

  /** The DSN coordinate of the nearest board grid line at or beyond {@code dsn} in {@code sign}. */
  private static double outwards(double dsn, int sign, CoordinateTransform coordinateTransform) {
    double board = coordinateTransform.dsnToBoard(dsn);
    double nearest = Math.rint(board);
    // tolerate the floating point error of the DSN to board scaling
    if (Math.abs(board - nearest) > 1e-6) {
      nearest = sign > 0 ? Math.ceil(board) : Math.floor(board);
    }
    return coordinateTransform.boardToDsn(nearest);
  }

  /**
   * The real corner radius of a legacy export, whose name carries {@code r + round(r * (1 - cos(PI
   * / 36)))} with {@code r} in whole nanometres.
   */
  static double legacyRealRadiusUm(double nameRadiusUm) {
    long nameNm = Math.round(nameRadiusUm * 1000);
    long guess = Math.round(nameNm / (1 + LEGACY_COMPENSATION));
    for (long r = guess - 2; r <= guess + 2; r++) {
      if (r > 0 && r + Math.round(r * LEGACY_COMPENSATION) == nameNm) {
        return r / 1000.0;
      }
    }
    return nameRadiusUm / (1 + LEGACY_COMPENSATION);
  }

  /** Smallest segment count whose circumscribed corner exceeds the arc by at most 0.5 um. */
  static int segmentsPerQuarter(double radiusUm) {
    int n = 1;
    while (n < MAX_SEGMENTS_PER_QUARTER
        && radiusUm * (1 / Math.cos(Math.PI / (4 * n)) - 1) > MAX_CORNER_EXCESS_UM) {
      n++;
    }
    return n;
  }

  /**
   * The vertices, counter-clockwise, of the rectangle {@code [left, right] x [bottom, top]} whose
   * corners of radius {@code radius} are replaced by {@code n} segments tangent to the arc. The
   * first and last vertex of each corner lie on the straight edges, pushed along them towards the
   * corner by {@code margin}; the vertices between them are moved out by {@code margin}.
   */
  static double[] corners(
      double left, double bottom, double right, double top, double radius, int n, double margin) {
    double step = Math.PI / 2 / n;
    double along = radius * Math.tan(step / 2) + margin;
    double[] edgeX = {right, left, left, right};
    double[] edgeY = {top, top, bottom, bottom};
    double[] signX = {1, -1, -1, 1};
    double[] signY = {1, 1, -1, -1};
    double[] coor = new double[8 * n];
    int index = 0;
    for (int quarter = 0; quarter < 4; quarter++) {
      double arcCentreX = edgeX[quarter] - signX[quarter] * radius;
      double arcCentreY = edgeY[quarter] - signY[quarter] * radius;
      for (int k = 0; k < n; k++) {
        double x;
        double y;
        if (n == 1) {
          x = edgeX[quarter];
          y = edgeY[quarter];
        } else if (k == 0 || k == n - 1) {
          // On a straight edge: the vertical one first in quarters 0 and 2, last in 1 and 3.
          if ((k == 0) == (quarter % 2 == 0)) {
            x = edgeX[quarter];
            y = arcCentreY + signY[quarter] * along;
          } else {
            x = arcCentreX + signX[quarter] * along;
            y = edgeY[quarter];
          }
        } else {
          double angle = quarter * Math.PI / 2 + (k + 0.5) * step;
          double distance = radius / Math.cos(step / 2) + margin;
          x = arcCentreX + distance * Math.cos(angle);
          y = arcCentreY + distance * Math.sin(angle);
        }
        coor[index++] = x;
        coor[index++] = y;
      }
    }
    return coor;
  }
}
