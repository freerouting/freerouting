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
 * MoveComponentState.java
 *
 * Created on 11. Mai 2005, 06:34
 */

package interactive;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;

import geometry.planar.FloatPoint;
import geometry.planar.IntPoint;
import geometry.planar.Vector;
import geometry.planar.Point;

import library.BoardLibrary;

import board.Component;
import board.Item;
import board.Via;
import board.ClearanceViolation;
import board.LayerStructure;

/**
 *
 * @author Alfons Wirtz
 */
public class MoveItemState extends InteractiveState
{
    /**
     * Returns a new instance of MoveComponentState, or null, if the items of p_itemlist do not belong
     * to a single component.
     */
    public static MoveItemState get_instance(FloatPoint p_location, Collection<Item> p_item_list,
            InteractiveState p_parent_state, BoardHandling p_board_handling, Logfile p_logfile)
    {
        java.util.ResourceBundle resources = java.util.ResourceBundle.getBundle("interactive.resources.InteractiveState", p_board_handling.get_locale());
        if (p_item_list.isEmpty())
        {
            p_board_handling.screen_messages.set_status_message(resources.getString("move_component_failed_because_no_item_selected"));
            return null;
        }
        // extend p_item_list to full  components
        Set<Item> item_list = new TreeSet<Item>();
        Set<Component> component_list = new TreeSet<Component>();
        board.BasicBoard routing_board = p_board_handling.get_routing_board();
        Component grid_snap_component = null;
        for (Item curr_item : p_item_list)
        {
            if (curr_item.get_component_no() > 0)
            {
                Component curr_component = routing_board.components.get(curr_item.get_component_no());
                if (curr_component == null)
                {
                    System.out.println("MoveComponentState.get_instance inconsistant component number");
                    return null;
                }
                if (grid_snap_component == null &&
                        (p_board_handling.settings.horizontal_component_grid > 0 ||
                        p_board_handling.settings.horizontal_component_grid > 0))
                {
                    grid_snap_component = curr_component;
                }
                if (!component_list.contains(curr_component))
                {
                    java.util.Collection<Item> component_items = routing_board.get_component_items(curr_component.no);
                    for (Item curr_component_item : component_items)
                    {
                        component_list.add(curr_component);
                        item_list.add(curr_component_item);
                    }
                }
            }
            else
            {
                item_list.add(curr_item);
            }
        }
        Set<Item> fixed_items = new TreeSet<Item>();
        Set<Item> obstacle_items = new TreeSet<Item>();
        Set<Item> add_items = new TreeSet<Item>();
        boolean move_ok = true;
        for (Item curr_item : item_list)
        {
            if (curr_item.is_user_fixed())
            {
                p_board_handling.screen_messages.set_status_message(resources.getString("some_items_cannot_be_moved_because_they_are_fixed"));
                move_ok = false;
                obstacle_items.add(curr_item);
                fixed_items.add(curr_item);
            }
            else if (curr_item.is_connected())
            {
                // Check if the whole connected set is inside the selected items,
                // and add the items of the connected set  to the move list in this case.
                // Conduction areas are ignored, because otherwise components with
                // pins contacted to a plane could never be moved.
                boolean item_movable = true;
                Collection<Item> contacts = curr_item.get_connected_set(-1, true);
                {
                    for (Item curr_contact : contacts)
                    {
                        if (curr_contact instanceof board.ConductionArea)
                        {
                            
                            continue;
                        }
                        if (curr_contact.is_user_fixed())
                        {
                            item_movable = false;
                            fixed_items.add(curr_contact);
                        }
                        else if (curr_contact.get_component_no() != 0)
                        {
                            Component curr_component = routing_board.components.get(curr_contact.get_component_no());
                            if (!component_list.contains(curr_component))
                            {
                                item_movable = false;
                            }
                        }
                        if (item_movable)
                        {
                            add_items.add(curr_contact);
                        }
                        else
                        {
                            obstacle_items.add(curr_contact);
                        }
                    }
                }
                if (!item_movable)
                {
                    move_ok = false;
                }
                
            }
        }
        if (!move_ok)
        {
            if (p_parent_state instanceof SelectedItemState)
            {
                if (fixed_items.size() > 0)
                {
                    ((SelectedItemState)p_parent_state).get_item_list().addAll(fixed_items);
                    p_board_handling.screen_messages.set_status_message(resources.getString("please_unfix_selected_items_before_moving"));
                }
                else
                {
                    ((SelectedItemState)p_parent_state).get_item_list().addAll(obstacle_items);
                    p_board_handling.screen_messages.set_status_message(resources.getString("please_unroute_or_extend_selection_before_moving"));
                }
            }
            return null;
        }
        item_list.addAll(add_items);
        return new MoveItemState(p_location, item_list, component_list, grid_snap_component,
                p_parent_state.return_state, p_board_handling, p_logfile);
    }
    
