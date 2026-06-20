package app.freerouting.interactive;

import app.freerouting.geometry.planar.FloatPoint;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.geom.Point2D;

public class SelectRegionState extends InteractiveState {

  protected FloatPoint corner1;
  protected FloatPoint corner2;

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
      hdlg.repaint();
      return this;
    }

    FloatPoint previous_corner2 = corner2;
    corner2 = p_point;
    hdlg.repaint(rubber_band_dirty_rect(previous_corner2, corner2));
    return this;
  }

  private Rectangle rubber_band_dirty_rect(FloatPoint p_old_corner2, FloatPoint p_new_corner2) {
    var transform = hdlg.graphics_context.coordinate_transform;
    Point2D sc_corner1 = transform.board_to_screen(corner1);
    Rectangle dirty_rect = screen_rect(sc_corner1, transform.board_to_screen(p_new_corner2));
    if (p_old_corner2 != null) {
      dirty_rect = dirty_rect.union(screen_rect(sc_corner1, transform.board_to_screen(p_old_corner2)));
    }
    dirty_rect.grow(3, 3); // margin for the rectangle's stroke
    return dirty_rect;
  }

  private static Rectangle screen_rect(Point2D p_a, Point2D p_b) {
    int x = (int) Math.min(p_a.getX(), p_b.getX());
    int y = (int) Math.min(p_a.getY(), p_b.getY());
    int w = (int) Math.abs(p_a.getX() - p_b.getX()) + 1;
    int h = (int) Math.abs(p_a.getY() - p_b.getY()) + 1;
    return new Rectangle(x, y, w, h);
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
}