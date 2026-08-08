package app.freerouting.board;

import app.freerouting.boardgraphics.Drawable;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.PolylineArea;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import java.awt.Color;
import java.awt.Graphics;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Locale;

/** Class describing a board outline. */
public class BoardOutline extends Item implements Serializable {

  private static final int HALF_WIDTH = 100;

  /** The board shapes inside the outline curves. */
  private final PolylineShape[] shapes;

  /**
   * The board shape outside the outline curves, where a keepout will be generated The outline
   * curves are holes of the keepoutArea.
   */
  private Area keepoutArea;

  /**
   * Used instead of keepoutArea if only the line shapes of the outlines are inserted as keepout.
   */
  private TileShape[] keepoutLines;

  private boolean keepoutOutsideOutline;

  /** Creates a new instance of BoardOutline */
  public BoardOutline(
      PolylineShape[] pShapes, int pClearanceClassNo, int pIdNo, BasicBoard pBoard) {
    super(new int[0], pClearanceClassNo, pIdNo, 0, FixedState.SYSTEM_FIXED, pBoard);
    shapes = pShapes;
  }

  @Override
  public int tileShapeCount() {
    int result;
    if (this.keepoutOutsideOutline) {
      TileShape[] tileShapes = this.getKeepoutArea().splitToConvex();
      if (tileShapes == null) {
        // an error occurred while dividing the area
        result = 0;
      } else {
        result = tileShapes.length * this.board.layerStructure.arr.length;
      }
    } else {
      result = this.lineCount() * this.board.layerStructure.arr.length;
    }
    return result;
  }

  @Override
  public int shapeLayer(int pIndex) {
    int shapeCount = this.tileShapeCount();
    int result;
    if (shapeCount > 0) {
      result = pIndex * this.board.layerStructure.arr.length / shapeCount;
    } else {
      result = 0;
    }
    if (result < 0 || result >= this.board.layerStructure.arr.length) {
      FRLogger.warn("BoardOutline.shapeLayer: p_index out of range");
    }
    return result;
  }

  @Override
  public boolean isObstacle(Item pOther) {
    return !(pOther instanceof BoardOutline || pOther instanceof ObstacleArea);
  }

  @Override
  public IntBox boundingBox() {
    IntBox result = IntBox.EMPTY;
    for (PolylineShape currShape : this.shapes) {
      result = result.union(currShape.boundingBox());
    }
    return result;
  }

  @Override
  public int firstLayer() {
    return 0;
  }

  @Override
  public int lastLayer() {
    return this.board.layerStructure.arr.length - 1;
  }

  @Override
  public boolean isOnLayer(int pLayer) {
    return true;
  }

  @Override
  public void translateBy(Vector pVector) {
    for (PolylineShape currShape : this.shapes) {
      currShape = currShape.translateBy(pVector);
    }
    if (keepoutArea != null) {
      keepoutArea = keepoutArea.translateBy(pVector);
    }
    keepoutLines = null;
  }

  @Override
  public void turn90Degree(int pFactor, IntPoint pPole) {
    for (PolylineShape currShape : this.shapes) {
      currShape = currShape.turn90Degree(pFactor, pPole);
    }
    if (keepoutArea != null) {
      keepoutArea = keepoutArea.turn90Degree(pFactor, pPole);
    }
    keepoutLines = null;
  }

  @Override
  public void rotateApprox(double pAngleInDegree, FloatPoint pPole) {
    double angle = Math.toRadians(pAngleInDegree);
    for (PolylineShape currShape : this.shapes) {
      currShape = currShape.rotateApprox(angle, pPole);
    }
    if (keepoutArea != null) {
      keepoutArea = keepoutArea.rotateApprox(angle, pPole);
    }
    keepoutLines = null;
  }

  @Override
  public void changePlacementSide(IntPoint pPole) {
    for (PolylineShape currShape : this.shapes) {
      currShape = currShape.mirrorVertical(pPole);
    }
    if (keepoutArea != null) {
      keepoutArea = keepoutArea.mirrorVertical(pPole);
    }
    keepoutLines = null;
  }

  @Override
  public double getDrawIntensity(GraphicsContext pGraphicsContext) {
    return 1;
  }

