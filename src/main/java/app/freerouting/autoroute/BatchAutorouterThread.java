package app.freerouting.autoroute;

import app.freerouting.autoroute.events.BoardUpdatedEvent;
import app.freerouting.autoroute.events.BoardUpdatedEventListener;
import app.freerouting.board.ConductionArea;
import app.freerouting.board.DrillItem;
import app.freerouting.board.Item;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.RoutingBoard;
import app.freerouting.core.ProgressThrottler;
import app.freerouting.core.RouterCounters;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import app.freerouting.settings.RouterSettings;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/** Handles the sequencing of the auto-router passes. */
public class BatchAutorouterThread extends StoppableThread {

  private static final int TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP = 1000;

  protected final transient List<BoardUpdatedEventListener> boardUpdatedEventListeners =
      new ArrayList<>();

  private final ProgressThrottler progressThrottler = new ProgressThrottler(1000);
  private final RoutingBoard board;
  private final boolean removeUnconnectedVias;
  private final AutorouteControl.ExpansionCostFactor[] traceCostArr;
  private final boolean retainAutorouteDatabase;
  private final int startRipupCosts;
  private final int tracePullTightAccuracy;
  private final RouterSettings settings;
  private final List<Item> autorouteItemList;
  private final int passNo;

  public FloatLine latestAirLine;
  public float cpuTimeUsed = 0.0F;
  public float maxMemoryUsed = 0.0F;
  private int routedCount;
  private int failedCount;

  /** Constructs a BatchAutorouterThread for running an autorouting pass. */
  public BatchAutorouterThread(
      RoutingBoard board,
      List<Item> autorouteItemList,
      int passNo,
      RouterSettings routerSettings,
      int startRipupCosts,
      int tracePullTightAccuracy,
      boolean removeUnconnectedVias,
      boolean withPreferredDirections) {
    this.board = board;
    this.settings = routerSettings;
    this.autorouteItemList = autorouteItemList;
    this.passNo = passNo;

    this.removeUnconnectedVias = removeUnconnectedVias;
    if (withPreferredDirections) {
      this.traceCostArr = this.settings.getTraceCostArr();
    } else {
      // remove preferred direction
      this.traceCostArr = new AutorouteControl.ExpansionCostFactor[this.board.getLayerCount()];
      for (int i = 0; i < this.traceCostArr.length; i++) {
        double currentMinCost = this.settings.getPreferredDirectionTraceCosts(i);
        this.traceCostArr[i] =
            new AutorouteControl.ExpansionCostFactor(currentMinCost, currentMinCost);
      }
    }

    this.startRipupCosts = startRipupCosts;
    this.tracePullTightAccuracy = tracePullTightAccuracy;
    this.retainAutorouteDatabase = false;
  }

  // Calculates the shortest distance between two sets of items, specifically
  // between Pin and Via items (pins and vias are connectable DrillItems)
  private static FloatLine calcAirline(Collection<Item> fromItems, Collection<Item> toItems) {
    FloatPoint fromCorner = null;
    FloatPoint toCorner = null;
    double minDistance = Double.MAX_VALUE;
    for (Item currentFromItem : fromItems) {
      FloatPoint currentFromCorner;
      if (currentFromItem instanceof DrillItem item) {
        currentFromCorner = item.getCenter().toFloat();
      } else if (currentFromItem instanceof PolylineTrace fromTrace) {
        // Use trace endpoints as potential connection points
        continue; // We'll handle traces in the second loop for better efficiency
      } else {
        continue;
      }

      for (Item currentToItem : toItems) {
        FloatPoint currentToCorner;
        if (currentToItem instanceof DrillItem drillItem) {
          currentToCorner = drillItem.getCenter().toFloat();
        } else if (currentToItem instanceof PolylineTrace toTrace) {
          // Find nearest point on trace to the from item point
          currentToCorner = nearestPointOnTrace(toTrace, currentFromCorner);
        } else {
          continue;
        }

        double currentDistance = currentFromCorner.distance(currentToCorner);
        if (currentDistance < minDistance) {
          minDistance = currentDistance;
          fromCorner = currentFromCorner;
          toCorner = currentToCorner;
        }
      }
    }

    // Check trace-to-trace and trace-to-drill connections
    for (Item currentFromItem : fromItems) {
      if (!(currentFromItem instanceof PolylineTrace fromTrace)) {
        continue;
      }

      for (Item currentToItem : toItems) {
        FloatPoint currentFromCorner;
        FloatPoint currentToCorner;

        if (currentToItem instanceof DrillItem item) {
          // Trace to drill item
          currentToCorner = item.getCenter().toFloat();
          currentFromCorner = nearestPointOnTrace(fromTrace, currentToCorner);
        } else if (currentToItem instanceof PolylineTrace toTrace) {
          // Trace to trace - find the closest points between the two traces
          FloatPoint[] closestPoints = findClosestPointsBetweenTraces(fromTrace, toTrace);
          currentFromCorner = closestPoints[0];
          currentToCorner = closestPoints[1];
        } else {
          continue;
        }

        double currentDistance = currentFromCorner.distance(currentToCorner);
        if (currentDistance < minDistance) {
          minDistance = currentDistance;
          fromCorner = currentFromCorner;
          toCorner = currentToCorner;
        }
      }
    }

    if (fromCorner != null && toCorner != null) {
      return new FloatLine(fromCorner, toCorner);
    } else {
      return null;
    }
  }

