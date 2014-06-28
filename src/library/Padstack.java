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
 * Padstack.java
 *
 * Created on 27. Mai 2004, 06:35
 */

package library;

import geometry.planar.ConvexShape;
import geometry.planar.Direction;
import geometry.planar.IntBox;
import geometry.planar.IntOctagon;

/**
 * Describes padstack masks for pins or vias located at the origin.
 *
 * @author  alfons
 */
public class Padstack implements Comparable<Padstack>, board.ObjectInfoPanel.Printable, java.io.Serializable
{
    
    /**
     * Creates a new Padstack with shape p_shapes[i] on layer i (0 <= i < p_shapes.length).
     * p_is_drilllable indicates, if vias of the own net are allowed to overlap with this padstack
     * If p_placed_absolute is false, the layers of the padstack are mirrored, if it is placed on the back side.
     * p_padstack_list is the list, where this padstack belongs to.
     */
    Padstack(String p_name, int p_no, ConvexShape[] p_shapes, boolean p_is_drilllable,
            boolean p_placed_absolute, Padstacks p_padstack_list)
    {
        shapes = p_shapes;
        name = p_name;
        no = p_no;
        attach_allowed = p_is_drilllable;
        placed_absolute = p_placed_absolute;
        padstack_list = p_padstack_list;
    }
    
    /**
     * Compares 2 padstacks by name.
     * Useful for example to display padstacks in alphabetic order.
     */
    public int compareTo(Padstack p_other)
    {
        return this.name.compareToIgnoreCase(p_other.name);
    }
    
    /**
     * Gets the shape of this padstack on layer p_layer
     */
    public ConvexShape get_shape(int p_layer)
    {
        if (p_layer < 0 || p_layer >= shapes.length)
        {
            System.out.println("Padstack.get_layer p_layer out of range");
            return null;
        }
        return shapes[p_layer];
    }
    
    /**
     * Returns the first layer of this padstack with a shape != null.
     */
    public int from_layer()
    {
        int result = 0;
        while (result < shapes.length && shapes[result] == null)
        {
            ++result;
        }
        return result;
    }
    
    /**
     * Returns the last layer of this padstack with a shape != null.
     */
    public int to_layer()
    {
        int result = shapes.length - 1;
        while (result >= 0 && shapes[result] == null)
        {
            --result;
        }
        return result;
    }
    
    /** Returns the layer ciount of the board of this padstack. */
    public int board_layer_count()
    {
        return shapes.length;
    }
    
    public String toString()
    {
        return this.name;
    }
    
    /**
     * Calculates the allowed trace exit directions of the shape of this padstack on layer p_layer.
     * If the length of the pad is smaller than p_factor times the height of the pad,
     * connection also to the long side is allowed.
     */
    public java.util.Collection<Direction> get_trace_exit_directions(int p_layer, double p_factor)
    {
        java.util.Collection<Direction> result = new java.util.LinkedList<Direction>();
        if (p_layer < 0 || p_layer >= shapes.length)
        {
            return result;
        }
        ConvexShape curr_shape = shapes[p_layer];
        if (curr_shape == null)
        {
            return result;
        }
        if (!(curr_shape instanceof IntBox || curr_shape instanceof IntOctagon))
        {
            return result;
        }
        IntBox curr_box = curr_shape.bounding_box();
 
        boolean all_dirs = false;
        if (Math.max(curr_box.width(), curr_box.height()) < 
                p_factor * Math.min(curr_box.width(), curr_box.height()))
        {
            all_dirs = true;
        }
        
        if (all_dirs || curr_box.width() >= curr_box.height())
        {
            result.add(Direction.RIGHT);
            result.add(Direction.LEFT);
        }
        if (all_dirs || curr_box.width() <= curr_box.height())
        {
            result.add(Direction.UP);
            result.add(Direction.DOWN);
        }
        return result;
    }
    
    public void print_info(board.ObjectInfoPanel p_window, java.util.Locale p_locale)
    {
        java.util.ResourceBundle resources = 
                java.util.ResourceBundle.getBundle("board.resources.ObjectInfoPanel", p_locale);
        p_window.append_bold(resources.getString("padstack") + " ");
        p_window.append_bold(this.name);
        for (int i = 0; i < shapes.length; ++i)
        {
            if (shapes[i] != null)
            {
                p_window.newline();
                p_window.indent();
                p_window.append(shapes[i], p_locale);
                p_window.append(" " + resources.getString("on_layer") + " ");
                p_window.append(padstack_list.board_layer_structure.arr[i].name);
            }
        }
        p_window.newline();
    }
    
    private final ConvexShape [] shapes;
    public final String name;
    public final int no;
    
    /** true, if vias of the own net are allowed to overlap with this padstack*/
    public final boolean attach_allowed;
    
    /**
     * If false, the layers of the padstack are mirrored, if it is placed on the back side.
     * The default is false.
     */
    public final boolean placed_absolute;
    
    /** Pointer to the pacdstack list containing this padstack */
    private final Padstacks padstack_list;
}
