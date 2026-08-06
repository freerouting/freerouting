package app.freerouting.autoroute;

import static java.util.Collections.shuffle;

import app.freerouting.autoroute.events.BoardUpdatedEvent;
import app.freerouting.autoroute.events.BoardUpdatedEventListener;
import app.freerouting.autoroute.events.TaskStateChangedEvent;
import app.freerouting.board.ConductionArea;
import app.freerouting.board.Connectable;
import app.freerouting.board.DrillItem;
import app.freerouting.board.Item;
import app.freerouting.board.Pin;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Trace;
import app.freerouting.board.Unit;
import app.freerouting.board.Via;
import app.freerouting.core.RouterCounters;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.drc.AirLine;
import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import app.freerouting.settings.RouterSettings;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Handles the sequencing of the auto-router passes.
 */
public class BatchAutorouter extends NamedAlgorithm {

  // The lowest rank of the board to be selected to go back to.
  // Must not exceed BoardHistory.MAX_HISTORY_SIZE so the check can actually fire.
  private static final int BOARD_RANK_LIMIT = BoardHistory.MAX_HISTORY_SIZE;
  // Maximum number of tries on the same board
  private static final int MAXIMUM_TRIES_ON_THE_SAME_BOARD = 3;
  private static final int TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP = 1000;
  // The minimum number of passes to complete the board, unless all items are
  // routed
  private static final int STOP_AT_PASS_MINIMUM = 8;
  // The modulo of the pass number to check if the improvements were so small that
  // process should stop despite not all items are routed
  private static final int STOP_AT_PASS_MODULO = 4;
  // Number of consecutive passes with no meaningful score improvement before
  // aborting (prevents endless looping when items cannot be routed)
  private static final int STAGNATION_PASS_LIMIT = 5;
  // Per-via budget for the opt-in power-trunk passes. These run once per escape
  // via across three phases, so the old 30s/45s/60s figures let a single power
  // net with a few dozen escape vias dominate the entire routing job.
  private static final int POWER_TRUNK_MILLIS_PER_VIA = 10000;
  // Number of no-improvement passes before attempting a one-time fanout-tail cleanup.
  private static final int FANOUT_RECOVERY_STAGNATION_PASSES = 3;
  // Minimum score gain (on the 0–1000 normalized scale) that counts as a
  // meaningful improvement; gains smaller than this are treated as stagnation.
  private static final float STAGNATION_SCORE_THRESHOLD = 0.5f;

  private final boolean remove_unconnected_vias;
  private final AutorouteControl.ExpansionCostFactor[] trace_cost_arr;
  private final boolean retain_autoroute_database;
  private final int start_ripup_costs;
  private final int trace_pull_tight_accuracy;
  // Reusable collections to reduce memory churn (thread-safe as each thread has
  // its own BatchAutorouter instance)
  private final List<Item> reusable_autoroute_item_list = new ArrayList<>();
  private final Set<Item> reusable_handled_items = new TreeSet<>();
  protected RoutingJob job;
  private int totalItemsRouted = 0;
  /**
   * Time when the routing session started.
   */
  private Random random;
  /**
   * Used to draw the airline of the current routed incomplete.
   */
  private FloatLine air_line;
  /**
   * Initial number of unrouted nets at the start of the routing session.
   */
  private int initialUnroutedCount;
  /**
   * Time when the routing session started.
   */
  private Instant sessionStartTime;
  private long lastBoardUpdateTimestamp = 0;
  /**
   * Memoized net priorities. calculateNetPriority walks every connectable item on
   * the net, so it must not be recomputed on each comparison during the sort.
   */
  private final Map<Integer, Integer> netPriorityCache = new HashMap<>();
  /** Upper-cased net names, built lazily; see {@link #netNameSetUpper()}. */
  private Set<String> netNamesUpper;

  /**
   * Converts millimetres to this board's coordinate units. Board units come from
   * the DSN resolution and are NOT always microns, so distance thresholds must be
   * derived rather than written as literals.
   */
  private double mmToBoardUnits(double p_mm) {
    return p_mm * board.communication.get_resolution(Unit.MM);
  }

  public BatchAutorouter(RoutingJob job) {
    this(job.thread, job.board, job.routerSettings, !job.routerSettings.isFanoutEnabled(), true,
        job.routerSettings.get_start_ripup_costs(), job.routerSettings.trace_pull_tight_accuracy);
    this.job = job;
  }

  public BatchAutorouter(StoppableThread p_thread, RoutingBoard board, RouterSettings settings,
      boolean p_remove_unconnected_vias, boolean p_with_preferred_directions, int p_start_ripup_costs,
      int p_pull_tight_accuracy) {
    super(p_thread, board, settings);

    this.random = new Random(0);

    this.remove_unconnected_vias = p_remove_unconnected_vias;
    if (p_with_preferred_directions) {
      this.trace_cost_arr = this.settings.get_trace_cost_arr();
    } else {
      // remove preferred direction
      this.trace_cost_arr = new AutorouteControl.ExpansionCostFactor[this.board.get_layer_count()];
      for (int i = 0; i < this.trace_cost_arr.length; i++) {
        double curr_min_cost = this.settings.get_preferred_direction_trace_costs(i);
        this.trace_cost_arr[i] = new AutorouteControl.ExpansionCostFactor(curr_min_cost, curr_min_cost);
      }
    }

    this.start_ripup_costs = p_start_ripup_costs;
    this.trace_pull_tight_accuracy = p_pull_tight_accuracy;
    this.retain_autoroute_database = false;
  }

  /**
   * Auto-routes ripup passes until the board is completed or the auto-router is
   * stopped by the user, or if p_max_pass_count is exceeded. Is currently used in
   * the optimize via batch pass. Returns the
   * number of passes to complete the board or p_max_pass_count + 1, if the board
   * is not completed.
   */
  public static int autoroute_passes_for_optimizing_item(RoutingJob job, int p_max_pass_count, int p_ripup_costs,
      int trace_pull_tight_accuracy, boolean p_with_preferred_directions,
      RoutingBoard updated_routing_board, RouterSettings routerSettings) {
    BatchAutorouter router_instance = new BatchAutorouter(job.thread, updated_routing_board, routerSettings, true,
        p_with_preferred_directions, p_ripup_costs, trace_pull_tight_accuracy);
    router_instance.job = job;

    boolean still_unrouted_items = true;
    int curr_pass_no = 1;
    while (still_unrouted_items && !job.thread.is_stop_auto_router_requested() && curr_pass_no <= p_max_pass_count) {
      // Phase 4: multi-threaded ripup pass. OPT-IN ONLY.
      // autoroute_pass_multi_thread is still a work in progress (see its javadoc),
      // so it must not be selected implicitly from the thread count -- maxThreads
      // defaults to (cores - 1), which would silently enable it for almost everyone.
      boolean useMultiThread = routerSettings.isParallelPassesEnabled()
          && routerSettings.maxThreads != null && routerSettings.maxThreads > 1;
      if (useMultiThread) {
        still_unrouted_items = router_instance.autoroute_pass_multi_thread(curr_pass_no);
      } else {
        still_unrouted_items = router_instance.autoroute_pass(curr_pass_no);
      }
      if (still_unrouted_items && !job.thread.is_stop_auto_router_requested() && updated_routing_board == null) {
      }
      ++curr_pass_no;
    }
    router_instance.remove_tails(Item.StopConnectionOption.NONE);

    // Post-routing optimization: stub minimization (remove unused trace stubs).
    // OPT-IN ONLY -- this pass removes copper, and its endpoint test compares exact
    // corner coordinates, so it cannot tell a dangling stub from a T-junction or a
    // trace terminating on a pad edge. Never run it unless explicitly requested.
    if (router_instance.settings.isMinimizeStubsEnabled()) {
      router_instance.minimize_stubs();
    }

    if (!still_unrouted_items) {
      --curr_pass_no;
    }
    return curr_pass_no;
  }

  private static Point[] getImpactedPoints(Item item) {
    if (item instanceof Trace trace) {
      return new Point[] { trace.first_corner(), trace.last_corner() };
    }
    if (item instanceof Via via) {
      return new Point[] { via.get_center() };
    }
    if (item instanceof Pin pin) {
      return new Point[] { pin.get_center() };
    }
    if (item instanceof DrillItem drillItem) {
      return new Point[] { drillItem.get_center() };
    }
    return new Point[0];
  }

  private static float getCpuSecondsSnapshot(RoutingJob job) {
    if (job == null || job.resourceUsage == null) {
      return 0f;
    }
    return job.resourceUsage.cpuTimeUsed;
  }

  /**
   * Auto-routes one ripup pass of all items of the board. Returns false, if the
   * board is already completely routed.
   */

  private static float getAllocatedMemoryMbSnapshot(RoutingJob job) {
    if (job == null || job.resourceUsage == null) {
      return 0f;
    }
    return job.resourceUsage.maxMemoryUsed;
  }

  private static float getPeakHeapMbSnapshot(RoutingJob job) {
    if (job == null || job.resourceUsage == null) {
      return 0f;
    }
    return job.resourceUsage.peakMemoryUsed;
  }

  private static float sampleCurrentThreadCpuSeconds() {
    try {
      ThreadMXBean threadMxBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
      long cpuNanos = threadMxBean.getThreadCpuTime(Thread.currentThread().threadId());
      return cpuNanos < 0 ? -1f : cpuNanos / 1_000_000_000.0f;
    } catch (Throwable t) {
      return -1f;
    }
  }

  private static float sampleCurrentThreadAllocatedMb() {
    try {
      ThreadMXBean threadMxBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
      threadMxBean.setThreadAllocatedMemoryEnabled(true);
      long allocatedBytes = threadMxBean.getThreadAllocatedBytes(Thread.currentThread().threadId());
      return allocatedBytes < 0 ? -1f : allocatedBytes / (1024.0f * 1024.0f);
    } catch (Throwable t) {
      return -1f;
    }
  }

  private static float sampleHeapUsageMb() {
    try {
      long heapUsed = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
      return heapUsed / (1024.0f * 1024.0f);
    } catch (Throwable t) {
      return 0f;
    }
  }

  private boolean shouldFireBoardUpdate() {
    long currentTime = System.currentTimeMillis();
    if (currentTime - lastBoardUpdateTimestamp > 250) { // Limit updates to 4 times per second (250ms)
      lastBoardUpdateTimestamp = currentTime;
      return true;
    }
    return false;
  }

  /**
   * Calculate routing priority for a net. Lower value = route first.
   * Priority order: GND (0) > VCC (1) > high density (2-N) > low density (100+)
   */
  private int calculateNetPriority(int p_net_no) {
    Integer cached = netPriorityCache.get(p_net_no);
    if (cached != null) {
      return cached;
    }
    int result = computeNetPriority(p_net_no);
    netPriorityCache.put(p_net_no, result);
    return result;
  }

  private int computeNetPriority(int p_net_no) {
    Net net = board.rules.nets.get(p_net_no);
    if (net == null || net.name == null) {
      return 100; // Unknown or unnamed net, low priority
    }

    // Calculate trace width for this net (average of existing traces)
    int max_width = getNetTraceWidth(p_net_no);

    // GND net gets highest priority (0), with width + thermal sub-priority
    if (net.name.toUpperCase().contains("GND")) {
      // Wider GND traces: priority 0 + width factor (0.0-0.9)
      // Narrower GND traces: priority 0.5-0.9
      double width_factor = Math.min(0.9, max_width / 10000.0);
      // Thermal factor: GND is thermal sink, route first for heat dissipation
      double thermal_factor = 0.1; // GND gets slight thermal boost
      return (int) ((width_factor + thermal_factor) * 10);
    }

    // VCC/POWER nets get second priority (10), prioritize by width + thermal awareness
    String name = net.name.toUpperCase();
    if (name.contains("VCC") || name.contains("POWER") || name.contains("VDD") || name.contains("VSS")) {
      double width_factor = Math.min(0.9, max_width / 10000.0);
      // Thermal factor: high-power traces need early routing for thermal distribution
      double thermal_factor = isHighPowerNet(name) ? 0.2 : 0.0;
      return 10 + (int) ((width_factor + thermal_factor) * 10); // 10-19 range
    }

    // Signal integrity tier: critical nets (clock, reset, high-speed) get priority 20-29
    // These route after power but before regular signals to minimize crosstalk
    if (isCriticalSignal(name)) {
      int pin_count = board.connectable_item_count(p_net_no);
      double width_factor = Math.min(0.5, max_width / 20000.0);
      return Math.max(20, 25 - (int) (width_factor * 5)); // 20-25 range
    }

    // Differential pair routing: DP/DM pair members route together with length matching
    // Priority 26-28 to route paired signals consecutively (reduces skew)
    if (isDifferentialPairMember(name)) {
      return 26; // Route differential pairs early to maintain tight coupling
    }

    // Congestion prediction: high-density nets route first to reduce space contention
    // Route dense nets early to prevent congestion lock-out in later passes
    int item_count = board.connectable_item_count(p_net_no);
    if (item_count > 50) {
      // Very dense nets (50+ items): priority 27 (ultra-congested, route early)
      return 27;
    } else if (item_count > 30) {
      // Dense nets (30-50 items): priority 28 (congested, route early)
      return 28;
    }

    // Regular nets: sort by pin count (density) + width
    // More pins = higher priority, wider traces = higher priority
    int pin_count = item_count;
    double width_factor = Math.min(0.5, max_width / 20000.0); // Width contributes 0-5 points
    return Math.max(30, (int) (200 - pin_count - (width_factor * 10)));
  }

