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
 * DragItemState.java
 *
 * Created on 9. November 2003, 08:13
 */

package interactive;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.Iterator;

import geometry.planar.FloatPoint;
import geometry.planar.IntPoint;
import geometry.planar.Vector;
import board.AngleRestriction;
import board.Item;
import board.MoveComponent;

/**
 * Class for interactive dragging items with the mouse on a routing board
 *
 * @author Alfons Wirtz
 */
public class DragItemState extends DragState
{
    
    /** Creates a new instance of MoveItemState */
    protected DragItemState(Item p_item_to_move, FloatPoint p_location, InteractiveState p_parent_state, BoardHandling p_board_handling, Logfile p_logfile)
    {
        super(p_location, p_parent_state, p_board_handling, p_logfile);
        item_to_move = p_item_to_move;
    }
    
    
    public void display_default_message()
    {
        hdlg.screen_messages.set_status_message(resources.getString("dragging_item"));
    }
    
    /**
     * Moves the items of the group to p_to_location.
     * Return this.return_state, if an error eccured  while moving,
     * so that an undo may be necessary.
     */
    public InteractiveState move_to(FloatPoint p_to_location)
    {
        IntPoint to_location = p_to_location.round();
        IntPoint from_location = this.previous_location.round();
        if (hdlg.get_routing_board().rules.get_trace_angle_restriction() == AngleRestriction.NINETY_DEGREE)
        {
            to_location = to_location.orthogonal_projection(from_location);
        }
        else if (hdlg.get_routing_board().rules.get_trace_angle_restriction() == AngleRestriction.FORTYFIVE_DEGREE)
        {
            to_location = to_location.fortyfive_degree_projection(from_location);
        }
        if (to_location.equals(from_location))
        {
            return this;
        }
        if (item_to_move.is_user_fixed())
        {
            hdlg.screen_messages.set_status_message("Please unfix item before dragging");
            return this;
        }
        MoveComponent move_component = null;
        Vector rel_coor = to_location.difference_by(from_location);
        double length = rel_coor.length_approx();
        boolean shove_ok = false;
        for (int i = 0; i < 2; ++i)
        {
            move_component = new MoveComponent(item_to_move, rel_coor, 99, 5);
            if (move_component.check())
            {
                shove_ok = true;
                break;
            }
            if (i == 0)
            {
                // reduce evtl. the shove distance to make the check shove function
                // work properly, if more than 1 trace have to be shoved.
                double sample_width = 2 * hdlg.get_routing_board().get_min_trace_half_width();
                if (length > sample_width)
                {
                    rel_coor = rel_coor.change_length_approx(sample_width);
                }
            }
        }
        
        if (shove_ok)
        {
            if (!this.something_dragged)
            {
                // initialisitions for the first time dragging
                this.observers_activated = !hdlg.get_routing_board().observers_active();
                if (this.observers_activated)
                {
                    hdlg.get_routing_board().start_notify_observers();
                }
                // make the situation restorable by undo
                hdlg.get_routing_board().generate_snapshot();
                if (logfile != null)
                {
                    // Delayed till here because otherwise the mouse
                    // might have been only clicked for selecting
                    // and not pressed for moving.
                    logfile.start_scope(LogfileScope.DRAGGING_ITEMS, this.previous_location);
                }
                this.something_dragged = true;
            }
            if (!move_component.insert(hdlg.settings.trace_pull_tight_region_width,
                    hdlg.settings.trace_pull_tight_accuracy))
            {
                // an insert error occured, end the drag state
                return this.return_state;
            }
            hdlg.repaint();
        }
        this.previous_location = p_to_location;//(IntPoint)this.curr_location.translate_by(rel_coor);
        return this;
    }
    
    public InteractiveState button_released()
    {
        if (this.observers_activated)
        {
            hdlg.get_routing_board().end_notify_observers();
            this.observers_activated = false;
        }
        if (logfile != null && something_dragged)
        {
            logfile.start_scope(LogfileScope.COMPLETE_SCOPE);
        }
        if (something_dragged)
        {
            // Update the incompletes for the nets of the moved items.
            if (item_to_move.get_component_no() == 0)
            {
                for (int i = 0; i < item_to_move.net_count(); ++i)
                {
                    hdlg.update_ratsnest(item_to_move.get_net_no(i));
                }
            }
            else
            {
                Collection<Item> moved_items =  hdlg.get_routing_board().get_component_items(item_to_move.get_component_no());
                Set<Integer> changed_nets = new TreeSet<Integer>();
                Iterator<Item> it = moved_items.iterator();
                while (it.hasNext())
                {
                    Item curr_moved_item = it.next();
                    for (int i = 0; i < curr_moved_item.net_count(); ++i)
                    {
                        changed_nets.add(new Integer(curr_moved_item.get_net_no(i)));
                    }
                }
                for (Integer curr_net_no : changed_nets)
                {
                    hdlg.update_ratsnest(curr_net_no.intValue());
                }
            }
        }
        else
        {
            hdlg.show_ratsnest();
        }
        hdlg.screen_messages.set_status_message("");
        return this.return_state;
    }
    
    private Item item_to_move = null;
}
