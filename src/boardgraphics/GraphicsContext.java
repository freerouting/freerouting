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

import geometry.planar.Area;
import geometry.planar.Circle;
import geometry.planar.Ellipse;
import geometry.planar.FloatPoint;
import geometry.planar.IntBox;
import geometry.planar.PolylineShape;
import geometry.planar.Shape;
import geometry.planar.TileShape;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.AffineTransform;


/**
 * Context for drawing items in the board package to the screen.
 *
 * @author Alfons Wirtz
 */


public class GraphicsContext implements java.io.Serializable
{
    public GraphicsContext( IntBox p_design_bounds,
            Dimension p_panel_bounds, board.LayerStructure p_layer_structure, java.util.Locale p_locale)
    {
        coordinate_transform = new CoordinateTransform(p_design_bounds, p_panel_bounds);
        item_color_table = new ItemColorTableModel(p_layer_structure, p_locale);
        other_color_table = new OtherColorTableModel(p_locale);
        color_intensity_table = new ColorIntensityTable();
        layer_visibility_arr = new double [p_layer_structure.arr.length];
        for (int i = 0; i < layer_visibility_arr.length; ++i)
        {
            if (p_layer_structure.arr[i].is_signal)
            {
                layer_visibility_arr [i] = 1;
            }
            else
            {
                layer_visibility_arr [i] = 0;
            }
        }
    }
    
    /**
     * Copy constructor
     */
    public GraphicsContext(GraphicsContext p_graphics_context)
    {
        this.coordinate_transform = new CoordinateTransform(p_graphics_context.coordinate_transform);
        this.item_color_table = new ItemColorTableModel(p_graphics_context.item_color_table);
        this.other_color_table = new OtherColorTableModel(p_graphics_context.other_color_table);
        this.color_intensity_table = new ColorIntensityTable(p_graphics_context.color_intensity_table);
        this.layer_visibility_arr = p_graphics_context.copy_layer_visibility_arr();
    }
    
    /**
     *  Changes  the bounds of the board design to p_design_bounds.
     *  Useful when components are still placed outside the boaed.
     */
    public void change_design_bounds(IntBox p_new_design_bounds)
    {
        if (p_new_design_bounds.equals(this.coordinate_transform.design_box))
        {
            return;
        }
        Dimension screen_bounds = this.coordinate_transform.screen_bounds;
        this.coordinate_transform = new CoordinateTransform(p_new_design_bounds, screen_bounds);
    }
    
    /**
     * changes the size of the panel to p_new_bounds
     */
    public void change_panel_size(Dimension p_new_bounds)
    {
        if (coordinate_transform == null)
        {
            return;
        }
        IntBox design_box = coordinate_transform.design_box;
        boolean left_right_swapped = coordinate_transform.is_mirror_left_right();
        boolean top_bottom_swapped = coordinate_transform.is_mirror_top_bottom();
        double rotation = coordinate_transform.get_rotation();
        coordinate_transform = new CoordinateTransform(design_box, p_new_bounds);
        coordinate_transform.set_mirror_left_right(left_right_swapped);
        coordinate_transform.set_mirror_top_bottom(top_bottom_swapped);
        coordinate_transform.set_rotation(rotation);
    }
    
