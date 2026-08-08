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
      PolylineShape[] p_shapes, int p_clearance_class_no, int p_id_no, BasicBoard p_board) {
    super(new int[0], p_clearance_class_no, p_id_no, 0, FixedState.SYSTEM_FIXED, p_board);
    shapes = p_shapes;
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
  public int shapeLayer(int p_index) {
    int shapeCount = this.tileShapeCount();
    int result;
    if (shapeCount > 0) {
      result = p_index * this.board.layerStructure.arr.length / shapeCount;
    } else {
      result = 0;
    }
    if (result < 0 || result >= this.board.layerStructure.arr.length) {
      FRLogger.warn("BoardOutline.shapeLayer: p_index out of range");
    }
    return result;
  }

  @Override
  public boolean isObstacle(Item p_other) {
    return !(p_other instanceof BoardOutline || p_other instanceof ObstacleArea);
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
  public boolean isOnLayer(int p_layer) {
    return true;
  }

  @Override
  public void translateBy(Vector p_vector) {
    for (PolylineShape currShape : this.shapes) {
      currShape = currShape.translateBy(p_vector);
    }
    if (keepoutArea != null) {
      keepoutArea = keepoutArea.translateBy(p_vector);
    }
    keepoutLines = null;
  }

  @Override
  public void turn90Degree(int p_factor, IntPoint p_pole) {
    for (PolylineShape currShape : this.shapes) {
      currShape = currShape.turn90Degree(p_factor, p_pole);
    }
    if (keepoutArea != null) {
      keepoutArea = keepoutArea.turn90Degree(p_factor, p_pole);
    }
    keepoutLines = null;
  }

  @Override
  public void rotateApprox(double p_angle_in_degree, FloatPoint p_pole) {
    double angle = Math.toRadians(p_angle_in_degree);
    for (PolylineShape currShape : this.shapes) {
      currShape = currShape.rotateApprox(angle, p_pole);
    }
    if (keepoutArea != null) {
      keepoutArea = keepoutArea.rotateApprox(angle, p_pole);
    }
    keepoutLines = null;
  }

  @Override
  public void changePlacementSide(IntPoint p_pole) {
    for (PolylineShape currShape : this.shapes) {
      currShape = currShape.mirrorVertical(p_pole);
    }
    if (keepoutArea != null) {
      keepoutArea = keepoutArea.mirrorVertical(p_pole);
    }
    keepoutLines = null;
  }

  @Override
  public double getDrawIntensity(GraphicsContext p_graphics_context) {
    return 1;
  }

  @Override
  public int getDrawPriority() {
    return Drawable.MAX_DRAW_PRIORITY;
  }

  public int shapeCount() {
    return this.shapes.length;
  }

  public PolylineShape getShape(int p_index) {
    if (p_index < 0 || p_index >= this.shapes.length) {
      FRLogger.warn("BoardOutline.get_shape: p_index out of range");
      return null;
    }
    return this.shapes[p_index];
  }

  @Override
  public boolean isSelectedByFilter(ItemSelectionFilter p_filter) {
    if (!this.isSelectedByFixedFilter(p_filter)) {
      return false;
    }
    return p_filter.isSelected(ItemSelectionFilter.SelectableChoices.BOARD_OUTLINE);
  }

  @Override
  public Color[] getDrawColors(GraphicsContext p_graphics_context) {
    Color[] colorArr = new Color[this.board.layerStructure.arr.length];
    Color drawColor = p_graphics_context.getOutlineColor();
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
      Graphics p_g, GraphicsContext p_graphics_context, Color[] p_color_arr, double p_intensity) {
    if (p_graphics_context == null || p_intensity <= 0) {
      return;
    }
    for (PolylineShape currShape : this.shapes) {
      FloatPoint[] drawCorners = currShape.cornerApproxArr();
      FloatPoint[] closedDrawCorners = new FloatPoint[drawCorners.length + 1];
      System.arraycopy(drawCorners, 0, closedDrawCorners, 0, drawCorners.length);
      closedDrawCorners[closedDrawCorners.length - 1] = drawCorners[0];
      p_graphics_context.draw(closedDrawCorners, HALF_WIDTH, p_color_arr[0], p_g, p_intensity);
    }
  }

  @Override
  public Item copy(int p_id_no) {
    return new BoardOutline(this.shapes, this.clearanceClassNo(), p_id_no, this.board);
  }

  @Override
  public void printInfo(ObjectInfoPanel p_window, Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);
    p_window.appendBold(tm.getText("boardOutline"));
    printClearanceInfo(p_window, p_locale);
    p_window.newline();
  }

  @Override
  public boolean write(ObjectOutputStream p_stream) {
    try {
      p_stream.writeObject(this);
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
  public void generateKeepoutOutside(boolean p_value) {
    if (p_value == keepoutOutsideOutline) {
      return;
    }
    keepoutOutsideOutline = p_value;
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
  protected TileShape[] calculateTreeShapes(ShapeSearchTree p_search_tree) {
    return p_search_tree.calculateTreeShapes(this);
  }
}
