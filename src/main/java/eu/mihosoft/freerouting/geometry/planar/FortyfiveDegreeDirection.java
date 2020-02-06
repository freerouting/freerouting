/*
 *   Copyright (C) 2014  Alfons Wirtz
 *   website www.freerouting.net
 *
 *   Copyright (C) 2017 Michael Hoffer <info@michaelhoffer.de>
 *   Website www.freerouting.mihosoft.eu
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
 * FortyfiveDegreeDirections.java
 *
 * Created on 11. Dezember 2005, 07:18
 *
 */

package eu.mihosoft.freerouting.geometry.planar;
@SuppressWarnings("all") // Eclipse regards get_direction() as unused

/**
 * Enum for the eight 45-degree direction starting from right in counterclocksense to down45.
 *
 * @author Alfons Wirtz
 */
public enum FortyfiveDegreeDirection
{
    RIGHT
    {
        public IntDirection get_direction()
        {
            return Direction.RIGHT;
        }
    },
    RIGHT45
    {
        public IntDirection get_direction()
        {
            return Direction.RIGHT45;
        }
    },
    UP
    {
        public IntDirection get_direction()
        {
            return Direction.UP;
        }
    },
    UP45
    {
        public IntDirection get_direction()
        {
            return Direction.UP45;
        }
    },
    LEFT
    {
        public IntDirection get_direction()
        {
            return Direction.LEFT;
        }
    },
    LEFT45
    {
        public IntDirection get_direction()
        {
            return Direction.LEFT45;
        }
    },
    DOWN
    {
        public IntDirection get_direction()
        {
            return Direction.DOWN;
        }
    },
    DOWN45
    {
        public IntDirection get_direction()
        {
            return Direction.DOWN45;
        }
    }
}