    /**
     * draws a polygon with corners p_points
     */
    public void draw(FloatPoint[] p_points, double p_half_width, Color p_color, Graphics p_g, double p_translucency_factor)
    {
        if (p_color == null)
        {
            return;
        }
        Graphics2D g2 = (Graphics2D)p_g;
        Rectangle clip_shape = (Rectangle)p_g.getClip() ;
        // the class member update_box cannot be used here, because
        // the dirty rectangle is internally enlarged by the system.
        // Therefore we can not improve the performance by using an
        // update octagon instead of a box.
        IntBox clip_box = coordinate_transform.screen_to_board(clip_shape);
        double scaled_width = coordinate_transform.board_to_screen(p_half_width);
        
        init_draw_graphics(g2, p_color, (float)scaled_width*2);
        set_translucency(g2, p_translucency_factor);
        
        GeneralPath draw_path = null;
        if (!show_line_segments)
        {
            draw_path = new GeneralPath();
        }
        
        for(int i= 0; i<(p_points.length-1); i++)
        {
            if (line_outside_update_box(p_points[i], p_points[i + 1],
                    p_half_width + update_offset, clip_box))
            {
                // this check should be unnessersary here,
                // the system should do it in the draw(line) function
                continue;
            }
            Point2D p1 = coordinate_transform.board_to_screen(p_points[i]) ;
            Point2D p2 = coordinate_transform.board_to_screen(p_points[i+1]) ;
            Line2D line = new Line2D.Double(p1, p2) ;
            
            if (show_line_segments)
            {
                g2.draw(line);
            }
            else
            {
                draw_path.append(line, false);
            }
        }
        if (!show_line_segments)
        {
            g2.draw(draw_path);
        }
    }
    
    /*
     * draws the boundary of a circle
     */
    public void draw_circle(FloatPoint p_center, double p_radius,  double p_draw_half_width,
            Color p_color, Graphics p_g, double p_translucency_factor)
    {
        if (p_color == null)
        {
            return;
        }
        Graphics2D g2 = (Graphics2D)p_g;
        Point2D center = coordinate_transform.board_to_screen(p_center);
        
        double radius = coordinate_transform.board_to_screen(p_radius);
        double diameter = 2 * radius;
        float draw_width = (float)(2 * coordinate_transform.board_to_screen(p_draw_half_width));
        Ellipse2D circle =
                new Ellipse2D.Double(center.getX() - radius,center.getY() - radius, diameter, diameter);
        set_translucency(g2, p_translucency_factor);
        init_draw_graphics(g2, p_color, draw_width);
        g2.draw(circle);
    }
    
     /*
      * draws a rectangle
      */
    public void draw_rectangle(FloatPoint p_corner1, FloatPoint p_corner2,
            double p_draw_half_width, Color p_color,  Graphics p_g, double p_translucency_factor)
    {
        if (p_color == null)
        {
            return;
        }
        Graphics2D g2 = (Graphics2D)p_g ;
        Point2D corner1 = coordinate_transform.board_to_screen(p_corner1) ;
        Point2D corner2 = coordinate_transform.board_to_screen(p_corner2) ;
        
        double xmin = Math.min(corner1.getX(), corner2.getX()) ;
        double ymin = Math.min(corner1.getY(), corner2.getY()) ;
        
        float draw_width = (float)(2 * coordinate_transform.board_to_screen(p_draw_half_width)) ;
        double width = Math.abs(corner2.getX() - corner1.getX()) ;
        double height = Math.abs(corner2.getY() - corner1.getY()) ;
        Rectangle2D rectangle = new Rectangle2D.Double(xmin, ymin, width, height) ;
        set_translucency(g2, p_translucency_factor);
        init_draw_graphics(g2, p_color, draw_width) ;
        g2.draw(rectangle) ;
    }
    
    /**
     * Draws the boundary of p_shape.
     */
    public void draw_boundary(Shape p_shape, double p_draw_half_width, Color p_color,  Graphics p_g, double p_translucency_factor)
    {
        if (p_shape instanceof PolylineShape)
        {
            FloatPoint[] draw_corners = p_shape.corner_approx_arr();
            if (draw_corners.length <= 1)
            {
                return;
            }
            FloatPoint[] closed_draw_corners = new FloatPoint[draw_corners.length + 1];
            System.arraycopy(draw_corners, 0, closed_draw_corners, 0, draw_corners.length);
            closed_draw_corners[closed_draw_corners.length - 1] = draw_corners [0];
            this.draw( closed_draw_corners, p_draw_half_width, p_color, p_g, p_translucency_factor);
        }
        else if (p_shape instanceof Circle)
        {
            Circle curr_circle = (Circle) p_shape;
            this.draw_circle(curr_circle.center.to_float(), curr_circle.radius, p_draw_half_width,
                    p_color, p_g, p_translucency_factor);
        }
    }
    
