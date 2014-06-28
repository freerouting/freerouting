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
 * CopyItemState.java
 *
 * Created on 11. November 2003, 08:23
 */

package interactive;

import geometry.planar.FloatPoint;
import geometry.planar.Point;
import geometry.planar.Vector;
import geometry.planar.ConvexShape;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import library.Padstack;
import library.Package;

import board.Item;
import board.DrillItem;
import board.ObstacleArea;
import board.Via;
import board.Component;
import board.RoutingBoard;

/**
 * Interactive copying of items.
 *
 * @author  Alfons Wirtz
 */
public class CopyItemState extends InteractiveState
{
    /**
     * Returns a new instance of CopyItemState or null, if p_item_list is empty.
     */
    public static CopyItemState get_instance(FloatPoint p_location, Collection<Item> p_item_list,
    InteractiveState p_parent_state, BoardHandling p_board_handling, Logfile p_logfile)
    {
        if (p_item_list.size() == 0)
        {
            return null;
        }
        p_board_handling.remove_ratsnest(); // copying an item may change the connectivity.
        return new CopyItemState(p_location, p_item_list, p_parent_state, p_board_handling, p_logfile);
    }
    
    /** Creates a new instance of CopyItemState */
    private CopyItemState(FloatPoint p_location, Collection<Item> p_item_list,
    InteractiveState p_parent_state, BoardHandling p_board_handling, Logfile p_logfile)
    {
        super(p_parent_state, p_board_handling, p_logfile);
        item_list = new LinkedList<Item>();
        
        start_position = p_location.round();
        current_layer =  p_board_handling.settings.layer;
        layer_changed = false;
        current_position = start_position;
        previous_position = current_position;
        Iterator<Item> it = p_item_list.iterator();
        while (it.hasNext())
        {
            Item curr_item = it.next();
            if (curr_item instanceof DrillItem || curr_item instanceof ObstacleArea)
            {
                Item new_item = curr_item.copy(0);
                item_list.add(new_item);
            }
        }
        if (logfile != null)
        {
            logfile.start_scope(LogfileScope.COPYING_ITEMS, p_location);
        }
    }
    
    public InteractiveState mouse_moved()
    {
        super.mouse_moved();
        change_position(hdlg.get_current_mouse_position());
        return this;
    }
    
    /**
     * Changes the position for inserting the copied items to p_new_location.
     */
    private void change_position(FloatPoint p_new_position)
    {
        current_position = p_new_position.round();
        if (!current_position.equals(previous_position))
        {
            Vector translate_vector = current_position.difference_by(previous_position);
            Iterator<board.Item> it = item_list.iterator();
            while (it.hasNext())
            {
                board.Item curr_item = it.next();
                curr_item.translate_by(translate_vector);
            }
            previous_position = current_position;
            hdlg.repaint();
        }
    }
    
    /**
     * Changes the first layer of the items in the copy list to p_new_layer.
     */
    public boolean change_layer_action(int p_new_layer)
    {
        if (logfile != null)
        {
            logfile.start_scope(LogfileScope.CHANGE_LAYER, p_new_layer);
        }
        current_layer = p_new_layer;
        layer_changed = true;
        hdlg.set_layer(p_new_layer);
        return true;
    }
    
