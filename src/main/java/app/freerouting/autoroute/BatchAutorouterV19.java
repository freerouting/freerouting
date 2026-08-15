package app.freerouting.autoroute;

import app.freerouting.autoroute.events.TaskStateChangedEvent;
import app.freerouting.board.ConductionArea;
import app.freerouting.board.Connectable;
import app.freerouting.board.DrillItem;
import app.freerouting.board.Item;
import app.freerouting.board.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.StoppableThread;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import app.freerouting.settings.RouterSettings;
import java.time.Instant;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * V1.9 BatchAutorouter implementation - ported from v1.9 for performance comparison. This is a
 * simpler, single-threaded router that was used in v1.9.
 *
 * <p>Key differences from current router: - Single-threaded only (no multi-threading support) -
 * Simpler pass logic without board history/backtracking - Different item routing order (natural
 * order vs sorted) - No failure tracking or skip logic
 */
public class BatchAutorouterV19 extends NamedAlgorithm {

  private static final int TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP = 1000;

  private final boolean removeUnconnectedVias;
  private final AutorouteControl.ExpansionCostFactor[] traceCostArr;
  private final boolean retainAutorouteDatabase;
  private final int startRipupCosts;
  private final int tracePullTightAccuracy;

  protected RoutingJob job;
  private boolean isOptimizerAutorouter;

  /** Used to draw the airline of the current routed incomplete. */
  private FloatLine airLine;

  /** Initial number of unrouted nets at the start of the routing session. */
  private int initialUnroutedCount;

  /** Time when the routing session started. */
  private Instant sessionStartTime;

  /**
   * Creates a new V1.9 batch autorouter from a RoutingJob. This constructor adapts the current
   * RoutingJob structure to the v1.9 router's needs.
   */
  public BatchAutorouterV19(RoutingJob job) {
    this(
        job.thread,
        job.board,
        job.routerSettings,
        true,
        true,
        job.routerSettings.getStartRipupCosts(),
        job.routerSettings.tracePullTightAccuracy);
    this.job = job;
  }

  /** Internal constructor matching v1.9 signature. */
  public BatchAutorouterV19(
      StoppableThread thread,
      RoutingBoard board,
      RouterSettings settings,
      boolean removeUnconnectedVias,
      boolean withPreferredDirections,
      int startRipupCosts,
      int pullTightAccuracy) {
    super(thread, board, settings);

    // Validate that this is single-threaded (v1.9 doesn't support multi-threading)
    if (settings.maxThreads > 1) {
      FRLogger.warn(
          "V1.9 router only supports single-threaded operation. Setting maxThreads to 1.");
      settings.maxThreads = 1;
    }

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
    this.tracePullTightAccuracy = pullTightAccuracy;
    this.retainAutorouteDatabase = false;
  }

  @Override
  public String getId() {
    return RouterSettings.ALGORITHM_V19;
  }

  @Override
  public String getName() {
    return "Freerouting Auto-router v1.9";
  }

  @Override
  public String getVersion() {
    return "1.9";
  }

  @Override
  public String getDescription() {
    return "Freerouting Auto-router v1.9 (ported for performance comparison)";
  }

  @Override
  public NamedAlgorithmType getType() {
    return NamedAlgorithmType.ROUTER;
  }

  /** Returns the initial number of unrouted nets at the start of the routing session. */
  public int getInitialUnroutedCount() {
    return this.initialUnroutedCount;
  }

  /** Returns the time when the routing session started. */
  public Instant getSessionStartTime() {
    return this.sessionStartTime;
  }

