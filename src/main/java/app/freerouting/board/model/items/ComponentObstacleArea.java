package app.freerouting.board.model.items;

import app.freerouting.board.actions.ItemInfoPrinter;
import app.freerouting.board.actions.ItemSelectionFilter;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.structure.Component;
import app.freerouting.board.model.structure.FixedState;
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
  public ComponentObstacleArea(
      Area area,
      int layer,
      Vector translation,
      double rotationInDegree,
      boolean sideChanged,
      int clearanceClassIndex,
      int id,
      int componentId,
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
        clearanceClassIndex,
        id,
        componentId,
        name,
        fixedState,
        board);
  }

  @Override
  public Item copy(int id) {
    return new ComponentObstacleArea(
        getRelativeArea(),
        getLayer(),
        getTranslation(),
        getRotationInDegree(),
        getSideChanged(),
        clearanceClassIndex(),
        id,
        getComponentId(),
        this.name,
        getFixedState(),
        board);
  }

  @Override
  public boolean isObstacle(Item other) {
    return other != this
        && other instanceof ComponentObstacleArea
        && other.getComponentId() != this.getComponentId();
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
    Component component = board.components.get(this.getComponentId());
    return component == null || component.placedOnFront();
  }

  @Override
  public void printInfo(ItemInfoPrinter printer, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    printer.appendBold(tm.getText("component_keepout"));
    this.printShapeInfo(printer, locale);
    this.printClearanceInfo(printer, locale);
    this.printClearanceViolationInfo(printer, locale);
    printer.newline();
  }
}
