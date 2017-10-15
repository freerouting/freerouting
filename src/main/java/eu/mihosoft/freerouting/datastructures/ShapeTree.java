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
 * ShapeTree.java
 *
 * Created on 1. September 2004, 10:50
 */

package datastructures;

import geometry.planar.ShapeBoundingDirections;
import geometry.planar.RegularTileShape;
import geometry.planar.Shape;
import geometry.planar.TileShape;

/**
 * Abstract binary search tree for shapes in the plane.
 * The shapes are stored in the leafs of the tree.
 * Objects to be stored in the tree must implement the interface ShapeTree.Storable.
 *
 * @author  Alfons Wirtz
 */
public abstract class ShapeTree
{
    
    /** Creates a new instance of ShapeTree */
    public ShapeTree(ShapeBoundingDirections p_directions)
    {
        bounding_directions = p_directions ;
        root = null ;
        leaf_count = 0;
    }
    
    
    /**
     * Inserts all shapes of p_obj into the tree
     */
    public void insert(ShapeTree.Storable p_obj)
    {
        int shape_count = p_obj.tree_shape_count(this);
        if (shape_count <= 0)
        {
            return;
        }
        Leaf [] leaf_arr = new Leaf [shape_count];
        for (int i = 0; i < shape_count; ++i)
        {
            leaf_arr [i] = insert(p_obj, i);
        }
        p_obj.set_search_tree_entries(leaf_arr, this);
    }
    
    /**
     * Insert a shape - creates a new node with a bounding shape
     */
    protected Leaf insert(ShapeTree.Storable p_object, int p_index)
    {
        Shape object_shape = p_object.get_tree_shape(this, p_index);
        if (object_shape == null)
        {
            return null;
        }
        
        RegularTileShape  bounding_shape = object_shape.bounding_shape(bounding_directions) ;
        if (  bounding_shape == null )
        {
            System.out.println("ShapeTree.insert: bounding shape of TreeObject is null");
            return null;
        }
        // Construct a new KdLeaf and set it up
        Leaf new_leaf = new Leaf(p_object, p_index, null, bounding_shape) ;
        this.insert(new_leaf);
        return new_leaf;
    }
    
    
    /** Inserts the leaves of this tree into an array. */
    public Leaf[] to_array()
    {
        Leaf [] result = new Leaf[this.leaf_count];
        if (result.length == 0)
        {
            return result;
        }
        TreeNode curr_node = this.root;
        int curr_index = 0;
        for (;;)
        {
            // go down from curr_node to the left most leaf
            while (curr_node instanceof InnerNode)
            {
                curr_node = ((InnerNode) curr_node).first_child;
            }
            result[curr_index] = (Leaf) curr_node;
            
            ++curr_index;
            // go up until parent.second_child != curr_node, which means we came from first_child
            InnerNode curr_parent = curr_node.parent;
            while (curr_parent != null && curr_parent.second_child == curr_node)
            {
                curr_node = curr_parent;
                curr_parent = curr_node.parent;
            }
            if (curr_parent == null)
            {
                break;
            }
            curr_node = curr_parent.second_child;
        }
        return result;
    }
    
    abstract void insert(Leaf p_leaf);
    
    abstract void remove_leaf(Leaf p_leaf);
    
    
    
    /**
     * removes all entries of p_obj in the tree.
     */
    public void remove(Leaf []  p_entries)
    {
        if (p_entries == null)
        {
            return;
        }
        for (int i = 0; i < p_entries.length; ++i)
        {
            remove_leaf(p_entries[i]);
        }
    }
    
