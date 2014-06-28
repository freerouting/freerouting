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
 * WindowUnconnectedRoute.java
 *
 * Created on 16. Februar 2006, 06:20
 *
 */

package gui;

import java.util.Collection;
import java.util.Set;
import java.util.SortedSet;

import board.Item;

/**
 *
 * @author Alfons Wirtz
 */
public class WindowUnconnectedRoute extends WindowObjectListWithFilter
{
    
    /** Creates a new instance of WindowUnconnectedRoute */
    public WindowUnconnectedRoute(BoardFrame p_board_frame)
    {
        super(p_board_frame);
        this.resources = java.util.ResourceBundle.getBundle("gui.resources.CleanupWindows", p_board_frame.get_locale());
        this.setTitle(resources.getString("unconnected_route"));
        this.list_empty_message.setText(resources.getString("no_unconnected_route_found"));
        p_board_frame.set_context_sensitive_help(this, "WindowObjectList_UnconnectedRoute");
    }
    
    protected void fill_list()
    {
        board.BasicBoard routing_board = this.board_frame.board_panel.board_handling.get_routing_board();
        
        Set<Item> handled_items = new java.util.TreeSet<Item>();
        
        SortedSet<UnconnectedRouteInfo> unconnected_route_info_set = new java.util.TreeSet<UnconnectedRouteInfo>();
        
        Collection<Item> board_items = routing_board.get_items();
        for (Item curr_item : board_items)
        {
            if (!(curr_item instanceof board.Trace || curr_item instanceof board.Via))
            {
                continue;
            }
            if (handled_items.contains(curr_item))
            {
                continue;
            }
            Collection<Item> curr_connected_set = curr_item.get_connected_set(-1);
            boolean terminal_item_found = false;
            for (Item curr_connnected_item : curr_connected_set)
            {
                handled_items.add(curr_connnected_item);
                if (!(curr_connnected_item instanceof board.Trace || curr_connnected_item instanceof board.Via))
                {
                    terminal_item_found = true;
                }
            }
            if (!terminal_item_found)
            {
                // We have found unconnnected route
                if (curr_item.net_count() == 1)
                {
                    rules.Net curr_net = routing_board.rules.nets.get(curr_item.get_net_no(0));
                    if (curr_net != null)
                    {
                        UnconnectedRouteInfo curr_unconnected_route_info =
                                new UnconnectedRouteInfo(curr_net, curr_connected_set);
                        unconnected_route_info_set.add(curr_unconnected_route_info);
                    }
                }
                else
                {
                    System.out.println("WindowUnconnectedRoute.fill_list: net_count 1 expected");
                }
            }
        }
        
        for (UnconnectedRouteInfo curr_info : unconnected_route_info_set)
        {
            this.add_to_list(curr_info);
        }
        this.list.setVisibleRowCount(Math.min(unconnected_route_info_set.size(), DEFAULT_TABLE_SIZE));
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
            selected_items.addAll(((UnconnectedRouteInfo)selected_list_values[i]).item_list);
        }
        interactive.BoardHandling board_handling = board_frame.board_panel.board_handling;
        board_handling.select_items(selected_items);
        board_handling.zoom_selection();
    }
    
    private final java.util.ResourceBundle resources;
    private int max_unconnected_route_info_id_no = 0;
    
    /**
     * Describes information of a connected set of unconnected  traces and vias.
     */
    private class UnconnectedRouteInfo implements Comparable<UnconnectedRouteInfo>
    {
        public UnconnectedRouteInfo(rules.Net p_net, Collection<Item> p_item_list)
        {
            this.net = p_net;
            this.item_list = p_item_list;
            ++max_unconnected_route_info_id_no;
            this.id_no = max_unconnected_route_info_id_no;
            int curr_trace_count = 0;
            int curr_via_count = 0;
            for (Item curr_item: p_item_list)
            {
                if (curr_item instanceof board.Trace)
                {
                    ++curr_trace_count;
                }
                else if (curr_item instanceof board.Via)
                {
                    ++curr_via_count;
                }
            }
            this.trace_count = curr_trace_count;
            this.via_count = curr_via_count;
        }
        
        public String toString()
        {
            
            String result = resources.getString("net") + " " + this.net.name + ": " 
                    + resources.getString("trace_count") + " " + this.trace_count.toString() + ", "
                    + resources.getString("via_count") + " " + this.via_count.toString();
  
            return result;
        }
        
        public int compareTo(UnconnectedRouteInfo p_other)
        {
            int result = this.net.name.compareTo(p_other.net.name);
            if (result == 0)
            {
                result = this.id_no - p_other.id_no;
            }
            return result;
        }
        
        private final rules.Net net;
        private final Collection<Item> item_list;
        private final int id_no;
        private final Integer trace_count;
        private final Integer via_count;
    }
}
