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
 * PolygonShapeConstructionState.java
 *
 * Created on 7. November 2003, 17:19
 */

package interactive;

import geometry.planar.FloatPoint;
import geometry.planar.IntPoint;
import geometry.planar.PolygonShape;

import java.util.Iterator;

import rules.BoardRules;

/**
 * Interactive state for constructing an obstacle with a polygon shape.
 *
 * @author Alfons Wirtz
 */
public class PolygonShapeConstructionState extends CornerItemConstructionState
{
    /**
     * Returns a new instance of this class
     * If p_logfile != null; the creation of this item is stored in a logfile
     */
    public static PolygonShapeConstructionState get_instance(FloatPoint p_location, InteractiveState p_parent_state, BoardHandling p_board_handling, Logfile p_logfile)
    {
        return new PolygonShapeConstructionState(p_location, p_parent_state, p_board_handling, p_logfile);
    }
    
    /** Creates a new instance of PolygonShapeConstructionState */
    private PolygonShapeConstructionState(FloatPoint p_location, InteractiveState p_parent_state, BoardHandling p_board_handling, Logfile p_logfile)
    {
        super(p_parent_state, p_board_handling, p_logfile);
        if (this.logfile != null)
        {
            logfile.start_scope(LogfileScope.CREATING_POLYGONSHAPE);
        }
        this.add_corner(p_location);
    }
    
    /**
     * Inserts the polygon shape item into the board, if possible
     * and returns to the main state
     */
    public InteractiveState complete()
    {
        add_corner_for_snap_angle();
        int corner_count = corner_list.size();
        boolean construction_succeeded = (corner_count > 2);
        if (construction_succeeded)
        {
            IntPoint [] corner_arr = new IntPoint[corner_count];
            Iterator<IntPoint> it = corner_list.iterator();
            for (int i = 0; i < corner_count ; ++i)
            {
                corner_arr[i] = it.next();
            }
            PolygonShape obstacle_shape = new PolygonShape(corner_arr);
            int cl_class = BoardRules.clearance_class_none();
            if (obstacle_shape.split_to_convex() == null)
            {
                // shape is invalid, maybe it has selfintersections
                construction_succeeded = false;
            }
            else
            {
                construction_succeeded = hdlg.get_routing_board().check_shape(obstacle_shape,
                        hdlg.settings.layer, new int[0], cl_class);
            }
            if (construction_succeeded)
            {
                this.observers_activated = !hdlg.get_routing_board().observers_active();
                if (this.observers_activated)
                {
                    hdlg.get_routing_board().start_notify_observers();
                }
                hdlg.get_routing_board().generate_snapshot();
                hdlg.get_routing_board().insert_obstacle(obstacle_shape, hdlg.settings.layer, cl_class, board.FixedState.UNFIXED);
                hdlg.get_routing_board().end_notify_observers();
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
    
    public void display_default_message()
    {
        hdlg.screen_messages.set_status_message(resources.getString("creating_polygonshape"));
    }
    
}
