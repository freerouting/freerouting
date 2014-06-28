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

import datastructures.TimeLimit;

import geometry.planar.ConvexShape;
import geometry.planar.Direction;
import geometry.planar.FloatPoint;
import geometry.planar.IntBox;
import geometry.planar.Line;
import geometry.planar.Point;
import geometry.planar.IntPoint;
import geometry.planar.Vector;
import geometry.planar.Polyline;
import geometry.planar.TileShape;
import geometry.planar.LineSegment;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * Contains internal auxiliary functions of class RoutingBoard
 * for shoving traces
 *
 * @author Alfons Wirtz
 */
public class ShoveTraceAlgo
{

    public ShoveTraceAlgo(RoutingBoard p_board)
    {
        board = p_board;
    }

    /**
     * Checks if a shove with the input parameters is possible without clearance violations
     * p_dir is used internally to prevent the check from bouncing back.
     * Returns false, if the shove failed.
     */
    public boolean check(TileShape p_trace_shape, CalcFromSide p_from_side,
            Direction p_dir, int p_layer, int[] p_net_no_arr,
            int p_cl_type, int p_max_recursion_depth, int p_max_via_recursion_depth,
            int p_max_spring_over_recursion_depth, TimeLimit p_time_limit)
    {
        if (p_time_limit != null && p_time_limit.limit_exceeded())
        {
            return false;
        }

        if (p_trace_shape.is_empty())
        {
            System.out.println("ShoveTraceAux.check: p_trace_shape is empty");
            return true;
        }
        if (!p_trace_shape.is_contained_in(board.get_bounding_box()))
        {
            this.board.set_shove_failing_obstacle(board.get_outline());
            return false;
        }
        ShapeTraceEntries shape_entries =
                new ShapeTraceEntries(p_trace_shape, p_layer, p_net_no_arr, p_cl_type, p_from_side, board);
        ShapeSearchTree search_tree = this.board.search_tree_manager.get_default_tree();
        Collection<Item> obstacles =
                search_tree.overlapping_items_with_clearance(p_trace_shape, p_layer, new int[0], p_cl_type);
        obstacles.removeAll(get_ignore_items_at_tie_pins(p_trace_shape, p_layer, p_net_no_arr));
        boolean obstacles_shovable = shape_entries.store_items(obstacles, false, true);
        if (!obstacles_shovable)
        {
            this.board.set_shove_failing_obstacle(shape_entries.get_found_obstacle());
            return false;
        }
        int trace_piece_count = shape_entries.substitute_trace_count();

        if (shape_entries.stack_depth() > 1)
        {
            this.board.set_shove_failing_obstacle(shape_entries.get_found_obstacle());
            return false;
        }
        double shape_radius = 0.5 * p_trace_shape.bounding_box().min_width();

        // check, if the obstacle vias can be shoved


        for (Via curr_shove_via : shape_entries.shove_via_list)
        {
            if (curr_shove_via.shares_net_no(p_net_no_arr))
            {
                continue;
            }
            if (p_max_via_recursion_depth <= 0)
            {
                this.board.set_shove_failing_obstacle(curr_shove_via);
                return false;
            }
            FloatPoint curr_shove_via_center = curr_shove_via.get_center().to_float();
            IntPoint[] try_via_centers =
                    MoveDrillItemAlgo.try_shove_via_points(p_trace_shape, p_layer, curr_shove_via, p_cl_type,
                    true, board);

            double max_dist = 0.5 * curr_shove_via.get_shape_on_layer(p_layer).bounding_box().max_width() + shape_radius;
            double max_dist_square = max_dist * max_dist;
            boolean shove_via_ok = false;
            for (int i = 0; i < try_via_centers.length; ++i)
            {
                if (i == 0 || curr_shove_via_center.distance_square(try_via_centers[i].to_float()) <= max_dist_square)
                {
                    Vector delta = try_via_centers[i].difference_by(curr_shove_via.get_center());
                    Collection<Item> ignore_items = new java.util.LinkedList<Item>();
                    if (MoveDrillItemAlgo.check(curr_shove_via, delta, p_max_recursion_depth,
                            p_max_via_recursion_depth - 1, ignore_items, this.board, p_time_limit))
                    {
                        shove_via_ok = true;
                        break;
                    }
                }
            }
            if (!shove_via_ok)
            {
                return false;
            }
        }

        if (trace_piece_count == 0)
        {
            return true;
        }
        if (p_max_recursion_depth <= 0)
        {
            this.board.set_shove_failing_obstacle(shape_entries.get_found_obstacle());
            return false;
        }

        boolean is_orthogonal_mode = p_trace_shape instanceof IntBox;
        for (;;)
        {
            PolylineTrace curr_substitute_trace =
                    shape_entries.next_substitute_trace_piece();
            if (curr_substitute_trace == null)
            {
                break;
            }
            if (p_max_spring_over_recursion_depth > 0)
            {
                Polyline new_polyline = spring_over(curr_substitute_trace.polyline(),
                        curr_substitute_trace.get_compensated_half_width(search_tree), p_layer, curr_substitute_trace.net_no_arr,
                        curr_substitute_trace.clearance_class_no(), false, p_max_spring_over_recursion_depth, null);
                if (new_polyline == null)
                {
                    // spring_over did not work
                    return false;
                }
                if (new_polyline != curr_substitute_trace.polyline())
                {
                    // spring_over changed something
                    --p_max_spring_over_recursion_depth;
                    curr_substitute_trace.change(new_polyline);
                }
            }
            for (int i = 0; i < curr_substitute_trace.tile_shape_count(); ++i)
            {
                Direction curr_dir = curr_substitute_trace.polyline().arr[i + 1].direction();
                boolean is_in_front = p_dir == null || p_dir.equals(curr_dir);
                if (is_in_front)
                {
                    CalcShapeAndFromSide curr =
                            new CalcShapeAndFromSide(curr_substitute_trace, i, is_orthogonal_mode, true);
                    if (!this.check(curr.shape, curr.from_side, curr_dir, p_layer, curr_substitute_trace.net_no_arr,
                            curr_substitute_trace.clearance_class_no(),
                            p_max_recursion_depth - 1, p_max_via_recursion_depth,
                            p_max_spring_over_recursion_depth, p_time_limit))
                    {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Checks if a shove with the input parameters is possible without clearance violations
     * The result is the maximum lenght of a trace from the start of the line segment to the end of
     * the line segment, for wich the algoritm succeedes.
     * If the algorithm succeedes completely, the result will be equal to Integer.MAX_VALUE.
     */
    public static double check(RoutingBoard p_board, LineSegment p_line_segment, boolean p_shove_to_the_left, int p_layer, int[] p_net_no_arr, int p_trace_half_width,
            int p_cl_type, int p_max_recursion_depth, int p_max_via_recursion_depth)
    {
        ShapeSearchTree search_tree = p_board.search_tree_manager.get_default_tree();
        if (search_tree.is_clearance_compensation_used())
        {
            p_trace_half_width += search_tree.clearance_compensation_value(p_cl_type, p_layer);
        }
        TileShape[] trace_shapes = p_line_segment.to_polyline().offset_shapes(p_trace_half_width);
        if (trace_shapes.length != 1)
        {
            System.out.println("ShoveTraceAlgo.check: trace_shape count 1 expected");
            return 0;
        }

        TileShape trace_shape = trace_shapes[0];
        if (trace_shape.is_empty())
        {
            System.out.println("ShoveTraceAlgo.check: trace_shape is empty");
            return 0;
        }
        if (!trace_shape.is_contained_in(p_board.get_bounding_box()))
        {
            return 0;
        }
        CalcFromSide from_side = new CalcFromSide(p_line_segment, trace_shape, p_shove_to_the_left);
        ShapeTraceEntries shape_entries =
                new ShapeTraceEntries(trace_shape, p_layer, p_net_no_arr, p_cl_type, from_side, p_board);
        Collection<Item> obstacles =
                search_tree.overlapping_items_with_clearance(trace_shape, p_layer, new int[0], p_cl_type);
        boolean obstacles_shovable = shape_entries.store_items(obstacles, false, true);
        if (!obstacles_shovable || shape_entries.trace_tails_in_shape())
        {
            return 0;
        }
        int trace_piece_count = shape_entries.substitute_trace_count();

        if (shape_entries.stack_depth() > 1)
        {
            return 0;
        }

        FloatPoint start_corner_appprox = p_line_segment.start_point_approx();
        FloatPoint end_corner_appprox = p_line_segment.end_point_approx();
        double segment_length = end_corner_appprox.distance(start_corner_appprox);

        rules.ClearanceMatrix cl_matrix = p_board.rules.clearance_matrix;

        double result = Integer.MAX_VALUE;

        // check, if the obstacle vias can be shoved

        for (Via curr_shove_via : shape_entries.shove_via_list)
        {
            if (curr_shove_via.shares_net_no(p_net_no_arr))
            {
                continue;
            }
            boolean shove_via_ok = false;
            if (p_max_via_recursion_depth > 0)
            {

                IntPoint[] new_via_center =
                        MoveDrillItemAlgo.try_shove_via_points(trace_shape, p_layer, curr_shove_via, p_cl_type,
                        false, p_board);

                if (new_via_center.length <= 0)
                {
                    return 0;
                }
                Vector delta = new_via_center[0].difference_by(curr_shove_via.get_center());
                Collection<Item> ignore_items = new java.util.LinkedList<Item>();
                shove_via_ok = MoveDrillItemAlgo.check(curr_shove_via, delta, p_max_recursion_depth,
                        p_max_via_recursion_depth - 1, ignore_items, p_board, null);
            }

            if (!shove_via_ok)
            {
                FloatPoint via_center_appprox = curr_shove_via.get_center().to_float();
                double projection = start_corner_appprox.scalar_product(end_corner_appprox, via_center_appprox);
                projection /= segment_length;
                IntBox via_box = curr_shove_via.get_tree_shape_on_layer(search_tree, p_layer).bounding_box();
                double via_radius = 0.5 * via_box.max_width();
                double curr_ok_lenght = projection - via_radius - p_trace_half_width;
                if (!search_tree.is_clearance_compensation_used())
                {
                    curr_ok_lenght -= cl_matrix.value(p_cl_type, curr_shove_via.clearance_class_no(), p_layer);
                }
                if (curr_ok_lenght <= 0)
                {
                    return 0;
                }
                result = Math.min(result, curr_ok_lenght);
            }
        }
        if (trace_piece_count == 0)
        {
            return result;
        }
        if (p_max_recursion_depth <= 0)
        {
            return 0;
        }

        Direction line_direction = p_line_segment.get_line().direction();
        for (;;)
        {
            PolylineTrace curr_substitute_trace =
                    shape_entries.next_substitute_trace_piece();
            if (curr_substitute_trace == null)
            {
                break;
            }
            for (int i = 0; i < curr_substitute_trace.tile_shape_count(); ++i)
            {
                LineSegment curr_line_segment = new LineSegment(curr_substitute_trace.polyline(), i + 1);
                if (p_shove_to_the_left)
                {
                    // swap the line segmment to get the corredct shove length
                    // in case it is smmaller than the length of the whole line segmment.
                    curr_line_segment = curr_line_segment.opposite();
                }
                boolean is_in_front = curr_line_segment.get_line().direction().equals(line_direction);
                if (is_in_front)
                {
                    double shove_ok_length = check(p_board, curr_line_segment, p_shove_to_the_left, p_layer, curr_substitute_trace.net_no_arr,
                            curr_substitute_trace.get_half_width(), curr_substitute_trace.clearance_class_no(),
                            p_max_recursion_depth - 1, p_max_via_recursion_depth);
                    if (shove_ok_length < Integer.MAX_VALUE)
                    {
                        if (shove_ok_length <= 0)
                        {
                            return 0;
                        }
                        double projection =
                                Math.min(start_corner_appprox.scalar_product(end_corner_appprox, curr_line_segment.start_point_approx()),
                                start_corner_appprox.scalar_product(end_corner_appprox, curr_line_segment.end_point_approx()));
                        projection /= segment_length;
                        double curr_ok_length = shove_ok_length + projection - p_trace_half_width - curr_substitute_trace.get_half_width();
                        if (search_tree.is_clearance_compensation_used())
                        {
                            curr_ok_length -= search_tree.clearance_compensation_value(curr_substitute_trace.clearance_class_no(), p_layer);
                        }
                        else
                        {
                            curr_ok_length -= cl_matrix.value(p_cl_type, curr_substitute_trace.clearance_class_no(), p_layer);
                        }
                        if (curr_ok_length <= 0)
                        {
                            return 0;
                        }
                        result = Math.min(curr_ok_length, result);
                    }
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Puts in a trace segment with the input parameters and
     * shoves obstacles out of the way. If the shove does not work,
     * the database may be damaged. To prevent this, call check first.
     */
    public boolean insert(TileShape p_trace_shape, CalcFromSide p_from_side, int p_layer, int[] p_net_no_arr,
            int p_cl_type, Collection<Item> p_ignore_items,
            int p_max_recursion_depth, int p_max_via_recursion_depth, int p_max_spring_over_recursion_depth)
    {
        if (p_trace_shape.is_empty())
        {
            System.out.println("ShoveTraceAux.insert: p_trace_shape is empty");
            return true;
        }
        if (!p_trace_shape.is_contained_in(board.get_bounding_box()))
        {
            this.board.set_shove_failing_obstacle(board.get_outline());
            return false;
        }
        if (!MoveDrillItemAlgo.shove_vias(p_trace_shape, p_from_side, p_layer, p_net_no_arr, p_cl_type,
                p_ignore_items, p_max_recursion_depth, p_max_via_recursion_depth, true, this.board))
        {
            return false;
        }
        ShapeTraceEntries shape_entries =
                new ShapeTraceEntries(p_trace_shape, p_layer, p_net_no_arr, p_cl_type, p_from_side, board);
        ShapeSearchTree search_tree = this.board.search_tree_manager.get_default_tree();
        Collection<Item> obstacles =
                search_tree.overlapping_items_with_clearance(p_trace_shape, p_layer, new int[0], p_cl_type);
        obstacles.removeAll(get_ignore_items_at_tie_pins(p_trace_shape, p_layer, p_net_no_arr));
        boolean obstacles_shovable = shape_entries.store_items(obstacles, false, true);
        if (!shape_entries.shove_via_list.isEmpty())
        {
            obstacles_shovable = false;
            this.board.set_shove_failing_obstacle(shape_entries.shove_via_list.iterator().next());
            return false;
        }
        if (!obstacles_shovable)
        {
            this.board.set_shove_failing_obstacle(shape_entries.get_found_obstacle());
            return false;
        }
        int trace_piece_count = shape_entries.substitute_trace_count();
        if (trace_piece_count == 0)
        {
            return true;
        }
        if (p_max_recursion_depth <= 0)
        {
            this.board.set_shove_failing_obstacle(shape_entries.get_found_obstacle());
            return false;
        }
        boolean tails_exist_before = board.contains_trace_tails(obstacles, p_net_no_arr);
        shape_entries.cutout_traces(obstacles);
        boolean is_orthogonal_mode = p_trace_shape instanceof IntBox;
        for (;;)
        {
            PolylineTrace curr_substitute_trace =
                    shape_entries.next_substitute_trace_piece();
            if (curr_substitute_trace == null)
            {
                break;
            }
            if (curr_substitute_trace.first_corner().equals(curr_substitute_trace.last_corner()))
            {
                continue;
            }
            if (p_max_spring_over_recursion_depth > 0)
            {
                Polyline new_polyline = spring_over(curr_substitute_trace.polyline(),
                        curr_substitute_trace.get_compensated_half_width(search_tree), p_layer, curr_substitute_trace.net_no_arr,
                        curr_substitute_trace.clearance_class_no(), false, p_max_spring_over_recursion_depth, null);

                if (new_polyline == null)
                {
                    // spring_over did not work
                    return false;
                }
                if (new_polyline != curr_substitute_trace.polyline())
                {
                    // spring_over changed something
                    --p_max_spring_over_recursion_depth;
                    curr_substitute_trace.change(new_polyline);
                }
            }
            int[] curr_net_no_arr = curr_substitute_trace.net_no_arr;
            for (int i = 0; i < curr_substitute_trace.tile_shape_count(); ++i)
            {
                CalcShapeAndFromSide curr =
                        new CalcShapeAndFromSide(curr_substitute_trace, i, is_orthogonal_mode, false);
                if (!this.insert(curr.shape, curr.from_side, p_layer, curr_net_no_arr, curr_substitute_trace.clearance_class_no(),
                        p_ignore_items, p_max_recursion_depth - 1, p_max_via_recursion_depth, p_max_spring_over_recursion_depth))
                {
                    return false;
                }
            }
            for (int i = 0; i < curr_substitute_trace.corner_count(); ++i)
            {
                board.join_changed_area(
                        curr_substitute_trace.polyline().corner_approx(i), p_layer);
            }
            Point[] end_corners = null;
            if (!tails_exist_before)
            {
                end_corners = new Point[2];
                end_corners[0] = curr_substitute_trace.first_corner();
                end_corners[1] = curr_substitute_trace.last_corner();
            }
            board.insert_item(curr_substitute_trace);
            curr_substitute_trace.normalize(board.changed_area.get_area(p_layer));
            if (!tails_exist_before)
            {
                for (int i = 0; i < 2; ++i)
                {
                    Trace tail = board.get_trace_tail(end_corners[i], p_layer, curr_net_no_arr);
                    if (tail != null)
                    {
                        board.remove_items(tail.get_connection_items(Item.StopConnectionOption.VIA), false);
                        for (int curr_net_no : curr_net_no_arr)
                        {
                            board.combine_traces(curr_net_no);
                        }
                    }
                }
            }
        }
        return true;
    }

    Collection<Item> get_ignore_items_at_tie_pins(TileShape p_trace_shape, int p_layer, int[] p_net_no_arr)
    {
        Collection<SearchTreeObject> overlaps = this.board.overlapping_objects(p_trace_shape, p_layer);
        Set<Item> result = new java.util.TreeSet<Item>();
        for (SearchTreeObject curr_object : overlaps)
        {
            if (curr_object instanceof Pin)
            {
                Pin curr_pin = (Pin) curr_object;
                if (curr_pin.shares_net_no(p_net_no_arr))
                {
                    result.addAll(curr_pin.get_all_contacts(p_layer));
                }
            }
        }
        return result;
    }

    /**
     * Checks, if there are obstacle in the way of p_polyline and tries
     * to wrap the polyline trace around these obstacles in counterclock sense.
     * Returns null, if that is not possible.
     * Returns p_polyline, if there were no obstacles
     * If p_contact_pins != null, all pins not contained in p_contact_pins are
     * regarded as obstacles, even if they are of the own net.
     */
    private Polyline spring_over(Polyline p_polyline, int p_half_width,
            int p_layer, int[] p_net_no_arr, int p_cl_type, boolean p_over_connected_pins,
            int p_recursion_depth, Set<Pin> p_contact_pins)
    {
        Item found_obstacle = null;
        IntBox found_obstacle_bounding_box = null;
        ShapeSearchTree search_tree = this.board.search_tree_manager.get_default_tree();
        int[] check_net_no_arr;
        if (p_contact_pins == null)
        {
            check_net_no_arr = p_net_no_arr;
        }
        else
        {
            check_net_no_arr = new int[0];
        }
        for (int i = 0; i < p_polyline.arr.length - 2; ++i)
        {
            TileShape curr_shape = p_polyline.offset_shape(p_half_width, i);
            Collection<Item> obstacles = search_tree.overlapping_items_with_clearance(curr_shape, p_layer, check_net_no_arr, p_cl_type);
            Iterator<Item> it = obstacles.iterator();
            while (it.hasNext())
            {
                Item curr_item = it.next();
                boolean is_obstacle;
                if (curr_item.shares_net_no(p_net_no_arr))
                {
                    // to avoid acid traps
                    is_obstacle = curr_item instanceof Pin && p_contact_pins != null &&
                            !p_contact_pins.contains(curr_item);
                }
                else if (curr_item instanceof ConductionArea)
                {
                    is_obstacle = ((ConductionArea) curr_item).get_is_obstacle();
                }
                else if (curr_item instanceof ViaObstacleArea || curr_item instanceof ComponentObstacleArea)
                {
                    is_obstacle = false;
                }
                else if (curr_item instanceof PolylineTrace)
                {
                    if (curr_item.is_shove_fixed())
                    {
                        is_obstacle = true;
                        if (curr_item instanceof PolylineTrace)
                        {
                            // check for a shove fixed trace exit stub, which has to be be ignored at a tie pin.  
                            Collection<Item> curr_contacts = curr_item.get_normal_contacts();
                            for (Item curr_contact : curr_contacts)
                            {
                                if (curr_contact.shares_net_no(p_net_no_arr))
                                {
                                    is_obstacle = false;
                                }
                            }
                        }
                    }
                    else
                    {
                        // a unfixed trace can be pushed aside eventually
                        is_obstacle = false;
                    }
                }
                else
                {
                    // a unfixed via can be pushed aside eventually
                    is_obstacle = !curr_item.is_route();
                }

                if (is_obstacle)
                {
                    if (found_obstacle == null)
                    {
                        found_obstacle = curr_item;
                        found_obstacle_bounding_box = curr_item.bounding_box();
                    }
                    else if (found_obstacle != curr_item)
                    {
                        // check, if 1 obstacle is contained in the other obstacle and take
                        // the bigger obstacle in this case.
                        // That may happen in case of fixed vias inside of pins.
                        IntBox curr_item_bounding_box = curr_item.bounding_box();
                        if (found_obstacle_bounding_box.intersects(curr_item_bounding_box))
                        {
                            if (curr_item_bounding_box.contains(found_obstacle_bounding_box))
                            {
                                found_obstacle = curr_item;
                                found_obstacle_bounding_box = curr_item_bounding_box;
                            }
                            else if (!found_obstacle_bounding_box.contains(curr_item_bounding_box))
                            {
                                return null;
                            }
                        }
                    }
                }
            }
            if (found_obstacle != null)
            {
                break;
            }
        }
        if (found_obstacle == null)
        {
            // no obstacle in the way, nothing to do
            return p_polyline;
        }










        if (p_recursion_depth <= 0 || found_obstacle instanceof BoardOutline || (found_obstacle instanceof Trace && !found_obstacle.is_shove_fixed()))
        {
            this.board.set_shove_failing_obstacle(found_obstacle);
            return null;
        }
        boolean try_spring_over = true;
        if (!p_over_connected_pins)
        {
            // Check if the obstacle has a trace contact on p_layer
            Collection<Item> contacts_on_layer = found_obstacle.get_all_contacts(p_layer);
            for (Item curr_contact : contacts_on_layer)
            {
                if (curr_contact instanceof Trace)
                {
                    try_spring_over = false;
                    break;
                }
            }
        }
        ConvexShape obstacle_shape = null;
        if (try_spring_over)
        {
            if (found_obstacle instanceof ObstacleArea || found_obstacle instanceof Trace)
            {
                if (found_obstacle.tree_shape_count(search_tree) == 1)
                {
                    obstacle_shape = found_obstacle.get_tree_shape(search_tree, 0);
                }
                else
                {
                    try_spring_over = false;
                }
            }
            else if (found_obstacle instanceof DrillItem)
            {
                DrillItem found_drill_item = (DrillItem) found_obstacle;
                obstacle_shape = (found_drill_item.get_tree_shape_on_layer(search_tree, p_layer));
            }
        }
        if (!try_spring_over)
        {
            this.board.set_shove_failing_obstacle(found_obstacle);
            return null;
        }
        TileShape offset_shape;
        if (search_tree.is_clearance_compensation_used())
        {
            int offset = p_half_width + 1;
            offset_shape = (TileShape) obstacle_shape.enlarge(offset);
        }
        else
        {
            // enlarge the shape in 2 steps  for symmetry reasons
            int offset = p_half_width + 1;
            double half_cl_offset = 0.5 * board.clearance_value(found_obstacle.clearance_class_no(), p_cl_type, p_layer);
            offset_shape = (TileShape) obstacle_shape.enlarge(offset + half_cl_offset);
            offset_shape = (TileShape) offset_shape.enlarge(half_cl_offset);
        }
        if (this.board.rules.get_trace_angle_restriction() == AngleRestriction.NINETY_DEGREE)
        {
            offset_shape = offset_shape.bounding_box();
        }
        else if (this.board.rules.get_trace_angle_restriction() == AngleRestriction.FORTYFIVE_DEGREE)
        {
            offset_shape = offset_shape.bounding_octagon();
        }





        if (offset_shape.contains_inside(p_polyline.first_corner()) || offset_shape.contains_inside(p_polyline.last_corner()))
        {
            // can happen with clearance compensation off because of asymmetry in calculations with the offset shapes
            this.board.set_shove_failing_obstacle(found_obstacle);
            return null;
        }
        int[][] entries = offset_shape.entrance_points(p_polyline);
        if (entries.length == 0)
        {
            return p_polyline; // no obstacle
        }

        if (entries.length <
                2)
        {
            this.board.set_shove_failing_obstacle(found_obstacle);
            return null;
        }
        Polyline[] pieces = offset_shape.cutout(p_polyline);    // build a circuit around the offset_shape in counter clock sense
        // from the first intersection point to the second intersection point
        int first_intersection_side_no = entries[0][1];
        int last_intersection_side_no = entries[entries.length - 1][1];
        int first_intersection_line_no = entries[0][0];
        int last_intersection_line_no = entries[entries.length - 1][0];
        int side_diff = last_intersection_side_no - first_intersection_side_no;
        if (side_diff <
                0)
        {
            side_diff += offset_shape.border_line_count();
        }
        else if (side_diff == 0)
        {
            FloatPoint compare_corner = offset_shape.corner_approx(first_intersection_side_no);
            FloatPoint first_intersection = p_polyline.arr[first_intersection_line_no].intersection_approx(offset_shape.border_line(first_intersection_side_no));
            FloatPoint second_intersection = p_polyline.arr[last_intersection_line_no].intersection_approx(offset_shape.border_line(last_intersection_side_no));
            if (compare_corner.distance(second_intersection) < compare_corner.distance(first_intersection))
            {
                side_diff += offset_shape.border_line_count();
            }
        }
        Line[] substitute_lines = new Line[side_diff + 3];
        substitute_lines[0] = p_polyline.arr[first_intersection_line_no];
        int curr_edge_line_no = first_intersection_side_no;

        for (int i = 1;
                i <= side_diff + 1;
                ++i)
        {
            substitute_lines[i] = offset_shape.border_line(curr_edge_line_no);
            if (curr_edge_line_no == offset_shape.border_line_count() - 1)
            {
                curr_edge_line_no = 0;
            }
            else
            {
                ++curr_edge_line_no;
            }
        }
        substitute_lines[side_diff + 2] = p_polyline.arr[last_intersection_line_no];
        Polyline substitute_polyline = new Polyline(substitute_lines);
        Polyline result = substitute_polyline;





        if (pieces.length > 0)
        {
            result = pieces[0].combine(substitute_polyline);
        }
        if (pieces.length > 1)
        {
            result = result.combine(pieces[1]);
        }
        return spring_over(result, p_half_width, p_layer, p_net_no_arr, p_cl_type, p_over_connected_pins,
                p_recursion_depth - 1, p_contact_pins);
    }

    /**
     * Checks, if there are obstacle in the way of p_polyline and tries
     * to wrap the polyline trace around these obstacles.
     * Returns null, if that is not possible.
     * Returns p_polyline, if there were no obstacles
     * This function looks contrary to the previous function for the shortest
     * way around the obstaccles.
     * If p_contact_pins != null, all pins not contained in p_contact_pins are
     * regarded as obstacles, even if they are of the own net.
     */
    Polyline spring_over_obstacles(
            Polyline p_polyline, int p_half_width, int p_layer, int[] p_net_no_arr,
            int p_cl_type, Set<Pin> p_contact_pins)
    {
        final int c_max_spring_over_recursion_depth = 20;
        Polyline counter_clock_wise_result = spring_over(p_polyline, p_half_width, p_layer, p_net_no_arr, p_cl_type,
                true, c_max_spring_over_recursion_depth, p_contact_pins);
        if (counter_clock_wise_result == p_polyline)
        {
            return p_polyline; // no obstacle
        }

        Polyline clock_wise_result = spring_over(p_polyline.reverse(), p_half_width, p_layer, p_net_no_arr, p_cl_type,
                true, c_max_spring_over_recursion_depth, p_contact_pins);
        Polyline result = null;
        if (clock_wise_result != null && counter_clock_wise_result != null)
        {
            if (clock_wise_result.length_approx() <= counter_clock_wise_result.length_approx())
            {
                result = clock_wise_result.reverse();
            }
            else
            {
                result = counter_clock_wise_result;
            }

        }
        else if (clock_wise_result != null)
        {
            result = clock_wise_result.reverse();
        }
        else if (counter_clock_wise_result != null)
        {
            result = counter_clock_wise_result;
        }

        return result;
    }
    private final RoutingBoard board;
}