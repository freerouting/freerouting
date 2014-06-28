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
 * ViaInfo.java
 *
 * Created on 31. Maerz 2005, 05:34
 */

package rules;
import library.Padstack;

/**
 * Information about a combination of via_padstack, via clearance class and drill_to_smd_allowed
 * used in interactive and automatic routing.
 *
 * @author Alfons Wirtz
 */
public class ViaInfo implements Comparable<ViaInfo>, board.ObjectInfoPanel.Printable, java.io.Serializable
{
    
    /** Creates a new instance of ViaRule */
    public ViaInfo(String p_name, Padstack p_padstack, int p_clearance_class, boolean p_drill_to_smd_allowed,
            BoardRules p_board_rules)
    {
        name = p_name;
        padstack = p_padstack;
        clearance_class = p_clearance_class;
        attach_smd_allowed = p_drill_to_smd_allowed;
        board_rules = p_board_rules;
    }
    
    public String get_name()
    {
        return name;
    }
    
    public void set_name(String p_name)
    {
        name = p_name;
    }
    
    public String toString()
    {
        return this.name;
    }
    
    public Padstack get_padstack()
    {
        return padstack;
    }
    
    public void set_padstack(Padstack p_padstack)
    {
        padstack = p_padstack;
    }
    
    public int get_clearance_class()
    {
        return clearance_class;
    }
    
    public void set_clearance_class(int p_clearance_class)
    {
        clearance_class = p_clearance_class;
    }
    
    public boolean attach_smd_allowed()
    {
        return attach_smd_allowed;
    }
    
    public void set_attach_smd_allowed(boolean p_attach_smd_allowed)
    {
        attach_smd_allowed = p_attach_smd_allowed;
    }
    
    public int compareTo(ViaInfo p_other)
    {
        return this.name.compareTo(p_other.name);
    }
    
    public void print_info(board.ObjectInfoPanel p_window, java.util.Locale p_locale)
    {
        java.util.ResourceBundle resources = 
                java.util.ResourceBundle.getBundle("board.resources.ObjectInfoPanel", p_locale);
        p_window.append_bold(resources.getString("via") + " ");
        p_window.append_bold(this.name);
        p_window.append_bold(": ");
        p_window.append(resources.getString("padstack") + " ");
        p_window.append(this.padstack.name, resources.getString("padstack_info"), this.padstack);
        p_window.append(", " + resources.getString("clearance_class") + " ");
        String curr_name = board_rules.clearance_matrix.get_name(this.clearance_class);
        p_window.append(curr_name, resources.getString("clearance_class_2"), board_rules.clearance_matrix.get_row(this.clearance_class));
        p_window.append(", " + resources.getString("attach_smd") + " ");
        if (attach_smd_allowed)
        {
            p_window.append(" " + resources.getString("on"));
        }
        else
        {
            p_window.append(" " + resources.getString("off") );
        }
        p_window.newline();
    }
    
    private String name;
    private Padstack padstack;
    private int clearance_class;
    private boolean attach_smd_allowed;
    private final BoardRules board_rules;
}
