package app.freerouting.autoroute.pipeline;

import app.freerouting.autoroute.AutorouteAttemptResult;
import app.freerouting.autoroute.AutorouteAttemptState;
import app.freerouting.autoroute.maze.AutorouteControl;
import app.freerouting.autoroute.maze.AutorouteEngine;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.drc.ClearanceBaseline;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/** Executes one autoroute connection, including necked retry and strict-DRC recovery. */
final class AutorouteConnectionRouter {

  private final BatchAutorouter router;

  AutorouteConnectionRouter(BatchAutorouter router) {
    this.router = router;
  }

  /**
   * Set by {@link #route(Item, int, SortedSet, Map, int, boolean)} when it aimed at the plane only.
   */
  private boolean aimedAtPlaneOnly;

  /**
   * plane_as_priority: pads of the net already in the plane and at most plane_share_gap_um from
   * {@code item}, edge to edge, are accepted as destinations next to the plane. No via fits between
   * two such pads; sharing one is the usual practice for adjacent pins of a package.
   */
  private void addSharablePads(Item item, int netNo, Set<Item> destinations) {
    if (!(item instanceof Pin pin)
        || pin.firstLayer() != pin.lastLayer()
        || router.settings.autorouter == null
        || !Boolean.TRUE.equals(router.settings.autorouter.planeAsPriority)) {
      return;
    }
    Double gapUm = router.settings.autorouter.planeShareGapUm;
    double shareGapUm = gapUm != null ? gapUm : 500.0;
    if (shareGapUm <= 0) {
      return;
    }
    int resolution = Math.max(1, router.board.communication.resolution);
    double maxGap = Unit.scale(shareGapUm * resolution, Unit.UM, router.board.communication.unit);
    IntBox box = pin.boundingBox();
    Set<Item> inPlane = new java.util.TreeSet<>();
    for (Item plane : List.copyOf(destinations)) {
      inPlane.addAll(plane.getConnectedSet(netNo));
    }
    for (Item other : inPlane) {
      if (other instanceof Pin otherPin
          && otherPin != pin
          && otherPin.isOnLayer(pin.firstLayer())
          && boxGap(box, otherPin.boundingBox()) <= maxGap) {
        destinations.add(otherPin);
      }
    }
  }

  private Set<Item> planeEscapeObstacles(
      Item item, int netNo, Set<Item> connectedSet, Set<Item> destinations) {
    if (!(item instanceof Pin pin)
        || router.settings.autorouter == null
        || !Boolean.TRUE.equals(router.settings.autorouter.planeAsPriority)) {
      return Set.of();
    }
    return router.board.planeEscapeObstacles(pin, netNo, connectedSet, destinations);
  }

  private static double boxGap(IntBox a, IntBox b) {
    double dx = Math.max(0, Math.max(b.ll.x - a.ur.x, a.ll.x - b.ur.x));
    double dy = Math.max(0, Math.max(b.ll.y - a.ur.y, a.ll.y - b.ur.y));
    return Math.hypot(dx, dy);
  }

  /**
   * Routes one connection of {@code item}. A pad of a plane net is first routed to its planes on
   * power layers only, so that it drops into the plane through a via of its own instead of reaching
   * a neighbouring pad of the same net on the outer layers (a short trace costs less than a via,
   * and every unconnected pad of the net used to be a valid destination). Only when no route to the
   * plane is found is the rest of the net accepted as destination, so a pad with no room for a via
   * still gets connected.
   */
  AutorouteAttemptResult route(
      Item item,
      int routeNetNo,
      SortedSet<Item> rippedItemList,
      Map<Item, Integer> ripupCosts,
      int ripupPassNo) {
    AutorouteAttemptResult result =
        route(item, routeNetNo, rippedItemList, ripupCosts, ripupPassNo, true);
    if (aimedAtPlaneOnly
        && (result.state == AutorouteAttemptState.FAILED
            || result.state == AutorouteAttemptState.INSERT_ERROR)) {
      result = route(item, routeNetNo, rippedItemList, ripupCosts, ripupPassNo, false);
    }
    return result;
  }

