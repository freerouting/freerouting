package app.freerouting.board.optimize;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.datastructures.Stoppable;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.Side;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.rules.BoardRules;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Slides the steps of the traces along them, with shoving, to straighten bundles of traces.
 *
 * <p>A step is a short segment between two segments of the same direction: the trace moves sideways
 * and goes on parallel to itself. A step costs no length wherever it sits on its run, so the pull
 * tight leaves it where it was made. Steps are made when a new connection is inserted with shoving:
 * the pushed trace gets a bump, the new trace is pulled tight along it, and so is the next one. A
 * bundle laid at the clearance this way keeps a staircase of steps (issue #1161), and the pull
 * tight, which moves one trace with the others fixed, cannot move a step that the neighbouring
 * trace blocks.
 *
 * <p>This pass moves each step forward or backward along its trace, over one or more segments, by
 * translating the part of the trace in between by the step vector, until the step merges with a
 * parallel segment or cancels against an opposite step. The trace is inserted again with shoving
 * and pull tight, so the neighbouring traces move along. A move is kept only if the board as a
 * whole gets better: no more vias, incomplete connections, clearance violations, trace length or
 * bends, and less of one of the last two.
 *
 * <p>A plane or copper zone is only known by its outline, so a move that brings a trace into a zone
 * or closer to its outline than the clearance is refused. The pass does nothing on a board that
 * already has clearance violations.
 *
 * <p>The pass is deterministic and does not read the clock: traces are visited in ascending id
 * order, steps in the order of their trace, and the moves in a fixed order.
 */
public final class StepSlider {

  /** A step is at most this many pitches of its trace long. */
  private static final int MAX_STEP_LENGTH_IN_PITCHES = 4;

  /** A step is moved over at most this many segments. */
  private static final int MAX_SLIDE_SEGMENTS = 16;

  /** A bend is worth this many half pitches of trace when two improving moves are compared. */
  private static final double BEND_WEIGHT_IN_HALF_PITCHES = 0.5;

  private static final int MAX_PASSES = 10;

  private static final int MAX_SHOVE_RECURSION_DEPTH = 0;

  /** What a run of the pass did, and the board before and after it. Lengths are in board units. */
  public record Result(
      int passes,
      int triedMoves,
      int acceptedMoves,
      double lengthBefore,
      double lengthAfter,
      int bendsBefore,
      int bendsAfter,
      int viasBefore,
      int viasAfter,
      int incompleteBefore,
      int incompleteAfter,
      int violationsBefore,
      int violationsAfter) {}

  private record Cost(double length, int bends, int vias) {}

  private final RoutingBoard board;
  private final int pullTightAccuracy;
  private final Stoppable stoppable;

  /** The planes and copper zones of the board. */
  private final List<ConductionArea> zones;

  private StepSlider(RoutingBoard board, int pullTightAccuracy, Stoppable stoppable) {
    this.board = board;
    this.pullTightAccuracy = pullTightAccuracy;
    this.stoppable = stoppable;
    this.zones = new ArrayList<>(board.getConductionAreas());
  }

  /** Runs the pass on board until no step can be moved with a gain. */
  public static Result run(RoutingBoard board, int pullTightAccuracy, Stoppable stoppable) {
    return new StepSlider(board, pullTightAccuracy, stoppable).run();
  }

  private Result run() {
    final Cost start = cost();
    final int incompleteAtStart = board.routingLedger().incompleteCount();
    final int violationsAllowed = board.getStatistics().clearanceViolations.totalCount;
    int tried = 0;
    int accepted = 0;
    int passes = 0;
    // Shoving next to copper that already violates a clearance is not reliable: leave the board.
    while (violationsAllowed == 0 && passes < MAX_PASSES && !isStopRequested()) {
      ++passes;
      int acceptedInThisPass = 0;
      List<Integer> traceIds = new ArrayList<>();
      for (Trace trace : board.getTraces()) {
        if (trace instanceof PolylineTrace) {
          traceIds.add(trace.getId());
        }
      }
      Collections.sort(traceIds);
      for (int traceId : traceIds) {
        if (isStopRequested()) {
          break;
        }
        PolylineTrace trace = traceById(traceId);
        if (trace == null || trace.isShoveFixed() || trace.isUserFixed()) {
          continue;
        }
        List<Point[]> moves = moves(trace);
        if (moves.isEmpty()) {
          continue;
        }
        int halfPitch = halfPitch(trace);
        Cost before = cost();
        Map<Integer, Integer> tracesBefore = traceSignatures();
        int incompleteBefore = board.routingLedger().incompleteCount();
        double bendWeight = BEND_WEIGHT_IN_HALF_PITCHES * halfPitch;
        double tolerance = 1e-6 * Math.max(1.0, before.length);
        Point[] bestMove = null;
        double bestScore = before.length + bendWeight * before.bends;
        for (Point[] move : moves) {
          ++tried;
          board.generateSnapshot();
          if (insert(traceId, move) && !changedTraceEntersZone(tracesBefore)) {
            Cost after = cost();
            boolean notWorse =
                after.vias <= before.vias
                    && after.bends <= before.bends
                    && after.length <= before.length + tolerance
                    && board.routingLedger().incompleteCount() <= incompleteBefore;
            boolean better = after.bends < before.bends || after.length < before.length - tolerance;
            double score = after.length + bendWeight * after.bends;
            if (notWorse && better && score < bestScore - tolerance) {
              bestScore = score;
              bestMove = move;
            }
          }
          board.undo(null);
          board.clearTransientAutorouteState();
        }
        if (bestMove == null) {
          continue;
        }
        board.generateSnapshot();
        if (insert(traceId, bestMove)
            && !changedTraceEntersZone(tracesBefore)
            && board.routingLedger().incompleteCount() <= incompleteBefore
            && board.getStatistics().clearanceViolations.totalCount <= violationsAllowed) {
          board.popSnapshot();
          board.clearTransientAutorouteState();
          ++accepted;
          ++acceptedInThisPass;
        } else {
          board.undo(null);
          board.clearTransientAutorouteState();
        }
      }
      if (acceptedInThisPass == 0) {
        break;
      }
    }
    Cost end = cost();
    return new Result(
        passes,
        tried,
        accepted,
        start.length,
        end.length,
        start.bends,
        end.bends,
        start.vias,
        end.vias,
        incompleteAtStart,
        board.routingLedger().incompleteCount(),
        violationsAllowed,
        board.getStatistics().clearanceViolations.totalCount);
  }

  /**
   * The corners of trace with one of its steps moved, for every step and every place it can be
   * moved to with a chance of a gain: the step must merge with a segment or meet another step
   * there, so that the trace loses a bend or the following pull tight can shorten it.
   */
  private List<Point[]> moves(PolylineTrace trace) {
    List<Point[]> result = new ArrayList<>();
    int cornerCount = trace.cornerCount();
    if (cornerCount < 4) {
      return result;
    }
    IntPoint[] corners = new IntPoint[cornerCount];
    for (int i = 0; i < cornerCount; i++) {
      if (!(trace.polyline().corner(i) instanceof IntPoint corner)) {
        return result;
      }
      corners[i] = corner;
    }
    double maxStepLength = 2.0 * MAX_STEP_LENGTH_IN_PITCHES * halfPitch(trace);
    int steps = stepCount(corners);
    for (int k = 1; k + 2 < cornerCount; k++) {
      if (!isStep(corners, k)
          || corners[k].toFloat().distance(corners[k + 1].toFloat()) > maxStepLength) {
        continue;
      }
      Vector step = corners[k + 1].differenceBy(corners[k]);
      for (int m = 1; m <= MAX_SLIDE_SEGMENTS; m++) {
        if (k + m + 1 < cornerCount) {
          addIfPromising(result, slide(corners, k, m + 1, step), cornerCount, steps);
        }
        if (k - m >= 0) {
          addIfPromising(result, slide(corners, k, -m, step), cornerCount, steps);
        }
      }
    }
    return result;
  }

  /** Keeps a moved trace that has fewer corners, or as many corners and fewer steps. */
  private static void addIfPromising(
      List<Point[]> result, Point[] moved, int cornerCount, int steps) {
    if (moved.length < 2 || moved.length > cornerCount) {
      return;
    }
    if (moved.length == cornerCount && stepCount(moved) >= steps) {
      return;
    }
    result.add(moved);
  }

  /**
   * The corners of the trace with the step from corners[k] to corners[k + 1] moved over |shift|
   * segments. Forward (shift {@literal >} 0), the corners k + 2 .. k + shift are translated by
   * -step and the step ends at corners[k + shift]. Backward (shift {@literal <} 0), the corners k +
   * shift .. k - 1 are translated by +step and the step starts at corners[k + shift]. The first and
   * the last corner never move. Repeated and collinear corners are dropped.
   */
  static Point[] slide(IntPoint[] corners, int k, int shift, Vector step) {
    List<Point> moved = new ArrayList<>(corners.length + 1);
    int keptBefore = shift > 0 ? k : k + shift;
    int from = shift > 0 ? k + 2 : k + shift;
    int to = shift > 0 ? k + shift : k - 1;
    int keptAfter = shift > 0 ? k + shift : k + 1;
    Vector translation = shift > 0 ? step.negate() : step;
    for (int i = 0; i <= keptBefore; i++) {
      moved.add(corners[i]);
    }
    for (int i = from; i <= to; i++) {
      moved.add(corners[i].translateBy(translation));
    }
    for (int i = keptAfter; i < corners.length; i++) {
      moved.add(corners[i]);
    }
    return withoutCollinearCorners(moved);
  }

  /** Drops repeated corners and corners in the middle of a straight line or of a turn back. */
  private static Point[] withoutCollinearCorners(List<Point> corners) {
    List<Point> result = new ArrayList<>(corners.size());
    for (Point corner : corners) {
      if (!result.isEmpty() && result.getLast().equals(corner)) {
        continue;
      }
      while (result.size() >= 2
          && corner.sideOf(result.get(result.size() - 2), result.getLast()) == Side.COLLINEAR) {
        result.removeLast();
      }
      result.add(corner);
    }
    return result.toArray(new Point[0]);
  }

  /** The number of steps of a trace with these corners, whatever their length. */
  static int stepCount(Point[] corners) {
    int count = 0;
    for (int k = 1; k + 2 < corners.length; k++) {
      if (isStep(corners, k)) {
        count++;
      }
    }
    return count;
  }

  /**
   * Whether the segment from corners[k] to corners[k + 1] is a step: the segments before and after
   * it have the same direction, and it is not parallel to them.
   */
  static boolean isStep(Point[] corners, int k) {
    if (!(corners[k - 1] instanceof IntPoint a
        && corners[k] instanceof IntPoint b
        && corners[k + 1] instanceof IntPoint c
        && corners[k + 2] instanceof IntPoint d)) {
      return false;
    }
    long ax = (long) b.x - a.x;
    long ay = (long) b.y - a.y;
    long sx = (long) c.x - b.x;
    long sy = (long) c.y - b.y;
    long bx = (long) d.x - c.x;
    long by = (long) d.y - c.y;
    return ax * by - ay * bx == 0 && ax * bx + ay * by > 0 && ax * sy - ay * sx != 0;
  }

  /** Replaces the trace with the given id by a trace through corners, inserted with shoving. */
  private boolean insert(int traceId, Point[] corners) {
    PolylineTrace trace = traceById(traceId);
    if (trace == null) {
      return false;
    }
    try {
      Polyline polyline = new Polyline(corners);
      if (polyline.cornerCount() < 2) {
        return false;
      }
      int halfWidth = trace.getHalfWidth();
      int layer = trace.getLayer();
      int clearanceClass = trace.clearanceClassIndex();
      int[] netNumbers = trace.netNumbers;
      if (!board.removeItems(List.of((Item) trace))) {
        return false;
      }
      Point end =
          board.insertForcedTracePolyline(
              polyline,
              halfWidth,
              layer,
              netNumbers,
              clearanceClass,
              MAX_SHOVE_RECURSION_DEPTH,
              0,
              MAX_SHOVE_RECURSION_DEPTH,
              Integer.MAX_VALUE,
              this.pullTightAccuracy,
              true,
              null);
      return end != null && end.equals(polyline.lastCorner());
    } catch (RuntimeException e) {
      return false;
    }
  }

  private boolean isStopRequested() {
    return this.stoppable != null && this.stoppable.isStopRequested();
  }

  private PolylineTrace traceById(int id) {
    for (Trace trace : board.getTraces()) {
      if (trace.getId() == id && trace instanceof PolylineTrace polylineTrace) {
        return polylineTrace;
      }
    }
    return null;
  }

  /**
   * Whether trace is inside a zone or closer to its outline than the clearance, tested with exact
   * shape intersection against the zone's convex tiles.
   */
  private boolean traceEntersZone(PolylineTrace trace) {
    int layer = trace.getLayer();
    int halfWidth = trace.getHalfWidth();
    int clearanceClass = trace.clearanceClassIndex();
    IntBox traceBox = trace.boundingBox();
    for (ConductionArea zone : zones) {
      if (zone.getLayer() != layer) {
        continue;
      }
      int reach =
          halfWidth + board.clearanceValue(clearanceClass, zone.clearanceClassIndex(), layer);
      IntBox zoneBox = zone.boundingBox();
      if (traceBox.ur.x + reach < zoneBox.ll.x
          || traceBox.ll.x - reach > zoneBox.ur.x
          || traceBox.ur.y + reach < zoneBox.ll.y
          || traceBox.ll.y - reach > zoneBox.ur.y) {
        continue;
      }
      TileShape[] traceShapes = trace.polyline().offsetShapes(reach);
      int zoneTileCount = zone.tileShapeCount();
      for (TileShape traceShape : traceShapes) {
        for (int j = 0; j < zoneTileCount; ++j) {
          if (traceShape.intersects(zone.getTileShape(j))) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /** The corners of every trace, hashed, by trace id. Empty on a board without zones. */
  private Map<Integer, Integer> traceSignatures() {
    Map<Integer, Integer> result = new HashMap<>();
    if (zones.isEmpty()) {
      return result;
    }
    for (Item item : board.getItems()) {
      if (item instanceof PolylineTrace trace) {
        result.put(trace.getId(), signature(trace));
      }
    }
    return result;
  }

  private static int signature(PolylineTrace trace) {
    int result = trace.getLayer();
    for (int i = 0; i < trace.cornerCount(); ++i) {
      FloatPoint corner = trace.polyline().corner(i).toFloat();
      result = 31 * result + Double.hashCode(corner.x);
      result = 31 * result + Double.hashCode(corner.y);
    }
    return result;
  }

  /** Whether a trace that is new or changed since tracesBefore runs in or too close to a zone. */
  private boolean changedTraceEntersZone(Map<Integer, Integer> tracesBefore) {
    if (zones.isEmpty()) {
      return false;
    }
    for (Item item : board.getItems()) {
      if (!(item instanceof PolylineTrace trace)) {
        continue;
      }
      Integer before = tracesBefore.get(trace.getId());
      if (before != null && before == signature(trace)) {
        continue;
      }
      if (traceEntersZone(trace)) {
        return true;
      }
    }
    return false;
  }

  /** Half of the width plus the clearance of trace. */
  private int halfPitch(PolylineTrace trace) {
    int pitch =
        2 * trace.getHalfWidth()
            + board.clearanceValue(
                trace.clearanceClassIndex(), BoardRules.defaultClearanceClass(), trace.getLayer());
    return Math.max(1, pitch / 2);
  }

  private Cost cost() {
    double length = 0;
    int bends = 0;
    int vias = 0;
    for (Item item : board.getItems()) {
      if (item instanceof PolylineTrace trace) {
        length += trace.getLength();
        bends += Math.max(0, trace.cornerCount() - 2);
      } else if (item instanceof Via) {
        ++vias;
      }
    }
    return new Cost(length, bends, vias);
  }
}
