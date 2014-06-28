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
package interactive;

import datastructures.TimeLimit;

import geometry.planar.Area;
import geometry.planar.FloatPoint;
import geometry.planar.IntBox;
import geometry.planar.IntOctagon;
import geometry.planar.IntPoint;
import geometry.planar.Vector;
import geometry.planar.Point;
import geometry.planar.Polyline;
import geometry.planar.Ellipse;

import java.awt.Graphics;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

import library.Padstack;

import rules.ViaRule;
import rules.ViaInfo;
import rules.Net;

import board.AngleRestriction;
import board.Trace;
import board.ConductionArea;
import board.DrillItem;
import board.Item;
import board.PolylineTrace;
import board.RoutingBoard;
import board.ItemSelectionFilter;
import board.TestLevel;
import board.Unit;

import boardgraphics.GraphicsContext;

/**
 *
 * Functionality for interactive routing.
 *
 * @author Alfons Wirtz
 */
public class Route
{

    /**
     * Starts routing a connection.
     * p_pen_half_width_arr is provided because it may be different from
     * the half width array in p_board.rules.
     */
    public Route(Point p_start_corner, int p_layer, int[] p_pen_half_width_arr, boolean[] p_layer_active_arr, int[] p_net_no_arr,
            int p_clearance_class, ViaRule p_via_rule, boolean p_push_enabled,
            int p_trace_tidy_width, int p_pull_tight_accuracy, Item p_start_item, Set<Item> p_target_set,
            RoutingBoard p_board, boolean p_is_stitch_mode, boolean p_with_neckdown, boolean p_via_snap_to_smd_center,
            boolean p_hilight_shove_failing_obstacle)
    {
        board = p_board;
        layer = p_layer;
        if (p_push_enabled)
        {
            max_shove_trace_recursion_depth = 20;
            max_shove_via_recursion_depth = 8;
            max_spring_over_recursion_depth = 5;
        }
        else
        {
            max_shove_trace_recursion_depth = 0;
            max_shove_via_recursion_depth = 0;
            max_spring_over_recursion_depth = 0;
        }
        trace_tidy_width = p_trace_tidy_width;
        pull_tight_accuracy = p_pull_tight_accuracy;
        prev_corner = p_start_corner;
        net_no_arr = p_net_no_arr;
        pen_half_width_arr = p_pen_half_width_arr;
        layer_active = p_layer_active_arr;
        clearance_class = p_clearance_class;
        via_rule = p_via_rule;
        start_item = p_start_item;
        target_set = p_target_set;
        is_stitch_mode = p_is_stitch_mode;
        with_neckdown = p_with_neckdown;
        via_snap_to_smd_center = p_via_snap_to_smd_center;
        hilight_shove_failing_obstacle = p_hilight_shove_failing_obstacle;
        if (p_board.get_test_level() == TestLevel.RELEASE_VERSION)
        {
            this.pull_tight_time_limit = PULL_TIGHT_TIME_LIMIT;
        }
        else
        {
            this.pull_tight_time_limit = 0;
        }
        calculate_target_points_and_areas();
        swap_pin_infos = calculate_swap_pin_infos();
    }

