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
 * WindowNetDemonstration.java
 *
 * Created on 14. November 2006, 12:20
 *
 */

package eu.mihosoft.freerouting.gui;

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
        SampleDesignListElement selected_element = list_model.elementAt(index);
        String[] name_parts = selected_element.design_name.split("\\.");
        String archive_name = name_parts[0];
        BoardFrame new_frame = open_design(archive_name, selected_element.design_name, this.locale);
        if (new_frame != null)
        {
            selected_element.additional_action.perform(new_frame, archive_name);
        }
    }
}
