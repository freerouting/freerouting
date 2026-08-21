package app.freerouting.autoroute.pipeline;

import app.freerouting.autoroute.AutorouteAttemptResult;
import app.freerouting.autoroute.AutorouteAttemptState;
import app.freerouting.autoroute.BoardHistory;
import app.freerouting.autoroute.maze.AutorouteControl;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Connectable;
import app.freerouting.board.model.items.DrillItem;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.Point;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import app.freerouting.settings.RouterSettings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/** Handles the sequencing of the auto-router passes. */
public final class BatchAutorouter extends NamedAlgorithm {

  // The lowest rank of the board to be selected to go back to.
  // Must not exceed BoardHistory.MAX_HISTORY_SIZE so the check can actually fire.
  static final int BOARD_RANK_LIMIT = BoardHistory.MAX_HISTORY_SIZE;
  // Maximum number of tries on the same board
  static final int MAXIMUM_TRIES_ON_THE_SAME_BOARD = 3;
  static final int TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP = 1000;
  // The minimum number of passes to complete the board, unless all items are
  // routed
  static final int STOP_AT_PASS_MINIMUM = 8;
  // The modulo of the pass number to check if the improvements were so small that
  // process should stop despite not all items are routed
  static final int STOP_AT_PASS_MODULO = 4;
  // Number of consecutive passes with no meaningful score improvement before
  // aborting (prevents endless looping when items cannot be routed)
  static final int STAGNATION_PASS_LIMIT = 10;
  // Number of no-improvement passes before attempting a one-time fanout-tail cleanup.
  static final int FANOUT_RECOVERY_STAGNATION_PASSES = 3;
  // Progress statistics are informational only; avoid rebuilding the expensive snapshot for
  // every item while keeping the GUI reasonably current on large boards.
  static final int PROGRESS_STATISTICS_ITEM_INTERVAL = 10;
  // Minimum score gain (on the 0–1000 normalized scale) that counts as a
  // meaningful improvement; gains smaller than this are treated as stagnation.
  static final float STAGNATION_SCORE_THRESHOLD = 0.5F;
  private static final boolean BENCHMARK_PROFILE_ENABLED =
      Boolean.getBoolean("freerouting.benchmark.profile");
  private static final boolean BENCHMARK_RETAIN_AUTOROUTE_DATABASE =
      Boolean.getBoolean("freerouting.benchmark.retain_autoroute_database");

  final boolean removeUnconnectedVias;
  final AutorouteControl.ExpansionCostFactor[] traceCosts;
  final boolean retainAutorouteDatabase;
  final int startRipupCosts;
  final int tracePullTightAccuracy;
  // Reusable collections to reduce memory churn (thread-safe as each thread has
  // its own BatchAutorouter instance)
  private final List<Item> reusableAutorouteItemList = new ArrayList<>();
  private final Set<Item> reusableHandledItems = new TreeSet<>();
  private final AutorouteConnectionRouter connectionRouter;
  private final AutoroutePassRunner passRunner;
  private final AutorouteBatchLoop batchLoop;
  protected RoutingJob job;
  int totalItemsRouted;
  boolean fanoutTimedOut;

  /** Time when the routing session started. */
  Random random;

  /** Used to draw the airline of the current routed incomplete. */
  FloatLine airLine;

  /** Initial number of unrouted nets at the start of the routing session. */
  int initialUnroutedCount;

  /** Time when the routing session started. */
  Instant sessionStartTime;

  long lastBoardUpdateTimestamp;
  boolean isOptimizerAutorouter;
  long profileItemSelectionNanos;
  long profileIntermediateStatisticsNanos;
  long profileBoardStatisticsNanos;
  long profileIncompleteDrcNanos;
  long profileAutorouteItemNanos;
  long profileMazeSearchNanos;
  long profileOptChangedAreaNanos;
  long profileTailRemovalNanos;
  int profileRouteItemCount;
  int profilePlaneItemCount;
  BoardStatistics progressStatistics;
  int progressItemsSinceStatistics;

  /** Creates a BatchAutorouter for the given routing job. */
  public BatchAutorouter(RoutingJob job) {
    this(
        job.thread,
        job.board,
        job.routerSettings,
        !job.routerSettings.isFanoutEnabled(),
        true,
        job.routerSettings.getStartRipupCosts(),
        job.routerSettings.tracePullTightAccuracy != null
            ? job.routerSettings.tracePullTightAccuracy
            : 500);
    this.job = job;
  }