  private AutorouteAttemptResult route(
      Item item,
      int routeNetNo,
      SortedSet<Item> rippedItemList,
      Map<Item, Integer> ripupCosts,
      int ripupPassNo,
      boolean planeOnly) {
    aimedAtPlaneOnly = false;
    try {
      Net routeNet = router.board.rules.nets.get(routeNetNo);
      boolean containsPlane = routeNet != null && routeNet.containsPlane();
      boolean routedFirst = routeNet != null && routeNet.isRoutedFirst();
      int signalViaCosts =
          routedFirst ? router.settings.getPriorityViaCosts() : router.settings.getViaCosts();
      int currentViaCosts =
          containsPlane
              ? Math.min(router.settings.getPlaneViaCosts(), signalViaCosts)
              : signalViaCosts;

      AutorouteControl autorouteControl =
          new AutorouteControl(
              router.board, routeNetNo, router.settings, currentViaCosts, router.getTraceCosts());
      autorouteControl.ripupAllowed = true;
      autorouteControl.ripupCosts = router.getStartRipupCosts() * ripupPassNo;
      autorouteControl.netsNotToRip =
          routedFirst
              ? router.ripupMemory.netsNotToRip(routeNetNo)
              : router.netsNotToRipFor(routeNetNo);
      autorouteControl.removeUnconnectedVias = router.isRemoveUnconnectedVias();

      Set<Item> unconnectedSet = item.getUnconnectedSet(routeNetNo);
      if (unconnectedSet.isEmpty()) {
        return new AutorouteAttemptResult(AutorouteAttemptState.NO_UNCONNECTED_NETS);
      }

      Set<Item> connectedSet = item.getConnectedSet(routeNetNo);
      Set<Item> routeStartSet;
      Set<Item> routeDestSet;
      Set<Item> planeEscapeObstacles = Set.of();
      if (containsPlane) {
        if (ConductionArea.coversAllPlanesOfNet(connectedSet, unconnectedSet, routeNetNo)) {
          return new AutorouteAttemptResult(AutorouteAttemptState.CONNECTED_TO_PLANE);
        }
        routeStartSet = connectedSet;
        routeDestSet = unconnectedSet;
        if (planeOnly) {
          Set<Item> planes = router.board.powerLayerPlanes(unconnectedSet);
          planes.removeIf(p -> p instanceof ConductionArea ca && ca.isFractured());
          if (!planes.isEmpty()) {
            addSharablePads(item, routeNetNo, planes);
            routeDestSet = planes;
            aimedAtPlaneOnly = true;
            planeEscapeObstacles = planeEscapeObstacles(item, routeNetNo, connectedSet, planes);
          }
        }
        Set<Item> filteredStartSet = new TreeSet<>();
        for (Item it : routeStartSet) {
          if (!(it instanceof ConductionArea ca && ca.isFractured())) {
            filteredStartSet.add(it);
          }
        }
        if (!filteredStartSet.isEmpty()) {
          routeStartSet = filteredStartSet;
        }
        Set<Item> filteredDestSet = new TreeSet<>();
        for (Item it : routeDestSet) {
          if (!(it instanceof ConductionArea ca && ca.isFractured())) {
            filteredDestSet.add(it);
          }
        }
        if (!filteredDestSet.isEmpty()) {
          routeDestSet = filteredDestSet;
        }
      } else {
        routeStartSet = unconnectedSet;
        routeDestSet = connectedSet;
      }
      router.setAirLine(AutorouteAirlineCalculator.calculateAirline(routeStartSet, routeDestSet));

      double maxMilliseconds = 100000 * Math.pow(2, ripupPassNo - 1);
      maxMilliseconds = Math.min(maxMilliseconds, Integer.MAX_VALUE);
      TimeLimit timeLimit = new TimeLimit((int) maxMilliseconds);

      boolean strictDrc = router.settings.isStrictDrc();
      // The obstacles of a plane escape are set around the class-width search below only. A necked
      // search builds its own engine without them and would reach the plane through the copper
      // they block, so a plane escape is never necked: when it fails, the search to the whole net
      // follows and may neck.
      boolean escaping = !planeEscapeObstacles.isEmpty();

      // start_necked: a net that needed the neck before routes at the neck width, ripping like any
      // search, and
      // that result stands. The class-width search it skips would use up the space before the
      // necked retry
      // and, when it routes, displace the neighbours the neck fits beside.
      if (router.startNecked
          && !escaping
          && router.settings.getNeckWidthUm() > 0
          && router.neckedRouteNets.contains(routeNetNo)) {
        int beforeStart = router.board.communication.idGenerator.maxGeneratedId();
        byte[] startSnapshot = strictDrc ? router.board.serialize(false) : null;
        AutorouteAttemptResult started =
            retryConnectionNecked(
                routeNetNo,
                autorouteControl,
                currentViaCosts,
                routeStartSet,
                routeDestSet,
                rippedItemList,
                ripupCosts,
                ripupPassNo,
                timeLimit,
                /* planned= */ true,
                /* quiet= */ true);
        if (started != null) {
          if (started.state != AutorouteAttemptState.ROUTED) {
            router.startNeckedFailed++;
            return started;
          }
          AutorouteAttemptResult rejection =
              applyStrictDrcAfterRoute(routeNetNo, beforeStart, startSnapshot, ripupPassNo);
          if (rejection != null) {
            return rejection;
          }
          router.startNeckedRouted++;
          return started;
        }
      }

      // The obstacles below hold for this search only: rooms and drill pages built with them must
      // not be retained for the next connection of the net.
      AutorouteEngine autorouteEngine =
          router.board.initAutoroute(
              routeNetNo,
              autorouteControl.traceClearanceClassIndex,
              router.thread,
              timeLimit,
              router.isRetainAutorouteDatabase() && !escaping);
      int maxItemIdBeforeRoute = router.board.communication.idGenerator.maxGeneratedId();
      // Strict DRC needs this snapshot to undo a rejected route together with the ripups it made,
      // so it is applied only when enabled explicitly: snapshotting every connection of every late
      // pass would serialize the whole board per connection.
      byte[] strictDrcBoardSnapshot = strictDrc ? router.board.serialize(false) : null;

      long mazeSearchStart = BatchAutorouter.isBenchmarkProfileEnabled() ? System.nanoTime() : 0;
      AutorouteAttemptResult searchResult;
      planeEscapeObstacles.forEach(obstacle -> obstacle.setPlaneEscapeObstacle(true));
      try {
        searchResult =
            autorouteEngine.autorouteConnection(
                routeStartSet, routeDestSet, autorouteControl, rippedItemList, ripupCosts);
      } finally {
        planeEscapeObstacles.forEach(obstacle -> obstacle.setPlaneEscapeObstacle(false));
        if (escaping) {
          router.board.finishAutoroute();
        }
      }
      final AutorouteAttemptResult autorouteResult = searchResult;
      if (BatchAutorouter.isBenchmarkProfileEnabled()) {
        router.addProfileMazeSearchNanos(System.nanoTime() - mazeSearchStart);
      }

      if (autorouteResult.state == AutorouteAttemptState.ROUTED) {
        int maxItemIdBeforeOpt = router.board.communication.idGenerator.maxGeneratedId();
        FRLogger.trace(
            "compare_trace_opt_changed_area_before net="
                + routeNetNo
                + ", maxItemId="
                + maxItemIdBeforeOpt);
        long pullTightStart = BatchAutorouter.isBenchmarkProfileEnabled() ? System.nanoTime() : 0;
        router.board.optChangedArea(
            new int[0],
            null,
            router.getTracePullTightAccuracy(),
            autorouteControl.traceCosts,
            router.thread);
        if (BatchAutorouter.isBenchmarkProfileEnabled()) {
          router.addProfileOptChangedAreaNanos(System.nanoTime() - pullTightStart);
        }
        int maxItemIdAfterOpt = router.board.communication.idGenerator.maxGeneratedId();
        FRLogger.trace(
            "compare_trace_opt_changed_area_after net="
                + routeNetNo
                + ", maxItemId="
                + maxItemIdAfterOpt
                + ", delta="
                + (maxItemIdAfterOpt - maxItemIdBeforeOpt));
      }

      if ((autorouteResult.state == AutorouteAttemptState.FAILED
              || autorouteResult.state == AutorouteAttemptState.INSERT_ERROR)
          && !escaping
          && router.settings.getNeckWidthUm() > 0) {
        AutorouteAttemptResult neckedResult =
            retryConnectionNecked(
                routeNetNo,
                autorouteControl,
                currentViaCosts,
                routeStartSet,
                routeDestSet,
                rippedItemList,
                ripupCosts,
                ripupPassNo,
                timeLimit,
                /* planned= */ false,
                /* quiet= */ false);
        if (neckedResult != null) {
          AutorouteAttemptResult strictResult =
              applyStrictDrcAfterRoute(
                  routeNetNo, maxItemIdBeforeRoute, strictDrcBoardSnapshot, ripupPassNo);
          if (strictResult != null) {
            return strictResult;
          }
          return neckedResult;
        }
      }

      if (autorouteResult.state == AutorouteAttemptState.ROUTED) {
        AutorouteAttemptResult strictResult =
            applyStrictDrcAfterRoute(
                routeNetNo, maxItemIdBeforeRoute, strictDrcBoardSnapshot, ripupPassNo);
        if (strictResult != null) {
          return strictResult;
        }
      }

      return autorouteResult;
    } catch (Exception e) {
      FRLogger.error("Error during routing passes", e);
      return new AutorouteAttemptResult(AutorouteAttemptState.FAILED);
    }
  }

