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
 * LogicalParts.java
 *
 * Created on 26. Maerz 2005, 06:08
 */

package library;

import java.util.Vector;

/**
 * The logical parts contain information for gate swap and pin swap.
 *
 * @author Alfons Wirtz
 */
public class LogicalParts implements java.io.Serializable
{
    /** Adds a logical part to the database. */
    public LogicalPart add(String p_name, LogicalPart.PartPin[] p_part_pin_arr)
    {
        java.util.Arrays.sort(p_part_pin_arr);
        LogicalPart new_part = new LogicalPart(p_name, part_arr.size() + 1, p_part_pin_arr);
        part_arr.add(new_part);
        return new_part;
    }
    
    /**
     * Returns the logical part with the input name or null, if no such package exists.
     */
    public LogicalPart get(String p_name)
    {
        for (LogicalPart curr_part : this.part_arr)
        {
            if (curr_part != null && curr_part.name.compareToIgnoreCase(p_name) == 0)
            {
                return curr_part;
            }
        }
        return null;
    }
    
    /**
     * Returns the logical part with index p_part_no. Part numbers are from 1 to part count.
     */
    public LogicalPart get(int p_part_no)
    {
        LogicalPart result = part_arr.elementAt(p_part_no - 1);
        if (result != null && result.no != p_part_no)
        {
            System.out.println("LogicalParts.get: inconsistent part number");
        }
        return result;
    }
    
    /**
     * Returns the count of logical parts.
     */
    public int count()
    {
        return part_arr.size();
    }
    
    /** The array of logical parts */
    private Vector<LogicalPart> part_arr = new Vector<LogicalPart>();
}
