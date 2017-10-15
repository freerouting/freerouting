/*
 *  Copyright (C) 2014  Alfons Wirtz  
 *   website www.freerouting.net
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License at <http://www.gnu.org/licenses/> 
 *   for more details.
 *
 * RouteState.java
 *
 * Created on 8. November 2003, 08:22
 */
package interactive;

import geometry.planar.FloatPoint;
import geometry.planar.IntPoint;
import geometry.planar.Point;

import java.util.Collection;
import java.util.Set;

import board.Trace;
import board.Via;
import board.PolylineTrace;
import board.ConductionArea;
import board.DrillItem;
import board.Item;
import board.RoutingBoard;
import board.ItemSelectionFilter;

/**
 * Interactive routing state.
 *
 * @author Alfons Wirtz
 */
public class RouteState extends InteractiveState
{

    /**
     * Returns a new instance of this class or null,
     * if starting a new route was not possible at p_location.
     * If p_logfile != null, the creation of the route is stored
     * in the logfile.
     **/
    public static RouteState get_instance(FloatPoint p_location, InteractiveState p_parent_state, BoardHandling p_board_handling, Logfile p_logfile)
    {
        if (!(p_parent_state instanceof MenuState))
        {
            System.out.println("RouteState.get_instance: unexpected parent state");
        }
        p_board_handling.display_layer_messsage();
        IntPoint location = p_location.round();
        Item picked_item = start_ok(location, p_board_handling);
        if (picked_item == null)
        {
            return null;
        }
        int net_count = picked_item.net_count();
        if (net_count <= 0)
        {
            return null;
        }
        int[] route_net_no_arr;
        if (picked_item instanceof board.Pin && net_count > 1)
        {
            // tie pin, remove nets, which are already conneccted to this pin on the current layer.
            route_net_no_arr = get_route_net_numbers_at_tie_pin((board.Pin) picked_item, p_board_handling.settings.layer);
        }
        else
        {
            route_net_no_arr = new int[net_count];
            for (int i = 0; i < net_count; ++i)
            {
                route_net_no_arr[i] = picked_item.get_net_no(i);
            }
        }
        if (route_net_no_arr.length <= 0)
        {
            return null;
        }
        board.RoutingBoard routing_board = p_board_handling.get_routing_board();
        int[] trace_half_widths = new int[routing_board.get_layer_count()];
        boolean[] layer_active_arr = new boolean[trace_half_widths.length];
        for (int i = 0; i < trace_half_widths.length; ++i)
        {
            trace_half_widths[i] = p_board_handling.get_trace_halfwidth(route_net_no_arr[0], i);
            layer_active_arr[i] = false;
            for (int j = 0; j < route_net_no_arr.length; ++j)
            {
                if (p_board_handling.is_active_routing_layer(route_net_no_arr[j], i))
                {
                    layer_active_arr[i] = true;
                }
            }
        }

        int trace_clearance_class = p_board_handling.get_trace_clearance_class(route_net_no_arr[0]);
        boolean start_ok = true;
        if (picked_item instanceof Trace)
        {
            Trace picked_trace = (Trace) picked_item;
            Point picked_corner = picked_trace.nearest_end_point(location);
            if (picked_corner instanceof IntPoint &&
                    p_location.distance(picked_corner.to_float()) < 5 * picked_trace.get_half_width())
            {
                location = (IntPoint) picked_corner;
            }
            else
            {
                if (picked_trace instanceof PolylineTrace)
                {
                    FloatPoint nearest_point = ((PolylineTrace) picked_trace).polyline().nearest_point_approx(p_location);
                    location = nearest_point.round();
                }
                if (!routing_board.connect_to_trace(location, picked_trace,
                        picked_trace.get_half_width(), picked_trace.clearance_class_no()))
                {
                    start_ok = false;
                }
            }
            if (start_ok && !p_board_handling.settings.manual_rule_selection)
            {
                // Pick up the half with and the clearance class of the found trace.
                int[] new_trace_half_widths = new int[trace_half_widths.length];
                System.arraycopy(trace_half_widths, 0, new_trace_half_widths, 0, trace_half_widths.length);
                new_trace_half_widths[picked_trace.get_layer()] = picked_trace.get_half_width();
                trace_half_widths = new_trace_half_widths;
                trace_clearance_class = picked_trace.clearance_class_no();

            }
        }
        else if (picked_item instanceof DrillItem)
        {
            DrillItem drill_item = (DrillItem) picked_item;
            Point center = drill_item.get_center();
            if (center instanceof IntPoint)
            {
                location = (IntPoint) center;
            }
        }
        if (!start_ok)
        {
            return null;
        }


        rules.Net curr_net = routing_board.rules.nets.get(route_net_no_arr[0]);
        if (curr_net == null)
        {
            return null;
        }
        // Switch to stitch mode for nets, which are shove fixed.
        boolean is_stitch_route = p_board_handling.settings.is_stitch_route || curr_net.get_class().is_shove_fixed() || !curr_net.get_class().get_pull_tight();
        routing_board.generate_snapshot();
        RouteState new_instance;
        if (is_stitch_route)
        {
            new_instance = new StitchRouteState(p_parent_state, p_board_handling, p_logfile);
        }
        else
        {
            new_instance = new DynamicRouteState(p_parent_state, p_board_handling, p_logfile);
        }
        new_instance.routing_target_set = picked_item.get_unconnected_set(-1);

        new_instance.route = new Route(location, p_board_handling.settings.layer, trace_half_widths, layer_active_arr, route_net_no_arr,
                trace_clearance_class, p_board_handling.get_via_rule(route_net_no_arr[0]),
                p_board_handling.settings.push_enabled, p_board_handling.settings.trace_pull_tight_region_width,
                p_board_handling.settings.trace_pull_tight_accuracy, picked_item, new_instance.routing_target_set, routing_board,
                is_stitch_route, p_board_handling.settings.automatic_neckdown, p_board_handling.settings.via_snap_to_smd_center,
                p_board_handling.settings.hilight_routing_obstacle);
        new_instance.observers_activated = !routing_board.observers_active();
        if (new_instance.observers_activated)
        {
            routing_board.start_notify_observers();
        }
        p_board_handling.repaint();
        if (new_instance != null)
        {
            if (new_instance.logfile != null)
            {
                new_instance.logfile.start_scope(LogfileScope.CREATING_TRACE, p_location);
                p_board_handling.hide_ratsnest();
            }
            new_instance.display_default_message();
        }
        return new_instance;
    }