  /** Creates a new BatchAutorouter instance. */
  public BatchAutorouter(
      StoppableThread thread,
      RoutingBoard board,
      RouterSettings settings,
      boolean removeUnconnectedVias,
      boolean withPreferredDirections,
      int startRipupCosts,
      int pullTightAccuracy) {
    super(thread, board, settings);

    this.random = new Random(0);

    this.removeUnconnectedVias = removeUnconnectedVias;
    if (withPreferredDirections) {
      this.traceCosts = this.settings.getTraceCosts();
    } else {
      // remove preferred direction
      this.traceCosts = new AutorouteControl.ExpansionCostFactor[this.board.getLayerCount()];
      for (int i = 0; i < this.traceCosts.length; i++) {
        double currentMinCost = this.settings.getPreferredDirectionTraceCosts(i);
        this.traceCosts[i] =
            new AutorouteControl.ExpansionCostFactor(currentMinCost, currentMinCost);
      }
    }

    this.startRipupCosts = startRipupCosts;
    this.tracePullTightAccuracy = pullTightAccuracy;
    // Retained state is deliberately opt-in for bounded benchmark experiments only. The default
    // remains the fresh-engine behavior used by production routing and parity comparisons.
    this.retainAutorouteDatabase = BENCHMARK_RETAIN_AUTOROUTE_DATABASE;
    this.connectionRouter = new AutorouteConnectionRouter(this);
    this.passRunner = new AutoroutePassRunner(this);
    this.batchLoop = new AutorouteBatchLoop(this);
  }

  static boolean isBenchmarkProfileEnabled() {
    return BENCHMARK_PROFILE_ENABLED;
  }

  boolean isRemoveUnconnectedVias() {
    return this.removeUnconnectedVias;
  }

  AutorouteControl.ExpansionCostFactor[] getTraceCosts() {
    return this.traceCosts;
  }

  boolean isRetainAutorouteDatabase() {
    return this.retainAutorouteDatabase;
  }

  int getStartRipupCosts() {
    return this.startRipupCosts;
  }

  int getTracePullTightAccuracy() {
    return this.tracePullTightAccuracy;
  }

  void addProfileMazeSearchNanos(long elapsedNanos) {
    this.profileMazeSearchNanos += elapsedNanos;
  }

  void addProfileOptChangedAreaNanos(long elapsedNanos) {
    this.profileOptChangedAreaNanos += elapsedNanos;
  }

  void setAirLine(FloatLine airLine) {
    this.airLine = airLine;
  }

  void resetPassProfile() {
    this.profileItemSelectionNanos = 0;
    this.profileIntermediateStatisticsNanos = 0;
    this.profileBoardStatisticsNanos = 0;
    this.profileIncompleteDrcNanos = 0;
    this.profileAutorouteItemNanos = 0;
    this.profileMazeSearchNanos = 0;
    this.profileOptChangedAreaNanos = 0;
    this.profileTailRemovalNanos = 0;
    this.profileRouteItemCount = 0;
    this.profilePlaneItemCount = 0;
  }

  void logBenchmarkProfile(int passNo) {
    if (!BENCHMARK_PROFILE_ENABLED) {
      return;
    }
    FRLogger.info(
        "BENCHMARK_PROFILE pass="
            + passNo
            + ", items="
            + this.profileRouteItemCount
            + ", plane_items="
            + this.profilePlaneItemCount
            + ", selection_ms="
            + AutorouteRuntimeMetrics.nanosToMillis(this.profileItemSelectionNanos)
            + ", autoroute_item_ms="
            + AutorouteRuntimeMetrics.nanosToMillis(this.profileAutorouteItemNanos)
            + ", maze_search_ms="
            + AutorouteRuntimeMetrics.nanosToMillis(this.profileMazeSearchNanos)
            + ", opt_changed_area_ms="
            + AutorouteRuntimeMetrics.nanosToMillis(this.profileOptChangedAreaNanos)
            + ", tail_removal_ms="
            + AutorouteRuntimeMetrics.nanosToMillis(this.profileTailRemovalNanos)
            + ", statistics_ms="
            + AutorouteRuntimeMetrics.nanosToMillis(this.profileIntermediateStatisticsNanos)
            + ", retain_database="
            + this.retainAutorouteDatabase
            + ", board_statistics_ms="
            + AutorouteRuntimeMetrics.nanosToMillis(this.profileBoardStatisticsNanos)
            + ", incomplete_drc_ms="
            + AutorouteRuntimeMetrics.nanosToMillis(this.profileIncompleteDrcNanos));
  }

