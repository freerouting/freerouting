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
 * TimeLimit.java
 *
 * Created on 15. Maerz 2006, 09:27
 *
 */

package datastructures;

/**
 * Class used to cancel a performance critical algorithm after a time limit is exceeded.
 * @author Alfons Wirtz
 */
public class TimeLimit
{
    
    /**
     * Creates a new instance with a time limit of p_milli_seconds milli seconds
     */
    public TimeLimit(int p_milli_seconds)
    {
        this.time_limit = p_milli_seconds;
        this.time_stamp = (new java.util.Date()).getTime();
    }
    
    /**
     * Returns true, if the time limit provided in the constructor of this class is exceeded.
     */
    public boolean limit_exceeded()
    {
        long curr_time = (new java.util.Date()).getTime();
        return (curr_time - this.time_stamp > this.time_limit);
    }
    
    /**
     * Multiplies this TimeLimit by p_factor.
     */
    public void muultiply(double  p_factor)
    {
        if (p_factor <= 0)
        {
            return;
        }
        double new_limit = (p_factor * this.time_limit);
        new_limit = Math.min(new_limit, Integer.MAX_VALUE);
        this.time_limit = (int) new_limit;
    }
    
    private final long time_stamp;
    private int time_limit;
}
