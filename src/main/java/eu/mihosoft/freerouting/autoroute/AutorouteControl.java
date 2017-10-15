/*
 *  Copyright (C) 2014  Alfons Wirtz
 *  website www.freerouting.net
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
 * AutorouteControl.java
 *
 * Created on 25. Januar 2004, 09:38
 */
package autoroute;

import geometry.planar.ConvexShape;

import rules.ViaInfo;
import rules.ViaRule;

import board.RoutingBoard;

/**
 * Structure for controlling the autoroute algorithm.
 *
 * @author  Alfons Wirtz
 */
public class AutorouteControl
{

    /** Creates a new instance of AutorouteControl for the input net */
    public AutorouteControl(RoutingBoard p_board, int p_net_no, interactive.Settings p_settings)
    {
        this(p_board, p_settings, p_settings.autoroute_settings.get_trace_cost_arr());
        init_net(p_net_no, p_board, p_settings.autoroute_settings.get_via_costs());
    }

    /** Creates a new instance of AutorouteControl for the input net */
    public AutorouteControl(RoutingBoard p_board, int p_net_no, interactive.Settings p_settings, int p_via_costs, ExpansionCostFactor[] p_trace_cost_arr)
    {
        this(p_board, p_settings, p_trace_cost_arr);
        init_net(p_net_no, p_board, p_via_costs);
    }

    /** Creates a new instance of AutorouteControl */
    private AutorouteControl(RoutingBoard p_board, interactive.Settings p_settings,
                             ExpansionCostFactor[] p_trace_costs_arr)
    {
        layer_count = p_board.get_layer_count();
        trace_half_width = new int[layer_count];
        compensated_trace_half_width = new int[layer_count];
        layer_active = new boolean[layer_count];
        vias_allowed = p_settings.autoroute_settings.get_vias_allowed();
        via_radius_arr = new double[layer_count];
        add_via_costs = new ViaCost[layer_count];

        for (int i = 0; i < layer_count; ++i)
        {
            add_via_costs[i] = new ViaCost(layer_count);
            layer_active[i] = p_settings.autoroute_settings.get_layer_active(i);
        }
        is_fanout = false;
        remove_unconnected_vias = true;
        with_neckdown = p_settings.get_automatic_neckdown();
        tidy_region_width = Integer.MAX_VALUE;
        pull_tight_accuracy = 500;
        max_shove_trace_recursion_depth = 20;
        max_shove_via_recursion_depth = 5;
        max_spring_over_recursion_depth = 5;
        for (int i = 0; i < layer_count; ++i)
        {
            for (int j = 0; j < layer_count; ++j)
            {
                add_via_costs[i].to_layer[j] = 0;
            }
        }
        trace_costs = p_trace_costs_arr;
        attach_smd_allowed = false;
        via_lower_bound = 0;
        via_upper_bound = layer_count;

        ripup_allowed = false;
        ripup_costs = 1000;
        ripup_pass_no = 1;
    }

