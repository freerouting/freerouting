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
 * LocateFoundConnectionAlgo.java
 *
 * Created on 31. Januar 2006, 08:20
 *
 */
package autoroute;

import java.util.Collection;
import java.util.SortedSet;
import java.util.LinkedList;
import java.util.Iterator;

import geometry.planar.IntPoint;
import geometry.planar.FloatPoint;
import geometry.planar.TileShape;

import board.Connectable;
import board.Item;
import board.AngleRestriction;
import board.ShapeSearchTree;
import board.TestLevel;

/**
 *
 * @author Alfons Wirtz
 */
public abstract class LocateFoundConnectionAlgo
{

    /**
     * Returns a new Instance of LocateFoundConnectionAlgo or null,
     * if p_destination_door is null.
     */
    public static LocateFoundConnectionAlgo get_instance(MazeSearchAlgo.Result p_maze_search_result, AutorouteControl p_ctrl,
            ShapeSearchTree p_search_tree, AngleRestriction p_angle_restriction, SortedSet<Item> p_ripped_item_list, TestLevel p_test_level)
    {
        if (p_maze_search_result == null)
        {
            return null;
        }
        LocateFoundConnectionAlgo result;
        if (p_angle_restriction == AngleRestriction.NINETY_DEGREE || p_angle_restriction == AngleRestriction.FORTYFIVE_DEGREE)
        {
            result = new LocateFoundConnectionAlgo45Degree(p_maze_search_result, p_ctrl, p_search_tree, p_angle_restriction,
                    p_ripped_item_list, p_test_level);
        }
        else
        {
            result = new LocateFoundConnectionAlgoAnyAngle(p_maze_search_result, p_ctrl, p_search_tree, p_angle_restriction,
                    p_ripped_item_list, p_test_level);
        }
        return result;
    }

    /** Creates a new instance of LocateFoundConnectionAlgo */
    protected LocateFoundConnectionAlgo(MazeSearchAlgo.Result p_maze_search_result, AutorouteControl p_ctrl,
            ShapeSearchTree p_search_tree, AngleRestriction p_angle_restriction, SortedSet<Item> p_ripped_item_list, TestLevel p_test_level)
    {
        this.ctrl = p_ctrl;
        this.angle_restriction = p_angle_restriction;
        this.test_level = p_test_level;
        Collection<BacktrackElement> backtrack_list = backtrack(p_maze_search_result, p_ripped_item_list);
        this.backtrack_array = new BacktrackElement[backtrack_list.size()];
        Iterator<BacktrackElement> it = backtrack_list.iterator();
        for (int i = 0; i < backtrack_array.length; ++i)
        {
            this.backtrack_array[i] = it.next();
        }
        this.connection_items = new LinkedList<ResultItem>();
        BacktrackElement start_info = this.backtrack_array[backtrack_array.length - 1];
        if (!(start_info.door instanceof TargetItemExpansionDoor))
        {
            System.out.println("LocateFoundConnectionAlgo: ItemExpansionDoor expected for start_info.door");
            this.start_item = null;
            this.start_layer = 0;
            this.target_item = null;
            this.target_layer = 0;
            this.start_door = null;
            return;
        }
        this.start_door = (TargetItemExpansionDoor) start_info.door;
        this.start_item = start_door.item;
        this.start_layer = start_door.room.get_layer();
        this.current_from_door_index = 0;
        boolean at_fanout_end = false;
        if (p_maze_search_result.destination_door instanceof TargetItemExpansionDoor)
        {
            TargetItemExpansionDoor curr_destination_door = (TargetItemExpansionDoor) p_maze_search_result.destination_door;
            this.target_item = curr_destination_door.item;
            this.target_layer = curr_destination_door.room.get_layer();

            this.current_from_point = calculate_starting_point(curr_destination_door, p_search_tree);
        }
        else if (p_maze_search_result.destination_door instanceof ExpansionDrill)
        {
            // may happen only in case of fanout
            this.target_item = null;
            ExpansionDrill curr_drill = (ExpansionDrill) p_maze_search_result.destination_door;
            this.current_from_point = curr_drill.location.to_float();
            this.target_layer = curr_drill.first_layer + p_maze_search_result.section_no_of_door;
            at_fanout_end = true;
        }
        else
        {
            System.out.println("LocateFoundConnectionAlgo: unexpected type of destination_door");
            this.target_item = null;
            this.target_layer = 0;
            return;
        }
        this.current_trace_layer = this.target_layer;
        this.previous_from_point = this.current_from_point;


        boolean connection_done = false;
        while (!connection_done)
        {
            boolean layer_changed = false;
            if (at_fanout_end)
            {
                // do not increase this.current_target_door_index
                layer_changed = true;
            }
            else
            {
                this.current_target_door_index = this.current_from_door_index + 1;
                while (current_target_door_index < this.backtrack_array.length && !layer_changed)
                {
                    if (this.backtrack_array[this.current_target_door_index].door instanceof ExpansionDrill)
                    {
                        layer_changed = true;
                    }
                    else
                    {
                        ++this.current_target_door_index;
                    }
                }
            }
            if (layer_changed)
            {
                // the next trace leads to a via
                ExpansionDrill current_target_drill = (ExpansionDrill) this.backtrack_array[this.current_target_door_index].door;
                this.current_target_shape = TileShape.get_instance(current_target_drill.location);
            }
            else
            {
                // the next trace leads to the final target
                connection_done = true;
                this.current_target_door_index = this.backtrack_array.length - 1;
                TileShape target_shape = ((Connectable) start_item).get_trace_connection_shape(p_search_tree, start_door.tree_entry_no);
                this.current_target_shape = target_shape.intersection(start_door.room.get_shape());
                if (this.current_target_shape.dimension() >= 2)
                {
                    // the target is a conduction area, make a save connection
                    // by shrinking the shape by the trace halfwidth.
                    double trace_half_width = this.ctrl.compensated_trace_half_width[start_door.room.get_layer()];
                    TileShape shrinked_shape = (TileShape) this.current_target_shape.offset(-trace_half_width);
                    if (!shrinked_shape.is_empty())
                    {
                        this.current_target_shape = shrinked_shape;
                    }
                }
            }
            this.current_to_door_index = this.current_from_door_index + 1;
            ResultItem next_trace = this.calculate_next_trace(layer_changed, at_fanout_end);
            at_fanout_end = false;
            this.connection_items.add(next_trace);
        }
    }

