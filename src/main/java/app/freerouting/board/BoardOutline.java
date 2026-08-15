package app.freerouting.board;

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
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
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

  /** Creates a new instance of BoardOutline. */
  public BoardOutline(PolylineShape[] shapes, int clearanceClassNo, int idNo, BasicBoard board) {
    super(new int[0], clearanceClassNo, idNo, 0, FixedState.SYSTEM_FIXED, board);
    this.shapes = shapes;
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
  public int shapeLayer(int index) {
    int shapeCount = this.tileShapeCount();
    int result;
    if (shapeCount > 0) {
      result = index * this.board.layerStructure.arr.length / shapeCount;
    } else {
      result = 0;
    }
    if (result < 0 || result >= this.board.layerStructure.arr.length) {
      FRLogger.warn("BoardOutline.shapeLayer: p_index out of range");
    }
    return result;
  }

  @Override
  public boolean isObstacle(Item other) {
    return !(other instanceof BoardOutline || other instanceof ObstacleArea);
  }

  @Override
  public IntBox boundingBox() {
    IntBox result = IntBox.EMPTY;
    for (PolylineShape currentShape : this.shapes) {
      result = result.union(currentShape.boundingBox());
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
  public boolean isOnLayer(int layer) {
    return true;
  }

  @Override
  public void translateBy(Vector vector) {
    for (PolylineShape currentShape : this.shapes) {
      currentShape = currentShape.translateBy(vector);
    }
    if (keepoutArea != null) {
      keepoutArea = keepoutArea.translateBy(vector);
    }
    keepoutLines = null;
  }

  @Override
  public void turn90Degree(int factor, IntPoint pole) {
    for (PolylineShape currentShape : this.shapes) {
      currentShape = currentShape.turn90Degree(factor, pole);
    }
    if (keepoutArea != null) {
      keepoutArea = keepoutArea.turn90Degree(factor, pole);
    }
    keepoutLines = null;
  }

  @Override
  public void rotateApprox(double angleInDegree, FloatPoint pole) {
    double angle = Math.toRadians(angleInDegree);
    for (PolylineShape currentShape : this.shapes) {
      currentShape = currentShape.rotateApprox(angle, pole);
    }
    if (keepoutArea != null) {
      keepoutArea = keepoutArea.rotateApprox(angle, pole);
    }
    keepoutLines = null;
  }

  @Override
  public void changePlacementSide(IntPoint pole) {
    for (PolylineShape currentShape : this.shapes) {
      currentShape = currentShape.mirrorVertical(pole);
    }
    if (keepoutArea != null) {
      keepoutArea = keepoutArea.mirrorVertical(pole);
    }
    keepoutLines = null;
  }

  /** ShapeCount. */
  public int shapeCount() {
    return this.shapes.length;
  }

  /** Get shape. */
  public PolylineShape getShape(int index) {
    if (index < 0 || index >= this.shapes.length) {
      FRLogger.warn("BoardOutline.get_shape: p_index out of range");
      return null;
    }
    return this.shapes[index];
  }

  @Override
  public boolean isSelectedByFilter(ItemSelectionFilter filter) {
    if (!this.isSelectedByFixedFilter(filter)) {
      return false;
    }
    return filter.isSelected(ItemSelectionFilter.SelectableChoices.BOARD_OUTLINE);
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
  public Item copy(int idNo) {
    return new BoardOutline(this.shapes, this.clearanceClassNo(), idNo, this.board);
  }

  @Override
  public void printInfo(ObjectInfoPanel window, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);
    window.appendBold(tm.getText("boardOutline"));
    printClearanceInfo(window, locale);
    window.newline();
  }

  @Override
  public boolean write(ObjectOutputStream stream) {
    try {
      stream.writeObject(this);
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
  public void generateKeepoutOutside(boolean value) {
    if (value == keepoutOutsideOutline) {
      return;
    }
    keepoutOutsideOutline = value;
    if (this.board == null || this.board.searchTreeManager == null) {
      return;
    }
    this.board.searchTreeManager.remove(this);
    this.board.searchTreeManager.insert(this);
  }

  /** Returns the sum of the lines of all outline polygons. */
  public int lineCount() {
    int result = 0;
    for (PolylineShape currentShape : this.shapes) {
      result += currentShape.borderLineCount();
    }
    return result;
  }

  /** Returns the half width of the lines of this outline. */
  public int getHalfWidth() {
    return HALF_WIDTH;
  }

  @Override
  protected TileShape[] calculateTreeShapes(ShapeSearchTree searchTree) {
    return searchTree.calculateTreeShapes(this);
  }
}
