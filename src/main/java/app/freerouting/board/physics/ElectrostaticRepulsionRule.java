package app.freerouting.board.physics;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Polyline;

/**
 * Electrostatic Trace Repulsion ("Anti-PullTight") {@link PhysicsRule}.
 *
 * <p>Evaluates the line integral of the blurred foreign-copper repulsive potential along trace
 * segments, skipping cells owned exclusively by {@code netNo} so traces never repel themselves.
 */
public final class ElectrostaticRepulsionRule implements PhysicsRule {

  /** Singleton instance of the stateless electrostatic repulsion rule. */
  public static final ElectrostaticRepulsionRule INSTANCE = new ElectrostaticRepulsionRule();

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
      int idx = context.cellIndex(layer, cx, cy);
      if (context.getNetId(idx) != netNo) {
        sum += context.getEnergy(idx);
      }
    }
    return sum / (steps + 1);
  }

  @Override
  public double evaluateVertex(
      BoardFieldContext context, int layer, int netNo, double x, double y) {
    int idx = context.cellIndex(layer, context.toCellX(x), context.toCellY(y));
    return context.getNetId(idx) != netNo ? context.getEnergy(idx) : 0.0;
  }

  @Override
  public double evaluateBoard(BasicBoard board, BoardFieldContext context) {
    if (board == null) {
      return 0.0;
    }
    double total = 0.0;
    for (Trace trace : board.getTraces()) {
      if (trace instanceof PolylineTrace polylineTrace) {
        int layer = polylineTrace.getLayer();
        int halfWidth = polylineTrace.getHalfWidth();
        int netNo = polylineTrace.netCount() > 0 ? polylineTrace.getNetNumber(0) : 0;
        Polyline polyline = polylineTrace.polyline();
        int corners = polyline.cornerCount();
        for (int i = 0; i < corners - 1; i++) {
          FloatPoint p1 = polyline.corner(i).toFloat();
          FloatPoint p2 = polyline.corner(i + 1).toFloat();
          total += evaluateSegment(context, layer, netNo, halfWidth, p1.x, p1.y, p2.x, p2.y);
        }
      }
    }
    return total;
  }
}
