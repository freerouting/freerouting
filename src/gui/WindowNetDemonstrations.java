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
 * WindowNetDemonstration.java
 *
 * Created on 14. November 2006, 12:20
 *
 */

package gui;

import java.util.zip.ZipInputStream;

/**
 * Window with a list for selecting router demonstrations in the net.
 *
 * @author Alfons Wirtz
 */
public class WindowNetDemonstrations extends WindowNetSamples
{
    
    /** Creates a new instance of WindowNetDemonstration */
    public WindowNetDemonstrations(java.util.Locale p_locale)
    {
        super(p_locale, "router_demonstrations", "replay_example", 7);
    }
    
    /**
     * To be edited when the demonstration examples change.
     * For every String in the second column a String has to be added to the resource file WindowNetSamples.
     */
    protected void fill_list()
    {
        add("sample_45.dsn", "45_degree_logfile", AdditionalAction.READ_LOGFILE);
        add("int_ar.dsn", "drag_component_logfile", AdditionalAction.READ_LOGFILE);
        add("single_layer.dsn", "any_angle_logfile", AdditionalAction.READ_LOGFILE);
        add("hexapod_empty.dsn", "autorouter_example_1", AdditionalAction.AUTOROUTE);
        add("at14_empty.dsn", "autorouter_example_2", AdditionalAction.AUTOROUTE);
        add("sharp_empty.dsn", "autorouter_example_3", AdditionalAction.AUTOROUTE);
    }
    
    protected void button_pushed()
    {
        int index = list.getSelectedIndex();
        if (index < 0 || index >= list_model.getSize())
        {
            return;
        }
        ListElement selected_element = (ListElement) list_model.elementAt(index);
        String[] name_parts = selected_element.design_name.split("\\.");
        String archive_name = name_parts[0];
        BoardFrame new_frame = open_design(archive_name, selected_element.design_name, this.locale);
        if (new_frame != null)
        {
            selected_element.additional_action.perform(new_frame, archive_name);
        }
    }
    
    /**
     * Adds an element to the list.
     */
    private void add(String p_design_name, String p_message_name, AdditionalAction p_additional_action)
    {
        list_model.addElement(new ListElement(p_design_name,
                resources.getString(p_message_name), p_additional_action));
    }
    
    
    /**
     * Replays a zipped logfile from an URL in the net.
     */
    private static void read_zipped_logfile(BoardFrame p_board_frame, String p_archive_name, String p_logfile_name)
    {
        if (p_board_frame == null)
        {
            return;
        }
        ZipInputStream zip_input_stream = WindowNetSamples.open_zipped_file(p_archive_name, p_logfile_name);
        if (zip_input_stream == null)
        {
            return;
        }
        p_board_frame.read_logfile(zip_input_stream);
    }
    
    /**
     * Additional Acction to be performed after opening the board.
     */
    private enum AdditionalAction
    {
        READ_LOGFILE
        {
            void perform(BoardFrame p_board_frame, String p_archive_name)
            {
                String logfile_archive_name = "route_" + p_archive_name;
                read_zipped_logfile(p_board_frame, logfile_archive_name, logfile_archive_name + ".log");
            }
        },
        
        
        AUTOROUTE
        {
            void perform(BoardFrame p_board_frame, String p_archive_name)
            {
                p_board_frame.board_panel.board_handling.start_batch_autorouter();
            }
        },
        
        NONE
        {
            void perform(BoardFrame p_board_frame, String p_archive_name)
            {
                
            }
        };
        
        abstract void perform(BoardFrame p_board_frame, String p_archive_name);
    }
    
    /**
     * Structure of the elements in the list
     * For every instance in a String has to be added to the resource file WindowNetSamples fo the
     * String in the field message_name.
     */
    private static class ListElement
    {
        ListElement(String p_design_name, String p_message_name, AdditionalAction p_additional_action)
        {
            design_name = p_design_name;
            message_name = p_message_name;
            additional_action = p_additional_action;
        }
        
        public String toString()
        {
            return message_name;
        }
        
        final String design_name;
        final String message_name;
        final AdditionalAction additional_action;
    }
}
