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
 * ItemSearchTreesInfo.java
 *
 * Created on 10. Januar 2006, 08:46
 *
 */

package board;

import java.util.Collection;
import java.util.LinkedList;

import  datastructures.ShapeTree;

import geometry.planar.TileShape;

/**
 * Stores information about the search trees of the board items,
 * which is precalculated for performance reasons.
 *
 * @author Alfons Wirtz
 */
class ItemSearchTreesInfo
{
    
    /** Creates a new instance of ItemSearchTreeEntries */
    public ItemSearchTreesInfo()
    {
        this.tree_list = new LinkedList<SearchTreeInfo>();
    }
    
    /**
     * Returns the tree entries for the tree with identification number p_tree_no,
     * or null, if for this tree no entries of this item are inserted.
     */
    public ShapeTree.Leaf[] get_tree_entries(ShapeTree p_tree)
    {
        for (SearchTreeInfo curr_tree_info : this.tree_list)
        {
            if (curr_tree_info.tree == p_tree)
            {
                return curr_tree_info.entry_arr;
            }
        }
        return null;
    }
    
    /**
     * Sets the item tree entries for the  tree with identification number p_tree_no.
     */
    public void set_tree_entries(ShapeTree.Leaf[] p_tree_entries, ShapeTree p_tree)
    {
        for (SearchTreeInfo curr_tree_info : this.tree_list)
        {
            if (curr_tree_info.tree == p_tree)
            {
                curr_tree_info.entry_arr = p_tree_entries;
                return;
            }
        }
        SearchTreeInfo new_tree_info = new SearchTreeInfo(p_tree);
        new_tree_info.entry_arr = p_tree_entries;
        this.tree_list.add(new_tree_info);
    }
    
    /**
     * Returns the precalculated tiles hapes  for the tree with identification number p_tree_no,
     * or null, if the tile shapes of this tree are nnot yet precalculated.
     */
    public TileShape[] get_precalculated_tree_shapes(ShapeTree p_tree)
    {
        for (SearchTreeInfo curr_tree_info : this.tree_list)
        {
            if (curr_tree_info.tree == p_tree)
            {
                return curr_tree_info.precalculated_tree_shapes;
            }
        }
        return null;
    }
    
    /**
     * Sets the item tree entries for the  tree with identification number p_tree_no.
     */
    public void set_precalculated_tree_shapes(TileShape[] p_tile_shapes, ShapeTree p_tree)
    {
        for (SearchTreeInfo curr_tree_info : this.tree_list)
        {
            if (curr_tree_info.tree == p_tree)
            {
                curr_tree_info.precalculated_tree_shapes = p_tile_shapes;
                return;
            }
        }
        SearchTreeInfo new_tree_info = new SearchTreeInfo(p_tree);
        new_tree_info.precalculated_tree_shapes = p_tile_shapes;
        this.tree_list.add(new_tree_info);
    }
    
    /**
     * clears the stored information about the precalculated tree shapes for all search trees.
     */
    public void clear_precalculated_tree_shapes()
    {
        for (SearchTreeInfo curr_tree_info : this.tree_list)
        {
            
            curr_tree_info.precalculated_tree_shapes = null;
        }
    }
    
    
    private final Collection<SearchTreeInfo> tree_list;
    
    private static class SearchTreeInfo
    {
        SearchTreeInfo(ShapeTree p_tree)
        {
            tree = p_tree;
            entry_arr = null;
            precalculated_tree_shapes = null;
        }
        
        final ShapeTree tree;
        ShapeTree.Leaf [] entry_arr;
        TileShape [] precalculated_tree_shapes;
    }
}