    /**
     * Append a line to the trace routed so far.
     * Return true, if the route is completed by connecting
     * to a target.
     */
    public boolean next_corner(FloatPoint p_corner)
    {
        if (!this.layer_active[this.layer])
        {
            return false;
        }
        IntPoint curr_corner = p_corner.round();
        if (!(board.contains(prev_corner) && board.contains(curr_corner) && board.layer_structure.arr[this.layer].is_signal))
        {
            return false;
        }

        if (curr_corner.equals(prev_corner))
        {
            return false;
        }
        if (nearest_target_item instanceof DrillItem)
        {
            DrillItem target = (DrillItem) nearest_target_item;
            if (this.prev_corner.equals(target.get_center()))
            {
                return true; // connection already completed at prev_corner.
            }
        }
        this.shove_failing_obstacle = null;
        AngleRestriction angle_restriction = this.board.rules.get_trace_angle_restriction();
        if (angle_restriction != AngleRestriction.NONE && !(prev_corner instanceof IntPoint))
        {
            return false;
        }
        if (angle_restriction == AngleRestriction.NINETY_DEGREE)
        {
            curr_corner = curr_corner.orthogonal_projection((IntPoint) prev_corner);
        }
        else if (angle_restriction == AngleRestriction.FORTYFIVE_DEGREE)
        {
            curr_corner = curr_corner.fortyfive_degree_projection((IntPoint) prev_corner);
        }
        Item end_routing_item = board.pick_nearest_routing_item(prev_corner, this.layer, null);
        // look for a nearby item of this net, which is not connected to end_routing_item.
        nearest_target_item = board.pick_nearest_routing_item(curr_corner, this.layer, end_routing_item);
        TimeLimit check_forced_trace_time_limit;
        if (is_stitch_mode || this.board.get_test_level() != TestLevel.RELEASE_VERSION)
        {
            // because no check before inserting in this case
            check_forced_trace_time_limit = null;
        }
        else
        {
            check_forced_trace_time_limit = new TimeLimit(CHECK_FORCED_TRACE_TIME_LIMIT);
        }


        // tests.Validate.check("before insert", board);
        Point ok_point = board.insert_forced_trace_segment(prev_corner,
                curr_corner, pen_half_width_arr[layer], layer, net_no_arr, clearance_class,
                max_shove_trace_recursion_depth, max_shove_via_recursion_depth, max_spring_over_recursion_depth,
                trace_tidy_width, pull_tight_accuracy, !is_stitch_mode, check_forced_trace_time_limit);
        // tests.Validate.check("after insert", board);
        if (ok_point == prev_corner && this.with_neckdown)
        {
            ok_point = try_neckdown_at_start(curr_corner);
        }
        if (ok_point == prev_corner && this.with_neckdown)
        {
            ok_point = try_neckdown_at_end(this.prev_corner, curr_corner);
        }
        if (ok_point == null)
        {
            // database may be damaged, restore previous situation
            board.undo(null);
            // end routing in case it is dynamic
            return (!is_stitch_mode);
        }

        if (ok_point == prev_corner)
        {
            set_shove_failing_obstacle(board.get_shove_failing_obstacle());
            return false;
        }
        this.prev_corner = ok_point;
        // check, if a target is reached
        boolean route_completed = false;
        if (ok_point == curr_corner)
        {
            route_completed = connect_to_target(curr_corner);
        }

        IntOctagon tidy_clip_shape;
        if (trace_tidy_width == Integer.MAX_VALUE)
        {
            tidy_clip_shape = null;
        }
        else if (trace_tidy_width == 0)
        {
            tidy_clip_shape = IntOctagon.EMPTY;
        }
        else
        {
            tidy_clip_shape = ok_point.surrounding_octagon().enlarge(trace_tidy_width);
        }
        int[] opt_net_no_arr;
        if (max_shove_trace_recursion_depth <= 0)
        {
            opt_net_no_arr = net_no_arr;
        }
        else
        {
            opt_net_no_arr = new int[0];
        }
        if (route_completed)
        {
            this.board.reduce_nets_of_route_items();
            for (int curr_net_no : this.net_no_arr)
            {
                this.board.combine_traces(curr_net_no);
            }
        }
        else
        {
            calc_nearest_target_point(this.prev_corner.to_float());
        }
        board.opt_changed_area(opt_net_no_arr, tidy_clip_shape, pull_tight_accuracy,
                null, null, pull_tight_time_limit, ok_point, layer);
        return route_completed;
    }

