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
      Item pFirstItem,
      Item pSecondItem,
      ConvexShape pShape,
      int pLayer,
      double pExpectedClearance,
      double pActualClearance) {
    firstItem = pFirstItem;
    secondItem = pSecondItem;
    shape = pShape;
    layer = pLayer;
    expectedClearance = pExpectedClearance;
    actualClearance = pActualClearance;
  }

  @Override
  public void printInfo(ObjectInfoPanel pWindow, Locale pLocale) {
    TextManager tm = new TextManager(this.getClass(), pLocale);

    pWindow.appendBold(tm.getText("clearance_violation_2"));
    pWindow.append(" " + tm.getText("at") + " ");
    pWindow.append(shape.centreOfGravity());
    pWindow.append(", " + tm.getText("width") + " ");
    pWindow.append(2 * this.shape.smallestRadius());
    pWindow.append(", " + tm.getText("layer") + " ");
    pWindow.append(firstItem.board.layerStructure.arr[this.layer].name);
    pWindow.append(", " + tm.getText("between"));
    pWindow.newline();
    pWindow.indent();
    firstItem.printInfo(pWindow, pLocale);
    pWindow.indent();
    secondItem.printInfo(pWindow, pLocale);
    pWindow.newline();
    pWindow.indent();
    String clearanceViolationInfoExpectedClearance =
        tm.getText(
            "clearanceViolationInfoExpectedClearance",
            "%.4f".formatted(this.expectedClearance / 10000.0),
            "%.4f".formatted(this.actualClearance / 10000.0));
    pWindow.append(clearanceViolationInfoExpectedClearance);
  }
}
