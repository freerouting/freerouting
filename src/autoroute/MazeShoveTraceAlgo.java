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
 * MazeShoveTraceAlgo.java
 *
 * Created on 10. Mai 2006, 06:41
 *
 */

package autoroute;

import java.util.Collection;

import geometry.planar.TileShape;
import geometry.planar.Line;
import geometry.planar.Polyline;
import geometry.planar.Point;
import geometry.planar.FloatPoint;
import geometry.planar.FloatLine;
import geometry.planar.Side;
import geometry.planar.Direction;
import geometry.planar.LineSegment;

import board.Item;
import board.RoutingBoard;
import board.ShoveTraceAlgo;

/**
 * Auxiliary functions used in MazeSearchAlgo.
 *
 * @author Alfons Wirtz
 */
public class MazeShoveTraceAlgo
{
    
    /**
     * Returns false, if the algorithm did not succeed and trying to shove from another door section
     * may be more successful.
     */
    public static boolean check_shove_trace_line(MazeListElement p_list_element,
            ObstacleExpansionRoom p_obstacle_room, RoutingBoard p_board, AutorouteControl p_ctrl,
            boolean p_shove_to_the_left, Collection<DoorSection> p_to_door_list)
    {
        if (!(p_list_element.door instanceof ExpansionDoor))
        {
            return true;
        }
        ExpansionDoor from_door = (ExpansionDoor) p_list_element.door;
        if (!(p_obstacle_room.get_item() instanceof board.PolylineTrace))
        {
            return true;
        }
        board.PolylineTrace obstacle_trace = (board.PolylineTrace)p_obstacle_room.get_item();
        int trace_layer = p_obstacle_room.get_layer();
        // only traces with the same halfwidth and the same clearance class can be shoved.
        if (obstacle_trace.get_half_width() != p_ctrl.trace_half_width[trace_layer]
                || obstacle_trace.clearance_class_no() != p_ctrl.trace_clearance_class_no)
        {
            return true;
        }
        double compensated_trace_half_width = p_ctrl.compensated_trace_half_width[trace_layer];
        TileShape from_door_shape = from_door.get_shape();
        if (from_door_shape.max_width() < 2 * compensated_trace_half_width)
        {
            return true;
        }
        int trace_corner_no = p_obstacle_room.get_index_in_item();
        
        Polyline trace_polyline = obstacle_trace.polyline();
        
        if (trace_corner_no >= trace_polyline.arr.length - 1)
        {
            System.out.println("MazeShoveTraceAlgo.check_shove_trace_line: trace_corner_no to big");
            return false;
        }
        Collection<ExpansionDoor> room_doors = p_obstacle_room.get_doors();
        // The side of the trace line seen from the doors to expand.
        // Used to determine, if a door is on the right side to put it into the p_door_list.
        LineSegment shove_line_segment;
        if (from_door.dimension == 2)
        {
            // shove from a link door into the direction of the other link door.
            CompleteExpansionRoom other_room = from_door.other_room(p_obstacle_room);
            if (!(other_room instanceof ObstacleExpansionRoom))
            {
                return false;
            }
            if (!end_points_matching(obstacle_trace, ((ObstacleExpansionRoom)other_room).get_item()))
            {
                return false;
            }
            FloatPoint door_center = from_door_shape.centre_of_gravity();
            FloatPoint corner_1 = trace_polyline.corner_approx(trace_corner_no);
            FloatPoint corner_2 = trace_polyline.corner_approx(trace_corner_no + 1);
            if (corner_1.distance_square(corner_2) < 1)
            {
                // shove_line_segment may be reduced to a point
                return false;
            }
            boolean shove_into_direction_of_trace_start =
                    door_center.distance_square(corner_2) < door_center.distance_square(corner_1);
            shove_line_segment = new LineSegment(trace_polyline, trace_corner_no + 1);
            if (shove_into_direction_of_trace_start)
            {
                
                // shove from the endpoint to the start point of the line segment
                shove_line_segment = shove_line_segment.opposite();
            }
        }
        else
        {
            CompleteExpansionRoom from_room = from_door.other_room(p_obstacle_room);
            FloatPoint from_point = from_room.get_shape().centre_of_gravity();
            Line shove_trace_line = trace_polyline.arr[trace_corner_no + 1];
            FloatLine door_line_segment = from_door_shape.diagonal_corner_segment();
            Side side_of_trace_line = shove_trace_line.side_of(door_line_segment.a, 0);
            
            FloatLine polar_line_segment = from_door_shape.polar_line_segment(from_point);
            
            boolean door_line_swapped =
                    polar_line_segment.b.distance_square(door_line_segment.a) <
                    polar_line_segment.a.distance_square(door_line_segment.a);
            
            boolean section_ok;
            // shove only from the right most section to the right or from the left most section to the left.
            
            double shape_entry_check_distance  = compensated_trace_half_width + 5;
            double check_dist_square = shape_entry_check_distance * shape_entry_check_distance;
            
            if (p_shove_to_the_left && !door_line_swapped || !p_shove_to_the_left && door_line_swapped)
            {
                section_ok =
                        p_list_element.section_no_of_door == p_list_element.door.maze_search_element_count() - 1
                        && (p_list_element.shape_entry.a.distance_square(door_line_segment.b) <= check_dist_square
                        || p_list_element.shape_entry.b.distance_square(door_line_segment.b) <= check_dist_square);
            }
            else
            {
                section_ok =
                        p_list_element.section_no_of_door == 0
                        && (p_list_element.shape_entry.a.distance_square(door_line_segment.a) <= check_dist_square
                        || p_list_element.shape_entry.b.distance_square(door_line_segment.a) <= check_dist_square);
            }
            if (!section_ok)
            {
                return false;
            }
            
            
            // create the line segment for shoving
            
            FloatLine shrinked_line_segment = polar_line_segment.shrink_segment(compensated_trace_half_width);
            Direction perpendicular_direction = shove_trace_line.direction().turn_45_degree(2);
            if (side_of_trace_line == Side.ON_THE_LEFT)
            {
                if (p_shove_to_the_left)
                {
                    Line start_closing_line = new Line(shrinked_line_segment.b.round(), perpendicular_direction);
                    shove_line_segment =
                            new LineSegment(start_closing_line, trace_polyline.arr[trace_corner_no + 1],
                            trace_polyline.arr[trace_corner_no + 2]);
                }
                else
                {
                    Line start_closing_line = new Line(shrinked_line_segment.a.round(), perpendicular_direction);
                    shove_line_segment =
                            new LineSegment(start_closing_line, trace_polyline.arr[trace_corner_no + 1].opposite(),
                            trace_polyline.arr[trace_corner_no].opposite());
                }
            }
            else
            {
                if (p_shove_to_the_left)
                {
                    Line start_closing_line = new Line(shrinked_line_segment.b.round(), perpendicular_direction);
                    shove_line_segment =
                            new LineSegment(start_closing_line, trace_polyline.arr[trace_corner_no + 1].opposite(),
                            trace_polyline.arr[trace_corner_no].opposite());
                }
                else
                {
                    Line start_closing_line = new Line(shrinked_line_segment.a.round(), perpendicular_direction);
                    shove_line_segment =
                            new LineSegment(start_closing_line, trace_polyline.arr[trace_corner_no + 1],
                            trace_polyline.arr[trace_corner_no + 2]);
                }
            }
        }
        int trace_half_width = p_ctrl.trace_half_width[trace_layer];
        int [] net_no_arr = new int[1];
        net_no_arr[0] = p_ctrl.net_no;
        
        double shove_width =
                p_board.check_trace_segment(shove_line_segment, trace_layer, net_no_arr, trace_half_width,
                p_ctrl.trace_clearance_class_no, true);
        boolean segment_shortened = false;
        if (shove_width < Integer.MAX_VALUE)
        {
            // shorten shove_line_segment
            shove_width = shove_width - 1;
            if (shove_width <= 0)
            {
                return true;
            }
            shove_line_segment = shove_line_segment.change_length_approx(shove_width);
            segment_shortened = true;
        }
        
        FloatPoint from_corner = shove_line_segment.start_point_approx();
        FloatPoint to_corner = shove_line_segment.end_point_approx();
        boolean segment_ist_point = from_corner.distance_square(to_corner) < 0.1;
        
        if (!segment_ist_point)
        {
            shove_width = ShoveTraceAlgo.check(p_board, shove_line_segment, p_shove_to_the_left, trace_layer, net_no_arr, trace_half_width,
                    p_ctrl.trace_clearance_class_no, p_ctrl.max_shove_trace_recursion_depth, p_ctrl.max_shove_via_recursion_depth);
            
            if (shove_width <= 0)
            {
                return true;
            }
        }
        
        // Put the doors on this side of the room into p_to_door_list with
        if (segment_shortened)
        {
            shove_width = Math.min(shove_width, from_corner.distance(to_corner));
        }
        
        Line shove_line = shove_line_segment.get_line();
        
        // From_door_compare_distance is used to check, that a door is between from_door and the end point
        // of the shove line.
        double from_door_compare_distance;
        if (from_door.dimension == 2 || segment_ist_point)
        {
            from_door_compare_distance = Double.MAX_VALUE;
        }
        else
        {
            from_door_compare_distance = to_corner.distance_square(from_door_shape.corner_approx(0));
        }
        
        for (ExpansionDoor curr_door : room_doors)
        {
            if (curr_door == from_door)
            {
                continue;
            }
            if (curr_door.first_room instanceof ObstacleExpansionRoom &&
                    curr_door.second_room instanceof ObstacleExpansionRoom)
            {
                Item first_room_item = ((ObstacleExpansionRoom)curr_door.first_room).get_item();
                Item second_room_item = ((ObstacleExpansionRoom)curr_door.second_room).get_item();
                if (first_room_item != second_room_item)
                {
                    // there may be topological problems at a trace fork
                    continue;
                }
            }
            TileShape curr_door_shape = curr_door.get_shape();
            if (curr_door.dimension == 2 && shove_width >= Integer.MAX_VALUE)
            {
                boolean add_link_door = curr_door_shape.contains(to_corner);
                
                
                if (add_link_door)
                {
                    FloatLine[] line_sections = curr_door.get_section_segments(compensated_trace_half_width);
                    p_to_door_list.add(new DoorSection(curr_door, 0, line_sections[0]));
                }
                continue;
            }
            else if (!segment_ist_point)
            {
                // now curr_door is 1-dimensional
                
                // check, that curr_door is on the same border_line as p_from_door.
                FloatLine curr_door_segment = curr_door_shape.diagonal_corner_segment();
                if (curr_door_segment == null)
                {
                    if (p_board.get_test_level() == board.TestLevel.ALL_DEBUGGING_OUTPUT)
                    {
                        System.out.println("MazeShoveTraceAlgo.check_shove_trace_line: door shape is empty");
                    }
                    continue;
                }
                Side start_corner_side_of_trace_line = shove_line.side_of(curr_door_segment.a, 0);
                Side end_corner_side_of_trace_line = shove_line.side_of(curr_door_segment.b, 0);
                if (p_shove_to_the_left)
                {
                    if (start_corner_side_of_trace_line != Side.ON_THE_LEFT || end_corner_side_of_trace_line != Side.ON_THE_LEFT)
                    {
                        continue;
                    }
                }
                else
                {
                    if (start_corner_side_of_trace_line != Side.ON_THE_RIGHT || end_corner_side_of_trace_line != Side.ON_THE_RIGHT)
                    {
                        continue;
                    }
                }
                FloatLine curr_door_line = curr_door_shape.polar_line_segment(from_corner);
                FloatPoint curr_door_nearest_corner;
                if (curr_door_line.a.distance_square(from_corner) <= curr_door_line.b.distance_square(from_corner))
                {
                    curr_door_nearest_corner = curr_door_line.a;
                }
                else
                {
                    curr_door_nearest_corner = curr_door_line.b;
                }
                if (to_corner.distance_square(curr_door_nearest_corner) >= from_door_compare_distance)
                {
                    // curr_door is not located into the direction of to_corner.
                    continue;
                }
                FloatPoint curr_door_projection = curr_door_nearest_corner.projection_approx(shove_line);
                
                if (curr_door_projection.distance(from_corner) + compensated_trace_half_width <= shove_width)
                {
                    FloatLine[] line_sections = curr_door.get_section_segments(compensated_trace_half_width);
                    for (int i = 0; i < line_sections.length; ++i)
                    {
                        FloatLine curr_line_section = line_sections[i];
                        FloatPoint curr_section_nearest_corner;
                        if (curr_line_section.a.distance_square(from_corner) <= curr_line_section.b.distance_square(from_corner))
                        {
                            curr_section_nearest_corner = curr_line_section.a;
                        }
                        else
                        {
                            curr_section_nearest_corner = curr_line_section.b;
                        }
                        FloatPoint curr_section_projection = curr_section_nearest_corner.projection_approx(shove_line);
                        if (curr_section_projection.distance(from_corner) <= shove_width)
                        {
                            p_to_door_list.add(new DoorSection(curr_door, i, curr_line_section));
                        }
                    }
                }
            }
        }
        return true;
    }
    