    /** Creates a new instance of MoveComponentState */
    private MoveItemState(FloatPoint p_location, Set<Item> p_item_list, Set<Component> p_component_list,
            Component p_first_component, InteractiveState p_parent_state, BoardHandling p_board_handling, Logfile p_logfile)
    {
        super(p_parent_state, p_board_handling, p_logfile);
        this.component_list = p_component_list;
        this.grid_snap_component = p_first_component;
        this.current_position = p_location.round();
        this.previous_position = current_position;
        if (logfile != null)
        {
            logfile.start_scope(LogfileScope.MOVE_ITEMS, p_location);
        }
        board.BasicBoard routing_board = hdlg.get_routing_board();
        this.observers_activated = !hdlg.get_routing_board().observers_active();
        if (this.observers_activated)
        {
            hdlg.get_routing_board().start_notify_observers();
        }
        // make the situation restorable by undo
        routing_board.generate_snapshot();
        
        for (Item curr_item : p_item_list)
        {
            routing_board.remove_item(curr_item);
        }
        this.net_items_list = new LinkedList<NetItems>();
        this.item_list = new TreeSet<Item>();
        
        for (Item curr_item : p_item_list)
        {
            // Copy the items in p_item_list, because otherwise the undo algorithm will not work.
            Item copied_item = curr_item.copy(0);
            for (int i = 0; i < curr_item.net_count(); ++i)
            {
                add_to_net_items_list(copied_item, curr_item.get_net_no(i));
            }
            this.item_list.add(copied_item);
        }
    }
    
    private void add_to_net_items_list(Item p_item, int p_net_no)
    {
        for ( NetItems curr_items : this.net_items_list)
        {
            if (curr_items.net_no == p_net_no)
            {
                // list for p_net_no exists already
                curr_items.items.add(p_item) ;
                return;
            }
        }
        Collection<Item> new_item_list = hdlg.get_routing_board().get_connectable_items(p_net_no);
        new_item_list.add(p_item);
        NetItems new_net_items = new NetItems(p_net_no, new_item_list);
        this.net_items_list.add(new_net_items);
    }
    
    public InteractiveState mouse_moved()
    {
        super.mouse_moved();
        move(hdlg.get_current_mouse_position());
        if (logfile != null)
        {
            logfile.add_corner(this.current_position.to_float());
        }
        return this;
    }
    
    public InteractiveState process_logfile_point(FloatPoint p_point)
    {
        move(p_point);
        return this;
    }
    
    public InteractiveState left_button_clicked(FloatPoint p_location)
    {
        return this.complete();
    }
    
    public InteractiveState complete()
    {
        for (Item curr_item : this.item_list)
        {
            if (curr_item.clearance_violation_count() > 0)
            {
                hdlg.screen_messages.set_status_message(resources.getString("insertion_failed_because_of_obstacles"));
                return this;
            }
        }
        board.BasicBoard routing_board = hdlg.get_routing_board();
        for (Item curr_item : this.item_list)
        {
            routing_board.insert_item(curr_item);
        }
        
        // let the observers syncronize the moving
        for (Component curr_component : this.component_list)
        {
            routing_board.communication.observers.notify_moved(curr_component);
        }
        
        for (NetItems curr_net_items : this.net_items_list)
        {
            this.hdlg.update_ratsnest(curr_net_items.net_no);
        }
        
        if (logfile != null)
        {
            logfile.start_scope(LogfileScope.COMPLETE_SCOPE);
        }
        hdlg.screen_messages.set_status_message(resources.getString("move_completed"));
        hdlg.repaint();
        return this.return_state;
    }
    
    public InteractiveState cancel()
    {
        hdlg.get_routing_board().undo(null);
        for (NetItems curr_net_items : this.net_items_list)
        {
            this.hdlg.update_ratsnest(curr_net_items.net_no);
        }
        if (logfile != null)
        {
            logfile.start_scope(LogfileScope.CANCEL_SCOPE);
        }
        return this.return_state;
    }
    