    /**
     * Calclates the next trace trace of the connection under construction.
     * Returns null, if all traces are returned.
     */
    private ResultItem calculate_next_trace(boolean p_layer_changed, boolean p_at_fanout_end)
    {
        Collection<FloatPoint> corner_list = new LinkedList<FloatPoint>();
        corner_list.add(this.current_from_point);
        if (!p_at_fanout_end)
        {
            FloatPoint adjusted_start_corner = this.adjust_start_corner();
            if (adjusted_start_corner != this.current_from_point)
            {
                FloatPoint add_corner = calculate_additional_corner(this.current_from_point, adjusted_start_corner,
                        true, this.angle_restriction);
                corner_list.add(add_corner);
                corner_list.add(adjusted_start_corner);
                this.previous_from_point = this.current_from_point;
                this.current_from_point = adjusted_start_corner;
            }
        }
        FloatPoint prev_corner = this.current_from_point;
        for (;;)
        {
            Collection<FloatPoint> next_corners = calculate_next_trace_corners();
            if (next_corners.isEmpty())
            {
                break;
            }
            Iterator<FloatPoint> it = next_corners.iterator();
            while (it.hasNext())
            {
                FloatPoint curr_next_corner = it.next();
                if (curr_next_corner != prev_corner)
                {
                    corner_list.add(curr_next_corner);
                    this.previous_from_point = this.current_from_point;
                    this.current_from_point = curr_next_corner;
                    prev_corner = curr_next_corner;
                }
            }
        }

        int next_layer = this.current_trace_layer;
        if (p_layer_changed)
        {
            this.current_from_door_index = this.current_target_door_index + 1;
            CompleteExpansionRoom next_room = this.backtrack_array[this.current_from_door_index].next_room;
            if (next_room != null)
            {
                next_layer = next_room.get_layer();
            }
        }

        // Round the new trace corners to Integer.
        Collection<IntPoint> rounded_corner_list = new LinkedList<IntPoint>();
        Iterator<FloatPoint> it = corner_list.iterator();
        IntPoint prev_point = null;
        while (it.hasNext())
        {
            IntPoint curr_point = (it.next()).round();
            if (!curr_point.equals(prev_point))
            {
                rounded_corner_list.add(curr_point);
                prev_point = curr_point;
            }
        }

        // Construct the result item
        IntPoint[] corner_arr = new IntPoint[rounded_corner_list.size()];
        Iterator<IntPoint> it2 = rounded_corner_list.iterator();
        for (int i = 0; i < corner_arr.length; ++i)
        {
            corner_arr[i] = it2.next();
        }
        ResultItem result = new ResultItem(corner_arr, this.current_trace_layer);
        this.current_trace_layer = next_layer;
        return result;
    }

    /**
     * Returns the next list of corners for the construction of the trace
     * in calculate_next_trace. If the result is emppty, the trace is already completed.
     */
    protected abstract Collection<FloatPoint> calculate_next_trace_corners();

