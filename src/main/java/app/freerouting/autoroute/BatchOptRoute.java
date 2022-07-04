package app.freerouting.autoroute;

import java.util.Iterator;
import java.util.Collection;
import java.util.Set;
import java.util.function.Function;

import app.freerouting.datastructures.UndoableObjects;

import app.freerouting.geometry.planar.FloatPoint;

import app.freerouting.board.Item;
import app.freerouting.board.Via;
import app.freerouting.board.Trace;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.FixedState;
import app.freerouting.board.TestLevel;

import app.freerouting.interactive.InteractiveActionThread;
import app.freerouting.interactive.RatsNest;
import app.freerouting.logger.FRLogger;

/**
 *  Optimizes routes using a single thread on a board that has completed auto-routing.
 */
public class BatchOptRoute
{

    /**
     *  To optimize the route on the board after the autoroute task is finished.
     */
    public BatchOptRoute(InteractiveActionThread p_thread)
    {
    	this(p_thread, false);
    }

    public BatchOptRoute(InteractiveActionThread p_thread, boolean p_clone_board)
    {
        this.thread = p_thread;
        this.clone_board = p_clone_board;

        this.routing_board = p_clone_board ? p_thread.hdlg.deep_copy_routing_board()
        		                           : p_thread.hdlg.get_routing_board();
        this.sorted_route_items = null;
    }


    /**
     * Optimize the route on the board.
     */
    public void optimize_board(boolean save_intermediate_stages, float optimization_improvement_threshold, InteractiveActionThread isStopRequested)
    {
        if (routing_board.get_test_level() != TestLevel.RELEASE_VERSION)
        {
            FRLogger.warn("Before optimize: Via count: " + routing_board.get_vias().size() + ", trace length: " + Math.round(routing_board.cumulative_trace_length()));
        }
        double route_improved = -1;
        int curr_pass_no = 0;
        use_increased_ripup_costs = true;

        while (((route_improved >= optimization_improvement_threshold) || (route_improved < 0)) && (!isStopRequested.is_stop_requested()))
        {
            ++curr_pass_no;
            boolean with_preferred_directions = (curr_pass_no % 2 != 0); // to create more variations
            route_improved = opt_route_pass(curr_pass_no, with_preferred_directions);

            if ((route_improved > optimization_improvement_threshold) && (save_intermediate_stages))
            {	// Save intermediate optimization results:
            	// 1. To save the result in case the program is terminated unexpectedly,
            	//    e.g., Windows OS update automatically reboots machine
            	// 2. To provide a way to check intermediate results for a long-running optimization
            	String suffix = "_op" + curr_pass_no + ".bin";
            	this.thread.hdlg.get_panel().board_frame.save(suffix);
            }
        }
    }

    /**
     * Tries to reduce the number of vias and the trace length of a completely routed board.
     * Returns the amount of improvements were made in percentage (expressed between 0.0 and 1.0). -1 if the routing must go on no matter how much it improved.
     */
    protected float opt_route_pass(int p_pass_no, boolean p_with_prefered_directions)
    {
        float route_improved = 0.0f;
        int via_count_before = this.routing_board.get_vias().size();
        double trace_length_before = this.thread.hdlg.coordinate_transform.board_to_user(this.routing_board.cumulative_trace_length());
        this.thread.hdlg.screen_messages.set_post_route_info(via_count_before, trace_length_before);
        this.sorted_route_items = new ReadSortedRouteItems();
        this.min_cumulative_trace_length_before = calc_weighted_trace_length(routing_board);
        String optimizationPassId = "BatchOptRoute.opt_route_pass #" + p_pass_no + " with " + via_count_before + " vias and " + String.format("%(,.2f", trace_length_before) + " trace length.";

        FRLogger.traceEntry(optimizationPassId);

        while (true)
        {
            if (this.thread.is_stop_requested())
            {
                FRLogger.traceExit(optimizationPassId);
                return route_improved;
            }
            Item curr_item = sorted_route_items.next();
            if (curr_item == null)
            {
                break;
            }
            if (opt_route_item(curr_item, p_pass_no, p_with_prefered_directions).improved())
            {
                int via_count_after = this.routing_board.get_vias().size();
                double trace_length_after = this.thread.hdlg.coordinate_transform.board_to_user(this.routing_board.cumulative_trace_length());

                route_improved = (float)((via_count_before != 0 && trace_length_before != 0) ? 1.0 - ((((via_count_after / via_count_before) + (trace_length_after / trace_length_before)) / 2)) : 0);
            }
        }

        this.sorted_route_items = null;
        if (this.use_increased_ripup_costs && (route_improved == 0))
        {
            this.use_increased_ripup_costs = false;
            route_improved = -1; // to keep the optimizer going with lower ripup costs
        }

        FRLogger.traceExit(optimizationPassId);
        return route_improved;
    }

