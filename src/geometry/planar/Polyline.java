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

package geometry.planar;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/**
 *
 * A Polyline is a sequence of lines, where no 2 consecutive
 * lines may be parallel. A Polyline of n lines defines a Polygon of
 * n-1 intersection points of consecutive lines.
 * The lines of the objects of class Polyline are normally defined
 * by points with integer coordinates, wheras the intersections
 * of Lines can be representated in general only by infinite precision
 * rational points. We use polylines with integer coordinates instead
 * of polygons with infinite precision rational coordinates
 * because of its better performance in geometric calculations.
 *
 *
 * @author Alfons Wirtz
 */

public class Polyline implements java.io.Serializable
{
    /**
     * creates a polyline of length p_polygon.corner_count + 1 from p_polygon,
     * so that the i-th corner of p_polygon will be the intersection of
     * the i-th and the i+1-th lines of the new created p_polyline
     * for 0 <= i < p_point_arr.length. p_polygon must have at least 2 corners
     */
    public Polyline(Polygon p_polygon)
    {
        Point[] point_arr = p_polygon.corner_array();
        if (point_arr.length < 2)
        {
            System.out.println("Polyline: must contain at least 2 different points");
            arr = new Line[0];
            return;
        }
        arr = new Line [point_arr.length + 1];
        for (int i = 1; i < point_arr.length; ++i)
        {
            arr[i] = new Line(point_arr[i - 1], point_arr[i]);
        }
        // construct perpendicular lines at the start and at the end to represent
        // the first and the last point of point_arr as intersection of lines.
        
        Direction dir = Direction.get_instance(point_arr[0], point_arr[1]);
        arr [0] = Line.get_instance(point_arr[0], dir.turn_45_degree(2));
        
        dir = Direction.get_instance(point_arr[point_arr.length - 1], point_arr[point_arr.length - 2]);
        arr[point_arr.length] =
                Line.get_instance(point_arr[point_arr.length - 1], dir.turn_45_degree(2));
    }
    
    public Polyline(Point[] p_points)
    {
        this(new Polygon(p_points));
    }
    
    /**
     * creates a polyline consisting of 3 lines
     */
    public Polyline(Point p_from_corner, Point p_to_corner)
    {
        if (p_from_corner.equals(p_to_corner))
        {
            arr = new Line [0];
            return;
        }
        arr = new Line [3];
        Direction dir = Direction.get_instance(p_from_corner, p_to_corner);
        arr [0] = Line.get_instance(p_from_corner, dir.turn_45_degree(2));
        arr[1] = new Line(p_from_corner, p_to_corner);
        dir = Direction.get_instance(p_from_corner, p_to_corner);
        arr [2] = Line.get_instance(p_to_corner, dir.turn_45_degree(2));
    }
    
    /**
     * Creates a polyline from an array of lines.
     * Lines, which are parallel to the previous line are skipped.
     * The directed lines are normalized, so that they intersect
     * the previous line before the next line
     */
    public Polyline(Line[] p_line_arr)
    {
        Line [] lines  = remove_consecutive_parallel_lines(p_line_arr);
        lines = remove_overlaps(lines);
        if (lines.length < 3 )
        {
            arr = new Line[0];
            return;
        }
        precalculated_float_corners = new FloatPoint [lines.length - 1];
        
        // turn evtl the direction of the lines that they point always
        // from the previous corner to the next corner
        for (int i = 1; i < lines.length - 1; ++i)
        {
            precalculated_float_corners[i] = lines[i].intersection_approx(lines[i + 1]);
            Side side_of_line = lines[i - 1].side_of(precalculated_float_corners[i]);
            if (side_of_line != Side.COLLINEAR)
            {
                Direction d0 = lines[i - 1].direction();
                Direction d1 = lines[i].direction();
                Side side1 = d0.side_of(d1);
                if (side1 != side_of_line)
                {
                    lines[i] = lines[i].opposite();
                }
            }
        }
        arr = lines;
    }
    
    /**
     * Returns the number of lines minus 1
     */
    public int corner_count()
    {
        return arr.length - 1;
    }
    
    public boolean is_empty()
    {
        return arr.length < 3;
    }
    
    /**
     * Checks, if this polyline is empty or if all corner points are equal.
     */
    public boolean is_point()
    {
        if ( arr.length < 3)
        {
            return true;
        }
        Point first_corner = this.corner(0);
        for (int i = 1; i < arr.length - 1; ++i)
        {
            if (!(this.corner(i).equals(first_corner)))
            {
                return false;
            }
        }
        return true;
    }
    
    /**
     * checks, if all lines of this polyline are orthogonal
     */
    public boolean is_orthogonal()
    {
        for (int i = 0; i < arr.length; ++i)
        {
            if (!arr[i].is_orthogonal())
            {
                return false;
            }
        }
        return true;
    }
    
