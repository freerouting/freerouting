package app.freerouting.autoroute.plane;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.DrillItem;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.ObstacleArea;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.searchtree.SearchTreeObject;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.geometry.planar.Area;
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
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

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

  private boolean partitionBuffersReady;

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

  /**
   * What a conduction area keeps between two fracture evaluations, so that a board change that
   * leaves its inputs alone costs no rasterization or flood fill.
   *
   * <p>The copper mask of an area depends only on its (immutable) {@link Area} and on the grid
   * geometry, so it is reused as long as both are the same. The island partition depends only on
   * the grid geometry, the copper and obstacle masks and the contact items with their locations, so
   * the previous result is returned when all of them are equal. Both checks compare the actual
   * inputs: the memo cannot go stale, it can only miss.
   */
  public static final class FractureMemo {
    private Area copperArea;
    private int copperOriginX;
    private int copperOriginY;
    private int copperCellSize;
    private int copperCols;
    private int copperRows;
    private long[] copperWords;

    private int areasOriginX;
    private int areasOriginY;
    private int areasCellSize;
    private int areasCols;
    private int areasRows;
    private Item[] areasItems;
    private int[] areasShapeIndices;
    private TileShape[] areasShapes;
    private double[] areasClearances;
    private long[] areasWords;

    private Area partitionArea;
    private int partitionOriginX;
    private int partitionOriginY;
    private int partitionCellSize;
    private int partitionCols;
    private int partitionRows;
    private long[] partitionObstacleWords;
    private Item[] partitionItems;
    private IntPoint[] partitionLocations;
    private PlaneFractureResult partitionResult;

    /** Copies the cached copper mask into the grid; returns false if it was not computed for it. */
    synchronized boolean restoreCopper(FastPlaneConnectivityGrid grid, Area area) {
      if (copperWords == null
          || area == null
          || area != copperArea
          || grid.originX != copperOriginX
          || grid.originY != copperOriginY
          || grid.cellSize != copperCellSize
          || grid.cols != copperCols
          || grid.rows != copperRows) {
        return false;
      }
      System.arraycopy(copperWords, 0, grid.copperMask, 0, copperWords.length);
      return true;
    }

    synchronized void storeCopper(FastPlaneConnectivityGrid grid, Area area) {
      if (area == null) {
        return;
      }
      copperArea = area;
      copperOriginX = grid.originX;
      copperOriginY = grid.originY;
      copperCellSize = grid.cellSize;
      copperCols = grid.cols;
      copperRows = grid.rows;
      copperWords = Arrays.copyOf(grid.copperMask, grid.rows * grid.wordsPerRow);
    }

    /**
     * Returns the raster of these obstacle area shapes (tile shapes are immutable, so the same
     * references with the same clearances give the same raster) if it is cached for this grid.
     */
    synchronized long[] lookupObstacleAreas(
        FastPlaneConnectivityGrid grid, TileShape[] shapes, double[] clearances) {
      if (areasWords == null
          || grid.originX != areasOriginX
          || grid.originY != areasOriginY
          || grid.cellSize != areasCellSize
          || grid.cols != areasCols
          || grid.rows != areasRows
          || shapes.length != areasShapes.length
          || !Arrays.equals(clearances, areasClearances)) {
        return null;
      }
      for (int i = 0; i < shapes.length; i++) {
        if (shapes[i] != areasShapes[i]) {
          return null;
        }
      }
      return areasWords;
    }

    synchronized void storeObstacleAreas(
        FastPlaneConnectivityGrid grid,
        Item[] items,
        int[] shapeIndices,
        TileShape[] shapes,
        double[] clearances,
        long[] words) {
      areasItems = items;
      areasShapeIndices = shapeIndices;
      areasOriginX = grid.originX;
      areasOriginY = grid.originY;
      areasCellSize = grid.cellSize;
      areasCols = grid.cols;
      areasRows = grid.rows;
      areasShapes = shapes;
      areasClearances = clearances;
      areasWords = words;
    }

    /** Returns the previous partition if it was computed from exactly these inputs, else null. */
    synchronized PlaneFractureResult lookupPartition(
        FastPlaneConnectivityGrid grid, Area area, Item[] items, IntPoint[] locations) {
      if (partitionResult == null
          || area == null
          || area != partitionArea
          || grid.originX != partitionOriginX
          || grid.originY != partitionOriginY
          || grid.cellSize != partitionCellSize
          || grid.cols != partitionCols
          || grid.rows != partitionRows
          || items.length != partitionItems.length) {
        return null;
      }
      for (int i = 0; i < items.length; i++) {
        if (items[i] != partitionItems[i]) {
          return null;
        }
      }
      if (!Arrays.equals(locations, partitionLocations)) {
        return null;
      }
      int words = grid.rows * grid.wordsPerRow;
      if (!Arrays.equals(grid.obstacleMask, 0, words, partitionObstacleWords, 0, words)) {
        return null;
      }
      return partitionResult;
    }

    /**
     * Returns what a copy of the conduction area can reuse of this memo, detached from the area it
     * was computed for, or null if nothing is valid for {@code currentArea} (the area of the
     * conduction area now). The board must not change while this runs.
     */
    public synchronized FractureMemo detach(Area currentArea) {
      if (currentArea == null) {
        return null;
      }
      FractureMemo detached = new FractureMemo();
      boolean any = false;
      if (copperWords != null && copperArea == currentArea) {
        detached.copperOriginX = copperOriginX;
        detached.copperOriginY = copperOriginY;
        detached.copperCellSize = copperCellSize;
        detached.copperCols = copperCols;
        detached.copperRows = copperRows;
        // never written after it is stored, so it can be shared
        detached.copperWords = copperWords;
        any = true;
      }
      if (areasWords != null && areaShapesAreCurrent()) {
        detached.areasOriginX = areasOriginX;
        detached.areasOriginY = areasOriginY;
        detached.areasCellSize = areasCellSize;
        detached.areasCols = areasCols;
        detached.areasRows = areasRows;
        detached.areasItems = areasItems;
        detached.areasShapeIndices = areasShapeIndices;
        detached.areasShapes = areasShapes;
        detached.areasClearances = areasClearances;
        detached.areasWords = areasWords;
        any = true;
      }
      if (partitionResult != null && partitionArea == currentArea) {
        detached.partitionOriginX = partitionOriginX;
        detached.partitionOriginY = partitionOriginY;
        detached.partitionCellSize = partitionCellSize;
        detached.partitionCols = partitionCols;
        detached.partitionRows = partitionRows;
        detached.partitionObstacleWords = partitionObstacleWords;
        detached.partitionItems = partitionItems;
        detached.partitionLocations = partitionLocations;
        detached.partitionResult = partitionResult;
        any = true;
      }
      return any ? detached : null;
    }

    /** Returns true if every cached obstacle area shape is still the shape of its item. */
    private boolean areaShapesAreCurrent() {
      for (int i = 0; i < areasItems.length; i++) {
        Item item = areasItems[i];
        int index = areasShapeIndices[i];
        if (index >= item.tileShapeCount() || item.getTileShape(index) != areasShapes[i]) {
          return false;
        }
      }
      return true;
    }

    /**
     * Returns this detached memo bound to the copy of its conduction area: {@code copyArea} is the
     * area of the copy, and {@code copyItemById} gives the copy of an item by its id. Every cached
     * input is still compared at lookup, so a copy that differs from its original only misses.
     */
    public FractureMemo attach(Area copyArea, IntFunction<Item> copyItemById) {
      if (copyArea == null) {
        return null;
      }
      FractureMemo attached = new FractureMemo();
      if (copperWords != null) {
        attached.copperArea = copyArea;
        attached.copperOriginX = copperOriginX;
        attached.copperOriginY = copperOriginY;
        attached.copperCellSize = copperCellSize;
        attached.copperCols = copperCols;
        attached.copperRows = copperRows;
        attached.copperWords = copperWords;
      }
      if (areasWords != null) {
        TileShape[] copiedShapes = new TileShape[areasShapes.length];
        Item[] copiedAreaItems = new Item[areasItems.length];
        boolean complete = true;
        for (int i = 0; i < areasItems.length && complete; i++) {
          Item copy = copyItemById.apply(areasItems[i].getId());
          int index = areasShapeIndices[i];
          if (copy == null
              || copy.getClass() != areasItems[i].getClass()
              || index >= copy.tileShapeCount()) {
            complete = false;
          } else {
            copiedAreaItems[i] = copy;
            copiedShapes[i] = copy.getTileShape(index);
          }
        }
        if (complete) {
          attached.areasOriginX = areasOriginX;
          attached.areasOriginY = areasOriginY;
          attached.areasCellSize = areasCellSize;
          attached.areasCols = areasCols;
          attached.areasRows = areasRows;
          attached.areasItems = copiedAreaItems;
          attached.areasShapeIndices = areasShapeIndices;
          attached.areasShapes = copiedShapes;
          attached.areasClearances = areasClearances;
          attached.areasWords = areasWords;
        }
      }
      if (partitionResult != null) {
        Map<Item, Item> copies = new IdentityHashMap<>();
        Item[] copiedItems = new Item[partitionItems.length];
        boolean complete = true;
        for (int i = 0; i < partitionItems.length && complete; i++) {
          Item copy = copyItemById.apply(partitionItems[i].getId());
          if (copy == null || copy.getClass() != partitionItems[i].getClass()) {
            complete = false;
          } else {
            copiedItems[i] = copy;
            copies.put(partitionItems[i], copy);
          }
        }
        Map<Integer, List<Item>> copiedIslands = new HashMap<>();
        for (Map.Entry<Integer, List<Item>> island : partitionResult.islandItems.entrySet()) {
          if (!complete) {
            break;
          }
          List<Item> copiedIsland = new ArrayList<>(island.getValue().size());
          for (Item item : island.getValue()) {
            Item copy = copies.get(item);
            if (copy == null) {
              complete = false;
              break;
            }
            copiedIsland.add(copy);
          }
          copiedIslands.put(island.getKey(), copiedIsland);
        }
        if (complete) {
          attached.partitionArea = copyArea;
          attached.partitionOriginX = partitionOriginX;
          attached.partitionOriginY = partitionOriginY;
          attached.partitionCellSize = partitionCellSize;
          attached.partitionCols = partitionCols;
          attached.partitionRows = partitionRows;
          attached.partitionObstacleWords = partitionObstacleWords;
          attached.partitionItems = copiedItems;
          attached.partitionLocations = partitionLocations;
          attached.partitionResult = new PlaneFractureResult(copiedIslands);
        }
      }
      return attached;
    }

    synchronized void storePartition(
        FastPlaneConnectivityGrid grid,
        Area area,
        Item[] items,
        IntPoint[] locations,
        PlaneFractureResult result) {
      if (area == null) {
        return;
      }
      partitionArea = area;
      partitionOriginX = grid.originX;
      partitionOriginY = grid.originY;
      partitionCellSize = grid.cellSize;
      partitionCols = grid.cols;
      partitionRows = grid.rows;
      partitionObstacleWords = Arrays.copyOf(grid.obstacleMask, grid.rows * grid.wordsPerRow);
      partitionItems = items;
      partitionLocations = locations;
      partitionResult = result;
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

    this.partitionBuffersReady = false;
  }

  /**
   * Prepares the island map and the flood-fill queue. Only a partition needs them, and they are the
   * largest buffers of the grid, so an evaluation that ends before the partition never allocates
   * them.
   */
  private void preparePartitionBuffers() {
    if (this.partitionBuffersReady) {
      return;
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
    this.partitionBuffersReady = true;
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
    return evaluateFracture(ca, board, sameNetItems, null);
  }

  /**
   * Same as {@link #evaluateFracture(ConductionArea, BasicBoard, Collection)}, reusing what {@code
   * memo} kept from the previous evaluation of the same conduction area when its inputs are
   * unchanged. The result is the one a fresh evaluation would return.
   *
   * @param memo the memo of {@code ca}, or null to evaluate from scratch
   */
  public PlaneFractureResult evaluateFracture(
      ConductionArea ca,
      BasicBoard board,
      Collection<? extends Item> sameNetItems,
      FractureMemo memo) {
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
    Area area = ca.getArea();
    if (memo == null || !memo.restoreCopper(this, area)) {
      rasterizeConductionArea(ca);
      if (memo != null) {
        memo.storeCopper(this, area);
      }
    }

    // 2. Query spatial index for obstacles overlapping conduction area
    Set<SearchTreeObject> overlaps = board.overlappingObjects(queryBbox, layer);
    boolean hasForeignObstacles = false;
    List<Item> areaItems = new ArrayList<>();
    List<Integer> areaShapeIndices = new ArrayList<>();
    List<TileShape> areaShapes = new ArrayList<>();
    List<Double> areaClearances = new ArrayList<>();
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
                areaItems.add(item);
                areaShapeIndices.add(i);
                areaShapes.add(tileShape);
                areaClearances.add(clearance + (cellSize + 1.0) / 2.0);
              }
            }
          }
        }
      }
    }
    // The enlarged area shapes are the costly obstacles, and they rarely change: their raster is
    // kept apart, then merged into the obstacle mask (a union, so the order does not matter).
    rasterizeObstacleAreas(areaItems, areaShapeIndices, areaShapes, areaClearances, memo);

    // Without foreign obstacles, only the area's own holes can split it: an area given as an
    // outline minus windows (KiCad exports filled zones this way) can already be several islands.
    if (!hasForeignObstacles && (ca.getArea() == null || ca.getArea().getHoles().length == 0)) {
      Map<Integer, List<Item>> singleIsland = new HashMap<>();
      singleIsland.put(0, new ArrayList<>(sameNetItems));
      return new PlaneFractureResult(singleIsland);
    }

    // 3. Partition same-net items into connected islands using bitwise BFS
    if (memo == null) {
      return partitionItemsByIsland(ca, sameNetItems);
    }
    Item[] items = sameNetItems.toArray(new Item[0]);
    IntPoint[] locations = new IntPoint[items.length];
    for (int i = 0; i < items.length; i++) {
      locations[i] = getItemLocation(items[i], ca);
    }
    PlaneFractureResult previous = memo.lookupPartition(this, area, items, locations);
    if (previous != null) {
      return previous;
    }
    PlaneFractureResult result = partitionItemsByIsland(ca, sameNetItems, items, locations);
    memo.storePartition(this, area, items, locations, result);
    return result;
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

  /**
   * Rasterizes the tile shapes of foreign obstacle areas, each enlarged by its clearance, into the
   * obstacle mask. The raster of exactly these shapes and clearances is taken from {@code memo}
   * when it has it.
   */
  private void rasterizeObstacleAreas(
      List<Item> items,
      List<Integer> shapeIndices,
      List<TileShape> shapes,
      List<Double> clearances,
      FractureMemo memo) {
    if (shapes.isEmpty()) {
      return;
    }
    if (memo == null) {
      for (int i = 0; i < shapes.size(); i++) {
        rasterizeObstacleShape(shapes.get(i).enlarge(clearances.get(i)), obstacleMask);
      }
      return;
    }
    TileShape[] shapeArr = shapes.toArray(new TileShape[0]);
    double[] clearanceArr = new double[clearances.size()];
    for (int i = 0; i < clearanceArr.length; i++) {
      clearanceArr[i] = clearances.get(i);
    }
    int words = rows * wordsPerRow;
    long[] areaWords = memo.lookupObstacleAreas(this, shapeArr, clearanceArr);
    if (areaWords == null) {
      areaWords = new long[words];
      for (int i = 0; i < shapeArr.length; i++) {
        rasterizeObstacleShape(shapeArr[i].enlarge(clearanceArr[i]), areaWords);
      }
      int[] indexArr = new int[shapeIndices.size()];
      for (int i = 0; i < indexArr.length; i++) {
        indexArr[i] = shapeIndices.get(i);
      }
      memo.storeObstacleAreas(
          this, items.toArray(new Item[0]), indexArr, shapeArr, clearanceArr, areaWords);
    }
    for (int w = 0; w < words; w++) {
      obstacleMask[w] |= areaWords[w];
    }
  }

  /** Rasterizes an obstacle shape into the obstacle mask. */
  public void rasterizeObstacleShape(Shape shape) {
    rasterizeObstacleShape(shape, obstacleMask);
  }

  private void rasterizeObstacleShape(Shape shape, long[] mask) {
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
        setRowSpan(mask, r, startCol, endCol);
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
            setRowSpan(mask, r, spanStart, c - 1);
            spanStart = -1;
          }
        }
        if (spanStart != -1) {
          setRowSpan(mask, r, spanStart, endCol);
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
    Item[] itemArr = items.toArray(new Item[0]);
    IntPoint[] locations = new IntPoint[itemArr.length];
    for (int i = 0; i < itemArr.length; i++) {
      locations[i] = getItemLocation(itemArr[i], ca);
    }
    return partitionItemsByIsland(ca, items, itemArr, locations);
  }

  /**
   * Partitions items into islands, given their locations ({@code locations[i]} is {@link
   * #getItemLocation} of {@code itemArr[i]}). The location and the seed cell of an item do not
   * change while the masks stay the same, so both are computed once per item instead of once per
   * island.
   */
  private PlaneFractureResult partitionItemsByIsland(
      ConductionArea ca, Collection<? extends Item> items, Item[] itemArr, IntPoint[] locations) {
    preparePartitionBuffers();
    Map<Integer, List<Item>> islands = new HashMap<>();
    Map<Item, Integer> itemIslandMap = new HashMap<>();
    List<Item> unseededItems = new ArrayList<>();
    Set<Item> unseededSet = new HashSet<>();
    // seed cell index of each item: SEED_UNKNOWN until computed, SEED_NONE if there is none
    int[] seeds = new int[itemArr.length];
    Arrays.fill(seeds, SEED_UNKNOWN);
    int nextIslandId = 0;

    for (int i = 0; i < itemArr.length; i++) {
      Item item = itemArr[i];
      if (itemIslandMap.containsKey(item)) {
        continue;
      }

      if (locations[i] == null) {
        unseededItems.add(item);
        unseededSet.add(item);
        continue;
      }

      int seedIndex = seedCell(seeds, i, locations[i]);
      if (seedIndex == SEED_NONE) {
        unseededItems.add(item);
        unseededSet.add(item);
        continue;
      }

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

      runFloodFill(seedIndex % cols, seedIndex / cols, (short) currentIslandId);

      // Check remaining unassigned items to see if they are reached by this flood fill
      for (int j = 0; j < itemArr.length; j++) {
        Item other = itemArr[j];
        if (!itemIslandMap.containsKey(other) && !unseededSet.contains(other)) {
          if (locations[j] != null) {
            int otherIndex = seedCell(seeds, j, locations[j]);
            if (otherIndex != SEED_NONE && cellIslandMap[otherIndex] == currentIslandId) {
              islands.get(currentIslandId).add(other);
              itemIslandMap.put(other, currentIslandId);
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

  private static final int SEED_UNKNOWN = -2;
  private static final int SEED_NONE = -1;

  /** Returns the seed cell index of the item at {@code index}, computing it on first use. */
  private int seedCell(int[] seeds, int index, IntPoint location) {
    if (seeds[index] == SEED_UNKNOWN) {
      int[] seed =
          findNearestConductiveCell(
              (location.x - originX) / cellSize, (location.y - originY) / cellSize);
      seeds[index] = seed == null ? SEED_NONE : seed[1] * cols + seed[0];
    }
    return seeds[index];
  }

  /**
   * Labels with {@code islandId} every conductive, not yet visited cell 4-connected to the start
   * cell.
   */
  private void runFloodFill(int startCol, int startRow, short islandId) {
    int head = 0;
    int tail = 0;

    int startCell = startRow * cols + startCol;
    queue[tail++] = startCell;
    setBit(visitedMask, startRow, startCol);
    cellIslandMap[startCell] = islandId;

    while (head < tail) {
      int cell = queue[head++];
      int r = cell / cols;
      int c = cell - r * cols;
      if (c + 1 < cols) {
        tail = visitCell(r, c + 1, cell + 1, islandId, tail);
      }
      if (c > 0) {
        tail = visitCell(r, c - 1, cell - 1, islandId, tail);
      }
      if (r + 1 < rows) {
        tail = visitCell(r + 1, c, cell + cols, islandId, tail);
      }
      if (r > 0) {
        tail = visitCell(r - 1, c, cell - cols, islandId, tail);
      }
    }
  }

  /** Visits one in-grid neighbour of the flood fill; returns the new queue tail. */
  private int visitCell(int r, int c, int cell, short islandId, int tail) {
    int wordIdx = r * wordsPerRow + (c >> 6);
    long bit = 1L << (c & 63);
    if ((visitedMask[wordIdx] & bit) == 0L
        && (copperMask[wordIdx] & bit) != 0L
        && (obstacleMask[wordIdx] & bit) == 0L) {
      visitedMask[wordIdx] |= bit;
      cellIslandMap[cell] = islandId;
      queue[tail] = cell;
      return tail + 1;
    }
    return tail;
  }

  private int[] findNearestConductiveCell(int col, int row) {
    if (col >= 0 && col < cols && row >= 0 && row < rows && isConductive(col, row)) {
      return new int[] {col, row};
    }
    // Search expanding neighborhood up to radius 4
    for (int radius = 1; radius <= 4; radius++) {
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
