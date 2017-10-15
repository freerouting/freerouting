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
 * BoxShapeSearchTree.java
 *
 * Created on 20. Mai 2007, 07:33
 *
 */

package board;

import java.util.Collection;
import java.util.LinkedList;

import geometry.planar.OrthogonalBoundingDirections;
import geometry.planar.TileShape;
import geometry.planar.Shape;
import geometry.planar.IntBox;
import geometry.planar.Polyline;

import autoroute.IncompleteFreeSpaceExpansionRoom;
import autoroute.CompleteFreeSpaceExpansionRoom;

/**
 * A special simple ShapeSearchtree, where the shapes are of class IntBox.
 * It is used in the 90-degree autorouter algorithm.
 *
 * @author Alfons Wirtz
 */
public class ShapeSearchTree90Degree extends ShapeSearchTree
{
    
    /** Creates a new instance of ShapeSearchTree90Degree */
    public ShapeSearchTree90Degree(BasicBoard p_board, int p_compensated_clearance_class_no)
    {
        super(OrthogonalBoundingDirections.INSTANCE, p_board, p_compensated_clearance_class_no);
    }
    
    /**
     * Calculates a new incomplete room with a maximal TileShape contained in the shape of p_room,
     * which may overlap only with items of the input net on the input layer.
     * p_room.get_contained_shape() will be contained in the shape of the result room.
     * If that is not possible, several rooms are returned with shapes,
     * which intersect with p_room.get_contained_shape().
     * The result room is not yet complete, because its doors are not yet calculated.
     */
    public Collection<IncompleteFreeSpaceExpansionRoom> complete_shape(IncompleteFreeSpaceExpansionRoom p_room,
            int p_net_no, SearchTreeObject p_ignore_object, TileShape p_ignore_shape)
    {
        if (!(p_room.get_contained_shape() instanceof IntBox))
        {
            System.out.println("BoxShapeSearchTree.complete_shape: unexpected p_shape_to_be_contained");
            return new LinkedList<IncompleteFreeSpaceExpansionRoom>();
        }
        IntBox shape_to_be_contained = (IntBox) p_room.get_contained_shape();
        if (this.root == null)
        {
            return new LinkedList<IncompleteFreeSpaceExpansionRoom>();
        }
        IntBox start_shape = board.get_bounding_box();
        if (p_room.get_shape() != null)
        {
            if (!(p_room.get_shape() instanceof IntBox))
            {
                System.out.println("BoxShapeSearchTree.complete_shape: p_start_shape of type IntBox expected");
                return new LinkedList<IncompleteFreeSpaceExpansionRoom>();
            }
            start_shape = ((IntBox)p_room.get_shape()).intersection(start_shape);
        }
        IntBox bounding_shape = start_shape;
        int room_layer = p_room.get_layer();
        Collection<IncompleteFreeSpaceExpansionRoom> result = new LinkedList<IncompleteFreeSpaceExpansionRoom>();
        result.add(new IncompleteFreeSpaceExpansionRoom(start_shape, room_layer, shape_to_be_contained));
        this.node_stack.reset();
        this.node_stack.push(this.root);
        TreeNode curr_node;
        
        for (;;)
        {
            curr_node = this.node_stack.pop();
            if (curr_node == null)
            {
                break;
            }
            if (curr_node.bounding_shape.intersects(bounding_shape))
            {
                if (curr_node instanceof Leaf)
                {
                    Leaf curr_leaf = (Leaf) curr_node;
                    SearchTreeObject curr_object = (SearchTreeObject) curr_leaf.object;
                    int shape_index = curr_leaf.shape_index_in_object;
                    if (curr_object.is_trace_obstacle(p_net_no) && curr_object.shape_layer(shape_index) == room_layer
                            && curr_object != p_ignore_object)
                    {
                        
                        IntBox curr_object_shape = curr_object.get_tree_shape(this, shape_index).bounding_box();
                        Collection<IncompleteFreeSpaceExpansionRoom> new_result = new LinkedList<IncompleteFreeSpaceExpansionRoom>();
                        IntBox new_bounding_shape = IntBox.EMPTY;
                        for (IncompleteFreeSpaceExpansionRoom curr_room : result)
                        {
                            IntBox curr_shape = (IntBox) curr_room.get_shape();
                            if (curr_shape.overlaps(curr_object_shape))
                            {
                                if (curr_object instanceof CompleteFreeSpaceExpansionRoom
                                        && p_ignore_shape != null )
                                {
                                    IntBox intersection = curr_shape.intersection(curr_object_shape);
                                    if (p_ignore_shape.contains(intersection))
                                    {
                                        // ignore also all objects, whose intersection is contained in the
                                        // 2-dim overlap-door with the from_room.
                                        continue;
                                    }
                                }
                                Collection <IncompleteFreeSpaceExpansionRoom> new_restrained_shapes =
                                        restrain_shape(curr_room, curr_object_shape);
                                new_result.addAll(new_restrained_shapes);
                                
                                
                                for (IncompleteFreeSpaceExpansionRoom tmp_shape : new_result)
                                {
                                    new_bounding_shape = new_bounding_shape.union(tmp_shape.get_shape().bounding_box());
                                }
                            }
                            else
                            {
                                new_result.add(curr_room);
                                new_bounding_shape = new_bounding_shape.union(curr_shape.bounding_box());
                            }
                        }
                        result = new_result;
                        bounding_shape = new_bounding_shape;
                    }
                }
                else
                {
                    this.node_stack.push(((InnerNode)curr_node).first_child);
                    this.node_stack.push(((InnerNode)curr_node).second_child);
                }
            }
        }
        return result;
    }
    
