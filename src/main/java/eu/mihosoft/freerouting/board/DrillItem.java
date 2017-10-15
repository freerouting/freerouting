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
 * DrillItem.java
 *
 * Created on 27. Juni 2003, 11:38
 */

package board;


import geometry.planar.IntBox;
import geometry.planar.FloatPoint;
import geometry.planar.Point;
import geometry.planar.IntPoint;
import geometry.planar.Shape;
import geometry.planar.TileShape;
import geometry.planar.Vector;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import library.Padstack;

/**
 * Common superclass for Pins and Vias
 *
 * @author  Alfons Wirtz
 */
public abstract class DrillItem extends Item implements Connectable, java.io.Serializable
{
    
    public DrillItem( Point p_center, int[] p_net_no_arr, int p_clearance_type, int p_id_no,
            int p_group_no, FixedState p_fixed_state, BasicBoard p_board)
    {
        super(p_net_no_arr, p_clearance_type, p_id_no, p_group_no, p_fixed_state, p_board);
        this.center = p_center;
    }
    
    /**
     * Works only for symmettric DrillItems
     */
    public void translate_by(Vector p_vector)
    {
        if (center != null)
        {
            center  = center.translate_by(p_vector);
        }
        this.clear_derived_data();
    }
    
    
    public void turn_90_degree(int p_factor, IntPoint p_pole)
    {
        if (center != null)
        {
            center  = center.turn_90_degree(p_factor, p_pole);
        }
        this.clear_derived_data();
    }
    
    public void rotate_approx(double p_angle_in_degree, FloatPoint p_pole)
    {
        if (center != null)
        {
            FloatPoint new_center  = center.to_float().rotate(Math.toRadians(p_angle_in_degree), p_pole);
            this.center = new_center.round();
        }
        this.clear_derived_data();
    }
    
    public void change_placement_side(IntPoint p_pole)
    {
        if (center != null)
        {
            center  = center.mirror_vertical(p_pole);
        }
        this.clear_derived_data();
    }
    
    public void move_by(Vector p_vector)
    {
        Point old_center = this.get_center();
        // remember the contact situation of this drillitem  to traces on each layer
        Set<TraceInfo> contact_trace_info = new TreeSet<TraceInfo>();
        Collection<Item> contacts = this.get_normal_contacts();
        Iterator<Item> it = contacts.iterator();
        while (it.hasNext())
        {
            Item curr_contact = it.next();
            if (curr_contact instanceof Trace)
            {
                Trace curr_trace = (Trace) curr_contact;
                TraceInfo curr_trace_info =
                        new TraceInfo(curr_trace.get_layer(), curr_trace.get_half_width(), curr_trace.clearance_class_no());
                contact_trace_info.add(curr_trace_info);
            }
        }
        super.move_by(p_vector);
        
        // Insert a Trace from the old center to the new center, on all layers, where
        // this DrillItem was connected to a Trace.
        Collection<Point> connect_point_list = new java.util.LinkedList<Point>();
        connect_point_list.add(old_center);
        Point new_center = this.get_center();
        IntPoint add_corner = null;
        if (old_center instanceof  IntPoint && new_center instanceof IntPoint)
        {
            // Make shure, that the traces will remain 90- or 45-degree.
            if (board.rules.get_trace_angle_restriction() == AngleRestriction.NINETY_DEGREE)
            {
                add_corner = ((IntPoint)old_center).ninety_degree_corner((IntPoint)new_center, true);
            }
            else if (board.rules.get_trace_angle_restriction() == AngleRestriction.FORTYFIVE_DEGREE)
            {
                add_corner = ((IntPoint)old_center).fortyfive_degree_corner((IntPoint)new_center, true);
            }
        }
        if (add_corner != null)
        {
            connect_point_list.add(add_corner);
        }
        connect_point_list.add(new_center);
        Point[] connect_points = new Point[connect_point_list.size()];
        Iterator<Point> it3 = connect_point_list.iterator();
        for (int i = 0 ; i < connect_points.length; ++i)
        {
            connect_points[i] = it3.next();
        }
        Iterator<TraceInfo> it2 = contact_trace_info.iterator();
        while (it2.hasNext())
        {
            TraceInfo curr_trace_info = it2.next();
            board.insert_trace(connect_points, curr_trace_info.layer, curr_trace_info.half_width,
                    this.net_no_arr, curr_trace_info.clearance_type, FixedState.UNFIXED);
        }
    }
    
    public int shape_layer(int p_index)
    {
        int index = Math.max( p_index, 0);
        int from_layer = first_layer();
        int to_layer = last_layer();
        index = Math.min(index, to_layer - from_layer);
        return from_layer + index;
    }
    
