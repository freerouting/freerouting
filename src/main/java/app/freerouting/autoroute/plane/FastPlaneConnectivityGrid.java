package app.freerouting.autoroute.plane;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.DrillItem;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.ObstacleArea;
import app.freerouting.board.model.items.PadPlaneConnection;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.structure.BoardOutline;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.board.searchtree.SearchTreeObject;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.TileShape;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * High-performance, constant-memory 2D occupancy grid for evaluating copper pour topological
 * connectivity and detecting zone fracturing in sub-5ms time.
 *
 * <p>Unlike expensive Constructive Solid Geometry (CSG) polygon clipping (which allocates thousands
 * of {@code java.awt.geom.Area} objects and performs \(O(N \cdot V^2)\) clipping), this class uses
 * a bounded, flat primitive {@code long[]} bitset buffer (~31 KB RAM) with zero heap garbage
 * collection overhead.
 */
public class FastPlaneConnectivityGrid {

  /** Maximum dimension of the grid in either axis (512 * 512 = 256K cells). */
  public static final int MAX_GRID_DIM = 512;

  /** Minimum cell size in internal board units (e.g. 10 um at 10 units/um resolution). */
  public static final int MIN_CELL_SIZE = 100;

  /** Default cell size in internal board units (e.g. 100 um at 10 units/um resolution). */
  public static final int DEFAULT_CELL_SIZE = 1_000;

  private int originX;
  private int originY;
  private int cellSize;
  private int cols;
  private int rows;
  private int wordsPerRow;

  private long[] copperMask;
  private long[] obstacleMask;
  private long[] visitedMask;
  private short[] cellIslandMap;

  private int[] queue;

  /**
   * The number of points per cell and axis at which the obstacles are sampled ({@link #fineMask}).
   */
  private static final int FINE = 8;

  /**
   * The obstacles at {@link #FINE} times the resolution of the cells, when the fill rules of the
   * design are known: a cell is then free of fill only if all its sample points are. A passage
   * narrower than a cell is kept this way.
   */
  private long[] fineMask = new long[0];

  private boolean[] partialCell = new boolean[0];

  private int fineCols;
  private int fineRows;
  private int fineWordsPerRow;

  /** Result container holding partitioned same-net items per isolated copper island. */
  public static class PlaneFractureResult {
    /** Whether the conduction area has fractured into multiple disconnected islands with pins. */
    public final boolean isFractured;

    /** Map of island ID (0-based) to list of same-net items residing in that island. */
    public final Map<Integer, List<Item>> islandItems;

    /** Total count of distinct islands containing electrical contact items. */
    public final int islandCount;

    /**
     * The box outside of which nothing on the layer of the area changes this result, or null when
     * it is not known: the result then holds for one revision of the board only.
     */
    public final IntBox dependencyBox;

    /**
     * Creates a new fracture result.
     *
     * @param islandItems mapping from island ID to items in that island
     */
    public PlaneFractureResult(Map<Integer, List<Item>> islandItems) {
      this(islandItems, null);
    }

    /**
     * Creates a new fracture result that holds as long as nothing changes inside the box.
     *
     * @param islandItems mapping from island ID to items in that island
     * @param dependencyBox the box on the layer of the area that the result depends on, or null
     */
    public PlaneFractureResult(Map<Integer, List<Item>> islandItems, IntBox dependencyBox) {
      this.islandItems = islandItems != null ? islandItems : Collections.emptyMap();
      this.islandCount = this.islandItems.size();
      this.isFractured = this.islandCount > 1;
      this.dependencyBox = dependencyBox;
    }
  }

  /** Creates a new uninitialized connectivity grid. */
  public FastPlaneConnectivityGrid() {
    this.copperMask = new long[0];
    this.obstacleMask = new long[0];
    this.visitedMask = new long[0];
    this.cellIslandMap = new short[0];
    this.queue = new int[0];
  }

  /**
   * Initializes the grid geometry for the given bounding box and desired cell size.
   *
   * @param bbox the bounding box of the conduction area
   * @param requestedCellSize desired cell resolution in board units
   */
  public void init(IntBox bbox, int requestedCellSize) {
    if (bbox == null) {
      throw new IllegalArgumentException("Bounding box must not be null");
    }
    this.originX = bbox.ll.x;
    this.originY = bbox.ll.y;

    int width = Math.max(1, bbox.ur.x - bbox.ll.x);
    int height = Math.max(1, bbox.ur.y - bbox.ll.y);

    int maxAllowedCell = Math.max(1, Math.min(width, height) / 2);
    int calculatedCellSize = Math.max(1, Math.min(requestedCellSize, maxAllowedCell));
    int c = (width + calculatedCellSize - 1) / calculatedCellSize + 1;
    int r = (height + calculatedCellSize - 1) / calculatedCellSize + 1;

    // Scale cell size up if grid would exceed MAX_GRID_DIM
    if (c > MAX_GRID_DIM || r > MAX_GRID_DIM) {
      double maxDim = Math.max(width, height);
      calculatedCellSize = (int) Math.ceil(maxDim / (MAX_GRID_DIM - 2));
      c = (width + calculatedCellSize - 1) / calculatedCellSize + 1;
      r = (height + calculatedCellSize - 1) / calculatedCellSize + 1;
    }

    this.cellSize = calculatedCellSize;
    this.cols = Math.min(MAX_GRID_DIM, c);
    this.rows = Math.min(MAX_GRID_DIM, r);
    this.wordsPerRow = (this.cols + 63) / 64;

    int totalWords = this.rows * this.wordsPerRow;
    if (this.copperMask.length < totalWords) {
      this.copperMask = new long[totalWords];
      this.obstacleMask = new long[totalWords];
      this.visitedMask = new long[totalWords];
    } else {
      Arrays.fill(this.copperMask, 0, totalWords, 0L);
      Arrays.fill(this.obstacleMask, 0, totalWords, 0L);
      Arrays.fill(this.visitedMask, 0, totalWords, 0L);
    }

    int totalCells = this.cols * this.rows;
    if (this.cellIslandMap.length < totalCells) {
      this.cellIslandMap = new short[totalCells];
    }
    Arrays.fill(this.cellIslandMap, 0, totalCells, (short) -1);

    int maxQueueSize = this.cols * this.rows;
    if (this.queue.length < maxQueueSize) {
      this.queue = new int[maxQueueSize];
    }
  }

  /**
   * Evaluates the topological connectivity of a conduction area against foreign routing obstacles.
   *
   * @param ca the conduction area to inspect
   * @param board the routing board context
   * @param sameNetItems same-net items (pins, vias) to verify for unbroken plane connectivity
   * @return analysis result detailing whether the plane is fractured and grouping items by island
   */
  public PlaneFractureResult evaluateFracture(
      ConductionArea ca, BasicBoard board, Collection<? extends Item> sameNetItems) {
    if (ca == null || board == null || sameNetItems == null || sameNetItems.isEmpty()) {
      return new PlaneFractureResult(Collections.emptyMap());
    }

    if (sameNetItems.size() <= 1) {
      Map<Integer, List<Item>> singleIsland = new HashMap<>();
      singleIsland.put(0, new ArrayList<>(sameNetItems));
      return new PlaneFractureResult(singleIsland);
    }

    IntBox bbox = ca.boundingBox();
    int netClassNo = ca.netCount() > 0 ? ca.getNetNumber(0) : 0;
    int clearanceClass = ca.clearanceClassIndex();
    int layer = ca.getLayer();

    double baseClearance = board.clearanceValue(clearanceClass, clearanceClass, layer);
    if (baseClearance <= 0) {
      baseClearance = DEFAULT_CELL_SIZE;
    }
    double maxClearance = baseClearance;
    if (board.rules != null && board.rules.clearanceMatrix != null) {
      maxClearance =
          Math.max(maxClearance, board.rules.clearanceMatrix.maxValue(clearanceClass, layer));
    }
    // the fill of the zone may keep further away than the clearance classes say
    int clearanceInflate = (int) Math.ceil(ca.fillKeepout(maxClearance));
    IntBox queryBbox =
        new IntBox(
            new IntPoint(bbox.ll.x - clearanceInflate, bbox.ll.y - clearanceInflate),
            new IntPoint(bbox.ur.x + clearanceInflate, bbox.ur.y + clearanceInflate));

    int maxDim = Math.max(bbox.width(), bbox.height());
    int desiredCell = (int) Math.max(MIN_CELL_SIZE, Math.round(baseClearance / 2.0));
    if (maxDim > 0 && maxDim / 64 < desiredCell) {
      desiredCell = Math.max(MIN_CELL_SIZE, maxDim / 64);
    }
    init(bbox, desiredCell);

    // 1. Rasterize conduction area boundary
    rasterizeConductionArea(ca);

    // 2. Query spatial index for obstacles overlapping conduction area
    Set<SearchTreeObject> overlaps = board.overlappingObjects(queryBbox, layer);
    boolean hasForeignObstacles = false;
    // With the fill rules of the design, a cell stands for the centre of a disc of fill as wide as
    // the minimum width: it is free when that centre keeps its distance to the foreign copper,
    // without the margin of half a cell that would close the narrow passages the fill keeps.
    boolean exact = ca.getPadConnection() != null;
    if (exact) {
      initFineMask();
    }
    for (SearchTreeObject ob : overlaps) {
      if (!(ob instanceof Item item) || item == ca || !item.sharesLayer(ca)) {
        continue;
      }

      // Foreign traces, vias, and blocking obstacles are obstacles
      boolean isForeign = !item.sharesNet(ca);
      if (isForeign) {
        if (item instanceof ConductionArea foreignCa && !foreignCa.getIsObstacle()) {
          continue;
        }
        hasForeignObstacles = true;
        int itemClearanceClass = item.clearanceClassIndex();
        double clearance =
            ca.fillKeepout(board.clearanceValue(clearanceClass, itemClearanceClass, layer));

        if (exact) {
          double leniency = FILL_LENIENCY * ca.getFillMinWidth();
          if (item instanceof BoardOutline outline && ca.getFillEdgeClearance() > 0) {
            // the fill keeps its own distance to the edge of the board
            rasterizeOutline(
                outline, ca.getFillEdgeClearance() + ca.getFillMinWidth() / 2.0 - leniency);
          } else {
            rasterizeForeignItem(item, layer, clearance - leniency);
          }
        } else if (item instanceof Trace trace) {
          int radius = (int) Math.round(trace.getHalfWidth() + clearance);
          if (trace instanceof PolylineTrace polylineTrace) {
            Point[] corners = polylineTrace.polyline().corners();
            for (int i = 0; i < corners.length - 1; i++) {
              IntPoint p1 = toIntPoint(corners[i]);
              IntPoint p2 = toIntPoint(corners[i + 1]);
              rasterizeThickSegment(p1.x, p1.y, p2.x, p2.y, radius);
            }
          } else {
            IntPoint p1 = toIntPoint(trace.firstCorner());
            IntPoint p2 = toIntPoint(trace.lastCorner());
            rasterizeThickSegment(p1.x, p1.y, p2.x, p2.y, radius);
          }
        } else if (item instanceof DrillItem drillItem) {
          IntPoint center = toIntPoint(drillItem.getCenter());
          IntBox itemBbox = drillItem.boundingBox();
          int radius =
              (int) Math.round(Math.max(itemBbox.width(), itemBbox.height()) / 2.0 + clearance);
          rasterizeThickSegment(center.x, center.y, center.x, center.y, radius);
        } else if (item instanceof ObstacleArea) {
          int shapeCount = item.tileShapeCount();
          for (int i = 0; i < shapeCount; i++) {
            if (item.shapeLayer(i) == layer) {
              TileShape tileShape = item.getTileShape(i);
              if (tileShape != null) {
                double dilatedClearance = clearance + (cellSize + 1.0) / 2.0;
                Shape enlargedShape = tileShape.enlarge(dilatedClearance);
                rasterizeObstacleShape(enlargedShape);
              }
            }
          }
        }
      }
    }

    if (ca.getPadConnection() != null) {
      // The design says how each pad is connected to the fill: a pad can be cut off the plane
      // without any foreign copper cutting the plane itself.
      return partitionItemsByPadConnection(ca, board, sameNetItems);
    }

    if (!hasForeignObstacles) {
      Map<Integer, List<Item>> singleIsland = new HashMap<>();
      singleIsland.put(0, new ArrayList<>(sameNetItems));
      return new PlaneFractureResult(singleIsland);
    }

    // 3. Partition same-net items into connected islands using bitwise BFS
    return partitionItemsByIsland(ca, sameNetItems);
  }

