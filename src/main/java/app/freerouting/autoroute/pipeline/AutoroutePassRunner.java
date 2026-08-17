package app.freerouting.autoroute.pipeline;

import static java.util.Collections.shuffle;

import app.freerouting.autoroute.AutorouteAttemptResult;
import app.freerouting.autoroute.AutorouteAttemptState;
import app.freerouting.autoroute.BoardHistory;
import app.freerouting.autoroute.PerformanceProfiler;
import app.freerouting.autoroute.events.BoardUpdatedEvent;
import app.freerouting.autoroute.events.BoardUpdatedEventListener;
import app.freerouting.autoroute.maze.AutorouteControl;
import app.freerouting.autoroute.pipeline.BatchAutorouterThread;
import app.freerouting.board.Item;
import app.freerouting.board.Pin;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Trace;
import app.freerouting.board.Via;
import app.freerouting.core.RouterCounters;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.Point;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/** Executes one single-threaded or multi-threaded autoroute pass. */
final class AutoroutePassRunner {

  private static final int TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP = 1000;

  private final BatchAutorouter router;

  AutoroutePassRunner(BatchAutorouter router) {
    this.router = router;
  }

  boolean runMultiThread(int passNo) {
    try {
      List<Item> autorouteItemList = router.getAutorouteItems(router.board);

      if (autorouteItemList.isEmpty()) {
        router.airLine = null;
        return false;
      }

      BatchAutorouterThread[] autorouterThreads =
          new BatchAutorouterThread[router.job.routerSettings.maxThreads];
      final BoardHistory boardHistory = new BoardHistory(router.job.routerSettings.scoring);

      for (int threadIndex = 0;
          threadIndex < router.job.routerSettings.maxThreads;
          threadIndex++) {
        PerformanceProfiler.start("board.deepCopy");
        RoutingBoard clonedBoard = router.board.deepCopy();
        PerformanceProfiler.end("board.deepCopy");

        List<Item> clonedAutorouteItemList =
            new ArrayList<>(router.getAutorouteItems(clonedBoard));
        shuffle(clonedAutorouteItemList, router.random);

        autorouterThreads[threadIndex] =
            new BatchAutorouterThread(
                clonedBoard,
                clonedAutorouteItemList,
                passNo,
                router.job.routerSettings,
                router.startRipupCosts,
                router.tracePullTightAccuracy,
                router.removeUnconnectedVias,
                true);
        autorouterThreads[threadIndex].setName(
            "Router thread #"
                + passNo
                + "."
                + router.threadIndexToLetter(threadIndex));
        autorouterThreads[threadIndex].setDaemon(true);
        autorouterThreads[threadIndex].setPriority(Thread.MIN_PRIORITY);
      }

      autorouterThreads[0].addBoardUpdatedEventListener(
          new BoardUpdatedEventListener() {
            @Override
            public void onBoardUpdatedEvent(BoardUpdatedEvent event) {
              router.airLine = autorouterThreads[0].latestAirLine;
              router.fireBoardUpdatedEvent(
                  event.getBoardStatistics(), event.getRouterCounters(), event.getBoard());
            }
          });

      for (BatchAutorouterThread autorouterThread : autorouterThreads) {
        autorouterThread.start();
      }

      for (int threadIndex = 0;
          threadIndex < router.job.routerSettings.maxThreads;
          threadIndex++) {
        BatchAutorouterThread autorouterThread = autorouterThreads[threadIndex];
        try {
          autorouterThread.join(TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP);
        } catch (InterruptedException e) {
          router.job.logError(
              "Autorouter thread #"
                  + passNo
                  + "."
                  + router.threadIndexToLetter(threadIndex)
                  + " was interrupted",
              e);
          router.thread.requestStop();
          break;
        }

        boardHistory.add(autorouterThread.getBoard());
        BoardStatistics clonedBoardStatistics = autorouterThread.getBoard().getStatistics();
        float clonedBoardScore =
            clonedBoardStatistics.getNormalizedScore(router.job.routerSettings.scoring);

        router.job.logDebug(
            "Router thread #"
                + passNo
                + "."
                + router.threadIndexToLetter(threadIndex)
                + " finished with score: "
                + FRLogger.formatScore(
                    clonedBoardScore,
                    clonedBoardStatistics.connections.incompleteCount,
                    clonedBoardStatistics.clearanceViolations.totalCount));

        router.job.resourceUsage.cpuTimeUsed += autorouterThread.cpuTimeUsed;
        router.job.resourceUsage.maxMemoryUsed += autorouterThread.maxMemoryUsed;
      }

      BatchAutorouterThread bestThread = autorouterThreads[0];
      float bestScore = -Float.MAX_VALUE;
      for (BatchAutorouterThread autorouterThread : autorouterThreads) {
        BoardStatistics stats = autorouterThread.getBoard().getStatistics();
        float score = stats.getNormalizedScore(router.job.routerSettings.scoring);
        if (score > bestScore) {
          bestScore = score;
          bestThread = autorouterThread;
        }
      }

      router.board = boardHistory.restoreBestBoard();
      boardHistory.clear();

      boolean anyProgress = bestThread.getRoutedCount() > 0 || bestThread.getFailedCount() > 0;
      router.airLine = null;
      return anyProgress;
    } catch (Exception e) {
      router.job.logError("Something went wrong during the auto-routing", e);
      router.airLine = null;
      return false;
    }
  }

