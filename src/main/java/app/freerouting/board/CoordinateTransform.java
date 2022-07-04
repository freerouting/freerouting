package app.freerouting.board;

import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.logger.FRLogger;

/** Class for transforming objects between user coordinate space and board coordinate space. */
public class CoordinateTransform implements java.io.Serializable {

  /** The unit used for user coordinates */
  public final Unit user_unit;
  /** The factor of the user unit */
  public final double user_unit_factor;
  /** The unit used for board coordinates */
  public final Unit board_unit;
  /** The factor of the board unit */
  public final double board_unit_factor;
  /**
   * The factor used for transforming coordinates between user coordinate space and board coordinate
   * space
   */
  private final double scale_factor;

  /** Creates a new instance of CoordinateTransform */
  public CoordinateTransform(
      double p_user_unit_factor, Unit p_user_unit, double p_board_unit_factor, Unit p_board_unit) {
    user_unit = p_user_unit;
    board_unit = p_board_unit;
    user_unit_factor = p_user_unit_factor;
    board_unit_factor = p_board_unit_factor;
    scale_factor = board_unit_factor / user_unit_factor;
  }

  /** Scale a value from the board to the user coordinate system. */
  public double board_to_user(double p_value) {
    return Unit.scale(p_value * scale_factor, board_unit, user_unit);
  }

  /** Scale a value from the user to the board coordinate system. */
  public double user_to_board(double p_value) {
    return Unit.scale(p_value / scale_factor, user_unit, board_unit);
  }

  /**
   * Transforms a geometry.planar.FloatPoint from the board coordinate space to the user coordinate
   * space.
   */
  public FloatPoint board_to_user(FloatPoint p_point) {
    return new FloatPoint(board_to_user(p_point.x), board_to_user(p_point.y));
  }

  /**
   * Transforms a geometry.planar.FloatPoint from the user coordinate space. to the board coordinate
   * space.
   */
  public FloatPoint user_to_board(FloatPoint p_point) {
    return new FloatPoint(user_to_board(p_point.x), user_to_board(p_point.y));
  }

  public PrintableShape board_to_user(
      app.freerouting.geometry.planar.Shape p_shape, java.util.Locale p_locale) {
    PrintableShape result;
    if (p_shape instanceof app.freerouting.geometry.planar.Circle) {
      result = board_to_user((app.freerouting.geometry.planar.Circle) p_shape, p_locale);
    } else if (p_shape instanceof app.freerouting.geometry.planar.IntBox) {
      result = board_to_user((app.freerouting.geometry.planar.IntBox) p_shape, p_locale);
    } else if (p_shape instanceof app.freerouting.geometry.planar.PolylineShape) {
      result = board_to_user((app.freerouting.geometry.planar.PolylineShape) p_shape, p_locale);
    } else {
      FRLogger.warn("CoordinateTransform.board_to_user not yet implemented for p_shape");
      result = null;
    }
    return result;
  }

  public PrintableShape.Circle board_to_user(
      app.freerouting.geometry.planar.Circle p_circle, java.util.Locale p_locale) {
    return new PrintableShape.Circle(
        board_to_user(p_circle.center.to_float()), board_to_user(p_circle.radius), p_locale);
  }

  public PrintableShape.Rectangle board_to_user(
      app.freerouting.geometry.planar.IntBox p_box, java.util.Locale p_locale) {
    return new PrintableShape.Rectangle(
        board_to_user(p_box.ll.to_float()), board_to_user(p_box.ur.to_float()), p_locale);
  }

  public PrintableShape.Polygon board_to_user(
      app.freerouting.geometry.planar.PolylineShape p_shape, java.util.Locale p_locale) {
    FloatPoint[] corners = p_shape.corner_approx_arr();
    FloatPoint[] transformed_corners = new FloatPoint[corners.length];
    for (int i = 0; i < corners.length; ++i) {
      transformed_corners[i] = board_to_user(corners[i]);
    }
    return new PrintableShape.Polygon(transformed_corners, p_locale);
  }
}
