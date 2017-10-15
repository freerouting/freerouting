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
 * IdNoGenerator.java
 *
 * Created on 13. Februar 2005, 08:21
 */

package datastructures;

/**
 * Interface for creatiing unique identification number.
 *
 * @author Alfons Wirtz
 */
public interface IdNoGenerator
{
    /**
     * Create a new unique identification number.
     */
    int new_no();
    
    /**
     * Return the maximum generated id number so far.
     */
    int max_generated_no();
}
