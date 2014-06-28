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
 * AutorouteSettings.java
 *
 * Created on 27. Juli 2006, 09:16
 *
 */
package interactive;

import board.RoutingBoard;
import autoroute.AutorouteControl.ExpansionCostFactor;

/**
 * Contains the interactive settings for the autorouter.
 *
 * @author Alfons Wirtz
 */
public class AutorouteSettings implements java.io.Serializable
{

    /** Creates a new instance of AutorouteSettings */
    public AutorouteSettings(int p_layer_count)
    {
        layer_active_arr = new boolean[p_layer_count];
        preferred_direction_is_horizontal_arr = new boolean[p_layer_count];
        preferred_direction_trace_cost_arr = new double[p_layer_count];
        against_preferred_direction_trace_cost_arr = new double[p_layer_count];
    }

    /** Creates a new instance of AutorouteSettings */
    public AutorouteSettings(RoutingBoard p_board)
    {
        this(p_board.get_layer_count());

        // set default values

        start_ripup_costs = 100;
        start_pass_no = 1;
        vias_allowed = true;
        with_fanout = false;
        with_autoroute = true;
        with_postroute = true;
        via_costs = 50;
        plane_via_costs = 5;

        double horizontal_width = p_board.bounding_box.width();
        double vertical_width = p_board.bounding_box.height();

        int layer_count = p_board.get_layer_count();

        // additional costs aagainst  preferred direcction with 1 digit behind the decimal point.
        double horizontal_add_costs_against_preferred_dir = 0.1 * Math.round(10 * horizontal_width / vertical_width);

        double vertical_add_costs_against_preferred_dir = 0.1 * Math.round(10 * vertical_width / horizontal_width);

        // make more horizontal pefered direction, if the board is horizontal.

        boolean curr_preferred_direction_is_horizontal = horizontal_width < vertical_width;
        for (int i = 0; i < layer_count; ++i)
        {
            layer_active_arr[i] = p_board.layer_structure.arr[i].is_signal;
            if (p_board.layer_structure.arr[i].is_signal)
            {
                curr_preferred_direction_is_horizontal = !curr_preferred_direction_is_horizontal;
            }
            preferred_direction_is_horizontal_arr[i] = curr_preferred_direction_is_horizontal;
            preferred_direction_trace_cost_arr[i] = 1;
            against_preferred_direction_trace_cost_arr[i] = 1;
            if (curr_preferred_direction_is_horizontal)
            {
                against_preferred_direction_trace_cost_arr[i] += horizontal_add_costs_against_preferred_dir;
            }
            else
            {
                against_preferred_direction_trace_cost_arr[i] += vertical_add_costs_against_preferred_dir;
            }
        }
        int signal_layer_count = p_board.layer_structure.signal_layer_count();
        if (signal_layer_count > 2)
        {
            double outer_add_costs = 0.2 * signal_layer_count;
            // increase costs on the outer layers.
            preferred_direction_trace_cost_arr[0] += outer_add_costs;
            preferred_direction_trace_cost_arr[layer_count - 1] += outer_add_costs;
            against_preferred_direction_trace_cost_arr[0] += outer_add_costs;
            against_preferred_direction_trace_cost_arr[layer_count - 1] += outer_add_costs;
        }
    }

    /**
     * Copy constructor
     */
    public AutorouteSettings(AutorouteSettings p_settings)
    {
        start_ripup_costs = p_settings.start_ripup_costs;
        start_pass_no = p_settings.start_pass_no;
        via_costs = p_settings.via_costs;
        plane_via_costs = p_settings.plane_via_costs;
        layer_active_arr = new boolean[p_settings.layer_active_arr.length];
        System.arraycopy(p_settings.layer_active_arr, 0, this.layer_active_arr, 0, layer_active_arr.length);
        preferred_direction_is_horizontal_arr = new boolean[p_settings.preferred_direction_is_horizontal_arr.length];
        System.arraycopy(p_settings.preferred_direction_is_horizontal_arr, 0, this.preferred_direction_is_horizontal_arr, 0,
                preferred_direction_is_horizontal_arr.length);
        preferred_direction_trace_cost_arr = new double[p_settings.preferred_direction_trace_cost_arr.length];
        System.arraycopy(p_settings.preferred_direction_trace_cost_arr, 0, preferred_direction_trace_cost_arr, 0,
                preferred_direction_trace_cost_arr.length);
        against_preferred_direction_trace_cost_arr = new double[p_settings.against_preferred_direction_trace_cost_arr.length];
        System.arraycopy(p_settings.against_preferred_direction_trace_cost_arr, 0, against_preferred_direction_trace_cost_arr, 0,
                against_preferred_direction_trace_cost_arr.length);
    }

    public void set_start_ripup_costs(int p_value)
    {
        start_ripup_costs = Math.max(p_value, 1);
    }

    public int get_start_ripup_costs()
    {
        return start_ripup_costs;
    }

    public void set_pass_no(int p_value)
    {
        start_pass_no = Math.max(p_value, 1);
        start_pass_no = Math.min(start_pass_no, 99);
    }

    public int get_pass_no()
    {
        return start_pass_no;
    }

    public void increment_pass_no()
    {
        ++start_pass_no;
    }

    public void set_with_fanout(boolean p_value)
    {
        with_fanout = p_value;
    }

    public boolean get_with_fanout()
    {
        return with_fanout;
    }

    public void set_with_autoroute(boolean p_value)
    {
        with_autoroute = p_value;
    }