    public boolean is_on_layer(int p_layer)
    {
        return p_layer >=  first_layer() && p_layer <= last_layer();
    }
    
    
    public int first_layer()
    {
        if (this.precalculated_first_layer < 0)
        {
            Padstack padstack = get_padstack();
            if (this.is_placed_on_front() || padstack.placed_absolute)
            {
                this.precalculated_first_layer =  padstack.from_layer();
            }
            else
            {
                this.precalculated_first_layer = padstack.board_layer_count() - padstack.to_layer() - 1;
            }
        }
        return this.precalculated_first_layer;
    }
    
    public int last_layer()
    {
        if (this.precalculated_last_layer < 0)
        {
            Padstack padstack = get_padstack();
            if (this.is_placed_on_front()|| padstack.placed_absolute)
            {
                this.precalculated_last_layer =  padstack.to_layer();
            }
            else
            {
                this.precalculated_last_layer = padstack.board_layer_count() - padstack.from_layer() - 1;
            }
        }
        return this.precalculated_last_layer;
    }
    
    public abstract Shape get_shape(int p_index);
    
    public IntBox bounding_box()
    {
        IntBox result = IntBox.EMPTY;
        for (int i = 0; i < tile_shape_count(); ++i)
        {
            Shape curr_shape = this.get_shape(i);
            if (curr_shape != null)
            {
                result = result.union(curr_shape.bounding_box());
            }
        }
        return result;
    }
    
    public int tile_shape_count()
    {
        Padstack padstack = get_padstack();
        int from_layer = padstack.from_layer();
        int to_layer = padstack.to_layer();
        return to_layer - from_layer + 1;
    }
    
    protected TileShape[] calculate_tree_shapes(ShapeSearchTree p_search_tree)
    {
        return p_search_tree.calculate_tree_shapes(this);
    }
    
    /**
     * Returns the smallest distance from the center to the border of
     * the shape on any layer.
     */
    public double smallest_radius()
    {
        double result = Double.MAX_VALUE;
        FloatPoint c = get_center().to_float();
        for (int i = 0; i < tile_shape_count(); ++i)
        {
            Shape curr_shape = get_shape(i);
            if (curr_shape != null)
            {
                result = Math.min(result, curr_shape.border_distance(c));
            }
        }
        return result;
    }
    
    
    /** Returns the center point of this DrillItem. */
    public Point get_center()
    {
        return center;
    }
    
    protected void set_center(Point p_center)
    {
        center = p_center;
    }
    
    /**
     * Returns the padstack of this drillitem.
     */
    public abstract Padstack get_padstack();
    
    public TileShape get_tree_shape_on_layer(ShapeSearchTree p_tree, int p_layer)
    {
        int from_layer = first_layer();
        int to_layer = last_layer();
        if (p_layer < from_layer || p_layer > to_layer)
        {
            System.out.println("DrillItem.get_tree_shape_on_layer: p_layer out of range");
            return null;
        }
        return get_tree_shape(p_tree, p_layer - from_layer);
    }
    
    public TileShape get_tile_shape_on_layer(int p_layer)
    {
        int from_layer = first_layer();
        int to_layer = last_layer();
        if (p_layer < from_layer || p_layer > to_layer)
        {
            System.out.println("DrillItem.get_tile_shape_on_layer: p_layer out of range");
            return null;
        }
        return get_tile_shape(p_layer - from_layer);
    }
    
    public Shape get_shape_on_layer(int p_layer)
    {
        int from_layer = first_layer();
        int to_layer = last_layer();
        if (p_layer < from_layer || p_layer > to_layer)
        {
            System.out.println("DrillItem.get_shape_on_layer: p_layer out of range");
            return null;
        }
        return get_shape(p_layer - from_layer);
    }
    
    public Set<Item> get_normal_contacts()
    {
        Point drill_center = this.get_center();
        TileShape search_shape = TileShape.get_instance(drill_center);
        Set<SearchTreeObject> overlaps = board.overlapping_objects(search_shape, -1);
        Iterator<SearchTreeObject> it = overlaps.iterator();
        Set<Item> result = new TreeSet<Item>();
        while(it.hasNext())
        {
            SearchTreeObject curr_ob = it.next();
            if (!(curr_ob instanceof Item))
            {
                continue;
            }
            Item curr_item = (Item) curr_ob;
            if (curr_item != this && curr_item.shares_net(this) && curr_item.shares_layer(this))
            {
                if (curr_item instanceof Trace)
                {
                    Trace curr_trace = (Trace) curr_item;
                    if(drill_center.equals(curr_trace.first_corner())
                    || drill_center.equals(curr_trace.last_corner()))
                    {
                        result.add(curr_item);
                    }
                }
                else if (curr_item instanceof DrillItem)
                {
                    DrillItem curr_drill_item = (DrillItem) curr_item;
                    if (drill_center.equals(curr_drill_item.get_center()))
                    {
                        result.add(curr_item);
                    }
                }
                else if (curr_item instanceof ConductionArea)
                {
                    ConductionArea curr_area = (ConductionArea) curr_item;
                    if (curr_area.get_area().contains(drill_center))
                    {
                        result.add(curr_item);
                    }
                }
            }
        }
        return result;
    }
    
