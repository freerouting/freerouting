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
 * RationalPoint.java
 *
 * Created on 1. Februar 2003, 13:12
 */

package geometry.planar;
import java.math.BigInteger;

import datastructures.BigIntAux;

/**
 *
 * Implementation of points in the projective plane represented by
 * 3 coordinates x, y, z, which are infinite precision integers.
 * Two projective points (x1, y1, z1) and (x2, y2 z2) are equal,
 * if they are located on the same line through the zero point,
 * that means, there exist a number r with x2 = r*x1,
 * y2 = r*y1 and z2 = r*z1.
 * The affine Point with rational coordinates  represented by
 * the projective Point  (x, y, z) is (x/z, y/z).
 * The projective plane with integer coordinates contains in
 * addition to the affine plane with rational coordinates the
 * so-called line at infinity, which consist of
 * all projective points (x, y, z) with z = 0.
 *
 * @author Alfons Wirtz
 */

public class RationalPoint extends Point implements java.io.Serializable
{
    
    /**
     * approximates the coordinates of this point by float coordinates
     */
    public FloatPoint to_float()
    {
        double xd = x.doubleValue();
        double yd = y.doubleValue();
        double zd = z.doubleValue();
        if (zd == 0)
        {
            xd = Float.MAX_VALUE;
            yd = Float.MAX_VALUE;
        }
        else
        {
            xd /= zd;
            yd /= zd;
        }
        
        return new FloatPoint( xd, yd);
    }
    
    /**
     * returns true, if this RationalPoint is equal to p_ob
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
        RationalPoint other = (RationalPoint)p_ob;
        BigInteger det = BigIntAux.determinant(x, other.x, z, other.z);
        if (det.signum() != 0)
        {
            return false;
        }
        det = BigIntAux.determinant(y, other.y, z, other.z);
        
        return (det.signum() == 0);
    }
    
    public boolean is_infinite()
    {
        return z.signum() == 0;
    }
    
    public IntBox surrounding_box()
    {
        FloatPoint fp = to_float();
        int llx = (int) Math.floor(fp.x);
        int lly = (int) Math.floor(fp.y);
        int urx = (int) Math.ceil(fp.x);
        int ury = (int) Math.ceil(fp.y);
        return new IntBox(llx, lly, urx, ury);
    }
    
    public IntOctagon surrounding_octagon()
    {
        FloatPoint fp = to_float();
        int lx = (int) Math.floor(fp.x);
        int ly = (int) Math.floor(fp.y);
        int rx = (int) Math.ceil(fp.x);
        int uy = (int) Math.ceil(fp.y);
        
        double tmp = fp.x - fp.y;
        int ulx = (int) Math.floor(tmp);
        int lrx = (int) Math.ceil(tmp);
        
        tmp = fp.x + fp.y;
        int llx = (int) Math.floor(tmp);
        int urx = (int) Math.ceil(tmp);
        return new IntOctagon(lx, ly, rx, uy, ulx, lrx, llx, urx);
    }
    
    public boolean is_contained_in(IntBox p_box)
    {
        BigInteger tmp = BigInteger.valueOf(p_box.ll.x).multiply(z);
        if (x.compareTo(tmp) < 0)
        {
            return false;
        }
        tmp = BigInteger.valueOf(p_box.ll.y).multiply(z);
        if (y.compareTo(tmp) < 0)
        {
            return false;
        }
        tmp = BigInteger.valueOf(p_box.ur.x).multiply(z);
        if (x.compareTo(tmp) > 0)
        {
            return false;
        }
        tmp = BigInteger.valueOf(p_box.ur.y).multiply(z);
        if (y.compareTo(tmp) > 0)
        {
            return false;
        }
        return true;
    }
    
    /**
     * returns the translation of this point by p_vector
     */
    public Point translate_by(Vector p_vector)
    {
        if (p_vector.equals(Vector.ZERO))
        {
            return this;
        }
        return p_vector.add_to(this) ;
    }
    
    Point translate_by(IntVector p_vector)
    {
        RationalVector vector = new RationalVector(p_vector);
        return  translate_by(vector);
    }
    
    Point translate_by(RationalVector p_vector)
    {
        BigInteger v1[] = new BigInteger[3];
        v1[0] = x;
        v1[1] = y;
        v1[2] = z;
        
        BigInteger v2[] = new BigInteger[3];
        v2[0] = p_vector.x;
        v2[1] = p_vector.y;
        v2[2] = p_vector.z;
        BigInteger[] result = BigIntAux.add_rational_coordinates(v1, v2);
        return new RationalPoint(result[0], result[1], result[2]);
    }
    
