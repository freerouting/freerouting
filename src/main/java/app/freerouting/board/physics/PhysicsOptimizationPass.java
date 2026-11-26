package app.freerouting.board.physics;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.searchtree.ShapeSearchTree;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.datastructures.ShapeTree.TreeEntry;
import app.freerouting.datastructures.Stoppable;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.settings.PhysicsSettings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * DRC-guarded, deterministic post-route physics optimization pass operating on a thread-confined
 * {@link RoutingBoard} using Tier 1 static fields and Tier 2 exact near-field kernels with an
 * active-set worklist.
 */
public final class PhysicsOptimizationPass {

  /** Default maximum relaxation iterations when none is configured. */
  public static final int DEFAULT_MAX_ITERATIONS = 8;

  private static final int DEFAULT_BLUR_PASSES = 4;
  private static final double WIRE_LENGTH_PENALTY_PER_STEP = 0.015;
  private static final double BEND_PENALTY_PER_CORNER = 0.12;
  private static final double JOG_MIN_SEGMENT_ENERGY = 0.05;

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

  /** Outcome of a single trace geometry transformation attempt. */
  public record MoveAttempt(int tried, boolean accepted, PolylineTrace replacement) {}

  private PhysicsOptimizationPass() {}

  /**
   * Executes the physics pass on {@code board} using configured {@link PhysicsRule} weights.
   *
   * <p>Execution is deterministic and never branches algorithmic decisions on elapsed wall-clock
   * time. Cancellation is checked via {@code stoppable} and thread interruption.
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

  /** Executes the physics pass on {@code board} with a caller-supplied {@link PhysicsEvaluator}. */
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

    // Tier 1 static fields built ONCE per pass (planes, vias, pins, board outline never move)
    BoardFieldContext context = BoardFieldContext.fromBoard(board, resolutionMm);
    context.rasterizeStaticBoard(board, DEFAULT_BLUR_PASSES);

    double initialEnergy = evaluator.evaluateBoard(board, context);
    double currentEnergy = initialEnergy;
    int iterations = 0;
    int triedMoves = 0;
    int acceptedMoves = 0;

    ShapeSearchTree tree =
        board.searchTreeManager != null ? board.searchTreeManager.getDefaultTree() : null;
    AngleRestriction angleRestriction =
        board.rules != null ? board.rules.getTraceAngleRestriction() : AngleRestriction.NONE;

    // Sorted active-set worklist preserves 100% deterministic traversal order
    TreeSet<Integer> activeIds = new TreeSet<>();
    TreeSet<Integer> nextActiveIds = new TreeSet<>();
    TreeSet<Integer> modifiedTraceIds = new TreeSet<>();
    Map<Integer, PolylineTrace> traceMap = new HashMap<>();
    List<TreeEntry> scratchNeighborEntries = new ArrayList<>(32);
    int[] scratchOverlapIgnoreNet = new int[1];

    for (Trace trace : board.getTraces()) {
      if (trace instanceof PolylineTrace polylineTrace) {
        activeIds.add(polylineTrace.getId());
      }
    }

