package app.freerouting.autoroute.decoupling;

import app.freerouting.autoroute.AutorouteAttemptResult;
import app.freerouting.autoroute.AutorouteAttemptState;
import app.freerouting.autoroute.maze.AutorouteControl;
import app.freerouting.autoroute.maze.AutorouteEngine;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ComponentOutline;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.DrillItem;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.items.ViaObstacleArea;
import app.freerouting.board.model.structure.Component;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.core.StoppableThread;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import app.freerouting.settings.DecouplingLockMode;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pre-routing pass for decoupling capacitors.
 *
 * <p>Uses {@link DecouplingAssignmentPlanner} to match decoupling capacitor supply pads to their
 * paired IC supply pins via same-layer surface straps. Integrates {@link DecouplingRouteCheckpoint}
 * to guarantee O(1) split-trace rollback fidelity (Issue #1054) and {@link CourtyardEscapeProbe}
 * with trace width scaling and automatic downgrade (1.5x to 1.0x) to eliminate signal pin trapping
 * in dense IC courtyards prior to fanout and general autorouting.
 */
public final class BatchDecouplingRouter {

  private BatchDecouplingRouter() {}

  /** Result record for a single strap routing attempt. */
  record StrapAttemptOutcome(
      boolean success, String status, double lengthMm, List<Item> newItems) {}

  /**
   * Runs the decoupling capacitor pre-routing pass on the given board.
   *
   * @param board routing board to process
   * @param settings router settings containing decoupling configuration
   * @param thread stoppable thread context
   * @return summary of decoupling routing results, or {@code null} if disabled or skipped
   */
  public static DecouplingRoutingSummary run(
      RoutingBoard board, RouterSettings settings, StoppableThread thread) {
    if (!settings.isDecouplingEnabled()) {
      return null;
    }

    if (settings.getLayerCount() <= 0) {
      settings.setLayerCount(board.getLayerCount());
      settings.applyBoardSpecificOptimizations(board);
    }

    double maxOwnerRadiusMm =
        settings.decoupling != null && settings.decoupling.maxOwnerRadiusMm != null
            ? settings.decoupling.maxOwnerRadiusMm
            : DefaultSettings.DEFAULT_DECOUPLING_MAX_OWNER_RADIUS_MM;
    boolean includeReview =
        settings.decoupling != null
            && Boolean.TRUE.equals(settings.decoupling.includeReviewCandidates);
    DecouplingLockMode lockMode =
        settings.decoupling != null && settings.decoupling.lockMode != null
            ? settings.decoupling.lockMode
            : DecouplingLockMode.SOFT;
    double maxStrapLengthMm =
        settings.decoupling != null && settings.decoupling.maxStrapLengthMm != null
            ? settings.decoupling.maxStrapLengthMm
            : DefaultSettings.DEFAULT_DECOUPLING_MAX_STRAP_LENGTH_MM;
    double traceWidthScale =
        settings.decoupling != null && settings.decoupling.traceWidthScale != null
            ? settings.decoupling.traceWidthScale
            : 1.5;

    double mmResolution = board.communication.getResolution(Unit.MM);
    double maxStrapLengthUnits = maxStrapLengthMm * mmResolution;

    IdentifierConfig defaultConfig = IdentifierConfig.defaultConfig();
    List<String> combinedPowerNets = new ArrayList<>();
    if (settings != null && settings.powerNets != null) {
      combinedPowerNets.addAll(Arrays.asList(settings.powerNets));
    }
    if (settings != null && settings.decoupling != null && settings.decoupling.powerNets != null) {
      combinedPowerNets.addAll(Arrays.asList(settings.decoupling.powerNets));
    }

    Set<Integer> forcedGroundNets = new HashSet<>(defaultConfig.forcedGroundNets());
    List<String> combinedGroundNets = new ArrayList<>();
    if (settings != null && settings.groundNets != null) {
      combinedGroundNets.addAll(Arrays.asList(settings.groundNets));
    }
    if (settings != null && settings.decoupling != null && settings.decoupling.groundNets != null) {
      combinedGroundNets.addAll(Arrays.asList(settings.decoupling.groundNets));
    }
    if (board.rules != null && board.rules.nets != null) {
      for (String gNetName : combinedGroundNets) {
        Collection<Net> gNets = board.rules.nets.get(gNetName);
        if (gNets != null) {
          for (Net net : gNets) {
            forcedGroundNets.add(net.netNumber);
          }
        }
      }
    }

    IdentifierConfig config =
        new IdentifierConfig(
            maxOwnerRadiusMm,
            defaultConfig.minPitchMm(),
            defaultConfig.maxPitchMm(),
            defaultConfig.minPadAreaRatio(),
            defaultConfig.decouplingThreshold(),
            defaultConfig.reviewThreshold(),
            defaultConfig.ownerTopK(),
            defaultConfig.runRefDesAudit(),
            defaultConfig.auditDemotesToReview(),
            defaultConfig.groundNameHints(),
            forcedGroundNets,
            defaultConfig.forcedRejectComponents(),
            combinedPowerNets);

    IdentificationResult idResult = DecouplingIdentifier.identify(board, config);

    List<DecouplingMetrics> metricsList = new ArrayList<>();
    int routedStraps = 0;
    int alreadyConnected = 0;
    int viasPlaced = 0;
    int coupledViaPairs = 0;
    int fallbackViaPairs = 0;
    int failed = 0;
    double totalStrapLengthMm = 0.0;
    int totalCandidates = 0;

    List<DecouplingRouteTask> tasks =
        DecouplingAssignmentPlanner.plan(
            board, idResult.candidates(), maxOwnerRadiusMm, includeReview);

    try {
      for (DecouplingRouteTask task : tasks) {
        if ((thread != null && (thread.isStopRequested() || thread.isStopAutoRouterRequested()))
            || Thread.currentThread().isInterrupted()) {
          break;
        }

        CapacitorCandidate candidate = task.candidate();
        Component capComp = board.components.get(candidate.componentId());
        String capName = capComp != null ? capComp.name : ("Comp#" + candidate.componentId());

        totalCandidates++;

        Item itemSupply = board.getItem(task.supplyPinId());
        Item itemGround = board.getItem(task.groundPinId());
        if (!(itemSupply instanceof Pin supplyPin) || !(itemGround instanceof Pin groundPin)) {
          failed++;
          metricsList.add(
              new DecouplingMetrics(
                  candidate.componentId(), capName, 0, "", 0.0, 0, 0.0, false, "INVALID_PINS"));
          continue;
        }

        int railNetNo = candidate.railNetNumber();
        Net railNet = board.rules.nets.get(railNetNo);
        if (supplyPin.hasIgnoredNets() || groundPin.hasIgnoredNets()) {
          failed++;
          metricsList.add(
              new DecouplingMetrics(
                  candidate.componentId(),
                  capName,
                  task.targetSupplyItemId(),
                  "",
                  0.0,
                  0,
                  0.0,
                  false,
                  "IGNORED_NET"));
          continue;
        }

        int capLayer = supplyPin.firstLayer();
        int targetPinId = task.targetSupplyItemId();
        Pin ownerPin = null;
        if (task.hasTargetSupply()) {
          Item targetItem = board.getItem(task.targetSupplyItemId());
          if (targetItem instanceof Pin pin) {
            ownerPin = pin;
          } else if (task.isSecondary() && task.pairedPrimaryCapPinId() > 0) {
            Item primaryCapItem = board.getItem(task.pairedPrimaryCapPinId());
            if (primaryCapItem instanceof Pin primaryPin) {
              ownerPin = primaryPin;
              targetPinId = primaryPin.getId();
            }
          }
        }

        String ownerName = "NONE";
        int ownerCompId = -1;
        if (ownerPin != null) {
          ownerCompId = ownerPin.getComponentId();
          Component ownerComp = board.components.get(ownerCompId);
          ownerName =
              (ownerComp != null ? ownerComp.name : ("Comp#" + ownerCompId))
                  + "-"
                  + ownerPin.name();
        }

        FloatPoint p1 = groundPin.getCenter().toFloat();
        FloatPoint p2 = supplyPin.getCenter().toFloat();
        double dx = p2.x - p1.x;
        double dy = p2.y - p1.y;
        double padDist = Math.sqrt(dx * dx + dy * dy);
        double escapeDistance = 0.5 * mmResolution;

        FloatPoint primaryGroundTarget = null;
        FloatPoint secondaryGroundTarget = null;
        if (padDist > 10.0) {
          double ux = dx / padDist;
          double uy = dy / padDist;
          double nx = -uy;
          double ny = ux;
          primaryGroundTarget =
              new FloatPoint(p1.x + escapeDistance * nx, p1.y + escapeDistance * ny);
          secondaryGroundTarget =
              new FloatPoint(p1.x - escapeDistance * nx, p1.y - escapeDistance * ny);
          if (ownerPin != null) {
            FloatPoint ownerCenter = ownerPin.getCenter().toFloat();
            double d1 = primaryGroundTarget.distance(ownerCenter);
            double d2 = secondaryGroundTarget.distance(ownerCenter);
            if (d2 > d1) {
              FloatPoint tmp = primaryGroundTarget;
              primaryGroundTarget = secondaryGroundTarget;
              secondaryGroundTarget = tmp;
            }
          }
        }

        int traceClearance =
            (capLayer >= 0 && capLayer < board.getLayerCount())
                ? board.rules.clearanceMatrix.maxValue(capLayer)
                : 0;
        IntBox forbiddenCourtyardBox =
            computeForbiddenCourtyardBox(board, ownerCompId, traceClearance);

        PairRouteResult pairResult =
            routeDecouplingPair(
                board,
                supplyPin,
                groundPin,
                ownerPin,
                capLayer,
                railNetNo,
                candidate.groundNetNumber(),
                ownerCompId,
                candidate,
                task,
                traceWidthScale,
                maxStrapLengthMm,
                mmResolution,
                forbiddenCourtyardBox,
                settings,
                thread,
                lockMode,
                primaryGroundTarget,
                secondaryGroundTarget);

        boolean strapSuccess = pairResult.success();
        String strapStatus = pairResult.status();
        double candidateStrapLengthMm = pairResult.lengthMm();
        int viasPlacedForCap = pairResult.viasPlaced();
        double groundViaDistanceMm = pairResult.groundViaDistanceMm();

        if (strapSuccess) {
          if ("ALREADY_CONNECTED".equals(strapStatus)) {
            alreadyConnected++;
          } else {
            routedStraps++;
            totalStrapLengthMm += candidateStrapLengthMm;
            viasPlaced += viasPlacedForCap;
          }
          coupledViaPairs += pairResult.coupledViaPairs();
          fallbackViaPairs += pairResult.fallbackViaPairs();
        } else {
          failed++;
        }

        metricsList.add(
            new DecouplingMetrics(
                candidate.componentId(),
                capName,
                targetPinId,
                ownerName,
                candidateStrapLengthMm,
                viasPlacedForCap,
                groundViaDistanceMm,
                strapSuccess,
                strapStatus));

        if (!strapSuccess) {
          if ("DISTANCE_EXCEEDED".equals(strapStatus) || "NO_OWNER_IN_RADIUS".equals(strapStatus)) {
            FRLogger.warn(
                String.format(
                    "Decoupling capacitor %s is outside maximum reach (%.2f mm) from owner %s"
                        + " (%s).",
                    capName, maxStrapLengthMm, ownerName, strapStatus));
          } else if ("NO_ROUTE_FOUND".equals(strapStatus)
              || "DRC_ROLLBACK".equals(strapStatus)
              || "GROUND_ROUTING_FAILED".equals(strapStatus)
              || "PLANE_VIA_FAILED".equals(strapStatus)) {
            FRLogger.warn(
                String.format(
                    "Decoupling route for %s (net %s) was blocked by clearance or obstacles (%s).",
                    capName, railNet != null ? railNet.name : "unknown", strapStatus));
          } else if ("NO_GROUND_PLANE".equals(strapStatus)) {
            FRLogger.warn(
                String.format(
                    "Decoupling capacitor %s has no ground plane directly underneath and no IC"
                        + " ground pin reachable; left for general autorouter escape.",
                    capName));
          }
        }

        board.clearTransientAutorouteState();
      }
    } finally {
      board.clearTransientAutorouteState();
    }

    DecouplingRoutingSummary summary =
        new DecouplingRoutingSummary(
            totalCandidates,
            routedStraps,
            alreadyConnected,
            viasPlaced,
            failed,
            totalStrapLengthMm,
            metricsList,
            coupledViaPairs,
            fallbackViaPairs);

    FRLogger.info(
        String.format(
            "Decoupling routing complete: %d eligible candidates, %d straps routed, %d already"
                + " connected, %d decoupling vias placed (%d coupled pairs, %d fallback pairs),"
                + " %d failed/skipped. Total strap length: %.2f mm.",
            totalCandidates,
            routedStraps,
            alreadyConnected,
            viasPlaced,
            coupledViaPairs,
            fallbackViaPairs,
            failed,
            totalStrapLengthMm));

    return summary;
  }

  static record LegRouteOutcome(
      boolean success, String status, List<Item> newItems, double lengthMm, Via placedVia) {}

  static record PairRouteResult(
      boolean success,
      String status,
      List<Item> newItems,
      double lengthMm,
      int viasPlaced,
      double groundViaDistanceMm,
      int coupledViaPairs,
      int fallbackViaPairs) {}

  /** Determines if the capacitor power pad is more boxed-in towards the IC than the ground pad. */
  static boolean isPadBoxedIn(Pin supplyPin, Pin groundPin, Pin ownerPin) {
    if (ownerPin == null) {
      return true;
    }
    double pwrDist = supplyPin.getCenter().toFloat().distance(ownerPin.getCenter().toFloat());
    double gndDist = groundPin.getCenter().toFloat().distance(ownerPin.getCenter().toFloat());
    return pwrDist <= gndDist;
  }

  /**
   * Routes a decoupling capacitor pair (power leg and ground leg) as an atomic transaction. If
   * either leg fails, or if combined DRC fails, rolls back all changes.
   */
  static PairRouteResult routeDecouplingPair(
      RoutingBoard board,
      Pin supplyPin,
      Pin groundPin,
      Pin ownerPin,
      int capLayer,
      int railNetNo,
      int groundNetNo,
      int ownerCompId,
      CapacitorCandidate candidate,
      DecouplingRouteTask task,
      double traceWidthScale,
      double maxStrapLengthMm,
      double mmResolution,
      IntBox forbiddenCourtyardBox,
      RouterSettings settings,
      StoppableThread thread,
      DecouplingLockMode lockMode,
      FloatPoint primaryGroundTarget,
      FloatPoint secondaryGroundTarget) {
    boolean supplyConnectedToOwner =
        (ownerPin != null && supplyPin.getConnectedSet(railNetNo).contains(ownerPin))
            || (task.isSecondary()
                && task.pairedPrimaryCapPinId() > 0
                && board.getItem(task.pairedPrimaryCapPinId()) instanceof Pin primaryPin
                && supplyPin.getConnectedSet(railNetNo).contains(primaryPin));

    boolean groundAlreadyConnected = false;
    for (Item it : groundPin.getNormalContacts()) {
      if (it instanceof ConductionArea || it instanceof Via) {
        groundAlreadyConnected = true;
        break;
      }
    }
    if (!groundAlreadyConnected) {
      Net gNet = board.rules.nets.get(groundNetNo);
      if (gNet != null && gNet.containsPlane()) {
        for (Item it : groundPin.getConnectedSet(groundNetNo)) {
          if (it instanceof ConductionArea || it instanceof Via) {
            groundAlreadyConnected = true;
            break;
          }
        }
      }
    }
    if (!groundAlreadyConnected && ownerCompId > 0) {
      for (Item it : groundPin.getConnectedSet(groundNetNo)) {
        if (it instanceof Pin p && p.getComponentId() == ownerCompId) {
          groundAlreadyConnected = true;
          break;
        }
      }
    }
    if (!groundAlreadyConnected) {
      for (Item it : groundPin.getConnectedSet(groundNetNo)) {
        if (it.isDecouplingProtected() && !it.equals(groundPin)) {
          groundAlreadyConnected = true;
          break;
        }
      }
    }

    if (supplyConnectedToOwner && (groundAlreadyConnected || task.isPlaneOnly())) {
      tagDecouplingProtected(supplyPin, lockMode);
      tagDecouplingProtected(groundPin, lockMode);
      if (ownerPin != null) {
        tagDecouplingProtected(ownerPin, lockMode);
      }
      return new PairRouteResult(true, "ALREADY_CONNECTED", List.of(), 0.0, 0, 0.0, 0, 0);
    }

    IntBox transactionBox = supplyPin.boundingBox().union(groundPin.boundingBox());
    if (ownerPin != null) {
      transactionBox = transactionBox.union(ownerPin.boundingBox());
    }
    int searchRadius = (int) Math.ceil(maxStrapLengthMm * mmResolution) + 10000;
    final DecouplingRouteCheckpoint atomicCheckpoint =
        DecouplingRouteCheckpoint.capture(board, transactionBox.offset(searchRadius), -1, -1);

    boolean pwrBoxedIn = isPadBoxedIn(supplyPin, groundPin, ownerPin);
    LegRouteOutcome firstLeg;
    LegRouteOutcome secondLeg;

    if (pwrBoxedIn) {
      firstLeg =
          routePowerLeg(
              board,
              supplyPin,
              ownerPin,
              capLayer,
              railNetNo,
              groundNetNo,
              ownerCompId,
              candidate.componentId(),
              traceWidthScale,
              maxStrapLengthMm,
              mmResolution,
              forbiddenCourtyardBox,
              settings,
              thread,
              task);
      if (!firstLeg.success()) {
        atomicCheckpoint.rollback(board);
        return new PairRouteResult(false, firstLeg.status(), List.of(), 0.0, 0, 0.0, 0, 0);
      }

      secondLeg =
          routeGroundLeg(
              board,
              groundPin,
              ownerPin,
              capLayer,
              groundNetNo,
              railNetNo,
              candidate,
              task,
              maxStrapLengthMm,
              mmResolution,
              forbiddenCourtyardBox,
              settings,
              thread,
              primaryGroundTarget,
              secondaryGroundTarget);
      if (!secondLeg.success()) {
        atomicCheckpoint.rollback(board);
        return new PairRouteResult(false, secondLeg.status(), List.of(), 0.0, 0, 0.0, 0, 0);
      }
    } else {
      firstLeg =
          routeGroundLeg(
              board,
              groundPin,
              ownerPin,
              capLayer,
              groundNetNo,
              railNetNo,
              candidate,
              task,
              maxStrapLengthMm,
              mmResolution,
              forbiddenCourtyardBox,
              settings,
              thread,
              primaryGroundTarget,
              secondaryGroundTarget);
      if (!firstLeg.success()) {
        atomicCheckpoint.rollback(board);
        return new PairRouteResult(false, firstLeg.status(), List.of(), 0.0, 0, 0.0, 0, 0);
      }

      secondLeg =
          routePowerLeg(
              board,
              supplyPin,
              ownerPin,
              capLayer,
              railNetNo,
              groundNetNo,
              ownerCompId,
              candidate.componentId(),
              traceWidthScale,
              maxStrapLengthMm,
              mmResolution,
              forbiddenCourtyardBox,
              settings,
              thread,
              task);
      if (!secondLeg.success()) {
        atomicCheckpoint.rollback(board);
        return new PairRouteResult(false, secondLeg.status(), List.of(), 0.0, 0, 0.0, 0, 0);
      }
    }

    List<Item> combinedItems = new ArrayList<>();
    combinedItems.addAll(firstLeg.newItems());
    combinedItems.addAll(secondLeg.newItems());

    Via placedGndVia = pwrBoxedIn ? secondLeg.placedVia() : firstLeg.placedVia();
    Via placedSupplyVia = pwrBoxedIn ? firstLeg.placedVia() : secondLeg.placedVia();

    FloatPoint p1 = groundPin.getCenter().toFloat();
    FloatPoint p2 = supplyPin.getCenter().toFloat();
    double dx = p2.x - p1.x;
    double dy = p2.y - p1.y;
    double padDist = Math.sqrt(dx * dx + dy * dy);
    double escapeDistance = 0.5 * mmResolution;

    Net railNet = board.rules.nets.get(railNetNo);
    if (railNet != null && railNet.containsPlane() && placedSupplyVia == null) {
      boolean supplyIsSmd = (supplyPin.firstLayer() == supplyPin.lastLayer());
      boolean supplyAlreadyOnPlane = !supplyIsSmd;
      if (!supplyAlreadyOnPlane) {
        for (Item it : supplyPin.getNormalContacts()) {
          if (it instanceof ConductionArea || it instanceof Via) {
            supplyAlreadyOnPlane = true;
            break;
          }
        }
      }
      if (!supplyAlreadyOnPlane) {
        for (Item it : supplyPin.getConnectedSet(railNetNo)) {
          if (it instanceof ConductionArea || (it instanceof Via v && !v.isDecouplingProtected())) {
            supplyAlreadyOnPlane = true;
            break;
          }
        }
      }

      if (!supplyAlreadyOnPlane) {
        FloatPoint primarySupplyTarget = null;
        FloatPoint secondarySupplyTarget = null;
        if (padDist > 10.0) {
          double ux = dx / padDist;
          double uy = dy / padDist;
          double nx = -uy;
          double ny = ux;
          if (placedGndVia != null) {
            FloatPoint v1 = placedGndVia.getCenter().toFloat();
            double offsetX = v1.x - p1.x;
            double offsetY = v1.y - p1.y;
            double compN = offsetX * nx + offsetY * ny;
            if (Math.abs(compN) >= 0.2 * escapeDistance) {
              double sign = Math.signum(compN);
              if (sign == 0.0) {
                sign = 1.0;
              }
              primarySupplyTarget = new FloatPoint(p2.x + compN * nx, p2.y + compN * ny);
              secondarySupplyTarget = new FloatPoint(p2.x - compN * nx, p2.y - compN * ny);
            } else {
              primarySupplyTarget =
                  new FloatPoint(p2.x + escapeDistance * nx, p2.y + escapeDistance * ny);
              secondarySupplyTarget =
                  new FloatPoint(p2.x - escapeDistance * nx, p2.y - escapeDistance * ny);
            }
          } else {
            primarySupplyTarget =
                new FloatPoint(p2.x + escapeDistance * nx, p2.y + escapeDistance * ny);
            secondarySupplyTarget =
                new FloatPoint(p2.x - escapeDistance * nx, p2.y - escapeDistance * ny);
          }
        }

        ViaDropResult supplyViaResult =
            tryViaFanoutWithDualFlankProbe(
                board,
                supplyPin,
                railNetNo,
                settings,
                thread,
                primarySupplyTarget,
                secondarySupplyTarget,
                forbiddenCourtyardBox,
                ownerCompId);
        if (supplyViaResult != null) {
          placedSupplyVia = supplyViaResult.placedVia();
          combinedItems.addAll(supplyViaResult.newItems());
        }
      }
    }

    int coupledPairs = 0;
    int fallbackPairs = 0;
    if (placedGndVia != null && placedSupplyVia != null && padDist > 10.0) {
      double interViaDistMm =
          placedGndVia.getCenter().toFloat().distance(placedSupplyVia.getCenter().toFloat())
              / mmResolution;
      double padDistMm = padDist / mmResolution;
      if (interViaDistMm <= 1.5 * padDistMm) {
        coupledPairs++;
      } else {
        fallbackPairs++;
      }
    }

    for (Item it : combinedItems) {
      if (it.isOnTheBoard() && !it.clearanceViolations().isEmpty()) {
        atomicCheckpoint.rollback(board);
        return new PairRouteResult(false, "PAIR_DRC_ROLLBACK", List.of(), 0.0, 0, 0.0, 0, 0);
      }
    }

    int accuracy = settings.tracePullTightAccuracy != null ? settings.tracePullTightAccuracy : 500;
    board.optChangedArea(new int[] {railNetNo, groundNetNo}, null, accuracy, null, thread, 0);

    int viasPlacedForCap = 0;
    for (Item it : combinedItems) {
      if (it.isOnTheBoard()) {
        if (it instanceof Via) {
          viasPlacedForCap++;
        }
        tagDecouplingProtected(it, lockMode);
        if (ReasonCode.FILTERED_ISLAND.isSet(candidate.reasonMask())) {
          it.setFilteredIslandNonFeeder(true);
        }
      }
    }
    tagDecouplingProtected(supplyPin, lockMode);
    tagDecouplingProtected(groundPin, lockMode);
    if (ownerPin != null) {
      tagDecouplingProtected(ownerPin, lockMode);
      if (ReasonCode.FILTERED_ISLAND.isSet(candidate.reasonMask())) {
        ownerPin.setFilteredIslandNonFeeder(true);
      }
    }

    double totalLengthMm = firstLeg.lengthMm() + secondLeg.lengthMm();
    double gndViaDistMm =
        (placedGndVia != null)
            ? groundPin.getCenter().toFloat().distance(placedGndVia.getCenter().toFloat())
                / mmResolution
            : 0.0;

    String finalStatus = pwrBoxedIn ? firstLeg.status() : secondLeg.status();
    if ("ALREADY_CONNECTED".equals(finalStatus)) {
      String otherStatus = pwrBoxedIn ? secondLeg.status() : firstLeg.status();
      if (!"ALREADY_CONNECTED".equals(otherStatus)) {
        finalStatus = otherStatus;
      }
    }

    return new PairRouteResult(
        true,
        finalStatus,
        combinedItems,
        totalLengthMm,
        viasPlacedForCap,
        gndViaDistMm,
        coupledPairs,
        fallbackPairs);
  }

  static LegRouteOutcome routePowerLeg(
      RoutingBoard board,
      Pin supplyPin,
      Pin ownerPin,
      int capLayer,
      int railNetNo,
      int groundNetNo,
      int ownerCompId,
      int capCompId,
      double traceWidthScale,
      double maxStrapLengthMm,
      double mmResolution,
      IntBox forbiddenCourtyardBox,
      RouterSettings settings,
      StoppableThread thread,
      DecouplingRouteTask task) {
    if (ownerPin == null) {
      if (task.isPlaneOnly()) {
        boolean supplyIsSmd = (supplyPin.firstLayer() == supplyPin.lastLayer());
        if (!supplyIsSmd) {
          return new LegRouteOutcome(true, "ALREADY_CONNECTED", List.of(), 0.0, null);
        }
        for (Item it : supplyPin.getNormalContacts()) {
          if (it instanceof ConductionArea || it instanceof Via) {
            return new LegRouteOutcome(true, "ALREADY_CONNECTED", List.of(), 0.0, null);
          }
        }
        Net railNet = board.rules.nets.get(railNetNo);
        if (railNet != null
            && (isPlaneDirectlyUnderneath(board, supplyPin, railNetNo)
                || railNet.containsPlane())) {
          FloatPoint p1 = supplyPin.getCenter().toFloat();
          double escapeDistance = 0.5 * mmResolution;
          FloatPoint biasTarget = new FloatPoint(p1.x + escapeDistance, p1.y + escapeDistance);
          ViaDropResult result =
              trySingleViaFanout(
                  board,
                  supplyPin,
                  railNetNo,
                  settings,
                  thread,
                  biasTarget,
                  forbiddenCourtyardBox,
                  ownerCompId);
          if (result != null) {
            return new LegRouteOutcome(
                true, "PLANE_ONLY_ROUTED", result.newItems(), 0.0, result.placedVia());
          }
          return new LegRouteOutcome(false, "PLANE_VIA_FAILED", List.of(), 0.0, null);
        }
        return new LegRouteOutcome(false, "NO_GROUND_PLANE", List.of(), 0.0, null);
      }
      return new LegRouteOutcome(false, "NO_OWNER_IN_RADIUS", List.of(), 0.0, null);
    }

    double maxStrapLengthUnits = maxStrapLengthMm * mmResolution;
    double lateralDistUnits =
        supplyPin.getCenter().toFloat().distance(ownerPin.getCenter().toFloat());
    if (lateralDistUnits > maxStrapLengthUnits) {
      return new LegRouteOutcome(false, "DISTANCE_EXCEEDED", List.of(), 0.0, null);
    }

    Set<Item> supplyConnected = supplyPin.getConnectedSet(railNetNo);
    if (supplyConnected.contains(ownerPin)) {
      return new LegRouteOutcome(true, "ALREADY_CONNECTED", List.of(), 0.0, null);
    }

    int ownerLayer = ownerPin.firstLayer();
    boolean isSameLayer = (capLayer == ownerLayer);
    boolean isOwnerTht =
        (ownerPin.firstLayer() != ownerPin.lastLayer()) && ownerPin.isOnLayer(capLayer);

    double[] scalesToTry =
        (traceWidthScale > 1.0) ? new double[] {traceWidthScale, 1.0} : new double[] {1.0};

    if (isSameLayer || isOwnerTht) {
      int targetLayer = capLayer;
      AutorouteControl control = new AutorouteControl(board, railNetNo, settings);
      if (targetLayer < 0
          || targetLayer >= control.layerCount
          || !control.layerActive[targetLayer]) {
        return new LegRouteOutcome(false, "LAYER_DISABLED", List.of(), 0.0, null);
      }

      control.viasAllowed = false;
      control.ripupAllowed = false;
      control.removeUnconnectedVias = false;
      if (settings.decoupling != null && settings.decoupling.maxDoorExpansions != null) {
        control.maxDoorExpansions = settings.decoupling.maxDoorExpansions;
      }
      for (int i = 0; i < control.layerCount; i++) {
        control.layerActive[i] = control.layerActive[i] && (i == targetLayer);
      }

      StrapAttemptOutcome outcome = null;
      for (double scale : scalesToTry) {
        Set<Item> currentSupplyConnected = supplyPin.getConnectedSet(railNetNo);
        Set<Item> currentOwnerConnected = ownerPin.getConnectedSet(railNetNo);
        outcome =
            tryRouteStrap(
                board,
                settings,
                thread,
                control,
                supplyPin,
                ownerPin,
                currentSupplyConnected,
                currentOwnerConnected,
                capLayer,
                targetLayer,
                railNetNo,
                groundNetNo,
                ownerCompId,
                capCompId,
                scale,
                maxStrapLengthMm,
                mmResolution);
        if (outcome.success()) {
          break;
        }
      }

      if (outcome != null && outcome.success()) {
        Via placedVia = null;
        for (Item it : outcome.newItems()) {
          if (it instanceof Via v) {
            placedVia = v;
          }
        }
        return new LegRouteOutcome(
            true, outcome.status(), outcome.newItems(), outcome.lengthMm(), placedVia);
      }

      // Secondary overflow cap: fallback flanking attempt to primary cap pad
      if (task.isSecondary() && task.pairedPrimaryCapPinId() > 0) {
        Item primaryCapItem = board.getItem(task.pairedPrimaryCapPinId());
        if (primaryCapItem instanceof Pin primaryCapPin
            && primaryCapPin.firstLayer() == supplyPin.firstLayer()
            && !primaryCapPin.equals(ownerPin)
            && !primaryCapPin.hasIgnoredNets()) {
          Set<Item> primaryCapConnected = primaryCapPin.getConnectedSet(railNetNo);
          if (supplyConnected.contains(primaryCapPin) && primaryCapConnected.contains(ownerPin)) {
            return new LegRouteOutcome(true, "ALREADY_CONNECTED", List.of(), 0.0, null);
          }
          if (primaryCapConnected.contains(ownerPin)) {
            double flankDistUnits =
                supplyPin.getCenter().toFloat().distance(primaryCapPin.getCenter().toFloat());
            if (flankDistUnits <= maxStrapLengthUnits) {
              StrapAttemptOutcome flankOutcome = null;
              for (double scale : scalesToTry) {
                Set<Item> currentSupplyConnected = supplyPin.getConnectedSet(railNetNo);
                Set<Item> currentPrimaryConnected = primaryCapPin.getConnectedSet(railNetNo);
                flankOutcome =
                    tryRouteStrap(
                        board,
                        settings,
                        thread,
                        control,
                        supplyPin,
                        primaryCapPin,
                        currentSupplyConnected,
                        currentPrimaryConnected,
                        capLayer,
                        capLayer,
                        railNetNo,
                        groundNetNo,
                        primaryCapPin.getComponentId(),
                        capCompId,
                        scale,
                        maxStrapLengthMm,
                        mmResolution);
                if (flankOutcome.success()) {
                  break;
                }
              }
              if (flankOutcome != null && flankOutcome.success()) {
                Via placedVia = null;
                for (Item it : flankOutcome.newItems()) {
                  if (it instanceof Via v) {
                    placedVia = v;
                  }
                }
                return new LegRouteOutcome(
                    true,
                    "ROUTED_FLANKING",
                    flankOutcome.newItems(),
                    flankOutcome.lengthMm(),
                    placedVia);
              }
            }
          }
        }
      }

      return new LegRouteOutcome(
          false, outcome != null ? outcome.status() : "NO_ROUTE_FOUND", List.of(), 0.0, null);
    } else {
      boolean oppositeSideEnabled =
          settings.decoupling == null
              || settings.decoupling.oppositeSideRouting == null
              || Boolean.TRUE.equals(settings.decoupling.oppositeSideRouting);
      if (!oppositeSideEnabled) {
        return new LegRouteOutcome(false, "OPPOSITE_SIDE_DISABLED", List.of(), 0.0, null);
      }

      AutorouteControl control = new AutorouteControl(board, railNetNo, settings);
      if (capLayer < 0
          || capLayer >= control.layerCount
          || ownerLayer < 0
          || ownerLayer >= control.layerCount
          || !control.layerActive[capLayer]
          || !control.layerActive[ownerLayer]) {
        return new LegRouteOutcome(false, "LAYER_DISABLED", List.of(), 0.0, null);
      }

      control.viasAllowed = true;
      control.ripupAllowed = false;
      control.removeUnconnectedVias = false;
      if (settings.decoupling != null && settings.decoupling.maxDoorExpansions != null) {
        control.maxDoorExpansions = settings.decoupling.maxDoorExpansions;
      }
      for (int i = 0; i < control.layerCount; i++) {
        control.layerActive[i] = (i == capLayer || i == ownerLayer);
      }

      StrapAttemptOutcome outcome = null;
      for (double scale : scalesToTry) {
        Set<Item> currentSupplyConnected = supplyPin.getConnectedSet(railNetNo);
        Set<Item> currentOwnerConnected = ownerPin.getConnectedSet(railNetNo);
        outcome =
            tryRouteStrap(
                board,
                settings,
                thread,
                control,
                supplyPin,
                ownerPin,
                currentSupplyConnected,
                currentOwnerConnected,
                capLayer,
                ownerLayer,
                railNetNo,
                groundNetNo,
                ownerCompId,
                capCompId,
                scale,
                maxStrapLengthMm,
                mmResolution);
        if (outcome.success()) {
          break;
        }
      }

      if (outcome != null && outcome.success()) {
        Via placedVia = null;
        for (Item it : outcome.newItems()) {
          if (it instanceof Via v) {
            placedVia = v;
          }
        }
        return new LegRouteOutcome(
            true,
            "ROUTED_OPPOSITE_FEEDTHROUGH_VIA",
            outcome.newItems(),
            outcome.lengthMm(),
            placedVia);
      }

      return new LegRouteOutcome(
          false, outcome != null ? outcome.status() : "NO_ROUTE_FOUND", List.of(), 0.0, null);
    }
  }

  static LegRouteOutcome routeGroundLeg(
      RoutingBoard board,
      Pin groundPin,
      Pin ownerPwrPin,
      int capLayer,
      int groundNetNo,
      int railNetNo,
      CapacitorCandidate candidate,
      DecouplingRouteTask task,
      double maxStrapLengthMm,
      double mmResolution,
      IntBox forbiddenCourtyardBox,
      RouterSettings settings,
      StoppableThread thread,
      FloatPoint primaryGroundTarget,
      FloatPoint secondaryGroundTarget) {
    Net groundNet = board.rules.nets.get(groundNetNo);
    boolean groundIsSmd = (groundPin.firstLayer() == groundPin.lastLayer());
    if (!groundIsSmd) {
      return new LegRouteOutcome(true, "ALREADY_CONNECTED", List.of(), 0.0, null);
    }
    for (Item it : groundPin.getNormalContacts()) {
      if (it instanceof ConductionArea || it instanceof Via) {
        return new LegRouteOutcome(true, "ALREADY_CONNECTED", List.of(), 0.0, null);
      }
    }
    if (groundNet != null && groundNet.containsPlane()) {
      for (Item it : groundPin.getConnectedSet(groundNetNo)) {
        if (it instanceof ConductionArea) {
          return new LegRouteOutcome(true, "ALREADY_CONNECTED", List.of(), 0.0, null);
        }
      }
    }

    Item targetGndItem =
        (task.targetGroundItemId() > 0) ? board.getItem(task.targetGroundItemId()) : null;
    Pin targetGndPin = (targetGndItem instanceof Pin p) ? p : null;
    if (targetGndPin != null && groundPin.getConnectedSet(groundNetNo).contains(targetGndPin)) {
      return new LegRouteOutcome(true, "ALREADY_CONNECTED", List.of(), 0.0, null);
    }

    int ownerCompId = (ownerPwrPin != null) ? ownerPwrPin.getComponentId() : -1;
    if (ownerCompId > 0) {
      for (Item it : groundPin.getConnectedSet(groundNetNo)) {
        if (it instanceof Pin p && p.getComponentId() == ownerCompId) {
          return new LegRouteOutcome(true, "ALREADY_CONNECTED", List.of(), 0.0, null);
        }
      }
    }
    for (Item it : groundPin.getConnectedSet(groundNetNo)) {
      if (it.isDecouplingProtected() && !it.equals(groundPin)) {
        return new LegRouteOutcome(true, "ALREADY_CONNECTED", List.of(), 0.0, null);
      }
    }

    // --- Tier 1: Any ground pin on the SAME IC within 3.0 mm of paired power pin ---
    if (ownerCompId > 0) {
      Collection<Pin> ownerPins = board.getComponentPins(ownerCompId);
      List<Pin> candidateIcGndPins = new ArrayList<>();
      if (ownerPins != null) {
        for (Pin p : ownerPins) {
          if (p.containsNet(groundNetNo) && !p.hasIgnoredNets()) {
            double distToPwrPinMm =
                (ownerPwrPin != null)
                    ? p.getCenter().toFloat().distance(ownerPwrPin.getCenter().toFloat())
                        / mmResolution
                    : 0.0;
            if (distToPwrPinMm <= 3.0) {
              candidateIcGndPins.add(p);
            }
          }
        }
      }

      final Pin preferredGnd = targetGndPin;
      candidateIcGndPins.sort(
          Comparator.comparingDouble(
              p -> {
                if (p.equals(preferredGnd)) {
                  return -1.0;
                }
                return p.getCenter().toFloat().distance(groundPin.getCenter().toFloat());
              }));

      for (Pin candidatePin : candidateIcGndPins) {
        StrapAttemptOutcome gndOutcome =
            tryRouteGroundStrap(
                board,
                settings,
                thread,
                groundPin,
                candidatePin,
                capLayer,
                groundNetNo,
                railNetNo,
                candidate,
                maxStrapLengthMm,
                mmResolution);
        if (gndOutcome != null && gndOutcome.success()) {
          Via placedVia = null;
          for (Item it : gndOutcome.newItems()) {
            if (it instanceof Via v) {
              placedVia = v;
            }
          }
          return new LegRouteOutcome(
              true,
              "ROUTED_IC_GROUND_PIN",
              gndOutcome.newItems(),
              gndOutcome.lengthMm(),
              placedVia);
        }
      }
    }

    // --- Tier 2: Existing ground plane or existing ground via within 3.5 mm ---
    boolean planeUnderneath = isPlaneDirectlyUnderneath(board, groundPin, groundNetNo);
    boolean groundNetHasPlane = groundNet != null && groundNet.containsPlane();
    if (planeUnderneath || groundNetHasPlane) {
      ViaDropResult groundViaResult =
          tryViaFanoutWithDualFlankProbe(
              board,
              groundPin,
              groundNetNo,
              settings,
              thread,
              primaryGroundTarget,
              secondaryGroundTarget,
              forbiddenCourtyardBox,
              ownerCompId);
      if (groundViaResult != null) {
        return new LegRouteOutcome(
            true,
            "ROUTED_GROUND_PLANE_VIA",
            groundViaResult.newItems(),
            0.0,
            groundViaResult.placedVia());
      }
    }

    double maxExistingViaDistUnits = 3.5 * mmResolution;
    Via closestExistingVia = null;
    double minViaDist = Double.MAX_VALUE;
    for (Item it : board.getItems()) {
      if (it instanceof Via v && v.containsNet(groundNetNo) && !v.isDecouplingProtected()) {
        double dist = v.getCenter().toFloat().distance(groundPin.getCenter().toFloat());
        if (dist <= maxExistingViaDistUnits && dist < minViaDist) {
          minViaDist = dist;
          closestExistingVia = v;
        }
      }
    }
    if (closestExistingVia != null) {
      AutorouteControl control = new AutorouteControl(board, groundNetNo, settings);
      control.viasAllowed = false;
      control.ripupAllowed = false;
      for (int i = 0; i < control.layerCount; i++) {
        control.layerActive[i] = (i == capLayer);
      }
      StrapAttemptOutcome outcome =
          tryRouteStrap(
              board,
              settings,
              thread,
              control,
              groundPin,
              closestExistingVia,
              groundPin.getConnectedSet(groundNetNo),
              Set.of(closestExistingVia),
              capLayer,
              capLayer,
              groundNetNo,
              railNetNo,
              ownerCompId,
              candidate.componentId(),
              1.0,
              3.5,
              mmResolution);
      if (outcome.success()) {
        return new LegRouteOutcome(
            true, "ROUTED_EXISTING_GROUND_VIA", outcome.newItems(), outcome.lengthMm(), null);
      }
    }

    // --- Tier 3: Search within 6.0 mm for ground pin/via; fallback to standalone stub via ---
    double maxTier3DistUnits = 6.0 * mmResolution;
    List<Item> nearbyGndTargets = new ArrayList<>();
    for (Item it : board.getItems()) {
      if ((it instanceof Pin p
              && p.containsNet(groundNetNo)
              && !p.equals(groundPin)
              && !p.hasIgnoredNets())
          || (it instanceof Via v && v.containsNet(groundNetNo) && !v.isDecouplingProtected())) {
        double dist =
            ((DrillItem) it).getCenter().toFloat().distance(groundPin.getCenter().toFloat());
        if (dist <= maxTier3DistUnits) {
          nearbyGndTargets.add(it);
        }
      }
    }
    nearbyGndTargets.sort(
        Comparator.comparingDouble(
            it ->
                ((DrillItem) it).getCenter().toFloat().distance(groundPin.getCenter().toFloat())));

    int candidatesTried = 0;
    for (Item gndTarget : nearbyGndTargets) {
      if (candidatesTried++ >= 3) {
        break;
      }
      int targetLayer = gndTarget.firstLayer();
      boolean sameLayer = (targetLayer == capLayer);
      AutorouteControl control = new AutorouteControl(board, groundNetNo, settings);
      control.viasAllowed = !sameLayer;
      control.ripupAllowed = false;
      for (int i = 0; i < control.layerCount; i++) {
        control.layerActive[i] = (i == capLayer || i == targetLayer);
      }
      StrapAttemptOutcome outcome =
          tryRouteStrap(
              board,
              settings,
              thread,
              control,
              groundPin,
              gndTarget,
              groundPin.getConnectedSet(groundNetNo),
              Set.of(gndTarget),
              capLayer,
              targetLayer,
              groundNetNo,
              railNetNo,
              ownerCompId,
              candidate.componentId(),
              1.0,
              6.0,
              mmResolution);
      if (outcome.success()) {
        Via placedVia = null;
        for (Item it : outcome.newItems()) {
          if (it instanceof Via v) {
            placedVia = v;
          }
        }
        return new LegRouteOutcome(
            true, "ROUTED_NEARBY_GROUND_TARGET", outcome.newItems(), outcome.lengthMm(), placedVia);
      }
    }

    ViaDropResult stubResult =
        tryViaFanoutWithDualFlankProbe(
            board,
            groundPin,
            groundNetNo,
            settings,
            thread,
            primaryGroundTarget,
            secondaryGroundTarget,
            forbiddenCourtyardBox,
            ownerCompId);
    if (stubResult != null) {
      return new LegRouteOutcome(
          true, "ROUTED_STANDALONE_STUB_VIA", stubResult.newItems(), 0.0, stubResult.placedVia());
    }

    return new LegRouteOutcome(false, "GROUND_ROUTING_FAILED", List.of(), 0.0, null);
  }

  /**
   * Checks whether a ground plane or conduction area on the given net exists directly underneath or
   * above the capacitor ground pin on another layer.
   */
  static boolean isPlaneDirectlyUnderneath(RoutingBoard board, Pin groundPin, int groundNetNo) {
    if (groundNetNo <= 0 || board == null || board.rules == null || board.rules.nets == null) {
      return false;
    }
    Net groundNet = board.rules.nets.get(groundNetNo);
    if (groundNet == null) {
      return false;
    }
    Point center = groundPin.getCenter();
    int pinLayer = groundPin.firstLayer();
    for (ConductionArea ca : board.getConductionAreas()) {
      if (ca.containsNet(groundNetNo) && ca.getLayer() != pinLayer) {
        if (ca.getArea() != null && ca.getArea().contains(center)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Routes a direct surface return strap from the capacitor ground pad directly to the paired IC
   * ground pin, restricting destination copper exclusively to the target IC to prevent shorting to
   * arbitrary neighboring components, and supporting opposite-side mounting through feed-through
   * vias.
   */
  private static StrapAttemptOutcome tryRouteGroundStrap(
      RoutingBoard board,
      RouterSettings settings,
      StoppableThread thread,
      Pin groundPin,
      Pin targetGndPin,
      int capLayer,
      int groundNetNo,
      int railNetNo,
      CapacitorCandidate candidate,
      double maxStrapLengthMm,
      double mmResolution) {
    AutorouteControl gndControl = new AutorouteControl(board, groundNetNo, settings);
    gndControl.ripupAllowed = false;
    gndControl.removeUnconnectedVias = false;
    if (settings.decoupling != null && settings.decoupling.maxDoorExpansions != null) {
      gndControl.maxDoorExpansions = settings.decoupling.maxDoorExpansions;
    }

    int ownerLayer = targetGndPin.firstLayer();
    boolean isSameLayer = (capLayer == ownerLayer);
    boolean oppositeSideEnabled =
        settings.decoupling == null
            || settings.decoupling.oppositeSideRouting == null
            || Boolean.TRUE.equals(settings.decoupling.oppositeSideRouting);

    if (!isSameLayer && !oppositeSideEnabled) {
      return new StrapAttemptOutcome(false, "OPPOSITE_SIDE_DISABLED", 0.0, List.of());
    }

    // Restrict destination set strictly to targetGndPin and IC-owned ground items
    // (prevents short-circuiting to arbitrary neighboring capacitors or unrelated ground copper)
    Set<Item> restrictedTargetGnd = new java.util.HashSet<>();
    restrictedTargetGnd.add(targetGndPin);
    for (Item it : targetGndPin.getConnectedSet(groundNetNo)) {
      if (it instanceof Pin p && p.getComponentId() == targetGndPin.getComponentId()) {
        restrictedTargetGnd.add(p);
      } else if (it instanceof Via || it instanceof Trace) {
        if (it.getNormalContacts().contains(targetGndPin)) {
          restrictedTargetGnd.add(it);
        }
      }
    }

    int effectiveTargetLayer;
    if (isSameLayer) {
      effectiveTargetLayer = capLayer;
      gndControl.viasAllowed = false;
      for (int i = 0; i < gndControl.layerCount; i++) {
        gndControl.layerActive[i] = gndControl.layerActive[i] && (i == capLayer);
      }
    } else {
      effectiveTargetLayer = ownerLayer;
      gndControl.viasAllowed = true;
      for (int i = 0; i < gndControl.layerCount; i++) {
        gndControl.layerActive[i] = (i == capLayer || i == ownerLayer);
      }
    }

    Set<Item> curGndConnected = groundPin.getConnectedSet(groundNetNo);
    return tryRouteStrap(
        board,
        settings,
        thread,
        gndControl,
        groundPin,
        targetGndPin,
        curGndConnected,
        restrictedTargetGnd,
        capLayer,
        effectiveTargetLayer,
        groundNetNo,
        railNetNo,
        targetGndPin.getComponentId(),
        candidate.componentId(),
        1.0,
        maxStrapLengthMm,
        mmResolution);
  }

  /**
   * Computes the forbidden bounding box for vias around an IC component, spanning its courtyard and
   * pad ring expanded by the clearance margin.
   */
  static IntBox computeForbiddenCourtyardBox(RoutingBoard board, int ownerCompId, int clearance) {
    if (ownerCompId <= 0 || board == null) {
      return null;
    }
    IntBox forbidden = CourtyardEscapeProbe.computeComponentPadBounds(board, ownerCompId);
    for (Item it : board.getItems()) {
      if (it instanceof ComponentOutline co && co.getComponentId() == ownerCompId) {
        if (co.isCourtyard() || co.isClosed()) {
          forbidden = (forbidden == null) ? co.boundingBox() : forbidden.union(co.boundingBox());
        }
      }
    }
    return forbidden != null ? forbidden.offset(clearance) : null;
  }

  /**
   * Attempts to route a surface strap between supply and target pins at the specified trace width
   * scale. Validates physical pad layer contact, guides and constrains via placement strictly
   * outside the IC courtyard, validates DRC, and verifies that adjacent unrouted SMD signal pins
   * are not trapped.
   */
  static StrapAttemptOutcome tryRouteStrap(
      RoutingBoard board,
      RouterSettings settings,
      StoppableThread thread,
      AutorouteControl control,
      Pin supplyPin,
      Item targetItem,
      Set<Item> supplyConnected,
      Set<Item> targetConnected,
      int capLayer,
      int targetLayer,
      int railNetNo,
      int groundNetNo,
      int ownerCompId,
      int capCompId,
      double widthScale,
      double maxStrapLengthMm,
      double mmResolution) {
    IntBox padUnion = supplyPin.boundingBox().union(targetItem.boundingBox());
    for (Item it : targetConnected) {
      if (it.isOnLayer(capLayer) || it.isOnLayer(targetLayer)) {
        padUnion = padUnion.union(it.boundingBox());
      }
    }
    int maxClearanceCap =
        (capLayer >= 0 && capLayer < board.getLayerCount())
            ? board.rules.clearanceMatrix.maxValue(capLayer)
            : 0;
    int maxClearanceTarget =
        (targetLayer >= 0 && targetLayer < board.getLayerCount())
            ? board.rules.clearanceMatrix.maxValue(targetLayer)
            : 0;
    int maxClearance = Math.max(maxClearanceCap, maxClearanceTarget);
    int searchRadius = (int) Math.ceil(maxStrapLengthMm * mmResolution) + maxClearance;
    IntBox searchBox = padUnion.offset(searchRadius);

    // Capture local checkpoint; if cross-layer, snapshot all layers (-1)
    int checkpointLayer = (capLayer == targetLayer) ? capLayer : -1;
    final DecouplingRouteCheckpoint checkpoint =
        DecouplingRouteCheckpoint.capture(board, searchBox, checkpointLayer, railNetNo);

    // Enforce layer constraints in AutorouteControl
    if (capLayer == targetLayer) {
      control.viasAllowed = false;
      for (int i = 0; i < control.layerCount; i++) {
        control.layerActive[i] = (i == capLayer);
      }
    } else {
      control.viasAllowed = true;
      for (int i = 0; i < control.layerCount; i++) {
        control.layerActive[i] = (i == capLayer || i == targetLayer);
      }
    }

    Set<Item> filteredSupply = new HashSet<>();
    for (Item it : supplyConnected) {
      if (it == supplyPin || it.isOnLayer(capLayer)) {
        filteredSupply.add(it);
      }
    }
    Set<Item> filteredTarget = new HashSet<>();
    for (Item it : targetConnected) {
      if (it == targetItem || it.isOnLayer(targetLayer)) {
        filteredTarget.add(it);
      }
    }

    // Apply scaled trace width safely to both layers
    applyScaledTraceWidth(control, capLayer, railNetNo, widthScale, board, settings, mmResolution);
    if (capLayer != targetLayer) {
      applyScaledTraceWidth(
          control, targetLayer, railNetNo, widthScale, board, settings, mmResolution);
    }

    // In-flight via guidance and forbidden courtyard constraints
    IntBox forbiddenBox = computeForbiddenCourtyardBox(board, ownerCompId, maxClearance);
    List<ViaObstacleArea> tempObstacles = new ArrayList<>();
    if (control.viasAllowed && forbiddenBox != null) {
      TileShape forbiddenShape = forbiddenBox.toSimplex();
      for (int layer : new int[] {capLayer, targetLayer}) {
        if (layer >= 0 && layer < board.getLayerCount()) {
          ViaObstacleArea obs =
              new ViaObstacleArea(
                  forbiddenShape,
                  layer,
                  Vector.ZERO,
                  0.0,
                  false,
                  0,
                  board.communication.idGenerator.newId(),
                  ownerCompId,
                  "TEMP_DECOUPLING_VIA_KEEPOUT",
                  FixedState.SYSTEM_FIXED,
                  board);
          board.insertItem(obs);
          tempObstacles.add(obs);
        }
      }
      // Guide via placement towards supply pin outside the IC courtyard
      control.directionalViaBiasTarget = supplyPin.getCenter().toFloat();
      control.directionalViaBiasWeight = 0.2;
    }

    AutorouteAttemptResult attempt;
    try {
      var engine =
          board.initAutoroute(
              railNetNo, control.traceClearanceClassIndex, thread, new TimeLimit(1000), false);
      attempt =
          engine.autorouteConnection(
              filteredSupply, filteredTarget, control, new TreeSet<>(), null);
    } finally {
      for (ViaObstacleArea obs : tempObstacles) {
        if (obs.isOnTheBoard()) {
          board.removeItem(obs);
        }
      }
    }

    if (attempt.state != AutorouteAttemptState.ROUTED) {
      checkpoint.rollback(board);
      return new StrapAttemptOutcome(false, "NO_ROUTE_FOUND", 0.0, List.of());
    }

    // Deterministic pull-tight optimization with timeLimit = 0
    int accuracy = settings.tracePullTightAccuracy != null ? settings.tracePullTightAccuracy : 500;
    board.optChangedArea(new int[] {railNetNo}, null, accuracy, control.traceCosts, thread, 0);

    // Collect newly added items
    List<Item> newItems = new ArrayList<>();
    int currentMaxId = board.communication.idGenerator.maxGeneratedId();
    for (int id = checkpoint.maxIdBefore + 1; id <= currentMaxId; id++) {
      Item it = board.getItem(id);
      if (it != null && it.isOnTheBoard()) {
        newItems.add(it);
      }
    }

    // Strict via count verification
    int viasPlacedInStrap = 0;
    for (Item newItem : newItems) {
      if (newItem instanceof Via) {
        viasPlacedInStrap++;
      }
    }

    if (!control.viasAllowed && viasPlacedInStrap > 0) {
      checkpoint.rollback(board);
      return new StrapAttemptOutcome(false, "UNEXPECTED_VIA_IN_SURFACE_STRAP", 0.0, List.of());
    }

    if (control.viasAllowed && viasPlacedInStrap != 1) {
      checkpoint.rollback(board);
      return new StrapAttemptOutcome(false, "EXCESS_VIAS_ROLLBACK", 0.0, List.of());
    }

    // Forbidden courtyard and pin heel via verification
    for (Item newItem : newItems) {
      if (newItem instanceof Via via) {
        if (forbiddenBox != null && forbiddenBox.contains(via.getCenter())) {
          checkpoint.rollback(board);
          return new StrapAttemptOutcome(false, "VIA_INSIDE_COURTYARD_ROLLBACK", 0.0, List.of());
        }
        Collection<Pin> ownerPins = board.getComponentPins(ownerCompId);
        if (ownerPins != null) {
          for (Pin p : ownerPins) {
            double dist = via.getCenter().toFloat().distance(p.getCenter().toFloat());
            int viaWidth =
                (via.getShape(targetLayer) != null)
                    ? via.getShape(targetLayer).boundingBox().width()
                    : via.boundingBox().width();
            double minSafeDist = (viaWidth + p.boundingBox().width()) / 2.0;
            if (dist < minSafeDist) {
              checkpoint.rollback(board);
              return new StrapAttemptOutcome(false, "VIA_ON_PIN_HEEL_ROLLBACK", 0.0, List.of());
            }
          }
        }
      }
    }

    // Physical pad layer contact verification: no trace on wrong layer touching SMD pads
    for (Item newItem : newItems) {
      if (newItem instanceof Trace trace) {
        for (Item contact : trace.getNormalContacts()) {
          if (contact instanceof Pin pin && pin.firstLayer() == pin.lastLayer()) {
            if (trace.getLayer() != pin.firstLayer()) {
              checkpoint.rollback(board);
              return new StrapAttemptOutcome(
                  false, "FALSE_LAYER_PAD_CONTACT_ROLLBACK", 0.0, List.of());
            }
          }
        }
      }
    }

    // Connectivity verification
    if (!supplyPin.getConnectedSet(railNetNo).contains(targetItem)) {
      checkpoint.rollback(board);
      return new StrapAttemptOutcome(false, "DISCONNECTED_PIN_ROLLBACK", 0.0, List.of());
    }

    double lengthMm = 0.0;
    for (Item newItem : newItems) {
      if (newItem instanceof Trace trace) {
        lengthMm += trace.getLength() / mmResolution;
      }
    }

    // Check DRC clearance violations
    boolean hasViolation = false;
    for (Item newItem : newItems) {
      if (!newItem.clearanceViolations().isEmpty()) {
        hasViolation = true;
        break;
      }
    }

    if (hasViolation) {
      checkpoint.rollback(board);
      return new StrapAttemptOutcome(false, "DRC_ROLLBACK", 0.0, List.of());
    }

    if (lengthMm > maxStrapLengthMm) {
      checkpoint.rollback(board);
      return new StrapAttemptOutcome(false, "DISTANCE_EXCEEDED", 0.0, List.of());
    }

    // Run CourtyardEscapeProbe on neighbor pins across both involved layers
    IntBox strapBox = padUnion;
    for (Item newItem : newItems) {
      strapBox = strapBox.union(newItem.boundingBox());
    }
    IntBox probeBox = strapBox.offset(2 * Math.max(maxClearance, 1000));

    boolean escapeSafe =
        CourtyardEscapeProbe.isEscapePossible(
            board, ownerCompId, capCompId, probeBox, capLayer, railNetNo, groundNetNo, thread);
    if (escapeSafe && capLayer != targetLayer) {
      escapeSafe =
          CourtyardEscapeProbe.isEscapePossible(
              board, ownerCompId, capCompId, probeBox, targetLayer, railNetNo, groundNetNo, thread);
    }

    if (!escapeSafe) {
      checkpoint.rollback(board);
      return new StrapAttemptOutcome(false, "TRAPPED_PIN_ROLLBACK", 0.0, List.of());
    }

    return new StrapAttemptOutcome(true, "ROUTED", lengthMm, newItems);
  }

  /** Applies scaled trace half-width to the control parameters, respecting neck-down limits. */
  private static void applyScaledTraceWidth(
      AutorouteControl control,
      int layer,
      int railNetNo,
      double scale,
      RoutingBoard board,
      RouterSettings settings,
      double mmResolution) {
    if (layer < 0 || layer >= control.layerCount) {
      return;
    }
    int baseHalfWidth = board.rules.getTraceHalfWidth(railNetNo, layer);
    double neckWidthUm = settings.getNeckWidthUm();
    double neckHalfWidthUnits =
        (neckWidthUm > 0) ? (neckWidthUm * (mmResolution / 1000.0) / 2.0) : 0.0;
    int minAllowedHalfWidth = (int) Math.max(baseHalfWidth, Math.round(neckHalfWidthUnits));
    int effectiveHalfWidth = (int) Math.max(minAllowedHalfWidth, Math.round(baseHalfWidth * scale));

    control.traceHalfWidth[layer] = effectiveHalfWidth;
    control.compensatedTraceHalfWidth[layer] =
        effectiveHalfWidth
            + board.rules.clearanceMatrix.clearanceCompensationValue(
                control.traceClearanceClassIndex, layer);

    int safetyMargin =
        board.rules.clearanceMatrix.getValue(
                control.traceClearanceClassIndex, control.traceClearanceClassIndex, layer, true)
            - board.rules.clearanceMatrix.getValue(
                control.traceClearanceClassIndex, control.traceClearanceClassIndex, layer, false);
    control.traceClearanceOffset[layer] =
        control.compensatedTraceHalfWidth[layer]
            + AutorouteEngine.TRACE_WIDTH_TOLERANCE
            + safetyMargin;
  }

  private static void tagDecouplingProtected(Item item, DecouplingLockMode lockMode) {
    item.setDecouplingProtected(true);
    if ((item instanceof Trace || item instanceof Via)
        && lockMode != DecouplingLockMode.NONE
        && !item.getFixedState().isPreservedRoute()) {
      item.setFixedState(FixedState.SYSTEM_FIXED);
    }
  }

  private record ViaDropResult(Via placedVia, List<Item> newItems) {}

  /**
   * Attempts via fanout using primary flank target first, probing secondary flank if primary fails.
   */
  private static ViaDropResult tryViaFanoutWithDualFlankProbe(
      RoutingBoard board,
      Pin pin,
      int netNo,
      RouterSettings settings,
      StoppableThread thread,
      FloatPoint primaryTarget,
      FloatPoint secondaryTarget,
      IntBox forbiddenCourtyardBox,
      int ownerCompId) {
    ViaDropResult result =
        trySingleViaFanout(
            board, pin, netNo, settings, thread, primaryTarget, forbiddenCourtyardBox, ownerCompId);
    if (result != null) {
      return result;
    }
    if (secondaryTarget != null) {
      return trySingleViaFanout(
          board, pin, netNo, settings, thread, secondaryTarget, forbiddenCourtyardBox, ownerCompId);
    }
    return null;
  }

  /** Performs a single fanout attempt with clearance violation checks and automatic rollback. */
  private static ViaDropResult trySingleViaFanout(
      RoutingBoard board,
      Pin pin,
      int netNo,
      RouterSettings settings,
      StoppableThread thread,
      FloatPoint biasTarget,
      IntBox forbiddenCourtyardBox,
      int ownerCompId) {
    int maxIdBefore = board.communication.idGenerator.maxGeneratedId();
    AutorouteAttemptResult attempt =
        board.fanout(pin, settings, -1, thread, new TimeLimit(1000), biasTarget);
    if (attempt.state != AutorouteAttemptState.ROUTED) {
      return null;
    }
    List<Item> newItems = new ArrayList<>();
    Via placedVia = null;
    for (Item it : pin.getConnectedSet(netNo)) {
      if (it.getId() > maxIdBefore) {
        newItems.add(it);
        if (it instanceof Via v) {
          placedVia = v;
        }
      }
    }
    boolean violation = false;
    for (Item item : newItems) {
      if (!item.clearanceViolations().isEmpty()) {
        violation = true;
        break;
      }
    }
    if (placedVia != null) {
      if (forbiddenCourtyardBox != null && forbiddenCourtyardBox.contains(placedVia.getCenter())) {
        violation = true;
      }
      Collection<Pin> ownerPins = board.getComponentPins(ownerCompId);
      if (ownerPins != null) {
        for (Pin p : ownerPins) {
          double dist = placedVia.getCenter().toFloat().distance(p.getCenter().toFloat());
          int viaWidth =
              (placedVia.getShape(pin.firstLayer()) != null)
                  ? placedVia.getShape(pin.firstLayer()).boundingBox().width()
                  : placedVia.boundingBox().width();
          double minSafeDist = (viaWidth + p.boundingBox().width()) / 2.0;
          if (dist < minSafeDist) {
            violation = true;
            break;
          }
        }
      }
    }
    if (violation) {
      for (Item item : newItems) {
        item.setFixedState(FixedState.UNFIXED);
        board.removeItem(item);
      }
      return null;
    }
    return new ViaDropResult(placedVia, newItems);
  }
}
