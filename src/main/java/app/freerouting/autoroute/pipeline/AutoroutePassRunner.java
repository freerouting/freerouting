package app.freerouting.autoroute.pipeline;

import static java.util.Collections.shuffle;

import app.freerouting.autoroute.AutorouteAttemptResult;
import app.freerouting.autoroute.AutorouteAttemptState;
import app.freerouting.autoroute.DesignBlocks;
import app.freerouting.autoroute.PerformanceProfiler;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.board.searchtree.SearchTreeObject;
import app.freerouting.core.RouterCounters;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.drc.AirLine;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.Point;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/** Executes one autoroute pass on the calling thread. */
final class AutoroutePassRunner {

  private final BatchAutorouter router;
  private Set<Integer> previousIncompleteNets = Collections.emptySet();
  private int previousIncompleteCount = -1;
  private int stagnationCount = 0;

  AutoroutePassRunner(BatchAutorouter router) {
    this.router = router;
  }

  boolean runSingleThread(int passNo) {
    long passStartTime = System.currentTimeMillis();
    if (BatchAutorouter.isBenchmarkProfileEnabled()) {
      router.resetPassProfile();
    }
    try {
      long itemSelectionStart = BatchAutorouter.isBenchmarkProfileEnabled() ? System.nanoTime() : 0;
      List<Item> autorouteItemList = router.getAutorouteItems(router.board);
      dropDesignBlockedItems(autorouteItemList);
      if (BatchAutorouter.isBenchmarkProfileEnabled()) {
        router.profileItemSelectionNanos += System.nanoTime() - itemSelectionStart;
      }

      if (autorouteItemList.isEmpty()) {
        router.airLine = null;
        return false;
      }

      if (passNo > 1) {
        reorderSingleThreadItems(autorouteItemList, passNo);
      } else if (router.settings.autorouter != null
          && Boolean.TRUE.equals(router.settings.autorouter.planeAsPriority)) {
        // plane_as_priority: the plane pads drop into their planes before the signals take the
        // room around them (later passes already route plane items first).
        List<Item> planeItems = new ArrayList<>();
        autorouteItemList.removeIf(
            item -> router.isPlaneItem(item, router.board) && planeItems.add(item));
        autorouteItemList.addAll(0, planeItems);
      }
      priorityItemsFirst(autorouteItemList);

      // freerouting.net: the balls of ball grid parts first in the first pass, ring by ring
      // (router.autorouter.ball_escape_order); a retry plan still goes ahead of them.
      if (passNo == 1 && router.getBallEscapeOrder() != null) {
        int moved = router.getBallEscapeOrder().moveToFront(autorouteItemList);
        if (router.job != null) {
          router.job.logDebug("Pass #1: the ball escape order puts " + moved + " ball(s) first.");
        }
      }

      long initialProgressStatisticsStart =
          BatchAutorouter.isBenchmarkProfileEnabled() ? System.nanoTime() : 0;
      // The optimizer routes each candidate with a listener-less router: no one reads these.
      router.progressStatistics =
          router.hasBoardStatisticsReaders()
              ? new BoardStatistics(router.board, null, false)
              : null;
      router.progressItemsSinceStatistics = 0;
      final BoardStatistics stats = router.progressStatistics;
      if (BatchAutorouter.isBenchmarkProfileEnabled()) {
        router.profileBoardStatisticsNanos += System.nanoTime() - initialProgressStatisticsStart;
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

      long statisticsStart = BatchAutorouter.isBenchmarkProfileEnabled() ? System.nanoTime() : 0;
      // The ledger keeps these counts up to date; a full DRC scan would count every net again.
      routerCounters.incompleteCount = router.calculateIncompleteCount(router.board);
      if (BatchAutorouter.isBenchmarkProfileEnabled()) {
        router.profileIntermediateStatisticsNanos += System.nanoTime() - statisticsStart;
      }

      logIncompleteDetails(passNo, itemsToGoCount, routerCounters);
      router.fireBoardUpdatedEvent(stats, routerCounters, router.board);

      int rippedItemCount = 0;
      int notRouted = 0;
      int routed = 0;
      int skipped = 0;
      Map<Integer, IntBox> netCandidateBounds = buildPaddedNetBounds(autorouteItemList);
      Map<FailedConnectionPair, IntBox> zeroRipupFailedPairs = new LinkedHashMap<>();
      Set<Integer> keptAwayNetsAttemptedInPass = new TreeSet<>();
      Map<Integer, NetPassProfile> netProfiles =
          BatchAutorouter.isBenchmarkProfileEnabled() ? new LinkedHashMap<>() : null;
      List<ItemPassProfile> slowItemProfiles =
          BatchAutorouter.isBenchmarkProfileEnabled() ? new ArrayList<>() : null;
      for (Item currentItem : autorouteItemList) {
        if (router.thread.isStopAutoRouterRequested()) {
          break;
        }

        for (int i = 0; i < currentItem.netCount(); i++) {
          if (router.thread.isStopAutoRouterRequested()) {
            break;
          }

          if (router.settings.autorouter.maxItems != null
              && router.settings.autorouter.maxItems > 0
              && router.totalItemsRouted >= router.settings.autorouter.maxItems) {
            router.job.logInfo(
                "Max items limit reached ("
                    + router.settings.autorouter.maxItems
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

          long routeItemStart = BatchAutorouter.isBenchmarkProfileEnabled() ? System.nanoTime() : 0;
          PerformanceProfiler.start("autoroute_item");
          int currentNetNo = currentItem.getNetNumber(i);
          Net currentRouteNet = router.board.rules.nets.get(currentNetNo);
          FailedConnectionPair failedPairKey = null;
          if (currentRouteNet != null && !currentRouteNet.containsPlane()) {
            failedPairKey = FailedConnectionPair.of(currentItem, currentNetNo);
          }
          final AutorouteAttemptResult autorouterResult;
          if (failedPairKey != null && zeroRipupFailedPairs.containsKey(failedPairKey)) {
            autorouterResult =
                new AutorouteAttemptResult(
                    AutorouteAttemptState.FAILED,
                    "Skipped reverse attempt in same pass (pair already failed with 0 ripups).");
          } else {
            autorouterResult =
                router.autorouteItem(
                    currentItem, currentNetNo, rippedItemList, rippedItemCosts, passNo);
          }
          PerformanceProfiler.end("autoroute_item");
          if (!rippedItemList.isEmpty()) {
            invalidateZeroRipupPairs(zeroRipupFailedPairs, rippedItemList);
          } else if (failedPairKey != null
              && autorouterResult.state == AutorouteAttemptState.FAILED) {
            zeroRipupFailedPairs.put(failedPairKey, netCandidateBounds.get(currentNetNo));
          }
          if (BatchAutorouter.isBenchmarkProfileEnabled()) {
            long itemElapsedNanos = System.nanoTime() - routeItemStart;
            router.profileAutorouteItemNanos += itemElapsedNanos;
            int netNo = currentItem.getNetNumber(i);
            Net routeNet = router.board.rules.nets.get(netNo);
            String netName = routeNet != null ? routeNet.name : "net#" + netNo;
            NetPassProfile np =
                netProfiles.computeIfAbsent(netNo, k -> new NetPassProfile(netNo, netName));
            np.totalNanos += itemElapsedNanos;
            np.attempts++;
            np.ripped += rippedItemList.size();
            if (autorouterResult.state == AutorouteAttemptState.ROUTED) {
              np.routed++;
            } else if (autorouterResult.state == AutorouteAttemptState.FAILED
                || autorouterResult.state == AutorouteAttemptState.INSERT_ERROR) {
              np.failed++;
            } else {
              np.skipped++;
            }
            slowItemProfiles.add(
                new ItemPassProfile(
                    itemElapsedNanos,
                    netNo,
                    netName,
                    currentItem.getClass().getSimpleName(),
                    currentItem.getId(),
                    autorouterResult.state,
                    rippedItemList.size()));
          }

          if (router.ripupMemory.isKeptAway(currentItem.getNetNumber(i))) {
            keptAwayNetsAttemptedInPass.add(currentItem.getNetNumber(i));
          }
          logRippedItems(currentItem, i, rippedItemList, rippedItemCosts);
          rememberRipups(currentItem.getNetNumber(i), rippedItemList, passNo);
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
            int netNo = currentItem.getNetNumber(i);
            if (failureCount >= 2 && !designBlocked(router.board, currentItem, netNo)) {
              List<Item> tracesToRip = failedNetRipItems(router.board, currentItem, netNo);
              if (!tracesToRip.isEmpty()) {
                router.board.removeItems(tracesToRip);
                invalidateZeroRipupPairs(zeroRipupFailedPairs, tracesToRip);
              }
            }
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
      final BoardStatistics boardStatistics =
          router.hasBoardStatisticsReaders() ? router.board.getStatistics() : null;
      if (BatchAutorouter.isBenchmarkProfileEnabled()) {
        router.profileBoardStatisticsNanos += System.nanoTime() - finalBoardStatisticsStart;
        router.profileIntermediateStatisticsNanos += System.nanoTime() - finalStatisticsStart;
      }
      routerCounters.passCount = passNo;
      routerCounters.queuedToBeRoutedCount = itemsToGoCount;
      routerCounters.skippedCount = skipped;
      routerCounters.rippedCount = rippedItemCount;
      routerCounters.failedToBeRoutedCount = notRouted;
      routerCounters.routedCount = routed;
      Set<Integer> currentIncompleteNets = new TreeSet<>();
      routerCounters.incompleteCount =
          router.calculateIncompleteCount(router.board, currentIncompleteNets);
      if (!router.thread.isStopAutoRouterRequested()) {
        resolveKeptAwayOutcomes(
            keptAwayNetsAttemptedInPass, currentIncompleteNets, routerCounters, passNo);
      }
      router.fireBoardUpdatedEvent(boardStatistics, routerCounters, router.board);

      if (this.previousIncompleteCount >= 0
          && routerCounters.incompleteCount >= this.previousIncompleteCount) {
        this.stagnationCount++;
      } else {
        this.stagnationCount = 0;
      }
      this.previousIncompleteCount = routerCounters.incompleteCount;
      this.previousIncompleteNets = currentIncompleteNets;

      long passDuration = System.currentTimeMillis() - passStartTime;
      int currentRipupCost = router.startRipupCosts * passNo;
      PerformanceProfiler.recordPass(
          passNo, routerCounters.incompleteCount, passDuration, currentRipupCost);
      router.logBenchmarkProfile(passNo);
      if (BatchAutorouter.isBenchmarkProfileEnabled()) {
        logPassBreakdownProfile(passNo, netProfiles, slowItemProfiles);
      }

      router.airLine = null;
      return routed > 0 || notRouted > 0;
    } catch (Exception e) {
      router.job.logError("Something went wrong during the auto-routing", e);
      router.airLine = null;
      return false;
    }
  }

  static Map<Integer, IntBox> buildPaddedNetBounds(List<Item> autorouteItemList) {
    Map<Integer, IntBox> bounds = new LinkedHashMap<>();
    for (Item item : autorouteItemList) {
      IntBox box = item.boundingBox();
      for (int i = 0; i < item.netCount(); i++) {
        int netNo = item.getNetNumber(i);
        bounds.merge(netNo, box, IntBox::union);
      }
    }
    for (Map.Entry<Integer, IntBox> entry : bounds.entrySet()) {
      IntBox box = entry.getValue();
      int pad = Math.max(100_000, Math.max(box.width(), box.height()) / 2);
      entry.setValue(box.offset(pad));
    }
    return bounds;
  }

  static void invalidateZeroRipupPairs(
      Map<FailedConnectionPair, IntBox> zeroRipupFailedPairs,
      java.util.Collection<Item> rippedItems) {
    if (zeroRipupFailedPairs.isEmpty() || rippedItems.isEmpty()) {
      return;
    }
    zeroRipupFailedPairs
        .entrySet()
        .removeIf(
            entry -> {
              IntBox region = entry.getValue();
              if (region == null) {
                return true;
              }
              for (Item ripped : rippedItems) {
                if (ripped.boundingBox().intersects(region)) {
                  return true;
                }
              }
              return false;
            });
  }

  static int componentPinCount(RoutingBoard board, Item item) {
    int componentId = item.getComponentId();
    if (componentId <= 0 || board == null || board.components == null) {
      return 0;
    }
    var component = board.components.get(componentId);
    if (component == null || component.getPackage() == null) {
      return 0;
    }
    return component.getPackage().pinCount();
  }

  static Comparator<Item> deterministicItemComparator(RoutingBoard board) {
    return Comparator.comparingInt((Item item) -> item.netCount() > 0 ? item.getNetNumber(0) : 0)
        .thenComparing(
            Comparator.comparingInt((Item item) -> componentPinCount(board, item)).reversed())
        .thenComparingInt(Item::getId);
  }

  private void reorderSingleThreadItems(List<Item> autorouteItemList, int passNo) {
    List<Item> planeItems = new ArrayList<>();
    List<Item> signalItems = new ArrayList<>();
    for (Item item : autorouteItemList) {
      if (router.isPlaneItem(item, router.board)) {
        planeItems.add(item);
      } else {
        signalItems.add(item);
      }
    }

    if (signalItems.isEmpty()) {
      return;
    }

    Comparator<Item> deterministicItemComparator = deterministicItemComparator(router.board);

    Integer orderSeed =
        router.settings.autorouter != null ? router.settings.autorouter.itemOrderSeed : null;
    if (this.stagnationCount >= 2 || orderSeed != null) {
      signalItems.sort(deterministicItemComparator);
      long seed = (long) passNo * 1000003L + router.board.rules.nets.maxNetNumber();
      if (orderSeed != null) {
        seed = seed * 31L + orderSeed;
      }
      shuffle(signalItems, new Random(seed));
      router.job.logDebug(
          "Pass #"
              + passNo
              + ": applied deterministic permutation to "
              + signalItems.size()
              + " signal items (stagnation count: "
              + this.stagnationCount
              + ").");
    } else if (!this.previousIncompleteNets.isEmpty()) {
      List<Item> persistentItems = new ArrayList<>();
      List<Item> otherItems = new ArrayList<>();
      for (Item item : signalItems) {
        boolean isPersistent = false;
        for (int i = 0; i < item.netCount(); i++) {
          if (this.previousIncompleteNets.contains(item.getNetNumber(i))) {
            isPersistent = true;
            break;
          }
        }
        if (isPersistent) {
          persistentItems.add(item);
        } else {
          otherItems.add(item);
        }
      }
      persistentItems.sort(deterministicItemComparator);
      otherItems.sort(deterministicItemComparator);
      signalItems.clear();
      signalItems.addAll(persistentItems);
      signalItems.addAll(otherItems);
    } else {
      signalItems.sort(deterministicItemComparator);
    }

    keptAwayNetsLast(signalItems);
    autorouteItemList.clear();
    autorouteItemList.addAll(planeItems);
    autorouteItemList.addAll(signalItems);
  }

  /**
   * Moves the items of the priority net classes (router.autorouter.priority_net_classes) ahead of
   * the other signal items, after the plane items, keeping the order within each group. Nothing
   * moves on a board without priority classes.
   */
  private void priorityItemsFirst(List<Item> autorouteItemList) {
    List<Item> planeItems = new ArrayList<>();
    List<Item> priorityItems = new ArrayList<>();
    List<Item> otherItems = new ArrayList<>();
    for (Item item : autorouteItemList) {
      if (router.isPlaneItem(item, router.board)) {
        planeItems.add(item);
      } else if (isRoutedFirst(item)) {
        priorityItems.add(item);
      } else {
        otherItems.add(item);
      }
    }
    if (priorityItems.isEmpty()) {
      return;
    }
    autorouteItemList.clear();
    autorouteItemList.addAll(planeItems);
    autorouteItemList.addAll(priorityItems);
    autorouteItemList.addAll(otherItems);
  }

  private boolean isRoutedFirst(Item item) {
    for (int i = 0; i < item.netCount(); i++) {
      Net net = router.board.rules.nets.get(item.getNetNumber(i));
      if (net != null && net.isRoutedFirst()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Moves the items of nets that the rip-up memory keeps away from other nets to the end of the
   * pass, keeping the order otherwise: the nets they were moved for route first.
   */
  private void keptAwayNetsLast(List<Item> signalItems) {
    if (router.ripupMemory.activeCount() == 0) {
      return;
    }
    List<Item> keptAway = new ArrayList<>();
    signalItems.removeIf(
        item -> {
          for (int i = 0; i < item.netCount(); i++) {
            if (router.ripupMemory.isKeptAway(item.getNetNumber(i))) {
              keptAway.add(item);
              return true;
            }
          }
          return false;
        });
    signalItems.addAll(keptAway);
  }

  /** Feeds the rip-up memory: which nets this attempt moved aside. */
  private void rememberRipups(int netNo, SortedSet<Item> rippedItemList, int passNo) {
    if (!rippedItemList.isEmpty()) {
      Set<Integer> started = router.ripupMemory.recordRipup(netNo, rippedItemList, passNo);
      if (!started.isEmpty()) {
        logDebug(
            passNo,
            "net '"
                + netName(netNo)
                + "' moved "
                + netNames(started)
                + " aside a second time; they will not rip it back.");
      }
    }
  }

  /**
   * Evaluates the outcome for nets that were kept away from other nets during this pass. Only after
   * all connections for the net have succeeded in the pass is the relation resolved. When a
   * kept-away net keeps failing, the relation flips and the other net's copper in the way of the
   * failed connections is removed, so that the failed net routes first and the other one looks for
   * another way.
   */
  void resolveKeptAwayOutcomes(
      Set<Integer> keptAwayNetsAttempted,
      Set<Integer> incompleteNets,
      RouterCounters routerCounters,
      int passNo) {
    if (keptAwayNetsAttempted.isEmpty()) {
      return;
    }
    int itemsRemovedByFlip = 0;
    for (int netNo : keptAwayNetsAttempted) {
      Set<Integer> keptAwayFrom = router.ripupMemory.netsNotToRip(netNo);
      if (keptAwayFrom.isEmpty()) {
        continue;
      }
      boolean routed = !incompleteNets.contains(netNo);
      for (int keptFromNet : new ArrayList<>(keptAwayFrom)) {
        RipupMemory.Outcome outcome =
            router.ripupMemory.recordOutcome(netNo, keptFromNet, routed, passNo);
        String message =
            switch (outcome) {
              case RESOLVED -> "routed without ripping '" + netName(keptFromNet) + "'.";
              case FLIPPED -> {
                int ripped = ripInCorridors(keptFromNet, netNo);
                itemsRemovedByFlip += ripped;
                yield "failed again without ripping '"
                    + netName(keptFromNet)
                    + "'; "
                    + ripped
                    + " item(s) of it removed from its way, '"
                    + netName(keptFromNet)
                    + "' is now kept away from it.";
              }
              case RETIRED ->
                  "failed again while '"
                      + netName(keptFromNet)
                      + "' was kept away; the pair is released.";
              case NONE -> "failed without ripping '" + netName(keptFromNet) + "'.";
            };
        logDebug(passNo, "net '" + netName(netNo) + "' " + message);
      }
    }
    if (itemsRemovedByFlip > 0) {
      routerCounters.incompleteCount = router.calculateIncompleteCount(router.board);
    }
  }

  /**
   * Removes the unfixed traces and vias of {@code netNo} that lie across the open connections of
   * {@code failedNetNo}. Returns the number of items removed.
   */
  private int ripInCorridors(int netNo, int failedNetNo) {
    AirLine[] airlines = new DesignRulesChecker(router.board, null).getAllAirlines();
    Set<Item> inTheWay = new TreeSet<>();
    for (AirLine airline : airlines) {
      if (airline == null || airline.net == null || airline.net.netNumber != failedNetNo) {
        continue;
      }
      IntBox corridor = LastMileBlockerRipup.corridor(airline.fromCorner, airline.toCorner);
      for (SearchTreeObject object : router.board.overlappingObjects(corridor, -1)) {
        if (object instanceof Item item
            && item.containsNet(netNo)
            && LastMileBlockerRipup.isRemovableBlocker(item, failedNetNo)) {
          inTheWay.add(item);
        }
      }
    }
    if (!inTheWay.isEmpty()) {
      router.board.removeItems(new ArrayList<>(inTheWay));
    }
    return inTheWay.size();
  }

  private void logDebug(int passNo, String message) {
    if (router.job != null) {
      router.job.logDebug("Pass #" + passNo + ": " + message);
    }
  }

  private String netName(int netNo) {
    Net net = router.board.rules.nets.get(netNo);
    return net != null ? net.name : "net#" + netNo;
  }

  private String netNames(Set<Integer> netNumbers) {
    StringBuilder names = new StringBuilder();
    for (int netNo : netNumbers) {
      if (names.length() > 0) {
        names.append(", ");
      }
      names.append('\'').append(netName(netNo)).append('\'');
    }
    return names.toString();
  }

  void resetAntiOscillationState() {
    this.previousIncompleteNets = Collections.emptySet();
    this.previousIncompleteCount = -1;
    this.stagnationCount = 0;
  }

  private void logIncompleteDetails(int passNo, int itemsToGoCount, RouterCounters counters) {
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
      int netIncompletes = router.board.routingLedger().incompleteCount(netNumber);
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
            "Net #94,Item #" + netItem.getId() + ",Type=" + netItem.getClass().getSimpleName(),
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
    if (router.progressItemsSinceStatistics >= BatchAutorouter.PROGRESS_STATISTICS_ITEM_INTERVAL
        && router.hasBoardStatisticsReaders()) {
      long progressStatisticsStart =
          BatchAutorouter.isBenchmarkProfileEnabled() ? System.nanoTime() : 0;
      router.progressStatistics = new BoardStatistics(router.board, null, false);
      router.progressItemsSinceStatistics = 0;
      if (BatchAutorouter.isBenchmarkProfileEnabled()) {
        router.profileBoardStatisticsNanos += System.nanoTime() - progressStatisticsStart;
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

  private void dropDesignBlockedItems(List<Item> items) {
    int before = items.size();
    Set<Integer> ruleBlockedNets = ruleBlockedNets(router.board);
    items.removeIf(
        item ->
            (router.board.failureLog.getFailureCount(item) > 0
                    || (ruleBlockedNets != null
                        && item.netCount() == 1
                        && ruleBlockedNets.contains(item.getNetNumber(0))))
                && designBlocked(router.board, item, item.getNetNumber(0)));
    if (items.size() < before && router.job != null) {
      router.job.logDebug(
          "Not retrying "
              + (before - items.size())
              + " item(s) whose open connections the design blocks (pins on foreign copper).");
    }
  }

  static Set<Integer> ruleBlockedNets(RoutingBoard board) {
    if (board.ruleBlockedPins == null || board.ruleBlockedPins.isEmpty()) {
      return null;
    }
    Set<Integer> nets = new HashSet<>();
    for (Pin pin : board.getPins()) {
      if (pin.netCount() == 1 && board.ruleBlockedPins.contains(pin.getId())) {
        nets.add(pin.getNetNumber(0));
      }
    }
    return nets.isEmpty() ? null : nets;
  }

  static boolean designBlocked(RoutingBoard board, Item item, int netNumber) {
    return item.netCount() == 1 && DesignBlocks.connectionsBlocked(board, item, netNumber);
  }

  static List<Item> failedNetRipItems(RoutingBoard board, Item failedItem, int netNumber) {
    Collection<Item> items = board.getConnectableItems(netNumber);
    long pinCount = items.stream().filter(i -> i instanceof Pin).count();
    boolean branched = pinCount > 2;
    int offset =
        (int)
            Math.round(
                board.communication.coordinateTransform.dsnToBoard(
                    Unit.scale(1500, Unit.UM, board.communication.unit)));
    IntBox region = failedItem.boundingBox().offset(offset);
    List<Item> result = new ArrayList<>();
    for (Item i : items) {
      if ((i instanceof Trace || i instanceof Via)
          && !i.isUserFixed()
          && i.netCount() == 1
          && (!branched || region.intersects(i.boundingBox()))) {
        result.add(i);
      }
    }
    return result;
  }

  private void logTailRemoval(int passNo, int incompleteCount, boolean before) {
    FRLogger.trace(
        "BatchAutorouter.autoroute_pass",
        "compare_trace_remove_tails",
        "Incompletes " + (before ? "before" : "after") + " remove_tails=" + incompleteCount,
        "Autorouter pass #" + passNo,
        new Point[0]);
  }

  private void logPassBreakdownProfile(
      int passNo,
      Map<Integer, NetPassProfile> netProfiles,
      List<ItemPassProfile> slowItemProfiles) {
    if (netProfiles != null && !netProfiles.isEmpty()) {
      List<NetPassProfile> sortedNets = new ArrayList<>(netProfiles.values());
      sortedNets.sort(Comparator.comparingLong((NetPassProfile n) -> n.totalNanos).reversed());
      StringBuilder sb = new StringBuilder();
      sb.append("BENCHMARK_PROFILE_NETS pass=").append(passNo);
      int limit = Math.min(10, sortedNets.size());
      for (int idx = 0; idx < limit; idx++) {
        NetPassProfile np = sortedNets.get(idx);
        sb.append(
            String.format(
                java.util.Locale.US,
                " | #%d net=%s(%d) ms=%.1f att=%d ok=%d fail=%d skip=%d rip=%d",
                idx + 1,
                np.netName,
                np.netNo,
                np.totalNanos / 1_000_000.0,
                np.attempts,
                np.routed,
                np.failed,
                np.skipped,
                np.ripped));
      }
      FRLogger.info(sb.toString());
    }
    if (slowItemProfiles != null && !slowItemProfiles.isEmpty()) {
      slowItemProfiles.sort(
          Comparator.comparingLong((ItemPassProfile p) -> p.elapsedNanos).reversed());
      StringBuilder sb = new StringBuilder();
      sb.append("BENCHMARK_PROFILE_SLOW_ITEMS pass=").append(passNo);
      int limit = Math.min(10, slowItemProfiles.size());
      for (int idx = 0; idx < limit; idx++) {
        ItemPassProfile ip = slowItemProfiles.get(idx);
        sb.append(
            String.format(
                java.util.Locale.US,
                " | #%d %s#%d net=%s(%d) ms=%.1f state=%s rip=%d",
                idx + 1,
                ip.itemType,
                ip.itemId,
                ip.netName,
                ip.netNo,
                ip.elapsedNanos / 1_000_000.0,
                ip.state,
                ip.ripped));
      }
      FRLogger.info(sb.toString());
    }
  }

  private static final class NetPassProfile {
    final int netNo;
    final String netName;
    long totalNanos;
    int attempts;
    int routed;
    int failed;
    int skipped;
    int ripped;

    NetPassProfile(int netNo, String netName) {
      this.netNo = netNo;
      this.netName = netName;
    }
  }

  private static final class ItemPassProfile {
    final long elapsedNanos;
    final int netNo;
    final String netName;
    final String itemType;
    final int itemId;
    final AutorouteAttemptState state;
    final int ripped;

    ItemPassProfile(
        long elapsedNanos,
        int netNo,
        String netName,
        String itemType,
        int itemId,
        AutorouteAttemptState state,
        int ripped) {
      this.elapsedNanos = elapsedNanos;
      this.netNo = netNo;
      this.netName = netName;
      this.itemType = itemType;
      this.itemId = itemId;
      this.state = state;
      this.ripped = ripped;
    }
  }

  record FailedConnectionPair(int netNo, Set<Integer> sideA, Set<Integer> sideB) {
    static FailedConnectionPair of(Item item, int netNo) {
      Set<Item> connected = item.getConnectedSet(netNo);
      Set<Item> unconnected = item.getUnconnectedSet(netNo);
      if (connected.isEmpty() || unconnected.isEmpty()) {
        return null;
      }
      Set<Integer> a = new TreeSet<>();
      for (Item it : connected) {
        a.add(it.getId());
      }
      Set<Integer> b = new TreeSet<>();
      for (Item it : unconnected) {
        b.add(it.getId());
      }
      int minA = a.iterator().next();
      int minB = b.iterator().next();
      return minA <= minB
          ? new FailedConnectionPair(netNo, a, b)
          : new FailedConnectionPair(netNo, b, a);
    }
  }
}
