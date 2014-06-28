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
 * Convex shape defined as intersection of half-planes.
 * A half-plane is defined as the positive side of a directed line.
 *
 * @author Alfons Wirtz
 */


public class Simplex extends TileShape implements java.io.Serializable
{
    
    /**
     * Standard implementation for an empty Simplex.
     */
    public static final Simplex EMPTY = new Simplex(new Line [0]);
    
    /**
     * creates a Simplex as intersection of the halfplanes defined
     * by an array of directed lines
     */
    public static Simplex get_instance(Line[] p_line_arr)
    {
        if (p_line_arr.length <= 0)
        {
            return Simplex.EMPTY;
        }
        Line [] curr_arr = new Line[p_line_arr.length];
        System.arraycopy(p_line_arr, 0, curr_arr, 0, p_line_arr.length);
        // sort the lines in ascending direction
        java.util.Arrays.sort(curr_arr);
        Simplex curr_simplex = new Simplex(curr_arr);
        Simplex result = curr_simplex.remove_redundant_lines();
        return result;
    }
    
    /**
     * Return true, if this simplex is empty
     */
    public boolean is_empty()
    {
        return (arr.length == 0);
    }
    
    /**
     * Converts the physical instance of this shape to a simpler physical instance, if possible.
     * (For example a Simplex to an IntOctagon).
     */
    public TileShape simplify()
    {
        TileShape result = this;
        if (this.is_empty())
        {
            result = Simplex.EMPTY;
        }
        else if (this.is_IntBox())
        {
            result = this.bounding_box();
        }
        else if (this.is_IntOctagon())
        {
            result = this.to_IntOctagon();
        }
        return result;
    }
    
