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
 * LocateFoundConnectionAlgo.java
 *
 * Created on 14. Februar 2004, 07:55
 */

package autoroute;

import geometry.planar.FloatLine;
import geometry.planar.FloatPoint;
import geometry.planar.Side;
import geometry.planar.TileShape;

import java.util.Collection;
import java.util.SortedSet;
import java.util.LinkedList;

import board.ShapeSearchTree;
import board.AngleRestriction;
import board.Item;
import board.TestLevel;


/**
 * Calculates from the backtrack list the location of the traces and vias,
 * which realize a connection found by the maze search algorithm.
 *
 * @author  Alfons Wirtz
 */
class LocateFoundConnectionAlgoAnyAngle extends LocateFoundConnectionAlgo
{
    
    /** Creates a new instance of LocateFoundConnectionAlgo */
    protected LocateFoundConnectionAlgoAnyAngle(MazeSearchAlgo.Result p_maze_search_result, AutorouteControl p_ctrl,
            ShapeSearchTree p_search_tree, AngleRestriction p_angle_restriction,
            SortedSet<Item> p_ripped_item_list, TestLevel p_test_level)
    {
        super(p_maze_search_result, p_ctrl, p_search_tree, p_angle_restriction, p_ripped_item_list, p_test_level);
    }
    