    protected void remove_ratsnest()
    {
    	this.thread.hdlg.remove_ratsnest();
    }

    protected RatsNest get_ratsnest()
    {
    	return this.thread.hdlg.get_ratsnest();
    }

    /**
     * Try to improve the route by re-routing the connections containing p_item.
     */
    protected ItemRouteResult opt_route_item(Item p_item, int p_pass_no,
    		                                 boolean p_with_prefered_directions)
    {
        java.util.ResourceBundle resources =
                java.util.ResourceBundle.getBundle("app.freerouting.interactive.InteractiveState", this.thread.hdlg.get_locale());
        String start_message = resources.getString("batch_optimizer") + " " + resources.getString("stop_message") + "        " + resources.getString("routeoptimizer_pass") + (Integer.valueOf(p_pass_no)).toString();
        this.thread.hdlg.screen_messages.set_status_message(start_message); // assume overwriting messages is harmless

        this.remove_ratsnest();  // looks like caching the ratsnest is not necessary
                                 // as a new instance is needed every time, i.e., remove/get ratsnest are called in pair
        int incomplete_count_before = this.get_ratsnest().incomplete_count();

        int via_count_before = this.routing_board.get_vias().size();
        Set<Item> ripped_items = new java.util.TreeSet<Item>();
        ripped_items.add(p_item);
        if (p_item instanceof Trace)
        {
            // add also the fork items, especially because not all fork items may be
            // returned by ReadSortedRouteItems because of matching end points.
            Trace curr_trace = (Trace) p_item;
            Set<Item> curr_contact_list = curr_trace.get_start_contacts();
            for (int i = 0; i < 2; ++i)
            {
                if (contains_only_unfixed_traces(curr_contact_list))
                {
                    ripped_items.addAll(curr_contact_list);
                }
                curr_contact_list = curr_trace.get_end_contacts();
            }
        }
        Set<Item> ripped_connections = new java.util.TreeSet<Item>();
        for (Item curr_item : ripped_items)
        {
            ripped_connections.addAll(curr_item.get_connection_items(Item.StopConnectionOption.NONE));
        }
        for (Item curr_item : ripped_connections)
        {
            if (curr_item.is_user_fixed())
            {
                return new ItemRouteResult(p_item.get_id_no());
            }
        }

        if (!this.clone_board) { routing_board.generate_snapshot(); }
        // no need to undo for cloned board which is either promoted to master or discarded

        this.routing_board.remove_items(ripped_connections, false);
        for (int i = 0; i < p_item.net_count(); ++i)
        {
            this.routing_board.combine_traces(p_item.get_net_no(i));
        }
        int ripup_costs = this.thread.hdlg.get_settings().autoroute_settings.get_start_ripup_costs();
        if (this.use_increased_ripup_costs)
        {
            ripup_costs *= ADDITIONAL_RIPUP_COST_FACTOR_AT_START;
        }
        if (p_item instanceof Trace)
        {
            // taking less ripup costs seems to produce better results
            ripup_costs = (int) Math.round(0.6 * (double) ripup_costs);
        }

        BatchAutorouter.autoroute_passes_for_optimizing_item(this.thread,
        		MAX_AUTOROUTE_PASSES, ripup_costs, p_with_prefered_directions,
                this.clone_board ? this.routing_board : null);

        this.remove_ratsnest();
        int incomplete_count_after = this.get_ratsnest().incomplete_count();

        int via_count_after = this.routing_board.get_vias().size();
        double trace_length_after = calc_weighted_trace_length(routing_board);

        ItemRouteResult result = new ItemRouteResult(p_item.get_id_no(),
        		via_count_before, via_count_after,
        		this.min_cumulative_trace_length_before, trace_length_after,
        		incomplete_count_before, incomplete_count_after);
        boolean route_improved = !this.thread.is_stop_requested() &&
        		                 result.improved();
        result.update_improved(route_improved);

        if (route_improved)
        {
            if (incomplete_count_after < incomplete_count_before ||
                 (incomplete_count_after == incomplete_count_before &&
                  via_count_after < via_count_before))
            {
                this.min_cumulative_trace_length_before = trace_length_after;
            }
            else
            {
                // Only cumulative trace length shortened.
                // Catch unexpected increase of cumulative trace length somewhere for examole by removing acid trapsw.
                this.min_cumulative_trace_length_before = Math.min(this.min_cumulative_trace_length_before, trace_length_after);
            }

            if (!this.clone_board) { routing_board.pop_snapshot(); }

            double new_trace_length = this.thread.hdlg.coordinate_transform.board_to_user(this.routing_board.cumulative_trace_length());
            this.thread.hdlg.screen_messages.set_post_route_info(via_count_after, new_trace_length);
        }
        else
        {
        	if (!this.clone_board) { routing_board.undo(null); }
        }

        return result;
    }

