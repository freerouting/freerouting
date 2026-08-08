package app.freerouting.board;

import app.freerouting.boardgraphics.Drawable;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import java.awt.Color;
import java.awt.Graphics;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Locale;

public class ComponentOutline extends Item implements Serializable {

  private final Area relativeArea;
  private transient Area precalculatedAbsoluteArea;
  private Vector translation;
  private double rotationInDegree;
  private boolean isFront;
  private final boolean isCourtyard;
  private final boolean isFabrication;
  private final boolean isClosed;

  /** Creates a new instance of ComponentOutline */
  public ComponentOutline(
      Area pArea,
      boolean pIsFront,
      Vector pTranslation,
      double pRotationInDegree,
      int pIdNo,
      int pComponentNo,
      boolean pIsCourtyard,
      boolean pIsFabrication,
      boolean pIsClosed,
      FixedState pFixedState,
      BasicBoard pBoard) {
    super(new int[0], 0, pIdNo, pComponentNo, pFixedState, pBoard);
    this.relativeArea = pArea;
    this.isFront = pIsFront;
    this.translation = pTranslation;
    this.rotationInDegree = pRotationInDegree;
    this.isCourtyard = pIsCourtyard;
    this.isFabrication = pIsFabrication;
    this.isClosed = pIsClosed;
  }

  @Override
  public Item copy(int pIdNo) {
    return new ComponentOutline(
        this.relativeArea,
        this.isFront,
        this.translation,
        this.rotationInDegree,
        pIdNo,
        this.getComponentNo(),
        this.isCourtyard,
        this.isFabrication,
        this.isClosed,
        this.getFixedState(),
        this.board);
  }

  public boolean isFront() {
    return this.isFront;
  }

  public boolean isCourtyard() {
    return this.isCourtyard;
  }

  public boolean isFabrication() {
    return this.isFabrication;
  }

  public boolean isClosed() {
    return this.isClosed;
  }

  @Override
  public boolean isSelectedByFilter(ItemSelectionFilter pFilter) {
    return false;
  }

  public int getLayer() {
    int result;
    if (this.isFront) {
      result = 0;
    } else {
      result = this.board.getLayerCount() - 1;
    }
    return result;
  }

  @Override
  public int firstLayer() {
    return getLayer();
  }

  @Override
  public int lastLayer() {
    return getLayer();
  }

  @Override
  public boolean isOnLayer(int pLayer) {
    return getLayer() == pLayer;
  }

  @Override
  public boolean isObstacle(Item pItem) {
    return false;
  }

  @Override
  public int shapeLayer(int pIndex) {
    return getLayer();
  }

  @Override
  public int tileShapeCount() {
    return 0;
  }

  @Override
  protected TileShape[] calculateTreeShapes(ShapeSearchTree pSearchTree) {
    return new TileShape[0];
  }

  @Override
  public double getDrawIntensity(GraphicsContext pGraphicsContext) {
    return pGraphicsContext.getComponentOutlineColorIntensity();
  }

  @Override
  public Color[] getDrawColors(GraphicsContext pGraphicsContext) {
    Color[] colorArr = new Color[this.board.layerStructure.arr.length];
    Color frontDrawColor;
    Color backDrawColor;
    if (this.isCourtyard) {
      frontDrawColor = pGraphicsContext.otherColorTable.getCourtyardColor(true);
      backDrawColor = pGraphicsContext.otherColorTable.getCourtyardColor(false);
    } else if (this.isFabrication) {
      frontDrawColor = pGraphicsContext.otherColorTable.getFabColor(true);
      backDrawColor = pGraphicsContext.otherColorTable.getFabColor(false);
    } else {
      frontDrawColor = pGraphicsContext.otherColorTable.getSilkscreenColor(true);
      backDrawColor = pGraphicsContext.otherColorTable.getSilkscreenColor(false);
    }
    for (int i = 0; i < colorArr.length - 1; i++) {
      colorArr[i] = frontDrawColor;
    }
    if (colorArr.length > 1) {
      colorArr[colorArr.length - 1] = backDrawColor;
    }
    return colorArr;
  }

  @Override
  public int getDrawPriority() {
    return Drawable.MIDDLE_DRAW_PRIORITY;
  }

