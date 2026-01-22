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
import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.interactive.RatsNest;
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
 * V1.9 BatchAutorouter implementation - ported from v1.9 for performance
 * comparison.
 * This is a simpler, single-threaded router that was used in v1.9.
 * 
 * Key differences from current router:
 * - Single-threaded only (no multi-threading support)
 * - Simpler pass logic without board history/backtracking
 * - Different item routing order (natural order vs sorted)
 * - No failure tracking or skip logic
 */
public class BatchAutorouterV19 extends NamedAlgorithm {

    private static final int TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP = 1000;

    private final boolean remove_unconnected_vias;
    private final AutorouteControl.ExpansionCostFactor[] trace_cost_arr;
    private final boolean retain_autoroute_database;
    private final int start_ripup_costs;
    private final int trace_pull_tight_accuracy;

    protected RoutingJob job;

    /** Used to draw the airline of the current routed incomplete. */
    private FloatLine air_line;

    /**
     * Initial number of unrouted nets at the start of the routing session.
     */
    private int initialUnroutedCount;

    /**
     * Time when the routing session started.
     */
    private Instant sessionStartTime;

    /**
     * Creates a new V1.9 batch autorouter from a RoutingJob.
     * This constructor adapts the current RoutingJob structure to the v1.9 router's
     * needs.
     */
    public BatchAutorouterV19(RoutingJob job) {
        this(job.thread, job.board, job.routerSettings, true, true,
                job.routerSettings.get_start_ripup_costs(), job.routerSettings.trace_pull_tight_accuracy);
        this.job = job;
    }

