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
 * Implements the abstract class ShapeDirections as
 * the 4 orthogonal directions.
 * The class is a singleton with the only instanciation INSTANCE.
 *

 * @author Alfons Wirtz
 */
public class OrthogonalBoundingDirections implements ShapeBoundingDirections
{
    /**
     * the one and only instantiation
     */
    public static final OrthogonalBoundingDirections
          INSTANCE = new OrthogonalBoundingDirections();

    public int count()
    {
        return 4;
    }

    public RegularTileShape bounds (ConvexShape p_shape)
    {
        return p_shape.bounding_shape(this);
    }

    public RegularTileShape bounds (IntBox p_box)
    {
        return p_box;
    }
        
    public RegularTileShape bounds (IntOctagon p_oct)
    {
        return p_oct.bounding_box();
    }

    public RegularTileShape bounds (Simplex p_simplex)
    {
        return p_simplex.bounding_box();
    }
    
    public RegularTileShape bounds (Circle p_circle)
    {
        return p_circle.bounding_box();
    }
    
    public RegularTileShape bounds (PolygonShape p_polygon)
    {
        return p_polygon.bounding_box();
    }

    /**
     * prevent instantiation
     */
    private OrthogonalBoundingDirections()
    {
    }
}