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
 * SessionFile.java
 *
 * Created on 29. Oktober 2004, 08:01
 */

package designformats.specctra;

import java.util.Collection;
import java.util.Iterator;

import geometry.planar.FloatPoint;
import geometry.planar.Point;

import board.BasicBoard;
import board.PolylineTrace;
import board.Via;
import board.ConductionArea;
import datastructures.IndentFileWriter;
import datastructures.IdentifierType;

/**
 * Methods to handle a Specctra session file.
 *
 * @author  alfons
 */
public class SessionFile
{
    /**
     * Creates a Specctra session file to update the host system from the RoutingBooard
     */
    public static boolean write( BasicBoard p_board, java.io.OutputStream p_output_stream,  String p_design_name)
    {
        if (p_output_stream == null)
        {
            return false;
        }
        IndentFileWriter output_file = null;
        try
        {
            output_file = new IndentFileWriter(p_output_stream);
        }
        catch (Exception e)
        {
            System.out.println("unable to create session file");
            return false;
        }
        String session_name = p_design_name.replace(".dsn", ".ses");
        try
        {
            String [] reserved_chars = {"(", ")", " ", "-"};
            IdentifierType identifier_type = new IdentifierType(reserved_chars, p_board.communication.specctra_parser_info.string_quote);
            write_session_scope(p_board, identifier_type, output_file, session_name, p_design_name);
        }
        catch (java.io.IOException e)
        {
            System.out.println("unable to write session file");
            return false;
        }
        try
        {
            output_file.close();
        }
        catch (java.io.IOException e)
        {
            System.out.println("unable to close session file");
            return false;
        }
        return true;
    }
    
    private static void write_session_scope(BasicBoard p_board, IdentifierType p_identifier_type,
            IndentFileWriter p_file, String p_session_name, String p_design_name) throws java.io.IOException
    {
        double scale_factor =  p_board.communication.coordinate_transform.dsn_to_board(1)/p_board.communication.resolution;
        CoordinateTransform coordinate_transform = new CoordinateTransform(scale_factor, 0, 0);
        p_file.start_scope();
        p_file.write("session ");
        p_identifier_type.write(p_session_name, p_file);
        p_file.new_line();
        p_file.write("(base_design ");
        p_identifier_type.write(p_design_name, p_file);
        p_file.write(")");
        write_placement(p_board, p_identifier_type, coordinate_transform, p_file);
        write_was_is(p_board, p_identifier_type, p_file);
        write_routes(p_board, p_identifier_type, coordinate_transform, p_file);
        p_file.end_scope();
    }
    
    public static void write_placement(BasicBoard p_board, IdentifierType p_identifier_type,
            CoordinateTransform p_coordinate_transform, IndentFileWriter p_file) throws java.io.IOException
    {
        p_file.start_scope();
        p_file.write("placement");
        Resolution.write_scope(p_file, p_board.communication);
        
        for (int i = 1; i <= p_board.library.packages.count(); ++i)
        {
            write_components(p_board, p_identifier_type, p_coordinate_transform, p_file, p_board.library.packages.get(i));
        }
        p_file.end_scope();
    }
    
    /**
     * Writes all components with the package p_package to the session file.
     */
    public static void write_components(BasicBoard p_board, IdentifierType p_identifier_type, CoordinateTransform p_coordinate_transform,
            IndentFileWriter p_file, library.Package p_package) throws java.io.IOException
    {
        Collection<board.Item> board_items =  p_board.get_items();
        boolean component_found = false;
        for (int i = 1; i <= p_board.components.count(); ++i)
        {
            board.Component curr_component =  p_board.components.get(i);
            if (curr_component.get_package() == p_package)
            {
                // check, if not all items of the component are deleted
                boolean undeleted_item_found = false;
                Iterator<board.Item> it = board_items.iterator();
                while (it.hasNext())
                {
                    board.Item curr_item = it.next();
                    if (curr_item.get_component_no() == curr_component.no)
                    {
                        undeleted_item_found = true;
                        break;
                    }
                }
                if (undeleted_item_found)
                {
                    if (!component_found)
                    {
                        // write the scope header
                        p_file.start_scope();
                        p_file.write("component ");
                        p_identifier_type.write(p_package.name, p_file);
                        component_found = true;
                    }
                    write_component(p_board, p_identifier_type, p_coordinate_transform, p_file, curr_component);
                }
            }
        }
        if (component_found)
        {
            p_file.end_scope();
        }
    }
    
