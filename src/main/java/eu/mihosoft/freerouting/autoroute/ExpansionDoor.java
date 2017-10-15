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
 * ExpansionDoor.java
 *
 * Created on 6. Januar 2004, 07:23
 */
package autoroute;

import geometry.planar.TileShape;
import geometry.planar.FloatPoint;
import geometry.planar.FloatLine;
import geometry.planar.Point;

/**
 * An ExpansionDoor is a common edge between two ExpansionRooms
 *
 *
 * @author Alfons Wirtz
 */
public class ExpansionDoor implements ExpandableObject
{

    /** Creates a new instance of ExpansionDoor */
    public ExpansionDoor(ExpansionRoom p_first_room, ExpansionRoom p_second_room, int p_dimension)
    {
        first_room = p_first_room;
        second_room = p_second_room;
        dimension = p_dimension;
    }

    /** Creates a new instance of ExpansionDoor */
    public ExpansionDoor(ExpansionRoom p_first_room, ExpansionRoom p_second_room)
    {
        first_room = p_first_room;
        second_room = p_second_room;
        dimension = first_room.get_shape().intersection(second_room.get_shape()).dimension();
    }

    /**
     * Calculates the intersection of the shapes of the 2 rooms belonging to this door.
     */
    public TileShape get_shape()
    {
        TileShape first_shape = first_room.get_shape();
        TileShape second_shape = second_room.get_shape();
        return first_shape.intersection(second_shape);
    }

    /**
     * The dimension of a door may be 1 or 2.
     * 2-dimensional doors can only exist between ObstacleExpansionRooms
     */
    public int get_dimension()
    {
        return this.dimension;
    }

    /**
     * Returns the other room of this door, or null, if p_roon
     * is neither equal to this.first_room nor to this.second_room.
     */
    public ExpansionRoom other_room(ExpansionRoom p_room)
    {
        ExpansionRoom result;
        if (p_room == first_room)
        {
            result = second_room;
        }
        else if (p_room == second_room)
        {
            result = first_room;
        }
        else
        {
            result = null;
        }
        return result;
    }

    /**
     * Returns the other room of this door, or null, if p_roon
     * is neither equal to this.first_room nor to this.second_room,
     * or if the other room is not a CompleteExpansionRoom.
     */
    public CompleteExpansionRoom other_room(CompleteExpansionRoom p_room)
    {
        ExpansionRoom result;
        if (p_room == first_room)
        {
            result = second_room;
        }
        else if (p_room == second_room)
        {
            result = first_room;
        }
        else
        {
            result = null;
        }
        if (!(result instanceof CompleteExpansionRoom))
        {
            result = null;
        }
        return (CompleteExpansionRoom) result;
    }

    public int maze_search_element_count()
    {
        return this.section_arr.length;
    }

    public MazeSearchElement get_maze_search_element(int p_no)
    {
        return this.section_arr[p_no];
    }

    /**
     * Calculates the Line segments of the sections of this door.
     */
    public FloatLine[] get_section_segments(double p_offset)
    {
        double offset = p_offset + AutorouteEngine.TRACE_WIDTH_TOLERANCE;
        TileShape door_shape = this.get_shape();
        {
            if (door_shape.is_empty())
            {
                return new FloatLine[0];
            }
        }
        FloatLine door_line_segment;
        FloatLine shrinked_line_segment;
        if (this.dimension == 1)
        {
            door_line_segment = door_shape.diagonal_corner_segment();
            shrinked_line_segment = door_line_segment.shrink_segment(offset);
        }
        else if (this.dimension == 2 && this.first_room instanceof CompleteFreeSpaceExpansionRoom && this.second_room instanceof CompleteFreeSpaceExpansionRoom)
        {
            // Overlapping doors at a corner possible in case of 90- or 45-degree routing.
            // In case of freeangle routing the corners are cut off.
            door_line_segment = calc_door_line_segment(door_shape);
            if (door_line_segment == null)
            {
                // CompleteFreeSpaceExpansionRoom inside other room
                return new FloatLine[0];
            }
            if (door_line_segment.b.distance_square(door_line_segment.a) < 4 * offset * offset)
            {
                // door is small, 2 dimensional small doors are not yet expanded.
                return new FloatLine[0];
            }
            shrinked_line_segment = door_line_segment.shrink_segment(offset);
        }
        else
        {
            FloatPoint gravity_point = door_shape.centre_of_gravity();
            door_line_segment = new FloatLine(gravity_point, gravity_point);
            shrinked_line_segment = door_line_segment;
        }
        final double c_max_door_section_width = 10 * offset;
        int section_count = (int) (door_line_segment.b.distance(door_line_segment.a) / c_max_door_section_width) + 1;
        this.allocate_sections(section_count);
        FloatLine[] result = shrinked_line_segment.divide_segment_into_sections(section_count);
        return result;
    }

    /**
     * Calculates a diagonal line of the 2-dimensional p_door_shape which represents the restraint line
     * between the shapes of this.first_room and this.second_room.
     */
    private FloatLine calc_door_line_segment(TileShape p_door_shape)
    {
        TileShape first_room_shape = this.first_room.get_shape();
        TileShape second_room_shape = this.second_room.get_shape();
        Point first_corner = null;
        Point second_corner = null;
        int corner_count = p_door_shape.border_line_count();
        for (int i = 0; i < corner_count; ++i)
        {
            Point curr_corner = p_door_shape.corner(i);
            if (!first_room_shape.contains_inside(curr_corner) && !second_room_shape.contains_inside(curr_corner))
            {
                // curr_corner is on the border of both room shapes.
                if (first_corner == null)
                {
                    first_corner = curr_corner;
                }
                else if (second_corner == null && !first_corner.equals(curr_corner))
                {
                    second_corner = curr_corner;
                    break;
                }
            }
        }
        if (first_corner == null || second_corner == null)
        {
            return null;
        }
        return new FloatLine(first_corner.to_float(), second_corner.to_float());
    }

    /**
     * Resets this ExpandableObject for autorouting the next connection.
     */
    public void reset()
    {
        if (section_arr != null)
        {
            for (MazeSearchElement curr_section : section_arr)
            {
                curr_section.reset();
            }
        }
    }

    /** allocates and initialises p_section_count sections */
    void allocate_sections(int p_section_count)
    {
        if (section_arr != null && section_arr.length == p_section_count)
        {
            return; // already allocated
        }
        section_arr = new MazeSearchElement[p_section_count];
        for (int i = 0; i < section_arr.length; ++i)
        {
            section_arr[i] = new MazeSearchElement();
        }
    }
    /** each section of the following arrray can be expanded seperately by the maze search algorithm */
    MazeSearchElement[] section_arr = null;
    /** The first room of this door. */
    public final ExpansionRoom first_room;
    /** The second room of this door. */
    public final ExpansionRoom second_room;
    /**
     * The dimension of a door may be 1 or 2.
     */
    public final int dimension;
}