  /**
   * Partitions the items of the net when the design says how the pads are connected to the fill
   * ({@link ConductionArea#setPadConnectionRules}). The grid gives the pieces of the fill. A pad is
   * then attached to them the way the zone filler of KiCad connects it:
   *
   * <ul>
   *   <li>solid connection: to every piece that reaches the copper of the pad;
   *   <li>thermal relief: through each spoke that foreign copper, kept at the clearance of the
   *       fill, leaves at least as wide as the minimum width of the fill, to the piece its far end
   *       lies in. A spoke whose end lies in the relief of a neighbouring pad of the net is kept
   *       when the spoke of that pad ends in it in turn: the two pads are then connected to each
   *       other. One spoke is enough for a connection; a pad with fewer spokes than the design
   *       rules ask for is a rule violation, not an open;
   *   <li>no connection: to no piece.
   * </ul>
   *
   * <p>The fill stops at the thermal gap of a pad with a thermal relief and at the clearance of a
   * pad it does not connect, as it stops at foreign copper, and at its own distance from the edge
   * of the board. A pad attached to nothing is alone on an island; a pad attached to two pieces
   * joins them. A via, a trace or a pad without a known connection is attached to every piece whose
   * copper reaches it, a trace along its whole length, and to none where the fill leaves it out.
   */
  private PlaneFractureResult partitionItemsByPadConnection(
      ConductionArea ca, BasicBoard board, Collection<? extends Item> sameNetItems) {
    int layer = ca.getLayer();
    List<Item> items = new ArrayList<>(sameNetItems);
    Map<Item, Integer> indexOfItem = new HashMap<>();
    for (int i = 0; i < items.size(); i++) {
      indexOfItem.put(items.get(i), i);
    }

    double minWidth = ca.getFillMinWidth();
    // the far end of a spoke lies 5 um beyond the relief and half the minimum width (the maximum
    // error of the arc approximation of KiCad, at its default)
    double endSlack = board.communication.getResolution(Unit.UM) * 5.0;
    PadPlaneConnection.Mode[] modes = new PadPlaneConnection.Mode[items.size()];
    PadPlaneConnection.Spoke[][] spokes = new PadPlaneConnection.Spoke[items.size()][];
    double maxGap = ca.getThermalGap();
    for (int i = 0; i < items.size(); i++) {
      if (items.get(i) instanceof Pin pin && pin.getPlaneConnection() != null) {
        PadPlaneConnection connection = pin.getPlaneConnection();
        modes[i] = connection.resolve(ca.getPadConnection());
        if (modes[i] == PadPlaneConnection.Mode.THERMAL) {
          spokes[i] =
              connection.spokes(ca.getThermalGap(), ca.getThermalSpokeWidth(), minWidth, endSlack);
          maxGap = Math.max(maxGap, connection.gapOn(ca.getThermalGap()));
        }
      }
    }
    double maxKeepout = ca.fillKeepout(maxClearanceOf(ca, board));

    // The fill stops at the thermal gap of a pad of the net, and at the clearance of a pad it does
    // not connect: a row of such pads can close a passage as foreign copper does.
    for (int i = 0; i < items.size(); i++) {
      if (modes[i] != PadPlaneConnection.Mode.THERMAL && modes[i] != PadPlaneConnection.Mode.NONE) {
        continue;
      }
      Pin pin = (Pin) items.get(i);
      TileShape padShape = pin.getTreeShapeOnLayer(board.searchTreeManager.getDefaultTree(), layer);
      if (padShape == null) {
        continue;
      }
      double relief =
          modes[i] == PadPlaneConnection.Mode.THERMAL
              ? pin.getPlaneConnection().gapOn(ca.getThermalGap()) + minWidth / 2.0
              : ca.fillKeepout(
                  board.clearanceValue(ca.clearanceClassIndex(), pin.clearanceClassIndex(), layer));
      if (!rasterizeRoundPad(pin, layer, relief - FILL_LENIENCY * minWidth)) {
        rasterizeKeepout(padShape, relief - FILL_LENIENCY * minWidth);
      }
    }

    // The fill keeps half its minimum width inside the outline of the zone, as from any copper.
    rasterizeOutsideOf(ca.getArea(), minWidth / 2.0 - FILL_LENIENCY * minWidth);

    foldFineMask();
    int[] pieceOfCell = new int[cols * rows];
    int pieceCount = labelPieces(pieceOfCell).length;
    // nodes: the pieces of the fill, then the items
    int[] parent = new int[pieceCount + items.size()];
    for (int i = 0; i < parent.length; i++) {
      parent[i] = i;
    }
    for (int i = 0; i < items.size(); i++) {
      Item item = items.get(i);
      int node = pieceCount + i;
      if (modes[i] == PadPlaneConnection.Mode.NONE) {
        continue;
      }
      if (modes[i] == PadPlaneConnection.Mode.SOLID) {
        attachSolidPad(ca, (Pin) item, board, maxKeepout, pieceOfCell, parent, node);
        continue;
      }
      if (modes[i] == PadPlaneConnection.Mode.THERMAL) {
        attachThermalPad(
            ca,
            board,
            items,
            indexOfItem,
            modes,
            spokes,
            i,
            maxGap,
            maxKeepout,
            pieceOfCell,
            parent,
            pieceCount);
        continue;
      }
      // A via or a trace is covered by the fill: it is attached to every piece whose copper
      // reaches it, and to none if the fill leaves it out.
      if (item instanceof Trace trace) {
        attachTrace(trace, ca, minWidth, pieceOfCell, parent, node);
      } else if (item instanceof DrillItem drillItem) {
        IntBox box = drillItem.boundingBox();
        TileShape shape =
            drillItem.getTreeShapeOnLayer(board.searchTreeManager.getDefaultTree(), layer);
        if (shape != null) {
          box = shape.boundingBox();
        }
        double reach = Math.min(box.width(), box.height()) / 2.0 + minWidth / 2.0;
        attachPiecesNear(drillItem.getCenter().toFloat(), reach, pieceOfCell, parent, node);
      }
    }

    // islands: the groups of items, the largest first, then in the order of the items
    Map<Integer, List<Item>> groups = new java.util.LinkedHashMap<>();
    for (int i = 0; i < items.size(); i++) {
      groups
          .computeIfAbsent(find(parent, pieceCount + i), k -> new ArrayList<>())
          .add(items.get(i));
    }
    List<List<Item>> ordered = new ArrayList<>(groups.values());
    int largest = 0;
    for (int i = 1; i < ordered.size(); i++) {
      if (ordered.get(i).size() > ordered.get(largest).size()) {
        largest = i;
      }
    }
    Map<Integer, List<Item>> islands = new HashMap<>();
    int nextId = 0;
    if (!ordered.isEmpty()) {
      islands.put(nextId++, ordered.get(largest));
    }
    for (int i = 0; i < ordered.size(); i++) {
      if (i != largest) {
        islands.put(nextId++, ordered.get(i));
      }
    }
    // Everything read above lies within the area, its foreign copper and the reliefs and spokes of
    // the pads of the net, with the copper around them: the result holds while nothing changes
    // there.
    int reach =
        (int) Math.ceil(2.0 * maxKeepout + 2.0 * maxGap + 2.0 * minWidth + 2.0 * cellSize) + 1;
    IntBox box = ca.boundingBox();
    for (Item item : items) {
      box = box.union(item.boundingBox());
    }
    IntBox dependencyBox =
        new IntBox(
            new IntPoint(box.ll.x - reach, box.ll.y - reach),
            new IntPoint(box.ur.x + reach, box.ur.y + reach));
    return new PlaneFractureResult(islands, dependencyBox);
  }

  /** The largest clearance the fill of the area keeps to any item on its layer. */
  private static double maxClearanceOf(ConductionArea ca, BasicBoard board) {
    int clearanceClass = ca.clearanceClassIndex();
    double result = board.clearanceValue(clearanceClass, clearanceClass, ca.getLayer());
    if (board.rules != null && board.rules.clearanceMatrix != null) {
      result =
          Math.max(result, board.rules.clearanceMatrix.maxValue(clearanceClass, ca.getLayer()));
    }
    return result;
  }

