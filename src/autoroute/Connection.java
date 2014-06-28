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
 * Connection.java
 *
 * Created on 4. Juni 2006, 07:24
 *
 */

package autoroute;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import geometry.planar.Point;

import board.Item;
import board.Trace;

/**
 * Describes a routing connection ending at the next fork or terminal item.
 *
 * @author Alfons Wirtz
 */
public class Connection
{
    
    /**
     * Gets the connection this item belongs to. A connection ends at the next fork or terminal item.
     * Returns null, if p_item is not a route item, or if it is a via belonging to more than 1 connection.
     */
    public static Connection get(Item p_item)
    {
        if (!p_item.is_route())
        {
            return null;
        }
        Connection precalculated_connection  = p_item.get_autoroute_info().get_precalculated_connection();
        if (precalculated_connection != null)
        {
            return precalculated_connection;
        }
        Set<Item> contacts = p_item.get_normal_contacts();
        Set<Item> connection_items = new TreeSet<Item>();
        connection_items.add(p_item);
        
        Point start_point = null;
        int start_layer = 0;
        Point end_point = null;
        int end_layer = 0;
        
        for (Item curr_item : contacts)
        {
            Point prev_contact_point = p_item.normal_contact_point(curr_item);
            if (prev_contact_point == null)
            {
                // no unique contact point
                continue;
            }
            int prev_contact_layer = p_item.first_common_layer(curr_item);
            boolean fork_found = false;
            if (p_item instanceof Trace)
            {
                // Check, that there is only 1 contact at this location.
                // Only for pins and vias items of more than 1 connection
                // are collected
                Trace start_trace = (Trace) p_item;
                Collection<Item> check_contacts = start_trace.get_normal_contacts(prev_contact_point, false);
                if (check_contacts.size() != 1)
                {
                    fork_found = true;
                }
            }
            // Search from curr_item along the contacts
            // until the next fork or nonroute item.
            for (;;)
            {
                if(!curr_item.is_route() || fork_found)
                {
                    // connection ends
                    if (start_point == null)
                    {
                        start_point = prev_contact_point;
                        start_layer = prev_contact_layer;
                    }
                    else if (!prev_contact_point.equals(start_point))
                    {
                        end_point = prev_contact_point;
                        end_layer = prev_contact_layer;
                    }
                    break;
                }
                connection_items.add(curr_item);
                Collection<Item> curr_item_contacts =  curr_item.get_normal_contacts();
                // filter the contacts at the previous contact point,
                // because we were already there.
                // If then there is not exactly 1 new contact left, there is
                // a stub or a fork.
                Point next_contact_point = null;
                int next_contact_layer = -1;
                Item next_contact = null;
                for (Item tmp_contact : curr_item_contacts)
                {
                    int tmp_contact_layer = curr_item.first_common_layer(tmp_contact);
                    if (tmp_contact_layer >= 0)
                    {
                        Point tmp_contact_point = curr_item.normal_contact_point(tmp_contact);
                        if (tmp_contact_point == null)
                        {
                            // no unique contact point
                            fork_found = true;
                            break;
                        }
                        if (prev_contact_layer != tmp_contact_layer ||
                                !prev_contact_point.equals(tmp_contact_point))
                        {                                                      
                            next_contact_point = tmp_contact_point;
                            next_contact_layer = tmp_contact_layer;
                            if (next_contact != null)
                            {
                                // second new contact found
                                fork_found = true;
                                break;
                            }
                            next_contact = tmp_contact;
                        }
                    }
                }
                if (next_contact == null)
                {
                    break;
                }
                curr_item = next_contact;
                prev_contact_point = next_contact_point;
                prev_contact_layer = next_contact_layer;
            }
        }
        Connection result = new Connection(start_point, start_layer, end_point, end_layer, connection_items);
        for (Item curr_item : connection_items)
        {
            curr_item.get_autoroute_info().set_precalculated_connection(result);
        }
        return result;
    }
    
    /**
     * Creates a new instance of Connection
     */
    private Connection(Point p_start_point, int p_start_layer, Point p_end_point, int p_end_layer, Set<Item> p_item_list)
    {
        start_point = p_start_point;
        start_layer = p_start_layer;
        end_point = p_end_point;
        end_layer = p_end_layer;
        item_list = p_item_list;
    }
    
    /**
     * Returns the cumulative length of the traces in this  connection.
     */
    public double trace_length()
    {
        double result = 0;
        for (Item curr_item : item_list)
        {
            if (curr_item instanceof Trace)
            {
               result += ((Trace) curr_item).get_length();
            }
        }
        return result;
    }
    
    
    /**
     * Returns an estimation of the actual length of the connection divided by the minimal possible length.
     */
    public double get_detour()
    {
        if (start_point == null || end_point == null)
        {
            return Integer.MAX_VALUE;
        }
        double min_trace_length = start_point.to_float().distance(end_point.to_float());
        double detour = (this.trace_length() + DETOUR_ADD) / (min_trace_length + DETOUR_ADD) + 
                DETOUR_ITEM_COST * (item_list.size() - 1);
        return detour;
    }
    
    
    /**
     * If the connection ens in empty space, start_point or end_point may be null.
     */
    public final Point start_point;
    public final int start_layer;
    public final Point end_point;
    public final int end_layer;
    public final Set<Item> item_list;
    
    private static final double DETOUR_ADD = 100;
    private static final double DETOUR_ITEM_COST = 0.1;
}
