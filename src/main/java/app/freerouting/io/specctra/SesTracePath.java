package app.freerouting.io.specctra;

import app.freerouting.board.model.items.Item;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.io.CoordinateTransform;
import java.io.IOException;
import org.apache.commons.numbers.fraction.BigFraction;

/** Encodes SES trace paths without collapsing nonzero segments or moving real point copper. */
final class SesTracePath {
  private final BigFraction[] coordinates;

  private SesTracePath(BigFraction[] coordinates) {
    this.coordinates = coordinates;
  }

  static SesTracePath from(PolylineTrace wire, CoordinateTransform transform) throws IOException {
    return from(wire, transform, SesNumbers.fromDouble(transform.dsnToBoard(1)));
  }

  static SesTracePath from(PolylineTrace wire, CoordinateTransform transform, BigFraction scale)
      throws IOException {
    Point[] corners = wire.polyline().corners();
    if (corners.length < 2) {
      throw new IOException("SES trace has fewer than two source corners");
    }
    boolean exactPoint = isExactPoint(corners);
    Point[] original = corners.clone();
    Point[] snapped = corners.clone();
    for (int index = 0; index < corners.length; index++) {
      // Keep upstream pad/via endpoint snapping, but do not move point copper.
      if (!exactPoint && (index == 0 || index == corners.length - 1)) {
        FloatPoint endpoint = snappedEndpoint(wire, index == 0);
        if (endpoint != null) {
          snapped[index] = endpoint.round();
        }
      }
    }
    BigFraction[] encoded = encode(snapped, transform, scale);
    if (collapsesSegment(encoded, corners)) {
      // Contact snapping must never erase even one nonzero segment inside a longer trace.
      encoded = encode(original, transform, scale);
      if (collapsesSegment(encoded, corners)) {
        throw new IOException(
            "A nonzero SES segment cannot be represented at floating output precision");
      }
    }
    // Point copper intentionally retains two coincident endpoints for KiCad's path importer.
    return new SesTracePath(encoded);
  }

  private static boolean collapsesSegment(BigFraction[] coordinates, Point[] original) {
    for (int index = 1; index < original.length; index++) {
      if (!original[index - 1].equals(original[index])
          && coordinates[2 * index - 2].doubleValue() == coordinates[2 * index].doubleValue()
          && coordinates[2 * index - 1].doubleValue() == coordinates[2 * index + 1].doubleValue()) {
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

  private static BigFraction[] encode(
      Point[] corners, CoordinateTransform transform, BigFraction scale) throws IOException {
    double[] origin = transform.boardToDsn(Point.ZERO.toFloat());
    BigFraction baseX = SesNumbers.fromDouble(origin[0]);
    BigFraction baseY = SesNumbers.fromDouble(origin[1]);
    BigFraction[] encoded = new BigFraction[corners.length * 2];
    for (int index = 0; index < corners.length; index++) {
      var point = corners[index].exactCoordinates();
      if (point.z().signum() == 0) {
        throw new IOException("Non-finite SES trace coordinate");
      }
      encoded[2 * index] = BigFraction.of(point.x(), point.z()).divide(scale).add(baseX);
      encoded[2 * index + 1] = BigFraction.of(point.y(), point.z()).divide(scale).add(baseY);
      for (int axis = 0; axis < 2; axis++) {
        double value = encoded[2 * index + axis].doubleValue();
        if (!Double.isFinite(value) || value <= Long.MIN_VALUE || value >= Long.MAX_VALUE) {
          throw new IOException("Non-finite or out-of-range SES trace coordinate");
        }
      }
    }
    return encoded;
  }

  void write(String layer, double width, IdentifierType identifier, IndentFileWriter file)
      throws IOException {
    if (!Double.isFinite(width) || width <= 0) {
      throw new IOException("SES trace width must be finite and positive");
    }
    write(layer, SesNumbers.fromDouble(width), identifier, file);
  }

  void write(String layer, BigFraction width, IdentifierType identifier, IndentFileWriter file)
      throws IOException {
    if (width.signum() <= 0) {
      throw new IOException("SES trace width must be finite and positive");
    }
    file.startScope();
    file.write("path ");
    identifier.write(layer, file);
    file.write(" " + SesNumbers.format(width));
    for (int index = 0; index < coordinates.length; index += 2) {
      file.newLine();
      file.write(
          SesNumbers.format(coordinates[index]) + " " + SesNumbers.format(coordinates[index + 1]));
    }
    file.endScope();
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
