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
 * Layer.java
 *
 * Created on 26. Mai 2004, 06:31
 */

package board;

/**
 * Describes the structure of a board layer.
 *
 * @author  alfons
 */
public class Layer implements java.io.Serializable
{
    
    /** Creates a new instance of Layer */
    public Layer(String p_name, boolean p_is_signal)
    {
        name = p_name;
        is_signal = p_is_signal;
    }
    
    public String toString()
    {
        return name;
    }
    
    /** The name of the layer. */
    public final String name;
    
    /** 
     * True, if this is a signal layer, which can be used for routing.
     * Otherwise it may be for example a power ground layer.
     */
    public final boolean is_signal;
}