  /**
   * Get maximum trace width for net (in board units).
   * Scans all traces on the net and returns the widest one.
   */
  private int getNetTraceWidth(int p_net_no) {
    int max_width = 0;
    Collection<Item> net_items = board.get_connectable_items(p_net_no);
    for (Item item : net_items) {
      if (item instanceof Trace trace) {
        int trace_width = trace.get_half_width() * 2;
        max_width = Math.max(max_width, trace_width);
      }
    }
    return max_width;
  }

  /**
   * Identify critical signal nets (clock, reset, high-speed).
   * These nets benefit from earlier routing to minimize crosstalk and jitter.
   */
  private boolean isCriticalSignal(String p_net_name) {
    String[] tokens = netNameTokens(p_net_name);
    // Clocks. "CK" is accepted only as a whole token: as a substring it also
    // matches BACKLIGHT, TRACK, LOCK and CHECK.
    if (hasToken(tokens, "CLK", "CK", "CLOCK", "XTAL", "XIN", "XOUT", "OSC",
        "MCLK", "SCLK", "BCLK", "LRCLK", "REFCLK")) {
      return true;
    }
    // Resets. "RES" is deliberately NOT accepted -- it also spells RESERVED,
    // RESISTOR and RESULT, none of which are timing-critical.
    if (hasToken(tokens, "RST", "RSTN", "NRST", "RESET", "RESETN", "NRESET")) {
      return true;
    }
    return false;
  }

  /**
   * Splits a net name into upper-case alphanumeric tokens, so that matching is
   * done on name components rather than on substrings. "/U3/USB_DP" yields
   * [U3, USB, DP].
   */
  private static String[] netNameTokens(String p_net_name) {
    return p_net_name.toUpperCase().split("[^A-Z0-9]+");
  }

  /**
   * True if any token equals one of p_keywords, ignoring a trailing index so
   * that "CLK2" still matches "CLK".
   */
  private static boolean hasToken(String[] p_tokens, String... p_keywords) {
    for (String token : p_tokens) {
      if (token.isEmpty()) {
        continue;
      }
      String bare = token.replaceAll("[0-9]+$", "");
      for (String keyword : p_keywords) {
        if (token.equals(keyword) || bare.equals(keyword)) {
          return true;
        }
      }
    }
    return false;
  }

  /** Upper-cased set of every net name on the board, built once on first use. */
  private Set<String> netNameSetUpper() {
    if (netNamesUpper == null) {
      netNamesUpper = new HashSet<>();
      for (int i = 1; i <= board.rules.nets.max_net_no(); i++) {
        Net n = board.rules.nets.get(i);
        if (n != null && n.name != null) {
          netNamesUpper.add(n.name.toUpperCase());
        }
      }
    }
    return netNamesUpper;
  }

  /**
   * Identify high-power nets that generate significant heat dissipation.
   * These benefit from early routing to establish thermal distribution paths.
   */
  private boolean isHighPowerNet(String p_net_name) {
    String name = p_net_name.toUpperCase();
    // High-current supply rails (3V3, 5V, 12V, 48V, VBAT, VCIN)
    if (name.contains("3V3") || name.contains("5V") || name.contains("12V") ||
        name.contains("48V") || name.contains("VBAT") || name.contains("VCIN") ||
        name.contains("VREF") || name.contains("BIAS")) {
      return true;
    }
    // High-current distribution points (PGND, SGND for separate power domains)
    if (name.contains("PGND") || name.contains("SGND")) {
      return true;
    }
    return false;
  }

  /**
   * Identify differential pair members (DP/DM pairs).
   * These route together with tight coupling to minimize skew and maintain impedance.
   */
  private boolean isDifferentialPairMember(String p_net_name) {
    String name = p_net_name.toUpperCase();

    // A polarity suffix only means "differential pair" if the opposite member
    // actually exists on this board. Without that check an active-low enable
    // ("EN_N") or a supply return ("VBUS-") is misread as a pair member.
    String[][] suffix_pairs = {{"_P", "_N"}, {"_DP", "_DM"}, {"+", "-"}};
    for (String[] pair : suffix_pairs) {
      for (int i = 0; i < 2; i++) {
        String self = pair[i];
        String other = pair[1 - i];
        if (name.endsWith(self)) {
          String sibling = name.substring(0, name.length() - self.length()) + other;
          if (netNameSetUpper().contains(sibling)) {
            return true;
          }
        }
      }
    }

    // Named differential standards, matched on whole tokens only. As substrings,
    // "DP" and "DM" also match ADP, SDP and MDMA.
    return hasToken(netNameTokens(name),
        "DP", "DM", "USB", "HDMI", "LVDS", "DIFF", "CML", "TMDS", "MIPI");
  }

  /**
   * Minimize stubs: remove unused trace endpoints that don't connect to pads/vias.
   * Reduces signal integrity degradation from stub reflections on high-speed nets.
   * Post-routing optimization to clean up dangling trace segments.
   */
  private void minimize_stubs() {
    job.logInfo("Stub minimization: removing unused trace stubs");
    int stubs_removed = 0;

    // Contact index, built in a SINGLE pass over the item list. The previous
    // implementation rescanned every item twice per trace, which made this pass
    // O(N^2) in the item count.
    // Keys are packed coordinates rather than Point objects: IntPoint overrides
    // equals() but not hashCode(), so it cannot be used as a hash-map key.
    Map<Integer, Map<Long, Integer>> trace_ends_per_layer = new HashMap<>();
    Map<Long, Integer> via_or_pin_at = new HashMap<>();
    List<PolylineTrace> traces_to_check = new ArrayList<>();

    Iterator<UndoableObjects.UndoableObjectNode> it = board.item_list.start_read_object();
    for (;;) {
      UndoableObjects.Storable curr_ob = board.item_list.read_object(it);
      if (curr_ob == null) {
        break;
      }
      if (curr_ob instanceof Trace trace) {
        if (trace instanceof PolylineTrace polyline_trace) {
          traces_to_check.add(polyline_trace);
        }
        Map<Long, Integer> at_layer =
            trace_ends_per_layer.computeIfAbsent(trace.get_layer(), k -> new HashMap<>());
        Long first_key = pointKey(trace.first_corner());
        Long last_key = pointKey(trace.last_corner());
        if (first_key != null) {
          at_layer.merge(first_key, 1, Integer::sum);
        }
        if (last_key != null && !last_key.equals(first_key)) {
          at_layer.merge(last_key, 1, Integer::sum);
        }
      } else if (curr_ob instanceof Via via) {
        Long key = pointKey(via.get_center());
        if (key != null) {
          via_or_pin_at.merge(key, 1, Integer::sum);
        }
      } else if (curr_ob instanceof Pin pin) {
        Long key = pointKey(pin.get_center());
        if (key != null) {
          via_or_pin_at.merge(key, 1, Integer::sum);
        }
      }
    }

    for (PolylineTrace trace : traces_to_check) {
      Long first_key = pointKey(trace.first_corner());
      Long last_key = pointKey(trace.last_corner());
      if (first_key == null || last_key == null) {
        // Coordinate we cannot index. Be conservative and never remove copper on
        // the strength of an endpoint we could not evaluate.
        continue;
      }

      Map<Long, Integer> at_layer =
          trace_ends_per_layer.getOrDefault(trace.get_layer(), Map.of());

      // Subtract this trace's own contribution: the index counted it too, and the
      // question is what ELSE lands on that endpoint.
      int first_end_contacts = at_layer.getOrDefault(first_key, 0) - 1
          + via_or_pin_at.getOrDefault(first_key, 0);
      int last_end_contacts = at_layer.getOrDefault(last_key, 0)
          - (last_key.equals(first_key) ? 0 : 1)
          + via_or_pin_at.getOrDefault(last_key, 0);

      // A stub is an end with NO other item on it. The old test was "== 1", but a
      // normal pad->via segment has exactly one contact at each end, so that test
      // matched correctly routed copper.
      Net trace_net = trace.net_count() > 0 ? board.rules.nets.get(trace.get_net_no(0)) : null;
      if ((first_end_contacts == 0 || last_end_contacts == 0)
          && trace_net != null && trace_net.name != null
          && isDifferentialPairMember(trace_net.name)) {
        try {
          board.remove_item(trace);
          stubs_removed++;
        } catch (Exception e) {
          // Ignore removal failures (item locked, etc)
        }
      }
    }

    if (stubs_removed > 0) {
      job.logInfo("Stub minimization: removed " + stubs_removed + " unused trace stubs");
    }
  }

  /**
   * Packs a point into a hash-map key. Returns null for coordinate types this
   * cannot represent exactly, so callers can treat them as "unknown".
   * <p>
   * Needed because {@link app.freerouting.geometry.planar.IntPoint} overrides
   * {@code equals} but not {@code hashCode}, so it breaks the hash contract and
   * cannot be used as a key directly.
   */
  private static Long pointKey(app.freerouting.geometry.planar.Point p_point) {
    if (p_point instanceof app.freerouting.geometry.planar.IntPoint int_point) {
      return (((long) int_point.x) << 32) ^ (int_point.y & 0xffffffffL);
    }
    return null;
  }

  private List<Item> getAutorouteItems(RoutingBoard board) {
    // Reuse instance collections to reduce memory allocation
    reusable_autoroute_item_list.clear();
    reusable_handled_items.clear();
    List<Item> autoroute_item_list = reusable_autoroute_item_list;
    Set<Item> handled_items = reusable_handled_items;
    Iterator<UndoableObjects.UndoableObjectNode> it = board.item_list.start_read_object();
    for (;;) {
      UndoableObjects.Storable curr_ob = board.item_list.read_object(it);
      if (curr_ob == null) {
        break;
      }
      if (curr_ob instanceof Connectable && curr_ob instanceof Item curr_item) {
        // This is a connectable item, like PolylineTrace or Pin
        if (!curr_item.is_routable()) {
          if (!handled_items.contains(curr_item)) {

            // Let's go through all nets of this item
            for (int i = 0; i < curr_item.net_count(); i++) {
              int curr_net_no = curr_item.get_net_no(i);
              Set<Item> connected_set = curr_item.get_connected_set(curr_net_no);
              for (Item curr_connected_item : connected_set) {
                if (curr_connected_item.net_count() <= 1) {
                  handled_items.add(curr_connected_item);
                }
              }
              int net_item_count = board.connectable_item_count(curr_net_no);

              // If the item is not connected to all other items of the net, we add it to the
              // auto-router's to-do list
              if ((connected_set.size() < net_item_count) && (!curr_item.has_ignored_nets())) {
                Net net = board.rules.nets.get(curr_net_no);
                // For plane nets: skip items whose connected set already contains a
                // ConductionArea (copper pour). These items would immediately return
                // CONNECTED_TO_PLANE in autoroute_item(), wasting time and causing
                // spurious normalize_traces() failures on nearby stub geometry.
                // Items not yet connected to the plane are still enqueued so they can
                // be routed to the pour in this pass.
                if (net != null && net.contains_plane()) {
                  boolean alreadyConnectedToPlane = connected_set.stream()
                      .anyMatch(connectedItem -> connectedItem instanceof ConductionArea);
                  if (alreadyConnectedToPlane) {
                    continue;
                  }
                }
                autoroute_item_list.add(curr_item);
                String netName = (net != null) ? net.name : "net#" + curr_net_no;
                FRLogger.debug("Queuing item for routing: " + curr_item.getClass().getSimpleName() + " on net '"
                    + netName + "' (connected: " + connected_set.size() + "/" + net_item_count + ")");
              }
            }
          }
        }
      }
    }
    return autoroute_item_list;
  }