  /**
   * Autoroutes ripup passes until the board is completed or the autorouter is stopped by the user.
   * Returns true if the board is completed.
   *
   * <p>This is the main entry point, matching the current router's interface.
   */
  public boolean runBatchLoop() {
    boolean anyRoutable = false;
    for (int i = 0; i < this.settings.getLayerCount(); i++) {
      if (this.settings.getLayerActive(i) && this.board.layerStructure.arr[i].isSignal) {
        anyRoutable = true;
        break;
      }
    }
    if (!anyRoutable) {
      FRLogger.warn("Cannot start autorouter: all layers are disabled.");
      this.fireTaskStateChangedEvent(
          new TaskStateChangedEvent(this, TaskState.CANCELLED, 0, this.board.getHash()));
      throw new IllegalArgumentException("Cannot start autorouter: all layers are disabled.");
    }

    this.fireTaskStateChangedEvent(
        new TaskStateChangedEvent(this, TaskState.STARTED, 0, this.board.getHash()));

    // Capture initial state for session summary
    this.sessionStartTime = Instant.now();
    DesignRulesChecker tempDrc = new DesignRulesChecker(this.board, null);
    tempDrc.calculateAllIncompletes();
    this.initialUnroutedCount = tempDrc.getIncompleteCount();

    job.logInfo(
        "Starting V1.9 router with " + this.initialUnroutedCount + " incomplete connections.");

    boolean continueAutorouting = true;
    int currentPass = 1;

    while (continueAutorouting && !this.thread.isStopAutoRouterRequested()) {
      if (job != null && job.state == RoutingJobState.TIMED_OUT) {
        this.thread.requestStopAutoRouter();
      }

      String currentBoardHash = this.board.getHash();

      if (this.settings.maxPasses != null
          && this.settings.maxPasses > 0
          && currentPass > this.settings.maxPasses) {
        thread.requestStopAutoRouter();
        break;
      }

      this.fireTaskStateChangedEvent(
          new TaskStateChangedEvent(this, TaskState.RUNNING, currentPass, currentBoardHash));

      FRLogger.traceEntry(
          "BatchAutorouterV19.autoroute_pass #"
              + currentPass
              + " on board '"
              + currentBoardHash
              + "'");

      // Run one pass using v1.9 logic
      continueAutorouting = autoroutePass(currentPass, true);

      double autorouterPassDuration =
          FRLogger.traceExit(
              "BatchAutorouterV19.autoroute_pass #"
                  + currentPass
                  + " on board '"
                  + currentBoardHash
                  + "'");

      var boardStatistics = this.board.getStatistics();
      float boardScore = boardStatistics.getNormalizedScore(job.routerSettings.scoring);

      String passCompletedMessage =
          "V1.9 Auto-routing pass #"
              + currentPass
              + " on board '"
              + currentBoardHash
              + "' was completed in "
              + FRLogger.formatDuration(autorouterPassDuration)
              + " with the score of "
              + FRLogger.formatScore(
                  boardScore,
                  boardStatistics.connections.incompleteCount,
                  boardStatistics.clearanceViolations.totalCount);
      if (job.resourceUsage.cpuTimeUsed > 0) {
        passCompletedMessage +=
            ", using "
                + FRLogger.defaultFloatFormat.format(job.resourceUsage.cpuTimeUsed)
                + " CPU seconds and the job allocated "
                + FRLogger.defaultFloatFormat.format(job.resourceUsage.maxMemoryUsed / 1024.0f)
                + " GB of memory so far.";
      } else {
        passCompletedMessage += ".";
      }
      if (!isOptimizerAutorouter) {
        job.logInfo(passCompletedMessage);
      }

      if (this.settings.saveIntermediateStages) {
        fireBoardSnapshotEvent(this.board);
      }

      // check if there are still unrouted items
      if (continueAutorouting && !this.thread.isStopAutoRouterRequested()) {
        currentPass++;
      }
    }

    job.board = this.board;

    if (!(this.removeUnconnectedVias
        || continueAutorouting
        || this.thread.isStopAutoRouterRequested())) {
      // clean up the route if the board is completed and if fanout is used.
      removeTails(Item.StopConnectionOption.NONE);
    }

    if (!this.thread.isStopAutoRouterRequested()) {
      this.fireTaskStateChangedEvent(
          new TaskStateChangedEvent(this, TaskState.FINISHED, currentPass, this.board.getHash()));
    } else {
      this.fireTaskStateChangedEvent(
          new TaskStateChangedEvent(this, TaskState.CANCELLED, currentPass, this.board.getHash()));
    }

    return !this.thread.isStopAutoRouterRequested();
  }