    /** Returns the number of entries stored in the tree. */
    public int size()
    {
        return leaf_count;
    }
    
    
    /** Outputs some statistic information about the tree. */
    public void statistics(String p_message)
    {
        Leaf[] leaf_arr = this.to_array();
        double cumulative_depth = 0;
        int maximum_depth = 0;
        for (int i = 0; i < leaf_arr.length; ++ i)
        {
            if (leaf_arr[i] != null)
            {
                int distance_to_root = leaf_arr[i].distance_to_root();
                cumulative_depth += distance_to_root;
                maximum_depth = Math.max(maximum_depth, distance_to_root);
            }
        }
        double everage_depth = cumulative_depth / leaf_arr.length;
        System.out.print("MinAreaTree: Entry count: ");
        System.out.print(leaf_arr.length);
        System.out.print(" log: ");
        System.out.print(Math.round(Math.log(leaf_arr.length)));
        System.out.print(" Everage depth: ");
        System.out.print(Math.round(everage_depth));
        System.out.print(" ");
        System.out.print(" Maximum depth: ");
        System.out.print(maximum_depth);
        System.out.print(" ");
        System.out.println(p_message);
    }
    
    
    /**
     * the fixed directions for calculating bounding RegularTileShapes
     * of shapes  to store in this tree.
     */
    final protected ShapeBoundingDirections bounding_directions ;
    
    /** Root node - initially null */
    protected TreeNode root ;
    
    /** The number of entries stored in the tree */
    protected int leaf_count;
    
    /**
     * Interface, which must be implemented by objects to be stored
     * in a ShapeTree.
     */
    public interface Storable extends Comparable<Object>
    {
        /**
         * Number of shapes of an object to store in p_shape_tree
         */
        int tree_shape_count(ShapeTree p_shape_tree) ;
        
        /**
         * Get the Shape of this object with index p_index stored in the ShapeTree with index identification number p_tree_id_no
         */
        TileShape get_tree_shape(ShapeTree p_tree, int p_index) ;
        
        /**
         * Stores the entries in the ShapeTrees of this object for
         * better performance while for example deleting tree entries.
         * Called only by insert methods of class ShapeTree.
         */
        void set_search_tree_entries(Leaf [] p_entries, ShapeTree p_tree);
    }
    
    /**
     * Information of a single object stored in a tree
     */
    public static class TreeEntry
    {
        public TreeEntry(ShapeTree.Storable p_object, int p_shape_index_in_object)
        {
            object = p_object;
            shape_index_in_object = p_shape_index_in_object;
        }
        public final ShapeTree.Storable object;
        public final int shape_index_in_object;
    }
    
    //////////////////////////////////////////////////////////
    /** Common functionality of inner nodes and leaf nodes. */
    protected static class TreeNode
    {
        public RegularTileShape bounding_shape ;
        InnerNode parent;
    }
    
    //////////////////////////////////////////////////////////
    /**
     * Desscription of an inner node of the tree,
     * which implements a fork to its two children.
     */
    public static class InnerNode extends TreeNode
    {
        public InnerNode( RegularTileShape p_bounding_shape, InnerNode p_parent )
        {
            bounding_shape = p_bounding_shape;
            parent = p_parent;
            first_child = null ;
            second_child = null ;
        }
        
        public TreeNode first_child ;
        public TreeNode second_child ;
    }
    
    //////////////////////////////////////////////////////////
    /**
     * Description of a leaf of the Tree, where the geometric
     * information is stored.
     */
    public static class Leaf extends TreeNode implements Comparable<Leaf>
    {
        public Leaf( ShapeTree.Storable p_object, int p_index, InnerNode p_parent, RegularTileShape p_bounding_shape)
        {
            bounding_shape = p_bounding_shape;
            parent = p_parent;
            object = p_object ;
            shape_index_in_object = p_index ;
        }
        
        public int compareTo(Leaf p_other)
        {
            int result = this.object.compareTo(p_other.object);
            if (result == 0)
            {
                result =  shape_index_in_object - p_other.shape_index_in_object;
            }
            return result;
        }
        
        /** Returns the number of nodes between this leaf and the croot of the tree. */
        public int distance_to_root()
        {
            int result = 1;
            InnerNode curr_parent = this.parent;
            while (curr_parent.parent != null)
            {
                curr_parent = curr_parent.parent;
                ++result;
            }
            return result;
        }
        
        /** Actual object stored */
        public ShapeTree.Storable object ;
        
        /** index of the shape in the object */
        public int shape_index_in_object;
    }
}
