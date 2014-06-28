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

import geometry.planar.Area;
import geometry.planar.ConvexShape;
import geometry.planar.IntBox;
import geometry.planar.IntOctagon;
import geometry.planar.Point;
import geometry.planar.Vector;
import geometry.planar.Polyline;
import geometry.planar.PolylineShape;
import geometry.planar.TileShape;

import java.awt.Graphics;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import datastructures.ShapeTree.TreeEntry;

import library.BoardLibrary;
import library.Padstack;
import rules.BoardRules;
import boardgraphics.GraphicsContext;
import boardgraphics.Drawable;
import datastructures.UndoableObjects;

/**
 *
 * Provides basic functionality of a board with geometric items.
 * Contains functions such as inserting, deleting, modifying
 * and picking items and elementary checking functions.
 * A board may have 1 or several layers.
 *
 * @author Alfons Wirtz
 */
public class BasicBoard implements java.io.Serializable
{

    /**
     * Creates a new instance of a routing Board with surrrounding box
     * p_bounding_box
     * Rules contains the restrictions to obey when inserting items.
     * Among other things it may contain a clearance matrix.
     * p_observers is used for syncronisation, if the board is generated
     * by a host database. Otherwise it is null.
     * If p_test_level  != RELEASE_VERSION,, some features may be used, which are still in experimental state.
     * Also warnings  for debugging may be printed depending on the size of p_test_level.
     */
    public BasicBoard(IntBox p_bounding_box, LayerStructure p_layer_structure, PolylineShape[] p_outline_shapes,
                      int p_outline_cl_class_no, BoardRules p_rules, Communication p_communication, TestLevel p_test_level)
    {
        layer_structure = p_layer_structure;
        rules = p_rules;
        library = new BoardLibrary();
        item_list = new UndoableObjects();
        components = new Components();
        communication = p_communication;
        bounding_box = p_bounding_box;
        this.test_level = p_test_level;
        search_tree_manager = new SearchTreeManager(this);
        p_rules.nets.set_board(this);
        insert_outline(p_outline_shapes, p_outline_cl_class_no);
    }

    /**
     * Inserts a trace into the board, whose geometry is described by
     * a Polyline. p_clearance_class is the index in the clearance_matix,
     * which describes the required clearance restrictions to other items.
     * Because no internal cleaning of items is done, the new inserted
     * item can be returned.
     */
    public PolylineTrace insert_trace_without_cleaning(Polyline p_polyline, int p_layer,
                                                       int p_half_width, int[] p_net_no_arr, int p_clearance_class, FixedState p_fixed_state)
    {
        if (p_polyline.corner_count() < 2)
        {
            return null;
        }
        PolylineTrace new_trace = new PolylineTrace(p_polyline, p_layer, p_half_width, p_net_no_arr,
                p_clearance_class, 0, 0, p_fixed_state, this);
        if (new_trace.first_corner().equals(new_trace.last_corner()))
        {
            if (p_fixed_state.ordinal() < FixedState.USER_FIXED.ordinal())
            {
                return null;
            }
        }
        insert_item(new_trace);
        if (new_trace.nets_normal())
        {
            max_trace_half_width = Math.max(max_trace_half_width, p_half_width);
            min_trace_half_width = Math.min(min_trace_half_width, p_half_width);
        }
        return new_trace;
    }

    /**
     * Inserts a trace into the board, whose geometry is described by
     * a Polyline. p_clearance_class is the index in the clearance_matix,
     * which describes the required clearance restrictions to other items.
     */
    public void insert_trace(Polyline p_polyline, int p_layer,
                             int p_half_width, int[] p_net_no_arr, int p_clearance_class, FixedState p_fixed_state)
    {
        PolylineTrace new_trace =
                insert_trace_without_cleaning(p_polyline, p_layer, p_half_width,
                p_net_no_arr, p_clearance_class, p_fixed_state);
        if (new_trace == null)
        {
            return;
        }
        IntOctagon clip_shape = null;
        if (this instanceof RoutingBoard)
        {
            ChangedArea changed_area = ((RoutingBoard) this).changed_area;
            if (changed_area != null)
            {
                clip_shape = changed_area.get_area(p_layer);
            }
        }
        new_trace.normalize(clip_shape);
    }

    /**
     * Inserts a trace into the board, whose geometry is described by
     * an array of points, and cleans up the net.
     */
    public void insert_trace(Point[] p_points, int p_layer,
                             int p_half_width, int[] p_net_no_arr, int p_clearance_class, FixedState p_fixed_state)
    {
        for (int i = 0; i < p_points.length; ++i)
        {
            if (!this.bounding_box.contains(p_points[i]))
            {
                System.out.println("LayeredBoard.insert_trace: input point out of range");
            }
        }
        Polyline poly = new Polyline(p_points);
        insert_trace(poly, p_layer, p_half_width, p_net_no_arr, p_clearance_class, p_fixed_state);
    }

    /**
     * Inserts a via into the board. p_attach_allowed indicates, if the via may overlap with smd pins
     * of the same net.
     */
    public Via insert_via(Padstack p_padstack, Point p_center, int[] p_net_no_arr, int p_clearance_class,
                          FixedState p_fixed_state, boolean p_attach_allowed)
    {
        Via new_via = new Via(p_padstack, p_center, p_net_no_arr, p_clearance_class, 0, 0, p_fixed_state,
                p_attach_allowed, this);
        insert_item(new_via);
        int from_layer = p_padstack.from_layer();
        int to_layer = p_padstack.to_layer();
        for (int i = from_layer; i < to_layer; ++i)
        {
            for (int curr_net_no : p_net_no_arr)
            {
                split_traces(p_center, i, curr_net_no);
            }
        }
        return new_via;
    }

