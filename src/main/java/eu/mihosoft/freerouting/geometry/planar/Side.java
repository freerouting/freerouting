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
 * Implementation of an enum class Side with the three values
 * ON_THE_LEFT, ON_THE_RIGHT, COLLINEAR.
 *

 * @author Alfons Wirtz
 */

public class Side
{
    public static final Side ON_THE_LEFT = new Side ("on_the_left");
    public static final Side ON_THE_RIGHT = new Side ("on_the_right");
    public static final Side COLLINEAR = new Side ("collinear");

    /**
     * returns the string of this instance
     */
    public String to_string ()
    {
        return name;
    }

    /**
     * returns the opposite side of this side
     */
    public final Side negate()
    {
        Side result;
        if (this == ON_THE_LEFT)
        {
            result = ON_THE_RIGHT;
        }
        else if (this == ON_THE_RIGHT)
        {
            result = ON_THE_LEFT;
        }
        else
        {
            result = this;
        }
        return result;
    }

    /**
     * returns ON_THE_LEFT, if p_value < 0,
     *         ON_THE_RIGHT, if p_value > 0
     *         and COLLINEAR, if p_value == 0
     */
     static final Side of(double p_value)
     {
        Side result;
        if (p_value > 0)
        {
            result = Side.ON_THE_LEFT;
        }
        else if (p_value < 0)
        {
            result = Side.ON_THE_RIGHT;
        }
        else
        {
            result = Side.COLLINEAR;
        }
        return result;
     }


    private Side(String p_name)
    {
        name = p_name;
    }

    private final String name;
}