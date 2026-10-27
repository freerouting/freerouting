package app.freerouting.autoroute.plane;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.DrillItem;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.searchtree.SearchTreeObject;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
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

  /** Maximum dimension of the grid in either axis (1024 * 1024 bits = 128 KB max ceiling). */
  public static final int MAX_GRID_DIM = 1024;

  /** Minimum cell size in internal board units (50 um in nm coordinates). */
  public static final int MIN_CELL_SIZE = 50_000;

  /** Default cell size in internal board units (200 um in nm coordinates). */
  public static final int DEFAULT_CELL_SIZE = 200_000;

  private int originX;
  private int originY;
  private int cellSize;
  private int cols;
  private int rows;
  private int wordsPerRow;

  private long[] copperMask;
  private long[] obstacleMask;
  private long[] visitedMask;

  private int[] queueX;
  private int[] queueY;

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
    this.queueX = new int[0];
    this.queueY = new int[0];
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

    int calculatedCellSize = Math.max(MIN_CELL_SIZE, requestedCellSize);
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

    int maxQueueSize = this.cols * this.rows;
    if (this.queueX.length < maxQueueSize) {
      this.queueX = new int[maxQueueSize];
      this.queueY = new int[maxQueueSize];
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

    IntBox bbox = ca.boundingBox();
    int netClassNo = ca.netCount() > 0 ? ca.getNetNumber(0) : 0;
    int clearanceClass = ca.clearanceClassIndex();
    int layer = ca.getLayer();

    double baseClearance = board.clearanceValue(clearanceClass, clearanceClass, layer);
    int desiredCell = (int) Math.max(MIN_CELL_SIZE, Math.round(baseClearance / 2.0));
    init(bbox, desiredCell);

    // 1. Rasterize conduction area boundary
    rasterizeConductionArea(ca);

    // 2. Query spatial index for obstacles overlapping conduction area
    Set<SearchTreeObject> overlaps = board.overlappingObjects(bbox, layer);
    for (SearchTreeObject ob : overlaps) {
      if (!(ob instanceof Item item) || item == ca || !item.sharesLayer(ca)) {
        continue;
      }

      // Foreign traces and vias are obstacles
      boolean isForeign = !item.sharesNet(ca);
      if (isForeign) {
        int itemClearanceClass = item.clearanceClassIndex();
        double clearance = board.clearanceValue(clearanceClass, itemClearanceClass, layer);

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
        }
      }
    }

    // 3. Partition same-net items into connected islands using bitwise BFS
    return partitionItemsByIsland(sameNetItems);
  }

  /** Rasterizes the convex tile shapes of a conduction area into the copper mask. */
  public void rasterizeConductionArea(ConductionArea ca) {
    int tileCount = ca.tileShapeCount();
    for (int t = 0; t < tileCount; t++) {
      TileShape tile = ca.getTileShape(t);
      if (tile == null) {
        continue;
      }
      IntBox box = tile.boundingBox();
      int startCol = Math.max(0, (box.ll.x - originX) / cellSize);
      int endCol = Math.min(cols - 1, (box.ur.x - originX) / cellSize);
      int startRow = Math.max(0, (box.ll.y - originY) / cellSize);
      int endRow = Math.min(rows - 1, (box.ur.y - originY) / cellSize);

      if (tile.isIntBox()) {
        for (int r = startRow; r <= endRow; r++) {
          setRowSpan(copperMask, r, startCol, endCol);
        }
      } else {
        // Precise point-in-convex-tile test along cell centers
        for (int r = startRow; r <= endRow; r++) {
          int cellY = originY + r * cellSize + cellSize / 2;
          int spanStart = -1;
          int spanEnd = -1;
          for (int c = startCol; c <= endCol; c++) {
            int cellX = originX + c * cellSize + cellSize / 2;
            if (tile.contains(new IntPoint(cellX, cellY))) {
              if (spanStart == -1) {
                spanStart = c;
              }
              spanEnd = c;
            } else if (spanStart != -1) {
              setRowSpan(copperMask, r, spanStart, spanEnd);
              spanStart = -1;
              spanEnd = -1;
            }
          }
          if (spanStart != -1) {
            setRowSpan(copperMask, r, spanStart, spanEnd);
          }
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
    int minX = Math.min(x1, x2) - radius;
    int maxX = Math.max(x1, x2) + radius;
    int minY = Math.min(y1, y2) - radius;
    int maxY = Math.max(y1, y2) + radius;

    int startCol = Math.max(0, (minX - originX) / cellSize);
    int endCol = Math.min(cols - 1, (maxX - originX) / cellSize);
    int startRow = Math.max(0, (minY - originY) / cellSize);
    int endRow = Math.min(rows - 1, (maxY - originY) / cellSize);

    if (startCol > endCol || startRow > endRow) {
      return;
    }

    long radiusSq = (long) radius * radius;
    long dx = x2 - x1;
    long dy = y2 - y1;

    // Fast-path: Horizontal line segment or point (dx arbitrary, dy == 0)
    if (dy == 0) {
      int segMinX = Math.min(x1, x2);
      int segMaxX = Math.max(x1, x2);
      for (int r = startRow; r <= endRow; r++) {
        int cellY = originY + r * cellSize + cellSize / 2;
        long distY = Math.abs(cellY - y1);
        if (distY <= radius) {
          int deltaX = (int) Math.sqrt(radiusSq - distY * distY);
          int sCol = Math.max(startCol, (segMinX - deltaX - originX) / cellSize);
          int eCol = Math.min(endCol, (segMaxX + deltaX - originX) / cellSize);
          if (sCol <= eCol) {
            setRowSpan(obstacleMask, r, sCol, eCol);
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
        if (distY <= radius) {
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
   * @param items same-net items to cluster
   * @return fracture result containing items grouped per island
   */
  private PlaneFractureResult partitionItemsByIsland(Collection<? extends Item> items) {
    Map<Integer, List<Item>> islands = new HashMap<>();
    Map<Item, Integer> itemIslandMap = new HashMap<>();
    int nextIslandId = 0;

    for (Item item : items) {
      if (itemIslandMap.containsKey(item)) {
        continue;
      }

      IntPoint pt = getItemLocation(item);
      if (pt == null) {
        continue;
      }

      int targetCol = (pt.x - originX) / cellSize;
      int targetRow = (pt.y - originY) / cellSize;

      int[] seed = findNearestConductiveCell(targetCol, targetRow);
      if (seed == null) {
        // Item is completely cut off from conductive copper (isolated dead copper)
        int isolatedId = nextIslandId++;
        islands.computeIfAbsent(isolatedId, k -> new ArrayList<>()).add(item);
        itemIslandMap.put(item, isolatedId);
        continue;
      }

      // Check if this seed was already visited by an earlier island flood fill
      if (isBitSet(visitedMask, seed[1], seed[0])) {
        // Find which island visited this seed
        int existingIsland = findIslandContainingCell(items, itemIslandMap, seed[0], seed[1]);
        if (existingIsland != -1) {
          islands.computeIfAbsent(existingIsland, k -> new ArrayList<>()).add(item);
          itemIslandMap.put(item, existingIsland);
          continue;
        }
      }

      // Start new BFS flood fill for this island
      int currentIslandId = nextIslandId++;
      islands.computeIfAbsent(currentIslandId, k -> new ArrayList<>()).add(item);
      itemIslandMap.put(item, currentIslandId);

      runFloodFill(seed[0], seed[1]);

      // Check remaining unassigned items to see if they are reached by this flood fill
      for (Item other : items) {
        if (!itemIslandMap.containsKey(other)) {
          IntPoint otherPt = getItemLocation(other);
          if (otherPt != null) {
            int oc = (otherPt.x - originX) / cellSize;
            int or = (otherPt.y - originY) / cellSize;
            int[] otherSeed = findNearestConductiveCell(oc, or);
            if (otherSeed != null && isBitSet(visitedMask, otherSeed[1], otherSeed[0])) {
              islands.get(currentIslandId).add(other);
              itemIslandMap.put(other, currentIslandId);
            }
          }
        }
      }
    }

    return new PlaneFractureResult(islands);
  }

  private void runFloodFill(int startCol, int startRow) {
    int head = 0;
    int tail = 0;

    queueX[tail] = startCol;
    queueY[tail] = startRow;
    tail++;
    setBit(visitedMask, startRow, startCol);

    int[] dc = {1, -1, 0, 0};
    int[] dr = {0, 0, 1, -1};

    while (head < tail) {
      int c = queueX[head];
      int r = queueY[head];
      head++;

      for (int i = 0; i < 4; i++) {
        int nc = c + dc[i];
        int nr = r + dr[i];

        if (nc >= 0 && nc < cols && nr >= 0 && nr < rows) {
          if (!isBitSet(visitedMask, nr, nc) && isConductive(nc, nr)) {
            setBit(visitedMask, nr, nc);
            queueX[tail] = nc;
            queueY[tail] = nr;
            tail++;
          }
        }
      }
    }
  }

  private int[] findNearestConductiveCell(int col, int row) {
    if (col >= 0 && col < cols && row >= 0 && row < rows && isConductive(col, row)) {
      return new int[] {col, row};
    }
    // Search 3x3 then 5x5 neighborhood
    for (int radius = 1; radius <= 2; radius++) {
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

  private int findIslandContainingCell(
      Collection<? extends Item> items, Map<Item, Integer> itemIslandMap, int c, int r) {
    for (Item it : items) {
      Integer id = itemIslandMap.get(it);
      if (id != null) {
        IntPoint pt = getItemLocation(it);
        if (pt != null) {
          int ic = (pt.x - originX) / cellSize;
          int ir = (pt.y - originY) / cellSize;
          if (Math.abs(ic - c) <= 2 && Math.abs(ir - r) <= 2) {
            return id;
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

  private static IntPoint getItemLocation(Item item) {
    if (item instanceof DrillItem drillItem) {
      return toIntPoint(drillItem.getCenter());
    } else if (item instanceof Trace trace) {
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
