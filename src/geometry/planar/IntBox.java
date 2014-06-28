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
 * IntBox.java
 *
 * Created on 2. Februar 2003, 14:09
 */

package geometry.planar;

/**
 *
 * Implements functionality of orthogonal rectangles in the plane
 * with integer coordinates.
 *
 *
 * @author Alfons Wirtz
 */

public class IntBox extends RegularTileShape implements java.io.Serializable
{
    /**
     * Standard implementataion of an empty box.
     */
    public static final IntBox EMPTY = new IntBox(Limits.CRIT_INT, Limits.CRIT_INT,
            -Limits.CRIT_INT, -Limits.CRIT_INT);
    
    /**
     * Creates an IntBox from its lower left and upper right corners.
     */
    public IntBox(IntPoint p_ll, IntPoint p_ur)
    {
        ll = p_ll;
        ur = p_ur;
    }
    
    /**
     * creates an IntBox from the coordinates of its lower left and
     * upper right corners.
     */
    public IntBox(int p_ll_x, int p_ll_y, int p_ur_x, int p_ur_y)
    {
        ll = new IntPoint(p_ll_x, p_ll_y);
        ur = new IntPoint(p_ur_x, p_ur_y);
    }
    
    public boolean is_IntOctagon()
    {
        return true;
    }
    
    
    /**
     * Returns true, if the box is empty
     */
    public boolean is_empty()
    {
        return (ll.x > ur.x || ll.y > ur.y);
    }
    
    public int border_line_count()
    {
        return 4;
    }
    
    /**
     * returns the horizontal extension of the box.
     */
    public int width()
    {
        return (ur.x - ll.x);
    }
    
    /**
     * Returns the vertical extension of the box.
     */
    public int height()
    {
        return (ur.y - ll.y) ;
    }
    
    public double max_width()
    {
        return Math.max(ur.x - ll.x, ur.y - ll.y);
    }
    
    public double min_width()
    {
        return Math.min(ur.x - ll.x, ur.y - ll.y);
    }
    
    public double area()
    {
        return ((double)(ur.x - ll.x))* ((double) (ur.y - ll.y));
    }
    
    public double circumference()
    {
        return 2 * ((ur.x - ll.x) + (ur.y - ll.y));
    }
    
    public IntPoint corner(int p_no)
    {
        if (p_no == 0)
        {
            return ll;
        }
        if (p_no == 1)
        {
            return new IntPoint(ur.x, ll.y);
        }
        if (p_no == 2)
        {
            return ur;
        }
        if (p_no == 3)
        {
            return new IntPoint(ll.x, ur.y);
        }
        throw new IllegalArgumentException("IntBox.corner: p_no out of range");
    }
    
    public int dimension()
    {
        if (is_empty())
        {
            return -1;
        }
        if (ll.equals(ur))
        {
            return 0;
        }
        if (ur.x == ll.x || ll.y == ur.y)
        {
            return 1;
        }
        return 2;
    }
    
    /**
     * Chechs, if p_point is located in the interiour of this box.
     */
    public boolean contains_inside(IntPoint p_point)
    {
        return p_point.x > this.ll.x &&  p_point.x < this.ur.x
                && p_point.y > this.ll.y &&  p_point.y < this.ur.y;
    }
    
    public boolean is_IntBox()
    {
        return true;
    }
    
    public TileShape simplify()
    {
        return this;
    }
    
    /**
     * Calculates the nearest point of this box to p_from_point.
     */
    public FloatPoint nearest_point(FloatPoint p_from_point)
    {
        double x;
        if (p_from_point.x <= ll.x)
            x = ll.x;
        else if (p_from_point.x >= ur.x)
            x = ur.x;
        else
            x = p_from_point.x;
        
        double y;
        if (p_from_point.y <= ll.y)
            y = ll.y;
        else if (p_from_point.y >= ur.y)
            y = ur.y;
        else
            y = p_from_point.y;
        
        return new FloatPoint(x,y);
    }
    
