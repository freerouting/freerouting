package app.freerouting.board.model.structure;

import app.freerouting.board.actions.ItemInfoPrinter;
import app.freerouting.board.actions.ItemSelectionFilter;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.ObstacleArea;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.searchtree.ShapeSearchTree;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.PolylineArea;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/** Class describing a board outline. */
public class BoardOutline extends Item implements Serializable {

  private static final int HALF_WIDTH = 100;

  /** Upper bound of the slices of one outline line. */
  private static final int MAX_LINE_SLICES = 256;

  /** The board shapes inside the outline curves. */
  private PolylineShape[] shapes;

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

  /**
   * Convex pieces of the keepout area, cut finer where an oblique outline edge would make their
   * bounding octagon reach into the board. Derived from keepoutArea, rebuilt on demand.
   */
  private transient TileShape[] keepoutTiles;

  /** Number of slices per outline line, in the order of {@link #lineShape}. Rebuilt on demand. */
  private transient int[] lineSlices;

  /** Creates a new instance of BoardOutline. */
  public BoardOutline(PolylineShape[] shapes, int clearanceClassIndex, int id, BasicBoard board) {
    super(new int[0], clearanceClassIndex, id, 0, FixedState.SYSTEM_FIXED, board);
    this.shapes = shapes;
  }

  @Override
  public int tileShapeCount() {
    int result;
    if (this.keepoutOutsideOutline) {
      TileShape[] tileShapes = this.getKeepoutTiles();
      if (tileShapes == null) {
        // an error occurred while dividing the area
        result = 0;
      } else {
        result = tileShapes.length * this.board.layerStructure.layers.length;
      }
    } else {
      result = this.lineSliceCount() * this.board.layerStructure.layers.length;
    }
    return result;
  }

  @Override
  public int shapeLayer(int index) {
    int shapeCount = this.tileShapeCount();
    int result;
    if (shapeCount > 0) {
      result = index * this.board.layerStructure.layers.length / shapeCount;
    } else {
      result = 0;
    }
    if (result < 0 || result >= this.board.layerStructure.layers.length) {
      FRLogger.warn("BoardOutline.shapeLayer: index out of range");
    }
    return result;
  }

  private transient Set<Integer> edgePinNets;
  private transient int cachedItemCount = -1;
  private transient int countedPins = -1;
  private transient long countedVersion = -1;
  private transient UndoableObjects countedList;

  /** Nets whose pins touch the outline. */
  private Set<Integer> getEdgePinNets() {
    int currentItemCount = -1;
    if (this.board != null) {
      long version = this.board.itemList.getVersion();
      if (version != this.countedVersion || this.countedList != this.board.itemList) {
        this.countedPins = 0;
        Iterator<UndoableObjects.UndoableObjectNode> iterator =
            this.board.itemList.startReadObject();
        while (this.board.itemList.readObject(iterator) instanceof Item item) {
          if (item instanceof Pin) {
            this.countedPins++;
          }
        }
        this.countedVersion = version;
        this.countedList = this.board.itemList;
      }
      currentItemCount = this.countedPins;
    }
    if (this.edgePinNets == null || this.cachedItemCount != currentItemCount) {
      this.cachedItemCount = currentItemCount;
      Set<Integer> set = new HashSet<>();
      if (this.board != null) {
        for (Pin pin : this.board.getPins()) {
          Point center = pin.getCenter();
          boolean isEdgeOrOutside = false;
          if (center != null && !this.contains(center)) {
            isEdgeOrOutside = true;
          } else {
            for (int layer = pin.firstLayer(); layer <= pin.lastLayer(); layer++) {
              Shape shape = pin.getShape(layer - pin.firstLayer());
              if (shape instanceof TileShape tileShape) {
                for (int c = 0; c < tileShape.borderLineCount(); c++) {
                  if (!this.contains(tileShape.corner(c))) {
                    isEdgeOrOutside = true;
                    break;
                  }
                }
              }
              if (isEdgeOrOutside) {
                break;
              }
            }
          }
          if (isEdgeOrOutside) {
            for (int net : pin.netNumbers) {
              set.add(net);
            }
          }
        }
      }
      this.edgePinNets = set;
    }
    return this.edgePinNets;
  }

  /**
   * Returns the smallest gap, in board units, between the copper of any pin and the outline lines.
   * The gap is 0 if a pin touches or crosses the outline. Returns {@link Double#POSITIVE_INFINITY}
   * if the board has no pins.
   *
   * <p>Pad corners are sampled, so the result is exact for polygonal pads and conservative (never
   * larger than the true gap) for round pads, which are represented by their bounding box.
   */
  public double minimumPinGap() {
    double result = Double.POSITIVE_INFINITY;
    if (this.board == null) {
      return result;
    }
    for (Pin pin : this.board.getPins()) {
      for (int layer = pin.firstLayer(); layer <= pin.lastLayer(); layer++) {
        Shape shape = pin.getShape(layer - pin.firstLayer());
        if (shape == null) {
          continue;
        }
        result = Math.min(result, cornerGap(shape));
        if (result <= 0) {
          return 0;
        }
      }
    }
    return result;
  }

