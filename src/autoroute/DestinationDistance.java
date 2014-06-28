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
 * DestinationDistance.java
 *
 * Created on 26. Januar 2004, 10:08
 */

package autoroute;

import geometry.planar.FloatPoint;
import geometry.planar.IntBox;
import autoroute.AutorouteControl.ExpansionCostFactor;



/**
 * Calculation of a good lower bound for the distance between a new MazeExpansionElement
 * and the destination set of the expansion.
 *
 * @author  Alfons Wirtz
 */
public class DestinationDistance
{
    
    /**
     * Creates a new instance of DestinationDistance.
     * p_trace_costs and p_layer_active are arrays of dimension layer_count.
     */
    public DestinationDistance( ExpansionCostFactor [] p_trace_costs,
            boolean [] p_layer_active, double p_min_normal_via_cost, double p_min_cheap_via_cost)
    {
        trace_costs = p_trace_costs;
        layer_active = p_layer_active;
        layer_count = p_layer_active.length;
        min_normal_via_cost = p_min_normal_via_cost;
        min_cheap_via_cost = p_min_cheap_via_cost;
        int curr_active_layer_count = 0;
        for (int ind = 0; ind < layer_count; ++ind)
        {
            if (layer_active[ind])
            {
                ++curr_active_layer_count;
            }
        }
        this.active_layer_count = curr_active_layer_count;
        
        if (layer_active[0])
        {
            if (trace_costs[0].horizontal  < trace_costs[0].vertical)
            {
                min_component_side_trace_cost = trace_costs[0].horizontal;
                max_component_side_trace_cost = trace_costs[0].vertical;
            }
            else
            {
                min_component_side_trace_cost = trace_costs[0].vertical;
                max_component_side_trace_cost = trace_costs[0].horizontal;
            }
        }
        
        if (layer_active[layer_count - 1])
        {
            ExpansionCostFactor curr_trace_cost = trace_costs[layer_count - 1];
            
            if (curr_trace_cost.horizontal < curr_trace_cost.vertical)
            {
                min_solder_side_trace_cost = curr_trace_cost.horizontal;
                max_solder_side_trace_cost = curr_trace_cost.vertical;
            }
            else
            {
                min_solder_side_trace_cost = curr_trace_cost.vertical;
                max_solder_side_trace_cost = curr_trace_cost.horizontal;
            }
        }
        
        // Note: for inner layers we assume, that cost in preferred direction is 1
        max_inner_side_trace_cost =
                Math.min(max_component_side_trace_cost, max_solder_side_trace_cost);
        for (int ind2 = 1; ind2 < layer_count - 1; ++ind2)
        {
            if (!layer_active[ind2])
            {
                continue;
            }
            double curr_max_cost = Math.max(trace_costs[ind2].horizontal, trace_costs[ind2].vertical);
            
            max_inner_side_trace_cost = Math.min(max_inner_side_trace_cost, curr_max_cost);
        }
        min_component_inner_trace_cost = Math.min(min_component_side_trace_cost, max_inner_side_trace_cost);
        min_solder_inner_trace_cost = Math.min(min_solder_side_trace_cost, max_inner_side_trace_cost);
        min_component_solder_inner_trace_cost = Math.min(min_component_inner_trace_cost, min_solder_inner_trace_cost);
    }
    
    public void join(IntBox p_box, int p_layer)
    {
        if (p_layer == 0)
        {
            component_side_box = component_side_box.union(p_box);
            component_side_box_is_empty = false;
        }
        else if (p_layer == layer_count - 1)
        {
            solder_side_box =solder_side_box.union(p_box);
            solder_side_box_is_empty = false;
        }
        else
        {
            inner_side_box = inner_side_box.union(p_box);
            inner_side_box_is_empty = false;
        }
        box_is_empty = false;
    }
    
    public double calculate(FloatPoint p_point, int p_layer)
    {
        return calculate( p_point.bounding_box(), p_layer);
    }
    
