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

package boardgraphics;

import geometry.planar.FloatPoint;
import geometry.planar.IntBox;
import geometry.planar.Limits;

import java.awt.Dimension;
import java.awt.geom.Point2D;

/**
 * Transformation function between the board and the screen coordinate systems.
 *
 * @author Alfons Wirtz
 */


public class CoordinateTransform implements java.io.Serializable
{
    public CoordinateTransform(IntBox p_design_box, Dimension p_panel_bounds )
    {
        this.screen_bounds = p_panel_bounds;
        this.design_box = p_design_box;
        this.rotation_pole = p_design_box.centre_of_gravity();
        
        int min_ll = Math.min(p_design_box.ll.x, p_design_box.ll.y);
        int max_ur = Math.max(p_design_box.ur.x, p_design_box.ur.y);
        if (Math.max(Math.abs(min_ll), Math.abs(max_ur)) <= 0.3 * Limits.CRIT_INT)
        {
            // create an offset to p_design_box to enable deep zoom out
            double design_offset = Math.max(p_design_box.width(), p_design_box.height());
            design_box_with_offset = p_design_box.offset(design_offset);
        }
        else
        {
            // no offset because of danger of integer overflow
            design_box_with_offset = p_design_box;
        }
        
        double x_scale_factor = screen_bounds.getWidth()/design_box_with_offset.width();
        double y_scale_factor = screen_bounds.getHeight()/design_box_with_offset.height();
        
        scale_factor = Math.min(x_scale_factor, y_scale_factor) ;
        display_x_offset = scale_factor * design_box_with_offset.ll.x;
        display_y_offset = scale_factor * design_box_with_offset.ll.y ;
    }
    
    /** Copy constructor */
    public CoordinateTransform(CoordinateTransform p_coordinate_transform)
    {
        this.screen_bounds = new Dimension(p_coordinate_transform.screen_bounds);
        this.design_box = new IntBox(p_coordinate_transform.design_box.ll, p_coordinate_transform.design_box.ur);
        this.rotation_pole = new FloatPoint(p_coordinate_transform.rotation_pole.x, p_coordinate_transform.rotation_pole.y);
        this.design_box_with_offset = new IntBox(p_coordinate_transform.design_box_with_offset.ll, p_coordinate_transform.design_box_with_offset.ur);
        this.scale_factor = p_coordinate_transform.scale_factor;
        this.display_x_offset = p_coordinate_transform.display_x_offset;
        this.display_y_offset = p_coordinate_transform.display_y_offset;
        this.mirror_left_right = p_coordinate_transform.mirror_left_right;
        this.mirror_top_bottom = p_coordinate_transform.mirror_top_bottom;
        this.rotation = p_coordinate_transform.rotation;
    }
    
    /**
     * scale a value from the board to the screen coordinate system
     */
    public double board_to_screen(double p_val)
    {
        return p_val * scale_factor ;
    }
    
    /**
     * scale a value the screen to the board coordinate system
     */
    public double screen_to_board(double p_val)
    {
        return p_val / scale_factor;
    }
    
    
    /**
     * transform a geometry.planar.FloatPoint to a java.awt.geom.Point2D
     */
    public Point2D board_to_screen(FloatPoint p_point)
    {
        FloatPoint rotated_point = p_point.rotate(this.rotation, this.rotation_pole);
        
        double x, y;
        if (this.mirror_left_right)
        {
            x =  (design_box_with_offset.width() - rotated_point.x - 1) * scale_factor + display_x_offset;
        }
        else
        {
            x = rotated_point.x * scale_factor - display_x_offset;
        }
        if (this.mirror_top_bottom)
        {
            y = (design_box_with_offset.height() - rotated_point.y - 1) * scale_factor + display_y_offset;
        }
        else
        {
            y = rotated_point.y * scale_factor - display_y_offset;
        }
        return new Point2D.Double(x, y);
    }
    
    /**
     * Transform a java.awt.geom.Point2D to a geometry.planar.FloatPoint
     */
    public FloatPoint screen_to_board(Point2D p_point)
    {
        double x, y;
        if (this.mirror_left_right)
        {
            x = design_box_with_offset.width() -(p_point.getX() - display_x_offset) / scale_factor - 1;
        }
        else
        {
            x =  (p_point.getX() + display_x_offset)/ scale_factor;
        }
        if (this.mirror_top_bottom)
        {
            y = design_box_with_offset.height() -(p_point.getY() - display_y_offset) / scale_factor - 1;
        }
        else
        {
            y =  (p_point.getY() + display_y_offset)/ scale_factor;
        }
        FloatPoint result = new FloatPoint(x, y);
        return result.rotate(-this.rotation, this.rotation_pole);
    }
    