    /**
     * checks, if all lines of this polyline are multiples of 45 degree
     */
    public boolean is_multiple_of_45_degree()
    {
        for (int i = 0; i < arr.length; ++i)
        {
            if (!arr[i].is_multiple_of_45_degree())
            {
                return false;
            }
        }
        return true;
    }
    
    /**
     * returns the intersection of the first line with the second line
     */
    public Point first_corner()
    {
        return corner(0);
    }
    
    /**
     * returns the intersection of the last line with the line before
     * the last line
     */
    public Point last_corner()
    {
        return corner(arr.length - 2);
    }
    
    /**
     * returns the array of the intersection of two consecutive lines
     * approximated by FloatPoint's.
     */
    public Point [] corner_arr()
    {
        if (arr.length < 2)
        {
            return new Point[0];
        }
        if (precalculated_corners == null)
            // corner array is not yet allocated
        {
            precalculated_corners = new Point[arr.length - 1];
        }
        for (int i = 0; i < precalculated_corners.length; ++i)
        {
            if (precalculated_corners[i] == null)
            {
                precalculated_corners[i] = arr[i].intersection(arr[i + 1]);
            }
        }
        return precalculated_corners;
    }
    
    /**
     * returns the array of the intersection of two consecutive lines
     * approximated by FloatPoint's.
     */
    public FloatPoint [] corner_approx_arr()
    {
        if (arr.length < 2)
        {
            return new FloatPoint[0];
        }
        if (precalculated_float_corners == null)
            // corner array is not yet allocated
        {
            precalculated_float_corners = new FloatPoint[arr.length - 1];
        }
        for (int i = 0; i < precalculated_float_corners.length; ++i)
        {
            if (precalculated_float_corners[i] == null)
            {
                precalculated_float_corners[i] = arr[i].intersection_approx(arr[i + 1]);
            }
        }
        return precalculated_float_corners;
    }
    
    /**
     * Returns an approximation of the intersection of the p_no-th with
     * the (p_no - 1)-th line by a FloatPoint.
     */
    public FloatPoint corner_approx(int p_no)
    {
        int no;
        if (p_no < 0)
        {
            System.out.println("Polyline.corner_approx: p_no is < 0");
            no = 0;
        }
        else if (p_no >= arr.length - 1)
        {
            System.out.println("Polyline.corner_approx: p_no must be less than arr.length - 1");
            no = arr.length - 2;
        }
        else
        {
            no  = p_no;
        }
        if (precalculated_float_corners == null)
            // corner array is not yet allocated
        {
            precalculated_float_corners = new FloatPoint[arr.length - 1];
            for (int i = 0; i < precalculated_float_corners.length; ++i)
            {
                precalculated_float_corners[i] = null;
            }
        }
        if (precalculated_float_corners [no] == null)
            // corner is not yet calculated
        {
            precalculated_float_corners[no] = arr[no].intersection_approx(arr[no + 1]);
        }
        return precalculated_float_corners [no];
    }
    
    /**
     * Returns  the intersection of the p_no-th with the (p_no - 1)-th edge line.
     */
    public Point corner(int p_no)
    {
        if (arr.length < 2)
        {
            System.out.println("Polyline.corner: arr.length is < 2");
            return null;
        }
        int no;
        if (p_no < 0)
        {
            System.out.println("Polyline.corner: p_no is < 0");
            no = 0;
        }
        else if (p_no >= arr.length - 1)
        {
            System.out.println("Polyline.corner: p_no must be less than arr.length - 1");
            no = arr.length - 2;
        }
        else
        {
            no  = p_no;
        }
        if (precalculated_corners == null)
            // corner array is not yet allocated
        {
            precalculated_corners = new Point[arr.length - 1];
            for (int i = 0; i < precalculated_corners.length; ++i)
            {
                precalculated_corners[i] = null;
            }
        }
        if (precalculated_corners [no] == null)
            // corner is not yet calculated
        {
            precalculated_corners[no] = arr[no].intersection(arr[no + 1]);
        }
        return precalculated_corners [no];
    }
    
    /**
     * return the polyline with the reversed order of lines
     */
    public Polyline reverse()
    {
        Line [] reversed_lines  = new Line[arr.length];
        for (int i = 0; i < arr.length; ++i)
        {
            reversed_lines[i] = arr[arr.length - i - 1].opposite();
        }
        return new Polyline(reversed_lines);
    }
    