  /**
   * Multi-threaded version of the router that routes one ripup pass of all items
   * of the board. WARNING: this version is not working as intended yet. It is a
   * work in progress.
   * <p>
   * Returns false if the board is already completely routed.
   */
  private boolean autoroute_pass_multi_thread(int p_pass_no) {
    try {
      List<Item> autoroute_item_list = getAutorouteItems(this.board);

      // If there are no items to route, we're done
      if (autoroute_item_list.isEmpty()) {
        this.air_line = null;
        return false;
      }

      boolean useSlowAlgorithm = false;

      BatchAutorouterThread[] autorouterThreads = new BatchAutorouterThread[job.routerSettings.maxThreads];
      BoardHistory bh = new BoardHistory(job.routerSettings.scoring);

      // Start multiple instances of the following part in parallel, wait for the
      // results and keep only the best one

      // Prepare the threads
      for (int threadIndex = 0; threadIndex < job.routerSettings.maxThreads; threadIndex++) {
        // deep copy the board
        PerformanceProfiler.start("board.deepCopy");
        RoutingBoard clonedBoard = this.board.deepCopy();
        PerformanceProfiler.end("board.deepCopy");

        // clone the auto-route item list to avoid concurrent modification
        List<Item> clonedAutorouteItemList = new ArrayList<>(getAutorouteItems(clonedBoard));

        // shuffle the items to route
        shuffle(clonedAutorouteItemList, this.random);

        autorouterThreads[threadIndex] = new BatchAutorouterThread(clonedBoard, clonedAutorouteItemList, p_pass_no,
            job.routerSettings, this.start_ripup_costs,
            this.trace_pull_tight_accuracy, this.remove_unconnected_vias, true);
        autorouterThreads[threadIndex].setName("Router thread #" + p_pass_no + "." + ThreadIndexToLetter(threadIndex));
        autorouterThreads[threadIndex].setDaemon(true);
        autorouterThreads[threadIndex].setPriority(Thread.MIN_PRIORITY);
      }

      // Update the board on the GUI only based on the first thread
      autorouterThreads[0].addBoardUpdatedEventListener(new BoardUpdatedEventListener() {
        @Override
        public void onBoardUpdatedEvent(BoardUpdatedEvent event) {
          air_line = autorouterThreads[0].latest_air_line;
          fireBoardUpdatedEvent(event.getBoardStatistics(), event.getRouterCounters(), event.getBoard());
        }
      });

      // Start the threads
      for (int threadIndex = 0; threadIndex < job.routerSettings.maxThreads; threadIndex++) {
        // start the thread
        autorouterThreads[threadIndex].start();
      }

      // Wait for the threads to finish
      for (int threadIndex = 0; threadIndex < job.routerSettings.maxThreads; threadIndex++) {
        BatchAutorouterThread autorouterThread = autorouterThreads[threadIndex];

        // wait for the thread to finish
        try {
          autorouterThread.join(TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP);
        } catch (InterruptedException e) {
          job.logError("Autorouter thread #" + p_pass_no + "." + ThreadIndexToLetter(threadIndex) + " was interrupted",
              e);
          this.thread.requestStop();
          break;
        }

        bh.add(autorouterThread.getBoard());

        // calculate the new board score
        BoardStatistics clonedBoardStatistics = autorouterThread
            .getBoard()
            .get_statistics();
        float clonedBoardScore = clonedBoardStatistics.getNormalizedScore(job.routerSettings.scoring);

        job.logDebug("Router thread #" + p_pass_no + "." + ThreadIndexToLetter(threadIndex) + " finished with score: "
            + FRLogger.formatScore(clonedBoardScore,
                clonedBoardStatistics.connections.incompleteCount,
                clonedBoardStatistics.clearanceViolations.totalCount));

        // Aggregate resource usage
        job.resourceUsage.cpuTimeUsed += autorouterThread.cpuTimeUsed;
        job.resourceUsage.maxMemoryUsed += autorouterThread.maxMemoryUsed;
      }

      BatchAutorouterThread bestThread = autorouterThreads[0];
      float bestScore = -Float.MAX_VALUE;

      // Find the best thread
      for (int i = 0; i < job.routerSettings.maxThreads; i++) {
        BoardStatistics stats = autorouterThreads[i].getBoard().get_statistics();
        float score = stats.getNormalizedScore(job.routerSettings.scoring);
        if (score > bestScore) {
          bestScore = score;
          bestThread = autorouterThreads[i];
        }
      }

      this.board = bh.restoreBestBoard();
      bh.clear();

      // Check if we made any progress
      boolean anyProgress = bestThread.getRoutedCount() > 0 || bestThread.getFailedCount() > 0;

      // We are done with this pass
      this.air_line = null;
      return anyProgress;
    } catch (Exception e) {
      job.logError("Something went wrong during the auto-routing", e);
      this.air_line = null;
      return false;
    }
  }