    /**
     * Changing the layer in interactive route and inserting a via.
     *  Returns false, if changing the layer was not possible.
     */
    public boolean change_layer(int p_to_layer)
    {
        if (this.layer == p_to_layer)
        {
            return true;
        }
        if (p_to_layer < 0 || p_to_layer >= this.layer_active.length)
        {
            System.out.println("Route.change_layer: p_to_layer out of range");
            return false;
        }
        if (!this.layer_active[p_to_layer])
        {
            return false;
        }
        if (this.via_rule == null)
        {
            return false;
        }
        this.shove_failing_obstacle = null;
        if (this.via_snap_to_smd_center)
        {
            boolean snapped_to_smd_center = snap_to_smd_center(p_to_layer);
            if (!snapped_to_smd_center)
            {
                snap_to_smd_center(this.layer);
            }
        }
        boolean result = true;
        int min_layer = Math.min(this.layer, p_to_layer);
        int max_layer = Math.max(this.layer, p_to_layer);
        boolean via_found = false;
        for (int i = 0; i < this.via_rule.via_count(); ++i)
        {
            ViaInfo curr_via_info = this.via_rule.get_via(i);
            Padstack curr_via_padstack = curr_via_info.get_padstack();
            if (min_layer < curr_via_padstack.from_layer() || max_layer > curr_via_padstack.to_layer())
            {
                continue;
            }
            // make the current situation restorable by undo
            board.generate_snapshot();
            result = board.forced_via(curr_via_info, this.prev_corner, this.net_no_arr, clearance_class,
                    pen_half_width_arr, max_shove_trace_recursion_depth,
                    0, this.trace_tidy_width, this.pull_tight_accuracy, pull_tight_time_limit);
            if (result)
            {
                via_found = true;
                break;
            }
            set_shove_failing_obstacle(board.get_shove_failing_obstacle());
            board.undo(null);
        }
        if (via_found)
        {
            this.layer = p_to_layer;
        }
        return result;
    }

