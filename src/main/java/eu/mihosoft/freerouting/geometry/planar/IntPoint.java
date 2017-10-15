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
 * IntPoint.java
 *
 * Created on 1. Februar 2003, 10:31
 */

package geometry.planar;

import java.math.BigInteger;

/**
 * Implementation of the abstract class Point
 * as a tuple of integers.
 *
 *
 * @author Alfons Wirtz
 */

public class IntPoint extends Point implements java.io.Serializable
{
    
    /**
     * create an  IntPoint from two integer coordinates
     */
    public IntPoint(int p_x, int p_y)
    {
        if (Math.abs(p_x) > Limits.CRIT_INT || Math.abs(p_y) > Limits.CRIT_INT)
        {
            System.out.println("Warning in IntPoint: p_x or p_y to big");
        }
        x = p_x;
        y = p_y;
    }
    
    /**
     * Returns true, if this IntPoint is equal to p_ob
     */
    public final boolean equals( Object p_ob )
    {
        if ( this == p_ob )
        {
            return true;
        }
        if ( p_ob == null )
        {
            return false;
        }
        if ( getClass() != p_ob.getClass() )
        {
            return false ;
        }
        IntPoint other = (IntPoint)p_ob ;
        return ( x == other.x && y == other.y ) ;
    }
    
    public boolean is_infinite()
    {
        return false;
    }
    
    public IntBox surrounding_box()
    {
        return new IntBox(this, this);
    }
    
    public IntOctagon surrounding_octagon()
    {
        int tmp_1 = x - y;
        int tmp_2 = x + y;
        
        return new IntOctagon(x, y, x, y, tmp_1, tmp_1, tmp_2, tmp_2);
    }
    
    public boolean is_contained_in(IntBox p_box)
    {
        return x >= p_box.ll.x && y >= p_box.ll.y
                && x <= p_box.ur.x && y <= p_box.ur.y;
    }
    
    /**
     * returns the translation of this point by p_vector
     */
    public final Point translate_by( Vector p_vector )
    {
        if (p_vector.equals(Vector.ZERO))
        {
            return this;
        }
        return p_vector.add_to(this) ;
    }
    
    Point translate_by( IntVector p_vector )
    {
        return ( new IntPoint( x + p_vector.x, y + p_vector.y ) ) ;
    }
    
    Point translate_by( RationalVector p_vector )
    {
        return p_vector.add_to(this);
    }
    
    
    /**
     * returns the difference vector of this point and p_other
     */
    public Vector difference_by(Point p_other)
    {
        Vector tmp =  p_other.difference_by(this);
        return tmp.negate();
    }
    
    Vector difference_by(RationalPoint p_other)
    {
        Vector tmp = p_other.difference_by(this);
        return tmp.negate();
    }
    
    IntVector difference_by(IntPoint p_other)
    {
        return new IntVector(x - p_other.x, y - p_other.y);
    }
    
    public Side side_of(Line p_line)
    {
        Vector v1 = difference_by(p_line.a);
        Vector v2 = p_line.b.difference_by(p_line.a);
        return v1.side_of(v2);
    }
    
    /**
     * converts this point to a FloatPoint.
     */
    public FloatPoint to_float()
    {
        return new FloatPoint(x, y);
    }
    
    /**
     * returns the determinant of the vectors (x, y) and (p_other.x, p_other.y)
     */
    public final long determinant(IntPoint p_other)
    {
        return  (long)x * p_other.y - (long)y * p_other.x;
    }
    
    
    public Point perpendicular_projection(Line p_line)
    {
        // this function is at the moment only implemented for lines
        // consisting of IntPoints.
        // The general implementation is still missing.
        IntVector v = (IntVector)p_line.b.difference_by(p_line.a);
        BigInteger vxvx = BigInteger.valueOf((long)v.x * v.x);
        BigInteger vyvy = BigInteger.valueOf((long)v.y * v.y);
        BigInteger vxvy = BigInteger.valueOf((long) v.x * v.y);
        BigInteger denominator = vxvx.add(vyvy);
        BigInteger det =
                BigInteger.valueOf(((IntPoint)p_line.a).determinant((IntPoint)p_line.b));
        BigInteger point_x = BigInteger.valueOf(x);
        BigInteger point_y = BigInteger.valueOf(y);
        
        BigInteger tmp1 = vxvx.multiply(point_x);
        BigInteger tmp2 = vxvy.multiply(point_y);
        tmp1 = tmp1.add(tmp2);
        tmp2 = det.multiply(BigInteger.valueOf(v.y));
        BigInteger proj_x = tmp1.add(tmp2);
        
        tmp1 = vxvy.multiply(point_x);
        tmp2 = vyvy.multiply(point_y);
        tmp1 = tmp1.add(tmp2);
        tmp2 = det.multiply(BigInteger.valueOf(v.x));
        BigInteger proj_y = tmp1.subtract(tmp2);
        
        int signum = denominator.signum();
        if (signum != 0)
        {
            if (signum < 0)
            {
                denominator = denominator.negate();
                proj_x = proj_x.negate();
                proj_y = proj_y.negate();
            }
            if ((proj_x.mod(denominator)).signum() == 0 &&
                    (proj_y.mod(denominator)).signum() == 0)
            {
                proj_x = proj_x.divide(denominator);
                proj_y = proj_y.divide(denominator);
                return new IntPoint(proj_x.intValue(), proj_y.intValue());
            }
        }
        return new RationalPoint(proj_x, proj_y, denominator);
    }
    