    /**
     * Check if the endpoints of p_trace and p_from_item are maching, so that the
     * shove can continue through a link door.
     */
    private static boolean end_points_matching(board.PolylineTrace p_trace, Item p_from_item)
    {
        if (p_from_item == p_trace)
        {
            return true;
        }
        if (!p_trace.shares_net(p_from_item))
        {
            return false;
        }
        boolean points_matching;
        if (p_from_item instanceof board.DrillItem)
        {
            Point from_center = ((board.DrillItem) p_from_item).get_center();
            points_matching =  from_center.equals(p_trace.first_corner()) || from_center.equals(p_trace.last_corner());
        }
        else if (p_from_item instanceof board.PolylineTrace)
        {
            board.PolylineTrace from_trace = (board.PolylineTrace) p_from_item;
            points_matching = p_trace.first_corner().equals(from_trace.first_corner()) ||
                    p_trace.first_corner().equals(from_trace.last_corner()) ||
                    p_trace.last_corner().equals(from_trace.first_corner()) ||
                    p_trace.last_corner().equals(from_trace.last_corner());
        }
        else
        {
            points_matching = false;
        }
        return points_matching;
    }
    
    public static class DoorSection
    {
        DoorSection(ExpansionDoor p_door, int p_section_no, FloatLine p_section_line)
        {
            door = p_door;
            section_no = p_section_no;
            section_line = p_section_line;
            
        }
        final ExpansionDoor door;
        final int section_no;
        final FloatLine section_line;
    }
}
