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
    if (hdlg != null && hdlg.screen_messages != null) {
      hdlg.screen_messages.set_status_message("");
    }
    return complete();
  }

  @Override
  public InteractiveState mouse_dragged(FloatPoint p_point) {
    // Early exit on null or redundant micro-movements
    if (p_point == null || (corner2 != null && p_point.equals(corner2))) return this;

    if (corner1 == null) {
      corner1 = p_point;
      if (hdlg != null) hdlg.repaint();
      return this;
    }

    var previous_corner2 = corner2;
    corner2 = p_point;
    
    if (hdlg != null) {
      var dirtyRect = rubber_band_dirty_rect(previous_corner2, corner2);
      // Fall back to full repaint if dirty rect calculation fails
      if (dirtyRect != null) hdlg.repaint(dirtyRect);
      else hdlg.repaint();
    }
    return this;
  }

  private Rectangle rubber_band_dirty_rect(FloatPoint p_old_corner2, FloatPoint p_new_corner2) {
    if (hdlg == null || hdlg.graphics_context == null || hdlg.graphics_context.coordinate_transform == null) return null;
    
    var transform = hdlg.graphics_context.coordinate_transform;
    var sc_corner1 = transform.board_to_screen(corner1);
    var sc_new_corner2 = transform.board_to_screen(p_new_corner2);
    
    // Fail gracefully if transforms fail
    if (sc_corner1 == null || sc_new_corner2 == null) return null;
    
    var dirty_rect = screen_rect(sc_corner1, sc_new_corner2);
    
    if (p_old_corner2 != null) {
      var sc_old_corner2 = transform.board_to_screen(p_old_corner2);
      if (sc_old_corner2 != null) {
        // Mutate in-place to avoid GC allocation during rapid drags
        dirty_rect.add(screen_rect(sc_corner1, sc_old_corner2));
      }
    }
    
    dirty_rect.grow(3, 3); // stroke margin
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
    if (this.return_state != null) this.return_state.draw(p_graphics);
    
    if (hdlg == null || hdlg.graphics_context == null) return;
    
    var current_mouse = hdlg.get_current_mouse_position();
    if (corner1 == null || current_mouse == null) return;
    
    corner2 = current_mouse;
    hdlg.graphics_context.draw_rectangle(corner1, corner2, 1, Color.white, p_graphics, 1);
  }
}