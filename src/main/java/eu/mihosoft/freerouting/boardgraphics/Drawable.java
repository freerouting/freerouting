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

package boardgraphics;

import java.awt.Color;
import java.awt.Graphics;

/**
 * items to be drawn by the functions in  GraphicsContext
 * must implement this interface
 *
 * @author Alfons Wirtz
 */

public interface Drawable
{
    /**
     * Draws this item to the device provided in p_graphics_context.
     * p_color_arr is an array of dimenssion layer_count.
     * p_intensity is a number between between 0 and 1.
     */
    void draw(Graphics p_g, GraphicsContext p_graphics_context, Color[] p_color_arr, double p_intensity);
    
    /**
     * Draws this item to the device provided in p_graphics_context.
     * It is drawn on each layer with the same color p_color.
     * p_intensity is a number between 0 and 1.
     */
    void draw(Graphics p_g, GraphicsContext p_graphics_context, Color p_color, double p_intensity);
    
    /**
     * Returns the priority for drawing an item.
     * Items with higher priority are drawn later than items with lower priority.
     */
    int get_draw_priority();
    
    /**
     * Gets the drawing intensity in the alpha blending for this item.
     */
    double get_draw_intensity(GraphicsContext p_graphics_context);
     
    /**
     * gets the draw colors for this object from p_graphics_context
     */
    Color[] get_draw_colors(GraphicsContext p_graphics_context);
    
    static final int MIN_DRAW_PRIORITY = 1;
    static final int MIDDLE_DRAW_PRIORITY = 3;
    static final int MAX_DRAW_PRIORITY = 3;
}