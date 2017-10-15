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
 * Area.java
 *
 * Created on 22. Juni 2003, 10:16
 */

package geometry.planar;

/**
 * An Area is a not necessarily simply connected Shape, which means,
 * that it may contain holes.
 * The border and the holes of an Area are of class Shape.
 *
 * @author Alfons Wirtz
 */
public interface Area
{
    /**
     * returns true, if the area is empty
     */
    boolean is_empty();
    
    
    /**
     * returns true, if the area is contained in a sufficiently large box
     */
    boolean is_bounded();
    
    /**
     * returns 2, if the area contains 2 dimensional shapes ,
     * 1, if it contains curves,
     * 0, if it is reduced to a points and
     * -1, if it is empty.
     */
    int dimension();
    
    
    /**
     * Checks, if this area is completely contained in p_box.
     */
    boolean is_contained_in(IntBox p_box);
    
    /**
     * returns the border shape of this area
     */
    Shape get_border();
    
    /**
     * Returns the array of holes, of this area.
     */
    Shape[] get_holes();
    
    /**
     * Returns the smallest surrounding box of the area.
     * If the area is not bounded, some coordinates of the resulting
     * box may be equal Integer.MAX_VALUE
     */
    IntBox bounding_box();
    
    /**
     * Returns the smallest surrounding octagon of the area.
     * If the area is not bounded, some coordinates of the resulting
     * octagon may be equal Integer.MAX_VALUE
     */
    IntOctagon bounding_octagon();
    
    /**
     * Returns true, if p_point is contained in this area,
     * but not inside a hole.
     * Being on the border is not defined for FloatPoints
     * because of numerical inacurracy.
     */
    boolean contains(FloatPoint p_point);
    
    /**
     * Returns true, if p_point is inside or on the border
     * of this area, but not inside a hole.
     */
    boolean contains(Point p_point);
    
    /**
     * Calculates an approximation of the nearest point of the shape
     * to p_from_point
     */
    FloatPoint nearest_point_approx(FloatPoint p_ftom_point);
    
    /**
     * Turns this area by p_factor times 90 degree around p_pole.
     */
    Area turn_90_degree(int p_factor, IntPoint p_pole);
    
    /**
     * Rotates the area around p_pole by p_angle.
     * The result may be not exact.
     */ 
    Area rotate_approx(double p_angle, FloatPoint p_pole);
    
    /**
     * Returns the affine translation of the area by p_vector
     */
    Area translate_by(Vector p_vector);
    
    /**
     * Mirrors this area at the horizontal line through p_pole.
     */
    Area mirror_horizontal(IntPoint p_pole);
    
    
    /**
     * Mirrors this area at the vertical line through p_pole.
     */
    Area mirror_vertical (IntPoint p_pole);
    
    /**
     * Returns an approximation of the corners of this area.
     */
    FloatPoint[] corner_approx_arr();
    
    /**
     * Returns a division of this area into convex pieces.
     */
    TileShape[] split_to_convex();

}
