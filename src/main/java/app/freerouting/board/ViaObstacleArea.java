package app.freerouting.board;

import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.util.TextManager;
import java.util.Locale;

/** Describes Areas on the board, where vias are not allowed. */
public class ViaObstacleArea extends ObstacleArea {

  /** Creates a new area item which may belong to several nets. */
  ViaObstacleArea(
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
  ViaObstacleArea(
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
    return new ViaObstacleArea(
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
    if (other.sharesNet(this)) {
      return false;
    }
    return other instanceof Via;
  }

  @Override
  public boolean isTraceObstacle(int netNumber) {
    return false;
  }

  @Override
  public boolean isSelectedByFilter(ItemSelectionFilter filter) {
    if (!this.isSelectedByFixedFilter(filter)) {
      return false;
    }
    return filter.isSelected(ItemSelectionFilter.SelectableChoices.VIA_KEEPOUT);
  }

  @Override
  public void printInfo(ItemInfoPrinter printer, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    printer.appendBold(tm.getText("via_keepout"));
    this.printShapeInfo(printer, locale);
    this.printClearanceInfo(printer, locale);
    this.printClearanceViolationInfo(printer, locale);
    printer.newline();
  }
}
