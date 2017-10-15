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
 * DragMenuState.java
 *
 * Created on 4. November 2004, 11:14
 */

package interactive;

import geometry.planar.FloatPoint;

/**
 * Class implementing the different functionality in the drag menu
 *
 * @author  alfons
 */
public class DragMenuState extends MenuState
{
    /** Returns a new instance of DragMenuState */
    public static DragMenuState get_instance(BoardHandling p_board_handling, Logfile p_logfile)
    {
        DragMenuState new_state = new DragMenuState(p_board_handling, p_logfile);
        return new_state;
    }
    
    /** Creates a new instance of DragMenuState */
    public DragMenuState(BoardHandling p_board_handling, Logfile p_logfile)
    {
        super(p_board_handling, p_logfile);
    }
    
    public InteractiveState mouse_pressed(FloatPoint p_point)
    {
        return DragState.get_instance(p_point, this, hdlg, logfile);
    }
    
    public String get_help_id()
    {
        return "MenuState_DragMenuState";
    }
}