    /**
     * Calculates the length of this polyline from p_from_corner
     * to p_to_corner.
     */
    public double length_approx(int p_from_corner, int p_to_corner)
    {
        int from_corner = Math.max(p_from_corner, 0);
        int to_corner = Math.min(p_to_corner, arr.length - 2);
        double result = 0;
        for (int i = from_corner; i < to_corner; ++i)
        {
            result += this.corner_approx(i + 1).distance(this.corner_approx(i));
        }
        return result;
    }
    
    /**
     * Calculates the cumulative distance between consecutive corners of
     * this polyline.
     */
    public double length_approx()
    {
        return length_approx(0, arr.length - 2);
    }
    
    /**
     * calculates for each line a shape around this line
     * where the right and left edge lines have the distance p_half_width
     * from the center line
     * Returns an array of convex shapes of length line_count - 2
     */
    public TileShape[] offset_shapes(int p_half_width)
    {
        return offset_shapes(p_half_width, 0, arr.length -1);
    }
    
    /**
     * calculates for each line between p_from_no and p_to_no a shape around
     * this line, where the right and left edge lines have the distance p_half_width
     * from the center line
     */
    public TileShape[] offset_shapes(int p_half_width,
            int p_from_no, int p_to_no)
    {
        int from_no = Math.max(p_from_no, 0);
        int to_no = Math.min(p_to_no, arr.length -1);
        int shape_count = Math.max(to_no - from_no -1, 0);
        TileShape[] shape_arr = new TileShape[shape_count];
        if (shape_count == 0)
        {
            return shape_arr;
        }
        Vector prev_dir = arr[from_no].direction().get_vector();
        Vector curr_dir = arr[from_no + 1].direction().get_vector();
        for (int i = from_no + 1; i < to_no; ++i)
        {
            Vector next_dir = arr[i + 1].direction().get_vector();
            
            Line[] lines = new Line[4];
            
            lines[0] = arr[i].translate(-p_half_width);
            // current center line translated to the right
            
            // create the front line of the offset shape
            Side  next_dir_from_curr_dir = next_dir.side_of(curr_dir);
            // left turn from curr_line to next_line
            if (next_dir_from_curr_dir == Side.ON_THE_LEFT)
            {
                lines[1] = arr[i + 1].translate(-p_half_width);
                // next right line
            }
            else
            {
                lines[1] = arr[i + 1].opposite().translate(-p_half_width);
                // next left line in opposite direction
            }
            
            lines[2] = arr[i].opposite().translate(-p_half_width);
            // current left line in opposite direction
            
            // create the back line of the offset shape
            Side  curr_dir_from_prev_dir = curr_dir.side_of(prev_dir);
            // left turn from prev_line to curr_line
            if (curr_dir_from_prev_dir == Side.ON_THE_LEFT)
            {
                lines[3] = arr[i - 1].translate(-p_half_width);
                // previous line translated to the right
            }
            else
            {
                lines[3] = arr[i - 1].opposite().translate(-p_half_width);
                // previous left line in opposite direction
            }
            // cut off outstanding corners with following shapes
            FloatPoint corner_to_check = null;
            Line curr_line = lines[1];
            Line check_line = null;
            if (next_dir_from_curr_dir == Side.ON_THE_LEFT)
            {
                check_line = lines[2];
            }
            else
            {
                check_line = lines[0];
            }
            FloatPoint check_distance_corner = corner_approx(i);
            final double check_dist_square = 2.0 * p_half_width * p_half_width;
            Collection<Line> cut_dog_ear_lines = new LinkedList<Line>();
            Vector tmp_curr_dir = next_dir;
            boolean direction_changed = false;
            for (int j = i + 2; j < arr.length - 1; ++j)
            {
                if (corner_approx(j - 1).distance_square(check_distance_corner)
                > check_dist_square)
                {
                    break;
                }
                if (!direction_changed)
                {
                    corner_to_check = curr_line.intersection_approx(check_line);
                }
                Vector tmp_next_dir = arr[j].direction().get_vector();
                Line next_border_line = null;
                Side tmp_next_dir_from_tmp_curr_dir = tmp_next_dir.side_of(tmp_curr_dir);
                direction_changed =
                        tmp_next_dir_from_tmp_curr_dir != next_dir_from_curr_dir;
                if (!direction_changed)
                {
                    if (tmp_next_dir_from_tmp_curr_dir == Side.ON_THE_LEFT)
                    {
                        next_border_line = arr[j].translate(-p_half_width);
                    }
                    else
                    {
                        next_border_line = arr[j].opposite().translate(-p_half_width);
                    }
                    
                    if (next_border_line.side_of(corner_to_check) == Side.ON_THE_LEFT
                            && next_border_line.side_of(this.corner(i)) == Side.ON_THE_RIGHT
                            && next_border_line.side_of(this.corner(i - 1)) == Side.ON_THE_RIGHT)
                        // an outstanding corner
                    {
                        cut_dog_ear_lines.add(next_border_line);
                    }
                    tmp_curr_dir = tmp_next_dir;
                    curr_line = next_border_line;
                }
            }
            // cut off outstanding corners with previous shapes
            check_distance_corner = corner_approx(i - 1);
            if (curr_dir_from_prev_dir == Side.ON_THE_LEFT)
            {
                check_line = lines[2];
            }
            else
            {
                check_line = lines[0];
            }
            curr_line = lines [3];
            tmp_curr_dir = prev_dir;
            direction_changed = false;
            for (int j = i - 2; j >= 1; --j)
            {
                if (corner_approx(j).distance_square(check_distance_corner)
                > check_dist_square)
                {
                    break;
                }
                if (!direction_changed)
                {
                    corner_to_check = curr_line.intersection_approx(check_line);
                }
                Vector tmp_prev_dir = arr[j].direction().get_vector();
                Line prev_border_line = null;
                Side tmp_curr_dir_from_tmp_prev_dir = tmp_curr_dir.side_of(tmp_prev_dir);
                direction_changed =
                        tmp_curr_dir_from_tmp_prev_dir != curr_dir_from_prev_dir;
                if (!direction_changed)
                {
                    if (tmp_curr_dir.side_of(tmp_prev_dir) == Side.ON_THE_LEFT)
                    {
                        prev_border_line = arr[j].translate(-p_half_width);
                    }
                    else
                    {
                        prev_border_line = arr[j].opposite().translate(-p_half_width);
                    }
                    if (prev_border_line.side_of(corner_to_check) == Side.ON_THE_LEFT
                            && prev_border_line.side_of(this.corner(i)) == Side.ON_THE_RIGHT
                            && prev_border_line.side_of(this.corner(i - 1)) == Side.ON_THE_RIGHT)
                        // an outstanding corner
                    {
                        cut_dog_ear_lines.add(prev_border_line);
                    }
                    tmp_curr_dir = tmp_prev_dir;
                    curr_line = prev_border_line;
                }
            }
            TileShape s1 = TileShape.get_instance(lines);
            int cut_line_count = cut_dog_ear_lines.size();
            if (cut_line_count > 0)
            {
                Line[] cut_lines = new Line[cut_line_count];
                Iterator<Line> it = cut_dog_ear_lines.iterator();
                for (int j = 0; j < cut_line_count; ++j)
                {
                    cut_lines[j] = it.next();
                }
                s1  = s1.intersection(TileShape.get_instance(cut_lines));
            }
            int curr_shape_no = i - from_no - 1;
            TileShape bounding_shape;
            if (USE_BOUNDING_OCTAGON_FOR_OFFSET_SHAPES)
                // intersect with the bounding octagon
            {
                IntOctagon surr_oct = bounding_octagon(i-1, i);
                bounding_shape = surr_oct.offset(p_half_width);
                
            }
            else
                // intersect with the bounding box
            {
                IntBox surr_box = bounding_box(i-1, i);
                IntBox offset_box = surr_box.offset(p_half_width);
                bounding_shape = offset_box.to_Simplex();
            }
            shape_arr[curr_shape_no] = bounding_shape.intersection_with_simplify(s1);
            if (shape_arr[curr_shape_no].is_empty())
            {
                System.out.println("offset_shapes: shape is empty");
            }
            
            prev_dir = curr_dir;
            curr_dir = next_dir;
            
        }
        return shape_arr;
    }
    
