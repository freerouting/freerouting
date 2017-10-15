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
 * CircleConstructionState.java
 *
 * Created on 6. November 2003, 09:37
 */

package interactive;

import geometry.planar.Circle;
import geometry.planar.ConvexShape;
import geometry.planar.FloatPoint;
import geometry.planar.IntPoint;

import rules.BoardRules;

import board.AngleRestriction;
import board.RoutingBoard;
import board.FixedState;

/**
 * Interactive creation of a circle obstacle
 *
 * @author Alfons Wirtz
 */
public class CircleConstructionState extends InteractiveState
{
    /**
     * Returns a new instance of this class.
     * If p_logfile != null; the creation of this item is stored in a logfile
     */
    public static CircleConstructionState get_instance(FloatPoint p_location,
            InteractiveState p_parent_state, BoardHandling p_board_handling, Logfile p_logfile)
    {
        p_board_handling.remove_ratsnest(); // inserting a circle may change the connectivity.
        return new CircleConstructionState(p_location, p_parent_state, p_board_handling, p_logfile);
    }
    
    /** Creates a new instance of CircleConstructionState */
    private CircleConstructionState(FloatPoint p_location, InteractiveState p_parent_state, BoardHandling p_board_handling, Logfile p_logfile)
    {
        super(p_parent_state, p_board_handling, p_logfile);
        circle_center = p_location;
        if (this.logfile != null)
        {
            logfile.start_scope(LogfileScope.CREATING_CIRCLE, p_location);
        }
    }
    
    public InteractiveState left_button_clicked(FloatPoint p_location)
    {
        if (logfile != null)
        {
            logfile.add_corner(p_location);
        }
        return this.complete();
    }
    
    public InteractiveState mouse_moved()
    {
        super.mouse_moved();
        hdlg.repaint();
        return this;
    }
    
    /**
     * completes the circle construction state
     */
    public InteractiveState complete()
    {
        IntPoint center = this.circle_center.round();
        int radius = (int)Math.round(this.circle_radius);
        int layer = hdlg.settings.layer;
        int cl_class;
        RoutingBoard board = hdlg.get_routing_board();
        cl_class = BoardRules.clearance_class_none();
        boolean construction_succeeded = (this.circle_radius > 0);
        ConvexShape obstacle_shape = null;
        if (construction_succeeded)
        {
            
            obstacle_shape = new Circle(center, radius);
            if (hdlg.get_routing_board().rules.get_trace_angle_restriction() == AngleRestriction.NINETY_DEGREE)
            {
                obstacle_shape = obstacle_shape.bounding_box();
            }
            else if (hdlg.get_routing_board().rules.get_trace_angle_restriction() == AngleRestriction.FORTYFIVE_DEGREE)
            {
                obstacle_shape = obstacle_shape.bounding_octagon();
            }
            construction_succeeded = board.check_shape(obstacle_shape, layer, new int[0], cl_class);
        }
        if (construction_succeeded)
        {
            hdlg.screen_messages.set_status_message(resources.getString("keepout_successful_completed"));
            
            // insert the new shape as keepout
            this.observers_activated = !hdlg.get_routing_board().observers_active();
            if (this.observers_activated)
            {
                hdlg.get_routing_board().start_notify_observers();
            }
            board.generate_snapshot();
            board.insert_obstacle(obstacle_shape, layer, cl_class, FixedState.UNFIXED);
            if (this.observers_activated)
            {
                hdlg.get_routing_board().end_notify_observers();
                this.observers_activated = false;
            }
        }
        else
        {
            hdlg.screen_messages.set_status_message(resources.getString("keepout_cancelled_because_of_overlaps"));
        }
        if (logfile != null)
        {
            logfile.start_scope(LogfileScope.COMPLETE_SCOPE);
        }
        hdlg.repaint();
        return this.return_state;
    }
    
    /**
     * Used when reading the next point from a logfile.
     * Calls complete, because only 1 additional point is stored in the logfile.
     */
    public InteractiveState process_logfile_point(FloatPoint p_point)
    {
        this.circle_radius = circle_center.distance(p_point);
        return this;
    }
    
    /**
     * draws the graphic construction aid for the circle
     */
    public void draw(java.awt.Graphics p_graphics)
    {
        FloatPoint current_mouse_position = hdlg.get_current_mouse_position();
        if (current_mouse_position == null)
        {
            return;
        }
        this.circle_radius = circle_center.distance(current_mouse_position);
        hdlg.graphics_context.draw_circle(circle_center, circle_radius, 300, java.awt.Color.white, p_graphics, 1);
    }
    
    public javax.swing.JPopupMenu get_popup_menu()
    {
        return hdlg.get_panel().popup_menu_insert_cancel;
    }
    
    public void display_default_message()
    {
        hdlg.screen_messages.set_status_message(resources.getString("creating_circle"));
    }
    
    private final FloatPoint circle_center;
    private double circle_radius = 0;
    
    private boolean observers_activated = false;
}