  @Override
  public int getDrawPriority() {
    return Drawable.MAX_DRAW_PRIORITY;
  }

  public int shapeCount() {
    return this.shapes.length;
  }

  public PolylineShape getShape(int pIndex) {
    if (pIndex < 0 || pIndex >= this.shapes.length) {
      FRLogger.warn("BoardOutline.get_shape: p_index out of range");
      return null;
    }
    return this.shapes[pIndex];
  }

  @Override
  public boolean isSelectedByFilter(ItemSelectionFilter pFilter) {
    if (!this.isSelectedByFixedFilter(pFilter)) {
      return false;
    }
    return pFilter.isSelected(ItemSelectionFilter.SelectableChoices.BOARD_OUTLINE);
  }

  @Override
  public Color[] getDrawColors(GraphicsContext pGraphicsContext) {
    Color[] colorArr = new Color[this.board.layerStructure.arr.length];
    Color drawColor = pGraphicsContext.getOutlineColor();
    Arrays.fill(colorArr, drawColor);
    return colorArr;
  }

  /**
   * The board shape outside the outline curves, where a keepout will be generated The outline
   * curves are holes of the keepoutArea.
   */
  Area getKeepoutArea() {
    if (this.keepoutArea == null) {
      PolylineShape[] holeArr = this.shapes.clone();
      keepoutArea = new PolylineArea(this.board.boundingBox, holeArr);
    }
    return this.keepoutArea;
  }

  TileShape[] getKeepoutLines() {
    if (this.keepoutLines == null) {
      this.keepoutLines = new TileShape[0];
    }
    return this.keepoutLines;
  }

  @Override
  public void draw(
      Graphics pG, GraphicsContext pGraphicsContext, Color[] pColorArr, double pIntensity) {
    if (pGraphicsContext == null || pIntensity <= 0) {
      return;
    }
    for (PolylineShape currShape : this.shapes) {
      FloatPoint[] drawCorners = currShape.cornerApproxArr();
      FloatPoint[] closedDrawCorners = new FloatPoint[drawCorners.length + 1];
      System.arraycopy(drawCorners, 0, closedDrawCorners, 0, drawCorners.length);
      closedDrawCorners[closedDrawCorners.length - 1] = drawCorners[0];
      pGraphicsContext.draw(closedDrawCorners, HALF_WIDTH, pColorArr[0], pG, pIntensity);
    }
  }

  @Override
  public Item copy(int pIdNo) {
    return new BoardOutline(this.shapes, this.clearanceClassNo(), pIdNo, this.board);
  }

  @Override
  public void printInfo(ObjectInfoPanel pWindow, Locale pLocale) {
    TextManager tm = new TextManager(this.getClass(), pLocale);
    pWindow.appendBold(tm.getText("boardOutline"));
    printClearanceInfo(pWindow, pLocale);
    pWindow.newline();
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

  /**
   * Returns, if keepout is generated outside the board outline. Otherwise, only the line shapes of
   * the outlines are inserted as keepout.
   */
  public boolean keepoutOutsideOutlineGenerated() {
    return keepoutOutsideOutline;
  }

  /**
   * Makes the area outside this Outline to Keepout, if p_value = true. Reinserts this Outline into
   * the search trees, if the value changes.
   */
  public void generateKeepoutOutside(boolean pValue) {
    if (pValue == keepoutOutsideOutline) {
      return;
    }
    keepoutOutsideOutline = pValue;
    if (this.board == null || this.board.searchTreeManager == null) {
      return;
    }
    this.board.searchTreeManager.remove(this);
    this.board.searchTreeManager.insert(this);
  }

  /** Returns the sum of the lines of all outline polygons. */
  public int lineCount() {
    int result = 0;
    for (PolylineShape currShape : this.shapes) {
      result += currShape.borderLineCount();
    }
    return result;
  }

  /** Returns the half width of the lines of this outline. */
  public int getHalfWidth() {
    return HALF_WIDTH;
  }

  @Override
  protected TileShape[] calculateTreeShapes(ShapeSearchTree pSearchTree) {
    return pSearchTree.calculateTreeShapes(this);
  }
}
