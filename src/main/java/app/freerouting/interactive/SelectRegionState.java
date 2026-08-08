package app.freerouting.interactive;

import app.freerouting.geometry.planar.FloatPoint;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.geom.Point2D;

public class SelectRegionState extends InteractiveState {

  protected FloatPoint corner1;
  protected FloatPoint corner2;

  protected SelectRegionState(InteractiveState pParentState, GuiBoardManager pBoardHandling) {
    super(pParentState, pBoardHandling);
  }

  @Override
  public InteractiveState buttonReleased() {
    if (hdlg != null && hdlg.screenMessages != null) {
      hdlg.screenMessages.setStatusMessage("");
    }
    return complete();
  }

  @Override
  public InteractiveState mouseDragged(FloatPoint pPoint) {
    // Early exit on null or redundant micro-movements
    if (pPoint == null || (corner2 != null && pPoint.equals(corner2))) {
      return this;
    }

    if (corner1 == null) {
      corner1 = pPoint;
      if (hdlg != null) {
        hdlg.repaint();
      }
      return this;
    }

    var previousCorner2 = corner2;
    corner2 = pPoint;

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

  private Rectangle rubberBandDirtyRect(FloatPoint pOldCorner2, FloatPoint pNewCorner2) {
    if (hdlg == null
        || hdlg.graphicsContext == null
        || hdlg.graphicsContext.coordinateTransform == null) {
      return null;
    }

    var transform = hdlg.graphicsContext.coordinateTransform;
    var scCorner1 = transform.boardToScreen(corner1);
    var scNewCorner2 = transform.boardToScreen(pNewCorner2);

    // Fail gracefully if transforms fail
    if (scCorner1 == null || scNewCorner2 == null) {
      return null;
    }

    var dirtyRect = screenRect(scCorner1, scNewCorner2);

    if (pOldCorner2 != null) {
      var scOldCorner2 = transform.boardToScreen(pOldCorner2);
      if (scOldCorner2 != null) {
        // Mutate in-place to avoid GC allocation during rapid drags
        dirtyRect.add(screenRect(scCorner1, scOldCorner2));
      }
    }

    dirtyRect.grow(3, 3); // stroke margin
    return dirtyRect;
  }

  private static Rectangle screenRect(Point2D pA, Point2D pB) {
    int x = (int) Math.min(pA.getX(), pB.getX());
    int y = (int) Math.min(pA.getY(), pB.getY());
    int w = (int) Math.abs(pA.getX() - pB.getX()) + 1;
    int h = (int) Math.abs(pA.getY() - pB.getY()) + 1;
    return new Rectangle(x, y, w, h);
  }

  @Override
  public void draw(Graphics pGraphics) {
    if (this.returnState != null) {
      this.returnState.draw(pGraphics);
    }

    if (hdlg == null || hdlg.graphicsContext == null) {
      return;
    }

    var currentMouse = hdlg.getCurrentMousePosition();
    if (corner1 == null || currentMouse == null) {
      return;
    }

    corner2 = currentMouse;
    hdlg.graphicsContext.drawRectangle(corner1, corner2, 1, Color.white, pGraphics, 1);
  }
}
