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
  private final double displayXoffset;
  private final double displayYoffset;
  private final FloatPoint rotationPole;

  /** Left side and right side of the board are swapped. */
  private boolean mirrorLeftRight;

  /** Top side and bottom side of the board are swapped. */
  private boolean mirrorTopBottom = true;

  private double rotation = 0;

  /** Creates a coordinate transform for the given design bounds and panel size. */
  public CoordinateTransform(IntBox designBox, Dimension panelBounds) {
    this.screenBounds = panelBounds;
    this.designBox = designBox;
    this.rotationPole = designBox.centreOfGravity();

    int minLl = Math.min(designBox.ll.x, designBox.ll.y);
    int maxUr = Math.max(designBox.ur.x, designBox.ur.y);
    if (Math.max(Math.abs(minLl), Math.abs(maxUr)) <= 0.3 * Limits.CRIT_INT) {
      // create an offset to p_design_box to enable deep zoom out
      double designOffset = Math.max(designBox.width(), designBox.height());
      designBoxWithOffset = designBox.offset(designOffset);
    } else {
      // no offset because of danger of integer overflow
      designBoxWithOffset = designBox;
    }

    double scaleFactorX = screenBounds.getWidth() / designBoxWithOffset.width();
    double scaleFactorY = screenBounds.getHeight() / designBoxWithOffset.height();

    scaleFactor = Math.min(scaleFactorX, scaleFactorY);
    displayXoffset = scaleFactor * designBoxWithOffset.ll.x;
    displayYoffset = scaleFactor * designBoxWithOffset.ll.y;
  }

  /** Copy constructor. */
  public CoordinateTransform(CoordinateTransform coordinateTransform) {
    this.screenBounds = new Dimension(coordinateTransform.screenBounds);
    this.designBox = new IntBox(coordinateTransform.designBox.ll, coordinateTransform.designBox.ur);
    this.rotationPole =
        new FloatPoint(coordinateTransform.rotationPole.x, coordinateTransform.rotationPole.y);
    this.designBoxWithOffset =
        new IntBox(
            coordinateTransform.designBoxWithOffset.ll, coordinateTransform.designBoxWithOffset.ur);
    this.scaleFactor = coordinateTransform.scaleFactor;
    this.displayXoffset = coordinateTransform.displayXoffset;
    this.displayYoffset = coordinateTransform.displayYoffset;
    this.mirrorLeftRight = coordinateTransform.mirrorLeftRight;
    this.mirrorTopBottom = coordinateTransform.mirrorTopBottom;
    this.rotation = coordinateTransform.rotation;
  }

  /** Scales a value from the board to the screen coordinate system. */
  public double boardToScreen(double val) {
    return val * scaleFactor;
  }

  /** Transforms a board {@link FloatPoint} to screen coordinates. */
  public Point2D boardToScreen(FloatPoint point) {
    if (point == null) {
      return null;
    }

    FloatPoint rotatedPoint = point.rotate(this.rotation, this.rotationPole);

    double x;
    double y;
    if (this.mirrorLeftRight) {
      x = (designBoxWithOffset.width() - rotatedPoint.x - 1) * scaleFactor + displayXoffset;
    } else {
      x = rotatedPoint.x * scaleFactor - displayXoffset;
    }
    if (this.mirrorTopBottom) {
      y = (designBoxWithOffset.height() - rotatedPoint.y - 1) * scaleFactor + displayYoffset;
    } else {
      y = rotatedPoint.y * scaleFactor - displayYoffset;
    }
    return new Point2D.Double(x, y);
  }

  /**
   * Transforms a board {@link IntBox} to a screen {@link Rectangle}.
   *
   * <p>If the internal rotation is not a multiple of Pi/2, a bounding rectangle of the rotated
   * shape is returned.
   */
  public Rectangle boardToScreen(IntBox box) {
    Point2D corner1 = boardToScreen(box.ll.toFloat());
    Point2D corner2 = boardToScreen(box.ur.toFloat());
    double llX = Math.min(corner1.getX(), corner2.getX());
    double llY = Math.min(corner1.getY(), corner2.getY());
    double dx = Math.abs(corner2.getX() - corner1.getX());
    double dy = Math.abs(corner2.getY() - corner1.getY());
    return new Rectangle(
        (int) Math.floor(llX), (int) Math.floor(llY), (int) Math.ceil(dx), (int) Math.ceil(dy));
  }

  /** Scales a value from the screen to the board coordinate system. */
  public double screenToBoard(double val) {
    return val / scaleFactor;
  }

  /** Transforms screen coordinates to a board {@link FloatPoint}. */
  public FloatPoint screenToBoard(Point2D point) {
    double x;
    double y;
    if (this.mirrorLeftRight) {
      x = designBoxWithOffset.width() - (point.getX() - displayXoffset) / scaleFactor - 1;
    } else {
      x = (point.getX() + displayXoffset) / scaleFactor;
    }
    if (this.mirrorTopBottom) {
      y = designBoxWithOffset.height() - (point.getY() - displayYoffset) / scaleFactor - 1;
    } else {
      y = (point.getY() + displayYoffset) / scaleFactor;
    }
    FloatPoint result = new FloatPoint(x, y);
    return result.rotate(-this.rotation, this.rotationPole);
  }

  /**
   * Transforms a screen {@link Rectangle} to a board {@link IntBox}.
   *
   * <p>If the internal rotation is not a multiple of Pi/2, a bounding box of the rotated shape is
   * returned.
   */
  public IntBox screenToBoard(Rectangle rect) {
    FloatPoint corner1 = screenToBoard(new Point2D.Double(rect.getX(), rect.getY()));
    FloatPoint corner2 =
        screenToBoard(
            new Point2D.Double(rect.getX() + rect.getWidth(), rect.getY() + rect.getHeight()));
    int llx = (int) Math.floor(Math.min(corner1.x, corner2.x));
    int lly = (int) Math.floor(Math.min(corner1.y, corner2.y));
    int urx = (int) Math.ceil(Math.max(corner1.x, corner2.x));
    int ury = (int) Math.ceil(Math.max(corner1.y, corner2.y));
    return new IntBox(llx, lly, urx, ury);
  }

  /** Transforms an angle in radians on the board to an angle on the screen. */
  public double boardToScreenAngle(double angle) {
    double result = angle + this.rotation;
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

  /** Returns whether the left and right sides of the board are swapped. */
  public boolean isMirrorLeftRight() {
    return mirrorLeftRight;
  }

  /** If p_value is true, the left side and the right side of the board will be swapped. */
  public void setMirrorLeftRight(boolean value) {
    mirrorLeftRight = value;
  }

  /** Returns, if the top side and the bottom side of the board are swapped. */
  public boolean isMirrorTopBottom() {
    // Because the origin of display is the upper left corner, the internal value
    // is opposite to the result of this function.
    return !mirrorTopBottom;
  }

  /** If p_value is true, the top side and the bottom side of the board will be swapped. */
  public void setMirrorTopBottom(boolean value) {
    // Because the origin of display is the upper left corner, the internal value
    // will be opposite to the input value of this function.
    mirrorTopBottom = !value;
  }

  /** Returns the rotation of the displayed board. */
  public double getRotation() {
    return rotation;
  }

  /** Sets the rotation of the displayed board to p_value. */
  public void setRotation(double value) {
    rotation = value;
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

  /** Returns whether the zoom-invariant transform state matches {@code other}. */
  public boolean isZoomInvariantStateEqual(CoordinateTransform other) {
    if (other == null) {
      return false;
    }
    return this.scaleFactor == other.scaleFactor
        && this.rotation == other.rotation
        && this.mirrorLeftRight == other.mirrorLeftRight
        && this.mirrorTopBottom == other.mirrorTopBottom;
  }

  /** Returns whether the full transform state matches {@code other}. */
  public boolean isSameTransformState(CoordinateTransform other) {
    if (other == null) {
      return false;
    }
    return this.scaleFactor == other.scaleFactor
        && this.displayXoffset == other.displayXoffset
        && this.displayYoffset == other.displayYoffset
        && this.rotation == other.rotation
        && this.mirrorLeftRight == other.mirrorLeftRight
        && this.mirrorTopBottom == other.mirrorTopBottom;
  }
}