  /** Finds the nearest point on a trace to the given point. */
  private static FloatPoint nearestPointOnTrace(PolylineTrace trace, FloatPoint point) {
    double minDistance = Double.MAX_VALUE;
    FloatPoint nearestPoint = null;

    // Get endpoints
    FloatPoint firstCorner = trace.firstCorner().toFloat();
    FloatPoint lastCorner = trace.lastCorner().toFloat();

    // Check distance to endpoints first
    double distanceToFirst = point.distance(firstCorner);
    double distanceToLast = point.distance(lastCorner);

    if (distanceToFirst < minDistance) {
      minDistance = distanceToFirst;
      nearestPoint = firstCorner;
    }

    if (distanceToLast < minDistance) {
      minDistance = distanceToLast;
      nearestPoint = lastCorner;
    }

    // Check distances to line segments
    for (int i = 0; i < trace.cornerCount() - 1; i++) {
      FloatPoint segmentStart = trace.polyline().cornerApprox(i);
      FloatPoint segmentEnd = trace.polyline().cornerApprox(i + 1);
      FloatLine segment = new FloatLine(segmentStart, segmentEnd);

      FloatPoint projection = segment.perpendicularProjection(point);
      if (projection.isContainedInBox(segmentStart, segmentEnd, 0.01)) {
        double distance = point.distance(projection);
        if (distance < minDistance) {
          minDistance = distance;
          nearestPoint = projection;
        }
      }
    }

    return nearestPoint;
  }