  private AutorouteAttemptResult retryConnectionNecked(
      int routeNetNo,
      AutorouteControl originalControl,
      int viaCosts,
      Set<Item> routeStartSet,
      Set<Item> routeDestSet,
      SortedSet<Item> rippedItemList,
      Map<Item, Integer> ripupCosts,
      int ripupPassNo,
      TimeLimit timeLimit,
      boolean planned,
      boolean quiet) {
    int neckHalfWidth = AutorouteControl.neckHalfWidth(router.board, router.settings);
    boolean narrowerSomewhere = false;
    for (int i = 0; i < originalControl.layerCount; i++) {
      if (originalControl.layerActive[i] && originalControl.traceHalfWidth[i] > neckHalfWidth) {
        narrowerSomewhere = true;
        break;
      }
    }
    if (!narrowerSomewhere) {
      return null;
    }

    AutorouteControl neckControl =
        new AutorouteControl(
            router.board, routeNetNo, router.settings, viaCosts, router.getTraceCosts());
    neckControl.ripupAllowed = true;
    neckControl.ripupCosts = router.getStartRipupCosts() * ripupPassNo;
    neckControl.netsNotToRip = originalControl.netsNotToRip;
    neckControl.removeUnconnectedVias = router.isRemoveUnconnectedVias();
    for (int i = 0; i < neckControl.layerCount; i++) {
      int compensation = neckControl.compensatedTraceHalfWidth[i] - neckControl.traceHalfWidth[i];
      neckControl.traceHalfWidth[i] = Math.min(neckControl.traceHalfWidth[i], neckHalfWidth);
      neckControl.compensatedTraceHalfWidth[i] = neckControl.traceHalfWidth[i] + compensation;
      neckControl.updateTraceClearanceOffset(i, router.board);
    }

    AutorouteEngine neckEngine =
        router.board.initAutoroute(
            routeNetNo,
            neckControl.traceClearanceClassIndex,
            router.thread,
            timeLimit,
            router.isRetainAutorouteDatabase());
    long neckMazeSearchStart = BatchAutorouter.isBenchmarkProfileEnabled() ? System.nanoTime() : 0;
    AutorouteAttemptResult neckResult =
        neckEngine.autorouteConnection(
            routeStartSet, routeDestSet, neckControl, rippedItemList, ripupCosts);
    if (BatchAutorouter.isBenchmarkProfileEnabled()) {
      router.addProfileMazeSearchNanos(System.nanoTime() - neckMazeSearchStart);
    }
    if (neckResult.state != AutorouteAttemptState.ROUTED) {
      return planned ? neckResult : null;
    }

    router.neckedRouteNets.add(routeNetNo);

    long neckPullTightStart = BatchAutorouter.isBenchmarkProfileEnabled() ? System.nanoTime() : 0;
    router.board.optChangedArea(
        new int[0],
        null,
        router.getTracePullTightAccuracy(),
        neckControl.traceCosts,
        router.thread);
    if (BatchAutorouter.isBenchmarkProfileEnabled()) {
      router.addProfileOptChangedAreaNanos(System.nanoTime() - neckPullTightStart);
    }
    if (!quiet) {
      Net routeNet = router.board.rules.nets.get(routeNetNo);
      FRLogger.info(
          (planned ? "Retry plan routed net '" : "Necked retry routed net '")
              + (routeNet != null ? routeNet.name : "#" + routeNetNo)
              + "' at "
              + router.settings.getNeckWidthUm()
              + " um trace width.");
    }
    return neckResult;
  }

  /**
   * Rejects the route when its new items carry clearance violations and restores the board from the
   * snapshot taken before it. Without a snapshot the route is kept: removing only its new items
   * would leave the connections it ripped up unrouted.
   */
  private AutorouteAttemptResult applyStrictDrcAfterRoute(
      int routeNetNo, int maxItemIdBefore, byte[] boardSnapshotBeforeRoute, int ripupPassNo) {
    if (boardSnapshotBeforeRoute == null) {
      return null;
    }
    AutorouteAttemptResult rejection =
        BatchAutorouter.enforceStrictDrc(router.board, routeNetNo, maxItemIdBefore);
    if (rejection != null) {
      FRLogger.trace(
          "AutorouteConnectionRouter.apply_strict_drc",
          "strict_drc_rejection",
          "pass=" + ripupPassNo + ", net=" + routeNetNo + ", reason=" + rejection.details,
          "",
          null);
      ClearanceBaseline baseline = router.board.clearanceBaseline;
      router.board = (RoutingBoard) BasicBoard.deserialize(boardSnapshotBeforeRoute);
      router.board.clearanceBaseline = baseline;
    }
    return rejection;
  }
}
