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
 */

package board;
import geometry.planar.FloatPoint;
import geometry.planar.IntBox;
import geometry.planar.IntOctagon;

/**
 *
 * Used internally for marking changed areas on the board
 * after shoving and optimizing items.
 *

 * @author Alfons Wirtz
 */

class ChangedArea
{

    public ChangedArea(int p_layer_count)
    {
        layer_count = p_layer_count;
        arr = new MutableOctagon [layer_count];
        // initialise all octagons to empty
        for (int i = 0; i < layer_count; ++i)
        {
            arr[i] = new MutableOctagon();
            arr[i].set_empty();
        }
    }

    /**
     * enlarges the octagon on p_layer, so that it contains p_point
     */
    public void join (FloatPoint p_point, int p_layer)
    {
        MutableOctagon curr = arr[p_layer];
        curr.lx = Math.min(p_point.x, curr.lx);
        curr.ly = Math.min(p_point.y, curr.ly);
        curr.rx = Math.max(curr.rx, p_point.x);
        curr.uy = Math.max(curr.uy, p_point.y);

        double tmp = p_point.x - p_point.y;
        curr.ulx = Math.min(curr.ulx, tmp);
        curr.lrx = Math.max(curr.lrx, tmp);

        tmp = p_point.x + p_point.y;
        curr.llx = Math.min(curr.llx, tmp);
        curr.urx = Math.max(curr.urx, tmp);
    }
    
     /**
     * enlarges the octagon on p_layer, so that it contains p_shape
     */
    public void join (geometry.planar.TileShape p_shape, int p_layer)
    {
        if (p_shape == null)
        {
            return;
        }
        int corner_count = p_shape.border_line_count();
        for (int i = 0; i < corner_count; ++i)
        {
            join(p_shape.corner_approx(i), p_layer);
        }
    }

    /**
     * get the marking octagon on layer p_layer
     */
    public IntOctagon get_area (int p_layer)
    {

        return arr[p_layer].to_int();
    }

    public IntBox surrounding_box()
    {
        int llx = Integer.MAX_VALUE;
        int lly = Integer.MAX_VALUE;
        int urx = Integer.MIN_VALUE;
        int ury = Integer.MIN_VALUE;
        for (int i = 0; i < layer_count; ++i)
        {
            MutableOctagon curr = arr[i];
            llx = Math.min (llx, (int)Math.floor(curr.lx));
            lly = Math.min (lly, (int)Math.floor(curr.ly));
            urx = Math.max (urx, (int)Math.ceil(curr.rx));
            ury = Math.max (ury, (int)Math.ceil(curr.uy));
        }
        if (llx > urx || lly > ury)
        {
            return IntBox.EMPTY;
        }
        return new IntBox(llx, lly, urx, ury);
    }

    /**
     * inizialises the marking octagon on p_layer to empty
     */
    void set_empty(int p_layer)
    {
        arr[p_layer].set_empty();
    }

    final int layer_count;
    MutableOctagon [] arr;

    /**
     * mutable octagon with double coordinates (see geometry.planar.IntOctagon)
     */
    private static class MutableOctagon
    {
        double lx;
        double ly;
        double rx;
        double uy;
        double ulx;
        double lrx;
        double llx;
        double urx;
        
        void set_empty()
        {
            lx = Integer.MAX_VALUE;
            ly = Integer.MAX_VALUE;
            rx = Integer.MIN_VALUE;
            uy = Integer.MIN_VALUE;
            ulx = Integer.MAX_VALUE;
            lrx = Integer.MIN_VALUE;
            llx = Integer.MAX_VALUE;
            urx =  Integer.MIN_VALUE;
        }
        /**
         * calculates the smallest IntOctagon containing this octagon.
         */
        IntOctagon to_int()
        {
            if (rx < lx || uy < ly || lrx < ulx || urx < llx)
            {
                return IntOctagon.EMPTY;
            }
            return new IntOctagon ((int)Math.floor(lx), (int)Math.floor(ly),
                                   (int)Math.ceil(rx), (int)Math.ceil(uy),
                                   (int)Math.floor(ulx), (int)Math.ceil(lrx),
                                   (int)Math.floor(llx), (int)Math.ceil(urx));
        }
    }
}