  private double cornerGap(Shape padShape) {
    double result = Double.POSITIVE_INFINITY;
    if (padShape.isEmpty() || !padShape.isBounded()) {
      return result;
    }
    if (padShape instanceof PolylineShape polylineShape) {
      for (int c = 0; c < polylineShape.borderLineCount(); c++) {
        result = Math.min(result, distanceToOutline(polylineShape.cornerApprox(c)));
      }
    } else {
      IntBox box = padShape.boundingBox();
      for (int c = 0; c < 4; c++) {
        result = Math.min(result, distanceToOutline(box.corner(c).toFloat()));
      }
    }
    return result;
  }

  /** Distance to the outline lines, 0 for points that are not inside the outline. */
  private double distanceToOutline(FloatPoint point) {
    if (!this.contains(point)) {
      return 0;
    }
    double result = Double.POSITIVE_INFINITY;
    for (PolylineShape outlineShape : this.shapes) {
      // PolygonShape.borderDistance is not implemented, so measure to the corner-to-corner
      // segments directly.
      int cornerCount = outlineShape.borderLineCount();
      for (int i = 0; i < cornerCount; i++) {
        FloatPoint from = outlineShape.corner(i).toFloat();
        FloatPoint to = outlineShape.corner((i + 1) % cornerCount).toFloat();
        result = Math.min(result, new FloatLine(from, to).segmentDistance(point));
      }
    }
    return result;
  }

  /** Invalidates cached edge pin nets when board geometry or pins change. */
  public void invalidateEdgePinNets() {
    this.edgePinNets = null;
    this.cachedItemCount = -1;
    this.countedVersion = -1;
    this.countedList = null;
  }

  /**
   * Erik Gavriluk (freerouting.net): whether a net with a pin on or beyond the outline (such as an
   * edge connector's fingers) may route across the outline everywhere on the board. Default true
   * for Specctra/DSN compatibility; can be set false when edge pins must not exempt other traces of
   * that net from respecting edge clearance.
   */
  public boolean edgePinNetsCrossOutline = true;

  @Override
  public boolean isTraceObstacle(int netNumber) {
    if (edgePinNetsCrossOutline && netNumber > 0 && getEdgePinNets().contains(netNumber)) {
      return false;
    }
    return true;
  }