    /**
     * Restrains the shape of p_incomplete_room to a box shape, which does not intersect with the interiour
     * of p_obstacle_shape. p_incomplete_room.get_contained_shape() must be contained in the shape of
     * the result room.
     */
    private Collection<IncompleteFreeSpaceExpansionRoom> restrain_shape(IncompleteFreeSpaceExpansionRoom p_incomplete_room, IntBox p_obstacle_shape)
    {
        // Search the edge line of p_obstacle_shape, so that p_shape_to_be_contained
        // are on the right side of this line, and that the line segment
        // intersects with the interiour of p_shape.
        // If there are more than 1 such lines take the line which is
        // furthest away from the shape_to_be_contained
        // Then insersect p_shape with the halfplane defined by the
        // opposite of this line.
        
        Collection<IncompleteFreeSpaceExpansionRoom> result = new LinkedList<IncompleteFreeSpaceExpansionRoom>();
        if (p_incomplete_room.get_contained_shape().is_empty())
        {
            if (this.board.get_test_level().ordinal() >=  TestLevel.ALL_DEBUGGING_OUTPUT.ordinal())
            {
                System.out.println("BoxShapeSearchTree.restrain_shape: p_shape_to_be_contained is empty");
            }
            return result;
        }
        IntBox room_shape = p_incomplete_room.get_shape().bounding_box();
        IntBox shape_to_be_contained = p_incomplete_room.get_contained_shape().bounding_box();
        int cut_line_distance = 0;
        IntBox restrained_shape = null;
        
        if (room_shape.ll.x  < p_obstacle_shape.ur.x && room_shape.ur.x > p_obstacle_shape.ur.x
                && room_shape.ur.y > p_obstacle_shape.ll.y && room_shape.ll.y < p_obstacle_shape.ur.y )
        {
            // The right line segment of the obstacle_shape intersects the interiour of p_shape
            int curr_distance = shape_to_be_contained.ll.x - p_obstacle_shape.ur.x;
            if (curr_distance > cut_line_distance)
            {
                cut_line_distance = curr_distance;
                restrained_shape = new IntBox(p_obstacle_shape.ur.x, room_shape.ll.y, room_shape.ur.x, room_shape.ur.y);
            }
        }
        if (room_shape.ll.x  < p_obstacle_shape.ll.x && room_shape.ur.x > p_obstacle_shape.ll.x
                && room_shape.ur.y > p_obstacle_shape.ll.y && room_shape.ll.y < p_obstacle_shape.ur.y )
        {
            // The left line segment of the obstacle_shape intersects the interiour of p_shape
            int curr_distance = p_obstacle_shape.ll.x - shape_to_be_contained.ur.x;
            if (curr_distance > cut_line_distance)
            {
                cut_line_distance = curr_distance;
                restrained_shape = new IntBox(room_shape.ll.x, room_shape.ll.y, p_obstacle_shape.ll.x, room_shape.ur.y);
            }
        }
        if (room_shape.ll.y  < p_obstacle_shape.ll.y && room_shape.ur.y > p_obstacle_shape.ll.y
                && room_shape.ur.x > p_obstacle_shape.ll.x && room_shape.ll.x < p_obstacle_shape.ur.x )
        {
            // The lower  line segment of the obstacle_shape intersects the interiour of p_shape
            int curr_distance = p_obstacle_shape.ll.y - shape_to_be_contained.ur.y;
            if (curr_distance > cut_line_distance)
            {
                cut_line_distance = curr_distance;
                restrained_shape = new IntBox(room_shape.ll.x, room_shape.ll.y, room_shape.ur.x, p_obstacle_shape.ll.y);
            }
        }
        if (room_shape.ll.y  < p_obstacle_shape.ur.y && room_shape.ur.y > p_obstacle_shape.ur.y
                && room_shape.ur.x > p_obstacle_shape.ll.x && room_shape.ll.x < p_obstacle_shape.ur.x )
        {
            // The upper line segment of the obstacle_shape intersects the interiour of p_shape
            int curr_distance = shape_to_be_contained.ll.y - p_obstacle_shape.ur.y;
            if (curr_distance > cut_line_distance)
            {
                cut_line_distance = curr_distance;
                restrained_shape = new IntBox(room_shape.ll.x, p_obstacle_shape.ur.y, room_shape.ur.x, room_shape.ur.y);
            }
        }
        if (restrained_shape != null)
        {
            result.add(new IncompleteFreeSpaceExpansionRoom(restrained_shape,
                    p_incomplete_room.get_layer(), shape_to_be_contained));
            return result;
        }
        
        // Now shape_to_be_contained intersects with the obstacle_shape.
        // shape_to_be_contained and p_shape evtl. need to be divided in two.
        IntBox is = shape_to_be_contained.intersection(p_obstacle_shape);
        if (is.is_empty())
        {
            System.out.println("BoxShapeSearchTree.restrain_shape: Intersection between obstacle_shape and shape_to_be_contained expected");
            return result;
        }
        IntBox new_shape_1 = null;
        IntBox new_shape_2 = null;
        if (is.ll.x > room_shape.ll.x && is.ll.x == p_obstacle_shape.ll.x && is.ll.x < room_shape.ur.x)
        {
            new_shape_1 = new IntBox(room_shape.ll.x, room_shape.ll.y, is.ll.x, room_shape.ur.y);
            new_shape_2 = new IntBox(is.ll.x, room_shape.ll.y, room_shape.ur.x, room_shape.ur.y);
        }
        else if (is.ur.x > room_shape.ll.x  && is.ur.x == p_obstacle_shape.ur.x && is.ur.x < room_shape.ur.x)
        {
            new_shape_2 = new IntBox(room_shape.ll.x, room_shape.ll.y, is.ur.x, room_shape.ur.y);
            new_shape_1 = new IntBox(is.ur.x, room_shape.ll.y, room_shape.ur.x, room_shape.ur.y);
        }
        else if (is.ll.y > room_shape.ll.y && is.ll.y == p_obstacle_shape.ll.y && is.ll.y < room_shape.ur.y)
        {
            new_shape_1 = new IntBox(room_shape.ll.x, room_shape.ll.y, room_shape.ur.x, is.ll.y);
            new_shape_2 = new IntBox(room_shape.ll.x, is.ll.y, room_shape.ur.x, room_shape.ur.y);
        }
        else if (is.ur.y > room_shape.ll.y && is.ur.y == p_obstacle_shape.ur.y && is.ur.y < room_shape.ur.y)
        {
            new_shape_2 = new IntBox(room_shape.ll.x, room_shape.ll.y, room_shape.ur.x, is.ur.y);
            new_shape_1 = new IntBox(room_shape.ll.x, is.ur.y, room_shape.ur.x, room_shape.ur.y);
        }
        if (new_shape_1 != null)
        {
            IntBox new_shape_to_be_contained = shape_to_be_contained.intersection(new_shape_1);
            if (new_shape_to_be_contained.dimension() > 0)
            {
                result.add(new IncompleteFreeSpaceExpansionRoom(new_shape_1,
                        p_incomplete_room.get_layer(), new_shape_to_be_contained));
                IncompleteFreeSpaceExpansionRoom new_incomplete_room =
                        new IncompleteFreeSpaceExpansionRoom( new_shape_2, p_incomplete_room.get_layer(),
                        shape_to_be_contained.intersection(new_shape_2));
                result.addAll(restrain_shape(new_incomplete_room, p_obstacle_shape));
            }
        }
        return result;
    }
    