    public Point normal_contact_point(Item p_other)
    {
        return p_other.normal_contact_point(this);
    }
    
    Point normal_contact_point(DrillItem p_other)
    {
        if (this.shares_layer(p_other) && this.get_center().equals(p_other.get_center()))
        {
            return this.get_center();
        }
        return null;
    }
    
    Point normal_contact_point(Trace p_trace)
    {
        if (!this.shares_layer(p_trace))
        {
            return null;
        }
        Point drill_center = this.get_center();
        if (drill_center.equals(p_trace.first_corner()) || drill_center.equals(p_trace.last_corner()))
        {
            return drill_center;
        }
        return null;
    }
    
    public Point[] get_ratsnest_corners()
    {
        Point[] result = new Point[1];
        result[0] = this.get_center();
        return result;
    }
    
    public TileShape get_trace_connection_shape(ShapeSearchTree p_search_tree, int p_index)
    {
        return TileShape.get_instance(this.get_center());
    }
    
    /** False, if this drillitem is places on the back side of the board */
    public boolean is_placed_on_front()
    {
        return true;
    }
    
    /**
     * Return the mininal width of the shapes of this DrillItem on all signal layers.
     */
    public double min_width()
    {
        if (this.precalculated_min_width < 0)
        {
            double min_width = Integer.MAX_VALUE;
            int begin_layer = this.first_layer();
            int end_layer = this.last_layer();
            for (int curr_layer = begin_layer; curr_layer  <= end_layer; ++curr_layer )
            {
                if(this.board != null && !this.board.layer_structure.arr[curr_layer].is_signal)
                {
                    continue;
                }
                Shape curr_shape = this.get_shape_on_layer(curr_layer);
                if (curr_shape != null)
                {
                    IntBox curr_bounding_box = curr_shape.bounding_box();
                    min_width = Math.min(min_width, curr_bounding_box.width());
                    min_width = Math.min(min_width, curr_bounding_box.height());
                }
            }
            this.precalculated_min_width = min_width;
        }
        return this.precalculated_min_width;
    }
    
    public void clear_derived_data()
    {
          super.clear_derived_data();
          this.precalculated_first_layer = -1;
          this.precalculated_last_layer = -1;
    }
    
    public int get_draw_priority()
    {
        return boardgraphics.Drawable.MIDDLE_DRAW_PRIORITY;
    }
    
    public void draw(java.awt.Graphics p_g, boardgraphics.GraphicsContext p_graphics_context,
            java.awt.Color[] p_color_arr, double p_intensity)
    {
        if (p_graphics_context == null|| p_intensity <= 0)
        {
            return;
        }
        int from_layer = first_layer();
        int to_layer = last_layer();
        // Decrease the drawing intensity for items with many layers.
        double visibility_factor = 0;
        for (int i = from_layer; i <= to_layer; ++i)
        {
            visibility_factor += p_graphics_context.get_layer_visibility(i);
        }
        
        if (visibility_factor < 0.001)
        {
            return;
        }
        double intensity = p_intensity / Math.max(visibility_factor, 1);
        for (int i = 0; i <= to_layer - from_layer; ++i)
        {
            Shape curr_shape = this.get_shape(i);
            if (curr_shape == null)
            {
                continue;
            }
            java.awt.Color color = p_color_arr[from_layer + i];
            double layer_intensity = intensity * p_graphics_context.get_layer_visibility(from_layer + i);
            p_graphics_context.fill_area(curr_shape, p_g, color, layer_intensity);
        }
    }
    
    /** The center point of the drillitem */
    private Point center;
    
    /**
     * Contains the precalculated mininal width of the shapes of this DrillItem on all layers.
     * If < 0, the value is not yet calculated
     */
    private double precalculated_min_width = -1;
    
    /**
     * Contains the precalculated first layer, where this DrillItem contains a pad shape.
     * If < 0, the value is not yet calculated
     */
    private int precalculated_first_layer = -1;
    
    /**
     * Contains the precalculated last layer, where this DrillItem contains a pad shape.
     * If < 0, the value is not yet calculated
     */
    private int precalculated_last_layer = -1;
    
    /**
     * Auxiliary class used in the method move_by
     */
    private static class TraceInfo implements Comparable<TraceInfo>
    {
        TraceInfo(int p_layer, int p_half_width, int p_clearance_type)
        {
            layer = p_layer;
            half_width = p_half_width;
            clearance_type = p_clearance_type;
        }
        
        /**
         * Implements the comparable interface.
         */
        public int compareTo(TraceInfo p_other)
        {
            return p_other.layer - this.layer;
        }
        
        int layer;
        int half_width;
        int clearance_type;
    }
}
