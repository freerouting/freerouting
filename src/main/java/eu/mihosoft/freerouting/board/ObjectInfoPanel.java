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
 * PrintInfoWindow.java
 *
 * Created on 6. Januar 2005, 13:15
 */

package board;

/**
 * Output window for printing information about board objects.
 *
 * @author Alfons Wirtz
 */
public interface ObjectInfoPanel
{
    /**
     * Appends p_string to the window.
     * Returns false, if that was not possible.
     */
    boolean append(String p_string);
    
    /**
     * Appends p_string in bold styleto the window.
     * Returns false, if that was not possible.
     */
    boolean append_bold(String p_string);
    
    /**
     * Appends p_value to the window after
     * transforming it to the user coordinate sytem.
     * Returns false, if that was not possible.
     */
    boolean append(double p_value);
    
    /**
     * Appends p_value to the window without
     * transforming it to the user coordinate sytem.
     * Returns false, if that was not possible.
     */
    boolean append_without_transforming(double p_value);
    
    /**
     * Appends p_point to the window
     * after transforming to the user coordinate sytem.
     * Returns false, if that was not possible.
     */
    boolean append(geometry.planar.FloatPoint p_point);
    
    /**
     * Appends p_shape to the window
     * after transforming to the user coordinate sytem.
     * Returns false, if that was not possible.
     */
    boolean append(geometry.planar.Shape p_shape, java.util.Locale p_locale);
    
    /**
     * Begins a new line in the window.
     */
    boolean newline();
    
    /**
     * Appends a fixed number of spaces to the window.
     */
    boolean indent();
    
    /**
     * Appends a link for creating a new PrintInfoWindow with the information
     * of p_object to the window. Returns false, if that was not possible.
     */
    boolean append( String p_link_name, String p_window_title, ObjectInfoPanel.Printable p_object);
    
    
    /**
     * Appends a link for creating a new PrintInfoWindow with the information
     * of p_items to the window. Returns false, if that was not possible.
     */
    boolean append_items( String p_link_name, String p_window_title, java.util.Collection<board.Item> p_items);
    
    /**
     * Appends a link for creating a new PrintInfoWindow with the information
     * of p_objects to the window. Returns false, if that was not possible.
     */
    boolean append_objects( String p_button_name, String p_window_title,
            java.util.Collection<Printable> p_objects);
    
    /**
     * Funktionality needed for objects to print information into an ObjectInfoWindow
     */
    public interface Printable
    {
        /**
         * Prints information about an ObjectInfoWindow.Printable object into the input window.
         */
        void print_info(ObjectInfoPanel p_window, java.util.Locale p_locale);
    }
}
