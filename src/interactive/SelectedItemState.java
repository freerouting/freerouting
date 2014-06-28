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
 * SelectedItemState.java
 *
 * Created on 10. November 2003, 08:02
 */
package interactive;

import geometry.planar.FloatPoint;
import geometry.planar.IntPoint;
import geometry.planar.Point;
import geometry.planar.Vector;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import datastructures.Stoppable;

import library.Package;

import rules.Net;

import autoroute.AutorouteEngine;

import board.Component;
import board.Connectable;
import board.DrillItem;
import board.Via;
import board.Pin;
import board.Item;
import board.ObstacleArea;
import board.PolylineTrace;
import board.RoutingBoard;
import board.FixedState;
import board.OptViaAlgo;
import board.TestLevel;

/**
 *  Class implementing actions on the currently selected items.
 *
 * @author  Alfons Wirtz
 */
public class SelectedItemState extends InteractiveState
{

    /**
     * Creates a new SelectedItemState  with with the items in p_item_list selected.
     * Returns null, if p_item_list is empty.
     */
    public static SelectedItemState get_instance(Set<Item> p_item_list, InteractiveState p_parent_state, BoardHandling p_board_handling, Logfile p_logfile)
    {
        if (p_item_list.isEmpty())
        {
            return null;
        }
        SelectedItemState new_state = new SelectedItemState(p_item_list, p_parent_state, p_board_handling, p_logfile);
        return new_state;
    }

    /** Creates a new instance of SelectedItemState */
    private SelectedItemState(Set<Item> p_item_list, InteractiveState p_parent_state, BoardHandling p_board_handling, Logfile p_logfile)
    {
        super(p_parent_state, p_board_handling, p_logfile);
        item_list = p_item_list;
    }

    /**
     * Gets the list of the currently selected items.
     */
    public Collection<Item> get_item_list()
    {
        return item_list;
    }

    public InteractiveState left_button_clicked(FloatPoint p_location)
    {
        return toggle_select(p_location);
    }

    public InteractiveState mouse_dragged(FloatPoint p_point)
    {
        return SelectItemsInRegionState.get_instance(hdlg.get_current_mouse_position(), this, hdlg, logfile);
    }

    /**
     * Action to be taken when a key is pressed (Shortcut).
     */
    public InteractiveState key_typed(char p_key_char)
    {
        InteractiveState result = this;

        if (p_key_char == 'a')
        {
            this.hdlg.autoroute_selected_items();
        }
        else if (p_key_char == 'b')
        {
            this.extent_to_whole_components();
        }
        else if (p_key_char == 'd')
        {
            result = this.cutout_items();
        }
        else if (p_key_char == 'e')
        {
            result = this.extent_to_whole_connections();
        }
        else if (p_key_char == 'f')
        {
            this.fix_items();
        }
        else if (p_key_char == 'i')
        {
            result = this.info();
        }
        else if (p_key_char == 'm')
        {
            result = MoveItemState.get_instance(hdlg.get_current_mouse_position(), item_list,
                    this.return_state, hdlg, logfile);
        }
        else if (p_key_char == 'n')
        {
            this.extent_to_whole_nets();
        }
        else if (p_key_char == 'p')
        {
            this.hdlg.optimize_selected_items();
        }
        else if (p_key_char == 'r')
        {
            result = ZoomRegionState.get_instance(hdlg.get_current_mouse_position(), this, hdlg, logfile);
        }
        else if (p_key_char == 's')
        {
            result = this.extent_to_whole_connected_sets();
        }
        else if (p_key_char == 'u')
        {
            this.unfix_items();
        }
        else if (p_key_char == 'v')
        {
            this.toggle_clearance_violations();
        }
        else if (p_key_char == 'w')
        {
            this.hdlg.zoom_selection();
        }
        else if (p_key_char == java.awt.event.KeyEvent.VK_DELETE)
        {
            result = delete_items();
        }
        else
        {
            result = super.key_typed(p_key_char);
        }
        return result;
    }

