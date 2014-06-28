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
 * Unit.java
 *
 * Created on 13. Dezember 2004, 08:01
 */

package board;

/**
 * Enum for the userunits inch, mil or millimeter.
 *
 * @author Alfons Wirtz
 */
public enum Unit implements java.io.Serializable
{
    MIL
    {
        public String toString()
        {
            return "mil";
        }
    },
    INCH
    {
        public String toString()
        {
            return "inch";
        }
    },
    
    MM
    {
        public String toString()
        {
            return "mm";
        }
    },
    
    UM
    {
        public String toString()
        {
            return "um";
        }
    };
    
    /** Scales p_value from p_from_unit to p_to_unit */
    public static double scale(double p_value, Unit p_from_unit, Unit p_to_unit)
    {
        double result;
        if (p_from_unit == p_to_unit)
        {
            result = p_value;
        }
        else if (p_from_unit == INCH)
        {
            if(p_to_unit == MIL)
            {
                result =  p_value * 1000.0;
            }
            else if (p_to_unit == MM)
            {
                result = p_value * INCH_TO_MM;
            }
            else // um
            {
                result = p_value * INCH_TO_MM * 1000.0;
            }
        }
        else if (p_from_unit == MIL)
        {
            if(p_to_unit == INCH)
            {
                result =  p_value / 1000.0;
            }
            else if(p_to_unit == MM)
            {
                result =  p_value * INCH_TO_MM;
            }
            else // um
            {
                result = (p_value * INCH_TO_MM) * 1000.0;
            }
        }
        else if (p_from_unit == MM)
        {
            if(p_to_unit == INCH)
            {
                result =  p_value / INCH_TO_MM;
            }
            else if(p_to_unit == UM)
            {
                result =  p_value * 1000;
            }
            else // mil
            {
                result = (p_value * 1000.0) / INCH_TO_MM;
            }
        }
        else //UM
        {
            if(p_to_unit == INCH)
            {
                result =  p_value / (INCH_TO_MM * 1000.0);
            }
            else if(p_to_unit == MM)
            {
                result =  p_value / 1000.0;
            }
            else // mil
            {
                result = p_value / INCH_TO_MM;
            }
        }
        return result;
    }
    
    /**
     * Return the unit corresponding to the input string,
     * or null, if the input string is different from mil, inch and mm.
     */
    public static Unit from_string(String p_string)
    {
        Unit result;
        if (p_string.compareToIgnoreCase("mil") == 0)
        {
            result = MIL;
        }
        else if (p_string.compareToIgnoreCase("inch") == 0)
        {
            result = INCH;
        }
        else if (p_string.compareToIgnoreCase("mm") == 0)
        {
            result = MM;
        }
        else if (p_string.compareToIgnoreCase("um") == 0)
        {
            result = UM;
        }
        else
        {
            result = null;
        }
        return result;
    }
    
    public static final double INCH_TO_MM = 25.4;
}
