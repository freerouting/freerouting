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

import geometry.planar.FloatPoint;
import geometry.planar.IntBox;
import geometry.planar.IntOctagon;
import geometry.planar.IntPoint;
import geometry.planar.LineSegment;
import geometry.planar.Point;
import geometry.planar.Polyline;
import geometry.planar.PolylineShape;
import geometry.planar.TileShape;
import geometry.planar.Vector;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import datastructures.UndoableObjects;
import datastructures.Stoppable;
import datastructures.TimeLimit;
import datastructures.ShapeTree.TreeEntry;

import rules.ViaInfo;
import rules.BoardRules;

import autoroute.AutorouteControl;
import autoroute.AutorouteEngine;
import autoroute.AutorouteControl.ExpansionCostFactor;
import autoroute.CompleteFreeSpaceExpansionRoom;

/**
 *
 * Contains higher level functions of a board
 *
 * @author Alfons Wirtz
 */
public class RoutingBoard extends BasicBoard implements java.io.Serializable
{

    /**
     * Creates a new instance of a routing Board with surrrounding box
     * p_bounding_box
     * Rules contains the restrictions to obey when inserting items.
     * Among other things it may contain a clearance matrix.
     */
    public RoutingBoard(IntBox p_bounding_box, LayerStructure p_layer_structure, PolylineShape[] p_outline_shapes,
            int p_outline_cl_class_no, BoardRules p_rules, Communication p_board_communication, TestLevel p_test_level)
    {
        super(p_bounding_box, p_layer_structure, p_outline_shapes, p_outline_cl_class_no,
                p_rules, p_board_communication, p_test_level);
    }

    /**
     * Maintains the autorouter database after p_item is inserted, changed, or deleted.
     */
    public void additional_update_after_change(Item p_item)
    {
        if (p_item == null)
        {
            return;
        }
        if (this.autoroute_engine == null || !this.autoroute_engine.maintain_database)
        {
            return;
        }
        // Invalidate the free space expansion rooms touching a shape of p_item.
        int shape_count = p_item.tree_shape_count(this.autoroute_engine.autoroute_search_tree);
        for (int i = 0; i < shape_count; ++i)
        {
            TileShape curr_shape = p_item.get_tree_shape(this.autoroute_engine.autoroute_search_tree, i);
            this.autoroute_engine.invalidate_drill_pages(curr_shape);
            int curr_layer = p_item.shape_layer(i);
            Collection<SearchTreeObject> overlaps =
                    this.autoroute_engine.autoroute_search_tree.overlapping_objects(curr_shape, curr_layer);
            for (SearchTreeObject curr_object : overlaps)
            {
                if (curr_object instanceof CompleteFreeSpaceExpansionRoom)
                {
                    this.autoroute_engine.remove_complete_expansion_room((CompleteFreeSpaceExpansionRoom) curr_object);
                }
            }
        }
        p_item.clear_autoroute_info();
    }

    /**
     * Removes the items in p_item_list  and pulls the nearby rubbertraces tight.
     * Returns false, if some items could not be removed, because they were fixed.
     */
    public boolean remove_items_and_pull_tight(Collection<Item> p_item_list, int p_tidy_width,
            int p_pull_tight_accuracy, boolean p_with_delete_fixed)
    {
        boolean result = true;
        IntOctagon tidy_region;
        boolean calculate_tidy_region;
        if (p_tidy_width < Integer.MAX_VALUE)
        {
            tidy_region = IntOctagon.EMPTY;
            calculate_tidy_region = (p_tidy_width > 0);
        }
        else
        {
            tidy_region = null;
            calculate_tidy_region = false;
        }
        start_marking_changed_area();
        Set<Integer> changed_nets = new TreeSet<Integer>();
        Iterator<Item> it = p_item_list.iterator();
        while (it.hasNext())
        {
            Item curr_item = it.next();
            if (!p_with_delete_fixed && curr_item.is_delete_fixed() || curr_item.is_user_fixed())
            {
                result = false;
            }
            else
            {
                for (int i = 0; i < curr_item.tile_shape_count(); ++i)
                {
                    TileShape curr_shape = curr_item.get_tile_shape(i);
                    changed_area.join(curr_shape, curr_item.shape_layer(i));
                    if (calculate_tidy_region)
                    {
                        tidy_region = tidy_region.union(curr_shape.bounding_octagon());
                    }
                }
                remove_item(curr_item);
                for (int i = 0; i < curr_item.net_count(); ++i)
                {
                    changed_nets.add(curr_item.get_net_no(i));
                }
            }
        }
        for (Integer curr_net_no : changed_nets)
        {
            this.combine_traces(curr_net_no);
        }
        if (calculate_tidy_region)
        {
            tidy_region = tidy_region.enlarge(p_tidy_width);
        }
        opt_changed_area(new int[0], tidy_region, p_pull_tight_accuracy, null, null, PULL_TIGHT_TIME_LIMIT);
        return result;
    }

    /**
     * starts marking the changed areas for optimizing traces
     */
    public void start_marking_changed_area()
    {
        if (changed_area == null)
        {
            changed_area = new ChangedArea(get_layer_count());
        }
    }

    /**
     * enlarges the changed area on p_layer, so that it contains p_point
     */
    public void join_changed_area(FloatPoint p_point, int p_layer)
    {
        if (changed_area != null)
        {
            changed_area.join(p_point, p_layer);
        }
    }

    /**
     * marks the whole board as changed
     */
    public void mark_all_changed_area()
    {
        start_marking_changed_area();
        FloatPoint[] board_corners = new FloatPoint[4];
        board_corners[0] = bounding_box.ll.to_float();
        board_corners[1] = new FloatPoint(bounding_box.ur.x, bounding_box.ll.y);
        board_corners[2] = bounding_box.ur.to_float();
        board_corners[3] = new FloatPoint(bounding_box.ll.x, bounding_box.ur.y);
        for (int i = 0; i < get_layer_count(); ++i)
        {
            for (int j = 0; j < 4; ++j)
            {
                join_changed_area(board_corners[j], i);
            }
        }
    }

