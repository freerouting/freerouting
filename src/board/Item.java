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
 */
package board;

import java.util.Collection;
import java.util.LinkedList;

import geometry.planar.Point;
import geometry.planar.IntPoint;
import geometry.planar.FloatPoint;
import geometry.planar.TileShape;
import geometry.planar.Vector;
import geometry.planar.IntBox;

import java.awt.Color;
import java.awt.Graphics;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import rules.Nets;
import boardgraphics.Drawable;
import boardgraphics.GraphicsContext;

import datastructures.UndoableObjects;
import datastructures.ShapeTree;
import datastructures.ShapeTree.TreeEntry;

/**
 * Basic class of the items on a board.
 *
 * @author Alfons Wirtz
 */
public abstract class Item implements Drawable, SearchTreeObject, ObjectInfoPanel.Printable, UndoableObjects.Storable, Serializable
{

    /**
     * Implements the comparable interface.
     */
    public int compareTo(Object p_other)
    {
        int result;
        if (p_other instanceof Item)
        {
            result = ((Item) p_other).id_no - id_no;
        }
        else
        {
            result = 1;
        }
        return result;
    }

    /**
     * returns the unique idcentification number of this item
     */
    public int get_id_no()
    {
        return id_no;
    }

    /**
     * Returns true if the net number array of this item contains p_net_no.
     */
    public boolean contains_net(int p_net_no)
    {
        if (p_net_no <= 0)
        {
            return false;
        }
        for (int i = 0; i < net_no_arr.length; ++i)
        {
            if (net_no_arr[i] == p_net_no)
            {
                return true;
            }
        }
        return false;
    }

    public boolean is_obstacle(int p_net_no)
    {
        return !contains_net(p_net_no);
    }

    public boolean is_trace_obstacle(int p_net_no)
    {
        return !contains_net(p_net_no);
    }

    /**
     * Returns, if this item in not allowed to overlap with p_other.
     */
    public abstract boolean is_obstacle(Item p_other);

    /**
     *  Returns true if the net number arrays of this and p_other have a common
     *  number.
     */
    public boolean shares_net(Item p_other)
    {
        return this.shares_net_no(p_other.net_no_arr);
    }