    /**
     * Returns the signed area of the parallelogramm spanned by the vectors
     * p_2 - p_1 and this - p_1
     */
    public double signed_area( IntPoint p_1, IntPoint p_2 )
    {
        IntVector d21 = (IntVector) p_2.difference_by(p_1) ;
        IntVector d01 = (IntVector) this.difference_by(p_1) ;
        return d21.determinant(d01) ;
    }
    
    /**
     * calculates the square of the distance between this point and p_to_point
     */
    public double distance_square(IntPoint p_to_point)
    {
        double dx = p_to_point.x - this.x;
        double dy = p_to_point.y - this.y;
        return dx * dx + dy * dy;
    }
    
    /**
     * calculates the distance between this point and p_to_point
     */
    public double distance(IntPoint p_to_point)
    {
        return Math.sqrt(distance_square(p_to_point));
    }
    
    /**
     * Calculates the nearest point to this point on the horizontal or
     * vertical line through p_other (Snaps this point to on ortogonal line
     * through p_other).
     */
    public IntPoint orthogonal_projection(IntPoint p_other)
    {
        IntPoint result;
        int horizontal_distance = Math.abs(this.x -p_other.x);
        int vertical_distance = Math.abs(this.y -p_other.y);
        if (horizontal_distance <= vertical_distance)
        {
            // projection onto the vertical line through p_other
            result = new IntPoint(p_other.x, this.y);
        }
        else
        {
            // projection onto the horizontal line through p_other
            result = new IntPoint(this.x, p_other.y);
        }
        return result;
    }
    
    /**
     * Calculates the nearest point to this point on an orthogonal or
     * diagonal line through p_other (Snaps this point to on 45 degree line
     * through p_other).
     */
    public IntPoint fortyfive_degree_projection(IntPoint p_other)
    {
        int dx = this.x -p_other.x;
        int dy = this.y -p_other.y;
        double[] dist_arr = new double[4];
        dist_arr[0] = Math.abs(dx);
        dist_arr[1] = Math.abs(dy);
        double diagonal_1 = ((double)dy - (double)dx) / 2;
        double diagonal_2 = ((double)dy + (double)dx) / 2;
        dist_arr[2] = Math.abs(diagonal_1);
        dist_arr[3] = Math.abs(diagonal_2);
        double min_dist = dist_arr[0];
        for (int i = 1; i < 4; ++i)
        {
            if (dist_arr[i] < min_dist)
            {
                min_dist = dist_arr[i];
            }
        }
        IntPoint result;
        if (min_dist == dist_arr[0])
        {
            // projection onto the vertical line through p_other
            result = new IntPoint(p_other.x, this.y);
        }
        else if (min_dist == dist_arr[1])
        {
            // projection onto the horizontal line through p_other
            result = new IntPoint(this.x, p_other.y);
        }
        else if (min_dist == dist_arr[2])
        {
            // projection onto the right diagonal line through p_other
            int diagonal_value = (int)diagonal_2;
            result = new IntPoint(p_other.x + diagonal_value, p_other.y + diagonal_value);
        }
        else
        {
            // projection onto the left diagonal line through p_other
            int diagonal_value = (int)diagonal_1;
            result = new IntPoint(p_other.x - diagonal_value, p_other.y + diagonal_value);
        }
        return result;
    }
    
