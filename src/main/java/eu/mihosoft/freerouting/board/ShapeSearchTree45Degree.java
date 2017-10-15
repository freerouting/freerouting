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
 * ShapeSearchTree45Degree.java
 *
 * Created on 15. Juli 2007, 07:26
 *
 */
package board;

import java.util.Collection;
import java.util.LinkedList;

import geometry.planar.FortyfiveDegreeBoundingDirections;
import geometry.planar.TileShape;
import geometry.planar.Shape;
import geometry.planar.IntOctagon;
import geometry.planar.IntBox;
import geometry.planar.Side;
import geometry.planar.Line;

import autoroute.IncompleteFreeSpaceExpansionRoom;
import autoroute.CompleteFreeSpaceExpansionRoom;

/**
 * A special simple ShapeSearchtree, where the shapes are of class IntOctagon.
 * It is used in the 45-degree autorouter algorithm.
 *
 * @author Alfons Wirtz
 */
public class ShapeSearchTree45Degree extends ShapeSearchTree
{

    /** Creates a new instance of ShapeSearchTree45Degree */
    public ShapeSearchTree45Degree(BasicBoard p_board, int p_compensated_clearance_class_no)
    {
        super(FortyfiveDegreeBoundingDirections.INSTANCE, p_board, p_compensated_clearance_class_no);
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
        if (!(p_room.get_contained_shape().is_IntOctagon()) && this.board.get_test_level() != TestLevel.RELEASE_VERSION)
        {
            System.out.println("ShapeSearchTree45Degree.complete_shape: unexpected p_shape_to_be_contained");
            return new LinkedList<IncompleteFreeSpaceExpansionRoom>();
        }
        IntOctagon shape_to_be_contained = p_room.get_contained_shape().bounding_octagon();
        if (this.root == null)
        {
            return new LinkedList<IncompleteFreeSpaceExpansionRoom>();
        }
        IntOctagon start_shape = board.get_bounding_box().bounding_octagon();
        if (p_room.get_shape() != null)
        {
            if (!(p_room.get_shape() instanceof IntOctagon))
            {
                System.out.println("ShapeSearchTree45Degree.complete_shape: p_start_shape of type IntOctagon expected");
                return new LinkedList<IncompleteFreeSpaceExpansionRoom>();
            }
            start_shape = p_room.get_shape().bounding_octagon().intersection(start_shape);
        }
        IntOctagon bounding_shape = start_shape;
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
                    boolean is_obstacle = curr_object.is_trace_obstacle(p_net_no);

                    int shape_index = curr_leaf.shape_index_in_object;
                    if (is_obstacle && curr_object.shape_layer(shape_index) == room_layer && curr_object != p_ignore_object)
                    {

                        IntOctagon curr_object_shape = curr_object.get_tree_shape(this, shape_index).bounding_octagon();
                        Collection<IncompleteFreeSpaceExpansionRoom> new_result = new LinkedList<IncompleteFreeSpaceExpansionRoom>();
                        IntOctagon new_bounding_shape = IntOctagon.EMPTY;
                        for (IncompleteFreeSpaceExpansionRoom curr_room : result)
                        {
                            IntOctagon curr_shape = (IntOctagon) curr_room.get_shape();
                            if (curr_shape.overlaps(curr_object_shape))
                            {
                                if (curr_object instanceof CompleteFreeSpaceExpansionRoom && p_ignore_shape != null)
                                {
                                    IntOctagon intersection = curr_shape.intersection(curr_object_shape);
                                    if (p_ignore_shape.contains(intersection))
                                    {
                                        // ignore also all objects, whose intersection is contained in the
                                        // 2-dim overlap-door with the from_room.
                                        if (!p_ignore_shape.contains(curr_shape))
                                        {
                                            new_result.add(curr_room);
                                            new_bounding_shape = new_bounding_shape.union(curr_shape.bounding_box());
                                        }
                                        continue;
                                    }
                                }
                                Collection<IncompleteFreeSpaceExpansionRoom> new_restrained_shapes =
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
                    this.node_stack.push(((InnerNode) curr_node).first_child);
                    this.node_stack.push(((InnerNode) curr_node).second_child);
                }
            }
        }
        result = divide_large_room(result, board.get_bounding_box());
        // remove rooms with shapes equal to the contained shape to prevent endless loop.
        java.util.Iterator<IncompleteFreeSpaceExpansionRoom> it = result.iterator();
        while (it.hasNext())
        {
            IncompleteFreeSpaceExpansionRoom curr_room = it.next();
            if (curr_room.get_contained_shape().contains(curr_room.get_shape()))
            {
                it.remove();
            }
        }
        return result;
    }

