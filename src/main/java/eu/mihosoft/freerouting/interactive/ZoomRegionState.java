/*
 *   Copyright (C) 2014  Alfons Wirtz
 *   website www.freerouting.net
 *
 *   Copyright (C) 2017 Michael Hoffer <info@michaelhoffer.de>
 *   Website www.freerouting.mihosoft.eu
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
 * ZoomRegionState.java
 *
 * Created on 9. November 2003, 13:05
 */

package eu.mihosoft.freerouting.interactive;

import eu.mihosoft.freerouting.geometry.planar.FloatPoint;

import java.awt.geom.Point2D;

/**
 * Class for interactive zooming to a rectangle.
 *
 * @author Alfons Wirtz
 */
public class ZoomRegionState extends SelectRegionState
{
    /**
     * Returns a new instance of this class.
     */
    public static ZoomRegionState get_instance(InteractiveState p_parent_state, BoardHandling p_board_handling, ActivityReplayFile p_activityReplayFile)
    {
        return get_instance(null, p_parent_state, p_board_handling, p_activityReplayFile);
    }
    
    /**
     * Returns a new instance of this class with first point p_location.
     */
    public static ZoomRegionState get_instance(FloatPoint p_location, InteractiveState p_parent_state, BoardHandling p_board_handling, ActivityReplayFile p_activityReplayFile)
    {
        ZoomRegionState new_instance = new ZoomRegionState(p_parent_state, p_board_handling, p_activityReplayFile);
        new_instance.corner1 = p_location;
        new_instance.hdlg.screen_messages.set_status_message(new_instance.resources.getString("drag_left_mouse_button_to_create_region_to_display"));
        return new_instance;
    }
    
    /** Creates a new instance of ZoomRegionState */
    public ZoomRegionState(InteractiveState p_parent_state, BoardHandling p_board_handling, ActivityReplayFile p_activityReplayFile)
    {
        super(p_parent_state, p_board_handling, p_activityReplayFile);
        if (this.activityReplayFile != null)
        {
            activityReplayFile.start_scope(ActivityReplayFileScope.ZOOM_FRAME);
        }
    }
    
    public InteractiveState complete()
    {
        corner2 = hdlg.get_current_mouse_position();
        zoom_region();
        if (this.activityReplayFile != null)
        {
            activityReplayFile.add_corner(corner2);
        }
        return this.return_state;
    }
    
    private void zoom_region()
    {
        if (corner1 == null || corner2 == null)
        {
            return;
        }
        Point2D sc_corner1 = hdlg.graphics_context.coordinate_transform.board_to_screen(corner1) ;
        Point2D sc_corner2 = hdlg.graphics_context.coordinate_transform.board_to_screen(corner2) ;
        hdlg.get_panel().zoom_frame(sc_corner1, sc_corner2) ;
    }
}
