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
 * SelectRegionState.java
 *
 * Created on 9. November 2003, 11:34
 */

package interactive;

import geometry.planar.FloatPoint;

/**
 * Common base class for interactive selection of a rectangle.
 *
 * @author Alfons Wirtz
 */
public class SelectRegionState extends InteractiveState
{
    
    /** Creates a new instance of SelectRegionState */
    protected SelectRegionState(InteractiveState p_parent_state, BoardHandling p_board_handling, Logfile p_logfile)
    {
        super(p_parent_state, p_board_handling, p_logfile);
    }
    
    public InteractiveState button_released()
    {
        hdlg.screen_messages.set_status_message("");
        return complete();
    }
    
    public InteractiveState mouse_dragged(FloatPoint p_point)
    {
        if (corner1 == null)
        {
            corner1 = p_point;
            if (logfile != null)
            {
                logfile.add_corner(corner1);
            }
        }
        hdlg.repaint();
        return this;
    }
    
    public void draw(java.awt.Graphics p_graphics)
    {
        this.return_state.draw(p_graphics);
        FloatPoint current_mouse_position = hdlg.get_current_mouse_position();
        if (corner1 == null || current_mouse_position == null)
        {
            return;
        }
        corner2 = current_mouse_position;
        hdlg.graphics_context.draw_rectangle(corner1, corner2, 1, java.awt.Color.white, p_graphics, 1) ;
    }
    
    protected FloatPoint corner1 = null;
    protected FloatPoint corner2 = null;
}
