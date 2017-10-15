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
 * ItemAutorouteInfo.java
 *
 * Created on 22. Februar 2004, 12:09
 */

package autoroute;

import board.ShapeSearchTree;

import board.Item;


/**
 * Temporary data stored in board Items used in the autoroute algorithm
 *
 * @author  Alfons Wirtz
 */

public class ItemAutorouteInfo
{
    public ItemAutorouteInfo(Item p_item)
    {
        this.item = p_item;
    }
    /**
     * Looks, if the corresponding item belongs to the start or destination set of the autoroute algorithm.
     * Only used, if the item belongs to the net, which will be currently routed.
     */
    public boolean is_start_info()
    {
        return start_info;
    }
    
    /**
     * Sets, if the corresponding item belongs to the start or destination set of the autoroute algorithm.
     * Only used, if the item belongs to the net, which will be currently routed.
     */
    public void set_start_info(boolean p_value)
    {
        start_info = p_value;
    }
    
    /**
     *  Returns the precalculated connection of this item
     *  or null, if it is not yet precalculated.
     */
    public Connection get_precalculated_connection()
    {
        return this.precalculated_connnection;
    }
    
    /**
     *  Sets the precalculated connnection of this item.
     */
    public void set_precalculated_connection(Connection p_connection)
    {
        this.precalculated_connnection = p_connection;
    }
    
    /**
     * Gets the ExpansionRoom of of index p_index.
     * Creates it, if it is not yet existing.
     */
    public ObstacleExpansionRoom get_expansion_room(int p_index, ShapeSearchTree p_autoroute_tree)
    {
        if (expansion_room_arr == null)
        {
            expansion_room_arr = new ObstacleExpansionRoom[this.item.tree_shape_count(p_autoroute_tree)];
        }
        if (p_index < 0 || p_index >= expansion_room_arr.length)
        {
            System.out.println("ItemAutorouteInfo.get_expansion_room: p_index out of range");
            return null;
        }
        if (expansion_room_arr[p_index] == null)
        {
            expansion_room_arr[p_index]  = new ObstacleExpansionRoom(this.item, p_index, p_autoroute_tree);
        }
        return expansion_room_arr[p_index];
    }
    
    /**
     * Resets  the expansion rooms for autorouting the next connnection.
     */
    public void reset_doors()
    {
        if (expansion_room_arr != null)
        {
            for (ObstacleExpansionRoom curr_room: expansion_room_arr)
            {
                if (curr_room != null)
                {
                    curr_room.reset_doors();
                }
            }
        }
    }
    
    /**
     * Draws the shapes of the expansion rooms of this info for testing purposes.
     */
    public void draw(java.awt.Graphics p_graphics, boardgraphics.GraphicsContext p_graphics_context, double p_intensity)
    {
        if (expansion_room_arr == null)
        {
            return;
        }
        for (ObstacleExpansionRoom curr_room : expansion_room_arr)
        {
            if (curr_room != null)
            {
                curr_room.draw(p_graphics, p_graphics_context, p_intensity);
            }
        }
    }
    
    /**
     * Defines, if this item belongs to the start or destination set of
     * the maze search algorithm
     */
    private boolean start_info;
    
    private final Item item;
    
    private Connection precalculated_connnection = null;
    
    /**
     * ExpansionRoom for pushing or ripping the this object for each tree shape.
     */
    private ObstacleExpansionRoom[] expansion_room_arr;
}

