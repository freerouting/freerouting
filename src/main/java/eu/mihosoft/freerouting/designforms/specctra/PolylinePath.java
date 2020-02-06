/*
 *   Copyright (C) 2014  Alfons Wirtz
 *   website www.freerouting.net
 *
 *   Copyright (C) 2017 Michael Hoffer <info@michaelhoffer.de>
 *   Website www.freerouting.mihosoft.eu
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
 * PolylinePath.java
 *
 * Created on 30. Juni 2004, 08:24
 */

package eu.mihosoft.freerouting.designforms.specctra;

import eu.mihosoft.freerouting.datastructures.IndentFileWriter;
import eu.mihosoft.freerouting.datastructures.IdentifierType;
import eu.mihosoft.freerouting.logger.FRLogger;


/**
 * Describes a path defined by a sequence of lines (instead of a sequence of corners.
 *
 * @author Alfons Wirtz
 */
public class PolylinePath extends Path
{
    
    /** Creates a new instance of PolylinePath */
    public PolylinePath(Layer p_layer, double p_width, double[] p_corner_arr)
    {
        super(p_layer, p_width, p_corner_arr);
    }
    
    /**
     * Writes this path as a scope to an output dsn-file.
     */
    public void write_scope(IndentFileWriter p_file, IdentifierType p_identifier) throws java.io.IOException
    {
        p_file.start_scope();
        p_file.write("polyline_path ");
        p_identifier.write(this.layer.name, p_file);
        p_file.write(" ");
        p_file.write((Double.valueOf(this.width)).toString());
        int line_count = coordinate_arr.length/ 4;
        for (int i = 0; i < line_count; ++i)
        {
            p_file.new_line();
            for (int j = 0; j < 4; ++j)
            {
                p_file.write(Double.valueOf(coordinate_arr[4 * i + j]).toString());
                p_file.write(" ");
            }
        }
        p_file.end_scope();
    }
    
    public void write_scope_int(IndentFileWriter p_file, IdentifierType p_identifier) throws java.io.IOException
    {
        p_file.start_scope();
        p_file.write("polyline_path ");
        p_identifier.write(this.layer.name, p_file);
        p_file.write(" ");
        p_file.write((Double.valueOf(this.width)).toString());
        int line_count = coordinate_arr.length/ 4;
        for (int i = 0; i < line_count; ++i)
        {
            p_file.new_line();
            for (int j = 0; j < 4; ++j)
            {
                Integer curr_coor = (int) Math.round(coordinate_arr[4 * i + j]);
                p_file.write(curr_coor.toString());
                p_file.write(" ");
            }
        }
        p_file.end_scope();
    }
    
    public eu.mihosoft.freerouting.geometry.planar.Shape transform_to_board_rel(CoordinateTransform p_coordinate_transform)
    {
        FRLogger.warn("PolylinePath.transform_to_board_rel not implemented");
        return null;
    }
    
    public eu.mihosoft.freerouting.geometry.planar.Shape transform_to_board(CoordinateTransform p_coordinate_transform)
    {
        FRLogger.warn("PolylinePath.transform_to_board_rel not implemented");
        return null;
    }
    
    
    public Rectangle bounding_box()
    {
        FRLogger.warn("PolylinePath.boundingbox not implemented");
        return null;
    }
}