    /**
     * fixes all items in this selected set
     */
    public void fix_items()
    {
        java.util.Iterator<Item> it = item_list.iterator();
        while (it.hasNext())
        {
            Item curr_ob = it.next();
            if (curr_ob.get_fixed_state().ordinal() < FixedState.USER_FIXED.ordinal())
            {
                curr_ob.set_fixed_state(FixedState.USER_FIXED);
            }
        }
        if (this.logfile != null)
        {
            logfile.start_scope(LogfileScope.FIX_SELECTED_ITEMS);
        }
    }

    /**
     * unfixes all items in this selected set
     */
    public void unfix_items()
    {
        java.util.Iterator<Item> it = item_list.iterator();
        while (it.hasNext())
        {
            Item curr_ob = it.next();
            curr_ob.unfix();
        }
        if (this.logfile != null)
        {
            logfile.start_scope(LogfileScope.UNFIX_SELECTED_ITEMS);
        }
    }

    /**
     * Makes all items in this selected_set connectable and assigns
     * them to a new net.
     */
    public InteractiveState assign_items_to_new_net()
    {
        RoutingBoard board = hdlg.get_routing_board();
        // make the situation restorable by undo
        board.generate_snapshot();
        boolean items_already_connected = false;
        Net new_net = board.rules.nets.new_net(hdlg.get_locale());
        java.util.Iterator<Item> it = item_list.iterator();
        while (it.hasNext())
        {
            Item curr_item = it.next();
            if (curr_item instanceof ObstacleArea)
            {
                board.make_conductive((ObstacleArea) curr_item, new_net.net_number);
            }
            else if (curr_item instanceof DrillItem)
            {
                if (curr_item.is_connected())
                {
                    items_already_connected = true;

                }
                else
                {
                    curr_item.assign_net_no(new_net.net_number);
                }
            }
        }
        if (items_already_connected)
        {
            hdlg.screen_messages.set_status_message(resources.getString("some_items_are_not_changed_because_they_are_already_connected"));
        }
        else
        {
            hdlg.screen_messages.set_status_message(resources.getString("new_net_created_from_selected_items"));
        }
        if (this.logfile != null)
        {
            logfile.start_scope(LogfileScope.ASSIGN_SELECTED_TO_NEW_NET);
        }
        hdlg.update_ratsnest();
        hdlg.repaint();
        return this.return_state;
    }

    /**
     * Assigns all items in this selected_set to a new group( new component for example)
     */
    public InteractiveState assign_items_to_new_group()
    {
        RoutingBoard board = hdlg.get_routing_board();
        board.generate_snapshot();
        // Take the gravity point of all item centers for the location of the new component.
        double gravity_x = 0;
        double gravity_y = 0;
        int pin_count = 0;
        java.util.Iterator<Item> it = item_list.iterator();
        while (it.hasNext())
        {
            Item curr_ob = it.next();
            if (curr_ob instanceof Via)
            {
                FloatPoint curr_center = ((DrillItem) curr_ob).get_center().to_float();
                gravity_x += curr_center.x;
                gravity_y += curr_center.y;
                ++pin_count;
            }
            else
            {
                // currently only Vias can be aasigned to a new component
                it.remove();
            }
        }
        if (pin_count == 0)
        {
            return this.return_state;
        }
        gravity_x /= pin_count;
        gravity_y /= pin_count;
        Point gravity_point = new IntPoint((int) Math.round(gravity_x), (int) Math.round(gravity_y));
        // create a new package
        Package.Pin[] pin_arr = new Package.Pin[item_list.size()];
        it = item_list.iterator();
        for (int i = 0; i < pin_arr.length; ++i)
        {
            Via curr_via = (Via) it.next();
            Vector rel_coor = curr_via.get_center().difference_by(gravity_point);
            String pin_name = (new Integer(i + 1)).toString();
            pin_arr[i] = new Package.Pin(pin_name, curr_via.get_padstack().no, rel_coor, 0);
        }
        Package new_package = board.library.packages.add(pin_arr);
        Component new_component = board.components.add(gravity_point, 0, true, new_package);
        it = item_list.iterator();
        for (int i = 0; i < pin_arr.length; ++i)
        {
            Via curr_via = (Via) it.next();
            board.remove_item(curr_via);
            int[] net_no_arr = new int[curr_via.net_count()];
            for (int j = 0; j < net_no_arr.length; ++j)
            {
                net_no_arr[j] = curr_via.get_net_no(j);
            }
            board.insert_pin(new_component.no, i, net_no_arr, curr_via.clearance_class_no(), curr_via.get_fixed_state());
        }
        if (this.logfile != null)
        {
            logfile.start_scope(LogfileScope.ASSIGN_SELECTED_TO_NEW_GROUP);
        }
        hdlg.repaint();
        return this.return_state;
    }

