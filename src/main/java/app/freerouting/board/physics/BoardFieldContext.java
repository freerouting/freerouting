package app.freerouting.board.physics;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.BoardOutline;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.Polyline;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Per-layer coarse 2D field grid backed by flat primitive arrays for cache-friendly physics
 * evaluation without hot-loop allocations.
 *
 * <p>All array sizes are bounded by {@link #MAX_CELLS_PER_LAYER} via adaptive coarsening so large
 * boards or fine requested resolutions cannot overflow 32-bit indexing or exhaust heap memory.
 */
public final class BoardFieldContext {

  /** Default target cell size in millimeters when none is configured. */
  public static final double DEFAULT_RESOLUTION_MM = 0.5;

  /** Hard per-layer cell cap (512 x 512) to keep field buffers L2/L3-cache friendly. */
  public static final int MAX_CELLS_PER_LAYER = 512 * 512;

  /** Occupancy bitmask for copper traces, vias, and pins. */
  public static final byte OCCUPANCY_COPPER = 0x01;

  /** Occupancy bitmask for board outline boundaries. */
  public static final byte OCCUPANCY_OUTLINE = 0x02;

  /** Sentinel in {@link #netIdField} indicating multiple nets or a board outline. */
  public static final int MULTI_NET_OR_OUTLINE = -1;

  private static final int DEFAULT_SCRATCH_CAPACITY = 64;

  private final int layerCount;
  private final int gridWidth;
  private final int gridHeight;
  private final int cellsPerLayer;
  private final long originX;
  private final long originY;
  private final double cellSizeBoardUnits;
  private final double effectiveResolutionMm;

  private final float[] energyField;
  private final float[] scratchBlurBuffer;
  private final byte[] occupancyFlags;
  private final int[] netIdField;
  private final int[] scratchNetBuffer;
  private final List<Item> scratchItems;
  private int[] scratchCellIndices;

  private BoardFieldContext(
      int layerCount,
      int gridWidth,
      int gridHeight,
      long originX,
      long originY,
      double cellSizeBoardUnits,
      double effectiveResolutionMm) {
    this.layerCount = layerCount;
    this.gridWidth = gridWidth;
    this.gridHeight = gridHeight;
    this.cellsPerLayer = gridWidth * gridHeight;
    this.originX = originX;
    this.originY = originY;
    this.cellSizeBoardUnits = cellSizeBoardUnits;
    this.effectiveResolutionMm = effectiveResolutionMm;

    int totalCells = Math.multiplyExact(layerCount, this.cellsPerLayer);
    this.energyField = new float[totalCells];
    this.scratchBlurBuffer = new float[this.cellsPerLayer];
    this.occupancyFlags = new byte[totalCells];
    this.netIdField = new int[totalCells];
    this.scratchNetBuffer = new int[this.cellsPerLayer];
    this.scratchItems = new ArrayList<>(DEFAULT_SCRATCH_CAPACITY);
    this.scratchCellIndices = new int[DEFAULT_SCRATCH_CAPACITY];
  }

  /**
   * Creates a coarse field context quantized from {@code board}'s bounding box.
   *
   * @param board target board
   * @param requestedResolutionMm desired cell size in millimeters (must be positive and finite)
   * @return initialized field context
   */
  public static BoardFieldContext fromBoard(BasicBoard board, double requestedResolutionMm) {
    if (board == null) {
      throw new IllegalArgumentException("board must not be null");
    }
    double resolutionMm =
        (Double.isFinite(requestedResolutionMm) && requestedResolutionMm > 0.0)
            ? requestedResolutionMm
            : DEFAULT_RESOLUTION_MM;

    int layers = Math.max(1, board.getLayerCount());
    IntBox bounds = resolveBoardBounds(board);
    long spanX = Math.max(1L, (long) bounds.ur.x - (long) bounds.ll.x);
    long spanY = Math.max(1L, (long) bounds.ur.y - (long) bounds.ll.y);

    double unitsPerMm =
        (board.communication != null) ? board.communication.getResolution(Unit.MM) : 1.0;
    if (!Double.isFinite(unitsPerMm) || unitsPerMm <= 0.0) {
      unitsPerMm = 1.0;
    }

    double cellSizeUnits = Math.max(1.0, resolutionMm * unitsPerMm);
    long rawWidth = Math.max(1L, (long) Math.ceil(spanX / cellSizeUnits));
    long rawHeight = Math.max(1L, (long) Math.ceil(spanY / cellSizeUnits));

    double rawCells = (double) rawWidth * (double) rawHeight;
    double maxCells = Math.max(1.0, (double) MAX_CELLS_PER_LAYER / layers);
    double effectiveCellSizeUnits = cellSizeUnits;
    if (rawCells > maxCells) {
      double scale = Math.sqrt(rawCells / maxCells);
      effectiveCellSizeUnits = cellSizeUnits * scale;
      rawWidth = Math.max(1L, (long) Math.ceil(spanX / effectiveCellSizeUnits));
      rawHeight = Math.max(1L, (long) Math.ceil(spanY / effectiveCellSizeUnits));
      while ((rawWidth * rawHeight) > (long) maxCells && (rawWidth > 1L || rawHeight > 1L)) {
        effectiveCellSizeUnits *= 1.05;
        rawWidth = Math.max(1L, (long) Math.ceil(spanX / effectiveCellSizeUnits));
        rawHeight = Math.max(1L, (long) Math.ceil(spanY / effectiveCellSizeUnits));
      }
    }

    int finalWidth = (int) Math.max(1L, rawWidth);
    int finalHeight = (int) Math.max(1L, rawHeight);
    double effectiveMm = effectiveCellSizeUnits / unitsPerMm;

    return new BoardFieldContext(
        layers,
        finalWidth,
        finalHeight,
        bounds.ll.x,
        bounds.ll.y,
        effectiveCellSizeUnits,
        effectiveMm);
  }

  private static IntBox resolveBoardBounds(BasicBoard board) {
    IntBox box = board.getBoundingBox();
    if (box != null && !box.isEmpty()) {
      return box;
    }
    BoardOutline outline = board.getOutline();
    if (outline != null) {
      IntBox outlineBox = outline.boundingBox();
      if (outlineBox != null && !outlineBox.isEmpty()) {
        return outlineBox;
      }
    }
    return new IntBox(0, 0, 1, 1);
  }

  /** Zeroes all field accumulators and clears reusable scratch buffers without reallocation. */
  public void clearFields() {
    Arrays.fill(this.energyField, 0.0f);
    Arrays.fill(this.occupancyFlags, (byte) 0);
    Arrays.fill(this.netIdField, 0);
    this.scratchItems.clear();
  }

  /**
   * Rasterizes copper traces, vias, pins, and board outline into the grid and applies {@code
   * blurPasses} separable box-blur passes to create a smooth repulsive potential field.
   */
  public void rasterizeBoard(BasicBoard board, int blurPasses) {
    clearFields();
    if (board == null) {
      return;
    }

    for (Trace trace : board.getTraces()) {
      if (trace instanceof PolylineTrace polylineTrace) {
        int layer = clampLayer(polylineTrace.getLayer());
        int netNo = polylineTrace.netCount() > 0 ? polylineTrace.getNetNumber(0) : 0;
        Polyline polyline = polylineTrace.polyline();
        int corners = polyline.cornerCount();
        for (int i = 0; i < corners - 1; i++) {
          FloatPoint p1 = polyline.corner(i).toFloat();
          FloatPoint p2 = polyline.corner(i + 1).toFloat();
          stampSegment(layer, netNo, p1.x, p1.y, p2.x, p2.y, 1.0f, OCCUPANCY_COPPER);
        }
      }
    }

    for (Via via : board.getVias()) {
      int netNo = via.netCount() > 0 ? via.getNetNumber(0) : 0;
      FloatPoint center = via.getCenter().toFloat();
      int cx = toCellX(center.x);
      int cy = toCellY(center.y);
      int first = clampLayer(via.firstLayer());
      int last = clampLayer(via.lastLayer());
      for (int l = first; l <= last; l++) {
        stampCell(cellIndex(l, cx, cy), netNo, 1.0f, OCCUPANCY_COPPER);
      }
    }

    for (Pin pin : board.getPins()) {
      int netNo = pin.netCount() > 0 ? pin.getNetNumber(0) : 0;
      FloatPoint center = pin.getCenter().toFloat();
      int cx = toCellX(center.x);
      int cy = toCellY(center.y);
      int first = clampLayer(pin.firstLayer());
      int last = clampLayer(pin.lastLayer());
      for (int l = first; l <= last; l++) {
        stampCell(cellIndex(l, cx, cy), netNo, 1.0f, OCCUPANCY_COPPER);
      }
    }

    BoardOutline outline = board.getOutline();
    if (outline != null) {
      IntBox ob = outline.boundingBox();
      if (ob != null && !ob.isEmpty()) {
        for (int l = 0; l < this.layerCount; l++) {
          stampSegment(
              l, MULTI_NET_OR_OUTLINE, ob.ll.x, ob.ll.y, ob.ur.x, ob.ll.y, 0.5f, OCCUPANCY_OUTLINE);
          stampSegment(
              l, MULTI_NET_OR_OUTLINE, ob.ur.x, ob.ll.y, ob.ur.x, ob.ur.y, 0.5f, OCCUPANCY_OUTLINE);
          stampSegment(
              l, MULTI_NET_OR_OUTLINE, ob.ur.x, ob.ur.y, ob.ll.x, ob.ur.y, 0.5f, OCCUPANCY_OUTLINE);
          stampSegment(
              l, MULTI_NET_OR_OUTLINE, ob.ll.x, ob.ur.y, ob.ll.x, ob.ll.y, 0.5f, OCCUPANCY_OUTLINE);
        }
      }
    }

    for (int pass = 0; pass < blurPasses; pass++) {
      blurLayerFields();
    }
  }

  private void stampSegment(
      int layer, int netNo, double x1, double y1, double x2, double y2, float charge, byte mask) {
    int cx1 = toCellX(x1);
    int cy1 = toCellY(y1);
    int cx2 = toCellX(x2);
    int cy2 = toCellY(y2);
    int steps = Math.max(1, Math.max(Math.abs(cx2 - cx1), Math.abs(cy2 - cy1)));
    for (int s = 0; s <= steps; s++) {
      double t = (double) s / steps;
      int cx = (int) Math.round(cx1 + t * (cx2 - cx1));
      int cy = (int) Math.round(cy1 + t * (cy2 - cy1));
      stampCell(cellIndex(layer, cx, cy), netNo, charge, mask);
    }
  }

  /** Stamps a single cell with charge, occupancy mask, and net-ownership tracking. */
  public void stampCell(int idx, int netNo, float charge, byte mask) {
    this.energyField[idx] = Math.max(this.energyField[idx], charge);
    this.occupancyFlags[idx] = (byte) (this.occupancyFlags[idx] | mask);
    int existing = this.netIdField[idx];
    if (existing == 0) {
      this.netIdField[idx] = netNo;
    } else if (existing != netNo) {
      this.netIdField[idx] = MULTI_NET_OR_OUTLINE;
    }
  }

  private void blurLayerFields() {
    for (int l = 0; l < this.layerCount; l++) {
      int base = l * this.cellsPerLayer;
      // Horizontal 3-tap pass into scratchBlurBuffer & scratchNetBuffer
      for (int y = 0; y < this.gridHeight; y++) {
        int row = y * this.gridWidth;
        for (int x = 0; x < this.gridWidth; x++) {
          int idx = base + row + x;
          float sum = this.energyField[idx];
          int net = this.netIdField[idx];
          int count = 1;
          if (x > 0) {
            sum += this.energyField[idx - 1];
            net = mergeNetId(net, this.netIdField[idx - 1]);
            count++;
          }
          if (x + 1 < this.gridWidth) {
            sum += this.energyField[idx + 1];
            net = mergeNetId(net, this.netIdField[idx + 1]);
            count++;
          }
          this.scratchBlurBuffer[row + x] = sum / count;
          this.scratchNetBuffer[row + x] = net;
        }
      }
      // Vertical 3-tap pass back into energyField & netIdField (preserving copper peak charge)
      for (int y = 0; y < this.gridHeight; y++) {
        int row = y * this.gridWidth;
        for (int x = 0; x < this.gridWidth; x++) {
          float sum = this.scratchBlurBuffer[row + x];
          int net = this.scratchNetBuffer[row + x];
          int count = 1;
          if (y > 0) {
            int up = (y - 1) * this.gridWidth + x;
            sum += this.scratchBlurBuffer[up];
            net = mergeNetId(net, this.scratchNetBuffer[up]);
            count++;
          }
          if (y + 1 < this.gridHeight) {
            int down = (y + 1) * this.gridWidth + x;
            sum += this.scratchBlurBuffer[down];
            net = mergeNetId(net, this.scratchNetBuffer[down]);
            count++;
          }
          float blurred = sum / count;
          int dst = base + row + x;
          if ((this.occupancyFlags[dst] & OCCUPANCY_COPPER) != 0) {
            this.energyField[dst] = Math.max(1.0f, blurred);
          } else {
            this.energyField[dst] = blurred;
          }
          this.netIdField[dst] = net;
        }
      }
    }
  }

  private static int mergeNetId(int a, int b) {
    if (a == 0) {
      return b;
    }
    if (b == 0 || a == b) {
      return a;
    }
    return MULTI_NET_OR_OUTLINE;
  }

  /** Maps a board X coordinate to a clamped grid column index in {@code [0, gridWidth - 1]}. */
  public int toCellX(double boardX) {
    int cx = (int) Math.floor((boardX - this.originX) / this.cellSizeBoardUnits);
    if (cx < 0) {
      return 0;
    }
    return Math.min(cx, this.gridWidth - 1);
  }

  /** Maps a board Y coordinate to a clamped grid row index in {@code [0, gridHeight - 1]}. */
  public int toCellY(double boardY) {
    int cy = (int) Math.floor((boardY - this.originY) / this.cellSizeBoardUnits);
    if (cy < 0) {
      return 0;
    }
    return Math.min(cy, this.gridHeight - 1);
  }

  /** Clamps a layer index to {@code [0, layerCount - 1]}. */
  public int clampLayer(int layer) {
    if (layer < 0) {
      return 0;
    }
    return Math.min(layer, this.layerCount - 1);
  }

  /** Computes the flat array index for {@code (layer, cx, cy)} with bounds clamping. */
  public int cellIndex(int layer, int cx, int cy) {
    int l = clampLayer(layer);
    int x = Math.max(0, Math.min(cx, this.gridWidth - 1));
    int y = Math.max(0, Math.min(cy, this.gridHeight - 1));
    return (l * this.cellsPerLayer) + (y * this.gridWidth) + x;
  }

  /** Returns the field value at flat index {@code idx}. */
  public float getEnergy(int idx) {
    return this.energyField[idx];
  }

  /** Adds {@code delta} to the field accumulator at flat index {@code idx}. */
  public void addEnergy(int idx, float delta) {
    this.energyField[idx] += delta;
  }

  /** Returns the occupancy byte flags at flat index {@code idx}. */
  public byte getOccupancy(int idx) {
    return this.occupancyFlags[idx];
  }

  /** Bitwise-ORs {@code mask} into the occupancy flags at flat index {@code idx}. */
  public void markOccupancy(int idx, byte mask) {
    this.occupancyFlags[idx] = (byte) (this.occupancyFlags[idx] | mask);
  }

  /** Returns the net ID owning cell {@code idx}, {@code 0} if empty, or {@code -1} if shared. */
  public int getNetId(int idx) {
    return this.netIdField[idx];
  }

  /** Returns the reusable scratch item list (cleared by caller before use). */
  public List<Item> getScratchItems() {
    return this.scratchItems;
  }

  /** Returns a reusable scratch {@code int[]} buffer of at least {@code minCapacity} elements. */
  public int[] acquireScratchCellIndices(int minCapacity) {
    if (this.scratchCellIndices.length < minCapacity) {
      this.scratchCellIndices = new int[Math.max(minCapacity, this.scratchCellIndices.length * 2)];
    }
    return this.scratchCellIndices;
  }

  public int getLayerCount() {
    return this.layerCount;
  }

  public int getGridWidth() {
    return this.gridWidth;
  }

  public int getGridHeight() {
    return this.gridHeight;
  }

  public int getCellsPerLayer() {
    return this.cellsPerLayer;
  }

  public long getOriginX() {
    return this.originX;
  }

  public long getOriginY() {
    return this.originY;
  }

  public double getCellSizeBoardUnits() {
    return this.cellSizeBoardUnits;
  }

  public double getEffectiveResolutionMm() {
    return this.effectiveResolutionMm;
  }
}