    /**
     * Inserts a pin into the board.
     *  p_pin_no is the number of this pin in the library package of its component (starting with 0).
     */
    public Pin insert_pin(int p_component_no, int p_pin_no, int[] p_net_no_arr, int p_clearance_class, FixedState p_fixed_state)
    {
        Pin new_pin = new Pin(p_component_no, p_pin_no, p_net_no_arr, p_clearance_class, 0, p_fixed_state, this);
        insert_item(new_pin);
        return new_pin;
    }

    /**
     * Inserts an obstacle into the board , whose geometry is described
     * by a polygonyal shape, which may have holes.
     * If p_component_no != 0, the obstacle belongs to a component.
     */
    public ObstacleArea insert_obstacle(Area p_area, int p_layer, int p_clearance_class, FixedState p_fixed_state)
    {
        if (p_area == null)
        {
            System.out.println("BasicBoard.insert_obstacle: p_area is null");
            return null;
        }
        ObstacleArea obs = new ObstacleArea(p_area, p_layer, Vector.ZERO, 0, false, p_clearance_class, 0, 0, null, p_fixed_state, this);
        insert_item(obs);
        return obs;
    }

    /**
     * Inserts an obstacle belonging to a component into the board
     * p_name is to identify the corresponding ObstacstacleArea in the component package.
     */
    public ObstacleArea insert_obstacle(Area p_area, int p_layer, Vector p_translation, double p_rotation_in_degree,
                                        boolean p_side_changed, int p_clearance_class, int p_component_no, String p_name, FixedState p_fixed_state)
    {
        if (p_area == null)
        {
            System.out.println("BasicBoard.insert_obstacle: p_area is null");
            return null;
        }
        ObstacleArea obs = new ObstacleArea(p_area, p_layer, p_translation, p_rotation_in_degree, p_side_changed,
                p_clearance_class, 0, p_component_no, p_name, p_fixed_state, this);
        insert_item(obs);
        return obs;
    }

    /**
     * Inserts an via obstacle area into the board , whose geometry is described
     * by a polygonyal shape, which may have holes.
     */
    public ViaObstacleArea insert_via_obstacle(Area p_area, int p_layer, int p_clearance_class,
                                               FixedState p_fixed_state)
    {
        if (p_area == null)
        {
            System.out.println("BasicBoard.insert_via_obstacle: p_area is null");
            return null;
        }
        ViaObstacleArea obs = new ViaObstacleArea(p_area, p_layer, Vector.ZERO, 0, false,
                p_clearance_class, 0, 0, null, p_fixed_state, this);
        insert_item(obs);
        return obs;
    }

    /**
     * Inserts an via obstacle belonging to a component into the board
     * p_name is to identify the corresponding ObstacstacleArea in the component package.
     */
    public ViaObstacleArea insert_via_obstacle(Area p_area, int p_layer, Vector p_translation, double p_rotation_in_degree,
                                               boolean p_side_changed, int p_clearance_class, int p_component_no, String p_name,
                                               FixedState p_fixed_state)
    {
        if (p_area == null)
        {
            System.out.println("BasicBoard.insert_via_obstacle: p_area is null");
            return null;
        }
        ViaObstacleArea obs = new ViaObstacleArea(p_area, p_layer, p_translation, p_rotation_in_degree, p_side_changed,
                p_clearance_class, 0, p_component_no, p_name, p_fixed_state, this);
        insert_item(obs);
        return obs;
    }

    /**
     * Inserts a component obstacle area into the board , whose geometry is described
     * by a polygonyal shape, which may have holes.
     */
    public ComponentObstacleArea insert_component_obstacle(Area p_area, int p_layer,
                                                           int p_clearance_class, FixedState p_fixed_state)
    {
        if (p_area == null)
        {
            System.out.println("BasicBoard.insert_component_obstacle: p_area is null");
            return null;
        }
        ComponentObstacleArea obs = new ComponentObstacleArea(p_area, p_layer, Vector.ZERO, 0, false,
                p_clearance_class, 0, 0, null, p_fixed_state, this);
        insert_item(obs);
        return obs;
    }

    /**
     * Inserts a component obstacle belonging to a component into the board.
     * p_name is to identify the corresponding ObstacstacleArea in the component package.
     */
    public ComponentObstacleArea insert_component_obstacle(Area p_area, int p_layer, Vector p_translation, double p_rotation_in_degree,
                                                           boolean p_side_changed, int p_clearance_class, int p_component_no, String p_name, FixedState p_fixed_state)
    {
        if (p_area == null)
        {
            System.out.println("BasicBoard.insert_component_obstacle: p_area is null");
            return null;
        }
        ComponentObstacleArea obs = new ComponentObstacleArea(p_area, p_layer, p_translation, p_rotation_in_degree, p_side_changed,
                p_clearance_class, 0, p_component_no, p_name, p_fixed_state, this);
        insert_item(obs);
        return obs;
    }

    /**
     * Inserts a component ouline into the board.
     */
    public ComponentOutline insert_component_outline(Area p_area, boolean p_is_front, Vector p_translation, double p_rotation_in_degree,
                                                     int p_component_no, FixedState p_fixed_state)
    {
        if (p_area == null)
        {
            System.out.println("BasicBoard.insert_component_outline: p_area is null");
            return null;
        }
        if (!p_area.is_bounded())
        {
            System.out.println("BasicBoard.insert_component_outline: p_area is not bounded");
            return null;
        }
        ComponentOutline outline = new ComponentOutline(p_area, p_is_front, p_translation, p_rotation_in_degree,
                p_component_no, p_fixed_state, this);
        insert_item(outline);
        return outline;
    }

