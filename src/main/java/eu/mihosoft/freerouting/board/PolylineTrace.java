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
 */
package board;

import datastructures.Signum;
import datastructures.Stoppable;

import geometry.planar.IntBox;
import geometry.planar.IntOctagon;
import geometry.planar.Line;
import geometry.planar.LineSegment;
import geometry.planar.Point;
import geometry.planar.IntPoint;
import geometry.planar.FloatPoint;
import geometry.planar.Polyline;
import geometry.planar.Shape;
import geometry.planar.TileShape;
import geometry.planar.Direction;
import geometry.planar.Vector;

import java.awt.Color;
import java.awt.Graphics;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import boardgraphics.GraphicsContext;

/**
 *
 * Objects of class Trace, whose geometry is described by a Polyline
 *
 *
 * @author Alfons Wirtz
 */
public class PolylineTrace extends Trace implements java.io.Serializable
{

    /**
     * creates a new instance of a PolylineTrace with the input data
     */
    public PolylineTrace(Polyline p_polyline, int p_layer, int p_half_width,
            int[] p_net_no_arr, int p_clearance_type, int p_id_no,
            int p_group_no, FixedState p_fixed_state, BasicBoard p_board)
    {
        super(p_layer, p_half_width, p_net_no_arr, p_clearance_type,
                p_id_no, p_group_no, p_fixed_state, p_board);
        if (p_polyline.arr.length < 3)
        {
            System.out.println("PolylineTrace: p_polyline.arr.length >= 3 expected");
        }
        lines = p_polyline;
    }

    public Item copy(int p_id_no)
    {
        int[] curr_net_no_arr = new int[this.net_count()];
        for (int i = 0; i < curr_net_no_arr.length; ++i)
        {
            curr_net_no_arr[i] = get_net_no(i);
        }
        return new PolylineTrace(lines, get_layer(), get_half_width(), curr_net_no_arr, clearance_class_no(),
                p_id_no, get_component_no(), get_fixed_state(), board);
    }

    /**
     * checks, if this trace is on layer p_layer
     */
    public boolean is_on_layer(int p_layer)
    {
        return get_layer() == p_layer;
    }

    /**
     * returns the first corner of this trace, which is the intersection
     * of the first and second lines of its polyline
     */
    public Point first_corner()
    {
        return lines.corner(0);
    }

    /**
     * returns the last corner of this trace, which is the intersection
     * of the last two lines of its polyline
     */
    public Point last_corner()
    {
        return lines.corner(lines.arr.length - 2);
    }

    /**
     * returns the number of corners of this trace, which is the
     * number of lines of its polyline minus one
     */
    public int corner_count()
    {
        return lines.arr.length - 1;
    }

    public double get_length()
    {
        return lines.length_approx();
    }

    public IntBox bounding_box()
    {
        IntBox result = this.lines.bounding_box();
        return result.offset(this.get_half_width());
    }

    public void draw(Graphics p_g, GraphicsContext p_graphics_context, Color[] p_color_arr, double p_intensity)
    {
        if (p_graphics_context == null)
        {
            return;
        }
        int layer = this.get_layer();
        Color color = p_color_arr[layer];
        double display_width = get_half_width();
        double intensity = p_intensity * p_graphics_context.get_layer_visibility(layer);
        p_graphics_context.draw(lines.corner_approx_arr(), display_width, color, p_g, intensity);
    }

    /**
     * Returns the polyline of this trace.
     */
    public Polyline polyline()
    {
        return lines;
    }

    protected TileShape[] calculate_tree_shapes(ShapeSearchTree p_search_tree)
    {
        return p_search_tree.calculate_tree_shapes(this);
    }

    /**
     * returns the count of tile shapes of this polyline
     */
    public int tile_shape_count()
    {
        return Math.max(lines.arr.length - 2, 0);
    }

    public void translate_by(Vector p_vector)
    {
        lines = lines.translate_by(p_vector);
        this.clear_derived_data();
    }

    public void turn_90_degree(int p_factor, IntPoint p_pole)
    {
        lines = lines.turn_90_degree(p_factor, p_pole);
        this.clear_derived_data();
    }

    public void rotate_approx(double p_angle_in_degree, FloatPoint p_pole)
    {
        this.lines = this.lines.rotate_approx(Math.toRadians(p_angle_in_degree), p_pole);
    }

    public void change_placement_side(IntPoint p_pole)
    {
        lines = lines.mirror_vertical(p_pole);

        if (this.board != null)
        {
            this.set_layer(board.get_layer_count() - this.get_layer() - 1);
        }
        this.clear_derived_data();
    }

    /**
     * Looks, if other traces can be combined with this trace.
     * Returns true, if somthing has been combined.
     * This trace will be the combined trace, so that only other traces may be deleted.
     */
    public boolean combine()
    {
        if (!this.is_on_the_board())
        {
            return false;
        }
        boolean something_changed;
        if (this.combine_at_start(true))
        {
            something_changed = true;
            this.combine();
        }
        else if (this.combine_at_end(true))
        {
            something_changed = true;
            this.combine();
        }
        else
        {
            something_changed = false;
        }
        if (something_changed)
        {
            // let the observers syncronize the changes
            board.communication.observers.notify_changed(this);
            board.additional_update_after_change(this);
        }
        return something_changed;
    }

