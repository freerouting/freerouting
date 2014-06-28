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
 * SortedRoomNeighbours.java
 *
 * Created on 28. Mai 2007, 07:27
 *
 */

package autoroute;

import java.util.Collection;
import java.util.LinkedList;
import java.util.TreeSet;
import java.util.SortedSet;
import java.util.Iterator;

import datastructures.Signum;
import datastructures.ShapeTree;

import geometry.planar.Side;
import geometry.planar.Direction;
import geometry.planar.Point;
import geometry.planar.IntPoint;
import geometry.planar.FloatPoint;
import geometry.planar.Line;
import geometry.planar.TileShape;
import geometry.planar.Simplex;

import board.ShapeSearchTree;
import board.SearchTreeObject;
import board.Connectable;
import board.Item;
import board.PolylineTrace;
import board.TestLevel;

/**
 * To calculate the neigbour rooms of an expansion room.
 * The neighbour rooms will be sorted in counterclock sense around the border of the shape of p_room.
 * Overlapping neighbours containing an item may be stored in an unordered list.
 *
 * @author Alfons Wirtz
 */
public class SortedRoomNeighbours
{
    /**
     * To calculate the neigbour rooms of an expansion room.
     * The neighbour rooms will be sorted in counterclock sense around the border of the shape of p_room.
     * Overlapping neighbours containing an item may be stored in an unordered list.
     */
    public static CompleteExpansionRoom calculate(ExpansionRoom p_room, AutorouteEngine p_autoroute_engine)
    {
        int net_no = p_autoroute_engine.get_net_no();
        TestLevel test_level = p_autoroute_engine.board.get_test_level();
        SortedRoomNeighbours room_neighbours = calculate_neighbours(p_room, net_no, p_autoroute_engine.autoroute_search_tree,
                p_autoroute_engine.generate_room_id_no(), test_level);
        
        // Check, that each side of the romm shape has at least one touching neighbour.
        // Otherwise improve the room shape by enlarging.
        
        boolean edge_removed = room_neighbours.try_remove_edge(net_no,
                p_autoroute_engine.autoroute_search_tree, test_level);
        CompleteExpansionRoom result = room_neighbours.completed_room;
        if (edge_removed)
        {
            p_autoroute_engine.remove_all_doors(result);
            return calculate(p_room, p_autoroute_engine);
        }
        
        // Now calculate the new incomplete rooms together with the doors
        // between this room and the sorted neighbours.
        if (room_neighbours.sorted_neighbours.isEmpty())
        {
            if (result instanceof ObstacleExpansionRoom)
            {
                calculate_incomplete_rooms_with_empty_neighbours((ObstacleExpansionRoom) p_room, p_autoroute_engine);
            }
        }
        else
        {
            room_neighbours.calculate_new_incomplete_rooms(p_autoroute_engine);
            if (test_level.ordinal() >= TestLevel.ALL_DEBUGGING_OUTPUT.ordinal() && result.get_shape().dimension() < 2)
            {
                System.out.println("AutorouteEngine.calculate_new_incomplete_rooms_with_mmore_than_1_neighbour: unexpected dimension for smoothened_shape");
            }
        }
        
        if (result instanceof CompleteFreeSpaceExpansionRoom)
        {
            calculate_target_doors((CompleteFreeSpaceExpansionRoom) result,
                    room_neighbours.own_net_objects, p_autoroute_engine);
        }
        return result;
    }
    
