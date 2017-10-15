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
 * FixedState.java
 *
 * Created on 15. Maerz 2005, 06:34
 */

package board;

/**
 * Sorted fixed states of board items. The strongest fixed states came last.
 *
 * @author alfons
 */
public enum FixedState
{
    UNFIXED, SHOVE_FIXED, USER_FIXED, SYSTEM_FIXED
}