    /**
     * Snaps to the center of an smd pin, if the location location on p_layer is inside an smd pin of the own net,
     */
    private boolean snap_to_smd_center(int p_layer)
    {
        ItemSelectionFilter selection_filter = new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.PINS);
        java.util.Collection<Item> picked_items = board.pick_items(this.prev_corner, p_layer, selection_filter);
        board.Pin found_smd_pin = null;
        for (Item curr_item : picked_items)
        {
            if (curr_item instanceof board.Pin && curr_item.shares_net_no(this.net_no_arr))
            {
                board.Pin curr_pin = (board.Pin) curr_item;
                if (curr_pin.first_layer() == p_layer && curr_pin.last_layer() == p_layer)
                {
                    found_smd_pin = curr_pin;
                    break;
                }
            }
        }
        if (found_smd_pin == null)
        {
            return false;
        }
        Point pin_center = found_smd_pin.get_center();
        if (!(pin_center instanceof IntPoint))
        {
            return false;
        }
        IntPoint to_corner = (IntPoint) pin_center;
        if (this.connect(this.prev_corner, to_corner))
        {
            this.prev_corner = to_corner;
        }
        return true;
    }

    /**
     * If p_from_point is already on a target item, a connection
     * to the target is made and true returned.
     */
    private boolean connect_to_target(IntPoint p_from_point)
    {
        if (nearest_target_item != null && target_set != null && !target_set.contains(nearest_target_item))
        {
            nearest_target_item = null;
        }
        if (nearest_target_item == null || !nearest_target_item.shares_net_no(this.net_no_arr))
        {
            return false;
        }
        boolean route_completed = false;
        Point connection_point = null;
        if (nearest_target_item instanceof DrillItem)
        {
            DrillItem target = (DrillItem) nearest_target_item;
            connection_point = target.get_center();
        }
        else if (nearest_target_item instanceof PolylineTrace)
        {
            return board.connect_to_trace(p_from_point, (PolylineTrace) nearest_target_item,
                    this.pen_half_width_arr[layer], this.clearance_class);
        }
        else if (nearest_target_item instanceof ConductionArea)
        {
            connection_point = p_from_point;
        }
        if (connection_point != null && connection_point instanceof IntPoint)
        {
            route_completed = connect(p_from_point, (IntPoint) connection_point);
        }
        return route_completed;
    }

    /**
     * Tries to make a trace connection from p_from_point to p_to_point according to the angle restriction.
     * Returns true, if the connection succeeded.
     */
    private boolean connect(Point p_from_point, IntPoint p_to_point)
    {
        Point[] corners = angled_connection(p_from_point, p_to_point);
        boolean connection_succeeded = true;
        for (int i = 1; i < corners.length; ++i)
        {
            Point from_corner = corners[i - 1];
            Point to_corner = corners[i];
            TimeLimit time_limit = new TimeLimit(CHECK_FORCED_TRACE_TIME_LIMIT);
            while (!from_corner.equals(to_corner))
            {
                Point curr_ok_point = board.insert_forced_trace_segment(from_corner,
                        to_corner, pen_half_width_arr[layer], this.layer, net_no_arr,
                        clearance_class, max_shove_trace_recursion_depth,
                        max_shove_via_recursion_depth, max_spring_over_recursion_depth,
                        trace_tidy_width, pull_tight_accuracy, !is_stitch_mode, time_limit);
                if (curr_ok_point == null)
                {
                    // database may be damaged, restore previous situation
                    board.undo(null);
                    return true;
                }
                if (curr_ok_point.equals(from_corner) && this.with_neckdown)
                {
                    curr_ok_point = try_neckdown_at_end(from_corner, to_corner);
                }
                if (curr_ok_point.equals(from_corner))
                {
                    this.prev_corner = from_corner;
                    connection_succeeded = false;
                    break;
                }
                from_corner = curr_ok_point;
            }
        }
        return connection_succeeded;
    }

    /** Calculates the nearest layer of the nearest target item
     * to this.layer.
     */
    public int nearest_target_layer()
    {
        if (nearest_target_item == null)
        {
            return this.layer;
        }
        int result;
        int first_layer = nearest_target_item.first_layer();
        int last_layer = nearest_target_item.last_layer();
        if (this.layer < first_layer)
        {
            result = first_layer;
        }
        else if (this.layer > last_layer)
        {
            result = last_layer;
        }
        else
        {
            result = this.layer;
        }
        return result;
    }

    /**
     * Returns all pins, which can be reached by a pin swap from a srtart or target pin.
     */
    private Set<SwapPinInfo> calculate_swap_pin_infos()
    {
        Set<SwapPinInfo> result = new java.util.TreeSet<SwapPinInfo>();
        if (this.target_set == null)
        {
            return result;
        }
        for (Item curr_item : this.target_set)
        {
            if (curr_item instanceof board.Pin)
            {
                Collection<board.Pin> curr_swapppable_pins = ((board.Pin) curr_item).get_swappable_pins();
                for (board.Pin curr_swappable_pin : curr_swapppable_pins)
                {
                    result.add(new SwapPinInfo(curr_swappable_pin));
                }
            }
        }
        // add the from item, if it is a pin
        ItemSelectionFilter selection_filter = new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.PINS);
        java.util.Collection<Item> picked_items = board.pick_items(this.prev_corner, this.layer, selection_filter);
        for (Item curr_item : picked_items)
        {
            if (curr_item instanceof board.Pin)
            {
                Collection<board.Pin> curr_swapppable_pins = ((board.Pin) curr_item).get_swappable_pins();
                for (board.Pin curr_swappable_pin : curr_swapppable_pins)
                {
                    result.add(new SwapPinInfo(curr_swappable_pin));
                }
            }
        }
        return result;
    }

    /**
     * Hilights the targets and draws the incomplete.
     */
    public void draw(Graphics p_graphics, GraphicsContext p_graphics_context)
    {
        if (this.hilight_shove_failing_obstacle && this.shove_failing_obstacle != null)
        {
            this.shove_failing_obstacle.draw(p_graphics, p_graphics_context, p_graphics_context.get_violations_color(), 1);
        }
        if (target_set == null || net_no_arr.length < 1)
        {
            return;
        }
        Net curr_net = board.rules.nets.get(net_no_arr[0]);
        if (curr_net == null)
        {
            return;
        }
        java.awt.Color highlight_color = p_graphics_context.get_hilight_color();
        double highligt_color_intensity = p_graphics_context.get_hilight_color_intensity();


        // hilight the swapppable pins and their incompletes
        for (SwapPinInfo curr_info : this.swap_pin_infos)
        {
            curr_info.pin.draw(p_graphics, p_graphics_context, highlight_color, 0.3 * highligt_color_intensity);
            if (curr_info.incomplete != null)
            {
                // draw the swap pin incomplete
                FloatPoint[] draw_points = new FloatPoint[2];
                draw_points[0] = curr_info.incomplete.a;
                draw_points[1] = curr_info.incomplete.b;
                java.awt.Color draw_color = p_graphics_context.get_incomplete_color();
                p_graphics_context.draw(draw_points, 1, draw_color, p_graphics, highligt_color_intensity);
            }
        }

        // hilight the target set
        for (Item curr_item : target_set)
        {
            if (!(curr_item instanceof ConductionArea))
            {
                curr_item.draw(p_graphics, p_graphics_context, highlight_color, highligt_color_intensity);
            }
        }
        FloatPoint from_corner = this.prev_corner.to_float();
        if (nearest_target_point != null && prev_corner != null)
        {
            boolean curr_length_matching_ok = true; // used for drawing the incomplete as violation
            double max_trace_length = curr_net.get_class().get_maximum_trace_length();
            double min_trace_length = curr_net.get_class().get_minimum_trace_length();
            double length_matching_color_intensity = p_graphics_context.get_length_matching_area_color_intensity();
            if (max_trace_length > 0 || min_trace_length > 0 && length_matching_color_intensity > 0)
            {

                // draw the length matching area
                double trace_length_add = from_corner.distance(this.prev_corner.to_float());
                // trace_length_add is != 0 only in stitching mode.
                if (max_trace_length <= 0)
                {
                    // max_trace_length not provided. Create an ellipse containing the whole board.
                    max_trace_length = 0.3 * geometry.planar.Limits.CRIT_INT;
                }
                double curr_max_trace_length = max_trace_length - (curr_net.get_trace_length() + trace_length_add);
                double curr_min_trace_length = min_trace_length - (curr_net.get_trace_length() + trace_length_add);
                double incomplete_length = nearest_target_point.distance(from_corner);
                if (incomplete_length < curr_max_trace_length && min_trace_length <= max_trace_length)
                {
                    Vector delta = nearest_target_point.round().difference_by(prev_corner);
                    double rotation = delta.angle_approx();
                    FloatPoint center = from_corner.middle_point(nearest_target_point);
                    double bigger_radius = 0.5 * curr_max_trace_length;
                    // dist_focus_to_center^2 = bigger_radius^2 - smaller_radius^2
                    double smaller_radius = 0.5 * Math.sqrt(curr_max_trace_length * curr_max_trace_length - incomplete_length * incomplete_length);
                    int ellipse_count;
                    if (min_trace_length <= 0 || incomplete_length >= curr_min_trace_length)
                    {
                        ellipse_count = 1;
                    }
                    else
                    {
                        // display an ellipse ring.
                        ellipse_count = 2;
                    }
                    Ellipse[] ellipse_arr = new Ellipse[ellipse_count];
                    ellipse_arr[0] = new Ellipse(center, rotation, bigger_radius, smaller_radius);
                    IntBox bounding_box = new IntBox(prev_corner.to_float().round(), nearest_target_point.round());
                    bounding_box = bounding_box.offset(curr_max_trace_length - incomplete_length);
                    board.join_graphics_update_box(bounding_box);
                    if (ellipse_count == 2)
                    {
                        bigger_radius = 0.5 * curr_min_trace_length;
                        smaller_radius = 0.5 * Math.sqrt(curr_min_trace_length * curr_min_trace_length - incomplete_length * incomplete_length);
                        ellipse_arr[1] = new Ellipse(center, rotation, bigger_radius, smaller_radius);
                    }
                    p_graphics_context.fill_ellipse_arr(ellipse_arr, p_graphics,
                            p_graphics_context.get_length_matching_area_color(), length_matching_color_intensity);
                }
                else
                {
                    curr_length_matching_ok = false;
                }
            }

            // draw the incomplete
            FloatPoint[] draw_points = new FloatPoint[2];
            draw_points[0] = from_corner;
            draw_points[1] = nearest_target_point;
            java.awt.Color draw_color = p_graphics_context.get_incomplete_color();
            double draw_width = Math.min (this.board.communication.get_resolution(Unit.MIL), 100);  // problem with low resolution on Kicad
            if (!curr_length_matching_ok)
            {
                draw_color = p_graphics_context.get_violations_color();
                draw_width *= 3;
            }
            p_graphics_context.draw(draw_points, draw_width, draw_color, p_graphics, highligt_color_intensity);
            if (this.nearest_target_item != null && !this.nearest_target_item.is_on_layer(this.layer))
            {
                // draw a marker to indicate the layer change.
                NetIncompletes.draw_layer_change_marker(draw_points[0], 4 * pen_half_width_arr[0], p_graphics, p_graphics_context);
            }
        }

    }

    /**
     * Makes a connection polygon from p_from_point to p_to_point
     * whose lines fulfill the angle restriction.
     */
    private Point[] angled_connection(Point p_from_point, Point p_to_point)
    {
        IntPoint add_corner = null;
        if (p_from_point instanceof IntPoint && p_to_point instanceof IntPoint)
        {
            AngleRestriction angle_restriction = this.board.rules.get_trace_angle_restriction();
            if (angle_restriction == AngleRestriction.NINETY_DEGREE)
            {
                add_corner = ((IntPoint) p_from_point).ninety_degree_corner((IntPoint) p_to_point, true);
            }
            else if (angle_restriction == AngleRestriction.FORTYFIVE_DEGREE)
            {
                add_corner = ((IntPoint) p_from_point).fortyfive_degree_corner((IntPoint) p_to_point, true);
            }
        }
        int new_corner_count = 2;
        if (add_corner != null)
        {
            ++new_corner_count;
        }
        Point[] result = new Point[new_corner_count];
        result[0] = p_from_point;
        if (add_corner != null)
        {
            result[1] = add_corner;
        }
        result[result.length - 1] = p_to_point;
        return result;
    }

    /**
     * Calculates a list of the center points of DrillItems,
     * end points of traces and areas of ConductionAreas in the target set.
     */
    private void calculate_target_points_and_areas()
    {
        target_points = new LinkedList<TargetPoint>();
        target_traces_and_areas = new LinkedList<Item>();
        if (target_set == null)
        {
            return;
        }
        Iterator<Item> it = target_set.iterator();
        while (it.hasNext())
        {
            Item curr_ob = it.next();
            if (curr_ob instanceof DrillItem)
            {
                Point curr_point = ((DrillItem) curr_ob).get_center();
                target_points.add(new TargetPoint(curr_point.to_float(), curr_ob));
            }
            else if (curr_ob instanceof Trace || curr_ob instanceof ConductionArea)
            {
                target_traces_and_areas.add(curr_ob);
            }
        }
    }

    public Point get_last_corner()
    {
        return prev_corner;
    }

    public boolean is_layer_active(int p_layer)
    {
        if (p_layer < 0 || p_layer >= layer_active.length)
        {
            return false;
        }
        return layer_active[p_layer];
    }

    /**
     * The nearest point is used for drowing the incomplete
     */
    void calc_nearest_target_point(FloatPoint p_from_point)
    {
        double min_dist = Double.MAX_VALUE;
        FloatPoint nearest_point = null;
        Item nearest_item = null;
        for (TargetPoint curr_target_point : target_points)
        {
            double curr_dist = p_from_point.distance(curr_target_point.location);
            if (curr_dist < min_dist)
            {
                min_dist = curr_dist;
                nearest_point = curr_target_point.location;
                nearest_item = curr_target_point.item;
            }
        }
        Iterator<Item> it = target_traces_and_areas.iterator();
        while (it.hasNext())
        {
            Item curr_item = it.next();
            if (curr_item instanceof PolylineTrace)
            {
                PolylineTrace curr_trace = (PolylineTrace) curr_item;
                Polyline curr_polyline = curr_trace.polyline();
                if (curr_polyline.bounding_box().distance(p_from_point) < min_dist)
                {
                    FloatPoint curr_nearest_point = curr_polyline.nearest_point_approx(p_from_point);
                    double curr_dist = p_from_point.distance(curr_nearest_point);
                    if (curr_dist < min_dist)
                    {
                        min_dist = curr_dist;
                        nearest_point = curr_nearest_point;
                        nearest_item = curr_trace;
                    }
                }
            }
            else if (curr_item instanceof ConductionArea && curr_item.tile_shape_count() > 0)
            {
                ConductionArea curr_conduction_area = (ConductionArea) curr_item;
                Area curr_area = curr_conduction_area.get_area();
                if (curr_area.bounding_box().distance(p_from_point) < min_dist)
                {
                    FloatPoint curr_nearest_point = curr_area.nearest_point_approx(p_from_point);
                    double curr_dist = p_from_point.distance(curr_nearest_point);
                    if (curr_dist < min_dist)
                    {
                        min_dist = curr_dist;
                        nearest_point = curr_nearest_point;
                        nearest_item = curr_conduction_area;
                    }
                }
            }
        }
        if (nearest_point == null)
        {
            return; // target set is empty
        }
        nearest_target_point = nearest_point;
        nearest_target_item = nearest_item;
        // join the graphics update box by the nearest item, so that the incomplete
        // is completely displayed.
        board.join_graphics_update_box(nearest_item.bounding_box());
    }

    private void set_shove_failing_obstacle(Item p_item)
    {
        this.shove_failing_obstacle = p_item;
        if (p_item != null)
        {
            this.board.join_graphics_update_box(p_item.bounding_box());
        }
    }

    /**
     * If the routed starts at a pin and the route failed with the normal trace width,
     * another try with the smalllest pin width is done.
     * Returns the ok_point of the try, which is this.prev_point, if the try failed.
     */
    private Point try_neckdown_at_start(IntPoint p_to_corner)
    {
        if (!(this.start_item instanceof board.Pin))
        {
            return this.prev_corner;
        }
        board.Pin start_pin = (board.Pin) this.start_item;
        if (!start_pin.is_on_layer(this.layer))
        {
            return this.prev_corner;
        }
        FloatPoint pin_center = start_pin.get_center().to_float();
        double curr_clearance =
                this.board.rules.clearance_matrix.value(this.clearance_class, start_pin.clearance_class_no(), this.layer);
        double pin_neck_down_distance =
                2 * (0.5 * start_pin.get_max_width(this.layer) + curr_clearance);
        if (pin_center.distance(this.prev_corner.to_float()) >= pin_neck_down_distance)
        {
            return this.prev_corner;
        }

        int neck_down_halfwidth = start_pin.get_trace_neckdown_halfwidth(this.layer);
        if (neck_down_halfwidth >= this.pen_half_width_arr[this.layer])
        {
            return this.prev_corner;
        }

        // check, that the neck_down started inside the pin shape
        if (!this.prev_corner.equals(start_pin.get_center()))
        {
            Item picked_item = this.board.pick_nearest_routing_item(this.prev_corner, this.layer, null);
            if (picked_item instanceof Trace)
            {
                if (((Trace) picked_item).get_half_width() > neck_down_halfwidth)
                {
                    return this.prev_corner;
                }
            }
        }
        TimeLimit time_limit = new TimeLimit(CHECK_FORCED_TRACE_TIME_LIMIT);
        Point ok_point = board.insert_forced_trace_segment(prev_corner,
                p_to_corner, neck_down_halfwidth, layer, net_no_arr, clearance_class, max_shove_trace_recursion_depth,
                max_shove_via_recursion_depth, max_spring_over_recursion_depth, trace_tidy_width,
                pull_tight_accuracy, !is_stitch_mode, time_limit);
        return ok_point;
    }

    /**
     * If the routed ends at a pin and the route failed with the normal trace width,
     * another try with the smalllest pin width is done.
     * Returns the ok_point of the try, which is p_from_corner, if the try failed.
     */
    private Point try_neckdown_at_end(Point p_from_corner, Point p_to_corner)
    {
        if (!(this.nearest_target_item instanceof board.Pin))
        {
            return p_from_corner;
        }
        board.Pin target_pin = (board.Pin) this.nearest_target_item;
        if (!target_pin.is_on_layer(this.layer))
        {
            return p_from_corner;
        }
        FloatPoint pin_center = target_pin.get_center().to_float();
        double curr_clearance =
                this.board.rules.clearance_matrix.value(this.clearance_class, target_pin.clearance_class_no(), this.layer);
        double pin_neck_down_distance =
                2 * (0.5 * target_pin.get_max_width(this.layer) + curr_clearance);
        if (pin_center.distance(p_from_corner.to_float()) >= pin_neck_down_distance)
        {
            return p_from_corner;
        }
        int neck_down_halfwidth = target_pin.get_trace_neckdown_halfwidth(this.layer);
        if (neck_down_halfwidth >= this.pen_half_width_arr[this.layer])
        {
            return p_from_corner;
        }
        TimeLimit time_limit = new TimeLimit(CHECK_FORCED_TRACE_TIME_LIMIT);
        Point ok_point = board.insert_forced_trace_segment(p_from_corner,
                p_to_corner, neck_down_halfwidth, layer, net_no_arr, clearance_class,
                max_shove_trace_recursion_depth, max_shove_via_recursion_depth,
                max_spring_over_recursion_depth, trace_tidy_width,
                pull_tight_accuracy, !is_stitch_mode, time_limit);
        return ok_point;
    }
    /** The net numbers used for routing */
    final int[] net_no_arr;
    private Point prev_corner;
    private int layer;
    private final Item start_item;
    private final Set<Item> target_set;
    /** Pins, which can be reached by a pin swap by a target pin. */
    private final Set<SwapPinInfo> swap_pin_infos;
    private Collection<TargetPoint> target_points; // from drill_items
    private Collection<Item> target_traces_and_areas; // from traces and conduction areas
    private FloatPoint nearest_target_point;
    private Item nearest_target_item;
    private final int[] pen_half_width_arr;
    private final boolean[] layer_active;
    private final int clearance_class;
    private final ViaRule via_rule;
    private final int max_shove_trace_recursion_depth;
    private final int max_shove_via_recursion_depth;
    private final int max_spring_over_recursion_depth;
    private final int trace_tidy_width;
    private final int pull_tight_accuracy;
    private final RoutingBoard board;
    private final boolean is_stitch_mode;
    private final boolean with_neckdown;
    private final boolean via_snap_to_smd_center;
    private final boolean hilight_shove_failing_obstacle;
    private final int pull_tight_time_limit;
    private Item shove_failing_obstacle = null;
    /** The time limit in milliseconds for the pull tight algorithm */
    private static final int CHECK_FORCED_TRACE_TIME_LIMIT = 3000;
    /** The time limit in milliseconds for the pull tight algorithm */
    private static final int PULL_TIGHT_TIME_LIMIT = 2000;

    private static class TargetPoint
    {

        TargetPoint(FloatPoint p_location, Item p_item)
        {
            location = p_location;
            item = p_item;
        }
        final FloatPoint location;
        final Item item;
    }

    private class SwapPinInfo implements Comparable<SwapPinInfo>
    {

        SwapPinInfo(board.Pin p_pin)
        {
            pin = p_pin;
            incomplete = null;
            if (p_pin.is_connected() || p_pin.net_count() != 1)
            {
                return;
            }
            // calculate the incomplete of p_pin
            FloatPoint pin_center = p_pin.get_center().to_float();
            double min_dist = Double.MAX_VALUE;
            FloatPoint nearest_point = null;
            Collection<Item> net_items = board.get_connectable_items(p_pin.get_net_no(0));
            for (Item curr_item : net_items)
            {
                if (curr_item == this.pin || !(curr_item instanceof DrillItem))
                {
                    continue;
                }
                FloatPoint curr_point = ((DrillItem) curr_item).get_center().to_float();
                double curr_dist = pin_center.distance_square(curr_point);
                if (curr_dist < min_dist)
                {
                    min_dist = curr_dist;
                    nearest_point = curr_point;
                }
            }
            if (nearest_point != null)
            {
                incomplete = new geometry.planar.FloatLine(pin_center, nearest_point);
            }
        }

        public int compareTo(SwapPinInfo p_other)
        {
            return this.pin.compareTo(p_other.pin);
        }
        final board.Pin pin;
        geometry.planar.FloatLine incomplete;
    }
}