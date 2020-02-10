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
 * IdNoGenerator.java
 *
 * Created on 2. Juni 2003, 13:43
 */

package eu.mihosoft.freerouting.board;

import eu.mihosoft.freerouting.logger.FRLogger;

/**
 * Creates unique Item identication nunbers.
 *
 * @author Alfons Wirtz
 */
public class ItemIdNoGenerator implements eu.mihosoft.freerouting.datastructures.IdNoGenerator, java.io.Serializable
{
    
    /**
     * Creates a new ItemIdNoGenerator
     */
    public ItemIdNoGenerator()
    {
    }
    
    /**
     * Create a new unique identification number.
     * Use eventually the id_no generater from the host system
     * for syncronisation
     */
    public int new_no()
    {
        if (last_generated_id_no >= c_max_id_no)
        {
            FRLogger.warn("IdNoGenerator: danger of overflow, please regenerate id numbers from scratch!");
        }
        ++last_generated_id_no;
        return last_generated_id_no;
    }
    
    /**
     * Return the maximum generated id number so far.
     */
    public int max_generated_no()
    {
        return last_generated_id_no;
    }
    
    private int last_generated_id_no = 0;
    static final private int c_max_id_no = Integer.MAX_VALUE / 2;
}
