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
import java.math.BigInteger;

/**
 *
 * Some numerical limits and values are stored here.
 *

 * @author Alfons Wirtz
 */

public class Limits
{

    /**
     * An upper bound (2^25) so that the product of two integers with absolut
     * value at most CRIT_COOR is contained in the mantissa of a double with
     * some space left for addition.
     */
    public static final int CRIT_INT  = 33554432;

    /**
     * the biggest double value ( 2 ^53) , so that all integers smaller than
     * this value are exact represented as double value
     */
    public static final double CRIT_DOUBLE = 9007199254740992.0;

    public static final BigInteger CRIT_INT_BIG = BigInteger.valueOf(CRIT_INT);

    public static final double sqrt2 = Math.sqrt(2);

    private Limits() // disallow instantiation
    {
    }
}