    /** Test display of the baktrack rooms. */
    public void draw(java.awt.Graphics p_graphics, boardgraphics.GraphicsContext p_graphics_context)
    {
        for (int i = 0; i < backtrack_array.length; ++i)
        {
            CompleteExpansionRoom next_room = backtrack_array[i].next_room;
            if (next_room != null)
            {
                next_room.draw(p_graphics, p_graphics_context, 0.2);
            }
            ExpandableObject next_door = backtrack_array[i].door;
            if (next_door instanceof ExpansionDrill)
            {
                ((ExpansionDrill) next_door).draw(p_graphics, p_graphics_context, 0.2);
            }
        }
    }

    /**
     * Calculates the starting point of the next trace on p_from_door.item.
     * The implementation is not yet optimal for starting points on traces
     * or areas.
     */
    private static FloatPoint calculate_starting_point(TargetItemExpansionDoor p_from_door, ShapeSearchTree p_search_tree)
    {
        TileShape connection_shape =
                ((Connectable) p_from_door.item).get_trace_connection_shape(p_search_tree, p_from_door.tree_entry_no);
        connection_shape = connection_shape.intersection(p_from_door.room.get_shape());
        return connection_shape.centre_of_gravity().round().to_float();
    }

    /**
     * Creates a list of doors by backtracking from p_destination_door to
     * the start door.
     * Returns null, if p_destination_door is null.
     */
    private static Collection<BacktrackElement> backtrack(MazeSearchAlgo.Result p_maze_search_result, SortedSet<Item> p_ripped_item_list)
    {
        if (p_maze_search_result == null)
        {
            return null;
        }
        Collection<BacktrackElement> result = new LinkedList<BacktrackElement>();
        CompleteExpansionRoom curr_next_room = null;
        ExpandableObject curr_backtrack_door = p_maze_search_result.destination_door;
        MazeSearchElement curr_maze_search_element = curr_backtrack_door.get_maze_search_element(p_maze_search_result.section_no_of_door);
        if (curr_backtrack_door instanceof TargetItemExpansionDoor)
        {
            curr_next_room = ((TargetItemExpansionDoor) curr_backtrack_door).room;
        }
        else if (curr_backtrack_door instanceof ExpansionDrill)
        {
            ExpansionDrill curr_drill = (ExpansionDrill) curr_backtrack_door;
            curr_next_room = curr_drill.room_arr[curr_drill.first_layer + p_maze_search_result.section_no_of_door];
            if (curr_maze_search_element.room_ripped)
            {
                for (CompleteExpansionRoom tmp_room : curr_drill.room_arr)
                {
                    if (tmp_room instanceof ObstacleExpansionRoom)
                    {
                        p_ripped_item_list.add(((ObstacleExpansionRoom) tmp_room).get_item());
                    }
                }
            }
        }
        BacktrackElement curr_backtrack_element = new BacktrackElement(curr_backtrack_door, p_maze_search_result.section_no_of_door, curr_next_room);
        for (;;)
        {
            result.add(curr_backtrack_element);
            curr_backtrack_door = curr_maze_search_element.backtrack_door;
            if (curr_backtrack_door == null)
            {
                break;
            }
            int curr_section_no = curr_maze_search_element.section_no_of_backtrack_door;
            if (curr_section_no >= curr_backtrack_door.maze_search_element_count())
            {
                System.out.println("LocateFoundConnectionAlgo: curr_section_no to big");
                curr_section_no = curr_backtrack_door.maze_search_element_count() - 1;
            }
            if (curr_backtrack_door instanceof ExpansionDrill)
            {
                ExpansionDrill curr_drill = (ExpansionDrill) curr_backtrack_door;
                curr_next_room = curr_drill.room_arr[curr_section_no];
            }
            else
            {
                curr_next_room = curr_backtrack_door.other_room(curr_next_room);
            }
            curr_maze_search_element = curr_backtrack_door.get_maze_search_element(curr_section_no);
            curr_backtrack_element = new BacktrackElement(curr_backtrack_door, curr_section_no, curr_next_room);
            if (curr_maze_search_element.room_ripped)
            {
                if (curr_next_room instanceof ObstacleExpansionRoom)
                {
                    p_ripped_item_list.add(((ObstacleExpansionRoom) curr_next_room).get_item());
                }
            }
        }
        return result;
    }

    /**
     * Adjusts the start corner, so that a trace starting at this corner is completely
     * contained in the start room.
     */
    private FloatPoint adjust_start_corner()
    {
        if (this.current_from_door_index < 0)
        {
            return this.current_from_point;
        }
        BacktrackElement curr_from_info = this.backtrack_array[this.current_from_door_index];
        if (curr_from_info.next_room == null)
        {
            return this.current_from_point;
        }
        double trace_half_width = this.ctrl.compensated_trace_half_width[this.current_trace_layer];
        TileShape shrinked_room_shape = (TileShape) curr_from_info.next_room.get_shape().offset(-trace_half_width);
        if (shrinked_room_shape.is_empty() || shrinked_room_shape.contains(this.current_from_point))
        {
            return this.current_from_point;
        }
        return shrinked_room_shape.nearest_point_approx(this.current_from_point).round().to_float();
    }