    /**
     * Inserts a condution area into the board , whose geometry is described
     * by a polygonyal shape, which may have holes.
     * If p_is_obstacle is false, it is possible to route through the conduction area
     * with traces and vias of foreign nets.
     */
    public ConductionArea insert_conduction_area(Area p_area, int p_layer,
                                                 int[] p_net_no_arr, int p_clearance_class, boolean p_is_obstacle, FixedState p_fixed_state)
    {
        if (p_area == null)
        {
            System.out.println("BasicBoard.insert_conduction_area: p_area is null");
            return null;
        }
        ConductionArea c = new ConductionArea(p_area, p_layer, Vector.ZERO, 0, false, p_net_no_arr, p_clearance_class,
                0, 0, null, p_is_obstacle, p_fixed_state, this);
        insert_item(c);
        return c;
    }

    /**
     * Inserts an Outline into the board.
     */
    public BoardOutline insert_outline(PolylineShape[] p_outline_shapes, int p_clearance_class_no)
    {
        BoardOutline result = new BoardOutline(p_outline_shapes, p_clearance_class_no, 0, this);
        insert_item(result);
        return result;
    }

    /**
     * Returns the outline of the board.
     */
    public BoardOutline get_outline()
    {
        Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
        for (;;)
        {
            UndoableObjects.Storable curr_item = item_list.read_object(it);
            if (curr_item == null)
            {
                break;
            }
            if (curr_item instanceof BoardOutline)
            {
                return (BoardOutline) curr_item;
            }
        }
        return null;
    }

    /**
     * Removes an item from the board
     */
    public void remove_item(Item p_item)
    {
        if (p_item == null)
        {
            return;
        }
        additional_update_after_change(p_item); // must be called before p_item is deleted.
        search_tree_manager.remove(p_item);
        item_list.delete(p_item);

        // let the observers syncronize the deletion
        communication.observers.notify_deleted(p_item);
    }

    /**
     * looks, if an item with id_no p_id_no is on the board.
     * Returns the found item or null, if no such item is found.
     */
    public Item get_item(int p_id_no)
    {
        Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
        for (;;)
        {
            Item curr_item = (Item) item_list.read_object(it);
            if (curr_item == null)
            {
                break;
            }
            if (curr_item.get_id_no() == p_id_no)
            {
                return curr_item;
            }
        }
        return null;
    }

    /**
     * Returns the list of all items on the board
     */
    public Collection<Item> get_items()
    {
        Collection<Item> result = new LinkedList<Item>();
        Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
        for (;;)
        {
            Item curr_item = (Item) item_list.read_object(it);
            if (curr_item == null)
            {
                break;
            }
            result.add(curr_item);
        }
        return result;
    }

    /**
     * Returns all connectable items on the board containing p_net_no
     */
    public Collection<Item> get_connectable_items(int p_net_no)
    {
        Collection<Item> result = new LinkedList<Item>();
        Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
        for (;;)
        {
            Item curr_item = (Item) item_list.read_object(it);
            if (curr_item == null)
            {
                break;
            }
            if (curr_item instanceof Connectable && curr_item.contains_net(p_net_no))
            {
                result.add(curr_item);
            }
        }
        return result;
    }

    /**
     * Returns the count of connectable items of the net with number p_net_no
     */
    public int connectable_item_count(int p_net_no)
    {
        int result = 0;
        Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
        for (;;)
        {
            Item curr_item = (Item) item_list.read_object(it);
            if (curr_item == null)
            {
                break;
            }
            if (curr_item instanceof Connectable && curr_item.contains_net(p_net_no))
            {
                ++result;
            }
        }
        return result;
    }

    /**
     * Returns all items with the input component number
     */
    public Collection<Item> get_component_items(int p_component_no)
    {
        Collection<Item> result = new LinkedList<Item>();
        Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
        for (;;)
        {
            Item curr_item = (Item) item_list.read_object(it);
            if (curr_item == null)
            {
                break;
            }
            if (curr_item.get_component_no() == p_component_no)
            {
                result.add(curr_item);
            }
        }
        return result;
    }

    /**
     * Returns all pins with the input component number
     */
    public Collection<Pin> get_component_pins(int p_component_no)
    {
        Collection<Pin> result = new LinkedList<Pin>();
        Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
        for (;;)
        {
            Item curr_item = (Item) item_list.read_object(it);
            if (curr_item == null)
            {
                break;
            }
            if (curr_item.get_component_no() == p_component_no && curr_item instanceof Pin)
            {
                result.add((Pin) curr_item);
            }
        }
        return result;
    }

    /**
     * Returns the pin with the input component number and pin number, or null, if no such pinn exists.
     */
    public Pin get_pin(int p_component_no, int p_pin_no)
    {
        Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
        for (;;)
        {
            Item curr_item = (Item) item_list.read_object(it);
            if (curr_item == null)
            {
                break;
            }
            if (curr_item.get_component_no() == p_component_no && curr_item instanceof Pin)
            {
                Pin curr_pin = (Pin) curr_item;
                if (curr_pin.pin_no == p_pin_no)
                {
                    return curr_pin;
                }
            }
        }
        return null;
    }

    /**
     * Removes the items in p_item_list.
     * Returns false, if some items could not be removed, bcause they are fixed.
     */
    public boolean remove_items(Collection<Item> p_item_list, boolean p_with_delete_fixed)
    {
        boolean result = true;
        Iterator<Item> it = p_item_list.iterator();
        while (it.hasNext())
        {
            Item curr_item = it.next();
            if (!p_with_delete_fixed && curr_item.is_delete_fixed() || curr_item.is_user_fixed())
            {
                result = false;
            }
            else
            {
                remove_item(curr_item);
            }
        }
        return result;
    }

