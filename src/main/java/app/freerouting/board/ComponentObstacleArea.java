package app.freerouting.board;

import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.util.TextManager;
import java.awt.Color;
import java.awt.Graphics;
import java.util.Locale;

/** Describes areas of the board, where components are not allowed. */
public class ComponentObstacleArea extends ObstacleArea {

  /**
   * Creates a new instance of ComponentObstacleArea If p_is_obstacle is false, the new instance is
   * not regarded as obstacle and used only for displaying on the screen.
   */
  ComponentObstacleArea(
      Area pArea,
      int pLayer,
      Vector pTranslation,
      double pRotationInDegree,
      boolean pSideChanged,
      int pClearanceType,
      int pIdNo,
      int pComponentNo,
      String pName,
      FixedState pFixedState,
      BasicBoard pBoard) {
    super(
        pArea,
        pLayer,
        pTranslation,
        pRotationInDegree,
        pSideChanged,
        new int[0],
        pClearanceType,
        pIdNo,
        pComponentNo,
        pName,
        pFixedState,
        pBoard);
  }

  @Override
  public Item copy(int pIdNo) {
    return new ComponentObstacleArea(
        getRelativeArea(),
        getLayer(),
        getTranslation(),
        getRotationInDegree(),
        getSideChanged(),
        clearanceClassNo(),
        pIdNo,
        getComponentNo(),
        this.name,
        getFixedState(),
        board);
  }

  @Override
  public boolean isObstacle(Item pOther) {
    return pOther != this
        && pOther instanceof ComponentObstacleArea
        && pOther.getComponentNo() != this.getComponentNo();
  }

  @Override
  public boolean isTraceObstacle(int pNetNo) {
    return false;
  }

  @Override
  public boolean isSelectedByFilter(ItemSelectionFilter pFilter) {
    if (!this.isSelectedByFixedFilter(pFilter)) {
      return false;
    }
    return pFilter.isSelected(ItemSelectionFilter.SelectableChoices.COMPONENT_KEEPOUT);
  }

  public boolean isFront() {
    Component component = board.components.get(this.getComponentNo());
    return component == null || component.placedOnFront();
  }

  @Override
  public Color[] getDrawColors(GraphicsContext pGraphicsContext) {
    Color[] colorArr = new Color[this.board.layerStructure.arr.length];
    Color frontDrawColor = pGraphicsContext.otherColorTable.getCourtyardColor(true);
    for (int i = 0; i < colorArr.length - 1; i++) {
      colorArr[i] = frontDrawColor;
    }
    if (colorArr.length > 1) {
      colorArr[colorArr.length - 1] = pGraphicsContext.otherColorTable.getCourtyardColor(false);
    }
    return colorArr;
  }

  @Override
  public double getDrawIntensity(GraphicsContext pGraphicsContext) {
    return pGraphicsContext.getComponentOutlineColorIntensity();
  }

  @Override
  public void draw(
      Graphics pG, GraphicsContext pGraphicsContext, Color[] pColorArr, double pIntensity) {
    if (pGraphicsContext == null || pIntensity <= 0) {
      return;
    }
    int virtualLayerIdx = this.isFront() ? 2 : 3;
    double virtualVisibility = pGraphicsContext.getVirtualLayerVisibility(virtualLayerIdx);
    if (virtualVisibility <= 0) {
      return;
    }

    Color color = pColorArr[this.getLayer()];
    double intensity = virtualVisibility * pIntensity;

    double drawWidth = Math.min(this.board.communication.getResolution(Unit.MIL), 100);
    pGraphicsContext.drawBoundary(this.getArea(), drawWidth, color, pG, intensity);
  }

  @Override
  public void printInfo(ObjectInfoPanel pWindow, Locale pLocale) {
    TextManager tm = new TextManager(this.getClass(), pLocale);

    pWindow.appendBold(tm.getText("component_keepout"));
    this.printShapeInfo(pWindow, pLocale);
    this.printClearanceInfo(pWindow, pLocale);
    this.printClearanceViolationInfo(pWindow, pLocale);
    pWindow.newline();
  }
}