  boolean runSingleThread(int passNo) {
    long passStartTime = System.currentTimeMillis();
    if (BatchAutorouter.isBenchmarkProfileEnabled()) {
      router.resetPassProfile();
    }
    try {
      long itemSelectionStart =
          BatchAutorouter.isBenchmarkProfileEnabled() ? System.nanoTime() : 0;
      List<Item> autorouteItemList = router.getAutorouteItems(router.board);
      if (BatchAutorouter.isBenchmarkProfileEnabled()) {
        router.profileItemSelectionNanos += System.nanoTime() - itemSelectionStart;
      }

      if (autorouteItemList.isEmpty()) {
        router.airLine = null;
        return false;
      }

      long initialProgressStatisticsStart =
          BatchAutorouter.isBenchmarkProfileEnabled() ? System.nanoTime() : 0;
      router.progressStatistics = new BoardStatistics(router.board, null, false);
      router.progressItemsSinceStatistics = 0;
      final BoardStatistics stats = router.progressStatistics;
      if (BatchAutorouter.isBenchmarkProfileEnabled()) {
        router.profileBoardStatisticsNanos +=
            System.nanoTime() - initialProgressStatisticsStart;
      }

      int itemsToGoCount = autorouteItemList.size();
      RouterCounters routerCounters = new RouterCounters();
      routerCounters.phase = "autoroute";
      routerCounters.passCount = passNo;
      routerCounters.queuedToBeRoutedCount = itemsToGoCount;
      routerCounters.skippedCount = 0;
      routerCounters.rippedCount = 0;
      routerCounters.failedToBeRoutedCount = 0;
      routerCounters.routedCount = 0;

      long statisticsStart =
          BatchAutorouter.isBenchmarkProfileEnabled() ? System.nanoTime() : 0;
      DesignRulesChecker tempDrc = new DesignRulesChecker(router.board, null);
      tempDrc.calculateAllIncompletes();
      routerCounters.incompleteCount = tempDrc.getIncompleteCount();
      if (BatchAutorouter.isBenchmarkProfileEnabled()) {
        router.profileIntermediateStatisticsNanos += System.nanoTime() - statisticsStart;
      }

      logIncompleteDetails(passNo, itemsToGoCount, routerCounters, tempDrc);
      router.fireBoardUpdatedEvent(stats, routerCounters, router.board);

      int rippedItemCount = 0;
      int notRouted = 0;
      int routed = 0;
      int skipped = 0;
      for (Item currentItem : autorouteItemList) {
        if (router.thread.isStopAutoRouterRequested()) {
          break;
        }

        for (int i = 0; i < currentItem.netCount(); i++) {
          if (router.thread.isStopAutoRouterRequested()) {
            break;
          }

          if (router.settings.maxItems != null
              && router.settings.maxItems > 0
              && router.totalItemsRouted >= router.settings.maxItems) {
            router.job.logInfo(
                "Max items limit reached ("
                    + router.settings.maxItems
                    + "). Stopping auto-router.");
            router.thread.requestStop();
            break;
          }
          router.totalItemsRouted++;
          router.board.startMarkingChangedArea();

          SortedSet<Item> rippedItemList = new TreeSet<>();
          Map<Item, Integer> rippedItemCosts = new LinkedHashMap<>();
          final int netItemsBefore =
              router.board.getConnectableItems(currentItem.getNetNumber(i)).size();
          if (BatchAutorouter.isBenchmarkProfileEnabled()) {
            router.profileRouteItemCount++;
            Net routeNet = router.board.rules.nets.get(currentItem.getNetNumber(i));
            if (routeNet != null && routeNet.containsPlane()) {
              router.profilePlaneItemCount++;
            }
          }

          long routeItemStart =
              BatchAutorouter.isBenchmarkProfileEnabled() ? System.nanoTime() : 0;
          PerformanceProfiler.start("autoroute_item");
          final AutorouteAttemptResult autorouterResult =
              router.autorouteItem(
                  currentItem,
                  currentItem.getNetNumber(i),
                  rippedItemList,
                  rippedItemCosts,
                  passNo);
          PerformanceProfiler.end("autoroute_item");
          if (BatchAutorouter.isBenchmarkProfileEnabled()) {
            router.profileAutorouteItemNanos += System.nanoTime() - routeItemStart;
          }

          logRippedItems(currentItem, i, rippedItemList, rippedItemCosts);
          if (FRLogger.isTraceEnabled()) {
            logTraceRouteComparison(
                currentItem, i, autorouterResult, rippedItemList, netItemsBefore);
          }
          if (currentItem.getNetNumber(i) == 94) {
            logNet94Items();
          }

          if (autorouterResult.state == AutorouteAttemptState.ROUTED) {
            ++routed;
          } else if ((autorouterResult.state == AutorouteAttemptState.ALREADY_CONNECTED)
              || (autorouterResult.state == AutorouteAttemptState.NO_UNCONNECTED_NETS)
              || (autorouterResult.state == AutorouteAttemptState.CONNECTED_TO_PLANE)) {
            ++skipped;
          } else {
            Net net = router.board.rules.nets.get(currentItem.getNetNumber(i));
            String netName = net != null ? net.name : "net#" + currentItem.getNetNumber(i);
            router.board.failureLog.recordFailure(
                currentItem, passNo, autorouterResult.state, autorouterResult.details);
            router.job.logDebug("Autorouter " + autorouterResult.details);
            int failureCount = router.board.failureLog.getFailureCount(currentItem);
            if (itemsToGoCount <= 5 || failureCount >= 3) {
              router.job.logDebug(
                  "Pass #"
                      + passNo
                      + ": Failed to route "
                      + currentItem.getClass().getSimpleName()
                      + " on net '"
                      + netName
                      + "' ("
                      + itemsToGoCount
                      + " items remaining, "
                      + failureCount
                      + " failures). State: "
                      + autorouterResult.state);
            }
            ++notRouted;
          }
          --itemsToGoCount;
          rippedItemCount += rippedItemList.size();
          updateProgress(
              routerCounters, itemsToGoCount, rippedItemCount, notRouted, routed, skipped);
        }
      }

      logTailRemoval(passNo, router.calculateIncompleteCount(router.board), true);
      if (router.removeUnconnectedVias) {
        router.removeTails(Item.StopConnectionOption.NONE);
      } else {
        router.removeTails(Item.StopConnectionOption.FANOUT_VIA);
      }
      logTailRemoval(passNo, router.calculateIncompleteCount(router.board), false);

      long finalStatisticsStart =
          BatchAutorouter.isBenchmarkProfileEnabled() ? System.nanoTime() : 0;
      long finalBoardStatisticsStart =
          BatchAutorouter.isBenchmarkProfileEnabled() ? System.nanoTime() : 0;
      final BoardStatistics boardStatistics = router.board.getStatistics();
      if (BatchAutorouter.isBenchmarkProfileEnabled()) {
        router.profileBoardStatisticsNanos +=
            System.nanoTime() - finalBoardStatisticsStart;
        router.profileIntermediateStatisticsNanos +=
            System.nanoTime() - finalStatisticsStart;
      }
      routerCounters.passCount = passNo;
      routerCounters.queuedToBeRoutedCount = itemsToGoCount;
      routerCounters.skippedCount = skipped;
      routerCounters.rippedCount = rippedItemCount;
      routerCounters.failedToBeRoutedCount = notRouted;
      routerCounters.routedCount = routed;
      routerCounters.incompleteCount = router.calculateIncompleteCount(router.board);
      router.fireBoardUpdatedEvent(boardStatistics, routerCounters, router.board);

      long passDuration = System.currentTimeMillis() - passStartTime;
      int currentRipupCost = router.startRipupCosts * passNo;
      PerformanceProfiler.recordPass(
          passNo, routerCounters.incompleteCount, passDuration, currentRipupCost);
      router.logBenchmarkProfile(passNo);

      router.airLine = null;
      return routed > 0 || notRouted > 0;
    } catch (Exception e) {
      router.job.logError("Something went wrong during the auto-routing", e);
      router.airLine = null;
      return false;
    }
  }