    /**
     * Internal constructor matching v1.9 signature.
     */
    public BatchAutorouterV19(StoppableThread p_thread, RoutingBoard board, RouterSettings settings,
            boolean p_remove_unconnected_vias, boolean p_with_preferred_directions, int p_start_ripup_costs,
            int p_pull_tight_accuracy) {
        super(p_thread, board, settings);

        // Validate that this is single-threaded (v1.9 doesn't support multi-threading)
        if (settings.maxThreads > 1) {
            FRLogger.warn("V1.9 router only supports single-threaded operation. Setting maxThreads to 1.");
            settings.maxThreads = 1;
        }

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
     * stopped by the user.
     * Returns true if the board is completed.
     * 
     * This is the main entry point, matching the current router's interface.
     */
    public boolean runBatchLoop() {
        this.fireTaskStateChangedEvent(new TaskStateChangedEvent(this, TaskState.STARTED, 0, this.board.get_hash()));

        // Capture initial state for session summary
        this.sessionStartTime = Instant.now();
        RatsNest initialRatsNest = new RatsNest(this.board);
        this.initialUnroutedCount = initialRatsNest.incomplete_count();

        job.logInfo("Starting V1.9 router with " + this.initialUnroutedCount + " incomplete connections.");

        boolean continueAutorouting = true;
        int currentPass = 1;

        while (continueAutorouting && !this.thread.is_stop_auto_router_requested()) {
            if (job != null && job.state == RoutingJobState.TIMED_OUT) {
                this.thread.request_stop_auto_router();
            }

            String current_board_hash = this.board.get_hash();

            if (currentPass > this.settings.maxPasses) {
                thread.request_stop_auto_router();
                break;
            }

            this.fireTaskStateChangedEvent(
                    new TaskStateChangedEvent(this, TaskState.RUNNING, currentPass, current_board_hash));

            FRLogger.traceEntry(
                    "BatchAutorouterV19.autoroute_pass #" + currentPass + " on board '" + current_board_hash + "'");

            // Run one pass using v1.9 logic
            continueAutorouting = autoroute_pass(currentPass, true);

            double autorouter_pass_duration = FRLogger
                    .traceExit("BatchAutorouterV19.autoroute_pass #" + currentPass + " on board '" + current_board_hash
                            + "'");

            var boardStatistics = this.board.get_statistics();
            float boardScore = boardStatistics.getNormalizedScore(job.routerSettings.scoring);

            String passCompletedMessage = "V1.9 Auto-router pass #" + currentPass + " on board '" + current_board_hash
                    + "' was completed in " + FRLogger.formatDuration(autorouter_pass_duration) + " with the score of "
                    + FRLogger.formatScore(boardScore, boardStatistics.connections.incompleteCount,
                            boardStatistics.clearanceViolations.totalCount);
            if (job.resourceUsage.cpuTimeUsed > 0) {
                passCompletedMessage += ", using " + FRLogger.defaultFloatFormat.format(job.resourceUsage.cpuTimeUsed)
                        + " CPU seconds and the job allocated "
                        + FRLogger.defaultFloatFormat.format(job.resourceUsage.maxMemoryUsed / 1024.0f)
                        + " GB of memory so far.";
            } else {
                passCompletedMessage += ".";
            }
            job.logInfo(passCompletedMessage);

            if (this.settings.save_intermediate_stages) {
                fireBoardSnapshotEvent(this.board);
            }

            // check if there are still unrouted items
            if (continueAutorouting && !this.thread.is_stop_auto_router_requested()) {
                currentPass++;
            }
        }

        job.board = this.board;

        if (!(this.remove_unconnected_vias || continueAutorouting || this.thread.is_stop_auto_router_requested())) {
            // clean up the route if the board is completed and if fanout is used.
            remove_tails(Item.StopConnectionOption.NONE);
        }

        if (!this.thread.is_stop_auto_router_requested()) {
            this.fireTaskStateChangedEvent(new TaskStateChangedEvent(this, TaskState.FINISHED,
                    currentPass, this.board.get_hash()));
        } else {
            this.fireTaskStateChangedEvent(new TaskStateChangedEvent(this, TaskState.CANCELLED,
                    currentPass, this.board.get_hash()));
        }

        return !this.thread.is_stop_auto_router_requested();
    }

    /**
     * Auto-routes one ripup pass of all items of the board. Returns false, if the
     * board is already
     * completely routed.
     * 
     * This is the v1.9 implementation - simpler than current version:
     * - No board history/backtracking
     * - No failure tracking
     * - Natural item order (no sorting by airline distance)
     * - Simpler progress tracking
     */
    private boolean autoroute_pass(int p_pass_no, boolean p_with_screen_message) {
        try {
            Collection<Item> autoroute_item_list = new LinkedList<>();
            Set<Item> handled_items = new TreeSet<>();
            Iterator<UndoableObjects.UndoableObjectNode> it = board.item_list.start_read_object();
            for (;;) {
                UndoableObjects.Storable curr_ob = board.item_list.read_object(it);
                if (curr_ob == null) {
                    break;
                }
                if (curr_ob instanceof Connectable && curr_ob instanceof Item) {
                    // This is a connectable item, like PolylineTrace or Pin
                    Item curr_item = (Item) curr_ob;
                    if (!curr_item.is_routable()) {
                        if (!handled_items.contains(curr_item)) {

                            // Let's go through all nets of this item
                            for (int i = 0; i < curr_item.net_count(); ++i) {
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
                                    autoroute_item_list.add(curr_item);
                                }
                            }
                        }
                    }
                }
            }

            // If there are no items to route, we're done
            if (autoroute_item_list.isEmpty()) {
                this.air_line = null;
                return false;
            }

            int items_to_go_count = autoroute_item_list.size();
            int ripped_item_count = 0;
            int not_found = 0;
            int routed = 0;

            job.logDebug("V1.9 Pass #" + p_pass_no + ": " + items_to_go_count + " items to route");

            // Let's go through all items to route (v1.9: natural order, no sorting)
            for (Item curr_item : autoroute_item_list) {
                // If the user requested to stop the auto-router, we stop it
                if (this.thread.is_stop_auto_router_requested()) {
                    break;
                }

                // Let's go through all nets of this item
                for (int i = 0; i < curr_item.net_count(); ++i) {
                    // If the user requested to stop the auto-router, we stop it
                    if (this.thread.is_stop_auto_router_requested()) {
                        break;
                    }

                    // We visually mark the area of the board, which is changed by the auto-router
                    board.start_marking_changed_area();

                    // Do the auto-routing step for this item (typically PolylineTrace or Pin)
                    SortedSet<Item> ripped_item_list = new TreeSet<>();
                    if (autoroute_item(curr_item, curr_item.get_net_no(i), ripped_item_list, p_pass_no)) {
                        ++routed;
                    } else {
                        ++not_found;
                    }
                    --items_to_go_count;
                    ripped_item_count += ripped_item_list.size();
                }
            }

            // V1.9: Always remove tails after each pass
            if (this.remove_unconnected_vias) {
                remove_tails(Item.StopConnectionOption.NONE);
            } else {
                remove_tails(Item.StopConnectionOption.FANOUT_VIA);
            }

            // We are done with this pass
            this.air_line = null;

            job.logDebug("V1.9 Pass #" + p_pass_no + " completed: routed=" + routed + ", not_found=" + not_found
                    + ", ripped=" + ripped_item_count);

            return true;
        } catch (Exception e) {
            job.logError("Something went wrong during the V1.9 auto-routing", e);
            this.air_line = null;
            return false;
        }
    }

