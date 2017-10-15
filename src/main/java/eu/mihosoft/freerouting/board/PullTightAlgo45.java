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
 * Created on 19. Juli 2003, 18:59
 */
package board;

import datastructures.Stoppable;

import geometry.planar.Direction;
import geometry.planar.FloatPoint;
import geometry.planar.IntPoint;
import geometry.planar.Limits;
import geometry.planar.Line;
import geometry.planar.Point;
import geometry.planar.Polyline;
import geometry.planar.Side;
import geometry.planar.TileShape;
import geometry.planar.Vector;
import datastructures.Signum;

/**
 *
 * @author  Alfons Wirtz
 */
class PullTightAlgo45 extends PullTightAlgo
{

    /** Creates a new instance of PullTight90 */
    public PullTightAlgo45(RoutingBoard p_board, int[] p_only_net_no_arr, Stoppable p_stoppable_thread, int p_time_limit,
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
            Polyline tmp1 = reduce_corners(prev_result);
            Polyline tmp2 = smoothen_corners(tmp1);
            new_result = reposition_lines(tmp2);
        }
        return new_result;
    }

    AngleRestriction get_angle_restriction()
    {
        return AngleRestriction.FORTYFIVE_DEGREE;
    }

    /**
     * Tries to reduce the amount of corners of p_polyline.
     * Return p_polyline, if nothing was changed.
     */
    private Polyline reduce_corners(Polyline p_polyline)
    {
        if (p_polyline.arr.length <= 4)
        {
            return p_polyline;
        }
        int new_corner_count = 1;
        Point[] curr_corner = new Point[4];
        for (int i = 0; i < 4; ++i)
        {
            curr_corner[i] = p_polyline.corner(i);
            if (!(curr_corner[i] instanceof IntPoint))
            {
                return p_polyline;
            }
        }
        boolean[] curr_corner_in_clip_shape = new boolean[4];

        for (int i = 0; i < 4; ++i)
        {
            if (curr_clip_shape == null)
            {
                curr_corner_in_clip_shape[i] = true;
            }
            else
            {
                curr_corner_in_clip_shape[i] = !curr_clip_shape.is_outside(curr_corner[i]);
            }
        }

        boolean polyline_changed = false;
        Point[] new_corners = new Point[p_polyline.arr.length - 3];
        new_corners[0] = curr_corner[0];
        Point[] curr_check_points = new Point[2];
        Point new_corner = null;
        int corner_no = 3;
        while (corner_no < p_polyline.arr.length - 1)
        {
            boolean corner_removed = false;
            curr_corner[3] = p_polyline.corner(corner_no);
            if (!(curr_corner[3] instanceof IntPoint))
            {
                return p_polyline;
            }
            if (curr_corner[1].equals(curr_corner[2]) ||
                    corner_no < p_polyline.arr.length - 2 &&
                    curr_corner[3].side_of(curr_corner[1], curr_corner[2]) == Side.COLLINEAR)
            {
                // corners in the middle af a line can be skipped
                ++corner_no;
                curr_corner[2] = curr_corner[3];
                curr_corner_in_clip_shape[2] = curr_corner_in_clip_shape[3];
                if (corner_no < p_polyline.arr.length - 1)
                {
                    curr_corner[3] = p_polyline.corner(corner_no);
                    if (!(curr_corner[3] instanceof IntPoint))
                    {
                        return p_polyline;
                    }
                }
                polyline_changed = true;
            }
            curr_corner_in_clip_shape[3] = curr_clip_shape == null || !curr_clip_shape.is_outside(curr_corner[3]);
            if (curr_corner_in_clip_shape[1] && curr_corner_in_clip_shape[2] && curr_corner_in_clip_shape[3])
            {
                // translate the line from curr_corner[2] to curr_corner[1] to curr_corner[3]
                Vector delta = curr_corner[3].difference_by(curr_corner[2]);
                new_corner = curr_corner[1].translate_by(delta);
                if (curr_corner[3].equals(curr_corner[2]))
                {
                    // just remove multiple corner
                    corner_removed = true;
                }
                else if (new_corner.side_of(curr_corner[0], curr_corner[1]) == Side.COLLINEAR)
                {
                    curr_check_points[0] = new_corner;
                    curr_check_points[1] = curr_corner[1];
                    Polyline check_polyline = new Polyline(curr_check_points);
                    if (check_polyline.arr.length == 3)
                    {
                        TileShape shape_to_check = check_polyline.offset_shape(curr_half_width, 0);
                        if (board.check_trace_shape(shape_to_check, curr_layer, curr_net_no_arr,
                                curr_cl_type, this.contact_pins))
                        {
                            curr_check_points[1] = curr_corner[3];
                            if (curr_check_points[0].equals(curr_check_points[1]))
                            {
                                corner_removed = true;
                            }
                            else
                            {
                                check_polyline = new Polyline(curr_check_points);
                                if (check_polyline.arr.length == 3)
                                {
                                    shape_to_check = check_polyline.offset_shape(curr_half_width, 0);
                                    corner_removed = board.check_trace_shape(shape_to_check, curr_layer, curr_net_no_arr,
                                            curr_cl_type, this.contact_pins);
                                }
                                else
                                {
                                    corner_removed = true;
                                }
                            }
                        }
                    }
                    else
                    {
                        corner_removed = true;
                    }
                }
            }
            if (!corner_removed && curr_corner_in_clip_shape[0] && curr_corner_in_clip_shape[1] && curr_corner_in_clip_shape[2])
            {
                // the first try has failed. Try to translate the line from
                // corner_2 to corner_1 to corner_0
                Vector delta = curr_corner[0].difference_by(curr_corner[1]);
                new_corner = curr_corner[2].translate_by(delta);
                if (curr_corner[0].equals(curr_corner[1]))
                {
                    // just remove multiple corner
                    corner_removed = true;
                }
                else if (new_corner.side_of(curr_corner[2], curr_corner[3]) == Side.COLLINEAR)
                {
                    curr_check_points[0] = new_corner;
                    curr_check_points[1] = curr_corner[0];
                    Polyline check_polyline = new Polyline(curr_check_points);
                    if (check_polyline.arr.length == 3)
                    {
                        TileShape shape_to_check = check_polyline.offset_shape(curr_half_width, 0);
                        if (board.check_trace_shape(shape_to_check, curr_layer, curr_net_no_arr,
                                curr_cl_type, this.contact_pins))
                        {
                            curr_check_points[1] = curr_corner[2];
                            check_polyline = new Polyline(curr_check_points);
                            if (check_polyline.arr.length == 3)
                            {
                                shape_to_check = check_polyline.offset_shape(curr_half_width, 0);
                                corner_removed = board.check_trace_shape(shape_to_check, curr_layer, curr_net_no_arr,
                                        curr_cl_type, this.contact_pins);
                            }
                            else
                            {
                                corner_removed = true;
                            }
                        }
                    }
                    else
                    {
                        corner_removed = true;
                    }
                }
            }
            if (corner_removed)
            {
                polyline_changed = true;
                curr_corner[1] = new_corner;
                curr_corner_in_clip_shape[1] =
                        curr_clip_shape == null || !curr_clip_shape.is_outside(curr_corner[1]);
                if (board.changed_area != null)
                {
                    board.changed_area.join(new_corner.to_float(), curr_layer);
                    board.changed_area.join(curr_corner[1].to_float(), curr_layer);
                    board.changed_area.join(curr_corner[2].to_float(), curr_layer);
                }
            }
            else
            {
                new_corners[new_corner_count] = curr_corner[1];
                ++new_corner_count;
                curr_corner[0] = curr_corner[1];
                curr_corner[1] = curr_corner[2];
                curr_corner_in_clip_shape[0] = curr_corner_in_clip_shape[1];
                curr_corner_in_clip_shape[1] = curr_corner_in_clip_shape[2];
            }
            curr_corner[2] = curr_corner[3];
            curr_corner_in_clip_shape[2] = curr_corner_in_clip_shape[3];
            ++corner_no;
        }
        if (!polyline_changed)
        {
            return p_polyline;
        }
        Point adjusted_corners[] = new Point[new_corner_count + 2];
        for (int i = 0; i < new_corner_count; ++i)
        {
            adjusted_corners[i] = new_corners[i];
        }
        adjusted_corners[new_corner_count] = curr_corner[1];
        adjusted_corners[new_corner_count + 1] = curr_corner[2];
        Polyline result = new Polyline(adjusted_corners);
        return result;
    }

    /**
     * Smoothens the 90 degree corners of p_polyline to 45 degree
     * by cutting of the 90 degree corner. The cutting of is so small,
     * that no check is needed
     */
    private Polyline smoothen_corners(Polyline p_polyline)
    {
        Polyline result = p_polyline;
        boolean polyline_changed = true;
        while (polyline_changed)
        {
            if (result.arr.length < 4)
            {
                return result;
            }
            polyline_changed = false;
            Line[] line_arr = new Line[result.arr.length];
            System.arraycopy(result.arr, 0, line_arr, 0, line_arr.length);

            for (int i = 1; i < line_arr.length - 2; ++i)
            {
                Direction d1 = line_arr[i].direction();
                Direction d2 = line_arr[i + 1].direction();
                if (d1.is_multiple_of_45_degree() && d2.is_multiple_of_45_degree() && d1.projection(d2) != Signum.POSITIVE)
                {
                    // there is a 90 degree or sharper angle
                    Line new_line = smoothen_corner(line_arr, i);
                    if (new_line == null)
                    {
                        //the greedy smoothening couldn't change the polyline
                        new_line = smoothen_sharp_corner(line_arr, i);
                    }
                    if (new_line != null)
                    {
                        polyline_changed = true;
                        // add the new line into the line array
                        Line[] tmp_lines = new Line[line_arr.length + 1];
                        System.arraycopy(line_arr, 0, tmp_lines, 0, i + 1);
                        tmp_lines[i + 1] = new_line;
                        System.arraycopy(line_arr, i + 1, tmp_lines, i + 2,
                                tmp_lines.length - (i + 2));
                        line_arr = tmp_lines;
                        ++i;
                    }
                }
            }
            if (polyline_changed)
            {
                result = new Polyline(line_arr);
            }
        }
        return result;
    }

    /**
     * adds a line between at p_no  to smoothen a
     * 90 degree corner between p_line_1 and p_line_2 to 45 degree.
     * The distance of the new line to the corner will be so small
     * that no clearance check is necessary.
     */
    private Line smoothen_sharp_corner(Line[] p_line_arr, int p_no)
    {
        FloatPoint curr_corner = p_line_arr[p_no].intersection_approx(p_line_arr[p_no + 1]);
        if (curr_corner.x != (int) curr_corner.x)
        {
            // intersection of 2 diagonal lines is not integer
            Line result = smoothen_non_integer_corner(p_line_arr, p_no);
            {
                if (result != null)
                {
                    return result;
                }
            }
        }
        FloatPoint prev_corner = p_line_arr[p_no].intersection_approx(p_line_arr[p_no - 1]);
        FloatPoint next_corner = p_line_arr[p_no + 1].intersection_approx(p_line_arr[p_no + 2]);

        Direction prev_dir = p_line_arr[p_no].direction();
        Direction next_dir = p_line_arr[p_no + 1].direction();
        Direction new_line_dir = Direction.get_instance(prev_dir.get_vector().add(next_dir.get_vector()));
        Line translate_line = Line.get_instance(curr_corner.round(), new_line_dir);
        double translate_dist = (Limits.sqrt2 - 1) * this.curr_half_width;
        double prev_dist = Math.abs(translate_line.signed_distance(prev_corner));
        double next_dist = Math.abs(translate_line.signed_distance(next_corner));
        translate_dist = Math.min(translate_dist, prev_dist);
        translate_dist = Math.min(translate_dist, next_dist);
        if (translate_dist < 0.99)
        {
            return null;
        }
        translate_dist = Math.max(translate_dist - 1, 1);
        if (translate_line.side_of(next_corner) == Side.ON_THE_LEFT)
        {
            translate_dist = -translate_dist;
        }
        Line result = translate_line.translate(translate_dist);
        if (board.changed_area != null)
        {
            board.changed_area.join(curr_corner, curr_layer);
        }
        return result;
    }

    /**
     *  Smoothens with a short axis parrallel line to remove a non integer corner
     *  of two intersecting diagonal lines. 
     *  Returns null, if that is not possible.
     */
    private Line smoothen_non_integer_corner(Line[] p_line_arr, int p_no)
    {
        Line prev_line = p_line_arr[p_no];
        Line next_line = p_line_arr[p_no + 1];
        if (prev_line.is_equal_or_opposite(next_line))
        {
            return null;
        }
        if (!(prev_line.is_diagonal() && next_line.is_diagonal()))
        {
            return null;
        }
        FloatPoint curr_corner = prev_line.intersection_approx(next_line);
        FloatPoint prev_corner = prev_line.intersection_approx(p_line_arr[p_no - 1]);
        FloatPoint next_corner = next_line.intersection_approx(p_line_arr[p_no + 2]);
        Line result = null;
        int new_x = 0;
        int new_y = 0;
        boolean new_line_is_vertical = false;
        boolean new_line_is_horizontal = false;
        if (prev_corner.x > curr_corner.x && next_corner.x > curr_corner.x)
        {
            new_x = (int) Math.ceil(curr_corner.x);
            new_y = (int) Math.ceil(curr_corner.y);
            new_line_is_vertical = true;
        }
        else if (prev_corner.x < curr_corner.x && next_corner.x < curr_corner.x)
        {
            new_x = (int) Math.floor(curr_corner.x);
            new_y = (int) Math.floor(curr_corner.y);
            new_line_is_vertical = true;
        }
        else if (prev_corner.y > curr_corner.y && next_corner.y > curr_corner.y)
        {
            new_x = (int) Math.ceil(curr_corner.x);
            new_y = (int) Math.ceil(curr_corner.y);
            new_line_is_horizontal = true;
        }
        else if (prev_corner.y < curr_corner.y && next_corner.y < curr_corner.y)
        {
            new_x = (int) Math.floor(curr_corner.x);
            new_y = (int) Math.floor(curr_corner.y);
            new_line_is_horizontal = true;
        }
        Direction new_line_dir = null;
        if (new_line_is_vertical)
        {
            if (prev_corner.y < next_corner.y)
            {
                new_line_dir = Direction.UP;
            }
            else
            {
                new_line_dir = Direction.DOWN;
            }
        }
        else if (new_line_is_horizontal)
        {
            if (prev_corner.x < next_corner.x)
            {
                new_line_dir = Direction.RIGHT;
            }
            else
            {
                new_line_dir = Direction.LEFT;
            }
        }
        else
        {
            return null;
        }
        
        Point line_a = new IntPoint(new_x, new_y);
        result = new Line(line_a, new_line_dir);
        return result;
    }

    /**
     * adds a line between at p_no  to smoothen a
     * 90 degree corner between p_line_1 and p_line_2 to 45 degree.
     * The distance of the new line to the corner will be so big
     * that a clearance check is necessary.
     */
    private Line smoothen_corner(Line[] p_line_arr, int p_no)
    {
        FloatPoint prev_corner = p_line_arr[p_no].intersection_approx(p_line_arr[p_no - 1]);
        FloatPoint curr_corner = p_line_arr[p_no].intersection_approx(p_line_arr[p_no + 1]);
        FloatPoint next_corner = p_line_arr[p_no + 1].intersection_approx(p_line_arr[p_no + 2]);

        Direction prev_dir = p_line_arr[p_no].direction();
        Direction next_dir = p_line_arr[p_no + 1].direction();
        Direction new_line_dir = Direction.get_instance(prev_dir.get_vector().add(next_dir.get_vector()));
        Line translate_line = Line.get_instance(curr_corner.round(), new_line_dir);
        double prev_dist = Math.abs(translate_line.signed_distance(prev_corner));
        double next_dist = Math.abs(translate_line.signed_distance(next_corner));
        if (prev_dist == 0 || next_dist == 0)
        {
            return null;
        }
        double max_translate_dist;
        FloatPoint nearest_corner;
        if (prev_dist <= next_dist)
        {
            max_translate_dist = prev_dist;
            nearest_corner = prev_corner;
        }
        else
        {
            max_translate_dist = next_dist;
            nearest_corner = next_corner;
        }
        if (max_translate_dist < 1)
        {
            return null;
        }
        max_translate_dist = Math.max(max_translate_dist - 1, 1);
        if (translate_line.side_of(next_corner) == Side.ON_THE_LEFT)
        {
            max_translate_dist = -max_translate_dist;
        }
        Line[] check_lines = new Line[3];
        check_lines[0] = p_line_arr[p_no];
        check_lines[2] = p_line_arr[p_no + 1];
        double translate_dist = max_translate_dist;
        double delta_dist = max_translate_dist;
        Side side_of_nearest_corner = translate_line.side_of(nearest_corner);
        int sign = Signum.as_int(max_translate_dist);
        Line result = null;
        while (Math.abs(delta_dist) > this.min_translate_dist)
        {
            boolean check_ok = false;
            Line new_line = translate_line.translate(translate_dist);
            Side new_line_side_of_nearest_corner = new_line.side_of(nearest_corner);
            if (new_line_side_of_nearest_corner == side_of_nearest_corner || new_line_side_of_nearest_corner == Side.COLLINEAR)
            {
                check_lines[1] = new_line;
                Polyline tmp = new Polyline(check_lines);

                if (tmp.arr.length == 3)
                {
                    TileShape shape_to_check =
                            tmp.offset_shape(curr_half_width, 0);
                    check_ok = board.check_trace_shape(shape_to_check,
                            curr_layer, curr_net_no_arr, curr_cl_type, this.contact_pins);

                }
                delta_dist /= 2;
                if (check_ok)
                {
                    result = check_lines[1];
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
        if (result != null && board.changed_area != null)
        {
            FloatPoint new_prev_corner =
                    check_lines[0].intersection_approx(result);
            FloatPoint new_next_corner =
                    check_lines[2].intersection_approx(result);
            board.changed_area.join(new_prev_corner, curr_layer);
            board.changed_area.join(new_next_corner, curr_layer);
            board.changed_area.join(curr_corner, curr_layer);
        }
        return result;
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
        Side prev_corner_side = null;
        Direction line_direction = trace_polyline.arr[1].direction();
        Direction prev_line_direction = trace_polyline.arr[2].direction();

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
                    if (curr_other_trace_line.direction().is_orthogonal())
                    {
                        acute_angle = true;
                        other_trace_found = true;
                    }
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
                Line[] new_lines = new Line[trace_polyline.arr.length + 1];
                new_lines[0] = other_trace_line;
                new_lines[1] = add_line;
                for (int i = 2; i < new_lines.length; ++i)
                {
                    new_lines[i] = trace_polyline.arr[i - 1];
                }
                return new Polyline(new_lines);
            }
        }
        else if (bend)
        {
            Line[] check_line_arr = new Line[trace_polyline.arr.length + 1];
            check_line_arr[0] = other_prev_trace_line;
            check_line_arr[1] = other_trace_line;
            for (int i = 2; i < check_line_arr.length; ++i)
            {
                check_line_arr[i] = trace_polyline.arr[i - 1];
            }
            Line new_line = reposition_line(check_line_arr, 2);
            if (new_line != null)
            {
                Line[] new_lines = new Line[trace_polyline.arr.length];
                new_lines[0] = other_trace_line;
                new_lines[1] = new_line;
                for (int i = 2; i < new_lines.length; ++i)
                {
                    new_lines[i] = trace_polyline.arr[i];
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
        Side prev_corner_side = null;
        Direction line_direction = trace_polyline.arr[trace_polyline.arr.length - 2].direction().opposite();
        Direction prev_line_direction = trace_polyline.arr[trace_polyline.arr.length - 3].direction().opposite();

        java.util.Collection<Item> contact_list = p_trace.get_end_contacts();
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
                    if (curr_other_trace_line.direction().is_orthogonal())
                    {
                        acute_angle = true;
                        other_trace_found = true;
                    }
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
                Line[] new_lines = new Line[trace_polyline.arr.length + 1];
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
            Line[] check_line_arr = new Line[trace_polyline.arr.length + 1];
            for (int i = 0; i < trace_polyline.arr.length - 1; ++i)
            {
                check_line_arr[i] = trace_polyline.arr[i];
            }
            check_line_arr[check_line_arr.length - 2] = other_trace_line;
            check_line_arr[check_line_arr.length - 1] = other_prev_trace_line;
            Line new_line = reposition_line(check_line_arr, trace_polyline.arr.length - 2);
            if (new_line != null)
            {
                Line[] new_lines = new Line[trace_polyline.arr.length];
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
}
