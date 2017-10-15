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
 * MazeSearchElement.java
 *
 * Created on 31. Januar 2004, 07:46
 */

package autoroute;

/**
 * Describes the structure of a section of an ExpandebleObject.
 *
 * @author  Alfons Wirtz
 */
public class MazeSearchElement
{
    /**
     * Resets this MazeSearchElement for autorouting the next connection.
     */
    public void reset()
    {
        is_occupied = false;
        backtrack_door = null;
        section_no_of_backtrack_door = 0;
        room_ripped = false;
        adjustment = Adjustment.NONE;
    }
    
    /** true, if this door is already occupied by the maze expanding algorithm */
    public boolean is_occupied = false;
    
    /** Used for backtracking in the maze expanding algorithm */
    public ExpandableObject backtrack_door = null;
    
    public int section_no_of_backtrack_door = 0;
    
    public boolean room_ripped = false;
    
    public Adjustment adjustment = Adjustment.NONE;
    
    public enum Adjustment
    { NONE, RIGHT, LEFT}
}