    /**
     * Returns true,  if the determinant of the direction of index
     * p_no -1 and the direction of index p_no is > 0
     */
    public boolean corner_is_bounded(int p_no)
    {
        int no;
        if (p_no < 0)
        {
            System.out.println("corner: p_no is < 0");
            no = 0;
        }
        else if (p_no >= arr.length)
        {
            System.out.println("corner: p_index must be less than arr.length - 1");
            no = arr.length - 1;
        }
        else
        {
            no  = p_no;
        }
        if(arr.length == 1)
        {
            return false;
        }
        int prev_no;
        if (no == 0)
        {
            prev_no = arr.length - 1;
        }
        else
        {
            prev_no = no - 1;
        }
        IntVector prev_dir = (IntVector)arr[prev_no].direction().get_vector();
        IntVector curr_dir = (IntVector)arr[no].direction().get_vector();
        return (prev_dir.determinant(curr_dir) > 0);
    }
    
    
    /**
     * Returns true, if the shape of this simplex is contained in a
     * sufficiently large box
     */
    public boolean is_bounded()
    {
        if (arr.length == 0)
        {
            return true;
        }
        if (arr.length < 3)
        {
            return false;
        }
        for (int i = 0; i < arr.length; ++i)
        {
            if (!corner_is_bounded(i))
            {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Returns the number of edge lines defining this simplex
     */
    public int border_line_count()
    {
        return arr.length;
    }
    
    /**
     * Returns the intersection of the p_no -1-th with the p_no-th line of this simplex.
     * If the simplex is not bounded at this corner, the
     * coordinates of the result will be set to Integer.MAX_VALUE.
     */
    public Point corner(int p_no)
    {
        int no;
        if (p_no < 0)
        {
            System.out.println("Simplex.corner: p_no is < 0");
            no = 0;
        }
        else if (p_no >= arr.length)
        {
            System.out.println("Simplex.corner: p_no must be less than arr.length - 1");
            no = arr.length - 1;
        }
        else
        {
            no  = p_no;
        }
        if (precalculated_corners == null)
            // corner array is not yet allocated
        {
            precalculated_corners = new Point[arr.length];
        }
        if (precalculated_corners [no] == null)
            // corner is not yet calculated
        {
            Line prev;
            if (no == 0)
            {
                prev = arr[arr.length - 1];
            }
            else
            {
                prev = arr[no - 1];
            }
            precalculated_corners[no] = arr[no].intersection(prev);
        }
        return precalculated_corners [no];
    }
    
    /**
     * Returns an approximation of the intersection of the p_no -1-th with the
     * p_no-th line of this simplex by a FloatPoint.
     * If the simplex is not bounded at this corner, the
     * coordinates of the result will be set to Integer.MAX_VALUE.
     */
    public FloatPoint corner_approx(int p_no)
    {
        if (arr.length <= 0)
        {
            return null;
        }
        int no;
        if (p_no < 0)
        {
            System.out.println("Simplex.corner_approx: p_no is < 0");
            no = 0;
        }
        else if (p_no >= arr.length)
        {
            System.out.println("Simplex.corner_approx: p_no must be less than arr.length - 1");
            no = arr.length - 1;
        }
        else
        {
            no  = p_no;
        }
        if (precalculated_float_corners == null)
            // corner array is not yet allocated
        {
            precalculated_float_corners = new FloatPoint[arr.length];
        }
        if (precalculated_float_corners [no] == null)
            // corner is not yet calculated
        {
            Line prev;
            if (no == 0)
            {
                prev = arr[arr.length - 1];
            }
            else
            {
                prev = arr[no - 1];
            }
            precalculated_float_corners[no] = arr[no].intersection_approx(prev);
        }
        return precalculated_float_corners [no];
    }
    
    public FloatPoint[] corner_approx_arr()
    {
        if (precalculated_float_corners == null)
            // corner array is not yet allocated
        {
            precalculated_float_corners = new FloatPoint[arr.length];
        }
        for (int i = 0; i < precalculated_float_corners.length; ++i)
        {
            if (precalculated_float_corners [i] == null)
                // corner is not yet calculated
            {
                Line prev;
                if (i == 0)
                {
                    prev = arr[arr.length - 1];
                }
                else
                {
                    prev = arr[i - 1];
                }
                precalculated_float_corners[i] = arr[i].intersection_approx(prev);
            }
        }
        return precalculated_float_corners;
    }
    
    /**
     * returns the p_no-th edge line of this simplex.
     * The edge lines are sorted in ascending direction.
     */
    public Line border_line(int p_no)
    {
        if (arr.length <= 0)
        {
            System.out.println("Simplex.edge_line : simplex is empty");
            return null;
        }
        int no;
        if (p_no < 0)
        {
            System.out.println("Simplex.edge_line : p_no is < 0");
            no = 0;
        }
        else if (p_no >= arr.length)
        {
            System.out.println("Simplex.edge_line: p_no must be less than arr.length - 1");
            no = arr.length - 1;
        }
        else
        {
            no  = p_no;
        }
        return arr[no];
    }
    
    /**
     * Returns the dimension of this simplex.
     * The result may be 2, 1, 0, or -1 (if the simplex is empty).
     */
    public int dimension()
    {
        if (arr.length == 0)
        {
            return -1;
        }
        if (arr.length > 4)
        {
            return 2;
        }
        if (arr.length == 1)
        {
            // we have a half plane
            return 2;
        }
        if (arr.length == 2)
        {
            if(arr[0].overlaps(arr[1]))
            {
                return 1;
            }
            return 2;
        }
        if (arr.length == 3)
        {
            if (arr[0].overlaps(arr[1]) || arr[0].overlaps(arr[2])
            || arr[1].overlaps(arr[2]))
            {
                // simplex is 1 dimensional and unbounded at one side
                return 1;
            }
            Point intersection = arr[1].intersection(arr[2]);
            Side side_of_line0 = arr[0].side_of(intersection);
            if(side_of_line0 == Side.ON_THE_RIGHT)
            {
                return 2;
            }
            if (side_of_line0 == Side.ON_THE_LEFT)
            {
                System.out.println("empty Simplex not normalized");
                return -1;
            }
            // now the 3 lines intersect in the same point
            return 0;
        }
        // now the simplex has 4 edge lines
        // check if opposing lines are collinear
        boolean collinear_0_2 = arr[0].overlaps(arr[2]);
        boolean collinear_1_3 = arr[1].overlaps(arr[3]);
        if (collinear_0_2 && collinear_1_3)
        {
            return 0;
        }
        if (collinear_0_2 || collinear_1_3)
        {
            return 1;
        }
        return 2;
    }
    
    public double max_width()
    {
        if (!this.is_bounded())
        {
            return Integer.MAX_VALUE;
        }
        double max_distance = Integer.MIN_VALUE;
        double max_distance_2 = Integer.MIN_VALUE;
        FloatPoint   gravity_point = this.centre_of_gravity();
        
        for (int i = 0; i < border_line_count(); ++i)
        {
            double curr_distance = Math.abs(arr[i].signed_distance(gravity_point));
            
            if (curr_distance > max_distance)
            {
                max_distance_2 = max_distance;
                max_distance = curr_distance;
            }
            else if (curr_distance > max_distance_2)
            {
                max_distance_2 = curr_distance;
            }
        }
        return max_distance + max_distance_2;
    }
    
    public double min_width()
    {
        if (!this.is_bounded())
        {
            return Integer.MAX_VALUE;
        }
        double min_distance = Integer.MAX_VALUE;
        double min_distance_2 = Integer.MAX_VALUE;
        FloatPoint   gravity_point = this.centre_of_gravity();
        
        for (int i = 0; i < border_line_count(); ++i)
        {
            double curr_distance = Math.abs(arr[i].signed_distance(gravity_point));
            
            if (curr_distance < min_distance)
            {
                min_distance_2 = min_distance;
                min_distance = curr_distance;
            }
            else if (curr_distance < min_distance_2)
            {
                min_distance_2 = curr_distance;
            }
        }
        return min_distance + min_distance_2;
    }
    
    
    /**
     * checks if this simplex can be converted into an IntBox
     */
    public boolean is_IntBox()
    {
        for (int i = 0; i < arr.length; ++i)
        {
            Line curr_line = arr[i];
            if (!(curr_line.a instanceof IntPoint &&
                    curr_line.b instanceof IntPoint ))
            {
                return false;
            }
            if (!curr_line.is_orthogonal())
            {
                return false;
            }
            if (!corner_is_bounded(i))
            {
                return false;
            }
        }
        return true;
    }
    
    /**
     * checks if this simplex can be converted into an IntOctagon
     */
    public boolean is_IntOctagon()
    {
        for (int i = 0; i < arr.length; ++i)
        {
            Line curr_line = arr[i];
            if (!(curr_line.a instanceof IntPoint &&
                    curr_line.b instanceof IntPoint ))
            {
                return false;
            }
            if (!curr_line.is_multiple_of_45_degree())
            {
                return false;
            }
            if (!corner_is_bounded(i))
            {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Converts this IntSimplex to an IntOctagon.
     * Returns null, if that is not possible, because not all lines
     * of this IntSimplex are 45 degree
     */
    public IntOctagon to_IntOctagon()
    {
        // this function is at the moment only implemented for lines
        // consisting of IntPoints.
        // The general implementation is still missing.
        if (!is_IntOctagon())
        {
            return null;
        }
        if (is_empty())
        {
            return IntOctagon.EMPTY;
        }
        
        // initialise to biggest octagon values
        
        int rx = Limits.CRIT_INT;
        int uy = Limits.CRIT_INT;
        int lrx = Limits.CRIT_INT;
        int urx = Limits.CRIT_INT;
        int lx = -Limits.CRIT_INT;
        int ly = -Limits.CRIT_INT;
        int llx = -Limits.CRIT_INT;
        int ulx = -Limits.CRIT_INT;
        for (int i = 0; i < arr.length; ++i)
        {
            Line curr_line = arr[i];
            IntPoint a = (IntPoint) curr_line.a;
            IntPoint b = (IntPoint) curr_line.b;
            if (a.y == b.y)
            {
                if (b.x >= a.x)
                {
                    // lower boundary line
                    ly = a.y;
                }
                if (b.x <= a.x)
                {
                    // upper boundary line
                    uy = a.y;
                }
            }
            if (a.x == b.x)
            {
                if (b.y >= a.y)
                {
                    // right boundary line
                    rx = a.x;
                }
                if (b.y <= a.y)
                {
                    // left boundary line
                    lx = a.x;
                }
            }
            if (a.y < b.y)
            {
                if (a.x < b.x)
                {
                    // lower right boundary line
                    lrx = a.x - a.y;
                }
                else if (a.x > b.x)
                {
                    // upper right boundary line
                    urx = a.x + a.y;
                }
            }
            else if (a.y > b.y)
            {
                if (a.x < b.x)
                {
                    // lower left boundary line
                    llx = a.x + a.y;
                }
                else if (a.x > b.x)
                {
                    // upper left boundary line
                    ulx = a.x - a.y;
                }
            }
        }
        IntOctagon result = new IntOctagon(lx, ly, rx, uy, ulx, lrx, llx, urx);
        return result.normalize();
    }
    
    /**
     * Returns the simplex, which results from translating
     * the lines of this simplex by p_vector
     */
    public Simplex translate_by(Vector p_vector)
    {
        if (p_vector.equals(Vector.ZERO))
        {
            return this;
        }
        Line[] new_arr = new Line[arr.length];
        for( int i = 0; i < arr.length; ++i)
        {
            new_arr [i] = arr[i].translate_by(p_vector);
        }
        return new Simplex(new_arr);
    }
    
    
    /**
     * Returns the smallest box with int coordinates containing
     * all corners of this simplex.
     * The coordinates of the result will be Integer.MAX_VALUE,
     * if the simplex is not bounded
     */
    public IntBox bounding_box()
    {
        if (arr.length == 0)
        {
            return IntBox.EMPTY;
        }
        if (precalculated_bounding_box == null)
        {
            double llx = Integer.MAX_VALUE;
            double lly = Integer.MAX_VALUE;
            double urx = Integer.MIN_VALUE;
            double ury = Integer.MIN_VALUE;
            for (int i = 0; i < arr.length; ++i)
            {
                FloatPoint curr = corner_approx(i);
                llx = Math.min(llx, curr.x);
                lly = Math.min(lly, curr.y);
                urx = Math.max(urx, curr.x);
                ury = Math.max(ury, curr.y);
            }
            IntPoint lower_left = new IntPoint((int)Math.floor(llx),(int)Math.floor(lly));
            IntPoint upper_right = new IntPoint((int)Math.ceil(urx),(int)Math.ceil(ury));
            precalculated_bounding_box = new IntBox(lower_left, upper_right);
        }
        return precalculated_bounding_box;
    }
    
    /**
     * Calculates a bounding octagon of the Simplex.
     * Returns null, if the Simplex is not bounded.
     */
    public IntOctagon bounding_octagon()
    {
        if (precalculated_bounding_octagon == null)
        {
            double lx = Integer.MAX_VALUE;
            double ly = Integer.MAX_VALUE;
            double rx = Integer.MIN_VALUE;
            double uy = Integer.MIN_VALUE;
            double ulx = Integer.MAX_VALUE;
            double lrx = Integer.MIN_VALUE;
            double llx = Integer.MAX_VALUE;
            double urx = Integer.MIN_VALUE;
            for (int i = 0; i < arr.length; ++i)
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
            if (Math.min(lx, ly) < -Limits.CRIT_INT
                    || Math.max(rx, uy) > Limits.CRIT_INT
                    || Math.min(ulx, llx) < -Limits.CRIT_INT
                    || Math.max(lrx, urx) > Limits.CRIT_INT)
                // result is not bounded
            {
                return null;
            }
            precalculated_bounding_octagon = new
                    IntOctagon((int)Math.floor(lx), (int)Math.floor(ly),
                    (int)Math.ceil(rx), (int)Math.ceil(uy),
                    (int)Math.floor(ulx), (int)Math.ceil(lrx),
                    (int)Math.floor(llx), (int)Math.ceil(urx));
        }
        return precalculated_bounding_octagon;
    }
    
    public Simplex bounding_tile()
    {
        return this;
    }
    
    public RegularTileShape bounding_shape(ShapeBoundingDirections p_dirs)
    {
        return p_dirs.bounds(this);
    }
    
    
    /**
     * Returns the simplex offseted by p_with.
     * If p_width > 0, the offset is to the outer, else to the inner.
     */
    public Simplex offset(double p_width)
    {
        if (p_width == 0)
        {
            return this;
        }
        Line[] new_arr = new Line[arr.length];
        for (int i = 0; i < arr.length; ++i)
        {
            new_arr[i] = arr[i].translate(-p_width);
        }
        Simplex offset_simplex = new Simplex(new_arr);
        if (p_width < 0)
        {
            offset_simplex = offset_simplex.remove_redundant_lines();
        }
        return offset_simplex;
    }
    
    /**
     * Returns this simplex enlarged by p_offset.
     * The result simplex is intersected with the
     * by p_offset enlarged bounding octagon of this simplex
     */
    public Simplex enlarge(double p_offset)
    {
        if (p_offset == 0)
        {
            return this;
        }
        Simplex offset_simplex = offset(p_offset);
        IntOctagon bounding_oct = this.bounding_octagon();
        if (bounding_oct == null)
        {
            return Simplex.EMPTY;
        }
        IntOctagon offset_oct = bounding_oct.offset(p_offset);
        return offset_simplex.intersection(offset_oct.to_Simplex());
    }
    
    
    
    /**
     * Returns the number of the rightmost corner seen from p_from_point
     * No other point of this simplex may be to the right
     * of the line from p_from_point to the result corner.
     */
    public int index_of_right_most_corner( Point p_from_point)
    {
        Point pole = p_from_point;
        Point right_most_corner = corner(0);
        int result = 0;
        for (int i = 1; i < arr.length; ++i)
        {
            Point curr_corner = corner(i);
            if (curr_corner.side_of(pole, right_most_corner) == Side.ON_THE_RIGHT)
            {
                right_most_corner = curr_corner;
                result = i;
            }
        }
        return result;
    }
    
    /**
     * Returns the intersection of p_box with this simplex
     */
    public Simplex intersection(IntBox p_box)
    {
        return intersection(p_box.to_Simplex());
    }
    
    /**
     * Returns the intersection of this simplex and p_other
     */
    public Simplex intersection(Simplex p_other)
    {
        if (this.is_empty() || p_other.is_empty())
        {
            return EMPTY;
        }
        Line[] new_arr = new Line[arr.length + p_other.arr.length];
        System.arraycopy(arr, 0, new_arr, 0, arr.length);
        System.arraycopy(p_other.arr, 0, new_arr, arr.length, p_other.arr.length);
        java.util.Arrays.sort(new_arr);
        Simplex result = new Simplex( new_arr);
        return result.remove_redundant_lines();
    }
    
    /**
     * Returns the intersection of this simplex and the shape p_other
     */
    public TileShape intersection(TileShape p_other)
    {
        TileShape result = p_other.intersection(this);
        return result;
    }

    
    public boolean intersects(Shape p_other)
    {
        return p_other.intersects(this);
    }
    
    public boolean intersects(Simplex p_other)
    {
        ConvexShape is = intersection(p_other);
        return !is.is_empty();
    }
    
    /**
     * if p_line is a borderline of this simplex the number of that
     * edge is returned, otherwise -1
     */
    public int border_line_index(Line p_line)
    {
        for (int i = 0; i < arr.length; ++i)
        {
            if (p_line.equals(arr[i]))
            {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * Enlarges the simplex by removing the edge line with index p_no.
     * The result simplex may get unbounded.
     */
    public Simplex remove_border_line( int p_no)
    {
        if (p_no < 0 || p_no >= arr.length)
        {
            return this;
        }
        Line [] new_arr = new Line [this.arr.length - 1];
        System.arraycopy(this.arr, 0, new_arr, 0, p_no);
        System.arraycopy(this.arr, p_no + 1, new_arr, p_no, new_arr.length - p_no);
        return new Simplex(new_arr);
    }
    
    /**
     * Constructs a Simplex from the directed lines in p_line_arr.
     * The simplex will not be normalized.
     * To get a normalised simplex use TileShape.get_instance
     */
    public Simplex(Line[] p_line_arr)
    {
        arr = p_line_arr;
    }
    
    public Simplex to_Simplex()
    {
        return this;
    }
    
    
    Simplex intersection(IntOctagon p_other)
    {
        return intersection(p_other.to_Simplex());
    }
    
    public TileShape[] cutout(TileShape p_shape)
    {
        return p_shape.cutout_from(this);
    }
    
    /**
     * cuts this simplex out of p_outer_simplex.
     * Divides the resulting shape into simplices along the minimal
     * distance lines from the vertices of the inner simplex to the outer
     * simplex; Returns the convex pieces constructed by this division.
     */
    public Simplex[] cutout_from(Simplex p_outer_simplex)
    {
        if(this.dimension() < 2)
        {
            System.out.println("Simplex.cutout_from only implemented for 2-dim simplex");
            return null;
        }
        Simplex inner_simplex = this.intersection(p_outer_simplex);
        if (inner_simplex.dimension() < 2)
        {
            // nothing to cutout from p_outer_simplex
            Simplex[] result = new Simplex[1];
            result[0] = p_outer_simplex;
            return result;
        }
        int inner_corner_count = inner_simplex.arr.length;
        Line [][] division_line_arr = new Line[inner_corner_count][];
        for (int inner_corner_no = 0; inner_corner_no < inner_corner_count; ++inner_corner_no)
        {
            division_line_arr[inner_corner_no] =
                    inner_simplex.calc_division_lines(inner_corner_no, p_outer_simplex);
            if (division_line_arr[inner_corner_no] == null)
            {
                System.out.println("Simplex.cutout_from: division line is null");
                Simplex[] result = new Simplex[1];
                result[0] = p_outer_simplex;
                return result;
            }
        }
        boolean check_cross_first_line = false;
        Line prev_division_line = null;
        Line first_division_line = division_line_arr[0][0];
        IntDirection first_direction = (IntDirection)first_division_line.direction();
        Collection<Simplex> result_list = new LinkedList<Simplex>();
        
        for (int inner_corner_no = 0; inner_corner_no < inner_corner_count; ++inner_corner_no)
        {
            Line next_division_line;
            if (inner_corner_no == inner_simplex.arr.length - 1)
                next_division_line = division_line_arr[0][0];
            else
                next_division_line = division_line_arr[inner_corner_no + 1][0];
            Line[] curr_division_lines = division_line_arr[inner_corner_no];
            if (curr_division_lines.length == 2)
            {
                // 2 division lines are nessesary (sharp corner).
                // Construct an unbounded simplex from
                // curr_division_lines[1] and curr_division_lines[0]
                // and intersect it with the outer simplex
                IntDirection curr_dir = (IntDirection)curr_division_lines[0].direction();
                boolean merge_prev_division_line = false;
                boolean merge_first_division_line = false;
                if (prev_division_line != null)
                {
                    IntDirection prev_dir = (IntDirection)prev_division_line.direction();
                    if (curr_dir.determinant(prev_dir) > 0)
                        
                    {
                        // the previous division line may intersect
                        //  curr_division_lines[0] inside p_divide_simplex
                        merge_prev_division_line = true;
                    }
                }
                if (!check_cross_first_line)
                {
                    check_cross_first_line = (inner_corner_no > 0 &&
                            curr_dir.determinant(first_direction) > 0);
                }
                if (check_cross_first_line)
                {
                    IntDirection curr_dir2 = (IntDirection)curr_division_lines[1].direction();
                    if (curr_dir2.determinant(first_direction) < 0)
                    {
                        // The current piece has an intersection area with the first
                        // piece.
                        // Add a line to tmp_polyline to prevent this
                        merge_first_division_line = true;
                    }
                }
                int piece_line_count = 2;
                if (merge_prev_division_line)
                    ++piece_line_count;
                if (merge_first_division_line)
                    ++piece_line_count;
                Line[] piece_lines = new Line[piece_line_count];
                piece_lines[0] = new Line(curr_division_lines[1].b, curr_division_lines[1].a);
                piece_lines[1] =  curr_division_lines[0];
                int curr_line_no = 1;
                if (merge_prev_division_line)
                {
                    ++curr_line_no;
                    piece_lines[curr_line_no] = prev_division_line;
                }
                if (merge_first_division_line)
                {
                    ++curr_line_no;
                    piece_lines[curr_line_no] =
                            new Line(first_division_line.b, first_division_line.a);
                }
                Simplex curr_piece = new Simplex(piece_lines);
                result_list.add(curr_piece.intersection(p_outer_simplex));
            }
            // construct an unbounded simplex from next_division_line,
            // inner_simplex.line [inner_corner_no] and the last current division line
            // and intersect it with the outer simplex
            boolean merge_next_division_line = !next_division_line.b.equals(next_division_line.a);
            Line last_curr_division_line = curr_division_lines[curr_division_lines.length - 1];
            IntDirection last_curr_dir = (IntDirection)last_curr_division_line.direction();
            boolean merge_last_curr_division_line =
                    !last_curr_division_line.b.equals(last_curr_division_line.a);
            boolean merge_prev_division_line = false;
            boolean merge_first_division_line = false;
            if (prev_division_line != null)
            {
                IntDirection prev_dir = (IntDirection)prev_division_line.direction();
                if (last_curr_dir.determinant(prev_dir) > 0)
                    
                {
                    // the previous division line may intersect
                    //  the last current division line inside p_divide_simplex
                    merge_prev_division_line = true;
                }
            }
            if (!check_cross_first_line)
            {
                check_cross_first_line = inner_corner_no > 0 &&
                        last_curr_dir.determinant(first_direction) > 0 &&
                        last_curr_dir.get_vector().scalar_product(first_direction.get_vector()) < 0;
                // scalar_product checked to ignore backcrossing at
                // small inner_corner_no
            }
            if (check_cross_first_line)
            {
                IntDirection next_dir = (IntDirection)next_division_line.direction();
                if(next_dir.determinant(first_direction) < 0)
                {
                    // The current piece has an intersection area with the first piece.
                    // Add a line to tmp_polyline to prevent this
                    merge_first_division_line = true;
                }
            }
            int piece_line_count = 1;
            if (merge_next_division_line)
                ++piece_line_count;
            if (merge_last_curr_division_line)
                ++piece_line_count;
            if (merge_prev_division_line)
                ++piece_line_count;
            if (merge_first_division_line)
                ++piece_line_count;
            Line[] piece_lines = new Line[piece_line_count];
            Line curr_line = inner_simplex.arr[inner_corner_no];
            piece_lines[0] = new Line(curr_line.b, curr_line.a);
            int curr_line_no = 0;
            if (merge_next_division_line)
            {
                ++curr_line_no;
                piece_lines[curr_line_no] = new Line(next_division_line.b, next_division_line.a);
            }
            if (merge_last_curr_division_line)
            {
                ++curr_line_no;
                piece_lines[curr_line_no] = last_curr_division_line;
            }
            if (merge_prev_division_line)
            {
                ++curr_line_no;
                piece_lines[curr_line_no] = prev_division_line;
            }
            if (merge_first_division_line)
            {
                ++curr_line_no;
                piece_lines[curr_line_no] =
                        new Line(first_division_line.b, first_division_line.a);
            }
            Simplex curr_piece = new Simplex(piece_lines);
            result_list.add(curr_piece.intersection(p_outer_simplex));
            next_division_line = prev_division_line;
        }
        Simplex[] result = new Simplex[result_list.size()];
        Iterator<Simplex> it = result_list.iterator();
        for (int i = 0; i < result.length; ++i)
        {
            result[i] = it.next();
        }
        return result;
    }
    
    Simplex[] cutout_from(IntOctagon p_oct)
    {
        return cutout_from(p_oct.to_Simplex());
    }
    
    Simplex[] cutout_from(IntBox p_box)
    {
        return cutout_from(p_box.to_Simplex());
    }
    
    /**
     * Removes lines, which are redundant in the definition of the
     * shape of this simplex.
     * Assumes that the lines of this simplex are sorted.
     */
    Simplex remove_redundant_lines()
    {
        Line [] line_arr = new Line [arr.length];
        // copy the sorted lines of arr into line_arr while skipping
        // multiple lines
        int new_length = 1;
        line_arr[0] = arr[0];
        Line prev = line_arr[0];
        for (int i = 1; i < arr.length; ++i)
        {
            if (!arr[i].fast_equals(prev))
            {
                line_arr[new_length] = arr[i];
                prev = line_arr[new_length];
                ++new_length;
            }
        }
        
        Side [] intersection_sides = new Side [new_length];
        // precalculated array , on which side of this line the previous and the
        // next line do intersect
        
        boolean try_again = new_length > 2;
        int index_of_last_removed_line = new_length;
        while(try_again)
        {
            try_again = false;
            int prev_ind = new_length - 1;
            int next_ind;
            Line prev_line = line_arr[prev_ind];
            Line curr_line = line_arr[0];
            Line next_line;
            for (int ind = 0; ind < new_length; ++ind)
            {
                if (ind == new_length - 1)
                {
                    next_ind = 0;
                }
                else
                {
                    next_ind = ind + 1;
                }
                next_line = line_arr[next_ind];
                
                boolean remove_line = false;
                IntDirection prev_dir = (IntDirection) prev_line.direction();
                IntDirection next_dir = (IntDirection) next_line.direction();
                double det = prev_dir.determinant(next_dir);
                if (det != 0) // prev_line and next_line are not parallel
                {
                    if (intersection_sides [ind] == null)
                    {
                        // intersection_sides [ind] not precalculated
                        intersection_sides [ind] = curr_line.side_of_intersection(prev_line, next_line);
                    }
                    if(det > 0 )
                        // direction of next_line is bigger than direction of prev_line
                    {
                        // if the intersection of prev_line and next_line
                        // is on the left of curr_line, curr_line does not
                        // contribute to the shape of the simplex
                        remove_line = (intersection_sides[ind] != Side.ON_THE_LEFT);
                    }
                    else
                        // direction of next_line is smaller than direction of prev_line
                    {
                        
                        if (intersection_sides[ind] == Side.ON_THE_LEFT)
                        {
                            IntDirection curr_dir = (IntDirection) curr_line.direction();
                            if (prev_dir.determinant(curr_dir) > 0)
                                // direction of curr_line is bigger than direction of prev_line
                            {
                                // the halfplane defined by curr_line does not intersect
                                // with the simplex defined by prev_line and nex_line,
                                // hence this simplex must be empty
                                new_length = 0;
                                try_again = false;
                                break;
                            }
                        }
                    }
                }
                else // prev_line and next_line are parallel
                {
                    if (prev_line.side_of(next_line.a) == Side.ON_THE_LEFT)
                        // prev_line is to the left of next_line,
                        // the halfplanes defined by prev_line and next_line
                        // do not intersect
                    {
                        new_length = 0;
                        try_again = false;
                        break;
                    }
                }
                if (remove_line)
                {
                    try_again = true;
                    --new_length;
                    for (int i = ind; i < new_length; ++i)
                    {
                        line_arr [i] = line_arr [i + 1];
                        intersection_sides[i] = intersection_sides [i + 1];
                    }
                    
                    if (new_length < 3)
                    {
                        try_again = false;
                        break;
                    }
                    // reset 3 precalculated intersection_sides
                    if (ind == 0)
                    {
                        prev_ind = new_length - 1;
                    }
                    intersection_sides [prev_ind] = null;
                    if (ind >= new_length)
                    {
                        next_ind = 0;
                    }
                    else
                    {
                        next_ind = ind;
                    }
                    intersection_sides [next_ind] = null;
                    --ind;
                    index_of_last_removed_line = ind;
                }
                else
                {
                    prev_line = curr_line;
                    prev_ind = ind;
                }
                curr_line = next_line;
                if( !try_again && ind >= index_of_last_removed_line)
                    // tried all lines without removing one
                {
                    break;
                }
            }
        }
        
        if (new_length == 2)
        {
            if (line_arr[0].is_parallel(line_arr[1]))
            {
                if(line_arr[0].direction().equals(line_arr[1].direction()))
                    // one of the two remaining lines is redundant
                {
                    if (line_arr[1].side_of(line_arr[0].a) == Side.ON_THE_LEFT)
                    {
                        line_arr[0] = line_arr[1];
                    }
                    --new_length;
                }
                else
                    // the two remaining lines have opposite direction
                    // the simplex may be empty
                {
                    if (line_arr[1].side_of(line_arr[0].a) == Side.ON_THE_LEFT)
                    {
                        new_length = 0;
                    }
                }
            }
        }
        if (new_length == arr.length)
        {
            return this; // nothing removed
        }
        if (new_length == 0)
        {
            return Simplex.EMPTY;
        }
        Line [] result = new Line [new_length];
        System.arraycopy(line_arr, 0, result, 0, new_length);
        return new Simplex(result);
    }
    
    public boolean intersects(IntBox p_box)
    {
        return intersects(p_box.to_Simplex());
    }
    
    public boolean intersects(IntOctagon p_octagon)
    {
        return intersects(p_octagon.to_Simplex());
    }
    
    public boolean intersects(Circle p_circle)
    {
        return p_circle.intersects(this);
    }
    
    /**
     * For each corner of this inner simplex 1 or 2 perpendicular
     * projections onto lines of the outer simplex are constructed,
     * so that the resulting pieces after cutting out the inner simplex
     * are convex. 2 projections may be nessesary at sharp angle corners.
     * Used in in the method cutout_from with parametertype Simplex.
     */
    private Line[] calc_division_lines(int p_inner_corner_no, Simplex p_outer_simplex)
    {
        Line curr_inner_line = this.arr[p_inner_corner_no];
        Line prev_inner_line;
        if (p_inner_corner_no != 0)
            prev_inner_line = this.arr[p_inner_corner_no - 1];
        else
            prev_inner_line = this.arr[arr.length - 1];
        FloatPoint intersection = curr_inner_line.intersection_approx(prev_inner_line);
        if (intersection.x >= Integer.MAX_VALUE)
        {
            System.out.println("Simplex.calc_division_lines: intersection expexted");
            return null;
        }
        IntPoint inner_corner = intersection.round();
        double c_tolerance = 0.0001;
        boolean   is_exact =
                Math.abs(inner_corner.x - intersection.x) < c_tolerance
                && Math.abs(inner_corner.y - intersection.y) < c_tolerance;
        
        if (!is_exact)
        {
            // it is assumed, that the corners of the original inner simplex are
            // exact and the not exact corners come from the intersection of
            // the inner simplex with the outer simplex.
            // Because these corners lie on the border of the outer simplex,
            // no division is nessesary
            Line [] result = new Line[1];
            result[0] = prev_inner_line;
            return result;
        }
        IntDirection first_projection_dir = Direction.NULL;
        IntDirection second_projection_dir = Direction.NULL;
        IntDirection prev_inner_dir = (IntDirection) prev_inner_line.direction().opposite();
        IntDirection next_inner_dir = (IntDirection) curr_inner_line.direction();
        int outer_line_no = 0;
        
        
        // search the first outer line, so that
        // the perpendicular projection of the inner corner onto this
        // line is visible from inner_corner to the left of prev_inner_line.
        
        double min_distance = Integer.MAX_VALUE;
        
        for (int ind = 0; ind < p_outer_simplex.arr.length; ++ind)
        {
            Line outer_line = p_outer_simplex.arr[outer_line_no];
            IntDirection curr_projection_dir =
                    (IntDirection)inner_corner.perpendicular_direction(outer_line);
            if (curr_projection_dir == Direction.NULL)
            {
                Line [] result = new Line[1];
                result[0] = new Line(inner_corner, inner_corner);
                return result;
            }
            boolean projection_visible = prev_inner_dir.determinant(curr_projection_dir) >= 0;
            if (projection_visible)
            {
                double curr_distance = Math.abs(outer_line.signed_distance(inner_corner.to_float()));
                boolean second_division_necessary =
                        curr_projection_dir.determinant(next_inner_dir) < 0;
                // may occor at a sharp angle
                IntDirection curr_second_projection_dir = curr_projection_dir;
                
                if (second_division_necessary)
                {
                    // search the first projection_dir between curr_projection_dir
                    // and next_inner_dir, that is visible from next_inner_line
                    boolean second_projection_visible = false;
                    int tmp_outer_line_no = outer_line_no;
                    while (!second_projection_visible)
                    {
                        if (tmp_outer_line_no == p_outer_simplex.arr.length - 1)
                        {
                            tmp_outer_line_no = 0;
                        }
                        else
                        {
                            ++tmp_outer_line_no;
                        }
                        curr_second_projection_dir =
                                (IntDirection)inner_corner.perpendicular_direction(
                                p_outer_simplex.arr[tmp_outer_line_no]);
                        
                        if (curr_second_projection_dir == Direction.NULL)
                            // inner corner is on outer_line
                        {
                            Line [] result = new Line[1];
                            result[0] = new Line(inner_corner, inner_corner);
                            return result;
                        }
                        if (curr_projection_dir.determinant(curr_second_projection_dir) < 0)
                        {
                            // curr_second_projection_dir not found;
                            // the angle between curr_projection_dir and
                            // curr_second_projection_dir would be already bigger
                            // than 180 degree
                            curr_distance = Integer.MAX_VALUE;
                            break;
                        }
                        
                        second_projection_visible =
                                curr_second_projection_dir.determinant(next_inner_dir) >= 0;
                    }
                    curr_distance +=
                            Math.abs(p_outer_simplex.arr[tmp_outer_line_no].signed_distance(inner_corner.to_float()));
                }
                if (curr_distance < min_distance)
                {
                    min_distance = curr_distance;
                    first_projection_dir = curr_projection_dir;
                    second_projection_dir = curr_second_projection_dir;
                }
            }
            if (outer_line_no == p_outer_simplex.arr.length - 1)
            {
                outer_line_no = 0;
            }
            else
            {
                ++outer_line_no;
                
            }
        }
        if (min_distance == Integer.MAX_VALUE)
        {
            System.out.println("Simplex.calc_division_lines: division not found");
            return null;
        }
        Line[] result;
        if (first_projection_dir.equals(second_projection_dir))
        {
            result = new Line[1];
            result[0] = new Line(inner_corner, first_projection_dir);
        }
        else
        {
            result = new Line[2];
            result[0] = new Line(inner_corner, first_projection_dir);
            result[1] = new Line(inner_corner, second_projection_dir);
        }
        return result;
    }
    
    private final Line[] arr;
    
    /**
     * the following fields are for storing precalculated data
     */
    transient private Point[] precalculated_corners = null;
    transient private FloatPoint[] precalculated_float_corners = null;
    transient private IntBox precalculated_bounding_box = null;
    transient private IntOctagon precalculated_bounding_octagon = null;
    
}
