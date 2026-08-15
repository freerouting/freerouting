package app.freerouting.board;

import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.util.TextManager;
import java.util.Locale;

/** Describes areas of the board, where components are not allowed. */
public class ComponentObstacleArea extends ObstacleArea {

  /**
   * Creates a new instance of ComponentObstacleArea If isObstacle is false, the new instance is not
   * regarded as obstacle and used only for displaying on the screen.
   */
  ComponentObstacleArea(
      Area area,
      int layer,
      Vector translation,
      double rotationInDegree,
      boolean sideChanged,
      int clearanceType,
      int idNo,
      int componentNo,
      String name,
      FixedState fixedState,
      BasicBoard board) {
    super(
        area,
        layer,
        translation,
        rotationInDegree,
        sideChanged,
        new int[0],
        clearanceType,
        idNo,
        componentNo,
        name,
        fixedState,
        board);
  }

  @Override
  public Item copy(int idNo) {
    return new ComponentObstacleArea(
        getRelativeArea(),
        getLayer(),
        getTranslation(),
        getRotationInDegree(),
        getSideChanged(),
        clearanceClassIndex(),
        idNo,
        getComponentNo(),
        this.name,
        getFixedState(),
        board);
  }

  @Override
  public boolean isObstacle(Item other) {
    return other != this
        && other instanceof ComponentObstacleArea
        && other.getComponentNo() != this.getComponentNo();
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
    return filter.isSelected(ItemSelectionFilter.SelectableChoices.COMPONENT_KEEPOUT);
  }

  /** IsFront. */
  public boolean isFront() {
    Component component = board.components.get(this.getComponentNo());
    return component == null || component.placedOnFront();
  }

  @Override
  public void printInfo(ObjectInfoPanel window, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    window.appendBold(tm.getText("component_keepout"));
    this.printShapeInfo(window, locale);
    this.printClearanceInfo(window, locale);
    this.printClearanceViolationInfo(window, locale);
    window.newline();
  }
}
