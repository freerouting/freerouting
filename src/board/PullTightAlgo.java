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
 * PullTightAlgo.java
 *
 * Created on 19. Juli 2003, 12:42
 */
package board;

import geometry.planar.FloatPoint;
import geometry.planar.IntOctagon;
import geometry.planar.IntPoint;
import geometry.planar.Line;
import geometry.planar.Point;
import geometry.planar.Polyline;
import geometry.planar.Side;
import geometry.planar.TileShape;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import datastructures.Signum;
import datastructures.Stoppable;
import datastructures.TimeLimit;

import autoroute.AutorouteControl.ExpansionCostFactor;

/**
 * Class with functionality for optimising traces and vias.
 *
 * @author  Alfons Wirtz
 */
public abstract class PullTightAlgo
{

    /**
     * Returns a new instance of  PullTightAlgo.
     * If p_only_net_no > 0, only traces with net number p_not_no are optimized.
     * If p_stoppable_thread != null, the agorithm can be requested to be stopped.
     * If p_time_limit > 0; the algorithm will be stopped after p_time_limit Milliseconds.
     */
    static PullTightAlgo get_instance(RoutingBoard p_board,
            int[] p_only_net_no_arr, IntOctagon p_clip_shape, int p_min_translate_dist,
            Stoppable p_stoppable_thread, int p_time_limit, Point p_keep_point, int p_keep_point_layer)
    {
        PullTightAlgo result;
        AngleRestriction angle_restriction = p_board.rules.get_trace_angle_restriction();
        if (angle_restriction == AngleRestriction.NINETY_DEGREE)
        {
            result = new PullTightAlgo90(p_board, p_only_net_no_arr, p_stoppable_thread, p_time_limit,
                    p_keep_point, p_keep_point_layer);
        }
        else if (angle_restriction == AngleRestriction.FORTYFIVE_DEGREE)
        {
            result = new PullTightAlgo45(p_board, p_only_net_no_arr, p_stoppable_thread, p_time_limit,
                    p_keep_point, p_keep_point_layer);
        }
        else
        {
            result = new PullTightAlgoAnyAngle(p_board, p_only_net_no_arr, p_stoppable_thread, p_time_limit,
                    p_keep_point, p_keep_point_layer);
        }
        result.curr_clip_shape = p_clip_shape;
        result.min_translate_dist = Math.max(p_min_translate_dist, 100);
        return result;
    }

    /** Creates a new instance of PullTightAlgo */
    PullTightAlgo(RoutingBoard p_board, int[] p_only_net_no_arr, Stoppable p_stoppable_thread, int p_time_limit,
            Point p_keep_point, int p_keep_point_layer)
    {
        board = p_board;
        only_net_no_arr = p_only_net_no_arr;
        stoppable_thread = p_stoppable_thread;
        if (p_time_limit > 0)
        {
            this.time_limit = new TimeLimit(p_time_limit);
        }
        else
        {
            this.time_limit = null;
        }
        this.keep_point = p_keep_point;
        this.keep_point_layer = p_keep_point_layer;
    }

    /**
     * Function for optimizing the route in an internal marked area.
     * If p_clip_shape != null, the optimizing area is restricted to p_clip_shape.
     *  p_trace_cost_arr is used for optimizing vias and may be null.
     */
    void opt_changed_area(ExpansionCostFactor[] p_trace_cost_arr)
    {
        if (board.changed_area == null)
        {
            return;
        }
        boolean something_changed = true;
        // starting with curr_min_translate_dist big is a try to
        // avoid fine approximation at the beginning to avoid
        // problems with dog ears
        while (something_changed)
        {
            something_changed = false;
            for (int i = 0; i < board.get_layer_count(); ++i)
            {
                IntOctagon changed_region = board.changed_area.get_area(i);
                if (changed_region.is_empty())
                {
                    continue;
                }
                board.changed_area.set_empty(i);
                board.join_graphics_update_box(changed_region.bounding_box());
                double changed_area_offset =
                        1.5 * (board.rules.clearance_matrix.max_value(i) + 2 * board.rules.get_max_trace_half_width());
                changed_region = changed_region.enlarge(changed_area_offset);
                // search in the ShapeSearchTree for all overlapping traces
                // with clip_shape on layer i
                Collection<SearchTreeObject> items = board.overlapping_objects(changed_region, i);
                Iterator<SearchTreeObject> it = items.iterator();
                while (it.hasNext())
                {
                    if (this.is_stop_requested())
                    {
                        return;
                    }
                    SearchTreeObject curr_ob = it.next();
                    if (curr_ob instanceof PolylineTrace)
                    {
                        PolylineTrace curr_trace = (PolylineTrace) curr_ob;
                        if (curr_trace.pull_tight(this))
                        {
                            something_changed = true;
                            if (this.split_traces_at_keep_point())
                            {
                                break;
                            }
                        }
                        else if (smoothen_end_corners_at_trace_1(curr_trace))
                        {
                            something_changed = true;
                            break; // because items may be removed
                        }
                    }
                    else if (curr_ob instanceof Via && p_trace_cost_arr != null)
                    {
                        if (OptViaAlgo.opt_via_location(this.board, (Via) curr_ob,
                                p_trace_cost_arr, this.min_translate_dist, 10))
                        {
                            something_changed = true;
                        }
                    }
                }
            }
        }
    }