    /**
     * Calculates for the p_no-th line segment a shape around this line
     * where the right and left edge lines have the distance p_half_width
     * from the center line. 0 <= p_no <=  arr.length - 3
     */
    public TileShape offset_shape(int p_half_width, int p_no)
    {
        if (p_no < 0 || p_no > arr.length - 3)
        {
            System.out.println("Polyline.offset_shape: p_no out of range");
            return null;
        }
        TileShape[] result = offset_shapes(p_half_width, p_no, p_no + 2);
        return result[0];
    }
    
    /**
     * Calculates for the p_no-th line segment a box shape around this line
     * where the border lines have the distance p_half_width
     * from the center line. 0 <= p_no <=  arr.length - 3
     */
    public IntBox offset_box(int p_half_width, int p_no)
    {
        LineSegment curr_line_segment = new LineSegment(this, p_no + 1);
        IntBox result = curr_line_segment.bounding_box().offset(p_half_width);
        return result;
    }
    
    /**
     * Returns the by p_vector translated polyline
     */
    public Polyline translate_by(Vector p_vector)
    {
        if (p_vector.equals(Vector.ZERO))
        {
            return this;
        }
        Line [] new_arr = new Line[arr.length];
        for (int i = 0; i < new_arr.length; ++i)
        {
            new_arr[i] = arr[i].translate_by(p_vector);
        }
        return new Polyline(new_arr);
    }
    
