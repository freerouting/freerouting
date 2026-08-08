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
import app.freerouting.util.TextManager;
import java.awt.Color;
import java.awt.Graphics;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.text.NumberFormat;
import java.util.Locale;

/** An item on the board with a relativeArea shape, for example keepout, conduction relativeArea */
public class ObstacleArea extends Item implements Serializable {

  /** For debugging the division into tree shapes */
  private static final boolean display_tree_shapes = false;

  /**
   * The name of this ObstacleArea, which is null, if the ObstacleArea does not belong to a
   * component.
   */
  public final String name;

  private final Area relativeArea;

  /** the layer of this relativeArea */
  private int layer;

  private transient Area precalculatedAbsoluteArea;
  private Vector translation;
  private double rotationInDegree;
  private boolean sideChanged;

  /**
   * Creates a new relativeArea item which may belong to several nets. p_name is null, if the
   * ObstacleArea does not belong to a component.
   */
  ObstacleArea(
      Area pArea,
      int pLayer,
      Vector pTranslation,
      double pRotationInDegree,
      boolean pSideChanged,
      int[] pNetNoArr,
      int pClearanceType,
      int pIdNo,
      int pCmpNo,
      String pName,
      FixedState pFixedState,
      BasicBoard pBoard) {
    super(pNetNoArr, pClearanceType, pIdNo, pCmpNo, pFixedState, pBoard);
    this.relativeArea = pArea;
    this.layer = pLayer;
    this.translation = pTranslation;
    this.rotationInDegree = pRotationInDegree;
    this.sideChanged = pSideChanged;
    this.name = pName;
  }

  /**
   * Creates a new relativeArea item without net. p_name is null, if the ObstacleArea does not
   * belong to a component.
   */
  ObstacleArea(
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
    return new ObstacleArea(
        relativeArea,
        layer,
        translation,
        rotationInDegree,
        sideChanged,
        copiedNetNos,
        clearanceClassNo(),
        pIdNo,
        getComponentNo(),
        name,
        getFixedState(),
        board);
  }

