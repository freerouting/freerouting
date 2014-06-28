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
 * BoardObserverAdaptor.java
 *
 * Created on 20. September 2007, 07:44
 *
 */

package board;

/**
 * Empty adaptor implementing the BoardObservers interface.
 *
 * @author Alfons Wirtz
 */
public class BoardObserverAdaptor implements BoardObservers
{
    /**
     * Tell the observers the deletion p_object.
     */
    public void notify_deleted(Item p_item)
    {
        
    }
    
    /**
     * Notify the observers, that they can syncronize the changes on p_object.
     */
    public void notify_changed(Item p_item)
    {
        
    }
    
    /**
     * Enable the observers to syncronize the new created item.
     */
    public void notify_new(Item p_item)
    {
        
    }
    
    /**
     * Enable the observers to syncronize the moved component.
     */
    public void notify_moved(Component p_component)
    {
        
    }
    
    /**
     * activate the observers
     */
    public void activate()
    {
        active = true;
    }
    
    /**
     * Deactivate the observers.
     **/
    public void deactivate()
    {
        active = false;
    }
    
    /**
     * Returns, if the observer is activated.
     */
    public boolean is_active()
    {
        return active;
    }
    
    private boolean active = false;
    
}
