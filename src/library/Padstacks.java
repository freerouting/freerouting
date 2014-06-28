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
 * Padstacks.java
 *
 * Created on 3. Juni 2004, 09:42
 */

package library;

import java.util.Iterator;
import java.util.Vector;

import geometry.planar.ConvexShape;

/**
 * Describes a library of padstacks for pins or vias.
 *
 * @author  alfons
 */
public class Padstacks implements java.io.Serializable
{
    /** Creates a new instance of Padstacks */
    public Padstacks(board.LayerStructure p_layer_structure)
    {
        board_layer_structure = p_layer_structure;
        padstack_arr = new Vector<Padstack>();
    }
    
    /**
     * Returns the padstack with the input name or null,
     * if no such padstack exists.
     */
    public Padstack get(String p_name)
    {
        Iterator<Padstack> it = padstack_arr.iterator();
        while(it.hasNext())
        {
            Padstack curr_padstack = it.next();
            if (curr_padstack != null && curr_padstack.name.compareToIgnoreCase(p_name) == 0)
            {
                return curr_padstack;
            }
        }
        return null;
    }
    
    /**
     * Returns the count of Padstacks in this object.
     */
    public int count()
    {
        return padstack_arr.size();
    }
    
    /**
     * Returns the padstack with index p_padstack_no for 1 <= p_padstack_no <= padstack_count
     */
    public Padstack get(int p_padstack_no)
    {
        if (p_padstack_no <= 0 || p_padstack_no > padstack_arr.size())
        {
            Integer padstack_count = padstack_arr.size();
            System.out.println("Padstacks.get: 1 <= p_padstack_no <= " + padstack_count.toString() + " expected");
            return null;
        }
        Padstack result = padstack_arr.elementAt(p_padstack_no - 1);
        if (result != null && result.no != p_padstack_no)
        {
            System.out.println("Padstacks.get: inconsistent padstack number");
        }
        return result;
    }
    
    /**
     * Appends a new padstack with the input shapes to this padstacks.
     * p_shapes is an array of dimension board layer_count.
     * p_drill_allowed indicates, if vias of the own net are allowed to overlap with this padstack
     * If p_placed_absolute is false, the layers of the padstack are mirrored, if it is placed on the back side.
     */
    public Padstack add(String p_name, ConvexShape [] p_shapes, boolean p_drill_allowed, boolean p_placed_absolute)
    {
        Padstack new_padstack =
                new Padstack(p_name, padstack_arr.size() + 1, p_shapes, p_drill_allowed, p_placed_absolute, this);
        padstack_arr.add(new_padstack);
        return new_padstack;
    }
    
    /**
     * Appends a new padstack with the input shapes to this padstacks.
     * p_shapes is an array of dimension board layer_count.
     * The padatack name is generated internally.
     */
    public Padstack add(ConvexShape [] p_shapes)
    {
        String new_name = "padstack#" + (new Integer(padstack_arr.size() + 1).toString());
        return add(new_name, p_shapes, false, false);
    }
    
    /**
     * Appends a new padstack withe the input shape from p_from_layer to p_to_layer
     * and null on the other layers.
     * The padatack name is generated internally.
     */
    public Padstack add(ConvexShape p_shape, int p_from_layer, int p_to_layer)
    {
        ConvexShape[] shape_arr = new ConvexShape[board_layer_structure.arr.length];
        int from_layer = Math.max(p_from_layer, 0);
        int to_layer = Math.min(p_to_layer, board_layer_structure.arr.length - 1);
        for (int i = from_layer; i <= to_layer; ++i)
        {
            shape_arr[i] = p_shape;
        }
        return add(shape_arr);
    }
    
    /** The array of Padstacks in this object */
    private Vector<Padstack> padstack_arr;
    
    /** The layer structure of each padstack. */
    public final board.LayerStructure board_layer_structure;
}
