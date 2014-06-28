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
 * ItemVisibilityFrame.java
 *
 * Created on 7. November 2004, 07:38
 */

package gui;

import boardgraphics.ColorIntensityTable.ObjectNames;

/**
 * Interactive Frame to adjust the visibility of the individual board items
 *
 * @author  alfons
 */
public class WindowObjectVisibility extends WindowVisibility
{
    /** Returns a new instance of ItemVisibilityFrame */
    public static WindowObjectVisibility get_instance(BoardFrame p_board_frame)
    {
        java.util.ResourceBundle resources = 
                java.util.ResourceBundle.getBundle("gui.resources.WindowObjectVisibility", p_board_frame.get_locale());
        String title = resources.getString("title");
        String header_message = resources.getString("header_message");
        String [] message_arr = new String [ObjectNames.values().length];
        for (int i = 0; i < message_arr.length; ++i)
        {
            message_arr[i] = resources.getString(ObjectNames.values()[i].toString());
        }
        WindowObjectVisibility result =  new WindowObjectVisibility(p_board_frame, title, header_message, message_arr);
        p_board_frame.set_context_sensitive_help(result, "WindowDisplay_ObjectVisibility");
        result.refresh();
        return result;
    }
    
    /** Creates a new instance of ItemVisibilityFrame */
    private WindowObjectVisibility(BoardFrame p_board_frame, String p_title, String p_header_message, String[] p_message_arr)
    {
        
        super(p_board_frame, p_title, p_header_message, p_message_arr);
    }
    
    /**
     * Refreshs the displayed values in this window.
     */
    public void refresh()
    {
        boardgraphics.ColorIntensityTable color_intensity_table = this.get_board_handling().graphics_context.color_intensity_table;
        for (int i = 0; i < ObjectNames.values().length; ++i)
        {
            this.set_slider_value(i, color_intensity_table.get_value(i));
        }
    }
    
    protected void set_changed_value(int p_index, double p_value)
    {
        
        get_board_handling().graphics_context.color_intensity_table.set_value(p_index, p_value);
    }
}