  @Override
  public void draw(
      Graphics pG, GraphicsContext pGraphicsContext, Color[] pColorArr, double pIntensity) {
    if (pGraphicsContext == null || pIntensity <= 0) {
      return;
    }
    int virtualLayerIdx;
    if (this.isCourtyard) {
      virtualLayerIdx = this.isFront ? 2 : 3;
    } else if (this.isFabrication) {
      virtualLayerIdx = this.isFront ? 4 : 5;
    } else {
      virtualLayerIdx = this.isFront ? 0 : 1;
    }
    double virtualVisibility = pGraphicsContext.getVirtualLayerVisibility(virtualLayerIdx);
    if (virtualVisibility <= 0) {
      return;
    }

    Color color = pColorArr[this.getLayer()];
    double intensity = virtualVisibility * pIntensity;

    if (this.isCourtyard || this.isClosed) {
      double drawWidth = Math.min(this.board.communication.getResolution(Unit.MIL), 100);
      pGraphicsContext.drawBoundary(this.getArea(), drawWidth, color, pG, intensity);
    } else {
      pGraphicsContext.fillArea(this.getArea(), pG, color, intensity);
    }
  }

  @Override
  public IntBox boundingBox() {
    return getArea().boundingBox();
  }

  @Override
  public void translateBy(Vector pVector) {
    this.translation = this.translation.add(pVector);
    clearDerivedData();
  }

  @Override
  public void changePlacementSide(IntPoint pPole) {
    this.isFront = !this.isFront;
    Point relLocation = Point.ZERO.translateBy(this.translation);
    this.translation = relLocation.mirrorVertical(pPole).differenceBy(Point.ZERO);
    clearDerivedData();
  }

  @Override
  public void rotateApprox(double pAngleInDegree, FloatPoint pPole) {
    double turnAngle = pAngleInDegree;
    if (!this.isFront && this.board.components.getFlipStyleRotateFirst()) {
      turnAngle = 360 - pAngleInDegree;
    }
    this.rotationInDegree += turnAngle;
    while (this.rotationInDegree >= 360) {
      this.rotationInDegree -= 360;
    }
    while (this.rotationInDegree < 0) {
      this.rotationInDegree += 360;
    }
    FloatPoint newTranslation =
        this.translation.toFloat().rotate(Math.toRadians(pAngleInDegree), pPole);
    this.translation = newTranslation.round().differenceBy(Point.ZERO);
    clearDerivedData();
  }

  @Override
  public void turn90Degree(int pFactor, IntPoint pPole) {
    this.rotationInDegree += pFactor * 90;
    while (this.rotationInDegree >= 360) {
      this.rotationInDegree -= 360;
    }
    while (this.rotationInDegree < 0) {
      this.rotationInDegree += 360;
    }
    Point relLocation = Point.ZERO.translateBy(this.translation);
    this.translation = relLocation.turn90Degree(pFactor, pPole).differenceBy(Point.ZERO);
    clearDerivedData();
  }

  public Area getArea() {
    if (this.precalculatedAbsoluteArea == null) {
      if (this.relativeArea == null) {
        FRLogger.warn("ObstacleArea.get_area: area is null");
        return null;
      }
      Area turnedArea = this.relativeArea;
      if (!this.isFront && !this.board.components.getFlipStyleRotateFirst()) {
        turnedArea = turnedArea.mirrorVertical(Point.ZERO);
      }
      if (this.rotationInDegree != 0) {
        double rotation = this.rotationInDegree;
        if (rotation % 90 == 0) {
          turnedArea = turnedArea.turn90Degree(((int) rotation) / 90, Point.ZERO);
        } else {
          turnedArea = turnedArea.rotateApprox(Math.toRadians(rotation), FloatPoint.ZERO);
        }
      }
      if (!this.isFront && this.board.components.getFlipStyleRotateFirst()) {
        turnedArea = turnedArea.mirrorVertical(Point.ZERO);
      }
      this.precalculatedAbsoluteArea = turnedArea.translateBy(this.translation);
    }
    return this.precalculatedAbsoluteArea;
  }

  @Override
  public void clearDerivedData() {
    precalculatedAbsoluteArea = null;
  }

  @Override
  public void printInfo(ObjectInfoPanel pWindow, Locale pLocale) {}

  @Override
  public boolean write(ObjectOutputStream pStream) {
    try {
      pStream.writeObject(this);
    } catch (IOException _) {
      return false;
    }
    return true;
  }
}