    /**
     * Deletes all unfixed items in this selected set and
     * pulls tight the neighour traces.
     */
    public InteractiveState delete_items()
    {
        hdlg.get_routing_board().generate_snapshot();

        // calculate the changed nets for updating the ratsnest
        Set<Integer> changed_nets = new TreeSet<Integer>();
        Iterator<Item> it = item_list.iterator();
        while (it.hasNext())
        {
            Item curr_item = it.next();
            if (curr_item instanceof Connectable)
            {
                for (int i = 0; i < curr_item.net_count(); ++i)
                {
                    changed_nets.add(curr_item.get_net_no(i));
                }
            }
        }
        boolean with_delete_fixed = hdlg.get_routing_board().get_test_level() != TestLevel.RELEASE_VERSION;
        boolean all_items_removed;
        if (hdlg.settings.push_enabled)
        {
            all_items_removed = hdlg.get_routing_board().remove_items_and_pull_tight(item_list,
                    hdlg.settings.trace_pull_tight_region_width, hdlg.settings.trace_pull_tight_accuracy, with_delete_fixed);
        }
        else
        {
            all_items_removed = hdlg.get_routing_board().remove_items(item_list, with_delete_fixed);
        }
        if (!all_items_removed)
        {
            hdlg.screen_messages.set_status_message(resources.getString("some_items_are_fixed_and_could_therefore_not_be_removed"));
        }
        if (this.logfile != null)
        {
            logfile.start_scope(LogfileScope.DELETE_SELECTED);
        }

        for (Integer curr_net_no : changed_nets)
        {
            hdlg.update_ratsnest(curr_net_no.intValue());
        }
        hdlg.repaint();
        return this.return_state;
    }

    /**
     * Deletes all unfixed items in this selected set inside a rectangle
     */
    public InteractiveState cutout_items()
    {
        return CutoutRouteState.get_instance(this.item_list, this.return_state, hdlg, logfile);
    }