    /**
     * Makes shure that on each layer there will be more than 1 IncompleteFreeSpaceExpansionRoom,
     * even if there are no objects on the layer. Otherwise the maze search algprithm gets problems
     * with vias.
     */
    protected Collection<IncompleteFreeSpaceExpansionRoom> divide_large_room(
            Collection<IncompleteFreeSpaceExpansionRoom> p_room_list, IntBox p_board_bounding_box)
    {
        Collection<IncompleteFreeSpaceExpansionRoom> result =
                super.divide_large_room(p_room_list, p_board_bounding_box);
        for (IncompleteFreeSpaceExpansionRoom curr_room : result)
        {
            curr_room.set_shape(curr_room.get_shape().bounding_octagon());
            curr_room.set_contained_shape(curr_room.get_contained_shape().bounding_octagon());
        }
        return result;
    }

    /**
     * Checks, if the border line segment with index p_obstacle_border_line_no intersects with the inside
     * of p_room_shape.
     */
    private static boolean obstacle_segment_touches_inside(IntOctagon p_obstacle_shape,
                                                           int p_obstacle_border_line_no, IntOctagon p_room_shape)
    {
        int curr_border_line_no = p_obstacle_border_line_no;
        int curr_obstacle_corner_x = p_obstacle_shape.corner_x(p_obstacle_border_line_no);
        int curr_obstacle_corner_y = p_obstacle_shape.corner_y(p_obstacle_border_line_no);
        for (int j = 0; j < 5; ++j)
        {

            if (p_room_shape.side_of_border_line(curr_obstacle_corner_x, curr_obstacle_corner_y,
                    curr_border_line_no) != Side.ON_THE_LEFT)
            {
                return false;
            }
            curr_border_line_no = (curr_border_line_no + 1) % 8;
        }

        int next_obstacle_border_line_no = (p_obstacle_border_line_no + 1) % 8;
        int next_obstacle_corner_x = p_obstacle_shape.corner_x(next_obstacle_border_line_no);
        int next_obstacle_corner_y = p_obstacle_shape.corner_y(next_obstacle_border_line_no);
        curr_border_line_no = (p_obstacle_border_line_no + 5) % 8;
        for (int j = 0; j < 3; ++j)
        {
            if (p_room_shape.side_of_border_line(next_obstacle_corner_x, next_obstacle_corner_y,
                    curr_border_line_no) != Side.ON_THE_LEFT)
            {
                return false;
            }
            curr_border_line_no = (curr_border_line_no + 1) % 8;
        }
        return true;
    }