    /**
     * Calculates a list with the next  point  of the trace under construction.
     * If the trace is completed, the result list will be empty.
     */
    protected Collection<FloatPoint> calculate_next_trace_corners()
    {
        Collection<FloatPoint> result = new LinkedList<FloatPoint>();
        if (this.current_to_door_index >= this.current_target_door_index)
        {
            if (this.current_to_door_index == this.current_target_door_index)
            {
                FloatPoint nearest_point = this.current_target_shape.nearest_point(this.current_from_point.round()).to_float();
                ++this.current_to_door_index;
                result.add(nearest_point);
            }
            return result;
        }
        
        double trace_halfwidth_exact = this.ctrl.compensated_trace_half_width[this.current_trace_layer];
        double trace_halfwidth_max = trace_halfwidth_exact + AutorouteEngine.TRACE_WIDTH_TOLERANCE;
        double trace_halfwidth_middle = trace_halfwidth_exact + c_tolerance;
        
        BacktrackElement curr_to_info = this.backtrack_array[this.current_to_door_index];
        FloatPoint door_left_corner = calc_door_left_corner(curr_to_info);
        FloatPoint door_right_corner = calc_door_right_corner(curr_to_info);
        if (this.current_from_point.side_of(door_left_corner, door_right_corner) != Side.ON_THE_RIGHT)
        {
            // the door is already crossed at this.from_point
            if (this.current_from_point.scalar_product(this.previous_from_point, door_left_corner) >= 0)
            {
                // Also the left corner of the door is passed.
                // That may not be the case if the door line is crossed almost parallel.
                door_left_corner = null;
            }
            if(this.current_from_point.scalar_product(this.previous_from_point, door_right_corner) >= 0)
            {
                // Also the right corner of the door is passed.
                door_right_corner = null;
            }
            if (door_left_corner == null && door_right_corner == null)
            {
                // The door is completely passed.
                ++this.current_to_door_index;
                result.add(this.current_from_point);
                return result;
            }
        }
        
        // Calculate the visibility range for a trace line from current_from_point
        // through the interval from left_most_visible_point to right_most_visible_point,
        // by advancing the door index as far as possible, so that still somthing is visible.
        
        boolean end_of_trace = false;
        FloatPoint left_tangent_point = null;
        FloatPoint right_tangent_point = null;
        int new_door_ind = this.current_to_door_index;
        int left_ind = new_door_ind;
        int right_ind = new_door_ind;
        int curr_door_ind =  this.current_to_door_index + 1;
        FloatPoint result_corner = null;
        
        // construct a maximum lenght straight line through the doors
        
        for (;;)
        {
            left_tangent_point = this.current_from_point.right_tangential_point(door_left_corner, trace_halfwidth_max);
            if (door_left_corner != null && left_tangent_point == null)
            {
                if (this.test_level.ordinal() >=  TestLevel.ALL_DEBUGGING_OUTPUT.ordinal())
                {
                    System.out.println("LocateFoundConnectionAlgo.calculate_next_trace_corner: left tangent point is null");
                }
                left_tangent_point = door_left_corner;
            }
            right_tangent_point = this.current_from_point.left_tangential_point(door_right_corner, trace_halfwidth_max);
            if (door_right_corner != null && right_tangent_point == null)
            {
                if (this.test_level.ordinal() >=  TestLevel.ALL_DEBUGGING_OUTPUT.ordinal())
                {
                    System.out.println("LocateFoundConnectionAlgo.calculate_next_trace_corner: right tangent point is null");
                }
                right_tangent_point = door_right_corner;
            }
            if (left_tangent_point != null && right_tangent_point != null &&
                    right_tangent_point.side_of(this.current_from_point, left_tangent_point) != Side.ON_THE_RIGHT)
            {
                // The gap between  left_most_visible_point and right_most_visible_point ist to small
                // for a trace with the current half width.
                
                double left_corner_distance = door_left_corner.distance(this.current_from_point);
                double right_corner_distance = door_right_corner.distance(this.current_from_point);
                if ( left_corner_distance <= right_corner_distance)
                {
                    new_door_ind = left_ind;
                    result_corner = left_turn_next_corner(this.current_from_point, trace_halfwidth_max, door_left_corner, door_right_corner);
                }
                else
                {
                    new_door_ind = right_ind;
                    result_corner = right_turn_next_corner(this.current_from_point, trace_halfwidth_max, door_right_corner, door_left_corner);
                }
                break;
            }
            if (curr_door_ind >= this.current_target_door_index)
            {
                end_of_trace = true;
                break;
            }
            BacktrackElement next_to_info = this.backtrack_array[curr_door_ind];
            FloatPoint next_left_corner = calc_door_left_corner(next_to_info);
            FloatPoint next_right_corner = calc_door_right_corner(next_to_info);
            if (this.current_from_point.side_of(next_left_corner, next_right_corner) != Side.ON_THE_RIGHT)
            {
                // the door may be already crossed at this.from_point
                if (door_left_corner == null && this.current_from_point.scalar_product(this.previous_from_point, next_left_corner) >= 0)
                {
                    // Also the left corner of the door is passed.
                    // That may not be the case if the door line is crossed almost parallel.
                    next_left_corner = null;
                }
                if(door_right_corner == null && this.current_from_point.scalar_product(this.previous_from_point, next_right_corner) >= 0)
                {
                    // Also the right corner of the door is passed.
                    next_right_corner = null;
                }
                if (next_left_corner == null && next_right_corner == null)
                {
                    // The door is completely passed.
                    // Should not happen because the previous door was not passed compledtely.
                    if (this.test_level.ordinal() >=  TestLevel.ALL_DEBUGGING_OUTPUT.ordinal())
                    {
                        System.out.println("LocateFoundConnectionAlgo.calculate_next_trace_corner: next door passed unexpected");
                    }
                    ++this.current_to_door_index;
                    result.add(this.current_from_point);
                    return result;
                }
            }
            if (door_left_corner != null && door_right_corner != null)
                // otherwise the following side_of conditions may not be correct
                // even if all parameter points are defined
            {
                if (next_left_corner.side_of(this.current_from_point, door_right_corner) == Side.ON_THE_RIGHT)
                {
                    // bend to the right
                    new_door_ind = right_ind + 1;
                    result_corner = right_turn_next_corner(this.current_from_point, trace_halfwidth_max, door_right_corner, next_left_corner);
                    break;
                }
                
                if (next_right_corner.side_of(this.current_from_point, door_left_corner) == Side.ON_THE_LEFT)
                {
                    // bend to the left
                    new_door_ind = left_ind + 1;
                    result_corner = left_turn_next_corner(this.current_from_point, trace_halfwidth_max, door_left_corner, next_right_corner);
                    break;
                }
            }
            boolean visability_range_gets_smaller_on_the_right_side = (door_right_corner == null);
            if (door_right_corner != null && next_right_corner.side_of(this.current_from_point, door_right_corner) != Side.ON_THE_RIGHT)
            {
                FloatPoint curr_tangential_point = this.current_from_point.left_tangential_point(next_right_corner, trace_halfwidth_max);
                if (curr_tangential_point != null)
                {
                    FloatLine check_line = new FloatLine(this.current_from_point, curr_tangential_point);
                    if ( check_line.segment_distance(door_right_corner) >= trace_halfwidth_max)
                    {
                        visability_range_gets_smaller_on_the_right_side = true;
                    }
                }
            }
            if (visability_range_gets_smaller_on_the_right_side)
            {
                // The visibility range gets smaller on the right side.
                door_right_corner = next_right_corner;
                right_ind = curr_door_ind;
            }
            boolean visability_range_gets_smaller_on_the_left_side = (door_left_corner == null);
            if (door_left_corner != null && next_left_corner.side_of(this.current_from_point,  door_left_corner) != Side.ON_THE_LEFT)
            {
                FloatPoint curr_tangential_point = this.current_from_point.right_tangential_point(next_left_corner, trace_halfwidth_max);
                if (curr_tangential_point != null)
                {
                    FloatLine check_line = new FloatLine(this.current_from_point, curr_tangential_point);
                    if ( check_line.segment_distance(door_left_corner) >= trace_halfwidth_max)
                    {
                        visability_range_gets_smaller_on_the_left_side = true;
                    }
                }
            }
            if (visability_range_gets_smaller_on_the_left_side)
            {
                // The visibility range gets smaller on the left side.
                door_left_corner = next_left_corner;
                left_ind = curr_door_ind;
            }
            ++curr_door_ind;
        }
        
        if (end_of_trace)
        {
            FloatPoint nearest_point = this.current_target_shape.nearest_point(this.current_from_point.round()).to_float();
            result_corner = nearest_point;
            if (left_tangent_point != null && nearest_point.side_of(this.current_from_point, left_tangent_point) == Side.ON_THE_LEFT)
            {
                // The nearest target point is to the left of the visible range, add another corner
                new_door_ind = left_ind + 1;
                FloatPoint target_right_corner = this.current_target_shape.corner_approx(this.current_target_shape.index_of_right_most_corner(this.current_from_point));
                FloatPoint curr_corner = right_left_tangential_point(this.current_from_point,  target_right_corner, door_left_corner, trace_halfwidth_max);
                if (curr_corner != null)
                {
                    result_corner = curr_corner;
                    end_of_trace = false;
                }
            }
            else if (right_tangent_point != null && nearest_point.side_of(this.current_from_point, right_tangent_point) == Side.ON_THE_RIGHT)
            {
                // The nearest target point is to the right of the visible range, add another corner
                FloatPoint target_left_corner = this.current_target_shape.corner_approx(this.current_target_shape.index_of_left_most_corner(this.current_from_point));
                new_door_ind = right_ind + 1;
                FloatPoint curr_corner = left_right_tangential_point(this.current_from_point,  target_left_corner, door_right_corner, trace_halfwidth_max);
                if (curr_corner != null)
                {
                    result_corner = curr_corner;
                    end_of_trace = false;
                }
            }
        }
        if (end_of_trace)
        {
            new_door_ind = this.current_target_door_index;
        }
        
        // Check clearance violation with the previous door shapes
        // and correct them in this case.
        
        FloatLine check_line = new FloatLine(this.current_from_point, result_corner);
        int check_from_door_index = Math.max( this.current_to_door_index - 5 , this.current_from_door_index + 1);
        FloatPoint corrected_result = null;
        int corrected_door_ind = 0;
        for (int i = check_from_door_index; i < new_door_ind; ++i)
        {
            FloatPoint curr_left_corner = calc_door_left_corner(this.backtrack_array[i]);
            double curr_dist = check_line.segment_distance(curr_left_corner);
            if (Math.abs(curr_dist) < trace_halfwidth_middle)
            {
                FloatPoint curr_corrected_result = right_left_tangential_point(check_line.a, check_line.b, curr_left_corner,  trace_halfwidth_max);
                if (curr_corrected_result != null)
                {
                    if(corrected_result == null ||
                            curr_corrected_result.side_of(this.current_from_point, corrected_result) == Side.ON_THE_RIGHT)
                    {
                        corrected_door_ind = i;
                        corrected_result = curr_corrected_result;
                    }
                }
            }
            FloatPoint curr_right_corner = calc_door_right_corner(this.backtrack_array[i]);
            curr_dist = check_line.segment_distance(curr_right_corner);
            if (Math.abs(curr_dist) < trace_halfwidth_middle)
            {
                FloatPoint curr_corrected_result = left_right_tangential_point(check_line.a, check_line.b, curr_right_corner,  trace_halfwidth_max);
                if (curr_corrected_result != null)
                {
                    if(corrected_result == null ||
                            curr_corrected_result.side_of(this.current_from_point, corrected_result) == Side.ON_THE_LEFT)
                    {
                        corrected_door_ind = i;
                        corrected_result = curr_corrected_result;
                    }
                }
            }
        }
        if (corrected_result != null)
        {
            result_corner = corrected_result;
            new_door_ind = Math.max(corrected_door_ind, this.current_to_door_index);
        }
        
        this.current_to_door_index = new_door_ind;
        if(result_corner != null && result_corner != this.current_from_point)
        {
            result.add(result_corner);
        }
        return result;
    }
    
    
    /**
     * Calculates the left most corner of the shape of p_to_info.door
     * seen from the center of the common room with the previous door.
     */
    private static FloatPoint calc_door_left_corner(BacktrackElement p_to_info)
    {
        CompleteExpansionRoom from_room = p_to_info.door.other_room(p_to_info.next_room);
        FloatPoint pole = from_room.get_shape().centre_of_gravity();
        TileShape curr_to_door_shape = p_to_info.door.get_shape();
        int left_most_corner_no = curr_to_door_shape.index_of_left_most_corner(pole);
        return curr_to_door_shape.corner_approx(left_most_corner_no);
    }
    
