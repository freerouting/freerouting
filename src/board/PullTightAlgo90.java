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
 * PullTight90.java
 *
 * Created on 19. Juli 2003, 18:54
 */

package board;

import datastructures.Stoppable;

import geometry.planar.FloatPoint;
import geometry.planar.Point;
import geometry.planar.Line;
import geometry.planar.Polyline;
import geometry.planar.TileShape;

/**
 *
 * @author  Alfons Wirtz
 */
class PullTightAlgo90 extends PullTightAlgo
{
    
    /** Creates a new instance of PullTight90 */
    public PullTightAlgo90(RoutingBoard p_board, int[] p_only_net_no_arr, Stoppable p_stoppable_thread, int p_time_limit,
            Point p_keep_point, int p_keep_point_layer)
    {
        super(p_board, p_only_net_no_arr, p_stoppable_thread, p_time_limit, p_keep_point, p_keep_point_layer);
    }
    
    Polyline pull_tight(Polyline p_polyline)
    {
        Polyline new_result = avoid_acid_traps(p_polyline);
        Polyline prev_result = null;
        while (new_result != prev_result)
        {
            if (this.is_stop_requested())
            {
                break;
            }
            prev_result = new_result;
            Polyline tmp1 = try_skip_second_corner(prev_result);
            Polyline tmp2 = try_skip_corners(tmp1);
            new_result = reposition_lines(tmp2);
        }
        return new_result;
    }
    
    /**
     * Tries to skip the second corner of p_polyline.
     * Return p_polyline, if nothing was changed.
     */
    private Polyline try_skip_second_corner(Polyline p_polyline)
    {
        if (p_polyline.arr.length < 5)
        {
            return p_polyline;
        }
        Line[] check_lines = new Line[4];
        check_lines[0] = p_polyline.arr[1];
        check_lines[1] = p_polyline.arr[0];
        check_lines[2] = p_polyline.arr[3];
        check_lines[3] = p_polyline.arr[4];
        Polyline check_polyline = new Polyline(check_lines);
        if (check_polyline.arr.length != 4 ||
                curr_clip_shape != null &&
                !curr_clip_shape.contains(check_polyline.corner_approx(1)))
        {
            return p_polyline;
        }
        for (int i = 0; i < 2; ++i)
        {
            TileShape shape_to_check = check_polyline.offset_shape(curr_half_width, i);
            if (!board.check_trace_shape(shape_to_check, curr_layer, curr_net_no_arr,
                    curr_cl_type, this.contact_pins))
            {
                return p_polyline;
            }
        }
        // now the second corner can be skipped.
        Line []  new_lines = new Line [p_polyline.arr.length - 1];
        new_lines[0] = p_polyline.arr[1];
        new_lines[1] = p_polyline.arr[0];
        for (int i = 2; i < new_lines.length; ++i)
        {
            new_lines[i] = p_polyline.arr[i + 1];
        }
         return new Polyline(new_lines);
    }
    
    /**
     * Tries to reduce the amount of corners of p_polyline.
     * Return p_polyline, if nothing was changed.
     */
    private Polyline try_skip_corners(Polyline p_polyline)
    {
        Line []  new_lines = new Line [p_polyline.arr.length];
        new_lines[0] = p_polyline.arr[0];
        new_lines[1] = p_polyline.arr[1];
        int new_line_index = 1;
        boolean polyline_changed = false;
        Line[] check_lines = new Line[4];
        boolean second_last_corner_skipped = false;
        for (int i = 5;  i <= p_polyline.arr.length; ++i)
        {
            boolean skip_lines = false;
            boolean in_clip_shape = curr_clip_shape == null ||
                    curr_clip_shape.contains(p_polyline.corner_approx(i - 3));
            if (in_clip_shape)
            {
                check_lines[0] = new_lines[new_line_index - 1];
                check_lines[1] = new_lines[new_line_index];
                check_lines[2] = p_polyline.arr[i - 1];
                if (i < p_polyline.arr.length)
                {
                    check_lines[3] = p_polyline.arr[i];
                }
                else
                {
                    // use as concluding line the second last line
                    check_lines[3] = p_polyline.arr[i - 2];
                }
                Polyline check_polyline = new Polyline(check_lines);
                skip_lines = check_polyline.arr.length == 4 &&
                        (curr_clip_shape == null ||
                        curr_clip_shape.contains(check_polyline.corner_approx(1)));
                if (skip_lines)
                {
                    TileShape shape_to_check = check_polyline.offset_shape(curr_half_width, 0);
                    skip_lines = board.check_trace_shape(shape_to_check, curr_layer, curr_net_no_arr,
                            curr_cl_type, this.contact_pins);
                }
                if (skip_lines)
                {
                    TileShape shape_to_check = check_polyline.offset_shape(curr_half_width, 1);
                    skip_lines = board.check_trace_shape(shape_to_check, curr_layer, curr_net_no_arr,
                            curr_cl_type, this.contact_pins);
                }
            }
            if (skip_lines)
            {
                if (i == p_polyline.arr.length)
                {
                    second_last_corner_skipped = true;
                }
                if (board.changed_area != null)
                {
                    FloatPoint new_corner =  check_lines[1].intersection_approx(check_lines[2]);
                    board.changed_area.join(new_corner, curr_layer);
                    FloatPoint skipped_corner =
                            p_polyline.arr[i - 2].intersection_approx(p_polyline.arr[i - 3]);
                    board.changed_area.join(skipped_corner, curr_layer);
                }
                polyline_changed = true;
                ++i;
            }
            else
            {
                ++new_line_index;
                new_lines [new_line_index ] = p_polyline.arr[i - 3];
            }
        }
        if (!polyline_changed)
        {
            return p_polyline;
        }
        if (second_last_corner_skipped)
        {
            // The second last corner of p_polyline was skipped
            ++new_line_index;
            new_lines [new_line_index] = p_polyline.arr[ p_polyline.arr.length -1];
            ++new_line_index;
            new_lines [new_line_index] = p_polyline.arr[ p_polyline.arr.length -2];
        }
        else
        {
            for (int i = 3; i > 0; --i)
            {
                ++new_line_index;
                new_lines [new_line_index ] = p_polyline.arr[ p_polyline.arr.length - i];
            }
        }
        
        Line [] cleaned_new_lines = new Line [new_line_index + 1];
        System.arraycopy(new_lines, 0, cleaned_new_lines, 0, cleaned_new_lines.length);
        Polyline result = new Polyline(cleaned_new_lines);
        return result;
    }
    
    Polyline smoothen_start_corner_at_trace(PolylineTrace p_trace)
    {
        return null;
    }
    
    Polyline smoothen_end_corner_at_trace(PolylineTrace p_trace)
    {
        return null;
    }
}
