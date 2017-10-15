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
 * Created on 1. September 2003, 08:40
 */

package board;

import geometry.planar.FloatPoint;
import geometry.planar.Line;
import geometry.planar.Polyline;
import geometry.planar.Side;
import geometry.planar.TileShape;

/**
 * Used in the shove algorithm to calculate the fromside for pushing and
 * to cut off dog ears of the trace shape.
 *
 * @author  Alfons Wirtz
 */

class CalcShapeAndFromSide
{
    /**
     * Used in the shove algorithm to calculate the fromside for pushing and
     * to cut off dog ears of the trace shape.
     * In the check shove functions, p_in_shove_check is expected to be true.
     * In the actual shove functions p_in_shove_check is expected to be false.
     */
    CalcShapeAndFromSide(PolylineTrace p_trace,  int p_index, boolean p_orthogonal, boolean p_in_shove_check)
    {
        ShapeSearchTree search_tree = p_trace.board.search_tree_manager.get_default_tree();
        TileShape curr_shape = p_trace.get_tree_shape(search_tree, p_index);
        CalcFromSide curr_from_side = null;
        boolean cut_off_at_start = false;
        boolean cut_off_at_end = false;
        if (p_orthogonal)
        {
            curr_shape = curr_shape.bounding_box();
        }
        else
        {
            // prevent dog ears at the start and the end of the substitute trace
            curr_shape = curr_shape.to_Simplex();
            Line end_cutline = calc_cutline_at_end(p_index, p_trace);
            if (end_cutline != null)
            {
                TileShape cut_plane = TileShape.get_instance(end_cutline);
                TileShape tmp_shape = curr_shape.intersection(cut_plane);
                if (tmp_shape != curr_shape && !tmp_shape.is_empty())
                {
                    curr_shape = tmp_shape.to_Simplex();
                    cut_off_at_end = true;
                }
            }
            Line start_cutline = calc_cutline_at_start(p_index, p_trace);
            if (start_cutline != null)
            {
                TileShape cut_plane = TileShape.get_instance(start_cutline);
                TileShape tmp_shape = curr_shape.intersection(cut_plane);
                if (tmp_shape != curr_shape && !tmp_shape.is_empty())
                {
                    curr_shape = tmp_shape.to_Simplex();
                    cut_off_at_start = true;
                    
                }
            }
            int from_side_no = -1;
            Line curr_cut_line = null;
            if (cut_off_at_start == true)
            {
                curr_cut_line = start_cutline;
                from_side_no = curr_shape.border_line_index(curr_cut_line);
            }
            if (from_side_no < 0 && cut_off_at_end == true)
            {
                curr_cut_line = end_cutline;
                from_side_no = curr_shape.border_line_index(curr_cut_line);
            }
            if (from_side_no >= 0)
            {
                FloatPoint border_intersection =
                        curr_cut_line.intersection_approx(curr_shape.border_line(from_side_no));
                curr_from_side = new CalcFromSide(from_side_no,  border_intersection);
            }
        }
        if (curr_from_side == null && !p_in_shove_check )
        {
           // In p_in_shove_check, using this calculation may produce an undesired stack_level > 1 in ShapeTraceEntries.       
           curr_from_side = new CalcFromSide(p_trace.polyline(), p_index, curr_shape);    
        }
        this.shape = curr_shape;
        this.from_side = curr_from_side;
    }
    
    private static Line calc_cutline_at_end(int p_index, PolylineTrace p_trace)
    {
        Polyline trace_lines = p_trace.polyline();
        ShapeSearchTree search_tree = p_trace.board.search_tree_manager.get_default_tree();
        if (p_index == trace_lines.arr.length - 3 ||
                trace_lines.corner_approx(trace_lines.arr.length - 2).distance(trace_lines.corner_approx(p_index + 1))
                < p_trace.get_compensated_half_width(search_tree))
        {
            
            Line curr_line = trace_lines.arr[trace_lines.arr.length - 1];
            FloatPoint is = trace_lines.corner_approx(trace_lines.arr.length - 3);
            Line cut_line;
            if (curr_line.side_of(is) == Side.ON_THE_LEFT)
            {
                cut_line = curr_line.opposite();
            }
            else
            {
                cut_line = curr_line;
            }
            return cut_line;
        }
        return null;
    }
    
    private static Line calc_cutline_at_start(int p_index, PolylineTrace p_trace)
    {
        Polyline trace_lines = p_trace.polyline();
        ShapeSearchTree search_tree = p_trace.board.search_tree_manager.get_default_tree();
        if (p_index == 0 ||
                trace_lines.corner_approx(0).distance(trace_lines.corner_approx(p_index))
                < p_trace.get_compensated_half_width(search_tree))
        {
            Line curr_line = trace_lines.arr[0];
            FloatPoint is = trace_lines.corner_approx(1);
            Line cut_line;
            if (curr_line.side_of(is) == Side.ON_THE_LEFT)
            {
                cut_line = curr_line.opposite();
            }
            else
            {
                cut_line = curr_line;
            }
            return cut_line;
        }
        return null;
    }
    
    
    final TileShape shape;
    final CalcFromSide from_side;
}
