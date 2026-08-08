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
  public CoordinateTransform(double pScaleFactor, double pBaseX, double pBaseY) {
    scaleFactor = pScaleFactor;
    baseX = pBaseX;
    baseY = pBaseY;
  }

  /** Scale a value from the board to the external coordinate system */
  public double boardToDsn(double pVal) {
    return pVal / scaleFactor;
  }

  /** Scale a value from the external to the board coordinate system */
  public double dsnToBoard(double pVal) {
    return pVal * scaleFactor;
  }

  /**
   * Transforms a geometry.planar.FloatPoint to a tuple of doubles in the external coordinate
   * system.
   */
  public double[] boardToDsn(FloatPoint pPoint) {
    double[] result = new double[2];
    result[0] = boardToDsn(pPoint.x) + baseX;
    result[1] = boardToDsn(pPoint.y) + baseY;
    return result;
  }

  /**
   * Transforms a geometry.planar.FloatPoint to a tuple of doubles in the external coordinate system
   * in relative (vector) coordinates.
   */
  public double[] boardToDsnRel(FloatPoint pPoint) {
    double[] result = new double[2];
    result[0] = boardToDsn(pPoint.x);
    result[1] = boardToDsn(pPoint.y);
    return result;
  }

  /**
   * Transforms an array of n geometry.planar.FloatPoints to an array of 2*n doubles in the external
   * coordinate system.
   */
  public double[] boardToDsn(FloatPoint[] pPoints) {
    double[] result = new double[2 * pPoints.length];
    for (int i = 0; i < pPoints.length; i++) {
      result[2 * i] = boardToDsn(pPoints[i].x) + baseX;
      result[2 * i + 1] = boardToDsn(pPoints[i].y) + baseY;
    }
    return result;
  }

  /**
   * Transforms an array of n geometry.planar.Lines to an array of 4*n doubles in the external
   * coordinate system.
   */
  public double[] boardToDsn(Line[] pLines) {
    double[] result = new double[4 * pLines.length];
    for (int i = 0; i < pLines.length; i++) {
      FloatPoint a = pLines[i].a.toFloat();
      FloatPoint b = pLines[i].b.toFloat();
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
  public double[] boardToDsnRel(FloatPoint[] pPoints) {
    double[] result = new double[2 * pPoints.length];
    for (int i = 0; i < pPoints.length; i++) {
      result[2 * i] = boardToDsn(pPoints[i].x);
      result[2 * i + 1] = boardToDsn(pPoints[i].y);
    }
    return result;
  }

  /**
   * Transforms a geometry.planar.Vector to a tuple of doubles in the external coordinate system.
   */
  public double[] boardToDsn(Vector pVector) {
    double[] result = new double[2];
    FloatPoint v = pVector.toFloat();
    result[0] = boardToDsn(v.x);
    result[1] = boardToDsn(v.y);
    return result;
  }

  /** Transforms an external tuple to a geometry.planar.FloatPoint */
  public FloatPoint dsnToBoard(double[] pTuple) {
    double x = dsnToBoard(pTuple[0] - baseX);
    double y = dsnToBoard(pTuple[1] - baseY);
    return new FloatPoint(x, y);
  }

  /**
   * Transforms an external tuple to a geometry.planar.FloatPoint in relative (vector) coordinates.
   */
  public FloatPoint dsnToBoardRel(double[] pTuple) {
    double x = dsnToBoard(pTuple[0]);
    double y = dsnToBoard(pTuple[1]);
    return new FloatPoint(x, y);
  }

  /** Transforms a geometry.planar.Intbox to the coordinates of a Rectangle. */
  public double[] boardToDsn(IntBox pBox) {
    double[] result = new double[4];
    result[0] = pBox.ll.x / scaleFactor + baseX;
    result[1] = pBox.ll.y / scaleFactor + baseY;
    result[2] = pBox.ur.x / scaleFactor + baseX;
    result[3] = pBox.ur.y / scaleFactor + baseY;
    return result;
  }

  /** Transforms a geometry.planar.Intbox to a Rectangle in relative (vector) coordinates. */
  public double[] boardToDsnRel(IntBox pBox) {
    double[] result = new double[4];
    result[0] = pBox.ll.x / scaleFactor;
    result[1] = pBox.ll.y / scaleFactor;
    result[2] = pBox.ur.x / scaleFactor;
    result[3] = pBox.ur.y / scaleFactor;
    return result;
  }

  /** Transforms a board shape to an external shape. */
  public Shape boardToDsn(app.freerouting.geometry.planar.Shape pBoardShape, Layer pLayer) {
    Shape result;
    if (pBoardShape instanceof IntBox box) {
      result = new Rectangle(pLayer, boardToDsn(box));
    } else if (pBoardShape instanceof PolylineShape) {
      FloatPoint[] corners = pBoardShape.cornerApproxArr();
      double[] coors = boardToDsn(corners);
      result = new Polygon(pLayer, coors);
    } else if (pBoardShape instanceof app.freerouting.geometry.planar.Circle board_circle) {
      double diameter = 2 * boardToDsn(board_circle.radius);
      double[] centerCoor = boardToDsn(board_circle.center.toFloat());
      result = new Circle(pLayer, diameter, centerCoor[0], centerCoor[1]);
    } else {
      FRLogger.warn("CoordinateTransform.board_to_dsn not yet implemented for p_board_shape");
      result = null;
    }
    return result;
  }

  /**
   * Transforms the relative (vector) coordinates of a geometry.planar.Shape to an external shape.
   */
  public Shape boardToDsnRel(app.freerouting.geometry.planar.Shape pBoardShape, Layer pLayer) {
    Shape result;
    if (pBoardShape instanceof IntBox box) {
      result = new Rectangle(pLayer, boardToDsnRel(box));
    } else if (pBoardShape instanceof PolylineShape) {
      FloatPoint[] corners = pBoardShape.cornerApproxArr();
      double[] coors = boardToDsnRel(corners);
      result = new Polygon(pLayer, coors);
    } else if (pBoardShape instanceof app.freerouting.geometry.planar.Circle board_circle) {
      double diameter = 2 * boardToDsn(board_circle.radius);
      double[] centerCoor = boardToDsnRel(board_circle.center.toFloat());
      result = new Circle(pLayer, diameter, centerCoor[0], centerCoor[1]);
    } else {
      FRLogger.warn("CoordinateTransform.board_to_dsn not yet implemented for p_board_shape");
      result = null;
    }
    return result;
  }
}