  private void logIncompleteDetails(
      int passNo, int itemsToGoCount, RouterCounters counters, DesignRulesChecker drc) {
    if (counters.incompleteCount <= 0) {
      return;
    }
    router.job.logDebug(
        "Pass #"
            + passNo
            + ": "
            + counters.incompleteCount
            + " incompletes across "
            + itemsToGoCount
            + " items to route");
    for (int netNumber = 1; netNumber <= router.board.rules.nets.maxNetNumber(); netNumber++) {
      int netIncompletes = drc.getIncompleteCount(netNumber);
      if (netIncompletes > 0) {
        Net net = router.board.rules.nets.get(netNumber);
        String netName = net != null ? net.name : "net#" + netNumber;
        router.job.logDebug("  Net '" + netName + "' has " + netIncompletes + " incomplete(s)");
      }
    }
  }

  private void logRippedItems(
      Item currentItem,
      int netIndex,
      SortedSet<Item> rippedItemList,
      Map<Item, Integer> rippedItemCosts) {
    for (Item rippedItem : rippedItemList) {
      StringBuilder rippedNets = new StringBuilder();
      for (int netIx = 0; netIx < rippedItem.netCount(); netIx++) {
        if (netIx > 0) {
          rippedNets.append('|');
        }
        rippedNets.append(rippedItem.getNetNumber(netIx));
      }
      int ripupCost = rippedItemCosts.getOrDefault(rippedItem, -1);
      FRLogger.trace(
          "BatchAutorouter.autoroute_pass",
          "compare_trace_ripped_item",
          "source_item="
              + currentItem.getId()
              + ", source_net="
              + currentItem.getNetNumber(netIndex)
              + ", ripped_id="
              + rippedItem.getId()
              + ", ripped_type="
              + rippedItem.getClass().getSimpleName()
              + ", ripped_net_count="
              + rippedItem.netCount()
              + ", ripped_nets="
              + rippedNets
              + ", ripupCost="
              + ripupCost,
          "Net #" + currentItem.getNetNumber(netIndex) + ",Item #" + currentItem.getId(),
          router.getImpactedPoints(rippedItem));
    }
  }