    /**
     * Function for optimizing a single trace polygon
     * p_contact_pins are the pins at the end corners of p_polyline.
     * Other pins are regarded as obstacles, even if they are of the own net.
     */
    Polyline pull_tight(Polyline p_polyline, int p_layer, int p_half_width,
            int[] p_net_no_arr, int p_cl_type, Set<Pin> p_contact_pins)
    {
        curr_layer = p_layer;
        ShapeSearchTree search_tree = this.board.search_tree_manager.get_default_tree();
        curr_half_width = p_half_width + search_tree.clearance_compensation_value(p_cl_type, p_layer);
        curr_net_no_arr = p_net_no_arr;
        curr_cl_type = p_cl_type;
        contact_pins = p_contact_pins;
        return pull_tight(p_polyline);
    }

    /**
     * Termitates the pull tight algorithm, if the user has made a stop request.
     */
    protected boolean is_stop_requested()
    {
        if (this.stoppable_thread != null && this.stoppable_thread.is_stop_requested())
        {
            return true;
        }
        if (this.time_limit == null)
        {
            return false;
        }
        boolean time_limit_exceeded = this.time_limit.limit_exceeded();
        if (time_limit_exceeded && this.board.get_test_level().ordinal() >= TestLevel.CRITICAL_DEBUGGING_OUTPUT.ordinal())
        {
            System.out.println("PullTightAlgo.is_stop_requested: time limit exceeded");
        }
        return time_limit_exceeded;
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
        for (int i = 2; i < p_polyline.arr.length - 2; ++i)
        {
            Line new_line = reposition_line(p_polyline.arr, i);
            if (new_line != null)
            {
                Line[] line_arr = new Line[p_polyline.arr.length];
                System.arraycopy(p_polyline.arr, 0, line_arr, 0, line_arr.length);
                line_arr[i] = new_line;
                Polyline result = new Polyline(line_arr);
                return skip_segments_of_length_0(result);
            }
        }
        return p_polyline;
    }

    /**
     * Tries to reposition the line with index p_no to make the polyline consisting
     * of p_line_arr shorter.
     */
    protected Line reposition_line(Line[] p_line_arr, int p_no)
    {
        if (p_line_arr.length - p_no < 3)
        {
            return null;
        }
        if (curr_clip_shape != null)
        // check, that the corners of the line to translate are inside
        // the clip shape
        {
            for (int i = -1; i < 1; ++i)
            {
                Point curr_corner =
                        p_line_arr[p_no + i].intersection(p_line_arr[p_no + i + 1]);
                if (curr_clip_shape.is_outside(curr_corner))
                {
                    return null;
                }
            }
        }
        Line translate_line = p_line_arr[p_no];
        Point prev_corner =
                p_line_arr[p_no - 2].intersection(p_line_arr[p_no - 1]);
        Point next_corner =
                p_line_arr[p_no + 1].intersection(p_line_arr[p_no + 2]);
        double prev_dist = translate_line.signed_distance(prev_corner.to_float());
        double next_dist = translate_line.signed_distance(next_corner.to_float());
        if (Signum.of(prev_dist) != Signum.of(next_dist))
        {
            // the 2 corners are at different sides of translate_line
            return null;
        }
        Point nearest_point;
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
        double translate_dist = max_translate_dist;
        double delta_dist = max_translate_dist;
        Side side_of_nearest_point = translate_line.side_of(nearest_point);
        int sign = Signum.as_int(max_translate_dist);
        Line new_line = null;
        Line[] check_lines = new Line[3];
        check_lines[0] = p_line_arr[p_no - 1];
        check_lines[2] = p_line_arr[p_no + 1];
        boolean first_time = true;
        while (first_time || Math.abs(delta_dist) > min_translate_dist)
        {
            boolean check_ok = false;

            if (first_time && nearest_point instanceof IntPoint)
            {
                check_lines[1] = Line.get_instance(nearest_point, translate_line.direction());
            }
            else
            {
                check_lines[1] = translate_line.translate(-translate_dist);
            }
            if (check_lines[1].equals(translate_line))
            {
                // may happen at first time if nearest_point is not an IntPoint
                return null;
            }
            Side new_line_side_of_nearest_point = check_lines[1].side_of(nearest_point);
            if (new_line_side_of_nearest_point != side_of_nearest_point && new_line_side_of_nearest_point != Side.COLLINEAR)
            {
                // moved a little bit to far at the first time
                // because of numerical inaccuracy;
                // may happen if nearest_point is not an IntPoint
                double shorten_value = sign * 0.5;
                max_translate_dist -= shorten_value;
                translate_dist -= shorten_value;
                delta_dist -= shorten_value;
                continue;
            }
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
                new_line = check_lines[1];
                if (first_time)
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
            first_time = false;
        }
        if (new_line != null && board.changed_area != null)
        {
            // mark the changed area
            board.changed_area.join(check_lines[0].intersection_approx(new_line), curr_layer);
            board.changed_area.join(check_lines[2].intersection_approx(new_line), curr_layer);
            board.changed_area.join(p_line_arr[p_no - 1].intersection_approx(p_line_arr[p_no]), curr_layer);
            board.changed_area.join(p_line_arr[p_no].intersection_approx(p_line_arr[p_no + 1]), curr_layer);

        }
        return new_line;
    }

