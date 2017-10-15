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
 * ViaInfos.java
 *
 * Created on 2. April 2005, 06:49
 */

package rules;

import java.util.List;
import java.util.LinkedList;

/**
 * Contains the lists of different ViaInfo's, which can be used in interactive and automatic routing.
 *
 * @author Alfons Wirtz
 */
public class ViaInfos implements java.io.Serializable, board.ObjectInfoPanel.Printable
{
    /**
     * Adds a via info consisting of padstack, clearance class and drill_to_smd_allowed.
     * Return false, if the insertion failed, for example if the name existed already.
     */
    public boolean add(ViaInfo p_via_info)
    {
        if (name_exists(p_via_info.get_name()))
        {
            return false;
        }
        this.list.add(p_via_info);
        return true;
    }
    
    /**
     * Returns the number of different vias, which can be used for routing.
     */
    public int count()
    {
        return this.list.size();
    }
    
    /**
     * Returns the p_no-th via af the via types, which can be used for routing.
     */
    public ViaInfo get(int p_no)
    {
        assert p_no >= 0 && p_no < this.list.size();
        return this.list.get(p_no);
    }
    
    /**
     * Returns the via info with name p_name, or null, if no such via exists.
     */
    public ViaInfo get(String p_name)
    {
        for (ViaInfo curr_via : this.list)
        {
            if (curr_via.get_name().equals(p_name))
            {
                return curr_via;
            }
        }
        return null;
    }
    
    /**
     * Returns true, if a via info with name p_name is already wyisting in the list.
     */
    public boolean name_exists(String p_name)
    {
        for (ViaInfo curr_via : this.list)
        {
            if (curr_via.get_name().equals(p_name))
            {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Removes p_via_info from this list.
     * Returns false, if p_via_info was not contained in the list.
     */
    public boolean remove(ViaInfo p_via_info)
    {
        return this.list.remove(p_via_info);
    }
    
    public void print_info(board.ObjectInfoPanel p_window, java.util.Locale p_locale)
    {
        java.util.ResourceBundle resources = 
                java.util.ResourceBundle.getBundle("board.resources.ObjectInfoPanel", p_locale);
        p_window.append_bold(resources.getString("vias") + ": ");
        int counter = 0;
        boolean first_time = true;
        final int max_vias_per_row = 5;
        for (ViaInfo curr_via : this.list)
        {
            if (first_time)
            {
                first_time = false;
            }
            else
            {
                p_window.append(", ");
            }
            if (counter == 0)
            {
                p_window.newline();
                p_window.indent();
            }
            p_window.append(curr_via.get_name(), resources.getString("via_info"), curr_via);
            counter = (counter + 1) % max_vias_per_row;
        }
    }
    private List<ViaInfo> list = new LinkedList<ViaInfo>();
}