  /**
   * Auto-routes one ripup pass of all items of the board. Returns false, if the
   * board is already completely routed.
   */
  private boolean autoroute_pass(int p_pass_no) {
    long passStartTime = System.currentTimeMillis();
    try {
      List<Item> autoroute_item_list = getAutorouteItems(this.board);

      // If there are no items to route, we're done
      if (autoroute_item_list.isEmpty()) {
        this.air_line = null;
        return false;
      }

      int items_to_go_count = autoroute_item_list.size();
      int ripped_item_count = 0;
      int not_routed = 0;
      int routed = 0;
      int skipped = 0;
      BoardStatistics stats = board.get_statistics();
      RouterCounters routerCounters = new RouterCounters();
      routerCounters.phase = "autoroute";
      routerCounters.passCount = p_pass_no;
      routerCounters.queuedToBeRoutedCount = items_to_go_count;
      routerCounters.skippedCount = skipped;
      routerCounters.rippedCount = ripped_item_count;
      routerCounters.failedToBeRoutedCount = not_routed;
      routerCounters.routedCount = routed;
      DesignRulesChecker tempDrc = new DesignRulesChecker(board, null);
      tempDrc.calculateAllIncompletes();
      routerCounters.incompleteCount = tempDrc.getIncompleteCount();

      // Log incomplete details for debugging
      if (routerCounters.incompleteCount > 0) {
        job.logDebug("Pass #" + p_pass_no + ": " + routerCounters.incompleteCount + " incompletes across "
            + items_to_go_count + " items to route");
        for (int netNo = 1; netNo <= board.rules.nets.max_net_no(); netNo++) {
          int netIncompletes = tempDrc.getIncompleteCount(netNo);
          if (netIncompletes > 0) {
            Net net = board.rules.nets.get(netNo);
            String netName = (net != null) ? net.name : "net#" + netNo;
            job.logDebug("  Net '" + netName + "' has " + netIncompletes + " incomplete(s)");
          }
        }
      }

      this.fireBoardUpdatedEvent(stats, routerCounters, this.board);

      // TODO: Start mutliple instances of the following part in parallel, wait for
      // the results and keep the best one

      // Sort items by net priority: GND first, then VCC, then by pin density.
      // This reduces rip-ups by routing power/ground and dense nets while space is abundant.
      autoroute_item_list.sort((item1, item2) -> {
        // For items with multiple nets, find the best (lowest priority) net
        int minPriority1 = Integer.MAX_VALUE;
        for (int i = 0; i < item1.net_count(); i++) {
          minPriority1 = Math.min(minPriority1, calculateNetPriority(item1.get_net_no(i)));
        }

        int minPriority2 = Integer.MAX_VALUE;
        for (int i = 0; i < item2.net_count(); i++) {
          minPriority2 = Math.min(minPriority2, calculateNetPriority(item2.get_net_no(i)));
        }

        if (minPriority1 != minPriority2) {
          return Integer.compare(minPriority1, minPriority2);
        }
        // Tie-break by item ID for determinism
        return Integer.compare(item1.get_id_no(), item2.get_id_no());
      });

      // Let's go through all items to route
      for (Item curr_item : autoroute_item_list) {
        // If the user requested to stop the auto-router, we stop it
        if (this.thread.is_stop_auto_router_requested()) {
          break;
        }

        // Let's go through all nets of this item
        for (int i = 0; i < curr_item.net_count(); i++) {
          // If the user requested to stop the auto-router, we stop it
          if (this.thread.is_stop_auto_router_requested()) {
            break;
          }

          if (this.settings.maxItems != null && this.totalItemsRouted >= this.settings.maxItems) {
            job.logInfo("Max items limit reached (" + this.settings.maxItems + "). Stopping auto-router.");
            // Call requestStop() (sets ALL) instead of request_stop_auto_router() (sets
            // AUTO_ROUTER_ONLY) so the optimizer phase is also skipped.  maxItems is a
            // debugging/test ceiling meant to bound the entire routing job; running the
            // optimizer on a deliberately-incomplete board is not useful and prevents the
            // process from terminating promptly.
            this.thread.requestStop();
            break;
          }
          this.totalItemsRouted++;

          // We visually mark the area of the board, which is changed by the auto-router
          board.start_marking_changed_area();

          // Do the auto-routing step for this item (typically PolylineTrace or Pin)
          // Use a fresh set per item to mirror v1.9 behavior and avoid cross-item side effects.
          SortedSet<Item> ripped_item_list = new TreeSet<>();
          Map<Item, Integer> ripped_item_costs = new LinkedHashMap<>();
          int netItemsBefore = board.get_connectable_items(curr_item.get_net_no(i)).size();
          PerformanceProfiler.start("autoroute_item");
          var autorouterResult = autoroute_item(curr_item, curr_item.get_net_no(i), ripped_item_list, ripped_item_costs, p_pass_no);
          PerformanceProfiler.end("autoroute_item");
          if (!ripped_item_list.isEmpty()) {
            for (Item rippedItem : ripped_item_list) {
              StringBuilder rippedNets = new StringBuilder();
              for (int netIx = 0; netIx < rippedItem.net_count(); netIx++) {
                if (netIx > 0) {
                  rippedNets.append('|');
                }
                rippedNets.append(rippedItem.get_net_no(netIx));
              }
              int ripupCost = ripped_item_costs.getOrDefault(rippedItem, -1);
              FRLogger.trace(
                  "BatchAutorouter.autoroute_pass",
                  "compare_trace_ripped_item",
                  "source_item=" + curr_item.get_id_no()
                      + ", source_net=" + curr_item.get_net_no(i)
                      + ", ripped_id=" + rippedItem.get_id_no()
                      + ", ripped_type=" + rippedItem.getClass().getSimpleName()
                      + ", ripped_net_count=" + rippedItem.net_count()
                      + ", ripped_nets=" + rippedNets
                      + ", ripup_cost=" + ripupCost,
                  "Net #" + curr_item.get_net_no(i) + ",Item #" + curr_item.get_id_no(),
                  getImpactedPoints(rippedItem));
            }
          }
          if (FRLogger.isTraceEnabled()) {
            DesignRulesChecker innerDrc = new DesignRulesChecker(board, null);
            innerDrc.calculateAllIncompletes();
            int tempIncomp = innerDrc.getIncompleteCount();
            int tempNetIncomp = innerDrc.getIncompleteCount(curr_item.get_net_no(i));
            int netItemsAfter = board.get_connectable_items(curr_item.get_net_no(i)).size();
            int maxItemId = board.communication.id_no_generator.max_generated_no();
            FRLogger.trace(
                "BatchAutorouter.autoroute_pass",
                "compare_trace_route_item",
                "Routing " + curr_item.getClass().getSimpleName() + " -> result=" + autorouterResult.state
                    + ", details=" + autorouterResult.details
                    + ", incompletes=" + tempIncomp + ", netIncomplete=" + tempNetIncomp
                    + ", ripped=" + ripped_item_list.size() + ", netItems="
                    + netItemsBefore + "->" + netItemsAfter
                    + ", maxItemId=" + maxItemId,
                "Net #" + curr_item.get_net_no(i) + ",Item #" + curr_item.get_id_no() + ",Type="
                    + curr_item.getClass().getSimpleName(),
                getImpactedPoints(curr_item));
          }

          if (curr_item.get_net_no(i) == 94) {
            FRLogger.trace(
                "BatchAutorouter.autoroute_pass",
                "compare_trace_dump_net_items",
                "Dump net 94 items",
                "Net #94",
                new Point[0]);
            for (Item nItem : board.get_connectable_items(94)) {
              if (nItem instanceof Trace) {
                Trace t = (Trace) nItem;
                FRLogger.trace(
                    "BatchAutorouter.autoroute_pass",
                    "compare_trace_dump_net_item",
                    "Trace layer=" + t.get_layer() + " corners=" + t.first_corner() + " to " + t.last_corner(),
                    "Net #94,Item #" + t.get_id_no() + ",Type=Trace",
                    new Point[] { t.first_corner(), t.last_corner() });
              } else if (nItem instanceof Via) {
                Via v = (Via) nItem;
                FRLogger.trace(
                    "BatchAutorouter.autoroute_pass",
                    "compare_trace_dump_net_item",
                    "Via center=" + v.get_center(),
                    "Net #94,Item #" + v.get_id_no() + ",Type=Via",
                    new Point[] { v.get_center() });
              } else if (nItem instanceof Pin) {
                Pin p = (Pin) nItem;
                FRLogger.trace(
                    "BatchAutorouter.autoroute_pass",
                    "compare_trace_dump_net_item",
                    "Pin center=" + p.get_center() + " name=" + p.name() + " comp=" + p.component_name(),
                    "Net #94,Item #" + p.get_id_no() + ",Type=Pin",
                    new Point[] { p.get_center() });
              } else {
                FRLogger.trace(
                    "BatchAutorouter.autoroute_pass",
                    "compare_trace_dump_net_item",
                    "Item " + nItem.getClass().getSimpleName(),
                    "Net #94,Item #" + nItem.get_id_no() + ",Type=" + nItem.getClass().getSimpleName(),
                    getImpactedPoints(nItem));
              }
            }
          }

          if (autorouterResult.state == AutorouteAttemptState.ROUTED) {
            // The item was successfully routed
            ++routed;
          } else if ((autorouterResult.state == AutorouteAttemptState.ALREADY_CONNECTED)
              || (autorouterResult.state == AutorouteAttemptState.NO_UNCONNECTED_NETS)
              || (autorouterResult.state == AutorouteAttemptState.CONNECTED_TO_PLANE)) {
            // The item doesn't need to be routed
            ++skipped;
          } else {
            Net net = board.rules.nets.get(curr_item.get_net_no(i));
            String netName = (net != null) ? net.name : "net#" + curr_item.get_net_no(i);

            // Record the failure
            board.failureLog.recordFailure(curr_item, p_pass_no, autorouterResult.state, autorouterResult.details);

            job.logDebug("Autorouter " + autorouterResult.details);
            // Log details when we're down to last few items or item has many failures
            int failureCount = board.failureLog.getFailureCount(curr_item);
            if (items_to_go_count <= 5 || failureCount >= 3) {
              job.logDebug("Pass #" + p_pass_no + ": Failed to route " + curr_item.getClass().getSimpleName()
                  + " on net '" + netName + "' (" + items_to_go_count + " items remaining, "
                  + failureCount + " failures). State: " + autorouterResult.state);
            }
            ++not_routed;
          }
          --items_to_go_count;
          ripped_item_count += ripped_item_list.size();

          if (shouldFireBoardUpdate()) {
            BoardStatistics boardStatistics = board.get_statistics();
            routerCounters.passCount = p_pass_no;
            routerCounters.queuedToBeRoutedCount = items_to_go_count;
            routerCounters.skippedCount = skipped;
            routerCounters.rippedCount = ripped_item_count;
            routerCounters.failedToBeRoutedCount = not_routed;
            routerCounters.routedCount = routed;
            routerCounters.incompleteCount = calculateIncompleteCount(board);
            this.fireBoardUpdatedEvent(boardStatistics, routerCounters, this.board);
          }
        }
      }

      int incompletesBefore = calculateIncompleteCount(board);
      FRLogger.trace(
          "BatchAutorouter.autoroute_pass",
          "compare_trace_remove_tails",
          "Incompletes before remove_tails=" + incompletesBefore,
          "Autorouter pass #" + p_pass_no,
          new Point[0]);

      if (this.remove_unconnected_vias) {
        remove_tails(Item.StopConnectionOption.NONE);
      } else {
        remove_tails(Item.StopConnectionOption.FANOUT_VIA);
      }

      int incompletesAfter = calculateIncompleteCount(board);
      FRLogger.trace(
          "BatchAutorouter.autoroute_pass",
          "compare_trace_remove_tails",
          "Incompletes after remove_tails=" + incompletesAfter,
          "Autorouter pass #" + p_pass_no,
          new Point[0]);

      // Fire final update for this pass
      BoardStatistics boardStatistics = board.get_statistics();
      routerCounters.passCount = p_pass_no;
      routerCounters.queuedToBeRoutedCount = items_to_go_count;
      routerCounters.skippedCount = skipped;
      routerCounters.rippedCount = ripped_item_count;
      routerCounters.failedToBeRoutedCount = not_routed;
      routerCounters.routedCount = routed;
      routerCounters.incompleteCount = calculateIncompleteCount(board);
      this.fireBoardUpdatedEvent(boardStatistics, routerCounters, this.board);

      long passDuration = System.currentTimeMillis() - passStartTime;
      int currentRipupCost = this.start_ripup_costs * p_pass_no;
      PerformanceProfiler.recordPass(p_pass_no, routerCounters.incompleteCount, passDuration, currentRipupCost);

      // We are done with this pass
      this.air_line = null;
      return routed > 0 || not_routed > 0;
    } catch (Exception e) {
      job.logError("Something went wrong during the auto-routing", e);
      this.air_line = null;
      return false;
    }
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
   * Builds a human-readable summary of all unrouted connections on the current board,
   * grouped by net. For each unrouted connection the component and pin names of both
   * endpoints are listed so that the user can identify exactly which connections are
   * missing and address them in their design.
   *
   * <p>Example output:
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

  /**
   * Returns the initial number of unrouted nets at the start of the routing
   * session.
   */
  public int getInitialUnroutedCount() {
    return this.initialUnroutedCount;
  }

  /**
   * Returns the time when the routing session started.
   */
  public Instant getSessionStartTime() {
    return this.sessionStartTime;
  }

  /**
   * Autoroutes ripup passes until the board is completed or the autorouter is
   * stopped by the user. Returns true if the board is completed.
   */
  public boolean runBatchLoop() {
    boolean anyRoutable = false;
    for (int i = 0; i < this.settings.getLayerCount(); i++) {
      if (this.settings.get_layer_active(i)) {
        anyRoutable = true;
        break;
      }
    }
    if (!anyRoutable) {
      FRLogger.warn("Cannot start autorouter: all layers are disabled.");
      this.fireTaskStateChangedEvent(new TaskStateChangedEvent(this, TaskState.CANCELLED, 0, this.board.get_hash()));
      throw new IllegalArgumentException("Cannot start autorouter: all layers are disabled.");
    }

    this.fireTaskStateChangedEvent(new TaskStateChangedEvent(this, TaskState.STARTED, 0, this.board.get_hash()));

    // Capture initial state for session summary
    this.sessionStartTime = Instant.now();
    this.initialUnroutedCount = calculateIncompleteCount(this.board);

    boolean continueAutorouting = true;
    BoardHistory bh = new BoardHistory(job.routerSettings.scoring);

    // Record configuration for profiler
    if (this.settings.getLayerCount() > 0) {
      int layerCount = this.settings.getLayerCount();
      double[] prefCosts = new double[layerCount];
      double[] againstCosts = new double[layerCount];
      for (int i = 0; i < layerCount; i++) {
        prefCosts[i] = this.settings.get_preferred_direction_trace_costs(i);
        againstCosts[i] = this.settings.get_against_preferred_direction_trace_costs(i);
      }
      PerformanceProfiler.recordConfiguration(
          this.settings.get_via_costs(),
          this.settings.get_plane_via_costs(),
          prefCosts,
          againstCosts);
    }

    job.logDebug("Checking fanout pre-pass. settings.fanout.enabled=" + this.settings.isFanoutEnabled() + ", smd_pins=" + this.board.get_smd_pins().size());
    // Run SMD fanout pre-pass when the board has SMD pins and fanout is enabled
    if (this.settings.isFanoutEnabled() && !this.board.get_smd_pins().isEmpty()) {
      float fanoutCpuSecondsStart = sampleCurrentThreadCpuSeconds();
      float fanoutAllocatedMbStart = sampleCurrentThreadAllocatedMb();
      float fanoutPeakHeapMbAtStart = sampleHeapUsageMb();
      final float[] fanoutPeakHeapMbObserved = new float[] { fanoutPeakHeapMbAtStart };
      job.logInfo("Starting fanout pre-pass on board '" + this.board.get_hash() + "' for "
          + this.board.get_smd_pins().size() + " SMD pin" + (this.board.get_smd_pins().size() == 1 ? "" : "s") + ".");
      BatchFanout.FanoutRunSummary fanoutSummary = BatchFanout.fanout_board(this.board, this.settings, this.thread,
          status -> {
        fanoutPeakHeapMbObserved[0] = Math.max(fanoutPeakHeapMbObserved[0], sampleHeapUsageMb());
        RouterCounters fanoutCounters = new RouterCounters();
        fanoutCounters.phase = "fanout";
        fanoutCounters.passCount = status.passNo();
        fanoutCounters.queuedToBeRoutedCount = status.pinsToGo();
        fanoutCounters.routedCount = status.routedCount();
        fanoutCounters.skippedCount = 0;
        fanoutCounters.rippedCount = 0;
        fanoutCounters.failedToBeRoutedCount = status.notRoutedCount() + status.insertErrorCount();
        fanoutCounters.incompleteCount = status.boardStatistics().connections.incompleteCount;
        fanoutCounters.fanoutExtraViasCount = status.extraViasThisPass();
        this.fireBoardUpdatedEvent(status.boardStatistics(), fanoutCounters, this.board);

        if (status.passCompleted()) {
          String boardHash = this.board.get_hash();
          String fanoutMessage = "Fanout pass #" + status.passNo() + " on board '" + boardHash
              + "' completed in " + FRLogger.formatDuration(status.passDurationMillis() / 1000.0)
              + " with " + status.routedCount() + " SMD pin"
              + (status.routedCount() == 1 ? "" : "s") + " fanouted, "
              + status.notRoutedCount() + " not routed, " + status.insertErrorCount() + " insert error"
              + (status.insertErrorCount() == 1 ? "" : "s")
              + ", +" + status.extraViasThisPass() + " extra via"
              + (status.extraViasThisPass() == 1 ? "" : "s")
              + " (" + status.pinsToGo() + " SMD pin"
              + (status.pinsToGo() == 1 ? "" : "s") + " still to check in pass, ripup costs="
              + status.ripupCosts() + ").";
          job.logInfo(fanoutMessage);
        }
      });

      float fanoutCpuSecondsEnd = sampleCurrentThreadCpuSeconds();
      float fanoutAllocatedMbEnd = sampleCurrentThreadAllocatedMb();

      float fanoutCpuSecondsUsed;
      if (fanoutCpuSecondsStart >= 0f && fanoutCpuSecondsEnd >= fanoutCpuSecondsStart) {
        fanoutCpuSecondsUsed = fanoutCpuSecondsEnd - fanoutCpuSecondsStart;
      } else {
        fanoutCpuSecondsUsed = Math.max(0f, getCpuSecondsSnapshot(job));
      }

      float fanoutAllocatedGb;
      if (fanoutAllocatedMbStart >= 0f && fanoutAllocatedMbEnd >= fanoutAllocatedMbStart) {
        fanoutAllocatedGb = (fanoutAllocatedMbEnd - fanoutAllocatedMbStart) / 1024.0f;
      } else {
        fanoutAllocatedGb = Math.max(0f, getAllocatedMemoryMbSnapshot(job)) / 1024.0f;
      }

      float fanoutPeakHeapMb = Math.max(fanoutPeakHeapMbObserved[0], sampleHeapUsageMb());
      fanoutPeakHeapMb = Math.max(fanoutPeakHeapMb, getPeakHeapMbSnapshot(job));
      BatchFanout.EscapeStatistics finalEscape = fanoutSummary.escapeStatistics();
      String fanoutCompletionStatus = this.thread.is_stop_auto_router_requested() ? "interrupted:" : "completed:";
      String fanoutSummaryMessage = String.format(
          "Fanout session %s started with %d total SMD pins, completed in %s, escaped pins: %d/%d (%.1f%%), using %s total CPU seconds, %s GB total allocated, and %s MB peak heap usage.",
          fanoutCompletionStatus,
          finalEscape.totalSmdPins(),
          FRLogger.formatDuration(fanoutSummary.totalDurationMillis() / 1000.0),
          finalEscape.escapedCount(),
          finalEscape.totalSmdPins(),
          finalEscape.escapedPercentage(),
          FRLogger.defaultFloatFormat.format(fanoutCpuSecondsUsed),
          FRLogger.defaultFloatFormat.format(fanoutAllocatedGb),
          FRLogger.defaultFloatFormat.format(fanoutPeakHeapMb));
      job.logInfo(fanoutSummaryMessage);

      // Power trunk routing: route power nets (GND/VCC) from escape vias to main distribution
      if (this.settings.isRoutePowerTrunksEnabled()) {
        route_power_trunks_from_escapes();
      }
    }

    int currentPass = 1;
    int consecutiveNoImprovementPasses = 0;
    boolean fanoutRecoveryApplied = false;
    float lastBestScore = Float.NEGATIVE_INFINITY;   // score at last board-restore or improvement
    float globalBestScore = Float.NEGATIVE_INFINITY; // best score seen across all passes
    int passOfBestScore = 0;                         // pass where globalBestScore was achieved
    int incompleteCountAtBestScore = 0;              // incomplete count when globalBestScore was recorded
    // Track board hashes that have already been routed. If the board does not change between
    // two consecutive passes (same hash at pass start), the router is making no progress and
    // would produce identical decisions with identical ripup budgets — stop immediately rather
    // than waiting for the full stagnation window. This mirrors the v1.9 behaviour and catches
    // the degenerate case where plane-net items repeatedly fail or are inserted+removed each
    // pass without updating the board state.
    Set<String> alreadyRoutedBoardHashes = new java.util.HashSet<>();
    while (continueAutorouting && !this.thread.is_stop_auto_router_requested()) {
      if (job != null && job.state == RoutingJobState.TIMED_OUT) {
        this.thread.request_stop_auto_router();
      }

      String currentBoardHash = this.board.get_hash();

      // Same-hash stop: if this board state has already been routed in a previous pass, no
      // further progress is possible. Stop before wasting another pass.
      if (alreadyRoutedBoardHashes.contains(currentBoardHash)) {
        job.logInfo("Board state has not changed since pass #" + (currentPass - 1)
            + " (hash " + currentBoardHash + "). The auto-router cannot make further progress; stopping.");
        thread.request_stop_auto_router();
        break;
      }
      alreadyRoutedBoardHashes.add(currentBoardHash);

      if (currentPass > this.settings.maxPasses) {
        thread.request_stop_auto_router();
        break;
      }

      if (job != null) {
        job.setCurrentPass(currentPass);
      }

      this.fireTaskStateChangedEvent(
          new TaskStateChangedEvent(this, TaskState.RUNNING, currentPass, currentBoardHash));

      float boardScoreBefore = new BoardStatistics(this.board).getNormalizedScore(job.routerSettings.scoring);
      bh.add(this.board);

      FRLogger.traceEntry("BatchAutorouter.autoroute_pass #" + currentPass + " on board '" + currentBoardHash + "'");

      continueAutorouting = autoroute_pass(currentPass);

      BoardStatistics boardStatisticsAfter = new BoardStatistics(this.board);
      float boardScoreAfter = boardStatisticsAfter.getNormalizedScore(job.routerSettings.scoring);

      if ((bh.size() >= STOP_AT_PASS_MINIMUM) || (this.thread.is_stop_auto_router_requested())) {
        if (((currentPass % STOP_AT_PASS_MODULO == 0) && (currentPass >= STOP_AT_PASS_MINIMUM))
            || (this.thread.is_stop_auto_router_requested())) {
          // Check if the score improved compared to the previous passes, restore a
          // previous board if not. Use strict ">" so that equally-scored boards do NOT
          // trigger a restore — if every board has the same (possibly zero) score the old
          // ">=" test would restore on every check cycle, growing the history unboundedly
          // and never stopping.
          if (bh.getMaxScore() > boardScoreAfter) {
            var boardToRestore = bh.restoreBoard(MAXIMUM_TRIES_ON_THE_SAME_BOARD);
            if (boardToRestore == null) {
              job.logInfo("The router was not able to improve the board, stopping the auto-router.");
              thread.request_stop_auto_router();
              break;
            }

            int boardToRestoreRank = bh.getRank(boardToRestore);

            if (boardToRestoreRank > BOARD_RANK_LIMIT) {
              thread.request_stop_auto_router();
              break;
            }

            this.board = boardToRestore;
            var boardStatistics = this.board.get_statistics();
            // Reset pass-local stagnation counter when restoring a previous board state
            consecutiveNoImprovementPasses = 0;
            boardStatisticsAfter = boardStatistics;
            boardScoreAfter = boardStatisticsAfter.getNormalizedScore(job.routerSettings.scoring);
            lastBestScore = boardScoreAfter;
            currentBoardHash = this.board.get_hash();
            // Reset the same-hash set after a board restore: the restored board will be
            // routed with a higher ripup budget on subsequent passes, so earlier routing
            // decisions from the same hash may no longer apply.
            alreadyRoutedBoardHashes.clear();
            job.logDebug(
                "Restoring an earlier board that has the score of "
                    + FRLogger.formatScore(boardScoreAfter,
                        boardStatisticsAfter.connections.incompleteCount,
                        boardStatisticsAfter.clearanceViolations.totalCount)
                    + ".");
          }
        }
      }
      double autorouter_pass_duration = FRLogger
          .traceExit("BatchAutorouter.autoroute_pass #" + currentPass + " on board '" + currentBoardHash + "'");

      String passCompletedMessage = "Auto-router pass #" + currentPass + " on board '" + currentBoardHash
          + "' was completed in " + FRLogger.formatDuration(autorouter_pass_duration) + " with the score of "
          + FRLogger.formatScore(boardScoreAfter, boardStatisticsAfter.connections.incompleteCount,
              boardStatisticsAfter.clearanceViolations.totalCount);
      if (job.resourceUsage.cpuTimeUsed > 0) {
        passCompletedMessage += ", using " + FRLogger.defaultFloatFormat.format(job.resourceUsage.cpuTimeUsed)
            + " CPU seconds and the job allocated "
            + FRLogger.defaultFloatFormat.format(job.resourceUsage.maxMemoryUsed / 1024.0f) + " GB of memory so far.";
      } else {
        passCompletedMessage += ".";
      }
      job.logInfo(passCompletedMessage);

      DesignRulesChecker tempDrc = new DesignRulesChecker(this.board, null);
      tempDrc.calculateAllIncompletes();
      StringBuilder perNetBreakdown = new StringBuilder();
      for (int netNo = 1; netNo <= this.board.rules.nets.max_net_no(); netNo++) {
        int netIncomplete = tempDrc.getIncompleteCount(netNo);
        if (netIncomplete > 0) {
          FRLogger.trace(
              "BatchAutorouter.autoroute_pass",
              "compare_unrouted_net",
              "pass=" + currentPass + ", net=" + netNo + ", incomplete=" + netIncomplete,
              "Net #" + netNo,
              new Point[0]);
          if (!perNetBreakdown.isEmpty()) {
            perNetBreakdown.append(',');
          }
          perNetBreakdown.append(netNo).append('=').append(netIncomplete);
        }
      }
      FRLogger.trace("BatchAutorouter.autoroute_pass", "compare_unrouted_breakdown",
          "pass=" + currentPass
              + ", total=" + tempDrc.getIncompleteCount()
              + ", breakdown=" + perNetBreakdown,
          "",
          new Point[0]);

      if (this.settings.save_intermediate_stages) {
        fireBoardSnapshotEvent(this.board);
      }

      // Stagnation detection: abort when the normalized score hasn't improved by
      // at least STAGNATION_SCORE_THRESHOLD over STAGNATION_PASS_LIMIT consecutive
      // passes. This now fires whenever the router is still actively running
      // (continueAutorouting == true) after the mandatory minimum passes, regardless
      // of incompleteCount.  The old condition guarded on incompleteCount > 0, which
      // caused the check to be bypassed — and the counter to be silently reset — for
      // boards where DRC shows 0 incompletes but the router keeps cycling (e.g. when
      // plane-net false-work items kept autoroute_pass() returning true).  If the
      // board is genuinely done (continueAutorouting == false) the while-loop exits
      // naturally and we never reach this block.
      if (currentPass >= STOP_AT_PASS_MINIMUM && continueAutorouting) {

        // --- Pass-local counter (resets after board restores) ---
        if (boardScoreAfter > lastBestScore + STAGNATION_SCORE_THRESHOLD) {
          consecutiveNoImprovementPasses = 0;
          lastBestScore = boardScoreAfter;
        } else {
          consecutiveNoImprovementPasses++;

          // One-time recovery for fanout-enabled jobs: aggressively remove tails, including
          // fanout vias, when score plateaus with remaining incompletes. This gives the
          // autorouter a chance to escape local dead-ends introduced by pre-fanout geometry
          // while keeping fanout enabled as the default behavior.
          if (this.settings.isFanoutEnabled()
              && !fanoutRecoveryApplied
              && boardStatisticsAfter.connections.incompleteCount > 0
              && consecutiveNoImprovementPasses >= FANOUT_RECOVERY_STAGNATION_PASSES) {
            int incompletesBeforeRecovery = boardStatisticsAfter.connections.incompleteCount;
            remove_tails(Item.StopConnectionOption.NONE);
            boardStatisticsAfter = new BoardStatistics(this.board);
            boardScoreAfter = boardStatisticsAfter.getNormalizedScore(job.routerSettings.scoring);
            lastBestScore = boardScoreAfter;
            consecutiveNoImprovementPasses = 0;
            fanoutRecoveryApplied = true;
            alreadyRoutedBoardHashes.clear();
            job.logDebug("Applied one-time fanout recovery cleanup (removed fanout tails/vias). "
                + "Incompletes: " + incompletesBeforeRecovery + " -> "
                + boardStatisticsAfter.connections.incompleteCount + ".");
          }

          if (consecutiveNoImprovementPasses >= STAGNATION_PASS_LIMIT) {
            String report = buildUnroutedConnectionsReport();
            job.logInfo("The router's score (" + FRLogger.defaultFloatFormat.format(boardScoreAfter)
                + ") has not improved by more than " + STAGNATION_SCORE_THRESHOLD
                + " points in the last " + STAGNATION_PASS_LIMIT + " passes ("
                + boardStatisticsAfter.connections.incompleteCount + " item"
                + (boardStatisticsAfter.connections.incompleteCount == 1 ? "" : "s")
                + " still unconnected). Stopping the auto-router.\n"
                + "The following connections could not be routed -- please review your design "
                + "(e.g. check pad clearances, trace width rules, and available routing space):\n"
                + report);
            thread.request_stop_auto_router();
            break;
          }
        }

        // --- Global best tracker (not reset by board restores) ---
        // Stops the router if no pass anywhere has meaningfully improved the score
        // in the last STAGNATION_PASS_LIMIT passes, even across board-restore cycles.
        if (boardScoreAfter > globalBestScore + STAGNATION_SCORE_THRESHOLD) {
          globalBestScore = boardScoreAfter;
          passOfBestScore = currentPass;
          incompleteCountAtBestScore = boardStatisticsAfter.connections.incompleteCount;
        } else if ((currentPass - passOfBestScore) >= STAGNATION_PASS_LIMIT) {
          String report = buildUnroutedConnectionsReport();
          job.logInfo("The router's best score (" + FRLogger.defaultFloatFormat.format(globalBestScore)
              + ") has not improved by more than " + STAGNATION_SCORE_THRESHOLD
              + " points since pass #" + passOfBestScore
              + ". Stopping the auto-router after " + currentPass + " passes ("
              + incompleteCountAtBestScore + " item"
              + (incompleteCountAtBestScore == 1 ? "" : "s")
              + " still unconnected).\n"
              + "The following connections could not be routed -- please review your design "
              + "(e.g. check pad clearances, trace width rules, and available routing space):\n"
              + report);
          thread.request_stop_auto_router();
          break;
        }

      } else if (boardStatisticsAfter.connections.incompleteCount == 0 && boardScoreAfter > STAGNATION_SCORE_THRESHOLD) {
        // Board is fully routed AND has a positive score (genuine success).
        // A fully-routed board with score == 0 (e.g. caused by clearance violations
        // from plane routing) must NOT reset the stagnation counter; it should keep
        // accumulating until the global tracker fires.
        consecutiveNoImprovementPasses = 0;
        lastBestScore = boardScoreAfter;
      }

      // check if there are still unrouted items
      if (continueAutorouting && !this.thread.is_stop_auto_router_requested()) {
        currentPass++;
      }
    }

    // Ensure we finish with the best board ever seen during this routing session.
    // When stagnation or the max-pass limit fires, the loop exits with the board from the last
    // completed pass, which may be worse than an earlier pass that was recorded in the history.
    float currentFinalScore = new BoardStatistics(this.board).getNormalizedScore(job.routerSettings.scoring);
    float bestHistoryScore = bh.getMaxScore();
    if (bestHistoryScore > currentFinalScore) {
      RoutingBoard bestBoard = bh.restoreBestBoard();
      if (bestBoard != null) {
        BoardStatistics currentStats = new BoardStatistics(this.board);
        this.board = bestBoard;
        BoardStatistics bestStats = new BoardStatistics(this.board);
        job.logDebug("The final board state (score "
            + FRLogger.formatScore(currentFinalScore,
                currentStats.connections.incompleteCount,
                currentStats.clearanceViolations.totalCount)
            + ") is worse than the best board seen during routing (score "
            + FRLogger.formatScore(bestStats.getNormalizedScore(job.routerSettings.scoring),
                bestStats.connections.incompleteCount,
                bestStats.clearanceViolations.totalCount)
            + "). Restoring the best board as the final result.");
      }
    }

    job.board = this.board;

    if (!(this.remove_unconnected_vias || continueAutorouting || this.thread.is_stop_auto_router_requested())) {
      // clean up the route if the board is completed and if fanout is used.
      remove_tails(Item.StopConnectionOption.NONE);
    }

    bh.clear();

    // Print all profiling results at the end of session
    PerformanceProfiler.printResults();
    PerformanceProfiler.reset();

    if (!this.thread.is_stop_auto_router_requested()) {
      this.fireTaskStateChangedEvent(new TaskStateChangedEvent(this, TaskState.FINISHED,
          currentPass, this.board.get_hash()));
    } else {
      // Distinguish between a user-requested cancellation and a job timeout so that
      // API consumers can tell the two apart via TaskStateChangedEvent.
      boolean isTimedOut = (job != null) && (job.state == RoutingJobState.TIMED_OUT);
      this.fireTaskStateChangedEvent(new TaskStateChangedEvent(this,
          isTimedOut ? TaskState.TIMED_OUT : TaskState.CANCELLED,
          currentPass, this.board.get_hash()));
    }

    return !this.thread.is_stop_auto_router_requested();
  }

  private String buildUnroutedConnectionsReport() {
    DesignRulesChecker tempDrc = new DesignRulesChecker(this.board, null);
    tempDrc.calculateAllIncompletes();
    AirLine[] airlines = tempDrc.getAllAirlines();

    if (airlines == null || airlines.length == 0) {
      return "  (no unrouted connections found)";
    }

    // Group airlines by net name for a cleaner report
    Map<String, List<String>> byNet = new LinkedHashMap<>();
    for (AirLine al : airlines) {
      String netName = al.net != null ? al.net.name : "(unknown net)";
      String fromDesc = describeItem(al.from_item);
      String toDesc   = describeItem(al.to_item);
      byNet.computeIfAbsent(netName, k -> new ArrayList<>())
           .add("    - " + fromDesc + "  ->  " + toDesc);
    }

    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, List<String>> entry : byNet.entrySet()) {
      int count = entry.getValue().size();
      sb.append("  Net '").append(entry.getKey()).append("' (")
        .append(count).append(" unrouted connection").append(count == 1 ? "" : "s").append("):\n");
      for (String line : entry.getValue()) {
        sb.append(line).append('\n');
      }
    }
    return sb.toString().stripTrailing();
  }

