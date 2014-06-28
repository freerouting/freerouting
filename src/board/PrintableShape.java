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
 * PrintableShape.java
 *
 * Created on 5. Januar 2005, 08:02
 */

package board;

import geometry.planar.FloatPoint;

/**
 * Shape class used for printing a geometry.planar.Shape after transforming it to user coordinates.
 *
 * @author Alfons Wirtz
 */
public abstract class PrintableShape
{
    protected PrintableShape(java.util.Locale p_locale)
    {
        this.locale = p_locale;
    }
    
    /**
     * Returns text information about the PrintableShape.
     */
    public abstract String toString();
    
    protected final java.util.Locale locale;
    
    static class Circle extends PrintableShape
    {
        /**
         * Creates a Circle from the input coordinates.
         */
        public Circle(FloatPoint p_center, double p_radius, java.util.Locale p_locale)
        {
            super(p_locale);
            center = p_center;
            radius = p_radius;
        }
        
        public String toString()
        {
            java.util.ResourceBundle resources = 
                    java.util.ResourceBundle.getBundle("board.resources.ObjectInfoPanel", this.locale);
            String result = resources.getString("circle") + ": ";
            if (center.x != 0 || center.y != 0)
            {
                String center_string = resources.getString("center") + " =" + center.to_string(this.locale);
                result += center_string;
            }
            java.text.NumberFormat nf =  java.text.NumberFormat.getInstance(this.locale);
            nf.setMaximumFractionDigits(4);
            String radius_string = resources.getString("radius") + " = " + nf.format((float)radius);
            result += radius_string;
            return result;
        }
        
        public final FloatPoint center;
        public final double radius;
    }
    
    /**
     * Creates a Polygon from the input coordinates.
     */
    static class Rectangle extends PrintableShape
    {
        public Rectangle(FloatPoint p_lower_left, FloatPoint p_upper_right, java.util.Locale p_locale)
        {
            super(p_locale);
            lower_left = p_lower_left;
            upper_right = p_upper_right;
        }
        
        public String toString()
        {
            java.util.ResourceBundle resources = 
                    java.util.ResourceBundle.getBundle("board.resources.ObjectInfoPanel", this.locale);
            String result = resources.getString("rectangle") + ": " + resources.getString("lower_left") + " = "
                    + lower_left.to_string(this.locale) + ", " + resources.getString("upper_right") + " = "
                    + upper_right.to_string(this.locale) ;
            return result;
        }
        
        public final FloatPoint lower_left;
        public final FloatPoint upper_right;
    }
    
    
    static class Polygon extends PrintableShape
    {
        public Polygon(FloatPoint[] p_corners, java.util.Locale p_locale)
        {
            super(p_locale);
            corner_arr = p_corners;
        }
        
        public String toString()
        {
            java.util.ResourceBundle resources = 
                    java.util.ResourceBundle.getBundle("board.resources.ObjectInfoPanel", this.locale);
            String result = resources.getString("polygon") + ": ";
            for (int i = 0; i < corner_arr.length; ++i)
            {
                if (i > 0)
                {
                    result += ", ";
                }
                result += corner_arr[i].to_string(this.locale);
            }
            return result;
        }
        
        public final FloatPoint[] corner_arr;
    }
}
