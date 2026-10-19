package app.freerouting.io.specctra;

import app.freerouting.board.model.items.Item;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.io.CoordinateTransform;
import java.io.IOException;
import java.math.BigDecimal;

/** Encodes SES trace paths without collapsing nonzero segments or moving real point copper. */
final class SesTracePath {
  private final double[] coordinates;

  private SesTracePath(double[] coordinates) {
    this.coordinates = coordinates;
  }

  static SesTracePath from(PolylineTrace wire, CoordinateTransform transform) throws IOException {
    Point[] corners = wire.polyline().corners();
    if (corners.length < 2) {
      throw new IOException("SES trace has fewer than two source corners");
    }
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
    double[] encoded = encode(snapped, transform);
    if (collapsesSegment(encoded, corners)) {
      // Contact snapping must never erase even one nonzero segment inside a longer trace.
      encoded = encode(original, transform);
      if (collapsesSegment(encoded, corners)) {
        throw new IOException(
            "A nonzero SES segment cannot be represented at floating output precision");
      }
    }
    // Point copper intentionally retains two coincident endpoints for KiCad's path importer.
    return new SesTracePath(encoded);
  }

  private static boolean collapsesSegment(double[] coordinates, Point[] original) {
    for (int index = 1; index < original.length; index++) {
      if (!original[index - 1].equals(original[index])
          && coordinates[2 * index - 2] == coordinates[2 * index]
          && coordinates[2 * index - 1] == coordinates[2 * index + 1]) {
        return true;
      }
    }
    return false;
  }

  private static boolean isExactPoint(Point[] corners) {
    for (Point corner : corners) {
      if (!corners[0].equals(corner)) {
        return false;
      }
    }
    return true;
  }

  private static double[] encode(FloatPoint[] corners, CoordinateTransform transform)
      throws IOException {
    double[] encoded = transform.boardToDsn(corners);
    for (double coordinate : encoded) {
      if (!Double.isFinite(coordinate)
          || coordinate <= Long.MIN_VALUE
          || coordinate >= Long.MAX_VALUE) {
        throw new IOException("Non-finite or out-of-range SES trace coordinate");
      }
    }
    return encoded;
  }

  void write(String layer, double width, IdentifierType identifier, IndentFileWriter file)
      throws IOException {
    if (!Double.isFinite(width) || width <= 0) {
      throw new IOException("SES trace width must be finite and positive");
    }
    file.startScope();
    file.write("path ");
    identifier.write(layer, file);
    file.write(" " + format(width));
    for (int index = 0; index < coordinates.length; index += 2) {
      file.newLine();
      file.write(format(coordinates[index]) + " " + format(coordinates[index + 1]));
    }
    file.endScope();
  }

  private static String format(double coordinate) {
    return BigDecimal.valueOf(coordinate).stripTrailingZeros().toPlainString();
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
