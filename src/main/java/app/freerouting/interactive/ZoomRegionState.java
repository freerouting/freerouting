package app.freerouting.interactive;

import app.freerouting.geometry.planar.FloatPoint;
import java.awt.geom.Point2D;

/** Class for interactive zooming to a rectangle. */
public class ZoomRegionState extends SelectRegionState {

  /** Creates a new instance of ZoomRegionState. */
  public ZoomRegionState(InteractiveState parentState, GuiBoardManager boardHandling) {
    super(parentState, boardHandling);
  }

  /** Returns a new instance of this class. */
  public static ZoomRegionState getInstance(
      InteractiveState parentState, GuiBoardManager boardHandling) {
    return getInstance(null, parentState, boardHandling);
  }

  /** Returns a new instance of this class with the first point at the given location. */
  public static ZoomRegionState getInstance(
      FloatPoint location, InteractiveState parentState, GuiBoardManager boardHandling) {
    ZoomRegionState newInstance = new ZoomRegionState(parentState, boardHandling);
    newInstance.corner1 = location;
    newInstance.hdlg.screenMessages.setStatusMessage(
        newInstance.tm.getText("drag_left_mouse_button_to_create_region_to_display"));
    return newInstance;
  }

  @Override
  public InteractiveState complete() {
    corner2 = hdlg.getCurrentMousePosition();
    zoomRegion();
    corner2 = hdlg.getCurrentMousePosition();
    zoomRegion();
    return this.returnState;
  }

  private void zoomRegion() {
    if (corner1 == null || corner2 == null) {
      return;
    }
    Point2D scCorner1 = hdlg.graphicsContext.coordinateTransform.boardToScreen(corner1);
    Point2D scCorner2 = hdlg.graphicsContext.coordinateTransform.boardToScreen(corner2);
    hdlg.getPanel().zoomFrame(scCorner1, scCorner2);
  }
}