  public Area getArea() {
    if (this.precalculatedAbsoluteArea == null) {
      if (this.relativeArea == null) {
        FRLogger.warn("ObstacleArea.get_area: area is null");
        return null;
      }
      Area turnedArea = this.relativeArea;
      if (this.sideChanged && !this.board.components.getFlipStyleRotateFirst()) {
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
      if (this.sideChanged && this.board.components.getFlipStyleRotateFirst()) {
        turnedArea = turnedArea.mirrorVertical(Point.ZERO);
      }
      this.precalculatedAbsoluteArea = turnedArea.translateBy(this.translation);
    }
    return this.precalculatedAbsoluteArea;
  }

  protected Area getRelativeArea() {
    return this.relativeArea;
  }

  @Override
  public boolean isOnLayer(int pLayer) {
    return layer == pLayer;
  }

  @Override
  public int firstLayer() {
    return this.layer;
  }

  @Override
  public int lastLayer() {
    return this.layer;
  }

  public int getLayer() {
    return this.layer;
  }

  @Override
  public IntBox boundingBox() {
    return this.getArea().boundingBox();
  }

  @Override
  public boolean isObstacle(Item pOther) {
    if (pOther.sharesNet(this)) {
      return false;
    }
    return pOther instanceof Trace || pOther instanceof Via;
  }

  @Override
  protected TileShape[] calculateTreeShapes(ShapeSearchTree pSearchTree) {
    return pSearchTree.calculateTreeShapes(this);
  }

  @Override
  public int tileShapeCount() {
    TileShape[] tileShapes = this.splitToConvex();
    if (tileShapes == null) {
      // an error occurred while dividing the relativeArea
      return 0;
    }
    return tileShapes.length;
  }

  @Override
  public TileShape getTileShape(int pNo) {
    TileShape[] tileShapes = this.splitToConvex();
    if (tileShapes == null || pNo < 0 || pNo >= tileShapes.length) {
      FRLogger.warn("ConvexObstacle.get_tile_shape: p_no out of range");
      return null;
    }
    return tileShapes[pNo];
  }

  @Override
  public void translateBy(Vector pVector) {
    this.translation = this.translation.add(pVector);
    this.clearDerivedData();
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
    this.clearDerivedData();
  }

  @Override
  public void rotateApprox(double pAngleInDegree, FloatPoint pPole) {
    double turnAngle = pAngleInDegree;
    if (this.sideChanged && this.board.components.getFlipStyleRotateFirst()) {
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
    this.clearDerivedData();
  }

  @Override
  public void changePlacementSide(IntPoint pPole) {
    this.sideChanged = !this.sideChanged;
    if (this.board != null) {
      this.layer = board.getLayerCount() - this.layer - 1;
    }
    Point relLocation = Point.ZERO.translateBy(this.translation);
    this.translation = relLocation.mirrorVertical(pPole).differenceBy(Point.ZERO);
    this.clearDerivedData();
  }

  @Override
  public boolean isSelectedByFilter(ItemSelectionFilter pFilter) {
    if (!this.isSelectedByFixedFilter(pFilter)) {
      return false;
    }
    return pFilter.isSelected(ItemSelectionFilter.SelectableChoices.KEEPOUT);
  }

  @Override
  public Color[] getDrawColors(GraphicsContext pGraphicsContext) {
    return pGraphicsContext.getObstacleColors();
  }

  @Override
  public double getDrawIntensity(GraphicsContext pGraphicsContext) {
    return pGraphicsContext.getObstacleColorIntensity();
  }

  @Override
  public int getDrawPriority() {
    return Drawable.MIN_DRAW_PRIORITY;
  }

  @Override
  public void draw(
      Graphics pG, GraphicsContext pGraphicsContext, Color[] pColorArr, double pIntensity) {
    if (pGraphicsContext == null || pIntensity <= 0) {
      return;
    }
    Color color = pColorArr[this.layer];
    double intensity = pGraphicsContext.getLayerVisibility(this.layer) * pIntensity;
    pGraphicsContext.fillArea(this.getArea(), pG, color, intensity);
    if (intensity > 0 && display_tree_shapes) {
      ShapeSearchTree defaultTree = this.board.searchTreeManager.getDefaultTree();
      for (int i = 0; i < this.treeShapeCount(defaultTree); i++) {
        pGraphicsContext.drawBoundary(this.getTreeShape(defaultTree, i), 1, Color.white, pG, 1);
      }
    }
  }

  @Override
  public int shapeLayer(int pIndex) {
    return layer;
  }

  protected Vector getTranslation() {
    return translation;
  }

  protected double getRotationInDegree() {
    return rotationInDegree;
  }

  protected boolean getSideChanged() {
    return sideChanged;
  }

  @Override
  public void printInfo(ObjectInfoPanel pWindow, Locale pLocale) {
    TextManager tm = new TextManager(this.getClass(), pLocale);

    pWindow.appendBold(tm.getText("keepout"));
    int cmpNo = this.getComponentNo();
    if (cmpNo > 0) {
      pWindow.append(" " + tm.getText("of_component") + " ");
      Component component = board.components.get(cmpNo);
      pWindow.append(component.name, tm.getText("component_info"), component);
    }
    this.printShapeInfo(pWindow, pLocale);
    this.printItemInfo(pWindow, pLocale);
    pWindow.newline();
  }

  /** Used in the implementation of print_info for this class and derived classes. */
  protected final void printShapeInfo(ObjectInfoPanel pWindow, Locale pLocale) {
    TextManager tm = new TextManager(this.getClass(), pLocale);

    pWindow.append(" " + tm.getText("at") + " ");
    FloatPoint center = this.getArea().getBorder().centreOfGravity();
    pWindow.append(center);
    Integer holeCount = this.relativeArea.getHoles().length;
    if (holeCount > 0) {
      pWindow.append(" " + tm.getText("with") + " ");
      NumberFormat nf = NumberFormat.getInstance(pLocale);
      pWindow.append(nf.format(holeCount));
      if (holeCount == 1) {
        pWindow.append(" " + tm.getText("hole"));
      } else {
        pWindow.append(" " + tm.getText("holes"));
      }
    }
    pWindow.append(" " + tm.getText("on_layer") + " ");
    pWindow.append(this.board.layerStructure.arr[this.getLayer()].name);
  }

  TileShape[] splitToConvex() {
    if (this.relativeArea == null) {
      FRLogger.warn("ObstacleArea.split_to_convex: area is null");
      return null;
    }
    return this.getArea().splitToConvex();
  }

  @Override
  public void clearDerivedData() {
    super.clearDerivedData();
    this.precalculatedAbsoluteArea = null;
  }

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