    private void init_net(int p_net_no, RoutingBoard p_board, int p_via_costs)
    {
        net_no = p_net_no;
        rules.Net curr_net = p_board.rules.nets.get(p_net_no);
        rules.NetClass curr_net_class;
        if (curr_net != null)
        {
            curr_net_class = curr_net.get_class();
            trace_clearance_class_no = curr_net_class.get_trace_clearance_class();
            via_rule = curr_net_class.get_via_rule();
        }
        else
        {
            trace_clearance_class_no = 1;
            via_rule = p_board.rules.via_rules.firstElement();
            curr_net_class = null;
        }
        for (int i = 0; i < layer_count; ++i)
        {
            if (net_no > 0)
            {
                trace_half_width[i] = p_board.rules.get_trace_half_width(net_no, i);
            }
            else
            {
                trace_half_width[i] = p_board.rules.get_trace_half_width(1, i);
            }
            compensated_trace_half_width[i] = trace_half_width[i] + p_board.rules.clearance_matrix.clearance_compensation_value(trace_clearance_class_no, i);
            if (curr_net_class != null && !curr_net_class.is_active_routing_layer(i))
            {
                layer_active[i] = false;
            }
        }
        if (via_rule.via_count() > 0)
        {
            this.via_clearance_class = via_rule.get_via(0).get_clearance_class();
        }
        else
        {
            this.via_clearance_class = 1;
        }
        this.via_info_arr = new ViaMask[via_rule.via_count()];
        for (int i = 0; i < via_rule.via_count(); ++i)
        {
            ViaInfo curr_via = via_rule.get_via(i);
            if (curr_via.attach_smd_allowed())
            {
                this.attach_smd_allowed = true;
            }
            library.Padstack curr_via_padstack = curr_via.get_padstack();
            int from_layer = curr_via_padstack.from_layer();
            int to_layer = curr_via_padstack.to_layer();
            for (int j = from_layer; j <= to_layer; ++j)
            {
                ConvexShape curr_shape = curr_via_padstack.get_shape(j);
                double curr_radius;
                if( curr_shape != null)
                {
                   curr_radius = 0.5 * curr_shape.max_width();
                }
                else
                {
                    curr_radius = 0;
                }
                this.via_radius_arr[j] = Math.max(this.via_radius_arr[j], curr_radius);
            }
            via_info_arr[i] = new ViaMask(from_layer, to_layer, curr_via.attach_smd_allowed());
        }
        for (int j = 0; j < this.layer_count; ++j)
        {
            this.via_radius_arr[j] = Math.max(this.via_radius_arr[j], trace_half_width[j]);
            this.max_via_radius = Math.max(this.max_via_radius, this.via_radius_arr[j]);
        }
        double via_cost_factor = this.max_via_radius;
        via_cost_factor = Math.max(via_cost_factor, 1);
        min_normal_via_cost = p_via_costs * via_cost_factor;
        min_cheap_via_cost = 0.8 * min_normal_via_cost;
    }
    final int layer_count;
    /** The horizontal and vertical trace costs on each layer */
    public final ExpansionCostFactor[] trace_costs;
    /** Defines for each layer, if it may used for routing. */
    final boolean[] layer_active;
    /** The currently used net number in the autoroute algorithm */
    int net_no;
    /** The currently used trace half widths in the autoroute algorithm on each layer */
    final int[] trace_half_width;
    /**
     * The currently used compensated trace half widths in the autoroute algorithm on each layer.
     * Equal to trace_half_width if no clearance compensation is used.
     */
    final int[] compensated_trace_half_width;
    /** The currently used clearance class for traces in the autoroute algorithm */
    public int trace_clearance_class_no;
    /** The currently used clearance class for vias in the autoroute algorithm */
    int via_clearance_class;
    /** The possible (partial) vias, which can be used by the autorouter */
    ViaRule via_rule;
    /** The array of possible via ranges used bei the autorouter */
    ViaMask[] via_info_arr;
    /** The lower bound for the first layer of vias */
    int via_lower_bound;
    /** The upper bound for the last layer of vias */
    int via_upper_bound;
    final double[] via_radius_arr;
    double max_via_radius;
    /** The width of the region around changed traces, where traces are pulled tight */
    int tidy_region_width;
    /** The pull tight accuracy of traces*/
    int pull_tight_accuracy;
    /** The maximum recursion depth for shoving traces */
    int max_shove_trace_recursion_depth;
    /** The maximum recursion depth for shoving obstacles */
    int max_shove_via_recursion_depth;
    /** The maximum recursion depth for traces springing over obstacles */
    int max_spring_over_recursion_depth;
    /** True, if layer change by inserting of vias is allowed */
    public boolean vias_allowed;
    /** True, if vias may drill to the pad of SMD pins */
    public boolean attach_smd_allowed;
    /** the additiomal costs to min_normal via_cost for inserting a via between 2 layers */
    final ViaCost[] add_via_costs;
    /** The minimum cost valua of all normal vias */
    public double min_normal_via_cost;
    /** The minimal cost value of all cheap vias */
    double min_cheap_via_cost;
    public boolean ripup_allowed;
    public int ripup_costs;
    public int ripup_pass_no;
    public final boolean with_neckdown;
    /** If true, the autoroute algorithm completes after the first drill */
    public boolean is_fanout;
    /**
     *  Normally true, if the autorouter contains no fanout pass
     */
    public boolean remove_unconnected_vias;

    /** horizontal and vertical costs for traces on a board layer */
    public static class ExpansionCostFactor
    {

        public ExpansionCostFactor(double p_horizontal, double p_vertical)
        {
            horizontal = p_horizontal;
            vertical = p_vertical;
        }
        /** The horizontal expansion cost factor on a layer of the board */
        public final double horizontal;
        /** The verical expansion cost factor on a layer of the board */
        public final double vertical;
    }

    /** Array of via costs from one layer to the other layers */
    static class ViaCost
    {

        private ViaCost(int p_layer_count)
        {
            to_layer = new int[p_layer_count];
        }
        public int[] to_layer;
    }

    static class ViaMask
    {

        ViaMask(int p_from_layer, int p_to_layer, boolean p_attach_smd_allowed)
        {
            from_layer = p_from_layer;
            to_layer = p_to_layer;
            attach_smd_allowed = p_attach_smd_allowed;
        }
        final int from_layer;
        final int to_layer;
        final boolean attach_smd_allowed;
    }
}
