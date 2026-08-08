package app.freerouting.interactive;

import app.freerouting.geometry.planar.FloatPoint;
import java.awt.geom.Point2D;

/** Class for interactive zooming to a rectangle. */
public class ZoomRegionState extends SelectRegionState {

  /** Creates a new instance of ZoomRegionState */
  public ZoomRegionState(InteractiveState pParentState, GuiBoardManager pBoardHandling) {
    super(pParentState, pBoardHandling);
  }

  /** Returns a new instance of this class. */
  public static ZoomRegionState getInstance(
      InteractiveState pParentState, GuiBoardManager pBoardHandling) {
    return getInstance(null, pParentState, pBoardHandling);
  }

  /** Returns a new instance of this class with first point p_location. */
  public static ZoomRegionState getInstance(
      FloatPoint pLocation, InteractiveState pParentState, GuiBoardManager pBoardHandling) {
    ZoomRegionState newInstance = new ZoomRegionState(pParentState, pBoardHandling);
    newInstance.corner1 = pLocation;
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
