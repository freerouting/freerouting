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
 * LocateFoundConnectionAlgo45Degree.java
 *
 * Created on 1. Februar 2006, 07:43
 *
 */

package autoroute;

import java.util.Collection;
import java.util.LinkedList;
import java.util.SortedSet;

import datastructures.Signum;

import geometry.planar.FloatPoint;
import geometry.planar.FloatLine;
import geometry.planar.TileShape;
import geometry.planar.IntBox;
import geometry.planar.Simplex;

import board.ShapeSearchTree;
import board.AngleRestriction;
import board.Item;
import board.TestLevel;

/**
 *
 * @author Alfons Wirtz
 */
public class LocateFoundConnectionAlgo45Degree extends LocateFoundConnectionAlgo
{
    
    /**
     * Creates a new instance of LocateFoundConnectionAlgo45Degree
     */
    public LocateFoundConnectionAlgo45Degree(MazeSearchAlgo.Result p_maze_search_result,
            AutorouteControl p_ctrl, ShapeSearchTree p_search_tree, AngleRestriction p_angle_restriction,
            SortedSet<Item> p_ripped_item_list, TestLevel p_test_level)
    {
        super(p_maze_search_result, p_ctrl, p_search_tree, p_angle_restriction, p_ripped_item_list, p_test_level);
    }
    
    protected Collection<FloatPoint> calculate_next_trace_corners()
    {
        Collection<FloatPoint> result =  new LinkedList<FloatPoint>();
        
        if (this.current_to_door_index > this.current_target_door_index)
        {
            return result;
        }
        
        BacktrackElement curr_from_info = this.backtrack_array[this.current_to_door_index - 1];
        
        if (curr_from_info.next_room == null)
        {
            System.out.println("LocateFoundConnectionAlgo45Degree.calculate_next_trace_corners: next_room is null");
            return result;
        }
        
        TileShape room_shape = curr_from_info.next_room.get_shape();
        
        int trace_halfwidth = this.ctrl.compensated_trace_half_width[this.current_trace_layer];
        int trace_halfwidth_add = trace_halfwidth + AutorouteEngine.TRACE_WIDTH_TOLERANCE; // add some tolerance for free space expansion rooms.
        int shrink_offset;
        if (curr_from_info.next_room instanceof ObstacleExpansionRoom)
        {
            
            shrink_offset = trace_halfwidth;
        }
        else
        {
            shrink_offset = trace_halfwidth_add;
        }
        
        TileShape shrinked_room_shape = (TileShape) room_shape.offset(-shrink_offset);
        if (!shrinked_room_shape.is_empty())
        {
            // enter the shrinked room shape by a 45 degree angle first
            FloatPoint nearest_room_point = shrinked_room_shape.nearest_point_approx(this.current_from_point);
            boolean horizontal_first =
                    calc_horizontal_first_from_door(curr_from_info.door, this.current_from_point, nearest_room_point);
            nearest_room_point = round_to_integer(nearest_room_point);
            result.add(calculate_additional_corner(this.current_from_point, nearest_room_point,
                    horizontal_first, this.angle_restriction));
            result.add(nearest_room_point);
            this.current_from_point = nearest_room_point;
        }
        else
        {
            shrinked_room_shape = room_shape;
        }
        
        if (this.current_to_door_index == this.current_target_door_index)
        {
            FloatPoint nearest_point = this.current_target_shape.nearest_point_approx(this.current_from_point);
            nearest_point = round_to_integer(nearest_point);
            FloatPoint add_corner = calculate_additional_corner(this.current_from_point, nearest_point, true, this.angle_restriction);
            if (!shrinked_room_shape.contains(add_corner))
            {
                add_corner = calculate_additional_corner(this.current_from_point, nearest_point, false, this.angle_restriction);
            }
            result.add(add_corner);
            result.add(nearest_point);
            ++this.current_to_door_index;
            return result;
        }
        
        BacktrackElement curr_to_info = this.backtrack_array[this.current_to_door_index];
        if (!(curr_to_info.door instanceof ExpansionDoor))
        {
            System.out.println("LocateFoundConnectionAlgo45Degree.calculate_next_trace_corners: ExpansionDoor expected");
            return result;
        }
        ExpansionDoor curr_to_door = (ExpansionDoor) curr_to_info.door;
        
        
        FloatPoint nearest_to_door_point;
        if (curr_to_door.dimension == 2)
        {
            // May not happen in free angle routing mode because then corners are cut off.
            TileShape to_door_shape = curr_to_door.get_shape();
            
            TileShape shrinked_to_door_shape = (TileShape) to_door_shape.shrink(shrink_offset);
            nearest_to_door_point = shrinked_to_door_shape.nearest_point_approx(this.current_from_point);
            nearest_to_door_point = round_to_integer(nearest_to_door_point);
        }
        else
        {
            FloatLine[] line_sections = curr_to_door.get_section_segments(trace_halfwidth);
            if (curr_to_info.section_no_of_door >= line_sections.length)
            {
                System.out.println("LocateFoundConnectionAlgo45Degree.calculate_next_trace_corners: line_sections inconsistent");
                return result;
            }
            FloatLine curr_line_section = line_sections[curr_to_info.section_no_of_door];
            nearest_to_door_point =  curr_line_section.nearest_segment_point(this.current_from_point);
            
            boolean nearest_to_door_point_ok = true;
            if (curr_to_info.next_room != null)
            {
                Simplex next_room_shape = curr_to_info.next_room.get_shape().to_Simplex();
                // with IntBox or IntOctagon the next calculation will not work, because they have
                // border lines of lenght 0.
                FloatPoint[] nearest_points = next_room_shape.nearest_border_points_approx(nearest_to_door_point, 2);
                if (nearest_points.length >= 2)
                {
                    nearest_to_door_point_ok = nearest_points[1].distance(nearest_to_door_point) >= trace_halfwidth_add;
                }
            }
            if (!nearest_to_door_point_ok)
            {
                // may be the room has an acute (45 degree) angle at a corner of the door
                nearest_to_door_point =  curr_line_section.a.middle_point(curr_line_section.b);
            }
        }
        nearest_to_door_point = round_to_integer(nearest_to_door_point);
        boolean horizontal_first =
                calc_horizontal_first_to_door(curr_to_info.door, this.current_from_point, nearest_to_door_point);
        result.add(calculate_additional_corner(this.current_from_point, nearest_to_door_point,
                horizontal_first, this.angle_restriction));
        result.add(nearest_to_door_point);
        ++this.current_to_door_index;
        return result;
    }
    
