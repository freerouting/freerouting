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

  public CoordinateTransform(IntBox p_design_box, Dimension p_panel_bounds) {
    this.screenBounds = p_panel_bounds;
    this.designBox = p_design_box;
    this.rotationPole = p_design_box.centre_of_gravity();

    int minLl = Math.min(p_design_box.ll.x, p_design_box.ll.y);
    int maxUr = Math.max(p_design_box.ur.x, p_design_box.ur.y);
    if (Math.max(Math.abs(minLl), Math.abs(maxUr)) <= 0.3 * Limits.CRIT_INT) {
      // create an offset to p_design_box to enable deep zoom out
      double designOffset = Math.max(p_design_box.width(), p_design_box.height());
      designBoxWithOffset = p_design_box.offset(designOffset);
    } else {
      // no offset because of danger of integer overflow
      designBoxWithOffset = p_design_box;
    }

    double xScaleFactor = screenBounds.getWidth() / designBoxWithOffset.width();
    double yScaleFactor = screenBounds.getHeight() / designBoxWithOffset.height();

    scaleFactor = Math.min(xScaleFactor, yScaleFactor);
    displayXOffset = scaleFactor * designBoxWithOffset.ll.x;
    displayYOffset = scaleFactor * designBoxWithOffset.ll.y;
  }

  /** Copy constructor */
  public CoordinateTransform(CoordinateTransform p_coordinate_transform) {
    this.screenBounds = new Dimension(p_coordinate_transform.screenBounds);
    this.designBox =
        new IntBox(p_coordinate_transform.designBox.ll, p_coordinate_transform.designBox.ur);
    this.rotationPole =
        new FloatPoint(
            p_coordinate_transform.rotationPole.x, p_coordinate_transform.rotationPole.y);
    this.designBoxWithOffset =
        new IntBox(
            p_coordinate_transform.designBoxWithOffset.ll,
            p_coordinate_transform.designBoxWithOffset.ur);
    this.scaleFactor = p_coordinate_transform.scaleFactor;
    this.displayXOffset = p_coordinate_transform.displayXOffset;
    this.displayYOffset = p_coordinate_transform.displayYOffset;
    this.mirrorLeftRight = p_coordinate_transform.mirrorLeftRight;
    this.mirrorTopBottom = p_coordinate_transform.mirrorTopBottom;
    this.rotation = p_coordinate_transform.rotation;
  }

  /** scale a value from the board to the screen coordinate system */
  public double board_to_screen(double p_val) {
    return p_val * scaleFactor;
  }

  /** scale a value the screen to the board coordinate system */
  public double screen_to_board(double p_val) {
    return p_val / scaleFactor;
  }

  /** transform a geometry.planar.FloatPoint to a java.awt.geom.Point2D */
  public Point2D board_to_screen(FloatPoint p_point) {
    if (p_point == null) {
      return null;
    }

    FloatPoint rotatedPoint = p_point.rotate(this.rotation, this.rotationPole);

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
  public FloatPoint screen_to_board(Point2D p_point) {
    double x;
    double y;
    if (this.mirrorLeftRight) {
      x = designBoxWithOffset.width() - (p_point.getX() - displayXOffset) / scaleFactor - 1;
    } else {
      x = (p_point.getX() + displayXOffset) / scaleFactor;
    }
    if (this.mirrorTopBottom) {
      y = designBoxWithOffset.height() - (p_point.getY() - displayYOffset) / scaleFactor - 1;
    } else {
      y = (p_point.getY() + displayYOffset) / scaleFactor;
    }
    FloatPoint result = new FloatPoint(x, y);
    return result.rotate(-this.rotation, this.rotationPole);
  }

  /** Transforms an angle in radian on the board to an angle on the screen. */
  public double board_to_screen_angle(double p_angle) {
    double result = p_angle + this.rotation;
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
  public Rectangle board_to_screen(IntBox p_box) {
    Point2D corner1 = board_to_screen(p_box.ll.to_float());
    Point2D corner2 = board_to_screen(p_box.ur.to_float());
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
  public IntBox screen_to_board(Rectangle p_rect) {
    FloatPoint corner1 = screen_to_board(new Point2D.Double(p_rect.getX(), p_rect.getY()));
    FloatPoint corner2 =
        screen_to_board(
            new Point2D.Double(
                p_rect.getX() + p_rect.getWidth(), p_rect.getY() + p_rect.getHeight()));
    int llx = (int) Math.floor(Math.min(corner1.x, corner2.x));
    int lly = (int) Math.floor(Math.min(corner1.y, corner2.y));
    int urx = (int) Math.ceil(Math.max(corner1.x, corner2.x));
    int ury = (int) Math.ceil(Math.max(corner1.y, corner2.y));
    return new IntBox(llx, lly, urx, ury);
  }

  /** Returns, if the left side and the right side of the board are swapped. */
  public boolean is_mirror_left_right() {
    return mirrorLeftRight;
  }

  /** If p_value is true, the left side and the right side of the board will be swapped. */
  public void set_mirror_left_right(boolean p_value) {
    mirrorLeftRight = p_value;
  }

  /** Returns, if the top side and the bottom side of the board are swapped. */
  public boolean is_mirror_top_bottom() {
    // Because the origin of display is the upper left corner, the internal value
    // is opposite to the result of this function.
    return !mirrorTopBottom;
  }

  /** If p_value is true, the top side and the bottom side of the board will be swapped. */
  public void set_mirror_top_bottom(boolean p_value) {
    // Because the origin of display is the upper left corner, the internal value
    // will be opposite to the input value of this function.
    mirrorTopBottom = !p_value;
  }

  /** Returns the rotation of the displayed board. */
  public double get_rotation() {
    return rotation;
  }

  /** Sets the rotation of the displayed board to p_value. */
  public void set_rotation(double p_value) {
    rotation = p_value;
  }

  /**
   * Returns the internal rotation snapped to the nearest multiple of 90 degree. The result will be
   * 0, 1, 2 or 3.
   */
  public int get_90_degree_rotation() {
    int multiple = (int) Math.round(Math.toDegrees(rotation) / 90.0);
    while (multiple < 0) {
      multiple += 4;
    }
    while (multiple >= 4) {
      multiple -= 4;
    }
    return multiple;
  }

  public boolean is_zoom_invariant_state_equal(CoordinateTransform other) {
    if (other == null) {
      return false;
    }
    return this.scaleFactor == other.scaleFactor
        && this.rotation == other.rotation
        && this.mirrorLeftRight == other.mirrorLeftRight
        && this.mirrorTopBottom == other.mirrorTopBottom;
  }

  public boolean is_same_transform_state(CoordinateTransform other) {
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