    /**
     * looks, if this trace can be combined at its first point with
     * an other trace. Returns true, if somthing was combined.
     * The corners of the other trace will be inserted in front of thie trace.
     * In case of combine the other trace will be deleted and this trace will
     * remain.
     */
    private boolean combine_at_start(boolean p_ignore_areas)
    {
        Point start_corner = first_corner();
        Collection<Item> contacts = get_normal_contacts(start_corner, false);
        if (p_ignore_areas)
        {
            // remove conduction areas from the list
            Iterator<Item> it = contacts.iterator();
            while (it.hasNext())
            {
                if (it.next() instanceof ConductionArea)
                {
                    it.remove();
                }
            }
        }
        if (contacts.size() != 1)
        {
            return false;
        }
        PolylineTrace other_trace = null;
        boolean trace_found = false;
        boolean reverse_order = false;
        Iterator<Item> it = contacts.iterator();
        while (it.hasNext())
        {
            Item curr_ob = it.next();
            if (curr_ob instanceof PolylineTrace)
            {
                other_trace = (PolylineTrace) curr_ob;
                if (other_trace.get_layer() == get_layer() && other_trace.nets_equal(this) && other_trace.get_half_width() == get_half_width() && other_trace.get_fixed_state() == this.get_fixed_state())
                {
                    if (start_corner.equals(other_trace.last_corner()))
                    {
                        trace_found = true;
                        break;
                    }
                    else if (start_corner.equals(other_trace.first_corner()))
                    {
                        reverse_order = true;
                        trace_found = true;
                        break;
                    }
                }
            }
        }
        if (!trace_found)
        {
            return false;
        }

        board.item_list.save_for_undo(this);
        // create the lines of the joined polyline
        Line[] this_lines = lines.arr;
        Line[] other_lines;
        if (reverse_order)
        {
            other_lines = new Line[other_trace.lines.arr.length];
            for (int i = 0; i < other_lines.length; ++i)
            {
                other_lines[i] = other_trace.lines.arr[other_lines.length - 1 - i].opposite();
            }
        }
        else
        {
            other_lines = other_trace.lines.arr;
        }
        boolean skip_line =
                other_lines[other_lines.length - 2].is_equal_or_opposite(this_lines[1]);
        int new_line_count = this_lines.length + other_lines.length - 2;
        if (skip_line)
        {
            --new_line_count;
        }
        Line[] new_lines = new Line[new_line_count];
        System.arraycopy(other_lines, 0, new_lines, 0, other_lines.length - 1);
        int join_pos = other_lines.length - 1;
        if (skip_line)
        {
            --join_pos;
        }
        System.arraycopy(this_lines, 1, new_lines, join_pos, this_lines.length - 1);
        Polyline joined_polyline = new Polyline(new_lines);
        if (joined_polyline.arr.length != new_line_count)
        {
            // consecutive parallel lines where skipped at the join location
            // combine without performance optimation
            board.search_tree_manager.remove(this);
            this.lines = joined_polyline;
            this.clear_derived_data();
            board.search_tree_manager.insert(this);
        }
        else
        {
            // reuse the tree entries for better performance
            // create the changed line shape at the join location
            int to_no = other_lines.length;
            if (skip_line)
            {
                --to_no;
            }
            board.search_tree_manager.merge_entries_in_front(other_trace, this, joined_polyline,
                    other_lines.length - 3, to_no);
            other_trace.clear_search_tree_entries();
            this.lines = joined_polyline;
        }
        if (this.lines.arr.length < 3)
        {
            board.remove_item(this);
        }
        board.remove_item(other_trace);
        if (board instanceof RoutingBoard)
        {
            ((RoutingBoard) board).join_changed_area(start_corner.to_float(), get_layer());
        }
        return true;
    }

    /**
     * looks, if this trace can be combined at its last point with
     * another trace. Returns true, if somthing was combined.
     * The corners of the other trace will be inserted at the end of thie trace.
     * In case of combine the other trace will be deleted and this trace will
     * remain.
     */
    private boolean combine_at_end(boolean p_ignore_areas)
    {
        Point end_corner = last_corner();
        Collection<Item> contacts = get_normal_contacts(end_corner, false);
        if (p_ignore_areas)
        {
            // remove conduction areas from the list
            Iterator<Item> it = contacts.iterator();
            while (it.hasNext())
            {
                if (it.next() instanceof ConductionArea)
                {
                    it.remove();
                }
            }
        }
        if (contacts.size() != 1)
        {
            return false;
        }
        PolylineTrace other_trace = null;
        boolean trace_found = false;
        boolean reverse_order = false;
        Iterator<Item> it = contacts.iterator();
        while (it.hasNext())
        {
            Item curr_ob = it.next();
            if (curr_ob instanceof PolylineTrace)
            {
                other_trace = (PolylineTrace) curr_ob;
                if (other_trace.get_layer() == get_layer() && other_trace.nets_equal(this) && other_trace.get_half_width() == get_half_width() && other_trace.get_fixed_state() == this.get_fixed_state())
                {
                    if (end_corner.equals(other_trace.first_corner()))
                    {
                        trace_found = true;
                        break;
                    }
                    else if (end_corner.equals(other_trace.last_corner()))
                    {
                        reverse_order = true;
                        trace_found = true;
                        break;
                    }
                }
            }
        }
        if (!trace_found)
        {
            return false;
        }

        board.item_list.save_for_undo(this);
        // create the lines of the joined polyline
        Line[] this_lines = lines.arr;
        Line[] other_lines;
        if (reverse_order)
        {
            other_lines = new Line[other_trace.lines.arr.length];
            for (int i = 0; i < other_lines.length; ++i)
            {
                other_lines[i] = other_trace.lines.arr[other_lines.length - 1 - i].opposite();
            }
        }
        else
        {
            other_lines = other_trace.lines.arr;
        }
        boolean skip_line =
                this_lines[this_lines.length - 2].is_equal_or_opposite(other_lines[1]);
        int new_line_count = this_lines.length + other_lines.length - 2;
        if (skip_line)
        {
            --new_line_count;
        }
        Line[] new_lines = new Line[new_line_count];
        System.arraycopy(this_lines, 0, new_lines, 0, this_lines.length - 1);
        int join_pos = this_lines.length - 1;
        if (skip_line)
        {
            --join_pos;
        }
        System.arraycopy(other_lines, 1, new_lines, join_pos, other_lines.length - 1);
        Polyline joined_polyline = new Polyline(new_lines);
        if (joined_polyline.arr.length != new_line_count)
        {
            // consecutive parallel lines where skipped at the join location
            // combine without performance optimation
            board.search_tree_manager.remove(this);
            this.clear_search_tree_entries();
            this.lines = joined_polyline;
            this.clear_derived_data();
            board.search_tree_manager.insert(this);
        }
        else
        {
            // reuse tree entries for better performance
            // create the changed line shape at the join location
            int to_no = this_lines.length;
            if (skip_line)
            {
                --to_no;
            }
            board.search_tree_manager.merge_entries_at_end(other_trace, this, joined_polyline, this_lines.length - 3, to_no);
            other_trace.clear_search_tree_entries();
            this.lines = joined_polyline;
        }
        if (this.lines.arr.length < 3)
        {
            board.remove_item(this);
        }
        board.remove_item(other_trace);
        if (board instanceof RoutingBoard)
        {
            ((RoutingBoard) board).join_changed_area(end_corner.to_float(), get_layer());
        }
        return true;
    }