    /**
     * Calculates the sorted p_max_result_points nearest points on the  border of this box.
     * p_point is assumed to be located in the interiour of this nox.
     * The funtion is only  imoplemented for p_max_result_points <= 2;
     */
    public IntPoint[] nearest_border_projections(IntPoint p_point, int p_max_result_points)
    {
        if (p_max_result_points <= 0)
        {
            return new IntPoint[0];
        }
        p_max_result_points = Math.min(p_max_result_points, 2);
        IntPoint [] result = new IntPoint[p_max_result_points];
        
        int       lower_x_diff = p_point.x - ll.x;
        int       upper_x_diff = ur.x - p_point.x;
        int       lower_y_diff = p_point.y - ll.y;
        int       upper_y_diff = ur.y - p_point.y;
        
        int       min_diff;
        int       second_min_diff;
        
        int nearest_projection_x = p_point.x;
        int nearest_projection_y = p_point.y;
        int second_nearest_projection_x = p_point.x;
        int second_nearest_projection_y = p_point.y;
        if (lower_x_diff <= upper_x_diff)
        {
            min_diff = lower_x_diff;
            second_min_diff = upper_x_diff;
            nearest_projection_x = ll.x;
            second_nearest_projection_x = ur.x;
        }
        else
        {
            min_diff = upper_x_diff;
            second_min_diff = lower_x_diff;
            nearest_projection_x = ur.x;
            second_nearest_projection_x = ll.x;
        }
        if (lower_y_diff < min_diff)
        {
            second_min_diff = min_diff;
            min_diff = lower_y_diff;
            second_nearest_projection_x = nearest_projection_x;
            second_nearest_projection_y = nearest_projection_y;
            nearest_projection_x = p_point.x;
            nearest_projection_y = ll.y;
        }
        else if (lower_y_diff < second_min_diff)
        {
            second_min_diff = lower_y_diff;
            second_nearest_projection_x = p_point.x;
            second_nearest_projection_y = ll.y;
        }
        if (upper_y_diff < min_diff)
        {
            second_min_diff = min_diff;
            min_diff = upper_y_diff;
            second_nearest_projection_x = nearest_projection_x;
            second_nearest_projection_y = nearest_projection_y;
            nearest_projection_x = p_point.x;
            nearest_projection_y = ur.y;
        }
        else if (upper_y_diff < second_min_diff)
        {
            second_min_diff = upper_y_diff;
            second_nearest_projection_x = p_point.x;
            second_nearest_projection_y = ur.y;
        }
        result[0] = new IntPoint(nearest_projection_x, nearest_projection_y);
        if (result.length > 1)
        {
            result[1] = new IntPoint(second_nearest_projection_x, second_nearest_projection_y);
        }
        
        return result;
    }
    
    /**
     * Calculates distance of this box to p_from_point.
     */
    public double distance(FloatPoint p_from_point)
    {
        return p_from_point.distance(nearest_point(p_from_point));
    }
    
    /**
     * Computes the weighted distance to the box p_other.
     */
    public double weighted_distance(IntBox p_other, double p_horizontal_weight, double p_vertical_weight)
    {
        double result;
        
        double max_ll_x = Math.max(this.ll.x, p_other.ll.x);
        double max_ll_y = Math.max(this.ll.y, p_other.ll.y);
        double min_ur_x = Math.min(this.ur.x, p_other.ur.x);
        double min_ur_y = Math.min(this.ur.y, p_other.ur.y);
        
        if (min_ur_x >= max_ll_x)
        {
            result = Math.max(p_vertical_weight * (max_ll_y - min_ur_y), 0);
        }
        else if (min_ur_y >= max_ll_y)
        {
            result = Math.max(p_horizontal_weight * (max_ll_x - min_ur_x), 0);
        }
        else
        {
            double delta_x = max_ll_x - min_ur_x;
            double delta_y = max_ll_y - min_ur_y;
            delta_x *= p_horizontal_weight;
            delta_y *= p_vertical_weight;
            result = Math.sqrt(delta_x * delta_x + delta_y * delta_y);
        }
        return result;
    }
    
    public IntBox bounding_box()
    {
        return this;
    }
    
    public IntOctagon bounding_octagon()
    {
        return to_IntOctagon();
    }
    
    public boolean is_bounded()
    {
        return true;
    }
    
    public IntBox bounding_tile()
    {
        return this;
    }
    
    public boolean corner_is_bounded(int p_no)
    {
        return true;
    }
    
    public RegularTileShape union(RegularTileShape p_other)
    {
        return p_other.union(this);
    }
    
    public IntBox union(IntBox p_other)
    {
        int llx = Math.min(ll.x, p_other.ll.x);
        int lly = Math.min(ll.y, p_other.ll.y);
        int urx = Math.max(ur.x, p_other.ur.x);
        int ury = Math.max(ur.y, p_other.ur.y);
        return new IntBox(llx, lly, urx, ury);
    }
    
