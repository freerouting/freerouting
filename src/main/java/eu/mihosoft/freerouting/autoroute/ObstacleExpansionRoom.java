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
 * ObstacleExpansionRoom.java
 *
 * Created on 17. April 2006, 06:45
 *
 */

package autoroute;

import java.util.List;
import java.util.Collection;

import board.ShapeSearchTree;
import board.SearchTreeObject;
import board.PolylineTrace;

import geometry.planar.TileShape;

import board.Item;

/**
 * Expansion Room used for pushing and ripping obstacles in the autoroute algorithm.
 *
 * @author Alfons Wirtz
 */
public class ObstacleExpansionRoom implements CompleteExpansionRoom
{
    
    /** Creates a new instance of ObstacleExpansionRoom */
    ObstacleExpansionRoom(Item p_item, int p_index_in_item, ShapeSearchTree p_shape_tree)
    {
        this.item = p_item;
        this.index_in_item = p_index_in_item;
        this.shape = p_item.get_tree_shape(p_shape_tree, p_index_in_item);
        this.doors = new java.util.LinkedList<ExpansionDoor>();
    }
    
    public int get_index_in_item()
    {
        return this.index_in_item;
    }
    
    public int get_layer()
    {
        return this.item.shape_layer(this.index_in_item);
    }
    
    public TileShape get_shape()
    {
        return this.shape;
    }
    
    /**
     * Checks, if this room has already a 1-dimensional door to p_other
     */
    public boolean door_exists(ExpansionRoom p_other)
    {
        if (doors != null)
        {
            for (ExpansionDoor curr_door : this.doors)
            {
                if (curr_door.first_room == p_other || curr_door.second_room == p_other)
                {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Adds a door to the door list of this room.
     */
    public void add_door(ExpansionDoor p_door)
    {
        this.doors.add(p_door);
    }
    
    /**
     * Creates a 2-dim door with the other obstacle room, if that is useful for the autoroute algorithm.
     * It is assumed that this room and p_other have a 2-dimensional overlap.
     * Returns false, if no door was created.
     */
    public boolean create_overlap_door(ObstacleExpansionRoom p_other)
    {
        if (this.door_exists(p_other))
        {
            return false;
        }
        if (!(this.item.is_route() && p_other.item.is_route()))
        {
            return false;
        }
        if (!this.item.shares_net(p_other.item))
        {
            return false;
        }
        if (this.item == p_other.item)
        {
            if (!(this.item instanceof PolylineTrace))
            {
                return false;
            }
            // create only doors between consecutive trace segments
            if (this.index_in_item != p_other.index_in_item + 1 && this.index_in_item != p_other.index_in_item - 1)
            {
                return false;
            }
        }
        ExpansionDoor new_door = new ExpansionDoor(this, p_other, 2);
        this.add_door(new_door);
        p_other.add_door(new_door);
        return true;
    }
    
    /**
     * Returns the list of doors of this room to neighbour expansion rooms
     */
    public List<ExpansionDoor> get_doors()
    {
        return this.doors;
    }
    
    /**
     * Removes all doors from this room.
     */
    public void clear_doors()
    {
        this.doors = new java.util.LinkedList<ExpansionDoor>();
    }
    
    public void reset_doors()
    {
        for (ExpandableObject curr_door : this.doors)
        {
            curr_door.reset();
        }
    }
    
    public Collection<TargetItemExpansionDoor> get_target_doors()
    {
        return new java.util.LinkedList<TargetItemExpansionDoor>();
    }
    
    public Item get_item()
    {
        return this.item;
    }
    
    public SearchTreeObject get_object()
    {
        return this.item;
    }
    
    public boolean remove_door(ExpandableObject p_door)
    {
        return this.doors.remove(p_door);
    }
    
    /**
     * Returns, if all doors to the neighbour rooms are calculated.
     */
    boolean all_doors_calculated()
    {
        return this.doors_calculated;
    }
    
    void set_doors_calculated(boolean p_value)
    {
        this.doors_calculated = p_value;
    }
    
    
    /**
     * Draws the shape of this room.
     */
    public void draw(java.awt.Graphics p_graphics, boardgraphics.GraphicsContext p_graphics_context, double p_intensity)
    {
        java.awt.Color draw_color = java.awt.Color.WHITE;
        double layer_visibility = p_graphics_context.get_layer_visibility(this.get_layer());
        p_graphics_context.fill_area(this.get_shape(), p_graphics, draw_color, p_intensity * layer_visibility);
        p_graphics_context.draw_boundary(this.get_shape(), 0, draw_color, p_graphics, layer_visibility);
    }
    
    private final Item item;
    private final int index_in_item;
    private final TileShape shape;
    
    /** The list of doors to neighbour expansion rooms */
    private List<ExpansionDoor> doors;
    
    private boolean doors_calculated = false;
}