    private static void calculate_incomplete_rooms_with_empty_neighbours(ObstacleExpansionRoom p_room, AutorouteEngine p_autoroute_engine)
    {
        TileShape room_shape = p_room.get_shape();
        for (int i = 0; i < room_shape.border_line_count(); ++i)
        {
            Line curr_line = room_shape.border_line(i);
            if (SortedRoomNeighbours.insert_door_ok(p_room, curr_line))
            {
                Line[] shape_line = new Line[1];
                shape_line[0] = curr_line.opposite();
                TileShape new_room_shape = new Simplex(shape_line);
                TileShape new_contained_shape = room_shape.intersection(new_room_shape);
                FreeSpaceExpansionRoom new_room = p_autoroute_engine.add_incomplete_expansion_room(new_room_shape, p_room.get_layer(), new_contained_shape);
                ExpansionDoor new_door = new ExpansionDoor(p_room, new_room, 1);
                p_room.add_door(new_door);
                new_room.add_door(new_door);
            }
        }
    }
    
    
    private static void calculate_target_doors(CompleteFreeSpaceExpansionRoom p_room,
            Collection<ShapeTree.TreeEntry> p_own_net_objects, AutorouteEngine p_autoroute_engine)
    {
        if (!p_own_net_objects.isEmpty())
        {
            p_room.set_net_dependent();
        }
        for (ShapeTree.TreeEntry curr_entry : p_own_net_objects)
        {
            if (curr_entry.object instanceof Connectable)
            {
                Connectable curr_object = (Connectable) curr_entry.object;
                if (curr_object.contains_net(p_autoroute_engine.get_net_no()))
                {
                    TileShape curr_connection_shape =
                            curr_object.get_trace_connection_shape(p_autoroute_engine.autoroute_search_tree, curr_entry.shape_index_in_object);
                    if (curr_connection_shape != null && p_room.get_shape().intersects(curr_connection_shape))
                    {
                        Item curr_item = (Item) curr_object;
                        TargetItemExpansionDoor new_target_door =
                                new TargetItemExpansionDoor(curr_item, curr_entry.shape_index_in_object, p_room,
                                p_autoroute_engine.autoroute_search_tree);
                        p_room.add_target_door(new_target_door);
                    }
                }
            }
        }
    }
    
