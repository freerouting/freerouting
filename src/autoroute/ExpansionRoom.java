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
 * ExpansionRoom.java
 *
 * Created on 15. April 2006, 10:12
 *
 */

package autoroute;

import java.util.List;

import geometry.planar.TileShape;

/**
 *
 * @author alfons
 */
public interface ExpansionRoom
{
    /**
     * Adds p_door to the list of doors of this room.
     */
    void add_door(ExpansionDoor p_door);
    
    /**
     * Returns the list of doors of this room to neighbour expansion rooms
     */
    List<ExpansionDoor> get_doors();
    
    /**
     * Removes all doors from this room.
     */
    void clear_doors();
   
    /**
     * Clears the autorouting info of all  doors for routing the next connection.
     */
    void reset_doors();
    
    /**
     * Checks, if this room has already a door to p_other
     */
    boolean door_exists(ExpansionRoom p_other);
    
    /**
     * Removes p_door from this room.
     * Returns false, if p_room did not contain p_door.
     */
    boolean remove_door (ExpandableObject p_door);
    
    /**
     * Gets the shape of this room.
     */
    TileShape get_shape();
    
    /**
     * Returns the layer of this expansion room.
     */
    int get_layer();
}