    /**
     * Looks up traces intersecting with this trace and splits them at the intersection points.
     * In case of an overlaps, the traces are split at their first and their last common point.
     * Returns the pieces resulting from splitting.
     * Found cycles are removed.
     * If nothing is split, the result will contain just this Trace.
     * If p_clip_shape != null, the split may be resticted to p_clip_shape.
     */
    public Collection<PolylineTrace> split(IntOctagon p_clip_shape)
    {
        Collection<PolylineTrace> result = new LinkedList<PolylineTrace>();
        if (!this.nets_normal())
        {
            // only normal nets are split
            result.add(this);
            return result;
        }
        boolean own_trace_split = false;
        ShapeSearchTree default_tree = board.search_tree_manager.get_default_tree();
        for (int i = 0; i < this.lines.arr.length - 2; ++i)
        {
            if (p_clip_shape != null)
            {
                LineSegment curr_segment = new LineSegment(this.lines, i + 1);
                if (!p_clip_shape.intersects(curr_segment.bounding_box()))
                {
                    continue;
                }
            }
            TileShape curr_shape = this.get_tree_shape(default_tree, i);
            LineSegment curr_line_segment = new LineSegment(this.lines, i + 1);
            Collection<ShapeSearchTree.TreeEntry> overlapping_tree_entries = new LinkedList<ShapeSearchTree.TreeEntry>();
            // look for intersecting traces with the i-th line segment
            default_tree.overlapping_tree_entries(curr_shape, get_layer(), overlapping_tree_entries);
            Iterator<ShapeSearchTree.TreeEntry> it = overlapping_tree_entries.iterator();
            while (it.hasNext())
            {
                if (!this.is_on_the_board())
                {
                    // this trace has been deleted in a cleanup operation
                    return result;
                }
                ShapeSearchTree.TreeEntry found_entry = it.next();
                if (!(found_entry.object instanceof Item))
                {
                    continue;
                }
                Item found_item = (Item) found_entry.object;
                if (found_item == this)
                {

                    if (found_entry.shape_index_in_object >= i - 1 && found_entry.shape_index_in_object <= i + 1)
                    {
                        // don't split own trace at this line or at neighbour lines
                        continue;
                    }
                    // try to handle intermediate segments of length 0 by comparing end corners
                    if (i < found_entry.shape_index_in_object)
                    {
                        if (lines.corner(i + 1).equals(lines.corner(found_entry.shape_index_in_object)))
                        {
                            continue;
                        }
                    }
                    else if (found_entry.shape_index_in_object < i)
                    {
                        if (lines.corner(found_entry.shape_index_in_object + 1).equals(lines.corner(i)))
                        {
                            continue;
                        }
                    }
                }
                if (!found_item.shares_net(this))
                {
                    continue;
                }
                if (found_item instanceof PolylineTrace)
                {
                    PolylineTrace found_trace = (PolylineTrace) found_item;
                    LineSegment found_line_segment =
                            new LineSegment(found_trace.lines, found_entry.shape_index_in_object + 1);
                    Line[] intersecting_lines = found_line_segment.intersection(curr_line_segment);
                    Collection<PolylineTrace> split_pieces = new LinkedList<PolylineTrace>();

                    // try splitting the found trace first
                    boolean found_trace_split = false;

                    if (found_trace != this)
                    {
                        for (int j = 0; j < intersecting_lines.length; ++j)
                        {
                            int line_no = found_entry.shape_index_in_object + 1;
                            PolylineTrace[] curr_split_pieces = found_trace.split(line_no, intersecting_lines[j]);
                            if (curr_split_pieces != null)
                            {

                                for (int k = 0; k < 2; ++k)
                                {
                                    if (curr_split_pieces[k] != null)
                                    {
                                        found_trace_split = true;
                                        split_pieces.add(curr_split_pieces[k]);

                                    }
                                }
                                if (found_trace_split)
                                {
                                    // reread the overlapping tree entries and reset the iterator,
                                    // because the board has changed
                                    default_tree.overlapping_tree_entries(curr_shape, get_layer(), overlapping_tree_entries);
                                    it = overlapping_tree_entries.iterator();
                                    break;
                                }
                            }
                        }
                        if (!found_trace_split)
                        {
                            split_pieces.add(found_trace);
                        }
                    }
                    // now try splitting the own trace

                    intersecting_lines = curr_line_segment.intersection(found_line_segment);
                    for (int j = 0; j < intersecting_lines.length; ++j)
                    {
                        PolylineTrace[] curr_split_pieces = split(i + 1, intersecting_lines[j]);
                        if (curr_split_pieces != null)
                        {
                            own_trace_split = true;
                            // this trace was split itself into 2.
                            if (curr_split_pieces[0] != null)
                            {
                                result.addAll(curr_split_pieces[0].split(p_clip_shape));
                            }
                            if (curr_split_pieces[1] != null)
                            {
                                result.addAll(curr_split_pieces[1].split(p_clip_shape));
                            }
                            break;
                        }
                    }
                    if (found_trace_split || own_trace_split)
                    {
                        // something was split,
                        // remove cycles containing a split piece
                        Iterator<PolylineTrace> it2 = split_pieces.iterator();
                        for (int j = 0; j < 2; ++j)
                        {
                            while (it2.hasNext())
                            {
                                PolylineTrace curr_piece = it2.next();
                                board.remove_if_cycle(curr_piece);
                            }

                            // remove cycles in the own split pieces last
                            // to preserve them, if possible
                            it2 = result.iterator();
                        }
                    }
                    if (own_trace_split)
                    {
                        break;
                    }
                }
                else if (found_item instanceof DrillItem)
                {
                    DrillItem curr_drill_item = (DrillItem) found_item;
                    Point split_point = curr_drill_item.get_center();
                    if (curr_line_segment.contains(split_point))
                    {
                        Direction split_line_direction = curr_line_segment.get_line().direction().turn_45_degree(2);
                        Line split_line = new Line(split_point, split_line_direction);
                        split(i + 1, split_line);
                    }
                }
                else if (!this.is_user_fixed() && (found_item instanceof ConductionArea))
                {
                    boolean ignore_areas = false;
                    if (this.net_no_arr.length > 0)
                    {
                        rules.Net curr_net = this.board.rules.nets.get(this.net_no_arr[0]);
                        if (curr_net != null && curr_net.get_class() != null)
                        {
                            ignore_areas = curr_net.get_class().get_ignore_cycles_with_areas();
                        }
                    }
                    if (!ignore_areas && this.get_start_contacts().contains(found_item) &&
                            this.get_end_contacts().contains(found_item))
                    {
                        // this trace can be removed because of cycle with conduction area
                        board.remove_item(this);
                        return result;
                    }
                }
            }
            if (own_trace_split)
            {
                break;
            }
        }
        if (!own_trace_split)
        {
            result.add(this);
        }
        if (result.size() > 1)
        {
            for (Item curr_item : result)
            {
                board.additional_update_after_change(curr_item);
            }
        }
        return result;
    }

