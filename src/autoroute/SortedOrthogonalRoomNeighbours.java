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
 * OrthogonalAutorouteEngine.java
 *
 * Created on 24. Mai 2007, 07:51
 *
 */

package autoroute;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.SortedSet;
import java.util.TreeSet;

import datastructures.ShapeTree;

import geometry.planar.IntBox;
import geometry.planar.TileShape;
import geometry.planar.Limits;

import board.SearchTreeObject;
import board.ShapeSearchTree;
import board.Item;

/**
 *
 * @author Alfons Wirtz
 */
public class SortedOrthogonalRoomNeighbours
{
    
    public static CompleteExpansionRoom calculate(ExpansionRoom p_room, AutorouteEngine p_autoroute_engine)
    {
        int net_no = p_autoroute_engine.get_net_no();
        SortedOrthogonalRoomNeighbours room_neighbours  = SortedOrthogonalRoomNeighbours.calculate_neighbours(p_room, net_no,
                p_autoroute_engine.autoroute_search_tree, p_autoroute_engine.generate_room_id_no());
        if (room_neighbours == null)
        {
            return null;
        }
        
        // Check, that each side of the romm shape has at least one touching neighbour.
        // Otherwise improve the room shape by enlarging.
        boolean edge_removed = room_neighbours.try_remove_edge(net_no, p_autoroute_engine.autoroute_search_tree);
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
        }
        return result;
    }
    
    private static void calculate_incomplete_rooms_with_empty_neighbours(ObstacleExpansionRoom p_room, AutorouteEngine p_autoroute_engine)
    {
        TileShape room_shape = p_room.get_shape();
        if (!(room_shape instanceof IntBox))
        {
            System.out.println("SortedOrthoganelRoomNeighbours.calculate_incomplete_rooms_with_empty_neighbours: IntBox expected for room_shape");
            return;
        }
        IntBox room_box = (IntBox) room_shape;
        IntBox bounding_box = p_autoroute_engine.board.get_bounding_box();
        for (int i = 0; i < 4; ++i)
        {
            IntBox new_room_box;
            if (i == 0)
            {
                new_room_box = new IntBox(bounding_box.ll.x, bounding_box.ll.y, bounding_box.ur.x, room_box.ll.y);
            }
            else if (i == 1)
            {
                new_room_box = new IntBox(room_box.ur.x, bounding_box.ll.y, bounding_box.ur.x, bounding_box.ur.y);
            }
            else if (i == 2)
            {
                new_room_box = new IntBox(bounding_box.ll.x, room_box.ur.y, bounding_box.ur.x, bounding_box.ur.y);
            }
            else if (i == 3)
            {
                new_room_box = new IntBox(bounding_box.ll.x, bounding_box.ll.y, room_box.ll.x, bounding_box.ur.y);
            }
            else
            {
                System.out.println("SortedOrthoganelRoomNeighbours.calculate_incomplete_rooms_with_empty_neighbours: illegal index i");
                return;
            }
            IntBox new_contained_box = room_box.intersection(new_room_box);
            FreeSpaceExpansionRoom new_room = p_autoroute_engine.add_incomplete_expansion_room(new_room_box, p_room.get_layer(), new_contained_box);
            ExpansionDoor new_door = new ExpansionDoor(p_room, new_room, 1);
            p_room.add_door(new_door);
            new_room.add_door(new_door);
        }
    }
    
    /**
     * Calculates all touching neighbours of p_room and sorts them in
     * counterclock sense around the boundary  of the room shape.
     */
    private static SortedOrthogonalRoomNeighbours calculate_neighbours(ExpansionRoom p_room, int p_net_no,
            ShapeSearchTree p_autoroute_search_tree, int p_room_id_no)
    {
        TileShape room_shape = p_room.get_shape();
        if (!(room_shape instanceof IntBox))
        {
            System.out.println("SortedOrthogonalRoomNeighbours.calculate: IntBox expected for room_shape");
            return null;
        }
        IntBox room_box = (IntBox) room_shape;
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
            System.out.println("SortedOrthogonalRoomNeighbours.calculate: unexpected expansion room type");
            return null;
        }
        SortedOrthogonalRoomNeighbours result = new SortedOrthogonalRoomNeighbours(p_room, completed_room);
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
            if ((completed_room instanceof CompleteFreeSpaceExpansionRoom) && !curr_object.is_trace_obstacle(p_net_no))
            {
                ((CompleteFreeSpaceExpansionRoom) completed_room).calculate_target_doors(curr_entry,
                        p_net_no, p_autoroute_search_tree);
                continue;
            }
            TileShape curr_shape =
                    curr_object.get_tree_shape(p_autoroute_search_tree, curr_entry.shape_index_in_object);
            if (!(curr_shape instanceof IntBox))
            {
                System.out.println("OrthogonalAutorouteEngine:calculate_sorted_neighbours: IntBox expected for curr_shape");
                return null;
            }
            IntBox curr_box = (IntBox) curr_shape;
            IntBox intersection = room_box.intersection(curr_box);
            int dimension = intersection.dimension();
            if (dimension > 1 && completed_room instanceof ObstacleExpansionRoom)
            {
                if (curr_object instanceof Item)
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
                continue;
            }
            if (dimension < 0)
            {
                
                System.out.println("AutorouteEngine.calculate_doors: dimension >= 0 expected");
                continue;
            }
            result.add_sorted_neighbour(curr_box, intersection);
            if (dimension > 0)
            {
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
                        ExpansionDoor new_door = new ExpansionDoor(completed_room, neighbour_room);
                        neighbour_room.add_door(new_door);
                        completed_room.add_door(new_door);
                    }
                }
            }
        }
        return result;
    }
    
    private void calculate_new_incomplete_rooms(AutorouteEngine p_autoroute_engine)
    {
        IntBox board_bounds = p_autoroute_engine.board.bounding_box;
        SortedRoomNeighbour prev_neighbour = this.sorted_neighbours.last();
        Iterator<SortedRoomNeighbour> it = this.sorted_neighbours.iterator();
        
        while (it.hasNext())
        {
            SortedRoomNeighbour next_neighbour = it.next();
            
            if (!next_neighbour.intersection.intersects(prev_neighbour.intersection))
            {
                // create a door to a new incomplete expansion room between
                // the last corner of the previous neighbour and the first corner of the
                // current neighbour.
                if (next_neighbour.first_touching_side == 0)
                {
                    if (prev_neighbour.last_touching_side == 0)
                    {
                        if (prev_neighbour.intersection.ur.x < next_neighbour.intersection.ll.x)
                        {
                            insert_incomplete_room(p_autoroute_engine, prev_neighbour.intersection.ur.x, board_bounds.ll.y,
                                    next_neighbour.intersection.ll.x, this.room_shape.ll.y);
                        }
                    }
                    else
                    {
                        if (prev_neighbour.intersection.ll.y > this.room_shape.ll.y
                                || next_neighbour.intersection.ll.x > this.room_shape.ll.x)
                        {
                            if (is_obstacle_expansion_room)
                            {
                                // no 2-dim doors between obstacle_expansion_rooms and free space rooms allowed.
                                if (prev_neighbour.last_touching_side == 3)
                                {
                                    insert_incomplete_room(p_autoroute_engine, board_bounds.ll.x, room_shape.ll.y,
                                            room_shape.ll.x, prev_neighbour.intersection.ll.y);
                                }
                                insert_incomplete_room(p_autoroute_engine, room_shape.ll.x, board_bounds.ll.y,
                                        next_neighbour.intersection.ll.x, room_shape.ll.y);
                            }
                            else
                            {
                                insert_incomplete_room(p_autoroute_engine, board_bounds.ll.x, board_bounds.ll.y,
                                        next_neighbour.intersection.ll.x, prev_neighbour.intersection.ll.y);
                            }
                        }
                    }
                }
                else if (next_neighbour.first_touching_side == 1)
                {
                    if (prev_neighbour.last_touching_side == 1)
                    {
                        if (prev_neighbour.intersection.ur.y < next_neighbour.intersection.ll.y)
                        {
                            insert_incomplete_room(p_autoroute_engine, this.room_shape.ur.x, prev_neighbour.intersection.ur.y,
                                    board_bounds.ur.x, next_neighbour.intersection.ll.y );
                        }
                    }
                    else
                    {
                        if (prev_neighbour.intersection.ur.x < this.room_shape.ur.x
                                || next_neighbour.intersection.ll.y > this.room_shape.ll.y)
                        {
                            if (is_obstacle_expansion_room)
                            {
                                // no 2-dim doors between obstacle_expansion_rooms and free space rooms allowed.
                                if (prev_neighbour.last_touching_side == 0)
                                {
                                    insert_incomplete_room(p_autoroute_engine, prev_neighbour.intersection.ur.x, board_bounds.ll.y,
                                            room_shape.ur.x, room_shape.ll.y);
                                }
                                insert_incomplete_room(p_autoroute_engine, room_shape.ur.x, room_shape.ll.y,
                                        room_shape.ur.x, next_neighbour.intersection.ll.y );
                            }
                            else
                            {
                                insert_incomplete_room(p_autoroute_engine, prev_neighbour.intersection.ur.x, board_bounds.ll.y,
                                        board_bounds.ur.x, next_neighbour.intersection.ll.y);
                            }
                        }
                    }
                }
                else if (next_neighbour.first_touching_side == 2)
                {
                    if (prev_neighbour.last_touching_side == 2)
                    {
                        if (prev_neighbour.intersection.ll.x >  next_neighbour.intersection.ur.x)
                        {
                            insert_incomplete_room(p_autoroute_engine, next_neighbour.intersection.ur.x, this.room_shape.ur.y,
                                    prev_neighbour.intersection.ll.x, board_bounds.ur.y);
                        }
                    }
                    else
                    {
                        if (prev_neighbour.intersection.ur.y < this.room_shape.ur.y
                                || next_neighbour.intersection.ur.x < this.room_shape.ur.x)
                        {
                            if (is_obstacle_expansion_room)
                            {
                                // no 2-dim doors between obstacle_expansion_rooms and free space rooms allowed.
                                if (prev_neighbour.last_touching_side == 1)
                                {
                                    insert_incomplete_room(p_autoroute_engine, room_shape.ur.x, prev_neighbour.intersection.ur.y,
                                            board_bounds.ur.x, room_shape.ur.y);
                                }
                                insert_incomplete_room(p_autoroute_engine, next_neighbour.intersection.ur.x, room_shape.ur.y,
                                        room_shape.ur.x, board_bounds.ur.y );
                            }
                            else
                            {
                                insert_incomplete_room(p_autoroute_engine, next_neighbour.intersection.ur.x, prev_neighbour.intersection.ur.y,
                                        board_bounds.ur.x, board_bounds.ur.y);
                            }
                        }
                    }
                }
                else if (next_neighbour.first_touching_side == 3)
                {
                    if (prev_neighbour.last_touching_side == 3)
                    {
                        if (prev_neighbour.intersection.ll.y >  next_neighbour.intersection.ur.y)
                        {
                            insert_incomplete_room(p_autoroute_engine, board_bounds.ll.x, next_neighbour.intersection.ur.y,
                                    this.room_shape.ll.x, prev_neighbour.intersection.ll.y);
                        }
                    }
                    else
                    {
                        if (next_neighbour.intersection.ur.y < this.room_shape.ur.y
                                || prev_neighbour.intersection.ll.x > this.room_shape.ll.x)
                        {
                            if (is_obstacle_expansion_room)
                            {
                                // no 2-dim doors between obstacle_expansion_rooms and free space rooms allowed.
                                if (prev_neighbour.last_touching_side == 2)
                                {
                                    insert_incomplete_room(p_autoroute_engine, room_shape.ll.x, room_shape.ur.y,
                                            prev_neighbour.intersection.ll.x, board_bounds.ur.y);
                                }
                                insert_incomplete_room(p_autoroute_engine, board_bounds.ll.x, next_neighbour.intersection.ur.y,
                                        room_shape.ll.x, room_shape.ur.y);
                            }
                            else
                            {
                                insert_incomplete_room(p_autoroute_engine, board_bounds.ll.x, next_neighbour.intersection.ur.y,
                                        prev_neighbour.intersection.ll.x, board_bounds.ur.y);
                            }
                        }
                    }
                }
                else
                {
                    System.out.println("SortedOrthogonalRoomNeighbour.calculate_new_incomplete: illegal touching side");
                }
            }
            prev_neighbour = next_neighbour;
        }
    }
    
    private void insert_incomplete_room(AutorouteEngine p_autoroute_engine, int p_ll_x, int p_ll_y, int p_ur_x, int p_ur_y)
    {
        IntBox new_incomplete_room_shape = new IntBox(p_ll_x, p_ll_y, p_ur_x, p_ur_y);
        if (new_incomplete_room_shape.dimension() == 2)
        {
            IntBox new_contained_shape = this.room_shape.intersection(new_incomplete_room_shape);
            if (!new_contained_shape.is_empty())
            {
                int door_dimension = new_incomplete_room_shape.intersection(this.room_shape).dimension();
                if (door_dimension > 0)
                {
                    FreeSpaceExpansionRoom new_room =
                            p_autoroute_engine.add_incomplete_expansion_room(new_incomplete_room_shape, this.from_room.get_layer(), new_contained_shape);
                    ExpansionDoor new_door = new ExpansionDoor(this.completed_room, new_room, door_dimension);
                    this.completed_room.add_door(new_door);
                    new_room.add_door(new_door);
                }
            }
        }
    }
    
    /** Creates a new instance of SortedOrthogonalRoomNeighbours */
    private SortedOrthogonalRoomNeighbours(ExpansionRoom p_from_room, CompleteExpansionRoom p_completed_room)
    {
        from_room = p_from_room;
        completed_room = p_completed_room;
        is_obstacle_expansion_room = p_from_room instanceof ObstacleExpansionRoom;
        room_shape = (IntBox) p_completed_room.get_shape();
        sorted_neighbours = new TreeSet<SortedRoomNeighbour>();
        edge_interiour_touches_obstacle = new boolean[4];
        for (int i = 0; i < 4; ++i)
        {
            edge_interiour_touches_obstacle[i] = false;
        }
    }
    
    /**
     * Check, that each side of the romm shape has at least one touching neighbour.
     * Otherwise the room shape will be improved the by enlarging.
     * Returns true, if the room shape was changed.
     */
    private boolean try_remove_edge(int p_net_no, ShapeSearchTree p_autoroute_search_tree)
    {
        if (!(this.from_room instanceof IncompleteFreeSpaceExpansionRoom))
        {
            return false;
        }
        IncompleteFreeSpaceExpansionRoom curr_incomplete_room = (IncompleteFreeSpaceExpansionRoom) this.from_room;
        if (!(curr_incomplete_room.get_shape() instanceof IntBox))
        {
            System.out.println("SortedOrthogonalRoomNeighbours.try_remove_edge: IntBox expected for room_shape type");
            return false;
        }
        IntBox  room_box = (IntBox) curr_incomplete_room.get_shape();
        double room_area = room_box.area();
        
        int remove_edge_no = -1;
        for (int i = 0; i < 4; ++i)
        {
            if (!this.edge_interiour_touches_obstacle[i])
            {
                remove_edge_no = i;
                break;
            }
        }
        
        if (remove_edge_no >= 0)
        {
            // Touching neighbour missing at the edge side with index remove_edge_no
            // Remove the edge line and restart the algorithm.
            IntBox enlarged_box = remove_border_line( room_box, remove_edge_no);
            Collection<ExpansionDoor> door_list = this.completed_room.get_doors();
            TileShape ignore_shape = null;
            SearchTreeObject ignore_object = null;
            double max_door_area = 0;
            for (ExpansionDoor curr_door: door_list)
            {
                // insert the overlapping doors with CompleteFreeSpaceExpansionRooms
                // for the information in complete_shape about the objects to ignore.
                if (curr_door.dimension == 2)
                {
                    CompleteExpansionRoom other_room = curr_door.other_room(this.completed_room);
                    {
                        if (other_room instanceof CompleteFreeSpaceExpansionRoom)
                        {
                            TileShape curr_door_shape = curr_door.get_shape();
                            double curr_door_area = curr_door_shape.area();
                            if (curr_door_area > max_door_area)
                            {
                                max_door_area = curr_door_area;
                                ignore_shape = curr_door_shape;
                                ignore_object = (CompleteFreeSpaceExpansionRoom) other_room;
                            }
                        }
                    }
                }
            }
            IncompleteFreeSpaceExpansionRoom enlarged_room =
                    new IncompleteFreeSpaceExpansionRoom(enlarged_box, curr_incomplete_room.get_layer(),
                    curr_incomplete_room.get_contained_shape());
            Collection<IncompleteFreeSpaceExpansionRoom>  new_rooms =
                    p_autoroute_search_tree.complete_shape(enlarged_room, p_net_no, ignore_object, ignore_shape);
            if (new_rooms.size() == 1)
            {
                // Check, that the area increases to prevent endless loop.
                IncompleteFreeSpaceExpansionRoom new_room = new_rooms.iterator().next();
                if (new_room.get_shape().area() > room_area)
                {
                    curr_incomplete_room.set_shape(new_room.get_shape());
                    curr_incomplete_room.set_contained_shape(new_room.get_contained_shape());
                    return true;
                }
            }
        }
        return false;
    }
    
    private static IntBox remove_border_line( IntBox p_room_box, int p_remove_edge_no)
    {
        IntBox result;
        if (p_remove_edge_no == 0)
        {
            result = new IntBox(p_room_box.ll.x, -Limits.CRIT_INT, p_room_box.ur.x, p_room_box.ur.y);
        }
        else if (p_remove_edge_no == 1)
        {
            result = new IntBox(p_room_box.ll.x, p_room_box.ll.y, Limits.CRIT_INT, p_room_box.ur.y);
        }
        else if (p_remove_edge_no == 2)
        {
            result = new IntBox(p_room_box.ll.x, p_room_box.ll.y, p_room_box.ur.x, Limits.CRIT_INT);
        }
        else if (p_remove_edge_no == 3)
        {
            result = new IntBox(-Limits.CRIT_INT, p_room_box.ll.y, p_room_box.ur.x, p_room_box.ur.y);
        }
        else
        {
            System.out.println("SortedOrthogonalRoomNeighbours.remove_border_line: illegal p_remove_edge_no");
            result = null;
        }
        return result;
    }
    
    private void add_sorted_neighbour(IntBox p_neighbour_shape, IntBox p_intersection)
    {
        SortedRoomNeighbour new_neighbour = new SortedRoomNeighbour(p_neighbour_shape, p_intersection);
        sorted_neighbours.add(new_neighbour);
    }
    
    public final CompleteExpansionRoom completed_room;
    public final SortedSet<SortedRoomNeighbour> sorted_neighbours;
    private final ExpansionRoom from_room;
    private final boolean is_obstacle_expansion_room;
    private final IntBox room_shape;
    
    private final boolean[] edge_interiour_touches_obstacle;
    
    /**
     * Helper class to sort the doors of an expansion room counterclockwise
     * arount the border of the room shape.
     */
    
    private class SortedRoomNeighbour implements Comparable<SortedRoomNeighbour>
    {
        public SortedRoomNeighbour(IntBox p_neighbour_shape, IntBox p_intersection)
        {
            shape = p_neighbour_shape;
            intersection = p_intersection;
            
            if( p_intersection.ll.y == room_shape.ll.y
                    && p_intersection.ur.x > room_shape.ll.x && p_intersection.ll.x < room_shape.ur.x)
            {
                edge_interiour_touches_obstacle[0] = true;
            }
            if( p_intersection.ur.x == room_shape.ur.x
                    && p_intersection.ur.y > room_shape.ll.y && p_intersection.ll.y < room_shape.ur.y)
            {
                edge_interiour_touches_obstacle[1] = true;
            }
            if( p_intersection.ur.y == room_shape.ur.y
                    && p_intersection.ur.x > room_shape.ll.x && p_intersection.ll.x < room_shape.ur.x)
            {
                edge_interiour_touches_obstacle[2] = true;
            }
            if( p_intersection.ll.x == room_shape.ll.x
                    && p_intersection.ur.y > room_shape.ll.y && p_intersection.ll.y < room_shape.ur.y)
            {
                edge_interiour_touches_obstacle[3] = true;
            }
            
            if (p_intersection.ll.y == room_shape.ll.y && p_intersection.ll.x > room_shape.ll.x)
            {
                this.first_touching_side = 0;
            }
            else if (p_intersection.ur.x == room_shape.ur.x && p_intersection.ll.y > room_shape.ll.y)
            {
                this.first_touching_side = 1;
            }
            else if (p_intersection.ur.y == room_shape.ur.y )
            {
                this.first_touching_side = 2;
            }
            else if (p_intersection.ll.x == room_shape.ll.x)
            {
                this.first_touching_side = 3;
            }
            else
            {
                System.out.println("SortedRoomNeighbour: case not expected");
                this.first_touching_side = -1;
            }
            
            if (p_intersection.ll.x == room_shape.ll.x && p_intersection.ll.y > room_shape.ll.y)
            {
                this.last_touching_side = 3;
            }
            else if (p_intersection.ur.y == room_shape.ur.y && p_intersection.ll.x > room_shape.ll.x)
            {
                this.last_touching_side = 2;
            }
            else if (p_intersection.ur.x == room_shape.ur.x)
            {
                this.last_touching_side = 1;
            }
            else if (p_intersection.ll.y == room_shape.ll.y)
            {
                this.last_touching_side = 0;
            }
            else
            {
                System.out.println("SortedRoomNeighbour: case not expected");
                this.last_touching_side = -1;
            }
        }
        
        /**
         * Compare function for or sorting the neighbours in counterclock sense
         * around the border of the room shape in ascending order.
         */
        public int compareTo(SortedRoomNeighbour p_other)
        {
            if (this.first_touching_side > p_other.first_touching_side)
            {
                return 1;
            }
            if (this.first_touching_side < p_other.first_touching_side)
            {
                return -1;
            }
            
            // now the first touch of this and p_other is at the same side
            IntBox is1 = this.intersection;
            IntBox is2 = p_other.intersection;
            int cmp_value;
            
            if (first_touching_side == 0)
            {
                cmp_value = is1.ll.x - is2.ll.x;
            }
            else if (first_touching_side == 1)
            {
                cmp_value = is1.ll.y - is2.ll.y;
            }
            else if (first_touching_side == 2)
            {
                cmp_value = is2.ur.x - is1.ur.x;
            }
            else if (first_touching_side == 3)
            {
                cmp_value = is2.ur.y - is1.ur.y;
            }
            else
            {
                System.out.println("SortedRoomNeighbour.compareTo: first_touching_side out of range ");
                return 0;
            }
            if (cmp_value == 0)
            {
                // The first touching points of this neighbour and p_other with the room shape are equal.
                // Compare the last touching points.
                int this_touching_side_diff =  (this.last_touching_side - this.first_touching_side + 4) % 4;
                int other_touching_side_diff =  (p_other.last_touching_side - p_other.first_touching_side + 4) % 4;
                if (this_touching_side_diff > other_touching_side_diff)
                {
                    return 1;
                }
                if (this_touching_side_diff < other_touching_side_diff)
                {
                    return -1;
                }
                
                // now the last touch of this and p_other is at the same side
                if (last_touching_side == 0)
                {
                    cmp_value = is1.ur.x - is2.ur.x;
                }
                else if (last_touching_side == 1)
                {
                    cmp_value = is1.ur.y - is2.ur.y;
                }
                else if (last_touching_side == 2)
                {
                    cmp_value = is2.ll.x - is1.ll.x;
                }
                else if (last_touching_side == 3)
                {
                    cmp_value = is2.ll.y - is1.ll.y;
                }
                else
                {
                    System.out.println("SortedRoomNeighbour.compareTo: first_touching_side out of range ");
                    return 0;
                }
            }
            return cmp_value;
        }
        
        /** The shape of the neighbour room */
        public final IntBox shape;
        
        /** The intersection of tnis ExpansionRoom shape with the neighbour_shape */
        public final IntBox intersection;
        
        /** The first side of the room shape, where the neighbour_shape touches */
        public final int first_touching_side;
        
        /** The last side of the room shape, where the neighbour_shape touches */
        public final int last_touching_side;
    }
}
