package app.freerouting.io.specctra;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.io.CoordinateTransform;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;

/** Encodes SES trace paths without collapsing nonzero segments or moving real point copper. */
final class SesTracePath {
  private final long[] coordinates;
  private final int precision;

  private SesTracePath(long[] coordinates, int precision) {
    this.coordinates = coordinates;
    this.precision = precision;
  }

  static SesTracePath from(PolylineTrace wire, BasicBoard board, CoordinateTransform transform)
      throws IOException {
    Point[] corners = wire.polyline().corners();
    if (corners.length < 2) {
      throw new IOException("SES trace has fewer than two source corners");
    }
    int resolution = board.communication.resolution;
    int precision =
        board.communication.unit == Unit.UM
                && resolution > 0
                && resolution <= 1000
                && 1000 % resolution == 0
            ? 1000 / resolution
            : 1;
    boolean exactPoint = isExactPoint(corners);
    FloatPoint[] original = new FloatPoint[corners.length];
    FloatPoint[] snapped = new FloatPoint[corners.length];
    for (int index = 0; index < corners.length; index++) {
      original[index] = corners[index].toFloat();
      snapped[index] = original[index];
      // Keep upstream pad/via endpoint snapping, but do not move point copper.
      if (!exactPoint && (index == 0 || index == corners.length - 1)) {
        FloatPoint endpoint = snappedEndpoint(wire, index == 0);
        if (endpoint != null) {
          snapped[index] = endpoint;
        }
      }
    }
    long[] encoded = encode(snapped, transform, precision);
    if (encoded.length < 4 && !exactPoint) {
      // Two contacts can snap to the same pad center. Preserve the actual
      // nonzero trace instead of converting it to fabricated point geometry.
      encoded = encode(original, transform, precision);
      if (encoded.length < 4) {
        throw new IOException("SES trace has no representable segment at the output precision");
      }
    }
    if (encoded.length == 2) {
      // KiCad retains width-bearing point copper with two identical endpoints.
      encoded = new long[] {encoded[0], encoded[1], encoded[0], encoded[1]};
    }
    return new SesTracePath(encoded, precision);
  }

  private static boolean isExactPoint(Point[] corners) {
    for (Point corner : corners) {
      if (!corners[0].equals(corner)) {
        return false;
      }
    }
    return true;
  }

  private static long[] encode(FloatPoint[] corners, CoordinateTransform transform, int precision)
      throws IOException {
    long[] encoded = new long[corners.length * 2];
    int used = 0;
    for (FloatPoint corner : corners) {
      double[] coordinate = transform.boardToDsn(corner);
      long x = quantize(coordinate[0], precision);
      long y = quantize(coordinate[1], precision);
      if (used > 0 && encoded[used - 2] == x && encoded[used - 1] == y) {
        continue;
      }
      encoded[used++] = x;
      encoded[used++] = y;
    }
    return Arrays.copyOf(encoded, used);
  }

  private static long quantize(double coordinate, int precision) throws IOException {
    double scaled = coordinate * precision;
    if (!Double.isFinite(scaled) || scaled <= Long.MIN_VALUE || scaled >= Long.MAX_VALUE) {
      throw new IOException("Non-finite or out-of-range SES trace coordinate");
    }
    return Math.round(scaled);
  }

  void write(String layer, int width, IdentifierType identifier, IndentFileWriter file)
      throws IOException {
    file.startScope();
    file.write("path ");
    identifier.write(layer, file);
    file.write(" " + width);
    for (int index = 0; index < coordinates.length; index += 2) {
      file.newLine();
      file.write(format(coordinates[index]) + " " + format(coordinates[index + 1]));
    }
    file.endScope();
  }

  private String format(long coordinate) {
    return BigDecimal.valueOf(coordinate)
        .divide(BigDecimal.valueOf(precision))
        .stripTrailingZeros()
        .toPlainString();
  }

  /**
   * Snaps an endpoint only to the center of a contacted pad/via when it lies inside the pad
   * inradius. This closes approximation gaps without crossing a neighboring pad.
   */
  static FloatPoint snappedEndpoint(PolylineTrace wire, boolean startSide) {
    Point corner = startSide ? wire.firstCorner() : wire.lastCorner();
    FloatPoint cornerFloat = corner.toFloat();
    java.util.Set<Item> contacts = startSide ? wire.getStartContacts() : wire.getEndContacts();
    int layer = wire.getLayer();
    for (Item contact : contacts) {
      if (!(contact instanceof app.freerouting.board.model.items.DrillItem drill)) {
        continue;
      }
      if (layer < drill.firstLayer() || layer > drill.lastLayer()) {
        continue;
      }
      app.freerouting.geometry.planar.Shape padShape = drill.getShape(layer - drill.firstLayer());
      if (padShape == null) {
        continue;
      }
      FloatPoint center = drill.getCenter().toFloat();
      double centerDistance = cornerFloat.distance(center);
      if (centerDistance <= 0.5) {
        // Already at the center (within rounding) — nothing to fix.
        return null;
      }
      if (centerDistance <= padShape.borderDistance(center)) {
        return center;
      }
    }
    return null;
  }
}