  /**
   * Returns a short, user-friendly description of a board item suitable for the
   * stagnation report.  For pins the format is {@code ComponentName-PinName}
   * (e.g. {@code J2-A3}); for all other item types a generic fallback is used.
   */
  private String describeItem(Item item) {
    if (item instanceof Pin pin) {
      try {
        app.freerouting.board.Component comp = board.components.get(pin.get_component_no());
        if (comp != null) {
          app.freerouting.core.Package pkg = comp.get_package();
          if (pkg != null) {
            app.freerouting.core.Package.Pin pkgPin = pkg.get_pin(pin.pin_no);
            if (pkgPin != null) {
              return comp.name + "-" + pkgPin.name;
            }
          }
          return comp.name + " (pin #" + pin.pin_no + ")";
        }
      } catch (Exception e) {
        // fall through to generic
      }
    }
    return item != null ? item.toString() : "(unknown)";
  }

  private void remove_tails(Item.StopConnectionOption p_stop_connection_option) {
    board.start_marking_changed_area();
    board.remove_trace_tails(-1, p_stop_connection_option);
    board.opt_changed_area(new int[0], null, this.trace_pull_tight_accuracy, this.trace_cost_arr, this.thread,
        TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP);
  }

  // Tries to route an item on a specific net. Returns true, if the item is
  // routed.
  private AutorouteAttemptResult autoroute_item(Item p_item, int p_route_net_no, SortedSet<Item> p_ripped_item_list,
      Map<Item, Integer> p_ripup_costs, int p_ripup_pass_no) {
    try {
      boolean contains_plane = false;

      // Get the net
      Net route_net = board.rules.nets.get(p_route_net_no);
      if (route_net != null) {
        contains_plane = route_net.contains_plane();
      }

      // Get the current via costs based on auto-router settings
      int curr_via_costs;
      if (contains_plane) {
        curr_via_costs = this.settings.get_plane_via_costs();
      } else {
        curr_via_costs = this.settings.get_via_costs();
      }

      // Optimization: reduce via costs in early passes to encourage aggressive via placement.
      // Early passes (1-4) use lower via cost, enabling more flexible routing with more vias.
      // Later passes increase via cost to stabilize routing with fewer additional vias.
      if (p_ripup_pass_no <= 4) {
        // Early passes: 20-40% via cost reduction
        double via_cost_factor = 1.0 - (0.4 * Math.max(0, 5 - p_ripup_pass_no) / 5.0);
        curr_via_costs = (int) (curr_via_costs * via_cost_factor);
      } else if (p_ripup_pass_no <= 8) {
        // Mid passes: 10-20% via cost reduction
        double via_cost_factor = 1.0 - (0.2 * Math.max(0, 9 - p_ripup_pass_no) / 5.0);
        curr_via_costs = (int) (curr_via_costs * via_cost_factor);
      }
      // Late passes (9+): use normal via cost

      // Microvia stitching optimization: reduce via costs in escape regions
      // Use micro vias (smaller, denser) in escape clusters, standard vias in distribution
      if (isEscapeViaNet(p_route_net_no) && p_ripup_pass_no <= 6) {
        // In escape regions during early/mid passes: 30-50% via cost reduction (enable microvia placement)
        // Microvia cost = 50-70% of standard via cost, encouraging denser stitching
        double microvia_factor = 0.6 + (p_ripup_pass_no / 10.0); // 0.6-0.7 range
        curr_via_costs = (int) (curr_via_costs * microvia_factor);
      }

      // Thermal-aware via routing: enable more vias on high-power nets for thermal distribution
      // High-power nets (3V3, 5V, VBAT, etc) benefit from distributed via routing for heat dissipation
      app.freerouting.rules.Net curr_net = board.rules.nets.get(p_route_net_no);
      if (curr_net != null && isHighPowerNet(curr_net.name) && p_ripup_pass_no <= 5) {
        // Thermal-aware: 10-15% via cost reduction to enable thermal via distribution
        double thermal_via_factor = 0.90; // 10% reduction for thermal distribution
        curr_via_costs = (int) (curr_via_costs * thermal_via_factor);
      }

      // NOTE: a "prefer less-congested layers" adjustment was removed here. It
      // collapsed the per-layer trace counts into one board-wide average and
      // applied a single 0.95-1.0 scalar to curr_via_costs, which is not a
      // per-layer bias at all -- curr_via_costs is one value for the whole
      // AutorouteControl. The counts were also taken once before the first pass
      // and never refreshed, so they described the starting board, not the
      // current one. Expressing a real layer preference needs a per-layer cost
      // vector, which this call site does not have.

      // Get and calculate the auto-router settings based on the board and net we are
      // working on
      AutorouteControl autoroute_control = new AutorouteControl(this.board, p_route_net_no, settings, curr_via_costs,
          this.trace_cost_arr);
      autoroute_control.ripup_allowed = true;
      // Exponential ripup cost growth: early passes (1-3) are cheap, later passes get increasingly expensive.
      // This encourages aggressive rework early when space is abundant, then gradually increases cost
      // to stabilize the board in later passes. Formula: base * (1.5 ^ (pass-1))
      autoroute_control.ripup_costs = (int) (this.start_ripup_costs * Math.pow(1.5, Math.max(0, p_ripup_pass_no - 1)));

      // Escape via priority: reduce ripup costs for nets trapped in QFN/BGA escape corners.
      // These nets benefit from aggressive early rework to find routes before later passes lock them in.
      if (isEscapeViaNet(p_route_net_no)) {
        // 50% reduction for escape via nets - encourages ripup and exploration
        autoroute_control.ripup_costs = autoroute_control.ripup_costs / 2;
      }

      autoroute_control.remove_unconnected_vias = this.remove_unconnected_vias;

      // Check if the item is already routed
      Set<Item> unconnected_set = p_item.get_unconnected_set(p_route_net_no);
      if (unconnected_set.isEmpty()) {
        return new AutorouteAttemptResult(AutorouteAttemptState.NO_UNCONNECTED_NETS);
      }

      Set<Item> connected_set = p_item.get_connected_set(p_route_net_no);
      Set<Item> route_start_set;
      Set<Item> route_dest_set;
      if (contains_plane) {
        for (Item curr_item : connected_set) {
          if (curr_item instanceof ConductionArea) {
            return new AutorouteAttemptResult(AutorouteAttemptState.CONNECTED_TO_PLANE);
          }
        }
      }
      if (contains_plane) {
        route_start_set = connected_set;
        route_dest_set = unconnected_set;
      } else {
        route_start_set = unconnected_set;
        route_dest_set = connected_set;
      }

      // Calculate the shortest distance between the two sets of items
      calc_airline(route_start_set, route_dest_set);

      // Calculate the maximum time for this autoroute pass
      // Time limit tuning: generous early, strict late to force convergence
      double max_milliseconds;
      if (p_ripup_pass_no <= 3) {
        // Early passes (1-3): generous time (100s-400s) - allow full exploration
        max_milliseconds = 100000 * Math.pow(2, p_ripup_pass_no - 1);
      } else if (p_ripup_pass_no <= 8) {
        // Mid passes (4-8): moderate time (20s-60s) - balance speed and quality
        max_milliseconds = 20000 + (p_ripup_pass_no - 3) * 10000;
      } else {
        // Late passes (9+): strict time (5s-15s) - force convergence
        int late_pass_offset = Math.max(1, p_ripup_pass_no - 8);
        max_milliseconds = Math.max(5000, 15000 - (late_pass_offset * 500));
      }
      max_milliseconds = Math.min(max_milliseconds, Integer.MAX_VALUE);
      TimeLimit time_limit = new TimeLimit((int) max_milliseconds);

      // Initialize the auto-router engine
      AutorouteEngine autoroute_engine = board.init_autoroute(p_route_net_no,
          autoroute_control.trace_clearance_class_no, this.thread, time_limit, this.retain_autoroute_database);

      // Do the auto-routing between the two sets of items
      AutorouteAttemptResult autoroute_result = autoroute_engine.autoroute_connection(route_start_set, route_dest_set,
          autoroute_control, p_ripped_item_list, p_ripup_costs);

      // Update the changed area of the board
      if (autoroute_result.state == AutorouteAttemptState.ROUTED) {
        int maxItemIdBeforeOpt = board.communication.id_no_generator.max_generated_no();
        FRLogger.trace("compare_trace_opt_changed_area_before net=" + p_route_net_no + ", maxItemId=" + maxItemIdBeforeOpt);
        board.opt_changed_area(new int[0], null, this.trace_pull_tight_accuracy, autoroute_control.trace_costs,
            this.thread, TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP);
        int maxItemIdAfterOpt = board.communication.id_no_generator.max_generated_no();
        FRLogger.trace("compare_trace_opt_changed_area_after net=" + p_route_net_no + ", maxItemId=" + maxItemIdAfterOpt + ", delta=" + (maxItemIdAfterOpt - maxItemIdBeforeOpt));
      }

      return autoroute_result;
    } catch (Exception e) {
      FRLogger.error("Error during routing passes", e);
      return new AutorouteAttemptResult(AutorouteAttemptState.FAILED);
    }
  }

