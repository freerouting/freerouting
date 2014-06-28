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

package board;

import datastructures.Stoppable;
import geometry.planar.Limits;
import datastructures.Signum;

import geometry.planar.Direction;
import geometry.planar.FloatPoint;
import geometry.planar.IntPoint;
import geometry.planar.Point;
import geometry.planar.Line;
import geometry.planar.Polyline;
import geometry.planar.Side;
import geometry.planar.TileShape;

/**
 *
 * Auxiliary class containing internal functions for pulling any angle traces tight.
 *
 *
 * @author Alfons Wirtz
 */

class PullTightAlgoAnyAngle extends PullTightAlgo
{
    
    PullTightAlgoAnyAngle(RoutingBoard p_board, int[] p_only_net_no_arr, Stoppable p_stoppable_thread, int p_time_limit,
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
            if (is_stop_requested())
            {
                break;
            }
            prev_result = new_result;
            Polyline tmp = skip_segments_of_length_0(prev_result);
            Polyline tmp0 = reduce_lines(tmp);
            Polyline tmp1 = skip_lines(tmp0);
            
            // I intended to replace reduce_corners by the previous 2
            // functions, because with consecutive corners closer than
            // 1 grid point reduce_corners may loop with smoothen_corners
            // because of changing directions heavily.
            // Unlike reduce_corners, the above 2 functions do not
            // introduce new directions
            
            Polyline tmp2 = reduce_corners(tmp1);
            Polyline tmp3 = reposition_lines(tmp2);
            new_result = smoothen_corners(tmp3);
        }
        return new_result;
    }
    
    
    // tries to reduce the corner count of p_polyline by replacing two consecutive
    // lines by a line through IntPoints near the previous corner and the next
    // corner, if that is possible without clearance violation.
    private Polyline reduce_corners(Polyline p_polyline)
    {
        if (p_polyline.arr.length < 4)
        {
            return p_polyline;
        }
        int last_index = p_polyline.arr.length - 4;
        
        Line []  new_lines = new Line [p_polyline.arr.length];
        new_lines[0] = p_polyline.arr[0];
        new_lines[1] = p_polyline.arr[1];
        
        int new_line_index = 1;
        
        boolean polyline_changed = false;
        
        Line [] curr_lines = new Line[3];
        
        for (int i = 0; i <= last_index; ++i)
        {
            boolean skip_line = false;
            FloatPoint new_a = new_lines[new_line_index - 1].intersection_approx(new_lines[new_line_index]);
            FloatPoint new_b = p_polyline.corner_approx(i + 2);
            boolean in_clip_shape = curr_clip_shape == null ||
                    curr_clip_shape.contains(new_a) && curr_clip_shape.contains(new_b)
                    && curr_clip_shape.contains(p_polyline.corner_approx(new_line_index));
            
            if (in_clip_shape)
            {
                FloatPoint skip_corner =
                        new_lines[new_line_index].intersection_approx(p_polyline.arr[i + 2]);
                curr_lines [1] = new Line(new_a.round(), new_b.round());
                boolean ok = true;
                if (new_line_index == 1)
                {
                    if (!(p_polyline.first_corner() instanceof IntPoint))
                    {
                        // first corner must not be changed
                        ok = false;
                    }
                    else
                    {
                        Direction dir = curr_lines[1].direction();
                        curr_lines[0] =
                                Line.get_instance(p_polyline.first_corner(), dir.turn_45_degree(2));
                    }
                }
                else
                {
                    curr_lines[0] = new_lines[new_line_index - 1];
                }
                if (i == last_index)
                {
                    if (!(p_polyline.last_corner() instanceof IntPoint))
                    {
                        // last corner must not be changed
                        ok = false;
                    }
                    else
                    {
                        Direction dir = curr_lines[1].direction();
                        curr_lines[2] = Line.get_instance(p_polyline.last_corner(), dir.turn_45_degree(2));
                    }
                }
                else
                {
                    curr_lines[2] = p_polyline.arr[i + 3];
                }
                
                
                // check, if the intersection of curr_lines[0] and curr_lines[1]
                // is near new_a and the intersection of curr_lines[0] and
                // curr_lines[1] and curr_lines[2] is near new_b.
                // There may be numerical stability proplems with
                // near parallel lines.
                
                final double check_dist = 100;
                if (ok)
                {
                    FloatPoint check_is =  curr_lines[0].intersection_approx(curr_lines[1]);
                    double dist = check_is.distance_square(new_a);
                    
                    if (dist > check_dist)
                    {
                        ok = false;
                    }
                }
                if (ok)
                {
                    FloatPoint check_is =  curr_lines[1].intersection_approx(curr_lines[2]);
                    double dist = check_is.distance_square(new_b);
                    if (dist > check_dist)
                    {
                        ok = false;
                    }
                }
                if (ok && i == 1 && !(p_polyline.first_corner() instanceof IntPoint))
                {
                    // There may be a connection to a trace.
                    // make shure that the second corner of the new polyline
                    // is on the same side of the trace as the third corner. (There may be splitting problems)
                    Point new_corner  = curr_lines[0].intersection(curr_lines[1]);
                    if (new_corner.side_of(new_lines[0]) != p_polyline.corner(1).side_of(new_lines[0]))
                    {
                        ok = false;
                    }
                }
                if (ok && i == last_index - 1 && !(p_polyline.last_corner() instanceof IntPoint))
                {
                    // There may be a connection to a trace.
                    // make shure that the second last corner of the new polyline
                    // is on the same side of the trace as the third last corner (There may be splitting problems)
                    Point new_corner  = curr_lines[1].intersection(curr_lines[2]);
                    if (new_corner.side_of(new_lines[0]) != 
                            p_polyline.corner(p_polyline.corner_count() - 2).side_of(new_lines[0]))
                    {
                        ok = false;
                    }
                }
                Polyline curr_polyline = null;
                if (ok)
                {
                    curr_polyline = new Polyline(curr_lines);
                    if ( curr_polyline.arr.length !=  3)
                    {
                        ok = false;
                    }
                    double length_before = skip_corner.distance(new_a) +
                            skip_corner.distance(new_b);
                    double length_after = curr_polyline.length_approx() + 1.5;
                    // 1.5 added because of possible inacurracy SQRT_2
                    // by twice rounding.
                    if (length_after  >= length_before)
                        // May happen from rounding to integer.
                        // Prevent infinite loop.
                    {
                        ok = false;
                    }
                }
                
                if (ok)
                {
                    TileShape shape_to_check = curr_polyline.offset_shape(curr_half_width, 0);
                    skip_line = board.check_trace_shape(shape_to_check,
                            curr_layer, curr_net_no_arr, curr_cl_type, this.contact_pins);
                }
            }
            if (skip_line)
            {
                polyline_changed = true;
                new_lines[new_line_index] = curr_lines[1];
                if (new_line_index == 1)
                {
                    // make the first line perpendicular to the current line
                    new_lines[0] = curr_lines [0];
                }
                if (i == last_index)
                {
                    // make the last line perpendicular to the current line
                    ++new_line_index;
                    new_lines[new_line_index] = curr_lines[2];
                }
                if (board.changed_area != null)
                {
                    board.changed_area.join(new_a, curr_layer);
                    board.changed_area.join(new_b, curr_layer);
                }
            }
            else
            {
                ++new_line_index;
                new_lines[new_line_index] = p_polyline.arr[i + 2];
                if (i == last_index)
                {
                    ++new_line_index;
                    new_lines[new_line_index] = p_polyline.arr[i + 3];
                }
            }
            if (new_lines[new_line_index].is_parallel(new_lines[new_line_index - 1]))
            {
                // skip line, if it is parallel to the previous one
                --new_line_index;
            }
        }
        if (!polyline_changed)
        {
            return p_polyline;
        }
        Line [] cleaned_new_lines = new Line [new_line_index + 1];
        System.arraycopy(new_lines, 0, cleaned_new_lines, 0, cleaned_new_lines.length);
        Polyline result = new Polyline(cleaned_new_lines);
        return result;
    }
    
    /**
     * tries to smoothen p_polyline by cutting of corners, if possible
     */
    private Polyline smoothen_corners(Polyline p_polyline)
    {
        if (p_polyline.arr.length < 4)
        {
            return p_polyline;
        }
        boolean polyline_changed = false;
        Line[] line_arr = new Line[p_polyline.arr.length];
        System.arraycopy(p_polyline.arr, 0, line_arr, 0, line_arr.length);
        
        for (int i = 0; i < line_arr.length - 3; ++i)
        {
            Line new_line = smoothen_corner(line_arr, i);
            if (new_line != null)
            {
                polyline_changed = true;
                // add the new line into the line array
                Line[] tmp_lines = new Line[line_arr.length + 1];
                System.arraycopy(line_arr, 0, tmp_lines, 0, i + 2);
                tmp_lines [i + 2] = new_line;
                System.arraycopy(line_arr, i + 2, tmp_lines, i + 3,
                        tmp_lines.length - (i + 3));
                line_arr = tmp_lines;
                ++i;
            }
        }
        if (!polyline_changed)
        {
            return p_polyline;
        }
        return new Polyline(line_arr);
    }
    
    /**
     * tries to shorten p_polyline by relocating its lines
     */
    Polyline reposition_lines(Polyline p_polyline)
    {
        if (p_polyline.arr.length < 5)
        {
            return p_polyline;
        }
        boolean polyline_changed = false;
        Line[] line_arr = new Line[p_polyline.arr.length];
        System.arraycopy(p_polyline.arr, 0, line_arr, 0, line_arr.length);
        for (int i = 0; i < line_arr.length - 4; ++i)
        {
            Line new_line = reposition_line(line_arr, i);
            if (new_line != null)
            {
                polyline_changed = true;
                line_arr [i + 2] = new_line;
                if (line_arr[i + 2].is_parallel(line_arr[i + 1]) ||
                        line_arr[i + 2].is_parallel(line_arr[i + 3]))
                {
                    // calculation of corners not possible before skipping
                    // parallel lines
                    break;
                }
            }
        }
        if (!polyline_changed)
        {
            return p_polyline;
        }
        return new Polyline(line_arr);
    }
    
    /**
     * tries to reduce te number of lines of p_polyline by moving
     * lines parallel beyond the intersection of the next or privious lines.
     */
    private Polyline reduce_lines(Polyline p_polyline)
    {
        if (p_polyline.arr.length < 6)
        {
            return p_polyline;
        }
        boolean polyline_changed = false;
        Line[] line_arr = p_polyline.arr;
        for (int i = 2; i < line_arr.length - 2; ++i)
        {
            FloatPoint prev_corner =
                    line_arr[i - 2].intersection_approx( line_arr[i - 1]);
            FloatPoint next_corner =
                    line_arr[i +1 ].intersection_approx( line_arr[i + 2]);
            boolean in_clip_shape = curr_clip_shape == null ||
                    curr_clip_shape.contains(prev_corner) &&
                    curr_clip_shape.contains(next_corner);
            if (!in_clip_shape)
            {
                continue;
            }
            Line translate_line = line_arr [i];
            double prev_dist = translate_line.signed_distance(prev_corner);
            double next_dist = translate_line.signed_distance(next_corner);
            if (Signum.of(prev_dist)!= Signum.of(next_dist))
                // the 2 corners are on different sides of the translate_line
            {
                continue;
            }
            double translate_dist;
            if (Math.abs(prev_dist) < Math.abs(next_dist))
            {
                
                translate_dist = prev_dist;
            }
            else
            {
                translate_dist = next_dist;
            }
            if (translate_dist == 0)
            {
                //line segment may have length 0
                continue;
            }
            Side line_side = translate_line.side_of(prev_corner);
            Line new_line = translate_line.translate(-translate_dist);
            // make shure, we have crossed the nearest_corner;
            int sign = Signum.as_int(translate_dist);
            Side new_line_side_of_prev_corner = new_line.side_of(prev_corner);
            Side new_line_side_of_next_corner = new_line.side_of(next_corner);
            while (new_line_side_of_prev_corner == line_side &&
                    new_line_side_of_next_corner == line_side)
            {
                translate_dist += sign * 0.5;
                new_line = translate_line.translate(-translate_dist);
                new_line_side_of_prev_corner = new_line.side_of(prev_corner);
                new_line_side_of_next_corner = new_line.side_of(next_corner);
            }
            int crossed_corners_before_count = 0;
            int crossed_corners_after_count = 0;
            if (new_line_side_of_prev_corner != line_side)
            {
                ++crossed_corners_before_count;
            }
            if (new_line_side_of_next_corner != line_side)
            {
                ++crossed_corners_after_count;
            }
            // check, that we havent crossed both corners
            if (crossed_corners_before_count > 1 || crossed_corners_after_count > 1)
            {
                continue;
            }
            // check, that next_nearest_corner and nearest_corner are on
            // different sides of new_line;
            if (crossed_corners_before_count > 0)
            {
                if (i < 3)
                {
                    continue;
                }
                FloatPoint prev_prev_corner =
                        line_arr[i - 3].intersection_approx( line_arr[i - 2]);
                if (new_line.side_of(prev_prev_corner) != line_side)
                {
                    continue;
                }
            }
            if (crossed_corners_after_count > 0)
            {
                if (i >= line_arr.length - 3)
                {
                    continue;
                }
                FloatPoint next_next_corner =
                        line_arr[i + 2 ].intersection_approx( line_arr[i + 3]);
                if (new_line.side_of(next_next_corner) != line_side)
                {
                    continue;
                }
            }
            Line [] curr_lines = new Line[line_arr.length -
                    crossed_corners_before_count - crossed_corners_after_count];
            int keep_before_ind = i - crossed_corners_before_count;
            System.arraycopy(line_arr, 0, curr_lines, 0, keep_before_ind);
            curr_lines [keep_before_ind] = new_line;
            System.arraycopy(line_arr, i + 1 + crossed_corners_after_count, curr_lines,
                    keep_before_ind + 1, curr_lines.length - ( keep_before_ind + 1));
            Polyline tmp = new Polyline( curr_lines);
            boolean check_ok = false;
            if (tmp.arr.length == curr_lines.length)
            {
                TileShape shape_to_check =
                        tmp.offset_shape(curr_half_width, keep_before_ind - 1);
                check_ok = board.check_trace_shape(shape_to_check,
                        curr_layer, curr_net_no_arr, curr_cl_type, this.contact_pins);
                
            }
            if (check_ok)
            {
                if (board.changed_area != null)
                {
                    board.changed_area.join(prev_corner, curr_layer);
                    board.changed_area.join(next_corner, curr_layer);
                }
                polyline_changed = true;
                line_arr = curr_lines;
                --i;
            }
        }
        if (!polyline_changed)
        {
            return p_polyline;
        }
        return new Polyline(line_arr);
    }
    
    
    private Line smoothen_corner(Line[] p_line_arr, int p_start_no)
    {
        if ( p_line_arr.length - p_start_no < 4)
        {
            return null;
        }
        FloatPoint curr_corner =
                p_line_arr[p_start_no + 1].intersection_approx(p_line_arr[p_start_no + 2]);
        if (curr_clip_shape != null &&
                !curr_clip_shape.contains(curr_corner))
        {
            return null;
        }
        double cosinus_angle =
                p_line_arr[p_start_no + 1].cos_angle(p_line_arr[p_start_no + 2]);
        if (cosinus_angle > c_max_cos_angle)
            // lines are already nearly parallel, don't divide angle any further
            // because of problems with numerical stability
        {
            return null;
        }
        FloatPoint prev_corner =
                p_line_arr[p_start_no].intersection_approx( p_line_arr[p_start_no + 1]);
        FloatPoint next_corner =
                p_line_arr[p_start_no + 2].intersection_approx(p_line_arr[p_start_no + 3]);
        
        // create a line approximately through curr_corner, whose
        // direction is about the middle of the directions of the
        // previous and the next line.
        // Translations of this line are used to cut off the corner.
        Direction prev_dir =  p_line_arr[p_start_no + 1].direction();
        Direction next_dir =  p_line_arr[p_start_no + 2].direction();
        Direction middle_dir = prev_dir.middle_approx(next_dir);
        Line translate_line = Line.get_instance(curr_corner.round(), middle_dir);
        double prev_dist = translate_line.signed_distance(prev_corner);
        double next_dist = translate_line.signed_distance(next_corner);
        FloatPoint nearest_point;
        double max_translate_dist;
        if (Math.abs(prev_dist) < Math.abs(next_dist))
        {
            nearest_point = prev_corner;
            max_translate_dist = prev_dist;
        }
        else
        {
            nearest_point = next_corner;
            max_translate_dist = next_dist;
        }
        if (Math.abs(max_translate_dist) < 1)
        {
            return null;
        }
        Line [] curr_lines = new Line[p_line_arr.length + 1];
        System.arraycopy(p_line_arr, 0, curr_lines, 0, p_start_no + 2);
        System.arraycopy(p_line_arr, p_start_no + 2, curr_lines,
                p_start_no + 3, curr_lines.length - p_start_no - 3);
        double translate_dist = max_translate_dist;
        double delta_dist = max_translate_dist;
        Side side_of_nearest_point = translate_line.side_of(nearest_point);
        int sign = Signum.as_int(max_translate_dist);
        Line result = null;
        while (Math.abs(delta_dist) > this.min_translate_dist)
        {
            boolean check_ok = false;
            Line new_line = translate_line.translate(-translate_dist);
            Side new_line_side_of_nearest_point = new_line.side_of(nearest_point);
            if (new_line_side_of_nearest_point == side_of_nearest_point
                    || new_line_side_of_nearest_point == Side.COLLINEAR)
            {
                curr_lines [p_start_no + 2] = new_line;
                Polyline tmp = new Polyline( curr_lines);
                
                if (tmp.arr.length == curr_lines.length)
                {
                    TileShape shape_to_check =
                            tmp.offset_shape(curr_half_width, p_start_no + 1);
                    check_ok = board.check_trace_shape(shape_to_check,
                            curr_layer, curr_net_no_arr, curr_cl_type, this.contact_pins);
                }
                delta_dist /= 2;
                if (check_ok)
                {
                    result = curr_lines[p_start_no + 2];
                    if (translate_dist == max_translate_dist)
                    {
                        // biggest possible change
                        break;
                    }
                    translate_dist += delta_dist;
                }
                else
                {
                    translate_dist -= delta_dist;
                }
            }
            else
                // moved a little bit to far at the first time
                // because of numerical inaccuracy
            {
                double shorten_value = sign * 0.5;
                max_translate_dist -= shorten_value;
                translate_dist -= shorten_value;
                delta_dist -= shorten_value;
            }
        }
        if (result == null)
        {
            return null;
        }
        
        if (board.changed_area != null)
        {
            FloatPoint new_prev_corner =
                    curr_lines[p_start_no].intersection_approx( curr_lines[p_start_no + 1]);
            FloatPoint new_next_corner =
                    curr_lines[p_start_no + 3].intersection_approx( curr_lines[p_start_no + 4]);
            board.changed_area.join(new_prev_corner, curr_layer);
            board.changed_area.join(new_next_corner, curr_layer);
        }
        return result;
    }
    
    protected Line reposition_line(Line[] p_line_arr, int p_start_no)
    {
        if ( p_line_arr.length - p_start_no < 5)
        {
            return null;
        }
        if (curr_clip_shape != null)
            // check, that the corners of the line to translate are inside
            // the clip shape
        {
            for (int i = 1; i < 3; ++i)
            {
                FloatPoint curr_corner =
                        p_line_arr[p_start_no + i].intersection_approx(p_line_arr[p_start_no + i + 1]);
                if(!curr_clip_shape.contains(curr_corner))
                {
                    return null;
                }
            }
        }
        Line translate_line = p_line_arr[p_start_no + 2];
        FloatPoint prev_corner =
                p_line_arr[p_start_no].intersection_approx( p_line_arr[p_start_no + 1]);
        FloatPoint next_corner =
                p_line_arr[p_start_no + 3].intersection_approx(p_line_arr[p_start_no + 4]);
        double prev_dist = translate_line.signed_distance(prev_corner);
        int corners_skipped_before = 0;
        int corners_skipped_after = 0;
        final double c_epsilon = 0.001;
        while (Math.abs(prev_dist) < c_epsilon)
            // move also all lines trough the start corner of the line to translate
        {
            ++corners_skipped_before;
            int curr_no = p_start_no - corners_skipped_before;
            if (curr_no < 0)
                // the first corner is on the line to translate
            {
                return null;
            }
            prev_corner = p_line_arr[curr_no].intersection_approx( p_line_arr[curr_no + 1]);
            prev_dist = translate_line.signed_distance(prev_corner);
        }
        double next_dist = translate_line.signed_distance(next_corner);
        while (Math.abs(next_dist) < c_epsilon)
            // move also all lines trough the end corner of the line to translate
        {
            ++corners_skipped_after;
            int curr_no = p_start_no + 3 + corners_skipped_after;
            if (curr_no >= p_line_arr.length - 2)
                // the last corner is on the line to translate
            {
                return null;
            }
            next_corner = p_line_arr[curr_no].intersection_approx( p_line_arr[curr_no + 1]);
            next_dist = translate_line.signed_distance(next_corner);
        }
        if (Signum.of(prev_dist)!= Signum.of(next_dist))
            // the 2 corners are at different sides of translate_line
        {
            return null;
        }
        FloatPoint nearest_point;
        double max_translate_dist;
        if (Math.abs(prev_dist) < Math.abs(next_dist))
        {
            nearest_point = prev_corner;
            max_translate_dist = prev_dist;
        }
        else
        {
            nearest_point = next_corner;
            max_translate_dist = next_dist;
        }
        Line [] curr_lines = new Line[p_line_arr.length];
        System.arraycopy(p_line_arr, 0, curr_lines, 0, p_start_no + 2);
        System.arraycopy(p_line_arr, p_start_no + 3,
                curr_lines, p_start_no + 3, curr_lines.length - p_start_no - 3);
        double translate_dist = max_translate_dist;
        double delta_dist = max_translate_dist;
        Side side_of_nearest_point = translate_line.side_of(nearest_point);
        int sign = Signum.as_int(max_translate_dist);
        Line result = null;
        boolean first_time = true;
        while (first_time || Math.abs(delta_dist) > this.min_translate_dist)
        {
            boolean check_ok = false;
            Line new_line = translate_line.translate(-translate_dist);
            if (first_time && Math.abs(translate_dist) < 1)
            {
                if (new_line.equals(translate_line))
                {
                    // try the parallel line through the nearest_point
                    IntPoint rounded_nearest_point = nearest_point.round();
                    if (nearest_point.distance(rounded_nearest_point.to_float())
                    < Math.abs(translate_dist))
                    {
                        new_line = Line.get_instance(rounded_nearest_point,
                                translate_line.direction());
                    }
                    first_time = false;
                }
                if (new_line.equals(translate_line))
                {
                    return null;
                }
            }
            Side new_line_side_of_nearest_point = new_line.side_of(nearest_point);
            if (new_line_side_of_nearest_point == side_of_nearest_point
                    || new_line_side_of_nearest_point == Side.COLLINEAR)
            {
                first_time = false;
                curr_lines [p_start_no + 2] = new_line;
                // corners_skipped_before > 0 or corners_skipped_after > 0
                // happens very rarely. But this handling seems to be
                // important because there are situations which no other
                // tightening function can solve. For example when 3 ore more
                // consecutive corners are equal.
                Line prev_translated_line = new_line;
                for (int i = 0; i < corners_skipped_before; ++i)
                    // Translate the previous lines onto or past the
                    // intersection of new_line with the first untranslated line.
                {
                    int prev_line_no = p_start_no + 1 - corners_skipped_before;
                    FloatPoint curr_prev_corner =
                            prev_translated_line.intersection_approx(curr_lines[prev_line_no]);
                    Line curr_translate_line = p_line_arr [p_start_no + 1 - i];
                    double curr_translate_dist = curr_translate_line.signed_distance(curr_prev_corner);
                    prev_translated_line = curr_translate_line.translate(-curr_translate_dist);
                    curr_lines[p_start_no + 1 - i] = prev_translated_line;
                }
                prev_translated_line = new_line;
                for (int i = 0; i < corners_skipped_after; ++i)
                    // Translate the next lines onto or past the
                    // intersection of new_line with the first untranslated line.
                {
                    int next_line_no = p_start_no + 3 + corners_skipped_after;
                    FloatPoint curr_next_corner =
                            prev_translated_line.intersection_approx(curr_lines[next_line_no]);
                    Line curr_translate_line = p_line_arr [p_start_no + 3 + i];
                    double curr_translate_dist = curr_translate_line.signed_distance(curr_next_corner);
                    prev_translated_line = curr_translate_line.translate(-curr_translate_dist);
                    curr_lines[p_start_no + 3 + i] = prev_translated_line;
                }
                Polyline tmp = new Polyline( curr_lines);
                
                if (tmp.arr.length == curr_lines.length)
                {
                    TileShape shape_to_check =
                            tmp.offset_shape(curr_half_width, p_start_no + 1);
                    check_ok = board.check_trace_shape(shape_to_check,
                            curr_layer, curr_net_no_arr, curr_cl_type, this.contact_pins);
                    
                }
                delta_dist /= 2;
                if (check_ok)
                {
                    result = curr_lines[p_start_no + 2];
                    if (translate_dist == max_translate_dist)
                    {
                        // biggest possible change
                        break;
                    }
                    translate_dist += delta_dist;
                }
                else
                {
                    translate_dist -= delta_dist;
                }
            }
            else
                // moved a little bit to far at the first time
                // because of numerical inaccuracy
            {
                double shorten_value = sign * 0.5;
                max_translate_dist -= shorten_value;
                translate_dist -= shorten_value;
                delta_dist -= shorten_value;
            }
        }
        if (result == null)
        {
            return null;
        }
        
        if (board.changed_area != null)
        {
            FloatPoint new_prev_corner =
                    curr_lines[p_start_no].intersection_approx( curr_lines[p_start_no + 1]);
            FloatPoint new_next_corner =
                    curr_lines[p_start_no + 3].intersection_approx( curr_lines[p_start_no + 4]);
            board.changed_area.join(new_prev_corner, curr_layer);
            board.changed_area.join(new_next_corner, curr_layer);
        }
        return result;
    }
    
    
    private Polyline skip_lines(Polyline p_polyline)
    {
        for (int i = 1; i < p_polyline.arr.length - 3; ++i)
        {
            for (int j = 0; j <= 1; ++j)
            {
                FloatPoint corner1;
                FloatPoint corner2;
                Line curr_line;
                if (j == 0) // try to skip the line before the i+2-th line
                {
                    curr_line = p_polyline.arr[i + 2];
                    corner1 = p_polyline.corner_approx(i);
                    corner2 = p_polyline.corner_approx(i - 1);
                }
                else // try to skip the line after i-th line
                {
                    curr_line = p_polyline.arr[i];
                    corner1 = p_polyline.corner_approx(i + 1);
                    corner2 = p_polyline.corner_approx(i + 2);
                }
                boolean in_clip_shape = curr_clip_shape == null ||
                        curr_clip_shape.contains(corner1) &&
                        curr_clip_shape.contains(corner2);
                if (!in_clip_shape)
                {
                    continue;
                }
                
                Side side1 = curr_line.side_of(corner1);
                Side side2 = curr_line.side_of(corner2);
                if (side1 != side2)
                    // the two corners are on different sides of the line
                {
                    Polyline reduced_polyline = p_polyline.skip_lines(i + 1, i + 1);
                    if (reduced_polyline.arr.length == p_polyline.arr.length - 1)
                    {
                        int shape_no = i - 1;
                        if (j == 0)
                        {
                            ++shape_no;
                        }
                        TileShape shape_to_check =
                                reduced_polyline.offset_shape(curr_half_width, shape_no);
                        if (board.check_trace_shape(shape_to_check,
                                curr_layer, curr_net_no_arr, curr_cl_type, this.contact_pins))
                        {
                            if (board.changed_area != null)
                            {
                                board.changed_area.join(corner1, curr_layer);
                                board.changed_area.join(corner2, curr_layer);
                            }
                            return reduced_polyline;
                        }
                    }
                }
                // now try skipping 2 lines
                if (i >= p_polyline.arr.length - 4)
                {
                    break;
                }
                FloatPoint corner3;
                if (j == 1)
                {
                    corner3 = p_polyline.corner_approx(i + 3);
                }
                else
                {
                    corner3 = p_polyline.corner_approx(i + 1);
                }
                if (curr_clip_shape != null && !curr_clip_shape.contains(corner3))
                {
                    continue;
                }
                if (j == 0)
                    // curr_line is 1 line later than in the case skipping 1 line
                    // when coming from behind
                {
                    curr_line = p_polyline.arr[i + 3];
                    side1 = curr_line.side_of(corner1);
                    side2 = curr_line.side_of(corner2);
                }
                else
                {
                    side1 = curr_line.side_of(corner3);
                }
                if (side1 != side2)
                    // the two corners are on different sides of the line
                {
                    Polyline reduced_polyline = p_polyline.skip_lines(i + 1, i + 2);
                    if (reduced_polyline.arr.length == p_polyline.arr.length - 2)
                    {
                        int shape_no = i - 1;
                        if (j == 0)
                        {
                            ++shape_no;
                        }
                        TileShape shape_to_check =
                                reduced_polyline.offset_shape(curr_half_width, shape_no);
                        if (board.check_trace_shape(shape_to_check,
                                curr_layer, curr_net_no_arr, curr_cl_type, this.contact_pins))
                        {
                            if (board.changed_area != null)
                            {
                                board.changed_area.join(corner1, curr_layer);
                                board.changed_area.join(corner2, curr_layer);
                                board.changed_area.join(corner3, curr_layer);
                            }
                            return reduced_polyline;
                        }
                    }
                }
                
            }
        }
        return p_polyline;
    }
    
    Polyline smoothen_start_corner_at_trace(PolylineTrace p_trace)
    {
        boolean acute_angle = false;
        boolean bend = false;
        FloatPoint other_trace_corner_approx = null;
        Line other_trace_line = null;
        Line other_prev_trace_line = null;
        Polyline trace_polyline = p_trace.polyline();
        Point curr_end_corner = trace_polyline.corner(0);
        
        if (this.curr_clip_shape != null && this.curr_clip_shape.is_outside(curr_end_corner))
        {
            return null;
        }
        
        Point curr_prev_end_corner = trace_polyline.corner(1);
        boolean skip_short_segment = !(curr_end_corner instanceof IntPoint) &&
                curr_end_corner.to_float().distance_square(curr_prev_end_corner.to_float()) < SKIP_LENGTH;
        int start_line_no = 1;
        if (skip_short_segment)
        {
            if (trace_polyline.corner_count() < 3)
            {
                return null;
            }
            curr_prev_end_corner = trace_polyline.corner(2);
            ++start_line_no;
        }
        Side prev_corner_side = null;
        Direction line_direction = trace_polyline.arr[start_line_no].direction();
        Direction prev_line_direction = trace_polyline.arr[start_line_no + 1].direction();
        
        java.util.Collection<Item> contact_list = p_trace.get_start_contacts();
        for (Item curr_contact : contact_list)
        {
            if (curr_contact instanceof PolylineTrace && !curr_contact.is_shove_fixed())
            {
                Polyline contact_trace_polyline = ((PolylineTrace) curr_contact).polyline();
                FloatPoint curr_other_trace_corner_approx;
                Line curr_other_trace_line;
                Line curr_other_prev_trace_line;
                if (contact_trace_polyline.first_corner().equals(curr_end_corner))
                {
                    curr_other_trace_corner_approx = contact_trace_polyline.corner_approx(1);
                    curr_other_trace_line = contact_trace_polyline.arr[1];
                    curr_other_prev_trace_line = contact_trace_polyline.arr[2];
                }
                else
                {
                    int curr_corner_no = contact_trace_polyline.corner_count() - 2;
                    curr_other_trace_corner_approx = contact_trace_polyline.corner_approx(curr_corner_no);
                    curr_other_trace_line = contact_trace_polyline.arr[curr_corner_no + 1].opposite();
                    curr_other_prev_trace_line = contact_trace_polyline.arr[curr_corner_no];
                }
                Side curr_prev_corner_side = curr_prev_end_corner.side_of(curr_other_trace_line);
                Signum curr_projection = line_direction.projection(curr_other_trace_line.direction());
                boolean other_trace_found = false;
                if (curr_projection == Signum.POSITIVE && curr_prev_corner_side != Side.COLLINEAR)
                {
                    acute_angle = true;
                    other_trace_found = true;
                    
                }
                else if (curr_projection == Signum.ZERO && trace_polyline.corner_count() > 2)
                {
                    if (prev_line_direction.projection(curr_other_trace_line.direction()) == Signum.POSITIVE)
                    {
                        bend = true;
                        other_trace_found = true;
                    }
                }
                if (other_trace_found)
                {
                    other_trace_corner_approx = curr_other_trace_corner_approx;
                    other_trace_line = curr_other_trace_line;
                    prev_corner_side = curr_prev_corner_side;
                    other_prev_trace_line = curr_other_prev_trace_line;
                }
            }
            else
            {
                return null;
            }
        }
        int new_line_count = trace_polyline.arr.length + 1;
        int diff = 1;
        if (skip_short_segment)
        {
            --new_line_count;
            --diff;
        }
        if (acute_angle)
        {
            Direction new_line_dir;
            if (prev_corner_side == Side.ON_THE_LEFT)
            {
                new_line_dir = other_trace_line.direction().turn_45_degree(2);
            }
            else
            {
                new_line_dir = other_trace_line.direction().turn_45_degree(6);
            }
            Line translate_line = Line.get_instance(curr_end_corner.to_float().round(), new_line_dir);
            double translate_dist = (Limits.sqrt2 - 1) * this.curr_half_width;
            double prev_corner_dist = Math.abs(translate_line.signed_distance(curr_prev_end_corner.to_float()));
            double other_dist = Math.abs(translate_line.signed_distance(other_trace_corner_approx));
            translate_dist = Math.min(translate_dist, prev_corner_dist);
            translate_dist = Math.min(translate_dist, other_dist);
            if (translate_dist >= 0.99)
            {
                
                translate_dist = Math.max(translate_dist - 1, 1);
                if (translate_line.side_of(curr_prev_end_corner) == Side.ON_THE_LEFT)
                {
                    translate_dist = -translate_dist;
                }
                Line add_line = translate_line.translate(translate_dist);
                // constract the new trace polyline.
                Line[] new_lines = new Line[new_line_count];
                new_lines[0] = other_trace_line;
                new_lines[1] = add_line;
                for (int i = 2; i < new_lines.length; ++i)
                {
                    new_lines[i] = trace_polyline.arr[i - diff];
                }
                return new Polyline(new_lines);
            }
        }
        else if (bend)
        {
            Line[] check_line_arr = new Line[new_line_count];
            check_line_arr [0] = other_prev_trace_line;
            check_line_arr [1] = other_trace_line;
            for (int i = 2; i < check_line_arr.length; ++i)
            {
                check_line_arr [i] = trace_polyline.arr[i - diff];
            }
            Line new_line = reposition_line(check_line_arr, 0);
            if (new_line != null)
            {
                Line [] new_lines = new Line[trace_polyline.arr.length];
                new_lines[0] = other_trace_line;
                new_lines[1] = new_line;
                for (int i = 2; i < new_lines.length; ++i)
                {
                    new_lines [i] = trace_polyline.arr[i];
                }
                return new Polyline(new_lines);
            }
        }
        return null;
    }
    
    Polyline smoothen_end_corner_at_trace(PolylineTrace p_trace)
    {
        boolean acute_angle = false;
        boolean bend = false;
        FloatPoint other_trace_corner_approx = null;
        Line other_trace_line = null;
        Line other_prev_trace_line = null;
        Polyline trace_polyline = p_trace.polyline();
        Point curr_end_corner = trace_polyline.last_corner();
        
        if (this.curr_clip_shape != null && this.curr_clip_shape.is_outside(curr_end_corner))
        {
            return null;
        }
        
        Point curr_prev_end_corner = trace_polyline.corner(trace_polyline.corner_count() - 2);
        boolean skip_short_segment = !(curr_end_corner instanceof IntPoint) &&
                curr_end_corner.to_float().distance_square(curr_prev_end_corner.to_float()) < SKIP_LENGTH;
        int end_line_no = trace_polyline.arr.length - 2;
        if (skip_short_segment)
        {
            if (trace_polyline.corner_count() < 3)
            {
                return null;
            }
            curr_prev_end_corner = trace_polyline.corner(trace_polyline.corner_count() - 3);
            --end_line_no;
        }
        Side prev_corner_side = null;
        Direction line_direction = trace_polyline.arr[end_line_no].direction().opposite();
        Direction prev_line_direction = trace_polyline.arr[end_line_no].direction().opposite();
        
        java.util.Collection<Item> contact_list = p_trace.get_end_contacts();
        for (Item curr_contact : contact_list)
        {
            if (curr_contact instanceof PolylineTrace && !curr_contact.is_shove_fixed())
            {
                Polyline contact_trace_polyline = ((PolylineTrace) curr_contact).polyline();
                if (contact_trace_polyline.corner_count() > 2)
                {
                    FloatPoint curr_other_trace_corner_approx;
                    Line curr_other_trace_line;
                    Line curr_other_prev_trace_line;
                    if (contact_trace_polyline.first_corner().equals(curr_end_corner))
                    {
                        curr_other_trace_corner_approx = contact_trace_polyline.corner_approx(1);
                        curr_other_trace_line = contact_trace_polyline.arr[1];
                        curr_other_prev_trace_line = contact_trace_polyline.arr[2];
                    }
                    else
                    {
                        int curr_corner_no = contact_trace_polyline.corner_count() - 2;
                        curr_other_trace_corner_approx = contact_trace_polyline.corner_approx(curr_corner_no);
                        curr_other_trace_line = contact_trace_polyline.arr[curr_corner_no + 1].opposite();
                        curr_other_prev_trace_line = contact_trace_polyline.arr[curr_corner_no];
                    }
                    Side curr_prev_corner_side = curr_prev_end_corner.side_of(curr_other_trace_line);
                    Signum curr_projection = line_direction.projection(curr_other_trace_line.direction());
                    boolean other_trace_found = false;
                    if (curr_projection == Signum.POSITIVE && curr_prev_corner_side != Side.COLLINEAR)
                    {
                        acute_angle = true;
                        other_trace_found = true;
                    }
                    else if (curr_projection == Signum.ZERO && trace_polyline.corner_count() > 2)
                    {
                        if (prev_line_direction.projection(curr_other_trace_line.direction()) == Signum.POSITIVE)
                        {
                            bend = true;
                            other_trace_found = true;
                        }
                    }
                    if (other_trace_found)
                    {
                        other_trace_corner_approx = curr_other_trace_corner_approx;
                        other_trace_line = curr_other_trace_line;
                        prev_corner_side = curr_prev_corner_side;
                        other_prev_trace_line = curr_other_prev_trace_line;
                    }
                }
            }
            else
            {
                return null;
            }
        }
        
        int new_line_count = trace_polyline.arr.length + 1;
        int diff = 0;
        if (skip_short_segment)
        {
            --new_line_count;
            ++diff;
        }
        
        if (acute_angle)
        {
            Direction new_line_dir;
            if (prev_corner_side == Side.ON_THE_LEFT)
            {
                new_line_dir = other_trace_line.direction().turn_45_degree(6);
            }
            else
            {
                new_line_dir = other_trace_line.direction().turn_45_degree(2);
            }
            Line translate_line = Line.get_instance(curr_end_corner.to_float().round(), new_line_dir);
            double translate_dist = (Limits.sqrt2 - 1) * this.curr_half_width;
            double prev_corner_dist = Math.abs(translate_line.signed_distance(curr_prev_end_corner.to_float()));
            double other_dist = Math.abs(translate_line.signed_distance(other_trace_corner_approx));
            translate_dist = Math.min(translate_dist, prev_corner_dist);
            translate_dist = Math.min(translate_dist, other_dist);
            if (translate_dist >= 0.99)
            {
                
                translate_dist = Math.max(translate_dist - 1, 1);
                if (translate_line.side_of(curr_prev_end_corner) == Side.ON_THE_LEFT)
                {
                    translate_dist = -translate_dist;
                }
                Line add_line = translate_line.translate(translate_dist);
                // constract the new trace polyline.
                Line[] new_lines = new Line[new_line_count];
                for (int i = 0; i < trace_polyline.arr.length - 1; ++i)
                {
                    new_lines[i] = trace_polyline.arr[i];
                }
                new_lines[new_lines.length - 2] = add_line;
                new_lines[new_lines.length - 1] = other_trace_line;
                return new Polyline(new_lines);
            }
        }
        else if (bend)
        {
            Line[] check_line_arr = new Line[new_line_count];
            for (int i = 0; i < check_line_arr.length - 2; ++i)
            {
                check_line_arr[i] = trace_polyline.arr[i + diff];
            }
            check_line_arr[check_line_arr.length - 2] = other_trace_line;
            check_line_arr[check_line_arr.length - 1] = other_prev_trace_line;
            Line new_line = reposition_line(check_line_arr, check_line_arr.length - 5);
            if (new_line != null)
            {
                Line [] new_lines = new Line[trace_polyline.arr.length];
                for (int i = 0; i < new_lines.length - 2; ++i)
                {
                    new_lines[i] = trace_polyline.arr[i];
                }
                new_lines[new_lines.length - 2] = new_line;
                new_lines[new_lines.length - 1] = other_trace_line;
                return new Polyline(new_lines);
            }
        }
        return null;
    }
    
    
    private static double SKIP_LENGTH = 10.0;
}