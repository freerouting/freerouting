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
 * MenuState.java
 *
 * Created on 28. November 2003, 10:04
 */

package interactive;

import geometry.planar.FloatPoint;

import java.util.Collection;

import board.Item;
import board.ItemSelectionFilter;
import board.TestLevel;

/**
 * Common base class for the main menus, which can be selected in the tool bar.
 *
 * @author  Alfons Wirtz
 */
public class MenuState extends InteractiveState
{
    
    /** Creates a new instance of MenuState */
    MenuState(BoardHandling p_board_handle, Logfile p_logfile)
    {
        super(null, p_board_handle, p_logfile);
        this.return_state = this;
    }
    
    public javax.swing.JPopupMenu get_popup_menu()
    {
        return hdlg.get_panel().popup_menu_main;
    }
    
    /**
     * Selects items at p_location.
     * Returns a new instance of SelectedItemState with the selected items,
     * if somthing was selected.
     */
    public InteractiveState select_items(FloatPoint p_location)
    {
        this.hdlg.display_layer_messsage();
        java.util.Set<Item> picked_items = hdlg.pick_items(p_location);
        boolean something_found = (picked_items.size() > 0);
        InteractiveState result;
        if (something_found)
        {
            result = SelectedItemState.get_instance(picked_items, this, hdlg, this.logfile);
            hdlg.screen_messages.set_status_message(resources.getString("in_select_mode"));
            if (logfile != null)
            {
                logfile.start_scope(LogfileScope.START_SELECT, p_location);
            }
        }
        else
        {
            result = this;
        }
        hdlg.repaint();
        return result;
    }
    
    public InteractiveState swap_pin(FloatPoint p_location)
    {
        ItemSelectionFilter selection_filter = new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.PINS);
        Collection<Item> picked_items = hdlg.pick_items(p_location, selection_filter);
        InteractiveState result = this;
        if (picked_items.size() > 0)
        {
            Item first_item = picked_items.iterator().next();
            if (!(first_item instanceof board.Pin))
            {
                System.out.println("MenuState.swap_pin: Pin expected");
                return this;
            }
            board.Pin selected_pin = (board.Pin) first_item;
            result = PinSwapState.get_instance(selected_pin, this, hdlg, this.logfile);
        }
        else
        {
            hdlg.screen_messages.set_status_message(resources.getString("no_pin_selected"));
        }
        hdlg.repaint();
        return result;
    }
    
    /**
     * Action to be taken when a key shortcut is pressed.
     */
    public InteractiveState key_typed(char p_key_char)
    {
        InteractiveState curr_return_state = this;
        if (p_key_char == 'b')
        {
            hdlg.redo();
        }
        else if (p_key_char == 'd')
        {
            curr_return_state = DragMenuState.get_instance(hdlg, logfile);
        }
        else if (p_key_char == 'e')
        {
            if (hdlg.get_routing_board().get_test_level() != TestLevel.RELEASE_VERSION)
            {
                curr_return_state = ExpandTestState.get_instance(hdlg.get_current_mouse_position(), this, hdlg);
            }
        }
        else if (p_key_char == 'g')
        {
            hdlg.toggle_ratsnest();
        }
        else if (p_key_char == 'i')
        {
            curr_return_state = this.select_items(hdlg.get_current_mouse_position());
        }
        else if (p_key_char == 'p')
        {
            hdlg.settings.set_push_enabled(!hdlg.settings.push_enabled);
            hdlg.get_panel().board_frame.refresh_windows();
        }
        else if (p_key_char == 'r')
        {
            curr_return_state = RouteMenuState.get_instance(hdlg, logfile);
        }
        else if (p_key_char == 's')
        {
            curr_return_state = SelectMenuState.get_instance(hdlg, logfile);
        }
        else if (p_key_char == 't')
        {
            curr_return_state = RouteState.get_instance(hdlg.get_current_mouse_position(), this, hdlg, logfile);
        }
        else if (p_key_char == 'u')
        {
            hdlg.undo();
        }
        else if (p_key_char == 'v')
        {
            hdlg.toggle_clearance_violations();
        }
        else if (p_key_char == 'w')
        {
            curr_return_state = swap_pin(hdlg.get_current_mouse_position());
        }
        else if (p_key_char == '+')
        {
            // increase the current layer to the next signal layer
            board.LayerStructure layer_structure = hdlg.get_routing_board().layer_structure;
            int current_layer_no = hdlg.settings.layer;
            for(;;)
            {
                ++current_layer_no;
                if (current_layer_no >=  layer_structure.arr.length || layer_structure.arr[current_layer_no].is_signal)
                {
                    break;
                }
            }
            if (current_layer_no < layer_structure.arr.length)
            {
                hdlg.set_current_layer(current_layer_no);
            }
        }
        else if (p_key_char == '-')
        {
            // decrease the current layer to the previous signal layer
            board.LayerStructure layer_structure = hdlg.get_routing_board().layer_structure;
            int current_layer_no = hdlg.settings.layer;
            for(;;)
            {
                --current_layer_no;
                if (current_layer_no < 0 || layer_structure.arr[current_layer_no].is_signal)
                {
                    break;
                }
            }
            if (current_layer_no >= 0)
            {
                hdlg.set_current_layer(current_layer_no);
            }
            
        }
        else
        {
            curr_return_state = super.key_typed(p_key_char);
        }
        return curr_return_state;
    }
    
    /**
     * Do nothing on complete.
     */
    public InteractiveState complete()
    {
        return this;
    }
    
    /**
     * Do nothing on cancel.
     */
    public InteractiveState cancel()
    {
        return this;
    }
    
    public void set_toolbar()
    {
        hdlg.get_panel().board_frame.set_menu_toolbar();
    }
}
