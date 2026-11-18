package app.freerouting.autoroute.plane;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.DrillItem;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.ObstacleArea;
import app.freerouting.board.model.items.PadPlaneConnection;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.board.searchtree.SearchTreeObject;
import app.freerouting.board.trace.PolylineTrace;
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

  /** Result container holding partitioned same-net items per isolated copper island. */
  public static class PlaneFractureResult {
    /** Whether the conduction area has fractured into multiple disconnected islands with pins. */
    public final boolean isFractured;

    /** Map of island ID (0-based) to list of same-net items residing in that island. */
    public final Map<Integer, List<Item>> islandItems;

    /** Total count of distinct islands containing electrical contact items. */
    public final int islandCount;

    /**
     * Creates a new fracture result.
     *
     * @param islandItems mapping from island ID to items in that island
     */
    public PlaneFractureResult(Map<Integer, List<Item>> islandItems) {
      this.islandItems = islandItems != null ? islandItems : Collections.emptyMap();
      this.islandCount = this.islandItems.size();
      this.isFractured = this.islandCount > 1;
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

        if (item instanceof Trace trace) {
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
   * <p>A pad attached to nothing is alone on an island; a pad attached to two pieces joins them.
   * Vias, trace ends and pads without a known connection are placed as before, in the piece of the
   * nearest cell of fill.
   */
  private PlaneFractureResult partitionItemsByPadConnection(
      ConductionArea ca, BasicBoard board, Collection<? extends Item> sameNetItems) {
    int layer = ca.getLayer();
    int[] pieceOfCell = new int[cols * rows];
    int[] pieceSizes = labelPieces(pieceOfCell);
    int pieceCount = pieceSizes.length;
    int largestPiece = -1;
    for (int i = 0; i < pieceCount; i++) {
      if (largestPiece < 0 || pieceSizes[i] > pieceSizes[largestPiece]) {
        largestPiece = i;
      }
    }

    List<Item> items = new ArrayList<>(sameNetItems);
    Map<Item, Integer> indexOfItem = new HashMap<>();
    for (int i = 0; i < items.size(); i++) {
      indexOfItem.put(items.get(i), i);
    }
    // nodes: the pieces of the fill, then the items
    int[] parent = new int[pieceCount + items.size()];
    for (int i = 0; i < parent.length; i++) {
      parent[i] = i;
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
      IntPoint location = getItemLocation(item, ca);
      int piece = -1;
      if (location != null) {
        int col = (location.x - originX) / cellSize;
        int row = (location.y - originY) / cellSize;
        int[] seed = findNearestConductiveCell(col, row);
        if (seed == null) {
          seed = findNearestConductiveCell(col, row, 10);
        }
        if (seed != null) {
          piece = pieceOfCell[seed[1] * cols + seed[0]];
        }
      }
      if (piece < 0) {
        piece = largestPiece;
      }
      if (piece >= 0) {
        union(parent, node, piece);
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
    return new PlaneFractureResult(islands);
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
    Arrays.fill(pieceOfCell, -1);
    List<Integer> sizes = new ArrayList<>();
    int[] dc = {1, -1, 0, 0};
    int[] dr = {0, 0, 1, -1};
    for (int row = 0; row < rows; row++) {
      for (int col = 0; col < cols; col++) {
        if (pieceOfCell[row * cols + col] >= 0 || !isConductive(col, row)) {
          continue;
        }
        int piece = sizes.size();
        int head = 0;
        int tail = 0;
        queue[tail++] = row * cols + col;
        pieceOfCell[row * cols + col] = piece;
        while (head < tail) {
          int cell = queue[head++];
          int r = cell / cols;
          int c = cell % cols;
          for (int i = 0; i < 4; i++) {
            int nc = c + dc[i];
            int nr = r + dr[i];
            if (nc >= 0
                && nc < cols
                && nr >= 0
                && nr < rows
                && pieceOfCell[nr * cols + nc] < 0
                && isConductive(nc, nr)) {
              pieceOfCell[nr * cols + nc] = piece;
              queue[tail++] = nr * cols + nc;
            }
          }
        }
        sizes.add(tail);
      }
    }
    int[] result = new int[sizes.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = sizes.get(i);
    }
    return result;
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
            || !isFree(centre, centre, minWidth, foreignShapes, foreignClearances)) {
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
      if (!isStripFree(start, end, spoke.halfWidth(), minWidth, foreignShapes, foreignClearances)
          || !isEndInFill(
              start, end, minWidth, ownShape, ownGap, foreignShapes, foreignClearances)) {
        continue;
      }
      IntPoint endPoint = end.round();
      if (ca.getArea() == null || !ca.getArea().contains(endPoint)) {
        continue;
      }
      int[] seed =
          findNearestConductiveCell(
              (endPoint.x - originX) / cellSize, (endPoint.y - originY) / cellSize, 3);
      if (seed != null) {
        union(parent, pieceCount + index, pieceOfCell[seed[1] * cols + seed[0]]);
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
        if (isFree(a[0], meet, minWidth, foreignShapes, foreignClearances)
            && isFree(b[0], meet, minWidth, foreignShapes, foreignClearances)) {
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
   * left: measured with a via in front of a spoke, moved in steps of 10 um.
   */
  private static final double FILL_LENIENCY = 0.06;

  /**
   * Whether foreign copper leaves a part of the spoke between two points of its centre line that
   * the fill keeps: a strip as wide as the minimum width of the fill, whose centre line stays at
   * the clearance of the fill plus half the minimum width from any foreign copper. Three positions
   * of the strip are tried: in the middle of the spoke and along each side.
   */
  private static boolean isStripFree(
      FloatPoint start,
      FloatPoint end,
      double halfWidth,
      double minWidth,
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
      if (isFree(from, to, minWidth, foreignShapes, foreignClearances)) {
        return true;
      }
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
      if (isFree(centre, centre, minWidth, foreignShapes, foreignClearances)) {
        return true;
      }
    }
    return false;
  }

  /** Whether the segment keeps the clearance of the fill plus half its minimum width. */
  private static boolean isFree(
      FloatPoint from,
      FloatPoint to,
      double minWidth,
      List<TileShape> foreignShapes,
      List<double[]> foreignClearances) {
    for (int i = 0; i < foreignShapes.size(); i++) {
      if (distance(from, to, foreignShapes.get(i))
          < foreignClearances.get(i)[0] - FILL_LENIENCY * minWidth) {
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