    public boolean get_with_autoroute()
    {
        return with_autoroute;
    }

    public void set_with_postroute(boolean p_value)
    {
        with_postroute = p_value;
    }

    public boolean get_with_postroute()
    {
        return with_postroute;
    }

    public void set_vias_allowed(boolean p_value)
    {
        vias_allowed = p_value;
    }

    public boolean get_vias_allowed()
    {
        return vias_allowed;
    }

    public void set_via_costs(int p_value)
    {
        via_costs = Math.max(p_value, 1);
    }

    public int get_via_costs()
    {
        return via_costs;
    }

    public void set_plane_via_costs(int p_value)
    {
        plane_via_costs = Math.max(p_value, 1);
    }

    public int get_plane_via_costs()
    {
        return plane_via_costs;
    }

    public void set_layer_active(int p_layer, boolean p_value)
    {
        if (p_layer < 0 || p_layer >= layer_active_arr.length)
        {
            System.out.println("AutorouteSettings.set_layer_active: p_layer out of range");
            return;
        }
        layer_active_arr[p_layer] = p_value;
    }

    public boolean get_layer_active(int p_layer)
    {
        if (p_layer < 0 || p_layer >= layer_active_arr.length)
        {
            System.out.println("AutorouteSettings.get_layer_active: p_layer out of range");
            return false;
        }
        return layer_active_arr[p_layer];
    }

    public void set_preferred_direction_is_horizontal(int p_layer, boolean p_value)
    {
        if (p_layer < 0 || p_layer >= layer_active_arr.length)
        {
            System.out.println("AutorouteSettings.set_preferred_direction_is_horizontal: p_layer out of range");
            return;
        }
        preferred_direction_is_horizontal_arr[p_layer] = p_value;
    }

    public boolean get_preferred_direction_is_horizontal(int p_layer)
    {
        if (p_layer < 0 || p_layer >= layer_active_arr.length)
        {
            System.out.println("AutorouteSettings.get_preferred_direction_is_horizontal: p_layer out of range");
            return false;
        }
        return preferred_direction_is_horizontal_arr[p_layer];
    }

    public void set_preferred_direction_trace_costs(int p_layer, double p_value)
    {
        if (p_layer < 0 || p_layer >= layer_active_arr.length)
        {
            System.out.println("AutorouteSettings.set_preferred_direction_trace_costs: p_layer out of range");
            return;
        }
        preferred_direction_trace_cost_arr[p_layer] = Math.max(p_value, 0.1);
    }

    public double get_preferred_direction_trace_costs(int p_layer)
    {
        if (p_layer < 0 || p_layer >= layer_active_arr.length)
        {
            System.out.println("AutorouteSettings.get_preferred_direction_trace_costs: p_layer out of range");
            return 0;
        }
        return preferred_direction_trace_cost_arr[p_layer];
    }

    public double get_against_preferred_direction_trace_costs(int p_layer)
    {
        if (p_layer < 0 || p_layer >= layer_active_arr.length)
        {
            System.out.println("AutorouteSettings.get_against_preferred_direction_trace_costs: p_layer out of range");
            return 0;
        }
        return against_preferred_direction_trace_cost_arr[p_layer];
    }

    public double get_horizontal_trace_costs(int p_layer)
    {
        if (p_layer < 0 || p_layer >= layer_active_arr.length)
        {
            System.out.println("AutorouteSettings.get_preferred_direction_trace_costs: p_layer out of range");
            return 0;
        }
        double result;
        if (preferred_direction_is_horizontal_arr[p_layer])
        {
            result = preferred_direction_trace_cost_arr[p_layer];
        }
        else
        {
            result = against_preferred_direction_trace_cost_arr[p_layer];
        }
        return result;
    }

    public void set_against_preferred_direction_trace_costs(int p_layer, double p_value)
    {
        if (p_layer < 0 || p_layer >= layer_active_arr.length)
        {
            System.out.println("AutorouteSettings.set_against_preferred_direction_trace_costs: p_layer out of range");
            return;
        }
        against_preferred_direction_trace_cost_arr[p_layer] = Math.max(p_value, 0.1);
    }

    public double get_vertical_trace_costs(int p_layer)
    {
        if (p_layer < 0 || p_layer >= layer_active_arr.length)
        {
            System.out.println("AutorouteSettings.get_against_preferred_direction_trace_costs: p_layer out of range");
            return 0;
        }
        double result;
        if (preferred_direction_is_horizontal_arr[p_layer])
        {
            result = against_preferred_direction_trace_cost_arr[p_layer];
        }
        else
        {
            result = preferred_direction_trace_cost_arr[p_layer];
        }
        return result;
    }

    public ExpansionCostFactor[] get_trace_cost_arr()
    {
        ExpansionCostFactor[] result = new ExpansionCostFactor[preferred_direction_trace_cost_arr.length];
        for (int i = 0; i < result.length; ++i)
        {
            result[i] = new ExpansionCostFactor(get_horizontal_trace_costs(i), get_vertical_trace_costs(i));
        }
        return result;
    }
    
    private boolean with_fanout;
    private boolean with_autoroute;
    private boolean with_postroute;
    private boolean vias_allowed;
    private int via_costs;
    private int plane_via_costs;
    private int start_ripup_costs;
    private int start_pass_no;
    private final boolean[] layer_active_arr;
    private final boolean[] preferred_direction_is_horizontal_arr;
    private final double[] preferred_direction_trace_cost_arr;
    private final double[] against_preferred_direction_trace_cost_arr;
}