  /**
   * Auto-routes ripup passes until the board is completed or the auto-router is stopped by the
   * user, or if maxPassCount is exceeded. Is currently used in the optimize via batch pass. Returns
   * the number of passes to complete the board or maxPassCount + 1, if the board is not completed.
   */
  public static int autoroutePassesForOptimizingItem(
      RoutingJob job,
      int maxPassCount,
      int ripupCosts,
      int tracePullTightAccuracy,
      boolean withPreferredDirections,
      RoutingBoard updatedRoutingBoard,
      RouterSettings routerSettings) {
    BatchAutorouter routerInstance =
        new BatchAutorouter(
            job.thread,
            updatedRoutingBoard,
            routerSettings,
            true,
            withPreferredDirections,
            ripupCosts,
            tracePullTightAccuracy);
    routerInstance.job = job;
    routerInstance.isOptimizerAutorouter = true;

    boolean stillUnroutedItems = true;
    int currentPassNo = 1;
    while (stillUnroutedItems
        && !job.thread.isStopAutoRouterRequested()
        && currentPassNo <= maxPassCount) {
      stillUnroutedItems = routerInstance.autoroutePass(currentPassNo);
      if (stillUnroutedItems
          && !job.thread.isStopAutoRouterRequested()
          && updatedRoutingBoard == null) {}
      ++currentPassNo;
    }
    routerInstance.removeTails(Item.StopConnectionOption.NONE);
    if (!stillUnroutedItems) {
      --currentPassNo;
    }
    return currentPassNo;
  }

  static Point[] getImpactedPoints(Item item) {
    if (item instanceof Trace trace) {
      return new Point[] {trace.firstCorner(), trace.lastCorner()};
    }
    if (item instanceof Via via) {
      return new Point[] {via.getCenter()};
    }
    if (item instanceof Pin pin) {
      return new Point[] {pin.getCenter()};
    }
    if (item instanceof DrillItem drillItem) {
      return new Point[] {drillItem.getCenter()};
    }
    return new Point[0];
  }

  /**
   * Strict-DRC enforcement: if any trace/via inserted by the connection that just routed (item id
   * above {@code maxItemIdBefore}) carries a clearance violation, rip the whole set of new items
   * and report the connection FAILED, so the pass counts it as not routed and later passes (higher
   * ripup costs) retry it. Returns null when the connection is clean and may be kept.
   */
  public static AutorouteAttemptResult enforceStrictDrc(
      app.freerouting.board.facade.RoutingBoard board, int routeNetNo, int maxItemIdBefore) {
    List<Item> newItems = new ArrayList<>();
    boolean hasViolation = false;
    for (Item currentItem : board.getConnectableItems(routeNetNo)) {
      if (currentItem.getId() <= maxItemIdBefore
          || !(currentItem instanceof Trace
              || currentItem instanceof app.freerouting.board.model.items.Via)) {
        continue;
      }
      newItems.add(currentItem);
      if (!hasViolation && !currentItem.clearanceViolations().isEmpty()) {
        hasViolation = true;
      }
    }
    if (!hasViolation) {
      return null;
    }
    board.removeItems(newItems);
    return new AutorouteAttemptResult(
        AutorouteAttemptState.FAILED,
        "strict_drc: connection ripped because "
            + newItems.size()
            + " new item(s) included clearance violations");
  }

  public boolean isFanoutTimedOut() {
    return this.fanoutTimedOut;
  }

  boolean shouldFireBoardUpdate() {
    long currentTime = System.currentTimeMillis();
    if (currentTime - lastBoardUpdateTimestamp
        > 250) { // Limit updates to 4 times per second (250ms)
      lastBoardUpdateTimestamp = currentTime;
      return true;
    }
    return false;
  }

