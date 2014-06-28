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
 * ItemDrawInfo.java
 *
 * Created on 14. Juli 2004, 10:26
 */

package boardgraphics;

import java.awt.Color;

/**
 * Information for drawing an item on the screen.
 *
 * @author  alfons
 */
public class ItemDrawInfo
{
    
    /** Creates a new instance of ItemDrawInfo */
    public ItemDrawInfo(Color[] p_layer_color, double p_intensity)
    {
        layer_color = p_layer_color;
        intensity = p_intensity;
    }
    
    /** The color of the item on each layer */
    public final Color[] layer_color;
    
    // The translucency factor of the color. Must be between 0 and 1.
    public final double intensity;
}
