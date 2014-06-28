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
 * BigIntDirection.java
 *
 * Created on 4. Februar 2003, 14:10
 */

package geometry.planar;
import java.math.BigInteger;

/**
 *
 * Implements the abstract class Direction as a tuple
 * of infinite precision integers.
 *
 *
 * @author Alfons Wirtz
 */

public class BigIntDirection extends Direction implements java.io.Serializable
{
    public boolean is_orthogonal()
    {
        return (x.signum() == 0 || y.signum() == 0);
    }
    
    public boolean is_diagonal()
    {
        return x.abs().equals(y.abs());
    }
    
    public Vector get_vector()
    {
        return new RationalVector(x, y, BigInteger.ONE);
    }
    
    public Direction turn_45_degree(int p_factor)
    {
        System.out.println("BigIntDirection: turn_45_degree not yet implemented");
        return this;
    }
    
    public Direction opposite()
    {
        return new BigIntDirection(x.negate(), y.negate());
    }
    
    final BigInteger x;
    final BigInteger y;
    
    BigIntDirection(BigInteger p_x, BigInteger p_y)
    {
        x = p_x;
        y = p_y;
    }
    
    /**
     * creates a BigIntDirection from an IntDirection
     */
    BigIntDirection(IntDirection p_dir)
    {
        x = BigInteger.valueOf(p_dir.x);
        y = BigInteger.valueOf(p_dir.y);
    }
    
    /**
     * Implements the Comparable interface.
     * Returns 1, if this direction has a strict bigger angle with
     * the positive x-axis than p_other_direction,
     * 0, if this direction is equal to p_other_direction,
     * and -1 otherwise.
     * Throws an exception, if p_other_direction is not a Direction.
     */
    public int compareTo(Direction p_other_direction)
    {
        return -p_other_direction.compareTo(this);
    }
    
    int compareTo(IntDirection p_other)
    {
        BigIntDirection other = new BigIntDirection(p_other);
        return compareTo(other);
    }
    
    int compareTo(BigIntDirection p_other)
    {
        int x1 = x.signum();
        int y1 = y.signum();
        int x2 = p_other.x.signum();
        int y2 = p_other.y.signum();
        if (y1 > 0)
        {
            if (y2 < 0)
            {
                return -1 ;
            }
            if (y2 == 0)
            {
                if (x2 > 0)
                {
                    return 1 ;
                }
                return -1 ;
            }
        }
        else if (y1 < 0)
        {
            if (y2 >= 0)
            {
                return 1 ;
            }
        }
        else // y1 == 0
        {
            if (x1 > 0)
            {
                if (y2 != 0 || x2 < 0)
                {
                    return -1 ;
                }
                return 0 ;
            }
            // x1 < 0
            if (y2 > 0 || y2 == 0 && x2 > 0)
            {
                return 1 ;
            }
            if (y2 < 0)
            {
                return -1 ;
            }
            return 0;
        }
        
        // now this direction and p_other are located in the same
        // open horizontal half plane
        
        BigInteger tmp_1 = y.multiply(p_other.x);
        BigInteger tmp_2 = x.multiply(p_other.y);
        BigInteger determinant = tmp_1.subtract(tmp_2);
        return determinant.signum();
    }
}