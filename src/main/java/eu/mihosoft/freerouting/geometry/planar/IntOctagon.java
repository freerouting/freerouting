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

/**
 *
 * Implements functionality for convex shapes, whose borderline directions are
 * multiples of 45 degree and defined with integer coordinates.
 *
 * @author Alfons Wirtz
 */

public class IntOctagon extends RegularTileShape implements java.io.Serializable
{
    /**
     * Reusable instance of an empty octagon.
     */
    public static final IntOctagon EMPTY =
            new IntOctagon(Limits.CRIT_INT, Limits.CRIT_INT, -Limits.CRIT_INT,
            -Limits.CRIT_INT, Limits.CRIT_INT, -Limits.CRIT_INT,
            Limits.CRIT_INT, -Limits.CRIT_INT);
    
    /**
     * Creates an IntOctagon from 8 integer values.
     * p_lx is the smallest x value of the shape.
     * p_ly is the smallest y value of the shape.
     * p_rx is the biggest x valuje af the shape.
     * p_uy is the biggest y value of the shape.
     * p_ulx is the intersection of the upper left diagonal boundary line
     * with the x axis.
     * p_lrx is the intersection of the lower right diagonal boundary line
     * with the x axis.
     * p_llx is the intersection of the lower left diagonal boundary line
     * with the x axis.
     * p_urx is the intersection of the upper right diagonal boundary line
     * with the x axis.
     */
    public IntOctagon(int p_lx, int p_ly, int p_rx, int p_uy,
            int p_ulx, int p_lrx, int p_llx, int p_urx)
    {
        lx = p_lx;
        ly = p_ly;
        rx = p_rx;
        uy = p_uy;
        ulx = p_ulx;
        lrx = p_lrx;
        llx = p_llx;
        urx = p_urx;
    }
    
    public boolean is_empty()
    {
        return this == EMPTY;
    }
    
    public boolean is_IntOctagon()
    {
        return true;
    }
    
    public boolean is_bounded()
    {
        return true;
    }
    
    public boolean corner_is_bounded(int p_no)
    {
        return true;
    }
    
    public IntBox bounding_box()
    {
        return new IntBox(lx, ly, rx, uy);
    }
    
    public IntOctagon bounding_octagon()
    {
        return this;
    }
    
    public IntOctagon bounding_tile()
    {
        return this;
    }
    
    public int dimension()
    {
        if (this == EMPTY)
        {
            return -1;
        }
        int result;
        
        if (rx > lx && uy > ly && lrx > ulx && urx > llx)
        {
            result = 2;
        }
        else if (rx == lx && uy == ly)
        {
            result = 0;
        }
        else
        {
            result = 1;
        }
        return result;
    }
    
    public IntPoint corner(int p_no)
    {
        
        int x;
        int y;
        switch (p_no)
        {
            case 0:
                x = llx - ly;
                y = ly;
                break;
            case 1:
                x = lrx + ly;
                y = ly;
                break;
            case 2:
                x = rx;
                y = rx - lrx;
                break;
            case 3:
                x = rx;
                y = urx - rx;
                break;
            case 4:
                x = urx - uy;
                y = uy;
                break;
            case 5:
                x = ulx + uy;
                y = uy;
                break;
            case 6:
                x = lx;
                y = lx - ulx;
                break;
            case 7:
                x = lx;
                y = llx - lx;
                break;
            default:
                throw new IllegalArgumentException
                        ("IntOctagon.corner: p_no out of range");
        }
        return new IntPoint(x,y);
    }
    
    /**
     * Additional to the function corner() for performance reasons to avoid allocation of an IntPoint.
     */
    public int corner_y(int p_no)
    {
        int y;
        switch (p_no)
        {
            case 0:
                y = ly;
                break;
            case 1:
                y = ly;
                break;
            case 2:
                y = rx - lrx;
                break;
            case 3:
                y = urx - rx;
                break;
            case 4:
                y = uy;
                break;
            case 5:
                y = uy;
                break;
            case 6:
                y = lx - ulx;
                break;
            case 7:
                y = llx - lx;
                break;
            default:
                throw new IllegalArgumentException
                        ("IntOctagon.corner: p_no out of range");
        }
        return y;
    }
    
    /**
     * Additional to the function corner() for performance reasons to avoid allocation of an IntPoint.
     */
    public int corner_x(int p_no)
    {
        
        int x;
        switch (p_no)
        {
            case 0:
                x = llx - ly;
                break;
            case 1:
                x = lrx + ly;
                break;
            case 2:
                x = rx;
                break;
            case 3:
                x = rx;
                break;
            case 4:
                x = urx - uy;
                break;
            case 5:
                x = ulx + uy;
                break;
            case 6:
                x = lx;
                break;
            case 7:
                x = lx;
                break;
            default:
                throw new IllegalArgumentException
                        ("IntOctagon.corner: p_no out of range");
        }
        return x;
    }
    
    public double area()
    {
        
        // calculate half of the absolute value of
        // x0 (y1 - y7) + x1 (y2 - y0) + x2 (y3 - y1) + ...+ x7( y0 - y6)
        // where xi, yi are the coordinates of the i-th corner of this Octagon.
        
        // Overwrites the same implementation in TileShape for performence
        // reasons to avoid Point allocation.
        
        double result = (double) (llx - ly) * (double) (ly - llx + lx);
        result += (double) (lrx + ly) * (double) (rx - lrx - ly);
        result += (double) rx * (double) (urx - 2 * rx - ly + uy  + lrx);
        result += (double) (urx - uy) * (double) (uy - urx + rx);
        result += (double) (ulx + uy) * (double) (lx - ulx - uy);
        result += (double) lx * (double) (llx - 2 * lx - uy + ly  + ulx);
        
        return  0.5 * Math.abs(result);
    }
    