    /**
     * Creates a new instance of RouteState
     * If p_logfile != null, the creation of the route is stored
     * in the logfile.
     */
    protected RouteState(InteractiveState p_parent_state, BoardHandling p_board_handling, Logfile p_logfile)
    {
        super(p_parent_state, p_board_handling, p_logfile);
    }

    /**
     * Checks starting an interactive route at p_location.
     * Returns the picked start item of the routing at p_location,
     * or null, if no such item was found.
     */
    static protected Item start_ok(IntPoint p_location, BoardHandling p_hdlg)
    {
        board.RoutingBoard routing_board = p_hdlg.get_routing_board();

        /**
         * look if an already exististing trace ends at p_start_corner
         * and pick it up in this case.
         */
        Item picked_item = routing_board.pick_nearest_routing_item(p_location, p_hdlg.settings.layer, null);
        int layer_count = routing_board.get_layer_count();
        if (picked_item == null && p_hdlg.settings.select_on_all_visible_layers)
        {
            // Nothing found on preferred layer, try the other visible layers.
            // Prefer the outer layers.
            picked_item = pick_routing_item(p_location, 0, p_hdlg);
            if (picked_item == null)
            {
                picked_item = pick_routing_item(p_location, layer_count - 1, p_hdlg);
            }
            // prefer signal layers
            if (picked_item == null)
            {
                for (int i = 1; i < layer_count - 1; ++i)
                {
                    if (routing_board.layer_structure.arr[i].is_signal)
                    {
                        picked_item = pick_routing_item(p_location, i, p_hdlg);
                        if (picked_item != null)
                        {
                            break;
                        }
                    }
                }
            }
            if (picked_item == null)
            {
                for (int i = 1; i < layer_count - 1; ++i)
                {
                    if (!routing_board.layer_structure.arr[i].is_signal)
                    {
                        picked_item = pick_routing_item(p_location, i, p_hdlg);
                        if (picked_item != null)
                        {
                            break;
                        }
                    }
                }
            }
        }
        return picked_item;
    }

    static private Item pick_routing_item(IntPoint p_location, int p_layer_no, BoardHandling p_hdlg)
    {

        if (p_layer_no == p_hdlg.settings.layer || (p_hdlg.graphics_context.get_layer_visibility(p_layer_no) <= 0))
        {
            return null;
        }
        Item picked_item = p_hdlg.get_routing_board().pick_nearest_routing_item(p_location, p_layer_no, null);
        if (picked_item != null)
        {
            p_hdlg.set_layer(picked_item.first_layer());
        }
        return picked_item;
    }

    public InteractiveState process_logfile_point(FloatPoint p_point)
    {
        return add_corner(p_point);
    }

