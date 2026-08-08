package app.freerouting.board;

import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.util.TextManager;
import java.awt.Color;
import java.util.Locale;

/** Describes Areas on the board, where vias are not allowed. */
public class ViaObstacleArea extends ObstacleArea {

  /** Creates a new area item which may belong to several nets */
  ViaObstacleArea(
      Area pArea,
      int pLayer,
      Vector pTranslation,
      double pRotationInDegree,
      boolean pSideChanged,
      int[] pNetNoArr,
      int pClearanceType,
      int pIdNo,
      int pGroupNo,
      String pName,
      FixedState pFixedState,
      BasicBoard pBoard) {
    super(
        pArea,
        pLayer,
        pTranslation,
        pRotationInDegree,
        pSideChanged,
        pNetNoArr,
        pClearanceType,
        pIdNo,
        pGroupNo,
        pName,
        pFixedState,
        pBoard);
  }

  /** Creates a new area item without net */
  ViaObstacleArea(
      Area pArea,
      int pLayer,
      Vector pTranslation,
      double pRotationInDegree,
      boolean pSideChanged,
      int pClearanceType,
      int pIdNo,
      int pGroupNo,
      String pName,
      FixedState pFixedState,
      BasicBoard pBoard) {
    this(
        pArea,
        pLayer,
        pTranslation,
        pRotationInDegree,
        pSideChanged,
        new int[0],
        pClearanceType,
        pIdNo,
        pGroupNo,
        pName,
        pFixedState,
        pBoard);
  }

  @Override
  public Item copy(int pIdNo) {
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
        pIdNo,
        getComponentNo(),
        this.name,
        getFixedState(),
        board);
  }

  @Override
  public boolean isObstacle(Item pOther) {
    if (pOther.sharesNet(this)) {
      return false;
    }
    return pOther instanceof Via;
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
    return pFilter.isSelected(ItemSelectionFilter.SelectableChoices.VIA_KEEPOUT);
  }

  @Override
  public void printInfo(ObjectInfoPanel pWindow, Locale pLocale) {
    TextManager tm = new TextManager(this.getClass(), pLocale);

    pWindow.appendBold(tm.getText("via_keepout"));
    this.printShapeInfo(pWindow, pLocale);
    this.printClearanceInfo(pWindow, pLocale);
    this.printClearanceViolationInfo(pWindow, pLocale);
    pWindow.newline();
  }

  @Override
  public Color[] getDrawColors(GraphicsContext pGraphicsContext) {
    return pGraphicsContext.getViaObstacleColors();
  }

  @Override
  public double getDrawIntensity(GraphicsContext pGraphicsContext) {
    return pGraphicsContext.getViaObstacleColorIntensity();
  }
}