    public int border_line_count()
    {
        return 8;
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
                a_y = ly;
                b_x = 1;
                b_y = ly;
                break;
            case 1:
                // lower right boundary line
                a_x = lrx;
                a_y = 0;
                b_x = lrx + 1;
                b_y = 1;
                break;
            case 2:
                // right boundary line
                a_x = rx;
                a_y = 0;
                b_x = rx;
                b_y = 1;
                break;
            case 3:
                // upper right boundary line
                a_x = urx;
                a_y = 0;
                b_x = urx - 1;
                b_y = 1;
                break;
            case 4:
                // upper boundary line
                a_x = 0;
                a_y = uy;
                b_x = -1;
                b_y = uy;
                break;
            case 5:
                // upper left boundary line
                a_x = ulx;
                a_y = 0;
                b_x = ulx - 1;
                b_y = -1;
                break;
            case 6:
                // left boundary line
                a_x = lx;
                a_y = 0;
                b_x = lx;
                b_y = -1;
                break;
            case 7:
                // lower left boundary line
                a_x = llx;
                a_y = 0;
                b_x = llx + 1;
                b_y = -1;
                break;
            default:
                throw new IllegalArgumentException
                        ("IntOctagon.edge_line: p_no out of range");
        }
        return new Line(a_x, a_y, b_x, b_y);
    }
    
    public IntOctagon translate_by(Vector p_rel_coor)
    {
        // This function is at the moment only implemented for Vectors
        // with integer coordinates.
        // The general implementation is still missing.
        
        if (p_rel_coor.equals(Vector.ZERO))
        {
            return this;
        }
        IntVector rel_coor = (IntVector) p_rel_coor;
        return new IntOctagon(lx + rel_coor.x, ly + rel_coor.y, rx + rel_coor.x, uy + rel_coor.y,
                ulx + rel_coor.x - rel_coor.y, lrx + rel_coor.x - rel_coor.y,
                llx + rel_coor.x + rel_coor.y, urx + rel_coor.x + rel_coor.y);
    }
    
    public double max_width()
    {
        double width_1 = Math.max(rx - lx, uy - ly);
        double width2 = Math.max(urx - llx, lrx - ulx);
        double result = Math.max(width_1, width2/ Limits.sqrt2);
        return result;
    }
    
    public double min_width()
    {
        double width_1 = Math.min(rx - lx, uy - ly);
        double width2 = Math.min(urx - llx, lrx - ulx);
        double result = Math.min(width_1, width2/ Limits.sqrt2);
        return result;
    }
    
    public IntOctagon offset(double p_distance)
    {
        int width = (int) Math.round(p_distance);
        if (width == 0)
        {
            return this;
        }
        int dia_width  = (int) Math.round(Limits.sqrt2 * p_distance);
        IntOctagon result =
                new IntOctagon(lx - width, ly - width, rx + width, uy + width,
                ulx - dia_width, lrx + dia_width,
                llx - dia_width, urx + dia_width);
        return result.normalize();
    }
    
    public IntOctagon enlarge(double p_offset)
    {
        return offset(p_offset);
    }
    
    public boolean contains(RegularTileShape p_other)
    {
        return p_other.is_contained_in(this);
    }
    
    public RegularTileShape union(RegularTileShape p_other)
    {
        return p_other.union(this);
    }
    
    public TileShape intersection(TileShape p_other)
    {
        return p_other.intersection(this);
    }
    
    public IntOctagon normalize()
    {
        if (lx > rx || ly > uy || llx > urx || ulx > lrx)
        {
            return EMPTY;
        }
        int new_lx = lx;
        int new_rx = rx;
        int new_ly = ly;
        int new_uy = uy;
        int new_llx = llx;
        int new_ulx = ulx;
        int new_lrx = lrx;
        int new_urx = urx;
        
        if (new_lx < new_llx - new_uy)
            // the point new_lx, new_uy is the the lower left border line of
            // this octagon
            // change new_lx , that the the lower left border line runs through
            // this point
        {
            new_lx = new_llx - new_uy;
        }
        
        if (new_lx < new_ulx + new_ly)
            // the point new_lx, new_ly is above the the upper left border line of
            // this octagon
            // change new_lx , that the the upper left border line runs through
            // this point
        {
            new_lx = new_ulx + new_ly;
        }
        
        if (new_rx > new_urx - new_ly)
            // the point new_rx, new_ly is above the the upper right border line of
            // this octagon
            // change new_rx , that the the upper right border line runs through
            // this point
        {
            new_rx = new_urx - new_ly;
        }
        
        if (new_rx > new_lrx + new_uy)
            // the point new_rx, new_uy is below the the lower right border line of
            // this octagon
            // change rx , that the the lower right border line runs through
            // this point
            
        {
            new_rx = new_lrx + new_uy;
        }
        
        if (new_ly < new_lx - new_lrx)
            // the point lx, ly is below the lower right border line of this
            // octagon
            // change ly, so that the lower right border line runs through
            // this point
        {
            new_ly = new_lx - new_lrx;
        }
        
        if (new_ly < new_llx - new_rx)
            // the point rx, ly is below the lower left border line of
            // this octagon.
            // change ly, so that the lower left border line runs through
            // this point
        {
            new_ly = new_llx - new_rx;
        }
        
        if (new_uy > new_urx - new_lx)
            // the point lx, uy is above the upper right border line of
            // this octagon.
            // Change the uy, so that the upper right border line runs through
            // this point.
        {
            new_uy = new_urx - new_lx;
        }
        
        if (new_uy > new_rx - new_ulx)
            // the point rx, uy is above the upper left border line of
            // this octagon.
            // Change the uy, so that the upper left border line runs through
            // this point.
        {
            new_uy = new_rx - new_ulx;
        }
        
        if (new_llx - new_lx < new_ly)
            // The point lx, ly is above the lower left border line of
            // this octagon.
            // Change the lower left line, so that it runs through this point.
        {
            new_llx = new_lx + new_ly;
        }
        
        if (new_rx - new_lrx < new_ly)
            // the point rx, ly is above the lower right border line of
            // this octagon.
            // Change the lower right line, so that it runs through this point.
        {
            new_lrx = new_rx - new_ly;
        }
        
        if (new_urx - new_rx > new_uy)
            // the point rx, uy is below the upper right border line of p_oct.
            // Change the upper right line, so that it runs through this point.
        {
            new_urx = new_uy + new_rx;
        }
        
        if (new_lx - new_ulx > new_uy)
            // the point lx, uy is below the upper left border line of
            // this octagon.
            // Change the upper left line, so that it runs through this point.
        {
            new_ulx = new_lx - new_uy;
        }
        
        int diag_upper_y =  (int)Math.ceil((new_urx - new_ulx) /  2.0);
        
        if (new_uy > diag_upper_y)
            // the intersection of the upper right and the upper left border
            // line is below new_uy.  Adjust new_uy to diag_upper_y.
        {
            new_uy = diag_upper_y;
        }
        
        int diag_lower_y =  (int)Math.floor((new_llx - new_lrx) /  2.0);
        
        if (new_ly < diag_lower_y)
            // the intersection of the lower right and the lower left border
            // line is above new_ly.  Adjust new_ly to diag_lower_y.
        {
            new_ly = diag_lower_y;
        }
        
        int diag_right_x = (int)Math.ceil((new_urx + new_lrx)/ 2.0);
        
        if (new_rx > diag_right_x)
            // the intersection of the upper right and the lower right border
            // line is to the left of  right x.  Adjust new_rx to diag_right_x.
        {
            new_rx = diag_right_x;
        }
        
        int diag_left_x =  (int)Math.floor((new_llx + new_ulx) / 2.0);
        
        if (new_lx < diag_left_x)
            // the intersection of the lower left and the upper left border
            // line is to the right of left x.  Ajust new_lx to diag_left_x.
        {
            new_lx = diag_left_x;
        }
        if (new_lx > new_rx || new_ly > new_uy || new_llx > new_urx
                || new_ulx > new_lrx)
        {
            return EMPTY;
        }
        return new IntOctagon(new_lx, new_ly, new_rx, new_uy, new_ulx,
                new_lrx, new_llx, new_urx);
    }
    
    /**
     * Checks, if this IntOctagon is normalized.
     */
    public boolean is_normalized()
    {
        IntOctagon on = this.normalize();
        boolean result =
                lx == on.lx && ly == on.ly && rx == on.rx && uy == on.uy &&
                llx == on.llx && lrx == on.lrx && ulx == on.ulx && urx == on.urx;
        return result;
    }
    
    
    public Simplex to_Simplex()
    {
        if (is_empty())
        {
            return Simplex.EMPTY;
        }
        if (precalculated_to_simplex == null)
        {
            Line [] line_arr  = new Line[8];
            for (int i = 0; i < 8; ++i)
            {
                line_arr[i] = border_line(i);
            }
            Simplex curr_simplex = new Simplex(line_arr);
            precalculated_to_simplex = curr_simplex.remove_redundant_lines();
        }
        return precalculated_to_simplex;
    }
    
    public RegularTileShape bounding_shape(ShapeBoundingDirections p_dirs)
    {
        return p_dirs.bounds(this);
    }
    
    public boolean intersects(Shape p_other)
    {
        return p_other.intersects(this);
    }
    
    /**
     * Returns true, if p_point is contained in this octagon.
     * Because of the parameter type FloatPoint, the function may not
     * be exact close to the border.
     */
    public boolean contains(FloatPoint p_point)
    {
        if (lx > p_point.x || ly  > p_point.y
                || rx < p_point.x || uy < p_point.y)
        {
            return false;
        }
        double tmp_1 = p_point.x - p_point.y;
        double tmp_2 = p_point.x + p_point.y;
        if (ulx > tmp_1 || lrx < tmp_1 || llx > tmp_2 || urx < tmp_2)
        {
            return false;
        }
        return true;
    }
    
    /**
     * Calculates the side of the point (p_x, p_y)  of the border line with index p_border_line_no.
     * The border lines are located in counterclock sense around this octagon.
     */
    public Side side_of_border_line(int p_x, int p_y, int p_border_line_no)
    {
        
        int tmp;
        if (p_border_line_no == 0)
        {
            tmp = this.ly - p_y;
        }
        else if (p_border_line_no == 2)
        {
            tmp = p_x - this.rx;
        }
        else if (p_border_line_no == 4)
        {
            tmp = p_y - this.uy;
        }
        else if (p_border_line_no == 6)
        {
            tmp = this.lx - p_x;
        }
        else if (p_border_line_no == 1)
        {
            tmp = p_x - p_y - this.lrx;
        }
        else if (p_border_line_no == 3)
        {
            tmp = p_x + p_y - this.urx;
        }
        else if (p_border_line_no == 5)
        {
            tmp = this.ulx + p_y - p_x;
        }
        else if (p_border_line_no == 7)
        {
            tmp = this.llx - p_x - p_y;
        }
        else
        {
            System.out.println("IntOctagon.side_of_border_line: p_border_line_no out of range");
            tmp = 0;
        }
        Side result;
        if (tmp < 0)
        {
            result = Side.ON_THE_LEFT;
        }
        else if (tmp > 0)
        {
            result = Side.ON_THE_RIGHT;
        }
        else
        {
            result = Side.COLLINEAR;
        }
        return result;
    }
    
    Simplex intersection(Simplex p_other)
    {
        return p_other.intersection(this);
    }
    
    
    public IntOctagon intersection(IntOctagon p_other)
    {
        IntOctagon result =
                new IntOctagon(Math.max(lx, p_other.lx), Math.max(ly, p_other.ly),
                Math.min(rx, p_other.rx), Math.min(uy, p_other.uy),
                Math.max(ulx, p_other.ulx), Math.min(lrx, p_other.lrx),
                Math.max(llx, p_other.llx), Math.min(urx, p_other.urx));
        return result.normalize();
    }
    
    IntOctagon intersection(IntBox p_other)
    {
        return intersection(p_other.to_IntOctagon());
    }
    
    /**
     * checkes if this (normalized) octagon is contained in p_box
     */
    public boolean is_contained_in(IntBox p_box)
    {
        return (lx >= p_box.ll.x && ly >= p_box.ll.y &&
                rx <= p_box.ur.x && uy <=p_box.ur.y);
    }
    
    public boolean is_contained_in(IntOctagon p_other)
    {
        boolean result = lx >= p_other.lx && ly >= p_other.ly &&
                rx <= p_other.rx && uy <= p_other.uy &&
                llx >= p_other.llx && ulx >= p_other.ulx &&
                lrx <= p_other.lrx && urx <= p_other.urx;
        
        return result;
    }
    
    public IntOctagon union(IntOctagon p_other)
    {
        IntOctagon result =
                new IntOctagon(Math.min(lx, p_other.lx), Math.min(ly, p_other.ly),
                Math.max(rx, p_other.rx), Math.max(uy, p_other.uy),
                Math.min(ulx, p_other.ulx), Math.max(lrx, p_other.lrx),
                Math.min(llx, p_other.llx), Math.max(urx, p_other.urx));
        return result;
    }
    
    public boolean intersects(IntBox p_other)
    {
        return intersects(p_other.to_IntOctagon());
    }
    
    /**
     * checks, if two normalized Octagons intersect.
     */
    public boolean intersects(IntOctagon p_other)
    {
        int is_lx;
        int is_rx;
        if (p_other.lx > this.lx)
        {
            is_lx = p_other.lx;
        }
        else
        {
            is_lx =  this.lx;
        }
        if (p_other.rx < this.rx)
        {
            is_rx = p_other.rx;
        }
        else
        {
            is_rx =  this.rx;
        }
        if (is_lx > is_rx)
        {
            return false;
        }
        
        int is_ly;
        int is_uy;
        if (p_other.ly > this.ly)
        {
            is_ly = p_other.ly;
        }
        else
        {
            is_ly =  this.ly;
        }
        if (p_other.uy < this.uy)
        {
            is_uy = p_other.uy;
        }
        else
        {
            is_uy =  this.uy;
        }
        if (is_ly > is_uy)
        {
            return false;
        }
        
        int is_llx;
        int is_urx;
        if (p_other.llx > this.llx)
        {
            is_llx = p_other.llx;
        }
        else
        {
            is_llx =  this.llx;
        }
        if (p_other.urx < this.urx)
        {
            is_urx = p_other.urx;
        }
        else
        {
            is_urx =  this.urx;
        }
        if (is_llx > is_urx)
        {
            return false;
        }
        
        int is_ulx;
        int is_lrx;
        if (p_other.ulx > this.ulx)
        {
            is_ulx = p_other.ulx;
        }
        else
        {
            is_ulx =  this.ulx;
        }
        if (p_other.lrx < this.lrx)
        {
            is_lrx = p_other.lrx;
        }
        else
        {
            is_lrx =  this.lrx;
        }
        if (is_ulx > is_lrx)
        {
            return false;
        }
        return true;
    }
    
    /**
     * Returns true, if this octagon intersects with p_other and the intersection is 2-dimensional.
     */
    public boolean overlaps(IntOctagon p_other)
    {
        int is_lx;
        int is_rx;
        if (p_other.lx > this.lx)
        {
            is_lx = p_other.lx;
        }
        else
        {
            is_lx =  this.lx;
        }
        if (p_other.rx < this.rx)
        {
            is_rx = p_other.rx;
        }
        else
        {
            is_rx =  this.rx;
        }
        if (is_lx >= is_rx)
        {
            return false;
        }
        
        int is_ly;
        int is_uy;
        if (p_other.ly > this.ly)
        {
            is_ly = p_other.ly;
        }
        else
        {
            is_ly =  this.ly;
        }
        if (p_other.uy < this.uy)
        {
            is_uy = p_other.uy;
        }
        else
        {
            is_uy =  this.uy;
        }
        if (is_ly >= is_uy)
        {
            return false;
        }
        
        int is_llx;
        int is_urx;
        if (p_other.llx > this.llx)
        {
            is_llx = p_other.llx;
        }
        else
        {
            is_llx =  this.llx;
        }
        if (p_other.urx < this.urx)
        {
            is_urx = p_other.urx;
        }
        else
        {
            is_urx =  this.urx;
        }
        if (is_llx >= is_urx)
        {
            return false;
        }
        
        int is_ulx;
        int is_lrx;
        if (p_other.ulx > this.ulx)
        {
            is_ulx = p_other.ulx;
        }
        else
        {
            is_ulx =  this.ulx;
        }
        if (p_other.lrx < this.lrx)
        {
            is_lrx = p_other.lrx;
        }
        else
        {
            is_lrx =  this.lrx;
        }
        if (is_ulx >= is_lrx)
        {
            return false;
        }
        return true;
    }
    
    public boolean intersects(Simplex p_other)
    {
        return p_other.intersects(this);
    }
    
    public boolean intersects(Circle p_other)
    {
        return p_other.intersects(this);
    }
    
    
    public IntOctagon union(IntBox p_other)
    {
        return union(p_other.to_IntOctagon());
    }
    
    /**
     * computes the x value of the left boundary of this Octagon at p_y
     */
    public int left_x_value(int p_y)
    {
        int result = Math.max(lx, ulx + p_y);
        return Math.max(result, llx - p_y);
    }
    
    /**
     * computes the x value of the right boundary of this Octagon at p_y
     */
    public int right_x_value(int p_y)
    {
        int result = Math.min(rx, urx - p_y);
        return Math.min(result, lrx + p_y);
    }
    
    /**
     * computes the y value of the lower boundary of this Octagon at p_x
     */
    public int lower_y_value(int p_x)
    {
        int result = Math.max(ly, llx - p_x);
        return Math.max(result, p_x - lrx);
    }
    
    /**
     * computes the y value of the upper boundary of this Octagon at p_x
     */
    public int upper_y_value(int p_x)
    {
        int result = Math.min(uy, p_x - ulx);
        return Math.min(result, urx - p_x);
    }
    
    public Side compare(RegularTileShape p_other, int p_edge_no)
    {
        Side result = p_other.compare(this, p_edge_no);
        return result.negate();
    }
    
    public Side compare(IntOctagon p_other, int p_edge_no)
    {
        Side result;
        switch (p_edge_no)
        {
            case 0:
                // compare the lower edge line
                if (ly > p_other.ly)
                {
                    result = Side.ON_THE_LEFT;
                }
                else if (ly < p_other.ly)
                {
                    result = Side.ON_THE_RIGHT;
                }
                else
                {
                    result = Side.COLLINEAR;
                }
                break;
                
            case 1:
                // compare the lower right edge line
                if (lrx < p_other.lrx)
                {
                    result = Side.ON_THE_LEFT;
                }
                else if (lrx > p_other.lrx)
                {
                    result = Side.ON_THE_RIGHT;
                }
                else
                {
                    result = Side.COLLINEAR;
                }
                break;
                
            case 2:
                // compare the right edge line
                if (rx < p_other.rx)
                {
                    result = Side.ON_THE_LEFT;
                }
                else if (rx > p_other.rx)
                {
                    result = Side.ON_THE_RIGHT;
                }
                else
                {
                    result = Side.COLLINEAR;
                }
                break;
                
                
            case 3:
                // compare the upper right edge line
                if (urx < p_other.urx)
                {
                    result = Side.ON_THE_LEFT;
                }
                else if (urx > p_other.urx)
                {
                    result = Side.ON_THE_RIGHT;
                }
                else
                {
                    result = Side.COLLINEAR;
                }
                break;
                
            case 4:
                // compare the upper edge line
                if (uy < p_other.uy)
                {
                    result = Side.ON_THE_LEFT;
                }
                else if (uy > p_other.uy)
                {
                    result = Side.ON_THE_RIGHT;
                }
                else
                {
                    result = Side.COLLINEAR;
                }
                break;
                
                
            case 5:
                // compare the upper left edge line
                if (ulx > p_other.ulx)
                {
                    result = Side.ON_THE_LEFT;
                }
                else if (ulx < p_other.ulx)
                {
                    result = Side.ON_THE_RIGHT;
                }
                else
                {
                    result = Side.COLLINEAR;
                }
                break;
                
            case 6:
                // compare the left edge line
                if (lx > p_other.lx)
                {
                    result = Side.ON_THE_LEFT;
                }
                else if (lx < p_other.lx)
                {
                    result = Side.ON_THE_RIGHT;
                }
                else
                {
                    result = Side.COLLINEAR;
                }
                break;
                
            case 7:
                // compare the lower left edge line
                if (llx > p_other.llx)
                {
                    result = Side.ON_THE_LEFT;
                }
                else if (llx < p_other.llx)
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
    
    public Side compare(IntBox p_other, int p_edge_no)
    {
        return compare(p_other.to_IntOctagon(), p_edge_no);
    }
    
    public int border_line_index(Line p_line)
    {
        System.out.println("edge_index_of_line not yet implemented for octagons");
        return -1;
    }
    /**
     * Calculates the border point of this octagon from p_point into the 45 degree direction p_dir.
     * If this border point is not an IntPoint, the nearest outside IntPoint of the octagon is returned.
     */
    public IntPoint border_point(IntPoint p_point, FortyfiveDegreeDirection p_dir)
    {
        int result_x;
        int result_y;
        switch (p_dir)
        {
            case RIGHT:
                result_x = Math.min(rx, urx - p_point.y);
                result_x = Math.min(result_x, lrx + p_point.y);
                result_y = p_point.y;
                break;
            case LEFT:
                result_x = Math.max(lx, ulx + p_point.y);
                result_x = Math.max(result_x, llx - p_point.y);
                result_y = p_point.y;
                break;
            case UP:
                result_x = p_point.x;
                result_y = Math.min(uy, p_point.x - ulx);
                result_y = Math.min(result_y, urx - p_point.x);
                break;
            case DOWN:
                result_x = p_point.x;
                result_y = Math.max(ly, llx - p_point.x);
                result_y = Math.max(result_y, p_point.x - lrx);
                break;
            case RIGHT45:
                result_x = (int) (Math.ceil(0.5 * (p_point.x - p_point.y + urx)));
                result_x = Math.min(result_x, rx);
                result_x = Math.min(result_x, p_point.x - p_point.x + uy);
                result_y = p_point.y - p_point.x + result_x;
                break;
            case UP45:
                result_x = (int)(Math.floor(0.5 * (p_point.x + p_point.y + ulx)));
                result_x = Math.max(result_x, lx);
                result_x = Math.max(result_x, p_point.x + p_point.y - uy);
                result_y = p_point.y + p_point.x - result_x;
                break;
            case LEFT45:
                result_x = (int)(Math.floor(0.5 * (p_point.x - p_point.y + llx)));
                result_x = Math.max(result_x, lx);
                result_x = Math.max(result_x, p_point.x - p_point.y + ly);
                result_y = p_point.y - p_point.x + result_x;
                break;
            case DOWN45:
                result_x = (int) (Math.ceil(0.5 * (p_point.x + p_point.y + lrx)));
                result_x = Math.min(result_x, rx);
                result_x = Math.min(result_x, p_point.x + p_point.y - ly);
                result_y = p_point.y + p_point.x - result_x;
                break;
            default:
                System.out.println("IntOctagon.border_point: unexpected 45 degree direction");
                result_x = 0;
                result_y = 0;
        }
        return new IntPoint(result_x, result_y);
    }
    
    /**
     * Calculates the sorted  p_max_result_points nearest points on the
     * border of this octagon in the 45-degree directions.
     * p_point is assumed to be located in the interiour of this octagon.
     */
    public IntPoint[] nearest_border_projections(IntPoint p_point, int p_max_result_points)
    {
        if (!this.contains(p_point) || p_max_result_points <= 0)
        {
            return new IntPoint[0];
        }
        p_max_result_points = Math.min(p_max_result_points, 8);
        IntPoint [] result = new IntPoint[p_max_result_points];
        double [] min_dist = new double [p_max_result_points];
        for (int i = 0; i < p_max_result_points; ++i)
        {
            min_dist[i] = Double.MAX_VALUE;
        }
        FloatPoint inside_point = p_point.to_float();
        for (FortyfiveDegreeDirection curr_dir : FortyfiveDegreeDirection.values())
        {
            IntPoint curr_border_point = border_point(p_point, curr_dir);
            double curr_dist = inside_point.distance_square(curr_border_point.to_float());
            for (int i = 0; i < p_max_result_points; ++i)
            {
                if (curr_dist < min_dist[i])
                {
                    for (int k = p_max_result_points - 1; k > i; --k)
                    {
                        min_dist[k] = min_dist[k - 1];
                        result[k] = result[k - 1];
                    }
                    min_dist[i] = curr_dist;
                    result[i] = curr_border_point;
                    break;
                }
            }
        }
        return result;
    }
    
    Side border_line_side_of( FloatPoint p_point, int p_line_no, double p_tolerance)
    {
        Side result;
        if (p_line_no == 0)
        {
            if (p_point.y > this.ly + p_tolerance)
            {
                result  = Side.ON_THE_RIGHT;
            }
            else if (p_point.y < this.ly - p_tolerance)
            {
                result  = Side.ON_THE_LEFT;
            }
            else
            {
                result = Side.COLLINEAR;
            }
        }
        else if (p_line_no == 2)
        {
            if (p_point.x < this.rx - p_tolerance)
            {
                result  = Side.ON_THE_RIGHT;
            }
            else if (p_point.x > this.rx + p_tolerance)
            {
                result  = Side.ON_THE_LEFT;
            }
            else
            {
                result = Side.COLLINEAR;
            }
        }
        else if (p_line_no == 4)
        {
            if (p_point.y < this.uy - p_tolerance)
            {
                result  = Side.ON_THE_RIGHT;
            }
            else if (p_point.y > this.uy  + p_tolerance )
            {
                result  = Side.ON_THE_LEFT;
            }
            else
            {
                result = Side.COLLINEAR;
            }
        }
        else if (p_line_no == 6)
        {
            if (p_point.x > this.lx + p_tolerance )
            {
                result  = Side.ON_THE_RIGHT;
            }
            else if (p_point.x < this.lx - p_tolerance )
            {
                result  = Side.ON_THE_LEFT;
            }
            else
            {
                result = Side.COLLINEAR;
            }
        }
        else if (p_line_no == 1)
        {
            double tmp = p_point.y - p_point.x + lrx;
            if (tmp > p_tolerance)
                // the p_point is above the the lower right border line of this octagon
            {
                result  = Side.ON_THE_RIGHT;
            }
            else if (tmp < -p_tolerance)
                // the p_point is below the the lower right border line of this octagon
            {
                result  = Side.ON_THE_LEFT;
            }
            else
            {
                result = Side.COLLINEAR;
            }
        }
        else if (p_line_no == 3)
        {
            double tmp = p_point.x + p_point.y - urx;
            if (tmp < -p_tolerance)
            {
                // the p_point is below the the upper right border line of this octagon
                result  = Side.ON_THE_RIGHT;
            }
            else if (tmp > p_tolerance)
            {
                // the p_point is above the the upper right border line of this octagon
                result  = Side.ON_THE_LEFT;
            }
            else
            {
                result = Side.COLLINEAR;
            }
        }
        else if (p_line_no == 5)
        {
            double tmp = p_point.y - p_point.x + ulx;
            if (tmp < -p_tolerance)
                // the p_point is below the the upper left border line of this octagon
            {
                result  = Side.ON_THE_RIGHT;
            }
            else if (tmp > p_tolerance)
                // the p_point is above the the upper left border line of this octagon
            {
                result  = Side.ON_THE_LEFT;
            }
            else
            {
                result = Side.COLLINEAR;
            }
        }
        else if (p_line_no == 7)
        {
            double tmp = p_point.x + p_point.y - llx;
            if (tmp > p_tolerance)
            {
                // the p_point is above the the lower left border line of this octagon
                result  = Side.ON_THE_RIGHT;
            }
            else if (tmp < -p_tolerance)
            {
                // the p_point is below the the lower left border line of this octagon
                result  = Side.ON_THE_LEFT;
            }
            else
            {
                result = Side.COLLINEAR;
            }
        }
        else
        {
            System.out.println("IntOctagon.border_line_side_of: p_line_no out of range");
            result = Side.COLLINEAR;
        }
        return result;
    }
    
    /**
     * Checks, if this octagon can be converted to an IntBox.
     */
    public boolean is_IntBox()
    {
        if (llx != lx + ly)
            return false;
        if (lrx != rx - ly)
            return false;
        if (urx != rx + uy)
            return false;
        if (ulx != lx - uy)
            return false;
        return true;
        
    }
    
    public TileShape simplify()
    {
        if (this.is_IntBox())
        {
            return this.bounding_box();
        }
        return this;
    }
    
    public TileShape[] cutout(TileShape p_shape)
    {
        return p_shape.cutout_from(this);
    }
    
    /**
     * Divide p_d minus this octagon into 8 convex pieces,
     * from which 4 have cut off a corner.
     */
    IntOctagon[] cutout_from(IntBox p_d)
    {
        IntOctagon c = this.intersection(p_d);
        
        if (this.is_empty() || c.dimension() < this.dimension())
        {
            // there is only an overlap at the border
            IntOctagon [] result = new IntOctagon[1];
            result[0] = p_d.to_IntOctagon();
            return result;
        }
        
        IntBox [] boxes = new IntBox[4];
        
        // construct left box
        
        boxes[0] = new IntBox(p_d.ll.x, c.llx - c.lx, c.lx, c.lx - c.ulx);
        
        // construct right box
        
        boxes[1] = new IntBox(c.rx, c.rx - c.lrx, p_d.ur.x, c.urx - c.rx);
        
        // construct lower box
        
        boxes[2] = new IntBox(c.llx - c.ly, p_d.ll.y, c.lrx + c.ly, c.ly);
        
        // construct upper box
        
        boxes[3] = new IntBox(c.ulx + c.uy, c.uy, c.urx - c.uy, p_d.ur.y);
        
        IntOctagon[] octagons = new IntOctagon[4];
        
        // construct upper left octagon
        
        IntOctagon curr_oct = new IntOctagon(p_d.ll.x, boxes[0].ur.y, boxes[3].ll.x,
                p_d.ur.y, -Limits.CRIT_INT, c.ulx, -Limits.CRIT_INT, Limits.CRIT_INT);
        octagons[0] = curr_oct.normalize();
        
        // construct lower left octagon
        
        curr_oct = new IntOctagon(p_d.ll.x, p_d.ll.y, boxes[2].ll.x, boxes[0].ll.y,
                -Limits.CRIT_INT, Limits.CRIT_INT, -Limits.CRIT_INT, c.llx);
        octagons[1] = curr_oct.normalize();
        
        // construct lower right octagon
        
        curr_oct = new IntOctagon(boxes[2].ur.x, p_d.ll.y, p_d.ur.x, boxes[1].ll.y,
                c.lrx, Limits.CRIT_INT, -Limits.CRIT_INT, Limits.CRIT_INT);
        octagons[2] = curr_oct.normalize();
        
        // construct upper right octagon
        
        curr_oct = new IntOctagon(boxes[3].ur.x, boxes[1].ur.y, p_d.ur.x, p_d.ur.y,
                -Limits.CRIT_INT, Limits.CRIT_INT, c.urx, Limits.CRIT_INT);
        octagons[3] = curr_oct.normalize();
        
        // optimise the result to minimum cumulative circumference
        
        IntBox b = boxes[0];
        IntOctagon o = octagons[0];
        if (b.ur.x - b.ll.x > o.uy - o.ly)
        {
            // switch the horizontal upper left divide line to vertical
            
            boxes[0] = new IntBox(b.ll.x, b.ll.y, b.ur.x, o.uy);
            curr_oct = new IntOctagon(b.ur.x, o.ly, o.rx, o.uy, o.ulx, o.lrx, o.llx, o.urx);
            octagons[0] = curr_oct.normalize();
        }
        
        b = boxes[3];
        o  = octagons[0];
        if (b.ur.y - b.ll.y > o.rx - o.lx)
        {
            // switch the vertical upper left divide line to horizontal
            
            boxes[3] = new IntBox(o.lx, b.ll.y, b.ur.x, b.ur.y);
            curr_oct = new IntOctagon(o.lx, o.ly, o.rx, b.ll.y, o.ulx, o.lrx, o.llx, o.urx);
            octagons[0] = curr_oct.normalize();
        }
        b = boxes[3];
        o = octagons[3];
        if (b.ur.y - b.ll.y > o.rx - o.lx)
        {
            // switch the vertical upper right divide line to horizontal
            
            boxes[3] = new IntBox(b.ll.x, b.ll.y, o.rx, b.ur.y);
            curr_oct = new IntOctagon(o.lx, o.ly, o.rx, o.uy, o.ulx, o.lrx, o.llx, o.urx);
            octagons[3] = curr_oct.normalize();
        }
        b = boxes[1];
        o = octagons[3];
        if (b.ur.x - b.ll.x > o.uy - o.ly)
        {
            // switch the horizontal upper right divide line to vertical
            
            boxes[1] = new IntBox(b.ll.x, b.ll.y, b.ur.x, o.uy);
            curr_oct = new IntOctagon(o.lx, o.ly, b.ll.x, o.uy, o.ulx, o.lrx, o.llx, o.urx);
            octagons[3] = curr_oct.normalize();
        }
        b = boxes[1];
        o = octagons[2];
        if (b.ur.x - b.ll.x > o.uy - o.ly)
        {
            // switch the horizontal lower right divide line to vertical
            
            boxes[1] = new IntBox(b.ll.x, o.ly, b.ur.x, b.ur.y);
            curr_oct = new IntOctagon(o.lx, o.ly, b.ll.x, o.uy, o.ulx, o.lrx, o.llx, o.urx);
            octagons[2] = curr_oct.normalize();
        }
        b = boxes[2];
        o = octagons[2];
        if (b.ur.y - b.ll.y > o.rx - o.lx)
        {
            // switch the vertical lower right divide line to horizontal
            
            boxes[2] = new IntBox(b.ll.x, b.ll.y, o.rx, b.ur.y);
            curr_oct = new IntOctagon(o.lx, b.ur.y, o.rx, o.uy, o.ulx, o.lrx, o.llx, o.urx);
            octagons[2] = curr_oct.normalize();
        }
        b = boxes[2];
        o = octagons[1];
        if (b.ur.y - b.ll.y > o.rx - o.lx)
        {
            // switch the vertical lower  left divide line to horizontal
            
            boxes[2] = new IntBox(o.lx, b.ll.y, b.ur.x, b.ur.y);
            curr_oct = new IntOctagon(o.lx, b.ur.y, o.rx, o.uy, o.ulx, o.lrx, o.llx, o.urx);
            octagons[1] = curr_oct.normalize();
        }
        b = boxes[0];
        o = octagons[1];
        if (b.ur.x - b.ll.x > o.uy - o.ly)
        {
            // switch the horizontal lower left divide line to vertical
            boxes[0] = new IntBox(b.ll.x, o.ly, b.ur.x, b.ur.y);
            curr_oct = new IntOctagon(b.ur.x, o.ly, o.rx, o.uy, o.ulx, o.lrx, o.llx, o.urx);
            octagons[1] = curr_oct.normalize();
        }
        
        IntOctagon[] result = new IntOctagon[8];
        
        // add the 4 boxes to the result
        for (int i = 0; i < 4; ++i)
        {
            result[i] = boxes[i].to_IntOctagon();
        }
        
        // add the 4 octagons to the result
        for (int i = 0; i < 4; ++i)
        {
            result[4 + i] = octagons[i];
        }
        return result;
    }
    
    /**
     * Divide p_divide_octagon minus cut_octagon into 8 convex
     * pieces without sharp angles.
     */
    IntOctagon[] cutout_from(IntOctagon p_d)
    {
        IntOctagon c = this.intersection(p_d);
        
        if (this.is_empty() || c.dimension() < this.dimension())
        {
            // there is only an overlap at the border
            IntOctagon [] result = new IntOctagon[1];
            result[0] = p_d;
            return result;
        }
        
        IntOctagon [] result = new IntOctagon[8];
        
        int tmp = c.llx - c.lx;
        
        result[0] = new
                IntOctagon(p_d.lx, tmp, c.lx, c.lx - c.ulx, p_d.ulx, p_d.lrx, p_d.llx, p_d.urx);
        
        int tmp2 = c.llx - c.ly;
        
        result[1] = new
                IntOctagon(p_d.lx, p_d.ly, tmp2, tmp, p_d.ulx, p_d.lrx, p_d.llx, c.llx);
        
        tmp = c.lrx + c.ly;
        
        result[2] = new
                IntOctagon(tmp2, p_d.ly, tmp, c.ly, p_d.ulx, p_d.lrx, p_d.llx, p_d.urx);
        
        tmp2 = c.rx - c.lrx;
        
        result[3] = new
                IntOctagon(tmp, p_d.ly, p_d.rx, tmp2, c.lrx, p_d.lrx, p_d.llx, p_d.urx);
        
        tmp = c.urx - c.rx;
        
        result[4] = new
                IntOctagon(c.rx, tmp2, p_d.rx, tmp, p_d.ulx, p_d.lrx, p_d.llx, p_d.urx);
        
        tmp2 = c.urx - c.uy;
        
        result[5] = new
                IntOctagon(tmp2, tmp, p_d.rx, p_d.uy, p_d.ulx, p_d.lrx, c.urx, p_d.urx);
        
        tmp = c.ulx + c.uy;
        
        result[6] = new
                IntOctagon(tmp, c.uy, tmp2, p_d.uy, p_d.ulx, p_d.lrx, p_d.llx, p_d.urx);
        
        tmp2 = c.lx - c.ulx;
        
        result[7] = new
                IntOctagon(p_d.lx, tmp2, tmp, p_d.uy, p_d.ulx, c.ulx, p_d.llx, p_d.urx);
        
        for (int i = 0; i < 8; ++i)
        {
            result[i] = result[i].normalize();
        }
        
        IntOctagon curr_1 = result[0];
        IntOctagon curr_2 = result[7];
        
        if (!(curr_1.is_empty() || curr_2.is_empty()) &&
                curr_1.rx - curr_1.left_x_value(curr_1.uy)
                > curr_2.upper_y_value(curr_1.rx) - curr_2.ly)
        {
            // switch the horizontal upper left divide line to vertical
            curr_1 = new IntOctagon(Math.min(curr_1.lx, curr_2.lx), curr_1.ly,
                    curr_1.rx, curr_2.uy, curr_2.ulx, curr_1.lrx,curr_1.llx, curr_2.urx);
            
            curr_2 = new IntOctagon(curr_1.rx, curr_2.ly, curr_2.rx, curr_2.uy,
                    curr_2.ulx, curr_2.lrx, curr_2.llx, curr_2.urx);
            
            result[0] = curr_1.normalize();
            result[7] = curr_2.normalize();
        }
        curr_1 = result[7];
        curr_2 = result[6];
        if (!(curr_1.is_empty() || curr_2.is_empty()) &&
                curr_2.upper_y_value(curr_1.rx) - curr_2.ly
                > curr_1.rx - curr_1.left_x_value(curr_2.ly))
            // switch the vertical upper left divide line to horizontal
        {
            curr_2 = new IntOctagon(curr_1.lx, curr_2.ly, curr_2.rx,
                    Math.max(curr_2.uy, curr_1.uy), curr_1.ulx, curr_2.lrx, curr_1.llx, curr_2.urx);
            
            curr_1 = new IntOctagon(curr_1.lx, curr_1.ly, curr_1.rx, curr_2.ly,
                    curr_1.ulx, curr_1.lrx, curr_1.llx, curr_1.urx);
            
            result[7] = curr_1.normalize();
            result[6] = curr_2.normalize();
        }
        curr_1 = result[6];
        curr_2 = result[5];
        if (!(curr_1.is_empty() || curr_2.is_empty()) &&
                curr_2.upper_y_value(curr_1.rx) - curr_1.ly
                > curr_2.right_x_value(curr_1.ly) - curr_2.lx)
            // switch the vertical upper right divide line to horizontal
        {
            curr_1 = new IntOctagon(curr_1.lx, curr_1.ly, curr_2.rx,
                    Math.max(curr_2.uy, curr_1.uy), curr_1.ulx, curr_2.lrx, curr_1.llx, curr_2.urx);
            
            curr_2 = new IntOctagon(curr_2.lx, curr_2.ly, curr_2.rx, curr_1.ly,
                    curr_2.ulx, curr_2.lrx, curr_2.llx, curr_2.urx);
            
            result[6] = curr_1.normalize();
            result[5] = curr_2.normalize();
        }
        curr_1 = result[5];
        curr_2 = result[4];
        if (!(curr_1.is_empty() || curr_2.is_empty()) &&
                curr_2.right_x_value(curr_2.uy) - curr_2.lx
                > curr_1.upper_y_value(curr_2.lx) - curr_2.uy)
            // switch the horizontal upper right divide line to vertical
        {
            curr_2 = new IntOctagon(curr_2.lx, curr_2.ly, Math.max(curr_2.rx, curr_1.rx),
                    curr_1.uy,  curr_1.ulx, curr_2.lrx, curr_2.llx, curr_1.urx);
            
            curr_1 = new IntOctagon(curr_1.lx, curr_1.ly, curr_2.lx, curr_1.uy,
                    curr_1.ulx, curr_1.lrx, curr_1.llx, curr_1.urx);
            
            result[5] = curr_1.normalize();
            result[4] = curr_2.normalize();
        }
        curr_1 = result[4];
        curr_2 = result[3];
        if (!(curr_1.is_empty() || curr_2.is_empty()) &&
                curr_1.right_x_value(curr_1.ly) - curr_1.lx
                > curr_1.ly - curr_2.lower_y_value(curr_1.lx))
            // switch the horizontal lower right divide line to vertical
        {
            curr_1 = new IntOctagon(curr_1.lx, curr_2.ly, Math.max(curr_2.rx, curr_1.rx),
                    curr_1.uy, curr_1.ulx, curr_2.lrx, curr_2.llx, curr_1.urx);
            
            curr_2 = new IntOctagon(curr_2.lx, curr_2.ly, curr_1.lx, curr_2.uy,
                    curr_2.ulx, curr_2.lrx, curr_2.llx, curr_2.urx);
            
            result[4] = curr_1.normalize();
            result[3] = curr_2.normalize();
        }
        
        curr_1 = result[3];
        curr_2 = result[2];
        
        if (!(curr_1.is_empty() || curr_2.is_empty()) &&
                curr_2.uy - curr_2.lower_y_value(curr_2.rx)
                > curr_1.right_x_value(curr_2.uy) - curr_2.rx)
            // switch the vertical lower right divide line to horizontal
        {
            curr_2 = new IntOctagon(curr_2.lx, Math.min(curr_1.ly, curr_2.ly),
                    curr_1.rx, curr_2.uy, curr_2.ulx, curr_1.lrx, curr_2.llx, curr_1.urx);
            
            curr_1 = new IntOctagon(curr_1.lx, curr_2.uy, curr_1.rx, curr_1.uy,
                    curr_1.ulx, curr_1.lrx, curr_1.llx, curr_1.urx);
            
            result[3] = curr_1.normalize();
            result[2] = curr_2.normalize();
        }
        
        curr_1 = result[2];
        curr_2 = result[1];
        
        if (!(curr_1.is_empty() || curr_2.is_empty()) &&
                curr_1.uy - curr_1.lower_y_value(curr_1.lx)
                > curr_1.lx - curr_2.left_x_value(curr_1.uy))
            // switch the vertical lower left divide line to horizontal
        {
            curr_1 = new IntOctagon(curr_2.lx, Math.min(curr_1.ly, curr_2.ly),
                    curr_1.rx, curr_1.uy, curr_2.ulx, curr_1.lrx, curr_2.llx, curr_1.urx);
            
            curr_2 = new IntOctagon(curr_2.lx, curr_1.uy, curr_2.rx, curr_2.uy,
                    curr_2.ulx, curr_2.lrx, curr_2.llx, curr_2.urx);
            
            result[2] = curr_1.normalize();
            result[1] = curr_2.normalize();
        }
        
        curr_1 = result[1];
        curr_2 = result[0];
        
        if (!(curr_1.is_empty() || curr_2.is_empty()) &&
                curr_2.rx - curr_2.left_x_value(curr_2.ly)
                > curr_2.ly - curr_1.lower_y_value(curr_2.rx))
            // switch the horizontal lower left divide line to vertical
        {
            curr_2 = new IntOctagon(Math.min(curr_2.lx, curr_1.lx), curr_1.ly,
                    curr_2.rx, curr_2.uy, curr_2.ulx, curr_1.lrx, curr_1.llx, curr_2.urx);
            
            curr_1 = new IntOctagon(curr_2.rx, curr_1.ly, curr_1.rx, curr_1.uy,
                    curr_1.ulx, curr_1.lrx, curr_1.llx, curr_1.urx);
            
            result[1] = curr_1.normalize();
            result[0] = curr_2.normalize();
        }
        
        return result;
    }
    
    
    Simplex[] cutout_from(Simplex p_simplex)
    {
        return this.to_Simplex().cutout_from(p_simplex);
    }
    
    /**
     * x coordinate of the left border line
     */
    public final int lx;
    
    /**
     * y coordinate of the lower border line
     */
    public final int ly;
    
    /**
     * x coordinate of the right border line
     */
    public final int rx;
    
    /**
     * y coordinate of the upper border line
     */
    public final int uy;
    
    /**
     * x axis intersection of the upper left border line
     */
    public final int ulx;
    
    /**
     * x axis intersection of the lower right border line
     */
    public final int lrx;
    
    /**
     *  x axis intersection of the lower left border line
     */
    public final int llx;
    
    /**
     *  x axis intersection of the upper right border line
     */
    public final int urx;
    
    /**
     * Result of to_simplex() memorized for performance reasons.
     */
    private Simplex precalculated_to_simplex = null;
    
}