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

package geometry.planar;

/**
 *
 * Describing the functionality for the fixed directions of a RegularTileShape.
 *
 * @author Alfons Wirtz
 */

public interface ShapeBoundingDirections
{
    /**
     * Retuns the count of the fixed directions.
     */
    int count();

    /**
     * Calculates for an abitrary ConvexShape a surrounding RegularTileShape
     * with this fixed directions.
     * Is used in the implementation of the seach trees.
     */
    RegularTileShape bounds(ConvexShape p_shape);

    /**
     * Auxiliary function to implement the same function with parameter
     * type ConvexShape.
     */
    RegularTileShape bounds(IntBox p_box);
    /**
     * Auxiliary function to implement the same function with parameter
     * type ConvexShape.
     */
    RegularTileShape bounds(IntOctagon p_oct);
    /**
     * Auxiliary function to implement the same function with parameter
     * type ConvexShape.
     */
    RegularTileShape bounds(Simplex p_simplex);
    /**
     * Auxiliary function to implement the same function with parameter
     * type ConvexShape.
     */
    RegularTileShape bounds(Circle p_circle);
    /**
     * Auxiliary function to implement the same function with parameter
     * type ConvexShape.
     */
    RegularTileShape bounds(PolygonShape p_polygon);
}