  /**
   * Finds the closest points between two traces.
   *
   * @return an array with two FloatPoints: [point_on_first_trace, point_on_second_trace]
   */
  private static FloatPoint[] findClosestPointsBetweenTraces(
      PolylineTrace firstTrace, PolylineTrace secondTrace) {
    double minDistance = Double.MAX_VALUE;
    FloatPoint[] result = new FloatPoint[2];

    // Check endpoints to endpoints
    FloatPoint firstTraceStart = firstTrace.firstCorner().toFloat();
    final FloatPoint firstTraceEnd = firstTrace.lastCorner().toFloat();
    FloatPoint secondTraceStart = secondTrace.firstCorner().toFloat();
    FloatPoint secondTraceEnd = secondTrace.lastCorner().toFloat();

    // Check all endpoint combinations
    double distance = firstTraceStart.distance(secondTraceStart);
    if (distance < minDistance) {
      minDistance = distance;
      result[0] = firstTraceStart;
      result[1] = secondTraceStart;
    }

    distance = firstTraceStart.distance(secondTraceEnd);
    if (distance < minDistance) {
      minDistance = distance;
      result[0] = firstTraceStart;
      result[1] = secondTraceEnd;
    }

    distance = firstTraceEnd.distance(secondTraceStart);
    if (distance < minDistance) {
      minDistance = distance;
      result[0] = firstTraceEnd;
      result[1] = secondTraceStart;
    }

    distance = firstTraceEnd.distance(secondTraceEnd);
    if (distance < minDistance) {
      minDistance = distance;
      result[0] = firstTraceEnd;
      result[1] = secondTraceEnd;
    }

    // Check all segment combinations for closest points
    for (int i = 0; i < firstTrace.cornerCount() - 1; i++) {
      FloatPoint firstSegmentStart = firstTrace.polyline().cornerApprox(i);
      FloatPoint firstSegmentEnd = firstTrace.polyline().cornerApprox(i + 1);
      FloatLine firstSegment = new FloatLine(firstSegmentStart, firstSegmentEnd);

      for (int j = 0; j < secondTrace.cornerCount() - 1; j++) {
        FloatPoint secondSegmentStart = secondTrace.polyline().cornerApprox(j);
        FloatPoint secondSegmentEnd = secondTrace.polyline().cornerApprox(j + 1);
        FloatLine secondSegment = new FloatLine(secondSegmentStart, secondSegmentEnd);

        // Find closest points between these two line segments
        FloatPoint pointOnFirst = firstSegment.nearestSegmentPoint(secondSegmentStart);
        FloatPoint pointOnSecond = secondSegment.perpendicularProjection(pointOnFirst);

        // Check if projection is on the segment
        if (!pointOnSecond.isContainedInBox(secondSegmentStart, secondSegmentEnd, 0.01)) {
          // If not, use the nearest endpoint
          double distToStart = pointOnFirst.distance(secondSegmentStart);
          double distToEnd = pointOnFirst.distance(secondSegmentEnd);
          pointOnSecond = distToStart < distToEnd ? secondSegmentStart : secondSegmentEnd;
        }

        // Recalculate the point on first segment based on the point on second segment
        pointOnFirst = firstSegment.nearestSegmentPoint(pointOnSecond);

        distance = pointOnFirst.distance(pointOnSecond);
        if (distance < minDistance) {
          minDistance = distance;
          result[0] = pointOnFirst;
          result[1] = pointOnSecond;
        }
      }
    }

    return result;
  }

