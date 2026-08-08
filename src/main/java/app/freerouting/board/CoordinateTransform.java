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
      double pUserUnitFactor, Unit pUserUnit, double pBoardUnitFactor, Unit pBoardUnit) {
    userUnit = pUserUnit;
    boardUnit = pBoardUnit;
    userUnitFactor = pUserUnitFactor;
    boardUnitFactor = pBoardUnitFactor;
    scaleFactor = boardUnitFactor / userUnitFactor;

    if (userUnitFactor != 1.0) {
      throw new RuntimeException("userUnitFactor must be 1.0");
    }
  }

  /** Scale a value from the board to the user coordinate system. */
  public double boardToUser(double pValue) {
    return Unit.scale(pValue * scaleFactor, boardUnit, userUnit);
  }

  /** Scale a value from the user to the board coordinate system. */
  public double userToBoard(double pValue) {
    return Unit.scale(pValue / scaleFactor, userUnit, boardUnit);
  }

  /**
   * Transforms a geometry.planar.FloatPoint from the board coordinate space to the user coordinate
   * space.
   */
  public FloatPoint boardToUser(FloatPoint pPoint) {
    return new FloatPoint(boardToUser(pPoint.x), boardToUser(pPoint.y));
  }

  /**
   * Transforms a geometry.planar.FloatPoint from the user coordinate space. to the board coordinate
   * space.
   */
  public FloatPoint userToBoard(FloatPoint pPoint) {
    return new FloatPoint(userToBoard(pPoint.x), userToBoard(pPoint.y));
  }

  public PrintableShape boardToUser(Shape pShape, Locale pLocale) {
    PrintableShape result;
    if (pShape instanceof Circle circle) {
      result = boardToUser(circle, pLocale);
    } else if (pShape instanceof IntBox box) {
      result = boardToUser(box, pLocale);
    } else if (pShape instanceof PolylineShape shape) {
      result = boardToUser(shape, pLocale);
    } else {
      FRLogger.warn("CoordinateTransform.board_to_user not yet implemented for p_shape");
      result = null;
    }
    return result;
  }

  public PrintableShape.Circle boardToUser(Circle pCircle, Locale pLocale) {
    return new PrintableShape.Circle(
        boardToUser(pCircle.center.toFloat()), boardToUser(pCircle.radius), pLocale);
  }

  public PrintableShape.Rectangle boardToUser(IntBox pBox, Locale pLocale) {
    return new PrintableShape.Rectangle(
        boardToUser(pBox.ll.toFloat()), boardToUser(pBox.ur.toFloat()), pLocale);
  }

  public PrintableShape.Polygon boardToUser(PolylineShape pShape, Locale pLocale) {
    FloatPoint[] corners = pShape.cornerApproxArr();
    FloatPoint[] transformedCorners = new FloatPoint[corners.length];
    for (int i = 0; i < corners.length; i++) {
      transformedCorners[i] = boardToUser(corners[i]);
    }
    return new PrintableShape.Polygon(transformedCorners, pLocale);
  }
}