  private void logTraceRouteComparison(
      Item currentItem,
      int netIndex,
      AutorouteAttemptResult result,
      SortedSet<Item> rippedItemList,
      int netItemsBefore) {
    DesignRulesChecker innerDrc = new DesignRulesChecker(router.board, null);
    innerDrc.calculateAllIncompletes();
    int tempIncomp = innerDrc.getIncompleteCount();
    int tempNetIncomp = innerDrc.getIncompleteCount(currentItem.getNetNumber(netIndex));
    int netItemsAfter = router.board.getConnectableItems(currentItem.getNetNumber(netIndex)).size();
    int maxItemId = router.board.communication.idGenerator.maxGeneratedId();
    FRLogger.trace(
        "BatchAutorouter.autoroute_pass",
        "compare_trace_route_item",
        "Routing "
            + currentItem.getClass().getSimpleName()
            + " -> result="
            + result.state
            + ", details="
            + result.details
            + ", incompletes="
            + tempIncomp
            + ", netIncomplete="
            + tempNetIncomp
            + ", ripped="
            + rippedItemList.size()
            + ", netItems="
            + netItemsBefore
            + "->"
            + netItemsAfter
            + ", maxItemId="
            + maxItemId,
        "Net #"
            + currentItem.getNetNumber(netIndex)
            + ",Item #"
            + currentItem.getId()
            + ",Type="
            + currentItem.getClass().getSimpleName(),
        router.getImpactedPoints(currentItem));
  }