    /**
     * Calculates the right most corner of the shape of p_to_info.door
     * seen from the center of the common room with the previous door.
     */
    private static FloatPoint calc_door_right_corner(BacktrackElement p_to_info)
    {
        CompleteExpansionRoom from_room = p_to_info.door.other_room(p_to_info.next_room);
        FloatPoint pole = from_room.get_shape().centre_of_gravity();
        TileShape curr_to_door_shape = p_to_info.door.get_shape();
        int right_most_corner_no = curr_to_door_shape.index_of_right_most_corner(pole);
        return curr_to_door_shape.corner_approx(right_most_corner_no);
    }
    
    /**
     * Calculates as first line the left side tangent from p_from_corner to
     * the circle with center p_to_corner and radius p_dist.
     * As second line the right side tangent from p_to_corner to the circle
     * with center p_next_corner and radius 2 * p_dist is constructed.
     * The second line is than translated by the distance p_dist to the left.
     * Returned is the intersection of the first and the second line.
     */
    private FloatPoint right_turn_next_corner(FloatPoint p_from_corner, double p_dist, FloatPoint p_to_corner, FloatPoint p_next_corner)
    {
        FloatPoint curr_tangential_point = p_from_corner.left_tangential_point(p_to_corner, p_dist);
        if (curr_tangential_point == null)
        {
            if (this.test_level.ordinal() >=  TestLevel.ALL_DEBUGGING_OUTPUT.ordinal())
            {
                System.out.println("LocateFoundConnectionAlgo.right_turn_next_corner: left tangential point is null");
            }
            return p_from_corner;
        }
        FloatLine first_line = new FloatLine(p_from_corner, curr_tangential_point);
        curr_tangential_point = p_to_corner.right_tangential_point(p_next_corner, 2 * p_dist + c_tolerance);
        if (curr_tangential_point == null)
        {
            if (this.test_level.ordinal() >=  TestLevel.ALL_DEBUGGING_OUTPUT.ordinal())
            {
                System.out.println("LocateFoundConnectionAlgo.right_turn_next_corner: right tangential point is null");
            }
            return p_from_corner;
        }
        FloatLine second_line = new FloatLine(p_to_corner, curr_tangential_point);
        second_line = second_line.translate(p_dist);
        return first_line.intersection(second_line);
    }
    
