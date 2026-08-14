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
      int[] netNoArr,
      int clearanceType,
      int idNo,
      int groupNo,
      String name,
      FixedState fixedState,
      BasicBoard board) {
    super(
        area,
        layer,
        translation,
        rotationInDegree,
        sideChanged,
        netNoArr,
        clearanceType,
        idNo,
        groupNo,
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
      int clearanceType,
      int idNo,
      int groupNo,
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
        clearanceType,
        idNo,
        groupNo,
        name,
        fixedState,
        board);
  }

  @Override
  public Item copy(int idNo) {
    int[] copiedNetNos = new int[netNoArr.length];
    System.arraycopy(netNoArr, 0, copiedNetNos, 0, netNoArr.length);
    return new ViaObstacleArea(
        getRelativeArea(),
        getLayer(),
        getTranslation(),
        getRotationInDegree(),
        getSideChanged(),
        copiedNetNos,
        clearanceClassNo(),
        idNo,
        getComponentNo(),
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
  public boolean isTraceObstacle(int netNo) {
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
  public void printInfo(ObjectInfoPanel window, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    window.appendBold(tm.getText("via_keepout"));
    this.printShapeInfo(window, locale);
    this.printClearanceInfo(window, locale);
    this.printClearanceViolationInfo(window, locale);
    window.newline();
  }
}
