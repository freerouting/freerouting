package app.freerouting.board.model.structure;

import app.freerouting.board.actions.ItemInfoPrinter;
import app.freerouting.board.actions.ItemSelectionFilter;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.ObstacleArea;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.searchtree.ShapeSearchTree;
import app.freerouting.datastructures.ShapeTree;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Class describing a board outline. */
public class BoardOutline extends Item implements Serializable {

  private static final int HALF_WIDTH = 100;

  /**
   * Upper bound of the slices of one outline line. It keeps a long outline line from filling the
   * search trees: past it, the bounding octagons of the slices exceed the line by more than the
   * tolerance, each by about 1/256 of the excess of the whole line.
   */
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

  /**
   * The tolerance keepoutTiles and lineSlices were cut with, 0 before they are cut. Kept with them
   * so that both caches use the same value and a later change of the trace widths can be detected
   * (see {@link #updateSlicesToTraceWidths}).
   */
  private transient int sliceTolerance;

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
          if (this.isEdgePin(pin)) {
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
   * edge connector's fingers) may route across the outline everywhere on the board.
   *
   * <p>Default false: only the stretch of the outline next to an edge pin is exempt, for the nets
   * of that pin (see {@link #isTraceObstacle(int, int)}), so that a trace can reach the pin. Every
   * other trace and via, including those of the edge pin's net, keeps its clearance to the outline,
   * as KiCad's DRC ({@code copper_edge_clearance}) requires. Set true to restore the earlier
   * net-wide exemption. With {@link #keepoutOutsideOutlineGenerated} the net-wide exemption is
   * kept, since the keepout tiles are not cut around the edge pins.
   */
  public boolean edgePinNetsCrossOutline = false;

  /**
   * The stretch of the outline next to one edge pin on one layer: the pin's bounding box, widened
   * by the clearance and the trace half width of the pin's nets. The outline lines are cut at its
   * borders, and the pieces inside it do not block traces of these nets on this layer.
   */
  private record EdgeWindow(IntBox box, int layer, int[] nets) {}

  /** Edge pin windows the line pieces were cut with; derived with lineSlices, rebuilt on demand. */
  private transient EdgeWindow[] edgeWindows;

  /**
   * Per outline line, the coordinates its tree shapes are cut at along {@link #lineCutAlongX}, or
   * null if no edge window touches the line (the line is then cut by {@link
   * TileShape#splitIntoSlices}). Rebuilt with lineSlices.
   */
  private transient int[][] lineCuts;

  private transient boolean[] lineCutAlongX;

  /** Per line piece of one layer, the indices of the edge windows that contain it. */
  private transient int[][] pieceWindows;

  @Override
  public boolean isTraceObstacle(int netNumber) {
    if ((edgePinNetsCrossOutline || keepoutOutsideOutline)
        && netNumber > 0
        && getEdgePinNets().contains(netNumber)) {
      return false;
    }
    return true;
  }

  /**
   * Like {@link #isTraceObstacle(int)}, but a tree shape of the outline line next to an edge pin of
   * the net (inside its {@link EdgeWindow}, on the pin's layer) is no obstacle to the net's traces.
   */
  @Override
  public boolean isTraceObstacle(int netNumber, int shapeIndex) {
    if (!isTraceObstacle(netNumber)) {
      return false;
    }
    return !exemptsPiece(shapeIndex, netNumber);
  }

  /** Whether the tree shape with the given index lies next to an edge pin of the given net. */
  private boolean exemptsPiece(int shapeIndex, int netNumber) {
    if (netNumber <= 0 || this.keepoutOutsideOutline || this.board == null) {
      return false;
    }
    int perLayer = this.lineSliceCount();
    if (perLayer <= 0 || shapeIndex < 0 || this.pieceWindows == null) {
      return false;
    }
    int layer = shapeIndex / perLayer;
    int[] windows = this.pieceWindows[shapeIndex % perLayer];
    if (windows == null) {
      return false;
    }
    for (int w : windows) {
      EdgeWindow window = this.edgeWindows[w];
      if (window.layer() != layer) {
        continue;
      }
      for (int net : window.nets()) {
        if (net == netNumber) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Whether the tree shape with the given index is exempt for a trace with all the given nets, so
   * that the clearance check accepts what the router was allowed to do.
   */
  public boolean exemptsTrace(int shapeIndex, int[] netNumbers) {
    if (netNumbers == null || netNumbers.length == 0) {
      return false;
    }
    for (int net : netNumbers) {
      if (isTraceObstacle(net, shapeIndex)) {
        return false;
      }
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

  /**
   * Like {@link #blocksNets}, for a trace of the given nets whose shape overlaps this outline on
   * the given layer: false if every tree shape of this outline that the trace shape reaches, with
   * its clearance, lies next to an edge pin of these nets. Used by the shove and insertion checks,
   * which see the outline as one item.
   */
  public boolean blocksTraceAt(
      TileShape traceShape, int layer, int[] netNumbers, int clearanceClassIndex) {
    if (!blocksNets(netNumbers)) {
      return false;
    }
    if (this.board == null || this.lineSliceCount() <= 0 || this.pieceWindows == null) {
      return true;
    }
    Collection<ShapeTree.TreeEntry> entries = new ArrayList<>();
    this.board
        .searchTreeManager
        .getDefaultTree()
        .overlappingTreeEntriesWithClearance(
            traceShape, layer, new int[0], clearanceClassIndex, entries);
    boolean reached = false;
    for (ShapeTree.TreeEntry entry : entries) {
      if (entry.object != this) {
        continue;
      }
      reached = true;
      if (!exemptsTrace(entry.shapeIndexInObject, netNumbers)) {
        return true;
      }
    }
    return !reached;
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
    clearLineCuts();
    sliceTolerance = 0;
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
    clearLineCuts();
    sliceTolerance = 0;
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
    clearLineCuts();
    sliceTolerance = 0;
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
    clearLineCuts();
    sliceTolerance = 0;
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
   * them by more than the smallest trace half width are therefore cut into smaller pieces, up to
   * the depth limit of {@link TileShape#splitToTightOctagons}. The union of the pieces is still the
   * keepout area, so nothing outside the board becomes routable.
   */
  public TileShape[] getKeepoutTiles() {
    if (this.keepoutTiles == null) {
      TileShape[] convexShapes = this.getKeepoutArea().splitToConvex();
      if (convexShapes == null) {
        return null;
      }
      this.keepoutTiles = TileShape.splitToTightOctagons(convexShapes, this.sliceTolerance());
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
   * smallest trace half width (see {@link #getKeepoutTiles}), within {@link #MAX_LINE_SLICES}. The
   * count does not depend on the layer, so that {@link #shapeLayer} stays valid.
   */
  public int lineSlices(int lineIndex) {
    if (this.lineSlices == null) {
      int[] slices = new int[this.lineCount()];
      double tolerance = this.sliceTolerance();
      for (int i = 0; i < slices.length; i++) {
        double excess = this.lineShape(i, HALF_WIDTH).boundingOctagonExcess();
        slices[i] = (int) Math.min(MAX_LINE_SLICES, Math.max(1, Math.ceil(excess / tolerance)));
      }
      this.cutAtEdgeWindows(slices, this.computeEdgeWindows());
      this.lineSlices = slices;
    }
    return this.lineSlices[lineIndex];
  }

  /** Forgets the cuts at the edge windows; they are made again with the line slices. */
  private void clearLineCuts() {
    this.edgeWindows = null;
    this.lineCuts = null;
    this.lineCutAlongX = null;
    this.pieceWindows = null;
    this.lineSliceTotal = -1;
  }

  private transient int lineSliceTotal = -1;

  /**
   * Cuts every outline line that an edge window touches at the window borders, and at the borders
   * of its equal slices, and records which pieces lie inside which windows. Updates slices to the
   * new piece counts.
   */
  private void cutAtEdgeWindows(int[] slices, EdgeWindow[] windows) {
    this.edgeWindows = windows;
    this.lineCuts = new int[slices.length][];
    this.lineCutAlongX = new boolean[slices.length];
    if (windows.length == 0) {
      this.pieceWindows = null;
      return;
    }
    List<int[]> pieces = new ArrayList<>();
    for (int i = 0; i < slices.length; i++) {
      TileShape base = this.lineShape(i, HALF_WIDTH);
      IntBox box = base.boundingBox();
      boolean alongX = box.width() >= box.height();
      int low = alongX ? box.ll.x : box.ll.y;
      int high = alongX ? box.ur.x : box.ur.y;
      TreeSet<Integer> cuts = new TreeSet<>();
      for (EdgeWindow window : windows) {
        if (!window.box().intersects(box)
            || base.intersectionWithSimplify(window.box()).dimension() < 2) {
          continue;
        }
        cuts.add(alongX ? window.box().ll.x : window.box().ll.y);
        cuts.add(alongX ? window.box().ur.x : window.box().ur.y);
      }
      cuts.removeIf(c -> c <= low || c >= high);
      if (cuts.isEmpty()) {
        for (int k = 0; k < slices[i]; k++) {
          pieces.add(null);
        }
        continue;
      }
      long extent = (long) high - low;
      for (int k = 1; k < slices[i]; k++) {
        cuts.add((int) (low + extent * k / slices[i]));
      }
      int[] cutArray = cuts.stream().mapToInt(Integer::intValue).toArray();
      this.lineCuts[i] = cutArray;
      this.lineCutAlongX[i] = alongX;
      slices[i] = cutArray.length + 1;
      for (int k = 0; k <= cutArray.length; k++) {
        int from = k == 0 ? low : cutArray[k - 1];
        int to = k == cutArray.length ? high : cutArray[k];
        IntBox slab =
            alongX
                ? new IntBox(from, box.ll.y, to, box.ur.y)
                : new IntBox(box.ll.x, from, box.ur.x, to);
        TileShape piece = base.intersectionWithSimplify(slab);
        List<Integer> inside = new ArrayList<>();
        if (piece != null && !piece.isEmpty() && piece.dimension() >= 2) {
          IntBox pieceBox = piece.boundingBox();
          for (int w = 0; w < windows.length; w++) {
            if (windows[w].box().contains(pieceBox)) {
              inside.add(w);
            }
          }
        }
        pieces.add(inside.isEmpty() ? null : inside.stream().mapToInt(Integer::intValue).toArray());
      }
    }
    this.pieceWindows = pieces.toArray(new int[0][]);
  }

  /**
   * Returns the tree shapes of the outline line with the given index, cut from lineShape, the line
   * widened for one search tree. A line next to an edge pin is cut at the borders of the pin's
   * window, and each piece reaches beyond its cuts by the widening, so that the pieces outside the
   * window keep their clearance into it. Other lines are cut by {@link TileShape#splitIntoSlices}.
   * Returns exactly {@link #lineSlices} shapes.
   */
  public TileShape[] splitLine(int lineIndex, TileShape lineShape, int widening) {
    int count = this.lineSlices(lineIndex);
    int[] cuts = this.lineCuts == null ? null : this.lineCuts[lineIndex];
    if (cuts == null) {
      return lineShape.splitIntoSlices(count);
    }
    boolean alongX = this.lineCutAlongX[lineIndex];
    IntBox box = lineShape.boundingBox();
    int low = alongX ? box.ll.x : box.ll.y;
    int high = alongX ? box.ur.x : box.ur.y;
    TileShape[] result = new TileShape[count];
    for (int k = 0; k < count; k++) {
      int from = k == 0 ? low : Math.max(low, cuts[k - 1] - widening);
      int to = k == cuts.length ? high : Math.min(high, cuts[k] + widening);
      IntBox slab =
          alongX
              ? new IntBox(from, box.ll.y, to, box.ur.y)
              : new IntBox(box.ll.x, from, box.ur.x, to);
      TileShape piece = to > from ? lineShape.intersectionWithSimplify(slab) : null;
      result[k] = piece == null || piece.isEmpty() || piece.dimension() < 2 ? lineShape : piece;
    }
    return result;
  }

  /**
   * The windows next to the edge pins: per pin that touches or crosses the outline and per layer of
   * the pin, its bounding box widened by the clearance to the outline and the trace half width of
   * its nets. Empty with {@link #keepoutOutsideOutlineGenerated}.
   */
  private EdgeWindow[] computeEdgeWindows() {
    if (this.board == null || this.keepoutOutsideOutline || this.board.rules == null) {
      return new EdgeWindow[0];
    }
    List<EdgeWindow> result = new ArrayList<>();
    for (Pin pin : this.board.getPins()) {
      int[] nets = Arrays.stream(pin.netNumbers).filter(n -> n > 0).toArray();
      if (nets.length == 0 || !this.isEdgePin(pin)) {
        continue;
      }
      for (int layer = pin.firstLayer(); layer <= pin.lastLayer(); layer++) {
        Shape shape = pin.getShape(layer - pin.firstLayer());
        if (shape == null || shape.isEmpty() || !shape.isBounded()) {
          continue;
        }
        int margin = 0;
        for (int net : nets) {
          int traceClass = this.board.rules.nets.get(net).getNetClass().getTraceClearanceClass();
          int clearance =
              this.board.rules.clearanceMatrix.getValue(
                  this.clearanceClassIndex(), traceClass, layer, false);
          margin =
              Math.max(
                  margin, clearance + this.board.rules.getTraceHalfWidth(net, layer) + HALF_WIDTH);
        }
        result.add(new EdgeWindow(shape.boundingBox().offset(margin), layer, nets));
      }
    }
    return result.toArray(new EdgeWindow[0]);
  }

  /** Whether the copper of the pin touches or crosses the outline. */
  public boolean isEdgePin(Pin pin) {
    Point center = pin.getCenter();
    if (center != null && !this.contains(center)) {
      return true;
    }
    for (int layer = pin.firstLayer(); layer <= pin.lastLayer(); layer++) {
      Shape shape = pin.getShape(layer - pin.firstLayer());
      if (shape instanceof TileShape tileShape) {
        for (int c = 0; c < tileShape.borderLineCount(); c++) {
          if (!this.contains(tileShape.corner(c))) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean sameWindows(EdgeWindow[] a, EdgeWindow[] b) {
    if (a == null || b == null || a.length != b.length) {
      return a == b;
    }
    for (int i = 0; i < a.length; i++) {
      if (a[i].layer() != b[i].layer()
          || !a[i].box().ll.equals(b[i].box().ll)
          || !a[i].box().ur.equals(b[i].box().ur)
          || !Arrays.equals(a[i].nets(), b[i].nets())) {
        return false;
      }
    }
    return true;
  }

  /** The tolerance of the current slices, chosen from the trace widths when they are first cut. */
  private int sliceTolerance() {
    if (this.sliceTolerance == 0) {
      this.sliceTolerance = currentSliceTolerance();
    }
    return this.sliceTolerance;
  }

  private int currentSliceTolerance() {
    return Math.max(HALF_WIDTH, this.board.rules.getMinNetClassTraceHalfWidth());
  }

  /**
   * Cuts the tree shapes of this outline again if the smallest trace half width of the board, net
   * classes included, changed since they were cut, and reinserts the outline into the search trees.
   * The outline is inserted before the net classes are read, and a rules file read after the board
   * or a trace width set in the GUI can lower that width too; the slices cut for the former width
   * would then let their bounding octagons reach further into the board than a trace of the new
   * width can pass. The slice count may not change while the outline is in the trees, since {@link
   * #shapeLayer} derives the layer of a tree shape from it.
   */
  public void updateSlicesToTraceWidths() {
    if (this.lineSlices == null || this.sliceTolerance == 0) {
      return;
    }
    if (this.sliceTolerance == currentSliceTolerance()
        && sameWindows(this.edgeWindows, this.computeEdgeWindows())) {
      return;
    }
    boolean inTrees = this.isOnTheBoard() && this.board.searchTreeManager != null;
    if (inTrees) {
      this.board.additionalUpdateAfterChange(this);
      this.board.searchTreeManager.remove(this);
    }
    this.keepoutTiles = null;
    this.lineSlices = null;
    clearLineCuts();
    this.sliceTolerance = 0;
    this.clearDerivedData();
    if (inTrees) {
      this.board.searchTreeManager.insert(this);
      this.board.additionalUpdateAfterChange(this);
    }
  }

  /** Returns the number of line slices of all outline polygons on one layer. */
  public int lineSliceCount() {
    if (this.lineSlices == null || this.lineSliceTotal < 0) {
      int result = 0;
      for (int i = 0; i < this.lineCount(); i++) {
        result += this.lineSlices(i);
      }
      this.lineSliceTotal = result;
    }
    return this.lineSliceTotal;
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
