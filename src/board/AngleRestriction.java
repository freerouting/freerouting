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
 * SnapAngle.java
 *
 * Created on 14. Juli 2003, 07:40
 */

package board;

/**
 * Enum for angle restrictions none, fortyfive degree and ninety degree.
 *
 * @author Alfons Wirtz
 */
public class AngleRestriction
{
    public static final AngleRestriction NONE = new AngleRestriction("none", 0);
    public static final AngleRestriction FORTYFIVE_DEGREE = new AngleRestriction("45 degree", 1);
    public static final AngleRestriction NINETY_DEGREE = new AngleRestriction("90 degree", 2);
    
    public static final AngleRestriction[] arr =
    {
        NONE, FORTYFIVE_DEGREE, NINETY_DEGREE
    };
    
    /**
     * Returns the string of this instance
     */
    public String to_string()
    {
        return name;
    }
    
    /**
     * Returns the number of this instance
     */
    public int get_no()
    {
        return no;
    }
    /** Creates a new instance of SnapAngle */
    private AngleRestriction(String p_name, int p_no)
    {
        name = p_name;
        no = p_no;
    }
    
    private final String name;
    private final int no;
}