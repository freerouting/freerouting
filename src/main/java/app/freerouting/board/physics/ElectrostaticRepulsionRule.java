package app.freerouting.board.physics;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.searchtree.ShapeSearchTree;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.datastructures.ShapeTree.TreeEntry;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.Polyline;
import java.util.ArrayList;
import java.util.List;

/**
 * Tier 2 Electrostatic Trace Repulsion ("Anti-PullTight") implementing both {@link
 * NearFieldEnergyRule} and {@link PhysicsRule}.
 *
 * <p>Uses {@link ShapeSearchTree#overlappingTreeEntries} with reusable scratch buffers to compute
 * exact continuous segment-to-segment separation {@code s} and parallel overlap {@code l_parallel}
 * with symmetric {@code 2x} pairwise energy accounting.
 */
public final class ElectrostaticRepulsionRule implements PhysicsRule, NearFieldEnergyRule {

  /** Singleton instance of the stateless electrostatic repulsion rule. */
  public static final ElectrostaticRepulsionRule INSTANCE = new ElectrostaticRepulsionRule();

  private static final double MIN_PARALLEL_ALIGNMENT = 0.1;

  private ElectrostaticRepulsionRule() {}

  @Override
  public String id() {
    return "electrostatic_repulsion";
  }

  @Override
  public double evaluateSegment(
      BoardFieldContext context,
      int layer,
      int netNo,
      int halfWidth,
      double x1,
      double y1,
      double x2,
      double y2) {
    // Tier 1 static background potential fallback when no board/tree is passed
    int cx1 = context.toCellX(x1);
    int cy1 = context.toCellY(y1);
    int cx2 = context.toCellX(x2);
    int cy2 = context.toCellY(y2);
    int steps = Math.max(2, Math.max(Math.abs(cx2 - cx1), Math.abs(cy2 - cy1)) + 1);
    double sum = 0.0;
    for (int s = 0; s <= steps; s++) {
      double t = (double) s / steps;
      int cx = context.toCellX(x1 + t * (x2 - x1));
      int cy = context.toCellY(y1 + t * (y2 - y1));
      sum += context.getEnergy(context.cellIndex(layer, cx, cy));
    }
    return sum / (steps + 1);
  }

  @Override
  public double evaluateSegment(
      BasicBoard board,
      ShapeSearchTree tree,
      List<TreeEntry> scratchEntries,
      int[] scratchIgnoreNet,
      int layer,
      int netNo,
      int halfWidth,
      double x1,
      double y1,
      double x2,
      double y2,
      double cutoffUnits) {
    if (board == null || tree == null) {
      return 0.0;
    }
    double segLen = Math.hypot(x2 - x1, y2 - y1);
    if (segLen < 1.0) {
      return 0.0;
    }
    double ux = (x2 - x1) / segLen;
    double uy = (y2 - y1) / segLen;

    int minX = (int) Math.floor(Math.min(x1, x2) - halfWidth - cutoffUnits);
    int minY = (int) Math.floor(Math.min(y1, y2) - halfWidth - cutoffUnits);
    int maxX = (int) Math.ceil(Math.max(x1, x2) + halfWidth + cutoffUnits);
    int maxY = (int) Math.ceil(Math.max(y1, y2) + halfWidth + cutoffUnits);
    IntBox queryBox = new IntBox(minX, minY, maxX, maxY);

    scratchEntries.clear();
    scratchIgnoreNet[0] = netNo;
    tree.overlappingTreeEntries(
        queryBox, layer, netNo > 0 ? scratchIgnoreNet : new int[0], scratchEntries);

    double energy = 0.0;
    double normScale = Math.max(1.0, cutoffUnits * 0.25);

    for (int i = 0, n = scratchEntries.size(); i < n; i++) {
      TreeEntry entry = scratchEntries.get(i);
      if (!(entry.object instanceof PolylineTrace other)) {
        continue;
      }
      // 1. Pre-filter: skip same-net traces
      if (netNo > 0 && other.containsNet(netNo)) {
        continue;
      }
      // 2. Pre-filter: 4-comparison axis-aligned IntBox distance > cutoff
      IntBox itemBox = other.boundingBox();
      if (itemBox == null
          || itemBox.ur.x < minX
          || itemBox.ll.x > maxX
          || itemBox.ur.y < minY
          || itemBox.ll.y > maxY) {
        continue;
      }

      int segIdx = entry.shapeIndexInObject;
      Polyline otherPoly = other.polyline();
      if (segIdx < 0 || segIdx >= otherPoly.cornerCount() - 1) {
        continue;
      }
      FloatPoint o1 = otherPoly.corner(segIdx).toFloat();
      FloatPoint o2 = otherPoly.corner(segIdx + 1).toFloat();
      double oLen = Math.hypot(o2.x - o1.x, o2.y - o1.y);
      if (oLen < 1.0) {
        continue;
      }
      double vx = (o2.x - o1.x) / oLen;
      double vy = (o2.y - o1.y) / oLen;
      // 3. Pre-filter: skip nearly perpendicular segments
      double alignment = Math.abs(ux * vx + uy * vy);
      if (alignment < MIN_PARALLEL_ALIGNMENT) {
        continue;
      }

      // Project neighbor endpoints onto segment A's axis to get parallel overlap
      double p1 = (o1.x - x1) * ux + (o1.y - y1) * uy;
      double p2 = (o2.x - x1) * ux + (o2.y - y1) * uy;
      double oMin = Math.min(p1, p2);
      double oMax = Math.max(p1, p2);
      double overlap = Math.min(segLen, oMax) - Math.max(0.0, oMin);
      if (overlap <= 0.0) {
        continue;
      }

      // Exact perpendicular edge-to-edge gap s
      double midOx = 0.5 * (o1.x + o2.x);
      double midOy = 0.5 * (o1.y + o2.y);
      double centerDist = Math.abs((midOx - x1) * (-uy) + (midOy - y1) * ux);
      double edgeGap = Math.max(0.0, centerDist - halfWidth - other.getHalfWidth());
      if (edgeGap >= cutoffUnits) {
        continue;
      }

      double dNorm = Math.max(0.25, edgeGap / normScale);
      double lNorm = overlap / normScale;
      // Symmetric 2x pairwise interaction: penalizes moving closer to C immediately
      energy += 2.0 * alignment * (lNorm / dNorm);
    }
    return energy;
  }