  /**
   * Numbers the connected pieces of the fill: writes the piece of each conductive cell into
   * pieceOfCell (-1 elsewhere) and returns the number of cells of each piece.
   */
  private int[] labelPieces(int[] pieceOfCell) {
    // -1: fill not yet numbered, -2: no fill
    for (int row = 0; row < rows; row++) {
      for (int col = 0; col < cols; col++) {
        pieceOfCell[row * cols + col] = isConductive(col, row) ? -1 : -2;
      }
    }
    // cells none of whose sample points is marked: two of them that are neighbours always touch
    boolean[] partial = partialCells();
    List<Integer> sizes = new ArrayList<>();
    // diagonal neighbours too: what separates two pieces is much thicker than a cell
    int[] dc = {1, -1, 0, 0, 1, 1, -1, -1};
    int[] dr = {0, 0, 1, -1, 1, -1, 1, -1};
    for (int start = 0; start < cols * rows; start++) {
      if (pieceOfCell[start] != -1) {
        continue;
      }
      int piece = sizes.size();
      int head = 0;
      int tail = 0;
      queue[tail++] = start;
      pieceOfCell[start] = piece;
      while (head < tail) {
        int cell = queue[head++];
        int r = cell / cols;
        int c = cell - r * cols;
        for (int i = 0; i < dc.length; i++) {
          int nc = c + dc[i];
          int nr = r + dr[i];
          if (nc < 0 || nc >= cols || nr < 0 || nr >= rows) {
            continue;
          }
          int neighbour = nr * cols + nc;
          if (pieceOfCell[neighbour] == -1
              && ((!partial[cell] && !partial[neighbour]) || cellsTouch(r, c, dr[i], dc[i]))) {
            pieceOfCell[neighbour] = piece;
            queue[tail++] = neighbour;
          }
        }
      }
      sizes.add(tail);
    }
    for (int cell = 0; cell < cols * rows; cell++) {
      if (pieceOfCell[cell] == -2) {
        pieceOfCell[cell] = -1;
      }
    }
    int[] result = new int[sizes.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = sizes.get(i);
    }
    return result;
  }

  /** For each cell, whether one of its sample points is marked free of fill. */
  private boolean[] partialCells() {
    if (partialCell.length < cols * rows) {
      partialCell = new boolean[cols * rows];
    } else {
      Arrays.fill(partialCell, 0, cols * rows, false);
    }
    for (int row = 0; row < rows; row++) {
      for (int word = 0; word < fineWordsPerRow; word++) {
        long any = 0L;
        for (int k = 0; k < FINE; k++) {
          any |= fineMask[(FINE * row + k) * fineWordsPerRow + word];
        }
        if (any == 0L) {
          continue;
        }
        for (int col = word * 64 / FINE; col < (word + 1) * 64 / FINE && col < cols; col++) {
          if ((any & (((1L << FINE) - 1) << ((col * FINE) & 63))) != 0L) {
            partialCell[row * cols + col] = true;
          }
        }
      }
    }
    return partialCell;
  }

  /**
   * Whether the fill passes from a cell to its neighbour in the direction (dr, dc): a free sample
   * point of the cell next to a free sample point of the neighbour, across their common border. Two
   * cells that each hold a free point, with no free point where they meet, are on the two sides of
   * a passage that the fill does not cross.
   */
  private boolean cellsTouch(int row, int col, int dr, int dc) {
    if (dr != 0 && dc != 0) {
      int fineRow = dr > 0 ? FINE * row + FINE - 1 : FINE * row;
      int fineCol = dc > 0 ? FINE * col + FINE - 1 : FINE * col;
      return !isFineSet(fineRow, fineCol) && !isFineSet(fineRow + dr, fineCol + dc);
    }
    for (int k = 0; k < FINE; k++) {
      // the sample point of the cell on the border, and the three of the neighbour next to it
      int fineRow = dr == 0 ? FINE * row + k : (dr > 0 ? FINE * row + FINE - 1 : FINE * row);
      int fineCol = dc == 0 ? FINE * col + k : (dc > 0 ? FINE * col + FINE - 1 : FINE * col);
      if (isFineSet(fineRow, fineCol)) {
        continue;
      }
      for (int m = -1; m <= 1; m++) {
        int otherRow = dr == 0 ? fineRow + m : fineRow + dr;
        int otherCol = dc == 0 ? fineCol + m : fineCol + dc;
        // only the points of the neighbour itself
        if (dr == 0 && (otherRow < FINE * row || otherRow >= FINE * row + FINE)) {
          continue;
        }
        if (dc == 0 && (otherCol < FINE * col || otherCol >= FINE * col + FINE)) {
          continue;
        }
        if (!isFineSet(otherRow, otherCol)) {
          return true;
        }
      }
    }
    return false;
  }

  private static int find(int[] parent, int node) {
    while (parent[node] != node) {
      parent[node] = parent[parent[node]];
      node = parent[node];
    }
    return node;
  }

  private static void union(int[] parent, int a, int b) {
    int rootA = find(parent, a);
    int rootB = find(parent, b);
    if (rootA != rootB) {
      // the smaller index is the root, so that the result does not depend on the order of calls
      parent[Math.max(rootA, rootB)] = Math.min(rootA, rootB);
    }
  }

  /**
   * Attaches a solidly connected pad to every piece of the fill that reaches its copper. A cell of
   * fill is free of foreign copper over a disc of half a cell around its centre; it reaches the pad
   * when its centre is no further from the pad than the half diagonal of a cell.
   *
   * <p>Around a small pad between close neighbours the cells are too coarse to tell. When no cell
   * reaches the pad, the border of the pad is walked instead: the fill touches the pad where a disc
   * as wide as the minimum width of the fill fits against the border, at the clearance of the fill
   * from any foreign copper. The pad is then attached to the piece of the nearest cell of fill.
   */
  private void attachSolidPad(
      ConductionArea ca,
      Pin pin,
      BasicBoard board,
      double maxKeepout,
      int[] pieceOfCell,
      int[] parent,
      int node) {
    int layer = ca.getLayer();
    TileShape padShape = pin.getTreeShapeOnLayer(board.searchTreeManager.getDefaultTree(), layer);
    if (padShape == null) {
      return;
    }
    double reach = cellSize * Math.sqrt(0.5) + 1.0;
    IntBox box = padShape.boundingBox();
    int startCol = Math.max(0, (box.ll.x - originX) / cellSize - 1);
    int endCol = Math.min(cols - 1, (box.ur.x - originX) / cellSize + 1);
    int startRow = Math.max(0, (box.ll.y - originY) / cellSize - 1);
    int endRow = Math.min(rows - 1, (box.ur.y - originY) / cellSize + 1);
    boolean attached = false;
    for (int row = startRow; row <= endRow; row++) {
      for (int col = startCol; col <= endCol; col++) {
        int piece = pieceOfCell[row * cols + col];
        if (piece < 0) {
          continue;
        }
        if (find(parent, piece) == find(parent, node)) {
          attached = true;
          continue;
        }
        FloatPoint centre =
            new FloatPoint(
                originX + col * cellSize + cellSize / 2.0,
                originY + row * cellSize + cellSize / 2.0);
        if (padShape.distance(centre) <= reach) {
          union(parent, node, piece);
          attached = true;
        }
      }
    }
    if (attached) {
      return;
    }

    double minWidth = ca.getFillMinWidth();
    double margin = maxKeepout + minWidth + 1.0;
    IntBox query =
        new IntBox(
            new IntPoint((int) Math.floor(box.ll.x - margin), (int) Math.floor(box.ll.y - margin)),
            new IntPoint((int) Math.ceil(box.ur.x + margin), (int) Math.ceil(box.ur.y + margin)));
    List<TileShape> foreignShapes = new ArrayList<>();
    List<double[]> foreignClearances = new ArrayList<>();
    collectForeignCopper(ca, board, query, foreignShapes, foreignClearances, null, null, null, -1);
    FloatPoint[] corners = padShape.cornerApproxArr();
    if (corners == null || corners.length < 2) {
      return;
    }
    FloatPoint inside = padShape.centreOfGravity();
    double perimeter = 0;
    for (int i = 0; i < corners.length; i++) {
      perimeter += corners[i].distance(corners[(i + 1) % corners.length]);
    }
    double step = Math.max(Math.max(minWidth / 2.0, perimeter / 64.0), 1.0);
    for (int i = 0; i < corners.length; i++) {
      FloatPoint from = corners[i];
      FloatPoint to = corners[(i + 1) % corners.length];
      double length = from.distance(to);
      if (length <= 0) {
        continue;
      }
      // the normal of the side that points away from the pad
      double outX = -(to.y - from.y) / length;
      double outY = (to.x - from.x) / length;
      if (outX * (inside.x - from.x) + outY * (inside.y - from.y) > 0) {
        outX = -outX;
        outY = -outY;
      }
      int count = (int) Math.ceil(length / step);
      for (int k = 0; k <= count; k++) {
        double t = (double) k / count;
        FloatPoint centre =
            new FloatPoint(
                from.x + t * (to.x - from.x) + outX * minWidth / 2.0,
                from.y + t * (to.y - from.y) + outY * minWidth / 2.0);
        IntPoint point = centre.round();
        if (ca.getArea() == null
            || !ca.getArea().contains(point)
            || !isFree(centre, centre, 0, foreignShapes, foreignClearances)) {
          continue;
        }
        int[] seed =
            findNearestConductiveCell(
                (point.x - originX) / cellSize, (point.y - originY) / cellSize, 3);
        if (seed != null) {
          union(parent, node, pieceOfCell[seed[1] * cols + seed[0]]);
        }
      }
    }
  }

  /**
   * Collects the copper of other nets on the layer of the area that overlaps the box, each shape
   * with the distance the fill keeps free around it. With netPads, also the pads of the net of the
   * area that carry a thermal relief or no connection, as indices of items.
   */
  private static void collectForeignCopper(
      ConductionArea ca,
      BasicBoard board,
      IntBox query,
      List<TileShape> foreignShapes,
      List<double[]> foreignClearances,
      List<Integer> netPads,
      Map<Item, Integer> indexOfItem,
      PadPlaneConnection.Mode[] modes,
      int ownIndex) {
    int layer = ca.getLayer();
    int clearanceClass = ca.clearanceClassIndex();
    for (SearchTreeObject object : board.overlappingObjects(query, layer)) {
      if (!(object instanceof Item item) || item == ca || !item.sharesLayer(ca)) {
        continue;
      }
      if (item.sharesNet(ca)) {
        if (netPads != null) {
          Integer other = indexOfItem.get(item);
          if (other != null
              && other != ownIndex
              && (modes[other] == PadPlaneConnection.Mode.THERMAL
                  || modes[other] == PadPlaneConnection.Mode.NONE)) {
            netPads.add(other);
          }
        }
        continue;
      }
      if (item instanceof ConductionArea foreignCa && !foreignCa.getIsObstacle()) {
        continue;
      }
      if (!(item instanceof Trace || item instanceof DrillItem || item instanceof ObstacleArea)) {
        continue;
      }
      double clearance = board.clearanceValue(clearanceClass, item.clearanceClassIndex(), layer);
      double[] distances = {ca.fillKeepout(clearance)};
      for (int i = 0; i < item.tileShapeCount(); i++) {
        if (item.shapeLayer(i) == layer && item.getTileShape(i) != null) {
          foreignShapes.add(item.getTileShape(i));
          foreignClearances.add(distances);
        }
      }
    }
    if (netPads != null) {
      // the same order on every run
      netPads.sort(null);
    }
  }

