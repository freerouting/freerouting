package app.freerouting.io;

import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.io.specctra.parser.Circle;
import app.freerouting.io.specctra.parser.Layer;
import app.freerouting.io.specctra.parser.Polygon;
import app.freerouting.io.specctra.parser.Rectangle;
import app.freerouting.io.specctra.parser.Shape;
import app.freerouting.logger.FRLogger;
import java.io.Serializable;

/**
 * Computes transformations between board coordinates and external coordinates, such as Specctra DSN
 * or KiCad JSON coordinates.
 */
public class CoordinateTransform implements Serializable {

  private final double scaleFactor;
  private final double baseX;
  private final double baseY;

  /** Creates a new instance of CoordinateTransform. */
  public CoordinateTransform(double scaleFactor, double baseX, double baseY) {
    this.scaleFactor = scaleFactor;
    this.baseX = baseX;
    this.baseY = baseY;
  }

  /** Scales a value from the board to the external coordinate system. */
  public double boardToDsn(double value) {
    return value / scaleFactor;
  }

  /** Transforms a point from the board to the external coordinate system. */
  public double[] boardToDsn(FloatPoint point) {
    double[] result = new double[2];
    result[0] = boardToDsn(point.x) + baseX;
    result[1] = boardToDsn(point.y) + baseY;
    return result;
  }

  /** Transforms points from the board to the external coordinate system. */
  public double[] boardToDsn(FloatPoint[] points) {
    double[] result = new double[2 * points.length];
    for (int i = 0; i < points.length; i++) {
      result[2 * i] = boardToDsn(points[i].x) + baseX;
      result[2 * i + 1] = boardToDsn(points[i].y) + baseY;
    }
    return result;
  }

  /** Transforms lines from the board to the external coordinate system. */
  public double[] boardToDsn(Line[] lines) {
    double[] result = new double[4 * lines.length];
    for (int i = 0; i < lines.length; i++) {
      FloatPoint a = lines[i].a.toFloat();
      FloatPoint b = lines[i].b.toFloat();
      result[4 * i] = boardToDsn(a.x) + baseX;
      result[4 * i + 1] = boardToDsn(a.y) + baseY;
      result[4 * i + 2] = boardToDsn(b.x) + baseX;
      result[4 * i + 3] = boardToDsn(b.y) + baseY;
    }
    return result;
  }

  /** Transforms a vector from the board to the external coordinate system. */
  public double[] boardToDsn(Vector vector) {
    double[] result = new double[2];
    FloatPoint value = vector.toFloat();
    result[0] = boardToDsn(value.x);
    result[1] = boardToDsn(value.y);
    return result;
  }

  /** Transforms a box from the board to the external coordinate system. */
  public double[] boardToDsn(IntBox box) {
    double[] result = new double[4];
    result[0] = box.ll.x / scaleFactor + baseX;
    result[1] = box.ll.y / scaleFactor + baseY;
    result[2] = box.ur.x / scaleFactor + baseX;
    result[3] = box.ur.y / scaleFactor + baseY;
    return result;
  }

  /** Transforms a board shape to an external shape. */
  public Shape boardToDsn(app.freerouting.geometry.planar.Shape boardShape, Layer layer) {
    Shape result;
    if (boardShape instanceof IntBox box) {
      result = new Rectangle(layer, boardToDsn(box));
    } else if (boardShape instanceof PolylineShape) {
      FloatPoint[] corners = boardShape.cornerApproxArr();
      double[] coordinates = boardToDsn(corners);
      result = new Polygon(layer, coordinates);
    } else if (boardShape instanceof app.freerouting.geometry.planar.Circle boardCircle) {
      double diameter = 2 * boardToDsn(boardCircle.radius);
      double[] centerCoordinates = boardToDsn(boardCircle.center.toFloat());
      result = new Circle(layer, diameter, centerCoordinates[0], centerCoordinates[1]);
    } else {
      FRLogger.warn("CoordinateTransform.board_to_dsn not yet implemented for boardShape");
      result = null;
    }
    return result;
  }

  /** Transforms a point to relative external (vector) coordinates. */
  public double[] boardToDsnRel(FloatPoint point) {
    double[] result = new double[2];
    result[0] = boardToDsn(point.x);
    result[1] = boardToDsn(point.y);
    return result;
  }

  /** Transforms points to relative external (vector) coordinates. */
  public double[] boardToDsnRel(FloatPoint[] points) {
    double[] result = new double[2 * points.length];
    for (int i = 0; i < points.length; i++) {
      result[2 * i] = boardToDsn(points[i].x);
      result[2 * i + 1] = boardToDsn(points[i].y);
    }
    return result;
  }

  /** Transforms a box to relative external (vector) coordinates. */
  public double[] boardToDsnRel(IntBox box) {
    double[] result = new double[4];
    result[0] = box.ll.x / scaleFactor;
    result[1] = box.ll.y / scaleFactor;
    result[2] = box.ur.x / scaleFactor;
    result[3] = box.ur.y / scaleFactor;
    return result;
  }

  /** Transforms a board shape to a relative external shape. */
  public Shape boardToDsnRel(app.freerouting.geometry.planar.Shape boardShape, Layer layer) {
    Shape result;
    if (boardShape instanceof IntBox box) {
      result = new Rectangle(layer, boardToDsnRel(box));
    } else if (boardShape instanceof PolylineShape) {
      FloatPoint[] corners = boardShape.cornerApproxArr();
      double[] coordinates = boardToDsnRel(corners);
      result = new Polygon(layer, coordinates);
    } else if (boardShape instanceof app.freerouting.geometry.planar.Circle boardCircle) {
      double diameter = 2 * boardToDsn(boardCircle.radius);
      double[] centerCoordinates = boardToDsnRel(boardCircle.center.toFloat());
      result = new Circle(layer, diameter, centerCoordinates[0], centerCoordinates[1]);
    } else {
      FRLogger.warn("CoordinateTransform.board_to_dsn not yet implemented for boardShape");
      result = null;
    }
    return result;
  }

  /** Scales a value from the external to the board coordinate system. */
  public double dsnToBoard(double value) {
    return value * scaleFactor;
  }

  /** Transforms an external tuple to a board point. */
  public FloatPoint dsnToBoard(double[] tuple) {
    double x = dsnToBoard(tuple[0] - baseX);
    double y = dsnToBoard(tuple[1] - baseY);
    return new FloatPoint(x, y);
  }

  /** Transforms an external tuple to a board point in relative coordinates. */
  public FloatPoint dsnToBoardRel(double[] tuple) {
    double x = dsnToBoard(tuple[0]);
    double y = dsnToBoard(tuple[1]);
    return new FloatPoint(x, y);
  }
}
