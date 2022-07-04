package app.freerouting.designforms.specctra;

import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;

/** Computes transformations between a specctra dsn-file coordinates and board coordinates. */
public class CoordinateTransform implements java.io.Serializable {

  private final double scale_factor;
  private final double base_x;
  private final double base_y;

  /**
   * Creates a new instance of CoordinateTransform. The base point of the dsn coordinate system will
   * be translated to zero in the board coordinate system.
   */
  public CoordinateTransform(double p_scale_factor, double p_base_x, double p_base_y) {
    scale_factor = p_scale_factor;
    base_x = p_base_x;
    base_y = p_base_y;
  }

  /** Scale a value from the board to the dsn coordinate system */
  public double board_to_dsn(double p_val) {
    return p_val / scale_factor;
  }

  /** Scale a value from the dsn to the board coordinate system */
  public double dsn_to_board(double p_val) {
    return p_val * scale_factor;
  }

  /** Transforms a geometry.planar.FloatPoint to a tuple of doubles in the dsn coordinate system. */
  public double[] board_to_dsn(FloatPoint p_point) {
    double[] result = new double[2];
    result[0] = board_to_dsn(p_point.x) + base_x;
    result[1] = board_to_dsn(p_point.y) + base_y;
    return result;
  }

  /**
   * Transforms a geometry.planar.FloatPoint to a tuple of doubles in the dsn coordinate system in
   * relative (vector) coordinates.
   */
  public double[] board_to_dsn_rel(FloatPoint p_point) {
    double[] result = new double[2];
    result[0] = board_to_dsn(p_point.x);
    result[1] = board_to_dsn(p_point.y);
    return result;
  }

  /**
   * Transforms an array of n geometry.planar.FloatPoints to an array of 2*n doubles in the dsn
   * coordinate system.
   */
  public double[] board_to_dsn(FloatPoint[] p_points) {
    double[] result = new double[2 * p_points.length];
    for (int i = 0; i < p_points.length; ++i) {
      result[2 * i] = board_to_dsn(p_points[i].x) + base_x;
      result[2 * i + 1] = board_to_dsn(p_points[i].y) + base_y;
    }
    return result;
  }

  /**
   * Transforms an array of n geometry.planar.Lines to an array of 4*n doubles in the dsn coordinate
   * system.
   */
  public double[] board_to_dsn(Line[] p_lines) {
    double[] result = new double[4 * p_lines.length];
    for (int i = 0; i < p_lines.length; ++i) {
      FloatPoint a = p_lines[i].a.to_float();
      FloatPoint b = p_lines[i].b.to_float();
      result[4 * i] = board_to_dsn(a.x) + base_x;
      result[4 * i + 1] = board_to_dsn(a.y) + base_y;
      result[4 * i + 2] = board_to_dsn(b.x) + base_x;
      result[4 * i + 3] = board_to_dsn(b.y) + base_y;
    }
    return result;
  }

  /**
   * Transforms an array of n geometry.planar.FloatPoints to an array of 2*n doubles in the dsn
   * coordinate system in relative (vector) coordinates.
   */
  public double[] board_to_dsn_rel(FloatPoint[] p_points) {
    double[] result = new double[2 * p_points.length];
    for (int i = 0; i < p_points.length; ++i) {
      result[2 * i] = board_to_dsn(p_points[i].x);
      result[2 * i + 1] = board_to_dsn(p_points[i].y);
    }
    return result;
  }

  /** Transforms a geometry.planar.Vector to a tuple of doubles in the dsn coordinate system. */
  public double[] board_to_dsn(Vector p_vector) {
    double[] result = new double[2];
    FloatPoint v = p_vector.to_float();
    result[0] = board_to_dsn(v.x);
    result[1] = board_to_dsn(v.y);
    return result;
  }

