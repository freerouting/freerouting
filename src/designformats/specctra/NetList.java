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
 * NetList.java
 *
 * Created on 19. Mai 2004, 09:05
 */

package designformats.specctra;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;

/**
 * Describes a list of nets sorted by its names.
 * The net number is generated internally.
 *
 * @author  alfons
 */
public class NetList
{
    
    /**
     * Returns true, if the netlist contains a net with the input name.
     */
    public boolean contains(Net.Id p_net_id)
    {
        return nets.containsKey(p_net_id);
    }
    
    /**
     * Adds a new net mit the input name to the net list.
     * Returns null, if a net with p_name already exists in the net list.
     * In this case no new net is added.
     */
    public Net add_net(Net.Id p_net_id)
    {
        Net result;
        if (nets.containsKey(p_net_id))
        {
            result = null;
        }
        else
        {
            result = new Net(p_net_id);
            nets.put(p_net_id, result);
        }
        return result;
    }
    
    /**
     * Returns the net with the input name, or null,
     * if the netlist does not contain a net with the input name.
     */
    public Net get_net(Net.Id p_net_id)
    {
        Object value = nets.get(p_net_id);
        return ((Net) value);
    }
    
    /**
     * Returns all nets in this net list containing the input pin.
     */
    public Collection<Net> get_nets(String p_component_name, String p_pin_name)
    {
        Collection<Net> result = new java.util.LinkedList<Net>();
        Net.Pin search_pin = new Net.Pin(p_component_name, p_pin_name);
        Collection<Net>  net_list = nets.values();
        Iterator<Net> it = net_list.iterator();
        while (it.hasNext())
        {
            Net curr_net = it.next();
            Set<Net.Pin> net_pins = curr_net.get_pins();
            if (net_pins != null && net_pins.contains(search_pin))
            {
                result.add(curr_net);
            }
        }
        return result;
    }
    
    /** The entries of this map are of type Net, the keys are the net_ids. */
    private final Map<Net.Id, Net> nets = new TreeMap<Net.Id, Net>();
    
}