    public static void write_component(BasicBoard p_board, IdentifierType p_identifier_type, CoordinateTransform p_coordinate_transform,
            IndentFileWriter p_file, board.Component p_component) throws java.io.IOException
    {
        p_file.new_line();
        p_file.write("(place ");
        p_identifier_type.write(p_component.name, p_file);
        double[] location = p_coordinate_transform.board_to_dsn(p_component.get_location().to_float());
        Integer x_coor = (int) Math.round(location[0]);
        Integer y_coor = (int) Math.round(location[1]);
        p_file.write(" ");
        p_file.write(x_coor.toString());
        p_file.write(" ");
        p_file.write(y_coor.toString());
        if (p_component.placed_on_front())
        {
            p_file.write(" front ");
        }
        else
        {
            p_file.write(" back ");
        }
        int rotation = (int) Math.round(p_component.get_rotation_in_degree());
        p_file.write((new Integer(rotation).toString()));
        if (p_component.position_fixed)
        {
            p_file.new_line();
            p_file.write(" (lock_type position)");
        }
        p_file.write(")");
    }
    
    public static void write_was_is(BasicBoard p_board, IdentifierType p_identifier_type,
            IndentFileWriter p_file) throws java.io.IOException
    {
        p_file.start_scope();
        p_file.write("was_is");
        Collection<board.Pin> board_pins = p_board.get_pins();
        for (board.Pin curr_pin : board_pins)
        {
            board.Pin swapped_with = curr_pin.get_changed_to();
            if (curr_pin.get_changed_to() != curr_pin)
            {
                p_file.new_line();
                p_file.write("(pins ");
                board.Component curr_cmp = p_board.components.get(curr_pin.get_component_no());
                if (curr_cmp != null)
                {
                    p_identifier_type.write(curr_cmp.name, p_file);
                    p_file.write("-");
                    library.Package.Pin package_pin = curr_cmp.get_package().get_pin(curr_pin.get_index_in_package());
                    p_identifier_type.write(package_pin.name, p_file);
                }
                else
                {
                    System.out.println("SessionFile.write_was_is: component not found");
                }
                p_file.write(" ");
                board.Component swap_cmp = p_board.components.get(swapped_with.get_component_no());
                if (swap_cmp != null)
                {
                    p_identifier_type.write(swap_cmp.name, p_file);
                    p_file.write("-");
                    library.Package.Pin package_pin = swap_cmp.get_package().get_pin(swapped_with.get_index_in_package());
                    p_identifier_type.write(package_pin.name, p_file);
                }
                else
                {
                    System.out.println("SessionFile.write_was_is: component not found");
                }
                p_file.write(")");
            }
        }
        p_file.end_scope();
    }
    
    private static void write_routes(BasicBoard p_board, IdentifierType p_identifier_type, CoordinateTransform p_coordinate_transform,
            IndentFileWriter p_file) throws java.io.IOException
    {
        p_file.start_scope();
        p_file.write("routes ");
        Resolution.write_scope(p_file, p_board.communication);
        Parser.write_scope(p_file,p_board.communication.specctra_parser_info, p_identifier_type, true);
        write_library(p_board, p_identifier_type, p_coordinate_transform, p_file);
        write_network(p_board, p_identifier_type, p_coordinate_transform, p_file);
        p_file.end_scope();
    }
    
    private static void write_library(BasicBoard p_board, IdentifierType p_identifier_type, CoordinateTransform p_coordinate_transform,
            IndentFileWriter p_file) throws java.io.IOException
    {
        p_file.start_scope();
        p_file.write("library_out ");
        for (int i = 0; i < p_board.library.via_padstack_count(); ++i)
        {
            write_padstack(p_board.library.get_via_padstack(i), p_board, p_identifier_type, p_coordinate_transform, p_file);
        }
        p_file.end_scope();
    }
    
