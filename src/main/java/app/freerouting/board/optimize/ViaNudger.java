package app.freerouting.board.optimize;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.datastructures.Stoppable;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.IntVector;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.rules.BoardRules;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Moves vias to shorten and straighten the traces of the whole board, not only the traces of the
 * via.
 *
 * <p>{@link ViaOptimizer} moves a via towards the next corner of its own two traces, without
 * shoving, and reads only the length of these two traces. A via that sits in a channel of foreign
 * traces therefore stays where it is, and the foreign traces keep their detour around it. This pass
 * tries a fixed set of translations for each via, with shoving and pull tight, and keeps a
 * translation only if the board as a whole gets better: no more vias, no more incomplete
 * connections, no more clearance violations, no more trace length and no more bends, and less of
 * one of the last two.
 *
 * <p>A plane or copper zone is only known by its outline: its real copper is what is left once it
 * is filled around the other items, and moving copper inside it can cut it in two without the board
 * noticing. So a via that touches a plane, or that is inside a zone or closer to its outline than
 * the clearance, stays where it is, a via is never moved to such a place, and a translation that
 * shoves a trace into a zone is refused.
 *
 * <p>The pass does nothing on a board that already has clearance violations: shoving next to copper
 * that violates a clearance is not reliable.
 *
 * <p>The pass is deterministic and does not read the clock: vias are visited in ascending id order,
 * and the translations in a fixed order.
 */
public final class ViaNudger {

  /** The translations tried for a via, in units of half the pitch of its thinnest trace. */
  private static final int[] STEPS_IN_HALF_PITCHES = {1, 2, 4, 6};

  private static final int[][] DIRECTIONS = {
    {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}
  };

  /** A bend is worth this many half pitches of trace when two improving moves are compared. */
  private static final double BEND_WEIGHT_IN_HALF_PITCHES = 0.5;

  private static final int MAX_PASSES = 10;

  /** A via is tried again when a via moved within this many half pitches of it. */
  private static final int RETRY_RADIUS_IN_HALF_PITCHES = 50;

  private static final int MAX_SHOVE_RECURSION_DEPTH = 9;

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

  private ViaNudger(RoutingBoard board, int pullTightAccuracy, Stoppable stoppable) {
    this.board = board;
    this.pullTightAccuracy = pullTightAccuracy;
    this.stoppable = stoppable;
    this.zones = new ArrayList<>(board.getConductionAreas());
  }

  /** Runs the pass on board until no via can be moved with a gain. */
  public static Result run(RoutingBoard board, int pullTightAccuracy, Stoppable stoppable) {
    return new ViaNudger(board, pullTightAccuracy, stoppable).run();
  }

