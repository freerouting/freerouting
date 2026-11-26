package app.freerouting.board.physics;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.BoardOutline;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tier 1 per-layer coarse 2D static field grid backed by flat primitive arrays for L2-cache
 * friendly evaluation without hot-loop allocations.
 *
 * <p>Stores static board features (conduction areas, vias, pins, and board outline) computed once
 * per pass. All array sizes are bounded by {@link #MAX_CELLS_PER_LAYER} via adaptive coarsening.
 */
public final class BoardFieldContext {

  /** Default target cell size in millimeters when none is configured. */
  public static final double DEFAULT_RESOLUTION_MM = 0.5;

  /** Hard per-layer cell cap (512 x 512) to keep field buffers L2/L3-cache friendly. */
  public static final int MAX_CELLS_PER_LAYER = 512 * 512;

  /** Occupancy bitmask for static copper (vias, pins, conduction areas). */
  public static final byte OCCUPANCY_STATIC_COPPER = 0x01;

  /** Occupancy bitmask for board outline boundaries. */
  public static final byte OCCUPANCY_OUTLINE = 0x02;

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
    this.scratchItems.clear();
  }

  /**
   * Rasterizes static board geometry (vias, pins, conduction areas, and board outline) once per
   * pass and applies {@code blurPasses} separable box-blur passes.
   */
  public void rasterizeStaticBoard(BasicBoard board, int blurPasses) {
    clearFields();
    if (board == null) {
      return;
    }

    for (Via via : board.getVias()) {
      FloatPoint center = via.getCenter().toFloat();
      int cx = toCellX(center.x);
      int cy = toCellY(center.y);
      int first = clampLayer(via.firstLayer());
      int last = clampLayer(via.lastLayer());
      for (int l = first; l <= last; l++) {
        stampCell(cellIndex(l, cx, cy), 1.0f, OCCUPANCY_STATIC_COPPER);
      }
    }

    for (Pin pin : board.getPins()) {
      FloatPoint center = pin.getCenter().toFloat();
      int cx = toCellX(center.x);
      int cy = toCellY(center.y);
      int first = clampLayer(pin.firstLayer());
      int last = clampLayer(pin.lastLayer());
      for (int l = first; l <= last; l++) {
        stampCell(cellIndex(l, cx, cy), 1.0f, OCCUPANCY_STATIC_COPPER);
      }
    }

    for (ConductionArea area : board.getConductionAreas()) {
      IntBox box = area.boundingBox();
      if (box != null && !box.isEmpty()) {
        int l = clampLayer(area.getLayer());
        stampBoxPerimeter(l, box, 0.5f, OCCUPANCY_STATIC_COPPER);
      }
    }

    BoardOutline outline = board.getOutline();
    if (outline != null) {
      IntBox ob = outline.boundingBox();
      if (ob != null && !ob.isEmpty()) {
        for (int l = 0; l < this.layerCount; l++) {
          stampBoxPerimeter(l, ob, 0.5f, OCCUPANCY_OUTLINE);
        }
      }
    }

    for (int pass = 0; pass < blurPasses; pass++) {
      blurLayerFields();
    }
  }

  private void stampBoxPerimeter(int layer, IntBox box, float charge, byte mask) {
    stampLine(layer, box.ll.x, box.ll.y, box.ur.x, box.ll.y, charge, mask);
    stampLine(layer, box.ur.x, box.ll.y, box.ur.x, box.ur.y, charge, mask);
    stampLine(layer, box.ur.x, box.ur.y, box.ll.x, box.ur.y, charge, mask);
    stampLine(layer, box.ll.x, box.ur.y, box.ll.x, box.ll.y, charge, mask);
  }

  private void stampLine(
      int layer, double x1, double y1, double x2, double y2, float charge, byte mask) {
    int cx1 = toCellX(x1);
    int cy1 = toCellY(y1);
    int cx2 = toCellX(x2);
    int cy2 = toCellY(y2);
    int steps = Math.max(1, Math.max(Math.abs(cx2 - cx1), Math.abs(cy2 - cy1)));
    for (int s = 0; s <= steps; s++) {
      double t = (double) s / steps;
      int cx = (int) Math.round(cx1 + t * (cx2 - cx1));
      int cy = (int) Math.round(cy1 + t * (cy2 - cy1));
      stampCell(cellIndex(layer, cx, cy), charge, mask);
    }
  }

  /** Stamps a single cell with charge and occupancy mask. */
  public void stampCell(int idx, float charge, byte mask) {
    this.energyField[idx] = Math.max(this.energyField[idx], charge);
    this.occupancyFlags[idx] = (byte) (this.occupancyFlags[idx] | mask);
  }

  private void blurLayerFields() {
    for (int l = 0; l < this.layerCount; l++) {
      int base = l * this.cellsPerLayer;
      for (int y = 0; y < this.gridHeight; y++) {
        int row = y * this.gridWidth;
        for (int x = 0; x < this.gridWidth; x++) {
          int idx = base + row + x;
          float sum = this.energyField[idx];
          int count = 1;
          if (x > 0) {
            sum += this.energyField[idx - 1];
            count++;
          }
          if (x + 1 < this.gridWidth) {
            sum += this.energyField[idx + 1];
            count++;
          }
          this.scratchBlurBuffer[row + x] = sum / count;
        }
      }
      for (int y = 0; y < this.gridHeight; y++) {
        int row = y * this.gridWidth;
        for (int x = 0; x < this.gridWidth; x++) {
          float sum = this.scratchBlurBuffer[row + x];
          int count = 1;
          if (y > 0) {
            sum += this.scratchBlurBuffer[(y - 1) * this.gridWidth + x];
            count++;
          }
          if (y + 1 < this.gridHeight) {
            sum += this.scratchBlurBuffer[(y + 1) * this.gridWidth + x];
            count++;
          }
          float blurred = sum / count;
          int dst = base + row + x;
          if ((this.occupancyFlags[dst] & OCCUPANCY_STATIC_COPPER) != 0) {
            this.energyField[dst] = Math.max(1.0f, blurred);
          } else {
            this.energyField[dst] = blurred;
          }
        }
      }
    }
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
