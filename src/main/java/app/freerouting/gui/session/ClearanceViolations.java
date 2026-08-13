package app.freerouting.gui.session;

import app.freerouting.board.Item;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.drc.ClearanceViolation;
import java.awt.Color;
import java.awt.Graphics;
import java.util.Collection;
import java.util.LinkedList;

/**
 * Displays clearance violations between board items on the screen.
 *
 * <p>This is a thin <em>presentation</em> façade (SoC plan Phase 5, D13): the violation compute
 * lives in {@link ClearanceViolation#aggregateSortedBySeverity(Collection)} / {@link
 * ClearanceViolation#smallestClearance(Collection)} (headless-safe), and this class only holds the
 * resulting list plus the {@link #draw(Graphics, GraphicsContext) draw} routine.
 */
public class ClearanceViolations {

  /** The list of clearance violations. */
  public final LinkedList<ClearanceViolation> list;

  /** The smallest clearance between items. */
  public double globalSmallestClearance = Double.MAX_VALUE;

  /** Creates a new instance from the supplied board items. */
  public ClearanceViolations(Collection<Item> itemList) {

    this.list = new LinkedList<>(ClearanceViolation.aggregateSortedBySeverity(itemList));
    this.globalSmallestClearance = ClearanceViolation.smallestClearance(itemList);
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