  private void logNet94Items() {
    FRLogger.trace(
        "BatchAutorouter.autoroute_pass",
        "compare_trace_dump_net_items",
        "Dump net 94 items",
        "Net #94",
        new Point[0]);
    for (Item netItem : router.board.getConnectableItems(94)) {
      if (netItem instanceof Trace trace) {
        FRLogger.trace(
            "BatchAutorouter.autoroute_pass",
            "compare_trace_dump_net_item",
            "Trace layer="
                + trace.getLayer()
                + " corners="
                + trace.firstCorner()
                + " to "
                + trace.lastCorner(),
            "Net #94,Item #" + trace.getId() + ",Type=Trace",
            new Point[] {trace.firstCorner(), trace.lastCorner()});
      } else if (netItem instanceof Via via) {
        FRLogger.trace(
            "BatchAutorouter.autoroute_pass",
            "compare_trace_dump_net_item",
            "Via center=" + via.getCenter(),
            "Net #94,Item #" + via.getId() + ",Type=Via",
            new Point[] {via.getCenter()});
      } else if (netItem instanceof Pin pin) {
        FRLogger.trace(
            "BatchAutorouter.autoroute_pass",
            "compare_trace_dump_net_item",
            "Pin center="
                + pin.getCenter()
                + " name="
                + pin.name()
                + " comp="
                + pin.componentName(),
            "Net #94,Item #" + pin.getId() + ",Type=Pin",
            new Point[] {pin.getCenter()});
      } else {
        FRLogger.trace(
            "BatchAutorouter.autoroute_pass",
            "compare_trace_dump_net_item",
            "Item " + netItem.getClass().getSimpleName(),
            "Net #94,Item #"
                + netItem.getId()
                + ",Type="
                + netItem.getClass().getSimpleName(),
            router.getImpactedPoints(netItem));
      }
    }
  }

  private void updateProgress(
      RouterCounters counters,
      int itemsToGoCount,
      int rippedItemCount,
      int notRouted,
      int routed,
      int skipped) {
    router.progressItemsSinceStatistics++;
    if (router.progressItemsSinceStatistics
        >= BatchAutorouter.PROGRESS_STATISTICS_ITEM_INTERVAL) {
      long progressStatisticsStart =
          BatchAutorouter.isBenchmarkProfileEnabled() ? System.nanoTime() : 0;
      router.progressStatistics = new BoardStatistics(router.board, null, false);
      router.progressItemsSinceStatistics = 0;
      if (BatchAutorouter.isBenchmarkProfileEnabled()) {
        router.profileBoardStatisticsNanos +=
            System.nanoTime() - progressStatisticsStart;
      }
    }

    if (router.shouldFireBoardUpdate()) {
      counters.queuedToBeRoutedCount = itemsToGoCount;
      counters.skippedCount = skipped;
      counters.rippedCount = rippedItemCount;
      counters.failedToBeRoutedCount = notRouted;
      counters.routedCount = routed;
      counters.incompleteCount = router.calculateIncompleteCount(router.board);
      router.fireBoardUpdatedEvent(router.progressStatistics, counters, router.board);
    }
  }

  private void logTailRemoval(int passNo, int incompleteCount, boolean before) {
    FRLogger.trace(
        "BatchAutorouter.autoroute_pass",
        "compare_trace_remove_tails",
        "Incompletes "
            + (before ? "before" : "after")
            + " remove_tails="
            + incompleteCount,
        "Autorouter pass #" + passNo,
        new Point[0]);
  }
}