    /**
     * Optimizes the route in the internally marked area.
     * If p_net_no > 0, only traces with net number p_net_no are optimized.
     * If p_clip_shape != null the optimizing is restricted to p_clip_shape.
     * p_trace_cost_arr is used for optimizing vias and may be null.
     * If p_stoppable_thread != null, the agorithm can be requested to be stopped.
     * If p_time_limit > 0; the algorithm will be stopped after p_time_limit Milliseconds.
     */
    public void opt_changed_area(int[] p_only_net_no_arr, IntOctagon p_clip_shape, int p_accuracy, ExpansionCostFactor[] p_trace_cost_arr,
            Stoppable p_stoppable_thread, int p_time_limit)
    {
        opt_changed_area(p_only_net_no_arr, p_clip_shape, p_accuracy, p_trace_cost_arr,
                p_stoppable_thread, p_time_limit, null, 0);
    }

    /**
     * Optimizes the route in the internally marked area.
     * If p_net_no > 0, only traces with net number p_net_no are optimized.
     * If p_clip_shape != null the optimizing is restricted to p_clip_shape.
     * p_trace_cost_arr is used for optimizing vias and may be null.
     * If p_stoppable_thread != null, the agorithm can be requested to be stopped.
     * If p_time_limit > 0; the algorithm will be stopped after p_time_limit Milliseconds.
     * If p_keep_point != null, traces on layer p_keep_point_layer containing p_keep_point
     *  will also contain this point after optimizing.
     */
    public void opt_changed_area(int[] p_only_net_no_arr, IntOctagon p_clip_shape, int p_accuracy, ExpansionCostFactor[] p_trace_cost_arr,
            Stoppable p_stoppable_thread, int p_time_limit, Point p_keep_point, int p_keep_point_layer)
    {
        if (changed_area == null)
        {
            return;
        }
        if (p_clip_shape != IntOctagon.EMPTY)
        {
            PullTightAlgo pull_tight_algo =
                    PullTightAlgo.get_instance(this, p_only_net_no_arr, p_clip_shape,
                    p_accuracy, p_stoppable_thread, p_time_limit, p_keep_point, p_keep_point_layer);
            pull_tight_algo.opt_changed_area(p_trace_cost_arr);
        }
        join_graphics_update_box(changed_area.surrounding_box());
        changed_area = null;
    }

    /**
     * Checks if a rectangular boxed trace line segment with the input parameters can
     * be inserted without conflict. If a conflict exists,
     * The result length is the maximal line length from p_line.a to p_line.b,
     *  which can be inserted without conflict (Integer.MAX_VALUE, if no conflict exists).
     * If p_only_not_shovable_obstacles, unfixed traces and vias are ignored.
     */
    public double check_trace_segment(Point p_from_point, Point p_to_point, int p_layer, int[] p_net_no_arr,
            int p_trace_half_width, int p_cl_class_no, boolean p_only_not_shovable_obstacles)
    {
        if (p_from_point.equals(p_to_point))
        {
            return 0;
        }
        Polyline curr_polyline = new Polyline(p_from_point, p_to_point);
        LineSegment curr_line_segment = new LineSegment(curr_polyline, 1);
        return check_trace_segment(curr_line_segment, p_layer, p_net_no_arr,
                p_trace_half_width, p_cl_class_no, p_only_not_shovable_obstacles);
    }

    /**
     * Checks if a trace shape around the input parameters can
     * be inserted without conflict. If a conflict exists,
     * The result length is the maximal line length from p_line.a to p_line.b,
     *  which can be inserted without conflict (Integer.MAX_VALUE, if no conflict exists).
     * If p_only_not_shovable_obstacles, unfixed traces and vias are ignored.
     */
    public double check_trace_segment(LineSegment p_line_segment, int p_layer, int[] p_net_no_arr,
            int p_trace_half_width, int p_cl_class_no, boolean p_only_not_shovable_obstacles)
    {
        Polyline check_polyline = p_line_segment.to_polyline();
        if (check_polyline.arr.length != 3)
        {
            return 0;
        }
        TileShape shape_to_check = check_polyline.offset_shape(p_trace_half_width, 0);
        FloatPoint from_point = p_line_segment.start_point_approx();
        FloatPoint to_point = p_line_segment.end_point_approx();
        double line_length = to_point.distance(from_point);
        double ok_length = Integer.MAX_VALUE;
        ShapeSearchTree default_tree = this.search_tree_manager.get_default_tree();

        Collection<TreeEntry> obstacle_entries = default_tree.overlapping_tree_entries_with_clearance(shape_to_check, p_layer, p_net_no_arr, p_cl_class_no);

        for (TreeEntry curr_obstacle_entry : obstacle_entries)
        {

            if (!(curr_obstacle_entry.object instanceof Item))
            {
                continue;
            }
            Item curr_obstacle = (Item) curr_obstacle_entry.object;
            if (p_only_not_shovable_obstacles && curr_obstacle.is_route() && !curr_obstacle.is_shove_fixed())
            {
                continue;
            }
            TileShape curr_obstacle_shape = curr_obstacle_entry.object.get_tree_shape(default_tree, curr_obstacle_entry.shape_index_in_object);
            TileShape curr_offset_shape;
            FloatPoint nearest_obstacle_point;
            double shorten_value;
            if (default_tree.is_clearance_compensation_used())
            {
                curr_offset_shape = shape_to_check;
                shorten_value = p_trace_half_width + rules.clearance_matrix.clearance_compensation_value(curr_obstacle.clearance_class_no(), p_layer);
            }
            else
            {
                int clearance_value = this.clearance_value(curr_obstacle.clearance_class_no(), p_cl_class_no, p_layer);
                curr_offset_shape = (TileShape) shape_to_check.offset(clearance_value);
                shorten_value = p_trace_half_width + clearance_value;
            }
            TileShape intersection = curr_obstacle_shape.intersection(curr_offset_shape);
            if (intersection.is_empty())
            {
                continue;
            }
            nearest_obstacle_point = intersection.nearest_point_approx(from_point);

            double projection = from_point.scalar_product(to_point, nearest_obstacle_point) / line_length;

            projection = Math.max(0.0, projection - shorten_value - 1);

            if (projection < ok_length)
            {
                ok_length = projection;
                if (ok_length <= 0)
                {
                    return 0;
                }
            }
        }

        return ok_length;
    }