    public InteractiveState mouse_wheel_moved(int p_rotation)
    {
        if (hdlg.settings.zoom_with_wheel)
        {
            super.mouse_wheel_moved(p_rotation);
        }
        else
        {
            this.rotate(-p_rotation);
        }
        return this;
    }
    
    /**
     * Changes the position of the items in the list to p_new_location.
     */
    private void move(FloatPoint p_new_position)
    {
        current_position = p_new_position.round();
        if (!current_position.equals(previous_position))
        {
            Vector translate_vector = current_position.difference_by(previous_position);
            if (this.grid_snap_component != null)
            {
                translate_vector = adjust_to_placement_grid(translate_vector);
            }
            board.Components components = hdlg.get_routing_board().components;
            for (Component curr_component : this.component_list)
            {
                components.move(curr_component.no, translate_vector);
            }
            this.clearance_violations = new java.util.LinkedList<ClearanceViolation>();
            for (Item curr_item : this.item_list)
            {
                curr_item.translate_by(translate_vector);
                this.clearance_violations.addAll(curr_item.clearance_violations());
            }
            previous_position = current_position;
            for (NetItems curr_net_items : this.net_items_list)
            {
                this.hdlg.update_ratsnest(curr_net_items.net_no, curr_net_items.items);
            }
            hdlg.repaint();
        }
    }
    
    private Vector adjust_to_placement_grid(Vector p_vector)
    {
        Point new_component_location = this.grid_snap_component.get_location().translate_by(p_vector);
        IntPoint rounded_component_location =
                new_component_location.to_float().round_to_grid(hdlg.settings.horizontal_component_grid,
                hdlg.settings.vertical_component_grid);
        Vector adjustment = rounded_component_location.difference_by(new_component_location);
        Vector result = p_vector.add(adjustment);
        this.current_position = this.previous_position.translate_by(result).to_float().round();
        return p_vector.add(adjustment);
    }
    
    
    /**
     *   Turns the items in the list by p_factor times 90 degree around the current position.
     */
    public void turn_90_degree(int p_factor)
    {
        if (p_factor == 0)
        {
            return;
        }
        board.Components components = hdlg.get_routing_board().components;
        for (Component curr_component : this.component_list)
        {
            components.turn_90_degree(curr_component.no, p_factor, current_position);
        }
        this.clearance_violations = new java.util.LinkedList<ClearanceViolation>();
        for (Item curr_item : this.item_list)
        {
            curr_item.turn_90_degree(p_factor,  current_position);
            this.clearance_violations.addAll(curr_item.clearance_violations());
        }
        for (NetItems curr_net_items : this.net_items_list)
        {
            this.hdlg.update_ratsnest(curr_net_items.net_no, curr_net_items.items);
        }
        if (logfile != null)
        {
            logfile.start_scope(LogfileScope.TURN_90_DEGREE, p_factor);
        }
        hdlg.repaint();
    }
    
    
    public void rotate(double p_angle_in_degree)
    {
        if (p_angle_in_degree == 0)
        {
            return;
        }
        board.Components components = hdlg.get_routing_board().components;
        for (Component curr_component : this.component_list)
        {
            components.rotate(curr_component.no, p_angle_in_degree,  this.current_position);
        }
        this.clearance_violations = new java.util.LinkedList<ClearanceViolation>();
        FloatPoint float_position = this.current_position.to_float();
        for (Item curr_item : this.item_list)
        {
            curr_item.rotate_approx(p_angle_in_degree,  float_position);
            this.clearance_violations.addAll(curr_item.clearance_violations());
        }
        for (NetItems curr_net_items : this.net_items_list)
        {
            this.hdlg.update_ratsnest(curr_net_items.net_no, curr_net_items.items);
        }
        if (logfile != null)
        {
            logfile.start_scope(LogfileScope.ROTATE, (int) p_angle_in_degree);
        }
        hdlg.repaint();
    }
    
    
    /**
     *   Turns the items in the list by p_factor times 90 degree around the current position.
     */
    public void turn_45_degree(int p_factor)
    {
        if (p_factor % 2 == 0)
        {
            turn_90_degree(p_factor / 2);
        }
        else
        {
            rotate(p_factor * 45);
        }
    }
    
    
    /**
     * Changes the placement side of the items in the list.
     */
    public void change_placement_side()
    {
        // Check, that all items can be mirrored
        LayerStructure layer_structure = hdlg.get_routing_board().layer_structure;
        BoardLibrary board_library = hdlg.get_routing_board().library;
        boolean placement_side_changable = true;
        for(Item curr_item : item_list)
        {
            if (curr_item instanceof Via)
            {
                if (board_library.get_mirrored_via_padstack(((Via)curr_item).get_padstack()) == null)
                {
                    placement_side_changable = false;
                    break;
                }
            }
            else if (curr_item.first_layer() == curr_item.last_layer())
            {
                int new_layer_no = hdlg.get_layer_count() - curr_item.first_layer() - 1;
                if (!layer_structure.arr[new_layer_no].is_signal)
                {
                    placement_side_changable = false;
                    break;
                }
            }
            
            
        }
        if (!placement_side_changable)
        {
            hdlg.screen_messages.set_status_message(resources.getString("cannot_change_placement_side"));
            return;
        }
        
        board.Components components = hdlg.get_routing_board().components;
        for (Component curr_component : this.component_list)
        {
            components.change_side(curr_component.no, current_position);
        }
        this.clearance_violations = new java.util.LinkedList<ClearanceViolation>();
        for (Item curr_item : this.item_list)
        {
            curr_item.change_placement_side(current_position);
            this.clearance_violations.addAll(curr_item.clearance_violations());
        }
        for (NetItems curr_net_items : this.net_items_list)
        {
            this.hdlg.update_ratsnest(curr_net_items.net_no, curr_net_items.items);
        }
        if (logfile != null)
        {
            logfile.start_scope(LogfileScope.CHANGE_PLACEMENT_SIDE);
        }
        hdlg.repaint();
    }
    
    
    
