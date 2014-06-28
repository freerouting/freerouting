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
 * DefaultItemClearancesClasses.java
 *
 * Created on 12. Juni 2005, 07:19
 *
 */

package rules;

/**
 *
 * @author Alfons Wirtz
 */
public class DefaultItemClearanceClasses implements java.io.Serializable
{
    
    /** Creates a new instance of DefaultItemClearancesClasses */
    public DefaultItemClearanceClasses()
    {
        for (int i = 1; i < ItemClass.values().length; ++i)
        {
            arr[i] = 1;
        }
    }
    
    public DefaultItemClearanceClasses(DefaultItemClearanceClasses p_classes)
    {
        for (int i = 1; i < ItemClass.values().length; ++i)
        {
            arr[i] = p_classes.arr[i];
        }
    }
    
    /**
     *  Used in the function get_default_clearance_class to get the
     *  default claearance classes for item classes.
     */
    public enum ItemClass
    {
        NONE, TRACE, VIA, PIN, SMD, AREA
    }
    
    /**
     * Returns the number of the default clearance class for the input item class.
     */
    public int get(ItemClass p_item_class)
    {
        return this.arr[p_item_class.ordinal()];
    }
    
    /**
     * Sets the index of the default clearance class of the input item class
     * in the clearance matrix to p_index.
     */
    public void set(ItemClass p_item_class, int p_index)
    {
        this.arr[p_item_class.ordinal()] = p_index;
    }
    
    /**
     * Sets the indices of all default item clearance classes to p_index.
     */
    public void set_all(int p_index)
    {
        for (int i = 1; i < this.arr.length; ++i)
        {
            arr[i] = p_index;
        }
    }
    
    private final int[] arr = new int[ItemClass.values().length];
}