    private static void write_padstack(library.Padstack p_padstack, BasicBoard p_board, IdentifierType p_identifier_type,
            CoordinateTransform p_coordinate_transform, IndentFileWriter p_file)
            throws java.io.IOException
    {
        // search the layer range of the padstack
        int first_layer_no = 0;
        while (first_layer_no < p_board.get_layer_count())
        {
            if (p_padstack.get_shape(first_layer_no) != null)
            {
                break;
            }
            ++first_layer_no;
        }
        int last_layer_no = p_board.get_layer_count() - 1;
        while (last_layer_no >= 0 )
        {
            if (p_padstack.get_shape(last_layer_no) != null)
            {
                break;
            }
            --last_layer_no;
        }
        if (first_layer_no >= p_board.get_layer_count() || last_layer_no < 0)
        {
            System.out.println("SessionFile.write_padstack: padstack shape not found");
            return;
        }
        
        p_file.start_scope();
        p_file.write("padstack ");
        p_identifier_type.write(p_padstack.name, p_file);
        for (int i = first_layer_no; i <= last_layer_no; ++i)
        {
            geometry.planar.Shape curr_board_shape = p_padstack.get_shape(i);
            if (curr_board_shape == null)
            {
                continue;
            }
            board.Layer board_layer = p_board.layer_structure.arr[i];
            Layer curr_layer = new Layer(board_layer.name, i, board_layer.is_signal);
            Shape curr_shape = p_coordinate_transform.board_to_dsn_rel(curr_board_shape, curr_layer);
            p_file.start_scope();
            p_file.write("shape");
            curr_shape.write_scope_int(p_file, p_identifier_type);
            p_file.end_scope();
        }
        if (!p_padstack.attach_allowed)
        {
            p_file.new_line();
            p_file.write("(attach off)");
        }
        p_file.end_scope();
    }
    
    
    
    private static void write_network(BasicBoard p_board, IdentifierType p_identifier_type, CoordinateTransform p_coordinate_transform,
            IndentFileWriter p_file) throws java.io.IOException
    {
        p_file.start_scope();
        p_file.write("network_out ");
        for (int i = 1; i <= p_board.rules.nets.max_net_no(); ++i)
        {
            write_net(i, p_board, p_identifier_type, p_coordinate_transform, p_file);
        }
        p_file.end_scope();
    }
    
    private static void write_net(int p_net_no, BasicBoard p_board, IdentifierType p_identifier_type, CoordinateTransform p_coordinate_transform,
            IndentFileWriter p_file) throws java.io.IOException
    {
        Collection<board.Item> net_items = p_board.get_connectable_items(p_net_no);
        boolean header_written = false;
        Iterator<board.Item> it = net_items.iterator();
        while (it.hasNext())
        {
            board.Item curr_item = it.next();
            if (curr_item.get_fixed_state() == board.FixedState.SYSTEM_FIXED)
            {
                continue;
            }
            boolean is_wire = curr_item instanceof PolylineTrace;
            boolean is_via = curr_item instanceof Via;
            boolean is_conduction_area =  curr_item instanceof ConductionArea
                    && p_board.layer_structure.arr [curr_item.first_layer()].is_signal;
            if (!header_written && (is_wire || is_via || is_conduction_area))
            {
                p_file.start_scope();
                p_file.write("net ");
                rules.Net curr_net = p_board.rules.nets.get(p_net_no);
                if (curr_net == null)
                {
                    System.out.println("SessionFile.write_net: net not found");
                }
                else
                {
                    p_identifier_type.write(curr_net.name, p_file);
                }
                header_written = true;
            }
            if (is_wire)
            {
                write_wire((PolylineTrace)curr_item, p_board, p_identifier_type, p_coordinate_transform, p_file);
            }
            else if (is_via)
            {
                write_via((Via) curr_item, p_board, p_identifier_type, p_coordinate_transform, p_file);
            }
            else if (is_conduction_area)
            {
                write_conduction_area( (ConductionArea) curr_item, p_board, p_identifier_type,
                        p_coordinate_transform,  p_file);
            }
        }
        if (header_written)
        {
            p_file.end_scope();
        }
    }
    
    private static void write_wire(PolylineTrace p_wire, BasicBoard p_board, IdentifierType p_identifier_type,
            CoordinateTransform p_coordinate_transform, IndentFileWriter p_file) throws java.io.IOException
    {
        int layer_no = p_wire.get_layer();
        board.Layer board_layer = p_board.layer_structure.arr[layer_no];
        int wire_width = (int) Math.round(p_coordinate_transform.board_to_dsn(2 * p_wire.get_half_width()));
        p_file.start_scope();
        p_file.write("wire");
        Point[] corner_arr = p_wire.polyline().corner_arr();
        int [] coors = new int [2 * corner_arr.length];
        int corner_index = 0;
        int [] prev_coors = null;
        for (int i = 0; i < corner_arr.length; ++i)
        {
            double[] curr_float_coors = p_coordinate_transform.board_to_dsn(corner_arr[i].to_float());
            int [] curr_coors = new int[2];
            curr_coors[0] = (int) Math.round(curr_float_coors[0]);
            curr_coors[1] = (int) Math.round(curr_float_coors[1]);
            if (i == 0  || ( curr_coors[0] != prev_coors[0] || curr_coors[1] != prev_coors[1]))
            {
                coors[corner_index] = curr_coors[0];
                ++corner_index;
                coors[corner_index] = curr_coors[1];
                ++corner_index;
                prev_coors = curr_coors;
                
            }
        }
        if (corner_index < coors.length)
        {
            int [] adjusted_coors = new int[corner_index];
            for (int i = 0; i < adjusted_coors.length; ++i)
            {
                adjusted_coors[i] = coors[i];
            }
            coors = adjusted_coors;
        }
        write_path(board_layer.name, wire_width, coors, p_identifier_type, p_file);
        write_fixed_state(p_file, p_wire.get_fixed_state());
        p_file.end_scope();
    }
    