    /**
     * tries to skip linesegments of length 0.
     * A check is nessesary before skipping because new dog ears
     * may occur.
     */
    Polyline skip_segments_of_length_0(Polyline p_polyline)
    {
        boolean polyline_changed = false;
        Polyline curr_polyline = p_polyline;
        for (int i = 1; i < curr_polyline.arr.length - 1; ++i)
        {
            boolean try_skip;
            if (i == 1 || i == curr_polyline.arr.length - 2)
            // the position of the first corner and the last corner
            //  must be retaimed exactly
            {
                Point prev_corner = curr_polyline.corner(i - 1);
                Point curr_corner = curr_polyline.corner(i);
                try_skip = curr_corner.equals(prev_corner);
            }
            else
            {
                FloatPoint prev_corner = curr_polyline.corner_approx(i - 1);
                FloatPoint curr_corner = curr_polyline.corner_approx(i);
                try_skip = curr_corner.distance_square(prev_corner) <
                        c_min_corner_dist_square;
            }

            if (try_skip)
            {
                // check, if skipping the line of length 0 does not
                // result in a clearance violation
                Line[] curr_lines = new Line[curr_polyline.arr.length - 1];
                System.arraycopy(curr_polyline.arr, 0, curr_lines, 0, i);
                System.arraycopy(curr_polyline.arr, i + 1, curr_lines,
                        i, curr_lines.length - i);
                Polyline tmp = new Polyline(curr_lines);
                boolean check_ok = (tmp.arr.length == curr_lines.length);
                if (check_ok && !curr_polyline.arr[i].is_multiple_of_45_degree())
                {
                    // no check necessary for skipping 45 degree lines, because the check is
                    // performance critical and the line shapes
                    // are intersected with the bounding octagon anyway.
                    if (i > 1)
                    {
                        TileShape shape_to_check =
                                tmp.offset_shape(curr_half_width, i - 2);
                        check_ok = board.check_trace_shape(shape_to_check,
                                curr_layer, curr_net_no_arr, curr_cl_type, this.contact_pins);
                    }
                    if (check_ok && (i < curr_polyline.arr.length - 2))
                    {
                        TileShape shape_to_check = tmp.offset_shape(curr_half_width, i - 1);
                        check_ok = board.check_trace_shape(shape_to_check,
                                curr_layer, curr_net_no_arr, curr_cl_type, this.contact_pins);
                    }
                }
                if (check_ok)
                {
                    polyline_changed = true;
                    curr_polyline = tmp;
                    --i;
                }
            }
        }
        if (!polyline_changed)
        {
            return p_polyline;
        }
        return curr_polyline;
    }

    /**
     * Smoothens acute angles with contact traces.
     * Returns true, if something was changed.
     */
    boolean smoothen_end_corners_at_trace(PolylineTrace p_trace)
    {
        curr_layer = p_trace.get_layer();
        curr_half_width = p_trace.get_half_width();
        curr_net_no_arr = p_trace.net_no_arr;
        curr_cl_type = p_trace.clearance_class_no();
        return smoothen_end_corners_at_trace_1(p_trace);
    }