    /**
     * Returns the intersection of this box with an IntBox.
     */
    public IntBox intersection(IntBox p_other)
    {
        if (p_other.ll.x > ur.x)
        {
            return EMPTY;
        }
        if (p_other.ll.y > ur.y)
        {
            return EMPTY;
        }
        if (ll.x > p_other.ur.x)
        {
            return EMPTY;
        }
        if (ll.y > p_other.ur.y)
        {
            return EMPTY;
        }
        int llx = Math.max(ll.x, p_other.ll.x);
        int urx = Math.min(ur.x, p_other.ur.x);
        int lly = Math.max(ll.y, p_other.ll.y);
        int ury = Math.min(ur.y, p_other.ur.y);
        return new IntBox(llx, lly, urx, ury);
    }
    
    /**
     * returns the intersection of this box with a ConvexShape
     */
    public TileShape intersection(TileShape p_other)
    {
        return p_other.intersection(this);
    }
    
    
    IntOctagon intersection(IntOctagon p_other)
    {
        return p_other.intersection(this.to_IntOctagon());
    }
    
    Simplex intersection(Simplex p_other)
    {
        return p_other.intersection(this.to_Simplex());
    }
    
    public boolean intersects(Shape p_other)
    {
        return p_other.intersects(this);
    }
    
    public boolean intersects(IntBox p_other)
    {
        if (p_other.ll.x > this.ur.x)
            return false;
        if (p_other.ll.y > this.ur.y)
            return false;
        if (this.ll.x > p_other.ur.x)
            return false;
        if (this.ll.y > p_other.ur.y)
            return false;
        return true;
    }
    
    /**
     * Returns true, if this box intersects with p_other and the intersection is 2-dimensional.
     */
    public boolean overlaps(IntBox p_other)
    {
        if (p_other.ll.x >= this.ur.x)
            return false;
        if (p_other.ll.y >= this.ur.y)
            return false;
        if (this.ll.x >= p_other.ur.x)
            return false;
        if (this.ll.y >= p_other.ur.y)
            return false;
        return true;
    }
    
    public boolean contains(RegularTileShape p_other)
    {
        return p_other.is_contained_in(this);
    }
    
    public RegularTileShape bounding_shape(ShapeBoundingDirections p_dirs)
    {
        return p_dirs.bounds(this);
    }
    
    /**
     * Enlarges the box by p_offset.
     * Contrary to the offset() method the result is an IntOctagon, not an IntBox.
     */
    public IntOctagon enlarge(double p_offset)
    {
        return bounding_octagon().offset(p_offset);
    }
    
    public IntBox translate_by(Vector p_rel_coor)
    {
        // This function is at the moment only implemented for Vectors
        // with integer coordinates.
        // The general implementation is still missing.
        
        if (p_rel_coor.equals(Vector.ZERO))
        {
            return this;
        }
        IntPoint new_ll = (IntPoint)ll.translate_by(p_rel_coor);
        IntPoint new_ur = (IntPoint)ur.translate_by(p_rel_coor);
        return new IntBox(new_ll, new_ur);
    }
    
    public IntBox turn_90_degree(int p_factor, IntPoint p_pole)
    {
        IntPoint p1 = (IntPoint) ll.turn_90_degree(p_factor, p_pole);
        IntPoint p2 = (IntPoint) ur.turn_90_degree(p_factor, p_pole);
        
        int llx = Math.min(p1.x, p2.x);
        int lly = Math.min(p1.y, p2.y);
        int urx = Math.max(p1.x, p2.x);
        int ury = Math.max(p1.y, p2.y);
        return new IntBox(llx,lly,urx,ury);
    }
    
    public Line border_line(int p_no)
    {
        int a_x;
        int a_y;
        int b_x;
        int b_y;
        switch (p_no)
        {
            case 0:
                // lower boundary line
                a_x = 0;
                a_y = ll.y;
                b_x = 1;
                b_y = ll.y;
                break;
            case 1:
                // right boundary line
                a_x = ur.x;
                a_y = 0;
                b_x = ur.x;
                b_y = 1;
                break;
            case 2:
                // upper boundary line
                a_x = 0;
                a_y = ur.y;
                b_x = -1;
                b_y = ur.y;
                break;
            case 3:
                // left boundary line
                a_x = ll.x;
                a_y = 0;
                b_x = ll.x;
                b_y = -1;
                break;
            default:
                throw new IllegalArgumentException
                        ("IntBox.edge_line: p_no out of range");
        }
        return new Line(a_x, a_y, b_x, b_y);
    }
    
