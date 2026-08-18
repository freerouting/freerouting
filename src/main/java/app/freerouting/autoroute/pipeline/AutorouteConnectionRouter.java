package app.freerouting.autoroute.pipeline;

import app.freerouting.autoroute.AutorouteAttemptResult;
import app.freerouting.autoroute.AutorouteAttemptState;
import app.freerouting.autoroute.maze.AutorouteControl;
import app.freerouting.autoroute.maze.AutorouteEngine;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

/** Executes one autoroute connection, including necked retry and strict-DRC recovery. */
final class AutorouteConnectionRouter {

  private static final int TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP = 1000;

  private final BatchAutorouter router;

  AutorouteConnectionRouter(BatchAutorouter router) {
    this.router = router;
  }

  AutorouteAttemptResult route(
      Item item,
      int routeNetNo,
      SortedSet<Item> rippedItemList,
      Map<Item, Integer> ripupCosts,
      int ripupPassNo) {
    try {
      Net routeNet = router.board.rules.nets.get(routeNetNo);
      boolean containsPlane = routeNet != null && routeNet.containsPlane();
      int currentViaCosts =
          containsPlane ? router.settings.getPlaneViaCosts() : router.settings.getViaCosts();

      AutorouteControl autorouteControl =
          new AutorouteControl(
              router.board, routeNetNo, router.settings, currentViaCosts, router.getTraceCosts());
      autorouteControl.ripupAllowed = true;
      autorouteControl.ripupCosts = router.getStartRipupCosts() * ripupPassNo;
      autorouteControl.removeUnconnectedVias = router.isRemoveUnconnectedVias();

      Set<Item> unconnectedSet = item.getUnconnectedSet(routeNetNo);
      if (unconnectedSet.isEmpty()) {
        return new AutorouteAttemptResult(AutorouteAttemptState.NO_UNCONNECTED_NETS);
      }

      Set<Item> connectedSet = item.getConnectedSet(routeNetNo);
      Set<Item> routeStartSet;
      Set<Item> routeDestSet;
      if (containsPlane) {
        for (Item currentItem : connectedSet) {
          if (currentItem instanceof ConductionArea) {
            return new AutorouteAttemptResult(AutorouteAttemptState.CONNECTED_TO_PLANE);
          }
        }
        routeStartSet = connectedSet;
        routeDestSet = unconnectedSet;
      } else {
        routeStartSet = unconnectedSet;
        routeDestSet = connectedSet;
      }

      router.setAirLine(AutorouteAirlineCalculator.calculateAirline(routeStartSet, routeDestSet));

      double maxMilliseconds = 100000 * Math.pow(2, ripupPassNo - 1);
      maxMilliseconds = Math.min(maxMilliseconds, Integer.MAX_VALUE);
      TimeLimit timeLimit = new TimeLimit((int) maxMilliseconds);

      AutorouteEngine autorouteEngine =
          router.board.initAutoroute(
              routeNetNo,
              autorouteControl.traceClearanceClassIndex,
              router.thread,
              timeLimit,
              router.isRetainAutorouteDatabase());
      int maxItemIdBeforeRoute = router.board.communication.idGenerator.maxGeneratedId();
      byte[] strictDrcBoardSnapshot =
          router.settings.isStrictDrc() ? router.board.serialize(false) : null;

      long mazeSearchStart = BatchAutorouter.isBenchmarkProfileEnabled() ? System.nanoTime() : 0;
      AutorouteAttemptResult autorouteResult =
          autorouteEngine.autorouteConnection(
              routeStartSet, routeDestSet, autorouteControl, rippedItemList, ripupCosts);
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
            router.thread,
            TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP);
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
                timeLimit);
        if (neckedResult != null) {
          AutorouteAttemptResult strictResult =
              applyStrictDrcAfterRoute(routeNetNo, maxItemIdBeforeRoute, strictDrcBoardSnapshot);
          if (strictResult != null) {
            return strictResult;
          }
          return neckedResult;
        }
      }

      if (autorouteResult.state == AutorouteAttemptState.ROUTED) {
        AutorouteAttemptResult strictResult =
            applyStrictDrcAfterRoute(routeNetNo, maxItemIdBeforeRoute, strictDrcBoardSnapshot);
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
      TimeLimit timeLimit) {
    int boardResolution = Math.max(1, router.board.communication.resolution);
    int neckWidth =
        (int)
            Math.round(
                Unit.scale(
                    router.settings.getNeckWidthUm() * boardResolution,
                    Unit.UM,
                    router.board.communication.unit));
    int neckHalfWidth = Math.max(1, neckWidth / 2);
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
    neckControl.removeUnconnectedVias = router.isRemoveUnconnectedVias();
    for (int i = 0; i < neckControl.layerCount; i++) {
      int compensation = neckControl.compensatedTraceHalfWidth[i] - neckControl.traceHalfWidth[i];
      neckControl.traceHalfWidth[i] = Math.min(neckControl.traceHalfWidth[i], neckHalfWidth);
      neckControl.compensatedTraceHalfWidth[i] = neckControl.traceHalfWidth[i] + compensation;
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
      return null;
    }

    long neckPullTightStart = BatchAutorouter.isBenchmarkProfileEnabled() ? System.nanoTime() : 0;
    router.board.optChangedArea(
        new int[0],
        null,
        router.getTracePullTightAccuracy(),
        neckControl.traceCosts,
        router.thread,
        TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP);
    if (BatchAutorouter.isBenchmarkProfileEnabled()) {
      router.addProfileOptChangedAreaNanos(System.nanoTime() - neckPullTightStart);
    }
    Net routeNet = router.board.rules.nets.get(routeNetNo);
    FRLogger.info(
        "Necked retry routed net '"
            + (routeNet != null ? routeNet.name : "#" + routeNetNo)
            + "' at "
            + router.settings.getNeckWidthUm()
            + " um trace width.");
    return neckResult;
  }

  private AutorouteAttemptResult applyStrictDrcAfterRoute(
      int routeNetNo, int maxItemIdBefore, byte[] boardSnapshotBeforeRoute) {
    if (!router.settings.isStrictDrc()) {
      return null;
    }
    AutorouteAttemptResult rejection =
        BatchAutorouter.enforceStrictDrc(router.board, routeNetNo, maxItemIdBefore);
    if (rejection != null && boardSnapshotBeforeRoute != null) {
      router.board = (RoutingBoard) BasicBoard.deserialize(boardSnapshotBeforeRoute);
    }
    return rejection;
  }
}
