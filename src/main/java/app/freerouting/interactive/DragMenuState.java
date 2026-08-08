package app.freerouting.interactive;

import app.freerouting.geometry.planar.FloatPoint;

/** Class implementing the different functionality in the drag menu */
public class DragMenuState extends MenuState {

  /** Creates a new instance of DragMenuState */
  public DragMenuState(GuiBoardManager p_board_handling) {
    super(p_board_handling);
  }

  /** Returns a new instance of DragMenuState */
  public static DragMenuState getInstance(GuiBoardManager p_board_handling) {
    return new DragMenuState(p_board_handling);
  }

  @Override
  public InteractiveState mousePressed(FloatPoint p_point) {
    return DragState.getInstance(p_point, this, hdlg);
  }

  @Override
  public String getHelpId() {
    return "MenuState_DragMenuState";
  }
}
