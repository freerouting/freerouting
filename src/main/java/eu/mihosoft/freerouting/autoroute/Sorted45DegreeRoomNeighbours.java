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
 * Sorted45DegreeRoomNeighbours.java
 *
 * Created on 6. Juli 2007, 07:28
 *
 */

package autoroute;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.SortedSet;
import java.util.TreeSet;

import datastructures.ShapeTree;

import geometry.planar.Limits;
import geometry.planar.IntOctagon;
import geometry.planar.IntPoint;
import geometry.planar.TileShape;
import geometry.planar.FloatPoint;

import board.ShapeSearchTree;
import board.SearchTreeObject;
import board.Item;

/**
 *
 * @author Alfons Wirtz
 */
public class Sorted45DegreeRoomNeighbours
{
    
    public static CompleteExpansionRoom calculate(ExpansionRoom p_room, AutorouteEngine p_autoroute_engine)
    {
        int net_no = p_autoroute_engine.get_net_no();
        Sorted45DegreeRoomNeighbours room_neighbours  = Sorted45DegreeRoomNeighbours.calculate_neighbours(p_room, net_no,
                p_autoroute_engine.autoroute_search_tree, p_autoroute_engine.generate_room_id_no());
        if (room_neighbours == null)
        {
            return null;
        }
        
        // Check, that each side of the romm shape has at least one touching neighbour.
        // Otherwise improve the room shape by enlarging.
        boolean edge_removed = room_neighbours.try_remove_edge_line(net_no, p_autoroute_engine.autoroute_search_tree);
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
                room_neighbours.calculate_edge_incomplete_rooms_of_obstacle_expansion_room(0, 7, p_autoroute_engine);
            }
        }
        else
        {
            room_neighbours.calculate_new_incomplete_rooms(p_autoroute_engine);
        }
        return result;
    }
    
    /**
     * Calculates all touching neighbours of p_room and sorts them in
     * counterclock sense around the boundary  of the room shape.
     */
    private static Sorted45DegreeRoomNeighbours calculate_neighbours(ExpansionRoom p_room, int p_net_no,
            ShapeSearchTree p_autoroute_search_tree, int p_room_id_no)
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
            System.out.println("Sorted45DegreeRoomNeighbours.calculate_neighbours: unexpected expansion room type");
            return null;
        }
        IntOctagon room_oct = room_shape.bounding_octagon();
        Sorted45DegreeRoomNeighbours result = new Sorted45DegreeRoomNeighbours(p_room, completed_room);
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
            IntOctagon curr_oct = curr_shape.bounding_octagon();
            IntOctagon intersection = room_oct.intersection(curr_oct);
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
                // may happen at a corner from 2 diagonal lines with non integer  coordinates (--.5, ---.5).
                continue;
            }
            result.add_sorted_neighbour(curr_oct, intersection);
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
    
    
    /** Creates a new instance of Sorted45DegreeRoomNeighbours */
    private Sorted45DegreeRoomNeighbours(ExpansionRoom p_from_room, CompleteExpansionRoom p_completed_room)
    {
        from_room = p_from_room;
        completed_room = p_completed_room;
        room_shape = p_completed_room.get_shape().bounding_octagon();
        sorted_neighbours = new TreeSet<SortedRoomNeighbour>();
        
        edge_interiour_touches_obstacle = new boolean[8];
        for (int i = 0; i < 8; ++i)
        {
            edge_interiour_touches_obstacle[i] = false;
        }
    }
    
    private void add_sorted_neighbour(IntOctagon p_neighbour_shape, IntOctagon p_intersection)
    {
        SortedRoomNeighbour new_neighbour = new SortedRoomNeighbour(p_neighbour_shape, p_intersection);
        if (new_neighbour.last_touching_side >= 0)
        {
            sorted_neighbours.add(new_neighbour);
        }
    }
    
    /**
     * Calculates an incomplete room for each edge side from p_from_side_no to p_to_side_no.
     */
    private void calculate_edge_incomplete_rooms_of_obstacle_expansion_room(int p_from_side_no, int p_to_side_no, AutorouteEngine p_autoroute_engine)
    {
        if (!(this.from_room instanceof ObstacleExpansionRoom))
        {
            System.out.println("Sorted45DegreeRoomNeighbours.calculate_side_incomplete_rooms_of_obstacle_expansion_room: ObstacleExpansionRoom expected for this.from_room");
            return;
        }
        IntOctagon board_bounding_oct = p_autoroute_engine.board.get_bounding_box().bounding_octagon();
        IntPoint curr_corner = this.room_shape.corner(p_from_side_no);
        int curr_side_no = p_from_side_no;
        for (;;)
        {
            int next_side_no = (curr_side_no + 1) % 8;
            IntPoint next_corner  = this.room_shape.corner(next_side_no);
            if (!curr_corner.equals(next_corner))
            {
                int lx = board_bounding_oct.lx;
                int ly = board_bounding_oct.ly;
                int rx = board_bounding_oct.rx;
                int uy = board_bounding_oct.uy;
                int ulx = board_bounding_oct.ulx;
                int lrx = board_bounding_oct.lrx;
                int llx = board_bounding_oct.llx;
                int urx = board_bounding_oct.urx;
                if (curr_side_no == 0)
                {
                    uy = this.room_shape.ly;
                }
                else if (curr_side_no == 1)
                {
                    ulx = this.room_shape.lrx;
                }
                else if (curr_side_no == 2)
                {
                    lx = this.room_shape.rx;
                }
                else if (curr_side_no == 3)
                {
                    llx = this.room_shape.urx;
                }
                else if (curr_side_no == 4)
                {
                    ly = this.room_shape.uy;
                }
                else if (curr_side_no == 5)
                {
                    lrx = this.room_shape.ulx;
                }
                else if (curr_side_no == 6)
                {
                    rx = this.room_shape.lx;
                }
                else if (curr_side_no == 7)
                {
                    urx = this.room_shape.llx;
                }
                else
                {
                    System.out.println("SortedOrthoganelRoomNeighbours.calculate_edge_incomplete_rooms_of_obstacle_expansion_room: curr_side_no illegal");
                    return;
                }
                insert_incomplete_room(p_autoroute_engine, lx, ly, rx, uy, ulx, lrx, llx, urx);
            }
            if (curr_side_no == p_to_side_no)
            {
                break;
            }
            curr_side_no = next_side_no;
        }
    }
    
    private static IntOctagon remove_not_touching_border_lines( IntOctagon p_room_oct,
            boolean[] p_edge_interiour_touches_obstacle)
    {
        int lx;
        if (p_edge_interiour_touches_obstacle[6])
        {
            lx = p_room_oct.lx;
        }
        else
        {
            lx = -Limits.CRIT_INT;
        }
        
        int ly;
        if (p_edge_interiour_touches_obstacle[0])
        {
            ly = p_room_oct.ly;
        }
        else
        {
            ly = -Limits.CRIT_INT;
        }
        
        int rx;
        if (p_edge_interiour_touches_obstacle[2])
        {
            rx = p_room_oct.rx;
        }
        else
        {
            rx = Limits.CRIT_INT;
        }
        
        
        int uy;
        if (p_edge_interiour_touches_obstacle[4])
        {
            uy = p_room_oct.uy;
        }
        else
        {
            uy = Limits.CRIT_INT;
        }
        
        int ulx;
        if (p_edge_interiour_touches_obstacle[5])
        {
            ulx = p_room_oct.ulx;
        }
        else
        {
            ulx = -Limits.CRIT_INT;
        }
        
        int lrx;
        if (p_edge_interiour_touches_obstacle[1])
        {
            lrx = p_room_oct.lrx;
        }
        else
        {
            lrx = Limits.CRIT_INT;
        }
        
        int llx;
        if (p_edge_interiour_touches_obstacle[7])
        {
            llx = p_room_oct.llx;
        }
        else
        {
            llx = -Limits.CRIT_INT;
        }
        
        int urx;
        if (p_edge_interiour_touches_obstacle[3])
        {
            urx = p_room_oct.urx;
        }
        else
        {
            urx = Limits.CRIT_INT;
        }
        
        IntOctagon result = new IntOctagon( lx, ly, rx, uy, ulx, lrx, llx, urx);
        return result.normalize();
    }
    /**
     * Check, that each side of the romm shape has at least one touching neighbour.
     * Otherwise the room shape will be improved the by enlarging.
     * Returns true, if the room shape was changed.
     */
    private boolean try_remove_edge_line(int p_net_no, ShapeSearchTree p_autoroute_search_tree)
    {
        if (!(this.from_room instanceof IncompleteFreeSpaceExpansionRoom))
        {
            return false;
        }
        IncompleteFreeSpaceExpansionRoom curr_incomplete_room = (IncompleteFreeSpaceExpansionRoom) this.from_room;
        if (!(curr_incomplete_room.get_shape() instanceof IntOctagon))
        {
            System.out.println("Sorted45DegreeRoomNeighbours.try_remove_edge_line: IntOctagon expected for room_shape type");
            return false;
        }
        IntOctagon  room_oct = (IntOctagon) curr_incomplete_room.get_shape();
        double room_area = room_oct.area();
        
        boolean try_remove_edge_lines = false;
        for (int i = 0; i < 8; ++i)
        {
            if (!this.edge_interiour_touches_obstacle[i])
            {
                FloatPoint prev_corner = this.room_shape.corner_approx(i);
                FloatPoint next_corner = this.room_shape.corner_approx(this.room_shape.next_no(i));
                if(prev_corner.distance_square(next_corner) > 1)
                {
                    try_remove_edge_lines = true;
                    break;
                }
            }
        }
        
        if (try_remove_edge_lines)
        {
            // Touching neighbour missing at the edge side with index remove_edge_no
            // Remove the edge line and restart the algorithm.
            
            IntOctagon enlarged_oct = remove_not_touching_border_lines( room_oct, this.edge_interiour_touches_obstacle);
            
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
                    new IncompleteFreeSpaceExpansionRoom(enlarged_oct, curr_incomplete_room.get_layer(),
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
    
    /**
     * Inserts a new incomplete room with an octagon shape.
     */
    private void insert_incomplete_room(AutorouteEngine p_autoroute_engine, int p_lx, int p_ly, int p_rx, int p_uy,
            int p_ulx, int p_lrx, int p_llx, int p_urx)
    {
        IntOctagon new_incomplete_room_shape = new IntOctagon(p_lx, p_ly, p_rx, p_uy, p_ulx, p_lrx, p_llx, p_urx);
        new_incomplete_room_shape = new_incomplete_room_shape.normalize();
        if (new_incomplete_room_shape.dimension() == 2)
        {
            IntOctagon new_contained_shape = this.room_shape.intersection(new_incomplete_room_shape);
            if (!new_contained_shape.is_empty())
            {
                int door_dimension = new_contained_shape.dimension();
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
    
    private void calculate_new_incomplete_rooms_for_obstacle_expansion_room(SortedRoomNeighbour p_prev_neighbour,
            SortedRoomNeighbour p_next_neighbour, AutorouteEngine p_autoroute_engine)
    {
        int from_side_no = p_prev_neighbour.last_touching_side;
        int to_side_no = p_next_neighbour.first_touching_side;
        if (from_side_no == to_side_no && p_prev_neighbour != p_next_neighbour)
        {
            // no return in case of only 1 neighbour.
            return;
        }
        IntOctagon board_bounding_oct  = p_autoroute_engine.board.bounding_box.bounding_octagon();
        
        // insert the new incomplete room from p_prev_neighbour to the next corner of the room shape.
        
        int lx = board_bounding_oct.lx;
        int ly = board_bounding_oct.ly;
        int rx = board_bounding_oct.rx;
        int uy = board_bounding_oct.uy;
        int ulx = board_bounding_oct.ulx;
        int lrx = board_bounding_oct.lrx;
        int llx = board_bounding_oct.llx;
        int urx = board_bounding_oct.urx;
        if (from_side_no == 0)
        {
            uy = this.room_shape.ly;
            ulx = p_prev_neighbour.intersection.lrx;
        }
        else if (from_side_no == 1)
        {
            ulx = this.room_shape.lrx;
            lx = p_prev_neighbour.intersection.rx;
        }
        else if (from_side_no == 2)
        {
            lx = this.room_shape.rx;
            llx = p_prev_neighbour.intersection.urx;
        }
        else if (from_side_no == 3)
        {
            llx = this.room_shape.urx;
            ly = p_prev_neighbour.intersection.uy;
        }
        else if (from_side_no == 4)
        {
            ly = this.room_shape.uy;
            lrx = p_prev_neighbour.intersection.ulx;
        }
        else if (from_side_no == 5)
        {
            lrx = this.room_shape.ulx;
            rx = p_prev_neighbour.intersection.lx;
        }
        else if (from_side_no == 6)
        {
            rx = this.room_shape.lx;
            urx = p_prev_neighbour.intersection.llx;
        }
        else if (from_side_no == 7)
        {
            urx = this.room_shape.llx;
            uy = p_prev_neighbour.intersection.ly;
        }
        insert_incomplete_room(p_autoroute_engine, lx, ly, rx, uy, ulx, lrx, llx, urx);
        
        // insert the new incomplete room from p_prev_neighbour to the next corner of the room shape.
        
        lx = board_bounding_oct.lx;
        ly = board_bounding_oct.ly;
        rx = board_bounding_oct.rx;
        uy = board_bounding_oct.uy;
        ulx = board_bounding_oct.ulx;
        lrx = board_bounding_oct.lrx;
        llx = board_bounding_oct.llx;
        urx = board_bounding_oct.urx;
        
        if (to_side_no == 0)
        {
            uy = this.room_shape.ly;
            urx = p_next_neighbour.intersection.llx;
        }
        else if (to_side_no == 1)
        {
            ulx = this.room_shape.lrx;
            uy = p_next_neighbour.intersection.ly;
        }
        else if (to_side_no == 2)
        {
            lx = this.room_shape.rx;
            ulx = p_next_neighbour.intersection.lrx;
        }
        else if (to_side_no == 3)
        {
            llx = this.room_shape.urx;
            lx = p_next_neighbour.intersection.rx;
        }
        else if (to_side_no == 4)
        {
            ly = this.room_shape.uy;
            llx = p_next_neighbour.intersection.urx;
        }
        else if (to_side_no == 5)
        {
            lrx = this.room_shape.ulx;
            ly = p_next_neighbour.intersection.uy;
        }
        else if (to_side_no == 6)
        {
            rx = this.room_shape.lx;
            lrx = p_next_neighbour.intersection.ulx;
        }
        else if (to_side_no == 7)
        {
            urx = this.room_shape.llx;
            rx = p_next_neighbour.intersection.lx;
        }
        insert_incomplete_room(p_autoroute_engine, lx, ly, rx, uy, ulx, lrx, llx, urx);
        
        // Insert the new incomplete rooms on the intermediate free sides of the obstacle expansion room.
        int curr_from_side_no = (from_side_no + 1) % 8;
        if (curr_from_side_no == to_side_no)
        {
            return;
        }
        int curr_to_side_no = (to_side_no + 7) % 8;
        this.calculate_edge_incomplete_rooms_of_obstacle_expansion_room(curr_from_side_no,
                curr_to_side_no, p_autoroute_engine);
    }
    
    private void calculate_new_incomplete_rooms(AutorouteEngine p_autoroute_engine)
    {
        IntOctagon board_bounding_oct  = p_autoroute_engine.board.bounding_box.bounding_octagon();
        SortedRoomNeighbour prev_neighbour = this.sorted_neighbours.last();
        if (this.from_room instanceof ObstacleExpansionRoom && this.sorted_neighbours.size() == 1)
        {
            // ObstacleExpansionRoom has only only 1 neighbour
            calculate_new_incomplete_rooms_for_obstacle_expansion_room(prev_neighbour, prev_neighbour, p_autoroute_engine);
            return;
        }
        Iterator<SortedRoomNeighbour> it = this.sorted_neighbours.iterator();
        
        while (it.hasNext())
        {
            SortedRoomNeighbour next_neighbour = it.next();
            
            boolean insert_incomplete_room;
            
            if (this.completed_room instanceof ObstacleExpansionRoom && this.sorted_neighbours.size() == 2)
            {
                // check, if this site is touching or open.
                TileShape intersection = next_neighbour.intersection.intersection(prev_neighbour.intersection);
                if (intersection.is_empty())
                {
                    insert_incomplete_room = true;
                }
                else if (intersection.dimension() >= 1)
                {
                    insert_incomplete_room = false;
                }
                else // dimension = 1
                {
                    if (prev_neighbour.last_touching_side == next_neighbour.first_touching_side)
                    {
                        // touch along the side of the room shape
                        insert_incomplete_room = false;
                    }
                    else if(prev_neighbour.last_touching_side == (next_neighbour.first_touching_side + 1) % 8)
                    {
                        // touch at a corner of the room shape
                        insert_incomplete_room = false;
                    }
                    else
                    {
                        insert_incomplete_room = true;
                    }
                }
            }
            else
            {
                // the 2 neigbours do not touch
                insert_incomplete_room  = !next_neighbour.intersection.intersects(prev_neighbour.intersection);
            }
            
            
            if (insert_incomplete_room)
            {
                // create a door to a new incomplete expansion room between
                // the last corner of the previous neighbour and the first corner of the
                // current neighbour
                
                if (this.from_room instanceof ObstacleExpansionRoom &&
                        next_neighbour.first_touching_side != prev_neighbour.last_touching_side)
                {
                    calculate_new_incomplete_rooms_for_obstacle_expansion_room(prev_neighbour, next_neighbour, p_autoroute_engine);
                }
                else
                {
                    int lx = board_bounding_oct.lx;
                    int ly = board_bounding_oct.ly;
                    int rx = board_bounding_oct.rx;
                    int uy = board_bounding_oct.uy;
                    int ulx = board_bounding_oct.ulx;
                    int lrx = board_bounding_oct.lrx;
                    int llx = board_bounding_oct.llx;
                    int urx = board_bounding_oct.urx;
                    
                    if (next_neighbour.first_touching_side == 0)
                    {
                        if (prev_neighbour.intersection.llx < next_neighbour.intersection.llx)
                        {
                            urx = next_neighbour.intersection.llx;
                            uy =  prev_neighbour.intersection.ly;
                            if (prev_neighbour.last_touching_side == 0)
                            {
                                ulx = prev_neighbour.intersection.lrx;
                            }
                        }
                        else if (prev_neighbour.intersection.llx > next_neighbour.intersection.llx)
                        {
                            rx =  next_neighbour.intersection.lx;
                            urx = prev_neighbour.intersection.llx;
                        }
                        else // prev_neighbour.intersection.llx == next_neighbour.intersection.llx
                        {
                            urx = next_neighbour.intersection.llx;
                        }
                    }
                    else if (next_neighbour.first_touching_side == 1)
                    {
                        if (prev_neighbour.intersection.ly < next_neighbour.intersection.ly)
                        {
                            uy = next_neighbour.intersection.ly;
                            ulx = prev_neighbour.intersection.lrx;
                            if (prev_neighbour.last_touching_side == 1)
                            {
                                lx = prev_neighbour.intersection.rx;
                            }
                        }
                        else if (prev_neighbour.intersection.ly > next_neighbour.intersection.ly)
                        {
                            uy = prev_neighbour.intersection.ly;
                            urx = next_neighbour.intersection.llx;
                        }
                        else // prev_neighbour.intersection.ly == next_neighbour.intersection.ly
                        {
                            uy = next_neighbour.intersection.ly;
                        }
                    }
                    else if (next_neighbour.first_touching_side == 2)
                    {
                        if (prev_neighbour.intersection.lrx > next_neighbour.intersection.lrx)
                        {
                            ulx = next_neighbour.intersection.lrx;
                            lx =  prev_neighbour.intersection.rx;
                            if (prev_neighbour.last_touching_side == 2)
                            {
                                llx = prev_neighbour.intersection.urx;
                            }
                        }
                        else if (prev_neighbour.intersection.lrx < next_neighbour.intersection.lrx)
                        {
                            uy =  next_neighbour.intersection.ly;
                            ulx = prev_neighbour.intersection.lrx;
                        }
                        else // prev_neighbour.intersection.lrx == next_neighbour.intersection.lrx
                        {
                            ulx = next_neighbour.intersection.lrx;
                        }
                    }
                    else if (next_neighbour.first_touching_side == 3)
                    {
                        if (prev_neighbour.intersection.rx > next_neighbour.intersection.rx)
                        {
                            lx = next_neighbour.intersection.rx;
                            llx = prev_neighbour.intersection.urx;
                            if (prev_neighbour.last_touching_side == 3)
                            {
                                ly = prev_neighbour.intersection.uy;
                            }
                        }
                        else if (prev_neighbour.intersection.rx < next_neighbour.intersection.rx)
                        {
                            lx = prev_neighbour.intersection.rx;
                            ulx = next_neighbour.intersection.lrx;
                        }
                        else // prev_neighbour.intersection.ry == next_neighbour.intersection.ry
                        {
                            lx = next_neighbour.intersection.rx;
                        }
                    }
                    else if (next_neighbour.first_touching_side == 4)
                    {
                        if (prev_neighbour.intersection.urx > next_neighbour.intersection.urx)
                        {
                            llx = next_neighbour.intersection.urx;
                            ly =  prev_neighbour.intersection.uy;
                            if (prev_neighbour.last_touching_side == 4)
                            {
                                lrx = prev_neighbour.intersection.ulx;
                            }
                        }
                        else if (prev_neighbour.intersection.urx < next_neighbour.intersection.urx)
                        {
                            lx =  next_neighbour.intersection.rx;
                            llx = prev_neighbour.intersection.urx;
                        }
                        else // prev_neighbour.intersection.urx == next_neighbour.intersection.urx
                        {
                            llx = next_neighbour.intersection.urx;
                        }
                    }
                    else if (next_neighbour.first_touching_side == 5)
                    {
                        if (prev_neighbour.intersection.uy > next_neighbour.intersection.uy)
                        {
                            ly = next_neighbour.intersection.uy;
                            lrx = prev_neighbour.intersection.ulx;
                            if (prev_neighbour.last_touching_side == 5)
                            {
                                rx = prev_neighbour.intersection.lx;
                            }
                        }
                        else if (prev_neighbour.intersection.uy < next_neighbour.intersection.uy)
                        {
                            ly = prev_neighbour.intersection.uy;
                            llx = next_neighbour.intersection.urx;
                        }
                        else // prev_neighbour.intersection.uy == next_neighbour.intersection.uy
                        {
                            ly = next_neighbour.intersection.uy;
                        }
                    }
                    else if (next_neighbour.first_touching_side == 6)
                    {
                        if (prev_neighbour.intersection.ulx < next_neighbour.intersection.ulx)
                        {
                            lrx = next_neighbour.intersection.ulx;
                            rx =  prev_neighbour.intersection.lx;
                            if (prev_neighbour.last_touching_side == 6)
                            {
                                urx = prev_neighbour.intersection.llx;
                            }
                        }
                        else if (prev_neighbour.intersection.ulx > next_neighbour.intersection.ulx)
                        {
                            ly =  next_neighbour.intersection.uy;
                            lrx = prev_neighbour.intersection.ulx;
                        }
                        else // prev_neighbour.intersection.ulx == next_neighbour.intersection.ulx
                        {
                            lrx = next_neighbour.intersection.ulx;
                        }
                    }
                    else if (next_neighbour.first_touching_side == 7)
                    {
                        if (prev_neighbour.intersection.lx < next_neighbour.intersection.lx)
                        {
                            rx = next_neighbour.intersection.lx;
                            urx = prev_neighbour.intersection.llx;
                            if (prev_neighbour.last_touching_side == 7)
                            {
                                uy = prev_neighbour.intersection.ly;
                            }
                        }
                        else if (prev_neighbour.intersection.lx > next_neighbour.intersection.lx)
                        {
                            rx = prev_neighbour.intersection.lx;
                            lrx = next_neighbour.intersection.ulx;
                        }
                        else // prev_neighbour.intersection.lx == next_neighbour.intersection.lx
                        {
                            rx = next_neighbour.intersection.lx;
                        }
                    }
                    else
                    {
                        System.out.println("Sorted45DegreeRoomNeighbour.calculate_new_incomplete: illegal touching side");
                    }
                    insert_incomplete_room(p_autoroute_engine, lx, ly, rx, uy, ulx, lrx, llx, urx);
                }
            }
            prev_neighbour = next_neighbour;
        }
    }
    
    public final CompleteExpansionRoom completed_room;
    public final SortedSet<SortedRoomNeighbour> sorted_neighbours;
    private final ExpansionRoom from_room;
    private final IntOctagon room_shape;
    
    private final boolean[] edge_interiour_touches_obstacle;
    
    /**
     * Helper class to sort the doors of an expansion room counterclockwise
     * arount the border of the room shape.
     */
    
    private class SortedRoomNeighbour implements Comparable<SortedRoomNeighbour>
    {
        
        /**
         * Creates a new instance of SortedRoomNeighbour and calculates the first and last
         * touching sides with the room shape.
         * this.last_touching_side will be -1, if sorting did not work because
         * the room_shape is contained in the neighbour shape.
         */
        public SortedRoomNeighbour(IntOctagon p_neighbour_shape, IntOctagon p_intersection)
        {
            shape = p_neighbour_shape;
            intersection = p_intersection;
            
            if (intersection.ly == room_shape.ly && intersection.llx > room_shape.llx)
            {
                this.first_touching_side = 0;
            }
            else if (intersection.lrx == room_shape.lrx && intersection.ly > room_shape.ly)
            {
                this.first_touching_side = 1;
            }
            else if (intersection.rx == room_shape.rx && intersection.lrx < room_shape.lrx)
            {
                this.first_touching_side = 2;
            }
            else if (intersection.urx == room_shape.urx && intersection.rx < room_shape.rx)
            {
                this.first_touching_side = 3;
            }
            else if (intersection.uy == room_shape.uy && intersection.urx < room_shape.urx)
            {
                this.first_touching_side = 4;
            }
            else if (intersection.ulx == room_shape.ulx && intersection.uy < room_shape.uy)
            {
                this.first_touching_side = 5;
            }
            else if (intersection.lx == room_shape.lx && intersection.ulx > room_shape.ulx)
            {
                this.first_touching_side = 6;
            }
            else if (intersection.llx == room_shape.llx && intersection.lx > room_shape.lx)
            {
                this.first_touching_side = 7;
            }
            else
            {
                // the room_shape may be contained in the neighbour_shape
                this.first_touching_side = -1;
                this.last_touching_side = -1;
                return;
            }
            
            if (intersection.llx == room_shape.llx && intersection.ly > room_shape.ly)
            {
                this.last_touching_side = 7;
            }
            else if (intersection.lx == room_shape.lx && intersection.llx > room_shape.llx)
            {
                this.last_touching_side = 6;
            }
            else if (intersection.ulx == room_shape.ulx && intersection.lx > room_shape.lx)
            {
                this.last_touching_side = 5;
            }
            else if (intersection.uy == room_shape.uy && intersection.ulx > room_shape.ulx)
            {
                this.last_touching_side = 4;
            }
            else if (intersection.urx == room_shape.urx && intersection.uy < room_shape.uy)
            {
                this.last_touching_side = 3;
            }
            else if (intersection.rx == room_shape.rx && intersection.urx < room_shape.urx)
            {
                this.last_touching_side = 2;
            }
            else if (intersection.lrx == room_shape.lrx && intersection.rx < room_shape.rx)
            {
                this.last_touching_side = 1;
            }
            else if (intersection.ly == room_shape.ly && intersection.lrx < room_shape.lrx)
            {
                this.last_touching_side = 0;
            }
            else
            {
                // the room_shape may be contained in the neighbour_shape
                this.last_touching_side = -1;
                return;
            }
            
            int next_side_no = this.first_touching_side;
            for (;;)
            {
                int curr_side_no = next_side_no;
                next_side_no = (next_side_no + 1) % 8;
                if (!edge_interiour_touches_obstacle[curr_side_no])
                {
                    boolean touch_only_at_corner = false;
                    if (curr_side_no == this.first_touching_side)
                    {
                        if (intersection.corner(curr_side_no).equals(room_shape.corner(next_side_no)))
                        {
                            touch_only_at_corner = true;
                        }
                    }
                    if (curr_side_no == this.last_touching_side)
                    {
                        if (intersection.corner(next_side_no).equals(room_shape.corner(curr_side_no)))
                        {
                            touch_only_at_corner = true;
                        }
                    }
                    if (!touch_only_at_corner)
                    {
                        edge_interiour_touches_obstacle[curr_side_no] = true;
                    }
                }
                if (curr_side_no == this.last_touching_side)
                {
                    break;
                }
                
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
            IntOctagon is1 = this.intersection;
            IntOctagon is2 = p_other.intersection;
            int cmp_value;
            
            if (first_touching_side == 0)
            {
                cmp_value = is1.corner(0).x - is2.corner(0).x;
            }
            else if (first_touching_side == 1)
            {
                cmp_value = is1.corner(1).x - is2.corner(1).x;
            }
            else if (first_touching_side == 2)
            {
                cmp_value = is1.corner(2).y - is2.corner(2).y;
            }
            else if (first_touching_side == 3)
            {
                cmp_value = is1.corner(3).y - is2.corner(3).y;
            }
            else if (first_touching_side == 4)
            {
                cmp_value = is2.corner(4).x - is1.corner(4).x;
            }
            else if (first_touching_side == 5)
            {
                cmp_value = is2.corner(5).x - is1.corner(5).x;
            }
            else if (first_touching_side == 6)
            {
                cmp_value = is2.corner(6).y - is1.corner(6).y;
            }
            else if (first_touching_side == 7)
            {
                cmp_value = is2.corner(7).y - is1.corner(7).y;
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
                int this_touching_side_diff =  (this.last_touching_side - this.first_touching_side + 8) % 8;
                int other_touching_side_diff =  (p_other.last_touching_side - p_other.first_touching_side + 8) % 8;
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
                    cmp_value = is1.corner(1).x - is2.corner(1).x;
                }
                else if (last_touching_side == 1)
                {
                    cmp_value = is1.corner(2).x - is2.corner(2).x;
                }
                else if (last_touching_side == 2)
                {
                    cmp_value = is1.corner(3).y - is2.corner(3).y;
                }
                else if (last_touching_side == 3)
                {
                    cmp_value = is1.corner(4).y - is2.corner(4).y;
                }
                else if (last_touching_side == 4)
                {
                    cmp_value = is2.corner(5).x - is1.corner(5).x;
                }
                else if (last_touching_side == 5)
                {
                    cmp_value = is2.corner(6).x - is1.corner(6).x;
                }
                else if (last_touching_side == 6)
                {
                    cmp_value = is2.corner(7).y - is1.corner(7).y;
                }
                else if (last_touching_side == 7)
                {
                    cmp_value = is2.corner(0).y - is1.corner(0).y;
                }
            }
            return cmp_value;
        }
        /** The shape of the neighbour room */
        public final IntOctagon shape;
        
        /** The intersection of tnis ExpansionRoom shape with the neighbour_shape */
        public final IntOctagon intersection;
        
        /** The first side of the room shape, where the neighbour_shape touches */
        public final int first_touching_side;
        
        /** The last side of the room shape, where the neighbour_shape touches */
        public final int last_touching_side;
    }
}
