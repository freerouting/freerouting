package app.freerouting.interactive;

import app.freerouting.geometry.planar.FloatPoint;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.geom.Point2D;

public class SelectRegionState extends InteractiveState {

  protected FloatPoint corner1;
  protected FloatPoint corner2;

  protected SelectRegionState(InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    super(p_parent_state, p_board_handling);
  }

  @Override
  public InteractiveState buttonReleased() {
    if (hdlg != null && hdlg.screenMessages != null) {
      hdlg.screenMessages.setStatusMessage("");
    }
    return complete();
  }

  @Override
  public InteractiveState mouseDragged(FloatPoint p_point) {
    // Early exit on null or redundant micro-movements
    if (p_point == null || (corner2 != null && p_point.equals(corner2))) {
      return this;
    }

    if (corner1 == null) {
      corner1 = p_point;
      if (hdlg != null) {
        hdlg.repaint();
      }
      return this;
    }

    var previousCorner2 = corner2;
    corner2 = p_point;

    if (hdlg != null) {
      var dirtyRect = rubberBandDirtyRect(previousCorner2, corner2);
      // Fall back to full repaint if dirty rect calculation fails
      if (dirtyRect != null) {
        hdlg.repaint(dirtyRect);
      } else {
        hdlg.repaint();
      }
    }
    return this;
  }

  private Rectangle rubberBandDirtyRect(FloatPoint p_old_corner2, FloatPoint p_new_corner2) {
    if (hdlg == null
        || hdlg.graphicsContext == null
        || hdlg.graphicsContext.coordinateTransform == null) {
      return null;
    }

    var transform = hdlg.graphicsContext.coordinateTransform;
    var scCorner1 = transform.boardToScreen(corner1);
    var scNewCorner2 = transform.boardToScreen(p_new_corner2);

    // Fail gracefully if transforms fail
    if (scCorner1 == null || scNewCorner2 == null) {
      return null;
    }

    var dirtyRect = screenRect(scCorner1, scNewCorner2);

    if (p_old_corner2 != null) {
      var scOldCorner2 = transform.boardToScreen(p_old_corner2);
      if (scOldCorner2 != null) {
        // Mutate in-place to avoid GC allocation during rapid drags
        dirtyRect.add(screenRect(scCorner1, scOldCorner2));
      }
    }

    dirtyRect.grow(3, 3); // stroke margin
    return dirtyRect;
  }

  private static Rectangle screenRect(Point2D p_a, Point2D p_b) {
    int x = (int) Math.min(p_a.getX(), p_b.getX());
    int y = (int) Math.min(p_a.getY(), p_b.getY());
    int w = (int) Math.abs(p_a.getX() - p_b.getX()) + 1;
    int h = (int) Math.abs(p_a.getY() - p_b.getY()) + 1;
    return new Rectangle(x, y, w, h);
  }

  @Override
  public void draw(Graphics p_graphics) {
    if (this.returnState != null) {
      this.returnState.draw(p_graphics);
    }

    if (hdlg == null || hdlg.graphicsContext == null) {
      return;
    }

    var currentMouse = hdlg.getCurrentMousePosition();
    if (corner1 == null || currentMouse == null) {
      return;
    }

    corner2 = currentMouse;
    hdlg.graphicsContext.drawRectangle(corner1, corner2, 1, Color.white, p_graphics, 1);
  }
}
