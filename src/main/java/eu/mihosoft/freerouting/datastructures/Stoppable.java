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
 * Stoppable.java
 *
 * Created on 7. Maerz 2006, 09:46
 *
 */

package datastructures;

/**
 * Interface for stoppable threads.
 *
 * @author alfons
 */
public interface Stoppable
{
    /**
     * Requests this thread to be stopped.
     */
    void request_stop();
    
    /**
     * Returns true, if this thread is requested to be stopped.
     */
    boolean is_stop_requested();
}
