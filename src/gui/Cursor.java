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
 * Cursor.java
 *
 * Created on 17. Maerz 2006, 06:52
 *
 */

package gui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Graphics;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Line2D;


/**
 *
 * @author Alfons Wirtz
 */
public abstract class Cursor
{
    
    public static Cursor get_45_degree_cross_hair_cursor()
    {
        return new FortyfiveDegreeCrossHairCursor();
    }
    
    public abstract void draw(Graphics p_graphics);
    
    public void set_location(Point2D p_location)
    {
        this.x_coor = p_location.getX();
        this.y_coor = p_location.getY();
        location_initialized = true;
    }
    
    protected static void init_graphics(Graphics2D p_graphics)
    {
        BasicStroke bs = new BasicStroke(0, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        p_graphics.setStroke(bs);
        p_graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        p_graphics.setColor(Color.WHITE);
        p_graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1));
    }
    
    double x_coor;
    double y_coor;
    
    boolean location_initialized = false;
    
    private static final double MAX_COOR = 1000;
    
    private static Line2D VERTICAL_LINE = new Line2D.Double(0, -MAX_COOR, 0, MAX_COOR);
    private static Line2D HORIZONTAL_LINE = new Line2D.Double(-MAX_COOR, 0, MAX_COOR, 0);
    private static Line2D RIGHT_DIAGONAL_LINE = new Line2D.Double(-MAX_COOR, -MAX_COOR, MAX_COOR, MAX_COOR);
    private static Line2D LEFT_DIAGONAL_LINE = new Line2D.Double(-MAX_COOR, MAX_COOR, MAX_COOR, -MAX_COOR);
    
    
    private static class FortyfiveDegreeCrossHairCursor extends Cursor
    {
        
        public void draw(Graphics p_graphics)
        {
            
            if (!location_initialized)
            {
                return;
            }
            Graphics2D g2 = (Graphics2D)p_graphics;
            init_graphics(g2);
            GeneralPath draw_path = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
            draw_path.append(VERTICAL_LINE, false);
            draw_path.append(HORIZONTAL_LINE, false);
            draw_path.append(RIGHT_DIAGONAL_LINE, false);
            draw_path.append(LEFT_DIAGONAL_LINE, false);
            g2.translate(this.x_coor, this.y_coor);
            g2.draw(draw_path);
        }
    }
    
}