  /**
   * Auto-routes one ripup pass of all items of the board. Returns false, if the board is already
   * completely routed.
   *
   * <p>This is the v1.9 implementation - simpler than current version: - No board
   * history/backtracking - No failure tracking - Natural item order (no sorting by airline
   * distance) - Simpler progress tracking
   */
  private boolean autoroutePass(int passNo, boolean withScreenMessage) {
    try {
      Collection<Item> autorouteItemList = new LinkedList<>();
      Set<Item> handledItems = new TreeSet<>();
      Iterator<UndoableObjects.UndoableObjectNode> it = board.itemList.startReadObject();
      for (; ; ) {
        UndoableObjects.Storable currentObject = board.itemList.readObject(it);
        if (currentObject == null) {
          break;
        }
        if (currentObject instanceof Connectable && currentObject instanceof Item) {
          // This is a connectable item, like PolylineTrace or Pin
          Item currentItem = (Item) currentObject;
          if (!currentItem.isRoutable()) {
            if (!handledItems.contains(currentItem)) {

              // Let's go through all nets of this item
              for (int i = 0; i < currentItem.netCount(); ++i) {
                int currentNetNumber = currentItem.getNetNumber(i);
                Set<Item> connectedSet = currentItem.getConnectedSet(currentNetNumber);
                for (Item currentConnectedItem : connectedSet) {
                  if (currentConnectedItem.netCount() <= 1) {
                    handledItems.add(currentConnectedItem);
                  }
                }
                int netItemCount = board.connectableItemCount(currentNetNumber);

                // If the item is not connected to all other items of the net, we add it to the
                // auto-router's to-do list
                if ((connectedSet.size() < netItemCount) && (!currentItem.hasIgnoredNets())) {
                  autorouteItemList.add(currentItem);
                }
              }
            }
          }
        }
      }

      // If there are no items to route, we're done
      if (autorouteItemList.isEmpty()) {
        this.airLine = null;
        return false;
      }

      int itemsToGoCount = autorouteItemList.size();
      int rippedItemCount = 0;
      int notFound = 0;
      int routed = 0;

      job.logDebug("V1.9 Pass #" + passNo + ": " + itemsToGoCount + " items to route");

      // Let's go through all items to route (v1.9: natural order, no sorting)
      for (Item currentItem : autorouteItemList) {
        // If the user requested to stop the auto-router, we stop it
        if (this.thread.isStopAutoRouterRequested()) {
          break;
        }

        // Let's go through all nets of this item
        for (int i = 0; i < currentItem.netCount(); ++i) {
          // If the user requested to stop the auto-router, we stop it
          if (this.thread.isStopAutoRouterRequested()) {
            break;
          }

          // We visually mark the area of the board, which is changed by the auto-router
          board.startMarkingChangedArea();

          // Do the auto-routing step for this item (typically PolylineTrace or Pin)
          SortedSet<Item> rippedItemList = new TreeSet<>();
          if (autorouteItem(currentItem, currentItem.getNetNumber(i), rippedItemList, passNo)) {
            ++routed;
          } else {
            ++notFound;
          }
          --itemsToGoCount;
          rippedItemCount += rippedItemList.size();
        }
      }

      // V1.9: Always remove tails after each pass
      if (this.removeUnconnectedVias) {
        removeTails(Item.StopConnectionOption.NONE);
      } else {
        removeTails(Item.StopConnectionOption.FANOUT_VIA);
      }

      // We are done with this pass
      this.airLine = null;

      job.logDebug(
          "V1.9 Pass #"
              + passNo
              + " completed: routed="
              + routed
              + ", notFound="
              + notFound
              + ", ripped="
              + rippedItemCount);

      return true;
    } catch (Exception e) {
      job.logError("Something went wrong during the V1.9 auto-routing", e);
      this.airLine = null;
      return false;
    }
  }

  private void removeTails(Item.StopConnectionOption stopConnectionOption) {
    board.startMarkingChangedArea();
    board.removeTraceTails(-1, stopConnectionOption);
    board.optChangedArea(
        new int[0],
        null,
        this.tracePullTightAccuracy,
        this.traceCostArr,
        this.thread,
        TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP);
  }

