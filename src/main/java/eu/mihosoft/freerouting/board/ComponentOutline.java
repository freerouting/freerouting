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
 * ComponentOutline.java
 *
 * Created on 28. November 2005, 06:42
 *
 */
package board;

import java.awt.Color;

import geometry.planar.Area;
import geometry.planar.Vector;
import geometry.planar.Point;
import geometry.planar.IntPoint;
import geometry.planar.FloatPoint;

import boardgraphics.GraphicsContext;

/**
 *
 * @author Alfons Wirtz
 */
public class ComponentOutline extends Item implements java.io.Serializable
{

    /** Creates a new instance of ComponentOutline */
    public ComponentOutline(Area p_area, boolean p_is_front, Vector p_translation, double p_rotation_in_degree,
            int p_component_no, FixedState p_fixed_state, BasicBoard p_board)
    {
        super(new int[0], 0, 0, p_component_no, p_fixed_state, p_board);
        this.relative_area = p_area;
        this.is_front = p_is_front;
        this.translation = p_translation;
        this.rotation_in_degree = p_rotation_in_degree;
    }

    public Item copy(int p_id_no)
    {
        return new ComponentOutline(this.relative_area, this.is_front, this.translation, this.rotation_in_degree,
                this.get_component_no(), this.get_fixed_state(), this.board);
    }

    public boolean is_selected_by_filter(ItemSelectionFilter p_filter)
    {
        return false;
    }

    public int get_layer()
    {
        int result;
        if (this.is_front)
        {
            result = 0;
        }
        else
        {
            result = this.board.get_layer_count() - 1;
        }
        return result;
    }

    public int first_layer()
    {
        return get_layer();
    }

    public int last_layer()
    {
        return get_layer();
    }

    public boolean is_on_layer(int p_layer)
    {
        return get_layer() == p_layer;
    }

    public boolean is_obstacle(Item p_item)
    {
        return false;
    }

    public int shape_layer(int p_index)
    {
        return get_layer();
    }

    public int tile_shape_count()
    {
        return 0;
    }

    protected geometry.planar.TileShape[] calculate_tree_shapes(ShapeSearchTree p_search_tree)
    {
        return new geometry.planar.TileShape[0];
    }

    public double get_draw_intensity(GraphicsContext p_graphics_context)
    {
        return p_graphics_context.get_component_outline_color_intensity();
    }

    public Color[] get_draw_colors(GraphicsContext p_graphics_context)
    {
        Color[] color_arr = new java.awt.Color[this.board.layer_structure.arr.length];
        Color front_draw_color = p_graphics_context.get_component_color(true);
        for (int i = 0; i < color_arr.length - 1; ++i)
        {
            color_arr[i] = front_draw_color;
        }
        if (color_arr.length > 1)
        {
            color_arr[color_arr.length - 1] = p_graphics_context.get_component_color(false);
        }
        return color_arr;
    }

    public int get_draw_priority()
    {
        return boardgraphics.Drawable.MIDDLE_DRAW_PRIORITY;
    }

    public void draw(java.awt.Graphics p_g, GraphicsContext p_graphics_context, Color[] p_color_arr, double p_intensity)
    {
        if (p_graphics_context == null || p_intensity <= 0)
        {
            return;
        }
        Color color = p_color_arr[this.get_layer()];
        double intensity = p_graphics_context.get_layer_visibility(this.get_layer()) * p_intensity;

        double draw_width = Math.min (this.board.communication.get_resolution(Unit.MIL), 100);  // problem with low resolution on Kicad
        p_graphics_context.draw_boundary(this.get_area(), draw_width, color, p_g, intensity);
    }

    public geometry.planar.IntBox bounding_box()
    {
        return get_area().bounding_box();
    }

    public void translate_by(Vector p_vector)
    {
        this.translation = this.translation.add(p_vector);
        clear_derived_data();
    }

    public void change_placement_side(IntPoint p_pole)
    {
        this.is_front = !this.is_front;
        Point rel_location = Point.ZERO.translate_by(this.translation);
        this.translation = rel_location.mirror_vertical(p_pole).difference_by(Point.ZERO);
        clear_derived_data();
    }

    public void rotate_approx(double p_angle_in_degree, FloatPoint p_pole)
    {
        double turn_angle = p_angle_in_degree;
        if (!this.is_front && this.board.components.get_flip_style_rotate_first())
        {
            turn_angle = 360 - p_angle_in_degree;
        }
        this.rotation_in_degree += turn_angle;
        while (this.rotation_in_degree >= 360)
        {
            this.rotation_in_degree -= 360;
        }
        while (this.rotation_in_degree < 0)
        {
            this.rotation_in_degree += 360;
        }
        FloatPoint new_translation = this.translation.to_float().rotate(Math.toRadians(p_angle_in_degree), p_pole);
        this.translation = new_translation.round().difference_by(Point.ZERO);
        clear_derived_data();
    }

    public void turn_90_degree(int p_factor, IntPoint p_pole)
    {
        this.rotation_in_degree += p_factor * 90;
        while (this.rotation_in_degree >= 360)
        {
            this.rotation_in_degree -= 360;
        }
        while (this.rotation_in_degree < 0)
        {
            this.rotation_in_degree += 360;
        }
        Point rel_location = Point.ZERO.translate_by(this.translation);
        this.translation = rel_location.turn_90_degree(p_factor, p_pole).difference_by(Point.ZERO);
        clear_derived_data();
    }

    public Area get_area()
    {
        if (this.precalculated_absolute_area == null)
        {
            if (this.relative_area == null)
            {
                System.out.println("ObstacleArea.get_area: area is null");
                return null;
            }
            Area turned_area = this.relative_area;
            if (!this.is_front && !this.board.components.get_flip_style_rotate_first())
            {
                turned_area = turned_area.mirror_vertical(Point.ZERO);
            }
            if (this.rotation_in_degree != 0)
            {
                double rotation = this.rotation_in_degree;
                if (rotation % 90 == 0)
                {
                    turned_area = turned_area.turn_90_degree(((int) rotation) / 90, Point.ZERO);
                }
                else
                {
                    turned_area = turned_area.rotate_approx(Math.toRadians(rotation), FloatPoint.ZERO);
                }

            }
            if (!this.is_front && this.board.components.get_flip_style_rotate_first())
            {
                turned_area = turned_area.mirror_vertical(Point.ZERO);
            }
            this.precalculated_absolute_area = turned_area.translate_by(this.translation);
        }
        return this.precalculated_absolute_area;
    }

    public void clear_derived_data()
    {
        precalculated_absolute_area = null;
    }

    public void print_info(ObjectInfoPanel p_window, java.util.Locale p_locale)
    {
    }

    public boolean write(java.io.ObjectOutputStream p_stream)
    {
        try
        {
            p_stream.writeObject(this);
        } catch (java.io.IOException e)
        {
            return false;
        }
        return true;
    }
    private Area relative_area;
    private transient Area precalculated_absolute_area = null;
    private Vector translation;
    private double rotation_in_degree;
    private boolean is_front;
}