    /**
     * Returns the polyline turned by p_factor times 90 degree around p_pole.
     */
    public Polyline turn_90_degree(int p_factor, IntPoint p_pole)
    {
        Line [] new_arr = new Line[arr.length];
        for (int i = 0; i < new_arr.length; ++i)
        {
            new_arr[i] = arr[i].turn_90_degree(p_factor, p_pole);
        }
        return new Polyline(new_arr);
    }
    
    public Polyline rotate_approx(double p_angle, FloatPoint p_pole)
    {
        if (p_angle == 0)
        {
            return this;
        }
        IntPoint [] new_corners = new IntPoint[this.corner_count()];
        for (int i = 0; i < new_corners.length; ++i)
        {
            
            new_corners[i] = this.corner_approx(i).rotate(p_angle, p_pole).round();
        }
        return new Polyline(new_corners);
    }
    
    /** Mirrors this polyline at the vertical line through p_pole */
    public Polyline mirror_vertical(IntPoint p_pole)
    {
        Line [] new_arr = new Line[arr.length];
        for (int i = 0; i < new_arr.length; ++i)
        {
            new_arr[i] = arr[i].mirror_vertical(p_pole);
        }
        return new Polyline(new_arr);
    }
    
    /** Mirrors this polyline at the horizontal line through p_pole */
    public Polyline mirror_horizontal(IntPoint p_pole)
    {
        Line [] new_arr = new Line[arr.length];
        for (int i = 0; i < new_arr.length; ++i)
        {
            new_arr[i] = arr[i].mirror_horizontal(p_pole);
        }
        return new Polyline(new_arr);
    }
    
    
    /**
     * Returns the smallest box containing the intersection points
     * from index p_from_corner_no to index p_to_corner_no
     * of the lines of this polyline
     */
    public IntBox bounding_box(int p_from_corner_no, int p_to_corner_no)
    {
        int from_corner_no = Math.max(p_from_corner_no, 0);
        int to_corner_no = Math.min(p_to_corner_no, arr.length - 2);
        double llx = Integer.MAX_VALUE;
        double lly = llx;
        double urx = Integer.MIN_VALUE;
        double ury = urx;
        for (int i = from_corner_no; i <= to_corner_no; ++i)
        {
            FloatPoint curr_corner = corner_approx(i);
            llx = Math.min(llx, curr_corner.x);
            lly = Math.min(lly, curr_corner.y);
            urx = Math.max(urx, curr_corner.x);
            ury = Math.max(ury, curr_corner.y);
        }
        IntPoint lower_left = new IntPoint((int)Math.floor(llx), (int)Math.floor(lly));
        IntPoint upper_right = new IntPoint((int)Math.ceil(urx), (int)Math.ceil(ury));
        return  new IntBox(lower_left, upper_right);
    }
    
    /**
     * Returns the smallest box containing the intersection points
     * of the lines of this polyline
     */
    public IntBox bounding_box()
    {
        if (precalculated_bounding_box == null)
        {
            precalculated_bounding_box = bounding_box(0, corner_count() - 1);
        }
        return precalculated_bounding_box;
    }
    
    /**
     * Returns the smallest octagon containing the intersection points
     * from index p_from_corner_no to index p_to_corner_no
     * of the lines of this polyline
     */
    public IntOctagon bounding_octagon(int p_from_corner_no, int p_to_corner_no)
    {
        int from_corner_no = Math.max(p_from_corner_no, 0);
        int to_corner_no = Math.min(p_to_corner_no, arr.length - 2);
        double lx = Integer.MAX_VALUE;
        double ly = Integer.MAX_VALUE;
        double rx = Integer.MIN_VALUE;
        double uy = Integer.MIN_VALUE;
        double ulx = Integer.MAX_VALUE;
        double lrx = Integer.MIN_VALUE;
        double llx = Integer.MAX_VALUE;
        double urx = Integer.MIN_VALUE;
        for (int i = from_corner_no; i <= to_corner_no; ++i)
        {
            FloatPoint curr = corner_approx(i);
            lx = Math.min(lx, curr.x);
            ly = Math.min(ly, curr.y);
            rx = Math.max(rx, curr.x);
            uy = Math.max(uy, curr.y);
            double tmp = curr.x - curr.y;
            ulx = Math.min(ulx, tmp);
            lrx = Math.max(lrx, tmp);
            tmp = curr.x + curr.y;
            llx = Math.min(llx, tmp);
            urx = Math.max(urx, tmp);
        }
        IntOctagon surrounding_octagon = new
                IntOctagon((int)Math.floor(lx), (int)Math.floor(ly),
                (int)Math.ceil(rx), (int)Math.ceil(uy),
                (int)Math.floor(ulx), (int)Math.ceil(lrx),
                (int)Math.floor(llx), (int)Math.ceil(urx));
        return surrounding_octagon;
    }
    