  /**
   * Returns the airline of the current autorouted connection or null, if no such
   * airline exists
   */
  public FloatLine get_air_line() {
    if (this.air_line == null) {
      return null;
    }
    if (this.air_line.a == null || this.air_line.b == null) {
      return null;
    }
    return this.air_line;
  }

  // Calculates the shortest distance between two sets of items, specifically
  // between Pin and Via items (pins and vias are connectable DrillItems)
  private void calc_airline(Collection<Item> p_from_items, Collection<Item> p_to_items) {
    FloatPoint from_corner = null;
    FloatPoint to_corner = null;
    double min_distance = Double.MAX_VALUE;
    for (Item curr_from_item : p_from_items) {
      if (!(curr_from_item instanceof DrillItem)) {
        continue;
      }
      FloatPoint curr_from_corner = ((DrillItem) curr_from_item).get_center().to_float();

      for (Item curr_to_item : p_to_items) {
        if (!(curr_to_item instanceof DrillItem)) {
          continue;
        }
        FloatPoint curr_to_corner = ((DrillItem) curr_to_item).get_center().to_float();
        double curr_distance = curr_from_corner.distance_square(curr_to_corner);
        if (curr_distance < min_distance) {
          min_distance = curr_distance;
          from_corner = curr_from_corner;
          to_corner = curr_to_corner;
        }
      }
    }
    this.air_line = new FloatLine(from_corner, to_corner);
  }