  /**
   * Tries to route an item on a specific net. Returns true, if the item is routed. This is the v1.9
   * implementation.
   */
  private boolean autorouteItem(
      Item item, int routeNetNo, SortedSet<Item> rippedItemList, int ripupPassNo) {
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
        currentViaCosts = this.settings.getPlaneViaCosts();
      } else {
        currentViaCosts = this.settings.getViaCosts();
      }

      // Get and calculate the auto-router settings based on the board and net we are
      // working on
      AutorouteControl autorouteControl =
          new AutorouteControl(
              this.board, routeNetNo, settings, currentViaCosts, this.traceCostArr);
      autorouteControl.ripupAllowed = true;
      autorouteControl.ripupCosts = this.startRipupCosts * ripupPassNo;
      autorouteControl.removeUnconnectedVias = this.removeUnconnectedVias;

      // Check if the item is already routed
      Set<Item> unconnectedSet = item.getUnconnectedSet(routeNetNo);
      if (unconnectedSet.isEmpty()) {
        return true; // item is already routed.
      }

      Set<Item> connectedSet = item.getConnectedSet(routeNetNo);
      Set<Item> routeStartSet;
      Set<Item> routeDestSet;
      if (containsPlane) {
        for (Item currentItem : connectedSet) {
          if (currentItem instanceof ConductionArea) {
            return true; // already connected to plane
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
      calcAirline(routeStartSet, routeDestSet);

      // Calculate the maximum time for this autoroute pass
      double maxMilliseconds = 100000 * Math.pow(2, ripupPassNo - 1);
      maxMilliseconds = Math.min(maxMilliseconds, Integer.MAX_VALUE);
      TimeLimit timeLimit = new TimeLimit((int) maxMilliseconds);

      // Initialize the auto-router engine
      AutorouteEngine autorouteEngine =
          board.initAutoroute(
              routeNetNo,
              autorouteControl.traceClearanceClassIndex,
              this.thread,
              timeLimit,
              this.retainAutorouteDatabase);

      // Do the auto-routing between the two sets of items
      // V1.9 used AutorouteEngine.AutorouteResult enum, current version uses
      // AutorouteAttemptResult
      // We need to adapt to the current interface
      AutorouteAttemptResult autorouteResult =
          autorouteEngine.autorouteConnection(
              routeStartSet,
              routeDestSet,
              autorouteControl,
              rippedItemList,
              null); // null: costs not needed here

      // Update the changed area of the board
      if (autorouteResult.state == AutorouteAttemptState.ROUTED) {
        board.optChangedArea(
            new int[0],
            null,
            this.tracePullTightAccuracy,
            autorouteControl.traceCosts,
            this.thread,
            TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP);
      }

      // Return true, if the item is routed
      return autorouteResult.state == AutorouteAttemptState.ROUTED
          || autorouteResult.state == AutorouteAttemptState.ALREADY_CONNECTED;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Returns the airline of the current autorouted connection, or null if no such airline exists.
   */
  public FloatLine getAirLine() {
    if (this.airLine == null) {
      return null;
    }
    if (this.airLine.a == null || this.airLine.b == null) {
      return null;
    }
    return this.airLine;
  }

  /**
   * Calculates the shortest distance between two sets of items, specifically between Pin and Via.
   * items (pins and vias are connectable DrillItems)
   */
  private void calcAirline(Collection<Item> fromItems, Collection<Item> toItems) {
    FloatPoint fromCorner = null;
    FloatPoint toCorner = null;
    double minDistance = Double.MAX_VALUE;
    for (Item currentFromItem : fromItems) {
      if (!(currentFromItem instanceof DrillItem)) {
        continue;
      }
      FloatPoint currentFromCorner = ((DrillItem) currentFromItem).getCenter().toFloat();
      for (Item currentToItem : toItems) {
        if (!(currentToItem instanceof DrillItem)) {
          continue;
        }
        FloatPoint currentToCorner = ((DrillItem) currentToItem).getCenter().toFloat();
        double currentDistance = currentFromCorner.distanceSquare(currentToCorner);
        if (currentDistance < minDistance) {
          minDistance = currentDistance;
          fromCorner = currentFromCorner;
          toCorner = currentToCorner;
        }
      }
    }
    this.airLine = new FloatLine(fromCorner, toCorner);
  }
}
