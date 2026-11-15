package app.freerouting.autoroute.fanout;

import app.freerouting.autoroute.fanout.BgaEscapeDescriptor.BgaPinDescriptor;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.RegularTileShape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.NetClass;
import app.freerouting.settings.RouterSettings;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Marshals BGA pins, nearby board obstacles, and clearance/layer rules into flat, 64-byte aligned
 * off-heap {@link MemorySegment} buffers for OpenCL execution.
 *
 * <p>Enforces bounded pitch-harmonic grid quantization and conservative DRC obstacle inflation.
 */
public final class BgaBufferMarshaller {

  /** Bundled off-heap buffers and grid metadata for GPU dispatch. */
  public record MarshaledBgaData(
      Arena arena,
      MemorySegment pinBuffer,
      int pinCount,
      MemorySegment obstacleBuffer,
      int obstacleCount,
      MemorySegment ruleBuffer,
      int gridStepBoardUnits,
      int gridWidth,
      int gridHeight,
      int layerCount,
      int originX,
      int originY)
      implements AutoCloseable {

    @Override
    public void close() {
      if (arena != null && arena.scope().isAlive()) {
        arena.close();
      }
    }
  }

  private BgaBufferMarshaller() {}

  /**
   * Computes the pitch-harmonic grid step $\Delta_{\text{grid}}$ in board units.
   *
   * <pre>
   * Δgrid = clamp( min( ⌊P / 8⌋, ⌊(wmin + cmin) / 2⌋ ), Δmin, Δmax )
   * where Δmin = (P ≤ 500 µm) ? max(5 µm, ⌊P / 32⌋) : max(10 µm, ⌊P / 16⌋)
   * </pre>
   */
  public static int computeGridStep(RoutingBoard board, double pitchUm, double pitchBoardUnits) {
    double res = board.communication.getResolution(Unit.UM);
    int p = (int) Math.round(pitchBoardUnits);

    int minWidth = board.rules.getMinAllowedTraceHalfWidth() * 2;
    int minClearance = board.rules.clearanceMatrix.getValue(0, 0, 0, false);
    if (minClearance <= 0) {
      minClearance = (int) Math.round(100.0 * res);
    }

    int candidate = Math.min(p / 8, (minWidth + minClearance) / 2);

    int deltaMin;
    if (pitchUm <= 500.0) {
      int fiveUm = (int) Math.max(1, Math.round(5.0 * res));
      deltaMin = Math.max(fiveUm, p / 32);
    } else {
      int tenUm = (int) Math.max(1, Math.round(10.0 * res));
      deltaMin = Math.max(tenUm, p / 16);
    }
    deltaMin = Math.max(1, deltaMin);

    int deltaMax = Math.max(deltaMin, p / 4);

    return Math.clamp(candidate, deltaMin, deltaMax);
  }

