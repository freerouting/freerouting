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
 * WindowNeetSampleDesigns.java
 *
 * Created on 14. November 2006, 10:13
 *
 */
package gui;

/**
 *
 * Window with a list for selecting sample board designs in the net.
 *
 * @author Alfons Wirtz
 */
public class WindowNetSampleDesigns extends WindowNetSamples
{

    /** Creates a new instance of WindowNeetSampleDesigns */
    public WindowNetSampleDesigns(java.util.Locale p_locale)
    {
        super(p_locale, "sample_designs", "open_sample_design", 11);
    }

    protected void fill_list()
    {
        list_model.addElement("hexapod_empty.dsn");
        list_model.addElement("hexapod_autorouted.dsn");
        list_model.addElement("sharc_handrouted.dsn");
        list_model.addElement("at14_empty.dsn");
        list_model.addElement("at14_autorouted.dsn");
        list_model.addElement("sharp_empty.dsn");
        list_model.addElement("sharp_autorouted.dsn");
        list_model.addElement("bigdesign_unrouted.dsn");
        list_model.addElement("int_empty.dsn");
        list_model.addElement("int_autorouted.dsn");
        list_model.addElement("single_layer_empty.dsn");
        list_model.addElement("single_layer_handrouted.dsn");
    }

    protected void button_pushed()
    {
        int index = list.getSelectedIndex();
        if (index < 0 || index >= list_model.getSize())
        {
            return;
        }
        String design_name = (String) list_model.elementAt(index);
        String[] name_parts = design_name.split("\\.");
        String archive_name = name_parts[0];
        open_design(archive_name, design_name, this.locale);
    }
}
