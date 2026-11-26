package app.freerouting.board.physics;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;

/**
 * {@link GeometryTransformOperator} that bevels octilinear 90-degree interior trace corners into
 * 45-degree (or axis-aligned for 45/45 corners) chamfers when DRC clearance permits.
 */
public final class CornerChamferOperator implements GeometryTransformOperator {

  /** Singleton instance of the stateless corner-chamfer operator. */
  public static final CornerChamferOperator INSTANCE = new CornerChamferOperator();

  private CornerChamferOperator() {}

  @Override
  public String id() {
    return "corner_chamfer";
  }

  @Override
  public PhysicsOptimizationPass.MoveAttempt proposeAndApply(
      RoutingBoard board,
      BoardFieldContext context,
      PhysicsEvaluator evaluator,
      PolylineTrace trace,
      AngleRestriction angleRestriction,
      double cutoffUnits) {
    if (board == null
        || trace == null
        || angleRestriction == AngleRestriction.NINETY_DEGREE
        || trace.isShoveFixed()
        || trace.isUserFixed()) {
      return new PhysicsOptimizationPass.MoveAttempt(0, false, null);
    }

    int halfWidth = trace.getHalfWidth();
    int minLeg = Math.max(1, 2 * halfWidth);
    int targetLeg = Math.max(minLeg, 3 * halfWidth);

    int tried = 0;
    boolean anyAccepted = false;
    PolylineTrace current = trace;

    boolean changed = true;
    while (changed) {
      changed = false;
      Polyline poly = current.polyline();
      int corners = poly.cornerCount();
      if (corners < 3) {
        break;
      }

      for (int i = 1; i <= corners - 2; i++) {
        if (!(poly.corner(i - 1) instanceof IntPoint ip0)
            || !(poly.corner(i) instanceof IntPoint ip1)
            || !(poly.corner(i + 1) instanceof IntPoint ip2)) {
          continue;
        }
        long dx1 = (long) ip1.x - ip0.x;
        long dy1 = (long) ip1.y - ip0.y;
        long dx2 = (long) ip2.x - ip1.x;
        long dy2 = (long) ip2.y - ip1.y;

        // Require exact 90-degree corner between two octilinear legs
        if ((dx1 * dx2 + dy1 * dy2) != 0L) {
          continue;
        }
        if (!isOctilinear(dx1, dy1) || !isOctilinear(dx2, dy2)) {
          continue;
        }

        boolean leg1Diag = dx1 != 0L && dy1 != 0L;
        boolean leg2Diag = dx2 != 0L && dy2 != 0L;
        if (leg1Diag != leg2Diag) {
          continue;
        }

        long span1 = leg1Diag ? Math.abs(dx1) : Math.max(Math.abs(dx1), Math.abs(dy1));
        long span2 = leg2Diag ? Math.abs(dx2) : Math.max(Math.abs(dx2), Math.abs(dy2));

        int legStepMax =
            leg1Diag ? (int) Math.max(1L, Math.round(targetLeg / Math.sqrt(2.0))) : targetLeg;
        int minStep = leg1Diag ? (int) Math.max(1L, Math.round(minLeg / Math.sqrt(2.0))) : minLeg;

        long maxAllowed = Math.min(span1, span2) - minStep;
        if (maxAllowed < minStep) {
          continue;
        }
        int m = (int) Math.min(legStepMax, maxAllowed);
        if (m < minStep) {
          continue;
        }

        int sx1 = Long.signum(dx1);
        int sy1 = Long.signum(dy1);
        int sx2 = Long.signum(dx2);
        int sy2 = Long.signum(dy2);

        IntPoint pA = new IntPoint(ip1.x - sx1 * m, ip1.y - sy1 * m);
        IntPoint pB = new IntPoint(ip1.x + sx2 * m, ip1.y + sy2 * m);
        if (pA.equals(ip0) || pB.equals(ip2) || pA.equals(pB)) {
          continue;
        }

        Point[] candCorners = new Point[corners + 1];
        for (int j = 0; j < i; j++) {
          candCorners[j] = poly.corner(j);
        }
        candCorners[i] = pA;
        candCorners[i + 1] = pB;
        for (int j = i + 1; j < corners; j++) {
          candCorners[j + 1] = poly.corner(j);
        }

        Polyline candPoly = new Polyline(candCorners);
        if (candPoly.cornerCount() != corners + 1) {
          continue;
        }

        tried++;
        int fromSeg = Math.max(0, i - 1);
        int toSeg = Math.min(candPoly.cornerCount() - 2, i + 1);
        PolylineTrace inserted =
            PhysicsOptimizationPass.commitIfClearanceClean(
                board, current, candPoly, fromSeg, toSeg);
        if (inserted != null) {
          current = inserted;
          anyAccepted = true;
          changed = true;
          break;
        }
      }
    }

    return new PhysicsOptimizationPass.MoveAttempt(
        tried, anyAccepted, anyAccepted ? current : null);
  }

  private static boolean isOctilinear(long dx, long dy) {
    long adx = Math.abs(dx);
    long ady = Math.abs(dy);
    return (adx != 0L || ady != 0L) && (adx == 0L || ady == 0L || adx == ady);
  }
}