  /** Marshals the given BGA package and board geometry into flat off-heap buffers. */
  public static MarshaledBgaData marshal(
      RoutingBoard board, BgaEscapeDescriptor bga, RouterSettings settings) {

    int gridStep = computeGridStep(board, bga.pitchUm(), bga.pitchBoardUnits());
    // Conservative obstacle inflation: +⌈Δgrid · √2 / 2⌉
    int inflation = (int) Math.ceil(gridStep * 0.7071067811865475);

    double maxEscapeMm =
        (settings != null && settings.fanout != null && settings.fanout.maxEscapeLengthMm != null)
            ? settings.fanout.maxEscapeLengthMm
            : 4.5;
    double res = board.communication.getResolution(Unit.UM);
    int escapeMarginBoardUnits = (int) Math.round(maxEscapeMm * 1000.0 * res);

    int localMinX = (int) Math.floor(bga.minX()) - escapeMarginBoardUnits;
    int localMaxX = (int) Math.ceil(bga.maxX()) + escapeMarginBoardUnits;
    int localMinY = (int) Math.floor(bga.minY()) - escapeMarginBoardUnits;
    int localMaxY = (int) Math.ceil(bga.maxY()) + escapeMarginBoardUnits;

    int gridW = Math.max(1, (localMaxX - localMinX) / gridStep + 1);
    int gridH = Math.max(1, (localMaxY - localMinY) / gridStep + 1);
    int layers = board.getLayerCount();

    // Check VRAM budget
    long maxVramMb =
        (settings != null && settings.fanout != null && settings.fanout.gpuMaxVramMb != null)
            ? settings.fanout.gpuMaxVramMb
            : 256;
    long estimatedBytes = (long) gridW * gridH * layers * 4L;
    if (estimatedBytes > maxVramMb * 1024L * 1024L) {
      // Coarsen grid step once by 1.5x
      gridStep = (int) Math.round(gridStep * 1.5);
      inflation = (int) Math.ceil(gridStep * 0.7071067811865475);
      gridW = Math.max(1, (localMaxX - localMinX) / gridStep + 1);
      gridH = Math.max(1, (localMaxY - localMinY) / gridStep + 1);
    }

    Arena arena = Arena.ofShared();

    // 1. Populate PinBuffer (int32[N_pins * 12])
    List<BgaPinDescriptor> pins = bga.pins();
    int pinCount = pins.size();
    MemorySegment pinBuf =
        arena.allocate((long) pinCount * 12 * ValueLayout.JAVA_INT.byteSize(), 64);

    for (int i = 0; i < pinCount; i++) {
      BgaPinDescriptor pd = pins.get(i);
      long offset = (long) i * 12 * ValueLayout.JAVA_INT.byteSize();

      int u = (int) Math.round(pd.localU());
      int v = (int) Math.round(pd.localV());

      // Default half-plane bounds around pin pad
      int halfW = (int) Math.round(bga.pitchBoardUnits() * 0.25) + inflation;
      int lx = u - halfW;
      int rx = u + halfW;
      int ly = v - halfW;
      int uy = v + halfW;
      int ulx = u - halfW;
      int lrx = u + halfW;
      int llx = u - halfW;
      int urx = u + halfW;

      pinBuf.set(ValueLayout.JAVA_INT, offset, u);
      pinBuf.set(ValueLayout.JAVA_INT, offset + 4, v);
      pinBuf.set(ValueLayout.JAVA_INT, offset + 8, lx);
      pinBuf.set(ValueLayout.JAVA_INT, offset + 12, rx);
      pinBuf.set(ValueLayout.JAVA_INT, offset + 16, ly);
      pinBuf.set(ValueLayout.JAVA_INT, offset + 20, uy);
      pinBuf.set(ValueLayout.JAVA_INT, offset + 24, ulx);
      pinBuf.set(ValueLayout.JAVA_INT, offset + 28, lrx);
      pinBuf.set(ValueLayout.JAVA_INT, offset + 32, pd.netNo());
      pinBuf.set(ValueLayout.JAVA_INT, offset + 36, pd.clearanceClassIdx());
      pinBuf.set(ValueLayout.JAVA_INT, offset + 40, pd.ring());
      pinBuf.set(ValueLayout.JAVA_INT, offset + 44, pd.disposition().ordinal());
    }

    // 2. Query obstacles from searchTreeManager
    // Compute component centroid and global bounding box around BGA pins
    double sumX = 0;
    double sumY = 0;
    int minGlobalX = Integer.MAX_VALUE;
    int maxGlobalX = Integer.MIN_VALUE;
    int minGlobalY = Integer.MAX_VALUE;
    int maxGlobalY = Integer.MIN_VALUE;

    for (BgaPinDescriptor pd : pins) {
      Point c = pd.pin().getCenter();
      if (c != null) {
        FloatPoint fp = c.toFloat();
        sumX += fp.x;
        sumY += fp.y;
        minGlobalX = Math.min(minGlobalX, (int) Math.floor(fp.x));
        maxGlobalX = Math.max(maxGlobalX, (int) Math.ceil(fp.x));
        minGlobalY = Math.min(minGlobalY, (int) Math.floor(fp.y));
        maxGlobalY = Math.max(maxGlobalY, (int) Math.ceil(fp.y));
      }
    }

    double centroidX = pinCount > 0 ? sumX / pinCount : 0;
    double centroidY = pinCount > 0 ? sumY / pinCount : 0;

    double rotDeg = bga.rotationDeg();
    double rad = Math.toRadians(-rotDeg);
    double cos = Math.cos(rad);
    double sin = Math.sin(rad);

    IntBox searchBox =
        new IntBox(
            new IntPoint(minGlobalX - escapeMarginBoardUnits, minGlobalY - escapeMarginBoardUnits),
            new IntPoint(maxGlobalX + escapeMarginBoardUnits, maxGlobalY + escapeMarginBoardUnits));

    record LocalObstacle(
        int minU,
        int maxU,
        int minV,
        int maxV,
        int ulx,
        int lrx,
        int llx,
        int urx,
        int layer,
        int clearanceClass) {}

    List<LocalObstacle> localObstacles = new ArrayList<>();
    int[] emptyNets = new int[0];

    for (int l = 0; l < layers; l++) {
      Collection<Item> obstacles =
          board
              .searchTreeManager
              .getDefaultTree()
              .overlappingItemsWithClearance(searchBox, l, emptyNets, 0);
      for (Item item : obstacles) {
        if (item instanceof Pin p && p.getComponentId() == bga.component().id) {
          continue; // Already handled in pinBuffer
        }
        TileShape shape = item.getTreeShape(board.searchTreeManager.getDefaultTree(), l);
        if (shape instanceof RegularTileShape rts) {
          IntOctagon oct = rts.boundingOctagon();
          // Transform obstacle corners from global board space into component-local (u, v) space
          int[] xs = new int[] {oct.leftX, oct.rightX, oct.leftX, oct.rightX};
          int[] ys = new int[] {oct.bottomY, oct.bottomY, oct.topY, oct.topY};

          double uMin = Double.MAX_VALUE;
          double uMax = -Double.MAX_VALUE;
          double vMin = Double.MAX_VALUE;
          double vMax = -Double.MAX_VALUE;

          for (int k = 0; k < 4; k++) {
            double dx = xs[k] - centroidX;
            double dy = ys[k] - centroidY;
            double u = dx * cos - dy * sin;
            double v = dx * sin + dy * cos;
            if (u < uMin) uMin = u;
            if (u > uMax) uMax = u;
            if (v < vMin) vMin = v;
            if (v > vMax) vMax = v;
          }

          int lu = (int) Math.floor(uMin) - inflation;
          int ru = (int) Math.ceil(uMax) + inflation;
          int lv = (int) Math.floor(vMin) - inflation;
          int uv = (int) Math.ceil(vMax) + inflation;

          int ulx = lu - uv;
          int lrx = ru - lv;
          int llx = lu + lv;
          int urx = ru + uv;

          localObstacles.add(
              new LocalObstacle(lu, ru, lv, uv, ulx, lrx, llx, urx, l, item.clearanceClassIndex()));
        }
      }
    }

    int obsCount = localObstacles.size();
    MemorySegment obsBuf =
        arena.allocate((long) Math.max(1, obsCount) * 10 * ValueLayout.JAVA_INT.byteSize(), 64);
    for (int i = 0; i < obsCount; i++) {
      LocalObstacle obs = localObstacles.get(i);
      long offset = (long) i * 10 * ValueLayout.JAVA_INT.byteSize();
      obsBuf.set(ValueLayout.JAVA_INT, offset, obs.minU());
      obsBuf.set(ValueLayout.JAVA_INT, offset + 4, obs.maxU());
      obsBuf.set(ValueLayout.JAVA_INT, offset + 8, obs.minV());
      obsBuf.set(ValueLayout.JAVA_INT, offset + 12, obs.maxV());
      obsBuf.set(ValueLayout.JAVA_INT, offset + 16, obs.ulx());
      obsBuf.set(ValueLayout.JAVA_INT, offset + 20, obs.lrx());
      obsBuf.set(ValueLayout.JAVA_INT, offset + 24, obs.llx());
      obsBuf.set(ValueLayout.JAVA_INT, offset + 28, obs.urx());
      obsBuf.set(ValueLayout.JAVA_INT, offset + 32, obs.layer());
      obsBuf.set(ValueLayout.JAVA_INT, offset + 36, obs.clearanceClass());
    }

    // 3. Flatten ClearanceMatrix & Layer rules
    ClearanceMatrix cm = board.rules.clearanceMatrix;
    int classCount = cm.getClassCount();
    long ruleBytes =
        ((long) classCount * classCount * layers + (layers * 4L) + 32L)
            * ValueLayout.JAVA_INT.byteSize();
    MemorySegment ruleBuf = arena.allocate(Math.max(64, ruleBytes), 64);

    long ruleOffset = 0;
    for (int l = 0; l < layers; l++) {
      for (int ci = 0; ci < classCount; ci++) {
        for (int cj = 0; cj < classCount; cj++) {
          int val = cm.getValue(ci, cj, l, false);
          ruleBuf.set(ValueLayout.JAVA_INT, ruleOffset, val);
          ruleOffset += 4;
        }
      }
    }

    // Append per-layer trace half widths
    NetClass defaultNc = board.rules.getDefaultNetClass();
    for (int l = 0; l < layers; l++) {
      int hw =
          defaultNc != null
              ? defaultNc.getTraceHalfWidth(l)
              : board.rules.getMinAllowedTraceHalfWidth();
      ruleBuf.set(ValueLayout.JAVA_INT, ruleOffset, hw);
      ruleOffset += 4;
    }

    return new MarshaledBgaData(
        arena, pinBuf, pinCount, obsBuf, obsCount, ruleBuf, gridStep, gridW, gridH, layers,
        localMinX, localMinY);
  }
}
