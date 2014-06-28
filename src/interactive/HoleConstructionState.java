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
 * HoleConstructionState.java
 *
 * Created on 7. November 2003, 18:40
 */

package interactive;

import geometry.planar.Area;
import geometry.planar.Circle;
import geometry.planar.FloatPoint;
import geometry.planar.IntPoint;
import geometry.planar.PolygonShape;
import geometry.planar.PolylineArea;
import geometry.planar.PolylineShape;
import geometry.planar.Shape;

import java.util.Iterator;

import board.ObstacleArea;
import board.ItemSelectionFilter;

/**
 * Interactive cutting a hole into an obstacle shape
 *
 * @author  Alfons Wirtz
 */
public class HoleConstructionState extends CornerItemConstructionState
{
    /**
     * Returns a new instance of this class or null,
     * if that was not possible with the input parameters.
     * If p_logfile != null, the construction of this hole is stored in a logfile.
     */
    public static HoleConstructionState get_instance(FloatPoint p_location, InteractiveState p_parent_state, BoardHandling p_board_handling, Logfile p_logfile)
    {
        HoleConstructionState new_instance = new HoleConstructionState(p_parent_state, p_board_handling, p_logfile);
        if (!new_instance.start_ok(p_location))
        {
            new_instance = null;
        }
        return  new_instance;
    }
    
    /** Creates a new instance of HoleConstructionState */
    private  HoleConstructionState(InteractiveState p_parent_state, BoardHandling p_board_handling, Logfile p_logfile)
    {
        super(p_parent_state, p_board_handling, p_logfile);
    }
    
    /**
     * Looks for an obstacle area to modify
     * Returns false, if it cannot find one.
     */
    private boolean start_ok(FloatPoint p_location)
    {
        IntPoint pick_location = p_location.round();
        ItemSelectionFilter.SelectableChoices[]  selectable_choices =
        { ItemSelectionFilter.SelectableChoices.KEEPOUT,
          ItemSelectionFilter.SelectableChoices.VIA_KEEPOUT,
          ItemSelectionFilter.SelectableChoices.CONDUCTION
        };
        ItemSelectionFilter selection_filter = new ItemSelectionFilter(selectable_choices);
        java.util.Collection<board.Item> found_items = hdlg.get_routing_board().pick_items(pick_location,
                hdlg.settings.layer, selection_filter);
        if (found_items.size() != 1)
        {
            hdlg.screen_messages.set_status_message(resources.getString("no_item_found_for_adding_hole"));
            return false;
        }
        board.Item found_item = found_items.iterator().next();
        if (!(found_item instanceof ObstacleArea))
        {
            hdlg.screen_messages.set_status_message(resources.getString("no_obstacle_area_found_for_adding_hole"));
            return false;
        }
        this.item_to_modify = (ObstacleArea)found_item;
        if (item_to_modify.get_area() instanceof Circle)
        {
            hdlg.screen_messages.set_status_message(resources.getString("adding_hole_to_circle_not_yet_implemented"));
            return false;
        }
        if (this.logfile != null)
        {
            logfile.start_scope(LogfileScope.ADDING_HOLE);
        }
        this.add_corner(p_location);
        return true;
    }
    
    /**
     * Adds a corner to the polygon of the the hole under construction.
     */
    public InteractiveState left_button_clicked(FloatPoint p_next_corner)
    {
        if (item_to_modify == null)
        {
            return this.return_state;
        }
        if(item_to_modify.get_area().contains(p_next_corner))
        {
            super.add_corner(p_next_corner);
            hdlg.repaint();
        }
        return this;
    }
    
    /**
     * adds the just constructed hole to the item under modification,
     * if that is possible without clearance violations
     */
    public InteractiveState complete()
    {
        if (item_to_modify == null)
        {
            return this.return_state;
        }
        add_corner_for_snap_angle();
        int corner_count = corner_list.size();
        boolean construction_succeeded = (corner_count > 2);
        PolylineShape [] new_holes = null;
        PolylineShape new_border = null;
        if (construction_succeeded)
        {
            Area obs_area = item_to_modify.get_area();
            Shape[] old_holes = obs_area.get_holes();
            new_border = (PolylineShape)obs_area.get_border();
            if (new_border == null)
            {
                construction_succeeded = false;
            }
            else
            {
                new_holes = new PolylineShape[old_holes.length + 1];
                for (int i = 0; i < old_holes.length; ++i)
                {
                    new_holes[i] = (PolylineShape)old_holes[i];
                    if (new_holes[i] == null)
                    {
                        construction_succeeded = false;
                        break;
                    }
                }
            }
        }
        if (construction_succeeded)
        {
            IntPoint [] new_hole_corners = new IntPoint[corner_count];
            Iterator<IntPoint> it = corner_list.iterator();
            for (int i = 0; i < corner_count ; ++i)
            {
                new_hole_corners[i] = it.next();
            }
            new_holes[new_holes.length - 1] = new PolygonShape(new_hole_corners);
            PolylineArea new_obs_area = new PolylineArea(new_border, new_holes);
            
            if (new_obs_area.split_to_convex() == null)
            {
                // shape is invalid, maybe it has selfintersections
                construction_succeeded = false;
            }
            else
            {
                this.observers_activated = !hdlg.get_routing_board().observers_active();
                if (this.observers_activated)
                {
                    hdlg.get_routing_board().start_notify_observers();
                }
                hdlg.get_routing_board().generate_snapshot();
                hdlg.get_routing_board().remove_item( item_to_modify);
                hdlg.get_routing_board().insert_obstacle(new_obs_area, item_to_modify.get_layer(),
                        item_to_modify.clearance_class_no(), board.FixedState.UNFIXED);
                if (this.observers_activated)
                {
                    hdlg.get_routing_board().end_notify_observers();
                    this.observers_activated = false;
                }
            }
        }
        if (construction_succeeded)
        {
            hdlg.screen_messages.set_status_message(resources.getString("adding_hole_completed"));
        }
        else
        {
            hdlg.screen_messages.set_status_message(resources.getString("adding_hole_failed"));
        }
        if (logfile != null)
        {
            logfile.start_scope(LogfileScope.COMPLETE_SCOPE);
        }
        return this.return_state;
    }
    
    public void display_default_message()
    {
        hdlg.screen_messages.set_status_message(resources.getString("adding_hole_to_obstacle_area"));
    }
    
    private ObstacleArea item_to_modify = null;
}