    /**
     * Autoroutes the selected items.
     * If p_stoppable_thread != null, the algorithm can be requestet to terminate.
     */
    public InteractiveState autoroute(Stoppable p_stoppable_thread)
    {
        boolean saved_board_read_only = hdlg.is_board_read_only();
        hdlg.set_board_read_only(true);
        if (p_stoppable_thread != null)
        {
            String start_message = resources.getString("autoroute") + " " + resources.getString("stop_message");
            hdlg.screen_messages.set_status_message(start_message);
        }
        Integer not_found_count = 0;
        Integer found_count = 0;
        boolean interrupted = false;
        Collection<Item> autoroute_item_list = new java.util.LinkedList<Item>();
        for (Item curr_item : item_list)
        {
            if (curr_item instanceof Connectable)
            {
                for (int i = 0; i < curr_item.net_count(); ++i)
                {
                    if (!curr_item.get_unconnected_set(curr_item.get_net_no(i)).isEmpty())
                    {
                        autoroute_item_list.add(curr_item);
                    }
                }
            }
        }
        int items_to_go_count = autoroute_item_list.size();
        hdlg.screen_messages.set_interactive_autoroute_info(found_count, not_found_count, items_to_go_count);
        // Empty this.item_list to avoid displaying the seected items.
        this.item_list = new TreeSet<Item>();
        boolean ratsnest_hidden_before = hdlg.get_ratsnest().is_hidden();
        if (!ratsnest_hidden_before)
        {
            hdlg.get_ratsnest().hide();
        }
        for (Item curr_item : autoroute_item_list)
        {
            if (p_stoppable_thread != null && p_stoppable_thread.is_stop_requested())
            {
                interrupted = true;
                break;
            }
            if (curr_item.net_count() != 1)
            {
                continue;
            }
            boolean contains_plane = false;
            rules.Net route_net = hdlg.get_routing_board().rules.nets.get(curr_item.get_net_no(0));
            if (route_net != null)
            {
                contains_plane = route_net.contains_plane();
            }
            int via_costs;
            if (contains_plane)
            {
                via_costs = hdlg.settings.autoroute_settings.get_plane_via_costs();
            }
            else
            {
                via_costs = hdlg.settings.autoroute_settings.get_via_costs();
            }
            hdlg.get_routing_board().start_marking_changed_area();
            AutorouteEngine.AutorouteResult autoroute_result =
                    hdlg.get_routing_board().autoroute(curr_item, hdlg.settings, via_costs, p_stoppable_thread, null);
            if (autoroute_result == AutorouteEngine.AutorouteResult.ROUTED)
            {
                ++found_count;
                hdlg.repaint();
            }
            else if (autoroute_result != AutorouteEngine.AutorouteResult.ALREADY_CONNECTED)
            {
                ++not_found_count;
            }
            --items_to_go_count;
            hdlg.screen_messages.set_interactive_autoroute_info(found_count, not_found_count, items_to_go_count);
        }
        if (p_stoppable_thread != null)
        {
            hdlg.screen_messages.clear();
            String curr_message;
            if (interrupted)
            {
                curr_message = resources.getString("interrupted");
            }
            else
            {
                curr_message = resources.getString("completed");
            }
            String end_message = resources.getString("autoroute") + " " + curr_message + ": " + found_count.toString() +
                    " " + resources.getString("connections_found") + ", " + not_found_count.toString() +
                    " " + resources.getString("connections_not_found");
            hdlg.screen_messages.set_status_message(end_message);
        }
        hdlg.set_board_read_only(saved_board_read_only);
        if (this.logfile != null)
        {
            logfile.start_scope(LogfileScope.AUTOROUTE_SELECTED);
        }
        hdlg.update_ratsnest();
        if (!ratsnest_hidden_before)
        {
            hdlg.get_ratsnest().show();
        }
        return this.return_state;
    }

    /**
     * Fanouts the pins contained in the selected items.
     * If p_stoppable_thread != null, the algorithm can be requestet to terminate.
     */
    public InteractiveState fanout(Stoppable p_stoppable_thread)
    {
        boolean saved_board_read_only = hdlg.is_board_read_only();
        hdlg.set_board_read_only(true);
        if (p_stoppable_thread != null)
        {
            String start_message = resources.getString("fanout") + " " + resources.getString("stop_message");
            hdlg.screen_messages.set_status_message(start_message);
        }
        Integer not_found_count = 0;
        Integer found_count = 0;
        int trace_pull_tight_accuracy = hdlg.settings.trace_pull_tight_accuracy;
        boolean interrupted = false;
        Collection<Pin> fanout_list = new java.util.LinkedList<Pin>();
        for (Item curr_item : item_list)
        {
            if (curr_item instanceof Pin)
            {
                fanout_list.add((Pin) curr_item);
            }
        }
        int items_to_go_count = fanout_list.size();
        hdlg.screen_messages.set_interactive_autoroute_info(found_count, not_found_count, items_to_go_count);
        // Empty this.item_list to avoid displaying the seected items.
        this.item_list = new TreeSet<Item>();
        boolean ratsnest_hidden_before = hdlg.get_ratsnest().is_hidden();
        if (!ratsnest_hidden_before)
        {
            hdlg.get_ratsnest().hide();
        }
        for (Pin curr_pin : fanout_list)
        {
            if (p_stoppable_thread != null && p_stoppable_thread.is_stop_requested())
            {
                interrupted = true;
                break;
            }
            hdlg.get_routing_board().start_marking_changed_area();
            AutorouteEngine.AutorouteResult autoroute_result =
                    hdlg.get_routing_board().fanout(curr_pin, hdlg.settings, -1, p_stoppable_thread, null);
            if (autoroute_result == AutorouteEngine.AutorouteResult.ROUTED)
            {
                ++found_count;
                hdlg.repaint();
            }
            else if (autoroute_result != AutorouteEngine.AutorouteResult.ALREADY_CONNECTED)
            {
                ++not_found_count;
            }

            --items_to_go_count;
            hdlg.screen_messages.set_interactive_autoroute_info(found_count, not_found_count, items_to_go_count);
        }
        if (p_stoppable_thread != null)
        {
            hdlg.screen_messages.clear();
            String curr_message;
            if (interrupted)
            {
                curr_message = resources.getString("interrupted");
            }
            else
            {
                curr_message = resources.getString("completed");
            }
            String end_message = resources.getString("fanout") + " " + curr_message + ": " + found_count.toString() +
                    " " + resources.getString("connections_found") + ", " + not_found_count.toString() +
                    " " + resources.getString("connections_not_found");
            hdlg.screen_messages.set_status_message(end_message);
        }
        hdlg.set_board_read_only(saved_board_read_only);
        if (this.logfile != null)
        {
            logfile.start_scope(LogfileScope.FANOUT_SELECTED);
        }
        hdlg.update_ratsnest();
        if (!ratsnest_hidden_before)
        {
            hdlg.get_ratsnest().show();
        }
        return this.return_state;
    }