  /**
   * A trace may cross the outline only when every one of its nets is an edge-pin net. A tie trace
   * that also carries an ordinary net stays blocked.
   */
  public boolean blocksNets(int[] netNumbers) {
    if (netNumbers == null || netNumbers.length == 0) {
      return true;
    }
    for (int netNo : netNumbers) {
      if (isTraceObstacle(netNo)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isObstacle(Item other) {
    if (other instanceof BoardOutline || other instanceof ObstacleArea) {
      return false;
    }
    if (other instanceof Trace otherTrace && !blocksNets(otherTrace.netNumbers)) {
      return false;
    }
    return true;
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
    return this.board.layerStructure.layers.length - 1;
  }

  @Override
  public boolean isOnLayer(int layer) {
    return true;
  }

  @Override
  public void translateBy(Vector vector) {
    PolylineShape[] newShapes = new PolylineShape[this.shapes.length];
    for (int i = 0; i < this.shapes.length; i++) {
      newShapes[i] = this.shapes[i].translateBy(vector);
    }
    this.shapes = newShapes;
    if (keepoutArea != null) {
      keepoutArea = keepoutArea.translateBy(vector);
    }
    keepoutLines = null;
    keepoutTiles = null;
    lineSlices = null;
    invalidateEdgePinNets();
  }

  @Override
  public void turn90Degree(int factor, IntPoint pole) {
    PolylineShape[] newShapes = new PolylineShape[this.shapes.length];
    for (int i = 0; i < this.shapes.length; i++) {
      newShapes[i] = this.shapes[i].turn90Degree(factor, pole);
    }
    this.shapes = newShapes;
    if (keepoutArea != null) {
      keepoutArea = keepoutArea.turn90Degree(factor, pole);
    }
    keepoutLines = null;
    keepoutTiles = null;
    lineSlices = null;
    invalidateEdgePinNets();
  }

  @Override
  public void rotateApprox(double angleInDegree, FloatPoint pole) {
    double angle = Math.toRadians(angleInDegree);
    PolylineShape[] newShapes = new PolylineShape[this.shapes.length];
    for (int i = 0; i < this.shapes.length; i++) {
      newShapes[i] = this.shapes[i].rotateApprox(angle, pole);
    }
    this.shapes = newShapes;
    if (keepoutArea != null) {
      keepoutArea = keepoutArea.rotateApprox(angle, pole);
    }
    keepoutLines = null;
    keepoutTiles = null;
    lineSlices = null;
    invalidateEdgePinNets();
  }

  @Override
  public void changePlacementSide(IntPoint pole) {
    PolylineShape[] newShapes = new PolylineShape[this.shapes.length];
    for (int i = 0; i < this.shapes.length; i++) {
      newShapes[i] = this.shapes[i].mirrorVertical(pole);
    }
    this.shapes = newShapes;
    if (keepoutArea != null) {
      keepoutArea = keepoutArea.mirrorVertical(pole);
    }
    keepoutLines = null;
    keepoutTiles = null;
    lineSlices = null;
    invalidateEdgePinNets();
  }

  /** ShapeCount. */
  public int shapeCount() {
    return this.shapes.length;
  }

  /** Get shape. */
  public PolylineShape getShape(int index) {
    if (index < 0 || index >= this.shapes.length) {
      FRLogger.warn("BoardOutline.get_shape: index out of range");
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
  public Area getKeepoutArea() {
    if (this.keepoutArea == null) {
      PolylineShape[] holeArr = this.shapes.clone();
      keepoutArea = new PolylineArea(this.board.boundingBox, holeArr);
    }
    return this.keepoutArea;
  }

  /**
   * Returns the convex pieces of the keepout area that go into the search trees. The 45-degree and
   * 90-degree trees store each piece as its bounding octagon or box. Next to an outline edge that
   * is neither orthogonal nor diagonal, the bounding octagon of a large piece reaches far into the
   * board, and pads near that edge end up inside the keepout. Pieces whose bounding octagon exceeds
   * them by more than the smallest trace half width are therefore cut into smaller pieces. The
   * union of the pieces is still the keepout area, so nothing outside the board becomes routable.
   */
  public TileShape[] getKeepoutTiles() {
    if (this.keepoutTiles == null) {
      TileShape[] convexShapes = this.getKeepoutArea().splitToConvex();
      if (convexShapes == null) {
        return null;
      }
      int tolerance = Math.max(HALF_WIDTH, this.board.rules.getMinTraceHalfWidth());
      this.keepoutTiles = TileShape.splitToTightOctagons(convexShapes, tolerance);
    }
    return this.keepoutTiles;
  }

  TileShape[] getKeepoutLines() {
    if (this.keepoutLines == null) {
      this.keepoutLines = new TileShape[0];
    }
    return this.keepoutLines;
  }

  @Override
  public Item copy(int id) {
    return new BoardOutline(this.shapes, this.clearanceClassIndex(), id, this.board);
  }

  @Override
  public void printInfo(ItemInfoPrinter printer, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);
    printer.appendBold(tm.getText("boardOutline"));
    printClearanceInfo(printer, locale);
    printer.newline();
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
   * Makes the area outside this Outline to Keepout, if value = true. Reinserts this Outline into
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

  /**
   * Returns the shape of the outline line with the given index, counted over all outline polygons,
   * widened by halfWidth on both sides and cut by its neighbouring lines.
   */
  public TileShape lineShape(int lineIndex, int halfWidth) {
    int index = lineIndex;
    for (PolylineShape currentShape : this.shapes) {
      int borderLineCount = currentShape.borderLineCount();
      if (index < borderLineCount) {
        Line[] lines = {
          currentShape.borderLine((index + borderLineCount - 1) % borderLineCount),
          currentShape.borderLine(index),
          currentShape.borderLine((index + 1) % borderLineCount)
        };
        return new Polyline(lines).offsetShape(halfWidth, 0);
      }
      index -= borderLineCount;
    }
    throw new IndexOutOfBoundsException("outline line " + lineIndex);
  }

  /**
   * Returns in how many slices the outline line with the given index goes into the search trees. An
   * oblique line is cut so that the bounding octagon of each slice exceeds it by at most the
   * smallest trace half width (see {@link #getKeepoutTiles}). The count does not depend on the
   * layer, so that {@link #shapeLayer} stays valid.
   */
  public int lineSlices(int lineIndex) {
    if (this.lineSlices == null) {
      int[] slices = new int[this.lineCount()];
      double tolerance = Math.max(HALF_WIDTH, this.board.rules.getMinTraceHalfWidth());
      for (int i = 0; i < slices.length; i++) {
        double excess = this.lineShape(i, HALF_WIDTH).boundingOctagonExcess();
        slices[i] = (int) Math.min(MAX_LINE_SLICES, Math.max(1, Math.ceil(excess / tolerance)));
      }
      this.lineSlices = slices;
    }
    return this.lineSlices[lineIndex];
  }

  /** Returns the number of line slices of all outline polygons on one layer. */
  public int lineSliceCount() {
    int result = 0;
    for (int i = 0; i < this.lineCount(); i++) {
      result += this.lineSlices(i);
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

  /** Returns whether the given point is contained within the board outline shapes. */
  public boolean contains(Point point) {
    if (point == null) {
      return false;
    }
    for (PolylineShape currentShape : this.shapes) {
      if (currentShape.contains(point)) {
        return true;
      }
    }
    return false;
  }

  /** Returns whether the given float point is contained within the board outline shapes. */
  public boolean contains(FloatPoint point) {
    if (point == null) {
      return false;
    }
    for (PolylineShape currentShape : this.shapes) {
      if (currentShape.contains(point)) {
        return true;
      }
    }
    return false;
  }
}