    /**
     * Calculates as first line the right side tangent from p_from_corner to
     * the circle with center p_to_corner and radius p_dist.
     * As second line the left side tangent from p_to_corner to the circle
     * with center p_next_corner and radius 2 * p_dist is constructed.
     * The second line is than translated by the distance p_dist to the right.
     * Returned is the intersection of the first and the second line.
     */
    private FloatPoint left_turn_next_corner(FloatPoint p_from_corner, double p_dist, FloatPoint p_to_corner, FloatPoint p_next_corner)
    {
        FloatPoint curr_tangential_point = p_from_corner.right_tangential_point(p_to_corner, p_dist);
        if (curr_tangential_point == null)
        {
            if (this.test_level.ordinal() >=  TestLevel.ALL_DEBUGGING_OUTPUT.ordinal())
            {
                System.out.println("LocateFoundConnectionAlgo.left_turn_next_corner: right tangential point is null");
            }
            return p_from_corner;
        }
        FloatLine first_line = new FloatLine( p_from_corner, curr_tangential_point);
        curr_tangential_point = p_to_corner.left_tangential_point(p_next_corner, 2 * p_dist + c_tolerance);
        if (curr_tangential_point == null)
        {
            if (this.test_level.ordinal() >=  TestLevel.ALL_DEBUGGING_OUTPUT.ordinal())
            {
                System.out.println("LocateFoundConnectionAlgo.left_turn_next_corner: left tangential point is null");
            }
            return p_from_corner;
        }
        FloatLine second_line = new FloatLine(p_to_corner, curr_tangential_point);
        second_line = second_line.translate(-p_dist);
        return first_line.intersection(second_line);
    }
    