    /**
     * Optimizes the selected items.
     * If p_stoppable_thread != null, the algorithm can be requestet to terminate.
     */
    public InteractiveState pull_tight(Stoppable p_stoppable_thread)
    {
        boolean saved_board_read_only = hdlg.is_board_read_only();
        hdlg.set_board_read_only(true);
        if (p_stoppable_thread != null)
        {
            String start_message = resources.getString("pull_tight") + " " + resources.getString("stop_message");
            hdlg.screen_messages.set_status_message(start_message);
        }
        hdlg.get_routing_board().start_marking_changed_area();
        boolean interrupted = false;
        for (Item curr_item : item_list)
        {
            if (p_stoppable_thread != null && p_stoppable_thread.is_stop_requested())
            {
                interrupted = true;
                break;
            }
            if (curr_item.net_count() != 1)
            {
                continue;
            }
            if (curr_item instanceof PolylineTrace)
            {
                PolylineTrace curr_trace = (PolylineTrace) curr_item;
                boolean something_changed = curr_trace.pull_tight(!hdlg.settings.push_enabled,
                        hdlg.settings.trace_pull_tight_accuracy, p_stoppable_thread);
                if (!something_changed)
                {
                    curr_trace.smoothen_end_corners_fork(!hdlg.settings.push_enabled,
                            hdlg.settings.trace_pull_tight_accuracy, p_stoppable_thread);
                }
            }
            else if (curr_item instanceof Via)
            {
                OptViaAlgo.opt_via_location(hdlg.get_routing_board(), (Via) curr_item,
                        null, hdlg.settings.trace_pull_tight_accuracy, 10);
            }
        }
        String curr_message;

        if (hdlg.settings.push_enabled && !interrupted)
        {
            hdlg.get_routing_board().opt_changed_area(new int[0], null,
                    hdlg.settings.trace_pull_tight_accuracy, null, p_stoppable_thread, 0);
        }

        if (p_stoppable_thread != null)
        {
            if (interrupted)
            {
                curr_message = resources.getString("interrupted");
            }
            else
            {
                curr_message = resources.getString("completed");
            }
            String end_message = resources.getString("pull_tight") + " " + curr_message;
            hdlg.screen_messages.set_status_message(end_message);
        }
        hdlg.set_board_read_only(saved_board_read_only);
        if (this.logfile != null)
        {
            logfile.start_scope(LogfileScope.OPTIMIZE_SELECTED);
        }
        hdlg.update_ratsnest();
        return this.return_state;
    }

