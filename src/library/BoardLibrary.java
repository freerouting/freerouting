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
 * BoardLibrary.java
 *
 * Created on 4. Juni 2004, 06:37
 */

package library;

import java.util.List;
import java.util.Vector;

/**
 * Describes a board library of packages and padstacks.
 *
 * @author  alfons
 */
public class BoardLibrary implements java.io.Serializable
{
    
    /** Creates a new instance of BoardLibrary */
    public BoardLibrary(Padstacks p_padstacks, Packages p_packages)
    {
        padstacks = p_padstacks;
        packages = p_packages;
        logical_parts = new LogicalParts();
    }
    
    /** Creates a new instance of BoardLibrary */
    public BoardLibrary()
    {
        
    }
    
    /** Sets the subset of padstacks from this.padstacks, which can be used in routing for inserting vias. */
    public void set_via_padstacks(Padstack[] p_padstacks)
    {
        
        this.via_padstacks =  new Vector<Padstack>();
        for (int i = 0; i < p_padstacks.length; ++i)
        {
          this.via_padstacks.add(p_padstacks[i]);  
        }
    }
    
    /** The count of padstacks from this.padstacks, which can be used in routing */
    public int via_padstack_count()
    {
        if (this.via_padstacks == null)
        {
            return 0;
        }
        return this.via_padstacks.size();
    }
    
    /** Gets the via padstack for routing with index p_no */
    public Padstack get_via_padstack(int p_no)
    {
        if (this.via_padstacks == null || p_no < 0 || p_no >= this.via_padstacks.size())
        {
            return null;
        }
        return this.via_padstacks.get(p_no);
    }
    
    /** Gets the via padstack with name p_name, or null, if no such padstack exists. */
    public Padstack get_via_padstack(String p_name)
    {
        if (this.via_padstacks == null)
        {
            return null;
        }
        for(Padstack curr_padstack : this.via_padstacks)
        {
            if (curr_padstack.name.equals(p_name))
            {
                return curr_padstack;
            }
        }
        return null;
    }
    
    /**
     * Returns the via padstacks, which can be used for routing.
     */
    public Padstack[] get_via_padstacks()
    {
        if (this.via_padstacks == null)
        {
            return new Padstack[0];
        }
        Padstack[] result = new Padstack[via_padstacks.size()];
        for (int i = 0; i < result.length; ++i)
        {
            result[i] = via_padstacks.get(i);
        }
        return result;
    }
    
    /**
     * Apppends p_padstack to the list of via padstacks.
     * Returns false, if the list  contains already a  padstack  with p_padstack.name.
     */
    public boolean add_via_padstack(Padstack p_padstack)
    {
       if (get_via_padstack(p_padstack.name) != null) 
       {
           return false;
       }
       this.via_padstacks.add(p_padstack);  
       return true;
    }
    
    /** 
     * Removes p_padstack from the via padstack list. 
     * Returns false, if p_padstack was not found in the list.
     * If the padstack is no more used on the board, it will also be removed from the board padstacks. 
     */
    public boolean remove_via_padstack(Padstack p_padstack, board.BasicBoard p_board)
    {
        boolean result = via_padstacks.remove(p_padstack);
        return result;
    }
    
        
    /**
     * Gets the via padstack mirrored to the back side of the board.
     * Returns null, if no such via padstack exists.
     */
    public Padstack get_mirrored_via_padstack(Padstack p_via_padstack)
    {
        int layer_count = this.padstacks.board_layer_structure.arr.length;
        if (p_via_padstack.from_layer() == 0 && p_via_padstack.to_layer() == layer_count - 1)
        {
            return p_via_padstack;
        }
        int new_from_layer =layer_count - p_via_padstack.to_layer() - 1;
        int new_to_layer = layer_count - p_via_padstack.from_layer() - 1;
        for (Padstack curr_via_padstack : via_padstacks)
        {
            if (curr_via_padstack.from_layer() == new_from_layer && curr_via_padstack.to_layer() == new_to_layer)
            {
                return curr_via_padstack;
            }
        }
        return null;
    }
    
    /**
     * Looks, if the input padstack is used on p_board in a Package or in drill.
     */
    public boolean is_used (Padstack p_padstack, board.BasicBoard p_board)
    {
        java.util.Iterator<datastructures.UndoableObjects.UndoableObjectNode> it = p_board.item_list.start_read_object();
        for(;;)
        {
            datastructures.UndoableObjects.Storable curr_item = p_board.item_list.read_object(it);
            if (curr_item == null)
            {
                break;
            }
            if (curr_item instanceof board.DrillItem)
            {
                if (((board.DrillItem) curr_item).get_padstack() == p_padstack)
                {
                    return true;
                }
            }
        }
        for (int i = 1; i <= this.packages.count(); ++i)
        {
            Package curr_package = this.packages.get(i);
            for (int j = 0; j < curr_package.pin_count(); ++j)
            {
                if(curr_package.get_pin(j).padstack_no == p_padstack.no)
                {
                    return true;
                }
            }
        }
        return false;
    }
    
    public Padstacks padstacks = null;
    public Packages packages = null;
    /** Containes information for gate swap and pin swap in the Specctra-dsn format. */
    public LogicalParts logical_parts = new LogicalParts();
    
    /** The subset of padstacks in the board library, which can be used in routing for inserting vias. */
    private List<Padstack> via_padstacks = null;
}
