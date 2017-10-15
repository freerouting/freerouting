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
 * MazeListElement.java
 *
 * Created on 25. Januar 2004, 08:21
 */

package autoroute;

import geometry.planar.FloatLine;

/**
 * Information for the maze expand Algorithm contained in expansion doors and drills
 * while the maze expanding algorithm is in progress.
 *
 * @author  Alfons Wirtz
 */
public class MazeListElement implements Comparable<MazeListElement>
{
    
    /** Creates a new instance of ExpansionInfo */
    public MazeListElement(ExpandableObject p_door, int p_section_no_of_door,
            ExpandableObject p_backtrack_door, int p_section_no_of_backtrack_door,
            double p_expansion_value, double p_sorting_value,
            CompleteExpansionRoom p_next_room, FloatLine p_shape_entry,
            boolean p_room_ripped, MazeSearchElement.Adjustment p_adjustment, boolean p_already_checked)
    {
        door = p_door;
        section_no_of_door = p_section_no_of_door;
        backtrack_door = p_backtrack_door;
        section_no_of_backtrack_door = p_section_no_of_backtrack_door;
        expansion_value = p_expansion_value;
        sorting_value = p_sorting_value;
        next_room = p_next_room;
        shape_entry = p_shape_entry;
        room_ripped = p_room_ripped;
        adjustment = p_adjustment;
        already_checked = p_already_checked;
    }
    
    public int compareTo(MazeListElement p_other)
    {
        double compare_value = (this.sorting_value - p_other.sorting_value);
        // make shure, that the result cannot be 0, so that no element in the set is
        // skipped because of equal size.
        int result;
        if (compare_value >= 0)
        {
            result = 1;
        }
        else
        {
            result = -1;
        }
        return result;
    }
    
    /** The door or drill belonging to this MazeListElement */
    final ExpandableObject door;
    
    /** The section number of the door (or the layer of the drill) */
    final int section_no_of_door;
    
    /** The door, from which this door was expanded */
    final ExpandableObject backtrack_door;
    
    /** The section number of the backtrack door */
    final int section_no_of_backtrack_door;
    
    /** The wheighted distance to the start of the expansion */
    final double expansion_value;
    
    /**
     * The expansion value plus the shortest distance to a destination.
     * The list is sorted in ascending order by this value.
     */
    final double sorting_value;
    
    /** The the next room, which will be expanded from this maze search element */
    final CompleteExpansionRoom next_room;
    
    /**
     * Point of the region of the expansion door,
     * which has the shortest distance to the backtrack door.
     */
    final FloatLine shape_entry;
    
    final boolean room_ripped;
    
    final MazeSearchElement.Adjustment adjustment;
    
    final boolean already_checked;
}
