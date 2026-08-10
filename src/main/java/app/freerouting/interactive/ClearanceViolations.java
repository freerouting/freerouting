package app.freerouting.interactive;

import app.freerouting.board.Item;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.drc.ClearanceViolation;
import java.awt.Color;
import java.awt.Graphics;
import java.util.Collection;
import java.util.LinkedList;

/** Displays clearance violations between board items on the screen. */
public class ClearanceViolations {

  /** The list of clearance violations. */
  public final LinkedList<ClearanceViolation> list;

  /** The smallest clearance between items. */
  public double globalSmallestClearance = Double.MAX_VALUE;

  /** Creates a new instance from the supplied board items. */
  public ClearanceViolations(Collection<Item> itemList) {

    this.list = new LinkedList<>();
    for (Item currItem : itemList) {
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

  /** Draws each clearance violation using the supplied graphics context. */
  public void draw(Graphics graphics, GraphicsContext graphicsContext) {
    Color drawColor = graphicsContext.getViolationsColor();
    for (ClearanceViolation currViolation : list) {
      double intensity = graphicsContext.getLayerVisibility(currViolation.layer);
      graphicsContext.fillArea(currViolation.shape, graphics, drawColor, intensity);
      // draw a circle around the violation.
      double drawRadius = currViolation.firstItem.board.rules.getMinTraceHalfWidth() * 5;
      graphicsContext.drawCircle(
          currViolation.shape.centreOfGravity(),
          drawRadius,
          0.1 * drawRadius,
          drawColor,
          graphics,
          intensity);
    }
  }
}