    /**
     * Returns the list of all conduction areas on the board
     */
    public Collection<ConductionArea> get_conduction_areas()
    {
        Collection<ConductionArea> result = new LinkedList<ConductionArea>();
        Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
        for (;;)
        {
            UndoableObjects.Storable curr_item = item_list.read_object(it);
            if (curr_item == null)
            {
                break;
            }
            if (curr_item instanceof ConductionArea)
            {
                result.add((ConductionArea) curr_item);
            }
        }
        return result;
    }

    /**
     * Returns the list of all pins on the board
     */
    public Collection<Pin> get_pins()
    {
        Collection<Pin> result = new LinkedList<Pin>();
        Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
        for (;;)
        {
            UndoableObjects.Storable curr_item = item_list.read_object(it);
            if (curr_item == null)
            {
                break;
            }
            if (curr_item instanceof Pin)
            {
                result.add((Pin) curr_item);
            }
        }
        return result;
    }

    /**
     * Returns the list of all pins on the board with only 1 layer
     */
    public Collection<Pin> get_smd_pins()
    {
        Collection<Pin> result = new LinkedList<Pin>();
        Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
        for (;;)
        {
            UndoableObjects.Storable curr_item = item_list.read_object(it);
            if (curr_item == null)
            {
                break;
            }
            if (curr_item instanceof Pin)
            {
                Pin curr_pin = (Pin) curr_item;
                if (curr_pin.first_layer() == curr_pin.last_layer())
                {
                    result.add(curr_pin);
                }
            }
        }
        return result;
    }

    /**
     * Returns the list of all vias on the board
     */
    public Collection<Via> get_vias()
    {
        Collection<Via> result = new LinkedList<Via>();
        Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
        for (;;)
        {
            UndoableObjects.Storable curr_item = item_list.read_object(it);
            if (curr_item == null)
            {
                break;
            }
            if (curr_item instanceof Via)
            {
                result.add((Via) curr_item);
            }
        }
        return result;
    }

    /**
     * Returns the list of all traces on the board
     */
    public Collection<Trace> get_traces()
    {
        Collection<Trace> result = new LinkedList<Trace>();
        Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
        for (;;)
        {
            UndoableObjects.Storable curr_item = item_list.read_object(it);
            if (curr_item == null)
            {
                break;
            }
            if (curr_item instanceof Trace)
            {
                result.add((Trace) curr_item);
            }
        }
        return result;
    }

    /**
     * Returns the cumulative length of all traces on the board
     */
    public double cumulative_trace_length()
    {
        double result = 0;
        Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
        for (;;)
        {
            UndoableObjects.Storable curr_item = item_list.read_object(it);
            if (curr_item == null)
            {
                break;
            }
            if (curr_item instanceof Trace)
            {
                result += ((Trace) curr_item).get_length();
            }
        }
        return result;
    }

    /**
     * Combines the connected traces of this net, which have only 1 contact
     * at the connection point.
     * if p_net_no < 0 traces of all nets are combined.
     */
    public boolean combine_traces(int p_net_no)
    {
        boolean result = false;
        boolean something_changed = true;
        while (something_changed)
        {
            something_changed = false;
            Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
            for (;;)
            {
                Item curr_item = (Item) item_list.read_object(it);
                if (curr_item == null)
                {
                    break;
                }
                if ((p_net_no < 0 || curr_item.contains_net(p_net_no)) && curr_item instanceof Trace && curr_item.is_on_the_board())
                {
                    if (((Trace) curr_item).combine())
                    {
                        something_changed = true;
                        result = true;
                        break;
                    }
                }
            }
        }
        return result;
    }

    /**
     * Normalizes the traces of this net
     */
    public boolean normalize_traces(int p_net_no)
    {
        boolean result = false;
        boolean something_changed = true;
        Item curr_item = null;
        while (something_changed)
        {
            something_changed = false;
            Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
            for (;;)
            {
                try
                {
                    curr_item = (Item) item_list.read_object(it);
                }
                catch (java.util.ConcurrentModificationException e)
                {
                    something_changed = true;
                    break;
                }
                if (curr_item == null)
                {
                    break;
                }
                if (curr_item.contains_net(p_net_no) && curr_item instanceof PolylineTrace && curr_item.is_on_the_board())
                {
                    PolylineTrace curr_trace = (PolylineTrace) curr_item;
                    if (curr_trace.normalize(null))
                    {
                        something_changed = true;
                        result = true;
                    }
                    else if (!curr_trace.is_user_fixed() && this.remove_if_cycle(curr_trace))
                    {
                        something_changed = true;
                        result = true;
                    }
                }
            }
        }
        return result;
    }