    /**
     * Calculates an aproximation of the nearest point on this
     * polyline to p_from_point.
     */
    public FloatPoint nearest_point_approx(FloatPoint p_from_point)
    {
        double min_distance = Double.MAX_VALUE;
        FloatPoint nearest_point = null;
        // calculate the nearest corner point
        FloatPoint[] corners = corner_approx_arr();
        for (int i = 0; i < corners.length; ++i)
        {
            double curr_distance = corners[i].distance(p_from_point);
            if (curr_distance < min_distance)
            {
                min_distance = curr_distance;
                nearest_point = corners[i];
            }
        }
        final double c_tolerance = 1;
        for (int i = 1; i < arr.length - 1; ++i)
        {
            FloatPoint projection = p_from_point.projection_approx(arr[i]);
            double curr_distance = projection.distance(p_from_point);
            if (curr_distance < min_distance)
            {
                // look, if the projection is inside the segment
                double segment_length = corners[i].distance(corners[i - 1]);
                if (projection.distance(corners[i]) + projection.distance(corners[i - 1])
                < segment_length + c_tolerance)
                {
                    min_distance = curr_distance;
                    nearest_point = projection;
                }
            }
        }
        return nearest_point;
    }
    
    /**
     * Calculates the distance of p_from_point to the the nearest point
     * on this polyline
     */
    public double  distance(FloatPoint p_from_point)
    {
        double result = p_from_point.distance(nearest_point_approx(p_from_point));
        return result;
    }
    
    /**
     * Combines the two polylines, if they have a common end corner.
     * The order of lines in this polyline will be preserved.
     * Returns the combined polyline or this polyline, if this polyline
     * and p_other have no common end corner.
     * If there is something to combine at the start of this polyline,
     * p_other is inserted in front of this polyline.
     * If there is something to combine at the end of this polyline,
     * this polyline is inserted in front of p_other.
     */
    public Polyline combine(Polyline p_other)
    {
        if (p_other == null || arr.length < 3
                || p_other.arr.length < 3)
        {
            return this;
        }
        boolean combine_at_start;
        boolean combine_other_at_start;
        if (first_corner().equals(p_other.first_corner()))
        {
            combine_at_start = true;
            combine_other_at_start = true;
        }
        else if (first_corner().equals(p_other.last_corner()))
        {
            combine_at_start = true;
            combine_other_at_start = false;
        }
        else if (last_corner().equals(p_other.first_corner()))
        {
            combine_at_start = false;
            combine_other_at_start = true;
        }
        else if (last_corner().equals(p_other.last_corner()))
        {
            combine_at_start = false;
            combine_other_at_start = false;
        }
        else
        {
            return this; // no common endpoint
        }
        Line [] line_arr = new Line [arr.length + p_other.arr.length - 2];
        if (combine_at_start)
        {
            // insert the lines of p_other in front
            if (combine_other_at_start)
            {
                // insert in reverse order, skip the first line of p_other
                for (int i = 0; i < p_other.arr.length - 1; ++i)
                {
                    line_arr[i] = p_other.arr[p_other.arr.length - i - 1].opposite();
                }
            }
            else
            {
                // skip the last line of p_other
                for (int i = 0; i < p_other.arr.length - 1; ++i)
                {
                    line_arr[i] = p_other.arr[i];
                }
            }
            // append the lines of this polyline, skip the first line
            for (int i = 1; i < arr.length; ++i)
            {
                line_arr[p_other.arr.length + i - 2] = arr[i];
            }
        }
        else
        {
            // insert the lines of this polyline in front, skip the last line
            for (int i = 0; i < arr.length - 1; ++i)
            {
                line_arr[i] = arr[i];
            }
            if (combine_other_at_start)
            {
                // skip the first line of p_other
                for (int i = 1; i < p_other.arr.length; ++i)
                {
                    line_arr[arr.length + i - 2] = p_other.arr[i];
                }
            }
            else
            {
                // insert in reverse order, skip the last line of p_other
                for (int i = 1; i < p_other.arr.length; ++i)
                {
                    line_arr[arr.length + i - 2] =
                            p_other.arr[p_other.arr.length - i - 1].opposite();
                }
            }
        }
        return new Polyline(line_arr);
    }
    
