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
 * ConvexShape2.java
 *
 * Created on 15. November 2002, 08:44
 */

package geometry.planar;

/**
 * A shape is defined as convex, if for each line segment with both endpoints
 * contained in the shape the whole segment is contained completely in the shape.
 *
 * @author  Alfons Wirtz
 */
public interface ConvexShape extends Shape
{
    
    /**
     * Calculates the offset shape by p_distance.
     * If p_distance > 0, the shape will be enlarged, else the result
     * shape will be smaller.
     */
    ConvexShape offset(double p_distance);
    
    /**
     * Shrinks the shape by p_offset.
     * The result shape will not be empty.
     */
    ConvexShape shrink(double p_offset);
    
    /**
     * Returns the maximum diameter of the shape.
     */
    double max_width();
    
    /**
     * Returns the minimum diameter of the shape.
     */
    double min_width();
}