    /**
     * Smoothens acute angles with contact traces.
     * Returns true, if something was changed.
     */
    private boolean smoothen_end_corners_at_trace_1(PolylineTrace p_trace)
    {
        // try to improve the connection to other traces
        if (p_trace.is_shove_fixed())
        {
            return false;
        }
        Set<Pin> saved_contact_pins = this.contact_pins;
        // to allow the trace to slide to the end point of a contact trace, if the contact trace ends at a pin.
        this.contact_pins = null;
        boolean result = false;
        boolean connection_to_trace_improved = true;
        PolylineTrace curr_trace = p_trace;
        while (connection_to_trace_improved)
        {
            connection_to_trace_improved = false;
            Polyline adjusted_polyline = smoothen_end_corners_at_trace_2(curr_trace);
            if (adjusted_polyline != null)
            {
                result = true;
                connection_to_trace_improved = true;
                int trace_layer = curr_trace.get_layer();
                int curr_cl_class = curr_trace.clearance_class_no();
                FixedState curr_fixed_state = curr_trace.get_fixed_state();
                board.remove_item(curr_trace);
                curr_trace = board.insert_trace_without_cleaning(adjusted_polyline, trace_layer, curr_half_width, curr_trace.net_no_arr,
                        curr_cl_class, curr_fixed_state);
                for (int curr_net_no : curr_trace.net_no_arr)
                {
                    board.split_traces(adjusted_polyline.first_corner(), trace_layer, curr_net_no);
                    board.split_traces(adjusted_polyline.last_corner(), trace_layer, curr_net_no);
                    board.normalize_traces(curr_net_no);

                    if (split_traces_at_keep_point())
                    {
                        return true;
                    }
                }
            }
        }
        this.contact_pins = saved_contact_pins;
        return result;
    }

    /**
     *  Splits the traces containing this.keep_point if this.keep_point != null.
     *  Returns true, if something was split.
     */
    boolean split_traces_at_keep_point()
    {
        if (this.keep_point == null)
        {
            return false;
        }
        ItemSelectionFilter filter = new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.TRACES);
        Collection<Item> picked_items = this.board.pick_items(this.keep_point, this.keep_point_layer, filter);
        for (Item curr_item : picked_items)
        {
            Trace[] split_pieces = ((Trace) curr_item).split(this.keep_point);
            if (split_pieces != null)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Smoothens acute angles with contact traces.
     * Returns null, if something was changed.
     */
    private Polyline smoothen_end_corners_at_trace_2(PolylineTrace p_trace)
    {
        if (p_trace == null || !p_trace.is_on_the_board())
        {
            return null;
        }
        Polyline result = smoothen_start_corner_at_trace(p_trace);
        if (result == null)
        {
            result = smoothen_end_corner_at_trace(p_trace);
            if (result != null && board.changed_area != null)
            {
                // mark the changed area
                board.changed_area.join(result.corner_approx(result.corner_count() - 1), curr_layer);
            }
        }
        else if (board.changed_area != null)
        {
            // mark the changed area
            board.changed_area.join(result.corner_approx(0), curr_layer);
        }
        if (result != null)
        {
            this.contact_pins = p_trace.touching_pins_at_end_corners();
            result = skip_segments_of_length_0(result);
        }
        return result;
    }

    /**
     * Wraps around pins of the own net to avoid acid traps.
     */
    protected Polyline avoid_acid_traps(Polyline p_polyline)
    {
        if (true)
        {
            return p_polyline;
        }
        Polyline result = p_polyline;
        ShoveTraceAlgo shove_trace_algo = new ShoveTraceAlgo(this.board);
        Polyline new_polyline = shove_trace_algo.spring_over_obstacles(p_polyline,
                curr_half_width, curr_layer, curr_net_no_arr, curr_cl_type, contact_pins);
        if (new_polyline != null && new_polyline != p_polyline)
        {
            if (this.board.check_polyline_trace(new_polyline, curr_layer, curr_half_width,
                    curr_net_no_arr, curr_cl_type))
            {
                result = new_polyline;
            }
        }
        return result;
    }

    abstract Polyline pull_tight(Polyline p_polyline);

    abstract Polyline smoothen_start_corner_at_trace(PolylineTrace p_trace);

    abstract Polyline smoothen_end_corner_at_trace(PolylineTrace p_trace);
    protected final RoutingBoard board;
    /** If only_net_no > 0, only nets with this net numbers are optimized. */
    protected final int[] only_net_no_arr;
    protected int curr_layer;
    protected int curr_half_width;
    protected int[] curr_net_no_arr;
    protected int curr_cl_type;
    protected IntOctagon curr_clip_shape;
    protected Set<Pin> contact_pins;
    protected int min_translate_dist;
    protected static final double c_max_cos_angle = 0.999;
    // with angles to close to 180 degree the algorithm becomes numerically
    // unstable
    protected static final double c_min_corner_dist_square = 0.9;
    /**
     * If stoppable_thread != null, the agorithm can be requested to be stopped.
     */
    private final Stoppable stoppable_thread;
    private final TimeLimit time_limit;
    /**
     *  If keep_point != null, traces containing the keep_point must also 
     *  contain the keep_point after optimizing.
     */
    private final Point keep_point;
    private final int keep_point_layer;
}
