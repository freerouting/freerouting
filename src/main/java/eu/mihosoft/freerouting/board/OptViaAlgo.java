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
 * OptViaAlgo.java
 *
 * Created on 31. Maerz 2006, 06:58
 *
 */

package board;

import java.util.Collection;
import java.util.Iterator;

import geometry.planar.Point;
import geometry.planar.IntPoint;
import geometry.planar.Vector;
import geometry.planar.Polyline;
import geometry.planar.FloatPoint;
import geometry.planar.FloatLine;
import geometry.planar.Side;

import autoroute.AutorouteControl.ExpansionCostFactor;

/**
 * Contains functions for optimizing and improving via locations.
 *
 * @author Alfons Wirtz
 */
public class OptViaAlgo
{
    
    /**
     * Optimizes the location of a via connected to at most 2 traces
     * according to the trace costs on the layers of the connected traces
     * If p_trace_cost_arr == null, the horizontal and vertical trace costs will be set to 1.
     * Returns false, if the via was not changed.
     */
    public static boolean opt_via_location(RoutingBoard p_board, Via p_via,
            ExpansionCostFactor[] p_trace_cost_arr, int p_trace_pull_tight_accuracy, int p_max_recursion_depth)
    {
        if (p_via.is_shove_fixed())
        {
            return false;
        }
        if (p_max_recursion_depth <= 0)
        {
            if (p_board.get_test_level().ordinal() >= TestLevel.CRITICAL_DEBUGGING_OUTPUT.ordinal())
            {
                System.out.println("OptViaAlgo.opt_via_location: probably endless loop") ;
            }
            return false;
        }
        Collection<Item> contacts = p_via.get_normal_contacts();
        boolean is_plane_or_fanout_via = (contacts.size() == 1);
        PolylineTrace first_trace = null;
        PolylineTrace second_trace = null;
        if (!is_plane_or_fanout_via)
        {
            if (contacts.size() != 2)
            {
                return false;
            }
            Iterator<Item> it = contacts.iterator();
            Item curr_item = it.next();
            if (curr_item.is_shove_fixed() || !(curr_item instanceof PolylineTrace))
            {
                if (curr_item instanceof ConductionArea)
                {
                    is_plane_or_fanout_via = true;
                }
                else
                {
                    return false;
                }
            }
            else
            {
                first_trace = (PolylineTrace) curr_item;
            }
            curr_item = it.next();
            if (curr_item.is_shove_fixed() || !(curr_item instanceof PolylineTrace))
            {
                if (curr_item instanceof ConductionArea)
                {
                    is_plane_or_fanout_via = true;
                }
                else
                {
                    return false;
                }
            }
            else
            {
                second_trace = (PolylineTrace) curr_item;
            }
        }
        if (is_plane_or_fanout_via)
        {
            return opt_plane_or_fanout_via(p_board, p_via, p_trace_pull_tight_accuracy, p_max_recursion_depth);
        }
        Point via_center = p_via.get_center();
        int first_layer = first_trace.get_layer();
        int second_layer = second_trace.get_layer();
        Point first_trace_from_corner;
        Point second_trace_from_corner;
        
        // calculate first_trace_from_corner and second_trace_from_corner
        
        if (first_trace.first_corner().equals(via_center))
        {
            first_trace_from_corner = first_trace.polyline().corner(1);
        }
        else if (first_trace.last_corner().equals(via_center))
        {
            first_trace_from_corner
                    = first_trace.polyline().corner(first_trace.polyline().corner_count() - 2);
        }
        else
        {
            System.out.println("OptViaAlgo.opt_via_location: incorrect first contact");
            return false;
        }
        
        if (second_trace.first_corner().equals(via_center))
        {
            second_trace_from_corner = second_trace.polyline().corner(1);
        }
        else if (second_trace.last_corner().equals(via_center))
        {
            second_trace_from_corner
                    = second_trace.polyline().corner(second_trace.polyline().corner_count() - 2);
        }
        else
        {
            System.out.println("OptViaAlgo.opt_via_location: incorrect second contact");
            return false;
        }
        
        ExpansionCostFactor first_layer_trace_costs;
        ExpansionCostFactor second_layer_trace_costs;
        if (p_trace_cost_arr != null)
        {
            first_layer_trace_costs = p_trace_cost_arr[first_layer];
            second_layer_trace_costs = p_trace_cost_arr[second_layer];
        }
        else
        {
            first_layer_trace_costs = new ExpansionCostFactor(1, 1);
            second_layer_trace_costs = first_layer_trace_costs;
        }
        
        Point new_location =
                reposition_via(p_board, p_via,
                first_trace.get_half_width(), first_trace.clearance_class_no(), first_trace.get_layer(),
                first_layer_trace_costs, first_trace_from_corner,
                second_trace.get_half_width(), second_trace.clearance_class_no(), second_trace.get_layer(),
                second_layer_trace_costs, second_trace_from_corner);
        if (new_location == null || new_location.equals(via_center))
        {
            return false;
        }
        Vector delta = new_location.difference_by(via_center);
        if (!MoveDrillItemAlgo.insert(p_via, delta, 9, 9, null, p_board))
        {
            System.out.println("OptViaAlgo.opt_via_location: move via failed");
            return false;
        }
        ItemSelectionFilter filter = new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.TRACES);
        Collection<Item> picked_items = p_board.pick_items(new_location, first_trace.get_layer(), filter);
        for (Item curr_item : picked_items)
        {
            ((PolylineTrace) curr_item).pull_tight(true, p_trace_pull_tight_accuracy, null);
        }
        picked_items = p_board.pick_items(new_location, second_trace.get_layer(), filter);
        for (Item curr_item : picked_items)
        {
            ((PolylineTrace) curr_item).pull_tight(true, p_trace_pull_tight_accuracy, null);
        }
        filter = new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.VIAS);
        picked_items = p_board.pick_items(new_location, first_trace.get_layer(), filter);
        for (Item curr_item : picked_items)
        {
            opt_via_location(p_board, (Via) curr_item, p_trace_cost_arr,
                    p_trace_pull_tight_accuracy, p_max_recursion_depth - 1);
            break;
        }
        return true;
    }
    
    /**
     * Optimisations for vias with only 1 connected Trace (Plane or Fanout Vias).
     */
    private static boolean opt_plane_or_fanout_via(RoutingBoard p_board, Via p_via,
            int p_trace_pull_tight_accuracy, int p_max_recursion_depth)
    {
        if (p_max_recursion_depth <= 0)
        {
            if (p_board.get_test_level().ordinal() >= TestLevel.CRITICAL_DEBUGGING_OUTPUT.ordinal())
            {
                System.out.println("OptViaAlgo.opt_plane_or_fanout_via: probably endless loop") ;
            }
            return false;
        }
        Collection<Item> contact_list = p_via.get_normal_contacts();
        if (contact_list.isEmpty())
        {
            return false;
        }
        ConductionArea contact_plane = null;
        PolylineTrace contact_trace = null;
        for (Item curr_contact : contact_list)
        {
            if (curr_contact instanceof ConductionArea)
            {
                if (contact_plane != null)
                {
                    return false;
                }
                contact_plane = (ConductionArea) curr_contact;
            }
            else if (curr_contact instanceof PolylineTrace)
            {
                if (curr_contact.is_shove_fixed() || contact_trace != null)
                {
                    return false;
                }
                contact_trace = (PolylineTrace) curr_contact;
            }
            else
            {
                return false;
            }
        }
        if (contact_trace == null)
        {
            return false;
        }
        Point via_center = p_via.get_center();
        boolean at_first_corner;
        if (contact_trace.first_corner().equals(via_center))
        {
            at_first_corner = true;
        }
        else if (contact_trace.last_corner().equals(via_center))
        {
            at_first_corner = false;
        }
        else
        {
            System.out.println("OptViaAlgo.opt_plane_or_fanout_via: unconsistant contact");
            return false;
        }
        Polyline trace_polyline = contact_trace.polyline();
        Point check_corner;
        if (at_first_corner)
        {
            check_corner = trace_polyline.corner(1);
        }
        else
        {
            check_corner = trace_polyline.corner(trace_polyline.corner_count() - 2);
        }
        IntPoint rounded_check_corner = check_corner.to_float().round();
        int trace_half_width = contact_trace.get_half_width();
        int trace_layer = contact_trace.get_layer();
        int trace_cl_class_no = contact_trace.clearance_class_no();
        Point new_via_location =
                reposition_via(p_board, p_via, rounded_check_corner, trace_half_width ,
                trace_layer, trace_cl_class_no);
        if (new_via_location == null && trace_polyline.corner_count() >= 3)
        {
            
            // try to project the via to the previous line
            Point   prev_corner;
            
            if (at_first_corner)
            {
                prev_corner = trace_polyline.corner(2);
            }
            else
            {
                prev_corner = trace_polyline.corner(trace_polyline.corner_count() - 3);
            }
            FloatPoint float_check_corner = check_corner.to_float();
            FloatPoint float_via_center = via_center.to_float();
            FloatPoint float_prev_corner = prev_corner.to_float();
            if (float_check_corner.scalar_product(float_via_center, float_prev_corner) != 0)
            {
                FloatLine curr_line = new FloatLine(float_check_corner, float_prev_corner);
                Point projection = curr_line.perpendicular_projection(float_via_center).round();
                Vector diff_vector = projection.difference_by(via_center);
                boolean projection_ok = true;
                AngleRestriction angle_restriction = p_board.rules.get_trace_angle_restriction();
                if (projection.equals(via_center) ||
                        angle_restriction == AngleRestriction.NINETY_DEGREE && !diff_vector.is_orthogonal() ||
                        angle_restriction == AngleRestriction.FORTYFIVE_DEGREE && !diff_vector.is_multiple_of_45_degree())
                {
                    projection_ok = false;
                }
                if (projection_ok)
                {
                    if(MoveDrillItemAlgo.check(p_via, diff_vector, 0, 0, null, p_board, null))
                    {
                        double ok_length =
                                p_board.check_trace_segment(via_center, projection, trace_layer, p_via.net_no_arr,
                                trace_half_width, trace_cl_class_no, false);
                        if (ok_length >= Integer.MAX_VALUE)
                        {
                            new_via_location = projection;
                        }
                    }
                    
                }
            }
        }
        if (new_via_location == null)
        {
            return false;
        }
        if (contact_plane != null)
        {
            // check, that the new location is inside the contact plane
            ItemSelectionFilter filter = new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.CONDUCTION);
            Collection<Item> picked_items = p_board.pick_items(new_via_location, contact_plane.get_layer(), filter);
            boolean contact_ok = false;
            for (Item curr_item : picked_items)
            {
                if (curr_item == contact_plane)
                {
                    contact_ok = true;
                    break;
                }
            }
            if (!contact_ok)
            {
                return false;
            }
        }
        Vector diff_vector = new_via_location.difference_by(via_center);
        if (!MoveDrillItemAlgo.insert(p_via, diff_vector, 9, 9, null, p_board))
        {
            System.out.println("OptViaAlgo.opt_plane_or_fanout_via: move via failed");
            return false;
        }
        ItemSelectionFilter filter = new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.TRACES);
        Collection<Item> picked_items = p_board.pick_items(new_via_location, contact_trace.get_layer(), filter);
        for (Item curr_item : picked_items)
        {
            ((PolylineTrace) curr_item).pull_tight(true, p_trace_pull_tight_accuracy, null);
        }
        if (new_via_location.equals(check_corner))
        {
            opt_plane_or_fanout_via(p_board, p_via, p_trace_pull_tight_accuracy, p_max_recursion_depth - 1);
        }
        return true;
    }
    
    /**
     * Tries to move the via into the direction of p_to_location as far as possible
     * Return the new location of the via, or null, if no move was possible.
     */
    private static Point reposition_via(RoutingBoard p_board, Via p_via,
            IntPoint p_to_location, int p_trace_half_width, int p_trace_layer, int p_trace_cl_class)
            
    {
        Point   from_location = p_via.get_center();
        
        if (from_location.equals(p_to_location))
        {
            return null;
        }
        
        double ok_length = p_board.check_trace_segment(from_location, p_to_location, p_trace_layer, p_via.net_no_arr,
                p_trace_half_width, p_trace_cl_class, false);
        if (ok_length <= 0)
        {
            return null;
        }
        FloatPoint float_from_location = from_location.to_float();
        FloatPoint float_to_location = p_to_location.to_float();
        FloatPoint new_float_to_location;
        if (ok_length >= Integer.MAX_VALUE)
        {
            new_float_to_location = float_to_location;
        }
        else
        {
            new_float_to_location = float_from_location.change_length(float_to_location, ok_length);
        }
        Point new_to_location = new_float_to_location.round();
        Vector delta = new_to_location.difference_by(from_location);
        boolean check_ok =
                MoveDrillItemAlgo.check(p_via, delta, 0, 0, null, p_board, null);
        
        if (check_ok)
        {
            return new_to_location;
        }
        
        final double c_min_length = 0.3 * p_trace_half_width + 1;
        
        ok_length = Math.min(ok_length, float_from_location.distance(float_to_location));
        
        double curr_length = ok_length / 2;
        
        ok_length = 0;
        Point result = null;
        
        while (curr_length >= c_min_length)
        {
            Point check_point = float_from_location.change_length(float_to_location, ok_length + curr_length).round();
            
            delta = check_point.difference_by(from_location);
            if (MoveDrillItemAlgo.check(p_via, delta, 0, 0, null, p_board, null))
            {
                ok_length += curr_length;
                result = check_point;
            }
            curr_length /= 2;
        }
        return result;
    }
    
    private static boolean reposition_via(RoutingBoard p_board, Via p_via,
            IntPoint p_to_location, int p_trace_half_width_1, int p_trace_layer_1, int p_trace_cl_class_1,
            IntPoint p_connect_location, int p_trace_half_width_2, int p_trace_layer_2, int p_trace_cl_class_2)
            
    {
        Point   from_location = p_via.get_center();
        
        if (from_location.equals(p_to_location))
        {
            if (p_board.get_test_level() == TestLevel.ALL_DEBUGGING_OUTPUT)
            {
                System.out.println("OptViaAlgo.reposition_via: from_location equal p_to_location");
            }
            return false;
        }
        
        Vector delta = p_to_location.difference_by(from_location);
        
        if ( p_board.rules.get_trace_angle_restriction() == AngleRestriction.NONE && delta.length_approx() <= 1.5)
        {
            // PullTightAlgoAnyAngle.reduce_corners mmay not be able to remove the new generated overlap
            // because of numerical stability problems
            // That would result in an endless loop with removing the generated acute angle in reposition_via.
            return false;
        }
        
        int[] net_no_arr = p_via.net_no_arr;
        
        double ok_length = p_board.check_trace_segment(from_location, p_to_location, p_trace_layer_1, net_no_arr,
                p_trace_half_width_1, p_trace_cl_class_1, false);
        
        if (ok_length < Integer.MAX_VALUE)
        {
            return false;
        }
        
        ok_length = p_board.check_trace_segment(p_to_location, p_connect_location, p_trace_layer_2, net_no_arr,
                p_trace_half_width_2, p_trace_cl_class_2, false);
        
        if (ok_length < Integer.MAX_VALUE)
        {
            return false;
        }
        if (!MoveDrillItemAlgo.check(p_via, delta, 0, 0, null, p_board, null))
        {
            return false;
        }
        return true;
    }
    
    /**
     * Tries to reposition the via to a better location according to the
     * trace costs. Returns null, if no better location was found.
     */
    private static Point reposition_via(RoutingBoard p_board, Via p_via,
            int p_first_trace_half_width, int p_first_trace_cl_class, int p_first_trace_layer,
            ExpansionCostFactor p_first_trace_costs, Point p_first_trace_from_corner,
            int p_second_trace_half_width, int p_second_trace_cl_class, int p_second_trace_layer,
            ExpansionCostFactor p_second_trace_costs, Point p_second_trace_from_corner)
    {
        Point   via_location = p_via.get_center();
        
        Vector first_delta = p_first_trace_from_corner.difference_by(via_location);
        Vector second_delta = p_second_trace_from_corner.difference_by(via_location);
        double scalar_product = first_delta.scalar_product(second_delta);
        
        FloatPoint float_via_location = via_location.to_float();
        FloatPoint float_first_trace_from_corner = p_first_trace_from_corner.to_float();
        FloatPoint float_second_trace_from_corner = p_second_trace_from_corner.to_float();
        double first_trace_from_corner_distance = float_via_location.distance(float_first_trace_from_corner);
        double second_trace_from_corner_distance = float_via_location.distance(float_second_trace_from_corner);
        IntPoint rounded_first_trace_from_corner = float_first_trace_from_corner.round();
        IntPoint rounded_second_trace_from_corner = float_second_trace_from_corner.round();
        
        // handle case of overlapping lines first
        
        if (via_location.side_of(p_first_trace_from_corner, p_second_trace_from_corner) == Side.COLLINEAR
                && scalar_product > 0)
        {
            if (second_trace_from_corner_distance < first_trace_from_corner_distance)
            {
                return reposition_via(p_board, p_via, rounded_second_trace_from_corner,
                        p_first_trace_half_width, p_first_trace_layer, p_first_trace_cl_class);
            }
            return reposition_via(p_board, p_via, rounded_first_trace_from_corner,
                    p_second_trace_half_width, p_second_trace_layer, p_second_trace_cl_class);
        }
        Point result = null;
        
        double curr_weighted_distance_1 =
                float_via_location.weighted_distance(float_first_trace_from_corner, p_first_trace_costs.horizontal,
                p_first_trace_costs.vertical);
        double curr_weighted_distance_2 =
                float_via_location.weighted_distance(float_first_trace_from_corner, p_second_trace_costs.horizontal,
                p_second_trace_costs.vertical);
        
        if (curr_weighted_distance_1 > curr_weighted_distance_2)
        {
            // try to move the via in direction of p_first_trace_from_corner
            result = reposition_via(p_board, p_via, rounded_first_trace_from_corner,
                    p_second_trace_half_width, p_second_trace_layer, p_second_trace_cl_class);
            if (result != null)
            {
                return result;
            }
        }
        
        curr_weighted_distance_1 =
                float_via_location.weighted_distance(float_second_trace_from_corner, p_second_trace_costs.horizontal,
                p_second_trace_costs.vertical);
        curr_weighted_distance_2 =
                float_via_location.weighted_distance(float_second_trace_from_corner, p_first_trace_costs.horizontal,
                p_first_trace_costs.vertical);
        
        if (curr_weighted_distance_1 > curr_weighted_distance_2)
        {
            // try to move the via in direction of p_second_trace_from_corner
            result = reposition_via(p_board, p_via, rounded_second_trace_from_corner,
                    p_first_trace_half_width, p_first_trace_layer, p_first_trace_cl_class);
            if (result != null)
            {
                return result;
            }
        }
        if (scalar_product > 0 && p_board.rules.get_trace_angle_restriction() != AngleRestriction.NINETY_DEGREE)
        {
            // acute angle
            IntPoint to_point_1;
            IntPoint to_point_2;
            FloatPoint float_to_point_1;
            FloatPoint float_to_point_2;
            if (first_trace_from_corner_distance < second_trace_from_corner_distance)
            {
                to_point_1 = rounded_first_trace_from_corner;
                float_to_point_1 = float_first_trace_from_corner;
                float_to_point_2 =
                        float_via_location.change_length(float_second_trace_from_corner,
                        first_trace_from_corner_distance);
                to_point_2 =  float_to_point_2.round();
            }
            else
            {
                float_to_point_1 =
                        float_via_location.change_length(float_first_trace_from_corner,
                        second_trace_from_corner_distance);
                to_point_1 = float_to_point_1.round();
                to_point_2 = rounded_second_trace_from_corner;
                float_to_point_2 = float_second_trace_from_corner;
            }
            curr_weighted_distance_1 =
                    float_to_point_1.weighted_distance(float_to_point_2, p_first_trace_costs.horizontal,
                    p_first_trace_costs.vertical);
            curr_weighted_distance_2 =
                    float_to_point_1.weighted_distance(float_to_point_2, p_second_trace_costs.horizontal,
                    p_second_trace_costs.vertical);
            
            if (curr_weighted_distance_1 > curr_weighted_distance_2)
            {
                // try moving the via first into the direction of to_point_1
                result =  reposition_via(p_board, p_via, to_point_1,
                        p_second_trace_half_width, p_second_trace_layer, p_second_trace_cl_class);
                if (result == null)
                {
                    result =  reposition_via(p_board, p_via, to_point_2,
                            p_first_trace_half_width, p_first_trace_layer, p_first_trace_cl_class);
                }
            }
            else
            {
                // try moving the via first into the direction of to_point_2
                result =  reposition_via(p_board, p_via, to_point_2,
                        p_first_trace_half_width, p_first_trace_layer, p_first_trace_cl_class);
                if (result == null)
                {
                    result =  reposition_via(p_board, p_via, to_point_1,
                            p_second_trace_half_width, p_second_trace_layer, p_second_trace_cl_class);
                }
            }
            if (result != null)
            {
                return result;
            }
        }
        
        // try decomposition in axisparallel parts
        
        if (!first_delta.is_orthogonal())
        {
            FloatPoint float_check_location = new FloatPoint(float_via_location.x, float_first_trace_from_corner.y);
            
            curr_weighted_distance_1 =
                    float_via_location.weighted_distance(float_first_trace_from_corner, p_first_trace_costs.horizontal,
                    p_first_trace_costs.vertical);
            curr_weighted_distance_2 =
                    float_via_location.weighted_distance(float_check_location, p_second_trace_costs.horizontal,
                    p_second_trace_costs.vertical);
            double curr_weighted_distance_3 =
                    float_check_location.weighted_distance(float_first_trace_from_corner, p_first_trace_costs.horizontal,
                    p_first_trace_costs.vertical);
            
            if (curr_weighted_distance_1 > curr_weighted_distance_2 + curr_weighted_distance_3)
            {
                IntPoint check_location = float_check_location.round();
                boolean check_ok =
                        reposition_via(p_board, p_via, check_location, p_second_trace_half_width,
                        p_second_trace_layer, p_second_trace_cl_class, rounded_first_trace_from_corner,
                        p_first_trace_half_width, p_first_trace_layer, p_first_trace_cl_class);
                if (check_ok)
                {
                    return check_location;
                }
            }
            
            float_check_location = new FloatPoint(float_first_trace_from_corner.x, float_via_location.y);
            
            curr_weighted_distance_2 =
                    float_via_location.weighted_distance(float_check_location, p_second_trace_costs.horizontal,
                    p_second_trace_costs.vertical);
            curr_weighted_distance_3 =
                    float_check_location.weighted_distance(float_first_trace_from_corner, p_first_trace_costs.horizontal,
                    p_first_trace_costs.vertical);
            
            if (curr_weighted_distance_1 > curr_weighted_distance_2 + curr_weighted_distance_3)
            {
                IntPoint check_location = float_check_location.round();
                boolean check_ok =
                        reposition_via(p_board, p_via, check_location, p_second_trace_half_width,
                        p_second_trace_layer, p_second_trace_cl_class, rounded_first_trace_from_corner,
                        p_first_trace_half_width, p_first_trace_layer, p_first_trace_cl_class);
                if (check_ok)
                {
                    return check_location;
                }
            }
        }
        
        if (!second_delta.is_orthogonal())
        {
            FloatPoint float_check_location = new FloatPoint(float_via_location.x, float_second_trace_from_corner.y);
            
            curr_weighted_distance_1 =
                    float_via_location.weighted_distance(float_second_trace_from_corner, p_second_trace_costs.horizontal,
                    p_second_trace_costs.vertical);
            curr_weighted_distance_2 =
                    float_via_location.weighted_distance(float_check_location, p_first_trace_costs.horizontal,
                    p_first_trace_costs.vertical);
            double curr_weighted_distance_3 =
                    float_check_location.weighted_distance(float_second_trace_from_corner, p_second_trace_costs.horizontal,
                    p_second_trace_costs.vertical);
            
            if (curr_weighted_distance_1 > curr_weighted_distance_2 + curr_weighted_distance_3)
            {
                IntPoint check_location = float_check_location.round();
                boolean check_ok =
                        reposition_via(p_board, p_via,  check_location, p_first_trace_half_width,
                        p_first_trace_layer, p_first_trace_cl_class, rounded_second_trace_from_corner,
                        p_second_trace_half_width, p_second_trace_layer, p_second_trace_cl_class);
                if (check_ok)
                {
                    return check_location;
                }
            }
            
            float_check_location = new FloatPoint(float_second_trace_from_corner.x, float_via_location.y);
            
            curr_weighted_distance_2 =
                    float_via_location.weighted_distance(float_check_location, p_first_trace_costs.horizontal,
                    p_first_trace_costs.vertical);
            curr_weighted_distance_3 =
                    float_check_location.weighted_distance(float_second_trace_from_corner, p_second_trace_costs.horizontal,
                    p_second_trace_costs.vertical);
            
            if (curr_weighted_distance_1 > curr_weighted_distance_2 + curr_weighted_distance_3)
            {
                IntPoint check_location = float_check_location.round();
                boolean check_ok =
                        reposition_via(p_board, p_via, check_location, p_first_trace_half_width,
                        p_first_trace_layer, p_first_trace_cl_class, rounded_second_trace_from_corner,
                        p_second_trace_half_width, p_second_trace_layer, p_second_trace_cl_class);
                if (check_ok)
                {
                    return check_location;
                }
            }
        }
        return null;
    }
}
