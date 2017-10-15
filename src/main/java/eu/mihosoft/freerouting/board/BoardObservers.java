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
 * BoardObservers.java
 *
 * Created on 20. September 2007, 07:39
 *
 */

package board;

import datastructures.Observers;

/**
 *
 * @author alfons
 */
public interface BoardObservers extends Observers<Item>
{
    /**
     * Enable the observers to syncronize the moved component.
     */
    public void notify_moved(Component p_component);
}