    private static SortedRoomNeighbours calculate_neighbours(ExpansionRoom p_room, int p_net_no,
            ShapeSearchTree p_autoroute_search_tree, int p_room_id_no, TestLevel p_test_level)
    {
        TileShape room_shape = p_room.get_shape();
        CompleteExpansionRoom completed_room;
        if (p_room instanceof IncompleteFreeSpaceExpansionRoom)
        {
            completed_room = new CompleteFreeSpaceExpansionRoom(room_shape, p_room.get_layer(), p_room_id_no);
        }
        else if (p_room instanceof ObstacleExpansionRoom)
        {
            completed_room = (ObstacleExpansionRoom)p_room;
        }
        else
        {
            System.out.println("SortedRoomNeighbours.calculate: unexpected expansion room type");
            return null;
        }
        SortedRoomNeighbours result = new SortedRoomNeighbours(p_room, completed_room);
        Collection<ShapeTree.TreeEntry> overlapping_objects = new LinkedList<ShapeTree.TreeEntry>();
        p_autoroute_search_tree.overlapping_tree_entries(room_shape, p_room.get_layer(), overlapping_objects);
        // Calculate the touching neigbour objects and sort them in counterclock sence
        // around the border of the room shape.
        for (ShapeTree.TreeEntry curr_entry : overlapping_objects)
        {
            SearchTreeObject curr_object = (SearchTreeObject) curr_entry.object;
            if (curr_object == p_room)
            {
                continue;
            }
            if ((p_room instanceof IncompleteFreeSpaceExpansionRoom) && !curr_object.is_trace_obstacle(p_net_no))
            {
                // delay processing the target doors until the room shape will not change any more
                result.own_net_objects.add(curr_entry);
                continue;
            }
            TileShape curr_shape =
                    curr_object.get_tree_shape(p_autoroute_search_tree, curr_entry.shape_index_in_object);
            TileShape intersection = room_shape.intersection(curr_shape);
            int dimension = intersection.dimension();
            if (dimension > 1)
            {
                if (completed_room instanceof ObstacleExpansionRoom && curr_object instanceof Item)
                {
                    // only Obstacle expansion roos may have a 2-dim overlap
                    Item curr_item = (Item) curr_object;
                    if (curr_item.is_route())
                    {
                        ItemAutorouteInfo item_info = curr_item.get_autoroute_info();
                        ObstacleExpansionRoom curr_overlap_room = 
                                item_info.get_expansion_room(curr_entry.shape_index_in_object, p_autoroute_search_tree);
                        ((ObstacleExpansionRoom) completed_room).create_overlap_door(curr_overlap_room);
                    }
                }
                else if (p_test_level.ordinal() >= TestLevel.ALL_DEBUGGING_OUTPUT.ordinal())
                {
                    System.out.println("SortedRoomNeighbours.calculate: unexpected area overlap of free space expansion room");
                }
                continue;
            }
            if (dimension < 0)
            {
                if (p_test_level.ordinal() >= TestLevel.CRITICAL_DEBUGGING_OUTPUT.ordinal())
                {
                    System.out.println("SortedRoomNeighbours.calculate: dimension >= 0 expected");
                }
                continue;
            }
            if (dimension == 1)
            {
                int[] touching_sides = room_shape.touching_sides(curr_shape);
                if (touching_sides.length != 2)
                {
                    if (p_test_level.ordinal() >= TestLevel.CRITICAL_DEBUGGING_OUTPUT.ordinal())
                    {
                        System.out.println("SortedRoomNeighbours.calculate: touching_sides length 2 expected");
                    }
                    continue;
                }
                result.add_sorted_neighbour(curr_shape, intersection, touching_sides[0],
                        touching_sides[1], false, false);
                // make  shure, that there is a door to the neighbour room.
                ExpansionRoom neighbour_room = null;
                if (curr_object instanceof ExpansionRoom)
                {
                    neighbour_room = (ExpansionRoom) curr_object;
                }
                else if (curr_object instanceof Item)
                {
                    Item curr_item = (Item) curr_object;
                    if (curr_item.is_route())
                    {
                        // expand the item for ripup and pushing purposes
                        ItemAutorouteInfo item_info = curr_item.get_autoroute_info();
                        neighbour_room = 
                                item_info.get_expansion_room(curr_entry.shape_index_in_object, p_autoroute_search_tree);
                    }
                }
                if (neighbour_room != null)
                {
                    if (SortedRoomNeighbours.insert_door_ok(completed_room, neighbour_room, intersection))
                    {
                        ExpansionDoor new_door = new ExpansionDoor(completed_room, neighbour_room, 1);
                        neighbour_room.add_door(new_door);
                        completed_room.add_door(new_door);
                    }
                }
            }
            else // dimensin = 0
            {
                Point touching_point = intersection.corner(0);
                int room_corner_no = room_shape.equals_corner(touching_point);
                boolean room_touch_is_corner;
                int touching_side_no_of_room;
                if (room_corner_no >= 0)
                {
                    room_touch_is_corner = true;
                    touching_side_no_of_room = room_corner_no;
                }
                else
                {
                    room_touch_is_corner = false;
                    touching_side_no_of_room = room_shape.contains_on_border_line_no(touching_point);
                    if (touching_side_no_of_room < 0 && p_test_level.ordinal() >= TestLevel.CRITICAL_DEBUGGING_OUTPUT.ordinal())
                    {
                        System.out.println("SortedRoomNeighbours.calculate: touching_side_no_of_room >= 0 expected");
                    }
                }
                int neighbour_room_corner_no = curr_shape.equals_corner(touching_point);
                boolean neighbour_room_touch_is_corner;
                int touching_side_no_of_neighbour_room;
                if (neighbour_room_corner_no >= 0)
                {
                    neighbour_room_touch_is_corner = true;
                    // The previous border line is preferred to make the shape of the incomplete room as big as possible
                    touching_side_no_of_neighbour_room = curr_shape.prev_no(neighbour_room_corner_no);
                }
                else
                {
                    neighbour_room_touch_is_corner = false;
                    touching_side_no_of_neighbour_room = curr_shape.contains_on_border_line_no(touching_point);
                    if (touching_side_no_of_neighbour_room < 0 && p_test_level.ordinal() >= TestLevel.CRITICAL_DEBUGGING_OUTPUT.ordinal())
                    {
                        System.out.println("AutorouteEngine.SortedRoomNeighbours.calculate: touching_side_no_of_neighbour_room >= 0 expected");
                    }
                }
                result.add_sorted_neighbour(curr_shape, intersection,
                        touching_side_no_of_room , touching_side_no_of_neighbour_room,
                        room_touch_is_corner , neighbour_room_touch_is_corner);
            }
        }
        return result;
    }
    
    
    
