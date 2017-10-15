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
 * Observers.java
 *
 * Created on 13. Februar 2005, 09:14
 */

package datastructures;

/**
 * Interface to observe changes on objects for syncronisatiation purposes.
 *
 * @author Alfons Wirtz
 */
public interface Observers<ObjectType>
{
    /**
     * Tell the observers the deletion p_object.
     */
    void notify_deleted(ObjectType p_object);
    
    /**
     * Notify the observers, that they can syncronize the changes on p_object.
     */
    void notify_changed(ObjectType p_object);
    
    /**
     * Enable the observers to syncronize the new created item.
     */
    void notify_new(ObjectType p_object);
    
    /** Starts notifying the observers */
    void activate();
    
    /** Ends notifying the observers */
    void deactivate();
    
    /**
     * Returns, if the observer is activated.
     */
    boolean is_active();
}