  /** Attaches a pad with a thermal relief through the spokes that the fill keeps. */
  private void attachThermalPad(
      ConductionArea ca,
      BasicBoard board,
      List<Item> items,
      Map<Item, Integer> indexOfItem,
      PadPlaneConnection.Mode[] modes,
      PadPlaneConnection.Spoke[][] spokes,
      int index,
      double maxGap,
      double maxKeepout,
      int[] pieceOfCell,
      int[] parent,
      int pieceCount) {
    PadPlaneConnection.Spoke[] own = spokes[index];
    if (own == null || own.length == 0) {
      return;
    }
    int layer = ca.getLayer();
    int clearanceClass = ca.clearanceClassIndex();
    double minWidth = ca.getFillMinWidth();

    // one query for the four spokes: the foreign copper and the pads of the net around them
    double minX = Double.MAX_VALUE;
    double minY = Double.MAX_VALUE;
    double maxX = -Double.MAX_VALUE;
    double maxY = -Double.MAX_VALUE;
    for (PadPlaneConnection.Spoke spoke : own) {
      minX = Math.min(minX, Math.min(spoke.startX(), spoke.endX()));
      minY = Math.min(minY, Math.min(spoke.startY(), spoke.endY()));
      maxX = Math.max(maxX, Math.max(spoke.startX(), spoke.endX()));
      maxY = Math.max(maxY, Math.max(spoke.startY(), spoke.endY()));
    }
    double margin = Math.max(maxKeepout, maxGap) + own[0].halfWidth() + 1.0;
    IntBox query =
        new IntBox(
            new IntPoint((int) Math.floor(minX - margin), (int) Math.floor(minY - margin)),
            new IntPoint((int) Math.ceil(maxX + margin), (int) Math.ceil(maxY + margin)));
    List<TileShape> foreignShapes = new ArrayList<>();
    List<double[]> foreignClearances = new ArrayList<>();
    List<Integer> netPads = new ArrayList<>();
    collectForeignCopper(
        ca, board, query, foreignShapes, foreignClearances, netPads, indexOfItem, modes, index);

    Pin ownPin = (Pin) items.get(index);
    PadPlaneConnection.Spoke[] ownWhole = wholeSpokes(ownPin, own);
    TileShape ownShape =
        ownPin.getTreeShapeOnLayer(board.searchTreeManager.getDefaultTree(), layer);
    double ownGap = ownPin.getPlaneConnection().gapOn(ca.getThermalGap());
    for (int k = 0; k < own.length; k++) {
      PadPlaneConnection.Spoke spoke = own[k];
      FloatPoint start = new FloatPoint(spoke.startX(), spoke.startY());
      FloatPoint end = new FloatPoint(spoke.endX(), spoke.endY());
      // the relief of another pad of the net leaves no fill at the end of the spoke
      int inReliefOf = -1;
      for (int other : netPads) {
        Pin otherPin = (Pin) items.get(other);
        TileShape otherShape =
            otherPin.getTreeShapeOnLayer(board.searchTreeManager.getDefaultTree(), layer);
        if (otherShape == null) {
          continue;
        }
        double relief =
            modes[other] == PadPlaneConnection.Mode.THERMAL
                ? otherPin.getPlaneConnection().gapOn(ca.getThermalGap())
                : Math.max(
                    board.clearanceValue(clearanceClass, otherPin.clearanceClassIndex(), layer),
                    ca.getFillClearance());
        if (otherShape.distance(end) < relief) {
          inReliefOf = other;
          break;
        }
      }
      if (inReliefOf >= 0) {
        // Kept if a spoke of that pad ends in this spoke, and this one in that spoke. The two
        // spokes then join the two pads where they meet, if foreign copper leaves them that far.
        PadPlaneConnection.Spoke[] theirs = spokes[inReliefOf];
        if (theirs == null) {
          continue;
        }
        PadPlaneConnection.Spoke[] theirWhole = wholeSpokes((Pin) items.get(inReliefOf), theirs);
        for (int m = 0; m < theirs.length; m++) {
          if (isInSpoke(theirWhole[m], end.x, end.y)
              && isInSpoke(ownWhole[k], theirWhole[m].endX(), theirWhole[m].endY())) {
            if (spokesMeet(spoke, theirs[m], minWidth, foreignShapes, foreignClearances)) {
              union(parent, pieceCount + index, pieceCount + inReliefOf);
            }
            break;
          }
        }
        continue;
      }
      // the end first: it costs less to tell
      if (!isEndInFill(start, end, minWidth, ownShape, ownGap, foreignShapes, foreignClearances)
          || !isStripFree(
              start,
              end,
              spoke.halfWidth(),
              minWidth,
              ownShape,
              ownGap,
              foreignShapes,
              foreignClearances)) {
        continue;
      }
      IntPoint endPoint = end.round();
      if (ca.getArea() == null || !ca.getArea().contains(endPoint)) {
        continue;
      }
      // the piece of the fill the end lies in; a piece too small for the grid connects nothing
      attachPiecesNear(
          end, minWidth / 2.0 + FILL_LENIENCY * minWidth, pieceOfCell, parent, pieceCount + index);
    }
  }

  /**
   * Joins the node with every piece of the fill that has a free sample point no further than reach
   * from the point: the centre of a disc of fill.
   */
  private void attachPiecesNear(
      FloatPoint point, double reach, int[] pieceOfCell, int[] parent, int node) {
    double fine = (double) cellSize / FINE;
    // the sample points are that far apart
    double limit = reach + fine * 0.75;
    int startCol = Math.max(0, (int) Math.floor((point.x - limit - originX) / cellSize));
    int endCol = Math.min(cols - 1, (int) Math.floor((point.x + limit - originX) / cellSize));
    int startRow = Math.max(0, (int) Math.floor((point.y - limit - originY) / cellSize));
    int endRow = Math.min(rows - 1, (int) Math.floor((point.y + limit - originY) / cellSize));
    int root = find(parent, node);
    for (int row = startRow; row <= endRow; row++) {
      for (int col = startCol; col <= endCol; col++) {
        int piece = pieceOfCell[row * cols + col];
        // a piece the node is already joined with needs no second look
        if (piece >= 0
            && find(parent, piece) != root
            && hasFreeSampleNear(row, col, point, limit, fine)) {
          union(parent, node, piece);
          root = find(parent, node);
        }
      }
    }
  }