    /** Creates a new instance of SortedRoomNeighbours */
    private SortedRoomNeighbours(ExpansionRoom p_from_room, CompleteExpansionRoom p_completed_room)
    {
        from_room = p_from_room;
        completed_room = p_completed_room;
        room_shape = p_completed_room.get_shape();
        sorted_neighbours = new TreeSet<SortedRoomNeighbour>();
        own_net_objects = new LinkedList<ShapeTree.TreeEntry>();
    }
    
    private void add_sorted_neighbour(TileShape p_neighbour_shape, TileShape p_intersection,
            int p_touching_side_no_of_room, int p_touching_side_no_of_neighbour_room,
            boolean p_room_touch_is_corner, boolean p_neighbour_room_touch_is_corner)
    {
        SortedRoomNeighbour new_neighbour = new SortedRoomNeighbour(p_neighbour_shape, p_intersection,
                p_touching_side_no_of_room, p_touching_side_no_of_neighbour_room,
                p_room_touch_is_corner, p_neighbour_room_touch_is_corner);
        sorted_neighbours.add(new_neighbour);
    }
    
    /**
     * Check, that each side of the romm shape has at least one touching neighbour.
     * Otherwise the room shape will be improved the by enlarging.
     * Returns true, if the room shape was changed.
     */
    private boolean try_remove_edge(int p_net_no, ShapeSearchTree p_autoroute_search_tree, TestLevel p_test_level)
    {
        if (!(this.from_room instanceof IncompleteFreeSpaceExpansionRoom))
        {
            return false;
        }
        IncompleteFreeSpaceExpansionRoom curr_incomplete_room = (IncompleteFreeSpaceExpansionRoom) this.from_room;
        Iterator<SortedRoomNeighbour> it = sorted_neighbours.iterator();
        int remove_edge_no = -1;
        Simplex room_simplex = curr_incomplete_room.get_shape().to_Simplex();
        double room_shape_area = room_simplex.area();
        
        int prev_edge_no = -1;
        int curr_edge_no = 0;
        while (it.hasNext())
        {
            SortedRoomNeighbour next_neighbour = it.next();
            if (next_neighbour.touching_side_no_of_room == prev_edge_no)
            {
                continue;
            }
            if (next_neighbour.touching_side_no_of_room == curr_edge_no)
            {
                prev_edge_no = curr_edge_no;
                ++curr_edge_no;
            }
            else
            {
                // On the edge side with index curr_edge_no is no touching
                // neighbour.
                remove_edge_no = curr_edge_no;
                break;
            }
        }
        
        if (remove_edge_no < 0 && curr_edge_no < room_simplex.border_line_count())
        {
            // missing touching neighbour at the last edge side.
            remove_edge_no = curr_edge_no;
        }
        
        
        
        if (remove_edge_no >= 0)
        {
            // Touching neighbour missing at the edge side with index remove_edge_no
            // Remove the edge line and restart the algorithm.
            Simplex enlarged_shape = room_simplex.remove_border_line(remove_edge_no);
            IncompleteFreeSpaceExpansionRoom enlarged_room =
                    new IncompleteFreeSpaceExpansionRoom(enlarged_shape, curr_incomplete_room.get_layer(),
                    curr_incomplete_room.get_contained_shape());
            Collection<IncompleteFreeSpaceExpansionRoom>  new_rooms =
                    p_autoroute_search_tree.complete_shape(enlarged_room, p_net_no, null, null);
            if (new_rooms.size() != 1)
            {
                if (p_test_level.ordinal() >= TestLevel.ALL_DEBUGGING_OUTPUT.ordinal())
                {
                    System.out.println("AutorouteEngine.calculate_doors: 1 completed shape expected");
                }
                return false;
            }
            boolean remove_edge = false;
            if (new_rooms.size() == 1)
            {
                // Check, that the area increases to prevent endless loop.
                IncompleteFreeSpaceExpansionRoom new_shape = new_rooms.iterator().next();
                if (new_shape.get_shape().area() > room_shape_area)
                {
                    remove_edge = true;
                }
            }
            if (remove_edge)
            {
                Iterator<IncompleteFreeSpaceExpansionRoom> it2 = new_rooms.iterator();
                IncompleteFreeSpaceExpansionRoom new_room = it2.next();
                curr_incomplete_room.set_shape(new_room.get_shape());
                curr_incomplete_room.set_contained_shape(new_room.get_contained_shape());
                return true;
            }
        }
        return false;
    }
    
