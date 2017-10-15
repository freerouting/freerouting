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
 * CalcFromSide.java
 *
 * Created on 17. August 2003, 07:36
 */

package board;

import geometry.planar.FloatPoint;
import geometry.planar.Line;
import geometry.planar.LineSegment;
import geometry.planar.Point;
import geometry.planar.Polyline;
import geometry.planar.TileShape;
import geometry.planar.Side;

/**
 *
 * @author  Alfons Wirtz
 */
public class CalcFromSide
{
    public static final CalcFromSide NOT_CALCULATED = new CalcFromSide(-1, null);
    
    /**
     * calculates the number of the edge line of p_shape  where p_polyline
     * enters. Used in the push trace algorithm to determine the shove direction.
     * p_no is expected between 1 and p_polyline.line_count - 2 inclusive.
     */
    CalcFromSide(Polyline p_polyline, int p_no, TileShape p_shape )
    {
        int fromside_no = -1;
        FloatPoint intersection = null;
        boolean border_intersection_found = false;
        // calculate the edge_no of p_shape, where p_polyline enters
        for (int curr_no = p_no; curr_no > 0; --curr_no)
        {
            LineSegment curr_seg = new LineSegment(p_polyline, curr_no);
            int [] intersections = curr_seg.border_intersections(p_shape);
            if (intersections.length > 0)
            {
                fromside_no = intersections[0];
                intersection = curr_seg.get_line().intersection_approx(p_shape.border_line(fromside_no));
                border_intersection_found = true;
                break;
            }
        }
        if (!border_intersection_found)
        {
            // The first corner of p_polyline is inside p_shape.
            // Calculate the nearest intersection point of p_polyline.arr[1]
            // with the border of p_shape to the first corner of p_polyline
            FloatPoint from_point = p_polyline.corner_approx(0);
            Line check_line = p_polyline.arr[1];
            double min_dist = Double.MAX_VALUE;
            int edge_count = p_shape.border_line_count();
            for (int i = 0; i < edge_count; ++i)
            {
                Line curr_line = p_shape.border_line(i);
                FloatPoint curr_intersection = check_line.intersection_approx(curr_line);
                double curr_dist =
                        Math.abs(curr_intersection.distance(from_point));
                if (curr_dist < min_dist)
                {
                    fromside_no = i;
                    intersection = curr_intersection;
                    min_dist = curr_dist;
                }
            }
        }
        this.no = fromside_no;
        this.border_intersection = intersection;
    }
    
    /**
     * Calculates the nearest border side of p_shape to p_from_point.
     * Used in the shove_drill_item  algorithm to determine the shove direction.
     */
    CalcFromSide(Point p_from_point, TileShape p_shape)
    {
        Point border_projection = p_shape.nearest_border_point(p_from_point);
        this.no = p_shape.contains_on_border_line_no(border_projection);
        if (this.no < 0)
        {
            System.out.println("CalcFromSide: this.no >= 0 expected");
        }
        this.border_intersection = border_projection.to_float();
    }
    
    /**
     * Calculates the Side of p_shape at the start of p_line_segment.
     * If p_shove_to_the_left, the from_side_no is decremented by 2, else it is increased by 2.
     */
    CalcFromSide(LineSegment p_line_segment, TileShape p_shape, boolean p_shove_to_the_left)
    {
        FloatPoint start_corner = p_line_segment.start_point_approx();
        FloatPoint end_corner = p_line_segment.end_point_approx();
        int border_line_count = p_shape.border_line_count();
        Line check_line = p_line_segment.get_line();
        FloatPoint first_corner = p_shape.corner_approx(0);
        Side prev_side = check_line.side_of(first_corner);
        int front_side_no = -1;
        
        for (int i = 1; i <= border_line_count; ++i)
        {
            FloatPoint next_corner;
            if (i == border_line_count)
            {
                next_corner = first_corner;
            }
            else
            {
                next_corner = p_shape.corner_approx(i);
            }
            Side next_side = check_line.side_of(next_corner);
            if (prev_side != next_side)
            {
                FloatPoint curr_intersection = p_shape.border_line(i - 1).intersection_approx(check_line);
                if (curr_intersection.distance_square(start_corner) < curr_intersection.distance_square(end_corner))
                {
                    front_side_no = i - 1;
                    break;
                }
            }
            prev_side = next_side;
        }
        if (front_side_no < 0)
        {
            System.out.println("CalcFromSide: start corner not found");
            no = -1;
            border_intersection = null;
            return;
        }
        if (p_shove_to_the_left)
        {
            this.no = (front_side_no + 2) % border_line_count;
            
        }
        else
        {
            this.no = (front_side_no + border_line_count - 2) % border_line_count;
        }
        FloatPoint prev_corner = p_shape.corner_approx(this.no);
        FloatPoint next_corner = p_shape.corner_approx((this.no + 1) % border_line_count);
        this.border_intersection = prev_corner.middle_point(next_corner);
    }
    
    
    
    /**
     * Values already calculated. Just create an instance from them.
     */
    CalcFromSide(int p_no, FloatPoint p_border_intersection)
    {
        no = p_no;
        border_intersection = p_border_intersection;
    }
    
    final int no;
    final FloatPoint border_intersection;
}
