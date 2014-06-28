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
 * SearchTreeObject.java
 *
 * Created on 10. Januar 2004, 10:08
 */

package board;

/**
 * Common ShapeSearchTree functionality for board.Items and autoroute.ExpansionRooms
 *
 * @author  Alfons Wirtz
 */
public interface SearchTreeObject extends datastructures.ShapeTree.Storable
{
    /**
     * Returns true if this object is an obstacle to objects containing
     *  the net number p_net_no
     */
    boolean is_obstacle(int p_net_no);
    
    /**
     * Returns true if this object is an obstacle to traces containing
     *  the net number p_net_no
     */
    boolean is_trace_obstacle(int p_net_no);
    
    /**
     * returns for this object the layer of the shape with index p_index.
     */
    abstract int shape_layer(int p_index);
}