    /**
     * Called from calculate_doors().
     * The shape of the room p_result may change inside this function.
     */
    public void calculate_new_incomplete_rooms(AutorouteEngine p_autoroute_engine)
    {
        SortedRoomNeighbour prev_neighbour = this.sorted_neighbours.last();
        Iterator<SortedRoomNeighbour> it = this.sorted_neighbours.iterator();
        Simplex room_simplex = this.from_room.get_shape().to_Simplex();
        while (it.hasNext())
        {
            SortedRoomNeighbour next_neighbour = it.next();
            int first_touching_side_no = prev_neighbour.touching_side_no_of_room;
            int last_touching_side_no = next_neighbour.touching_side_no_of_room;
            
            int curr_next_no = room_simplex.next_no(first_touching_side_no);
            boolean intersection_with_prev_neighbour_ends_at_corner =
                    (first_touching_side_no != last_touching_side_no || prev_neighbour == this.sorted_neighbours.last())
                    && prev_neighbour.last_corner().equals(room_simplex.corner(curr_next_no));
            boolean intersection_with_next_neighbour_starts_at_corner =
                    (first_touching_side_no != last_touching_side_no || prev_neighbour == this.sorted_neighbours.last())
                    && next_neighbour.first_corner().equals(room_simplex.corner(last_touching_side_no));
            
            if (intersection_with_prev_neighbour_ends_at_corner)
            {
                first_touching_side_no = curr_next_no;
            }
            
            if(intersection_with_next_neighbour_starts_at_corner)
            {
                last_touching_side_no = room_simplex.prev_no(last_touching_side_no);
            }
            boolean neighbours_touch = false;
            
            if (this.sorted_neighbours.size() > 1)
            {
                neighbours_touch = prev_neighbour.last_corner().equals(next_neighbour.first_corner());
            }
            
            if (!neighbours_touch)
            {
                // create a door to a new incomplete expansion room between
                // the last corner of the previous neighbour and the first corner of the
                // current neighbour.
                int last_bounding_line_no = prev_neighbour.touching_side_no_of_neighbour_room;
                if (!(intersection_with_prev_neighbour_ends_at_corner
                        || prev_neighbour.room_touch_is_corner))
                {
                    last_bounding_line_no = prev_neighbour.neighbour_shape.prev_no(last_bounding_line_no);
                }
                
                
                int first_bounding_line_no = next_neighbour.touching_side_no_of_neighbour_room;
                if (!(intersection_with_next_neighbour_starts_at_corner
                        || next_neighbour.neighbour_room_touch_is_corner))
                {
                    first_bounding_line_no = next_neighbour.neighbour_shape.next_no(first_bounding_line_no);
                }
                Line start_edge_line = next_neighbour.neighbour_shape.border_line(first_bounding_line_no).opposite();
                // start_edge_line is only used for the first new incomplete room.
                Line middle_edge_line = null;
                int curr_touching_side_no = last_touching_side_no;
                boolean first_time = true;
                // The loop goes backwards fromm the edge line of next_neigbour to the edge line of prev_neigbour.
                for (;;)
                {
                    boolean corner_cut_off = false;
                    if (this.from_room instanceof IncompleteFreeSpaceExpansionRoom)
                    {
                        IncompleteFreeSpaceExpansionRoom incomplete_room = (IncompleteFreeSpaceExpansionRoom) this.from_room;
                        if (curr_touching_side_no == last_touching_side_no
                                && first_touching_side_no != last_touching_side_no)
                        {
                            // Create a new line approximately from the last corner of the previous
                            // neighbour to the first corner of the next neighbour to cut off
                            // the outstanding corners of the room shape in the empty space.
                            // That is only tried in the first pass of the loop.
                            IntPoint cut_line_start = prev_neighbour.last_corner().to_float().round();
                            IntPoint cut_line_end = next_neighbour.first_corner().to_float().round();
                            Line cut_line = new Line(cut_line_start, cut_line_end);
                            TileShape cut_half_plane = TileShape.get_instance(cut_line);
                            ((CompleteFreeSpaceExpansionRoom)this.completed_room).set_shape(this.completed_room.get_shape().intersection(cut_half_plane));
                            corner_cut_off = true;
                            if (incomplete_room.get_contained_shape().side_of(cut_line) != Side.ON_THE_LEFT)
                            {
                                // Otherwise p_room.contained_shape would no longer be contained
                                // in the shape after cutting of the corner.
                                corner_cut_off = false;
                            }
                            if (corner_cut_off)
                            {
                                middle_edge_line = cut_line.opposite();
                            }
                        }
                    }
                    int next_touching_side_no = room_simplex.prev_no(curr_touching_side_no);
                    
                    if (!corner_cut_off)
                    {
                        middle_edge_line = room_simplex.border_line(curr_touching_side_no).opposite();
                    }
                    
                    Direction middle_line_dir = middle_edge_line.direction();
                    
                    boolean last_time =
                            curr_touching_side_no == first_touching_side_no
                            && !(prev_neighbour == this.sorted_neighbours.last() && first_time)
                            // The expression above handles the case, when all neigbours are on 1 edge line.
                            || corner_cut_off;
                    
                    Line end_edge_line;
                    // end_edge_line is only used for the last new incomplete room.
                    if (last_time)
                    {
                        end_edge_line = prev_neighbour.neighbour_shape.border_line(last_bounding_line_no).opposite();
                        if (end_edge_line.direction().side_of(middle_line_dir) != Side.ON_THE_LEFT)
                        {
                            // Concave corner between the middle and the last line.
                            // May be there is a 1 point touch.
                            end_edge_line = null;
                        }
                    }
                    else
                    {
                        end_edge_line = null;
                    }
                    
                    if (start_edge_line != null && middle_line_dir.side_of(start_edge_line.direction()) != Side.ON_THE_LEFT)
                    {
                        // concave corner between the first and the middle line
                        // May be there is a 1 point touch.
                        start_edge_line = null;
                    }
                    int new_edge_line_count = 1;
                    if (start_edge_line != null)
                    {
                        ++new_edge_line_count;
                    }
                    if (end_edge_line != null)
                    {
                        ++new_edge_line_count;
                    }
                    Line [] new_edge_lines = new Line[new_edge_line_count];
                    int curr_index = 0;
                    if (start_edge_line != null)
                    {
                        new_edge_lines[curr_index] = start_edge_line;
                        ++curr_index;
                    }
                    new_edge_lines[curr_index] = middle_edge_line;
                    if (end_edge_line != null)
                    {
                        ++curr_index;
                        new_edge_lines[curr_index] = end_edge_line;
                    }
                    Simplex new_room_shape = Simplex.get_instance(new_edge_lines);
                    if (!new_room_shape.is_empty())
                    {
                        
                        TileShape new_contained_shape = this.completed_room.get_shape().intersection(new_room_shape);
                        if (!new_contained_shape.is_empty())
                        {
                            FreeSpaceExpansionRoom new_room =
                                    p_autoroute_engine.add_incomplete_expansion_room(new_room_shape, this.from_room.get_layer(), new_contained_shape);
                            ExpansionDoor new_door = new ExpansionDoor(this.completed_room, new_room, 1);
                            this.completed_room.add_door(new_door);
                            new_room.add_door(new_door);
                        }
                    }
                    if (last_time)
                    {
                        break;
                    }
                    curr_touching_side_no = next_touching_side_no;
                    start_edge_line = null;
                    first_time = false;
                }
            }
            prev_neighbour = next_neighbour;
        }
    }
    