    /**
     * Transforms an angle in radian on the board to an angle on the screen.
     */
    public double board_to_screen_angle(double p_angle)
    {
        double result = p_angle + this.rotation;
        if (this.mirror_left_right)
        {
            result = Math.PI - result;
        }
        if (this.mirror_top_bottom)
        {
            result = -result;
        }
        while (result >= 2 * Math.PI)
        {
            result -= 2 * Math.PI;
        }
        while (result < 0)
        {
            result += 2 * Math.PI;
        }
        return result;
    }
    
    /**
     * Transform a geometry.planar.IntBox to a java.awt.Rectangle
     * If the internal rotation is not a multiple of Pi/2, a bounding rectangle of the
     * rotated rectangular shape is returned.
     */
    public java.awt.Rectangle board_to_screen(IntBox p_box)
    {
        Point2D corner_1 = board_to_screen(p_box.ll.to_float());
        Point2D corner_2 = board_to_screen(p_box.ur.to_float());
        double ll_x = Math.min(corner_1.getX(), corner_2.getX());
        double ll_y = Math.min(corner_1.getY(), corner_2.getY());
        double dx = Math.abs(corner_2.getX() - corner_1.getX());
        double dy = Math.abs(corner_2.getY() - corner_1.getY());
        java.awt.Rectangle result =
                new java.awt. Rectangle((int) Math.floor(ll_x), (int) Math.floor(ll_y),
                (int) Math.ceil(dx), (int) Math.ceil(dy));
        return result;
    }
    
    /**
     * Transform a java.awt.Rectangle to a geometry.planar.IntBox
     * If the internal rotation is not a multiple of Pi/2, a bounding box of the
     * rotated rectangular shape is returned.
     */
    public IntBox screen_to_board(java.awt.Rectangle p_rect)
    {
        FloatPoint corner_1 = screen_to_board(new Point2D.Double(p_rect.getX(), p_rect.getY()));
        FloatPoint corner_2 = screen_to_board(new Point2D.Double(p_rect.getX() + p_rect.getWidth(),
                p_rect.getY() + p_rect.getHeight()));
        int llx = (int) Math.floor(Math.min(corner_1.x, corner_2.x));
        int lly = (int) Math.floor(Math.min(corner_1.y, corner_2.y));
        int urx = (int) Math.ceil(Math.max(corner_1.x, corner_2.x));
        int ury = (int) Math.ceil(Math.max(corner_1.y, corner_2.y));
        return new IntBox(llx, lly, urx, ury);
    }
    
    /**
     * If p_value is true, the left side and the right side of the board will be swapped.
     */
    public void set_mirror_left_right(boolean p_value)
    {
        mirror_left_right = p_value;
    }
    
    /**
     * Returns, if the left side and the right side of the board are swapped.
     */
    public boolean is_mirror_left_right()
    {
        return mirror_left_right;
    }
    
    /**
     * If p_value is true, the top side and the botton side of the board will be swapped.
     */
    public void set_mirror_top_bottom(boolean p_value)
    {
        // Because the origin of display is the upper left corner, the internal value
        // will be opposite to the input value of this function.
        mirror_top_bottom = !p_value;
    }
    
    /**
     * Returns, if the top side and the botton side of the board are swapped.
     */
    public boolean is_mirror_top_bottom()
    {
        // Because the origin of display is the upper left corner, the internal value
        // is opposite to the result of this function.
        return !mirror_top_bottom;
    }
    
    /**
     * Sets the rotation of the displayed board to p_value.
     */
    public void set_rotation(double p_value)
    {
        rotation = p_value;
    }
    
    /**
     * Returns the rotation of the displayed board.
     */
    public double get_rotation()
    {
        return rotation;
    }
    
    /**
     * Returns the internal rotation snapped to the nearest multiple of 90 degree.
     * The result will be 0, 1, 2 or 3.
     */
    public int get_90_degree_rotation()
    {
        int multiple =  (int) Math.round(Math.toDegrees(rotation) / 90.0);
        while (multiple < 0)
        {
            multiple += 4;
        }
        while (multiple >= 4)
        {
            multiple -= 4;
        }
        return multiple;
    }
    
    final IntBox design_box;
    final IntBox design_box_with_offset ;
    final Dimension screen_bounds ;
    private final double scale_factor ;
    private final double display_x_offset;
    private final double display_y_offset;
    
    /**
     * Left side and right side of the board are swapped.
     */
    private boolean mirror_left_right = false;
    
    /**
     * Top side and bottom  side of the board are swapped.
     */
    private boolean mirror_top_bottom = true;
    
    private double rotation = 0;
    
    private FloatPoint rotation_pole;
}