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
 * ColorIntensityTable.java
 *
 * Created on 1. August 2004, 07:46
 */

package boardgraphics;

/**
 * The color intensities for each item type.
 * The values are between 0 (invisible) and 1 (full intensity).
 *
 * @author  alfons
 */
public class ColorIntensityTable implements java.io.Serializable
{
    
    /**
     * Creates a new instance of ColorIntensityTable.
     * The elements of p_intensities are expected between 0 and 1.
     */
    public ColorIntensityTable()
    {
        arr = new double [ObjectNames.values().length];
        arr[ObjectNames.TRACES.ordinal()] = 0.4;
        arr[ObjectNames.VIAS.ordinal()] = 0.6;
        arr[ObjectNames.PINS.ordinal()] = 0.6;
        arr[ObjectNames.CONDUCTION_AREAS.ordinal()] = 0.2;
        arr[ObjectNames.KEEPOUTS.ordinal()] = 0.2;
        arr[ObjectNames.VIA_KEEPOUTS.ordinal()] = 0.2;
        arr[ObjectNames.PLACE_KEEPOUTS.ordinal()] = 0.2;
        arr[ObjectNames.COMPONENT_OUTLINES.ordinal()] = 1;
        arr[ObjectNames.HILIGHT.ordinal()] = 0.8;
        arr[ObjectNames.INCOMPLETES.ordinal()] = 1;
        arr[ObjectNames.LENGTH_MATCHING_AREAS.ordinal()] = 0.1;
    }
    
    /**
     * Copy constructor.
     */
    public ColorIntensityTable(ColorIntensityTable p_color_intesity_table)
    {
        this.arr = new double [p_color_intesity_table.arr.length];
        for (int i = 0; i < this.arr.length; ++i)
        {
            this.arr[i] = p_color_intesity_table.arr[i];
        }
    }
    
    public double get_value(int p_no)
    {
        if (p_no < 0 || p_no >= ObjectNames.values().length)
        {
            System.out.println("ColorIntensityTable.get_value: p_no out of range");
            return 0;
        }
        return arr[p_no];
    }
    
    public void set_value(int p_no, double p_value)
    {
        if (p_no < 0 || p_no >= ObjectNames.values().length)
        {
            System.out.println("ColorIntensityTable.set_value: p_no out of range");
            return;
        }
        arr [p_no] = p_value;
    }
    
    private final double [] arr;
    
    public enum ObjectNames 
    {
        TRACES, VIAS, PINS, CONDUCTION_AREAS, KEEPOUTS, VIA_KEEPOUTS, PLACE_KEEPOUTS, COMPONENT_OUTLINES, 
        HILIGHT, INCOMPLETES, LENGTH_MATCHING_AREAS
    }
}