    /**
     * p_door_shape is expected to bave dimension 1.
     */
    static boolean insert_door_ok(ExpansionRoom p_room_1, ExpansionRoom p_room_2, TileShape p_door_shape)
    {
        if (p_room_1.door_exists(p_room_2))
        {
            return false;
        }
        if (p_room_1 instanceof ObstacleExpansionRoom && p_room_2 instanceof ObstacleExpansionRoom)
        {
            Item first_item = ((ObstacleExpansionRoom) p_room_1).get_item();
            Item second_item = ((ObstacleExpansionRoom) p_room_2).get_item();
            // insert only overlap_doors between items of the same net for performance reasons.
            return (first_item.shares_net(second_item));
        }
        if (!(p_room_1 instanceof ObstacleExpansionRoom)  && !(p_room_2 instanceof ObstacleExpansionRoom))
        {
            return true;
        }
        // Insert 1 dimensional doors of trace rooms only, if they are parallel to the trace line.
        // Otherwise there may be check ripup problems with entering at the wrong side at a fork.
        Line door_line = null;
        Point prev_corner = p_door_shape.corner(0);
        int corner_count = p_door_shape.border_line_count();
        for (int i = 1; i < corner_count; ++i)
        {
            Point curr_corner = p_door_shape.corner(i);
            if (!curr_corner.equals(prev_corner))
            {
                door_line = p_door_shape.border_line(i - 1);
                break;
            }
            prev_corner = curr_corner;
        }
        if (p_room_1 instanceof ObstacleExpansionRoom)
        {
            if (!insert_door_ok((ObstacleExpansionRoom) p_room_1, door_line))
            {
                return false;
            }
        }
        if (p_room_2 instanceof ObstacleExpansionRoom)
        {
            if (!insert_door_ok((ObstacleExpansionRoom) p_room_2, door_line))
            {
                return false;
            }
        }
        return true;
    }
    /**
     * Insert 1 dimensional doors for the first and the last room of a  trace rooms only,
     * if they are parallel to the trace line.
     * Otherwise there may be check ripup problems with entering at the wrong side at a fork.
     */
    private static boolean insert_door_ok(ObstacleExpansionRoom p_room, Line p_door_line)
    {
        if (p_door_line == null)
        {
            System.out.println("SortedRoomNeighbours.insert_door_ok: p_door_line is null");
            return false;
        }
        Item curr_item = p_room.get_item();
        if (curr_item instanceof PolylineTrace)
        {
            int room_index = p_room.get_index_in_item();
            PolylineTrace curr_trace = (PolylineTrace) curr_item;
            if (room_index == 0 || room_index == curr_trace.tile_shape_count() - 1)
            {
                Line curr_trace_line = curr_trace.polyline().arr[room_index + 1];
                if (!curr_trace_line.is_parallel(p_door_line))
                {
                    return false;
                }
            }
        }
        return true;
    }
    
