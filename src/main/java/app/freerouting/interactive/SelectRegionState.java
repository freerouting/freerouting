package app.freerouting.interactive;

import app.freerouting.geometry.planar.FloatPoint;

/** Common base class for interactive selection of a rectangle. */
public class SelectRegionState extends InteractiveState {

  protected FloatPoint corner1 = null;
  protected FloatPoint corner2 = null;

  /** Creates a new instance of SelectRegionState */
  protected SelectRegionState(
      InteractiveState p_parent_state,
      BoardHandling p_board_handling,
      ActivityReplayFile p_activityReplayFile) {
    super(p_parent_state, p_board_handling, p_activityReplayFile);
  }

  public InteractiveState button_released() {
    hdlg.screen_messages.set_status_message("");
    return complete();
  }

  public InteractiveState mouse_dragged(FloatPoint p_point) {
    if (corner1 == null) {
      corner1 = p_point;
      if (activityReplayFile != null) {
        activityReplayFile.add_corner(corner1);
      }
    }
    hdlg.repaint();
    return this;
  }

  public void draw(java.awt.Graphics p_graphics) {
    this.return_state.draw(p_graphics);
    FloatPoint current_mouse_position = hdlg.get_current_mouse_position();
    if (corner1 == null || current_mouse_position == null) {
      return;
    }
    corner2 = current_mouse_position;
    hdlg.graphics_context.draw_rectangle(corner1, corner2, 1, java.awt.Color.white, p_graphics, 1);
  }
}