  List<Item> getAutorouteItems(RoutingBoard board) {
    // Reuse instance collections to reduce memory allocation
    reusableAutorouteItemList.clear();
    reusableHandledItems.clear();
    List<Item> autorouteItemList = reusableAutorouteItemList;
    Set<Item> handledItems = reusableHandledItems;
    Iterator<UndoableObjects.UndoableObjectNode> it = board.itemList.startReadObject();
    for (; ; ) {
      UndoableObjects.Storable currentObject = board.itemList.readObject(it);
      if (currentObject == null) {
        break;
      }
      if (currentObject instanceof Connectable && currentObject instanceof Item currentItem) {
        // This is a connectable item, like PolylineTrace or Pin
        if (!currentItem.isRoutable()) {
          if (!handledItems.contains(currentItem)) {

            // Let's go through all nets of this item
            for (int i = 0; i < currentItem.netCount(); i++) {
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
                Net net = board.rules.nets.get(currentNetNumber);
                // For plane nets: skip items whose connected set already contains a
                // ConductionArea (copper pour). These items would immediately return
                // CONNECTED_TO_PLANE in autorouteItem(), wasting time and causing
                // spurious normalizeTraces() failures on nearby stub geometry.
                // Items not yet connected to the plane are still enqueued so they can
                // be routed to the pour in this pass.
                if (net != null && net.containsPlane()) {
                  boolean alreadyConnectedToPlane =
                      connectedSet.stream().anyMatch(ConductionArea.class::isInstance);
                  if (alreadyConnectedToPlane) {
                    continue;
                  }
                }
                autorouteItemList.add(currentItem);
                String netName = net != null ? net.name : "net#" + currentNetNumber;
                FRLogger.debug(
                    "Queuing item for routing: "
                        + currentItem.getClass().getSimpleName()
                        + " on net '"
                        + netName
                        + "' (connected: "
                        + connectedSet.size()
                        + "/"
                        + netItemCount
                        + ")");
              }
            }
          }
        }
      }
    }
    return autorouteItemList;
  }

  boolean autoroutePassMultiThread(int passNo) {
    return passRunner.runMultiThread(passNo);
  }

  /**
   * Auto-routes one ripup pass of all items of the board. Returns false, if the board is already
   * completely routed.
   */
  boolean autoroutePass(int passNo) {
    return passRunner.runSingleThread(passNo);
  }

  @Override
  public String getId() {
    return "freerouting-router";
  }

  @Override
  public String getName() {
    return "Freerouting Auto-router";
  }

  @Override
  public String getVersion() {
    return "1.0";
  }

  @Override
  public String getDescription() {
    return "Freerouting Auto-router v1.0";
  }

  /**
   * Builds a human-readable summary of all unrouted connections on the current board, grouped by
   * net. For each unrouted connection the component and pin names of both endpoints are listed so
   * that the user can identify exactly which connections are missing and address them in their
   * design.
   *
   * <p>Example output:
   *
   * <pre>
   *   Net 'GND' (1 unrouted connection):
   *     - J2-A1  ->  U1-1
   *   Net '/MIPI_CSI_D0_N' (1 unrouted connection):
   *     - J2-A2  ->  U1-2
   * </pre>
   *
   * @return a formatted, multi-line string describing every unrouted airline
   */
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
   */
  public boolean runBatchLoop() {
    return batchLoop.run();
  }

  String buildUnroutedConnectionsReport() {
    return AutorouteUnroutedReport.build(this.board);
  }

  void removeTails(Item.StopConnectionOption stopConnectionOption) {
    final long tailRemovalStart = BENCHMARK_PROFILE_ENABLED ? System.nanoTime() : 0;
    board.startMarkingChangedArea();
    board.removeTraceTails(-1, stopConnectionOption);
    long pullTightStart = BENCHMARK_PROFILE_ENABLED ? System.nanoTime() : 0;
    board.optChangedArea(
        new int[0],
        null,
        this.tracePullTightAccuracy,
        this.traceCosts,
        this.thread,
        TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP);
    if (BENCHMARK_PROFILE_ENABLED) {
      this.profileTailRemovalNanos += System.nanoTime() - tailRemovalStart;
      this.profileOptChangedAreaNanos += System.nanoTime() - pullTightStart;
    }
  }

  // Tries to route an item on a specific net. Returns true, if the item is
  // routed.
  AutorouteAttemptResult autorouteItem(
      Item item,
      int routeNetNo,
      SortedSet<Item> rippedItemList,
      Map<Item, Integer> ripupCosts,
      int ripupPassNo) {
    return connectionRouter.route(item, routeNetNo, rippedItemList, ripupCosts, ripupPassNo);
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
   * Return an uppercase one-letter, two-letter or three-letter string based on the thread index (0
   * = A, 1 = B, 2 = C, ..., 26 = AA, 27 = AB, ...).
   *
   * @param threadIndex the thread index.
   * @return the letter label for the thread index.
   */
  String threadIndexToLetter(int threadIndex) {
    if (threadIndex < 0) {
      return "";
    }
    if (threadIndex < 26) {
      return String.valueOf((char) ('A' + threadIndex));
    } else if (threadIndex < 26 * 26) {
      int firstLetterIndex = threadIndex / 26;
      int secondLetterIndex = threadIndex % 26;
      return String.valueOf((char) ('A' + firstLetterIndex)) + (char) ('A' + secondLetterIndex);
    } else {
      int firstLetterIndex = threadIndex / (26 * 26);
      int secondLetterIndex = (threadIndex / 26) % 26;
      int thirdLetterIndex = threadIndex % 26;
      return String.valueOf((char) ('A' + firstLetterIndex))
          + (char) ('A' + secondLetterIndex)
          + (char) ('A' + thirdLetterIndex);
    }
  }

  int calculateIncompleteCount(RoutingBoard board) {
    long drcStart = BENCHMARK_PROFILE_ENABLED ? System.nanoTime() : 0;
    DesignRulesChecker tempDrc = new DesignRulesChecker(board, null);
    tempDrc.calculateAllIncompletes();
    if (BENCHMARK_PROFILE_ENABLED) {
      this.profileIncompleteDrcNanos += System.nanoTime() - drcStart;
    }
    return tempDrc.getIncompleteCount();
  }
}
