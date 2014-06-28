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
 * Nets.java
 *
 * Created on 9. Juni 2004, 10:24
 */
package rules;

import java.util.Vector;
import java.util.Collection;

/**
 * Describes the electrical Nets on a board.
 *
 * @author  alfons
 */
public class Nets implements java.io.Serializable
{

    /** Creates a new empty net list */
    public Nets()
    {
        net_arr = new Vector<Net>();
    }

    /**
     * Returns the biggest net number on the board.
     */
    public int max_net_no()
    {
        return net_arr.size();
    }

    /** Returns the net with the input name and subnet_number , or null, if no such net exists. */
    public Net get(String p_name, int p_subnet_number)
    {
        for (Net curr_net : net_arr)
        {
            if (curr_net != null && curr_net.name.compareToIgnoreCase(p_name) == 0)
            {
                if (curr_net.subnet_number == p_subnet_number)
                {
                    return curr_net;
                }
            }
        }
        return null;
    }

    /**
     *  Returns all subnets with the input name.
     */
    public Collection<Net> get(String p_name)
    {
        Collection<Net> result = new java.util.LinkedList<Net>();
        for (Net curr_net : net_arr)
        {
            if (curr_net != null && curr_net.name.compareToIgnoreCase(p_name) == 0)
            {
                result.add(curr_net);
            }
        }
        return result;
    }

    /**
     * Returns the net with the input net number or null, if no such net exists.
     */
    public Net get(int p_net_no)
    {
        if (p_net_no < 1 || p_net_no > net_arr.size())
        {
            return null;
        }
        Net result = net_arr.elementAt(p_net_no - 1);
        if (result != null && result.net_number != p_net_no)
        {
            System.out.println("Nets.get: inconsistent net_no");
        }
        return result;
    }

    /**
     * Generates a new net number.
     */
    public Net new_net(java.util.Locale p_locale)
    {
        java.util.ResourceBundle resources = java.util.ResourceBundle.getBundle("rules.resources.Default", p_locale);
        String net_name = resources.getString("net#") + (new Integer(net_arr.size() + 1)).toString();
        return add(net_name, 1, false);
    }

    /**
     * Adds a new net with default properties with the input name.
     * p_subnet_number is used only if a net is divided internally because of fromto rules for example.
     * For normal nets it is always 1.
     */
    public Net add(String p_name, int p_subnet_number, boolean p_contains_plane)
    {
        int new_net_no = net_arr.size() + 1;
        if (new_net_no >= max_legal_net_no)
        {
            System.out.println("Nets.add_net: max_net_no out of range");
        }
        Net new_net = new Net(p_name, p_subnet_number, new_net_no, this, p_contains_plane);
        net_arr.add(new_net);
        return new_net;
    }

    /**
     * Returns false, if p_net_no belongs to a net internally used
     * for special purposes.
     */
    public static boolean is_normal_net_no(int p_net_no)
    {
        return (p_net_no > 0 && p_net_no <= max_legal_net_no);
    }

    /**
     * Sets the Board of this net list.
     * Used for example to get access to the Items of the net.
     */
    public void set_board(board.BasicBoard p_board)
    {
        this.board = p_board;
    }

    /**
     * Gets the Board of this net list.
     * Used for example to get access to the Items of the net.
     */
    public board.BasicBoard get_board()
    {
        return this.board;
    }


    
    /** The maximum legal net number for nets. */
    public static final int max_legal_net_no = 9999999;
    /** auxiliary net number for internal use */
    public static final int hidden_net_no = 10000001;
    /** The list of electrical nets on the board */
    private Vector<Net> net_arr;
    private board.BasicBoard board;
}