  /**
   * Detects if a net is part of a tight escape via pattern (e.g., QFN-29 charger IC escape corner).
   * These nets get trapped in deadlocks due to competing escape via routing.
   * Returns true if pins are in tight cluster (< 10mm apart).
   */
  private boolean isEscapeViaNet(int p_net_no) {
    try {
      // Get all pins connected to this net
      java.util.List<FloatPoint> pin_locations = new java.util.ArrayList<>();

      for (Item item : board.get_connectable_items(p_net_no)) {
        if (item instanceof Pin pin) {
          pin_locations.add(pin.get_center().to_float());
        }
      }

      // If net has multiple pins in tight cluster (< 10mm apart), it's likely an escape corner
      if (pin_locations.size() >= 2) {
        for (int i = 0; i < pin_locations.size(); i++) {
          for (int j = i + 1; j < pin_locations.size(); j++) {
            double distance = pin_locations.get(i).distance(pin_locations.get(j));
            if (distance < mmToBoardUnits(10.0)) {
              // Tight cluster = escape via pattern (QFN/BGA escape corner)
              return true;
            }
          }
        }
      }
      return false;
    } catch (Exception e) {
      // If detection fails, safely return false
      return false;
    }
  }

  /**
   * Routes power trunks (GND/VCC nets) from escape via exit points to main power distribution.
   * Called after fanout when route_power_trunks=true, to handle post-escape power routing.
   *
   * Strategy:
   * 1. Identify escape vias (vias that are trapped in tight clusters with other vias)
   * 2. For each power net with escape vias, find exit points (edges of their escape zone)
   * 3. Route from exit points to main power distribution with aggressive ripup
   * 4. Prioritize width/connectivity over trace length for power nets
   */
  private void route_power_trunks_from_escapes() {
    job.logInfo("Power trunk routing: routing GND/VCC from escape via exits to main distribution");

    // Collect power nets that have escape vias trapped in zones
    java.util.Map<Integer, java.util.Set<app.freerouting.board.Via>> powerNetsWithEscapeVias =
        new java.util.HashMap<>();

    // Scan for power nets and their escape via patterns
    for (int i = 1; i <= board.rules.nets.max_net_no(); i++) {
      app.freerouting.rules.Net curr_net = board.rules.nets.get(i);
      if (curr_net == null) continue;

      // A net may have no name; contains_plane() is still meaningful for those.
      String netName = curr_net.name != null ? curr_net.name.toUpperCase() : "";
      boolean isPowerNet = netName.contains("GND") || netName.contains("VCC") ||
                           netName.contains("POWER") || curr_net.contains_plane();
      if (!isPowerNet) continue;

      // Check if this power net has vias in tight clusters (escape vias)
      java.util.Set<app.freerouting.board.Via> escapeVias = new java.util.HashSet<>();
      Collection<app.freerouting.board.Item> netItems = board.get_connectable_items(i);

      java.util.List<app.freerouting.board.Via> allVias = new java.util.ArrayList<>();
      for (app.freerouting.board.Item item : netItems) {
        if (item instanceof app.freerouting.board.Via via) {
          allVias.add(via);
        }
      }

      // Find vias that are in tight clusters (escape pattern)
      if (allVias.size() >= 2) {
        for (int j = 0; j < allVias.size(); j++) {
          app.freerouting.board.Via via1 = allVias.get(j);
          for (int k = j + 1; k < allVias.size(); k++) {
            app.freerouting.board.Via via2 = allVias.get(k);
            double dist = via1.get_center().to_float().distance(via2.get_center().to_float());
            // Vias closer than 15 mm to each other count as an escape cluster.
            if (dist < mmToBoardUnits(15.0)) {
              escapeVias.add(via1);
              escapeVias.add(via2);
            }
          }
        }
      }

      if (!escapeVias.isEmpty()) {
        powerNetsWithEscapeVias.put(i, escapeVias);
        job.logInfo("Power trunk: net #" + i + " (" + curr_net.name +
                   ") has " + escapeVias.size() + " escape vias");
      }
    }

    if (powerNetsWithEscapeVias.isEmpty()) {
      job.logInfo("Power trunk: no escape via patterns found in power nets");
      return;
    }

    // For each power net with escape vias, attempt aggressive routing
    for (java.util.Map.Entry<Integer, java.util.Set<app.freerouting.board.Via>> entry :
         powerNetsWithEscapeVias.entrySet()) {
      int net_no = entry.getKey();
      java.util.Set<app.freerouting.board.Via> escapeVias = entry.getValue();
      app.freerouting.rules.Net curr_net = board.rules.nets.get(net_no);

      int routed = 0;
      int failed = 0;

      // Try routing escape vias with very aggressive ripup
      // Use 1/4 of normal ripup cost to prioritize these nets
      int aggressive_ripup_cost = Math.max(1, this.start_ripup_costs / 4);

      for (app.freerouting.board.Via via_item : escapeVias) {
        app.freerouting.datastructures.TimeLimit time_limit =
            new app.freerouting.datastructures.TimeLimit(POWER_TRUNK_MILLIS_PER_VIA);

        app.freerouting.autoroute.AutorouteAttemptResult result =
            board.autoroute(via_item, this.settings, aggressive_ripup_cost, this.thread,
                time_limit);

        if (result.state == app.freerouting.autoroute.AutorouteAttemptState.ROUTED) {
          routed++;
        } else {
          failed++;
        }
      }

      job.logInfo("Power trunk: " + curr_net.name + " - routed " + routed +
                 "/" + escapeVias.size() + " escape vias" +
                 (failed > 0 ? ", failed: " + failed : ""));

      // Phase 2: Identify exit vias and route to anchor points
      if (failed > 0) {
        route_power_trunk_phase2(net_no, escapeVias, curr_net.name);
      }
    }
  }

  /**
   * Phase 2 of power trunk routing: route from edge vias (exit points) to power anchors.
   * Identifies escape via cluster boundaries and routes to main power distribution.
   *
   * Phase 3: Constrained exit-point routing
   * If Phase 2 still has failures, identify board-level escape regions and route vias
   * that lie on region boundaries with zero-cost pathfinding to anchors.
   */
  private void route_power_trunk_phase2(int p_net_no,
      java.util.Set<app.freerouting.board.Via> p_escape_vias, String p_net_name) {
    if (p_escape_vias.isEmpty()) return;

    job.logInfo("Power trunk phase 2: " + p_net_name + " - routing escape via exits to anchors");

    // Calculate centroid of escape via cluster
    double cx = 0, cy = 0;
    for (app.freerouting.board.Via via : p_escape_vias) {
      app.freerouting.geometry.planar.FloatPoint center = via.get_center().to_float();
      cx += center.x;
      cy += center.y;
    }
    cx /= p_escape_vias.size();
    cy /= p_escape_vias.size();
    final double cluster_cx = cx, cluster_cy = cy;

    // Find edge vias (furthest from cluster center = closest to main area)
    java.util.List<app.freerouting.board.Via> edge_vias =
        new java.util.ArrayList<>();
    // Two passes: establish the maximum distance first, THEN select against it.
    // Doing both in one pass made the threshold depend on iteration order -- the
    // first via always passed (dist > 0) and the selection varied with the set's
    // ordering, so "top 30% most distant" was neither.
    app.freerouting.geometry.planar.FloatPoint cluster_centre =
        new app.freerouting.geometry.planar.FloatPoint(cluster_cx, cluster_cy);

    double max_dist = 0;
    for (app.freerouting.board.Via via : p_escape_vias) {
      max_dist = Math.max(max_dist, via.get_center().to_float().distance(cluster_centre));
    }

    for (app.freerouting.board.Via via : p_escape_vias) {
      if (via.get_center().to_float().distance(cluster_centre) > max_dist * 0.7) {
        edge_vias.add(via);
      }
    }

    if (edge_vias.isEmpty()) {
      job.logInfo("Power trunk phase 2: " + p_net_name + " - no edge vias found");
      return;
    }

    // Find power anchor points (traces/vias of same net outside escape cluster)
    java.util.List<app.freerouting.board.Item> anchor_items = new java.util.ArrayList<>();
    Collection<app.freerouting.board.Item> net_items = board.get_connectable_items(p_net_no);

    for (app.freerouting.board.Item item : net_items) {
      if (item instanceof app.freerouting.board.Trace trace) {
        // Any trace outside cluster is anchor - use first corner as reference
        app.freerouting.geometry.planar.FloatPoint trace_point = trace.first_corner().to_float();
        double dist = trace_point.distance(
            new app.freerouting.geometry.planar.FloatPoint(cluster_cx, cluster_cy));
        if (dist > mmToBoardUnits(20.0)) { // > 20 mm from cluster = anchor
          anchor_items.add(item);
        }
      } else if (item instanceof app.freerouting.board.Via via &&
                 !p_escape_vias.contains(via)) {
        // Non-escape vias are anchors
        anchor_items.add(item);
      }
    }

    if (anchor_items.isEmpty()) {
      job.logInfo("Power trunk phase 2: " + p_net_name + " - no anchor points found");
      return;
    }

    // Route from each edge via to nearest anchor with ultra-aggressive ripup
    int connected = 0;
    int failed = 0;
    int ultra_aggressive_cost = Math.max(1, this.start_ripup_costs / 16); // 1/16 = extreme

    for (app.freerouting.board.Via edge_via : edge_vias) {
      // Try routing this edge via - should connect to anchors
      app.freerouting.datastructures.TimeLimit time_limit =
          new app.freerouting.datastructures.TimeLimit(POWER_TRUNK_MILLIS_PER_VIA);

      app.freerouting.autoroute.AutorouteAttemptResult result =
          board.autoroute(edge_via, this.settings, ultra_aggressive_cost, this.thread,
              time_limit);

      if (result.state == app.freerouting.autoroute.AutorouteAttemptState.ROUTED) {
        connected++;
      } else {
        failed++;
      }
    }

    job.logInfo("Power trunk phase 2: " + p_net_name + " - connected " + connected +
               "/" + edge_vias.size() + " edge vias to " + anchor_items.size() + " anchors" +
               (failed > 0 ? ", failed: " + failed : ""));

    // Phase 3: If still unrouted vias after Phase 2, use boundary-constrained routing
    if (failed > 0) {
      route_power_trunk_phase3(p_net_no, p_escape_vias, edge_vias, anchor_items, p_net_name);
    }
  }