    /**
     * returns the difference vector of this point and p_other
     */
    public Vector difference_by(Point p_other)
    {
        Vector tmp =  p_other.difference_by(this);
        return tmp.negate();
    }
    
    Vector difference_by(IntPoint p_other)
    {
        RationalPoint other = new RationalPoint(p_other);
        return difference_by(other);
    }
    
    Vector difference_by(RationalPoint p_other)
    {
        BigInteger v1[] = new BigInteger[3];
        v1[0] = x;
        v1[1] = y;
        v1[2] = z;
        
        BigInteger v2[] = new BigInteger[3];
        v2[0] = p_other.x.negate();
        v2[1] = p_other.y.negate();
        v2[2] = p_other.z;
        BigInteger[] result = BigIntAux.add_rational_coordinates(v1, v2);
        return new RationalVector(result[0], result[1], result[2]);
    }
    
    /**
     * The function returns
     *         Side.ON_THE_LEFT, if this Point is on the left
     *                            of the line from p_1 to p_2;
     *         Side.ON_THE_RIGHT, if this Point is on the right
     *                            f the line from p_1 to p_2;
     *     and Side.COLLINEAR, if this Point is collinear with p_1 and p_2.
     */
    public Side side_of(Point p_1, Point p_2)
    {
        Vector v1 = difference_by(p_1);
        Vector v2 = p_2.difference_by(p_1);
        return v1.side_of(v2);
    }
    
    public Side side_of(Line p_line)
    {
        return side_of(p_line.a, p_line.b);
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
        
        BigInteger tmp1 = vxvx.multiply(x);
        BigInteger tmp2 = vxvy.multiply(y);
        tmp1 = tmp1.add(tmp2);
        tmp2 = det.multiply(BigInteger.valueOf(v.y));
        tmp2 = tmp2.multiply(z);
        BigInteger proj_x = tmp1.add(tmp2);
        
        tmp1 = vxvy.multiply(x);
        tmp2 = vyvy.multiply(y);
        tmp1 = tmp1.add(tmp2);
        tmp2 = det.multiply(BigInteger.valueOf(v.x));
        tmp2 = tmp2.multiply(z);
        BigInteger proj_y = tmp1.add(tmp2);
        
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
                if (proj_x.abs().compareTo(Limits.CRIT_INT_BIG) <= 0
                        && proj_y.abs().compareTo(Limits.CRIT_INT_BIG) <= 0)
                {
                    return new IntPoint(proj_x.intValue(), proj_y.intValue());
                }
                denominator = BigInteger.ONE;
            }
        }
        return new RationalPoint(proj_x, proj_y, denominator);
    }
    
    public int compare_x(Point p_other)
    {
        return -p_other.compare_x(this);
    }
    
    
    public int compare_y(Point p_other)
    {
        return -p_other.compare_y(this);
    }
    
    int compare_x(RationalPoint p_other)
    {
        BigInteger tmp1 = this.x.multiply(p_other.z);
        BigInteger tmp2 = p_other.x.multiply(this.z);
        return tmp1.compareTo(tmp2);
    }
    
    int compare_y(RationalPoint p_other)
    {
        BigInteger tmp1 = this.y.multiply(p_other.z);
        BigInteger tmp2 = p_other.y.multiply(this.z);
        return tmp1.compareTo(tmp2);
    }
    
    int compare_x(IntPoint p_other)
    {
        BigInteger tmp1 = this.z.multiply(BigInteger.valueOf(p_other.x));
        return this.x.compareTo(tmp1);
    }
    
    int compare_y(IntPoint p_other)
    {
        BigInteger tmp1 = this.z.multiply(BigInteger.valueOf(p_other.y));
        return this.y.compareTo(tmp1);
        
    }
    
    /**
     *  creates a RetionalPoint from 3 BigIntegers p_x, p_y and p_z.
     *  They represent the 2-dimensinal point with the
     *  rational number Tuple ( p_x / p_z , p_y / p_z).
     *  Throws IllegalArgumentException if denominator p_z is <= 0
     */
    RationalPoint(BigInteger p_x, BigInteger p_y, BigInteger p_z)
    {
        x = p_x;
        y = p_y;
        z = p_z;
        if (p_z.signum() < 0)
        {
            throw new IllegalArgumentException
                    ("RationalPoint: p_z is expected to be >= 0");
        }
    }
    
    /**
     * creates a RetionalPoint from an IntPoint
     */
    RationalPoint(IntPoint p_point)
    {
        x = BigInteger.valueOf(p_point.x);
        y = BigInteger.valueOf(p_point.y);
        z = BigInteger.ONE;
    }
    
    
    
    final BigInteger x;
    final BigInteger y;
    final BigInteger z;
    
    
}