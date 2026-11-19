package app.freerouting.autoroute.decoupling;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Deterministic assignment planner for decoupling capacitor candidates.
 *
 * <p>Clusters decoupling capacitors by {@code (ownerComponentId, railNetNumber)}, prioritizes
 * lowest-ESL (smallest pad area) packages, and computes an optimal rectangular min-cost matching
 * (Hungarian algorithm) between capacitors and IC supply pins.
 *
 * <p>When a cluster has more capacitors than IC pins ($M &gt; N$, parallel sibling banks), the
 * planner assigns the smallest $N$ capacitors to primary IC straps and assigns the remaining $M -
 * N$ overflow capacitors as secondary parallel siblings paired with the nearest primary cap.
 */
public final class DecouplingAssignmentPlanner {

  /** Large penalty cost for unreachable pairings (exceeding max radius), safe from overflow. */
  private static final long INF_COST = 1_000_000_000_000_000L; // 10^15

  private DecouplingAssignmentPlanner() {}

  /**
   * Plans routing tasks for the given list of capacitor candidates.
   *
   * @param board routing board context
   * @param candidates evaluated capacitor candidates
   * @param maxOwnerRadiusMm maximum search radius in millimeters
   * @param includeReview whether to include REVIEW candidates
   * @return ordered list of decoupling route tasks
   */
  public static List<DecouplingRouteTask> plan(
      BasicBoard board,
      List<CapacitorCandidate> candidates,
      double maxOwnerRadiusMm,
      boolean includeReview) {
    if (board == null || candidates == null || candidates.isEmpty()) {
      return List.of();
    }

    double mmResolution = board.communication.getResolution(Unit.MM);
    double maxRadiusUnits = maxOwnerRadiusMm * mmResolution;
    long maxRadiusSq = Math.round(maxRadiusUnits * maxRadiusUnits);

    List<DecouplingRouteTask> primaryTasks = new ArrayList<>();
    List<DecouplingRouteTask> secondaryTasks = new ArrayList<>();
    List<DecouplingRouteTask> fallbackTasks = new ArrayList<>();

    // 1. Group eligible candidates by cluster key: (ownerComponentId, railNetNumber)
    Map<ClusterKey, List<CapacitorCandidate>> clusters = new HashMap<>();

    for (CapacitorCandidate candidate : candidates) {
      boolean eligible =
          (candidate.verdict() == Verdict.DECOUPLING
                  || (includeReview && candidate.verdict() == Verdict.REVIEW))
              && candidate.audit() != AuditResult.CONFLICT;
      if (!eligible) {
        continue;
      }

      int supplyPinId = resolveSupplyPinId(board, candidate);
      int groundPinId = resolveGroundPinId(board, candidate, supplyPinId);
      if (supplyPinId <= 0 || groundPinId <= 0) {
        continue;
      }

      // If candidate has no owners within radius, route via plane-only fallback
      if (candidate.owners().isEmpty()) {
        fallbackTasks.add(
            new DecouplingRouteTask(
                candidate, supplyPinId, groundPinId, 0, 0, TaskRole.PLANE_ONLY_FALLBACK, 0));
        continue;
      }

      int primaryOwnerCompId = candidate.owners().getFirst().componentId();
      ClusterKey key = new ClusterKey(primaryOwnerCompId, candidate.railNetNumber());
      clusters.computeIfAbsent(key, k -> new ArrayList<>()).add(candidate);
    }

    // 2. Sort cluster keys deterministically
    List<ClusterKey> sortedKeys = new ArrayList<>(clusters.keySet());
    sortedKeys.sort(
        Comparator.comparingInt(ClusterKey::componentId)
            .thenComparingInt(ClusterKey::railNetNumber));

    // 3. Process each cluster independently
    for (ClusterKey key : sortedKeys) {
      List<CapacitorCandidate> clusterCaps = clusters.get(key);
      planCluster(
          board, key, clusterCaps, maxRadiusSq, primaryTasks, secondaryTasks, fallbackTasks);
    }

    // 4. Combine in execution order: Primary straps first, then secondary parallel, then fallbacks
    List<DecouplingRouteTask> allTasks =
        new ArrayList<>(primaryTasks.size() + secondaryTasks.size() + fallbackTasks.size());
    allTasks.addAll(primaryTasks);
    allTasks.addAll(secondaryTasks);
    allTasks.addAll(fallbackTasks);
    return Collections.unmodifiableList(allTasks);
  }