    static boolean contains_only_unfixed_traces(Collection<Item> p_item_list)
    {
        for (Item curr_item : p_item_list)
        {
            if (curr_item.is_user_fixed() || !(curr_item instanceof Trace))
            {
                return false;
            }
        }
        return true;
    }

    /**
     *  Calculates the cumulative trace lengths multiplied by the trace radius of all traces
     *  on the board, which are not shove_fixed.
     */
    protected static double calc_weighted_trace_length(RoutingBoard p_board)
    {
        double result = 0;
        int default_clearance_class = app.freerouting.rules.BoardRules.default_clearance_class();
        Iterator<UndoableObjects.UndoableObjectNode> it = p_board.item_list.start_read_object();
        for (;;)
        {
            UndoableObjects.Storable curr_item = p_board.item_list.read_object(it);
            if (curr_item == null)
            {
                break;
            }
            if (curr_item instanceof Trace)
            {
                Trace curr_trace = (Trace) curr_item;
                FixedState fixed_state = curr_trace.get_fixed_state();
                if (fixed_state == FixedState.UNFIXED || fixed_state == FixedState.SHOVE_FIXED)
                {
                    double weighted_trace_length = curr_trace.get_length() * (curr_trace.get_half_width() + p_board.clearance_value(curr_trace.clearance_class_no(), default_clearance_class, curr_trace.get_layer()));
                    if (fixed_state == FixedState.SHOVE_FIXED)
                    {
                        // to produce less violations with pin exit directions.
                        weighted_trace_length /= 2;
                    }
                    result += weighted_trace_length;
                }
            }
        }
        return result;
    }

    /**
     *  Returns the current position of the item, which will be rerouted or null, if the optimizer is not active.
     */
    public FloatPoint get_current_position()
    {
        if (sorted_route_items == null)
        {
            return null;
        }
        return sorted_route_items.get_current_position();
    }

    protected boolean clone_board = false;
    protected final InteractiveActionThread thread;
    protected RoutingBoard routing_board;
    protected ReadSortedRouteItems sorted_route_items;
    protected boolean use_increased_ripup_costs; // in the first passes the ripup costs are icreased for better performance.
    protected double min_cumulative_trace_length_before = 0;
    protected static int MAX_AUTOROUTE_PASSES = 6;
    protected static int ADDITIONAL_RIPUP_COST_FACTOR_AT_START = 10;
    /*
    protected class RouteResult
    {
    	public boolean improved;
        int via_count_before, via_count_after;
        double trace_length_before, trace_length_after;
    	int incomplete_count_before, incomplete_count_after;

    	public RouteResult(boolean p_improved) {
    		this(p_improved, 0, 0, 0, 0, 0, 0);
    	}

    	public RouteResult(boolean p_improved,
    			    int p_via_count_before, int p_via_count_after,
    			    double p_trace_length_before, double p_trace_length_after,
    			    int p_incomplete_count_before, int p_incomplete_count_after)
    	{
    		improved                = p_improved;
    		via_count_before        = p_via_count_before;
    		via_count_after         = p_via_count_after;
    		trace_length_before     = p_trace_length_before;
    		trace_length_after      = p_trace_length_after;
    		incomplete_count_before = p_incomplete_count_before;
    		incomplete_count_after  = p_incomplete_count_after;
    	}

    	public int via_count_reduced() { return via_count_before - via_count_after; }
    	public double length_reduced() { return trace_length_before - trace_length_after; }
    } */

