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
 * SelectMenuState.java
 *
 * Created on 28. November 2003, 10:13
 */

package interactive;

import geometry.planar.FloatPoint;

/**
 * Class implementing the different functionality in the select menu,
 * especially the different behaviour of the mouse button 1.
 *
 * @author  Alfons Wirtz
 */
public class SelectMenuState extends MenuState
{
    /** Returns a new instance of SelectMenuState */
    public static SelectMenuState get_instance(BoardHandling p_board_handling, Logfile p_logfile)
    {
        SelectMenuState new_state = new SelectMenuState(p_board_handling, p_logfile);
        return new_state;
    }
    
    /** Creates a new instance of SelectMenuState */
    private SelectMenuState(BoardHandling p_board_handling, Logfile p_logfile)
    {
        super(p_board_handling, p_logfile);
    }
    
    public InteractiveState left_button_clicked(FloatPoint p_location)
    {
        InteractiveState result =  select_items(p_location);
        return result;
    }
    
    public InteractiveState mouse_dragged(FloatPoint p_point)
    {
        return SelectItemsInRegionState.get_instance(hdlg.get_current_mouse_position(), this, hdlg, logfile);
    }
    
    public void display_default_message()
    {
        hdlg.screen_messages.set_status_message(resources.getString("in_select_menu"));
    }
    
    public String get_help_id()
    {
        return "MenuState_SelectMenuState";
    }
}
