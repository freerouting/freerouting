package app.freerouting.gui.interactive;

import app.freerouting.geometry.planar.FloatPoint;

/** Implements the functionality of the drag menu. */
public class DragMenuState extends MenuState {

  /** Creates a new instance of DragMenuState. */
  public DragMenuState(GuiBoardManager boardHandling) {
    super(boardHandling);
  }

  /** Returns a new instance of DragMenuState. */
  public static DragMenuState getInstance(GuiBoardManager boardHandling) {
    return new DragMenuState(boardHandling);
  }

  @Override
  public InteractiveState mousePressed(FloatPoint point) {
    return DragState.getInstance(point, this, hdlg);
  }

  @Override
  public String getHelpId() {
    return "MenuState_DragMenuState";
  }
}