  /** Whether the cell has a free sample point no further than limit from the point. */
  private boolean hasFreeSampleNear(int row, int col, FloatPoint point, double limit, double fine) {
    for (int fineRow = FINE * row; fineRow < FINE * row + FINE; fineRow++) {
      double dy = originY + (fineRow + 0.5) * fine - point.y;
      for (int fineCol = FINE * col; fineCol < FINE * col + FINE; fineCol++) {
        double dx = originX + (fineCol + 0.5) * fine - point.x;
        if (dx * dx + dy * dy <= limit * limit && !isFineSet(fineRow, fineCol)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Joins the node of a trace of the net with every piece of the fill that its copper touches on
   * its way: a trace is covered by the fill wherever the fill reaches it, not only at its ends. The
   * fill reaches the trace where the centre of a disc of fill, a free sample point, is no further
   * from its centre line than its half width and half the minimum width.
   */
  private void attachTrace(
      Trace trace, ConductionArea ca, double minWidth, int[] pieceOfCell, int[] parent, int node) {
    Point[] corners =
        trace instanceof PolylineTrace polylineTrace
            ? polylineTrace.polyline().corners()
            : new Point[] {trace.firstCorner(), trace.lastCorner()};
    double fine = (double) cellSize / FINE;
    // the sample points are that far apart
    double limit = trace.getHalfWidth() + minWidth / 2.0 + fine * 0.75;
    double halfDiagonal = cellSize * Math.sqrt(0.5);
    int root = find(parent, node);
    for (int i = 0; i < Math.max(1, corners.length - 1); i++) {
      FloatPoint from = corners[i].toFloat();
      FloatPoint to = corners[Math.min(i + 1, corners.length - 1)].toFloat();
      int startCol =
          Math.max(0, (int) Math.floor((Math.min(from.x, to.x) - limit - originX) / cellSize));
      int endCol =
          Math.min(
              cols - 1, (int) Math.floor((Math.max(from.x, to.x) + limit - originX) / cellSize));
      int startRow =
          Math.max(0, (int) Math.floor((Math.min(from.y, to.y) - limit - originY) / cellSize));
      int endRow =
          Math.min(
              rows - 1, (int) Math.floor((Math.max(from.y, to.y) + limit - originY) / cellSize));
      for (int r = startRow; r <= endRow; r++) {
        for (int c = startCol; c <= endCol; c++) {
          int piece = pieceOfCell[r * cols + c];
          if (piece < 0 || find(parent, piece) == root) {
            continue;
          }
          FloatPoint centre =
              new FloatPoint(originX + (c + 0.5) * cellSize, originY + (r + 0.5) * cellSize);
          if (pointSegmentDistance(centre, from, to) > limit + halfDiagonal
              || !hasFreeSampleNearSegment(r, c, from, to, limit, fine)) {
            continue;
          }
          union(parent, node, piece);
          root = find(parent, node);
        }
      }
    }
  }

  /** Whether the cell has a free sample point no further than limit from the segment. */
  private boolean hasFreeSampleNearSegment(
      int row, int col, FloatPoint from, FloatPoint to, double limit, double fine) {
    for (int fineRow = FINE * row; fineRow < FINE * row + FINE; fineRow++) {
      double y = originY + (fineRow + 0.5) * fine;
      for (int fineCol = FINE * col; fineCol < FINE * col + FINE; fineCol++) {
        if (isFineSet(fineRow, fineCol)) {
          continue;
        }
        FloatPoint sample = new FloatPoint(originX + (fineCol + 0.5) * fine, y);
        if (pointSegmentDistance(sample, from, to) <= limit) {
          return true;
        }
      }
    }
    return false;
  }

  /** Clears the obstacles at the resolution of the sample points. */
  private void initFineMask() {
    this.fineCols = FINE * this.cols;
    this.fineRows = FINE * this.rows;
    this.fineWordsPerRow = (this.fineCols + 63) / 64;
    int words = this.fineRows * this.fineWordsPerRow;
    if (this.fineMask.length < words) {
      this.fineMask = new long[words];
    } else {
      Arrays.fill(this.fineMask, 0, words, 0L);
    }
  }

  /**
   * Marks as free of fill the sample points outside the area and those inside closer than reach to
   * its border or to one of its holes.
   */
  private void rasterizeOutsideOf(app.freerouting.geometry.planar.Area area, double reach) {
    if (area == null) {
      return;
    }
    TileShape[] tiles = area.splitToConvex();
    if (tiles == null) {
      return;
    }
    long[] inside = new long[fineRows * fineWordsPerRow];
    for (TileShape tile : tiles) {
      FloatPoint[] corners = tile == null ? null : tile.cornerApproxArr();
      if (corners != null && corners.length > 2) {
        rasterizeConvex(corners, 0.0, inside);
      }
    }
    for (int word = 0; word < inside.length; word++) {
      fineMask[word] |= ~inside[word];
    }
    if (reach <= 0) {
      return;
    }
    List<Shape> rims = new ArrayList<>();
    rims.add(area.getBorder());
    rims.addAll(Arrays.asList(area.getHoles()));
    for (Shape rim : rims) {
      if (!(rim instanceof app.freerouting.geometry.planar.PolylineShape polylineShape)) {
        continue;
      }
      FloatPoint[] corners = polylineShape.cornerApproxArr();
      for (int k = 0; corners != null && k < corners.length; k++) {
        rasterizeSegment(corners[k], corners[(k + 1) % corners.length], reach);
      }
    }
  }

  /** Marks the sample points of a row from startCol to endCol as free of fill. */
  private void setFineSpan(int row, int startCol, int endCol) {
    setFineSpan(fineMask, row, startCol, endCol);
  }

  /** Sets the bits of the sample points of a row from startCol to endCol in the mask. */
  private void setFineSpan(long[] mask, int row, int startCol, int endCol) {
    if (row < 0 || row >= fineRows) {
      return;
    }
    int from = Math.max(0, startCol);
    int to = Math.min(fineCols - 1, endCol);
    if (from > to) {
      return;
    }
    int startWord = row * fineWordsPerRow + (from >> 6);
    int endWord = row * fineWordsPerRow + (to >> 6);
    long startBits = ~0L << (from & 63);
    long endBits = ~0L >>> (63 - (to & 63));
    if (startWord == endWord) {
      mask[startWord] |= startBits & endBits;
      return;
    }
    mask[startWord] |= startBits;
    for (int word = startWord + 1; word < endWord; word++) {
      mask[word] = ~0L;
    }
    mask[endWord] |= endBits;
  }

  private boolean isFineSet(int row, int col) {
    return (fineMask[row * fineWordsPerRow + (col >> 6)] & (1L << (col & 63))) != 0L;
  }

  /** Marks as free of fill the cells whose sample points all are. */
  private void foldFineMask() {
    for (int row = 0; row < rows; row++) {
      for (int word = 0; word < fineWordsPerRow; word++) {
        long all = ~0L;
        for (int k = 0; k < FINE; k++) {
          all &= fineMask[(FINE * row + k) * fineWordsPerRow + word];
        }
        if (all == 0L) {
          continue;
        }
        for (int col = word * 64 / FINE; col < (word + 1) * 64 / FINE && col < cols; col++) {
          long cell = ((1L << FINE) - 1) << ((col * FINE) & 63);
          if ((all & cell) == cell) {
            setBit(obstacleMask, row, col);
          }
        }
      }
    }
  }

  /**
   * Marks as free of fill the sample points that are closer than distance to the copper of a
   * foreign item on the layer: to the centre line of a trace less its half width, to the shapes of
   * any other item.
   */
  private void rasterizeForeignItem(Item item, int layer, double distance) {
    if (item instanceof PolylineTrace trace) {
      double reach = trace.getHalfWidth() + distance;
      Point[] corners = trace.polyline().corners();
      for (int i = 0; i < corners.length - 1; i++) {
        rasterizeSegment(corners[i].toFloat(), corners[i + 1].toFloat(), reach);
      }
      return;
    }
    if (item instanceof DrillItem drillItem && rasterizeRoundPad(drillItem, layer, distance)) {
      return;
    }
    for (int i = 0; i < item.tileShapeCount(); i++) {
      if (item.shapeLayer(i) == layer && item.getTileShape(i) != null) {
        rasterizeKeepout(item.getTileShape(i), distance);
      }
    }
  }

  /**
   * Marks as free of fill the sample points that are closer than distance to the pad of the item on
   * the layer, if that pad is round: as a circle, not as the octagon that stands for it in the
   * search tree and reaches further out at its corners. Returns false for any other pad.
   */
  private boolean rasterizeRoundPad(DrillItem drillItem, int layer, double distance) {
    if (drillItem.getShapeOnLayer(layer) instanceof Circle circle) {
      rasterizeConvex(new FloatPoint[] {circle.center.toFloat()}, circle.radius + distance);
      return true;
    }
    return false;
  }

  /** Marks as free of fill the sample points that are closer than distance to the board edge. */
  private void rasterizeOutline(BoardOutline outline, double distance) {
    for (int i = 0; i < outline.shapeCount(); i++) {
      FloatPoint[] corners = outline.getShape(i).cornerApproxArr();
      if (corners == null) {
        continue;
      }
      for (int k = 0; k < corners.length; k++) {
        rasterizeSegment(corners[k], corners[(k + 1) % corners.length], distance);
      }
    }
  }

  /**
   * Marks as free of fill the sample points that are closer than reach to the segment, row by row:
   * what a row cuts out of the two discs around the ends and of the band between them.
   */
  private void rasterizeSegment(FloatPoint from, FloatPoint to, double reach) {
    rasterizeConvex(new FloatPoint[] {from, to}, reach);
  }

  /** Marks as free of fill the sample points that are closer to the shape than distance. */
  private void rasterizeKeepout(TileShape shape, double distance) {
    FloatPoint[] corners = shape.cornerApproxArr();
    if (corners == null || corners.length == 0) {
      return;
    }
    rasterizeConvex(corners, distance);
  }

  /**
   * Marks as free of fill the sample points that are closer than reach to the convex polygon with
   * these corners (a segment with two, a point with one), row by row. The points within reach of a
   * convex shape form a convex shape, of which a row cuts one span: from the leftmost to the
   * rightmost point that the row cuts out of the surroundings of the sides.
   */
  private void rasterizeConvex(FloatPoint[] corners, double reach) {
    rasterizeConvex(corners, reach, fineMask);
  }

  /** Sets in the mask the sample points closer than reach to the convex polygon, or inside it. */
  private void rasterizeConvex(FloatPoint[] corners, double reach, long[] mask) {
    double fine = (double) cellSize / FINE;
    double minY = Double.MAX_VALUE;
    double maxY = -Double.MAX_VALUE;
    for (FloatPoint corner : corners) {
      minY = Math.min(minY, corner.y);
      maxY = Math.max(maxY, corner.y);
    }
    int startRow = Math.max(0, (int) Math.floor((minY - reach - originY) / fine));
    int endRow = Math.min(fineRows - 1, (int) Math.floor((maxY + reach - originY) / fine));
    int sides = corners.length > 2 ? corners.length : corners.length - 1;
    // the corners of the band around each side
    double[][] bandX = new double[Math.max(sides, 0)][4];
    double[][] bandY = new double[Math.max(sides, 0)][4];
    boolean[] hasBand = new boolean[Math.max(sides, 0)];
    for (int i = 0; i < sides; i++) {
      FloatPoint from = corners[i];
      FloatPoint to = corners[(i + 1) % corners.length];
      double dx = to.x - from.x;
      double dy = to.y - from.y;
      double length = Math.hypot(dx, dy);
      if (length <= 0) {
        continue;
      }
      hasBand[i] = true;
      double offsetX = -dy / length * reach;
      double offsetY = dx / length * reach;
      bandX[i][0] = from.x + offsetX;
      bandY[i][0] = from.y + offsetY;
      bandX[i][1] = to.x + offsetX;
      bandY[i][1] = to.y + offsetY;
      bandX[i][2] = to.x - offsetX;
      bandY[i][2] = to.y - offsetY;
      bandX[i][3] = from.x - offsetX;
      bandY[i][3] = from.y - offsetY;
    }
    for (int row = startRow; row <= endRow; row++) {
      double y = originY + (row + 0.5) * fine;
      double low = Double.MAX_VALUE;
      double high = -Double.MAX_VALUE;
      for (FloatPoint corner : corners) {
        double offset = y - corner.y;
        if (Math.abs(offset) < reach) {
          double halfChord = Math.sqrt(reach * reach - offset * offset);
          low = Math.min(low, corner.x - halfChord);
          high = Math.max(high, corner.x + halfChord);
        }
      }
      for (int i = 0; i < sides; i++) {
        if (!hasBand[i]) {
          continue;
        }
        for (int k = 0; k < 4; k++) {
          double y1 = bandY[i][k];
          double y2 = bandY[i][(k + 1) % 4];
          if ((y1 - y) * (y2 - y) <= 0 && y1 != y2) {
            double x = bandX[i][k] + (y - y1) * (bandX[i][(k + 1) % 4] - bandX[i][k]) / (y2 - y1);
            low = Math.min(low, x);
            high = Math.max(high, x);
          }
        }
      }
      if (low < high) {
        setFineSpan(
            mask,
            row,
            (int) Math.ceil((low - originX) / fine - 0.5),
            (int) Math.floor((high - originX) / fine - 0.5));
      }
    }
  }

  /**
   * Whether two spokes that end in each other are joined by fill. Of each spoke, foreign copper
   * must leave a strip as wide as the minimum width of the fill from the edge of its pad to the
   * point where the two strips cross, or to the middle between the pads when the spokes face each
   * other. The strips are tried in the middle and along each side of their spokes.
   */
  private static boolean spokesMeet(
      PadPlaneConnection.Spoke own,
      PadPlaneConnection.Spoke their,
      double minWidth,
      List<TileShape> foreignShapes,
      List<double[]> foreignClearances) {
    double[] ownOffsets = stripOffsets(own.halfWidth(), minWidth);
    double[] theirOffsets = stripOffsets(their.halfWidth(), minWidth);
    for (double ownOffset : ownOffsets) {
      FloatPoint[] a = shifted(own, ownOffset);
      for (double theirOffset : theirOffsets) {
        FloatPoint[] b = shifted(their, theirOffset);
        double dx = a[1].x - a[0].x;
        double dy = a[1].y - a[0].y;
        double ex = b[1].x - b[0].x;
        double ey = b[1].y - b[0].y;
        double denominator = dx * ey - dy * ex;
        FloatPoint meet;
        if (Math.abs(denominator) > 1e-6 * Math.hypot(dx, dy) * Math.hypot(ex, ey)) {
          double qx = b[0].x - a[0].x;
          double qy = b[0].y - a[0].y;
          double t = (qx * ey - qy * ex) / denominator;
          double u = (qx * dy - qy * dx) / denominator;
          if (t > 1 || u > 1) {
            continue;
          }
          // a crossing under a pad: the strips already touch at the edges of the pads
          t = Math.max(0, t);
          meet = new FloatPoint(a[0].x + t * dx, a[0].y + t * dy);
        } else {
          meet = new FloatPoint((a[0].x + b[0].x) / 2.0, (a[0].y + b[0].y) / 2.0);
          double length = Math.hypot(dx, dy);
          double across =
              length > 0 ? Math.abs((meet.x - a[0].x) * dy - (meet.y - a[0].y) * dx) / length : 0;
          if (across > 1.0) {
            // parallel strips that are not on one line
            continue;
          }
        }
        if (isFree(a[0], meet, 0, foreignShapes, foreignClearances)
            && isFree(b[0], meet, 0, foreignShapes, foreignClearances)) {
          return true;
        }
      }
    }
    return false;
  }

  /** The positions of a strip as wide as the minimum width across a spoke: middle, both sides. */
  private static double[] stripOffsets(double halfWidth, double minWidth) {
    double side = Math.max(0, halfWidth - minWidth / 2.0);
    return side > 0 ? new double[] {0, side, -side} : new double[] {0};
  }

  /** The centre line of a spoke from the edge of the pad to its end, moved sideways by offset. */
  private static FloatPoint[] shifted(PadPlaneConnection.Spoke spoke, double offset) {
    double dx = spoke.endX() - spoke.startX();
    double dy = spoke.endY() - spoke.startY();
    double length = Math.hypot(dx, dy);
    double shiftX = length > 0 ? -dy / length * offset : 0;
    double shiftY = length > 0 ? dx / length * offset : 0;
    return new FloatPoint[] {
      new FloatPoint(spoke.startX() + shiftX, spoke.startY() + shiftY),
      new FloatPoint(spoke.endX() + shiftX, spoke.endY() + shiftY)
    };
  }

  /** The spokes from the centre of the pad instead of its edge, as the fill draws them. */
  private static PadPlaneConnection.Spoke[] wholeSpokes(
      Pin pin, PadPlaneConnection.Spoke[] fromEdge) {
    PadPlaneConnection connection = pin.getPlaneConnection();
    PadPlaneConnection.Spoke[] result = new PadPlaneConnection.Spoke[fromEdge.length];
    for (int i = 0; i < fromEdge.length; i++) {
      result[i] =
          new PadPlaneConnection.Spoke(
              connection.centerX(),
              connection.centerY(),
              fromEdge[i].endX(),
              fromEdge[i].endY(),
              fromEdge[i].halfWidth());
    }
    return result;
  }

  /** Whether the point lies in the rectangle of the spoke, with a tolerance of one unit. */
  private static boolean isInSpoke(PadPlaneConnection.Spoke spoke, double x, double y) {
    double dx = spoke.endX() - spoke.startX();
    double dy = spoke.endY() - spoke.startY();
    double length = Math.hypot(dx, dy);
    if (length <= 0) {
      return false;
    }
    double along = ((x - spoke.startX()) * dx + (y - spoke.startY()) * dy) / length;
    double across = Math.abs((x - spoke.startX()) * dy - (y - spoke.startY()) * dx) / length;
    return along >= -1.0 && along <= length + 1.0 && across <= spoke.halfWidth() + 1.0;
  }

  /**
   * The share of the minimum width by which the zone filler of KiCad 10.0.8 is more lenient than
   * the exact distance (clearance plus half the minimum width) when it decides whether fill is
   * left: measured with a via in front of a spoke, moved in steps of 10 um. It also keeps a neck
   * that is exactly as wide as the minimum width, which designs drawn on a grid are full of: the
   * pieces of the fill are told apart with this leniency. A spoke is not: the filler cuts it
   * exactly.
   */
  private static final double FILL_LENIENCY = 0.06;

  /**
   * Whether foreign copper leaves a part of the spoke between two points of its centre line that
   * the fill keeps: a strip as wide as the minimum width of the fill, whose centre line stays at
   * the clearance of the fill plus half the minimum width from any foreign copper. Three straight
   * strips are tried first: in the middle of the spoke and along each side. Foreign copper that
   * cuts a corner of the spoke can leave a strip that moves from one side to the other, or that
   * joins the fill beside the spoke: the centre line is then searched among the points of a small
   * grid over the spoke.
   */
  private static boolean isStripFree(
      FloatPoint start,
      FloatPoint end,
      double halfWidth,
      double minWidth,
      TileShape padShape,
      double gap,
      List<TileShape> foreignShapes,
      List<double[]> foreignClearances) {
    if (foreignShapes.isEmpty()) {
      return true;
    }
    double length = start.distance(end);
    for (double offset : stripOffsets(halfWidth, minWidth)) {
      double shiftX = length > 0 ? -(end.y - start.y) / length * offset : 0;
      double shiftY = length > 0 ? (end.x - start.x) / length * offset : 0;
      FloatPoint from = new FloatPoint(start.x + shiftX, start.y + shiftY);
      FloatPoint to = new FloatPoint(end.x + shiftX, end.y + shiftY);
      if (isFree(from, to, 0, foreignShapes, foreignClearances)) {
        return true;
      }
    }
    double side = halfWidth - minWidth / 2.0;
    if (side < 0 || length <= 0 || minWidth <= 0 || padShape == null) {
      return false;
    }
    // Points every eighth of the minimum width, as centres of a disc of that width. Within the
    // spoke the disc must stay between its sides; past the relief of the pad it is a disc of the
    // fill around the spoke. The spoke holds if such discs lead from the edge of the pad into the
    // fill.
    double step = minWidth / 8.0;
    int half = (int) Math.ceil((side + minWidth / 2.0) / step);
    int across = 2 * half + 1;
    int along = (int) Math.ceil((length + minWidth / 2.0) / step) + 1;
    double alongX = (end.x - start.x) / length;
    double alongY = (end.y - start.y) / length;
    double fillDistance = gap + minWidth / 2.0 - 1.0;
    // only the foreign copper that can reach one of these points
    FloatPoint farEnd =
        new FloatPoint(
            start.x + alongX * (along - 1) * step, start.y + alongY * (along - 1) * step);
    List<TileShape> nearShapes = new ArrayList<>();
    List<double[]> nearClearances = new ArrayList<>();
    for (int i = 0; i < foreignShapes.size(); i++) {
      if (distance(start, farEnd, foreignShapes.get(i))
          < foreignClearances.get(i)[0] + half * step) {
        nearShapes.add(foreignShapes.get(i));
        nearClearances.add(foreignClearances.get(i));
      }
    }
    boolean[] reached = new boolean[across];
    for (int m = 0; m < along; m++) {
      double distance = m * step;
      boolean[] free = new boolean[across];
      boolean[] inFill = new boolean[across];
      for (int n = 0; n < across; n++) {
        double offset = (n - half) * step;
        FloatPoint point =
            new FloatPoint(
                start.x + alongX * distance - alongY * offset,
                start.y + alongY * distance + alongX * offset);
        inFill[n] = padShape.distance(point) >= fillDistance;
        free[n] =
            (inFill[n] || Math.abs(offset) <= side + 1e-6)
                && isFree(point, point, 0, nearShapes, nearClearances);
      }
      boolean[] next = new boolean[across];
      for (int n = 0; n < across; n++) {
        next[n] =
            free[n]
                && (m == 0
                    || reached[n]
                    || (n > 0 && reached[n - 1])
                    || (n < across - 1 && reached[n + 1]));
      }
      for (int n = 1; n < across; n++) {
        next[n] = next[n] || (free[n] && next[n - 1]);
      }
      for (int n = across - 2; n >= 0; n--) {
        next[n] = next[n] || (free[n] && next[n + 1]);
      }
      boolean any = false;
      for (int n = 0; n < across; n++) {
        if (next[n] && inFill[n]) {
          return true;
        }
        any = any || next[n];
      }
      if (!any) {
        return false;
      }
      reached = next;
    }
    return false;
  }

  /**
   * Whether the far end of a spoke lies in fill, which KiCad asks of a spoke to keep it. The fill
   * there is what is left between the relief of the pad and the foreign copper, without the parts
   * narrower than the minimum width: the end lies in it when a disc of that width fits around it,
   * outside the relief of the pad and at the clearance of the fill from any foreign copper. The end
   * of the spoke is half the minimum width away from the relief, so the disc can only slide along
   * it.
   */
  private static boolean isEndInFill(
      FloatPoint start,
      FloatPoint end,
      double minWidth,
      TileShape padShape,
      double gap,
      List<TileShape> foreignShapes,
      List<double[]> foreignClearances) {
    if (foreignShapes.isEmpty()) {
      return true;
    }
    double length = start.distance(end);
    double[] slides =
        length > 0 && minWidth > 0
            ? new double[] {0, minWidth / 4.0, -minWidth / 4.0, minWidth / 2.0, -minWidth / 2.0}
            : new double[] {0};
    for (double slide : slides) {
      FloatPoint centre = end;
      if (slide != 0) {
        centre =
            new FloatPoint(
                end.x - (end.y - start.y) / length * slide,
                end.y + (end.x - start.x) / length * slide);
        if (padShape != null
            && padShape.distance(centre) < gap + minWidth / 2.0 - FILL_LENIENCY * minWidth) {
          continue;
        }
      }
      if (isFree(centre, centre, FILL_LENIENCY * minWidth, foreignShapes, foreignClearances)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Whether the segment keeps the clearance of the fill plus half its minimum width, less the
   * leniency.
   */
  private static boolean isFree(
      FloatPoint from,
      FloatPoint to,
      double leniency,
      List<TileShape> foreignShapes,
      List<double[]> foreignClearances) {
    for (int i = 0; i < foreignShapes.size(); i++) {
      if (distance(from, to, foreignShapes.get(i)) < foreignClearances.get(i)[0] - leniency) {
        return false;
      }
    }
    return true;
  }

  /** The distance between the segment from-to and a convex shape; 0 if they meet. */
  static double distance(FloatPoint from, FloatPoint to, TileShape shape) {
    if (shape.contains(from) || shape.contains(to)) {
      return 0;
    }
    FloatPoint[] corners = shape.cornerApproxArr();
    if (corners == null || corners.length == 0) {
      return Double.MAX_VALUE;
    }
    double result = Double.MAX_VALUE;
    for (int i = 0; i < corners.length; i++) {
      FloatPoint a = corners[i];
      FloatPoint b = corners[(i + 1) % corners.length];
      result = Math.min(result, segmentDistance(from, to, a, b));
      if (result <= 0) {
        return 0;
      }
    }
    return result;
  }

  private static double segmentDistance(
      FloatPoint p1, FloatPoint p2, FloatPoint q1, FloatPoint q2) {
    if (segmentsCross(p1, p2, q1, q2)) {
      return 0;
    }
    return Math.min(
        Math.min(pointSegmentDistance(p1, q1, q2), pointSegmentDistance(p2, q1, q2)),
        Math.min(pointSegmentDistance(q1, p1, p2), pointSegmentDistance(q2, p1, p2)));
  }

  private static boolean segmentsCross(FloatPoint p1, FloatPoint p2, FloatPoint q1, FloatPoint q2) {
    double d1 = cross(q1, q2, p1);
    double d2 = cross(q1, q2, p2);
    double d3 = cross(p1, p2, q1);
    double d4 = cross(p1, p2, q2);
    return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
  }

  private static double cross(FloatPoint a, FloatPoint b, FloatPoint c) {
    return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
  }

  private static double pointSegmentDistance(FloatPoint p, FloatPoint a, FloatPoint b) {
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    double lengthSquare = dx * dx + dy * dy;
    double t = lengthSquare > 0 ? ((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSquare : 0;
    t = Math.max(0, Math.min(1, t));
    return Math.hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy));
  }

  /** Rasterizes the convex tile shapes of a conduction area into the copper mask. */
  public void rasterizeConductionArea(ConductionArea ca) {
    if (ca == null || ca.getArea() == null) {
      return;
    }
    TileShape[] pieces = ca.getArea().splitToConvex();
    if (pieces == null) {
      return;
    }
    for (TileShape tile : pieces) {
      if (tile == null) {
        continue;
      }
      IntBox box = tile.boundingBox();
      int startCol = Math.max(0, (box.ll.x - originX) / cellSize);
      int endCol = Math.min(cols - 1, (box.ur.x - originX) / cellSize);
      int startRow = Math.max(0, (box.ll.y - originY) / cellSize);
      int endRow = Math.min(rows - 1, (box.ur.y - originY) / cellSize);

      if (startCol > endCol || startRow > endRow) {
        continue;
      }

      boolean tileMarked = false;
      if (tile.isIntBox()) {
        for (int r = startRow; r <= endRow; r++) {
          setRowSpan(copperMask, r, startCol, endCol);
          tileMarked = true;
        }
      } else {
        // Precise point-in-convex-tile test along cell centers
        for (int r = startRow; r <= endRow; r++) {
          int cellY = originY + r * cellSize + cellSize / 2;
          int spanStart = -1;
          for (int c = startCol; c <= endCol; c++) {
            int cellX = originX + c * cellSize + cellSize / 2;
            if (tile.contains(new IntPoint(cellX, cellY))) {
              if (spanStart == -1) {
                spanStart = c;
              }
            } else if (spanStart != -1) {
              setRowSpan(copperMask, r, spanStart, c - 1);
              spanStart = -1;
              tileMarked = true;
            }
          }
          if (spanStart != -1) {
            setRowSpan(copperMask, r, spanStart, endCol);
            tileMarked = true;
          }
        }
      }

      // Fallback for thin or tiny features whose cell centers fall outside the shape:
      // mark the cells containing the tile corners and center of gravity
      if (!tileMarked) {
        app.freerouting.geometry.planar.FloatPoint[] corners = tile.cornerApproxArr();
        if (corners != null) {
          for (app.freerouting.geometry.planar.FloatPoint corner : corners) {
            int c =
                Math.max(
                    0,
                    Math.min(cols - 1, (int) Math.floor((corner.x - originX) / (double) cellSize)));
            int r =
                Math.max(
                    0,
                    Math.min(rows - 1, (int) Math.floor((corner.y - originY) / (double) cellSize)));
            setBit(copperMask, r, c);
          }
        }
        app.freerouting.geometry.planar.FloatPoint cog = tile.centreOfGravity();
        if (cog != null) {
          int c =
              Math.max(
                  0, Math.min(cols - 1, (int) Math.floor((cog.x - originX) / (double) cellSize)));
          int r =
              Math.max(
                  0, Math.min(rows - 1, (int) Math.floor((cog.y - originY) / (double) cellSize)));
          setBit(copperMask, r, c);
        }
      }
    }
  }

  /** Rasterizes an obstacle shape into the obstacle mask. */
  public void rasterizeObstacleShape(Shape shape) {
    if (shape == null || shape.isEmpty()) {
      return;
    }
    IntBox box = shape.boundingBox();
    int startCol = Math.max(0, (box.ll.x - originX) / cellSize);
    int endCol = Math.min(cols - 1, (box.ur.x - originX) / cellSize);
    int startRow = Math.max(0, (box.ll.y - originY) / cellSize);
    int endRow = Math.min(rows - 1, (box.ur.y - originY) / cellSize);

    if (startCol > endCol || startRow > endRow) {
      return;
    }

    if (shape instanceof IntBox) {
      for (int r = startRow; r <= endRow; r++) {
        setRowSpan(obstacleMask, r, startCol, endCol);
      }
    } else {
      for (int r = startRow; r <= endRow; r++) {
        int cellY = originY + r * cellSize + cellSize / 2;
        int spanStart = -1;
        for (int c = startCol; c <= endCol; c++) {
          int cellX = originX + c * cellSize + cellSize / 2;
          if (shape.contains(new IntPoint(cellX, cellY))) {
            if (spanStart == -1) {
              spanStart = c;
            }
          } else if (spanStart != -1) {
            setRowSpan(obstacleMask, r, spanStart, c - 1);
            spanStart = -1;
          }
        }
        if (spanStart != -1) {
          setRowSpan(obstacleMask, r, spanStart, endCol);
        }
      }
    }
  }

  /**
   * Rasterizes a thick line segment (capsule) into the obstacle mask.
   *
   * @param x1 start X in board units
   * @param y1 start Y in board units
   * @param x2 end X in board units
   * @param y2 end Y in board units
   * @param radius total obstacle clearance radius in board units
   */
  public void rasterizeThickSegment(int x1, int y1, int x2, int y2, int radius) {
    // Conservative dilation: include cell half-diagonal so any cell touching the obstacle is marked
    int dilatedRadius = radius + (cellSize + 1) / 2;
    int minX = Math.min(x1, x2) - dilatedRadius;
    int maxX = Math.max(x1, x2) + dilatedRadius;
    int minY = Math.min(y1, y2) - dilatedRadius;
    int maxY = Math.max(y1, y2) + dilatedRadius;

    int startCol = Math.max(0, (minX - originX) / cellSize);
    int endCol = Math.min(cols - 1, (maxX - originX) / cellSize);
    int startRow = Math.max(0, (minY - originY) / cellSize);
    int endRow = Math.min(rows - 1, (maxY - originY) / cellSize);

    if (startCol > endCol || startRow > endRow) {
      return;
    }

    long radiusSq = (long) dilatedRadius * dilatedRadius;
    long dx = x2 - x1;
    long dy = y2 - y1;

    // Fast-path: Horizontal line segment or point (dx arbitrary, dy == 0)
    if (dy == 0) {
      int segMinX = Math.min(x1, x2);
      int segMaxX = Math.max(x1, x2);
      for (int r = startRow; r <= endRow; r++) {
        int cellY = originY + r * cellSize + cellSize / 2;
        long distY = Math.abs(cellY - y1);
        if (distY <= dilatedRadius) {
          int deltaX = (int) Math.sqrt(radiusSq - distY * distY);
          int spanStartCol = Math.max(startCol, (segMinX - deltaX - originX) / cellSize);
          int spanEndCol = Math.min(endCol, (segMaxX + deltaX - originX) / cellSize);
          if (spanStartCol <= spanEndCol) {
            setRowSpan(obstacleMask, r, spanStartCol, spanEndCol);
          }
        }
      }
      return;
    }

    // Fast-path: Vertical line segment (dx == 0, dy != 0)
    if (dx == 0) {
      int segMinY = Math.min(y1, y2);
      int segMaxY = Math.max(y1, y2);
      for (int r = startRow; r <= endRow; r++) {
        int cellY = originY + r * cellSize + cellSize / 2;
        int clampedY = Math.max(segMinY, Math.min(segMaxY, cellY));
        long distY = Math.abs(cellY - clampedY);
        if (distY <= dilatedRadius) {
          int deltaX = (int) Math.sqrt(radiusSq - distY * distY);
          int sCol = Math.max(startCol, (x1 - deltaX - originX) / cellSize);
          int eCol = Math.min(endCol, (x1 + deltaX - originX) / cellSize);
          if (sCol <= eCol) {
            setRowSpan(obstacleMask, r, sCol, eCol);
          }
        }
      }
      return;
    }

    long lenSq = dx * dx + dy * dy;

    for (int r = startRow; r <= endRow; r++) {
      int cellY = originY + r * cellSize + cellSize / 2;
      int spanStart = -1;
      int spanEnd = -1;

      for (int c = startCol; c <= endCol; c++) {
        int cellX = originX + c * cellSize + cellSize / 2;

        // Distance from (cellX, cellY) to segment (x1, y1)-(x2, y2)
        boolean inside;
        if (lenSq == 0) {
          long ddx = cellX - x1;
          long ddy = cellY - y1;
          inside = (ddx * ddx + ddy * ddy) <= radiusSq;
        } else {
          long dot = (long) (cellX - x1) * dx + (long) (cellY - y1) * dy;
          if (dot <= 0) {
            long ddx = cellX - x1;
            long ddy = cellY - y1;
            inside = (ddx * ddx + ddy * ddy) <= radiusSq;
          } else if (dot >= lenSq) {
            long ddx = cellX - x2;
            long ddy = cellY - y2;
            inside = (ddx * ddx + ddy * ddy) <= radiusSq;
          } else {
            // Perpendicular projection
            double t = (double) dot / lenSq;
            double projX = x1 + t * dx;
            double projY = y1 + t * dy;
            double distSq = (cellX - projX) * (cellX - projX) + (cellY - projY) * (cellY - projY);
            inside = distSq <= radiusSq;
          }
        }

        if (inside) {
          if (spanStart == -1) {
            spanStart = c;
          }
          spanEnd = c;
        } else if (spanStart != -1) {
          setRowSpan(obstacleMask, r, spanStart, spanEnd);
          spanStart = -1;
          spanEnd = -1;
        }
      }
      if (spanStart != -1) {
        setRowSpan(obstacleMask, r, spanStart, spanEnd);
      }
    }
  }

  /**
   * Partitions items into isolated connected components using BFS flood fill.
   *
   * @param ca conduction area whose pour contains or touches the items
   * @param items same-net items to cluster
   * @return fracture result containing items grouped per island
   */
  private PlaneFractureResult partitionItemsByIsland(
      ConductionArea ca, Collection<? extends Item> items) {
    Map<Integer, List<Item>> islands = new HashMap<>();
    Map<Item, Integer> itemIslandMap = new HashMap<>();
    List<Item> unseededItems = new ArrayList<>();
    int nextIslandId = 0;

    for (Item item : items) {
      if (itemIslandMap.containsKey(item)) {
        continue;
      }

      IntPoint pt = getItemLocation(item, ca);
      if (pt == null) {
        unseededItems.add(item);
        continue;
      }

      int targetCol = (pt.x - originX) / cellSize;
      int targetRow = (pt.y - originY) / cellSize;

      int[] seed = findNearestConductiveCell(targetCol, targetRow);
      if (seed == null) {
        unseededItems.add(item);
        continue;
      }

      int seedIndex = seed[1] * cols + seed[0];
      int existingIsland = cellIslandMap[seedIndex];
      if (existingIsland >= 0) {
        islands.computeIfAbsent(existingIsland, k -> new ArrayList<>()).add(item);
        itemIslandMap.put(item, existingIsland);
        continue;
      }

      // Start new BFS flood fill for this island
      int currentIslandId = nextIslandId++;
      islands.computeIfAbsent(currentIslandId, k -> new ArrayList<>()).add(item);
      itemIslandMap.put(item, currentIslandId);

      runFloodFill(seed[0], seed[1], (short) currentIslandId);

      // Check remaining unassigned items to see if they are reached by this flood fill
      for (Item other : items) {
        if (!itemIslandMap.containsKey(other) && !unseededItems.contains(other)) {
          IntPoint otherPt = getItemLocation(other, ca);
          if (otherPt != null) {
            int oc = (otherPt.x - originX) / cellSize;
            int or = (otherPt.y - originY) / cellSize;
            int[] otherSeed = findNearestConductiveCell(oc, or);
            if (otherSeed != null) {
              int otherIndex = otherSeed[1] * cols + otherSeed[0];
              if (cellIslandMap[otherIndex] == currentIslandId) {
                islands.get(currentIslandId).add(other);
                itemIslandMap.put(other, currentIslandId);
              }
            }
          }
        }
      }
    }

    if (nextIslandId == 0) {
      Map<Integer, List<Item>> single = new HashMap<>();
      single.put(0, new ArrayList<>(items));
      return new PlaneFractureResult(single);
    }

    for (Item unseeded : unseededItems) {
      if (itemIslandMap.containsKey(unseeded)) {
        continue;
      }
      IntPoint pt = getItemLocation(unseeded, ca);
      int assignedIsland = 0;
      if (pt != null) {
        int targetCol = (pt.x - originX) / cellSize;
        int targetRow = (pt.y - originY) / cellSize;
        int nearestIsland = findNearestMappedIsland(targetCol, targetRow, 10);
        if (nearestIsland >= 0) {
          assignedIsland = nearestIsland;
        }
      }
      islands.computeIfAbsent(assignedIsland, k -> new ArrayList<>()).add(unseeded);
      itemIslandMap.put(unseeded, assignedIsland);
    }

    return new PlaneFractureResult(islands);
  }

  private void runFloodFill(int startCol, int startRow, short islandId) {
    int head = 0;
    int tail = 0;

    queue[tail++] = (startRow << 16) | (startCol & 0xFFFF);
    setBit(visitedMask, startRow, startCol);
    cellIslandMap[startRow * cols + startCol] = islandId;

    int[] dc = {1, -1, 0, 0};
    int[] dr = {0, 0, 1, -1};

    while (head < tail) {
      int packed = queue[head++];
      int r = packed >>> 16;
      int c = packed & 0xFFFF;

      for (int i = 0; i < 4; i++) {
        int nc = c + dc[i];
        int nr = r + dr[i];

        if (nc >= 0 && nc < cols && nr >= 0 && nr < rows) {
          if (!isBitSet(visitedMask, nr, nc) && isConductive(nc, nr)) {
            setBit(visitedMask, nr, nc);
            cellIslandMap[nr * cols + nc] = islandId;
            queue[tail++] = (nr << 16) | (nc & 0xFFFF);
          }
        }
      }
    }
  }

  private int[] findNearestConductiveCell(int col, int row) {
    // Search expanding neighborhood up to radius 4
    return findNearestConductiveCell(col, row, 4);
  }

  private int[] findNearestConductiveCell(int col, int row, int maxRadius) {
    if (col >= 0 && col < cols && row >= 0 && row < rows && isConductive(col, row)) {
      return new int[] {col, row};
    }
    for (int radius = 1; radius <= maxRadius; radius++) {
      for (int dr = -radius; dr <= radius; dr++) {
        for (int dc = -radius; dc <= radius; dc++) {
          int nc = col + dc;
          int nr = row + dr;
          if (nc >= 0 && nc < cols && nr >= 0 && nr < rows && isConductive(nc, nr)) {
            return new int[] {nc, nr};
          }
        }
      }
    }
    return null;
  }

  private int findNearestMappedIsland(int col, int row, int maxRadius) {
    for (int r = 1; r <= maxRadius; r++) {
      for (int dr = -r; dr <= r; dr++) {
        for (int dc = -r; dc <= r; dc++) {
          int nc = col + dc;
          int nr = row + dr;
          if (nc >= 0 && nc < cols && nr >= 0 && nr < rows) {
            int island = cellIslandMap[nr * cols + nc];
            if (island >= 0) {
              return island;
            }
          }
        }
      }
    }
    return -1;
  }

  private static IntPoint toIntPoint(Point p) {
    if (p instanceof IntPoint ip) {
      return ip;
    }
    return p.toFloat().round();
  }

  private static IntPoint getItemLocation(Item item, ConductionArea ca) {
    if (item instanceof DrillItem drillItem) {
      IntPoint center = toIntPoint(drillItem.getCenter());
      if (ca != null && ca.getArea() != null && !ca.getArea().contains(center)) {
        IntBox box = drillItem.boundingBox();
        if (box != null) {
          IntPoint[] corners = {
            box.ll, box.ur, new IntPoint(box.ll.x, box.ur.y), new IntPoint(box.ur.x, box.ll.y)
          };
          for (IntPoint cp : corners) {
            if (ca.getArea().contains(cp)) {
              return cp;
            }
          }
        }
      }
      return center;
    } else if (item instanceof Trace trace) {
      if (ca != null && ca.getArea() != null) {
        if (ca.getArea().contains(trace.firstCorner())) {
          return toIntPoint(trace.firstCorner());
        }
        if (ca.getArea().contains(trace.lastCorner())) {
          return toIntPoint(trace.lastCorner());
        }
        if (trace instanceof PolylineTrace polylineTrace) {
          Point[] corners = polylineTrace.polyline().corners();
          for (Point c : corners) {
            if (ca.getArea().contains(c)) {
              return toIntPoint(c);
            }
          }
        }
      }
      return toIntPoint(trace.firstCorner());
    }
    return null;
  }

  /**
   * Returns true if the cell contains conductive copper (inside pour and not blocked by obstacle).
   */
  public boolean isConductive(int col, int row) {
    if (col < 0 || col >= cols || row < 0 || row >= rows) {
      return false;
    }
    int wordIdx = row * wordsPerRow + (col >> 6);
    long bit = 1L << (col & 63);
    return (copperMask[wordIdx] & bit) != 0L && (obstacleMask[wordIdx] & bit) == 0L;
  }

  private boolean isBitSet(long[] mask, int row, int col) {
    if (col < 0 || col >= cols || row < 0 || row >= rows) {
      return false;
    }
    int wordIdx = row * wordsPerRow + (col >> 6);
    return (mask[wordIdx] & (1L << (col & 63))) != 0L;
  }

  private void setBit(long[] mask, int row, int col) {
    if (col >= 0 && col < cols && row >= 0 && row < rows) {
      int wordIdx = row * wordsPerRow + (col >> 6);
      mask[wordIdx] |= (1L << (col & 63));
    }
  }

  private void setRowSpan(long[] mask, int row, int startCol, int endCol) {
    if (row < 0 || row >= rows || startCol > endCol || startCol >= cols || endCol < 0) {
      return;
    }
    int s = Math.max(0, startCol);
    int e = Math.min(cols - 1, endCol);

    int startWord = row * wordsPerRow + (s >> 6);
    int endWord = row * wordsPerRow + (e >> 6);

    int startBit = s & 63;
    int endBit = e & 63;

    if (startWord == endWord) {
      long range = ((~0L) >>> (63 - (endBit - startBit))) << startBit;
      mask[startWord] |= range;
    } else {
      mask[startWord] |= ((~0L) << startBit);
      for (int w = startWord + 1; w < endWord; w++) {
        mask[w] = ~0L;
      }
      mask[endWord] |= ((~0L) >>> (63 - endBit));
    }
  }

  /** Returns current grid column count. */
  public int getCols() {
    return cols;
  }

  /** Returns current grid row count. */
  public int getRows() {
    return rows;
  }

  /** Returns current cell size in board units. */
  public int getCellSize() {
    return cellSize;
  }
}