    /**
     * Action to be taken when a key is pressed (Shortcut).
     */
    public InteractiveState key_typed(char p_key_char)
    {
        InteractiveState curr_return_state = this;
        if (Character.isDigit(p_key_char))
        {
            // change to the p_key_char-ths signal layer
            board.LayerStructure layer_structure = hdlg.get_routing_board().layer_structure;
            int d = Character.digit(p_key_char, 10);
            d = Math.min(d, layer_structure.signal_layer_count());
            // Board layers start at 0, keyboard input for layers starts at 1.
            d = Math.max(d - 1, 0);
            board.Layer new_layer = layer_structure.get_signal_layer(d);
            d = layer_structure.get_no(new_layer);

            if (d >= 0)
            {
                change_layer_action(d);
            }
        }
        else if (p_key_char == '+')
        {
            // change to the next signal layer
            board.LayerStructure layer_structure = hdlg.get_routing_board().layer_structure;
            int current_layer_no = hdlg.settings.layer;
            for (;;)
            {
                ++current_layer_no;
                if (current_layer_no >= layer_structure.arr.length || layer_structure.arr[current_layer_no].is_signal)
                {
                    break;
                }
            }
            if (current_layer_no < layer_structure.arr.length)
            {
                change_layer_action(current_layer_no);
            }
        }
        else if (p_key_char == '-')
        {
            // change to the to the previous signal layer
            board.LayerStructure layer_structure = hdlg.get_routing_board().layer_structure;
            int current_layer_no = hdlg.settings.layer;
            for (;;)
            {
                --current_layer_no;
                if (current_layer_no < 0 || layer_structure.arr[current_layer_no].is_signal)
                {
                    break;
                }
            }
            if (current_layer_no >= 0)
            {
                change_layer_action(current_layer_no);
            }

        }
        else
        {
            curr_return_state = super.key_typed(p_key_char);
        }
        return curr_return_state;
    }

    /**
     * Append a line to p_location to the trace routed so far.
     * Returns from state, if the route is completed by connecting
     * to a target.
     */
    public InteractiveState add_corner(FloatPoint p_location)
    {
        boolean route_completed = route.next_corner(p_location);
        String layer_string = hdlg.get_routing_board().layer_structure.arr[route.nearest_target_layer()].name;
        hdlg.screen_messages.set_target_layer(layer_string);
        if (this.logfile != null)
        {
            this.logfile.add_corner(p_location);
        }
        if (route_completed)
        {
            if (this.observers_activated)
            {
                hdlg.get_routing_board().end_notify_observers();
                this.observers_activated = false;
            }
        }
        InteractiveState result;
        if (route_completed)
        {
            result = this.return_state;
            hdlg.screen_messages.clear();
            for (int curr_net_no : this.route.net_no_arr)
            {
                hdlg.update_ratsnest(curr_net_no);
            }
        }
        else
        {
            result = this;
        }
        hdlg.recalculate_length_violations();
        hdlg.repaint(hdlg.get_graphics_update_rectangle());
        return result;
    }

    public InteractiveState cancel()
    {
        Trace tail = hdlg.get_routing_board().get_trace_tail(route.get_last_corner(), hdlg.settings.layer, route.net_no_arr);
        if (tail != null)
        {
            Collection<Item> remove_items = tail.get_connection_items(Item.StopConnectionOption.VIA);
            if (hdlg.settings.push_enabled)
            {
                hdlg.get_routing_board().remove_items_and_pull_tight(remove_items,
                        hdlg.settings.trace_pull_tight_region_width, hdlg.settings.trace_pull_tight_accuracy, false);
            }
            else
            {
                hdlg.get_routing_board().remove_items(remove_items, false);
            }
        }
        if (this.observers_activated)
        {
            hdlg.get_routing_board().end_notify_observers();
            this.observers_activated = false;
        }
        if (logfile != null)
        {
            logfile.start_scope(LogfileScope.CANCEL_SCOPE);
        }
        hdlg.screen_messages.clear();
        for (int curr_net_no : this.route.net_no_arr)
        {
            hdlg.update_ratsnest(curr_net_no);
        }
        return this.return_state;
    }