    /**
     * Checks, if the intersection of the p_line_no-th line of this trace with p_line is inside
     * the pad of a pin. In this case the trace will be split only, if the intersection
     * is at the center of the pin.
     * Extending the function to vias leaded to broken connection problems wenn the autorouter connected to a trace.
     */
    private boolean split_inside_drill_pad_prohibited(int p_line_no, Line p_line)
    {
        if (this.board == null)
        {
            return false;
        }
        Point intersection = this.lines.arr[p_line_no].intersection(p_line);
        java.util.Collection<Item> overlap_items = this.board.pick_items(intersection, this.get_layer(), null);
        boolean pad_found = false;
        for (Item curr_item : overlap_items)
        {
            if (!curr_item.shares_net(this))
            {
                continue;
            }
            if (curr_item instanceof Pin)
            {
                DrillItem curr_drill_item = (DrillItem) curr_item;
                if (curr_drill_item.get_center().equals(intersection))
                {
                    return false; // split always at the center of a drill item.
                }
                pad_found = true;
            }
            else if (curr_item instanceof Trace)
            {
                Trace curr_trace = (Trace) curr_item;
                if (curr_trace != this && curr_trace.first_corner().equals(intersection) || curr_trace.last_corner().equals(intersection))
                {
                    return false;
                }
            }
        }
        return pad_found;
    }

    /**
     * Splits this trace into two at p_point.
     * Returns the 2 pieces of the splitted trace, or null if nothing was splitted because for example 
     * p_point is not located on a line segment of the p_polyline of this trace.
     */
    public Trace[] split(Point p_point)
    {
        for (int i = 0; i < this.lines.arr.length - 2; ++i)
        {
            LineSegment curr_line_segment = new LineSegment(this.lines, i + 1);
            if (curr_line_segment.contains(p_point))
            {
                Direction split_line_direction = curr_line_segment.get_line().direction().turn_45_degree(2);
                Line split_line = new Line(p_point, split_line_direction);
                Trace[] result = split(i + 1, split_line);
                if (result != null)
                {
                    return result;
                }
            }
        }
        return null;
    }

