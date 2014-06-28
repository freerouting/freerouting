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
 * ShoveViaAlgo.java
 *
 * Created on 12. Dezember 2005, 06:48
 *
 */

package board;

import java.util.Collection;

import datastructures.TimeLimit;

import geometry.planar.TileShape;
import geometry.planar.ConvexShape;
import geometry.planar.IntOctagon;
import geometry.planar.IntBox;
import geometry.planar.IntPoint;
import geometry.planar.Point;
import geometry.planar.Vector;
import geometry.planar.FloatPoint;


/**
 *
 * Contains internal auxiliary functions of class RoutingBoard
 * for shoving vias and pins
 *
 * @author Alfons Wirtz
 */
public class MoveDrillItemAlgo
{
    
    /**
     * checks, if p_drill_item can be translated by p_vector by shoving obstacle
     * traces and vias aside, so that no clearance violations occur.
     */
    public static boolean check(DrillItem p_drill_item, Vector p_vector, int p_max_recursion_depth,
            int p_max_via_recursion_depth, Collection<Item> p_ignore_items,
            RoutingBoard p_board, TimeLimit p_time_limit)
    {
        
        if (p_time_limit != null && p_time_limit.limit_exceeded())
        {
            return false;
        }
        if (p_drill_item.is_shove_fixed())
        {
            return false;
        }
        
        // Check, that p_drillitem is only connected to traces.
        Collection<Item> contact_list = p_drill_item.get_normal_contacts();
        for (Item curr_contact : contact_list)
        {
            if (!(curr_contact instanceof Trace || curr_contact instanceof ConductionArea))
            {
                return false;
            }
        }
        Collection<Item> ignore_items;
        if (p_ignore_items == null)
        {
            ignore_items = new java.util.LinkedList<Item>();
        }
        else
        {
            ignore_items = p_ignore_items;
        }
        ignore_items.add(p_drill_item);
        ForcedPadAlgo forced_pad_algo = new ForcedPadAlgo(p_board);
        boolean attach_allowed = false;
        if (p_drill_item instanceof Via)
        {
            attach_allowed = ((Via)p_drill_item).attach_allowed;
        }
        ShapeSearchTree search_tree = p_board.search_tree_manager.get_default_tree();
        for (int curr_layer = p_drill_item.first_layer(); curr_layer <= p_drill_item.last_layer(); ++curr_layer)
        {
            int curr_ind = curr_layer - p_drill_item.first_layer();
            TileShape curr_shape = p_drill_item.get_tree_shape(search_tree, curr_ind);
            if (curr_shape == null)
            {
                continue;
            }
            ConvexShape new_shape = (ConvexShape) curr_shape.translate_by(p_vector);
            TileShape curr_tile_shape;
            if (p_board.rules.get_trace_angle_restriction() == AngleRestriction.NINETY_DEGREE)
            {
                curr_tile_shape = new_shape.bounding_box();
            }
            else
            {
                curr_tile_shape = new_shape.bounding_octagon();
            }
            CalcFromSide from_side = new CalcFromSide(p_drill_item.get_center(), curr_tile_shape);
            if (forced_pad_algo.check_forced_pad(curr_tile_shape, from_side, curr_layer,
                    p_drill_item.net_no_arr, p_drill_item.clearance_class_no(), attach_allowed,
                    ignore_items, p_max_recursion_depth, p_max_via_recursion_depth, true, p_time_limit)
                    == ForcedPadAlgo.CheckDrillResult.NOT_DRILLABLE)
            {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Translates p_drill_item by p_vector by shoving obstacle
     * traces and vias aside, so that no clearance violations occur.
     * If p_tidy_region != null, it will be joined by the bounding octagons of the translated shapes.
     */
    static boolean insert(DrillItem p_drill_item, Vector p_vector,
            int p_max_recursion_depth, int p_max_via_recursion_depth, IntOctagon p_tidy_region,
            RoutingBoard p_board)
    {
        if (p_drill_item.is_shove_fixed())
        {
            return false;
        }

        boolean attach_allowed = false;
        if (p_drill_item instanceof Via)
        {
            attach_allowed = ((Via)p_drill_item).attach_allowed;
        }
        ForcedPadAlgo forced_pad_algo = new ForcedPadAlgo(p_board);
        Collection<Item> ignore_items = new java.util.LinkedList<Item>();
        ignore_items.add(p_drill_item);
        ShapeSearchTree search_tree = p_board.search_tree_manager.get_default_tree();
        for (int curr_layer = p_drill_item.first_layer(); curr_layer <= p_drill_item.last_layer(); ++curr_layer)
        {
            int curr_ind = curr_layer - p_drill_item.first_layer();
            TileShape curr_shape = p_drill_item.get_tree_shape(search_tree, curr_ind);
            if (curr_shape == null)
            {
                continue;
            }
            ConvexShape new_shape = (ConvexShape) curr_shape.translate_by(p_vector);
            TileShape curr_tile_shape;
            if (p_board.rules.get_trace_angle_restriction() == AngleRestriction.NINETY_DEGREE)
            {
                curr_tile_shape = new_shape.bounding_box();
            }
            else
            {
                curr_tile_shape = new_shape.bounding_octagon();
            }
            if (p_tidy_region != null)
            {
                p_tidy_region = p_tidy_region.union(curr_tile_shape.bounding_octagon());
            }
            CalcFromSide from_side = new CalcFromSide(p_drill_item.get_center(), curr_tile_shape);
            if (!forced_pad_algo.forced_pad(curr_tile_shape, from_side, curr_layer, p_drill_item.net_no_arr,
                    p_drill_item.clearance_class_no(), attach_allowed,
                    ignore_items, p_max_recursion_depth, p_max_via_recursion_depth))
            {
                return false;
            }
            IntBox curr_bounding_box = curr_shape.bounding_box();
            for (int j = 0; j < 4; ++j)
            {
                p_board.join_changed_area( curr_bounding_box.corner_approx(j), curr_layer);
            }
        }
        p_drill_item.move_by(p_vector);
        return true;
    }
    
    /**
     * Shoves vias out of p_obstacle_shape. Returns false, if the database is damaged, so that an undo is necessary afterwards.
     */
    static boolean shove_vias(TileShape p_obstacle_shape, CalcFromSide p_from_side, int p_layer, int[] p_net_no_arr,
            int p_cl_type, Collection<Item> p_ignore_items, int p_max_recursion_depth,
            int p_max_via_recursion_depth, boolean p_copper_sharing_allowed,
            RoutingBoard p_board)
    {
        ShapeSearchTree search_tree = p_board.search_tree_manager.get_default_tree();
        ShapeTraceEntries shape_entries =
                new ShapeTraceEntries(p_obstacle_shape, p_layer, p_net_no_arr, p_cl_type, p_from_side, p_board);
        Collection<Item> obstacles =
                search_tree.overlapping_items_with_clearance(p_obstacle_shape, p_layer, new int[0], p_cl_type);
        
        if (!shape_entries.store_items(obstacles, false, p_copper_sharing_allowed))
        {
            return true;
        }
        if (p_ignore_items != null)
        {
            shape_entries.shove_via_list.removeAll(p_ignore_items);
        }
        if (shape_entries.shove_via_list.isEmpty())
        {
            return true;
        }
        double shape_radius = 0.5 * p_obstacle_shape.bounding_box().min_width();
        for (Via curr_via : shape_entries.shove_via_list)
        {
            if (curr_via.shares_net_no(p_net_no_arr))
            {
                continue;
            }
            if (p_max_via_recursion_depth <= 0)
            {
                return true;
            }
            IntPoint [] try_via_centers =
                    try_shove_via_points(p_obstacle_shape, p_layer, curr_via, p_cl_type, true, p_board);
            IntPoint new_via_center = null;
            double max_dist = 0.5 * curr_via.get_shape_on_layer(p_layer).bounding_box().max_width() + shape_radius;
            double max_dist_square = max_dist * max_dist;
            IntPoint curr_via_center = (IntPoint) curr_via.get_center();
            FloatPoint check_via_center = curr_via_center.to_float();
            Vector rel_coor = null;
            for (int i = 0; i < try_via_centers.length; ++i)
            {
                if (i == 0 || check_via_center.distance_square(try_via_centers[i].to_float()) <= max_dist_square)
                {
                    Collection<Item> ignore_items = new java.util.LinkedList<Item>();
                    if (p_ignore_items != null)
                    {
                        ignore_items.addAll(p_ignore_items);
                    }
                    rel_coor = try_via_centers[i].difference_by(curr_via_center);
                    // No time limit here because the item database is already changed.
                    boolean shove_ok = check(curr_via, rel_coor, p_max_recursion_depth, 
                            p_max_via_recursion_depth - 1, ignore_items, p_board, null);
                    if (shove_ok)
                    {
                        new_via_center = try_via_centers[i];
                        break;
                    }
                }
            }
            if (new_via_center == null)
            {
                continue;
            }
            if (!insert(curr_via, rel_coor, p_max_recursion_depth, p_max_via_recursion_depth - 1, null, p_board))
            {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Calculates possible new location for a via to shove outside p_obstacle_shape.
     * if p_extended_check is true, more than 1 possible new locations are calculated.
     * The function isused here and in ShoveTraceAlgo.check.
     */
    static IntPoint[] try_shove_via_points(TileShape p_obstacle_shape, int p_layer, Via p_via, int  p_cl_class_no,
            boolean p_extended_check, RoutingBoard p_board)
    {
        ShapeSearchTree search_tree = p_board.search_tree_manager.get_default_tree();
        TileShape curr_via_shape = p_via.get_tree_shape_on_layer(search_tree, p_layer);
        if (curr_via_shape == null)
        {
            return new IntPoint [0];
        }
        boolean is_int_octagon = p_obstacle_shape.is_IntOctagon();
        double clearance_value = p_board.clearance_value(p_cl_class_no, p_via.clearance_class_no(), p_layer);
        double shove_distance;
        if (p_board.rules.get_trace_angle_restriction() == AngleRestriction.NINETY_DEGREE ||is_int_octagon )
        {
            shove_distance =  0.5 * curr_via_shape.bounding_box().max_width();
            if (!search_tree.is_clearance_compensation_used())
            {
                shove_distance += clearance_value;
            }
        }
        else
        {
            // a different algorithm is used for calculating the new via centers
            shove_distance = 0;
            if (!search_tree.is_clearance_compensation_used())
            {
                // enlarge p_obstacle_shape and curr_via_shape by half of the clearance value to syncronize
                // with the check algorithm in ShapeSearchTree.overlapping_tree_entries_with_clearance
                shove_distance += 0.5 * clearance_value;
            }
        }
        
        // The additional constant 2 is an empirical value for the tolerance in case of diagonal shoving.
        shove_distance  += 2;
        
        IntPoint curr_via_center = (IntPoint) p_via.get_center();
        IntPoint [] try_via_centers;
        int try_count = 1;
        if (p_board.rules.get_trace_angle_restriction() == AngleRestriction.NINETY_DEGREE)
        {
            IntBox curr_offset_box = p_obstacle_shape.bounding_box().offset(shove_distance);
            if (p_extended_check)
            {
                try_count = 2;
            }
            try_via_centers = curr_offset_box.nearest_border_projections(curr_via_center, try_count);
        }
        else if (is_int_octagon)
        {
            IntOctagon curr_offset_octagon = p_obstacle_shape.bounding_octagon().enlarge(shove_distance);
            if (p_extended_check)
            {
                try_count = 4;
            }
            
            try_via_centers = curr_offset_octagon.nearest_border_projections(curr_via_center, try_count);
        }
        else
        {
            TileShape curr_offset_shape = (TileShape) p_obstacle_shape.enlarge(shove_distance);
            if (!search_tree.is_clearance_compensation_used())
            {
                curr_via_shape = (TileShape) curr_via_shape.enlarge(0.5 * clearance_value);
            }
            if (p_extended_check)
            {
                try_count = 4;
            }
            FloatPoint[] shove_deltas =  curr_offset_shape.nearest_relative_outside_locations(curr_via_shape, try_count);
            try_via_centers = new IntPoint[shove_deltas.length];
            for (int i = 0; i < try_via_centers.length; ++i)
            {
                Vector curr_delta = shove_deltas[i].round().difference_by(Point.ZERO);
                try_via_centers[i] = (IntPoint) curr_via_center.translate_by(curr_delta);
            }
        }
        return try_via_centers;
    }
}