    /**
     * Assigns the input  clearance class to the selected items.
     */
    public InteractiveState assign_clearance_class(int p_cl_class_index)
    {
        board.BasicBoard routing_board = this.hdlg.get_routing_board();
        if (p_cl_class_index < 0 || p_cl_class_index >= routing_board.rules.clearance_matrix.get_class_count())
        {
            return this.return_state;
        }
        if (this.logfile != null)
        {
            logfile.start_scope(LogfileScope.ASSIGN_CLEARANCE_CLASS);
            logfile.add_int(p_cl_class_index);
        }
        // make the situation restorable by undo
        routing_board.generate_snapshot();
        for (Item curr_item : this.item_list)
        {
            if (curr_item.clearance_class_no() == p_cl_class_index)
            {
                continue;
            }
            curr_item.change_clearance_class(p_cl_class_index);
        }
        return this.return_state;
    }

    /**
     * Select also all items belonging to any net of the current selected items.
     */
    public InteractiveState extent_to_whole_nets()
    {

        // collect all net numbers of the selected items
        Set<Integer> curr_net_no_set = new TreeSet<Integer>();
        Iterator<Item> it = item_list.iterator();
        while (it.hasNext())
        {
            Item curr_item = it.next();
            if (curr_item instanceof Connectable)
            {
                for (int i = 0; i < curr_item.net_count(); ++i)
                {
                    curr_net_no_set.add(curr_item.get_net_no(i));
                }
            }
        }
        Set<Item> new_selected_items = new TreeSet<Item>();
        Iterator<Integer> it2 = curr_net_no_set.iterator();
        while (it2.hasNext())
        {
            int curr_net_no = it2.next();
            new_selected_items.addAll(hdlg.get_routing_board().get_connectable_items(curr_net_no));
        }
        this.item_list = new_selected_items;
        if (new_selected_items.isEmpty())
        {
            return this.return_state;
        }
        if (this.logfile != null)
        {
            logfile.start_scope(LogfileScope.EXTEND_TO_WHOLE_NETS);
        }
        filter();
        hdlg.repaint();
        return this;
    }

    /**
     * Select also all items belonging to any group of the current selected items.
     */
    public InteractiveState extent_to_whole_components()
    {

        // collect all group numbers of the selected items
        Set<Integer> curr_group_no_set = new TreeSet<Integer>();
        Iterator<Item> it = item_list.iterator();
        while (it.hasNext())
        {
            Item curr_item = it.next();
            if (curr_item.get_component_no() > 0)
            {
                curr_group_no_set.add(curr_item.get_component_no());
            }
        }
        Set<Item> new_selected_items = new TreeSet<Item>();
        new_selected_items.addAll(item_list);
        Iterator<Integer> it2 = curr_group_no_set.iterator();
        while (it2.hasNext())
        {
            int curr_group_no = it2.next();
            new_selected_items.addAll(hdlg.get_routing_board().get_component_items(curr_group_no));
        }
        if (new_selected_items.isEmpty())
        {
            return this.return_state;
        }
        this.item_list = new_selected_items;
        if (this.logfile != null)
        {
            logfile.start_scope(LogfileScope.EXTEND_TO_WHOLE_COMPONENTS);
        }
        hdlg.repaint();
        return this;
    }

    /**
     * Select also all items belonging to any connected set of the current selected items.
     */
    public InteractiveState extent_to_whole_connected_sets()
    {
        Set<Item> new_selected_items = new TreeSet<Item>();
        Iterator<Item> it = this.item_list.iterator();
        while (it.hasNext())
        {
            Item curr_item = it.next();
            if (curr_item instanceof Connectable)
            {
                new_selected_items.addAll(curr_item.get_connected_set(-1));
            }
        }
        if (new_selected_items.isEmpty())
        {
            return this.return_state;
        }
        this.item_list = new_selected_items;
        if (this.logfile != null)
        {
            logfile.start_scope(LogfileScope.EXTEND_TO_WHOLE_CONNECTED_SETS);
        }
        filter();
        hdlg.repaint();
        return this;
    }