  /**
   * Returns the maximum continuous parallel overlap (in board units) between segment {@code (x1,
   * y1)-(x2, y2)} and any distinct-net {@link PolylineTrace} within {@code cutoffUnits}.
   */
  public double maxParallelOverlap(
      BasicBoard board,
      ShapeSearchTree tree,
      List<TreeEntry> scratchEntries,
      int[] scratchIgnoreNet,
      int layer,
      int netNo,
      int halfWidth,
      double x1,
      double y1,
      double x2,
      double y2,
      double cutoffUnits) {
    if (board == null || tree == null) {
      return 0.0;
    }
    double segLen = Math.hypot(x2 - x1, y2 - y1);
    if (segLen < 1.0) {
      return 0.0;
    }
    double ux = (x2 - x1) / segLen;
    double uy = (y2 - y1) / segLen;

    int minX = (int) Math.floor(Math.min(x1, x2) - halfWidth - cutoffUnits);
    int minY = (int) Math.floor(Math.min(y1, y2) - halfWidth - cutoffUnits);
    int maxX = (int) Math.ceil(Math.max(x1, x2) + halfWidth + cutoffUnits);
    int maxY = (int) Math.ceil(Math.max(y1, y2) + halfWidth + cutoffUnits);
    IntBox queryBox = new IntBox(minX, minY, maxX, maxY);

    scratchEntries.clear();
    scratchIgnoreNet[0] = netNo;
    tree.overlappingTreeEntries(
        queryBox, layer, netNo > 0 ? scratchIgnoreNet : new int[0], scratchEntries);

    double maxOverlap = 0.0;
    for (int i = 0, n = scratchEntries.size(); i < n; i++) {
      TreeEntry entry = scratchEntries.get(i);
      if (!(entry.object instanceof PolylineTrace other)) {
        continue;
      }
      if (netNo > 0 && other.containsNet(netNo)) {
        continue;
      }
      int segIdx = entry.shapeIndexInObject;
      Polyline otherPoly = other.polyline();
      if (segIdx < 0 || segIdx >= otherPoly.cornerCount() - 1) {
        continue;
      }
      FloatPoint o1 = otherPoly.corner(segIdx).toFloat();
      FloatPoint o2 = otherPoly.corner(segIdx + 1).toFloat();
      double oLen = Math.hypot(o2.x - o1.x, o2.y - o1.y);
      if (oLen < 1.0) {
        continue;
      }
      double vx = (o2.x - o1.x) / oLen;
      double vy = (o2.y - o1.y) / oLen;
      if (Math.abs(ux * vx + uy * vy) < 0.85) {
        continue;
      }
      double p1 = (o1.x - x1) * ux + (o1.y - y1) * uy;
      double p2 = (o2.x - x1) * ux + (o2.y - y1) * uy;
      double overlap = Math.min(segLen, Math.max(p1, p2)) - Math.max(0.0, Math.min(p1, p2));
      if (overlap > maxOverlap) {
        maxOverlap = overlap;
      }
    }
    return maxOverlap;
  }

  @Override
  public double evaluateVertex(
      BoardFieldContext context, int layer, int netNo, double x, double y) {
    return context.getEnergy(context.cellIndex(layer, context.toCellX(x), context.toCellY(y)));
  }

  @Override
  public double evaluateBoard(BasicBoard board, BoardFieldContext context) {
    if (board == null || board.searchTreeManager == null) {
      return 0.0;
    }
    ShapeSearchTree tree = board.searchTreeManager.getDefaultTree();
    if (tree == null) {
      return 0.0;
    }
    List<TreeEntry> scratchEntries = new ArrayList<>(64);
    int[] scratchIgnoreNet = new int[1];
    double cellSize = context != null ? Math.max(1.0, context.getCellSizeBoardUnits()) : 100.0;

    double total = 0.0;
    for (Trace trace : board.getTraces()) {
      if (trace instanceof PolylineTrace polylineTrace) {
        int layer = polylineTrace.getLayer();
        int halfWidth = polylineTrace.getHalfWidth();
        int netNo = polylineTrace.netCount() > 0 ? polylineTrace.getNetNumber(0) : 0;
        double cutoff = Math.max(6.0 * halfWidth, 3.0 * cellSize);
        Polyline polyline = polylineTrace.polyline();
        int corners = polyline.cornerCount();
        for (int i = 0; i < corners - 1; i++) {
          FloatPoint p1 = polyline.corner(i).toFloat();
          FloatPoint p2 = polyline.corner(i + 1).toFloat();
          // Divide by 2.0 on full-board sum so each pairwise interaction is counted once globally
          total +=
              0.5
                  * evaluateSegment(
                      board,
                      tree,
                      scratchEntries,
                      scratchIgnoreNet,
                      layer,
                      netNo,
                      halfWidth,
                      p1.x,
                      p1.y,
                      p2.x,
                      p2.y,
                      cutoff);
        }
      }
    }
    return total;
  }
}