    /**
     * Draws the boundary of p_area.
     */
    public void draw_boundary(Area p_area, double p_draw_half_width, Color p_color,  Graphics p_g, double p_translucency_factor)
    {
        draw_boundary(p_area.get_border(), p_draw_half_width, p_color, p_g, p_translucency_factor);
        Shape[] holes = p_area.get_holes();
        for (int i = 0; i < holes.length; ++i)
        {
            draw_boundary(holes[i], p_draw_half_width, p_color, p_g, p_translucency_factor);
        }
    }
    
    /**
     * Draws the interiour of a circle
     */
    public void fill_circle(Circle p_circle, Graphics p_g, Color p_color, double p_translucency_factor)
    {
        if (p_color == null)
        {
            return;
        }
        Point2D center = coordinate_transform.board_to_screen(p_circle.center.to_float());
        double radius = coordinate_transform.board_to_screen(p_circle.radius);
        if (!point_near_rectangle(center.getX(), center.getY(), (Rectangle)p_g.getClip(), radius))
        {
            return;
        }
        double diameter = 2 * radius;
        Ellipse2D circle =
                new Ellipse2D.Double(center.getX() - radius,center.getY() - radius, diameter, diameter);
        Graphics2D g2 = (Graphics2D)p_g;
        g2.setColor(p_color);
        set_translucency(g2, p_translucency_factor);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.fill(circle);
    }
    
    /**
     * Draws the interiour of an ellipse.
     */
    public void fill_ellipse(Ellipse  p_ellipse, Graphics p_g, Color p_color, double p_translucency_factor)
    {
        Ellipse []  ellipse_arr = new Ellipse[1];
        ellipse_arr[0] = p_ellipse;
        fill_ellipse_arr(ellipse_arr, p_g, p_color, p_translucency_factor);
    }
    
    
    /**
     * Draws the interiour of an array  of ellipses.
     * Ellipses contained in an other ellipse are treated as holes.
     */
    public void fill_ellipse_arr(Ellipse []  p_ellipse_arr, Graphics p_g, Color p_color, double p_translucency_factor)
    {
        if (p_color == null || p_ellipse_arr.length <= 0)
        {
            return;
        }
        GeneralPath draw_path = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
        for (Ellipse curr_ellipse : p_ellipse_arr)
        {
            Point2D center = coordinate_transform.board_to_screen(curr_ellipse.center);
            double bigger_radius = coordinate_transform.board_to_screen(curr_ellipse.bigger_radius);
            if (!point_near_rectangle(center.getX(), center.getY(), (Rectangle)p_g.getClip(), bigger_radius))
            {
                continue;
            }
            double smaller_radius =  coordinate_transform.board_to_screen(curr_ellipse.smaller_radius);
            Ellipse2D draw_ellipse =
                    new Ellipse2D.Double(center.getX() - bigger_radius,center.getY() - smaller_radius,
                    2 * bigger_radius, 2 * smaller_radius);
            double rotation = coordinate_transform.board_to_screen_angle(curr_ellipse.rotation);
            AffineTransform affine_transform = new  AffineTransform();
            affine_transform.rotate(rotation, center.getX(), center.getY());
            java.awt.Shape rotated_ellipse = affine_transform.createTransformedShape(draw_ellipse);
            draw_path.append(rotated_ellipse, false);
        }
        Graphics2D g2 = (Graphics2D)p_g;
        g2.setColor(p_color);
        set_translucency(g2, p_translucency_factor);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.fill(draw_path);
    }
    
    /**
     * Checks, if the distance of the point with coordinates p_x, p_y to p_rect ist at most p_dist.
     */
    private boolean point_near_rectangle(double p_x, double p_y, Rectangle p_rect, double p_dist)
    {
        if (p_x < p_rect.x - p_dist)
        {
            return false;
        }
        if (p_y < p_rect.y - p_dist)
        {
            return false;
        }
        if (p_x > p_rect.x + p_rect.width + p_dist)
        {
            return false;
        }
        if (p_y > p_rect.y + p_rect.height + p_dist)
        {
            return false;
        }
        return true;
    }
    