    /**
     * Splits this polyline at the line with number p_line_no
     * into two by inserting p_endline as concluding line of the first split piece
     * and as the start line of the second split piece.
     * p_endline and the line with number p_line_no must not be parallel.
     * The order of the lines ins the two result pieces is preserved.
     * p_line_no must be bigger than 0 and less then arr.length - 1.
     * Returns null, if nothing was split.
     */
    public Polyline[] split(int p_line_no, Line p_end_line)
    {
        if (p_line_no < 1 || p_line_no > arr.length - 2)
        {
            System.out.println("Polyline.split: p_line_no out of range");
            return null;
        }
        if (this.arr[p_line_no].is_parallel(p_end_line))
        {
            return null;
        }
        Point new_end_corner = this.arr[p_line_no].intersection(p_end_line);
        if (p_line_no <= 1 && new_end_corner.equals(this.first_corner()) ||
                p_line_no >= arr.length - 2 && new_end_corner.equals(this.last_corner()))
        {
            // No split, if p_end_line does not intersect, but touches
            // only tnis Polyline at an end point.
            return null;
        }
        Line[] first_piece;
        if (this.corner(p_line_no - 1).equals(new_end_corner))
        {
            // skip line segment of length 0 at the end of the first piece
            first_piece  = new Line [p_line_no + 1];
            System.arraycopy(arr,  0, first_piece, 0, first_piece.length);
            
        }
        else
        {
            first_piece  = new Line [p_line_no + 2];
            System.arraycopy(arr,  0, first_piece, 0, p_line_no + 1);
            first_piece[p_line_no + 1] = p_end_line;
        }
        Line[] second_piece;
        if (this.corner(p_line_no).equals(new_end_corner))
        {
            // skip line segment of length 0 at the beginning of the second piece
            second_piece = new Line [arr.length - p_line_no];
            System.arraycopy(this.arr, p_line_no,second_piece, 0, second_piece.length);
            
        }
        else
        {
            second_piece  = new Line [arr.length - p_line_no + 1];
            second_piece[0] = p_end_line;
            System.arraycopy(this.arr, p_line_no, second_piece, 1,  second_piece.length - 1);
        }
        Polyline [] result = new Polyline[2];
        result[0] = new Polyline(first_piece);
        result[1] = new Polyline(second_piece);
        if (result[0].is_point() || result[1].is_point())
        {
            return null;
        }
        return result;
    }
    
    /**
     * create a new Polyline by skipping the lines of this Polyline
     * from p_from_no to p_to_no
     */
    public Polyline skip_lines(int p_from_no, int p_to_no)
    {
        if (p_from_no < 0 || p_to_no > arr.length - 1 || p_from_no > p_to_no)
        {
            return this;
        }
        Line [] new_lines = new Line [arr.length - (p_to_no - p_from_no + 1)];
        System.arraycopy(arr, 0, new_lines, 0, p_from_no);
        System.arraycopy(arr, p_to_no + 1, new_lines, p_from_no, new_lines.length - p_from_no);
        return new Polyline(new_lines);
    }
    