    /**
     *  Reads the vias and traces on the board in ascending x order.
     *  Because the vias and traces on the board change while optimizing the item list
     *  of the board is read from scratch each time the next route item is returned.
     */
    protected class ReadSortedRouteItems
    {

        ReadSortedRouteItems()
        {
            min_item_coor = new FloatPoint(Integer.MIN_VALUE, Integer.MIN_VALUE);
            min_item_layer = -1;
        }

        Item next()
        {
            Item result = null;
            FloatPoint curr_min_coor = new FloatPoint(Integer.MAX_VALUE, Integer.MAX_VALUE);
            int curr_min_layer = Integer.MAX_VALUE;
            Iterator<UndoableObjects.UndoableObjectNode> it = routing_board.item_list.start_read_object();
            for (;;)
            {
                UndoableObjects.Storable curr_item = routing_board.item_list.read_object(it);
                if (curr_item == null)
                {
                    break;
                }
                if (curr_item instanceof Via)
                {
                    Via curr_via = (Via) curr_item;
                    if (!curr_via.is_user_fixed())
                    {
                        FloatPoint curr_via_center = curr_via.get_center().to_float();
                        int curr_via_min_layer = curr_via.first_layer();
                        if (curr_via_center.x > min_item_coor.x ||
                                curr_via_center.x == min_item_coor.x && (curr_via_center.y > min_item_coor.y || curr_via_center.y == min_item_coor.y && curr_via_min_layer > min_item_layer))
                        {
                            if (curr_via_center.x < curr_min_coor.x || curr_via_center.x == curr_min_coor.x && (curr_via_center.y < curr_min_coor.y ||
                                    curr_via_center.y == curr_min_coor.y && curr_via_min_layer < curr_min_layer))
                            {
                                curr_min_coor = curr_via_center;
                                curr_min_layer = curr_via_min_layer;
                                result = curr_via;
                            }
                        }
                    }
                }
            }
            // Read traces last to prefer vias to traces at the same location
            it = routing_board.item_list.start_read_object();
            for (;;)
            {
                UndoableObjects.Storable curr_item = routing_board.item_list.read_object(it);
                if (curr_item == null)
                {
                    break;
                }
                if (curr_item instanceof Trace)
                {
                    Trace curr_trace = (Trace) curr_item;
                    if (!curr_trace.is_shove_fixed())
                    {
                        FloatPoint first_corner = curr_trace.first_corner().to_float();
                        FloatPoint last_corner = curr_trace.last_corner().to_float();
                        FloatPoint compare_corner;
                        if (first_corner.x < last_corner.x ||
                                first_corner.x == last_corner.x && first_corner.y < last_corner.y)
                        {
                            compare_corner = last_corner;
                        }
                        else
                        {
                            compare_corner = first_corner;
                        }
                        int curr_trace_layer = curr_trace.get_layer();
                        if (compare_corner.x > min_item_coor.x ||
                                compare_corner.x == min_item_coor.x && (compare_corner.y > min_item_coor.y || compare_corner.y == min_item_coor.y && curr_trace_layer > min_item_layer))
                        {
                            if (compare_corner.x < curr_min_coor.x || compare_corner.x == curr_min_coor.x &&
                                    (compare_corner.y < curr_min_coor.y || compare_corner.y == curr_min_coor.y && curr_trace_layer < curr_min_layer))
                            {
                                boolean is_connected_to_via = false;
                                Set<Item> trace_contacts = curr_trace.get_normal_contacts();
                                for (Item curr_contact : trace_contacts)
                                {
                                    if (curr_contact instanceof Via && !curr_contact.is_user_fixed())
                                    {
                                        is_connected_to_via = true;
                                        break;
                                    }
                                }
                                if (!is_connected_to_via)
                                {
                                    curr_min_coor = compare_corner;
                                    curr_min_layer = curr_trace_layer;
                                    result = curr_trace;
                                }
                            }
                        }
                    }
                }
            }
            min_item_coor = curr_min_coor;
            min_item_layer = curr_min_layer;
            return result;

        }

        FloatPoint get_current_position()
        {
            return min_item_coor;
        }
        protected FloatPoint min_item_coor;
        protected int min_item_layer;
     }
}
