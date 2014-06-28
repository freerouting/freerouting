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
 * Components.java
 *
 * Created on 9. Juni 2004, 06:47
 *
 */

package board;


import java.util.Iterator;
import java.util.Vector;

import datastructures.UndoableObjects;

import geometry.planar.Point;
import geometry.planar.IntPoint;

import library.Package;

/**
 * Contains the lists of components on the board.
 *
 * @author  Alfons Wirtz
 */
public class Components implements java.io.Serializable
{
    /**
     * Inserts a component into the list.
     * The items of the component have to be inserted seperately into the board.
     * If p_on_front is false, the component will be placed on the back side,
     * and p_package_back is used instead of p_package_front.
     */
    public Component add(String p_name, Point p_location, double p_rotation_in_degree,
            boolean p_on_front, Package p_package_front, Package p_package_back, boolean p_position_fixed)
    {
        
        Component new_component =
                new Component(p_name, p_location, p_rotation_in_degree, p_on_front, p_package_front,
                p_package_back, component_arr.size() + 1, p_position_fixed);
        component_arr.add(new_component);
        undo_list.insert(new_component);
        return new_component;
    }
    
    /**
     * Adds a component to this object.
     * The items of the component have to be inserted seperately into the board.
     * If p_on_front is false, the component will be placed on the back side.
     * The component name is generated internally.
     */
    public Component add(Point p_location, double p_rotation, boolean p_on_front, Package p_package)
    {
        String component_name = "Component#" + (new Integer(component_arr.size() + 1)).toString();
        return add(component_name, p_location, p_rotation, p_on_front, p_package, p_package, false);
    }
    
    /**
     * Returns the component with the input name or null,
     * if no such component exists.
     */
    public Component get(String p_name)
    {
        Iterator<Component> it = component_arr.iterator();
        while(it.hasNext())
        {
            Component curr = it.next();
            if (curr.name.equals(p_name))
            {
                return curr;
            }
        }
        return null;
    }
    
    /**
     * Returns the component with the input component number or null,
     * if no such component exists.
     * Component numbers are from 1 to component count
     */
    public Component get(int p_component_no)
    {
        Component result =  component_arr.elementAt(p_component_no - 1);
        if (result != null && result.no != p_component_no)
        {
            System.out.println("Components.get: inconsistent component number");
        }
        return result;
    }
    
    /**
     * Returns the number of components on the board.
     */
    public int count()
    {
        return component_arr.size();
    }
    
    /**
     * Generates a snapshot for the undo algorithm.
     */
    public void generate_snapshot()
    {
        this.undo_list.generate_snapshot();
    }
    
    /**
     * Restores the sitiation at the previous snapshot.
     * Returns false, if no more undo is possible.
     */
    public boolean undo(BoardObservers p_observers)
    {
        if (!this.undo_list.undo(null, null))
        {
            return false;
        }
        restore_component_arr_from_undo_list(p_observers);
        return true;
    }
    
    /**
     * Restores the sitiation before the last undo.
     * Returns false, if no more redo is possible.
     */
    public boolean redo(BoardObservers p_observers)
    {
        if (!this.undo_list.redo(null, null))
        {
            return false;
        }
        restore_component_arr_from_undo_list(p_observers);
        return true;
    }
    
    /*
     * Restore the components in component_arr from the undo list.
     */
    private void restore_component_arr_from_undo_list(BoardObservers p_observers)
    {
        Iterator<UndoableObjects.UndoableObjectNode> it  = this.undo_list.start_read_object();
        for (;;)
        {
            Component curr_component = (Component) this.undo_list.read_object(it);
            if (curr_component == null)
            {
                break;
            }
            this.component_arr.setElementAt(curr_component, curr_component.no - 1);
            p_observers.notify_moved(curr_component);
        }
    }
    
    /**
     * Moves the component with number p_component_no.
     * Works contrary to Component.translate_by with the undo algorithm of the board.
     */
    public void move(int p_component_no, geometry.planar.Vector p_vector )
    {
        Component curr_component = this.get(p_component_no);
        this.undo_list.save_for_undo(curr_component);
        curr_component.translate_by(p_vector);
    }
    
    /**
     * Turns the component with number p_component_no  by p_factor times 90 degree around p_pole.
     * Works contrary to Component.turn_90_degree with the undo algorithm of the board.
     */
    public void turn_90_degree(int p_component_no, int p_factor, IntPoint p_pole)
    {
        Component curr_component = this.get(p_component_no);
        this.undo_list.save_for_undo(curr_component);
        curr_component.turn_90_degree(p_factor, p_pole);
    }
    
       /**
     * Rotates the component with number p_component_no  by p_rotation_in_degree around p_pole.
     * Works contrary to Component.rotate with the undo algorithm of the board.
     */
    public void rotate (int p_component_no, double p_rotation_in_degree, IntPoint p_pole)
    {
        Component curr_component = this.get(p_component_no);
        this.undo_list.save_for_undo(curr_component);
        curr_component.rotate(p_rotation_in_degree, p_pole, flip_style_rotate_first);
    }
    
    /**
     * Changes the placement side of the component the component with numberp_component_no and
     * mirrors it  at the vertical line through p_pole.
     * Works contrary to Component.change_side the undo algorithm of the board.
     */
    public void change_side(int p_component_no, IntPoint p_pole)
    {
        Component curr_component = this.get(p_component_no);
        this.undo_list.save_for_undo(curr_component);
        curr_component.change_side(p_pole);
    }
    
    /**
     * If true, components on the back side are rotated before mirroring,
     * else they are mirrored before rotating.
     */
    public void set_flip_style_rotate_first(boolean p_value)
    {
        flip_style_rotate_first = p_value;
    }
    
    /**
     * If true, components on the back side are rotated before mirroring,
     * else they are mirrored before rotating.
     */
    public boolean get_flip_style_rotate_first()
    {
        return flip_style_rotate_first;
    }
    
    private final UndoableObjects undo_list = new UndoableObjects();
    
    private Vector<Component> component_arr = new Vector<Component>();
    
    /**
     * If true, components on the back side are rotated before mirroring,
     * else they are mirrored before rotating.
     */
    private  boolean flip_style_rotate_first = false;
}
