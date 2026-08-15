package app.freerouting.drc;

import app.freerouting.board.Item;
import app.freerouting.board.ObjectInfoPanel;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.util.TextManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Information of a clearance violation between 2 items. */
public class ClearanceViolation implements ObjectInfoPanel.Printable {

  /** The first item of the clearance violation. */
  public final Item firstItem;

  /** The second item of the clearance violation. */
  public final Item secondItem;

  /** The shape of the clearance violation. */
  public final ConvexShape shape;

  /** The layer of the clearance violation. */
  public final int layer;

  public final double expectedClearance;
  public final double actualClearance;

  /** Creates a new instance of ClearanceViolation. */
  public ClearanceViolation(
      Item firstItem,
      Item secondItem,
      ConvexShape shape,
      int layer,
      double expectedClearance,
      double actualClearance) {
    this.firstItem = firstItem;
    this.secondItem = secondItem;
    this.shape = shape;
    this.layer = layer;
    this.expectedClearance = expectedClearance;
    this.actualClearance = actualClearance;
  }

  /**
   * Aggregates the clearance violations of the given items into a single list, sorted by violation
   * severity (largest expected-minus-actual shortfall first). This is the clearance-violation
   * <em>compute</em> that the GUI façade {@code interactive.ClearanceViolations} presents; keeping
   * it here (headless-safe, no GUI/Swing/AWT types) lets the violations of a board be computed
   * without any GUI class (SoC plan Phase 5, D13).
   *
   * <p><strong>Note on double counting:</strong> a violation between items A and B is reported once
   * on each item, so the pair appears twice in this list. Callers that need a deduplicated,
   * whole-board count should use {@link DesignRulesChecker#getAllClearanceViolations()} instead.
   *
   * <p>As a side effect, each item's {@code smallestClearance} field is populated by {@link
   * Item#clearanceViolations()}; read it via {@link #smallestClearance(Collection)} after this
   * call.
   *
   * @param items the board items to aggregate
   * @return the aggregated, severity-sorted violations (never {@code null})
   */
  public static List<ClearanceViolation> aggregateSortedBySeverity(Collection<Item> items) {
    List<ClearanceViolation> violations = new ArrayList<>();
    for (Item item : items) {
      violations.addAll(item.clearanceViolations());
    }
    violations.sort(
        (o1, o2) ->
            -Double.compare(
                o1.expectedClearance - o1.actualClearance,
                o2.expectedClearance - o2.actualClearance));
    return violations;
  }

  /**
   * Returns the smallest clearance among the given items, or {@link Double#MAX_VALUE} if none has
   * been computed. This reads each item's {@code smallestClearance} field, which is populated by
   * {@link Item#clearanceViolations()}; call it after {@link
   * #aggregateSortedBySeverity(Collection)}.
   *
   * @param items the board items to inspect
   * @return the smallest clearance found, or {@link Double#MAX_VALUE} when no positive value exists
   */
  public static double smallestClearance(Collection<Item> items) {
    double smallest = Double.MAX_VALUE;
    for (Item item : items) {
      if ((item.smallestClearance > 0) && (item.smallestClearance < smallest)) {
        smallest = item.smallestClearance;
      }
    }
    return smallest;
  }

  @Override
  public void printInfo(ObjectInfoPanel window, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    window.appendBold(tm.getText("clearance_violation_2"));
    window.append(" " + tm.getText("at") + " ");
    window.append(shape.centreOfGravity());
    window.append(", " + tm.getText("width") + " ");
    window.append(2 * this.shape.smallestRadius());
    window.append(", " + tm.getText("layer") + " ");
    window.append(firstItem.board.layerStructure.layers[this.layer].name);
    window.append(", " + tm.getText("between"));
    window.newline();
    window.indent();
    firstItem.printInfo(window, locale);
    window.indent();
    secondItem.printInfo(window, locale);
    window.newline();
    window.indent();
    String clearanceViolationInfoExpectedClearance =
        tm.getText(
            "clearanceViolationInfoExpectedClearance",
            "%.4f".formatted(this.expectedClearance / 10000.0),
            "%.4f".formatted(this.actualClearance / 10000.0));
    window.append(clearanceViolationInfoExpectedClearance);
  }
}