    /**
     * Checks, if p_item can be translated by p_vector without
     * producing overlaps or clearance violations.
     */
    public boolean check_move_item(Item p_item, Vector p_vector, Collection<Item> p_ignore_items)
    {
        int net_count = p_item.net_no_arr.length;
        if (net_count > 1)
        {
            return false; //not yet implemented
        }
        int contact_count = 0;
        // the connected items must remain connected after moving
        if (p_item instanceof Connectable)
        {
            contact_count = p_item.get_all_contacts().size();
        }
        if (p_item instanceof Trace && contact_count > 0)
        {
            return false;
        }
        if (p_ignore_items != null)
        {
            p_ignore_items.add(p_item);
        }
        for (int i = 0; i < p_item.tile_shape_count(); ++i)
        {
            TileShape moved_shape = (TileShape) p_item.get_tile_shape(i).translate_by(p_vector);
            if (!moved_shape.is_contained_in(bounding_box))
            {
                return false;
            }
            Set<Item> obstacles =
                    this.overlapping_items_with_clearance(moved_shape, p_item.shape_layer(i), p_item.net_no_arr,
                    p_item.clearance_class_no());
            for (Item curr_item : obstacles)
            {
                if (p_ignore_items != null)
                {
                    if (!p_ignore_items.contains(curr_item))
                    {
                        if (curr_item.is_obstacle(p_item))
                        {
                            return false;
                        }
                    }
                }
                else if (curr_item != p_item)
                {
                    if (curr_item.is_obstacle(p_item))
                    {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Checks, if the net number of p_item can be  changed without producing clearance violations.
     */
    public boolean check_change_net(Item p_item, int p_new_net_no)
    {
        int[] net_no_arr = new int[1];
        net_no_arr[0] = p_new_net_no;
        for (int i = 0; i < p_item.tile_shape_count(); ++i)
        {
            TileShape curr_shape = p_item.get_tile_shape(i);
            Set<Item> obstacles =
                    this.overlapping_items_with_clearance(curr_shape, p_item.shape_layer(i),
                    net_no_arr, p_item.clearance_class_no());
            for (SearchTreeObject curr_ob : obstacles)
            {
                if (curr_ob != p_item && curr_ob instanceof Connectable && !((Connectable) curr_ob).contains_net(p_new_net_no))
                {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Translates p_drill_item by p_vector and shoves obstacle
     * traces aside. Returns false, if that was not possible without creating
     * clearance violations. In this case the database may be damaged, so that an undo
     * becomes necessesary.
     */
    public boolean move_drill_item(DrillItem p_drill_item, Vector p_vector,
            int p_max_recursion_depth, int p_max_via_recursion_depth,
            int p_tidy_width, int p_pull_tight_accuracy, int p_pull_tight_time_limit)
    {
        clear_shove_failing_obstacle();
        // unfix the connected shove fixed traces.
        Collection<Item> contact_list = p_drill_item.get_normal_contacts();
        Iterator<Item> it = contact_list.iterator();
        while (it.hasNext())
        {
            Item curr_contact = it.next();
            if (curr_contact.get_fixed_state() == FixedState.SHOVE_FIXED)
            {
                curr_contact.set_fixed_state(FixedState.UNFIXED);
            }
        }

        IntOctagon tidy_region;
        boolean calculate_tidy_region;
        if (p_tidy_width < Integer.MAX_VALUE)
        {
            tidy_region = IntOctagon.EMPTY;
            calculate_tidy_region = (p_tidy_width > 0);
        }
        else
        {
            tidy_region = null;
            calculate_tidy_region = false;
        }
        int[] net_no_arr = p_drill_item.net_no_arr;
        start_marking_changed_area();
        if (!MoveDrillItemAlgo.insert(p_drill_item, p_vector,
                p_max_recursion_depth, p_max_via_recursion_depth, tidy_region, this))
        {
            return false;
        }
        if (calculate_tidy_region)
        {
            tidy_region = tidy_region.enlarge(p_tidy_width);
        }
        int[] opt_net_no_arr;
        if (p_max_recursion_depth <= 0)
        {
            opt_net_no_arr = net_no_arr;
        }
        else
        {
            opt_net_no_arr = new int[0];
        }
        opt_changed_area(opt_net_no_arr, tidy_region, p_pull_tight_accuracy, null, null, p_pull_tight_time_limit);
        return true;
    }

    /**
     * Checks, if there is an item near by sharing a net with p_net_no_arr, from where a routing can start,
     * or where the routig can connect to.
     * If  p_from_item != null, items, which are connected to p_from_item, are
     * ignored.
     * Returns null, if no item is found,
     * If p_layer < 0, the layer is ignored
     */
    public Item pick_nearest_routing_item(Point p_location, int p_layer, Item p_from_item)
    {
        TileShape point_shape = TileShape.get_instance(p_location);
        Collection<Item> found_items = overlapping_items(point_shape, p_layer);
        FloatPoint pick_location = p_location.to_float();
        double min_dist = Integer.MAX_VALUE;
        Item nearest_item = null;
        Set<Item> ignore_set = null;
        Iterator<Item> it = found_items.iterator();
        while (it.hasNext())
        {
            Item curr_item = it.next();
            if (!curr_item.is_connectable())
            {
                continue;
            }
            boolean candidate_found = false;
            double curr_dist = 0;
            if (curr_item instanceof PolylineTrace)
            {
                PolylineTrace curr_trace = (PolylineTrace) curr_item;
                if (p_layer < 0 || curr_trace.get_layer() == p_layer)
                {
                    if (nearest_item instanceof DrillItem)
                    {
                        continue; // prefer drill items
                    }
                    int trace_radius = curr_trace.get_half_width();
                    curr_dist = curr_trace.polyline().distance(pick_location);
                    if (curr_dist < min_dist && curr_dist <= trace_radius)
                    {
                        candidate_found = true;
                    }
                }
            }
            else if (curr_item instanceof DrillItem)
            {
                DrillItem curr_drill_item = (DrillItem) curr_item;
                if (p_layer < 0 || curr_drill_item.is_on_layer(p_layer))
                {
                    FloatPoint drill_item_center = curr_drill_item.get_center().to_float();
                    curr_dist = drill_item_center.distance(pick_location);
                    if (curr_dist < min_dist || nearest_item instanceof Trace)
                    {
                        candidate_found = true;
                    }
                }
            }
            else if (curr_item instanceof ConductionArea)
            {
                ConductionArea curr_area = (ConductionArea) curr_item;
                if ((p_layer < 0 || curr_area.get_layer() == p_layer) && nearest_item == null)
                {
                    candidate_found = true;
                    curr_dist = Integer.MAX_VALUE;
                }
            }
            if (candidate_found)
            {
                if (p_from_item != null)
                {
                    if (ignore_set == null)
                    {
                        // calculated here to avoid unnessery calculations for performance reasoss.
                        ignore_set = p_from_item.get_connected_set(-1);
                    }
                    if (ignore_set.contains(curr_item))
                    {
                        continue;
                    }
                }
                min_dist = curr_dist;
                nearest_item = curr_item;
            }
        }
        return nearest_item;
    }

    /**
     * Shoves aside traces, so that a via with the input parameters can be
     * inserted without clearance violations. If the shove failed, the database may be damaged, so that an undo
     * becomes necessesary. Returns false, if the forced via failed.
     */
    public boolean forced_via(ViaInfo p_via_info, Point p_location, int[] p_net_no_arr,
            int p_trace_clearance_class_no, int[] p_trace_pen_halfwidth_arr,
            int p_max_recursion_depth, int p_max_via_recursion_depth,
            int p_tidy_width, int p_pull_tight_accuracy, int p_pull_tight_time_limit)
    {
        clear_shove_failing_obstacle();
        this.start_marking_changed_area();
        boolean result = ForcedViaAlgo.insert(p_via_info, p_location, p_net_no_arr,
                p_trace_clearance_class_no, p_trace_pen_halfwidth_arr,
                p_max_recursion_depth, p_max_via_recursion_depth, this);
        if (result)
        {
            IntOctagon tidy_clip_shape;
            if (p_tidy_width < Integer.MAX_VALUE)
            {
                tidy_clip_shape = p_location.surrounding_octagon().enlarge(p_tidy_width);
            }
            else
            {
                tidy_clip_shape = null;
            }
            int[] opt_net_no_arr;
            if (p_max_recursion_depth <= 0)
            {
                opt_net_no_arr = p_net_no_arr;
            }
            else
            {
                opt_net_no_arr = new int[0];
            }
            this.opt_changed_area(opt_net_no_arr, tidy_clip_shape,
                    p_pull_tight_accuracy, null, null, p_pull_tight_time_limit);
        }
        return result;
    }

    /**
     * Tries to insert a trace line with the input parameters from
     * p_from_corner to p_to_corner while shoving aside obstacle traces
     * and vias. Returns the last point between p_from_corner and p_to_corner,
     * to which the shove succeeded.
     * Returns null, if the check was inaccurate and an error accured while
     * inserting, so that the database may be damaged and an undo necessary.
     * p_search_tree is the shape search tree used in the algorithm.
     */
    public Point insert_forced_trace_segment(Point p_from_corner,
            Point p_to_corner, int p_half_width, int p_layer, int[] p_net_no_arr,
            int p_clearance_class_no, int p_max_recursion_depth, int p_max_via_recursion_depth,
            int p_max_spring_over_recursion_depth, int p_tidy_width,
            int p_pull_tight_accuracy, boolean p_with_check, TimeLimit p_time_limit)
    {
        if (p_from_corner.equals(p_to_corner))
        {
            return p_to_corner;
        }
        Polyline insert_polyline = new Polyline(p_from_corner, p_to_corner);
        Point ok_point = insert_forced_trace_polyline(insert_polyline, p_half_width, p_layer, p_net_no_arr,
                p_clearance_class_no, p_max_recursion_depth, p_max_via_recursion_depth,
                p_max_spring_over_recursion_depth, p_tidy_width,
                p_pull_tight_accuracy, p_with_check, p_time_limit);
        Point result;
        if (ok_point == insert_polyline.first_corner())
        {
            result = p_from_corner;
        }
        else if (ok_point == insert_polyline.last_corner())
        {
            result = p_to_corner;
        }
        else
        {
            result = ok_point;
        }
        return result;
    }

    /**
     * Checks, if a trace polyline with the input parameters can be inserted
     * while shoving aside obstacle traces and vias.
     */
    public boolean check_forced_trace_polyline(Polyline p_polyline, int p_half_width, int p_layer, int[] p_net_no_arr,
            int p_clearance_class_no, int p_max_recursion_depth, int p_max_via_recursion_depth,
            int p_max_spring_over_recursion_depth)
    {
        ShapeSearchTree search_tree = search_tree_manager.get_default_tree();
        int compensated_half_width = p_half_width + search_tree.clearance_compensation_value(p_clearance_class_no, p_layer);
        TileShape[] trace_shapes = p_polyline.offset_shapes(compensated_half_width,
                0, p_polyline.arr.length - 1);
        boolean orthogonal_mode = (rules.get_trace_angle_restriction() == AngleRestriction.NINETY_DEGREE);
        ShoveTraceAlgo shove_trace_algo = new ShoveTraceAlgo(this);
        for (int i = 0; i < trace_shapes.length; ++i)
        {
            TileShape curr_trace_shape = trace_shapes[i];
            if (orthogonal_mode)
            {
                curr_trace_shape = curr_trace_shape.bounding_box();
            }
            CalcFromSide from_side = new CalcFromSide(p_polyline, i + 1, curr_trace_shape);

            boolean check_shove_ok = shove_trace_algo.check(curr_trace_shape, from_side, null, p_layer,
                    p_net_no_arr, p_clearance_class_no, p_max_recursion_depth,
                    p_max_via_recursion_depth, p_max_spring_over_recursion_depth, null);
            if (!check_shove_ok)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Tries to insert a trace polyline with the input parameters from
     * while shoving aside obstacle traces and vias. Returns the last corner
     * on the polyline, to which the shove succeeded.
     * Returns null, if the check was inaccurate and an error accured while
     * inserting, so that the database may be damaged and an undo necessary.
     */
    public Point insert_forced_trace_polyline(Polyline p_polyline, int p_half_width, int p_layer, int[] p_net_no_arr,
            int p_clearance_class_no, int p_max_recursion_depth, int p_max_via_recursion_depth,
            int p_max_spring_over_recursion_depth, int p_tidy_width,
            int p_pull_tight_accuracy, boolean p_with_check, TimeLimit p_time_limit)
    {
        clear_shove_failing_obstacle();
        Point from_corner = p_polyline.first_corner();
        Point to_corner = p_polyline.last_corner();
        if (from_corner.equals(to_corner))
        {
            return to_corner;
        }
        if (!(from_corner instanceof IntPoint && to_corner instanceof IntPoint))
        {
            System.out.println("RoutingBoard.insert_forced_trace_segment: only implemented for IntPoints");
            return from_corner;
        }
        start_marking_changed_area();
        // Check, if there ends a item of the same net at p_from_corner.
        // If so, its geometry will be used to cut off dog ears of the check shape.
        Trace picked_trace = null;
        ItemSelectionFilter filter = new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.TRACES);
        Set<Item> picked_items = this.pick_items(from_corner, p_layer, filter);
        if (picked_items.size() == 1)
        {
            Trace curr_picked_trace = (Trace) picked_items.iterator().next();
            if (curr_picked_trace.nets_equal(p_net_no_arr) && curr_picked_trace.get_half_width() == p_half_width && curr_picked_trace.clearance_class_no() == p_clearance_class_no && (curr_picked_trace instanceof PolylineTrace))
            {
                // can combine  with the picked trace
                picked_trace = curr_picked_trace;
            }
        }
        ShapeSearchTree search_tree = search_tree_manager.get_default_tree();
        int compensated_half_width = p_half_width + search_tree.clearance_compensation_value(p_clearance_class_no, p_layer);
        ShoveTraceAlgo shove_trace_algo = new ShoveTraceAlgo(this);
        Polyline new_polyline = shove_trace_algo.spring_over_obstacles(p_polyline,
                compensated_half_width, p_layer, p_net_no_arr, p_clearance_class_no, null);
        if (new_polyline == null)
        {
            return from_corner;
        }
        Polyline combined_polyline;
        if (picked_trace == null)
        {
            combined_polyline = new_polyline;
        }
        else
        {
            PolylineTrace combine_trace = (PolylineTrace) picked_trace;
            combined_polyline = new_polyline.combine(combine_trace.polyline());
        }
        if (combined_polyline.arr.length < 3)
        {
            return from_corner;
        }
        int start_shape_no = combined_polyline.arr.length - new_polyline.arr.length;
        // calculate the last shapes of combined_polyline for checking
        TileShape[] trace_shapes = combined_polyline.offset_shapes(compensated_half_width,
                start_shape_no, combined_polyline.arr.length - 1);
        int last_shape_no = trace_shapes.length;
        boolean orthogonal_mode = (rules.get_trace_angle_restriction() == AngleRestriction.NINETY_DEGREE);
        for (int i = 0; i < trace_shapes.length; ++i)
        {
            TileShape curr_trace_shape = trace_shapes[i];
            if (orthogonal_mode)
            {
                curr_trace_shape = curr_trace_shape.bounding_box();
            }
            CalcFromSide from_side = new CalcFromSide(combined_polyline,
                    combined_polyline.corner_count() - trace_shapes.length - 1 + i, curr_trace_shape);
            if (p_with_check)
            {
                boolean check_shove_ok = shove_trace_algo.check(curr_trace_shape, from_side, null, p_layer,
                        p_net_no_arr, p_clearance_class_no, p_max_recursion_depth,
                        p_max_via_recursion_depth, p_max_spring_over_recursion_depth, p_time_limit);
                if (!check_shove_ok)
                {
                    last_shape_no = i;
                    break;
                }
            }
            boolean insert_ok = shove_trace_algo.insert(curr_trace_shape, from_side, p_layer, p_net_no_arr,
                    p_clearance_class_no, null, p_max_recursion_depth,
                    p_max_via_recursion_depth, p_max_spring_over_recursion_depth);
            if (!insert_ok)
            {
                return null;
            }
        }
        Point new_corner = to_corner;
        if (last_shape_no < trace_shapes.length)
        {
            // the shove with index last_shape_no failed.
            // Sample the shove line to a shorter shove distance and try again.
            TileShape last_trace_shape = trace_shapes[last_shape_no];
            if (orthogonal_mode)
            {
                last_trace_shape = last_trace_shape.bounding_box();
            }
            int sample_width = 2 * this.get_min_trace_half_width();
            FloatPoint last_corner = new_polyline.corner_approx(last_shape_no + 1);
            FloatPoint prev_last_corner = new_polyline.corner_approx(last_shape_no);
            double last_segment_length = last_corner.distance(prev_last_corner);
            if (last_segment_length > 100 * sample_width)
            {
                // to many cycles to sample
                return from_corner;
            }
            int shape_index = combined_polyline.corner_count() - trace_shapes.length - 1 + last_shape_no;
            if (last_segment_length > sample_width)
            {
                new_polyline =
                        new_polyline.shorten(new_polyline.arr.length - (trace_shapes.length - last_shape_no - 1), sample_width);
                Point curr_last_corner = new_polyline.last_corner();
                if (!(curr_last_corner instanceof IntPoint))
                {
                    System.out.println("insert_forced_trace_segment: IntPoint expected");
                    return from_corner;
                }
                new_corner = curr_last_corner;
                if (picked_trace == null)
                {
                    combined_polyline = new_polyline;
                }
                else
                {
                    PolylineTrace combine_trace = (PolylineTrace) picked_trace;
                    combined_polyline = new_polyline.combine(combine_trace.polyline());
                }
                if (combined_polyline.arr.length < 3)
                {
                    return new_corner;
                }
                shape_index = combined_polyline.arr.length - 3;
                last_trace_shape = combined_polyline.offset_shape(compensated_half_width, shape_index);
                if (orthogonal_mode)
                {
                    last_trace_shape = last_trace_shape.bounding_box();
                }
            }
            CalcFromSide from_side = new CalcFromSide(combined_polyline, shape_index, last_trace_shape);
            boolean check_shove_ok = shove_trace_algo.check(last_trace_shape, from_side, null, p_layer,
                    p_net_no_arr, p_clearance_class_no, p_max_recursion_depth,
                    p_max_via_recursion_depth, p_max_spring_over_recursion_depth, p_time_limit);
            if (!check_shove_ok)
            {
                return from_corner;
            }
            boolean insert_ok = shove_trace_algo.insert(last_trace_shape, from_side, p_layer,
                    p_net_no_arr, p_clearance_class_no, null, p_max_recursion_depth,
                    p_max_via_recursion_depth, p_max_spring_over_recursion_depth);
            if (!insert_ok)
            {
                System.out.println("shove trace failed");
                return null;
            }
        }
        // insert the new trace segment
        for (int i = 0; i < new_polyline.corner_count(); ++i)
        {
            join_changed_area(new_polyline.corner_approx(i), p_layer);
        }
        PolylineTrace new_trace = insert_trace_without_cleaning(new_polyline, p_layer, p_half_width, p_net_no_arr, p_clearance_class_no, FixedState.UNFIXED);
        new_trace.combine();

        IntOctagon tidy_region = null;
        if (p_tidy_width < Integer.MAX_VALUE)
        {
            tidy_region = new_corner.surrounding_octagon().enlarge(p_tidy_width);
        }
        int[] opt_net_no_arr;
        if (p_max_recursion_depth <= 0)
        {
            opt_net_no_arr = p_net_no_arr;
        }
        else
        {
            opt_net_no_arr = new int[0];
        }
        PullTightAlgo pull_tight_algo =
                PullTightAlgo.get_instance(this, opt_net_no_arr, tidy_region,
                p_pull_tight_accuracy, null, -1, new_corner, p_layer);

        // Remove evtl. generated cycles because otherwise pull_tight may not work correctly.
        if (new_trace.normalize(changed_area.get_area(p_layer)))
        {

            pull_tight_algo.split_traces_at_keep_point();
            // otherwise the new corner may no more be contained in the new trace after optimizing
            ItemSelectionFilter item_filter = new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.TRACES);
            Set<Item> curr_picked_items = this.pick_items(new_corner, p_layer, item_filter);
            new_trace = null;
            if (!curr_picked_items.isEmpty())
            {
                Item found_trace = curr_picked_items.iterator().next();
                if (found_trace instanceof PolylineTrace)
                {
                    new_trace = (PolylineTrace) found_trace;
                }
            }
        }

        // To avoid, that a separate handling for moving backwards in the own trace line
        // becomes necessesary, pull tight is called here.
        if (p_tidy_width > 0 && new_trace != null)
        {
            new_trace.pull_tight(pull_tight_algo);
        }
        return new_corner;
    }

    /**
     * Initialises the autoroute database for routing a connection.
     * If p_retain_autoroute_database, the autoroute database is retained and maintained after
     * the algorithm for performance reasons.
     */
    public AutorouteEngine init_autoroute(int p_net_no, int p_trace_clearance_class_no,
            Stoppable p_stoppable_thread, TimeLimit p_time_limit, boolean p_retain_autoroute_database)
    {
        if (this.autoroute_engine == null || !p_retain_autoroute_database || this.autoroute_engine.autoroute_search_tree.compensated_clearance_class_no != p_trace_clearance_class_no)
        {
            this.autoroute_engine = new AutorouteEngine(this, p_trace_clearance_class_no, p_retain_autoroute_database);
        }
        this.autoroute_engine.init_connection(p_net_no, p_stoppable_thread, p_time_limit);
        return this.autoroute_engine;
    }

    /**
     * Clears the autoroute database in case it was retained.
     */
    public void finish_autoroute()
    {
        if (this.autoroute_engine != null)
        {
            this.autoroute_engine.clear();
        }
        this.autoroute_engine = null;
    }

    /**
     * Routes automatically p_item to another item of the same net, to which it
     * is not yet electrically connected.
     * Returns an enum of type AutorouteEngine.AutorouteResult
     */
    public AutorouteEngine.AutorouteResult autoroute(Item p_item, interactive.Settings p_settings, int p_via_costs, Stoppable p_stoppable_thread, TimeLimit p_time_limit)
    {
        if (!(p_item instanceof Connectable) || p_item.net_count() == 0)
        {
            return AutorouteEngine.AutorouteResult.ALREADY_CONNECTED;
        }
        if (p_item.net_count() > 1)
        {
            System.out.println("RoutingBoard.autoroute: net_count > 1 not yet implemented");
        }
        int route_net_no = p_item.get_net_no(0);
        AutorouteControl ctrl_settings = new AutorouteControl(this, route_net_no, p_settings, p_via_costs, p_settings.autoroute_settings.get_trace_cost_arr());
        ctrl_settings.remove_unconnected_vias = false;
        Set<Item> route_start_set = p_item.get_connected_set(route_net_no);
        rules.Net route_net = rules.nets.get(route_net_no);
        if (route_net != null && route_net.contains_plane())
        {
            for (Item curr_item : route_start_set)
            {
                if (curr_item instanceof board.ConductionArea)
                {
                    return AutorouteEngine.AutorouteResult.ALREADY_CONNECTED; // already connected to plane
                }
            }
        }
        Set<Item> route_dest_set = p_item.get_unconnected_set(route_net_no);
        if (route_dest_set.size() == 0)
        {
            return AutorouteEngine.AutorouteResult.ALREADY_CONNECTED; // p_item is already routed.
        }
        SortedSet<Item> ripped_item_list = new TreeSet<Item>();
        AutorouteEngine curr_autoroute_engine = init_autoroute(p_item.get_net_no(0),
                ctrl_settings.trace_clearance_class_no, p_stoppable_thread, p_time_limit, false);
        AutorouteEngine.AutorouteResult result =
                curr_autoroute_engine.autoroute_connection(route_start_set, route_dest_set, ctrl_settings, ripped_item_list);
        if (result == AutorouteEngine.AutorouteResult.ROUTED)
        {
            final int time_limit_to_prevent_endless_loop = 1000;
            opt_changed_area(new int[0], null, p_settings.get_trace_pull_tight_accuracy(), ctrl_settings.trace_costs, p_stoppable_thread, time_limit_to_prevent_endless_loop);
        }
        return result;
    }

    /**
     *  Autoroutes from the input pin until the first via, in case the pin and its connected set
     *  has only 1 layer. Ripup is allowed if p_ripup_costs is >= 0.
     *  Returns an enum of type AutorouteEngine.AutorouteResult
     */
    public AutorouteEngine.AutorouteResult fanout(Pin p_pin, interactive.Settings p_settings, int p_ripup_costs,
            Stoppable p_stoppable_thread, TimeLimit p_time_limit)
    {
        if (p_pin.first_layer() != p_pin.last_layer() || p_pin.net_count() != 1)
        {
            return AutorouteEngine.AutorouteResult.ALREADY_CONNECTED;
        }
        int pin_net_no = p_pin.get_net_no(0);
        int pin_layer = p_pin.first_layer();
        Set<Item> pin_connected_set = p_pin.get_connected_set(pin_net_no);
        for (Item curr_item : pin_connected_set)
        {
            if (curr_item.first_layer() != pin_layer || curr_item.last_layer() != pin_layer)
            {
                return AutorouteEngine.AutorouteResult.ALREADY_CONNECTED;
            }
        }
        Set<Item> unconnected_set = p_pin.get_unconnected_set(pin_net_no);
        if (unconnected_set.isEmpty())
        {
            return AutorouteEngine.AutorouteResult.ALREADY_CONNECTED;
        }
        AutorouteControl ctrl_settings = new AutorouteControl(this, pin_net_no, p_settings);
        ctrl_settings.is_fanout = true;
        ctrl_settings.remove_unconnected_vias = false;
        if (p_ripup_costs >= 0)
        {
            ctrl_settings.ripup_allowed = true;
            ctrl_settings.ripup_costs = p_ripup_costs;
        }
        SortedSet<Item> ripped_item_list = new TreeSet<Item>();
        AutorouteEngine curr_autoroute_engine = init_autoroute(pin_net_no,
                ctrl_settings.trace_clearance_class_no, p_stoppable_thread, p_time_limit, false);
        AutorouteEngine.AutorouteResult result =
                curr_autoroute_engine.autoroute_connection(pin_connected_set,
                unconnected_set, ctrl_settings, ripped_item_list);
        if (result == AutorouteEngine.AutorouteResult.ROUTED)
        {
            final int time_limit_to_prevent_endless_loop = 1000;
            opt_changed_area(new int[0], null, p_settings.get_trace_pull_tight_accuracy(), ctrl_settings.trace_costs, p_stoppable_thread, time_limit_to_prevent_endless_loop);
        }
        return result;
    }

    /**
     * Inserts a trace from p_from_point to the nearest point on p_to_trace.
     * Returns false, if that is not possible without clearance violation.
     */
    public boolean connect_to_trace(IntPoint p_from_point, Trace p_to_trace,
            int p_pen_half_width, int p_cl_type)
    {

        Point first_corner = p_to_trace.first_corner();

        Point last_corner = p_to_trace.last_corner();

        int[] net_no_arr = p_to_trace.net_no_arr;

        if (!(p_to_trace instanceof PolylineTrace))
        {
            return false; // not yet implemented
        }
        PolylineTrace to_trace = (PolylineTrace) p_to_trace;
        if (to_trace.polyline().contains(p_from_point))
        {
            // no connection line necessary
            return true;
        }
        LineSegment projection_line = to_trace.polyline().projection_line(p_from_point);
        if (projection_line == null)
        {
            return false;
        }
        Polyline connection_line = projection_line.to_polyline();
        if (connection_line == null || connection_line.arr.length != 3)
        {
            return false;
        }
        int trace_layer = p_to_trace.get_layer();
        if (!this.check_polyline_trace(connection_line, trace_layer, p_pen_half_width,
                p_to_trace.net_no_arr, p_cl_type))
        {
            return false;
        }
        if (this.changed_area != null)
        {
            for (int i = 0; i < connection_line.corner_count(); ++i)
            {
                this.changed_area.join(connection_line.corner_approx(i), trace_layer);
            }
        }

        this.insert_trace(connection_line, trace_layer, p_pen_half_width, net_no_arr, p_cl_type, FixedState.UNFIXED);
        if (!p_from_point.equals(first_corner))
        {
            Trace tail = this.get_trace_tail(first_corner, trace_layer, net_no_arr);
            if (tail != null && !tail.is_user_fixed())
            {
                this.remove_item(tail);
            }
        }
        if (!p_from_point.equals(last_corner))
        {
            Trace tail = this.get_trace_tail(last_corner, trace_layer, net_no_arr);
            if (tail != null && !tail.is_user_fixed())
            {
                this.remove_item(tail);
            }
        }
        return true;
    }

    /**
     * Checks, if the list p_items contains traces, which have no contact at their
     * start or end point. Trace with net number p_except_net_no are ignored.
     */
    public boolean contains_trace_tails(Collection<Item> p_items, int[] p_except_net_no_arr)
    {
        Iterator<Item> it = p_items.iterator();
        while (it.hasNext())
        {
            Item curr_ob = it.next();
            if (curr_ob instanceof Trace)
            {
                Trace curr_trace = (Trace) curr_ob;
                if (!curr_trace.nets_equal(p_except_net_no_arr))
                {
                    if (curr_trace.is_tail())
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Removes all trace tails of the input net.
     * If p_net_no <= 0, the tails of all nets are removed.
     *  Returns true, if something was removed.
     */
    public boolean remove_trace_tails(int p_net_no, Item.StopConnectionOption p_stop_connection_option)
    {
        SortedSet<Item> stub_set = new TreeSet<Item>();
        Collection<Item> board_items = this.get_items();
        for (Item curr_item : board_items)
        {
            if (!curr_item.is_route())
            {
                continue;
            }
            if (curr_item.net_count() != 1)
            {
                continue;
            }
            if (p_net_no > 0 && curr_item.get_net_no(0) != p_net_no)
            {
                continue;
            }
            if (curr_item.is_tail())
            {
                if (curr_item instanceof Via)
                {
                    if (p_stop_connection_option == Item.StopConnectionOption.VIA)
                    {
                        continue;
                    }
                    if (p_stop_connection_option == Item.StopConnectionOption.FANOUT_VIA)
                    {
                        if (curr_item.is_fanout_via(null))
                        {
                            continue;
                        }
                    }
                }
                stub_set.add(curr_item);
            }
        }
        SortedSet<Item> stub_connections = new TreeSet<Item>();
        for (Item curr_item : stub_set)
        {
            int item_contact_count = curr_item.get_normal_contacts().size();
            if (item_contact_count == 1)
            {
                stub_connections.addAll(curr_item.get_connection_items(p_stop_connection_option));
            }
            else
            {
                // the connected items are no stubs for example if a via is only connected on 1 layer,
                // but to several traces.
                stub_connections.add(curr_item);
            }
        }
        if (stub_connections.isEmpty())
        {
            return false;
        }
        this.remove_items(stub_connections, false);
        this.combine_traces(p_net_no);
        return true;
    }

    public void clear_all_item_temporary_autoroute_data()
    {
        Iterator<UndoableObjects.UndoableObjectNode> it = this.item_list.start_read_object();
        for (;;)
        {
            Item curr_item = (Item) item_list.read_object(it);
            if (curr_item == null)
            {
                break;
            }
            curr_item.clear_autoroute_info();
        }
    }

    /**
     * Sets, if all conduction areas on the board are obstacles for route of foreign nets.
     */
    public void change_conduction_is_obstacle(boolean p_value)
    {
        if (this.rules.get_ignore_conduction() != p_value)
        {
            return; // no muultiply
        }
        boolean something_changed = false;
        // Change the is_obstacle property of all conduction areas of the board.
        Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
        for (;;)
        {
            Item curr_item = (Item) item_list.read_object(it);
            if (curr_item == null)
            {
                break;
            }
            if (curr_item instanceof ConductionArea)
            {
                ConductionArea curr_conduction_area = (ConductionArea) curr_item;
                Layer curr_layer = layer_structure.arr[curr_conduction_area.get_layer()];
                if (curr_layer.is_signal && curr_conduction_area.get_is_obstacle() != p_value)
                {
                    curr_conduction_area.set_is_obstacle(p_value);
                    something_changed = true;
                }
            }
        }
        this.rules.set_ignore_conduction(!p_value);
        if (something_changed)
        {
            this.search_tree_manager.reinsert_tree_items();
        }
    }

    /**
     * Tries to educe the nets of traces and vias,  so that the nets are a subset of the nets of the contact
     * items. This is applied to traces and vias with more than 1 net  connected to tie pins.
     * Returns true, if the nets of some items were reduced.
     */
    public boolean reduce_nets_of_route_items()
    {
        boolean result = false;
        boolean something_changed = true;
        while (something_changed)
        {
            something_changed = false;
            Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
            for (;;)
            {
                UndoableObjects.Storable curr_ob = item_list.read_object(it);
                if (curr_ob == null)
                {
                    break;
                }
                Item curr_item = (Item) curr_ob;
                if (curr_item.net_no_arr.length <= 1 || curr_item.get_fixed_state() == FixedState.SYSTEM_FIXED)
                {
                    continue;
                }
                if (curr_ob instanceof Via)
                {
                    Collection<Item> contacts = curr_item.get_normal_contacts();
                    for (int curr_net_no : curr_item.net_no_arr)
                    {
                        for (Item curr_contact : contacts)
                        {
                            if (!curr_contact.contains_net(curr_net_no))
                            {
                                curr_item.remove_from_net(curr_net_no);
                                something_changed = true;
                                break;
                            }
                        }
                        if (something_changed)
                        {
                            break;
                        }
                    }

                }
                else if (curr_ob instanceof Trace)
                {
                    Trace curr_trace = (Trace) curr_ob;
                    Collection<Item> contacts = curr_trace.get_start_contacts();
                    for (int i = 0; i < 2; ++i)
                    {
                        for (int curr_net_no : curr_item.net_no_arr)
                        {
                            boolean pin_found = false;
                            for (Item curr_contact : contacts)
                            {
                                if (curr_contact instanceof Pin)
                                {
                                    pin_found = true;
                                    if (!curr_contact.contains_net(curr_net_no))
                                    {
                                        curr_item.remove_from_net(curr_net_no);
                                        something_changed = true;
                                        break;
                                    }
                                }
                            }
                            if (!pin_found) // at tie pins traces may have different nets
                            {
                                for (Item curr_contact : contacts)
                                {
                                    if (!(curr_contact instanceof Pin) && !curr_contact.contains_net(curr_net_no))
                                    {
                                        curr_item.remove_from_net(curr_net_no);
                                        something_changed = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (something_changed)
                        {
                            break;
                        }
                        contacts = curr_trace.get_end_contacts();
                    }
                    if (something_changed)
                    {
                        break;
                    }
                }
                if (something_changed)
                {
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Returns the obstacle responsible for the last shove to fail.
     */
    public Item get_shove_failing_obstacle()
    {
        return shove_failing_obstacle;
    }

    void set_shove_failing_obstacle(Item p_item)
    {
        shove_failing_obstacle = p_item;
    }

    public int get_shove_failing_layer()
    {
        return shove_failing_layer;
    }

    void set_shove_failing_layer(int p_layer)
    {
        shove_failing_layer = p_layer;
    }

    private void clear_shove_failing_obstacle()
    {
        shove_failing_obstacle = null;
        shove_failing_layer = -1;
    }

    /**
     * Sets, if the autoroute database has to be maintained outside the outoroute algorithm
     * while changing items on rhe board.
     */
    void set_maintaining_autoroute_database(boolean p_value)
    {
        if (p_value)
        {

        }
        else
        {
            this.autoroute_engine = null;
        }
    }

    /**
     * Returns, if the autoroute database is maintained outside the outoroute algorithm
     * while changing items on rhe board.
     */
    boolean is_maintaining_autoroute_database()
    {
        return this.autoroute_engine != null;
    }
    /**
     * Contains the database for the autorouzte algorithm.
     */
    private transient AutorouteEngine autoroute_engine = null;
    /** the area marked for optimizing the route */
    transient ChangedArea changed_area;
    private transient Item shove_failing_obstacle = null;
    private transient int shove_failing_layer = -1;
    /** The time limit in milliseconds for the pull tight algorithm */
    private static final int PULL_TIGHT_TIME_LIMIT = 2000;
}
