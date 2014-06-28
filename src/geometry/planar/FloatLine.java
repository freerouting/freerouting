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
 * FloatLine.java
 *
 * Created on 19. Februar 2004, 07:22
 */

package geometry.planar;

/**
 * Defines a line in the plane by to FloatPoints.
 * Calculations with FloatLines are generally not exact.
 * For that reason collinearity for example is not defined for FloatLines.
 * If exactnesss is needed, use the class Line instead.
 *
 * @author  Alfons Wirtz
 */
public class FloatLine
{
    
    /**
     * Creates a line from two FloatPoints.
     */
    public FloatLine(FloatPoint p_a, FloatPoint p_b)
    {
        if (p_a == null || p_b == null)
        {
            System.out.println("FloatLine: Parameter is null");
        }
        a = p_a;
        b = p_b;
    }
    
    /**
     * Returns the FloatLine with swapped end points.
     */
    public FloatLine opposite()
    {
        return new FloatLine(this.b, this.a);
    }
    
    public FloatLine adjust_direction(FloatLine p_other)
    {
        if (this.b.side_of(this.a, p_other.a )== p_other.b.side_of(this.a, p_other.a ))
        {
            return this;
        }
        return this.opposite();
    }
    
    /**
     * Calculates the intersection of this line with p_other.
     * Returns null, if the lines are parallel.
     */
    public FloatPoint intersection(FloatLine p_other)
    {
        double d1x = this.b.x - this.a.x;
        double d1y = this.b.y - this.a.y;
        double d2x = p_other.b.x - p_other.a.x;
        double d2y = p_other.b.y - p_other.a.y;
        double det_1 = this.a.x * this.b.y - this.a.y * this.b.x;
        double det_2 = p_other.a.x * p_other.b.y - p_other.a.y * p_other.b.x;
        double det = d2x * d1y - d2y * d1x;
        double is_x;
        double is_y;
        if(det == 0)
        {
            return null;
        }
        is_x = (d2x * det_1 - d1x * det_2) / det;
        is_y = (d2y * det_1 - d1y * det_2) / det;
        return new FloatPoint(is_x, is_y);
    }
    
    /**
     * translates the line perpendicular at about p_dist.
     * If p_dist > 0, the line will be translated to the left, else to the right
     */
    public FloatLine translate(double p_dist)
    {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double dxdx = dx * dx;
        double dydy = dy * dy;
        double lenght = Math.sqrt(dxdx + dydy);
        FloatPoint new_a;
        if (dxdx <= dydy)
        {
            // translate along the x axis
            double rel_x = (p_dist * lenght) / dy;
            new_a = new FloatPoint(this.a.x - rel_x, this.a.y);
        }
        else
        {
            // translate along the  y axis
            double rel_y = (p_dist * lenght) / dx;
            new_a = new FloatPoint(this.a.x, this.a.y + rel_y);
        }
        FloatPoint new_b = new FloatPoint(new_a.x + dx, new_a.y + dy);
        return new FloatLine(new_a, new_b);
    }
    
    /**
     * Returns the signed distance of this line from p_point.
     * The result will be positive, if the line is on the left of p_point,
     * else negative.
     */
    public double signed_distance(FloatPoint p_point)
    {
        double dx = this.b.x - this.a.x;
        double dy = this.b.y - this.a.y;
        double det =
                dy * (p_point.x - this.a.x) -
                dx * (p_point.y - this.a.y);
        // area of the parallelogramm spanned by the 3 points
        double length = Math.sqrt(dx * dx + dy * dy);
        return det / length;
    }
    
    /**
     * Returns an approximation of the perpensicular projection
     * of p_point onto this line.
     */
    public FloatPoint perpendicular_projection(FloatPoint p_point)
    {
        
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        if (dx == 0 && dy == 0)
        {
            return this.a;
        }
        
        double dxdx = dx * dx;
        double dydy = dy * dy;
        double dxdy = dx * dy;
        double denominator = dxdx + dydy;
        double  det = a.x * b.y - b.x * a.y;
        
        double x = (p_point.x * dxdx + p_point.y * dxdy + det * dy) / denominator;
        double y = (p_point.x * dxdy + p_point.y * dydy - det * dx) / denominator;
        
        return new FloatPoint(x, y);
    }
    
    /**
     * Returns the distance of p_point to the nearest point of this line
     * betweem this.a and this.b.
     */
    public double segment_distance(FloatPoint p_point)
    {
        FloatPoint projection = perpendicular_projection(p_point);
        double result;
        if (projection.is_contained_in_box(this.a, this.b, 0.01))
        {
            result = p_point.distance(projection);
        }
        else
        {
            result = Math.min(p_point.distance(a), p_point.distance(b));
        }
        return result;
    }
    
