package app.freerouting.board;

import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.util.Locale;

/** Class for transforming objects between user coordinate space and board coordinate space. */
public class CoordinateTransform implements Serializable {

  /** The unit used for user coordinates */
  public final Unit userUnit;

  /** The factor of the user unit */
  public final double userUnitFactor;

  /** The unit used for board coordinates */
  public final Unit boardUnit;

  /** The factor of the board unit */
  public final double boardUnitFactor;

  /**
   * The factor used for transforming coordinates between user coordinate space and board coordinate
   * space
   */
  private final double scaleFactor;

  /** Creates a new instance of CoordinateTransform */
  public CoordinateTransform(
      double p_user_unit_factor, Unit p_user_unit, double p_board_unit_factor, Unit p_board_unit) {
    userUnit = p_user_unit;
    boardUnit = p_board_unit;
    userUnitFactor = p_user_unit_factor;
    boardUnitFactor = p_board_unit_factor;
    scaleFactor = boardUnitFactor / userUnitFactor;

    if (userUnitFactor != 1.0) {
      throw new RuntimeException("userUnitFactor must be 1.0");
    }
  }

  /** Scale a value from the board to the user coordinate system. */
  public double boardToUser(double p_value) {
    return Unit.scale(p_value * scaleFactor, boardUnit, userUnit);
  }

  /** Scale a value from the user to the board coordinate system. */
  public double userToBoard(double p_value) {
    return Unit.scale(p_value / scaleFactor, userUnit, boardUnit);
  }

  /**
   * Transforms a geometry.planar.FloatPoint from the board coordinate space to the user coordinate
   * space.
   */
  public FloatPoint boardToUser(FloatPoint p_point) {
    return new FloatPoint(boardToUser(p_point.x), boardToUser(p_point.y));
  }

  /**
   * Transforms a geometry.planar.FloatPoint from the user coordinate space. to the board coordinate
   * space.
   */
  public FloatPoint userToBoard(FloatPoint p_point) {
    return new FloatPoint(userToBoard(p_point.x), userToBoard(p_point.y));
  }

  public PrintableShape boardToUser(Shape p_shape, Locale p_locale) {
    PrintableShape result;
    if (p_shape instanceof Circle circle) {
      result = boardToUser(circle, p_locale);
    } else if (p_shape instanceof IntBox box) {
      result = boardToUser(box, p_locale);
    } else if (p_shape instanceof PolylineShape shape) {
      result = boardToUser(shape, p_locale);
    } else {
      FRLogger.warn("CoordinateTransform.board_to_user not yet implemented for p_shape");
      result = null;
    }
    return result;
  }

  public PrintableShape.Circle boardToUser(Circle p_circle, Locale p_locale) {
    return new PrintableShape.Circle(
        boardToUser(p_circle.center.toFloat()), boardToUser(p_circle.radius), p_locale);
  }

  public PrintableShape.Rectangle boardToUser(IntBox p_box, Locale p_locale) {
    return new PrintableShape.Rectangle(
        boardToUser(p_box.ll.toFloat()), boardToUser(p_box.ur.toFloat()), p_locale);
  }

  public PrintableShape.Polygon boardToUser(PolylineShape p_shape, Locale p_locale) {
    FloatPoint[] corners = p_shape.cornerApproxArr();
    FloatPoint[] transformedCorners = new FloatPoint[corners.length];
    for (int i = 0; i < corners.length; i++) {
      transformedCorners[i] = boardToUser(corners[i]);
    }
    return new PrintableShape.Polygon(transformedCorners, p_locale);
  }
}
