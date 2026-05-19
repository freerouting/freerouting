package app.freerouting.interactive;

import app.freerouting.geometry.planar.FloatPoint;
import java.awt.Rectangle;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.geom.Point2D;

/**
 * Common base class for interactive selection of a rectangle.
 */
public class SelectRegionState extends InteractiveState {

  protected FloatPoint corner1;
  protected FloatPoint corner2;
  private Rectangle last_dirty_region;

  /**
   * Creates a new instance of SelectRegionState
   */
  protected SelectRegionState(InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    super(p_parent_state, p_board_handling);
  }

  @Override
  public InteractiveState button_released() {
    hdlg.screen_messages.set_status_message("");
    return complete();
  }

  @Override
  public InteractiveState mouse_dragged(FloatPoint p_point) {
    if (corner1 == null) {
      corner1 = p_point;
    }
    if (p_point != null) {
      Rectangle current_region = get_rectangle(corner1, p_point);
      Rectangle dirty_region = get_dirty_region(current_region);
      if (dirty_region != null && !dirty_region.isEmpty()) {
        hdlg.repaint(dirty_region);
      }
      last_dirty_region = current_region;
    }
    return this;
  }

  @Override
  public void draw(Graphics p_graphics) {
    this.return_state.draw(p_graphics);
    FloatPoint current_mouse_position = hdlg.get_current_mouse_position();
    if (corner1 == null || current_mouse_position == null) {
      return;
    }
    corner2 = current_mouse_position;
    hdlg.graphics_context.draw_rectangle(corner1, corner2, 1, Color.white, p_graphics, 1);
  }

  private Rectangle get_dirty_region(Rectangle p_current_region) {
    if (corner1 == null) {
      return null;
    }
    if (last_dirty_region == null) {
      return p_current_region;
    }
    Rectangle result = p_current_region.union(last_dirty_region);
    result.grow(4, 4);
    return result;
  }

  private Rectangle get_rectangle(FloatPoint p_corner1, FloatPoint p_corner2) {
    Point2D screen_corner1 = hdlg.graphics_context.coordinate_transform.board_to_screen(p_corner1);
    Point2D screen_corner2 = hdlg.graphics_context.coordinate_transform.board_to_screen(p_corner2);
    int x = (int) Math.floor(Math.min(screen_corner1.getX(), screen_corner2.getX()));
    int y = (int) Math.floor(Math.min(screen_corner1.getY(), screen_corner2.getY()));
    int width = (int) Math.ceil(Math.abs(screen_corner1.getX() - screen_corner2.getX()));
    int height = (int) Math.ceil(Math.abs(screen_corner1.getY() - screen_corner2.getY()));
    return new Rectangle(x, y, Math.max(width, 1), Math.max(height, 1));
  }
}