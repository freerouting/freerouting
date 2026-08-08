package app.freerouting.interactive;

import app.freerouting.board.Item;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.drc.ClearanceViolation;
import java.awt.Color;
import java.awt.Graphics;
import java.util.Collection;
import java.util.LinkedList;

/** To display the clearance violations between items on the screen. */
public class ClearanceViolations {

  /** The list of clearance violations. */
  public final LinkedList<ClearanceViolation> list;

  /** The smallest clearance between items. */
  public double globalSmallestClearance = Double.MAX_VALUE;

  /** Creates a new instance of ClearanceViolations */
  public ClearanceViolations(Collection<Item> p_item_list) {

    this.list = new LinkedList<>();
    for (Item currItem : p_item_list) {
      this.list.addAll(currItem.clearanceViolations());
      if ((currItem.smallestClearance > 0)
          && (currItem.smallestClearance < globalSmallestClearance)) {
        globalSmallestClearance = currItem.smallestClearance;
      }
    }

    this.list.sort(
        (o1, o2) ->
            -Double.compare(
                o1.expectedClearance - o1.actualClearance,
                o2.expectedClearance - o2.actualClearance));
  }

  public void draw(Graphics p_graphics, GraphicsContext p_graphics_context) {
    Color drawColor = p_graphics_context.getViolationsColor();
    for (ClearanceViolation currViolation : list) {
      double intensity = p_graphics_context.getLayerVisibility(currViolation.layer);
      p_graphics_context.fillArea(currViolation.shape, p_graphics, drawColor, intensity);
      // draw a circle around the violation.
      double drawRadius = currViolation.firstItem.board.rules.getMinTraceHalfWidth() * 5;
      p_graphics_context.drawCircle(
          currViolation.shape.centreOfGravity(),
          drawRadius,
          0.1 * drawRadius,
          drawColor,
          p_graphics,
          intensity);
    }
  }
}