    public boolean contains(Point p_point)
    {
        for (int i = 1; i < arr.length - 1; ++i)
        {
            LineSegment curr_segment = new LineSegment(this, i);
            if (curr_segment.contains(p_point))
            {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Creates a perpendicular line segment from p_from_point onto the nearest
     * line segment of this polyline to p_from_side.
     * Returns null, if the perpendicular line does not intersect the neares line
     * segment inside its segment bounds or if p_from_point is contained in
     * this polyline.
     */
    public LineSegment projection_line(Point p_from_point)
    {
        FloatPoint from_point = p_from_point.to_float();
        double min_distance = Double.MAX_VALUE;
        Line result_line = null;
        Line nearest_line = null;
        for (int i = 1; i < arr.length - 1; ++i)
        {
            FloatPoint projection = from_point.projection_approx(arr[i]);
            double curr_distance = projection.distance(from_point);
            if (curr_distance < min_distance)
            {
                Direction direction_towards_line = this.arr[i].perpendicular_direction(p_from_point);
                if (direction_towards_line == null)
                {
                    continue;
                }
                Line curr_result_line =  new Line(p_from_point, direction_towards_line);
                Point prev_corner = this.corner(i - 1);
                Point next_corner = this.corner(i);
                Side prev_corner_side = curr_result_line.side_of(prev_corner);
                Side next_corner_side = curr_result_line.side_of(next_corner);
                if (prev_corner_side != Side.COLLINEAR && next_corner_side != Side.COLLINEAR
                        && prev_corner_side == next_corner_side)
                {
                    // the projection point is outside the line segment
                    continue;
                }
                nearest_line = this.arr[i];
                min_distance = curr_distance;
                result_line = curr_result_line;
            }
        }
        if (nearest_line == null)
        {
            return null;
        }
        Line start_line = new Line(p_from_point, nearest_line.direction());
        LineSegment result = new LineSegment(start_line, result_line, nearest_line);
        return result;
    }
    
    /**
     * Shortens this polyline to p_new_line_count lines. Additioanally
     * the last line segment will be approximately shortened to p_new_length.
     * The last corner of the new polyline will be an IntPoint.
     */
    public Polyline shorten(int p_new_line_count, double p_last_segment_length)
    {
        FloatPoint last_corner = this.corner_approx(p_new_line_count - 2);
        FloatPoint prev_last_corner = this.corner_approx(p_new_line_count - 3);
        IntPoint new_last_corner = prev_last_corner.change_length(last_corner, p_last_segment_length).round();
        if (new_last_corner.equals(this.corner(this.corner_count() - 2)))
        {
            // skip the last line
            return  skip_lines(  p_new_line_count - 1,  p_new_line_count - 1);
        }
        Line[] new_lines = new Line [p_new_line_count];
        System.arraycopy(arr, 0, new_lines, 0, p_new_line_count - 2);
        // create the last 2 lines of the new polyline
        Point first_line_point = arr[p_new_line_count - 2].a;
        if (first_line_point.equals(new_last_corner))
        {
            first_line_point = arr[p_new_line_count - 2].b;
        }
        Line new_prev_last_line = new Line(first_line_point, new_last_corner);
        new_lines[p_new_line_count - 2] = new_prev_last_line;
        new_lines[p_new_line_count - 1] =
                Line.get_instance(new_last_corner, new_prev_last_line.direction().turn_45_degree(6));
        return new Polyline(new_lines);
    }
    
    
    private static Line[] remove_consecutive_parallel_lines( Line [] p_line_arr)
    {
        if (p_line_arr.length < 3)
        {
            // polyline must have at least 3 lines
            return p_line_arr;
        }
        Line [] tmp_arr = new Line [p_line_arr.length];
        int new_length = 0;
        tmp_arr[0] = p_line_arr [0];
        for (int i = 1; i < p_line_arr.length; ++i)
        {
            // skip multiple lines
            if (!tmp_arr[new_length].is_parallel(p_line_arr[i]))
            {
                ++new_length;
                tmp_arr[new_length] = p_line_arr[i];
            }
        }
        ++new_length;
        if (new_length == p_line_arr.length)
        {
            // nothing skipped
            return p_line_arr;
        }
        // at least 1 line is skipped, adjust the array
        if (new_length < 3)
        {
            return new Line[0];
        }
        Line [] result = new Line[new_length];
        System.arraycopy(tmp_arr, 0, result, 0, new_length);
        return result;
    }
    
    /**
     * checks if previous and next line are equal or opposite and
     * removes the resulting overlap
     */
    private static Line [] remove_overlaps(Line [] p_line_arr)
    {
        if (p_line_arr.length < 4)
        {
            return p_line_arr;
        }
        int new_length = 0;
        Line [] tmp_arr = new Line [p_line_arr.length];
        tmp_arr[0] = p_line_arr[0];
        if (!p_line_arr[0].is_equal_or_opposite(p_line_arr[2]))
        {
            ++new_length;
        }
        // else  skip the first line
        tmp_arr[new_length] = p_line_arr[1];
        ++new_length;
        for (int i = 2; i < p_line_arr.length - 2; ++i)
        {
            if (tmp_arr[new_length - 1].is_equal_or_opposite(p_line_arr [i + 1]))
            {
                // skip 2 lines
                --new_length;
            }
            else
            {
                tmp_arr[new_length] = p_line_arr [i];
                ++new_length;
            }
        }
        tmp_arr [new_length] = p_line_arr[p_line_arr.length - 2];
        ++new_length;
        if (!p_line_arr[p_line_arr.length - 1].is_equal_or_opposite(tmp_arr[new_length - 2]))
        {
            tmp_arr[new_length] = p_line_arr[p_line_arr.length - 1];
            ++new_length;
        }
        // else skip the last line
        if (new_length == p_line_arr.length)
        {
            // nothing skipped
            return p_line_arr;
        }
        // at least 1 line is skipped, adjust the array
        if (new_length < 3)
        {
            return new Line[0];
        }
        Line [] result = new Line[new_length];
        System.arraycopy(tmp_arr, 0, result, 0, new_length);
        return result;
    }
    
    
    /**
     * the array of lines of this Polyline.
     */
    public final Line[] arr;
    
    transient private FloatPoint[] precalculated_float_corners = null;
    transient private Point[] precalculated_corners = null;
    transient private IntBox precalculated_bounding_box = null;
    private static final boolean USE_BOUNDING_OCTAGON_FOR_OFFSET_SHAPES = true;
}