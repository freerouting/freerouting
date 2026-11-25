package app.freerouting.board.physics;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.datastructures.Stoppable;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.settings.PhysicsSettings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * DRC-guarded, deterministic post-route physics optimization pass operating on a thread-confined
 * {@link RoutingBoard}.
 */
public final class PhysicsOptimizationPass {

  /** Default maximum relaxation iterations when none is configured. */
  public static final int DEFAULT_MAX_ITERATIONS = 8;

  private static final int DEFAULT_BLUR_PASSES = 4;

  private static final int[][] DIRECTIONS_8 = {
    {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
  };

  /** Structured execution metrics returned by {@link #run}. */
  public record Result(
      double initialEnergy,
      double finalEnergy,
      int iterations,
      int triedMoves,
      int acceptedMoves,
      long elapsedNanos) {}

  private PhysicsOptimizationPass() {}

  /**
   * Executes the physics pass on {@code board} using configured {@link PhysicsRule} weights.
   *
   * <p>Execution is deterministic and never branches algorithmic decisions on elapsed wall-clock
   * time. Cancellation is checked via {@code stoppable} and thread interruption.
   *
   * @param board thread-confined routing board
   * @param settings physics settings (nullable)
   * @param stoppable cooperative cancellation handle (nullable)
   * @return execution metrics
   */
  public static Result run(RoutingBoard board, PhysicsSettings settings, Stoppable stoppable) {
    double identityWeight =
        (settings != null && settings.identityWeight != null) ? settings.identityWeight : 1.0;
    double repulsionWeight =
        (settings != null && settings.repulsionWeight != null) ? settings.repulsionWeight : 1.0;
    PhysicsEvaluator evaluator =
        new PhysicsEvaluator(
            List.of(IdentityPhysicsRule.INSTANCE, ElectrostaticRepulsionRule.INSTANCE),
            new double[] {identityWeight, repulsionWeight});
    return run(board, settings, evaluator, repulsionWeight > 0.0, stoppable);
  }

  /**
   * Executes the physics pass on {@code board} with a caller-supplied {@link PhysicsEvaluator}.
   *
   * @param board thread-confined routing board
   * @param settings physics settings (nullable)
   * @param evaluator stateless scalar energy oracle
   * @param stoppable cooperative cancellation handle (nullable)
   * @return execution metrics
   */
  public static Result run(
      RoutingBoard board,
      PhysicsSettings settings,
      PhysicsEvaluator evaluator,
      Stoppable stoppable) {
    return run(board, settings, evaluator, false, stoppable);
  }

  private static Result run(
      RoutingBoard board,
      PhysicsSettings settings,
      PhysicsEvaluator evaluator,
      boolean relaxTraces,
      Stoppable stoppable) {
    long startNanos = System.nanoTime();
    if (board == null || evaluator == null) {
      return new Result(0.0, 0.0, 0, 0, 0, System.nanoTime() - startNanos);
    }

    double resolutionMm =
        (settings != null && settings.gridResolutionMm != null)
            ? settings.gridResolutionMm
            : BoardFieldContext.DEFAULT_RESOLUTION_MM;
    int maxIterations =
        (settings != null && settings.maxIterations != null && settings.maxIterations >= 0)
            ? settings.maxIterations
            : DEFAULT_MAX_ITERATIONS;

    BoardFieldContext context = BoardFieldContext.fromBoard(board, resolutionMm);
    context.rasterizeBoard(board, DEFAULT_BLUR_PASSES);

    double initialEnergy = evaluator.evaluateBoard(board, context);
    double currentEnergy = initialEnergy;
    int iterations = 0;
    int triedMoves = 0;
    int acceptedMoves = 0;

    List<Integer> traceIds = new ArrayList<>();
    AngleRestriction angleRestriction =
        board.rules != null ? board.rules.getTraceAngleRestriction() : AngleRestriction.NONE;

    for (int iter = 0; iter < maxIterations; iter++) {
      if (isCancelled(stoppable)) {
        break;
      }
      iterations++;
      if (!relaxTraces) {
        double nextEnergy = evaluator.evaluateBoard(board, context);
        if (nextEnergy >= currentEnergy) {
          break;
        }
        currentEnergy = nextEnergy;
        continue;
      }

      int acceptedInPass = 0;
      traceIds.clear();
      for (Trace trace : board.getTraces()) {
        if (trace instanceof PolylineTrace) {
          traceIds.add(trace.getId());
        }
      }
      Collections.sort(traceIds);

      for (int traceId : traceIds) {
        if (isCancelled(stoppable)) {
          break;
        }
        PolylineTrace trace = findTraceById(board, traceId);
        if (trace == null
            || trace.isShoveFixed()
            || trace.isUserFixed()
            || trace.polyline().cornerCount() < 4) {
          continue;
        }
        MoveAttempt attempt = relaxSingleTrace(board, context, evaluator, trace, angleRestriction);
        triedMoves += attempt.tried();
        if (attempt.accepted()) {
          acceptedMoves++;
          acceptedInPass++;
        }
      }

      if (acceptedInPass == 0) {
        break;
      }
      context.rasterizeBoard(board, DEFAULT_BLUR_PASSES);
      currentEnergy = evaluator.evaluateBoard(board, context);
    }

    return new Result(
        initialEnergy,
        currentEnergy,
        iterations,
        triedMoves,
        acceptedMoves,
        System.nanoTime() - startNanos);
  }

  private record MoveAttempt(int tried, boolean accepted) {}

  private static MoveAttempt relaxSingleTrace(
      RoutingBoard board,
      BoardFieldContext context,
      PhysicsEvaluator evaluator,
      PolylineTrace trace,
      AngleRestriction angleRestriction) {
    Polyline polyline = trace.polyline();
    int cornerCount = polyline.cornerCount();
    int layer = trace.getLayer();
    int halfWidth = trace.getHalfWidth();
    int netNo = trace.netCount() > 0 ? trace.getNetNumber(0) : 0;
    int step = Math.max(halfWidth, (int) Math.round(context.getCellSizeBoardUnits() * 0.5));
    if (step <= 0) {
      return new MoveAttempt(0, false);
    }

    Point[] baseCorners = new Point[cornerCount];
    for (int i = 0; i < cornerCount; i++) {
      baseCorners[i] = polyline.corner(i);
    }

    int tried = 0;
    double bestDeltaE = -1e-6;
    Polyline bestPolyline = null;
    int bestSegmentIndex = -1;

    for (int k = 1; k <= cornerCount - 3; k++) {
      Point p0 = baseCorners[k - 1];
      Point p1 = baseCorners[k];
      Point p2 = baseCorners[k + 1];
      Point p3 = baseCorners[k + 2];

      if (!(p1 instanceof IntPoint ip1) || !(p2 instanceof IntPoint ip2)) {
        continue;
      }

      double baseEnergy =
          segmentEnergy(evaluator, context, layer, netNo, halfWidth, p0, p1)
              + segmentEnergy(evaluator, context, layer, netNo, halfWidth, p1, p2)
              + segmentEnergy(evaluator, context, layer, netNo, halfWidth, p2, p3);
      double baseLength = segmentLength(p0, p1) + segmentLength(p1, p2) + segmentLength(p2, p3);

      for (int[] dir : DIRECTIONS_8) {
        if (angleRestriction == AngleRestriction.NINETY_DEGREE && dir[0] != 0 && dir[1] != 0) {
          continue;
        }
        int dx = dir[0] * step;
        int dy = dir[1] * step;
        IntPoint cand1 = new IntPoint(ip1.x + dx, ip1.y + dy);
        IntPoint cand2 = new IntPoint(ip2.x + dx, ip2.y + dy);

        if (!isSegmentAngleValid(p0, cand1, angleRestriction)
            || !isSegmentAngleValid(cand1, cand2, angleRestriction)
            || !isSegmentAngleValid(cand2, p3, angleRestriction)) {
          continue;
        }

        double candLength =
            segmentLength(p0, cand1) + segmentLength(cand1, cand2) + segmentLength(cand2, p3);
        if (candLength > baseLength + 2.0 * step) {
          continue;
        }

        tried++;
        double candEnergy =
            segmentEnergy(evaluator, context, layer, netNo, halfWidth, p0, cand1)
                + segmentEnergy(evaluator, context, layer, netNo, halfWidth, cand1, cand2)
                + segmentEnergy(evaluator, context, layer, netNo, halfWidth, cand2, p3);
        double deltaE = candEnergy - baseEnergy;
        if (deltaE < bestDeltaE) {
          Point[] candCorners = baseCorners.clone();
          candCorners[k] = cand1;
          candCorners[k + 1] = cand2;
          Polyline candPolyline = new Polyline(candCorners);
          if (candPolyline.cornerCount() == cornerCount) {
            bestDeltaE = deltaE;
            bestPolyline = candPolyline;
            bestSegmentIndex = k;
          }
        }
      }
    }

    if (bestPolyline == null) {
      return new MoveAttempt(tried, false);
    }

    int clearanceClass = trace.clearanceClassIndex();
    int[] netNumbers = trace.netNumbers;
    FixedState fixedState = trace.getFixedState();
    Set<Pin> contactPins = trace.touchingPinsAtEndCorners();

    // Pre-validate modified segment shapes in O(log N) before mutating the board.
    int fromSeg = Math.max(0, bestSegmentIndex - 1);
    int toSeg = Math.min(bestPolyline.cornerCount() - 2, bestSegmentIndex + 1);
    TileShape[] shapes = bestPolyline.offsetShapes(halfWidth, fromSeg, toSeg);
    for (TileShape shape : shapes) {
      if (shape == null
          || !board.checkTraceShape(shape, layer, netNumbers, clearanceClass, contactPins)) {
        return new MoveAttempt(tried, false);
      }
    }

    board.generateSnapshot();
    if (!board.removeItems(List.of((Item) trace))) {
      board.undo(null);
      return new MoveAttempt(tried, false);
    }
    PolylineTrace inserted =
        board.insertTraceWithoutCleaning(
            bestPolyline, layer, halfWidth, netNumbers, clearanceClass, fixedState);
    if (inserted == null || !inserted.clearanceViolations().isEmpty()) {
      board.undo(null);
      return new MoveAttempt(tried, false);
    }
    board.popSnapshot();
    return new MoveAttempt(tried, true);
  }

  private static double segmentEnergy(
      PhysicsEvaluator evaluator,
      BoardFieldContext context,
      int layer,
      int netNo,
      int halfWidth,
      Point a,
      Point b) {
    FloatPoint fa = a.toFloat();
    FloatPoint fb = b.toFloat();
    return evaluator.evaluateLocalSegment(context, layer, netNo, halfWidth, fa.x, fa.y, fb.x, fb.y);
  }

  private static double segmentLength(Point a, Point b) {
    FloatPoint fa = a.toFloat();
    FloatPoint fb = b.toFloat();
    return Math.hypot(fb.x - fa.x, fb.y - fa.y);
  }

  private static boolean isSegmentAngleValid(Point a, Point b, AngleRestriction restriction) {
    if (a.equals(b)) {
      return false;
    }
    if (restriction == null || restriction == AngleRestriction.NONE) {
      return true;
    }
    if (!(a instanceof IntPoint ia) || !(b instanceof IntPoint ib)) {
      return false;
    }
    long dx = Math.abs((long) ib.x - ia.x);
    long dy = Math.abs((long) ib.y - ia.y);
    return switch (restriction) {
      case NINETY_DEGREE -> dx == 0L || dy == 0L;
      case FORTYFIVE_DEGREE -> dx == 0L || dy == 0L || dx == dy;
      case NONE -> true;
    };
  }

  private static PolylineTrace findTraceById(RoutingBoard board, int id) {
    for (Trace trace : board.getTraces()) {
      if (trace.getId() == id && trace instanceof PolylineTrace polylineTrace) {
        return polylineTrace;
      }
    }
    return null;
  }

  private static boolean isCancelled(Stoppable stoppable) {
    return (stoppable != null && stoppable.isStopRequested())
        || Thread.currentThread().isInterrupted();
  }
}
