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
 * IncompletesWindow.java
 *
 * Created on 21. Maerz 2005, 05:30
 */

package eu.mihosoft.freerouting.gui;

import eu.mihosoft.freerouting.interactive.RatsNest;

import java.util.List;

/**
 *
 * @author Alfons Wirtz
 */
public class WindowIncompletes extends WindowObjectListWithFilter
{
    
    /** Creates a new instance of IncompletesWindow */
    public WindowIncompletes(BoardFrame p_board_frame)
    {
        super(p_board_frame); 
        java.util.ResourceBundle resources = 
                java.util.ResourceBundle.getBundle("eu.mihosoft.freerouting.gui.Default", p_board_frame.get_locale());
        this.setTitle(resources.getString("incompletes"));
        this.list_empty_message.setText(resources.getString("route_completed"));
        p_board_frame.set_context_sensitive_help(this, "WindowObjectList_Incompletes");
    }
    
    
    /**
     * Fills the list with the board incompletes.
     */
    protected void fill_list()
    {
        RatsNest ratsnest = board_frame.board_panel.board_handling.get_ratsnest();
        RatsNest.AirLine[] sorted_arr = ratsnest.get_airlines();
        
        java.util.Arrays.sort(sorted_arr);
        for (int i = 0; i < sorted_arr.length; ++i)
        {
            this.add_to_list(sorted_arr[i]);
        }
        this.list.setVisibleRowCount(Math.min(sorted_arr.length, DEFAULT_TABLE_SIZE));
    }
    
    protected void select_instances()
    {
        List<Object> selected_incompletes = list.getSelectedValuesList();
        if (selected_incompletes.size() <= 0)
        {
            return;
        }
        java.util.Set<eu.mihosoft.freerouting.board.Item> selected_items = new java.util.TreeSet<eu.mihosoft.freerouting.board.Item>();
        for (int i = 0; i < selected_incompletes.size(); ++i)
        {
            RatsNest.AirLine curr_airline = (RatsNest.AirLine) selected_incompletes.get(i);
            selected_items.add(curr_airline.from_item);
            selected_items.add(curr_airline.to_item);
            
        }
        board_frame.board_panel.board_handling.select_items(selected_items);
        board_frame.board_panel.board_handling.zoom_selection();
    }
}