    public void reset_rotation()
    {
        Component component_to_reset = null;
        for (Component curr_component : this.component_list)
        {
            if (component_to_reset == null)
            {
                component_to_reset = curr_component;
            }
            else if (component_to_reset.get_rotation_in_degree() != curr_component.get_rotation_in_degree())
            {
                hdlg.screen_messages.set_status_message(resources.getString("unable_to_reset_components_with_different_rotations"));
                return;
            }
        }
        if (component_to_reset == null)
        {
            return;
        }
        double rotation = component_to_reset.get_rotation_in_degree();
        if (!hdlg.get_routing_board().components.get_flip_style_rotate_first() || component_to_reset.placed_on_front())
        {
            rotation = 360 - rotation;
        }
        rotate(rotation);
    }
    
    
    /**
     * Action to be taken when a key is pressed (Shortcut).
     */
    public InteractiveState key_typed(char p_key_char)
    {
        InteractiveState curr_return_state = this;
        if (p_key_char == '+')
        {
            turn_90_degree(1);
        }
        else if (p_key_char == '*')
        {
            turn_90_degree(2);
        }
        else if (p_key_char == '-')
        {
            turn_90_degree(3);
        }
        else if (p_key_char == '/')
        {
            change_placement_side();
        }
        else if (p_key_char == 'r')
        {
            hdlg.settings.set_zoom_with_wheel(false);
        }
        else if (p_key_char == 'z')
        {
            hdlg.settings.set_zoom_with_wheel(true);
        }
        else
        {
            curr_return_state = super.key_typed(p_key_char);
        }
        return curr_return_state;
    }
    
    public javax.swing.JPopupMenu get_popup_menu()
    {
        return hdlg.get_panel().popup_menu_move;
    }
    
    public String get_help_id()
    {
        return "MoveItemState";
    }
    
    public void draw(java.awt.Graphics p_graphics)
    {
        if (this.item_list == null)
        {
            return;
        }
        for (Item curr_item : this.item_list)
        {
            curr_item.draw(p_graphics, hdlg.graphics_context);
        }
        if (this.clearance_violations != null)
        {
            java.awt.Color draw_color = hdlg.graphics_context.get_violations_color();
            for (ClearanceViolation curr_violation : this.clearance_violations)
            {
                hdlg.graphics_context.fill_area(curr_violation.shape, p_graphics, draw_color, 1);
            }
        }
    }
    
    private final Set<Item> item_list;
    private final Set<Component> component_list;
    
    /**
     *  In case of a component grid the first component is aligned to this grid.
     */
    private final Component grid_snap_component;
    
    private IntPoint current_position;
    private IntPoint previous_position;
    
    private Collection<ClearanceViolation> clearance_violations;
    
    private final Collection<NetItems> net_items_list;
    
    private boolean observers_activated = false;
    
    private static class NetItems
    {
        NetItems(int p_net_no, Collection<Item> p_items)
        {
            net_no = p_net_no;
            items = p_items;
        }
        final int net_no;
        final Collection<Item> items;
    }
}