    private final ExpansionRoom from_room;
    private final CompleteExpansionRoom completed_room;
    private final TileShape room_shape;
    private final SortedSet<SortedRoomNeighbour> sorted_neighbours;
    private final Collection<ShapeTree.TreeEntry> own_net_objects;
    
    /**
     * Helper class to sort the doors of an expansion room counterclockwise
     * arount the border of the room shape.
     *
     * @author  Alfons Wirtz
     */
    
    private class SortedRoomNeighbour implements Comparable<SortedRoomNeighbour>
    {
        public SortedRoomNeighbour(TileShape p_neighbour_shape, TileShape p_intersection,
                int p_touching_side_no_of_room, int p_touching_side_no_of_neighbour_room,
                boolean p_room_touch_is_corner, boolean p_neighbour_room_touch_is_corner)
        {
            neighbour_shape = p_neighbour_shape;
            intersection = p_intersection;
            touching_side_no_of_room = p_touching_side_no_of_room;
            touching_side_no_of_neighbour_room = p_touching_side_no_of_neighbour_room;
            room_touch_is_corner = p_room_touch_is_corner;
            neighbour_room_touch_is_corner = p_neighbour_room_touch_is_corner;
        }
        
        /**
         * Compare function for or sorting the neighbours in counterclock sense
         * around the border of the room shape in ascending order.
         */
        public int compareTo(SortedRoomNeighbour p_other)
        {
            int compare_value = this.touching_side_no_of_room - p_other.touching_side_no_of_room;
            if (compare_value != 0)
            {
                return compare_value;
            }
            FloatPoint compare_corner = room_shape.corner_approx(touching_side_no_of_room);
            double this_distance = this.first_corner().to_float().distance(compare_corner);
            double other_distance = p_other.first_corner().to_float().distance(compare_corner);
            double delta_distance = this_distance - other_distance;
            if (Math.abs(delta_distance) <= c_dist_tolerance)
            {
                // check corners for equality
                if (this.first_corner().equals(p_other.first_corner()))
                {
                    // in this case compare the last corners
                    double this_distance2 = this.last_corner().to_float().distance(compare_corner);
                    double other_distance2 = p_other.last_corner().to_float().distance(compare_corner);
                    delta_distance = this_distance2 - other_distance2;
                    if (Math.abs(delta_distance) <= c_dist_tolerance)
                    {
                        if (this.neighbour_room_touch_is_corner && p_other.neighbour_room_touch_is_corner)
                            // Otherwise there may be a short 1 dim. touch at a link between 2 trace lines.
                            // In this case equality is ok, because the 2 intersection pieces with
                            // the expansion room are identical, so that only 1 obstacle is needed.
                        {
                            int compare_line_no = touching_side_no_of_room;
                            if (room_touch_is_corner)
                            {
                                compare_line_no = room_shape.prev_no(compare_line_no);
                            }
                            Direction compare_dir = room_shape.border_line(compare_line_no).direction().opposite();
                            Line this_compare_line = this.neighbour_shape.border_line(this.touching_side_no_of_neighbour_room);
                            Line other_compare_line = p_other.neighbour_shape.border_line(p_other.touching_side_no_of_neighbour_room);
                            delta_distance = compare_dir.compare_from(this_compare_line.direction(), other_compare_line.direction());
                        }
                    }
                }
            }
            int result = Signum.as_int(delta_distance);
            return result;
        }
        