    /**
     * Calculates a corner point p so that the lines through this point and p and from
     * p to p_to_point are multiples of 45 degree, and that the angle at p will be
     * 45 degree. If p_left_turn, p_to_point will be on the left of the line
     * from this point to p, else on the right.
     * Returns null, if the line from this point to p_to_point is already a multiple
     * of 45 degree.
     */
    public IntPoint fortyfive_degree_corner( IntPoint p_to_point, boolean p_left_turn)
    {
        int dx = p_to_point.x - this.x;
        int dy = p_to_point.y - this.y;
        IntPoint result;
        
        // handle the 8 sections between the 45 degree lines
        
        if (dy > 0 && dy < dx)
        {
            if (p_left_turn)
            {
                result = new IntPoint(p_to_point.x - dy, this.y);
            }
            else
            {
                result = new IntPoint(this.x + dy, p_to_point.y);
            }
        }
        else if (dx > 0 && dy > dx)
        {
            if (p_left_turn)
            {
                result = new IntPoint(p_to_point.x, this.y + dx);
            }
            else
            {
                result = new IntPoint(this.x, p_to_point.y - dx);
            }
        }
        else if (dx < 0 && dy > -dx)
        {
            if (p_left_turn)
            {
                result = new IntPoint(this.x, p_to_point.y + dx);
            }
            else
            {
                result = new IntPoint(p_to_point.x, this.y - dx);
            }
        }
        else if (dy > 0 && dy < -dx)
        {
            if (p_left_turn)
            {
                result = new IntPoint(this.x - dy, p_to_point.y);
            }
            else
            {
                result = new IntPoint(p_to_point.x + dy, this.y);
            }
        }
        else if (dy < 0 && dy > dx)
        {
            if (p_left_turn)
            {
                result = new IntPoint(p_to_point.x - dy, this.y);
            }
            else
            {
                result = new IntPoint(this.x + dy, p_to_point.y);
            }
        }
        else if (dx < 0 && dy < dx)
        {
            if (p_left_turn)
            {
                result = new IntPoint(p_to_point.x, this.y + dx);
            }
            else
            {
                result = new IntPoint(this.x, p_to_point.y - dx);
            }
        }
        else if (dx > 0 && dy < -dx)
        {
            if (p_left_turn)
            {
                result = new IntPoint(this.x, p_to_point.y + dx);
            }
            else
            {
                result = new IntPoint(p_to_point.x, this.y - dx);
            }
        }
        else if (dy < 0 && dy > -dx)
        {
            if (p_left_turn)
            {
                result = new IntPoint(this.x - dy, p_to_point.y);
            }
            else
            {
                result = new IntPoint(p_to_point.x + dy, this.y);
            }
        }
        else
        {
            // the line from this point to p_to_point is already a multiple of 45 degree
            result = null;
        }
        return result;
    }
    
    /**
     * Calculates a corner point p so that the lines through this point and p and from
     * p to p_to_point are hprizontal or vertical, and that the angle at p will be
     * 90 degree. If p_left_turn, p_to_point will be on the left of the line
     * from this point to p, else on the right.
     * Returns null, if the line from this point to p_to_point is already orthogonal.
     */
    public IntPoint ninety_degree_corner( IntPoint p_to_point, boolean p_left_turn)
    {
        int dx = p_to_point.x - this.x;
        int dy = p_to_point.y - this.y;
        IntPoint result;
        
        // handle the 4 quadrants
        
        if (dx > 0 && dy > 0 || dx < 0 && dy < 0)
        {
            if (p_left_turn)
            {
                result = new IntPoint(p_to_point.x, this.y);
            }
            else
            {
                result = new IntPoint(this.x, p_to_point.y);
            }
        }
        else if (dx < 0 && dy > 0 || dx > 0 && dy < 0)
        {
            if (p_left_turn)
            {
                result = new IntPoint(this.x, p_to_point.y);
            }
            else
            {
                result = new IntPoint(p_to_point.x, this.y);
            }
        }
        else
        {
            //the line from this point to p_to_point is already orthogonal
            result = null;
        }
        return result;
    }
    
    public int compare_x(Point p_other)
    {
        return -p_other.compare_x(this);
    }
    
    public int compare_y(Point p_other)
    {
        return -p_other.compare_y(this);
    }
    
    
    int compare_x(IntPoint p_other)
    {
        int result;
        if (this.x > p_other.x)
        {
            result = 1;
        }
        else if (this.x == p_other.x)
        {
            result = 0;
        }
        else
        {
            result = -1;
        }
        return result;
    }
    
    int compare_y(IntPoint p_other)
    {
        int result;
        if (this.y > p_other.y)
        {
            result = 1;
        }
        else if (this.y == p_other.y)
        {
            result = 0;
        }
        else
        {
            result = -1;
        }
        return result;
    }
    
    int compare_x(RationalPoint p_other)
    {
        return -p_other.compare_x(this);
    }
    
    int compare_y(RationalPoint p_other)
    {
        return -p_other.compare_y(this);
    }
    
    /**
     * the x coordinate of this point
     */
    public final int x;
    
    /**
     * the y coordinate of this point
     */
    public final int y;
}