    /**
     * Fill the interior of the polygon shape  represented by p_points.
     */
    public void fill_shape(FloatPoint[] p_points, Graphics p_g, Color p_color, double p_translucency_factor)
    {
        if (p_color == null)
        {
            return;
        }
        Graphics2D g2 = (Graphics2D)p_g;
        Polygon draw_polygon = new Polygon();
        for(int i= 0; i < p_points.length; i++)
        {
            Point2D curr_corner = coordinate_transform.board_to_screen(p_points[i]);
            draw_polygon.addPoint((int)Math.round(curr_corner.getX()),
                    (int)Math.round(curr_corner.getY()));
        }
        g2.setColor(p_color);
        set_translucency(g2, p_translucency_factor);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.fill(draw_polygon);
    }
    
    /**
     * Fill the interiour of a list of polygons.
     * Used for example with an area consisting of a border polygon and some holes.
     */
    public void fill_area(FloatPoint[][] p_point_lists, Graphics p_g, Color p_color, double p_translucency_factor)
    {
        if (p_color == null)
        {
            return;
        }
        GeneralPath draw_path = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
        for (int j = 0; j < p_point_lists.length; ++j)
        {
            Polygon draw_polygon = new Polygon();
            FloatPoint[] curr_point_list = p_point_lists[j];
            for(int i= 0; i < curr_point_list.length; i++)
            {
                Point2D curr_corner = coordinate_transform.board_to_screen(curr_point_list[i]);
                draw_polygon.addPoint((int)Math.round(curr_corner.getX()),
                        (int)Math.round(curr_corner.getY()));
            }
            draw_path.append(draw_polygon, false);
        }
        Graphics2D g2 = (Graphics2D)p_g;
        g2.setColor(p_color);
        set_translucency(g2, p_translucency_factor);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.fill(draw_path);
    }
    
    /**
     * draws the interiour of an item of class geometry.planar.Area
     */
    public void fill_area(Area p_area, Graphics p_g, Color p_color, double p_translucency_factor)
    {
        if (p_color == null || p_area.is_empty())
        {
            return;
        }
        if (p_area instanceof Circle)
        {
            fill_circle((Circle) p_area, p_g, p_color, p_translucency_factor);
        }
        else
        {
            PolylineShape border = (PolylineShape) p_area.get_border();
            if (!border.is_bounded())
            {
                System.out.println("GraphicsContext.fill_area: shape not bounded");
                return;
            }
            Rectangle clip_shape = (Rectangle)p_g.getClip() ;
            IntBox clip_box = coordinate_transform.screen_to_board(clip_shape);
            if (!border.bounding_box().intersects(clip_box))
            {
                return;
            }
            Shape [] holes = p_area.get_holes();
            
            FloatPoint[] [] draw_polygons = new FloatPoint [holes.length + 1][];
            for (int j = 0; j < draw_polygons.length; ++j)
            {
                PolylineShape curr_draw_shape;
                if (j == 0)
                {
                    curr_draw_shape = border;
                }
                else
                {
                    curr_draw_shape = (PolylineShape) holes[j - 1];
                }
                draw_polygons[j] = new FloatPoint [curr_draw_shape.border_line_count() + 1];
                FloatPoint curr_draw_polygon[] = draw_polygons[j];
                for (int i = 0; i <  curr_draw_polygon.length - 1; ++i)
                {
                    curr_draw_polygon[i] = curr_draw_shape.corner_approx(i);
                }
                // close the polygon
                curr_draw_polygon[curr_draw_polygon.length - 1] = curr_draw_polygon[0];
            }
            fill_area(draw_polygons, p_g, p_color, p_translucency_factor) ;
        }
        if (show_area_division)
        {
            TileShape[] tiles = p_area.split_to_convex();
            for (int i = 0; i < tiles.length; ++i)
            {
                FloatPoint[] corners = new FloatPoint [tiles[i].border_line_count() + 1];
                TileShape curr_tile = tiles[i];
                for (int j = 0; j < corners.length - 1; ++j)
                {
                    corners[j]= curr_tile.corner_approx(j);
                }
                corners[corners.length - 1] = corners[0];
                draw(corners, 1, java.awt.Color.white, p_g, 0.7);
            }
        }
    }
    
