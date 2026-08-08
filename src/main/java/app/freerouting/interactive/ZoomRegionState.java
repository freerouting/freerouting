package app.freerouting.interactive;

import app.freerouting.geometry.planar.FloatPoint;
import java.awt.geom.Point2D;

/** Class for interactive zooming to a rectangle. */
public class ZoomRegionState extends SelectRegionState {

  /** Creates a new instance of ZoomRegionState */
  public ZoomRegionState(InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    super(p_parent_state, p_board_handling);
  }

  /** Returns a new instance of this class. */
  public static ZoomRegionState getInstance(
      InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    return getInstance(null, p_parent_state, p_board_handling);
  }

  /** Returns a new instance of this class with first point p_location. */
  public static ZoomRegionState getInstance(
      FloatPoint p_location, InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    ZoomRegionState newInstance = new ZoomRegionState(p_parent_state, p_board_handling);
    newInstance.corner1 = p_location;
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
