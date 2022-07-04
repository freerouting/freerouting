package app.freerouting.interactive;

import app.freerouting.board.ClearanceViolation;
import app.freerouting.board.Item;
import app.freerouting.boardgraphics.GraphicsContext;
import java.awt.Graphics;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/** To display the clearance violations between items on the screen. */
public class ClearanceViolations {

  /** The list of clearance violations. */
  public final Collection<ClearanceViolation> list;

  /** Creates a new instance of ClearanceViolations */
  public ClearanceViolations(Collection<Item> p_item_list) {
    this.list = new LinkedList<ClearanceViolation>();
    Iterator<Item> it = p_item_list.iterator();
    while (it.hasNext()) {
      Item curr_item = it.next();
      this.list.addAll(curr_item.clearance_violations());
    }
  }

  public void draw(Graphics p_graphics, GraphicsContext p_graphics_context) {
    java.awt.Color draw_color = p_graphics_context.get_violations_color();
    Iterator<ClearanceViolation> it = list.iterator();
    while (it.hasNext()) {
      ClearanceViolation curr_violation = it.next();
      double intensity = p_graphics_context.get_layer_visibility(curr_violation.layer);
      p_graphics_context.fill_area(curr_violation.shape, p_graphics, draw_color, intensity);
      // draw a circle around the violation.
      double draw_radius = curr_violation.first_item.board.rules.get_min_trace_half_width() * 5;
      p_graphics_context.draw_circle(
          curr_violation.shape.centre_of_gravity(),
          draw_radius,
          0.1 * draw_radius,
          draw_color,
          p_graphics,
          intensity);
    }
  }
}