  /** Transforms a dsn tuple to a geometry.planar.FloatPoint */
  public FloatPoint dsn_to_board(double[] p_tuple) {
    double x = dsn_to_board(p_tuple[0] - base_x);
    double y = dsn_to_board(p_tuple[1] - base_y);
    return new FloatPoint(x, y);
  }

  /** Transforms a dsn tuple to a geometry.planar.FloatPoint in relative (vector) coordinates. */
  public FloatPoint dsn_to_board_rel(double[] p_tuple) {
    double x = dsn_to_board(p_tuple[0]);
    double y = dsn_to_board(p_tuple[1]);
    return new FloatPoint(x, y);
  }

  /** Transforms a geometry.planar.Intbox to the coordinates of a Rectangle. */
  public double[] board_to_dsn(IntBox p_box) {
    double[] result = new double[4];
    result[0] = p_box.ll.x / scale_factor + base_x;
    result[1] = p_box.ll.y / scale_factor + base_y;
    result[2] = p_box.ur.x / scale_factor + base_x;
    result[3] = p_box.ur.y / scale_factor + base_y;
    return result;
  }

  /** Transforms a geometry.planar.Intbox to a Rectangle in relative (vector) coordinates. */
  public double[] board_to_dsn_rel(IntBox p_box) {
    double[] result = new double[4];
    result[0] = p_box.ll.x / scale_factor;
    result[1] = p_box.ll.y / scale_factor;
    result[2] = p_box.ur.x / scale_factor;
    result[3] = p_box.ur.y / scale_factor;
    return result;
  }

  /** Transforms a board shape to a dsn shape. */
  public Shape board_to_dsn(app.freerouting.geometry.planar.Shape p_board_shape, Layer p_layer) {
    Shape result;
    if (p_board_shape instanceof IntBox) {
      result = new Rectangle(p_layer, board_to_dsn((IntBox) p_board_shape));
    } else if (p_board_shape instanceof PolylineShape) {
      FloatPoint[] corners = p_board_shape.corner_approx_arr();
      double[] coors = board_to_dsn(corners);
      result = new Polygon(p_layer, coors);
    } else if (p_board_shape instanceof app.freerouting.geometry.planar.Circle) {
      app.freerouting.geometry.planar.Circle board_circle =
          (app.freerouting.geometry.planar.Circle) p_board_shape;
      double diameter = 2 * board_to_dsn(board_circle.radius);
      double[] center_coor = board_to_dsn(board_circle.center.to_float());
      result = new Circle(p_layer, diameter, center_coor[0], center_coor[1]);
    } else {
      FRLogger.warn("CoordinateTransform.board_to_dsn not yet implemented for p_board_shape");
      result = null;
    }
    return result;
  }

  /**
   * Transforms the relative (vector) coordinates of a geometry.planar.Shape to a specctra dsn
   * shape.
   */
  public Shape board_to_dsn_rel(
      app.freerouting.geometry.planar.Shape p_board_shape, Layer p_layer) {
    Shape result;
    if (p_board_shape instanceof IntBox) {
      result = new Rectangle(p_layer, board_to_dsn_rel((IntBox) p_board_shape));
    } else if (p_board_shape instanceof PolylineShape) {
      FloatPoint[] corners = p_board_shape.corner_approx_arr();
      double[] coors = board_to_dsn_rel(corners);
      result = new Polygon(p_layer, coors);
    } else if (p_board_shape instanceof app.freerouting.geometry.planar.Circle) {
      app.freerouting.geometry.planar.Circle board_circle =
          (app.freerouting.geometry.planar.Circle) p_board_shape;
      double diameter = 2 * board_to_dsn(board_circle.radius);
      double[] center_coor = board_to_dsn_rel(board_circle.center.to_float());
      result = new Circle(p_layer, diameter, center_coor[0], center_coor[1]);
    } else {
      FRLogger.warn("CoordinateTransform.board_to_dsn not yet implemented for p_board_shape");
      result = null;
    }
    return result;
  }
}