    private void remove_tails(Item.StopConnectionOption p_stop_connection_option) {
        board.start_marking_changed_area();
        board.remove_trace_tails(-1, p_stop_connection_option);
        board.opt_changed_area(new int[0], null, this.trace_pull_tight_accuracy, this.trace_cost_arr, this.thread,
                TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP);
    }

    /**
     * Tries to route an item on a specific net. Returns true, if the item is
     * routed.
     * This is the v1.9 implementation.
     */
    private boolean autoroute_item(Item p_item, int p_route_net_no, SortedSet<Item> p_ripped_item_list,
            int p_ripup_pass_no) {
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

            // Get and calculate the auto-router settings based on the board and net we are
            // working on
            AutorouteControl autoroute_control = new AutorouteControl(this.board, p_route_net_no, settings,
                    curr_via_costs,
                    this.trace_cost_arr);
            autoroute_control.ripup_allowed = true;
            autoroute_control.ripup_costs = this.start_ripup_costs * p_ripup_pass_no;
            autoroute_control.remove_unconnected_vias = this.remove_unconnected_vias;

            // Check if the item is already routed
            Set<Item> unconnected_set = p_item.get_unconnected_set(p_route_net_no);
            if (unconnected_set.isEmpty()) {
                return true; // p_item is already routed.
            }

            Set<Item> connected_set = p_item.get_connected_set(p_route_net_no);
            Set<Item> route_start_set;
            Set<Item> route_dest_set;
            if (contains_plane) {
                for (Item curr_item : connected_set) {
                    if (curr_item instanceof ConductionArea) {
                        return true; // already connected to plane
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
            double max_milliseconds = 100000 * Math.pow(2, p_ripup_pass_no - 1);
            max_milliseconds = Math.min(max_milliseconds, Integer.MAX_VALUE);
            TimeLimit time_limit = new TimeLimit((int) max_milliseconds);

            // Initialize the auto-router engine
            AutorouteEngine autoroute_engine = board.init_autoroute(p_route_net_no,
                    autoroute_control.trace_clearance_class_no, this.thread, time_limit,
                    this.retain_autoroute_database);

            // Do the auto-routing between the two sets of items
            // V1.9 used AutorouteEngine.AutorouteResult enum, current version uses
            // AutorouteAttemptResult
            // We need to adapt to the current interface
            AutorouteAttemptResult autoroute_result = autoroute_engine.autoroute_connection(route_start_set,
                    route_dest_set,
                    autoroute_control, p_ripped_item_list);

            // Update the changed area of the board
            if (autoroute_result.state == AutorouteAttemptState.ROUTED) {
                board.opt_changed_area(new int[0], null, this.trace_pull_tight_accuracy, autoroute_control.trace_costs,
                        this.thread, TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP);
            }

            // Return true, if the item is routed
            return autoroute_result.state == AutorouteAttemptState.ROUTED
                    || autoroute_result.state == AutorouteAttemptState.ALREADY_CONNECTED;
        } catch (Exception e) {
            return false;
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

    /**
     * Calculates the shortest distance between two sets of items, specifically
     * between Pin and Via items
     * (pins and vias are connectable DrillItems)
     */
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
}