    /**
     * Restrains the shape of p_incomplete_room to a octagon shape, which does not intersect with the interiour
     * of p_obstacle_shape. p_incomplete_room.get_contained_shape() must be contained in the shape of
     * the result room.
     */
    private Collection<IncompleteFreeSpaceExpansionRoom> restrain_shape(IncompleteFreeSpaceExpansionRoom p_incomplete_room, IntOctagon p_obstacle_shape)
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
            if (this.board.get_test_level().ordinal() >= TestLevel.ALL_DEBUGGING_OUTPUT.ordinal())
            {
                System.out.println("ShapeSearchTree45Degree.restrain_shape: p_shape_to_be_contained is empty");
            }
            return result;
        }
        IntOctagon room_shape = p_incomplete_room.get_shape().bounding_octagon();
        IntOctagon shape_to_be_contained = p_incomplete_room.get_contained_shape().bounding_octagon();
        double cut_line_distance = -1;
        int restraining_line_no = -1;

        for (int obstacle_line_no = 0; obstacle_line_no < 8; ++obstacle_line_no)
        {
            double curr_distance = signed_line_distance(p_obstacle_shape, obstacle_line_no, shape_to_be_contained);
            if (curr_distance > cut_line_distance)
            {
                if (obstacle_segment_touches_inside(p_obstacle_shape, obstacle_line_no, room_shape))
                {
                    cut_line_distance = curr_distance;
                    restraining_line_no = obstacle_line_no;
                }
            }
        }
        if (cut_line_distance >= 0)
        {
            IntOctagon restrained_shape = calc_outside_restrained_shape(p_obstacle_shape, restraining_line_no, room_shape);
            result.add(new IncompleteFreeSpaceExpansionRoom(restrained_shape,
                    p_incomplete_room.get_layer(), shape_to_be_contained));
            return result;
        }

        // There is no cut line, so that all p_shape_to_be_contained is
        // completely on the right side of that line. Search a cut line, so that
        // at least part of p_shape_to_be_contained is on the right side.
        if (shape_to_be_contained.dimension() < 1)
        {
            // There is already a completed expansion room around p_shape_to_be_contained.
            return result;
        }

        restraining_line_no = -1;
        for (int obstacle_line_no = 0; obstacle_line_no < 8; ++obstacle_line_no)
        {
            if (obstacle_segment_touches_inside(p_obstacle_shape, obstacle_line_no, room_shape))
            {
                Line curr_line = p_obstacle_shape.border_line(obstacle_line_no);
                if (shape_to_be_contained.side_of(curr_line) == Side.COLLINEAR)
                {
                    // curr_line intersects with the interiour of p_shape_to_be_contained
                    restraining_line_no = obstacle_line_no;
                    break;
                }
            }
        }
        if (restraining_line_no < 0)
        {
            // cut line not found, parts or the whole of p_shape may be already
            // occupied from somewhere else.
            return result;
        }
        IntOctagon restrained_shape = calc_outside_restrained_shape(p_obstacle_shape, restraining_line_no, room_shape);
        if (restrained_shape.dimension() == 2)
        {
            IntOctagon new_shape_to_be_contained = shape_to_be_contained.intersection(restrained_shape);
            if (new_shape_to_be_contained.dimension() > 0)
            {
                result.add(new IncompleteFreeSpaceExpansionRoom(restrained_shape,
                        p_incomplete_room.get_layer(), new_shape_to_be_contained));
            }
        }

        IntOctagon rest_piece = calc_inside_restrained_shape(p_obstacle_shape, restraining_line_no, room_shape);
        if (rest_piece.dimension() >= 2)
        {
            TileShape rest_shape_to_be_contained = shape_to_be_contained.intersection(rest_piece);
            if (rest_shape_to_be_contained.dimension() >= 0)
            {
                IncompleteFreeSpaceExpansionRoom rest_incomplete_room = new IncompleteFreeSpaceExpansionRoom(rest_piece, p_incomplete_room.get_layer(), rest_shape_to_be_contained);
                result.addAll(restrain_shape(rest_incomplete_room, p_obstacle_shape));
            }
        }
        return result;
    }

    private static double signed_line_distance(IntOctagon p_obstacle_shape, int p_obstacle_line_no, IntOctagon p_contained_shape)
    {
        double result;
        if (p_obstacle_line_no == 0)
        {
            result = p_obstacle_shape.ly - p_contained_shape.uy;
        }
        else if (p_obstacle_line_no == 2)
        {
            result = p_contained_shape.lx - p_obstacle_shape.rx;
        }
        else if (p_obstacle_line_no == 4)
        {
            result = p_contained_shape.ly - p_obstacle_shape.uy;
        }
        else if (p_obstacle_line_no == 6)
        {
            result = p_obstacle_shape.lx - p_contained_shape.rx;
        }
        // factor 0.5 used instead to 1 / sqrt(2) to prefer orthogonal lines slightly to diagonal restraining lines.
        else if (p_obstacle_line_no == 1)
        {
            result = 0.5 * (p_contained_shape.ulx - p_obstacle_shape.lrx);
        }
        else if (p_obstacle_line_no == 3)
        {
            result = 0.5 * (p_contained_shape.llx - p_obstacle_shape.urx);
        }
        else if (p_obstacle_line_no == 5)
        {
            result = 0.5 * (p_obstacle_shape.ulx - p_contained_shape.lrx);
        }
        else if (p_obstacle_line_no == 7)
        {
            result = 0.5 * (p_obstacle_shape.llx - p_contained_shape.urx);
        }
        else
        {
            System.out.println("ShapeSearchTree45Degree.signed_line_distance: p_obstacle_line_no out of range");
            result = 0;
        }
        return result;
    }

    /** Intersects p_room_shape with the half plane defined by the outside of the borderline
     * with index p_obstacle_line_no of p_obstacle_shape.
     */
    IntOctagon calc_outside_restrained_shape(IntOctagon p_obstacle_shape, int p_obstacle_line_no, IntOctagon p_room_shape)
    {
        int lx = p_room_shape.lx;
        int ly = p_room_shape.ly;
        int rx = p_room_shape.rx;
        int uy = p_room_shape.uy;
        int ulx = p_room_shape.ulx;
        int lrx = p_room_shape.lrx;
        int llx = p_room_shape.llx;
        int urx = p_room_shape.urx;

        if (p_obstacle_line_no == 0)
        {
            uy = p_obstacle_shape.ly;
        }
        else if (p_obstacle_line_no == 2)
        {
            lx = p_obstacle_shape.rx;
        }
        else if (p_obstacle_line_no == 4)
        {
            ly = p_obstacle_shape.uy;
        }
        else if (p_obstacle_line_no == 6)
        {
            rx = p_obstacle_shape.lx;
        }
        else if (p_obstacle_line_no == 1)
        {
            ulx = p_obstacle_shape.lrx;
        }
        else if (p_obstacle_line_no == 3)
        {
            llx = p_obstacle_shape.urx;
        }
        else if (p_obstacle_line_no == 5)
        {
            lrx = p_obstacle_shape.ulx;
        }
        else if (p_obstacle_line_no == 7)
        {
            urx = p_obstacle_shape.llx;
        }
        else
        {
            System.out.println("ShapeSearchTree45Degree.calc_outside_restrained_shape: p_obstacle_line_no out of range");
        }

        IntOctagon result = new IntOctagon(lx, ly, rx, uy, ulx, lrx, llx, urx);
        return result.normalize();
    }

    /** Intersects p_room_shape with the half plane defined by the inside of the borderline
     * with index p_obstacle_line_no of p_obstacle_shape.
     */
    IntOctagon calc_inside_restrained_shape(IntOctagon p_obstacle_shape, int p_obstacle_line_no, IntOctagon p_room_shape)
    {
        int lx = p_room_shape.lx;
        int ly = p_room_shape.ly;
        int rx = p_room_shape.rx;
        int uy = p_room_shape.uy;
        int ulx = p_room_shape.ulx;
        int lrx = p_room_shape.lrx;
        int llx = p_room_shape.llx;
        int urx = p_room_shape.urx;

        if (p_obstacle_line_no == 0)
        {
            ly = p_obstacle_shape.ly;
        }
        else if (p_obstacle_line_no == 2)
        {
            rx = p_obstacle_shape.rx;
        }
        else if (p_obstacle_line_no == 4)
        {
            uy = p_obstacle_shape.uy;
        }
        else if (p_obstacle_line_no == 6)
        {
            lx = p_obstacle_shape.lx;
        }
        else if (p_obstacle_line_no == 1)
        {
            lrx = p_obstacle_shape.lrx;
        }
        else if (p_obstacle_line_no == 3)
        {
            urx = p_obstacle_shape.urx;
        }
        else if (p_obstacle_line_no == 5)
        {
            ulx = p_obstacle_shape.ulx;
        }
        else if (p_obstacle_line_no == 7)
        {
            llx = p_obstacle_shape.llx;
        }
        else
        {
            System.out.println("ShapeSearchTree45Degree.calc_inside_restrained_shape: p_obstacle_line_no out of range");
        }

        IntOctagon result = new IntOctagon(lx, ly, rx, uy, ulx, lrx, llx, urx);
        return result.normalize();
    }

    TileShape[] calculate_tree_shapes(DrillItem p_drill_item)
    {
        if (this.board == null)
        {
            return new TileShape[0];
        }
        TileShape[] result = new TileShape[p_drill_item.tile_shape_count()];
        for (int i = 0; i < result.length; ++i)
        {
            Shape curr_shape = p_drill_item.get_shape(i);
            if (curr_shape == null)
            {
                result[i] = null;
            }
            else
            {
                TileShape curr_tile_shape = curr_shape.bounding_octagon();
                if (curr_tile_shape.is_IntBox())
                {
                    curr_tile_shape = curr_shape.bounding_box();

                    // To avoid small corner cutoffs when taking the offset as an octagon.
                    // That may complicate the room division in the maze expand algorithm unnecessesary.
                }

                int offset_width = this.clearance_compensation_value(p_drill_item.clearance_class_no(), p_drill_item.shape_layer(i));
                curr_tile_shape = (TileShape) curr_tile_shape.offset(offset_width);
                result[i] = curr_tile_shape.bounding_octagon();
            }
        }
        return result;
    }

    TileShape[] calculate_tree_shapes(ObstacleArea p_obstacle_area)
    {
        TileShape[] result = super.calculate_tree_shapes(p_obstacle_area);
        for (int i = 0; i < result.length; ++i)
        {
            result[i] = result[i].bounding_octagon();
        }
        return result;
    }

    TileShape[] calculate_tree_shapes(BoardOutline p_outline)
    {
        TileShape[] result = super.calculate_tree_shapes(p_outline);
        for (int i = 0; i < result.length; ++i)
        {
            result[i] = result[i].bounding_octagon();
        }
        return result;
    }
}