    public Color get_background_color()
    {
        return other_color_table.get_background_color();
    }
    
    public Color get_hilight_color()
    {
        return other_color_table.get_hilight_color();
    }
    
    public Color get_incomplete_color()
    {
        return other_color_table.get_incomplete_color();
    }
    
    public Color get_outline_color()
    {
        return other_color_table.get_outline_color();
    }
    
    public Color get_component_color(boolean p_front)
    {
        return other_color_table.get_component_color(p_front);
    }
    
    public Color get_violations_color()
    {
        return other_color_table.get_violations_color();
    }
    
    public Color get_length_matching_area_color()
    {
        return other_color_table.get_length_matching_area_color();
    }
    
    public Color[] get_trace_colors(boolean p_fixed)
    {
        
        return item_color_table.get_trace_colors(p_fixed);
    }
    
    public Color[] get_via_colors(boolean p_fixed)
    {
        return item_color_table.get_via_colors(p_fixed);
    }
    
    public Color[] get_pin_colors()
    {
        return item_color_table.get_pin_colors();
    }
    
    public Color[] get_conduction_colors()
    {
        return item_color_table.get_conduction_colors();
    }
    
    public Color[] get_obstacle_colors()
    {
        return item_color_table.get_obstacle_colors();
    }
    
    public Color[] get_via_obstacle_colors()
    {
        return item_color_table.get_via_obstacle_colors();
    }
    
    public Color[] get_place_obstacle_colors()
    {
        return item_color_table.get_place_obstacle_colors();
    }
    
    public double get_trace_color_intensity()
    {
        return color_intensity_table.get_value(ColorIntensityTable.ObjectNames.TRACES.ordinal());
    }
    
    public double get_via_color_intensity()
    {
        return color_intensity_table.get_value(ColorIntensityTable.ObjectNames.VIAS.ordinal());
    }
    
    public double get_pin_color_intensity()
    {
        return color_intensity_table.get_value(ColorIntensityTable.ObjectNames.PINS.ordinal());
    }
    
    public double get_conduction_color_intensity()
    {
        return color_intensity_table.get_value(ColorIntensityTable.ObjectNames.CONDUCTION_AREAS.ordinal());
    }
    
    public double get_obstacle_color_intensity()
    {
        return color_intensity_table.get_value(ColorIntensityTable.ObjectNames.KEEPOUTS.ordinal());
    }
    
    public double get_via_obstacle_color_intensity()
    {
        return color_intensity_table.get_value(ColorIntensityTable.ObjectNames.VIA_KEEPOUTS.ordinal());
    }
    
    public double get_place_obstacle_color_intensity()
    {
        return color_intensity_table.get_value(ColorIntensityTable.ObjectNames.PLACE_KEEPOUTS.ordinal());
    }
    
    public double get_component_outline_color_intensity()
    {
        return color_intensity_table.get_value(ColorIntensityTable.ObjectNames.COMPONENT_OUTLINES.ordinal());
    }
    
    public double get_hilight_color_intensity()
    {
        return color_intensity_table.get_value(ColorIntensityTable.ObjectNames.HILIGHT.ordinal());
    }
    
    public double get_incomplete_color_intensity()
    {
        return color_intensity_table.get_value(ColorIntensityTable.ObjectNames.INCOMPLETES.ordinal());
    }
    
    public double get_length_matching_area_color_intensity()
    {
        return color_intensity_table.get_value(ColorIntensityTable.ObjectNames.LENGTH_MATCHING_AREAS.ordinal());
    }
    
