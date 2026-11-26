package app.freerouting.board.physics;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;

/**
 * Board-level {@link PhysicsRule} scoring sharp interior corners on {@link PolylineTrace}s.
 *
 * <p>Uses forward polyline vectors {@code v1 = curr - prev} and {@code v2 = next - curr}: acute
 * corners ({@code v1 · v2 < 0}) score {@code 1.0}, right-angle corners ({@code v1 · v2 == 0}) score
 * {@code 0.5}, and obtuse/straight corners ({@code v1 · v2 > 0}) score {@code 0.0}.
 */
public final class CornerCrowdingRule implements PhysicsRule {

  /** Singleton instance of the stateless corner-crowding rule. */
  public static final CornerCrowdingRule INSTANCE = new CornerCrowdingRule();

  private CornerCrowdingRule() {}

  @Override
  public String id() {
    return "corner_crowding";
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
    return 0.0;
  }

  @Override
  public double evaluateVertex(
      BoardFieldContext context, int layer, int netNo, double x, double y) {
    return 0.0;
  }

  @Override
  public double evaluateBoard(BasicBoard board, BoardFieldContext context) {
    if (board == null) {
      return 0.0;
    }
    double total = 0.0;
    for (Trace trace : board.getTraces()) {
      if (trace instanceof PolylineTrace polylineTrace) {
        Polyline poly = polylineTrace.polyline();
        int corners = poly.cornerCount();
        for (int i = 1; i <= corners - 2; i++) {
          Point prev = poly.corner(i - 1);
          Point curr = poly.corner(i);
          Point next = poly.corner(i + 1);
          if (!(prev instanceof IntPoint ip0)
              || !(curr instanceof IntPoint ip1)
              || !(next instanceof IntPoint ip2)) {
            continue;
          }
          long dx1 = (long) ip1.x - ip0.x;
          long dy1 = (long) ip1.y - ip0.y;
          long dx2 = (long) ip2.x - ip1.x;
          long dy2 = (long) ip2.y - ip1.y;
          long dot = dx1 * dx2 + dy1 * dy2;
          if (dot < 0L) {
            total += 1.0;
          } else if (dot == 0L) {
            total += 0.5;
          }
        }
      }
    }
    return total;
  }
}
