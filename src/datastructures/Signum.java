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

package datastructures;

/**
 *
 * Implements the mathematical signum function.
 *

 * @author Alfons Wirtz
 */

public class Signum
{
    public static final Signum POSITIVE = new Signum ("positive");
    public static final Signum NEGATIVE = new Signum ("negative");
    public static final Signum ZERO = new Signum ("zero");

    /**
     * Returns the signum of p_value.
     * Values are Signum.POSITIVE, Signum.NEGATIVE and Signum.ZERO
     */
    public static final Signum of (double p_value)
    {
        Signum result;

        if (p_value > 0)
        {
            result = POSITIVE;
        }
        else if (p_value < 0)
        {
            result = NEGATIVE;
        }
        else
        {
            result = ZERO;
        }
        return result;
    }

    /**
     * Returns the signum of p_value as an int.
     * Values are +1, 0 and -1
     */
    public static final int as_int (double p_value)
    {
        int result;

        if (p_value > 0)
        {
            result = 1;
        }
        else if (p_value < 0)
        {
            result = -1;
        }
        else
        {
            result = 0;
        }
        return result;
    }

    /**
     * Returns the string of this instance
     */
    public String to_string ()
    {
        return name;
    }

    /**
     * Returns the opposite Signum of this Signum
     */
    public final Signum negate()
    {
        Signum result;
        if (this == POSITIVE)
        {
            result = NEGATIVE;
        }
        else if (this == NEGATIVE)
        {
            result = POSITIVE;
        }
        else
        {
            result = this;
        }
        return result;
    }


    private Signum(String p_name)
    {
        name = p_name;
    }

    private final String name;
}