  private static void planCluster(
      BasicBoard board,
      ClusterKey key,
      List<CapacitorCandidate> clusterCaps,
      long maxRadiusSq,
      List<DecouplingRouteTask> primaryTasks,
      List<DecouplingRouteTask> secondaryTasks,
      List<DecouplingRouteTask> fallbackTasks) {
    if (clusterCaps.isEmpty()) {
      return;
    }

    // Sort candidates: lowest ESL (padArea sum asc), distanceSquared asc, score desc, compId asc,
    // pinIdA asc
    clusterCaps.sort(
        (a, b) -> {
          int cmp = Long.compare(a.padAreaA() + a.padAreaB(), b.padAreaA() + b.padAreaB());
          if (cmp != 0) {
            return cmp;
          }
          long distA =
              a.owners().isEmpty() ? Long.MAX_VALUE : a.owners().getFirst().distanceSquared();
          long distB =
              b.owners().isEmpty() ? Long.MAX_VALUE : b.owners().getFirst().distanceSquared();
          cmp = Long.compare(distA, distB);
          if (cmp != 0) {
            return cmp;
          }
          cmp = Integer.compare(b.score(), a.score());
          if (cmp != 0) {
            return cmp;
          }
          cmp = Integer.compare(a.componentId(), b.componentId());
          if (cmp != 0) {
            return cmp;
          }
          return Integer.compare(a.pinIdA(), b.pinIdA());
        });

    // Collect distinct candidate IC supply pins belonging to this cluster's owner component
    Set<Integer> availableIcPinIds = new TreeSet<>();
    for (CapacitorCandidate c : clusterCaps) {
      for (OwnerCandidate oc : c.owners()) {
        if (oc.componentId() == key.componentId()) {
          availableIcPinIds.add(oc.pinId());
        }
      }
    }

    int groundNetNo = clusterCaps.getFirst().groundNetNumber();
    List<Pin> icGroundPins = new ArrayList<>();
    if (groundNetNo > 0 && key.componentId() > 0) {
      java.util.Collection<Pin> compPins = board.getComponentPins(key.componentId());
      if (compPins != null) {
        for (Pin p : compPins) {
          if (p.containsNet(groundNetNo)) {
            icGroundPins.add(p);
          }
        }
      }
    }

    if (availableIcPinIds.isEmpty()) {
      for (CapacitorCandidate c : clusterCaps) {
        int supplyPinId = resolveSupplyPinId(board, c);
        int groundPinId = resolveGroundPinId(board, c, supplyPinId);
        int targetGndId = resolveNearestGroundPinId(board, icGroundPins, groundPinId);
        fallbackTasks.add(
            new DecouplingRouteTask(
                c, supplyPinId, groundPinId, 0, targetGndId, TaskRole.PLANE_ONLY_FALLBACK, 0));
      }
      return;
    }

    List<Integer> icPinList = new ArrayList<>(availableIcPinIds);
    int m = clusterCaps.size();
    int n = icPinList.size();

    int primaryCount = Math.min(m, n);
    List<CapacitorCandidate> primaryCaps = clusterCaps.subList(0, primaryCount);
    List<CapacitorCandidate> overflowCaps = clusterCaps.subList(primaryCount, m);

    // Build cost matrix: primaryCount rows x n columns
    long[][] costMatrix = new long[primaryCount][n];
    for (int i = 0; i < primaryCount; i++) {
      CapacitorCandidate cap = primaryCaps.get(i);
      int capSupplyPinId = resolveSupplyPinId(board, cap);
      Pin capSupplyPin = getPin(board, capSupplyPinId);
      Point capCenter = capSupplyPin != null ? capSupplyPin.getCenter() : null;

      for (int j = 0; j < n; j++) {
        Pin icPin = getPin(board, icPinList.get(j));
        Point icCenter = icPin != null ? icPin.getCenter() : null;
        long distSq = computeDistanceSquared(capCenter, icCenter);
        costMatrix[i][j] = (distSq > maxRadiusSq) ? INF_COST : distSq;
      }
    }

    // Solve min-cost bipartite matching
    int[] matching = solveMinCostMatching(costMatrix, primaryCount, n);

    List<DecouplingRouteTask> clusterPrimaryTasks = new ArrayList<>();
    for (int i = 0; i < primaryCount; i++) {
      CapacitorCandidate cap = primaryCaps.get(i);
      int capSupplyPinId = resolveSupplyPinId(board, cap);
      int capGroundPinId = resolveGroundPinId(board, cap, capSupplyPinId);
      int matchedCol = matching[i];
      int targetGroundPinId = resolveNearestGroundPinId(board, icGroundPins, capGroundPinId);

      if (matchedCol >= 0 && costMatrix[i][matchedCol] < INF_COST) {
        int matchedIcPinId = icPinList.get(matchedCol);
        DecouplingRouteTask task =
            new DecouplingRouteTask(
                cap,
                capSupplyPinId,
                capGroundPinId,
                matchedIcPinId,
                targetGroundPinId,
                TaskRole.PRIMARY_IC_STRAP,
                0);
        clusterPrimaryTasks.add(task);
        primaryTasks.add(task);
      } else {
        fallbackTasks.add(
            new DecouplingRouteTask(
                cap,
                capSupplyPinId,
                capGroundPinId,
                0,
                targetGroundPinId,
                TaskRole.PLANE_ONLY_FALLBACK,
                0));
      }
    }

    // Handle overflow parallel capacitors (M > N, PARALLEL_SIBLING banks)
    for (CapacitorCandidate overflowCap : overflowCaps) {
      int capSupplyPinId = resolveSupplyPinId(board, overflowCap);
      int capGroundPinId = resolveGroundPinId(board, overflowCap, capSupplyPinId);

      // Find nearest primary task in this cluster
      DecouplingRouteTask nearestPrimary = null;
      long bestDistSq = Long.MAX_VALUE;

      Pin overflowSupplyPin = getPin(board, capSupplyPinId);
      Point overflowCenter = overflowSupplyPin != null ? overflowSupplyPin.getCenter() : null;

      for (DecouplingRouteTask primaryTask : clusterPrimaryTasks) {
        Pin primarySupplyPin = getPin(board, primaryTask.supplyPinId());
        Point primaryCenter = primarySupplyPin != null ? primarySupplyPin.getCenter() : null;
        long distSq = computeDistanceSquared(overflowCenter, primaryCenter);
        if (distSq < bestDistSq) {
          bestDistSq = distSq;
          nearestPrimary = primaryTask;
        }
      }

      int targetGroundPinId =
          nearestPrimary != null
              ? nearestPrimary.targetGroundItemId()
              : resolveNearestGroundPinId(board, icGroundPins, capGroundPinId);

      if (nearestPrimary != null) {
        // Target owner IC pin for preferred V-star; paired with primary cap supply pin for flanking
        secondaryTasks.add(
            new DecouplingRouteTask(
                overflowCap,
                capSupplyPinId,
                capGroundPinId,
                nearestPrimary.targetSupplyItemId(),
                targetGroundPinId,
                TaskRole.SECONDARY_PARALLEL_SIBLING,
                nearestPrimary.supplyPinId()));
      } else {
        fallbackTasks.add(
            new DecouplingRouteTask(
                overflowCap,
                capSupplyPinId,
                capGroundPinId,
                0,
                targetGroundPinId,
                TaskRole.PLANE_ONLY_FALLBACK,
                0));
      }
    }
  }

