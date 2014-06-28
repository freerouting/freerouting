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
 * SearchTreeManager.java
 *
 * Created on 9. Januar 2006, 08:28
 *
 */

package board;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Iterator;

import datastructures.UndoableObjects;
import datastructures.ShapeTree;

import geometry.planar.FortyfiveDegreeBoundingDirections;
import geometry.planar.Polyline;

/**
 *
 * @author Alfons Wirtz
 */
public class SearchTreeManager
{
    
    /** Creates a new instance of SearchTreeManager */
    public SearchTreeManager(BasicBoard p_board)
    {
        board = p_board;
        compensated_search_trees = new LinkedList<ShapeSearchTree>();
        default_tree =  new ShapeSearchTree(FortyfiveDegreeBoundingDirections.INSTANCE, p_board, 0);
        compensated_search_trees.add(default_tree);
        this.clearance_compensation_used = false;
    }
    
    /**
     * Inserts the tree shapes of p_item into all active search trees.
     */
    public void insert(Item p_item)
    {
        for (ShapeSearchTree curr_tree : compensated_search_trees)
        {
            curr_tree.insert(p_item);
        }
        p_item.set_on_the_board(true);
    }
    
    /**
     * Removes all entries of an item from the search trees.
     */
    public void remove(Item p_item)
    {
        if (!p_item.is_on_the_board())
        {
            return;
        }
        for (ShapeSearchTree curr_tree : compensated_search_trees)
        {
            
            ShapeTree.Leaf[] curr_tree_entries = p_item.get_search_tree_entries(curr_tree);
            {
                if (curr_tree_entries != null)
                {
                    curr_tree.remove(curr_tree_entries);
                }
            }
        }
        p_item.clear_search_tree_entries();
        p_item.set_on_the_board(false);
    }
    
    /**
     * Returns the default tree used in interactive routing.
     */
    public ShapeSearchTree get_default_tree()
    {
        return default_tree;
    }
    
    boolean validate_entries(Item p_item)
    {
        boolean result = true;
        for (ShapeSearchTree curr_tree : compensated_search_trees)
        {
            
            if (!curr_tree.validate_entries(p_item))
            {
                result = false;
            }
        }
        return result;
    }
    
    /**
     * Returns, if clearance compensation is used for the default tree.
     * This is normally the case, if there exist only the clearance classes null and default
     * in the clearance matrix.
     */
    public boolean is_clearance_compensation_used()
    {
        return this.clearance_compensation_used;
    }
    
    /**
     * Sets the usage of clearance compensation to true or false.
     */
    public void set_clearance_compensation_used(boolean p_value)
    {
        if (this.clearance_compensation_used == p_value)
        {
            return;
        }
        
        this.clearance_compensation_used = p_value;
        remove_all_board_items();
        this.compensated_search_trees.clear();
        int compensated_clearance_class_no;
        if (p_value)
        {
            compensated_clearance_class_no = 1;
        }
        else
        {
            compensated_clearance_class_no = 0;
        }
        default_tree  = new ShapeSearchTree(FortyfiveDegreeBoundingDirections.INSTANCE, this.board, compensated_clearance_class_no);
        this.compensated_search_trees.add(default_tree);
        insert_all_board_items();
    }
    
    /**
     * Actions to be done, when a value in the clearance matrix is changed interactively.
     */
    public void clearance_value_changed()
    {
        // delete all trees except the default tree
        Iterator<ShapeSearchTree> it = this.compensated_search_trees.iterator();
        while(it.hasNext())
        {
            ShapeSearchTree curr_tree = it.next();
            if (curr_tree.compensated_clearance_class_no != default_tree.compensated_clearance_class_no)
            {
                it.remove();
            }
        }
        if (this.clearance_compensation_used)
        {
            remove_all_board_items();
            insert_all_board_items();
        }
    }
    
    /**
     * Actions to be done, when a new clearance class is removed interactively.
     */
    public void clearance_class_removed(int p_no)
    {
        Iterator<ShapeSearchTree> it = this.compensated_search_trees.iterator();
        if (p_no == default_tree.compensated_clearance_class_no)
        {
            System.out.println("SearchtreeManager.clearance_class_removed: unable to remove default tree");
            return;
        }
        while(it.hasNext())
        {
            ShapeSearchTree curr_tree = it.next();
            if (curr_tree.compensated_clearance_class_no == p_no)
            {
                it.remove();
            }
        }
    }
    
