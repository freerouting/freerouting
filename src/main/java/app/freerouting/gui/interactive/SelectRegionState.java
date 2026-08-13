package app.freerouting.gui.interactive;

import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.gui.session.GuiBoardManager;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.geom.Point2D;

/** Interactive state for selecting a rectangular region on the board. */
public class SelectRegionState extends InteractiveState {

  protected FloatPoint corner1;
  protected FloatPoint corner2;

  /** Creates a new instance of SelectRegionState. */
  protected SelectRegionState(InteractiveState parentState, GuiBoardManager boardHandling) {
    super(parentState, boardHandling);
  }

  @Override
  public InteractiveState buttonReleased() {
    if (hdlg != null && hdlg.screenMessages != null) {
      hdlg.screenMessages.setStatusMessage("");
    }
    return complete();
  }

  @Override
  public InteractiveState mouseDragged(FloatPoint point) {
    // Early exit on null or redundant micro-movements
    if (point == null || (corner2 != null && point.equals(corner2))) {
      return this;
    }

    if (corner1 == null) {
      corner1 = point;
      if (hdlg != null) {
        hdlg.repaint();
      }
      return this;
    }

    var previousCorner2 = corner2;
    corner2 = point;

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

  private Rectangle rubberBandDirtyRect(FloatPoint oldCorner2, FloatPoint newCorner2) {
    if (hdlg == null
        || hdlg.graphicsContext == null
        || hdlg.graphicsContext.coordinateTransform == null) {
      return null;
    }

    var transform = hdlg.graphicsContext.coordinateTransform;
    var scCorner1 = transform.boardToScreen(corner1);
    var scNewCorner2 = transform.boardToScreen(newCorner2);

    // Fail gracefully if transforms fail
    if (scCorner1 == null || scNewCorner2 == null) {
      return null;
    }

    var dirtyRect = screenRect(scCorner1, scNewCorner2);

    if (oldCorner2 != null) {
      var scOldCorner2 = transform.boardToScreen(oldCorner2);
      if (scOldCorner2 != null) {
        // Mutate in-place to avoid GC allocation during rapid drags
        dirtyRect.add(screenRect(scCorner1, scOldCorner2));
      }
    }

    dirtyRect.grow(3, 3); // stroke margin
    return dirtyRect;
  }

  private static Rectangle screenRect(Point2D a, Point2D b) {
    int x = (int) Math.min(a.getX(), b.getX());
    int y = (int) Math.min(a.getY(), b.getY());
    int w = (int) Math.abs(a.getX() - b.getX()) + 1;
    int h = (int) Math.abs(a.getY() - b.getY()) + 1;
    return new Rectangle(x, y, w, h);
  }

  @Override
  public void draw(Graphics graphics) {
    if (this.returnState != null) {
      this.returnState.draw(graphics);
    }

    if (hdlg == null || hdlg.graphicsContext == null) {
      return;
    }

    var currentMouse = hdlg.getCurrentMousePosition();
    if (corner1 == null || currentMouse == null) {
      return;
    }

    corner2 = currentMouse;
    hdlg.graphicsContext.drawRectangle(corner1, corner2, 1, Color.white, graphics, 1);
  }
}