    /**
     * Splits this trace at the line with number p_line_no
     * into two by inserting p_endline as concluding line of the first split piece
     * and as the start line of the second split piece.
     * Returns the 2 pieces of the splitted trace, or null, if nothing was splitted.
     */
    private PolylineTrace[] split(int p_line_no, Line p_new_end_line)
    {
        if (!this.is_on_the_board())
        {
            return null;
        }
        Polyline[] split_polylines = lines.split(p_line_no, p_new_end_line);
        if (split_polylines == null)
        {
            return null;
        }
        if (split_polylines.length != 2)
        {
            System.out.println("PolylineTrace.split: array of length 2 expected for split_polylines");
            return null;
        }
        if (split_inside_drill_pad_prohibited(p_line_no, p_new_end_line))
        {
            return null;
        }
        board.remove_item(this);
        PolylineTrace[] result = new PolylineTrace[2];
        result[0] = board.insert_trace_without_cleaning(split_polylines[0], get_layer(), get_half_width(),
                net_no_arr, clearance_class_no(), get_fixed_state());
        result[1] = board.insert_trace_without_cleaning(split_polylines[1], get_layer(), get_half_width(),
                net_no_arr, clearance_class_no(), get_fixed_state());
        return result;
    }

    /**
     * Splits this trace and overlapping traces, and combines this trace.
     * Returns true, if something was changed.
     * If p_clip_shape != null, splitting is restricted to p_clip_shape.
     */
    public boolean normalize(IntOctagon p_clip_shape)
    {
        boolean observers_activated = false;
        BasicBoard routing_board = this.board;
        if (this.board != null)
        {
            // Let the observers know the trace changes.
            observers_activated = !routing_board.observers_active();
            if (observers_activated)
            {
                routing_board.start_notify_observers();
            }
        }
        Collection<PolylineTrace> split_pieces = this.split(p_clip_shape);
        boolean result = (split_pieces.size() != 1);
        Iterator<PolylineTrace> it = split_pieces.iterator();
        while (it.hasNext())
        {
            PolylineTrace curr_split_trace = it.next();
            if (curr_split_trace.is_on_the_board())
            {
                boolean trace_combined = curr_split_trace.combine();
                if (curr_split_trace.corner_count() == 2 && curr_split_trace.first_corner().equals(curr_split_trace.last_corner()))
                {
                    // remove trace with only 1 corner
                    board.remove_item(curr_split_trace);
                    result = true;
                }
                else if (trace_combined)
                {
                    curr_split_trace.normalize(p_clip_shape);
                    result = true;
                }
            }
        }
        if (observers_activated)
        {
            routing_board.end_notify_observers();
        }
        return result;
    }

    /**
     * Tries to shorten this trace without creating clearance violations
     * Returns true, if the trace was changed.
     */
    public boolean pull_tight(PullTightAlgo p_pull_tight_algo)
    {
        if (!this.is_on_the_board())
        {
            // This trace may have been deleted in a trace split for example
            return false;
        }
        if (this.is_shove_fixed())
        {
            return false;
        }
        if (!this.nets_normal())
        {
            return false;
        }
        if (p_pull_tight_algo.only_net_no_arr.length > 0 && !this.nets_equal(p_pull_tight_algo.only_net_no_arr))
        {
            return false;
        }
        if (this.net_no_arr.length > 0)
        {
            if (!this.board.rules.nets.get(this.net_no_arr[0]).get_class().get_pull_tight())
            {
                return false;
            }
        }
        Polyline new_lines =
                p_pull_tight_algo.pull_tight(lines, get_layer(), get_half_width(), net_no_arr, clearance_class_no(),
                this.touching_pins_at_end_corners());
        if (new_lines != lines)
        {
            change(new_lines);
            return true;
        }
        AngleRestriction angle_restriction = this.board.rules.get_trace_angle_restriction();
        if (angle_restriction != AngleRestriction.NINETY_DEGREE && this.board.rules.get_pin_edge_to_turn_dist() > 0)
        {
            if (this.swap_connection_to_pin(true))
            {
                pull_tight(p_pull_tight_algo);
                return true;
            }
            if (this.swap_connection_to_pin(false))
            {
                pull_tight(p_pull_tight_algo);
                return true;
            }
            // optimize algorithm could not improve the trace, try to remove acid traps
            if (this.correct_connection_to_pin(true, angle_restriction))
            {
                pull_tight(p_pull_tight_algo);
                return true;
            }
            if (this.correct_connection_to_pin(false, angle_restriction))
            {
                pull_tight(p_pull_tight_algo);
                return true;
            }
        }
        return false;
    }

    /**
     * Tries to pull this trace tight without creating clearance violations
     * Returns true, if the trace was changed.
     */
    public boolean pull_tight(boolean p_own_net_only, int p_pull_tight_accuracy, Stoppable p_stoppable_thread)
    {
        if (!(this.board instanceof RoutingBoard))
        {
            return false;
        }
        int[] opt_net_no_arr;
        if (p_own_net_only)
        {
            opt_net_no_arr = this.net_no_arr;
        }
        else
        {
            opt_net_no_arr = new int[0];
        }
        PullTightAlgo pull_tight_algo =
                PullTightAlgo.get_instance((RoutingBoard) this.board, opt_net_no_arr,
                null, p_pull_tight_accuracy, p_stoppable_thread, -1, null, -1);
        return pull_tight(pull_tight_algo);
    }

    /**
     * Tries to smoothen the end corners of this trace, which are at a fork with other traces.
     */
    public boolean smoothen_end_corners_fork(boolean p_own_net_only, int p_pull_tight_accuracy, Stoppable p_stoppable_thread)
    {
        if (!(this.board instanceof RoutingBoard))
        {
            return false;
        }
        int[] opt_net_no_arr;
        if (p_own_net_only)
        {
            opt_net_no_arr = this.net_no_arr;
        }
        else
        {
            opt_net_no_arr = new int[0];
        }
        PullTightAlgo pull_tight_algo =
                PullTightAlgo.get_instance((RoutingBoard) this.board, opt_net_no_arr,
                null, p_pull_tight_accuracy, p_stoppable_thread, -1, null, -1);
        return pull_tight_algo.smoothen_end_corners_at_trace(this);
    }

