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
 * DrillPageArray.java
 *
 * Created on 26. Maerz 2006, 06:54
 *
 */

package autoroute;

import java.util.Collection;
import java.util.LinkedList;

import geometry.planar.IntBox;
import geometry.planar.TileShape;

import board.RoutingBoard;

/**
 * Describes the 2 dimensional array of pages of ExpansionDrill`s used in the maze search algorithm.
 * The pages are rectangles of about equal width and height covering covering the bounding box of the board area.
 *
 * @author Alfons Wirtz
 */
public class DrillPageArray
{
    
    /** Creates a new instance of DrillPageArray */
    public DrillPageArray(RoutingBoard p_board, int p_max_page_width)
    {
        this.bounds = p_board.bounding_box;
        double length = bounds.ur.x - bounds.ll.x;
        double height = bounds.ur.y - bounds.ll.y;
        this.COLUMN_COUNT = (int) Math.ceil(length / p_max_page_width);
        this.ROW_COUNT =  (int) Math.ceil(height/ p_max_page_width);
        this.PAGE_WIDTH = (int) Math.ceil(length / COLUMN_COUNT);
        this.PAGE_HEIGHT = (int) Math.ceil(height / ROW_COUNT);
        this.page_arr = new DrillPage[ROW_COUNT] [COLUMN_COUNT];
        for (int j = 0; j < this.ROW_COUNT; ++j)
        {
            for (int i = 0; i < this.COLUMN_COUNT; ++i)
            {
                int ll_x = bounds.ll.x + i * PAGE_WIDTH;
                int ur_x;
                if (i == COLUMN_COUNT - 1)
                {
                    ur_x = bounds.ur.x;
                }
                else
                {
                    ur_x = ll_x + PAGE_WIDTH;
                }
                int ll_y = bounds.ll.y + j * PAGE_HEIGHT;
                int ur_y;
                if (j == ROW_COUNT - 1)
                {
                    ur_y = bounds.ur.y;
                }
                else
                {
                    ur_y = ll_y + PAGE_HEIGHT;
                }
                page_arr [j] [i] = new DrillPage(new IntBox(ll_x, ll_y, ur_x, ur_y), p_board);
            }
        }
    }
    
    /**
     * Invalidates all drill pages intersecting with p_shape, so the they must be recalculated at the next
     * call of get_ddrills()
     */
    public void  invalidate(TileShape p_shape)
    {
        Collection<DrillPage> overlaps = overlapping_pages( p_shape);
        for (DrillPage curr_page : overlaps)
        {
            curr_page.invalidate();
        }
    }
    
    /**
     * Collects all drill pages with a 2-dimensional overlap with p_shape.
     */
    public Collection<DrillPage> overlapping_pages(TileShape p_shape)
    {
        Collection<DrillPage> result = new LinkedList<DrillPage>();
        
        IntBox shape_box = p_shape.bounding_box().intersection(this.bounds);
        
        int min_j = (int) Math.floor(((double)(shape_box.ll.y - bounds.ll.y))/ (double) PAGE_HEIGHT);
        double max_j = ((double) (shape_box.ur.y - bounds.ll.y)) / (double) PAGE_HEIGHT;
        
        int min_i = (int) Math.floor(((double) (shape_box.ll.x - bounds.ll.x))/ (double) PAGE_WIDTH );
        double max_i = ((double) (shape_box.ur.x - bounds.ll.x)) / (double) PAGE_WIDTH;
        
        for (int j = min_j; j < max_j; ++j)
        {
            for (int i = min_i; i < max_i; ++i)
            {
                DrillPage curr_page = this.page_arr[j] [i];
                TileShape intersection = p_shape.intersection(curr_page.shape);
                if (intersection.dimension() > 1)
                {
                    result.add(this.page_arr[j] [i]);
                }
            }
        }
        return result;
    }
    
    /**
     * Resets all drill pages for autorouting the next connection.
     */
    public void reset()
    {
        for (int j = 0; j < page_arr.length; ++j)
        {
            DrillPage [] curr_row = page_arr[j];
            for (int i = 0; i < curr_row.length; ++i)
            {
                curr_row[i].reset();
            }
        }
    }
    
   /*
    * Test draw of the all drills
    */
    public void draw(java.awt.Graphics p_graphics, boardgraphics.GraphicsContext p_graphics_context, double p_intensity)
    {
        for (int j = 0; j < page_arr.length; ++j)
        {
            DrillPage [] curr_row = page_arr[j];
            for (int i = 0; i < curr_row.length; ++i)
            {
                curr_row[i].draw(p_graphics, p_graphics_context, p_intensity);
            }
        }
    }
    
    private final IntBox bounds;
    
    /**
     * The number of colums in the array.
     */
    private final int COLUMN_COUNT;
    
    /**
     * The number of rows in the array.
     */
    private final int ROW_COUNT;
    
    /**
     * The width of a single page in this array.
     */
    private final int PAGE_WIDTH;
    
    /**
     * The height of a single page in this array.
     */
    private final int PAGE_HEIGHT;
    
    private final DrillPage [][] page_arr;
}