    /**
     * Inserts the items in the copy list into the board.
     * Items, which would produce a clearance violation, are not inserted.
     */
    public void insert()
    {
        if (item_list == null)
        {
            return;
        }
        Map<Padstack, Padstack> padstack_pairs = new TreeMap<Padstack, Padstack>(); // Contains old and new padstacks after layer change.
        
        RoutingBoard board = hdlg.get_routing_board();
        if (layer_changed)
        {
            // create new via padstacks
            Iterator<Item> it = item_list.iterator();
            while (it.hasNext())
            {
                Item curr_ob = it.next();
                if (curr_ob instanceof Via)
                {
                    Via curr_via = (Via) curr_ob;
                    Padstack new_padstack = change_padstack_layers( curr_via.get_padstack(), current_layer, board, padstack_pairs);
                    curr_via.set_padstack(new_padstack);
                }
            }
        }
        // Copy the components of the old items and assign the new items to the copied components.
        
        /** Contailns the old and new id no of a copied component. */
        Map<Integer, Integer> cmp_no_pairs = new TreeMap<Integer, Integer>();
        
        /** Contains the new created components after copying. */
        Collection<Component> copied_components = new LinkedList<Component>();
        
        Vector translate_vector = current_position.difference_by(start_position);
        Iterator<Item> it = item_list.iterator();
        while (it.hasNext())
        {
            Item curr_item = it.next();
            int curr_cmp_no = curr_item.get_component_no();
            if (curr_cmp_no > 0)
            {
                //This item belongs to a component
                int new_cmp_no;
                Integer curr_key = new Integer(curr_cmp_no);
                if (cmp_no_pairs.containsKey(curr_key))
                {
                    // the new component for this pin is already created
                    Integer curr_value = cmp_no_pairs.get(curr_key);
                    new_cmp_no = curr_value.intValue();
                }
                else
                {
                    Component old_component = board.components.get(curr_cmp_no);
                    if (old_component == null)
                    {
                        System.out.println("CopyItemState: component not found");
                        continue;
                    }
                    Point new_location = old_component.get_location().translate_by(translate_vector);
                    Package new_package;
                    if (layer_changed)
                    {
                        // create a new package with changed layers of the padstacks.
                        Package.Pin [] new_pin_arr = new Package.Pin[ old_component.get_package().pin_count()];
                        for (int i = 0; i < new_pin_arr.length; ++i)
                        {
                            Package.Pin old_pin = old_component.get_package().get_pin(i);
                            Padstack old_padstack = board.library.padstacks.get(old_pin.padstack_no);
                            if (old_padstack == null)
                            {
                                System.out.println("CopyItemState.insert: package padstack not found");
                                return;
                            }
                            Padstack new_padstack = change_padstack_layers( old_padstack, current_layer, board, padstack_pairs);
                            new_pin_arr[i] = new Package.Pin(old_pin.name, new_padstack.no, old_pin.relative_location, old_pin.rotation_in_degree);
                        }
                        new_package = board.library.packages.add(new_pin_arr);
                    }
                    else
                    {
                        new_package = old_component.get_package();
                    }
                    Component new_component =
                    board.components.add(new_location, old_component.get_rotation_in_degree(), 
                            old_component.placed_on_front(), new_package);
                    copied_components.add(new_component);
                    new_cmp_no = new_component.no;
                    cmp_no_pairs.put(new Integer(curr_cmp_no), new Integer(new_cmp_no));
                }
                curr_item.assign_component_no(new_cmp_no);
            }
        }
        boolean all_items_inserted = true;
        it = item_list.iterator();
        boolean first_time = true;
        while (it.hasNext())
        {
            Item curr_item = it.next();
            if (curr_item.board != null && curr_item.clearance_violation_count() == 0)
            {
                if (first_time)
                {
                    // make the current situation restorable by undo
                    board.generate_snapshot();
                    first_time = false;
                }
                board.insert_item(curr_item.copy(0));
            }
            else
            {
                all_items_inserted = false;
            }
        }
        if (all_items_inserted)
        {
            hdlg.screen_messages.set_status_message(resources.getString("all_items_inserted"));
        }
        else
        {
            hdlg.screen_messages.set_status_message(resources.getString("some_items_not_inserted_because_of_obstacles"));
        }
        if (logfile != null)
        {
            logfile.add_corner(this.current_position.to_float());
        }
        start_position = current_position;
        layer_changed = false;
        hdlg.repaint();
    }
    
    public InteractiveState left_button_clicked(FloatPoint p_location)
    {
        insert();
        return this;
    }
    
    public InteractiveState process_logfile_point(FloatPoint p_location)
    {
        change_position(p_location);
        insert();
        return this;
    }
    
    public void draw(java.awt.Graphics p_graphics)
    {
        if (item_list == null)
        {
            return;
        }
        Iterator<Item> it = item_list.iterator();
        while (it.hasNext())
        {
            Item curr_item = it.next();
            curr_item.draw(p_graphics, hdlg.graphics_context, hdlg.graphics_context.get_hilight_color(),
            hdlg.graphics_context.get_hilight_color_intensity());
        }
    }
    
    public javax.swing.JPopupMenu get_popup_menu()
    {
        return hdlg.get_panel().popup_menu_copy;
    }
    
    /**
     * Creates a new padstack from p_old_pastack with a layer range starting at p_new_layer.
     */
    private static Padstack change_padstack_layers(Padstack p_old_padstack, int p_new_layer, 
            RoutingBoard p_board, Map<Padstack, Padstack> p_padstack_pairs)
    {
        Padstack new_padstack;
        int old_layer = p_old_padstack.from_layer();
        if (old_layer == p_new_layer)
        {
            new_padstack = p_old_padstack;
        }
        else if (p_padstack_pairs.containsKey(p_old_padstack))
        {
            // New padstack already created, assign it to the via.
            new_padstack = p_padstack_pairs.get(p_old_padstack);
        }
        else
        {
            // Create a new padstack.
            ConvexShape[] new_shapes = new ConvexShape[p_board.get_layer_count()];
            int layer_diff = old_layer - p_new_layer;
            for (int i = 0; i < new_shapes.length; ++i)
            {
                int new_layer_no = i + layer_diff;
                if (new_layer_no >= 0 && new_layer_no < new_shapes.length)
                {
                    new_shapes[i] = p_old_padstack.get_shape(i + layer_diff);
                }
            }
            new_padstack = p_board.library.padstacks.add(new_shapes);
            p_padstack_pairs.put(p_old_padstack, new_padstack);
        }
        return new_padstack;
    }
    
    private Collection<Item> item_list;
    private Point start_position;
    private Point current_position;
    private int current_layer;
    private boolean layer_changed;
    private Point previous_position;
}