  private RoutingBoard autorouteItems() {
    int itemsToGoCount = autorouteItemList.size();

    final BoardStatistics stats = board.getStatistics();
    RouterCounters routerCounters = new RouterCounters();
    routerCounters.passCount = passNo;
    routerCounters.queuedToBeRoutedCount = itemsToGoCount;
    routerCounters.skippedCount = 0;
    routerCounters.rippedCount = 0;
    routerCounters.failedToBeRoutedCount = 0;
    routerCounters.routedCount = 0;
    DesignRulesChecker drc = new DesignRulesChecker(board, null);
    drc.calculateAllIncompletes();
    routerCounters.incompleteCount = drc.getIncompleteCount();

    progressThrottler.reset();
    this.fireBoardUpdatedEvent(stats, routerCounters, board);

    int rippedItemCount = 0;
    int notRouted = 0;
    int routed = 0;
    int skipped = 0;
    // Let's go through all items to route
    for (Item currentItem : autorouteItemList) {
      // If the user requested to stop the auto-router, we stop it
      if (this.isStopAutoRouterRequested()) {
        break;
      }

      // Check if this item should be skipped due to repeated failures
      if (this.board.failureLog.shouldSkip(currentItem)) {
        Net net = board.rules.nets.get(currentItem.getNetNo(0));
        String netName = net != null ? net.name : "net#" + currentItem.getNetNo(0);
        FRLogger.debug(
            "Skipping "
                + currentItem.getClass().getSimpleName()
                + " on net '"
                + netName
                + "' - exceeded failure threshold ("
                + board.failureLog.getFailureCount(currentItem)
                + " failures)");
        --itemsToGoCount;
        continue;
      }

      // Let's go through all nets of this item
      for (int i = 0; i < currentItem.netCount(); i++) {
        // If the user requested to stop the auto-router, we stop it
        if (this.isStopAutoRouterRequested()) {
          break;
        }

        if (this.settings.maxItems != null
            && this.settings.maxItems > 0
            && (this.routedCount + this.failedCount) >= this.settings.maxItems) {
          FRLogger.info(
              "Max items limit reached (" + this.settings.maxItems + "). Stopping auto-router.");
          this.requestStopAutoRouter();
          break;
        }

        // We visually mark the area of the board, which is changed by the auto-router
        board.startMarkingChangedArea();

        // Do the auto-routing step for this item (typically PolylineTrace or Pin)
        SortedSet<Item> rippedItemList = new TreeSet<>();

        var autorouterResult =
            autorouteItem(board, currentItem, currentItem.getNetNo(i), rippedItemList, passNo);
        if (autorouterResult.state == AutorouteAttemptState.ROUTED) {
          // The item was successfully routed
          ++routed;
          this.routedCount++;
        } else if ((autorouterResult.state == AutorouteAttemptState.ALREADY_CONNECTED)
            || (autorouterResult.state == AutorouteAttemptState.NO_UNCONNECTED_NETS)
            || (autorouterResult.state == AutorouteAttemptState.CONNECTED_TO_PLANE)) {
          // The item doesn't need to be routed
          ++skipped;
        } else {
          Net net = board.rules.nets.get(currentItem.getNetNo(i));
          String netName = net != null ? net.name : "net#" + currentItem.getNetNo(i);

          // Record the failure
          this.board.failureLog.recordFailure(
              currentItem, passNo, autorouterResult.state, autorouterResult.details);

          FRLogger.debug("Autorouter " + autorouterResult.details);
          // Log details when we're down to last few items or item has many failures
          int failureCount = board.failureLog.getFailureCount(currentItem);
          if (itemsToGoCount <= 5 || failureCount >= 3) {
            FRLogger.debug(
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
          this.failedCount++;
        }
        --itemsToGoCount;
        rippedItemCount += rippedItemList.size();

        if (progressThrottler.shouldUpdate()) {
          PerformanceProfiler.start("stats_update");
          final BoardStatistics boardStatistics = board.getStatistics();
          routerCounters.passCount = passNo;
          routerCounters.queuedToBeRoutedCount = itemsToGoCount;
          routerCounters.skippedCount = skipped;
          routerCounters.rippedCount = rippedItemCount;
          routerCounters.failedToBeRoutedCount = notRouted;
          routerCounters.routedCount = routed;
          DesignRulesChecker drc2 = new DesignRulesChecker(board, null);
          drc2.calculateAllIncompletes();
          routerCounters.incompleteCount = drc2.getIncompleteCount();
          this.fireBoardUpdatedEvent(boardStatistics, routerCounters, board);
          PerformanceProfiler.end("stats_update");
        }
      }
    }

    if (this.removeUnconnectedVias) {
      removeTails(Item.StopConnectionOption.NONE);
    } else {
      removeTails(Item.StopConnectionOption.FANOUT_VIA);
    }

    routerCounters.passCount = passNo;
    routerCounters.queuedToBeRoutedCount = itemsToGoCount;
    routerCounters.skippedCount = skipped;
    routerCounters.rippedCount = rippedItemCount;
    routerCounters.failedToBeRoutedCount = notRouted;
    routerCounters.routedCount = routed;
    DesignRulesChecker drc3 = new DesignRulesChecker(board, null);
    drc3.calculateAllIncompletes();
    routerCounters.incompleteCount = drc3.getIncompleteCount();
    this.fireBoardUpdatedEvent(board.getStatistics(), routerCounters, board);

    return this.board;
  }

  // Tries to route an item on a specific net. Returns true, if the item is
  // routed.
  private AutorouteAttemptResult autorouteItem(
      RoutingBoard board,
      Item item,
      int routeNetNo,
      SortedSet<Item> rippedItemList,
      int ripupPassNo) {
    try {
      boolean containsPlane = false;

      // Get the net
      Net routeNet = board.rules.nets.get(routeNetNo);
      if (routeNet != null) {
        containsPlane = routeNet.containsPlane();
      }

      // Get the current via costs based on auto-router settings
      int currentViaCosts;
      if (containsPlane) {
        currentViaCosts = settings.getPlaneViaCosts();
      } else {
        currentViaCosts = settings.getViaCosts();
      }

      // Get and calculate the auto-router settings based on the board and net we are
      // working on
      AutorouteControl autorouteControl =
          new AutorouteControl(board, routeNetNo, settings, currentViaCosts, traceCostArr);
      autorouteControl.ripupAllowed = true;
      autorouteControl.ripupCosts = startRipupCosts * ripupPassNo;
      autorouteControl.removeUnconnectedVias = removeUnconnectedVias;

      // Check if the item is already routed
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
      }
      if (containsPlane) {
        routeStartSet = connectedSet;
        routeDestSet = unconnectedSet;
      } else {
        routeStartSet = unconnectedSet;
        routeDestSet = connectedSet;
      }

      // Calculate the shortest distance between the two sets of items
      this.latestAirLine = calcAirline(routeStartSet, routeDestSet);

      // Calculate the maximum time for this autoroute pass
      double maxMilliseconds = 100000 * Math.pow(2, ripupPassNo - 1);
      maxMilliseconds = Math.min(maxMilliseconds, Integer.MAX_VALUE);
      TimeLimit timeLimit = new TimeLimit((int) maxMilliseconds);

      // Initialize the auto-router engine
      AutorouteEngine autorouteEngine =
          board.initAutoroute(
              routeNetNo,
              autorouteControl.traceClearanceClassNo,
              this,
              timeLimit,
              this.retainAutorouteDatabase);

      // Do the auto-routing between the two sets of items
      AutorouteAttemptResult autorouteResult =
          autorouteEngine.autorouteConnection(
              routeStartSet,
              routeDestSet,
              autorouteControl,
              rippedItemList,
              null); // null: costs not needed by this thread

      // Update the changed area of the board
      if (autorouteResult.state == AutorouteAttemptState.ROUTED) {
        board.optChangedArea(
            new int[0],
            null,
            tracePullTightAccuracy,
            autorouteControl.traceCosts,
            this,
            TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP);
      }

      return autorouteResult;
    } catch (Exception e) {
      FRLogger.error("Error during autoroute_item", e);
      return new AutorouteAttemptResult(AutorouteAttemptState.FAILED);
    }
  }

  private void removeTails(Item.StopConnectionOption stopConnectionOption) {
    FRLogger.trace(
        "BatchAutorouterThread.remove_tails",
        "starting_tail_removal",
        FRLogger.buildTracePayload(
            "autoroute", "cleanup", "start", "stop_option=" + stopConnectionOption),
        "",
        null);
    board.startMarkingChangedArea();
    boolean tailsRemoved = board.removeTraceTails(-1, stopConnectionOption);
    FRLogger.trace(
        "BatchAutorouterThread.remove_tails",
        "tail_removal_complete",
        FRLogger.buildTracePayload(
            "autoroute",
            "cleanup",
            "complete",
            "tailsRemoved=" + tailsRemoved + " stop_option=" + stopConnectionOption),
        "",
        null);
    board.optChangedArea(
        new int[0],
        null,
        this.tracePullTightAccuracy,
        this.traceCostArr,
        this,
        TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP);
  }

  @Override
  protected void threadAction() {
    autorouteItems();
    captureStats();
  }

  private void captureStats() {
    try {
      ThreadMXBean threadMxBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
      long id = this.threadId();
      this.cpuTimeUsed = threadMxBean.getThreadCpuTime(id) / 1000.0f / 1000.0f / 1000.0f;
      this.maxMemoryUsed = threadMxBean.getThreadAllocatedBytes(id) / (1024.0f * 1024.0f);
    } catch (Throwable t) {
      // java.management or jdk.management module may not be available in minimal JRE builds;
      // leave cpuTimeUsed and maxMemoryUsed at their zero-initialized defaults.
    }
  }

  /** Registers a listener for board updated events. */
  public void addBoardUpdatedEventListener(BoardUpdatedEventListener listener) {
    boardUpdatedEventListeners.add(listener);
  }

  /**
   * Fires a board updated event. This happens when the board has been updated, e.g. after a route
   * has been added.
   */
  public void fireBoardUpdatedEvent(
      BoardStatistics boardStatistics, RouterCounters routerCounters, RoutingBoard board) {
    BoardUpdatedEvent event = new BoardUpdatedEvent(this, boardStatistics, routerCounters, board);
    for (BoardUpdatedEventListener listener : boardUpdatedEventListeners) {
      listener.onBoardUpdatedEvent(event);
    }
  }

  /** Returns the routing board being processed by this thread. */
  public RoutingBoard getBoard() {
    return board;
  }

  /** Returns the count of successfully routed items in this pass. */
  public int getRoutedCount() {
    return routedCount;
  }

  /** Returns the count of items that failed to route in this pass. */
  public int getFailedCount() {
    return failedCount;
  }
}