        /**
         * Returns the first corner of the intersection shape with the neighbour.
         */
        public Point first_corner()
        {
            if (precalculated_first_corner == null)
            {
                if (room_touch_is_corner)
                {
                    precalculated_first_corner = room_shape.corner(touching_side_no_of_room);
                }
                else if (neighbour_room_touch_is_corner)
                {
                    precalculated_first_corner = neighbour_shape.corner(touching_side_no_of_neighbour_room);
                }
                else
                {
                    Point curr_first_corner = neighbour_shape.corner(neighbour_shape.next_no(touching_side_no_of_neighbour_room));
                    Line prev_line = room_shape.border_line(room_shape.prev_no(touching_side_no_of_room));
                    if (prev_line.side_of(curr_first_corner) == Side.ON_THE_RIGHT)
                    {
                        precalculated_first_corner = curr_first_corner;
                    }
                    else // curr_first_corner is outside the door shape
                    {
                        precalculated_first_corner = room_shape.corner(touching_side_no_of_room);
                    }
                }
            }
            return precalculated_first_corner;
        }
        
        /**
         * Returns the last corner of the intersection shape with the neighbour.
         */
        public Point last_corner()
        {
            if (precalculated_last_corner == null)
            {
                if (room_touch_is_corner)
                {
                    precalculated_last_corner = room_shape.corner(touching_side_no_of_room);
                }
                else if (neighbour_room_touch_is_corner)
                {
                    precalculated_last_corner = neighbour_shape.corner(touching_side_no_of_neighbour_room);
                }
                else
                {
                    Point curr_last_corner = neighbour_shape.corner(touching_side_no_of_neighbour_room);
                    Line next_line = room_shape.border_line(room_shape.next_no(touching_side_no_of_room));
                    if (next_line.side_of(curr_last_corner) == Side.ON_THE_RIGHT)
                    {
                        precalculated_last_corner = curr_last_corner;
                    }
                    else // curr_last_corner is outside the door shape
                    {
                        precalculated_last_corner = room_shape.corner(room_shape.next_no(touching_side_no_of_room));
                    }
                }
            }
            return precalculated_last_corner;
        }
        
        /** The shape of the neighbour room */
        public final TileShape neighbour_shape;
        
        /** The intersection of tnis ExpansionRoom shape with the neighbour_shape */
        public final TileShape intersection;
        
        /** The side number of this room, where it touches the neighbour */
        public final int touching_side_no_of_room ;
        
        /** The side number of the neighbour room, where it touches this room */
        public final int touching_side_no_of_neighbour_room ;
        
        /** True, if the intersection of this room and the neighbour is
         * equal to a corner of this room */
        public final boolean room_touch_is_corner;
        
        /** True, if the intersection of this room and the neighbour is
         * equal to a corner of the neighbour room */
        public final boolean neighbour_room_touch_is_corner;
        
        private Point precalculated_first_corner = null;
        private Point precalculated_last_corner = null;
        
        static private final double c_dist_tolerance = 1;
    }
}
