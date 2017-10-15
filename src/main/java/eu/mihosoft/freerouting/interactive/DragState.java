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
 * DragState.java
 *
 * Created on 10. Dezember 2003, 09:08
 */

package interactive;

import geometry.planar.FloatPoint;

import java.util.Iterator;

import board.Trace;
import board.DrillItem;
import board.Item;

/**
 * Class implementing functionality when the mouse is dragged on a routing board
 *
 * @author  Alfons Wirtz
 */
public abstract class DragState extends InteractiveState
{
    /**
     * Returns a new instance of this state, if a item to drag was found at the input
     * location; null otherwise.
     */
    public static DragState get_instance(FloatPoint p_location, InteractiveState p_parent_state,
            BoardHandling p_board_handling, Logfile p_logfile)
    {
        p_board_handling.display_layer_messsage();
        Item item_to_move = null;
        int try_count = 1;
        if (p_board_handling.settings.select_on_all_visible_layers)
        {
            try_count += p_board_handling.get_layer_count();
        }
        int curr_layer = p_board_handling.settings.layer;
        int pick_layer = curr_layer;
        boolean item_found = false;
        
        for (int i = 0; i < try_count; ++i)
        {
            if (i == 0 ||  pick_layer != curr_layer &&
                    (p_board_handling.graphics_context.get_layer_visibility(pick_layer)) > 0)
            {
                java.util.Collection<Item> found_items =
                        p_board_handling.get_routing_board().pick_items(p_location.round(),
                        pick_layer, p_board_handling.settings.item_selection_filter);
                Iterator<Item> it = found_items.iterator();
                while (it.hasNext())
                {
                    item_found = true;
                    Item curr_item = it.next();
                    if (curr_item instanceof Trace)
                    {
                        continue; // traces are not moved
                    }
                    if (!p_board_handling.settings.drag_components_enabled && curr_item.get_component_no() != 0)
                    {
                        continue;
                    }
                    item_to_move = curr_item;
                    if (curr_item instanceof DrillItem)
                    {
                        break; // drill items are preferred
                    }
                }
                if (item_to_move != null)
                {
                    break;
                }
            }
            // nothing found on settings.layer, try all visible layers
            pick_layer = i;
        }
        DragState result;
        if (item_to_move != null)
        {
            result = new DragItemState(item_to_move, p_location, p_parent_state, p_board_handling, p_logfile);
        }
        else if (!item_found)
        {
            result = new MakeSpaceState(p_location, p_parent_state, p_board_handling, p_logfile);
        }
        else
        {
            result = null;
        }
        if (result != null)
        {
            p_board_handling.hide_ratsnest();
        }
        return result;
    }
    
    /** Creates a new instance of DragState */
    protected DragState(FloatPoint p_location, InteractiveState p_parent_state, BoardHandling p_board_handling, Logfile p_logfile)
    {
        super(p_parent_state, p_board_handling, p_logfile);
        previous_location = p_location;
    }
    
    public abstract InteractiveState move_to(FloatPoint p_to_location);
    
    
    public InteractiveState mouse_dragged(FloatPoint p_point)
    {
        InteractiveState result = this.move_to(p_point);
        if (result != this)
        {
            // an error occured
            java.util.Set<Integer> changed_nets = new java.util.TreeSet<Integer>();
            hdlg.get_routing_board().undo(changed_nets);
            for (Integer changed_net : changed_nets)
            {
                hdlg.update_ratsnest(changed_net);
            }
        }
        if (this.something_dragged)
        {
            if (logfile != null )
            {
                logfile.add_corner(p_point);
            }
        }
        return result;
    }
    
    public InteractiveState complete()
    {
        return this.button_released();
    }
    
    
    public InteractiveState process_logfile_point(FloatPoint p_point)
    {
        return move_to(p_point);
    }
    
    
    protected FloatPoint previous_location;
    protected boolean something_dragged = false;
    protected boolean observers_activated = false;
}