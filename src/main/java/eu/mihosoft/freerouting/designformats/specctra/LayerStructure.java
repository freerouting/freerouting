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
 * LayerStructure.java
 *
 * Created on 16. Mai 2004, 08:08
 */
package designformats.specctra;

import java.util.Collection;
import java.util.Iterator;

/**
 * Describes a layer structure read from a dsn file.
 *
 * @author  alfons
 */
public class LayerStructure
{

    /** Creates a new instance of LayerStructure  from a list of layers*/
    public LayerStructure(Collection<Layer> p_layer_list)
    {
        arr = new Layer[p_layer_list.size()];
        Iterator<Layer> it = p_layer_list.iterator();
        for (int i = 0; i < arr.length; ++i)
        {
            arr[i] = it.next();
        }
    }

    /**
     * Creates a dsn-LayerStructure from a board LayerStructure.
     */
    public LayerStructure(board.LayerStructure p_board_layer_structure)
    {
        arr = new Layer[p_board_layer_structure.arr.length];
        for (int i = 0; i < arr.length; ++i)
        {
            board.Layer board_layer = p_board_layer_structure.arr[i];
            arr[i] = new Layer(board_layer.name, i, board_layer.is_signal);
        }
    }

    /**
     * returns the number of the layer with the name p_name,
     * -1, if no layer with name p_name exists.
     */
    public int get_no(String p_name)
    {
        for (int i = 0; i < arr.length; ++i)
        {
            if (p_name.equals(arr[i].name))
            {
                return i;
            }
        }
        // check for special layers of the Electra autorouter used for the outline
        if (p_name.contains("Top"))
        {
            return 0;
        }
        if (p_name.contains("Bottom"))
        {
            return arr.length - 1;
        }
        return -1;
    }

    public int signal_layer_count()
    {
        int result = 0;
        for (Layer curr_layer : arr)
        {
            if (curr_layer.is_signal)
            {
                ++result;
            }
        }
        return result;
    }

    /**
     * Returns, if the net with name p_net_name contains a powwer plane.
     */
    public boolean contains_plane(String p_net_name)
    {

        for (Layer curr_layer : arr)
        {
            if (!curr_layer.is_signal)
            {
                if (curr_layer.net_names.contains(p_net_name))
                {
                    return true;
                }
            }
        }
        return false;
    }
    
    public final Layer[] arr;
}