    private static FloatPoint ninety_degree_corner(FloatPoint p_from_point, FloatPoint p_to_point,
            boolean p_horizontal_first)
    {
        double x;
        double y;
        if (p_horizontal_first)
        {
            x = p_to_point.x;
            y = p_from_point.y;
        }
        else
        {
            x = p_from_point.x;
            y = p_to_point.y;
        }
        return new FloatPoint(x, y);
    }

    private static FloatPoint fortyfive_degree_corner(FloatPoint p_from_point, FloatPoint p_to_point,
            boolean p_horizontal_first)
    {
        double abs_dx = Math.abs(p_to_point.x - p_from_point.x);
        double abs_dy = Math.abs(p_to_point.y - p_from_point.y);
        double x;
        double y;

        if (abs_dx <= abs_dy)
        {
            if (p_horizontal_first)
            {
                x = p_to_point.x;
                if (p_to_point.y >= p_from_point.y)
                {
                    y = p_from_point.y + abs_dx;
                }
                else
                {
                    y = p_from_point.y - abs_dx;
                }
            }
            else
            {
                x = p_from_point.x;
                if (p_to_point.y > p_from_point.y)
                {
                    y = p_to_point.y - abs_dx;
                }
                else
                {
                    y = p_to_point.y + abs_dx;
                }
            }
        }
        else
        {
            if (p_horizontal_first)
            {
                y = p_from_point.y;
                if (p_to_point.x > p_from_point.x)
                {
                    x = p_to_point.x - abs_dy;
                }
                else
                {
                    x = p_to_point.x + abs_dy;
                }
            }
            else
            {
                y = p_to_point.y;
                if (p_to_point.x > p_from_point.x)
                {
                    x = p_from_point.x + abs_dy;
                }
                else
                {
                    x = p_from_point.x - abs_dy;
                }
            }
        }
        return new FloatPoint(x, y);
    }

    /**
     * Calculates an additional corner, so that for the lines from p_from_point to the result corner
     * and from the result corner to p_to_point p_angle_restriction is fulfilled.
     */
    static FloatPoint calculate_additional_corner(FloatPoint p_from_point, FloatPoint p_to_point,
            boolean p_horizontal_first, AngleRestriction p_angle_restriction)
    {
        FloatPoint result;
        if (p_angle_restriction == AngleRestriction.NINETY_DEGREE)
        {
            result = ninety_degree_corner(p_from_point, p_to_point, p_horizontal_first);
        }
        else if (p_angle_restriction == AngleRestriction.FORTYFIVE_DEGREE)
        {
            result = fortyfive_degree_corner(p_from_point, p_to_point, p_horizontal_first);
        }
        else
        {
            result = p_to_point;
        }
        return result;
    }
    /** The new items implementing the found connection */
    public final Collection<ResultItem> connection_items;
    /** The start item of the new routed connection */
    public final Item start_item;
    /** The layer of the connection to the start item */
    public final int start_layer;
    /** The destination  item of the new routed connection */
    public final Item target_item;
    /** The layer of the connection to the target item */
    public final int target_layer;
    /**
     * The array of backtrack doors from the destination to the start of a found
     * connection of the maze search algorithm.
     */
    protected final BacktrackElement[] backtrack_array;
    protected final AutorouteControl ctrl;
    protected final AngleRestriction angle_restriction;
    protected final TestLevel test_level;
    protected final TargetItemExpansionDoor start_door;
    protected FloatPoint current_from_point;
    protected FloatPoint previous_from_point;
    protected int current_trace_layer;
    protected int current_from_door_index;
    protected int current_to_door_index;
    protected int current_target_door_index;
    protected TileShape current_target_shape;

    /**
     * Type of a single item in the result list connection_items.
     * Used to create a new PolylineTrace.
     */
    protected static class ResultItem
    {

        public ResultItem(IntPoint[] p_corners, int p_layer)
        {
            corners = p_corners;
            layer = p_layer;
        }
        public final IntPoint[] corners;
        public final int layer;
    }

    /**
     * Type of the elements of the list returned by this.backtrack().
     * Next_room is the common room of the current door and the next
     * door in the backtrack list.
     */
    protected static class BacktrackElement
    {

        private BacktrackElement(ExpandableObject p_door, int p_section_no_of_door, CompleteExpansionRoom p_room)
        {
            door = p_door;
            section_no_of_door = p_section_no_of_door;
            next_room = p_room;
        }
        public final ExpandableObject door;
        public final int section_no_of_door;
        public final CompleteExpansionRoom next_room;
    }
}
