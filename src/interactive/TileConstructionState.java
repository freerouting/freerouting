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
 * TileConstructionState.java
 *
 * Created on 6. November 2003, 14:46
 */

package interactive;


import java.util.Iterator;

import geometry.planar.FloatPoint;
import geometry.planar.IntPoint;
import geometry.planar.Line;
import geometry.planar.Side;
import geometry.planar.TileShape;

import rules.BoardRules;

import board.AngleRestriction;
import board.RoutingBoard;
import board.FixedState;

/**
 * Class for interactive construction of a tile shaped obstacle
 *
 * @author  Alfons Wirtz
 */
public class TileConstructionState extends CornerItemConstructionState
{
    /**
     * Returns a new instance of this class
     * If p_logfile != null; the creation of this item is stored in a logfile
     */
    public static TileConstructionState get_instance(FloatPoint p_location, InteractiveState p_parent_state, BoardHandling p_board_handling, Logfile p_logfile)
    {
        return new TileConstructionState(p_location, p_parent_state, p_board_handling, p_logfile);
    }
    
    /** Creates a new instance of TileConstructionState */
    private TileConstructionState(FloatPoint p_location, InteractiveState p_parent_state, BoardHandling p_board_handling, Logfile p_logfile)
    {
        super(p_parent_state, p_board_handling, p_logfile);
        if (this.logfile != null)
        {
            logfile.start_scope(LogfileScope.CREATING_TILE);
        }
        this.add_corner(p_location);
    }
    
    /**
     * adds a corner to the tile under construction
     */
    public InteractiveState left_button_clicked(FloatPoint p_location)
    {
        super.left_button_clicked(p_location);
        remove_concave_corners();
        hdlg.repaint();
        return this;
    }
    
    public InteractiveState process_logfile_point(FloatPoint p_point)
    {
        return left_button_clicked(p_point);
    }
    
    public InteractiveState complete()
    {
        remove_concave_corners_at_close();
        int corner_count = corner_list.size();
        boolean construction_succeeded = corner_count > 2;
        if (construction_succeeded)
        {
            // create the edgelines of the new tile
            Line[] edge_lines = new Line[corner_count];
            Iterator<IntPoint> it = corner_list.iterator();
            IntPoint first_corner = it.next();
            IntPoint prev_corner = first_corner;
            for (int i = 0; i < corner_count - 1; ++i)
            {
                IntPoint next_corner = it.next();
                edge_lines[i] = new Line(prev_corner, next_corner);
                prev_corner = next_corner;
            }
            edge_lines[corner_count - 1] = new Line(prev_corner, first_corner);
            TileShape obstacle_shape = TileShape.get_instance(edge_lines);
            RoutingBoard board = hdlg.get_routing_board();
            int layer = hdlg.settings.layer;
            int cl_class = BoardRules.clearance_class_none();
            
            construction_succeeded = board.check_shape(obstacle_shape, layer, new int[0], cl_class);
            if (construction_succeeded)
            {
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
        }
        if (construction_succeeded)
        {
            hdlg.screen_messages.set_status_message(resources.getString("keepout_successful_completed"));
        }
        else
        {
            hdlg.screen_messages.set_status_message(resources.getString("keepout_cancelled_because_of_overlaps"));
        }
        if (logfile != null)
        {
            logfile.start_scope(LogfileScope.COMPLETE_SCOPE);
        }
        return this.return_state;
    }
    
    /**
     * skips concave corners at the end of the corner_list.
     **/
    private void remove_concave_corners()
    {
        IntPoint[] corner_arr = new IntPoint[corner_list.size()];
        Iterator<IntPoint> it = corner_list.iterator();
        for (int i = 0; i < corner_arr.length; ++i)
        {
            corner_arr[i] = it.next();
        }
        
        int new_length = corner_arr.length;
        if (new_length < 3)
        {
            return;
        }
        IntPoint last_corner =  corner_arr[new_length - 1];
        IntPoint curr_corner = corner_arr[new_length - 2];
        while (new_length > 2)
        {
            IntPoint prev_corner =  corner_arr[new_length - 3];
            Side last_corner_side = last_corner.side_of(prev_corner, curr_corner);
            if (last_corner_side == Side.ON_THE_LEFT)
            {
                // side is ok, nothing to skip
                break;
            }
            if (this.hdlg.get_routing_board().rules.get_trace_angle_restriction() != AngleRestriction.FORTYFIVE_DEGREE)
            {
                // skip concave corner
                corner_arr[new_length - 2] = last_corner;
            }
            --new_length;
            // In 45 degree case just skip last corner as nothing like the following
            // calculation for the 90 degree case to keep
            // the angle restrictions is implemented.
            if (this.hdlg.get_routing_board().rules.get_trace_angle_restriction()  == AngleRestriction.NINETY_DEGREE)
            {
                // prevent generating a non orthogonal line by changing the previous corner
                IntPoint prev_prev_corner = null;
                if (new_length >= 3)
                {
                    prev_prev_corner = corner_arr[new_length - 3];
                }
                if (prev_prev_corner != null && prev_prev_corner.x == prev_corner.x)
                {
                    corner_arr[new_length - 2] = new IntPoint(prev_corner.x, last_corner.y);
                }
                else
                {
                    corner_arr[new_length - 2] = new IntPoint(last_corner.x, prev_corner.y);
                }
            }
            curr_corner = prev_corner;
        }
        if (new_length < corner_arr.length)
        {
            // somthing skipped, update corner_list
            corner_list = new java.util.LinkedList<IntPoint>();
            for (int i = 0; i < new_length; ++i)
            {
                corner_list.add(corner_arr[i]);
            }
        }
    }
    /**
     * removes as many corners at the end of the corner list, so that
     * closing the polygon will not create a concave corner
     */
    private void remove_concave_corners_at_close()
    {
        add_corner_for_snap_angle();
        if (corner_list.size() < 4)
        {
            return;
        }
        IntPoint[] corner_arr = new IntPoint[corner_list.size()];
        Iterator<IntPoint> it = corner_list.iterator();
        for (int i = 0; i < corner_arr.length; ++i)
        {
            corner_arr[i] = it.next();
        }
        int new_length = corner_arr.length;
        
        IntPoint first_corner = corner_arr[0];
        IntPoint second_corner = corner_arr[1];
        while (new_length > 3)
        {
            IntPoint last_corner =  corner_arr[new_length - 1];
            if (last_corner.side_of(second_corner, first_corner) != Side.ON_THE_LEFT)
            {
                break;
            }
            --new_length;
        }
        
        if (new_length != corner_arr.length)
        {
            // recalculate the corner_list
            corner_list = new java.util.LinkedList<IntPoint>();
            for (int i = 0; i < new_length; ++i)
            {
                corner_list.add(corner_arr[i]);
            }
            add_corner_for_snap_angle();
        }
    }
    
    public void display_default_message()
    {
        hdlg.screen_messages.set_status_message(resources.getString("creatig_tile"));
    }
}
