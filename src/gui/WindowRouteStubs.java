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
 * WindowRouteStubs.java
 *
 * Created on 17. Februar 2006, 07:16
 *
 */

package gui;

import java.util.Collection;
import java.util.Set;
import java.util.SortedSet;
import java.util.Iterator;

import datastructures.Signum;
import geometry.planar.FloatPoint;

import board.Item;

/**
 *
 * @author Alfons Wirtz
 */
public class WindowRouteStubs extends WindowObjectListWithFilter
{
    
    /** Creates a new instance of WindowRouteStubs */
    public WindowRouteStubs(BoardFrame p_board_frame)
    {
        super(p_board_frame);
        this.resources = java.util.ResourceBundle.getBundle("gui.resources.CleanupWindows", p_board_frame.get_locale());
        this.setTitle(resources.getString("route_stubs"));
        this.list_empty_message.setText(resources.getString("no_route_stubs_found"));
        p_board_frame.set_context_sensitive_help(this, "WindowObjectList_RouteStubs");
    }
    
    protected void fill_list()
    {
        board.BasicBoard routing_board = this.board_frame.board_panel.board_handling.get_routing_board();
        
        SortedSet<RouteStubInfo> route_stub_info_set = new java.util.TreeSet<RouteStubInfo>();
        
        Collection<Item> board_items = routing_board.get_items();
        for (Item curr_item : board_items)
        {
            if (!(curr_item instanceof board.Trace || curr_item instanceof board.Via))
            {
                continue;
            }
            if (curr_item.net_count() != 1)
            {
                continue;
            }
            
            FloatPoint stub_location;
            int stub_layer;
            if (curr_item instanceof board.Via)
            {
                Collection<Item> contact_list = curr_item.get_all_contacts();
                if  (contact_list.isEmpty())
                {
                    stub_layer = curr_item.first_layer();
                }
                else
                {
                    Iterator<Item> it = contact_list.iterator();
                    Item curr_contact_item = it.next();
                    int first_contact_first_layer = curr_contact_item.first_layer();
                    int first_contact_last_layer = curr_contact_item.last_layer();
                    boolean all_contacts_on_one_layer = true;
                    while (it.hasNext())
                    {
                        curr_contact_item = it.next();
                        if (curr_contact_item.first_layer() != first_contact_first_layer
                                || curr_contact_item.last_layer() != first_contact_last_layer)
                        {
                            all_contacts_on_one_layer = false;
                            break;
                        }
                    }
                    if (!all_contacts_on_one_layer)
                    {
                        continue;
                    }
                    if (curr_item.first_layer() >= first_contact_first_layer &&
                            curr_item.last_layer() <= first_contact_first_layer)
                    {
                        stub_layer = first_contact_first_layer;
                    }
                    else
                    {
                        stub_layer = first_contact_last_layer;
                    }
                }
                stub_location = ((board.Via)curr_item).get_center().to_float();
            }
            else
            {
                board.Trace curr_trace = (board.Trace) curr_item;
                if  (curr_trace.get_start_contacts().isEmpty())
                {
                    stub_location = curr_trace.first_corner().to_float();
                }
                else if (curr_trace.get_end_contacts().isEmpty())
                {
                    stub_location = curr_trace.last_corner().to_float();
                }
                else
                {
                    continue;
                }
                stub_layer = curr_trace.get_layer();
            }
            RouteStubInfo curr_route_stub_info = new RouteStubInfo(curr_item, stub_location, stub_layer);
            route_stub_info_set.add(curr_route_stub_info);
        }
        
        for (RouteStubInfo curr_info : route_stub_info_set)
        {
            this.add_to_list(curr_info);
        }
        this.list.setVisibleRowCount(Math.min(route_stub_info_set.size(), DEFAULT_TABLE_SIZE));
    }
    
    protected void select_instances()
    {
        Object[] selected_list_values = list.getSelectedValues();
        if (selected_list_values.length <= 0)
        {
            return;
        }
        Set<board.Item> selected_items = new java.util.TreeSet<board.Item>();
        for (int i = 0; i < selected_list_values.length; ++i)
        {
            selected_items.add(((RouteStubInfo)selected_list_values[i]).stub_item);
        }
        interactive.BoardHandling board_handling = board_frame.board_panel.board_handling;
        board_handling.select_items(selected_items);
        board_handling.zoom_selection();
    }
    
    private final java.util.ResourceBundle resources;
    
    /**
     * Describes information of a route stub in the list.
     */
    private class RouteStubInfo implements Comparable<RouteStubInfo>
    {
        public RouteStubInfo(Item p_stub, FloatPoint p_location, int p_layer_no)
        {
            interactive.BoardHandling board_handling = board_frame.board_panel.board_handling;
            this.stub_item = p_stub;
            this.location = board_handling.coordinate_transform.board_to_user(p_location);
            this.layer_no  = p_layer_no;
            int net_no = p_stub.get_net_no(0);
            this.net = board_handling.get_routing_board().rules.nets.get(net_no);
        }
        
        public String toString()
        {
            String item_string;
            if (this.stub_item instanceof board.Trace)
            {
                item_string = resources.getString("trace");
            }
            else
            {
                item_string = resources.getString("via");
            }
            String layer_name = board_frame.board_panel.board_handling.get_routing_board().layer_structure.arr[layer_no].name;
            String result = item_string + " " + resources.getString("stub_net") + " " + this.net.name + " " +
                    resources.getString("at") + " " + this.location.to_string(board_frame.get_locale()) + " " +
                    resources.getString("on_layer") + " " + layer_name;
            return result;
        }
        
        public int compareTo(RouteStubInfo p_other)
        {
            int result = this.net.name.compareTo(p_other.net.name);
            if (result == 0)
            {
                result = Signum.as_int(this.location.x  - p_other.location.x);
            }
            if (result == 0)
            {
                result = Signum.as_int(this.location.y  - p_other.location.y);
            }
            if (result == 0)
            {
                result = this.layer_no - p_other.layer_no;
            }
            return result;
        }
        
        private final Item stub_item;
        private final rules.Net net;
        private final FloatPoint location;
        private final int layer_no;
    }
}
