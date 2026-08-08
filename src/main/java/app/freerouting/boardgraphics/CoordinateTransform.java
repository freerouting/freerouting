package app.freerouting.boardgraphics;

import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.Limits;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.io.Serializable;

/** Transformation function between the board and the screen coordinate systems. */
public class CoordinateTransform implements Serializable {

  final IntBox designBox;
  final IntBox designBoxWithOffset;
  final Dimension screenBounds;
  private final double scaleFactor;
  private final double displayXOffset;
  private final double displayYOffset;
  private final FloatPoint rotationPole;

  /** Left side and right side of the board are swapped. */
  private boolean mirrorLeftRight;

  /** Top side and bottom side of the board are swapped. */
  private boolean mirrorTopBottom = true;

  private double rotation = 0;

  public CoordinateTransform(IntBox pDesignBox, Dimension pPanelBounds) {
    this.screenBounds = pPanelBounds;
    this.designBox = pDesignBox;
    this.rotationPole = pDesignBox.centreOfGravity();

    int minLl = Math.min(pDesignBox.ll.x, pDesignBox.ll.y);
    int maxUr = Math.max(pDesignBox.ur.x, pDesignBox.ur.y);
    if (Math.max(Math.abs(minLl), Math.abs(maxUr)) <= 0.3 * Limits.CRIT_INT) {
      // create an offset to p_design_box to enable deep zoom out
      double designOffset = Math.max(pDesignBox.width(), pDesignBox.height());
      designBoxWithOffset = pDesignBox.offset(designOffset);
    } else {
      // no offset because of danger of integer overflow
      designBoxWithOffset = pDesignBox;
    }

    double xScaleFactor = screenBounds.getWidth() / designBoxWithOffset.width();
    double yScaleFactor = screenBounds.getHeight() / designBoxWithOffset.height();

    scaleFactor = Math.min(xScaleFactor, yScaleFactor);
    displayXOffset = scaleFactor * designBoxWithOffset.ll.x;
    displayYOffset = scaleFactor * designBoxWithOffset.ll.y;
  }

  /** Copy constructor */
  public CoordinateTransform(CoordinateTransform pCoordinateTransform) {
    this.screenBounds = new Dimension(pCoordinateTransform.screenBounds);
    this.designBox =
        new IntBox(pCoordinateTransform.designBox.ll, pCoordinateTransform.designBox.ur);
    this.rotationPole =
        new FloatPoint(pCoordinateTransform.rotationPole.x, pCoordinateTransform.rotationPole.y);
    this.designBoxWithOffset =
        new IntBox(
            pCoordinateTransform.designBoxWithOffset.ll,
            pCoordinateTransform.designBoxWithOffset.ur);
    this.scaleFactor = pCoordinateTransform.scaleFactor;
    this.displayXOffset = pCoordinateTransform.displayXOffset;
    this.displayYOffset = pCoordinateTransform.displayYOffset;
    this.mirrorLeftRight = pCoordinateTransform.mirrorLeftRight;
    this.mirrorTopBottom = pCoordinateTransform.mirrorTopBottom;
    this.rotation = pCoordinateTransform.rotation;
  }

  /** scale a value from the board to the screen coordinate system */
  public double boardToScreen(double pVal) {
    return pVal * scaleFactor;
  }

  /** scale a value the screen to the board coordinate system */
  public double screenToBoard(double pVal) {
    return pVal / scaleFactor;
  }

  /** transform a geometry.planar.FloatPoint to a java.awt.geom.Point2D */
  public Point2D boardToScreen(FloatPoint pPoint) {
    if (pPoint == null) {
      return null;
    }

    FloatPoint rotatedPoint = pPoint.rotate(this.rotation, this.rotationPole);

    double x;
    double y;
    if (this.mirrorLeftRight) {
      x = (designBoxWithOffset.width() - rotatedPoint.x - 1) * scaleFactor + displayXOffset;
    } else {
      x = rotatedPoint.x * scaleFactor - displayXOffset;
    }
    if (this.mirrorTopBottom) {
      y = (designBoxWithOffset.height() - rotatedPoint.y - 1) * scaleFactor + displayYOffset;
    } else {
      y = rotatedPoint.y * scaleFactor - displayYOffset;
    }
    return new Point2D.Double(x, y);
  }

  /** Transform a java.awt.geom.Point2D to a geometry.planar.FloatPoint */
  public FloatPoint screenToBoard(Point2D pPoint) {
    double x;
    double y;
    if (this.mirrorLeftRight) {
      x = designBoxWithOffset.width() - (pPoint.getX() - displayXOffset) / scaleFactor - 1;
    } else {
      x = (pPoint.getX() + displayXOffset) / scaleFactor;
    }
    if (this.mirrorTopBottom) {
      y = designBoxWithOffset.height() - (pPoint.getY() - displayYOffset) / scaleFactor - 1;
    } else {
      y = (pPoint.getY() + displayYOffset) / scaleFactor;
    }
    FloatPoint result = new FloatPoint(x, y);
    return result.rotate(-this.rotation, this.rotationPole);
  }