  private Result run() {
    final Cost start = cost();
    final int incompleteAtStart = board.routingLedger().incompleteCount();
    int violationsAllowed = board.getStatistics().clearanceViolations.totalCount;
    if (violationsAllowed > 0) {
      // Shoving next to copper that already violates a clearance is not reliable: leave the board.
      return new Result(
          0,
          0,
          0,
          start.length,
          start.length,
          start.bends,
          start.bends,
          start.vias,
          start.vias,
          incompleteAtStart,
          incompleteAtStart,
          violationsAllowed,
          violationsAllowed);
    }
    List<Integer> viaIds = new ArrayList<>();
    for (Via via : board.getVias()) {
      viaIds.add(via.getId());
    }
    Collections.sort(viaIds);

    int tried = 0;
    int accepted = 0;
    int passes = 0;
    List<FloatPoint> movedInPreviousPass = null;
    while (passes < MAX_PASSES && !isStopRequested()) {
      ++passes;
      List<FloatPoint> movedInThisPass = new ArrayList<>();
      for (int viaId : viaIds) {
        if (isStopRequested()) {
          break;
        }
        Via via = viaById(viaId);
        if (via == null
            || via.isShoveFixed()
            || via.isUserFixed()
            || touchesConductionArea(via)
            || hasShoveFixedContact(via)) {
          continue;
        }
        int halfPitch = halfPitch(via);
        if (halfPitch <= 0) {
          continue;
        }
        if (!(via.getCenter() instanceof IntPoint centerPoint)) {
          continue;
        }
        FloatPoint center = centerPoint.toFloat();
        if (isInOrNearZone(via, centerPoint)) {
          continue;
        }
        if (movedInPreviousPass != null
            && !isNear(center, movedInPreviousPass, halfPitch)
            && !isNear(center, movedInThisPass, halfPitch)) {
          continue;
        }
        Cost before = cost();
        Map<Integer, Integer> tracesBefore = traceSignatures();
        int incompleteBefore = board.routingLedger().incompleteCount();
        double bendWeight = BEND_WEIGHT_IN_HALF_PITCHES * halfPitch;
        double tolerance = 1e-6 * Math.max(1.0, before.length);
        IntVector bestMove = null;
        double bestScore = before.length + bendWeight * before.bends;
        for (int[] direction : DIRECTIONS) {
          for (int step : STEPS_IN_HALF_PITCHES) {
            IntVector move =
                new IntVector(direction[0] * step * halfPitch, direction[1] * step * halfPitch);
            IntPoint target = new IntPoint(centerPoint.x + move.x, centerPoint.y + move.y);
            if (isInOrNearZone(via, target)) {
              continue;
            }
            ++tried;
            board.generateSnapshot();
            boolean moved = move(via, move) && !changedTraceEntersZone(tracesBefore);
            if (moved) {
              Cost after = cost();
              boolean notWorse =
                  after.vias <= before.vias
                      && after.bends <= before.bends
                      && after.length <= before.length + tolerance
                      && board.routingLedger().incompleteCount() <= incompleteBefore;
              boolean better =
                  after.bends < before.bends || after.length < before.length - tolerance;
              double score = after.length + bendWeight * after.bends;
              if (notWorse && better && score < bestScore - tolerance) {
                bestScore = score;
                bestMove = move;
              }
            }
            board.undo(null);
            board.clearTransientAutorouteState();
            via = viaById(viaId);
            if (via == null) {
              break;
            }
          }
          if (via == null) {
            break;
          }
        }
        if (bestMove == null || via == null) {
          continue;
        }
        board.generateSnapshot();
        if (move(via, bestMove)
            && !changedTraceEntersZone(tracesBefore)
            && board.routingLedger().incompleteCount() <= incompleteBefore
            && board.getStatistics().clearanceViolations.totalCount <= violationsAllowed) {
          board.popSnapshot();
          board.clearTransientAutorouteState();
          ++accepted;
          movedInThisPass.add(center);
        } else {
          board.undo(null);
          board.clearTransientAutorouteState();
        }
      }
      if (movedInThisPass.isEmpty()) {
        break;
      }
      movedInPreviousPass = movedInThisPass;
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

  private boolean move(Via via, IntVector move) {
    try {
      return board.moveDrillItem(
          via, move, MAX_SHOVE_RECURSION_DEPTH, 0, Integer.MAX_VALUE, this.pullTightAccuracy, 0);
    } catch (RuntimeException e) {
      return false;
    }
  }

  private boolean isStopRequested() {
    return this.stoppable != null && this.stoppable.isStopRequested();
  }

  private static boolean isNear(FloatPoint point, List<FloatPoint> others, int halfPitch) {
    double radius = (double) RETRY_RADIUS_IN_HALF_PITCHES * halfPitch;
    for (FloatPoint other : others) {
      if (Math.abs(point.x - other.x) <= radius && Math.abs(point.y - other.y) <= radius) {
        return true;
      }
    }
    return false;
  }

  private Via viaById(int id) {
    for (Via via : board.getVias()) {
      if (via.getId() == id) {
        return via;
      }
    }
    return null;
  }

  /**
   * A via that connects a plane stays where it is: the board only knows the outline of the plane,
   * not the copper that is left of it once it is filled around the other items.
   */
  private static boolean touchesConductionArea(Via via) {
    for (Item contact : via.getNormalContacts()) {
      if (contact instanceof ConductionArea) {
        return true;
      }
    }
    return false;
  }

  /**
   * {@link RoutingBoard#moveDrillItem} unfixes {@code SHOVE_FIXED} contacts before moving a drill
   * item; skip vias with any shove-fixed contact so we never unlock protected traces.
   */
  private static boolean hasShoveFixedContact(Via via) {
    for (Item contact : via.getNormalContacts()) {
      if (contact.isShoveFixed()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Whether a via with the pads of via and its centre at center is in or too close to a zone,
   * tested with exact circle-versus-zone tile intersection.
   */
  private boolean isInOrNearZone(Via via, IntPoint center) {
    if (zones.isEmpty()) {
      return false;
    }
    int clearanceClass = via.clearanceClassIndex();
    for (int layer = via.firstLayer(); layer <= via.lastLayer(); ++layer) {
      IntBox pad = via.getShape(layer - via.firstLayer()).boundingBox();
      int halfWidth = (int) Math.ceil(Math.max(pad.width(), pad.height()) / 2.0);
      for (ConductionArea zone : zones) {
        if (zone.getLayer() != layer) {
          continue;
        }
        int reach =
            halfWidth + board.clearanceValue(clearanceClass, zone.clearanceClassIndex(), layer);
        IntBox zoneBox = zone.boundingBox();
        if (center.x + reach < zoneBox.ll.x
            || center.x - reach > zoneBox.ur.x
            || center.y + reach < zoneBox.ll.y
            || center.y - reach > zoneBox.ur.y) {
          continue;
        }
        Circle viaCircle = new Circle(center, reach);
        int zoneTileCount = zone.tileShapeCount();
        for (int j = 0; j < zoneTileCount; ++j) {
          if (viaCircle.intersects(zone.getTileShape(j))) {
            return true;
          }
        }
      }
    }
    return false;
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

  /** Half of the width plus the clearance of the thinnest trace that ends at via. */
  private int halfPitch(Via via) {
    int result = 0;
    for (Item contact : via.getNormalContacts()) {
      if (!(contact instanceof Trace trace)) {
        continue;
      }
      int pitch =
          2 * trace.getHalfWidth()
              + board.clearanceValue(
                  trace.clearanceClassIndex(),
                  BoardRules.defaultClearanceClass(),
                  trace.getLayer());
      int halfPitch = pitch / 2;
      if (result == 0 || halfPitch < result) {
        result = halfPitch;
      }
    }
    return result;
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