    public TileShape get_trace_connection_shape(ShapeSearchTree p_search_tree, int p_index)
    {
        if (p_index < 0 || p_index >= this.tile_shape_count())
        {
            System.out.println("PolylineTrace.get_trace_connection_shape p_index out of range");
            return null;
        }
        LineSegment curr_line_segment = new LineSegment(this.lines, p_index + 1);
        TileShape result = curr_line_segment.to_simplex().simplify();
        return result;
    }

    public boolean write(java.io.ObjectOutputStream p_stream)
    {
        try
        {
            p_stream.writeObject(this);
        } catch (java.io.IOException e)
        {
            return false;
        }
        return true;
    }

    /**
     * changes the geometry of this trace to p_new_polyline
     */
    void change(Polyline p_new_polyline)
    {
        if (!this.is_on_the_board())
        {
            // Just change the polyline of this trace.
            lines = p_new_polyline;
            return;
        }

        board.additional_update_after_change(this);

        // The precalculated tile shapes must not be cleared here here because they are used and modified
        // in ShapeSearchTree.change_entries.

        board.item_list.save_for_undo(this);

        // for performance reasons  there is some effort to reuse
        // ShapeTree entries of the old trace in the changed trace

        // look for the first line in p_new_polyline different from
        // the lines of the existung trace
        int last_index = Math.min(p_new_polyline.arr.length, lines.arr.length);
        int index_of_first_different_line = last_index;
        for (int i = 0; i < last_index; ++i)
        {
            if (p_new_polyline.arr[i] != lines.arr[i])
            {
                index_of_first_different_line = i;
                break;
            }
        }
        if (index_of_first_different_line == last_index)
        {
            return; // both polylines are equal, no change nessesary
        }
        // look for the last line in p_new_polyline different from
        // the lines of the existung trace
        int index_of_last_different_line = -1;
        for (int i = 1; i <= last_index; ++i)
        {
            if (p_new_polyline.arr[p_new_polyline.arr.length - i] !=
                    lines.arr[lines.arr.length - i])
            {
                index_of_last_different_line = p_new_polyline.arr.length - i;
                break;
            }
        }
        if (index_of_last_different_line < 0)
        {
            return; // both polylines are equal, no change nessesary
        }
        int keep_at_start_count = Math.max(index_of_first_different_line - 2, 0);
        int keep_at_end_count = Math.max(p_new_polyline.arr.length - index_of_last_different_line - 3, 0);
        board.search_tree_manager.change_entries(this, p_new_polyline, keep_at_start_count, keep_at_end_count);
        lines = p_new_polyline;

        // let the observers syncronize the changes
        board.communication.observers.notify_changed(this);

        IntOctagon clip_shape = null;
        if (board instanceof RoutingBoard)
        {
            ChangedArea changed_area = ((RoutingBoard) board).changed_area;
            if (changed_area != null)
            {
                clip_shape = changed_area.get_area(this.get_layer());
            }
        }
        this.normalize(clip_shape);
    }

    /**
     * checks, that the connection restrictions to the contact pins
     * are satisfied. If p_at_start, the start of this trace is checked,
     * else the end. Returns false, if a pin is at that end, where
     * the connection is checked and the connection is not ok.
     */
    public boolean check_connection_to_pin(boolean p_at_start)
    {
        if (this.board == null)
        {
            return true;
        }
        if (this.corner_count() < 2)
        {
            return true;
        }
        Collection<Item> contact_list;
        if (p_at_start)
        {
            contact_list = this.get_start_contacts();
        }
        else
        {
            contact_list = this.get_end_contacts();
        }
        Pin contact_pin = null;
        for (Item curr_contact : contact_list)
        {
            if (curr_contact instanceof Pin)
            {
                contact_pin = (Pin) curr_contact;
                break;
            }
        }
        if (contact_pin == null)
        {
            return true;
        }
        Collection<Pin.TraceExitRestriction> trace_exit_restrictions = contact_pin.get_trace_exit_restrictions(this.get_layer());
        if (trace_exit_restrictions.isEmpty())
        {
            return true;
        }
        Point end_corner;
        Point prev_end_corner;
        if (p_at_start)
        {
            end_corner = this.first_corner();
            prev_end_corner = this.lines.corner(1);
        }
        else
        {
            end_corner = this.last_corner();
            prev_end_corner = this.lines.corner(this.lines.corner_count() - 2);
        }
        Direction trace_end_direction = Direction.get_instance(end_corner, prev_end_corner);
        if (trace_end_direction == null)
        {
            return true;
        }
        Pin.TraceExitRestriction matching_exit_restriction = null;
        for (Pin.TraceExitRestriction curr_exit_restriction : trace_exit_restrictions)
        {
            if (curr_exit_restriction.direction.equals(trace_end_direction))
            {
                matching_exit_restriction = curr_exit_restriction;
                break;
            }
        }
        if (matching_exit_restriction == null)
        {
            return false;
        }
        final double edge_to_turn_dist = this.board.rules.get_pin_edge_to_turn_dist();
        if (edge_to_turn_dist < 0)
        {
            return false;
        }
        double end_line_length = end_corner.to_float().distance(prev_end_corner.to_float());
        double curr_clearance = board.clearance_value(this.clearance_class_no(), contact_pin.clearance_class_no(), this.get_layer());
        double add_width = Math.max(edge_to_turn_dist, curr_clearance + 1);
        double preserve_length = matching_exit_restriction.min_length + this.get_half_width() + add_width;
        if (preserve_length > end_line_length)
        {
            return false;
        }
        return true;
    }

