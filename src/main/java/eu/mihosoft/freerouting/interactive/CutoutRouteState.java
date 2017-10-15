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
 * CutoutRouteState.java
 *
 * Created on 5. Juni 2005, 07:13
 *
 */

package interactive;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;

import geometry.planar.FloatPoint;
import geometry.planar.IntPoint;
import geometry.planar.IntBox;

import board.Item;
import board.PolylineTrace;

/**
 *
 * @author Alfons Wirtz
 */
public class CutoutRouteState extends SelectRegionState
{
    
    /**
     * Returns a new instance of this class.
     */
    public static CutoutRouteState get_instance( Collection<Item> p_item_list, InteractiveState p_parent_state,
            BoardHandling p_board_handling, Logfile p_logfile)
    {
        return get_instance(p_item_list,  null, p_parent_state, p_board_handling, p_logfile);
    }
    
    /**
     * Returns a new instance of this class.
     */
    public static CutoutRouteState get_instance( Collection<Item> p_item_list, FloatPoint p_location, InteractiveState p_parent_state,
            BoardHandling p_board_handling, Logfile p_logfile)
    {
        p_board_handling.display_layer_messsage();
        // filter items, whichh cannnot be cutout
        Collection<PolylineTrace> item_list = new LinkedList<PolylineTrace>();
        
        for (Item curr_item : p_item_list)
        {
            if (!curr_item.is_user_fixed() && curr_item instanceof PolylineTrace)
            {
                item_list.add((PolylineTrace) curr_item);
            }
        }
        
        CutoutRouteState new_instance =  new CutoutRouteState(item_list, p_parent_state, p_board_handling, p_logfile);
        new_instance.corner1 = p_location;
        if (p_location != null && new_instance.logfile != null)
        {
            new_instance.logfile.add_corner(p_location);
        }
        new_instance.hdlg.screen_messages.set_status_message(new_instance.resources.getString("drag_left_mouse_button_to_select_cutout_rectangle"));
        return new_instance;
    }
    
    /** Creates a new instance of CutoutRouteState */
    private CutoutRouteState(Collection<PolylineTrace> p_item_list, InteractiveState p_parent_state, BoardHandling p_board_handling, Logfile p_logfile)
    {
        super(p_parent_state, p_board_handling, p_logfile);
        if (logfile != null)
        {
            logfile.start_scope(LogfileScope.CUTOUT_ROUTE);
        }
        this.trace_list = p_item_list;
    }
    
    public InteractiveState complete()
    {
        hdlg.screen_messages.set_status_message("");
        corner2 = hdlg.get_current_mouse_position();
        if (logfile != null)
        {
            logfile.add_corner(corner2);
        }
        this.cutout_route();
        return this.return_state;
    }
    
    /**
     * Selects all items in the rectangle defined by corner1 and corner2.
     */
    private void cutout_route()
    {
        if (this.corner1 == null || this.corner2 == null)
        {
            return;
        }
        
        hdlg.get_routing_board().generate_snapshot();
        
        IntPoint p1 = this.corner1.round() ;
        IntPoint p2 = this.corner2.round() ;
        
        IntBox cut_box = new IntBox(Math.min(p1.x, p2.x), Math.min(p1.y, p2.y), Math.max(p1.x, p2.x), Math.max(p1.y, p2.y)) ;
        
        Set<Integer> changed_nets = new TreeSet<Integer>();
        
        for (PolylineTrace curr_trace : this.trace_list)
        {
            board.ShapeTraceEntries.cutout_trace(curr_trace, cut_box, 0);
            for (int i = 0; i < curr_trace.net_count(); ++i)
            {
                changed_nets.add(curr_trace.get_net_no(i));
            }
        }
        
        for (Integer changed_net : changed_nets)
        {
            hdlg.update_ratsnest(changed_net);
        }
    }
    
    public void draw(java.awt.Graphics p_graphics)
    {
        if (trace_list == null)
        {
            return;
        }
        
        for (PolylineTrace curr_trace : this.trace_list)
        {
            
            curr_trace.draw(p_graphics, hdlg.graphics_context, hdlg.graphics_context.get_hilight_color(),
                    hdlg.graphics_context.get_hilight_color_intensity());
        }
        super.draw(p_graphics);
    }
    
    private final Collection<PolylineTrace> trace_list;
}
