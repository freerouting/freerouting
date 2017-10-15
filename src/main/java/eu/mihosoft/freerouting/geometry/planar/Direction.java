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
 * Direction.java
 *
 * Created on 3. Februar 2003, 15:36
 */

package geometry.planar;
import datastructures.Signum;

/**
 *
 * Abstract class defining functionality of directions
 * in the plane. A Direction is an equivalence class of
 * vectors. Two vectors define the same object of class
 * Direction, if they point into the same direction.
 * We prefer using directions instead of angles, because
 * with angles the arithmetic calculations are in
 * general not exact.
 *
 * @author Alfons Wirtz
 */

public abstract class  Direction implements Comparable<Direction>, java.io.Serializable
{
    public static final IntDirection NULL = new IntDirection(0,0);
    
    /**
     * the direction to the east
     */
    public static final IntDirection RIGHT   = new IntDirection(1, 0);
    /**
     * the direction to the northeast
     */
    public static final IntDirection RIGHT45 = new IntDirection(1, 1);
    /**
     * the direction to the north
     */
    public static final IntDirection UP      = new IntDirection(0, 1);
    /**
     * the direction to the northwest
     */
    public static final IntDirection UP45    = new IntDirection(-1, 1);
    /**
     * the direction to the west
     */
    public static final IntDirection LEFT    = new IntDirection(-1, 0);
    /**
     * the direction to the southwest
     */
    public static final IntDirection LEFT45  = new IntDirection(-1, -1);
    /**
     * the direction to the south
     */
    public static final IntDirection DOWN    = new IntDirection(0, -1);
    /**
     * the direction to the southeast
     */
    public static final IntDirection DOWN45  = new IntDirection(1, -1);
    
    /**
     * creates a Direction from the input Vector
     */
    public static Direction get_instance( Vector p_vector )
    {
        return p_vector.to_normalized_direction();
    }
    
    /**
     * Calculates the direction from p_from to p_to.
     * If p_from and p_to are equal, null is returned.
     */
    public static Direction get_instance( Point p_from, Point p_to )
    {
        if ( p_from.equals(p_to) )
        {
            return null;
        }
        return get_instance(p_to.difference_by( p_from ));
    }
    
    /**
     * Creates a Direction whose angle with the x-axis is nearly equal to p_angle
     */
    public static Direction get_instance_approx( double p_angle )
    {
        final double scale_factor = 10000;
        int x = (int)Math.round(Math.cos(p_angle) * scale_factor);
        int y = (int)Math.round(Math.sin(p_angle) * scale_factor);
        return get_instance(new IntVector(x, y));
    }
    
    /**
     * return any Vector pointing into this direction
     */
    public abstract Vector get_vector();
    
    /**
     * returns true, if the direction is horizontal or vertical
     */
    public abstract boolean is_orthogonal();
    
    /**
     * returns true, if the direction is diagonal
     */
    public abstract boolean is_diagonal();
    
    /**
     * returns true, if the direction is orthogonal or diagonal
     */
    public boolean is_multiple_of_45_degree()
    {
        return ( is_orthogonal() || is_diagonal() ) ;
    }
    
    /**
     * turns the direction by p_factor times 45 degree
     */
    public abstract Direction turn_45_degree(int p_factor);
    
    /**
     * returns the opposite direction of this direction
     */
    public abstract Direction opposite();
    
    /**
     * Returns true, if p_ob is a Direction and
     * this Direction and p_ob point into the same direction
     */
    public final boolean equals( Direction p_other )
    {
        if ( this == p_other )
        {
            return true;
        }
        if ( p_other == null )
        {
            return false;
        }
        
        if (this.side_of(p_other) != Side.COLLINEAR)
        {
            return false;
        }
        // check, that dir and other_dir do not point into opposite directions
        Vector this_vector = get_vector();
        Vector  other_vector = p_other.get_vector() ;
        return this_vector.projection(other_vector) == Signum.POSITIVE;
    }
    
    /**
     * Let L be the line from the Zero Vector to p_other.get_vector().
     * The function returns
     *         Side.ON_THE_LEFT, if this.get_vector() is on the left of L
     *         Side.ON_THE_RIGHT, if this.get_vector() is on the right of L
     *     and Side.COLLINEAR, if this.get_vector() is collinear with L.
     */
    public Side side_of(Direction p_other)
    {
        return this.get_vector().side_of(p_other.get_vector());
    }
    
    /**
     * The function returns
     *   Signum.POSITIVE, if the scalar product of of a vector representing
     *                    this direction and a vector representing p_other is > 0,
     *   Signum.NEGATIVE, if the scalar product is < 0,
     *   and Signum.ZERO, if the scalar product is equal 0.
     */
    public Signum projection(Direction p_other)
    {
        return this.get_vector().projection(p_other.get_vector());
    }
    
    /**
     * calculates an approximation of the direction in the middle of
     * this direction and p_other
     */
    public Direction middle_approx(Direction p_other)
    {
        FloatPoint v1 = get_vector().to_float();
        FloatPoint v2 = p_other.get_vector().to_float();
        double length1 = v1.size();
        double length2 = v2.size();
        double x = v1.x / length1 + v2.x /length2;
        double y = v1.y / length1 + v2.y /length2;
        final double scale_factor = 1000;
        Vector vm = new IntVector((int)Math.round(x * scale_factor),
                (int)Math.round(y * scale_factor));
        return Direction.get_instance(vm);
    }
    
    
    /**
     * Returns 1, if the angle between p_1 and this direction is bigger
     * the angle between p_2 and this direction,
     * 0, if p_1 is equal to p_2, * and -1 otherwise.
     */
    public int compare_from(Direction p_1, Direction p_2)
    {
        int result;
        if (p_1.compareTo(this) >= 0)
        {
            if (p_2.compareTo(this) >= 0)
            {
                result = p_1.compareTo(p_2);
            }
            else
            {
                result = -1;
            }
        }
        else
        {
            if (p_2.compareTo(this) >= 0)
            {
                result = 1;
            }
            else
            {
               result = p_1.compareTo(p_2);
            }
        }
        return result;
    }
    
    /**
     * Returns an approximation of the signed angle corresponding to this dierection.
     */
    public double angle_approx()
    {
        return this.get_vector().angle_approx();
    }
    
    // auxiliary functions needed because the virtual function mechanism
    // does not work in parameter position
    
    abstract int compareTo(IntDirection p_other);
    abstract int compareTo(BigIntDirection p_other);
    
}