    /**
     *  Returns true if the net number array of this and p_net_no_arr have a common
     *  number.
     */
    public boolean shares_net_no(int[] p_net_no_arr)
    {
        for (int i = 0; i < net_no_arr.length; ++i)
        {
            for (int j = 0; j < p_net_no_arr.length; ++j)
            {
                if (net_no_arr[i] == p_net_no_arr[j])
                {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the number of shapes of this item after decomposition into convex polygonal shapes
     */
    public abstract int tile_shape_count();

    /**
     * Returns the p_index-throws  shape of this item after decomposition into convex polygonal shapes
     */
    public TileShape get_tile_shape(int p_index)
    {
        if (this.board == null)
        {
            System.out.println("Item.get_tile_shape: board is null");
            return null;
        }
        return get_tree_shape(this.board.search_tree_manager.get_default_tree(), p_index);
    }

    public int tree_shape_count(ShapeTree p_tree)
    {
        if (this.board == null)
        {
            return 0;
        }
        TileShape[] precalculated_tree_shapes = this.get_precalculated_tree_shapes(p_tree);
        return precalculated_tree_shapes.length;
    }

    public TileShape get_tree_shape(ShapeTree p_tree, int p_index)
    {
        if (this.board == null)
        {
            return null;
        }
        TileShape[] precalculated_tree_shapes = this.get_precalculated_tree_shapes(p_tree);
        return precalculated_tree_shapes[p_index];
    }

    private TileShape[] get_precalculated_tree_shapes(ShapeTree p_tree)
    {
        if (this.search_trees_info == null)
        {
            this.search_trees_info = new ItemSearchTreesInfo();
        }
        TileShape[] precalculated_tree_shapes = this.search_trees_info.get_precalculated_tree_shapes(p_tree);
        if (precalculated_tree_shapes == null)
        {
            precalculated_tree_shapes = this.calculate_tree_shapes((ShapeSearchTree) p_tree);
            this.search_trees_info.set_precalculated_tree_shapes(precalculated_tree_shapes, p_tree);
        }
        return precalculated_tree_shapes;
    }

    /**
     * Caculates the tree shapes for this item for p_search_tree.
     */
    protected abstract TileShape[] calculate_tree_shapes(ShapeSearchTree p_search_tree);

    /**
     * Returns false, if this item is deleted oor not inserted into
     * the board.
     */
    public boolean is_on_the_board()
    {
        return this.on_the_board;
    }

    void set_on_the_board(boolean p_value)
    {
        this.on_the_board = p_value;
    }

    /**
     * Creates a copy of this item with id number p_id_no.
     * If p_id_no <= 0, the id_no of the new item is generated internally
     */
    public abstract Item copy(int p_id_no);

    public Object clone()
    {
        return copy(this.get_id_no());
    }

    /**
     * returns true, if the layer range of this item contains p_layer
     */
    public abstract boolean is_on_layer(int p_layer);

    /**
     * Returns the number of the first layer containing geometry of this item.
     */
    public abstract int first_layer();

    /**
     * Returns the number of the last layer containing geometry of this item.
     */
    public abstract int last_layer();

    /**
     * write this item to an output stream
     */
    public abstract boolean write(java.io.ObjectOutputStream p_stream);

    /**
     * Translates the shapes of this item by p_vector.
     * Does not move the item in the board.
     */
    public abstract void translate_by(Vector p_vector);

    /**
     * Turns this Item by p_factor times 90 degree around p_pole.
     * Does not update the item in the board.
     */
    public abstract void turn_90_degree(int p_factor, IntPoint p_pole);

    /**
     * Rotates this Item by p_angle_in_degree around p_pole.
     * Does not update the item in the board.
     */
    public abstract void rotate_approx(double p_angle_in_degree, FloatPoint p_pole);

    /**
     * Changes the placement side of this Item and mirrors it at the vertical line through p_pole.
     * Does not update the item in the board.
     */
    public abstract void change_placement_side(IntPoint p_pole);

    /**
     * Returns a box containing the geometry of this item.
     */
    public abstract IntBox bounding_box();

    /**
     *  Translates this item by p_vector in the board.
     */
    public void move_by(Vector p_vector)
    {
        board.item_list.save_for_undo(this);
        board.search_tree_manager.remove(this);
        this.translate_by(p_vector);
        board.search_tree_manager.insert(this);
        // let the observers syncronize the changes
        board.communication.observers.notify_changed(this);
    }

    /**
     * Returns true, if some shapes of this item and p_other are
     * on the same layer.
     */
    public boolean shares_layer(Item p_other)
    {
        int max_first_layer = Math.max(this.first_layer(), p_other.first_layer());
        int min_last_layer = Math.min(this.last_layer(), p_other.last_layer());
        return max_first_layer <= min_last_layer;
    }

    /**
     * Returns the first layer, where both this item and p_other have a shape.
     * Returns -1, if such a layer does not exisr.
     */
    public int first_common_layer(Item p_other)
    {
        int max_first_layer = Math.max(this.first_layer(), p_other.first_layer());
        int min_last_layer = Math.min(this.last_layer(), p_other.last_layer());
        if (max_first_layer > min_last_layer)
        {
            return -1;
        }
        return max_first_layer;
    }

    /**
     * Returns the last layer, where both this item and p_other have a shape.
     * Returns -1, if such a layer does not exisr.
     */
    public int last_common_layer(Item p_other)
    {
        int max_first_layer = Math.max(this.first_layer(), p_other.first_layer());
        int min_last_layer = Math.min(this.last_layer(), p_other.last_layer());
        if (max_first_layer > min_last_layer)
        {
            return -1;
        }
        return min_last_layer;
    }

    /**
     * Return the name of the component of this item or null, if this item does not belong to a component.
     */
    public String component_name()
    {
        if (component_no <= 0)
        {
            return null;
        }
        return board.components.get(component_no).name;
    }

    /**
     * Returns the count of clearance violations of this item with
     * other items.
     */
    public int clearance_violation_count()
    {
        Collection<ClearanceViolation> violations = this.clearance_violations();
        return violations.size();
    }

    /**
     * Returns a list of all clearance violations of this item with other items.
     * The objects in the list are of type ClearanceViolations.
     * The first_item in such an object is always this item.
     */
    public Collection<ClearanceViolation> clearance_violations()
    {
        Collection<ClearanceViolation> result = new LinkedList<ClearanceViolation>();
        if (this.board == null)
        {
            return result;
        }
        ShapeSearchTree default_tree = board.search_tree_manager.get_default_tree();
        for (int i = 0; i < tile_shape_count(); ++i)
        {
            TileShape curr_tile_shape = get_tile_shape(i);
            Collection<TreeEntry> curr_overlapping_items =
                    default_tree.overlapping_tree_entries_with_clearance(curr_tile_shape, shape_layer(i), new int[0], clearance_class);
            Iterator<TreeEntry> it = curr_overlapping_items.iterator();
            while (it.hasNext())
            {
                TreeEntry curr_entry = it.next();
                if (!(curr_entry.object instanceof Item) || curr_entry.object == this)
                {
                    continue;
                }
                Item curr_item = (Item) curr_entry.object;
                boolean is_obstacle = curr_item.is_obstacle(this);
                if (is_obstacle && this instanceof Trace && curr_item instanceof Trace)
                {
                    // Look, if both traces are connected to the same tie pin.
                    // In this case they are allowed to overlap without sharing a net.
                    Trace this_trace = (Trace) this;
                    Point contact_point = this_trace.first_corner();
                    boolean contact_found = false;
                    Collection<Item> curr_contacts = this_trace.get_normal_contacts(contact_point, true);
                    {
                        if (curr_contacts.contains(curr_item))
                        {
                            contact_found = true;
                        }
                    }
                    if (!contact_found)
                    {
                        contact_point = this_trace.last_corner();
                        curr_contacts = this_trace.get_normal_contacts(contact_point, true);
                        {
                            if (curr_contacts.contains(curr_item))
                            {
                                contact_found = true;
                            }
                        }
                    }
                    if (contact_found)
                    {
                        for (Item curr_contact : curr_contacts)
                        {
                            if (curr_contact instanceof Pin)
                            {
                                if (curr_contact.shares_net(this) && curr_contact.shares_net(curr_item))
                                {
                                    is_obstacle = false;
                                    break;
                                }
                            }
                        }
                    }
                }

                if (is_obstacle)
                {
                    TileShape shape_1 = curr_tile_shape;
                    TileShape shape_2 = curr_item.get_tree_shape(default_tree, curr_entry.shape_index_in_object);
                    if (shape_1 == null || shape_2 == null)
                    {
                        System.out.println("Item.clearance_violations: unexpected  null shape");
                        continue;
                    }
                    if (!this.board.search_tree_manager.is_clearance_compensation_used())
                    {
                        double cl_offset = 0.5 *
                                board.rules.clearance_matrix.value(curr_item.clearance_class, this.clearance_class, shape_layer(i));
                        shape_1 = (TileShape) shape_1.enlarge(cl_offset);
                        shape_2 = (TileShape) shape_2.enlarge(cl_offset);
                    }

                    TileShape intersection = shape_1.intersection(shape_2);
                    if (intersection.dimension() == 2)
                    {
                        ClearanceViolation curr_violation =
                                new ClearanceViolation(this, curr_item, intersection, shape_layer(i));
                        result.add(curr_violation);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Returns all connectable Items with a direct contacts to this item.
     * The result will be empty, if this item is not connectable.
     */
    public Set<Item> get_all_contacts()
    {
        Set<Item> result = new TreeSet<Item>();
        if (!(this instanceof Connectable))
        {
            return result;
        }
        for (int i = 0; i < this.tile_shape_count(); ++i)
        {
            Collection<SearchTreeObject> overlapping_items = board.overlapping_objects(get_tile_shape(i), shape_layer(i));
            Iterator<SearchTreeObject> it = overlapping_items.iterator();
            while (it.hasNext())
            {
                SearchTreeObject curr_ob = it.next();
                if (!(curr_ob instanceof Item))
                {
                    continue;
                }
                Item curr_item = (Item) curr_ob;
                if (curr_item != this && curr_item instanceof Connectable && curr_item.shares_net(this))
                {
                    result.add(curr_item);
                }
            }
        }
        return result;
    }

    /**
     * Returns all connectable Items with a direct contacts to this item on the input layer.
     * The result will be empty, if this item is not connectable.
     */
    public Set<Item> get_all_contacts(int p_layer)
    {
        Set<Item> result = new TreeSet<Item>();
        if (!(this instanceof Connectable))
        {
            return result;
        }
        for (int i = 0; i < this.tile_shape_count(); ++i)
        {
            if (this.shape_layer(i) != p_layer)
            {
                continue;
            }
            Collection<SearchTreeObject> overlapping_items = board.overlapping_objects(get_tile_shape(i), p_layer);
            Iterator<SearchTreeObject> it = overlapping_items.iterator();
            while (it.hasNext())
            {
                SearchTreeObject curr_ob = it.next();
                if (!(curr_ob instanceof Item))
                {
                    continue;
                }
                Item curr_item = (Item) curr_ob;
                if (curr_item != this && curr_item instanceof Connectable && curr_item.shares_net(this))
                {
                    result.add(curr_item);
                }
            }
        }
        return result;
    }

    /**
     * Checks, if this item is electrically connected to another connectable
     * item. Returns false for items, which are not connectable.
     */
    public boolean is_connected()
    {
        Collection<Item> contacts = this.get_all_contacts();
        return (contacts.size() > 0);
    }

    /**
     * Checks, if this item is electrically connected to another connectable
     * item on the input layer. Returns false for items, which are not connectable.
     */
    public boolean is_connected_on_layer(int p_layer)
    {
        Collection<Item> contacts_on_layer = this.get_all_contacts(p_layer);
        return (contacts_on_layer.size() > 0);
    }

    /**
     * default implementation to be overwritten in the Connectable subclasses
     */
    public Set<Item> get_normal_contacts()
    {
        return new TreeSet<Item>();
    }

    /**
     * Returns the contact point, if this item and p_other are Connectable
     * and have a unique normal contact.
     * Returns null otherwise
     */
    public Point normal_contact_point(Item p_other)
    {
        return null;
    }

    /**
     * auxiliary function
     */
    Point normal_contact_point(Trace p_other)
    {
        return null;
    }

    /**
     * auxiliary function
     */
    Point normal_contact_point(DrillItem p_other)
    {
        return null;
    }

    /**
     * Returns the set of all Connectable items of the net with number p_net_no which can be reached recursively
     * via normal contacts from this item.
     * If p_net_no <= 0, the net number is ignored.
     */
    public Set<Item> get_connected_set(int p_net_no)
    {
        return get_connected_set(p_net_no, false);
    }

    /**
     * Returns the set of all Connectable items of the net with number p_net_no which can be reached recursively
     * via normal contacts from this item.
     * If p_net_no <= 0, the net number is ignored.
     * If p_stop_at_plane, the recursive algorithm stops, when a conduction area is reached,
     * which does not belong to a component.
     */
    public Set<Item> get_connected_set(int p_net_no, boolean p_stop_at_plane)
    {
        Set<Item> result = new TreeSet<Item>();
        if (p_net_no > 0 && !this.contains_net(p_net_no))
        {
            return result;
        }
        result.add(this);
        get_connected_set_recu(result, p_net_no, p_stop_at_plane);
        return result;
    }

    /**
     * recursive part of get_connected_set
     */
    private void get_connected_set_recu(Set<Item> p_result, int p_net_no, boolean p_stop_at_plane)
    {
        Collection<Item> contact_list = get_normal_contacts();
        if (contact_list == null)
        {
            return;
        }
        for (Item curr_contact : contact_list)
        {
            if (p_stop_at_plane && curr_contact instanceof ConductionArea && curr_contact.get_component_no() <= 0)
            {
                continue;
            }
            if (p_net_no > 0 && !curr_contact.contains_net(p_net_no))
            {
                continue;
            }
            if (p_result.add(curr_contact))
            {
                curr_contact.get_connected_set_recu(p_result, p_net_no, p_stop_at_plane);
            }
        }
    }

    /**
     * Returns true, if this item contains some overlap to be cleaned.
     */
    public boolean is_overlap()
    {
        return false;
    }

    /**
     * Recursive part of Trace.is_cycle.
     * If p_ignore_areas is true, cycles where conduction areas are involved are ignored.
     */
    boolean is_cycle_recu(Set<Item> p_visited_items, Item p_search_item, Item p_come_from_item,
            boolean p_ignore_areas)
    {
        if (p_ignore_areas && this instanceof ConductionArea)
        {
            return false;
        }
        Collection<Item> contact_list = get_normal_contacts();
        if (contact_list == null)
        {
            return false;
        }
        Iterator<Item> it = contact_list.iterator();
        while (it.hasNext())
        {
            Item curr_contact = it.next();
            if (curr_contact == p_come_from_item)
            {
                continue;
            }
            if (curr_contact == p_search_item)
            {
                return true;
            }
            if (p_visited_items.add(curr_contact))
            {
                if (curr_contact.is_cycle_recu(p_visited_items, p_search_item, this, p_ignore_areas))
                {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the set of all Connectable items belonging to the net with number p_net_no,
     * which are not in the connected set of this item.
     * If p_net_no <= 0, the net numbers contained in this items are used  instead of p_net_no.
     */
    public Set<Item> get_unconnected_set(int p_net_no)
    {
        Set<Item> result = new TreeSet<Item>();
        if (p_net_no > 0 && !this.contains_net(p_net_no))
        {
            return result;
        }
        if (p_net_no > 0)
        {
            result.addAll(board.get_connectable_items(p_net_no));
        }
        else
        {
            for (int curr_net_no : this.net_no_arr)
            {
                result.addAll(board.get_connectable_items(curr_net_no));
            }
        }
        result.removeAll(this.get_connected_set(p_net_no));
        return result;
    }

    /**
     * Returns all traces and vias from this item until the next fork or terminal item. 
     */
    public Set<Item> get_connection_items()
    {
        return get_connection_items(StopConnectionOption.NONE);
    }

    /**
     * Returns all traces and vias from this item until the next fork or terminal item. 
     * If p_stop_option == StopConnectionOption.FANOUT_VIA, the algorithm will stop at the next fanout via,
     * If p_stop_option == StopConnectionOption.VIA, the algorithm will stop at any via.
     */
    public Set<Item> get_connection_items(StopConnectionOption p_stop_option)
    {
        Set<Item> contacts = this.get_normal_contacts();
        Set<Item> result = new TreeSet<Item>();
        if (this.is_route())
        {
            result.add(this);
        }
        Iterator<Item> it = contacts.iterator();
        while (it.hasNext())
        {
            Item curr_item = it.next();
            Point prev_contact_point = this.normal_contact_point(curr_item);
            if (prev_contact_point == null)
            {
                // no unique contact point
                continue;
            }
            int prev_contact_layer = this.first_common_layer(curr_item);
            if (this instanceof Trace)
            {
                // Check, that there is only 1 contact at this location.
                // Only for pins and vias items of more than 1 connection
                // are collected
                Trace start_trace = (Trace) this;
                Collection<Item> check_contacts = start_trace.get_normal_contacts(prev_contact_point, false);
                if (check_contacts.size() != 1)
                {
                    continue;
                }
            }
            // Search from curr_item along the contacts
            // until the next fork or nonroute item.
            for (;;)
            {
                if (!curr_item.is_route())
                {
                    // connection ends
                    break;
                }
                if (curr_item instanceof Via)
                {
                    if (p_stop_option == StopConnectionOption.VIA)
                    {
                        break;
                    }
                    if (p_stop_option == StopConnectionOption.FANOUT_VIA)
                    {
                        if (curr_item.is_fanout_via(result))
                        {
                            break;
                        }
                    }
                }
                result.add(curr_item);
                Collection<Item> curr_ob_contacts = curr_item.get_normal_contacts();
                // filter the contacts at the previous contact point,
                // because we were already there.
                // If then there is not exactly 1 new contact left, there is
                // a stub or a fork.
                Point next_contact_point = null;
                int next_contact_layer = -1;
                Item next_contact = null;
                boolean fork_found = false;
                Iterator<Item> curr_it = curr_ob_contacts.iterator();
                while (curr_it.hasNext())
                {
                    Item tmp_contact = curr_it.next();
                    int tmp_contact_layer = curr_item.first_common_layer(tmp_contact);
                    if (tmp_contact_layer >= 0)
                    {
                        Point tmp_contact_point = curr_item.normal_contact_point(tmp_contact);
                        if (tmp_contact_point == null)
                        {
                            // no unique contact point
                            fork_found = true;
                            break;
                        }
                        if (prev_contact_layer != tmp_contact_layer ||
                                !prev_contact_point.equals(tmp_contact_point))
                        {
                            if (next_contact != null)
                            {
                                // second new contact found
                                fork_found = true;
                                break;
                            }
                            next_contact = tmp_contact;
                            next_contact_point = tmp_contact_point;
                            next_contact_layer = tmp_contact_layer;
                        }
                    }
                }
                if (next_contact == null || fork_found)
                {
                    break;
                }
                curr_item = next_contact;
                prev_contact_point = next_contact_point;
                prev_contact_layer = next_contact_layer;
            }
        }
        return result;
    }

    /**
     * Function o be overwritten by classes Trace ans Via
     */
    public boolean is_tail()
    {
        return false;
    }

    /**
     * Returns all corners of this item, which are used for displaying the ratsnest.
     * To be overwritten in derived classes implementing the Connectable interface.
     */
    public Point[] get_ratsnest_corners()
    {
        return new Point[0];
    }

    public void draw(Graphics p_g, GraphicsContext p_graphics_context, Color p_color, double p_intensity)
    {
        Color[] color_arr = new Color[board.get_layer_count()];
        for (int i = 0; i < color_arr.length; ++i)
        {
            color_arr[i] = p_color;
        }
        draw(p_g, p_graphics_context, color_arr, p_intensity);
    }

    /**
     * Draws this item whith its draw colors from p_graphics_context.
     * p_layer_visibility[i] is expected between 0 and 1 for each layer i.
     */
    public void draw(Graphics p_g, GraphicsContext p_graphics_context)
    {
        Color[] layer_colors = get_draw_colors(p_graphics_context);
        draw(p_g, p_graphics_context, layer_colors, get_draw_intensity(p_graphics_context));
    }

    /**
     * Test function checking the item for inconsitencies.
     */
    public boolean validate()
    {
        boolean result = true;
        if (!board.search_tree_manager.validate_entries(this))
        {
            result = false;
        }
        for (int i = 0; i < this.tile_shape_count(); ++i)
        {
            TileShape curr_shape = this.get_tile_shape(i);
            if (curr_shape.is_empty())
            {
                System.out.println("Item.validate: shape is empty");
                result = false;
            }
        }
        return result;
    }

    /**
     * Returns for this item the layer of the shape with index p_index.
     * If p_id_no <= 0, it w2ill be generated internally.
     */
    public abstract int shape_layer(int p_index);

    Item(int[] p_net_no_arr, int p_clearance_type, int p_id_no,
            int p_component_no, FixedState p_fixed_state, BasicBoard p_board)
    {
        if (p_net_no_arr == null)
        {
            net_no_arr = new int[0];
        }
        else
        {
            net_no_arr = new int[p_net_no_arr.length];
            System.arraycopy(p_net_no_arr, 0, net_no_arr, 0, p_net_no_arr.length);
        }
        clearance_class = p_clearance_type;
        component_no = p_component_no;
        fixed_state = p_fixed_state;
        board = p_board;
        if (p_id_no <= 0)
        {
            id_no = board.communication.id_no_generator.new_no();
        }
        else
        {
            id_no = p_id_no;
        }
    }

    /**
     * Returns true, if it is not allowed to change this item except evtl. shoving the item
     */
    public boolean is_user_fixed()
    {
        return (fixed_state.ordinal() >= FixedState.USER_FIXED.ordinal());
    }

    /**
     * Returns true, if it is not allowed to delete this item.
     */
    boolean is_delete_fixed()
    {
        // Items belonging to a component are delete_fixed.
        if (this.component_no > 0 || is_user_fixed())
        {
            return true;
        }
        // Also power planes are delete_fixed.
        if (this instanceof ConductionArea)
        {
            if (!this.board.layer_structure.arr[((ConductionArea) this).get_layer()].is_signal)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true, if it is not allowed to change the location of this item by the push algorithm.
     */
    public boolean is_shove_fixed()
    {
        return (this.fixed_state.ordinal() >= FixedState.SHOVE_FIXED.ordinal());
    }

    /**
     * Returns the fixed state of this Item.
     */
    public FixedState get_fixed_state()
    {
        return this.fixed_state;
    }

    /**
     * Returns false, if this item is an obstacle for vias with the input net number.
     */
    public boolean is_drillable(int p_net_no)
    {
        return false;
    }

    /**
     * Fixes the item.
     */
    public void set_fixed_state(FixedState p_fixed_state)
    {
        fixed_state = p_fixed_state;
    }

    /**
     * Unfixes the item, if it is not fixed by the system.
     */
    public void unfix()
    {
        if (fixed_state != FixedState.SYSTEM_FIXED)
        {
            fixed_state = FixedState.UNFIXED;
        }

    }

    /**
     * returns true, if this item is an unfixed trace or via
     */
    public boolean is_route()
    {
        return false;
    }

    /**
     * Returns, if this item can be routed to.
     */
    public boolean is_connectable()
    {
        return ((this instanceof Connectable) && this.net_count() > 0);
    }

    /**
     * Returns the count of nets this item belongs to.
     */
    public int net_count()
    {
        return net_no_arr.length;
    }

    /**
     * gets the p_no-th net number of this item for 0 <= p_no < this.net_count().
     */
    public int get_net_no(int p_no)
    {
        return net_no_arr[p_no];
    }

    /**
     * Return the component number of this item or 0, if it does not belong to a component.
     */
    public int get_component_no()
    {
        return component_no;
    }

    /**
     * Removes p_net_no from the net number array.
     * Returns false, if p_net_no was not contained in this array.
     */
    public boolean remove_from_net(int p_net_no)
    {
        int found_index = -1;
        for (int i = 0; i < this.net_no_arr.length; ++i)
        {
            if (this.net_no_arr[i] == p_net_no)
            {
                found_index = i;
            }
        }
        if (found_index < 0)
        {
            return false;
        }
        int[] new_net_no_arr = new int[this.net_no_arr.length - 1];
        for (int i = 0; i < found_index; ++i)
        {
            new_net_no_arr[i] = this.net_no_arr[i];
        }
        for (int i = found_index; i < new_net_no_arr.length; ++i)
        {
            new_net_no_arr[i] = this.net_no_arr[i + 1];
        }
        this.net_no_arr = new_net_no_arr;
        return true;
    }

    /**
     * Returns the index in the clearance matrix describing the required spacing
     * of this item to other items
     */
    public int clearance_class_no()
    {
        return clearance_class;
    }

    /**
     * Sets  the index in the clearance matrix describing the required spacing
     * of this item to other items.
     */
    public void set_clearance_class_no(int p_index)
    {
        if (p_index < 0 || p_index >= this.board.rules.clearance_matrix.get_class_count())
        {
            System.out.println("Item.set_clearance_class_no: p_index out of range");
            return;
        }
        clearance_class = p_index;
    }

    /**
     * Changes the clearance class of this item and updates the search tree.
     */
    public void change_clearance_class(int p_index)
    {
        if (p_index < 0 || p_index >= this.board.rules.clearance_matrix.get_class_count())
        {
            System.out.println("Item.set_clearance_class_no: p_index out of range");
            return;
        }
        clearance_class = p_index;
        this.clear_derived_data();
        if (this.board != null && this.board.search_tree_manager.is_clearance_compensation_used())
        {
            // reinsert the item into the search tree, because the compensated shape has changed.
            this.board.search_tree_manager.remove(this);
            this.board.search_tree_manager.insert(this);
        }
    }

    /**
     * Assigns this item to the component with the input component number.
     */
    public void assign_component_no(int p_no)
    {
        component_no = p_no;
    }

    /**
     * Makes this item connectable and assigns it to the input net.
     * If p_net_no < 0, the net items net number will be removed and the item will no longer be connectable.
     */
    public void assign_net_no(int p_net_no)
    {
        if (!Nets.is_normal_net_no(p_net_no))
        {
            return;
        }
        if (p_net_no > board.rules.nets.max_net_no())
        {
            System.out.println("Item.assign_net_no: p_net_no to big");
            return;
        }
        board.item_list.save_for_undo(this);
        if (p_net_no <= 0)
        {
            net_no_arr = new int[0];
        }
        else
        {
            if (net_no_arr.length == 0)
            {
                net_no_arr = new int[1];
            }
            else if (net_no_arr.length > 1)
            {
                System.out.println("Item.assign_net_no: unexpected net_count > 1");
            }
            net_no_arr[0] = p_net_no;
        }
    }

    /**
     * Returns true, if p_item is contained in the input filter.
     */
    public abstract boolean is_selected_by_filter(ItemSelectionFilter p_filter);

    /**
     * Internally used for implementing the function is_selectrd_by_filter
     */
    protected boolean is_selected_by_fixed_filter(ItemSelectionFilter p_filter)
    {
        boolean result;
        if (this.is_user_fixed())
        {
            result = p_filter.is_selected(ItemSelectionFilter.SelectableChoices.FIXED);
        }
        else
        {
            result = p_filter.is_selected(ItemSelectionFilter.SelectableChoices.UNFIXED);
        }
        return result;
    }

    /**
     * Sets the item tree entries for the  tree with identification number p_tree_no.
     */
    public void set_search_tree_entries(ShapeTree.Leaf[] p_tree_entries, ShapeTree p_tree)
    {
        if (this.board == null)
        {
            return;
        }
        if (this.search_trees_info == null)
        {
            this.search_trees_info = new ItemSearchTreesInfo();
        }
        this.search_trees_info.set_tree_entries(p_tree_entries, p_tree);
    }

    /**
     * Returns the tree entries for the tree with identification number p_tree_no,
     * or null, if for this tree no entries of this item are inserted.
     */
    public ShapeTree.Leaf[] get_search_tree_entries(ShapeSearchTree p_tree)
    {
        if (this.search_trees_info == null)
        {
            return null;
        }
        return this.search_trees_info.get_tree_entries(p_tree);
    }

    /**
     * Sets the precalculated tree shapes tree entries for the  tree with identification number p_tree_no.
     */
    protected void set_precalculated_tree_shapes(TileShape[] p_shapes, ShapeSearchTree p_tree)
    {
        if (this.board == null)
        {
            return;
        }
        if (this.search_trees_info == null)
        {
            System.out.println("Item.set_precalculated_tree_shapes search_trees_info not allocated");
            return;
        }
        this.search_trees_info.set_precalculated_tree_shapes(p_shapes, p_tree);
    }

    /**
     * Sets the searh tree entries of this item to null.
     */
    public void clear_search_tree_entries()
    {
        this.search_trees_info = null;
    }

    /**
     * Gets the information for the autoroute algorithm.
     * Creates it, if it does not yet exist.
     */
    public autoroute.ItemAutorouteInfo get_autoroute_info()
    {
        if (autoroute_info == null)
        {
            autoroute_info = new autoroute.ItemAutorouteInfo(this);
        }
        return autoroute_info;
    }

    /**
     * Gets the information for the autoroute algorithm.
     */
    public autoroute.ItemAutorouteInfo get_autoroute_info_pur()
    {
        return autoroute_info;
    }

    /**
     * Clears the data allocated for the autoroute algorithm.
     */
    public void clear_autoroute_info()
    {
        autoroute_info = null;
    }

    /**
     * Clear all cached or derived data. so that they have to be recalculated,
     * when they are used next time.
     */
    public void clear_derived_data()
    {
        if (this.search_trees_info != null)
        {
            this.search_trees_info.clear_precalculated_tree_shapes();
        }
        autoroute_info = null;
    }

    /**
     * Internal funktion used in the implementation of print_info
     */
    protected void print_net_info(ObjectInfoPanel p_window, java.util.Locale p_locale)
    {
        java.util.ResourceBundle resources =
                java.util.ResourceBundle.getBundle("board.resources.ObjectInfoPanel", p_locale);
        for (int i = 0; i < this.net_count(); ++i)
        {
            p_window.append(", " + resources.getString("net") + " ");
            rules.Net curr_net = board.rules.nets.get(this.get_net_no(i));
            p_window.append(curr_net.name, resources.getString("net_info"), curr_net);
        }
    }

    /**
     * Internal funktion used in the implementation of print_info
     */
    protected void print_clearance_info(ObjectInfoPanel p_window, java.util.Locale p_locale)
    {
        if (this.clearance_class > 0)
        {
            java.util.ResourceBundle resources =
                    java.util.ResourceBundle.getBundle("board.resources.ObjectInfoPanel", p_locale);
            p_window.append(", " + resources.getString("clearance_class") + " ");
            String name = board.rules.clearance_matrix.get_name(this.clearance_class);
            p_window.append(name, resources.getString("clearance_info"), board.rules.clearance_matrix.get_row(this.clearance_class));
        }
    }

    /**
     * Internal funktion used in the implementation of print_info
     */
    protected void print_fixed_info(ObjectInfoPanel p_window, java.util.Locale p_locale)
    {
        if (this.fixed_state != FixedState.UNFIXED)
        {
            java.util.ResourceBundle resources =
                    java.util.ResourceBundle.getBundle("board.resources.FixedState", p_locale);
            p_window.append(", ");
            p_window.append(resources.getString(this.fixed_state.toString()));
        }
    }

    /**
     * Internal funktion used in the implementation of print_info
     */
    protected void print_contact_info(ObjectInfoPanel p_window, java.util.Locale p_locale)
    {
        Collection<Item> contacts = this.get_normal_contacts();
        if (!contacts.isEmpty())
        {
            java.util.ResourceBundle resources =
                    java.util.ResourceBundle.getBundle("board.resources.ObjectInfoPanel", p_locale);
            p_window.append(", " + resources.getString("contacts") + " ");
            Integer contact_count = contacts.size();
            p_window.append_items(contact_count.toString(), resources.getString("contact_info"), contacts);
        }
    }

    /**
     * Internal funktion used in the implementation of print_info
     */
    protected void print_clearance_violation_info(ObjectInfoPanel p_window, java.util.Locale p_locale)
    {
        Collection<ClearanceViolation> clearance_violations = this.clearance_violations();
        if (!clearance_violations.isEmpty())
        {
            java.util.ResourceBundle resources =
                    java.util.ResourceBundle.getBundle("board.resources.ObjectInfoPanel", p_locale);
            p_window.append(", ");
            Integer violation_count = clearance_violations.size();
            Collection<ObjectInfoPanel.Printable> violations = new java.util.LinkedList<ObjectInfoPanel.Printable>();
            violations.addAll(clearance_violations);
            p_window.append_objects(violation_count.toString(), resources.getString("violation_info"), violations);
            if (violation_count == 1)
            {
                p_window.append(" " + resources.getString("clearance_violation"));
            }
            else
            {
                p_window.append(" " + resources.getString("clearance_violations"));
            }
        }
    }

    /**
     * Internal funktion used in the implementation of print_info
     */
    protected void print_connectable_item_info(ObjectInfoPanel p_window, java.util.Locale p_locale)
    {
        this.print_clearance_info(p_window, p_locale);
        this.print_fixed_info(p_window, p_locale);
        this.print_net_info(p_window, p_locale);
        this.print_contact_info(p_window, p_locale);
        this.print_clearance_violation_info(p_window, p_locale);
    }

    /**
     * Internal funktion used in the implementation of print_info
     */
    protected void print_item_info(ObjectInfoPanel p_window, java.util.Locale p_locale)
    {
        this.print_clearance_info(p_window, p_locale);
        this.print_fixed_info(p_window, p_locale);
        this.print_clearance_violation_info(p_window, p_locale);
    }

    /**
     * Checks, if all nets of this items are normal.
     */
    public boolean nets_normal()
    {
        for (int i = 0; i < this.net_no_arr.length; ++i)
        {
            if (!Nets.is_normal_net_no(this.net_no_arr[i]))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks, if this item and p_other contain exactly the same net numbers.
     */
    public boolean nets_equal(Item p_other)
    {
        return nets_equal(p_other.net_no_arr);
    }

    /**
     * Checks, if this item contains exacly the nets in p_net_no_arr
     */
    public boolean nets_equal(int[] p_net_no_arr)
    {
        if (this.net_no_arr.length != p_net_no_arr.length)
        {
            return false;
        }
        for (int curr_net_no : p_net_no_arr)
        {
            if (!this.contains_net(curr_net_no))
            {
                return false;
            }
        }
        return true;
    }

    /**
     *  Returns true, if the via is directly ob by a trace connected to a nearby SMD-pin.
     * If p_ignore_items != null, contact traces in P-ignore_items are ignored.
     */
    boolean is_fanout_via(Set<Item> p_ignore_items)
    {
        Collection<Item> contact_list = this.get_normal_contacts();
        for (Item curr_contact : contact_list)
        {
            if (curr_contact instanceof Pin && curr_contact.first_layer() == curr_contact.last_layer() && curr_contact.get_normal_contacts().size() <= 1)
            {
                return true;
            }
            if (curr_contact instanceof Trace)
            {
                if (p_ignore_items != null && p_ignore_items.contains(curr_contact))
                {
                    continue;
                }
                Trace curr_trace = (Trace) curr_contact;
                if (curr_trace.get_length() >= PROTECT_FANOUT_LENGTH * curr_trace.get_half_width())
                {
                    continue;
                }
                Collection<Item> trace_contact_list = curr_trace.get_normal_contacts();
                for (Item tmp_contact : trace_contact_list)
                {
                    if (tmp_contact instanceof Pin && curr_contact.first_layer() == curr_contact.last_layer() && tmp_contact.get_normal_contacts().size() <= 1)
                    {
                        return true;
                    }
                    if (tmp_contact instanceof PolylineTrace && tmp_contact.get_fixed_state() == FixedState.SHOVE_FIXED)
                    {
                        // look for shove fixed exit traces of SMD-pins
                        PolylineTrace contact_trace = (PolylineTrace) tmp_contact;
                        if (contact_trace.corner_count() == 2)
                        {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
    /**
     * the index in the clearance matrix describing the required spacing
     * to other items
     */
    private int clearance_class;
    /** The board this Itewm is on */
    transient public BasicBoard board;
    /** The nets, to which this item belongs */
    int[] net_no_arr;
    /** points to the entries of this item in the ShapeSearchTrees */
    transient private ItemSearchTreesInfo search_trees_info = null;
    private FixedState fixed_state;
    /** not 0, if this item belongs to a component */
    private int component_no = 0;
    private final int id_no;
    /**
     * Folse, if the item is deleted or not inserted into the board
     */
    private boolean on_the_board = false;
    /** Temporary data used in the autoroute algorithm. */
    transient private autoroute.ItemAutorouteInfo autoroute_info = null;
    private static double PROTECT_FANOUT_LENGTH = 400;

    /**
     *  Used as parameter of get_connection to control, that the connection
     *  stops at the next fanout via or at any via.
     */
    public enum StopConnectionOption
    {

        NONE, FANOUT_VIA, VIA
    }
}