    private static FloatPoint round_to_integer(FloatPoint p_point)
    {
        return p_point.round().to_float();
    }
    
    /**
     * Calculates, if the next 45-degree angle should be horizontal first when coming fromm
     * p_from_point on p_from_door.
     */
    private static boolean calc_horizontal_first_from_door(ExpandableObject p_from_door,
            FloatPoint p_from_point, FloatPoint p_to_point)
    {
        TileShape door_shape = p_from_door.get_shape();
        IntBox from_door_box = door_shape.bounding_box();
        if (p_from_door.get_dimension() != 1)
        {
            return from_door_box.height() >= from_door_box.width();
        }
        
        FloatLine door_line_segment = door_shape.diagonal_corner_segment();
        FloatPoint left_corner;
        FloatPoint right_corner;
        if (door_line_segment.a.x < door_line_segment.b.x || door_line_segment.a.x == door_line_segment.b.x
                && door_line_segment.a.y <= door_line_segment.b.y)
        {
            left_corner = door_line_segment.a;
            right_corner = door_line_segment.b;
        }
        else
        {
            left_corner = door_line_segment.b;
            right_corner = door_line_segment.a;
        }
        double door_dx = right_corner.x - left_corner.x;
        double door_dy = right_corner.y - left_corner.y;
        double abs_door_dy =  Math.abs(door_dy);
        double door_max_width = Math.max(door_dx, abs_door_dy);
        boolean result;
        double door_half_max_width = 0.5 * door_max_width;
        if (from_door_box.width() <= door_half_max_width)
        {
            // door is about vertical
            result = true;
        }
        else if (from_door_box.height() <= door_half_max_width)
        {
            // door is about horizontal
            result = false;
        }
        else
        {
            double dx = p_to_point.x - p_from_point.x;
            double dy = p_to_point.y - p_from_point.y;
            if (left_corner.y < right_corner.y)
            {
                // door is about right diagonal
                if (Signum.of(dx) == Signum.of(dy))
                {
                    result = Math.abs(dx) > Math.abs(dy);
                }
                else
                {
                    result = Math.abs(dx) < Math.abs(dy);
                }
                
            }
            else
            {
                // door is about left diagonal
                if (Signum.of(dx) == Signum.of(dy))
                {
                    result = Math.abs(dx) < Math.abs(dy);
                }
                else
                {
                    result = Math.abs(dx) > Math.abs(dy);
                }
            }
        }
        return result;
    }
    
    /**
     * Calculates, if the 45-degree angle to the next door shape should be horizontal first when coming fromm
     * p_from_point.
     */
    private boolean calc_horizontal_first_to_door(ExpandableObject p_to_door,
            FloatPoint p_from_point, FloatPoint p_to_point)
    {
        TileShape door_shape = p_to_door.get_shape();
        IntBox from_door_box = door_shape.bounding_box();
        if (p_to_door.get_dimension() != 1)
        {
            return from_door_box.height() <= from_door_box.width();
        }
        FloatLine door_line_segment = door_shape.diagonal_corner_segment();
        FloatPoint left_corner;
        FloatPoint right_corner;
        if (door_line_segment.a.x < door_line_segment.b.x || door_line_segment.a.x == door_line_segment.b.x
                && door_line_segment.a.y <= door_line_segment.b.y)
        {
            left_corner = door_line_segment.a;
            right_corner = door_line_segment.b;
        }
        else
        {
            left_corner = door_line_segment.b;
            right_corner = door_line_segment.a;
        }
        double door_dx = right_corner.x - left_corner.x;
        double door_dy = right_corner.y - left_corner.y;
        double abs_door_dy =  Math.abs(door_dy);
        double door_max_width = Math.max(door_dx, abs_door_dy);
        boolean result;
        double door_half_max_width = 0.5 * door_max_width;
        if (from_door_box.width() <= door_half_max_width)
        {
            // door is about vertical
            result = false;
        }
        else if (from_door_box.height() <= door_half_max_width)
        {
            // door is about horizontal
            result = true;
        }
        else
        {
            double dx = p_to_point.x - p_from_point.x;
            double dy = p_to_point.y - p_from_point.y;
            if (left_corner.y < right_corner.y)
            {
                // door is about right diagonal
                if (Signum.of(dx) == Signum.of(dy))
                {
                    result = Math.abs(dx) < Math.abs(dy);
                }
                else
                {
                    result = Math.abs(dx) > Math.abs(dy);
                }
                
            }
            else
            {
                // door is about left diagonal
                if (Signum.of(dx) == Signum.of(dy))
                {
                    result = Math.abs(dx) > Math.abs(dy);
                }
                else
                {
                    result = Math.abs(dx) < Math.abs(dy);
                }
            }
        }
        return result;
    }
    
}