    public double calculate(IntBox p_box, int p_layer)
    {
        if (box_is_empty)
        {
            return Integer.MAX_VALUE;
        }
        
        double component_side_delta_x;
        double component_side_delta_y;
        
        if (p_box.ll.x > component_side_box.ur.x)
        {
            component_side_delta_x = p_box.ll.x - component_side_box.ur.x;
        }
        else if (p_box.ur.x < component_side_box.ll.x)
        {
            component_side_delta_x = component_side_box.ll.x - p_box.ur.x;
        }
        else
        {
            component_side_delta_x = 0;
        }
        
        if (p_box.ll.y > component_side_box.ur.y)
        {
            component_side_delta_y = p_box.ll.y - component_side_box.ur.y;
        }
        else if (p_box.ur.y < component_side_box.ll.y)
        {
            component_side_delta_y = component_side_box.ll.y - p_box.ur.y;
        }
        else
        {
            component_side_delta_y = 0;
        }
        
        double solder_side_delta_x;
        double solder_side_delta_y;
        
        if (p_box.ll.x > solder_side_box.ur.x)
        {
            solder_side_delta_x = p_box.ll.x - solder_side_box.ur.x;
        }
        else if (p_box.ur.x < solder_side_box.ll.x)
        {
            solder_side_delta_x = solder_side_box.ll.x - p_box.ur.x;
        }
        else
        {
            solder_side_delta_x = 0;
        }
        
        if (p_box.ll.y > solder_side_box.ur.y)
        {
            solder_side_delta_y = p_box.ll.y - solder_side_box.ur.y;
        }
        else if (p_box.ur.y < solder_side_box.ll.y)
        {
            solder_side_delta_y = solder_side_box.ll.y - p_box.ur.y;
        }
        else
        {
            solder_side_delta_y = 0;
        }
        
        double inner_side_delta_x;
        double inner_side_delta_y;
        
        if (p_box.ll.x > inner_side_box.ur.x)
        {
            inner_side_delta_x = p_box.ll.x - inner_side_box.ur.x;
        }
        else if (p_box.ur.x < inner_side_box.ll.x)
        {
            inner_side_delta_x = inner_side_box.ll.x - p_box.ur.x;
        }
        else
        {
            inner_side_delta_x = 0;
        }
        
        if (p_box.ll.y > inner_side_box.ur.y)
        {
            inner_side_delta_y = p_box.ll.y - inner_side_box.ur.y;
        }
        else if (p_box.ur.y < inner_side_box.ll.y)
        {
            inner_side_delta_y = inner_side_box.ll.y - p_box.ur.y;
        }
        else
        {
            inner_side_delta_y = 0;
        }
        
        double component_side_max_delta;
        double component_side_min_delta;
        
        if (component_side_delta_x > component_side_delta_y)
        {
            component_side_max_delta = component_side_delta_x;
            component_side_min_delta = component_side_delta_y;
        }
        else
        {
            component_side_max_delta = component_side_delta_y;
            component_side_min_delta = component_side_delta_x;
        }
        
        double solder_side_max_delta;
        double solder_side_min_delta;
        
        if (solder_side_delta_x > solder_side_delta_y)
        {
            solder_side_max_delta = solder_side_delta_x;
            solder_side_min_delta = solder_side_delta_y;
        }
        else
        {
            solder_side_max_delta = solder_side_delta_y;
            solder_side_min_delta = solder_side_delta_x;
        }
        
        double inner_side_max_delta;
        double inner_side_min_delta;
        
        if (inner_side_delta_x > inner_side_delta_y)
        {
            inner_side_max_delta = inner_side_delta_x;
            inner_side_min_delta = inner_side_delta_y;
        }
        else
        {
            inner_side_max_delta = inner_side_delta_y;
            inner_side_min_delta = inner_side_delta_x;
        }
        
        double result = Integer.MAX_VALUE;
        
        if (p_layer == 0)
            // calculate shortest distance to component side box
        {
            // calculate one layer distance
            
            if (!component_side_box_is_empty)
            {
                result =
                        p_box.weighted_distance(component_side_box,
                        trace_costs[0].horizontal, trace_costs[0].vertical);
            }
            
            if (active_layer_count <= 1)
            {
                return result;
            }
            
            // calculate two layer distance on component and solder side
            
            double tmp_distance;
            if (min_solder_side_trace_cost < min_component_side_trace_cost)
                tmp_distance =
                        min_solder_side_trace_cost * solder_side_max_delta
                        + min_component_side_trace_cost * solder_side_min_delta
                        + min_normal_via_cost;
            else
                tmp_distance =
                        min_component_side_trace_cost * solder_side_max_delta
                        + min_solder_side_trace_cost * solder_side_min_delta
                        + min_normal_via_cost;
            
            result = Math.min(result, tmp_distance);
            
            // calculate two layer distance on component and solde side
            // with two vias
            
            tmp_distance = component_side_max_delta
                    + component_side_min_delta * min_component_inner_trace_cost
                    + 2 * min_normal_via_cost;
            
            result = Math.min(result, tmp_distance);
            
            if (active_layer_count <= 2)
                return result;
            
            // calculate two layer distance on component side and an inner side
            
            tmp_distance = inner_side_max_delta +
                    inner_side_min_delta * min_component_inner_trace_cost +
                    min_normal_via_cost;
            
            result = Math.min(result, tmp_distance);
            
            // calculate three layer distance
            
            tmp_distance = solder_side_max_delta +
                    +min_component_solder_inner_trace_cost * solder_side_min_delta
                    + 2 * min_normal_via_cost;
            result = Math.min(result, tmp_distance);
            
            tmp_distance = component_side_max_delta +
                    component_side_min_delta + 2 * min_normal_via_cost;
            result = Math.min(result, tmp_distance);
            
            if (active_layer_count <= 3)
                return result;
            
            tmp_distance = inner_side_max_delta + inner_side_min_delta
                    + 2 * min_normal_via_cost;
            
            result = Math.min(result, tmp_distance);
            
            // calculate four layer distance
            
            tmp_distance = solder_side_max_delta + solder_side_min_delta
                    + 3 * min_normal_via_cost;
            
            result = Math.min(result, tmp_distance);
            
            return result;
        }
        if (p_layer == layer_count - 1)
            // calculate shortest distance to solder side box
        {
            // calculate one layer distance
            
            if (!solder_side_box_is_empty)
            {
                result =
                        p_box.weighted_distance(solder_side_box,
                        trace_costs[p_layer].horizontal, trace_costs[p_layer].vertical);
            }
            
            // calculate two layer distance
            double tmp_distance;
            if (min_component_side_trace_cost < min_solder_side_trace_cost)
            {
                tmp_distance =
                        min_component_side_trace_cost * component_side_max_delta
                        + min_solder_side_trace_cost * component_side_min_delta
                        + min_normal_via_cost;
            }
            else
            {
                tmp_distance =
                        min_solder_side_trace_cost * component_side_max_delta
                        + min_component_side_trace_cost * component_side_min_delta
                        + min_normal_via_cost;
            }
            result = Math.min(result, tmp_distance);
            tmp_distance = solder_side_max_delta
                    + solder_side_min_delta * min_solder_inner_trace_cost
                    + 2 * min_normal_via_cost;
            result = Math.min(result, tmp_distance);
            if (active_layer_count <= 2)
            {
                return result;
            }
            tmp_distance = inner_side_min_delta * min_solder_inner_trace_cost
                    + inner_side_max_delta + min_normal_via_cost;
            result = Math.min(result, tmp_distance);
            
            // calculate three layer distance
            
            tmp_distance = component_side_max_delta +
                    min_component_solder_inner_trace_cost * component_side_min_delta
                    + 2 * min_normal_via_cost;
            result = Math.min(result, tmp_distance);
            tmp_distance = solder_side_max_delta + solder_side_min_delta
                    + 2 * min_normal_via_cost;
            result = Math.min(result, tmp_distance);
            if (active_layer_count <= 3)
                return result;
            tmp_distance = inner_side_max_delta + inner_side_min_delta
                    + 2 * min_normal_via_cost;
            result = Math.min(result, tmp_distance);
            
            // calculate four layer distance
            
            tmp_distance = component_side_max_delta + component_side_min_delta
                    + 3 * min_normal_via_cost;
            result = Math.min(result, tmp_distance);
            return result;
        }
        
        // calculate distance to inner layer box
        
        // calculate one layer distance
        
        if (!inner_side_box_is_empty)
        {
            result =
                    p_box.weighted_distance(inner_side_box,
                    trace_costs[p_layer].horizontal, trace_costs[p_layer].vertical);
        }
        
        // calculate two layer distance
        
        double tmp_distance = inner_side_max_delta + inner_side_min_delta + min_normal_via_cost;
        
        result = Math.min(result, tmp_distance);
        tmp_distance = component_side_max_delta
                + component_side_min_delta * min_component_inner_trace_cost
                + min_normal_via_cost;
        result = Math.min(result, tmp_distance);
        tmp_distance = solder_side_max_delta
                + solder_side_min_delta * min_solder_inner_trace_cost
                + min_normal_via_cost;
        result = Math.min(result, tmp_distance);
        
        // calculate three layer distance
        
        tmp_distance = component_side_max_delta + component_side_min_delta
                + 2 * min_normal_via_cost;
        result = Math.min(result, tmp_distance);
        tmp_distance = solder_side_max_delta + solder_side_min_delta
                + 2 * min_normal_via_cost;
        result = Math.min(result, tmp_distance);
        
        return result;
    }
    