    public int border_line_index(Line p_line)
    {
        System.out.println("edge_index_of_line not yet implemented for IntBoxes");
        return -1;
    }
    
    /**
     * Returns the box offseted by p_dist.
     * If p_dist > 0, the offset is to the outside,
     * else to the inside.
     */
    public IntBox offset(double p_dist)
    {
        if (p_dist == 0 || is_empty())
        {
            return this;
        }
        int dist = (int)Math.round(p_dist);
        IntPoint lower_left = new IntPoint(ll.x - dist, ll.y - dist);
        IntPoint upper_right = new IntPoint(ur.x + dist, ur.y + dist);
        return new IntBox(lower_left, upper_right);
    }
    
    /**
     * Returns the box, where the horizontal boundary is offseted by p_dist.
     * If p_dist > 0, the offset is to the outside,
     * else to the inside.
     */
    public IntBox horizontal_offset(double p_dist)
    {
        if (p_dist == 0 || is_empty())
        {
            return this;
        }
        int dist = (int)Math.round(p_dist);
        IntPoint lower_left = new IntPoint(ll.x - dist, ll.y);
        IntPoint upper_right = new IntPoint(ur.x + dist, ur.y);
        return new IntBox(lower_left, upper_right);
    }
    
    /**
     * Returns the box, where the vertical boundary is offseted by p_dist.
     * If p_dist > 0, the offset is to the outside,
     * else to the inside.
     */
    public IntBox vertical_offset(double p_dist)
    {
        if (p_dist == 0 || is_empty())
        {
            return this;
        }
        int dist = (int)Math.round(p_dist);
        IntPoint lower_left = new IntPoint(ll.x, ll.y - dist);
        IntPoint upper_right = new IntPoint(ur.x, ur.y + dist);
        return new IntBox(lower_left, upper_right);
    }
    
    /**
     * Shrinks the width and height of the box by the input width.
     * The box will not vanish completely.
     */
    public IntBox shrink(int p_width)
    {
        int ll_x;
        int ur_x;
        if (2 * p_width <= this.ur.x - this.ll.x)
        {
            ll_x = this.ll.x + p_width;
            ur_x = this.ur.x - p_width;
        }
        else
        {
            ll_x = (this.ll.x + this.ur.x) / 2;
            ur_x = ll_x;
        }
        int ll_y;
        int ur_y;
        if (2 * p_width <= this.ur.y - this.ll.y)
        {
            ll_y = this.ll.y + p_width;
            ur_y = this.ur.y - p_width;
        }
        else
        {
            ll_y = (this.ll.y + this.ur.y) / 2;
            ur_y = ll_y;
        }
        return new IntBox(ll_x, ll_y, ur_x, ur_y);
    }
    
    public Side compare(RegularTileShape p_other, int p_edge_no)
    {
        Side result = p_other.compare(this, p_edge_no);
        return result.negate();
    }
    
    public Side compare(IntBox p_other, int p_edge_no)
    {
        Side result;
        switch (p_edge_no)
        {
            case 0:
                // compare the lower edge line
                if (ll.y > p_other.ll.y)
                {
                    result = Side.ON_THE_LEFT;
                }
                else if (ll.y < p_other.ll.y)
                {
                    result = Side.ON_THE_RIGHT;
                }
                else
                {
                    result = Side.COLLINEAR;
                }
                break;
                
            case 1:
                // compare the right edge line
                if (ur.x < p_other.ur.x)
                {
                    result = Side.ON_THE_LEFT;
                }
                else if (ur.x > p_other.ur.x)
                {
                    result = Side.ON_THE_RIGHT;
                }
                else
                {
                    result = Side.COLLINEAR;
                }
                break;
                
            case 2:
                // compare the upper edge line
                if (ur.y < p_other.ur.y)
                {
                    result = Side.ON_THE_LEFT;
                }
                else if (ur.y > p_other.ur.y)
                {
                    result = Side.ON_THE_RIGHT;
                }
                else
                {
                    result = Side.COLLINEAR;
                }
                break;
                
            case 3:
                // compare the left edge line
                if (ll.x > p_other.ll.x)
                {
                    result = Side.ON_THE_LEFT;
                }
                else if (ll.x < p_other.ll.x)
                {
                    result = Side.ON_THE_RIGHT;
                }
                else
                {
                    result = Side.COLLINEAR;
                }
                break;
            default:
                throw new IllegalArgumentException
                        ("IntBox.compare: p_edge_no out of range");
                
        }
        return result;
    }
    