    /**
     * Tries to correct a connection restriction of this trace.
     * If p_at_start, the start of the trace polygon is corrected, else the end.
     *Returns true, if this trace was changed.
     */
    public boolean correct_connection_to_pin(boolean p_at_start, AngleRestriction p_angle_restriction)
    {
        if (this.check_connection_to_pin(p_at_start))
        {
            return false;
        }

        Polyline trace_polyline;
        Collection<Item> contact_list;
        if (p_at_start)
        {
            trace_polyline = this.polyline();
            contact_list = this.get_start_contacts();
        }
        else
        {
            trace_polyline = this.polyline().reverse();
            contact_list = this.get_end_contacts();
        }
        Pin contact_pin = null;
        for (Item curr_contact : contact_list)
        {
            if (curr_contact instanceof Pin)
            {
                contact_pin = (Pin) curr_contact;
                break;
            }
        }
        if (contact_pin == null)
        {
            return false;
        }
        Collection<Pin.TraceExitRestriction> trace_exit_restrictions = contact_pin.get_trace_exit_restrictions(this.get_layer());
        if (trace_exit_restrictions.isEmpty())
        {
            return false;
        }
        Shape pin_shape = contact_pin.get_shape(this.get_layer() - contact_pin.first_layer());
        if (!(pin_shape instanceof TileShape))
        {
            return false;
        }
        Point pin_center = contact_pin.get_center();

        final double edge_to_turn_dist = this.board.rules.get_pin_edge_to_turn_dist();
        if (edge_to_turn_dist < 0)
        {
            return false;
        }
        double curr_clearance = board.clearance_value(this.clearance_class_no(), contact_pin.clearance_class_no(), this.get_layer());
        double add_width = Math.max(edge_to_turn_dist, curr_clearance + 1);
        TileShape offset_pin_shape = (TileShape) ((TileShape) pin_shape).offset(this.get_half_width() + add_width);
        if (p_angle_restriction == AngleRestriction.NINETY_DEGREE || offset_pin_shape.is_IntBox())
        {
            offset_pin_shape = offset_pin_shape.bounding_box();
        }
        else if (p_angle_restriction == AngleRestriction.FORTYFIVE_DEGREE)
        {
            offset_pin_shape = offset_pin_shape.bounding_octagon();
        }
        int[][] entries = offset_pin_shape.entrance_points(trace_polyline);
        if (entries.length == 0)
        {
            return false;
        }
        int[] latest_entry_tuple = entries[entries.length - 1];
        FloatPoint trace_entry_location_approx =
                trace_polyline.arr[latest_entry_tuple[0]].intersection_approx(offset_pin_shape.border_line(latest_entry_tuple[1]));
        // calculate the nearest legal pin exit point to trace_entry_location_approx
        double min_exit_corner_distance = Double.MAX_VALUE;
        Line nearest_pin_exit_ray = null;
        int nearest_border_line_no = -1;
        Direction pin_exit_direction = null;
        FloatPoint nearest_exit_corner = null;
        final double TOLERANCE = 1;
        for (Pin.TraceExitRestriction curr_exit_restriction : trace_exit_restrictions)
        {
            int curr_intersecting_border_line_no = offset_pin_shape.intersecting_border_line_no(pin_center, curr_exit_restriction.direction);
            Line curr_pin_exit_ray = new Line(pin_center, curr_exit_restriction.direction);
            FloatPoint curr_exit_corner = curr_pin_exit_ray.intersection_approx(offset_pin_shape.border_line(curr_intersecting_border_line_no));
            double curr_exit_corner_distance = curr_exit_corner.distance_square(trace_entry_location_approx);
            boolean new_nearest_corner_found = false;
            if (curr_exit_corner_distance + TOLERANCE < min_exit_corner_distance)
            {
                new_nearest_corner_found = true;
            }
            else if (curr_exit_corner_distance < min_exit_corner_distance + TOLERANCE)
            {
                // the distances are near equal, compare to the previous corners of p_trace_polyline
                for (int i = 1; i < trace_polyline.corner_count(); ++i)
                {
                    FloatPoint curr_trace_corner = trace_polyline.corner_approx(i);
                    double curr_trace_corner_distance = curr_trace_corner.distance_square(curr_exit_corner);
                    double old_trace_corner_distance = curr_trace_corner.distance_square(nearest_exit_corner);
                    if (curr_trace_corner_distance + TOLERANCE < old_trace_corner_distance)
                    {
                        new_nearest_corner_found = true;
                        break;
                    }
                    else if (curr_trace_corner_distance > old_trace_corner_distance + TOLERANCE)
                    {
                        break;
                    }
                }
            }
            if (new_nearest_corner_found)
            {
                min_exit_corner_distance = curr_exit_corner_distance;
                nearest_pin_exit_ray = curr_pin_exit_ray;
                nearest_border_line_no = curr_intersecting_border_line_no;
                pin_exit_direction = curr_exit_restriction.direction;
                nearest_exit_corner = curr_exit_corner;
            }
        }

        // append the polygon piece around the border of the pin shape.

        Line[] curr_lines;

        int corner_count = offset_pin_shape.border_line_count();
        int clock_wise_side_diff =
                (nearest_border_line_no - latest_entry_tuple[1] + corner_count) % corner_count;
        int counter_clock_wise_side_diff =
                (latest_entry_tuple[1] - nearest_border_line_no + corner_count) % corner_count;
        int curr_border_line_no = nearest_border_line_no;
        if (counter_clock_wise_side_diff <= clock_wise_side_diff)
        {
            curr_lines = new Line[counter_clock_wise_side_diff + 3];
            for (int i = 0; i <= counter_clock_wise_side_diff; ++i)
            {
                curr_lines[i + 1] = offset_pin_shape.border_line(curr_border_line_no);
                curr_border_line_no = (curr_border_line_no + 1) % corner_count;
            }
        }
        else
        {
            curr_lines = new Line[clock_wise_side_diff + 3];
            for (int i = 0; i <= clock_wise_side_diff; ++i)
            {
                curr_lines[i + 1] = offset_pin_shape.border_line(curr_border_line_no);
                curr_border_line_no = (curr_border_line_no - 1 + corner_count) % corner_count;
            }
        }
        curr_lines[0] = nearest_pin_exit_ray;
        curr_lines[curr_lines.length - 1] = trace_polyline.arr[latest_entry_tuple[0]];

        Polyline border_polyline = new Polyline(curr_lines);
        if (!this.board.check_polyline_trace(border_polyline, this.get_layer(),
                this.get_half_width(), this.net_no_arr, this.clearance_class_no()))
        {
            return false;
        }

        Line[] cut_lines = new Line[trace_polyline.arr.length - latest_entry_tuple[0] + 1];
        cut_lines[0] = curr_lines[curr_lines.length - 2];
        for (int i = 1; i < cut_lines.length; ++i)
        {
            cut_lines[i] = trace_polyline.arr[latest_entry_tuple[0] + i - 1];

        }
        Polyline cut_polyline = new Polyline(cut_lines);
        Polyline changed_polyline;
        if (cut_polyline.first_corner().equals(cut_polyline.last_corner()))
        {
            changed_polyline = border_polyline;
        }
        else
        {
            changed_polyline = border_polyline.combine(cut_polyline);
        }
        if (!p_at_start)
        {
            changed_polyline = changed_polyline.reverse();
        }
        this.change(changed_polyline);


        // create an shove_fixed exit line.
        curr_lines = new Line[3];
        curr_lines[0] = new Line(pin_center, pin_exit_direction.turn_45_degree(2));
        curr_lines[1] = nearest_pin_exit_ray;
        curr_lines[2] = offset_pin_shape.border_line(nearest_border_line_no);
        Polyline exit_line_segment = new Polyline(curr_lines);
        this.board.insert_trace(exit_line_segment, this.get_layer(), this.get_half_width(), this.net_no_arr,
                this.clearance_class_no(), FixedState.SHOVE_FIXED);
        return true;
    }

