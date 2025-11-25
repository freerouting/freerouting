package app.freerouting.interactive;

import app.freerouting.board.Item;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.drc.ClearanceViolation;

import java.awt.*;
import java.util.Collection;
import java.util.LinkedList;

/**
 * To display the clearance violations between items on the screen.
 */
public class ClearanceViolations
{

  /**
   * The list of clearance violations.
   */
  public final LinkedList<ClearanceViolation> list;

  /**
   * The smallest clearance between items.
   */
  public double global_smallest_clearance = Double.MAX_VALUE;

  /**
   * Creates a new instance of ClearanceViolations
   */
  public ClearanceViolations(Collection<Item> p_item_list)
  {

    this.list = new LinkedList<>();
    for (Item curr_item : p_item_list)
    {
      this.list.addAll(curr_item.clearance_violations());
      if ((curr_item.smallest_clearance > 0) && (curr_item.smallest_clearance < global_smallest_clearance))
      {
        global_smallest_clearance = curr_item.smallest_clearance;
      }
    }

    this.list.sort((o1, o2) -> -Double.compare(o1.expected_clearance - o1.actual_clearance, o2.expected_clearance - o2.actual_clearance));
  }

  public void draw(Graphics p_graphics, GraphicsContext p_graphics_context)
  {
    Color draw_color = p_graphics_context.get_violations_color();
    for (ClearanceViolation curr_violation : list)
    {
      double intensity = p_graphics_context.get_layer_visibility(curr_violation.layer);
      p_graphics_context.fill_area(curr_violation.shape, p_graphics, draw_color, intensity);
      // draw a circle around the violation.
      double draw_radius = curr_violation.first_item.board.rules.get_min_trace_half_width() * 5;
      p_graphics_context.draw_circle(curr_violation.shape.centre_of_gravity(), draw_radius, 0.1 * draw_radius, draw_color, p_graphics, intensity);
    }
  }
}