    /**
     * Calculates the right tangential line from p_from_point and the
     * left tangential line from p_to_point to the circle
     * with center p_center and radius p_dist.
     * Returns the intersection of the 2 lines.
     */
    private FloatPoint right_left_tangential_point(FloatPoint p_from_point, FloatPoint p_to_point, FloatPoint p_center, double p_dist)
    {
        FloatPoint curr_tangential_point = p_from_point.right_tangential_point(p_center, p_dist);
        if (curr_tangential_point == null)
        {
            if (this.test_level.ordinal() >=  TestLevel.ALL_DEBUGGING_OUTPUT.ordinal())
            {
                System.out.println("LocateFoundConnectionAlgo. right_left_tangential_point: right tangential point is null");
            }
            return null;
        }
        FloatLine first_line = new FloatLine(p_from_point, curr_tangential_point);
        curr_tangential_point = p_to_point.left_tangential_point(p_center, p_dist);
        if (curr_tangential_point == null)
        {
            if (this.test_level.ordinal() >=  TestLevel.ALL_DEBUGGING_OUTPUT.ordinal())
            {
                System.out.println("LocateFoundConnectionAlgo. right_left_tangential_point: left tangential point is null");
            }
            return null;
        }
        FloatLine second_line = new FloatLine(p_to_point, curr_tangential_point);
        return first_line.intersection(second_line);
    }
    
    /**
     * Calculates the left tangential line from p_from_point and the
     * right tangential line from p_to_point to the circle
     * with center p_center and radius p_dist.
     * Returns the intersection of the 2 lines.
     */
    private FloatPoint left_right_tangential_point(FloatPoint p_from_point, FloatPoint p_to_point, FloatPoint p_center, double p_dist)
    {
        FloatPoint curr_tangential_point = p_from_point.left_tangential_point(p_center, p_dist);
        if (curr_tangential_point == null)
        {
            if (this.test_level.ordinal() >=  TestLevel.ALL_DEBUGGING_OUTPUT.ordinal())
            {
                System.out.println("LocateFoundConnectionAlgo. left_right_tangential_point: left tangential point is null");
            }
            return null;
        }
        FloatLine first_line = new FloatLine(p_from_point, curr_tangential_point);
        curr_tangential_point = p_to_point.right_tangential_point(p_center, p_dist);
        if (curr_tangential_point == null)
        {
            if (this.test_level.ordinal() >=  TestLevel.ALL_DEBUGGING_OUTPUT.ordinal())
            {
                System.out.println("LocateFoundConnectionAlgo. left_right_tangential_point: right tangential point is null");
            }
            return null;
        }
        FloatLine second_line = new FloatLine(p_to_point, curr_tangential_point);
        return first_line.intersection(second_line);
    }
    
    static private final double c_tolerance = 1.0;
    
}