    /**
     * Returns the perpendicular projection of p_line_segment onto this oriented line segment,
     * Returns null, if the projection is empty.
     */
    public FloatLine segment_projection(FloatLine p_line_segment)
    {
        if (this.b.scalar_product(this.a, p_line_segment.a) < 0)
        {
            return null;
        }
        if (this.a.scalar_product(this.b, p_line_segment.b) < 0)
        {
            return null;
        }
        FloatPoint projected_a;
        if (this.a.scalar_product(this.b, p_line_segment.a) < 0)
        {
            projected_a = this.a;
        }
        else
        {
            projected_a  = this.perpendicular_projection(p_line_segment.a);
            if (Math.abs(projected_a.x) >= Limits.CRIT_INT || Math.abs(projected_a.y) >= Limits.CRIT_INT)
            {
                return null;
            }
        }
        FloatPoint projected_b;
        if (this.b.scalar_product(this.a, p_line_segment.b) < 0)
        {
            projected_b = this.b;
        }
        else
        {
            projected_b  = this.perpendicular_projection(p_line_segment.b);
        }
        if (Math.abs(projected_b.x) >= Limits.CRIT_INT || Math.abs(projected_b.y) >= Limits.CRIT_INT)
        {
            return null;
        }
        return new FloatLine(projected_a, projected_b);
    }
    
    /**
     * Returns the projection of p_line_segment onto this oriented line segment
     * by moving p_line_segment perpendicular into the direction of this  line segmant
     * Returns null, if the projection is empty or p_line_segment.a  == p_line_segment.b
     */
    public FloatLine segment_projection_2(FloatLine p_line_segment)
    {
        if (p_line_segment.a.scalar_product(p_line_segment.b, this.b) <= 0)
        {
            return null;
        }
        if ( p_line_segment.b.scalar_product(p_line_segment.a, this.a) <= 0)
        {
            return null;
        }
        FloatPoint projected_a;
        if (p_line_segment.a.scalar_product(p_line_segment.b, this.a) < 0)
        {
            FloatLine curr_perpendicular_line =
                    new FloatLine(p_line_segment.a, p_line_segment.b.turn_90_degree(1, p_line_segment.a));
            projected_a = curr_perpendicular_line.intersection(this);
            if (projected_a == null || Math.abs(projected_a.x) >= Limits.CRIT_INT || Math.abs(projected_a.y) >= Limits.CRIT_INT)
            {
                return null;
            }
        }
        else
        {
            projected_a = this.a;
        }
        
        FloatPoint projected_b;
        
        if (p_line_segment.b.scalar_product(p_line_segment.a, this.b) < 0)
        {
            FloatLine curr_perpendicular_line =
                    new FloatLine(p_line_segment.b, p_line_segment.a.turn_90_degree(1, p_line_segment.b));
            projected_b = curr_perpendicular_line.intersection(this);
            if (projected_b == null || Math.abs(projected_b.x) >= Limits.CRIT_INT || Math.abs(projected_b.y) >= Limits.CRIT_INT)
            {
                return null;
            }
        }
        else
        {
            projected_b = this.b;
        }
        return new FloatLine(projected_a, projected_b);
    }
    
    /**
     * Shrinks this line on both sides by p_value.
     * The result will contain at least the gravity point of the line.
     */
    public FloatLine shrink_segment(double p_offset)
    {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        if (dx == 0 && dy == 0)
        {
            return this;
        }
        double length = Math.sqrt(dx * dx + dy * dy);
        double offset = Math.min(p_offset, length/2);
        FloatPoint new_a = new FloatPoint(a.x + (dx * offset) / length, a.y + (dy * offset) / length);
        double new_length = length - offset;
        FloatPoint new_b = new FloatPoint(a.x + (dx * new_length) / length, a.y + (dy * new_length) / length);
        return new FloatLine(new_a, new_b);
    }
    
    /**
     * Calculates the nearest point on this line to p_from_point between
     * this.a and this.b.
     */
    public FloatPoint nearest_segment_point(FloatPoint p_from_point)
    {
        FloatPoint projection = this.perpendicular_projection(p_from_point);
        if (projection.is_contained_in_box(this.a, this.b, 0.01))
        {
            return projection;
        }
        // Now the projection is outside the line segment.
        FloatPoint result;
        if (p_from_point.distance_square(this.a) <= p_from_point.distance_square(this.b))
        {
            result = this.a;
        }
        else
        {
            result = this.b;
        }
        return result;
    }
    
    /**
     * Divides this line segment into p_count line segments of nearly equal length.
     * and at most p_max_section_length.
     */
    public FloatLine[] divide_segment_into_sections(int p_count)
    {
        if (p_count == 0)
        {
            return new FloatLine[0];
        }
        if (p_count == 1)
        {
            FloatLine []  result = new FloatLine[1];
            result[0] = this;
            return result;
        }
        double line_length = this.b.distance(this.a);
        FloatLine[] result = new FloatLine[p_count];
        double section_length = line_length / p_count;
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        FloatPoint curr_a = this.a;
        for (int i = 0; i < p_count; ++i)
        {
            FloatPoint curr_b;
            if (i == p_count - 1)
            {
                curr_b = this.b;
            }
            else
            {
                double curr_b_dist = (i + 1) * section_length;
                double curr_b_x = a.x + (dx * curr_b_dist)/line_length;
                double curr_b_y = a.y + (dy * curr_b_dist)/line_length;
                curr_b = new FloatPoint(curr_b_x, curr_b_y);
            }
            result[i] = new FloatLine(curr_a, curr_b);
            curr_a = curr_b;
        }
        return result;
    }
    
    public final FloatPoint a;
    public final FloatPoint b;
}