  /**
   * Phase 3: Constrained boundary routing - route vias that touch escape region boundaries
   * directly to anchors with minimal cost.
   *
   * Identifies vias at region edges (boundary intersection) and routes them with
   * zero ripup cost (always allow connection) to enable final power trunk closure.
   */
  private void route_power_trunk_phase3(int p_net_no,
      java.util.Set<app.freerouting.board.Via> p_all_escape_vias,
      java.util.List<app.freerouting.board.Via> p_edge_vias,
      java.util.List<app.freerouting.board.Item> p_anchor_items, String p_net_name) {

    job.logInfo("Power trunk phase 3: " + p_net_name +
               " - boundary-constrained routing for trapped vias");

    // Identify vias at cluster boundary (those closest to anchors)
    java.util.List<app.freerouting.board.Via> boundary_vias = new java.util.ArrayList<>();

    for (app.freerouting.board.Via via : p_edge_vias) {
      // Find nearest anchor
      double min_dist = Double.MAX_VALUE;
      for (app.freerouting.board.Item anchor : p_anchor_items) {
        app.freerouting.geometry.planar.FloatPoint via_center = via.get_center().to_float();
        app.freerouting.geometry.planar.FloatPoint anchor_center;

        if (anchor instanceof app.freerouting.board.Via anchor_via) {
          anchor_center = anchor_via.get_center().to_float();
        } else if (anchor instanceof app.freerouting.board.Trace anchor_trace) {
          anchor_center = anchor_trace.first_corner().to_float();
        } else {
          continue;
        }

        double dist = via_center.distance(anchor_center);
        if (dist < min_dist) {
          min_dist = dist;
        }
      }

      // Vias within 50 mm of anchors are boundary candidates
      if (min_dist < mmToBoardUnits(50.0)) {
        boundary_vias.add(via);
      }
    }

    if (boundary_vias.isEmpty()) {
      job.logInfo("Power trunk phase 3: " + p_net_name + " - no boundary vias found");
      return;
    }

    // Route boundary vias with extreme settings (force connection)
    // Use negative ripup cost = "no limit, always route"
    int connected = 0;
    int failed = 0;

    for (app.freerouting.board.Via boundary_via : boundary_vias) {
      // Phase 3: absolute minimum ripup cost (1) to force routing
      app.freerouting.datastructures.TimeLimit time_limit =
          new app.freerouting.datastructures.TimeLimit(POWER_TRUNK_MILLIS_PER_VIA);

      app.freerouting.autoroute.AutorouteAttemptResult result =
          board.autoroute(boundary_via, this.settings, 1, this.thread, time_limit);

      if (result.state == app.freerouting.autoroute.AutorouteAttemptState.ROUTED) {
        connected++;
      } else {
        failed++;
      }
    }

    job.logInfo("Power trunk phase 3: " + p_net_name + " - boundary routing: " + connected +
               "/" + boundary_vias.size() + " boundary vias connected to anchors" +
               (failed > 0 ? ", still blocked: " + failed : ""));
  }

  /**
   * Finds the nearest point on a trace to the given point
   */
  private FloatPoint nearest_point_on_trace(PolylineTrace p_trace, FloatPoint p_point) {
    double min_distance = Double.MAX_VALUE;
    FloatPoint nearest_point = null;

    // Get endpoints
    FloatPoint first_corner = p_trace
        .first_corner()
        .to_float();
    FloatPoint last_corner = p_trace
        .last_corner()
        .to_float();

    // Check distance to endpoints first
    double distance_to_first = p_point.distance(first_corner);
    double distance_to_last = p_point.distance(last_corner);

    if (distance_to_first < min_distance) {
      min_distance = distance_to_first;
      nearest_point = first_corner;
    }

    if (distance_to_last < min_distance) {
      min_distance = distance_to_last;
      nearest_point = last_corner;
    }

    // Check distances to line segments
    for (int i = 0; i < p_trace.corner_count() - 1; i++) {
      FloatPoint segment_start = p_trace
          .polyline()
          .corner_approx(i);
      FloatPoint segment_end = p_trace
          .polyline()
          .corner_approx(i + 1);
      FloatLine segment = new FloatLine(segment_start, segment_end);

      FloatPoint projection = segment.perpendicular_projection(p_point);
      if (projection.is_contained_in_box(segment_start, segment_end, 0.01)) {
        double distance = p_point.distance(projection);
        if (distance < min_distance) {
          min_distance = distance;
          nearest_point = projection;
        }
      }
    }

    return nearest_point;
  }

  /**
   * Finds the closest points between two traces
   *
   * @return an array with two FloatPoints: [point_on_first_trace,
   *         point_on_second_trace]
   */
  private FloatPoint[] find_closest_points_between_traces(PolylineTrace p_first_trace, PolylineTrace p_second_trace) {
    double min_distance = Double.MAX_VALUE;
    FloatPoint[] result = new FloatPoint[2];

    // Check endpoints to endpoints
    FloatPoint first_trace_start = p_first_trace
        .first_corner()
        .to_float();
    FloatPoint first_trace_end = p_first_trace
        .last_corner()
        .to_float();
    FloatPoint second_trace_start = p_second_trace
        .first_corner()
        .to_float();
    FloatPoint second_trace_end = p_second_trace
        .last_corner()
        .to_float();

    // Check all endpoint combinations
    double distance = first_trace_start.distance(second_trace_start);
    if (distance < min_distance) {
      min_distance = distance;
      result[0] = first_trace_start;
      result[1] = second_trace_start;
    }

    distance = first_trace_start.distance(second_trace_end);
    if (distance < min_distance) {
      min_distance = distance;
      result[0] = first_trace_start;
      result[1] = second_trace_end;
    }

    distance = first_trace_end.distance(second_trace_start);
    if (distance < min_distance) {
      min_distance = distance;
      result[0] = first_trace_end;
      result[1] = second_trace_start;
    }

    distance = first_trace_end.distance(second_trace_end);
    if (distance < min_distance) {
      min_distance = distance;
      result[0] = first_trace_end;
      result[1] = second_trace_end;
    }

    // Check all segment combinations for closest points
    for (int i = 0; i < p_first_trace.corner_count() - 1; i++) {
      FloatPoint first_segment_start = p_first_trace
          .polyline()
          .corner_approx(i);
      FloatPoint first_segment_end = p_first_trace
          .polyline()
          .corner_approx(i + 1);
      FloatLine first_segment = new FloatLine(first_segment_start, first_segment_end);

      for (int j = 0; j < p_second_trace.corner_count() - 1; j++) {
        FloatPoint second_segment_start = p_second_trace
            .polyline()
            .corner_approx(j);
        FloatPoint second_segment_end = p_second_trace
            .polyline()
            .corner_approx(j + 1);
        FloatLine second_segment = new FloatLine(second_segment_start, second_segment_end);

        // Find closest points between these two line segments
        FloatPoint point_on_first = first_segment.nearest_segment_point(second_segment_start);
        FloatPoint point_on_second = second_segment.perpendicular_projection(point_on_first);

        // Check if projection is on the segment
        if (!point_on_second.is_contained_in_box(second_segment_start, second_segment_end, 0.01)) {
          // If not, use the nearest endpoint
          double dist_to_start = point_on_first.distance(second_segment_start);
          double dist_to_end = point_on_first.distance(second_segment_end);
          point_on_second = dist_to_start < dist_to_end ? second_segment_start : second_segment_end;
        }

        // Recalculate the point on first segment based on the point on second segment
        point_on_first = first_segment.nearest_segment_point(point_on_second);

        distance = point_on_first.distance(point_on_second);
        if (distance < min_distance) {
          min_distance = distance;
          result[0] = point_on_first;
          result[1] = point_on_second;
        }
      }
    }

    return result;
  }

  /**
   * Return an uppercase one-letter, two-letter or three-letter string based on
   * the thread index (0 = A, 1 = B, 2 = C, ..., 26 = AA, 27 = AB, ...).
   *
   * @param threadIndex
   * @return
   */
  private String ThreadIndexToLetter(int threadIndex) {
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
      return String.valueOf((char) ('A' + firstLetterIndex)) + (char) ('A' + secondLetterIndex)
          + (char) ('A' + thirdLetterIndex);
    }
  }

  /**
   * Calculates the airline distance for an item to be routed.
   * Returns the shortest distance from the item to any item in its incomplete
   * connections.
   *
   * @param p_item The item to calculate distance for
   * @return The shortest airline distance, or Double.MAX_VALUE if no connections
   *         exist
   */
  private double calculateItemDistance(Item p_item) {
    if (p_item.net_count() == 0) {
      return Double.MAX_VALUE;
    }

    // Get the first net number (items typically have one net)
    int net_no = p_item.get_net_no(0);

    // Get incomplete items for this net
    Set<Item> unconnected_set = p_item.get_unconnected_set(net_no);
    Set<Item> connected_set = p_item.get_connected_set(net_no);

    if (unconnected_set.isEmpty()) {
      return 0; // Already connected, prioritize
    }

    // Calculate minimum distance from connected items to unconnected items
    return calculateMinDistance(connected_set.isEmpty() ? Set.of(p_item) : connected_set, unconnected_set);
  }

  /**
   * Helper method to calculate the minimum distance between two sets of items.
   */
  private double calculateMinDistance(Collection<Item> p_from_items, Collection<Item> p_to_items) {
    double min_distance = Double.MAX_VALUE;

    for (Item from_item : p_from_items) {
      FloatPoint from_point = getItemReferencePoint(from_item);
      if (from_point == null)
        continue;

      for (Item to_item : p_to_items) {
        FloatPoint to_point = getItemReferencePoint(to_item);
        if (to_point == null)
          continue;

        double distance = from_point.distance(to_point);
        if (distance < min_distance) {
          min_distance = distance;
        }
      }
    }

    return min_distance;
  }

  /**
   * Gets a representative point for an item (center for DrillItems, midpoint for
   * traces).
   */
  private FloatPoint getItemReferencePoint(Item p_item) {
    if (p_item instanceof DrillItem drillItem) {
      return drillItem.get_center().to_float();
    } else if (p_item instanceof PolylineTrace trace) {
      // Use the midpoint of the trace as a reference
      FloatPoint first = trace.first_corner().to_float();
      FloatPoint last = trace.last_corner().to_float();
      return new FloatPoint((first.x + last.x) / 2, (first.y + last.y) / 2);
    }
    return null;
  }

  private int calculateIncompleteCount(RoutingBoard board) {
    DesignRulesChecker tempDrc = new DesignRulesChecker(board, null);
    tempDrc.calculateAllIncompletes();
    return tempDrc.getIncompleteCount();
  }
}