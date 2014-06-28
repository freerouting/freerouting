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
 * MakeSpaceState.java
 *
 * Created on 10. Dezember 2003, 10:53
 */

package interactive;

import geometry.planar.FloatPoint;
import geometry.planar.Point;
import board.AngleRestriction;
import board.BasicBoard;

/**
 * Class for shoving items out of a region to make space to insert something else.
 * For that purpose traces of an unvisible net are created tempory for shoving.
 *
 * @author  Alfons Wirtz
 */
public class MakeSpaceState extends DragState
{
    
    /** Creates a new instance of MakeSpaceState */
    public MakeSpaceState(FloatPoint p_location, InteractiveState p_parent_state, BoardHandling p_board_handling, Logfile p_logfile)
    {
        super(p_location, p_parent_state, p_board_handling, p_logfile);
        int [] shove_trace_width_arr = new int[hdlg.get_routing_board().get_layer_count()];
        boolean [] layer_active_arr = new boolean[shove_trace_width_arr.length];
        int shove_trace_width = Math.min (100, hdlg.get_routing_board().get_min_trace_half_width() / 10);
        shove_trace_width = Math.max (shove_trace_width, 5);
        for (int i = 0; i < shove_trace_width_arr.length; ++i)
        {
            shove_trace_width_arr[i] = shove_trace_width;
            layer_active_arr[i] = true;
        }
        int [] route_net_no_arr = new int[1];
        route_net_no_arr[0] = rules.Nets.hidden_net_no;
        route = new Route(p_location.round(), hdlg.settings.layer, shove_trace_width_arr, layer_active_arr,
                route_net_no_arr, 0, rules.ViaRule.EMPTY, true, hdlg.settings.trace_pull_tight_region_width,
                hdlg.settings.trace_pull_tight_accuracy, null, null, hdlg.get_routing_board(),
                false, false, false, hdlg.settings.hilight_routing_obstacle);
    }
    
    public InteractiveState move_to(FloatPoint p_to_location)
    {
        if (!something_dragged)
        {
            // initialisitions for the first time dragging
            this.observers_activated = !hdlg.get_routing_board().observers_active();
            if (this.observers_activated)
            {
                hdlg.get_routing_board().start_notify_observers();
            }
            // make the situation restorable by undo
            hdlg.get_routing_board().generate_snapshot();
            if (logfile != null)
            {
                // Delayed till here because otherwise the mouse
                // might have been only clicked for selecting
                // and not pressed for moving.
                logfile.start_scope(LogfileScope.MAKING_SPACE, previous_location);
            }
            something_dragged = true;
        }
        route.next_corner(p_to_location);
        
        Point route_end = route.get_last_corner();
        if (hdlg.get_routing_board().rules.get_trace_angle_restriction() == AngleRestriction.NONE &&
                !route_end.equals(p_to_location.round()))
        {
            hdlg.move_mouse(route_end.to_float());
        }
        hdlg.recalculate_length_violations();
        hdlg.repaint();
        return this;
    }
    
    public InteractiveState button_released()
    {
        int delete_net_no = rules.Nets.hidden_net_no;
        BasicBoard board = hdlg.get_routing_board();
        board.remove_items(board.get_connectable_items(delete_net_no), false);
        if (this.observers_activated)
        {
            hdlg.get_routing_board().end_notify_observers();
            this.observers_activated = false;
        }
        if (logfile != null && something_dragged)
        {
            logfile.start_scope(LogfileScope.COMPLETE_SCOPE);
        }
        hdlg.show_ratsnest();
        return this.return_state;
    }
    
    public void draw(java.awt.Graphics p_graphics)
    {
        if (route != null)
        {
            route.draw(p_graphics,  hdlg.graphics_context);
        }
    }
    
    private Route route;
}
