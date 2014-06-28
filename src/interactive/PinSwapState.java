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
 * PinSwapState.java
 *
 * Created on 28. Maerz 2005, 09:25
 */

package interactive;

import geometry.planar.FloatPoint;

import board.Pin;
import board.Item;
import board.ItemSelectionFilter;

/**
 *
 * @author Alfons Wirtz
 */
public class PinSwapState extends InteractiveState
{
    public static InteractiveState get_instance(Pin p_pin_to_swap, InteractiveState p_return_state, BoardHandling p_board_handling, Logfile p_logfile)
    {
        PinSwapState new_state = new PinSwapState(p_pin_to_swap, p_return_state, p_board_handling, p_logfile);
        if (new_state.swappable_pins.isEmpty())
        {
            new_state.hdlg.screen_messages.set_status_message(new_state.resources.getString("no_swappable_pin_found"));
            return p_return_state;
        }
        new_state.hdlg.screen_messages.set_status_message(new_state.resources.getString("please_click_second_pin_with_the_left_mouse_button"));
        return new_state;
    }
    /** Creates a new instance of PinSwapState */
    private PinSwapState(Pin p_pin_to_swap, InteractiveState p_return_state, BoardHandling p_board_handling, Logfile p_logfile)
    {
        super(p_return_state, p_board_handling, p_logfile);
        this.from_pin = p_pin_to_swap;
        this.swappable_pins = p_pin_to_swap.get_swappable_pins();
    }
    
    
    public InteractiveState left_button_clicked(FloatPoint p_location)
    {
        ItemSelectionFilter selection_filter = new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.PINS);
        java.util.Collection<Item> picked_items = hdlg.pick_items(p_location, selection_filter);
        if (picked_items.isEmpty())
        {
            this.hdlg.screen_messages.set_status_message(resources.getString("no_pin_selected"));
            return this.cancel();
        }
        Item to_item = picked_items.iterator().next();
        if (!(to_item instanceof Pin))
        {
            hdlg.screen_messages.set_status_message(resources.getString("picked_pin_expected"));
            return this.cancel();
        }
        
        this.to_pin = (Pin) to_item;
        if (!swappable_pins.contains(this.to_pin))
        {
            return cancel();
        }
        return complete();
    }
    
    public InteractiveState complete()
    {
        if (this.from_pin == null || this.to_pin == null)
        {
            hdlg.screen_messages.set_status_message(resources.getString("pin_to_swap_missing"));
            return this.cancel();
        }
        if (this.from_pin.net_count() > 1 || this.to_pin.net_count() > 1)
        {
            System.out.println("PinSwapState.complete: pin swap not yet implemented for pins belonging to more than 1 net ");
            return this.cancel();
        }
        int from_net_no;
        if (this.from_pin.net_count() > 0)
        {
            from_net_no =  this.from_pin.get_net_no(0);
        }
        else
        {
            from_net_no = -1;
        }
        int to_net_no;
        if (this.to_pin.net_count() > 0)
        {
            to_net_no =  this.to_pin.get_net_no(0);
        }
        else
        {
            to_net_no = -1;
        }
        if (!hdlg.get_routing_board().check_change_net(this.from_pin, to_net_no))
        {
            hdlg.screen_messages.set_status_message(resources.getString("pin_not_swapped_because_it_is_already_connected"));
            return this.cancel();
        }
        if (!hdlg.get_routing_board().check_change_net(this.to_pin, from_net_no))
        {
            hdlg.screen_messages.set_status_message(resources.getString("pin_not_swapped_because_second_pin_is_already_connected"));
            return this.cancel();
        }
        hdlg.get_routing_board().generate_snapshot();
        this.from_pin.swap(this.to_pin);
        for (int i = 0; i < this.from_pin.net_count(); ++i)
        {
            hdlg.update_ratsnest(this.from_pin.get_net_no(i));
        }
        for (int i = 0; i < this.to_pin.net_count(); ++i)
        {
            hdlg.update_ratsnest(this.to_pin.get_net_no(i));
        }
        hdlg.screen_messages.set_status_message(resources.getString("pin_swap_completed"));
        return this.return_state;
    }
    
    public void draw(java.awt.Graphics p_graphics)
    {
        java.awt.Color highlight_color = hdlg.graphics_context.get_hilight_color();
        double highligt_color_intensity = hdlg.graphics_context.get_hilight_color_intensity();
        from_pin.draw(p_graphics, hdlg.graphics_context, highlight_color, 0.5 * highligt_color_intensity);
        for (Pin curr_pin: swappable_pins)
        {
            curr_pin.draw(p_graphics, hdlg.graphics_context, highlight_color, highligt_color_intensity);
        }
    }
    
    private final Pin from_pin;
    private Pin to_pin = null;
    private java.util.Set<Pin> swappable_pins;
}
