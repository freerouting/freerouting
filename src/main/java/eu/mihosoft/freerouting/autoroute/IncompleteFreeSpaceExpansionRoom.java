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
 * IncompleteFreeSpaceExpansionRoom.java
 *
 * Created on 10. Februar 2004, 10:13
 */

package autoroute;

import java.util.Collection;

import geometry.planar.TileShape;


/**
 * An expansion room, whose shape is not yet completely calculated.
 *
 * @author  Alfons Wirtz
 */
public class IncompleteFreeSpaceExpansionRoom extends FreeSpaceExpansionRoom
{
    
    /**
     * Creates a new instance of IncompleteFreeSpaceExpansionRoom.
     * If p_shape == null means p_shape is the whole plane.
     */
    public IncompleteFreeSpaceExpansionRoom(TileShape p_shape, int p_layer, TileShape p_contained_shape)
    {
        super(p_shape, p_layer);
        contained_shape = p_contained_shape;
    }
    
    public TileShape get_contained_shape()
    {
        return this.contained_shape;
    }
    
    public void set_contained_shape(TileShape p_shape)
    {
        this.contained_shape = p_shape;
    }
    
    public Collection<TargetItemExpansionDoor> get_target_doors()
    {
        return new java.util.LinkedList<TargetItemExpansionDoor>();
    }
    
    /** A shape which should be contained in the completed shape. */
    private TileShape contained_shape;
}
