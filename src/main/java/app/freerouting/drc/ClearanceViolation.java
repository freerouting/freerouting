package app.freerouting.drc;

import app.freerouting.board.Item;
import app.freerouting.board.ObjectInfoPanel;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.util.TextManager;
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

  @Override
  public void printInfo(ObjectInfoPanel window, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    window.appendBold(tm.getText("clearance_violation_2"));
    window.append(" " + tm.getText("at") + " ");
    window.append(shape.centreOfGravity());
    window.append(", " + tm.getText("width") + " ");
    window.append(2 * this.shape.smallestRadius());
    window.append(", " + tm.getText("layer") + " ");
    window.append(firstItem.board.layerStructure.arr[this.layer].name);
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
