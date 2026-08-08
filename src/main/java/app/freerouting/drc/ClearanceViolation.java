package app.freerouting.drc;

import app.freerouting.board.Item;
import app.freerouting.board.ObjectInfoPanel;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.util.TextManager;
import java.util.Locale;

/** Information of a clearance violation between 2 items. */
public class ClearanceViolation implements ObjectInfoPanel.Printable {

  /** The first item of the clearance violation */
  public final Item firstItem;

  /** The second item of the clearance violation */
  public final Item secondItem;

  /** The shape of the clearance violation */
  public final ConvexShape shape;

  /** The layer of the clearance violation */
  public final int layer;

  public final double expectedClearance;
  public final double actualClearance;

  /** Creates a new instance of ClearanceViolation */
  public ClearanceViolation(
      Item p_first_item,
      Item p_second_item,
      ConvexShape p_shape,
      int p_layer,
      double p_expected_clearance,
      double p_actual_clearance) {
    firstItem = p_first_item;
    secondItem = p_second_item;
    shape = p_shape;
    layer = p_layer;
    expectedClearance = p_expected_clearance;
    actualClearance = p_actual_clearance;
  }

  @Override
  public void printInfo(ObjectInfoPanel p_window, Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    p_window.appendBold(tm.getText("clearance_violation_2"));
    p_window.append(" " + tm.getText("at") + " ");
    p_window.append(shape.centreOfGravity());
    p_window.append(", " + tm.getText("width") + " ");
    p_window.append(2 * this.shape.smallestRadius());
    p_window.append(", " + tm.getText("layer") + " ");
    p_window.append(firstItem.board.layerStructure.arr[this.layer].name);
    p_window.append(", " + tm.getText("between"));
    p_window.newline();
    p_window.indent();
    firstItem.printInfo(p_window, p_locale);
    p_window.indent();
    secondItem.printInfo(p_window, p_locale);
    p_window.newline();
    p_window.indent();
    String clearanceViolationInfoExpectedClearance =
        tm.getText(
            "clearanceViolationInfoExpectedClearance",
            "%.4f".formatted(this.expectedClearance / 10000.0),
            "%.4f".formatted(this.actualClearance / 10000.0));
    p_window.append(clearanceViolationInfoExpectedClearance);
  }
}