    public double calculate_cheap_distance(IntBox p_box, int p_layer)
    {
        double min_normal_via_cost_save = min_normal_via_cost;
        
        min_normal_via_cost = min_cheap_via_cost;
        double result = calculate(p_box, p_layer);
        
        min_normal_via_cost = min_normal_via_cost_save;
        return result;
    }
    
    
    
    
    private final ExpansionCostFactor [] trace_costs;
    private final boolean [] layer_active;
    private final int layer_count;
    private final int active_layer_count;
    
    private double min_normal_via_cost;
    private double min_cheap_via_cost;
    double min_component_side_trace_cost;
    double max_component_side_trace_cost;
    double min_solder_side_trace_cost;
    double max_solder_side_trace_cost;
    double max_inner_side_trace_cost;
    // minimum of the maximal trace costs on each inner layer
    double min_component_inner_trace_cost;
    // minimum of min_component_side_trace_cost and
    // max_inner_side_trace_cost
    double min_solder_inner_trace_cost;
    // minimum of min_solder_side_trace_cost and max_inner_side_trace_cost
    double min_component_solder_inner_trace_cost;
    // minimum of min_component_inner_trace_cost and
    // min_solder_inner_trace_cost
    private IntBox component_side_box = IntBox.EMPTY;
    private IntBox solder_side_box = IntBox.EMPTY;
    private IntBox inner_side_box = IntBox.EMPTY;
    
    private boolean  box_is_empty = true;
    private boolean  component_side_box_is_empty = true;
    private boolean  solder_side_box_is_empty = true;
    private boolean  inner_side_box_is_empty = true;
}