    public void set_trace_color_intensity(double p_value)
    {
        color_intensity_table.set_value(ColorIntensityTable.ObjectNames.TRACES.ordinal(), p_value);
    }
    
    public void set_via_color_intensity(double p_value)
    {
        color_intensity_table.set_value(ColorIntensityTable.ObjectNames.VIAS.ordinal(), p_value);
    }
    
    public void set_pin_color_intensity(double p_value)
    {
        color_intensity_table.set_value(ColorIntensityTable.ObjectNames.PINS.ordinal(), p_value);
    }
    
    public void set_conduction_color_intensity(double p_value)
    {
        color_intensity_table.set_value(ColorIntensityTable.ObjectNames.CONDUCTION_AREAS.ordinal(), p_value);
    }
    
    public void set_obstacle_color_intensity(double p_value)
    {
        color_intensity_table.set_value(ColorIntensityTable.ObjectNames.KEEPOUTS.ordinal(), p_value);
    }
    
    public void set_via_obstacle_color_intensity(double p_value)
    {
        color_intensity_table.set_value(ColorIntensityTable.ObjectNames.VIA_KEEPOUTS.ordinal(), p_value);
    }
    
    public void set_hilight_color_intensity(double p_value)
    {
        color_intensity_table.set_value(ColorIntensityTable.ObjectNames.HILIGHT.ordinal(), p_value);
    }
    
    public void set_incomplete_color_intensity(double p_value)
    {
        color_intensity_table.set_value(ColorIntensityTable.ObjectNames.INCOMPLETES.ordinal(), p_value);
    }
    
    public void set_length_matching_area_color_intensity(double p_value)
    {
        color_intensity_table.set_value(ColorIntensityTable.ObjectNames.LENGTH_MATCHING_AREAS.ordinal(), p_value);
    }
    
    public java.awt.Dimension get_panel_size()
    {
        return coordinate_transform.screen_bounds;
    }
    
    /**
     * Returns the center of the design on the screen.
     */
    public Point2D get_design_center()
    {
        FloatPoint center = coordinate_transform.design_box_with_offset.centre_of_gravity();
        return coordinate_transform.board_to_screen(center);
    }
    
    /**
     * Returns the bounding box of the design in screen coordinates.
     */
    public java.awt.Rectangle get_design_bounds()
    {
        return coordinate_transform.board_to_screen(coordinate_transform.design_box);
    }
    
    /**
     * Sets the factor for automatic layer dimming.
     * Values are between 0 and 1. If 1, there is no automatic layer dimming.
     */
    public void set_auto_layer_dim_factor(double p_value)
    {
        auto_layer_dim_factor = p_value;
    }
    
    /** gets the factor for automatic layer dimming */
    public double get_auto_layer_dim_factor()
    {
        return this.auto_layer_dim_factor;
    }
    
    /** Sets the layer, which will be excluded from automatic layer  dimming. */
    public void set_fully_visible_layer(int p_layer_no)
    {
        fully_visible_layer = p_layer_no;
    }
    
    /**
     * Gets the visibility factor of the input layer.
     * The result is between 0 and 1.
     * If the result is 0, the layer is invisible,
     * if the result is 1, the layer is fully visible.
     */
    public double get_layer_visibility(int p_layer_no)
    {
        double result;
        if (p_layer_no == this.fully_visible_layer)
        {
            result = layer_visibility_arr[p_layer_no];
        }
        else
        {
            result = this.auto_layer_dim_factor * layer_visibility_arr[p_layer_no];
        }
        return result;
    }
    
    /**
     * Gets the visibility factor of the input layer without the aoutomatic layer dimming.
     */
    public double get_raw_layer_visibility(int p_layer_no)
    {
        return layer_visibility_arr[p_layer_no];
    }
    
