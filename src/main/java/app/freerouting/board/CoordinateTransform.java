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

  /** The unit used for user coordinates. */
  public final Unit userUnit;

  /** The factor of the user unit. */
  public final double userUnitFactor;

  /** The unit used for board coordinates. */
  public final Unit boardUnit;

  /** The factor of the board unit. */
  public final double boardUnitFactor;

  /**
   * The factor used for transforming coordinates between user coordinate space and board
   * coordinate space.
   */
  private final double scaleFactor;

  /** Creates a new instance of CoordinateTransform. */
  public CoordinateTransform(
      double userUnitFactor, Unit userUnit, double boardUnitFactor, Unit boardUnit) {
    this.userUnit = userUnit;
    this.boardUnit = boardUnit;
    this.userUnitFactor = userUnitFactor;
    this.boardUnitFactor = boardUnitFactor;
    scaleFactor = boardUnitFactor / userUnitFactor;

    if (userUnitFactor != 1.0) {
      throw new RuntimeException("userUnitFactor must be 1.0");
    }
  }

  /** Scales a value from the board to the user coordinate system. */
  public double boardToUser(double value) {
    return Unit.scale(value * scaleFactor, boardUnit, userUnit);
  }

  /**
   * Transforms a geometry.planar.FloatPoint from the board coordinate space to the user coordinate
   * space.
   */
  public FloatPoint boardToUser(FloatPoint point) {
    return new FloatPoint(boardToUser(point.x), boardToUser(point.y));
  }

  /** Transforms a board shape to a printable user-coordinate representation. */
  public PrintableShape boardToUser(Shape shape, Locale locale) {
    PrintableShape result;
    if (shape instanceof Circle circle) {
      result = boardToUser(circle, locale);
    } else if (shape instanceof IntBox box) {
      result = boardToUser(box, locale);
    } else if (shape instanceof PolylineShape polylineShape) {
      result = boardToUser(polylineShape, locale);
    } else {
      FRLogger.warn("CoordinateTransform.board_to_user not yet implemented for p_shape");
      result = null;
    }
    return result;
  }

  /** Transforms a board circle to a printable user-coordinate circle. */
  public PrintableShape.Circle boardToUser(Circle circle, Locale locale) {
    return new PrintableShape.Circle(
        boardToUser(circle.center.toFloat()), boardToUser(circle.radius), locale);
  }

  /** Transforms a board bounding box to a printable user-coordinate rectangle. */
  public PrintableShape.Rectangle boardToUser(IntBox box, Locale locale) {
    return new PrintableShape.Rectangle(
        boardToUser(box.ll.toFloat()), boardToUser(box.ur.toFloat()), locale);
  }

  /** Transforms a board polyline shape to a printable user-coordinate polygon. */
  public PrintableShape.Polygon boardToUser(PolylineShape shape, Locale locale) {
    FloatPoint[] corners = shape.cornerApproxArr();
    FloatPoint[] transformedCorners = new FloatPoint[corners.length];
    for (int i = 0; i < corners.length; i++) {
      transformedCorners[i] = boardToUser(corners[i]);
    }
    return new PrintableShape.Polygon(transformedCorners, locale);
  }

  /** Scales a value from the user to the board coordinate system. */
  public double userToBoard(double value) {
    return Unit.scale(value / scaleFactor, userUnit, boardUnit);
  }

  /**
   * Transforms a geometry.planar.FloatPoint from the user coordinate space to the board coordinate
   * space.
   */
  public FloatPoint userToBoard(FloatPoint point) {
    return new FloatPoint(userToBoard(point.x), userToBoard(point.y));
  }
}