    public boolean change_layer_action(int p_new_layer)
    {
        boolean result = true;
        if (p_new_layer >= 0 && p_new_layer < hdlg.get_routing_board().get_layer_count())
        {
            if (this.route != null && !this.route.is_layer_active(p_new_layer))
            {
                String layer_name = hdlg.get_routing_board().layer_structure.arr[p_new_layer].name;
                hdlg.screen_messages.set_status_message(resources.getString("layer_not_changed_because_layer") + " " + layer_name + " " + resources.getString("is_not_active_for_the_current_net"));
            }
            boolean change_layer_succeeded = route.change_layer(p_new_layer);
            if (change_layer_succeeded)
            {
                boolean connected_to_plane = false;
                // check, if the layer change resulted in a connection to a power plane.
                int old_layer = hdlg.settings.get_layer();
                ItemSelectionFilter selection_filter = new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.VIAS);
                Collection<Item> picked_items =
                        hdlg.get_routing_board().pick_items(route.get_last_corner(), old_layer, selection_filter);
                Via new_via = null;
                for (Item curr_via : picked_items)
                {
                    if (curr_via.shares_net_no(route.net_no_arr))
                    {
                        new_via = (Via) curr_via;
                        break;
                    }
                }
                if (new_via != null)
                {
                    int from_layer;
                    int to_layer;
                    if (old_layer < p_new_layer)
                    {
                        from_layer = old_layer + 1;
                        to_layer = p_new_layer;
                    }
                    else
                    {
                        from_layer = p_new_layer;
                        to_layer = old_layer - 1;
                    }
                    Collection<Item> contacts = new_via.get_normal_contacts();
                    for (Item curr_item : contacts)
                    {
                        if (curr_item instanceof ConductionArea)
                        {
                            ConductionArea curr_area = (ConductionArea) curr_item;
                            if (curr_area.get_layer() >= from_layer && curr_area.get_layer() <= to_layer)
                            {
                                connected_to_plane = true;
                                break;
                            }
                        }
                    }
                }

                if (connected_to_plane)
                {
                    hdlg.set_interactive_state(this.return_state);
                    for (int curr_net_no : this.route.net_no_arr)
                    {
                        hdlg.update_ratsnest(curr_net_no);
                    }
                }
                else
                {
                    hdlg.set_layer(p_new_layer);
                    String layer_name = hdlg.get_routing_board().layer_structure.arr[p_new_layer].name;
                    hdlg.screen_messages.set_status_message(resources.getString("layer_changed_to") + " " + layer_name);
                    // make the current situation restorable by undo
                    hdlg.get_routing_board().generate_snapshot();
                }
                if (logfile != null)
                {
                    logfile.start_scope(LogfileScope.CHANGE_LAYER, p_new_layer);
                }
            }
            else
            {
                int shove_failing_layer = hdlg.get_routing_board().get_shove_failing_layer();
                if (shove_failing_layer >= 0)
                {
                    String layer_name = hdlg.get_routing_board().layer_structure.arr[hdlg.get_routing_board().get_shove_failing_layer()].name;
                    hdlg.screen_messages.set_status_message(resources.getString("layer_not_changed_because_of_obstacle_on_layer") + " " + layer_name);
                }
                else
                {
                    System.out.println("RouteState.change_layer_action: shove_failing_layer not set");
                }
                result = false;
            }
            hdlg.repaint();
        }
        return result;
    }

    /**
     * get nets of p_tie_pin except nets of traces, which are already conneccted to this pin on p_layer.
     */
    static int[] get_route_net_numbers_at_tie_pin(board.Pin p_pin, int p_layer)
    {
        Set<Integer> net_number_list = new java.util.TreeSet<Integer>();
        for (int i = 0; i < p_pin.net_count(); ++i)
        {
            net_number_list.add(p_pin.get_net_no(i));
        }
        Set<Item> contacts = p_pin.get_normal_contacts();
        for (Item curr_contact : contacts)
        {
            if (curr_contact.first_layer() <= p_layer && curr_contact.last_layer() >= p_layer)
            {
                for (int i = 0; i < curr_contact.net_count(); ++i)
                {
                    net_number_list.remove(curr_contact.get_net_no(i));
                }
            }
        }
        int[] result = new int[net_number_list.size()];
        int curr_ind = 0;
        for (Integer curr_net_number : net_number_list)
        {
            result[curr_ind] = curr_net_number;
            ++curr_ind;
        }
        return result;
    }

    public void draw(java.awt.Graphics p_graphics)
    {
        if (route != null)
        {
            route.draw(p_graphics, hdlg.graphics_context);
        }
    }

    public void display_default_message()
    {
        if (route != null)
        {
            rules.Net curr_net = hdlg.get_routing_board().rules.nets.get(route.net_no_arr[0]);
            hdlg.screen_messages.set_status_message(resources.getString("routing_net") + " " + curr_net.name);
        }
    }
    protected Route route = null;
    private Set<Item> routing_target_set = null;
    protected boolean observers_activated = false;
}