    /**
     * Returns an object of class IntOctagon defining the same shape
     */
    public IntOctagon to_IntOctagon()
    {
        return new IntOctagon(ll.x, ll.y, ur.x, ur.y, ll.x - ur.y,
                ur.x - ll.y, ll.x + ll.y, ur.x + ur.y);
    }
    
    /**
     * Returns an object of class Simplex defining the same shape
     */
    public Simplex to_Simplex()
    {
        Line[] line_arr;
        if (is_empty())
        {
            line_arr = new Line[0];
        }
        else
        {
            line_arr = new Line[4];
            line_arr[0] = Line.get_instance(ll, IntDirection.RIGHT);
            line_arr[1] = Line.get_instance(ur, IntDirection.UP);
            line_arr[2] = Line.get_instance(ur, IntDirection.LEFT);
            line_arr[3] = Line.get_instance(ll, IntDirection.DOWN);
        }
        return new Simplex(line_arr);
    }
    
    public boolean is_contained_in( IntBox p_other)
    {
        if (is_empty() || this == p_other)
        {
            return true;
        }
        if (ll.x < p_other.ll.x || ll.y < p_other.ll.y
                || ur.x > p_other.ur.x || ur.y > p_other.ur.y)
        {
            return false;
        }
        return true;
    }
    
    /**
     * Return true, if p_other is contained in the interiour of this box.
     */
    public boolean contains_in_interiour(IntBox p_other)
    {
        if (p_other.is_empty())
        {
            return true;
        }
        if (p_other.ll.x <= ll.x || p_other.ll.y <= ll.y
                || p_other.ur.x >= ur.x || p_other.ur.y >= ur.y)
        {
            return false;
        }
        return true;
    }
    
    /**
     * Calculates the part of p_from_box, which has minimal distance
     * to this box.
     */
    public IntBox nearest_part(IntBox p_from_box)
    {
        int ll_x;
        
        if (p_from_box.ll.x >= this.ll.x)
        {
            ll_x = p_from_box.ll.x;
        }
        else if (p_from_box.ur.x >= this.ll.x)
        {
            ll_x = this.ll.x;
        }
        else
        {
            ll_x = p_from_box.ur.x;
        }
        
        int ur_x;
        
        if (p_from_box.ur.x <= this.ur.x)
        {
            ur_x = p_from_box.ur.x;
        }
        else if (p_from_box.ll.x <= this.ur.x)
        {
            ur_x = this.ur.x;
        }
        else
        {
            ur_x = p_from_box.ll.x;
        }
        
        int ll_y;
        
        if (p_from_box.ll.y >= this.ll.y)
        {
            ll_y = p_from_box.ll.y;
        }
        else if (p_from_box.ur.y >= this.ll.y)
        {
            ll_y = this.ll.y;
        }
        else
        {
            ll_y = p_from_box.ur.y;
        }
        
        int ur_y;
        
        if (p_from_box.ur.y <= this.ur.y)
        {
            ur_y = p_from_box.ur.y;
        }
        else if (p_from_box.ll.y <= this.ur.y)
        {
            ur_y = this.ur.y;
        }
        else
        {
            ur_y = p_from_box.ll.y;
        }
        return new IntBox(ll_x, ll_y, ur_x, ur_y);
    }
    
    public boolean is_contained_in( IntOctagon p_other)
    {
        return p_other.contains(to_IntOctagon());
    }
    
    public boolean intersects( IntOctagon p_other)
    {
        return p_other.intersects(to_IntOctagon());
    }
    
    public boolean intersects( Simplex p_other)
    {
        return p_other.intersects(to_Simplex());
    }
    
    public boolean intersects( Circle p_other)
    {
        return p_other.intersects(this);
    }
    
    public IntOctagon union( IntOctagon p_other)
    {
        return p_other.union(to_IntOctagon());
    }
    
    public Side compare(IntOctagon p_other, int p_edge_no)
    {
        return to_IntOctagon().compare(p_other, p_edge_no);
    }
    