    /**
     * Looks, if an other pin connection restriction fits better than the current connection restriction
     * and changes this trace in this case.
     * If p_at_start, the start of the trace polygon is changed, else the end.
     * Returns true, if this trace was changed.
     */
    public boolean swap_connection_to_pin(boolean p_at_start)
    {
        Polyline trace_polyline;
        Collection<Item> contact_list;
        if (p_at_start)
        {
            trace_polyline = this.polyline();
            contact_list = this.get_start_contacts();
        }
        else
        {
            trace_polyline = this.polyline().reverse();
            contact_list = this.get_end_contacts();
        }
        if (contact_list.size() != 1)
        {
            return false;
        }
        Item curr_contact = contact_list.iterator().next();
        if (!(curr_contact.get_fixed_state() == FixedState.SHOVE_FIXED && (curr_contact instanceof PolylineTrace)))
        {
            return false;
        }
        PolylineTrace contact_trace = (PolylineTrace) curr_contact;
        Polyline contact_polyline = contact_trace.polyline();
        Line contact_last_line = contact_polyline.arr[contact_polyline.arr.length - 2];
        // look, if this trace has a sharp angle with the contact trace.
        Line first_line = trace_polyline.arr[1];
        // check for sharp angle
        boolean check_swap = contact_last_line.direction().projection(first_line.direction()) == Signum.NEGATIVE;
        if (!check_swap)
        {
            double half_width = this.get_half_width();
            if (trace_polyline.arr.length > 3 &&
                    trace_polyline.corner_approx(0).distance_square(trace_polyline.corner_approx(1)) <= half_width * half_width)
            {
                // check also for sharp angle with the second line
                check_swap =
                        (contact_last_line.direction().projection(trace_polyline.arr[2].direction()) == Signum.NEGATIVE);
            }
        }
        if (!check_swap)
        {
            return false;
        }
        Pin contact_pin = null;
        Collection<Item> curr_contacts = contact_trace.get_start_contacts();
        for (Item tmp_contact : curr_contacts)
        {
            if (tmp_contact instanceof Pin)
            {
                contact_pin = (Pin) tmp_contact;
                break;
            }
        }
        if (contact_pin == null)
        {
            return false;
        }
        Polyline combined_polyline = contact_polyline.combine(trace_polyline);
        Direction nearest_pin_exit_direction =
                contact_pin.calc_nearest_exit_restriction_direction(combined_polyline, this.get_half_width(), this.get_layer());
        if (nearest_pin_exit_direction == null || nearest_pin_exit_direction.equals(contact_polyline.arr[1].direction()))
        {
            return false; // direction would not be changed
        }
        contact_trace.set_fixed_state(this.get_fixed_state());
        this.combine();
        return true;
    }
    // primary data
    private Polyline lines;
}
