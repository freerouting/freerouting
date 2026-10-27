package app.freerouting.board.model.items;

import app.freerouting.board.actions.ItemInfoPrinter;
import app.freerouting.board.actions.ItemSelectionFilter;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.util.TextManager;
import java.util.Locale;

/** Describes areas on the board where wires (traces) are not allowed, but vias are permitted. */
public class WireObstacleArea extends ObstacleArea {

  /** Creates a new area item which may belong to several nets. */
  public WireObstacleArea(
      Area area,
      int layer,
      Vector translation,
      double rotationInDegree,
      boolean sideChanged,
      int[] netNumbers,
      int clearanceClassIndex,
      int id,
      int groupId,
      String name,
      FixedState fixedState,
      BasicBoard board) {
    super(
        area,
        layer,
        translation,
        rotationInDegree,
        sideChanged,
        netNumbers,
        clearanceClassIndex,
        id,
        groupId,
        name,
        fixedState,
        board);
  }

  /** Creates a new area item without net. */
  public WireObstacleArea(
      Area area,
      int layer,
      Vector translation,
      double rotationInDegree,
      boolean sideChanged,
      int clearanceClassIndex,
      int id,
      int groupId,
      String name,
      FixedState fixedState,
      BasicBoard board) {
    this(
        area,
        layer,
        translation,
        rotationInDegree,
        sideChanged,
        new int[0],
        clearanceClassIndex,
        id,
        groupId,
        name,
        fixedState,
        board);
  }

  @Override
  public Item copy(int id) {
    int[] copiedNetNos = new int[netNumbers.length];
    System.arraycopy(netNumbers, 0, copiedNetNos, 0, netNumbers.length);
    return new WireObstacleArea(
        getRelativeArea(),
        getLayer(),
        getTranslation(),
        getRotationInDegree(),
        getSideChanged(),
        copiedNetNos,
        clearanceClassIndex(),
        id,
        getComponentId(),
        this.name,
        getFixedState(),
        board);
  }

  @Override
  public boolean isObstacle(Item other) {
    if (other == null || other == this || other.sharesNet(this)) {
      return false;
    }
    return other instanceof Trace;
  }

  @Override
  public boolean isTraceObstacle(int netNumber) {
    return !containsNet(netNumber);
  }

  /** Vias are allowed. DrillPage carves holes only for items that are not drillable. */
  @Override
  public boolean isDrillable(int netNumber) {
    return true;
  }

  @Override
  public boolean isSelectedByFilter(ItemSelectionFilter filter) {
    if (!this.isSelectedByFixedFilter(filter)) {
      return false;
    }
    return filter.isSelected(ItemSelectionFilter.SelectableChoices.KEEPOUT);
  }

  @Override
  public void printInfo(ItemInfoPrinter printer, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    printer.appendBold(tm.getText("wire_keepout"));
    this.printShapeInfo(printer, locale);
    this.printClearanceInfo(printer, locale);
    this.printClearanceViolationInfo(printer, locale);
    printer.newline();
  }
}