    TileShape[] calculate_tree_shapes(DrillItem p_drill_item)
    {
        if (this.board == null)
        {
            return new TileShape[0];
        }
        TileShape[] result = new TileShape [p_drill_item.tile_shape_count()];
        for (int i = 0; i < result.length; ++i)
        {
            Shape curr_shape = p_drill_item.get_shape(i);
            if (curr_shape == null)
            {
                result[i] = null;
            }
            else
            {
                IntBox curr_tile_shape = curr_shape.bounding_box();
                int offset_width = this.clearance_compensation_value(p_drill_item.clearance_class_no(), p_drill_item.shape_layer(i));
                if (curr_tile_shape == null)
                {
                    System.out.println("BoxShapeSearchTree.calculate_tree_shapes: shape is null");
                }
                else
                {
                    curr_tile_shape = curr_tile_shape.offset(offset_width);
                }
                result [i] = curr_tile_shape;
            }
        }
        return  result;
    }
    
    TileShape[] calculate_tree_shapes(ObstacleArea p_obstacle_area)
    {
        TileShape[] result = super.calculate_tree_shapes(p_obstacle_area);
        for (int i  = 0; i < result.length; ++i)
        {
            result[i] = result[i].bounding_box();
        }
        return result;
    }
    
    TileShape[] calculate_tree_shapes(BoardOutline p_outline)
    {
        TileShape[] result = super.calculate_tree_shapes(p_outline);
        for (int i  = 0; i < result.length; ++i)
        {
            result[i] = result[i].bounding_box();
        }
        return result;
    }
    
    /**
     * Used for creating the shapes of a polyline_trace for this tree.
     */
    TileShape offset_shape(Polyline p_polyline, int p_half_width, int p_no)
    {
        return p_polyline.offset_box(p_half_width, p_no);
    }
    
    /**
     * Used for creating the shapes of a polyline_trace for this tree.
     */
    public TileShape[] offset_shapes(Polyline p_polyline, int p_half_width,
            int p_from_no, int p_to_no)
    {
        int from_no = Math.max(p_from_no, 0);
        int to_no = Math.min(p_to_no, p_polyline.arr.length -1);
        int shape_count = Math.max(to_no - from_no -1, 0);
        TileShape[] shape_arr = new TileShape[shape_count];
        for (int j = from_no; j < to_no - 1; ++j)
        {
            shape_arr [j - from_no] = p_polyline.offset_box(p_half_width, j);
        }
        return shape_arr;
    }
}