    /**
     * Looks for traces of the input net on the input layer, so that p_location is on the trace polygon,
     * and splits these traces. Returns false, if no trace was split.
     */
    public boolean split_traces(Point p_location, int p_layer, int p_net_no)
    {
        ItemSelectionFilter filter = new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.TRACES);
        Collection<Item> picked_items = this.pick_items(p_location, p_layer, filter);
        IntOctagon location_shape = TileShape.get_instance(p_location).bounding_octagon();
        boolean trace_split = false;
        for (Item curr_item : picked_items)
        {
            Trace curr_trace = (Trace) curr_item;
            if (curr_trace.contains_net(p_net_no))
            {
                Collection<PolylineTrace> split_pieces = curr_trace.split(location_shape);
                if (split_pieces.size() != 1)
                {
                    trace_split = true;
                }
            }
        }
        return trace_split;
    }

    /**
     * Returs a Collection of Collections of items forming a connected set.
     */
    public Collection<Collection<Item>> get_connected_sets(int p_net_no)
    {
        Collection<Collection<Item>> result = new LinkedList<Collection<Item>>();
        if (p_net_no <= 0)
        {
            return result;
        }
        SortedSet<Item> items_to_handle = new TreeSet<Item>();
        Iterator<UndoableObjects.UndoableObjectNode> it = this.item_list.start_read_object();
        for (;;)
        {
            Item curr_item = (Item) item_list.read_object(it);
            if (curr_item == null)
            {
                break;
            }
            if (curr_item instanceof Connectable && curr_item.contains_net(p_net_no))
            {
                items_to_handle.add(curr_item);
            }
        }
        Iterator<Item> it2 = items_to_handle.iterator();
        while (it2.hasNext())
        {
            Item curr_item = it2.next();
            Collection<Item> next_connected_set = curr_item.get_connected_set(p_net_no);
            result.add(next_connected_set);
            items_to_handle.removeAll(next_connected_set);
            it2 = items_to_handle.iterator();
        }
        return result;
    }

    /**
     * Returns all SearchTreeObjects on layer p_layer, which overlap with p_shape.
     * If p_layer < 0, the layer is ignored
     */
    public Set<SearchTreeObject> overlapping_objects(ConvexShape p_shape, int p_layer)
    {
        return this.search_tree_manager.get_default_tree().overlapping_objects(p_shape, p_layer);
    }

    /**
     * Returns items, which overlap with p_shape on layer p_layer
     * inclusive clearance.
     * p_clearance_class is the index in the clearance matrix,
     * which describes the required clearance restrictions to other items.
     * The function may also return items, which are nearly overlapping,
     * but do not overlap with exact calculation.
     * If p_layer < 0, the layer is ignored.
     */
    public Set<Item> overlapping_items_with_clearance(ConvexShape p_shape, int p_layer, int[] p_ignore_net_nos,
                                                      int p_clearance_class)
    {
        ShapeSearchTree default_tree = this.search_tree_manager.get_default_tree();
        return default_tree.overlapping_items_with_clearance(p_shape, p_layer, p_ignore_net_nos, p_clearance_class);
    }

    /**
     * Returns all items on layer p_layer, which overlap with p_area.
     * If p_layer < 0, the layer is ignored
     */
    public Set<Item> overlapping_items(Area p_area, int p_layer)
    {
        Set<Item> result = new TreeSet<Item>();
        TileShape[] tile_shapes = p_area.split_to_convex();
        for (int i = 0; i < tile_shapes.length; ++i)
        {
            Set<SearchTreeObject> curr_overlaps = overlapping_objects(tile_shapes[i], p_layer);
            for (SearchTreeObject curr_overlap : curr_overlaps)
            {
                if (curr_overlap instanceof Item)
                {
                    result.add((Item) curr_overlap);
                }
            }
        }
        return result;
    }

    /**
     * Checks, if the an object with shape p_shape and net nos p_net_no_arr
     * and clearance class p_cl_class can be inserted on layer p_layer
     * without clearance violation.
     */
    public boolean check_shape(Area p_shape, int p_layer, int[] p_net_no_arr, int p_cl_class)
    {
        TileShape[] tiles = p_shape.split_to_convex();
        ShapeSearchTree default_tree = this.search_tree_manager.get_default_tree();
        for (int i = 0; i < tiles.length; ++i)
        {
            TileShape curr_shape = tiles[i];
            if (!curr_shape.is_contained_in(bounding_box))
            {
                return false;
            }
            Set<SearchTreeObject> obstacles = new TreeSet<SearchTreeObject>();
            default_tree.overlapping_objects_with_clearance(curr_shape, p_layer,
                    p_net_no_arr, p_cl_class, obstacles);
            for (SearchTreeObject curr_ob : obstacles)
            {
                boolean is_obstacle = true;
                for (int j = 0; j < p_net_no_arr.length; ++j)
                {
                    if (!curr_ob.is_obstacle(p_net_no_arr[j]))
                    {
                        is_obstacle = false;
                    }
                }
                if (is_obstacle)
                {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Checks, if the a trace line with shape p_shape and net numbers p_net_no_arr
     * and clearance class p_cl_class can be inserted on layer p_layer
     * without clearance violation.
     * If p_contact_pins != null, all pins not contained in p_contact_pins are
     * regarded as obstacles, even if they are of the own net.
     */
    public boolean check_trace_shape(TileShape p_shape, int p_layer, int[] p_net_no_arr,
                                     int p_cl_class, Set<Pin> p_contact_pins)
    {
        if (!p_shape.is_contained_in(bounding_box))
        {
            return false;
        }
        ShapeSearchTree default_tree = this.search_tree_manager.get_default_tree();
        Collection<TreeEntry> tree_entries = new LinkedList<TreeEntry>();
        int[] ignore_net_nos = new int[0];
        if (default_tree.is_clearance_compensation_used())
        {
            default_tree.overlapping_tree_entries(p_shape, p_layer, ignore_net_nos, tree_entries);
        }
        else
        {
            default_tree.overlapping_tree_entries_with_clearance(p_shape, p_layer, ignore_net_nos, p_cl_class, tree_entries);
        }
        for (TreeEntry curr_tree_entry : tree_entries)
        {
            if (!(curr_tree_entry.object instanceof Item))
            {
                continue;
            }
            Item curr_item = (Item) curr_tree_entry.object;
            if (p_contact_pins != null)
            {
                if (p_contact_pins.contains(curr_item))
                {
                    continue;
                }
                if (curr_item instanceof Pin)
                {
                    // The contact pins of the trace should be contained in p_ignore_items.
                    // Other pins are handled as obstacles to avoid acid traps.
                    return false;
                }
            }
            boolean is_obstacle = true;
            for (int i = 0; i < p_net_no_arr.length; ++i)
            {
                if (!curr_item.is_trace_obstacle(p_net_no_arr[i]))
                {
                    is_obstacle = false;
                }
            }
            if (is_obstacle && (curr_item instanceof PolylineTrace) && p_contact_pins != null)
            {
                // check for traces of foreign nets at tie pins, which will be ignored inside the pin shape
                TileShape intersection = null;
                for (Pin curr_contact_pin : p_contact_pins)
                {
                    if (curr_contact_pin.net_count() <= 1 || !curr_contact_pin.shares_net(curr_item))
                    {
                        continue;
                    }
                    if (intersection == null)
                    {
                        TileShape obstacle_trace_shape = curr_item.get_tile_shape(curr_tree_entry.shape_index_in_object);
                        intersection = p_shape.intersection(obstacle_trace_shape);
                    }
                    TileShape pin_shape = curr_contact_pin.get_tile_shape_on_layer(p_layer);
                    if (pin_shape.contains_approx(intersection))
                    {
                        is_obstacle = false;
                        break;
                    }
                }
            }
            if (is_obstacle)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks, if a polyline trace with the input parameters can be inserted
     * without clearance violations
     */
    public boolean check_polyline_trace(Polyline p_polyline, int p_layer, int p_pen_half_width,
                                        int[] p_net_no_arr, int p_clearance_class)
    {
        Trace tmp_trace =
                new PolylineTrace(p_polyline, p_layer, p_pen_half_width, p_net_no_arr, p_clearance_class, 0, 0, FixedState.UNFIXED, this);
        Set<Pin> contact_pins = tmp_trace.touching_pins_at_end_corners();
        for (int i = 0; i < tmp_trace.tile_shape_count(); ++i)
        {
            if (!this.check_trace_shape(tmp_trace.get_tile_shape(i), p_layer, p_net_no_arr,
                    p_clearance_class, contact_pins))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the layer count of this board.
     */
    public int get_layer_count()
    {
        return layer_structure.arr.length;
    }

    /**
     * Draws all items of the board on their visible layers. Called in the overwritten
     * paintComponent method of a class derived from JPanel.
     * The value of p_layer_visibility is expected between 0 and 1 for each layer.
     */
    public void draw(Graphics p_graphics, GraphicsContext p_graphics_context)
    {
        if (p_graphics_context == null)
        {
            return;
        }

        // draw all items on the board
        for (int curr_priority = Drawable.MIN_DRAW_PRIORITY; curr_priority <= Drawable.MIDDLE_DRAW_PRIORITY; ++curr_priority)
        {
            Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
            for (;;)
            {
                try
                {
                    Item curr_item = (Item) item_list.read_object(it);
                    if (curr_item == null)
                    {
                        break;
                    }
                    if (curr_item.get_draw_priority() == curr_priority)
                    {
                        curr_item.draw(p_graphics, p_graphics_context);
                    }
                }
                catch (java.util.ConcurrentModificationException e)
                {
                    // may happen when window are changed interactively while running a logfile
                    return;
                }
            }
        }
    }

    /**
     * Returns the list of items on the board, whose shape on layer p_layer contains the point at p_location.
     * If p_layer < 0, the layer is ignored.
     * If p_item_selection_filter != null, only items of types selected by the filter are picked.
     */
    public Set<Item> pick_items(Point p_location, int p_layer, ItemSelectionFilter p_filter)
    {
        TileShape point_shape = TileShape.get_instance(p_location);
        Collection<SearchTreeObject> overlaps = overlapping_objects(point_shape, p_layer);
        Set<Item> result = new TreeSet<Item>();
        for (SearchTreeObject curr_object : overlaps)
        {
            if (curr_object instanceof Item)
            {
                result.add((Item) curr_object);
            }
        }
        if (p_filter != null)
        {
            result = p_filter.filter(result);
        }
        return result;
    }

    /**
     * checks, if p_point is contained in the bounding box of this board.
     */
    public boolean contains(Point p_point)
    {
        return p_point.is_contained_in(bounding_box);
    }

    /**
     * Returns the minimum clearance requested between items of
     * clearance class p_class_1 and p_class_2.
     * p_class_1 and p_class_2 are indices in the clearance matrix.
     */
    public int clearance_value(int p_class_1, int p_class_2, int p_layer)
    {
        if (rules == null || rules.clearance_matrix == null)
        {
            return 0;
        }
        return rules.clearance_matrix.value(p_class_1, p_class_2, p_layer);
    }

    /**
     * returns the biggest half width of all traces on the board.
     */
    public int get_max_trace_half_width()
    {
        return max_trace_half_width;
    }

    /**
     * returns the smallest half width of all traces on the board.
     */
    public int get_min_trace_half_width()
    {
        return min_trace_half_width;
    }

    /**
     * Returns a surrounding box of the geometry of this board
     */
    public IntBox get_bounding_box()
    {
        return bounding_box;
    }

    /**
     * Returns a box containing all items in p_item_list.
     */
    public IntBox get_bounding_box(Collection<Item> p_item_list)
    {
        IntBox result = IntBox.EMPTY;
        for (Item curr_item : p_item_list)
        {
            result = result.union(curr_item.bounding_box());
        }
        return result;
    }

    /**
     * Resets the rectangle, where a graphics update is needed.
     */
    public void reset_graphics_update_box()
    {
        update_box = IntBox.EMPTY;
    }

    /**
     * Gets the rectancle, where a graphics update is needed on the screen.
     */
    public IntBox get_graphics_update_box()
    {
        return update_box;
    }

    /**
     * enlarges the graphics update box, so that it contains p_box
     */
    public void join_graphics_update_box(IntBox p_box)
    {
        if (update_box == null)
        {
            reset_graphics_update_box();
        }
        update_box = update_box.union(p_box);
    }

    /**
     * starts notifying the observers of any change in the objects list
     */
    public void start_notify_observers()
    {
        if (this.communication.observers != null)
        {
            communication.observers.activate();
        }
    }

    /**
     * ends notifying the observers of changes in the objects list
     */
    public void end_notify_observers()
    {
        if (this.communication.observers != null)
        {
            communication.observers.deactivate();
        }
    }

    /**
     * Returns, if the observer of the board items is activated.
     */
    public boolean observers_active()
    {
        boolean result;
        if (this.communication.observers != null)
        {
            result = communication.observers.is_active();
        }
        else
        {
            result = false;
        }
        return result;
    }

    /**
     * Turns an obstacle area into a conduction area with net number p_net_no
     * If it is convex and has no holes, it is turned into a Pin,
     * alse into a conduction area.
     */
    public Connectable make_conductive(ObstacleArea p_area, int p_net_no)
    {
        Item new_item;
        Area curr_area = p_area.get_relative_area();
        int layer = p_area.get_layer();
        FixedState fixed_state = p_area.get_fixed_state();
        Vector translation = p_area.get_translation();
        double rotation = p_area.get_rotation_in_degree();
        boolean side_changed = p_area.get_side_changed();
        int[] net_no_arr = new int[1];
        net_no_arr[0] = p_net_no;
        new_item = new ConductionArea(curr_area, layer, translation, rotation, side_changed, net_no_arr,
                p_area.clearance_class_no(), 0, p_area.get_component_no(), p_area.name, true, fixed_state, this);
        remove_item(p_area);
        insert_item(new_item);
        return (Connectable) new_item;
    }

    /**
     * Inserts an item into the board data base
     */
    public void insert_item(Item p_item)
    {
        if (p_item == null)
        {
            return;
        }

        if (rules == null || rules.clearance_matrix == null || p_item.clearance_class_no() < 0 ||
                p_item.clearance_class_no() >= rules.clearance_matrix.get_class_count())
        {
            System.out.println("LayeredBoard.insert_item: clearance_class no out of range");
            p_item.set_clearance_class_no(0);
        }
        p_item.board = this;
        item_list.insert(p_item);
        search_tree_manager.insert(p_item);
        communication.observers.notify_new(p_item);
        additional_update_after_change(p_item);
    }

    /**
     * Stub function overwritten in class RoutingBoard to maintain the autorouter database if necessesary.
     */
    public void additional_update_after_change(Item p_item)
    {
    }

    /**
     * Restores the sitiation at the previous snapshot.
     * Returns false, if no more undo is possible.
     * Puts the numbers of the changed nets into the set p_changed_nets, if p_changed_nets != null
     */
    public boolean undo(Set<Integer> p_changed_nets)
    {
        this.components.undo(this.communication.observers);
        Collection<UndoableObjects.Storable> cancelled_objects = new LinkedList<UndoableObjects.Storable>();
        Collection<UndoableObjects.Storable> restored_objects = new LinkedList<UndoableObjects.Storable>();
        boolean result = item_list.undo(cancelled_objects, restored_objects);
        // update the search trees
        Iterator<UndoableObjects.Storable> it = cancelled_objects.iterator();
        while (it.hasNext())
        {
            Item curr_item = (Item) it.next();
            search_tree_manager.remove(curr_item);

            // let the observers syncronize the deletion
            communication.observers.notify_deleted(curr_item);
            if (p_changed_nets != null)
            {
                for (int i = 0; i < curr_item.net_count(); ++i)
                {
                    p_changed_nets.add(new Integer(curr_item.get_net_no(i)));
                }
            }
        }
        it = restored_objects.iterator();
        while (it.hasNext())
        {
            Item curr_item = (Item) it.next();
            curr_item.board = this;
            search_tree_manager.insert(curr_item);
            curr_item.clear_autoroute_info();
            // let the observers know the insertion
            communication.observers.notify_new(curr_item);
            if (p_changed_nets != null)
            {
                for (int i = 0; i < curr_item.net_count(); ++i)
                {
                    p_changed_nets.add(new Integer(curr_item.get_net_no(i)));
                }
            }
        }
        return result;
    }

    /**
     * Restores the sitiation before the last undo.
     * Returns false, if no more redo is possible.
     * Puts the numbers of the changed nets into the set p_changed_nets, if p_changed_nets != null
     */
    public boolean redo(Set<Integer> p_changed_nets)
    {
        this.components.redo(this.communication.observers);
        Collection<UndoableObjects.Storable> cancelled_objects = new LinkedList<UndoableObjects.Storable>();
        Collection<UndoableObjects.Storable> restored_objects = new LinkedList<UndoableObjects.Storable>();
        boolean result = item_list.redo(cancelled_objects, restored_objects);
        // update the search trees
        Iterator<UndoableObjects.Storable> it = cancelled_objects.iterator();
        while (it.hasNext())
        {
            Item curr_item = (Item) it.next();
            search_tree_manager.remove(curr_item);
            // let the observers syncronize the deletion
            communication.observers.notify_deleted(curr_item);
            if (p_changed_nets != null)
            {
                for (int i = 0; i < curr_item.net_count(); ++i)
                {
                    p_changed_nets.add(curr_item.get_net_no(i));
                }
            }
        }
        it = restored_objects.iterator();
        while (it.hasNext())
        {
            Item curr_item = (Item) it.next();
            curr_item.board = this;
            search_tree_manager.insert(curr_item);
            curr_item.clear_autoroute_info();
            // let the observers know the insertion
            communication.observers.notify_new(curr_item);
            if (p_changed_nets != null)
            {
                for (int i = 0; i < curr_item.net_count(); ++i)
                {
                    p_changed_nets.add(curr_item.get_net_no(i));
                }
            }
        }
        return result;
    }

    /**
     * Makes the current board situation restorable by undo.
     */
    public void generate_snapshot()
    {
        item_list.generate_snapshot();
        components.generate_snapshot();
    }

    /**
     *  Removes the top snapshot from the undo stack, so that its situation cannot be
     *  restored any more.
     *  Returns false, if no more snapshot could be popped.
     */
    public boolean pop_snapshot()
    {
        return item_list.pop_snapshot();
    }

    /**
     * Looks if at the input position ends a trace with the input net number,
     * which has no normal contact at that position.
     * Returns null, if no tail is found.
     */
    public Trace get_trace_tail(Point p_location, int p_layer, int[] p_net_no_arr)
    {
        TileShape point_shape = TileShape.get_instance(p_location);
        Collection<SearchTreeObject> found_items = overlapping_objects(point_shape, p_layer);
        Iterator<SearchTreeObject> it = found_items.iterator();
        while (it.hasNext())
        {
            SearchTreeObject curr_ob = it.next();
            if (curr_ob instanceof Trace)
            {
                Trace curr_trace = (Trace) curr_ob;
                if (!curr_trace.nets_equal(p_net_no_arr))
                {
                    continue;
                }
                if (curr_trace.first_corner().equals(p_location))
                {
                    Collection<Item> contacts = curr_trace.get_start_contacts();
                    if (contacts.size() == 0)
                    {
                        return curr_trace;
                    }
                }
                if (curr_trace.last_corner().equals(p_location))
                {
                    Collection<Item> contacts = curr_trace.get_end_contacts();
                    if (contacts.size() == 0)
                    {
                        return curr_trace;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Checks, if p_item item is part of a cycle and remuve it
     * together with its connection in this case.
     */
    public boolean remove_if_cycle(Trace p_trace)
    {
        if (!p_trace.is_on_the_board())
        {
            return false;
        }
        if (!p_trace.is_cycle())
        {
            return false;
        }
        // Remove tails at the endpoints after removing the cycle,
        // if there was no tail before.
        boolean[] tail_at_endpoint_before = null;
        Point[] end_corners = null;
        int curr_layer = p_trace.get_layer();
        int[] curr_net_no_arr = p_trace.net_no_arr;
        end_corners = new Point[2];
        end_corners[0] = p_trace.first_corner();
        end_corners[1] = p_trace.last_corner();
        tail_at_endpoint_before = new boolean[2];
        for (int i = 0; i < 2; ++i)
        {
            Trace tail =
                    get_trace_tail(end_corners[i], curr_layer, curr_net_no_arr);
            tail_at_endpoint_before[i] = (tail != null);
        }
        Set<Item> connection_items = p_trace.get_connection_items();
        this.remove_items(connection_items, false);
        for (int i = 0; i < 2; ++i)
        {
            if (!tail_at_endpoint_before[i])
            {
                Trace tail = get_trace_tail(end_corners[i], curr_layer, curr_net_no_arr);
                if (tail != null)
                {
                    remove_items(tail.get_connection_items(), false);
                }
            }
        }
        return true;
    }

    /**
     * If != RELEASE_VERSION,, some features may be used, which are still in experimental state.
     * Also warnings for debugging may be printed depending on the test_level.
     */
    public TestLevel get_test_level()
    {
        return this.test_level;
    }

    /**
     * Only to be used in BoardHandling.read_design.
     */
    public void set_test_level(TestLevel p_value)
    {
        this.test_level = p_value;
    }

    private void readObject(java.io.ObjectInputStream p_stream)
            throws java.io.IOException, java.lang.ClassNotFoundException
    {
        p_stream.defaultReadObject();
        // insert the items on the board into the search trees
        search_tree_manager = new SearchTreeManager(this);
        Iterator<Item> it = this.get_items().iterator();
        while (it.hasNext())
        {
            Item curr_item = it.next();
            curr_item.board = this;
            search_tree_manager.insert(curr_item);
        }
    }
    /**
     * List of items inserted into this board
     */
    public final UndoableObjects item_list;
    /** List of placed components on the board. */
    public final Components components;
    /**
     * Class defining the rules for items to be inserted into this board.
     * Contains for example the clearance matrix.
     */
    public final BoardRules rules;
    /**
     * The library containing pastack masks, packagages and other
     * templates used on the board.
     */
    public final BoardLibrary library;
    /**
     * The layer structure of this board.
     */
    public final LayerStructure layer_structure;
    /**
     * Handels the search trees pointing into the items of this board
     */
    public transient SearchTreeManager search_tree_manager;
    /**
     * For communication with a host system or host design file formats.
     */
    public final Communication communication;
    /**
     * bounding orthogonal rectangle of this board
     */
    public final IntBox bounding_box;
    /**
     * If test_level != RELEASE_VERSION, some features may be used, which are still in experimental state.
     * Also warnings  for debugging may be printed depending on the size of test_level.
     */
    transient private TestLevel test_level;
    /** the rectangle, where the graphics may be not uptodate */
    transient private IntBox update_box = IntBox.EMPTY;
    /**
     * the biggest half width of all traces on the board
     */
    private int max_trace_half_width = 1000;
    /**
     * the smallest half width of all traces on the board
     */
    private int min_trace_half_width = 10000;
    /**
     * Limits the maximum width of a shape in the search tree.
     */
}