    /**
     * Divides this box into sections with width and height at most p_max_section_width
     * of about equal size.
     */
    public IntBox[] divide_into_sections(double p_max_section_width)
    {
        if (p_max_section_width <= 0)
        {
            return new IntBox[0];
        }
        double length = this.ur.x - this.ll.x;
        double height = this.ur.y - this.ll.y;
        int x_count =  (int) Math.ceil(length / p_max_section_width);
        int y_count = (int) Math.ceil(height / p_max_section_width);
        int section_length_x = (int) Math.ceil(length / x_count);
        int section_length_y = (int) Math.ceil(height / y_count);
        IntBox [] result = new IntBox[x_count * y_count];
        int curr_index = 0;
        for (int j = 0; j < y_count; ++j)
        {
            int curr_lly = this.ll.y + j * section_length_y;
            int curr_ury;
            if (j == (y_count - 1))
            {
                curr_ury = this.ur.y;
            }
            else
            {
                curr_ury = curr_lly + section_length_y;
            }
            for (int i = 0; i < x_count; ++i)
            {
                int curr_llx = this.ll.x + i * section_length_x;
                int curr_urx;
                if (i == (x_count - 1))
                {
                    curr_urx = this.ur.x;
                }
                else
                {
                    curr_urx = curr_llx + section_length_x;
                }
                result[curr_index] = new IntBox(curr_llx, curr_lly, curr_urx, curr_ury);
                ++curr_index;
            }
        }
        return result;
    }
    
    public TileShape[] cutout(TileShape p_shape)
    {
        TileShape[] tmp_result = p_shape.cutout_from(this);
        TileShape[] result = new TileShape[tmp_result.length];
        for (int i = 0; i < result.length; ++i)
        {
            result[i] = tmp_result[i].simplify();
        }
        return result;
    }
    
    IntBox[] cutout_from(IntBox p_d)
    {
        IntBox c = this.intersection(p_d);
        if (this.is_empty() || c.dimension() < this.dimension())
        {
            // there is only an overlap at the border
            IntBox[] result = new IntBox[1];
            result[0] = p_d;
            return result;
        }
        
        IntBox[] result = new IntBox[4];
        
        result[0] = new IntBox(p_d.ll.x, p_d.ll.y, c.ur.x, c.ll.y);
        
        result[1] = new IntBox(p_d.ll.x, c.ll.y, c.ll.x, p_d.ur.y);
        
        result[2] = new IntBox(c.ur.x, p_d.ll.y, p_d.ur.x, c.ur.y);
        
        result[3] = new IntBox(c.ll.x, c.ur.y, p_d.ur.x, p_d.ur.y);
        
        // now the division will be optimised, so that the cumulative
        // circumference will be minimal.
        
        IntBox b = null;
        
        if (c.ll.x - p_d.ll.x > c.ll.y - p_d.ll.y)
        {
            // switch left dividing line to lower
            b = result[0];
            result[0] = new IntBox(c.ll.x, b.ll.y, b.ur.x, b.ur.y);
            b = result[1];
            result[1] = new IntBox(b.ll.x, p_d.ll.y, b.ur.x, b.ur.y);
        }
        if (p_d.ur.y - c.ur.y > c.ll.x - p_d.ll.x)
        {
            // switch upper dividing line to the left
            b = result[1];
            result[1]= new IntBox(b.ll.x, b.ll.y, b.ur.x, c.ur.y);
            b = result[3];
            result[3] = new IntBox(p_d.ll.x, b.ll.y, b.ur.x, b.ur.y);
        }
        if (p_d.ur.x - c.ur.x > p_d.ur.y - c.ur.y)
        {
            // switch right dividing line to upper
            b = result[2];
            result[2] = new IntBox(b.ll.x, b.ll.y, b.ur.x, p_d.ur.y);
            b = result[3];
            result[3] = new IntBox(b.ll.x, b.ll.y, c.ur.x, b.ur.y);
        }
        if (c.ll.y - p_d.ll.y > p_d.ur.x - c.ur.x)
        {
            // switch lower dividing line to the left
            b = result[0];
            result[0] = new IntBox(b.ll.x, b.ll.y, p_d.ur.x, b.ur.y);
            b = result[2];
            result[2] = new IntBox(b.ll.x, c.ll.y, b.ur.x, b.ur.y);
        }
        return result;
    }
    
    Simplex[] cutout_from(Simplex p_simplex)
    {
        return this.to_Simplex().cutout_from(p_simplex);
    }
    
    IntOctagon[] cutout_from(IntOctagon p_oct)
    {
        return this.to_IntOctagon().cutout_from(p_oct);
    }
    
    /**
     * coordinates of the lower left corner
     */
    public final IntPoint ll;
    
    /**
     * coordinates of the upper right corner
     */
    public final IntPoint ur;
}