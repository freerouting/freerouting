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
 * Rectangle.java
 *
 * Created on 15. Mai 2004, 08:39
 */

package designformats.specctra;

import geometry.planar.FloatPoint;
import geometry.planar.IntBox;
import datastructures.IndentFileWriter;
import datastructures.IdentifierType;

/**
 * Describes a rectangle in a Specctra dsn file.
 *
 * @author  alfons
 */
public class Rectangle extends Shape
{
    /**
     * Creates a new instance of Rectangle
     * p_coor is an array of dimension 4 and contains the rectangle coordinates
     * in the following order: lower left x, lower left y, upper right x, uppper right y.
     */
    public Rectangle(Layer p_layer, double[] p_coor)
    {
        super(p_layer);
        coor = p_coor;
    }
    
    public Rectangle bounding_box()
    {
        return this;
    }
    
    /**
     *  Creates the smallest rectangle containing this rectangle and p_other
     */
    public Rectangle union (Rectangle p_other)
    {
             double[] result_coor = new double[4];
             result_coor[0] = Math.min(this.coor[0], p_other.coor[0]);
             result_coor[1] = Math.min(this.coor[1], p_other.coor[1]);
             result_coor[2] = Math.max(this.coor[2], p_other.coor[2]);
             result_coor[3] = Math.max(this.coor[3], p_other.coor[3]);
             return new Rectangle(this.layer, result_coor);
    }
    
    public geometry.planar.Shape transform_to_board_rel(CoordinateTransform p_coordinate_transform)
    {
        int box_coor[] = new int[4];
        for (int i = 0; i < 4; ++i)
        {
            box_coor[i] = (int) Math.round(p_coordinate_transform.dsn_to_board(this.coor[i]));
        }
        
        IntBox result;
        if (box_coor[1] <= box_coor[3])
        {
            // box_coor describe lower left and upper right corner
            result = new IntBox(box_coor[0], box_coor[1], box_coor[2], box_coor[3]);
        }
        else
        {
            // box_coor describe upper left and lower right corner
            result = new IntBox(box_coor[0], box_coor[3], box_coor[2], box_coor[1]);
        }
        return result;
    }
    
    public geometry.planar.Shape transform_to_board(CoordinateTransform p_coordinate_transform)
    {
        double [] curr_point = new double [2];
        curr_point[0] = Math.min(coor[0], coor[2]);
        curr_point[1] = Math.min(coor[1], coor[3]);
        FloatPoint lower_left = p_coordinate_transform.dsn_to_board(curr_point);
        curr_point[0] = Math.max(coor[0], coor[2]);
        curr_point[1] = Math.max(coor[1], coor[3]);
        FloatPoint upper_right= p_coordinate_transform.dsn_to_board(curr_point);
        return new IntBox(lower_left.round(), upper_right.round());
    }
    
    /**
     * Writes this rectangle as a scope to an output dsn-file.
     */
    public void write_scope(IndentFileWriter p_file, IdentifierType p_identifier) throws java.io.IOException
    {
        p_file.new_line();
        p_file.write("(rect ");
        p_identifier.write(this.layer.name, p_file);
        for (int i = 0; i < coor.length; ++i)
        {
            p_file.write(" ");
            p_file.write(new Double(coor[i]).toString());
        }
        p_file.write(")");
    }
    
    public void write_scope_int(IndentFileWriter p_file, IdentifierType p_identifier) throws java.io.IOException
    {
        p_file.new_line();
        p_file.write("(rect ");
        p_identifier.write(this.layer.name, p_file);
        for (int i = 0; i < coor.length; ++i)
        {
            p_file.write(" ");
            Integer curr_coor = (int) Math.round(coor[i]);
            p_file.write(curr_coor.toString());
        }
        p_file.write(")");
    }
    
    public final double [] coor;
}