    /**
     * Returns the tree compensated for the clearance class with number p_clearance_vlass_no.
     * Initialized the tree, if it is not yet allocated.
     */
    public ShapeSearchTree get_autoroute_tree(int p_clearance_class_no)
    {
        for (ShapeSearchTree curr_tree : compensated_search_trees)
        {
            if (curr_tree.compensated_clearance_class_no == p_clearance_class_no)
            {
                return curr_tree;
            }
        }
        // tree is not yet initialized
        ShapeSearchTree curr_autoroute_tree;
        boolean fast_algorithm = !this.board.rules.get_slow_autoroute_algorithm();
        if (fast_algorithm && this.board.rules.get_trace_angle_restriction() == AngleRestriction.NINETY_DEGREE)
        {
            curr_autoroute_tree = new ShapeSearchTree90Degree(this.board, p_clearance_class_no);
        }
        else if (fast_algorithm && this.board.rules.get_trace_angle_restriction() == AngleRestriction.FORTYFIVE_DEGREE)
        {
            curr_autoroute_tree = new ShapeSearchTree45Degree(this.board, p_clearance_class_no);
        }
        else
        {
            curr_autoroute_tree = new ShapeSearchTree(FortyfiveDegreeBoundingDirections.INSTANCE, this.board, p_clearance_class_no);
        }
        this.compensated_search_trees.add(curr_autoroute_tree);
        Iterator<UndoableObjects.UndoableObjectNode> it = this.board.item_list.start_read_object();
        for(;;)
        {
            Item curr_item = (Item) this.board.item_list.read_object(it);
            if (curr_item == null)
            {
                break;
            }
            curr_autoroute_tree.insert(curr_item);
        }
        return curr_autoroute_tree;
    }
    
    /**
     * Clears all compensated trees used in the autoroute algorithm apart from the default tree.
     */
    public void reset_compensated_trees()
    {
        Iterator<ShapeSearchTree> it = this.compensated_search_trees.iterator();
        while (it.hasNext())
        {
            ShapeSearchTree curr_tree = it.next();
            if (curr_tree != default_tree)
            {
                it.remove();
            }
        }
    }
    
    /** Reinsert all items into the search trees */
    void reinsert_tree_items()
    {
        remove_all_board_items();
        insert_all_board_items();
    }
    
    private void remove_all_board_items()
    {
        if (this.board == null)
        {
            System.out.println("SearchtreeManager.remove_all_board_items: board is null");
            return;
        }
        Iterator<UndoableObjects.UndoableObjectNode> it = this.board.item_list.start_read_object();
        for(;;)
        {
            Item curr_item = (Item) this.board.item_list.read_object(it);
            if (curr_item == null)
            {
                break;
            }
            this.remove(curr_item);
        }
    }
    
    private void insert_all_board_items()
    {
        if (this.board == null)
        {
            System.out.println("SearchtreeManager.insert_all_board_items: board is null");
            return;
        }
        Iterator<UndoableObjects.UndoableObjectNode> it = this.board.item_list.start_read_object();
        for(;;)
        {
            Item curr_item = (Item) this.board.item_list.read_object(it);
            if (curr_item == null)
            {
                break;
            }
            curr_item.clear_derived_data();
            this.insert(curr_item);
        }
    }
    
    //********************************************************************************
    
    // The following functions are used internally for perfomance improvement.
    
    //********************************************************************************
    
    /**
     * Merges the tree entries from p_from_trace in front of p_to_trace.
     * Special implementation for combine trace for performance reasons.
     */
    void merge_entries_in_front( PolylineTrace p_from_trace, PolylineTrace p_to_trace,
            Polyline p_joined_polyline, int p_from_entry_no, int  p_to_entry_no)
    {
        for (ShapeSearchTree curr_tree : compensated_search_trees)
        {
            curr_tree.merge_entries_in_front(p_from_trace, p_to_trace, p_joined_polyline, p_from_entry_no, p_to_entry_no);
        }
    }
    
    /**
     * Merges the tree entries from p_from_trace to the end of p_to_trace.
     * Special implementation for combine trace for performance reasons.
     */
    void merge_entries_at_end( PolylineTrace p_from_trace, PolylineTrace p_to_trace,
            Polyline p_joined_polyline, int p_from_entry_no, int  p_to_entry_no)
    {
        for (ShapeSearchTree curr_tree : compensated_search_trees)
        {
            curr_tree.merge_entries_at_end(p_from_trace, p_to_trace, p_joined_polyline, p_from_entry_no, p_to_entry_no);
        }
    }
    
    /**
     * Changes the tree entries from p_keep_at_start_count + 1
     * to new_shape_count - 1 - keep_at_end_count to p_changed_entries.
     * Special implementation for change_trace for performance reasons
     */
    void change_entries( PolylineTrace p_obj, Polyline p_new_polyline,
            int p_keep_at_start_count, int p_keep_at_end_count)
    {
        for (ShapeSearchTree curr_tree : compensated_search_trees)
        {
            curr_tree.change_entries(p_obj, p_new_polyline, p_keep_at_start_count, p_keep_at_end_count);
        }
    }
    
    /**
     * Trannsfers tree entries from p_from_trace to p_start and p_end_piece
     * after a moddle piece was cut out.
     * Special implementation for ShapeTraceEntries.fast_cutout_trace for performance reasoms.
     */
    void reuse_entries_after_cutout(PolylineTrace p_from_trace, PolylineTrace p_start_piece, PolylineTrace p_end_piece)
    {
        for (ShapeSearchTree curr_tree : compensated_search_trees)
        {
            
            curr_tree.reuse_entries_after_cutout(p_from_trace, p_start_piece, p_end_piece);
        }
    }
    
    private final Collection<ShapeSearchTree> compensated_search_trees;
    
    private ShapeSearchTree default_tree;
    
    private final BasicBoard board;
    
    private boolean clearance_compensation_used;
}
