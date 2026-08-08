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
 * Computes transformations between board coordinates and external (e.g. Specctra DSN, KiCad JSON)
 * coordinates.
 */
public class CoordinateTransform implements Serializable {

  private final double scaleFactor;
  private final double baseX;
  private final double baseY;

  /** Creates a new instance of CoordinateTransform. */
  public CoordinateTransform(double p_scale_factor, double p_base_x, double p_base_y) {
    scaleFactor = p_scale_factor;
    baseX = p_base_x;
    baseY = p_base_y;
  }

  /** Scale a value from the board to the external coordinate system */
  public double boardToDsn(double p_val) {
    return p_val / scaleFactor;
  }

  /** Scale a value from the external to the board coordinate system */
  public double dsnToBoard(double p_val) {
    return p_val * scaleFactor;
  }

  /**
   * Transforms a geometry.planar.FloatPoint to a tuple of doubles in the external coordinate
   * system.
   */
  public double[] boardToDsn(FloatPoint p_point) {
    double[] result = new double[2];
    result[0] = boardToDsn(p_point.x) + baseX;
    result[1] = boardToDsn(p_point.y) + baseY;
    return result;
  }

  /**
   * Transforms a geometry.planar.FloatPoint to a tuple of doubles in the external coordinate system
   * in relative (vector) coordinates.
   */
  public double[] boardToDsnRel(FloatPoint p_point) {
    double[] result = new double[2];
    result[0] = boardToDsn(p_point.x);
    result[1] = boardToDsn(p_point.y);
    return result;
  }

  /**
   * Transforms an array of n geometry.planar.FloatPoints to an array of 2*n doubles in the external
   * coordinate system.
   */
  public double[] boardToDsn(FloatPoint[] p_points) {
    double[] result = new double[2 * p_points.length];
    for (int i = 0; i < p_points.length; i++) {
      result[2 * i] = boardToDsn(p_points[i].x) + baseX;
      result[2 * i + 1] = boardToDsn(p_points[i].y) + baseY;
    }
    return result;
  }

  /**
   * Transforms an array of n geometry.planar.Lines to an array of 4*n doubles in the external
   * coordinate system.
   */
  public double[] boardToDsn(Line[] p_lines) {
    double[] result = new double[4 * p_lines.length];
    for (int i = 0; i < p_lines.length; i++) {
      FloatPoint a = p_lines[i].a.toFloat();
      FloatPoint b = p_lines[i].b.toFloat();
      result[4 * i] = boardToDsn(a.x) + baseX;
      result[4 * i + 1] = boardToDsn(a.y) + baseY;
      result[4 * i + 2] = boardToDsn(b.x) + baseX;
      result[4 * i + 3] = boardToDsn(b.y) + baseY;
    }
    return result;
  }

  /**
   * Transforms an array of n geometry.planar.FloatPoints to an array of 2*n doubles in the external
   * coordinate system in relative (vector) coordinates.
   */
  public double[] boardToDsnRel(FloatPoint[] p_points) {
    double[] result = new double[2 * p_points.length];
    for (int i = 0; i < p_points.length; i++) {
      result[2 * i] = boardToDsn(p_points[i].x);
      result[2 * i + 1] = boardToDsn(p_points[i].y);
    }
    return result;
  }

  /**
   * Transforms a geometry.planar.Vector to a tuple of doubles in the external coordinate system.
   */
  public double[] boardToDsn(Vector p_vector) {
    double[] result = new double[2];
    FloatPoint v = p_vector.toFloat();
    result[0] = boardToDsn(v.x);
    result[1] = boardToDsn(v.y);
    return result;
  }

  /** Transforms an external tuple to a geometry.planar.FloatPoint */
  public FloatPoint dsnToBoard(double[] p_tuple) {
    double x = dsnToBoard(p_tuple[0] - baseX);
    double y = dsnToBoard(p_tuple[1] - baseY);
    return new FloatPoint(x, y);
  }

  /**
   * Transforms an external tuple to a geometry.planar.FloatPoint in relative (vector) coordinates.
   */
  public FloatPoint dsnToBoardRel(double[] p_tuple) {
    double x = dsnToBoard(p_tuple[0]);
    double y = dsnToBoard(p_tuple[1]);
    return new FloatPoint(x, y);
  }

  /** Transforms a geometry.planar.Intbox to the coordinates of a Rectangle. */
  public double[] boardToDsn(IntBox p_box) {
    double[] result = new double[4];
    result[0] = p_box.ll.x / scaleFactor + baseX;
    result[1] = p_box.ll.y / scaleFactor + baseY;
    result[2] = p_box.ur.x / scaleFactor + baseX;
    result[3] = p_box.ur.y / scaleFactor + baseY;
    return result;
  }

  /** Transforms a geometry.planar.Intbox to a Rectangle in relative (vector) coordinates. */
  public double[] boardToDsnRel(IntBox p_box) {
    double[] result = new double[4];
    result[0] = p_box.ll.x / scaleFactor;
    result[1] = p_box.ll.y / scaleFactor;
    result[2] = p_box.ur.x / scaleFactor;
    result[3] = p_box.ur.y / scaleFactor;
    return result;
  }

  /** Transforms a board shape to an external shape. */
  public Shape boardToDsn(app.freerouting.geometry.planar.Shape p_board_shape, Layer p_layer) {
    Shape result;
    if (p_board_shape instanceof IntBox box) {
      result = new Rectangle(p_layer, boardToDsn(box));
    } else if (p_board_shape instanceof PolylineShape) {
      FloatPoint[] corners = p_board_shape.cornerApproxArr();
      double[] coors = boardToDsn(corners);
      result = new Polygon(p_layer, coors);
    } else if (p_board_shape instanceof app.freerouting.geometry.planar.Circle board_circle) {
      double diameter = 2 * boardToDsn(board_circle.radius);
      double[] centerCoor = boardToDsn(board_circle.center.toFloat());
      result = new Circle(p_layer, diameter, centerCoor[0], centerCoor[1]);
    } else {
      FRLogger.warn("CoordinateTransform.board_to_dsn not yet implemented for p_board_shape");
      result = null;
    }
    return result;
  }

  /**
   * Transforms the relative (vector) coordinates of a geometry.planar.Shape to an external shape.
   */
  public Shape boardToDsnRel(
      app.freerouting.geometry.planar.Shape p_board_shape, Layer p_layer) {
    Shape result;
    if (p_board_shape instanceof IntBox box) {
      result = new Rectangle(p_layer, boardToDsnRel(box));
    } else if (p_board_shape instanceof PolylineShape) {
      FloatPoint[] corners = p_board_shape.cornerApproxArr();
      double[] coors = boardToDsnRel(corners);
      result = new Polygon(p_layer, coors);
    } else if (p_board_shape instanceof app.freerouting.geometry.planar.Circle board_circle) {
      double diameter = 2 * boardToDsn(board_circle.radius);
      double[] centerCoor = boardToDsnRel(board_circle.center.toFloat());
      result = new Circle(p_layer, diameter, centerCoor[0], centerCoor[1]);
    } else {
      FRLogger.warn("CoordinateTransform.board_to_dsn not yet implemented for p_board_shape");
      result = null;
    }
    return result;
  }
}
