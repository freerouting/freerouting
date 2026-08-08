package app.freerouting.interactive;

import app.freerouting.geometry.planar.FloatPoint;

/** Class implementing the different functionality in the drag menu */
public class DragMenuState extends MenuState {

  /** Creates a new instance of DragMenuState */
  public DragMenuState(GuiBoardManager pBoardHandling) {
    super(pBoardHandling);
  }

  /** Returns a new instance of DragMenuState */
  public static DragMenuState getInstance(GuiBoardManager pBoardHandling) {
    return new DragMenuState(pBoardHandling);
  }

  @Override
  public InteractiveState mousePressed(FloatPoint pPoint) {
    return DragState.getInstance(pPoint, this, hdlg);
  }

  @Override
  public String getHelpId() {
    return "MenuState_DragMenuState";
  }
}
