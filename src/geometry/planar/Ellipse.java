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
 * Ellipse.java
 *
 * Created on 27. Mai 2005, 07:17
 *
 */

package geometry.planar;

/**
 * Describes functionality of an elllipse in the plane.
 * Does not implement the ConvexShape interface, because coordinates are float.
 *
 * @author Alfons Wirtz
 */
public class Ellipse implements java.io.Serializable
{
    
    /** Creates a new instance of Ellipse */
    public Ellipse(FloatPoint p_center, double p_rotation, double p_radius_1, double p_radius_2)
    {
        this.center = p_center;
        double curr_rotation;
        if (p_radius_1 >= p_radius_2)
        {
            this.bigger_radius = p_radius_1;
            this.smaller_radius = p_radius_2;
            curr_rotation = p_rotation;
        }
        else
        {
            this.bigger_radius = p_radius_2;
            this.smaller_radius = p_radius_1;
            curr_rotation = p_rotation + 0.5 * Math.PI;
        }
        while (curr_rotation >= Math.PI)
        {
            curr_rotation -= Math.PI;
        }
        while (curr_rotation < 0)
        {
            curr_rotation += Math.PI;
        }
        this.rotation = curr_rotation;
    }
    
    public final FloatPoint center;
    
    /** Rotation of the ellipse in radian normed to 0 <= rotation < pi */
    public final double rotation;
    public final double bigger_radius;
    public final double  smaller_radius;
}
