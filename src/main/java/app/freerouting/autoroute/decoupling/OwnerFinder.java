package app.freerouting.autoroute.decoupling;

import app.freerouting.board.model.items.Pin;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Discovers nearby IC supply load pins for capacitor candidates using a uniform-grid spatial hash.
 */
public final class OwnerFinder {

  private OwnerFinder() {}

  /**
   * Identifies candidate IC supply owner pins within {@code maxOwnerRadiusMm} of each candidate and
   * awards the {@link ReasonCode#OWNER_IN_RADIUS} bonus when found.
   *
   * @param states evaluated candidate states
   * @param boardIndex indexed board topology
   * @param config configuration parameters
   * @param resolutionMm internal board units per millimeter
   */
  public static void findOwners(
      List<CapacitorScorer.EvaluationState> states,
      BoardIndex boardIndex,
      IdentifierConfig config,
      double resolutionMm) {
    if (states == null || states.isEmpty() || boardIndex == null) {
      return;
    }

    long cellSize = Math.max(1L, Math.round(config.maxOwnerRadiusMm() * resolutionMm));
    long maxRadiusSq = cellSize * cellSize;

    // 1. Identify all rail nets that have active candidates
    Set<Integer> targetRails = new HashSet<>();
    for (CapacitorScorer.EvaluationState state : states) {
      if (state.gatePassed && state.railNet > 0) {
        targetRails.add(state.railNet);
      }
    }

    if (targetRails.isEmpty()) {
      return;
    }

    // 2. Build spatial grid indexing only IC pins on target rail nets
    Map<Long, List<Pin>> grid = new HashMap<>();

    for (int railNet : targetRails) {
      List<Pin> railPins = boardIndex.getPinsOfNet(railNet);
      for (Pin pin : railPins) {
        int compId = pin.getComponentId();
        if (compId > 0 && boardIndex.getComponentType(compId) == ComponentType.IC) {
          Point center = pin.getCenter();
          if (center != null) {
            long cx = Math.floorDiv(getCoord(center, true), cellSize);
            long cy = Math.floorDiv(getCoord(center, false), cellSize);
            long key = packCellKey((int) cx, (int) cy);
            grid.computeIfAbsent(key, k -> new ArrayList<>()).add(pin);
          }
        }
      }
    }

    // 3. Query 3x3 cell neighborhood for each capacitor candidate
    for (CapacitorScorer.EvaluationState state : states) {
      if (!state.gatePassed || state.railNet <= 0) {
        continue;
      }

      Pin capSupplyPin = null;
      if (pinContainsNet(state.pinA, state.railNet)) {
        capSupplyPin = state.pinA;
      } else if (pinContainsNet(state.pinB, state.railNet)) {
        capSupplyPin = state.pinB;
      }
      if (capSupplyPin == null) {
        continue;
      }
      Point capCenter = capSupplyPin.getCenter();
      if (capCenter == null) {
        continue;
      }

      long centerCx = Math.floorDiv(getCoord(capCenter, true), cellSize);
      long centerCy = Math.floorDiv(getCoord(capCenter, false), cellSize);

      List<OwnerCandidate> candidateOwners = new ArrayList<>();
      Set<Integer> seenPinIds = new HashSet<>();

      for (int dx = -1; dx <= 1; dx++) {
        for (int dy = -1; dy <= 1; dy++) {
          long key = packCellKey((int) (centerCx + dx), (int) (centerCy + dy));
          List<Pin> bucket = grid.get(key);
          if (bucket != null) {
            for (Pin icPin : bucket) {
              if (seenPinIds.add(icPin.getId()) && pinContainsNet(icPin, state.railNet)) {
                long distSq = computeDistanceSquared(capCenter, icPin.getCenter());
                if (distSq <= maxRadiusSq) {
                  int icCompId = icPin.getComponentId();
                  int icPinCount = boardIndex.getPinsOfComponent(icCompId).size();
                  candidateOwners.add(
                      new OwnerCandidate(icPin.getId(), icCompId, icPinCount, distSq));
                }
              }
            }
          }
        }
      }

      // Sort by distance squared ascending, tie-breaking by pin ID ascending
      Collections.sort(
          candidateOwners,
          (o1, o2) -> {
            int cmp = Long.compare(o1.distanceSquared(), o2.distanceSquared());
            return cmp != 0 ? cmp : Integer.compare(o1.pinId(), o2.pinId());
          });

      // Keep up to ownerTopK
      int topK = Math.min(candidateOwners.size(), config.ownerTopK());
      state.owners = List.copyOf(candidateOwners.subList(0, topK));

      // Award OWNER_IN_RADIUS bonus (+20 points)
      if (!state.owners.isEmpty()) {
        state.reasonMask |= ReasonCode.OWNER_IN_RADIUS.mask();
        state.score = Math.clamp(state.score + CapacitorScorer.SCORE_OWNER_IN_RADIUS, 0, 100);

        if (!ReasonCode.AMBIGUOUS_GROUND.isSet(state.reasonMask)) {
          if (state.score >= config.decouplingThreshold()) {
            state.verdict = Verdict.DECOUPLING;
          } else if (state.score >= config.reviewThreshold()) {
            state.verdict = Verdict.REVIEW;
          }
        }
      }
    }
  }

  private static boolean pinContainsNet(Pin pin, int targetNet) {
    for (int i = 0; i < pin.netCount(); i++) {
      if (pin.getNetNumber(i) == targetNet) {
        return true;
      }
    }
    return false;
  }

  private static long packCellKey(int cx, int cy) {
    return (((long) cx) << 32) | (cy & 0xFFFFFFFFL);
  }

  private static long computeDistanceSquared(Point p1, Point p2) {
    if (p1 == null || p2 == null) {
      return Long.MAX_VALUE;
    }
    long dx = getCoord(p1, true) - getCoord(p2, true);
    long dy = getCoord(p1, false) - getCoord(p2, false);
    return dx * dx + dy * dy;
  }

  private static long getCoord(Point p, boolean isX) {
    if (p instanceof IntPoint ip) {
      return isX ? ip.x : ip.y;
    }
    return Math.round(isX ? p.toFloat().x : p.toFloat().y);
  }
}