    /**
     * Select also all items belonging to any connection of the current selected items.
     */
    public InteractiveState extent_to_whole_connections()
    {
        Set<Item> new_selected_items = new TreeSet<Item>();
        Iterator<Item> it = this.item_list.iterator();
        while (it.hasNext())
        {
            Item curr_item = it.next();
            if (curr_item instanceof Connectable)
            {
                new_selected_items.addAll(curr_item.get_connection_items());
            }
        }
        if (new_selected_items.isEmpty())
        {
            return this.return_state;
        }
        this.item_list = new_selected_items;
        if (this.logfile != null)
        {
            logfile.start_scope(LogfileScope.EXTEND_TO_WHOLE_CONNECTIONS);
        }
        filter();
        hdlg.repaint();
        return this;
    }

    /**
     * Picks item at p_point.
     * Removes it from the selected_items list, if it is already in there,
     * otherwise adds it to the list.
     * Returns true (to change to the return_state) if nothing was picked.
     */
    public InteractiveState toggle_select(FloatPoint p_point)
    {
        Collection<Item> picked_items = hdlg.pick_items(p_point);
        boolean state_ended = (picked_items.isEmpty());
        if (picked_items.size() == 1)
        {
            Item picked_item = picked_items.iterator().next();
            if (this.item_list.contains(picked_item))
            {
                this.item_list.remove(picked_item);
                if (this.item_list.isEmpty())
                {
                    state_ended = true;
                }
            }
            else
            {
                this.item_list.add(picked_item);
            }
        }
        hdlg.repaint();
        InteractiveState result;
        if (state_ended)
        {
            result = this.return_state;
        }
        else
        {
            result = this;
        }
        if (this.logfile != null)
        {
            logfile.start_scope(LogfileScope.TOGGLE_SELECT, p_point);
        }
        return result;
    }

    /**
     * Shows or hides the clearance violations of the selected items..
     */
    public void toggle_clearance_violations()
    {
        if (clearance_violations == null)
        {
            clearance_violations = new ClearanceViolations(this.item_list);
            Integer violation_count = new Integer(clearance_violations.list.size());
            String curr_message = violation_count.toString() + " " + resources.getString("clearance_violations_found");
            hdlg.screen_messages.set_status_message(curr_message);
        }
        else
        {
            clearance_violations = null;
            hdlg.screen_messages.set_status_message("");
        }
        hdlg.repaint();
    }

    /**
     * Removes items not selected by the current interactive filter from the selected item list.
     */
    public InteractiveState filter()
    {
        item_list = hdlg.settings.item_selection_filter.filter(item_list);
        InteractiveState result = this;
        if (item_list.isEmpty())
        {
            result = this.return_state;
        }
        hdlg.repaint();
        return result;
    }

    /**
     * Prints information about the selected item into a graphical text window.
     */
    public SelectedItemState info()
    {
        gui.WindowObjectInfo.display(this.item_list, hdlg.get_panel().board_frame, hdlg.coordinate_transform, new java.awt.Point(100, 100));
        return this;
    }

    public String get_help_id()
    {
        return "SelectedItemState";
    }

    public void draw(java.awt.Graphics p_graphics)
    {
        if (item_list == null)
        {
            return;
        }

        for (board.Item curr_item : item_list)
        {
            curr_item.draw(p_graphics, hdlg.graphics_context, hdlg.graphics_context.get_hilight_color(),
                    hdlg.graphics_context.get_hilight_color_intensity());
        }
        if (clearance_violations != null)
        {
            clearance_violations.draw(p_graphics, hdlg.graphics_context);
        }
    }

    public javax.swing.JPopupMenu get_popup_menu()
    {
        return hdlg.get_panel().popup_menu_select;
    }

    public void set_toolbar()
    {
        hdlg.get_panel().board_frame.set_select_toolbar();
    }

    public void display_default_message()
    {
        hdlg.screen_messages.set_status_message(resources.getString("in_select_item_mode"));
    }
    private Set<Item> item_list;
    private ClearanceViolations clearance_violations = null;
}