  private static int resolveNearestGroundPinId(
      BasicBoard board, List<Pin> icGroundPins, int capGroundPinId) {
    if (icGroundPins == null || icGroundPins.isEmpty()) {
      return 0;
    }
    Pin capGndPin = getPin(board, capGroundPinId);
    Point capGndCenter = capGndPin != null ? capGndPin.getCenter() : null;
    if (capGndCenter == null) {
      return 0;
    }
    int bestPinId = 0;
    long minGndDistSq = Long.MAX_VALUE;
    for (Pin gPin : icGroundPins) {
      long dSq = computeDistanceSquared(capGndCenter, gPin.getCenter());
      if (dSq < minGndDistSq) {
        minGndDistSq = dSq;
        bestPinId = gPin.getId();
      }
    }
    return bestPinId;
  }

  /**
   * Solves minimum weight bipartite matching for an $M \times N$ cost matrix ($M \le N$) using the
   * Hungarian algorithm (Jonker-Volgenant shortest augmenting paths).
   *
   * @param cost $M \times N$ cost matrix
   * @param nRows number of rows $M$
   * @param nCols number of columns $N$
   * @return array of length $M$ mapping each row $i$ to its assigned column $j$, or -1 if
   *     unassigned
   */
  public static int[] solveMinCostMatching(long[][] cost, int nRows, int nCols) {
    int[] match = new int[nRows];
    Arrays.fill(match, -1);
    if (nRows == 0 || nCols == 0 || nRows > nCols) {
      return match;
    }

    long[] u = new long[nRows + 1];
    long[] v = new long[nCols + 1];
    int[] p = new int[nCols + 1];
    int[] way = new int[nCols + 1];
    long[] minv = new long[nCols + 1];
    boolean[] used = new boolean[nCols + 1];

    for (int i = 1; i <= nRows; i++) {
      p[0] = i;
      int j0 = 0;
      Arrays.fill(minv, Long.MAX_VALUE);
      Arrays.fill(used, false);

      do {
        used[j0] = true;
        int i0 = p[j0];
        long delta = Long.MAX_VALUE;
        int j1 = 0;

        for (int j = 1; j <= nCols; j++) {
          if (!used[j]) {
            long cur = cost[i0 - 1][j - 1] - u[i0] - v[j];
            if (cur < minv[j]) {
              minv[j] = cur;
              way[j] = j0;
            }
            if (minv[j] < delta) {
              delta = minv[j];
              j1 = j;
            }
          }
        }

        for (int j = 0; j <= nCols; j++) {
          if (used[j]) {
            u[p[j]] += delta;
            v[j] -= delta;
          } else {
            minv[j] -= delta;
          }
        }
        j0 = j1;
      } while (p[j0] != 0);

      do {
        int j1 = way[j0];
        p[j0] = p[j1];
        j0 = j1;
      } while (j0 != 0);
    }

    for (int j = 1; j <= nCols; j++) {
      if (p[j] > 0 && p[j] <= nRows) {
        match[p[j] - 1] = j - 1;
      }
    }
    return match;
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

  private static int resolveSupplyPinId(BasicBoard board, CapacitorCandidate candidate) {
    Item itemA = board.getItem(candidate.pinIdA());
    Item itemB = board.getItem(candidate.pinIdB());
    if (!(itemA instanceof Pin pinA) || !(itemB instanceof Pin pinB)) {
      return 0;
    }
    return (candidate.netNumberA() == candidate.railNetNumber()) ? pinA.getId() : pinB.getId();
  }

  private static int resolveGroundPinId(
      BasicBoard board, CapacitorCandidate candidate, int supplyPinId) {
    Item itemA = board.getItem(candidate.pinIdA());
    Item itemB = board.getItem(candidate.pinIdB());
    if (!(itemA instanceof Pin pinA) || !(itemB instanceof Pin pinB)) {
      return 0;
    }
    return (supplyPinId == pinA.getId()) ? pinB.getId() : pinA.getId();
  }

  private static Pin getPin(BasicBoard board, int itemId) {
    if (board == null || itemId <= 0) {
      return null;
    }
    Item item = board.getItem(itemId);
    return (item instanceof Pin pin) ? pin : null;
  }

  private record ClusterKey(int componentId, int railNetNumber) {}
}
