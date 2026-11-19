package app.freerouting.autoroute.decoupling;

import app.freerouting.autoroute.AutorouteAttemptResult;
import app.freerouting.autoroute.AutorouteAttemptState;
import app.freerouting.autoroute.maze.AutorouteControl;
import app.freerouting.autoroute.maze.AutorouteEngine;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.Component;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.core.StoppableThread;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.Point;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import app.freerouting.settings.DecouplingLockMode;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.util.ArrayList;
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
            defaultConfig.forcedGroundNets(),
            defaultConfig.forcedRejectComponents());

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
        boolean strapSuccess = false;
        double candidateStrapLengthMm = 0.0;
        String strapStatus = "NOT_ATTEMPTED";
        int targetPinId = task.targetSupplyItemId();
        String ownerName = "NONE";
        int viasPlacedForCap = 0;

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

        if (ownerPin != null) {
          Component ownerComp = board.components.get(ownerPin.getComponentId());
          ownerName =
              (ownerComp != null ? ownerComp.name : ("Comp#" + ownerPin.getComponentId()))
                  + "-"
                  + ownerPin.name();

          capLayer = supplyPin.firstLayer();
          boolean isSameLayer = (capLayer == ownerPin.firstLayer());
          boolean isOwnerTht =
              (ownerPin.firstLayer() != ownerPin.lastLayer()) && ownerPin.isOnLayer(capLayer);

          if (isSameLayer || isOwnerTht) {
            int targetLayer = capLayer;
            AutorouteControl control = new AutorouteControl(board, railNetNo, settings);
            if (targetLayer < 0
                || targetLayer >= control.layerCount
                || !control.layerActive[targetLayer]) {
              failed++;
              strapStatus = "LAYER_DISABLED";
            } else {
              double centerDistUnits =
                  supplyPin.getCenter().toFloat().distance(ownerPin.getCenter().toFloat());
              if (centerDistUnits > maxStrapLengthUnits) {
                failed++;
                strapStatus = "DISTANCE_EXCEEDED";
              } else {
                control.viasAllowed = false;
                control.ripupAllowed = false;
                control.removeUnconnectedVias = false;
                if (settings.decoupling != null && settings.decoupling.maxDoorExpansions != null) {
                  control.maxDoorExpansions = settings.decoupling.maxDoorExpansions;
                }
                for (int i = 0; i < control.layerCount; i++) {
                  control.layerActive[i] = control.layerActive[i] && (i == targetLayer);
                }

                Set<Item> supplyConnected = supplyPin.getConnectedSet(railNetNo);
                if (supplyConnected.contains(ownerPin)) {
                  alreadyConnected++;
                  strapSuccess = true;
                  strapStatus = "ALREADY_CONNECTED";
                  tagDecouplingProtected(supplyPin, lockMode);
                  tagDecouplingProtected(ownerPin, lockMode);
                  if (ReasonCode.FILTERED_ISLAND.isSet(candidate.reasonMask())) {
                    ownerPin.setFilteredIslandNonFeeder(true);
                  }
                } else {
                  Set<Item> ownerConnected = ownerPin.getConnectedSet(railNetNo);

                  // 2-Phase width trial: try wide strap (e.g. 1.5x), then downgrade to 1.0x if
                  // trapped/DRC
                  double[] scalesToTry =
                      (traceWidthScale > 1.0)
                          ? new double[] {traceWidthScale, 1.0}
                          : new double[] {1.0};

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
                            candidate.groundNetNumber(),
                            ownerPin.getComponentId(),
                            candidate.componentId(),
                            scale,
                            maxStrapLengthMm,
                            mmResolution);

                    if (outcome.success()) {
                      break;
                    }
                  }

                  if (outcome != null && outcome.success()) {
                    strapSuccess = true;
                    strapStatus = outcome.status();
                    routedStraps++;
                    candidateStrapLengthMm = outcome.lengthMm();
                    totalStrapLengthMm += candidateStrapLengthMm;
                    tagDecouplingProtected(supplyPin, lockMode);
                    tagDecouplingProtected(ownerPin, lockMode);
                    for (Item newItem : outcome.newItems()) {
                      if (newItem instanceof Via) {
                        viasPlacedForCap++;
                        viasPlaced++;
                      }
                      tagDecouplingProtected(newItem, lockMode);
                    }
                    if (ReasonCode.FILTERED_ISLAND.isSet(candidate.reasonMask())) {
                      ownerPin.setFilteredIslandNonFeeder(true);
                      for (Item newItem : outcome.newItems()) {
                        newItem.setFilteredIslandNonFeeder(true);
                      }
                    }
                  } else {
                    // Secondary overflow cap: fallback flanking attempt to primary cap pad
                    boolean flanked = false;
                    if (task.isSecondary() && task.pairedPrimaryCapPinId() > 0) {
                      Item primaryCapItem = board.getItem(task.pairedPrimaryCapPinId());
                      if (primaryCapItem instanceof Pin primaryCapPin
                          && primaryCapPin.firstLayer() == supplyPin.firstLayer()
                          && !primaryCapPin.equals(ownerPin)
                          && !primaryCapPin.hasIgnoredNets()) {
                        Set<Item> primaryCapConnected = primaryCapPin.getConnectedSet(railNetNo);
                        // Only flank to the primary cap if the primary cap is verified connected to
                        // the
                        // owner IC. If the primary cap failed to reach the IC, flanking would
                        // create an
                        // isolated, floating daisy-chain between two ungrounded/unpowered
                        // capacitors.
                        if (primaryCapConnected.contains(ownerPin)
                            && supplyConnected.contains(primaryCapPin)) {
                          alreadyConnected++;
                          strapSuccess = true;
                          strapStatus = "ALREADY_CONNECTED";
                          tagDecouplingProtected(supplyPin, lockMode);
                          tagDecouplingProtected(primaryCapPin, lockMode);
                          if (ReasonCode.FILTERED_ISLAND.isSet(candidate.reasonMask())) {
                            primaryCapPin.setFilteredIslandNonFeeder(true);
                          }
                          flanked = true;
                        } else if (primaryCapConnected.contains(ownerPin)) {
                          double flankDistUnits =
                              supplyPin
                                  .getCenter()
                                  .toFloat()
                                  .distance(primaryCapPin.getCenter().toFloat());
                          if (flankDistUnits <= maxStrapLengthUnits) {
                            StrapAttemptOutcome flankOutcome = null;
                            for (double scale : scalesToTry) {
                              Set<Item> currentSupplyConnected =
                                  supplyPin.getConnectedSet(railNetNo);
                              Set<Item> currentPrimaryConnected =
                                  primaryCapPin.getConnectedSet(railNetNo);
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
                                      candidate.groundNetNumber(),
                                      primaryCapPin.getComponentId(),
                                      candidate.componentId(),
                                      scale,
                                      maxStrapLengthMm,
                                      mmResolution);
                              if (flankOutcome.success()) {
                                break;
                              }
                            }

                            if (flankOutcome != null && flankOutcome.success()) {
                              flanked = true;
                              strapSuccess = true;
                              strapStatus = "ROUTED_FLANKING";
                              routedStraps++;
                              candidateStrapLengthMm = flankOutcome.lengthMm();
                              totalStrapLengthMm += candidateStrapLengthMm;
                              tagDecouplingProtected(supplyPin, lockMode);
                              tagDecouplingProtected(primaryCapPin, lockMode);
                              for (Item newItem : flankOutcome.newItems()) {
                                if (newItem instanceof Via) {
                                  viasPlacedForCap++;
                                  viasPlaced++;
                                }
                                tagDecouplingProtected(newItem, lockMode);
                              }
                              if (ReasonCode.FILTERED_ISLAND.isSet(candidate.reasonMask())) {
                                primaryCapPin.setFilteredIslandNonFeeder(true);
                                for (Item newItem : flankOutcome.newItems()) {
                                  newItem.setFilteredIslandNonFeeder(true);
                                }
                              }
                            }
                          }
                        }
                      }
                    }

                    if (!flanked) {
                      failed++;
                      strapStatus = outcome != null ? outcome.status() : "NO_ROUTE_FOUND";
                    }
                  }
                }
              }
            }
          } else {
            // Opposite-layer SMD mounting (e.g. bottom-side cap under top-side IC pad)
            boolean oppositeSideEnabled =
                settings.decoupling == null
                    || settings.decoupling.oppositeSideRouting == null
                    || Boolean.TRUE.equals(settings.decoupling.oppositeSideRouting);

            if (!oppositeSideEnabled) {
              failed++;
              strapStatus = "OPPOSITE_SIDE_DISABLED";
            } else {
              int ownerLayer = ownerPin.firstLayer();
              AutorouteControl control = new AutorouteControl(board, railNetNo, settings);
              if (capLayer < 0
                  || capLayer >= control.layerCount
                  || ownerLayer < 0
                  || ownerLayer >= control.layerCount
                  || !control.layerActive[capLayer]
                  || !control.layerActive[ownerLayer]) {
                failed++;
                strapStatus = "LAYER_DISABLED";
              } else {
                double lateralDistUnits =
                    supplyPin.getCenter().toFloat().distance(ownerPin.getCenter().toFloat());
                if (lateralDistUnits > maxStrapLengthUnits) {
                  failed++;
                  strapStatus = "DISTANCE_EXCEEDED";
                } else {
                  Set<Item> supplyConnected = supplyPin.getConnectedSet(railNetNo);
                  if (supplyConnected.contains(ownerPin)) {
                    alreadyConnected++;
                    strapSuccess = true;
                    strapStatus = "ALREADY_CONNECTED";
                    tagDecouplingProtected(supplyPin, lockMode);
                    tagDecouplingProtected(ownerPin, lockMode);
                    if (ReasonCode.FILTERED_ISLAND.isSet(candidate.reasonMask())) {
                      ownerPin.setFilteredIslandNonFeeder(true);
                    }
                  } else {
                    Set<Item> ownerConnected = ownerPin.getConnectedSet(railNetNo);

                    // Sub-case 3A: Does ownerPin already reach capLayer?
                    boolean hasConnectionOnCapLayer = false;
                    for (Item it : ownerConnected) {
                      if (it.isOnLayer(capLayer)) {
                        hasConnectionOnCapLayer = true;
                        break;
                      }
                    }

                    int effectiveTargetLayer;
                    if (hasConnectionOnCapLayer) {
                      // Sub-case 3A: Target existing fanout via/trace directly on capLayer (0 vias)
                      effectiveTargetLayer = capLayer;
                      control.viasAllowed = false;
                      control.ripupAllowed = false;
                      control.removeUnconnectedVias = false;
                      if (settings.decoupling != null
                          && settings.decoupling.maxDoorExpansions != null) {
                        control.maxDoorExpansions = settings.decoupling.maxDoorExpansions;
                      }
                      for (int i = 0; i < control.layerCount; i++) {
                        control.layerActive[i] = control.layerActive[i] && (i == capLayer);
                      }
                    } else {
                      // Sub-case 3B: Fresh feed-through via between capLayer and ownerLayer
                      effectiveTargetLayer = ownerLayer;
                      control.viasAllowed = true;
                      control.ripupAllowed = false;
                      control.removeUnconnectedVias = false;
                      if (settings.decoupling != null
                          && settings.decoupling.maxDoorExpansions != null) {
                        control.maxDoorExpansions = settings.decoupling.maxDoorExpansions;
                      }
                      for (int i = 0; i < control.layerCount; i++) {
                        control.layerActive[i] = (i == capLayer || i == ownerLayer);
                      }
                    }

                    double[] scalesToTry =
                        (traceWidthScale > 1.0)
                            ? new double[] {traceWidthScale, 1.0}
                            : new double[] {1.0};

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
                              effectiveTargetLayer,
                              railNetNo,
                              candidate.groundNetNumber(),
                              ownerPin.getComponentId(),
                              candidate.componentId(),
                              scale,
                              maxStrapLengthMm,
                              mmResolution);

                      if (outcome.success()) {
                        break;
                      }
                    }

                    if (outcome != null && outcome.success()) {
                      strapSuccess = true;
                      strapStatus =
                          hasConnectionOnCapLayer
                              ? "ROUTED_OPPOSITE_FANOUT"
                              : "ROUTED_OPPOSITE_FEEDTHROUGH_VIA";
                      routedStraps++;
                      candidateStrapLengthMm = outcome.lengthMm();
                      totalStrapLengthMm += candidateStrapLengthMm;
                      tagDecouplingProtected(supplyPin, lockMode);
                      tagDecouplingProtected(ownerPin, lockMode);
                      for (Item newItem : outcome.newItems()) {
                        if (newItem instanceof Via) {
                          viasPlacedForCap++;
                          viasPlaced++;
                        }
                        tagDecouplingProtected(newItem, lockMode);
                      }
                      if (ReasonCode.FILTERED_ISLAND.isSet(candidate.reasonMask())) {
                        ownerPin.setFilteredIslandNonFeeder(true);
                        for (Item newItem : outcome.newItems()) {
                          newItem.setFilteredIslandNonFeeder(true);
                        }
                      }
                    } else {
                      failed++;
                      strapStatus = outcome != null ? outcome.status() : "NO_ROUTE_FOUND";
                    }
                  }
                }
              }
            }
          }
        } else {
          strapStatus = task.isPlaneOnly() ? "PLANE_ONLY_PENDING" : "NO_OWNER_IN_RADIUS";
          if (!task.isPlaneOnly()) {
            failed++;
          }
        }

        // Dedicated ground/plane via adjacent to the pad
        int groundNetNo = candidate.groundNetNumber();
        Net groundNet = board.rules.nets.get(groundNetNo);

        // Through-hole pins span all layers and connect directly to internal planes
        boolean groundIsSmd = (groundPin.firstLayer() == groundPin.lastLayer());
        boolean groundAlreadyOnPlane = !groundIsSmd;
        if (!groundAlreadyOnPlane) {
          for (Item it : groundPin.getNormalContacts()) {
            if (it instanceof ConductionArea || it instanceof Via) {
              groundAlreadyOnPlane = true;
              break;
            }
          }
        }
        if (!groundAlreadyOnPlane && groundNet != null && groundNet.containsPlane()) {
          for (Item it : groundPin.getConnectedSet(groundNetNo)) {
            if (it instanceof ConductionArea) {
              groundAlreadyOnPlane = true;
              break;
            }
          }
        }

        boolean applyBias =
            settings.decoupling == null
                || settings.decoupling.mutualInductanceBias == null
                || Boolean.TRUE.equals(settings.decoupling.mutualInductanceBias);

        FloatPoint p1 = groundPin.getCenter().toFloat();
        FloatPoint p2 = supplyPin.getCenter().toFloat();
        double dx = p2.x - p1.x;
        double dy = p2.y - p1.y;
        double padDist = Math.sqrt(dx * dx + dy * dy);
        double escapeDistance = 0.5 * mmResolution;

        FloatPoint primaryGroundTarget = null;
        FloatPoint secondaryGroundTarget = null;
        double nx = 0.0;
        double ny = 0.0;
        boolean hasNormalVector = false;

        // Skip bias for degenerate concentric or near-zero pad pitch footprints
        if (applyBias && padDist > 10.0) {
          // Unit vector along inter-pad axis and normal vector out to the lateral flank
          double ux = dx / padDist;
          double uy = dy / padDist;
          nx = -uy;
          ny = ux;
          hasNormalVector = true;
          primaryGroundTarget =
              new FloatPoint(p1.x + escapeDistance * nx, p1.y + escapeDistance * ny);
          secondaryGroundTarget =
              new FloatPoint(p1.x - escapeDistance * nx, p1.y - escapeDistance * ny);
        }

        Via placedVia = null;
        if (groundAlreadyOnPlane) {
          tagDecouplingProtected(groundPin, lockMode);
          if (task.isPlaneOnly()) {
            strapSuccess = true;
            strapStatus = "PLANE_ONLY_ROUTED";
          }
        } else {
          boolean groundNetHasPlane = groundNet != null && groundNet.containsPlane();
          boolean planeUnderneath = isPlaneDirectlyUnderneath(board, groundPin, groundNetNo);
          Item targetGndItem =
              (task.targetGroundItemId() > 0) ? board.getItem(task.targetGroundItemId()) : null;
          Pin targetGndPin = (targetGndItem instanceof Pin p) ? p : null;

          Set<Item> currentGroundConnected = groundPin.getConnectedSet(groundNetNo);
          if (targetGndPin != null && currentGroundConnected.contains(targetGndPin)) {
            tagDecouplingProtected(groundPin, lockMode);
            tagDecouplingProtected(targetGndPin, lockMode);
            if (task.isPlaneOnly()) {
              strapSuccess = true;
              strapStatus = "ALREADY_CONNECTED";
            }
          } else {
            double distToIcGndUnits =
                (targetGndPin != null)
                    ? groundPin.getCenter().toFloat().distance(targetGndPin.getCenter().toFloat())
                    : Double.MAX_VALUE;
            double proximityThresholdUnits = 1.5 * mmResolution;

            boolean preferSurfaceStrap =
                (!planeUnderneath || distToIcGndUnits <= proximityThresholdUnits)
                    && (targetGndPin != null && distToIcGndUnits <= maxStrapLengthUnits);

            boolean groundRouted = false;

            if (preferSurfaceStrap) {
              // 1. Primary: Direct Surface Return Strap to IC ground pin
              StrapAttemptOutcome gndOutcome =
                  tryRouteGroundStrap(
                      board,
                      settings,
                      thread,
                      groundPin,
                      targetGndPin,
                      capLayer,
                      groundNetNo,
                      railNetNo,
                      candidate,
                      maxStrapLengthMm,
                      mmResolution);
              if (gndOutcome != null && gndOutcome.success()) {
                groundRouted = true;
                tagDecouplingProtected(groundPin, lockMode);
                tagDecouplingProtected(targetGndPin, lockMode);
                for (Item newItem : gndOutcome.newItems()) {
                  tagDecouplingProtected(newItem, lockMode);
                }
                if (task.isPlaneOnly()) {
                  strapSuccess = true;
                  strapStatus = "ROUTED_GROUND_STRAP";
                }
              }

              // 2. Fallback: Lateral flank via drop (if strap failed and plane exists)
              if (!groundRouted && (planeUnderneath || groundNetHasPlane)) {
                ViaDropResult groundViaResult =
                    tryViaFanoutWithDualFlankProbe(
                        board,
                        groundPin,
                        groundNetNo,
                        settings,
                        thread,
                        primaryGroundTarget,
                        secondaryGroundTarget);
                if (groundViaResult != null) {
                  groundRouted = true;
                  placedVia = groundViaResult.placedVia();
                  if (placedVia != null) {
                    viasPlacedForCap++;
                    viasPlaced++;
                  }
                  tagDecouplingProtected(groundPin, lockMode);
                  for (Item newItem : groundViaResult.newItems()) {
                    tagDecouplingProtected(newItem, lockMode);
                  }
                  if (task.isPlaneOnly()) {
                    strapSuccess = true;
                    strapStatus = "PLANE_ONLY_ROUTED";
                  }
                }
              }
            } else {
              // 1. Primary: Lateral flank via drop to solid plane
              if (planeUnderneath || groundNetHasPlane) {
                ViaDropResult groundViaResult =
                    tryViaFanoutWithDualFlankProbe(
                        board,
                        groundPin,
                        groundNetNo,
                        settings,
                        thread,
                        primaryGroundTarget,
                        secondaryGroundTarget);
                if (groundViaResult != null) {
                  groundRouted = true;
                  placedVia = groundViaResult.placedVia();
                  if (placedVia != null) {
                    viasPlacedForCap++;
                    viasPlaced++;
                  }
                  tagDecouplingProtected(groundPin, lockMode);
                  for (Item newItem : groundViaResult.newItems()) {
                    tagDecouplingProtected(newItem, lockMode);
                  }
                  if (task.isPlaneOnly()) {
                    strapSuccess = true;
                    strapStatus = "PLANE_ONLY_ROUTED";
                  }
                }
              }

              // 2. Fallback: Direct surface return strap to IC ground pin
              if (!groundRouted
                  && targetGndPin != null
                  && distToIcGndUnits <= maxStrapLengthUnits) {
                StrapAttemptOutcome gndOutcome =
                    tryRouteGroundStrap(
                        board,
                        settings,
                        thread,
                        groundPin,
                        targetGndPin,
                        capLayer,
                        groundNetNo,
                        railNetNo,
                        candidate,
                        maxStrapLengthMm,
                        mmResolution);
                if (gndOutcome != null && gndOutcome.success()) {
                  groundRouted = true;
                  tagDecouplingProtected(groundPin, lockMode);
                  tagDecouplingProtected(targetGndPin, lockMode);
                  for (Item newItem : gndOutcome.newItems()) {
                    tagDecouplingProtected(newItem, lockMode);
                  }
                  if (task.isPlaneOnly()) {
                    strapSuccess = true;
                    strapStatus = "ROUTED_GROUND_STRAP";
                  }
                }
              }
            }

            if (!groundRouted && task.isPlaneOnly()) {
              failed++;
              strapStatus =
                  (!planeUnderneath && !groundNetHasPlane)
                      ? "NO_GROUND_PLANE"
                      : "GROUND_ROUTING_FAILED";
            }
          }
        }

        // Dedicated power/plane via adjacent to the supply pad (pin-first supply routing)
        Net railNet = board.rules.nets.get(railNetNo);
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
        if (!supplyAlreadyOnPlane && railNet != null && railNet.containsPlane()) {
          for (Item it : supplyPin.getConnectedSet(railNetNo)) {
            if (it instanceof ConductionArea || it instanceof Via) {
              supplyAlreadyOnPlane = true;
              break;
            }
          }
        }

        Via placedSupplyVia = null;
        if (!supplyAlreadyOnPlane && railNet != null && railNet.containsPlane()) {
          FloatPoint primarySupplyTarget = null;
          FloatPoint secondarySupplyTarget = null;
          if (hasNormalVector) {
            if (placedVia != null) {
              // Placed ground via offset: v_offset = V1 - P1
              FloatPoint v1 = placedVia.getCenter().toFloat();
              double offsetX = v1.x - p1.x;
              double offsetY = v1.y - p1.y;
              // Project offset onto the normal flank vector to prevent end-fire fallback
              // from pointing inward along the longitudinal axis between pads:
              double compN = offsetX * nx + offsetY * ny;
              if (Math.abs(compN) >= 0.2 * escapeDistance) {
                double sign = Math.signum(compN);
                if (sign == 0.0) {
                  sign = 1.0;
                }
                primarySupplyTarget = new FloatPoint(p2.x + compN * nx, p2.y + compN * ny);
                secondarySupplyTarget =
                    new FloatPoint(
                        p2.x - sign * escapeDistance * nx, p2.y - sign * escapeDistance * ny);
              } else {
                // V1 was placed end-fire along ±u with negligible normal component;
                // fall back to the lateral flank target to ensure we stay out of the courtyard
                primarySupplyTarget =
                    new FloatPoint(p2.x + escapeDistance * nx, p2.y + escapeDistance * ny);
                secondarySupplyTarget =
                    new FloatPoint(p2.x - escapeDistance * nx, p2.y - escapeDistance * ny);
              }
            } else {
              // Ground was already on plane; target default lateral flank
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
                  secondarySupplyTarget);
          if (supplyViaResult != null) {
            placedSupplyVia = supplyViaResult.placedVia();
            if (placedSupplyVia != null) {
              viasPlacedForCap++;
              viasPlaced++;
            }
            tagDecouplingProtected(supplyPin, lockMode);
            for (Item newItem : supplyViaResult.newItems()) {
              tagDecouplingProtected(newItem, lockMode);
            }
          }
        }

        if (placedVia != null && placedSupplyVia != null && padDist > 10.0) {
          double interViaDistMm =
              placedVia.getCenter().toFloat().distance(placedSupplyVia.getCenter().toFloat())
                  / mmResolution;
          double padDistMm = padDist / mmResolution;
          if (interViaDistMm <= 1.5 * padDistMm) {
            coupledViaPairs++;
          } else {
            fallbackViaPairs++;
          }
        }

        double groundViaDistanceMm =
            (placedVia != null)
                ? groundPin.getCenter().toFloat().distance(placedVia.getCenter().toFloat())
                    / mmResolution
                : 0.0;

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
                    "Decoupling capacitor %s is outside maximum reach (%.2f mm) from owner %s (%s).",
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

    boolean hasConnectionOnCapLayer = false;
    if (!isSameLayer) {
      for (Item it : restrictedTargetGnd) {
        if (it.isOnLayer(capLayer)) {
          hasConnectionOnCapLayer = true;
          break;
        }
      }
    }

    int effectiveTargetLayer;
    if (isSameLayer || hasConnectionOnCapLayer) {
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
   * Attempts to route a surface strap between supply and target pins at the specified trace width
   * scale. Validates DRC and verifies that adjacent unrouted SMD signal pins are not trapped.
   */
  static StrapAttemptOutcome tryRouteStrap(
      RoutingBoard board,
      RouterSettings settings,
      StoppableThread thread,
      AutorouteControl control,
      Pin supplyPin,
      Pin targetPin,
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
    IntBox padUnion = supplyPin.boundingBox().union(targetPin.boundingBox());
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
    DecouplingRouteCheckpoint checkpoint =
        DecouplingRouteCheckpoint.capture(board, searchBox, checkpointLayer, railNetNo);

    // Apply scaled trace width safely to both layers
    applyScaledTraceWidth(control, capLayer, railNetNo, widthScale, board, settings, mmResolution);
    if (capLayer != targetLayer) {
      applyScaledTraceWidth(
          control, targetLayer, railNetNo, widthScale, board, settings, mmResolution);
    }

    var engine =
        board.initAutoroute(
            railNetNo, control.traceClearanceClassIndex, thread, new TimeLimit(1000), false);

    AutorouteAttemptResult attempt =
        engine.autorouteConnection(
            supplyConnected, targetConnected, control, new TreeSet<>(), null);

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
      if (it != null) {
        newItems.add(it);
      }
    }

    // Strict 1-via invariant verification
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

    if (control.viasAllowed && viasPlacedInStrap > 1) {
      checkpoint.rollback(board);
      return new StrapAttemptOutcome(false, "EXCESS_VIAS_ROLLBACK", 0.0, List.of());
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
      FloatPoint secondaryTarget) {
    ViaDropResult result = trySingleViaFanout(board, pin, netNo, settings, thread, primaryTarget);
    if (result != null) {
      return result;
    }
    if (secondaryTarget != null) {
      return trySingleViaFanout(board, pin, netNo, settings, thread, secondaryTarget);
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
      FloatPoint biasTarget) {
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
