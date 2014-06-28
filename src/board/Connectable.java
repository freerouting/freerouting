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
 */

package board;

import geometry.planar.TileShape;

import java.util.Set;

/**
 *
 * Functionality required for items, which can be
 * electrical connected to other items.
 *
 * @author Alfons Wirtz
 */

public interface Connectable
{
    /**
     * Returns true if this item belongs to the net with number p_net_no.
     */
    public boolean contains_net(int p_net_no);
    
    /**
     *  Returns true if the net number array of this and p_net_no_arr have a common
     *  number.
     */
    public boolean shares_net_no(int [] p_net_no_arr);
    
    /**
     * Returns a list of all connectable items overlapping
     * and sharing a net with this item.
     */
    Set<Item> get_all_contacts();
    
    /**
     * Returns a list of all connectable items overlapping with
     * this item on the input layer and sharing a net with this item.
     */
    Set<Item> get_all_contacts(int p_layer );
    
    /**
     * Returns the list of all contacts of a connectable item
     * located at defined connection points.
     * Connection points of traces are there endpoints, connection
     * points of drill_items there center points, and connection
     * points of conduction areas are points on there border.
     */
    Set<Item> get_normal_contacts();
    
    /**
     * Returns all connectable items of the net with number p_net_no, which can be reached recursively
     * from this item via normal contacts.
     * if (p_net_no <= 0, the net number is ignored.
     */
    Set<Item> get_connected_set(int p_net_no);
    
    /**
     * Returns for each convex shape of a connectable item
     * the subshape of points, where traces can be connected to that item.
     */
    TileShape get_trace_connection_shape(ShapeSearchTree p_tree, int p_index);
}