    /**
     * Gets the visibility factor of the input layer.
     * The value is expected between 0 and 1.
     * If the value is 0, the layer is invisible,
     * if the value is 1, the layer is fully visible.
     *
     */
    public void set_layer_visibility(int p_layer_no, double p_value)
    {
        layer_visibility_arr[p_layer_no] = Math.max(0, Math.min(p_value, 1));
    }
    
    public void set_layer_visibility_arr(double [] p_layer_visibility_arr)
    {
        this.layer_visibility_arr = p_layer_visibility_arr;
    }
    
    
    public double[] copy_layer_visibility_arr()
    {
        double[] result = new double [this.layer_visibility_arr.length];
        System.arraycopy(this.layer_visibility_arr, 0, result, 0, this.layer_visibility_arr.length);
        return result;
    }
    
    /** Returns the number of layers on the board */
    public int layer_count()
    {
        return layer_visibility_arr.length;
    }
    
    /**
     * filter lines, which cannot touch the update_box to improve the
     * performance of the draw function by avoiding unnessesary calls
     * of draw (line)
     */
    private boolean line_outside_update_box(FloatPoint p_1,
            FloatPoint p_2, double p_update_offset, IntBox p_update_box)
    {
        if (p_1 == null || p_2 == null)
        {
            return true;
        }
        if (Math.max(p_1.x, p_2.x) < p_update_box.ll.x - p_update_offset)
        {
            return true;
        }
        if (Math.max(p_1.y, p_2.y) < p_update_box.ll.y - p_update_offset )
        {
            return true;
        }
        if (Math.min(p_1.x, p_2.x) > p_update_box.ur.x + p_update_offset)
        {
            return true;
        }
        if (Math.min(p_1.y, p_2.y) > p_update_box.ur.y + p_update_offset)
        {
            return true;
        }
        return false;
    }
    
    /**
     * initialise some values in p_graphics
     */
    private static void init_draw_graphics(Graphics2D p_graphics, Color p_color, float p_width)
    {
        BasicStroke bs = new BasicStroke(Math.max(p_width, 0), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        p_graphics.setStroke(bs);
        p_graphics.setColor(p_color);
        p_graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }
    
    private static void set_translucency(Graphics2D p_g2, double p_factor)
    {
        AlphaComposite curr_alpha_composite;
        if (p_factor >= 0)
        {
            curr_alpha_composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float)  p_factor);
        }
        else
        {
            curr_alpha_composite = AlphaComposite.getInstance(AlphaComposite.DST_OVER, (float) -p_factor);
        }
        p_g2.setComposite(curr_alpha_composite);
    }
    
    /** 
     * Writes an instance of this class to a file.
     */
    private void writeObject(java.io.ObjectOutputStream p_stream)
            throws java.io.IOException
    {
        p_stream.defaultWriteObject();
        item_color_table.write_object(p_stream);
        other_color_table.write_object(p_stream);
    }
    
    /** Reads an instance of this class from a file */
    private void readObject(java.io.ObjectInputStream p_stream)
    throws java.io.IOException, java.lang.ClassNotFoundException
    {
        p_stream.defaultReadObject();
        this.item_color_table = new ItemColorTableModel(p_stream);
        this.other_color_table = new OtherColorTableModel(p_stream);
    }
    
    public transient ItemColorTableModel item_color_table;
    public transient OtherColorTableModel other_color_table;
    public ColorIntensityTable color_intensity_table;
    
        
    public CoordinateTransform coordinate_transform = null;
    
    /** layer_visibility_arr[i] is between 0 and 1, for each layer i, 0 is invisible and 1 fully visible. */
    private double [] layer_visibility_arr;
    
    
    /**
     * The factor for autoomatic layer dimming of layers different from the current layer.
     * Values are between 0 and 1. If 1, there is no automatic layer dimming.
     */
    private double auto_layer_dim_factor = 0.7;
    
    /** The layer, which is not automatically dimmed. */
    private int fully_visible_layer = 0;
    
    private static final int update_offset = 10000;
    
    private static final boolean show_line_segments = false;
    private static final boolean show_area_division = false;
    
}