    for (int iter = 0; iter < maxIterations; iter++) {
      if (isCancelled(stoppable) || activeIds.isEmpty()) {
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

      traceMap.clear();
      for (Trace trace : board.getTraces()) {
        if (trace instanceof PolylineTrace polylineTrace) {
          traceMap.put(polylineTrace.getId(), polylineTrace);
        }
      }

      int acceptedInPass = 0;
      nextActiveIds.clear();

      for (int traceId : activeIds) {
        if (isCancelled(stoppable)) {
          break;
        }
        PolylineTrace trace = traceMap.get(traceId);
        if (trace == null || trace.isShoveFixed() || trace.isUserFixed()) {
          continue;
        }
        double cutoffUnits =
            Math.max(6.0 * trace.getHalfWidth(), 3.0 * context.getCellSizeBoardUnits());
        MoveAttempt attempt =
            relaxSingleTrace(
                board,
                tree,
                context,
                evaluator,
                trace,
                angleRestriction,
                cutoffUnits,
                scratchNeighborEntries,
                scratchOverlapIgnoreNet);
        triedMoves += attempt.tried();
        if (attempt.accepted() && attempt.replacement() != null) {
          acceptedMoves++;
          acceptedInPass++;
          PolylineTrace inserted = attempt.replacement();
          modifiedTraceIds.remove(traceId);
          modifiedTraceIds.add(inserted.getId());
          nextActiveIds.add(inserted.getId());
          enqueueNeighbors(
              board, tree, inserted, cutoffUnits, scratchNeighborEntries, nextActiveIds);
        }
      }

      if (acceptedInPass == 0) {
        break;
      }
      double nextEnergy = evaluator.evaluateBoard(board, context);
      currentEnergy = nextEnergy;
      TreeSet<Integer> tmp = activeIds;
      activeIds = nextActiveIds;
      nextActiveIds = tmp;
    }

    if (relaxTraces && !modifiedTraceIds.isEmpty()) {
      straightenModifiedTraces(board, tree, context, evaluator, modifiedTraceIds, angleRestriction);
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

  private static void enqueueNeighbors(
      RoutingBoard board,
      ShapeSearchTree tree,
      PolylineTrace movedTrace,
      double cutoffUnits,
      List<TreeEntry> scratchEntries,
      TreeSet<Integer> nextActiveIds) {
    if (tree == null) {
      return;
    }
    IntBox box = movedTrace.boundingBox();
    if (box == null || box.isEmpty()) {
      return;
    }
    int pad = (int) Math.ceil(cutoffUnits);
    IntBox queryBox = new IntBox(box.ll.x - pad, box.ll.y - pad, box.ur.x + pad, box.ur.y + pad);
    scratchEntries.clear();
    tree.overlappingTreeEntries(queryBox, movedTrace.getLayer(), new int[0], scratchEntries);
    for (int i = 0, n = scratchEntries.size(); i < n; i++) {
      if (scratchEntries.get(i).object instanceof PolylineTrace neighbor) {
        nextActiveIds.add(neighbor.getId());
      }
    }
  }

  private static MoveAttempt relaxSingleTrace(
      RoutingBoard board,
      ShapeSearchTree tree,
      BoardFieldContext context,
      PhysicsEvaluator evaluator,
      PolylineTrace trace,
      AngleRestriction angleRestriction,
      double cutoffUnits,
      List<TreeEntry> scratchEntries,
      int[] scratchIgnoreNet) {
    Polyline polyline = trace.polyline();
    int cornerCount = polyline.cornerCount();
    int layer = trace.getLayer();
    int halfWidth = trace.getHalfWidth();
    int netNo = trace.netCount() > 0 ? trace.getNetNumber(0) : 0;
    int minStep = Math.max(1, 2 * halfWidth); // >= 1.0 * traceWidth
    int baseStep = Math.max(minStep, (int) Math.round(context.getCellSizeBoardUnits() * 0.5));
    if (baseStep <= 0) {
      return new MoveAttempt(0, false, null);
    }

    Point[] baseCorners = new Point[cornerCount];
    for (int i = 0; i < cornerCount; i++) {
      baseCorners[i] = polyline.corner(i);
    }

    int tried = 0;
    int[] stepCandidates = {baseStep, baseStep / 2, baseStep / 4};
    int prevStep = -1;

    if (cornerCount >= 4) {
      for (int step : stepCandidates) {
        if (step < minStep || step == prevStep) {
          continue;
        }
        prevStep = step;
        MoveAttempt shiftResult =
            tryInteriorSegmentShift(
                board,
                tree,
                context,
                evaluator,
                trace,
                baseCorners,
                layer,
                halfWidth,
                netNo,
                step,
                angleRestriction,
                cutoffUnits);
        tried += shiftResult.tried();
        if (shiftResult.accepted()) {
          return new MoveAttempt(tried, true, shiftResult.replacement());
        }
      }
    }

    // Restrict straight-segment jogs strictly to unbent 2-corner traces (no cascading re-splits)
    if (cornerCount == 2 && angleRestriction != AngleRestriction.NINETY_DEGREE) {
      int jogStep =
          Math.max(baseStep, 6 * halfWidth); // >= 3 * traceWidth so 45-deg shoulders >= 4w
      MoveAttempt jogResult =
          tryStraightSegmentJog(
              board,
              tree,
              context,
              evaluator,
              trace,
              baseCorners,
              layer,
              halfWidth,
              netNo,
              jogStep,
              cutoffUnits,
              scratchEntries,
              scratchIgnoreNet);
      tried += jogResult.tried();
      if (jogResult.accepted()) {
        return new MoveAttempt(tried, true, jogResult.replacement());
      }
    }

    return new MoveAttempt(tried, false, null);
  }

  private static MoveAttempt tryInteriorSegmentShift(
      RoutingBoard board,
      ShapeSearchTree tree,
      BoardFieldContext context,
      PhysicsEvaluator evaluator,
      PolylineTrace trace,
      Point[] baseCorners,
      int layer,
      int halfWidth,
      int netNo,
      int step,
      AngleRestriction angleRestriction,
      double cutoffUnits) {
    int cornerCount = baseCorners.length;
    int tried = 0;
    double bestDeltaE = -1e-6;
    int bestSegIndex = -1;
    IntPoint bestCand1 = null;
    IntPoint bestCand2 = null;

    for (int k = 1; k <= cornerCount - 3; k++) {
      Point p0 = baseCorners[k - 1];
      Point p1 = baseCorners[k];
      Point p2 = baseCorners[k + 1];
      Point p3 = baseCorners[k + 2];

      if (!(p1 instanceof IntPoint ip1) || !(p2 instanceof IntPoint ip2)) {
        continue;
      }

      double baseEnergy =
          segmentEnergy(
                  evaluator, board, tree, context, layer, netNo, halfWidth, p0, p1, cutoffUnits)
              + segmentEnergy(
                  evaluator, board, tree, context, layer, netNo, halfWidth, p1, p2, cutoffUnits)
              + segmentEnergy(
                  evaluator, board, tree, context, layer, netNo, halfWidth, p2, p3, cutoffUnits);
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

        double minSegLen = 8.0 * halfWidth; // 4.0 * traceWidth
        if (segmentLength(p0, cand1) < minSegLen
            || segmentLength(cand1, cand2) < minSegLen
            || segmentLength(cand2, p3) < minSegLen) {
          continue;
        }

        double candLength =
            segmentLength(p0, cand1) + segmentLength(cand1, cand2) + segmentLength(cand2, p3);
        if (candLength > baseLength + 2.0 * step) {
          continue;
        }

        tried++;
        double candEnergy =
            segmentEnergy(
                    evaluator,
                    board,
                    tree,
                    context,
                    layer,
                    netNo,
                    halfWidth,
                    p0,
                    cand1,
                    cutoffUnits)
                + segmentEnergy(
                    evaluator,
                    board,
                    tree,
                    context,
                    layer,
                    netNo,
                    halfWidth,
                    cand1,
                    cand2,
                    cutoffUnits)
                + segmentEnergy(
                    evaluator,
                    board,
                    tree,
                    context,
                    layer,
                    netNo,
                    halfWidth,
                    cand2,
                    p3,
                    cutoffUnits);
        double lengthPenalty = WIRE_LENGTH_PENALTY_PER_STEP * ((candLength - baseLength) / step);
        double deltaE = (candEnergy - baseEnergy) + lengthPenalty;
        if (deltaE < bestDeltaE) {
          bestDeltaE = deltaE;
          bestSegIndex = k;
          bestCand1 = cand1;
          bestCand2 = cand2;
        }
      }
    }

    if (bestCand1 == null) {
      return new MoveAttempt(tried, false, null);
    }

    Point[] candCorners = baseCorners.clone();
    candCorners[bestSegIndex] = bestCand1;
    candCorners[bestSegIndex + 1] = bestCand2;
    Polyline candPolyline = new Polyline(candCorners);
    if (candPolyline.cornerCount() != cornerCount) {
      return new MoveAttempt(tried, false, null);
    }

    int fromSeg = Math.max(0, bestSegIndex - 1);
    int toSeg = Math.min(candPolyline.cornerCount() - 2, bestSegIndex + 1);
    PolylineTrace inserted = commitIfClearanceClean(board, trace, candPolyline, fromSeg, toSeg);
    return new MoveAttempt(tried, inserted != null, inserted);
  }

  private static MoveAttempt tryStraightSegmentJog(
      RoutingBoard board,
      ShapeSearchTree tree,
      BoardFieldContext context,
      PhysicsEvaluator evaluator,
      PolylineTrace trace,
      Point[] baseCorners,
      int layer,
      int halfWidth,
      int netNo,
      int step,
      double cutoffUnits,
      List<TreeEntry> scratchEntries,
      int[] scratchIgnoreNet) {
    int cornerCount = baseCorners.length;
    int tried = 0;

    for (int k = 0; k < cornerCount - 1; k++) {
      if (!(baseCorners[k] instanceof IntPoint ip1)
          || !(baseCorners[k + 1] instanceof IntPoint ip2)) {
        continue;
      }
      boolean horizontal = ip1.y == ip2.y && ip1.x != ip2.x;
      boolean vertical = ip1.x == ip2.x && ip1.y != ip2.y;
      if (!horizontal && !vertical) {
        continue;
      }
      long span = horizontal ? Math.abs((long) ip2.x - ip1.x) : Math.abs((long) ip2.y - ip1.y);
      int minSeg = 8 * halfWidth; // 4.0 * traceWidth
      if (span < (2L * minSeg) + (2L * step)) {
        continue;
      }

      double baseEnergy =
          segmentEnergy(
              evaluator, board, tree, context, layer, netNo, halfWidth, ip1, ip2, cutoffUnits);
      if (baseEnergy < JOG_MIN_SEGMENT_ENERGY) {
        continue;
      }

      // Require sustained parallel overlap >= 5 * traceWidth (10 * halfWidth) with a foreign trace
      double overlap =
          ElectrostaticRepulsionRule.INSTANCE.maxParallelOverlap(
              board,
              tree,
              scratchEntries,
              scratchIgnoreNet,
              layer,
              netNo,
              halfWidth,
              ip1.x,
              ip1.y,
              ip2.x,
              ip2.y,
              cutoffUnits);
      if (overlap < 10.0 * halfWidth) {
        continue;
      }

      int dirSign = horizontal ? (ip2.x > ip1.x ? 1 : -1) : (ip2.y > ip1.y ? 1 : -1);
      // Require shifted middle segment to span >= 75% of original segment length
      int flatHalf = (int) Math.max(minSeg / 2, (span * 3) / 8);
      int shoulderOffset = flatHalf + step;
      if (2L * shoulderOffset + 2L * minSeg > span) {
        shoulderOffset = (int) ((span / 2) - minSeg);
        flatHalf = shoulderOffset - step;
        if (flatHalf < minSeg / 2 || (2L * flatHalf) < (span * 3) / 4) {
          continue;
        }
      }

      for (int lateralSign : new int[] {1, -1}) {
        int lat = lateralSign * step;
        IntPoint s1;
        IntPoint j1;
        IntPoint j2;
        IntPoint s2;
        if (horizontal) {
          int midX = (int) (((long) ip1.x + ip2.x) / 2);
          s1 = new IntPoint(midX - dirSign * shoulderOffset, ip1.y);
          j1 = new IntPoint(midX - dirSign * flatHalf, ip1.y + lat);
          j2 = new IntPoint(midX + dirSign * flatHalf, ip1.y + lat);
          s2 = new IntPoint(midX + dirSign * shoulderOffset, ip1.y);
        } else {
          int midY = (int) (((long) ip1.y + ip2.y) / 2);
          s1 = new IntPoint(ip1.x, midY - dirSign * shoulderOffset);
          j1 = new IntPoint(ip1.x + lat, midY - dirSign * flatHalf);
          j2 = new IntPoint(ip1.x + lat, midY + dirSign * flatHalf);
          s2 = new IntPoint(ip1.x, midY + dirSign * shoulderOffset);
        }

        tried++;
        double candEnergy =
            segmentEnergy(
                    evaluator, board, tree, context, layer, netNo, halfWidth, ip1, s1, cutoffUnits)
                + segmentEnergy(
                    evaluator, board, tree, context, layer, netNo, halfWidth, s1, j1, cutoffUnits)
                + segmentEnergy(
                    evaluator, board, tree, context, layer, netNo, halfWidth, j1, j2, cutoffUnits)
                + segmentEnergy(
                    evaluator, board, tree, context, layer, netNo, halfWidth, j2, s2, cutoffUnits)
                + segmentEnergy(
                    evaluator, board, tree, context, layer, netNo, halfWidth, s2, ip2, cutoffUnits);
        double deltaE =
            (candEnergy - baseEnergy)
                + (WIRE_LENGTH_PENALTY_PER_STEP * 0.83)
                + (4.0 * BEND_PENALTY_PER_CORNER);
        if (deltaE >= -1e-6) {
          continue;
        }

        Point[] candCorners = new Point[cornerCount + 4];
        System.arraycopy(baseCorners, 0, candCorners, 0, k + 1);
        candCorners[k + 1] = s1;
        candCorners[k + 2] = j1;
        candCorners[k + 3] = j2;
        candCorners[k + 4] = s2;
        System.arraycopy(baseCorners, k + 1, candCorners, k + 5, cornerCount - (k + 1));

        Polyline candPolyline = new Polyline(candCorners);
        if (candPolyline.cornerCount() != candCorners.length) {
          continue;
        }
        PolylineTrace inserted = commitIfClearanceClean(board, trace, candPolyline, k, k + 4);
        if (inserted != null) {
          return new MoveAttempt(tried, true, inserted);
        }
      }
    }

    return new MoveAttempt(tried, false, null);
  }

  private static PolylineTrace commitIfClearanceClean(
      RoutingBoard board, PolylineTrace trace, Polyline candPolyline, int fromSeg, int toSeg) {
    int layer = trace.getLayer();
    int halfWidth = trace.getHalfWidth();
    int clearanceClass = trace.clearanceClassIndex();
    int[] netNumbers = trace.netNumbers;
    FixedState fixedState = trace.getFixedState();
    Set<Pin> contactPins = trace.touchingPinsAtEndCorners();

    TileShape[] shapes = candPolyline.offsetShapes(halfWidth, fromSeg, toSeg);
    for (TileShape shape : shapes) {
      if (shape == null
          || !board.checkTraceShape(shape, layer, netNumbers, clearanceClass, contactPins)) {
        return null;
      }
    }

    board.generateSnapshot();
    if (!board.removeItems(List.of((Item) trace))) {
      board.undo(null);
      return null;
    }
    PolylineTrace inserted =
        board.insertTraceWithoutCleaning(
            candPolyline, layer, halfWidth, netNumbers, clearanceClass, fixedState);
    if (inserted == null || !inserted.clearanceViolations().isEmpty()) {
      board.undo(null);
      return null;
    }
    board.popSnapshot();
    return inserted;
  }

  private static double segmentEnergy(
      PhysicsEvaluator evaluator,
      RoutingBoard board,
      ShapeSearchTree tree,
      BoardFieldContext context,
      int layer,
      int netNo,
      int halfWidth,
      Point a,
      Point b,
      double cutoffUnits) {
    FloatPoint fa = a.toFloat();
    FloatPoint fb = b.toFloat();
    return evaluator.evaluateLocalSegment(
        board, tree, context, layer, netNo, halfWidth, fa.x, fa.y, fb.x, fb.y, cutoffUnits);
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

  private static void straightenModifiedTraces(
      RoutingBoard board,
      ShapeSearchTree tree,
      BoardFieldContext context,
      PhysicsEvaluator evaluator,
      TreeSet<Integer> modifiedTraceIds,
      AngleRestriction angleRestriction) {
    if (modifiedTraceIds.isEmpty()) {
      return;
    }
    Map<Integer, PolylineTrace> map = new HashMap<>();
    for (Trace t : board.getTraces()) {
      if (t instanceof PolylineTrace pt) {
        map.put(pt.getId(), pt);
      }
    }
    List<Integer> snapshot = new ArrayList<>(modifiedTraceIds);
    for (int id : snapshot) {
      PolylineTrace trace = map.get(id);
      if (trace == null || trace.isShoveFixed() || trace.isUserFixed()) {
        continue;
      }
      boolean changed = true;
      while (changed) {
        changed = false;
        Polyline poly = trace.polyline();
        int corners = poly.cornerCount();
        if (corners < 3) {
          break;
        }
        int layer = trace.getLayer();
        int halfWidth = trace.getHalfWidth();
        int netNo = trace.netCount() > 0 ? trace.getNetNumber(0) : 0;
        double cutoff = Math.max(6.0 * halfWidth, 3.0 * context.getCellSizeBoardUnits());

        // 1. Single-vertex bypass: (V_{i-1}, V_i, V_{i+1}) -> (V_{i-1}, V_{i+1})
        for (int i = 1; i <= corners - 2; i++) {
          Point prev = poly.corner(i - 1);
          Point curr = poly.corner(i);
          Point next = poly.corner(i + 1);
          if (!isSegmentAngleValid(prev, next, angleRestriction)) {
            continue;
          }
          double beforeE =
              segmentEnergy(
                      evaluator, board, tree, context, layer, netNo, halfWidth, prev, curr, cutoff)
                  + segmentEnergy(
                      evaluator, board, tree, context, layer, netNo, halfWidth, curr, next, cutoff);
          double afterE =
              segmentEnergy(
                  evaluator, board, tree, context, layer, netNo, halfWidth, prev, next, cutoff);
          if (afterE - beforeE - BEND_PENALTY_PER_CORNER > 0.0) {
            continue;
          }
          Point[] straight = new Point[corners - 1];
          for (int j = 0; j < i; j++) {
            straight[j] = poly.corner(j);
          }
          for (int j = i + 1; j < corners; j++) {
            straight[j - 1] = poly.corner(j);
          }
          Polyline candPoly = new Polyline(straight);
          if (candPoly.cornerCount() < 2) {
            continue;
          }
          int fromSeg = Math.max(0, i - 1);
          int toSeg = Math.min(candPoly.cornerCount() - 2, i - 1);
          PolylineTrace inserted = commitIfClearanceClean(board, trace, candPoly, fromSeg, toSeg);
          if (inserted != null) {
            trace = inserted;
            changed = true;
            break;
          }
        }
        if (changed || corners < 4) {
          continue;
        }

        // 2. Two-vertex jog-pair collapse: (V_{i-1}, V_i, V_{i+1}, V_{i+2}) -> (V_{i-1}, V_{i+2})
        for (int i = 1; i <= corners - 3; i++) {
          Point prev = poly.corner(i - 1);
          Point v1 = poly.corner(i);
          Point v2 = poly.corner(i + 1);
          Point next = poly.corner(i + 2);
          if (!isSegmentAngleValid(prev, next, angleRestriction)) {
            continue;
          }
          double beforeE =
              segmentEnergy(
                      evaluator, board, tree, context, layer, netNo, halfWidth, prev, v1, cutoff)
                  + segmentEnergy(
                      evaluator, board, tree, context, layer, netNo, halfWidth, v1, v2, cutoff)
                  + segmentEnergy(
                      evaluator, board, tree, context, layer, netNo, halfWidth, v2, next, cutoff);
          double afterE =
              segmentEnergy(
                  evaluator, board, tree, context, layer, netNo, halfWidth, prev, next, cutoff);
          if (afterE - beforeE - (2.0 * BEND_PENALTY_PER_CORNER) > 0.0) {
            continue;
          }
          Point[] straight = new Point[corners - 2];
          for (int j = 0; j <= i - 1; j++) {
            straight[j] = poly.corner(j);
          }
          for (int j = i + 2; j < corners; j++) {
            straight[j - 2] = poly.corner(j);
          }
          Polyline candPoly = new Polyline(straight);
          if (candPoly.cornerCount() < 2) {
            continue;
          }
          int fromSeg = Math.max(0, i - 1);
          int toSeg = Math.min(candPoly.cornerCount() - 2, i - 1);
          PolylineTrace inserted = commitIfClearanceClean(board, trace, candPoly, fromSeg, toSeg);
          if (inserted != null) {
            trace = inserted;
            changed = true;
            break;
          }
        }
      }
    }
  }

  private static boolean isCancelled(Stoppable stoppable) {
    return (stoppable != null && stoppable.isStopRequested())
        || Thread.currentThread().isInterrupted();
  }
}