  /** Transforms an angle in radian on the board to an angle on the screen. */
  public double boardToScreenAngle(double pAngle) {
    double result = pAngle + this.rotation;
    if (this.mirrorLeftRight) {
      result = Math.PI - result;
    }
    if (this.mirrorTopBottom) {
      result = -result;
    }
    while (result >= 2 * Math.PI) {
      result -= 2 * Math.PI;
    }
    while (result < 0) {
      result += 2 * Math.PI;
    }
    return result;
  }

  /**
   * Transform a geometry.planar.IntBox to a java.awt.Rectangle If the internal rotation is not a
   * multiple of Pi/2, a bounding rectangle of the rotated rectangular shape is returned.
   */
  public Rectangle boardToScreen(IntBox pBox) {
    Point2D corner1 = boardToScreen(pBox.ll.toFloat());
    Point2D corner2 = boardToScreen(pBox.ur.toFloat());
    double llX = Math.min(corner1.getX(), corner2.getX());
    double llY = Math.min(corner1.getY(), corner2.getY());
    double dx = Math.abs(corner2.getX() - corner1.getX());
    double dy = Math.abs(corner2.getY() - corner1.getY());
    return new Rectangle(
        (int) Math.floor(llX), (int) Math.floor(llY), (int) Math.ceil(dx), (int) Math.ceil(dy));
  }

  /**
   * Transform a java.awt.Rectangle to a geometry.planar.IntBox If the internal rotation is not a
   * multiple of Pi/2, a bounding box of the rotated rectangular shape is returned.
   */
  public IntBox screenToBoard(Rectangle pRect) {
    FloatPoint corner1 = screenToBoard(new Point2D.Double(pRect.getX(), pRect.getY()));
    FloatPoint corner2 =
        screenToBoard(
            new Point2D.Double(pRect.getX() + pRect.getWidth(), pRect.getY() + pRect.getHeight()));
    int llx = (int) Math.floor(Math.min(corner1.x, corner2.x));
    int lly = (int) Math.floor(Math.min(corner1.y, corner2.y));
    int urx = (int) Math.ceil(Math.max(corner1.x, corner2.x));
    int ury = (int) Math.ceil(Math.max(corner1.y, corner2.y));
    return new IntBox(llx, lly, urx, ury);
  }

  /** Returns, if the left side and the right side of the board are swapped. */
  public boolean isMirrorLeftRight() {
    return mirrorLeftRight;
  }

  /** If p_value is true, the left side and the right side of the board will be swapped. */
  public void setMirrorLeftRight(boolean pValue) {
    mirrorLeftRight = pValue;
  }

  /** Returns, if the top side and the bottom side of the board are swapped. */
  public boolean isMirrorTopBottom() {
    // Because the origin of display is the upper left corner, the internal value
    // is opposite to the result of this function.
    return !mirrorTopBottom;
  }

  /** If p_value is true, the top side and the bottom side of the board will be swapped. */
  public void setMirrorTopBottom(boolean pValue) {
    // Because the origin of display is the upper left corner, the internal value
    // will be opposite to the input value of this function.
    mirrorTopBottom = !pValue;
  }

  /** Returns the rotation of the displayed board. */
  public double getRotation() {
    return rotation;
  }

  /** Sets the rotation of the displayed board to p_value. */
  public void setRotation(double pValue) {
    rotation = pValue;
  }

  /**
   * Returns the internal rotation snapped to the nearest multiple of 90 degree. The result will be
   * 0, 1, 2 or 3.
   */
  public int get90DegreeRotation() {
    int multiple = (int) Math.round(Math.toDegrees(rotation) / 90.0);
    while (multiple < 0) {
      multiple += 4;
    }
    while (multiple >= 4) {
      multiple -= 4;
    }
    return multiple;
  }

  public boolean isZoomInvariantStateEqual(CoordinateTransform other) {
    if (other == null) {
      return false;
    }
    return this.scaleFactor == other.scaleFactor
        && this.rotation == other.rotation
        && this.mirrorLeftRight == other.mirrorLeftRight
        && this.mirrorTopBottom == other.mirrorTopBottom;
  }

  public boolean isSameTransformState(CoordinateTransform other) {
    if (other == null) {
      return false;
    }
    return this.scaleFactor == other.scaleFactor
        && this.displayXOffset == other.displayXOffset
        && this.displayYOffset == other.displayYOffset
        && this.rotation == other.rotation
        && this.mirrorLeftRight == other.mirrorLeftRight
        && this.mirrorTopBottom == other.mirrorTopBottom;
  }
}