    private static void write_via(Via p_via, BasicBoard p_board, IdentifierType p_identifier_type,
            CoordinateTransform p_coordinate_transform, IndentFileWriter p_file) throws java.io.IOException
    {
        library.Padstack via_padstack = p_via.get_padstack();
        FloatPoint via_location = p_via.get_center().to_float();
        p_file.start_scope();
        p_file.write("via ");
        p_identifier_type.write(via_padstack.name, p_file);
        p_file.write(" ");
        double[] location = p_coordinate_transform.board_to_dsn(via_location);
        Integer x_coor = (int) Math.round(location[0]);
        p_file.write(x_coor.toString());
        p_file.write(" ");
        Integer y_coor = (int) Math.round(location[1]);
        p_file.write(y_coor.toString());
        write_fixed_state(p_file, p_via.get_fixed_state());
        p_file.end_scope();
    }
    
    static private void write_fixed_state(IndentFileWriter p_file, board.FixedState p_fixed_state) throws java.io.IOException
    {
        if (p_fixed_state.ordinal() <= board.FixedState.SHOVE_FIXED.ordinal())
        {
            return;
        }
        p_file.new_line();
        p_file.write("(type ");
        if (p_fixed_state == board.FixedState.SYSTEM_FIXED)
        {
            p_file.write("fix)");
        }
        else
        {
            p_file.write("protect)");
        }
    }
    
    private static void write_path(String p_layer_name, int p_width, int[] p_coors, IdentifierType p_identifier_type,
            IndentFileWriter p_file)
            throws java.io.IOException
    {
        p_file.start_scope();
        p_file.write("path ");
        p_identifier_type.write(p_layer_name, p_file);
        p_file.write(" ");
        p_file.write((new Integer(p_width)).toString());
        int corner_count = p_coors.length/ 2;
        for (int i = 0; i < corner_count; ++i)
        {
            p_file.new_line();
            p_file.write(new Integer(p_coors[2 * i]).toString());
            p_file.write(" ");
            p_file.write(new Integer(p_coors[2 * i + 1]).toString());
        }
        p_file.end_scope();
    }
    
    private static void write_conduction_area( ConductionArea p_conduction_area, BasicBoard p_board,
            IdentifierType p_identifier_type, CoordinateTransform p_coordinate_transform,
            IndentFileWriter p_file) throws java.io.IOException
    {
        int net_count = p_conduction_area.net_count();
        if (net_count <= 0 || net_count > 1)
        {
            System.out.println("SessionFile.write_conduction_area: unexpected net count");
            return;
        }
        geometry.planar.Area curr_area = p_conduction_area.get_area();
        int layer_no = p_conduction_area.get_layer();
        board.Layer board_layer = p_board.layer_structure.arr[ layer_no];
        Layer conduction_layer = new Layer(board_layer.name, layer_no, board_layer.is_signal);
        geometry.planar.Shape boundary_shape;
        geometry.planar.Shape [] holes;
        if (curr_area instanceof geometry.planar.Shape)
        {
            boundary_shape = (geometry.planar.Shape) curr_area;
            holes = new geometry.planar.Shape [0];
        }
        else
        {
            boundary_shape = curr_area.get_border();
            holes = curr_area.get_holes();
        }
        p_file.start_scope();
        p_file.write("wire ");
        Shape dsn_shape = p_coordinate_transform.board_to_dsn(boundary_shape, conduction_layer);
        if (dsn_shape != null)
        {
            dsn_shape.write_scope_int(p_file, p_identifier_type);
        }
        for (int i = 0; i < holes.length; ++i)
        {
            Shape dsn_hole = p_coordinate_transform.board_to_dsn(holes[i], conduction_layer);
            dsn_hole.write_hole_scope(p_file, p_identifier_type);
        }
        p_file.end_scope();
    }
}