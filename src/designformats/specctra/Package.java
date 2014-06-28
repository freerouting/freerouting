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
 * Package.java
 *
 * Created on 21. Mai 2004, 09:31
 */

package designformats.specctra;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import board.Item;

/**
 * Class for reading and writing package scopes from dsn-files.
 *
 * @author  alfons
 */
public class Package
{
    
    /** Creates a new instance of Package */
    public Package(String p_name, PinInfo[] p_pin_info_arr, Collection<Shape> p_outline, Collection<Shape.ReadAreaScopeResult> p_keepouts,
            Collection<Shape.ReadAreaScopeResult> p_via_keepouts, Collection<Shape.ReadAreaScopeResult> p_place_keepouts, boolean p_is_front)
    {
        name = p_name;
        pin_info_arr = p_pin_info_arr;
        outline = p_outline;
        keepouts = p_keepouts;
        via_keepouts = p_via_keepouts;
        place_keepouts = p_place_keepouts;
        is_front = p_is_front;
    }
    
    public static Package read_scope(Scanner p_scanner, LayerStructure p_layer_structure)
    {
        try
        {
            boolean is_front = true;
            Collection <Shape> outline = new LinkedList<Shape>();
            Collection<Shape.ReadAreaScopeResult> keepouts = new LinkedList<Shape.ReadAreaScopeResult>();
            Collection<Shape.ReadAreaScopeResult> via_keepouts = new LinkedList<Shape.ReadAreaScopeResult>();
            Collection<Shape.ReadAreaScopeResult> place_keepouts = new LinkedList<Shape.ReadAreaScopeResult>();
            Object next_token = p_scanner.next_token();
            if ( !(next_token instanceof String))
            {
                System.out.println("Package.read_scope: String expected");
                return null;
            }
            String package_name = (String) next_token;
            Collection<PinInfo> pin_info_list = new LinkedList<PinInfo>();
            for (;;)
            {
                Object prev_token = next_token;
                next_token = p_scanner.next_token();
                
                if (next_token == null)
                {
                    System.out.println("Package.read_scope: unexpected end of file");
                    return null;
                }
                if (next_token == Keyword.CLOSED_BRACKET)
                {
                    // end of scope
                    break;
                }
                if (prev_token == Keyword.OPEN_BRACKET)
                {
                    if (next_token == Keyword.PIN)
                    {
                        PinInfo next_pin = read_pin_info(p_scanner);
                        if (next_pin == null)
                        {
                            return null;
                        }
                        pin_info_list.add(next_pin);
                    }
                    else if (next_token == Keyword.SIDE)
                    {
                        is_front = read_placement_side(p_scanner);
                    }
                    else if (next_token == Keyword.OUTLINE)
                    {
                        Shape curr_shape =  Shape.read_scope(p_scanner,  p_layer_structure);
                        if (curr_shape != null)
                        {
                            outline.add(curr_shape);
                        }
                        // overread closing bracket
                        next_token = p_scanner.next_token();
                        if (next_token != Keyword.CLOSED_BRACKET)
                        {
                            System.out.println("Package.read_scope: closed bracket expected");
                            return null;
                        }
                    }
                    else if (next_token == Keyword.KEEPOUT)
                    {
                        Shape.ReadAreaScopeResult keepout_area = Shape.read_area_scope(p_scanner,  p_layer_structure, false);
                        if (keepout_area != null)
                        {
                            keepouts.add(keepout_area);
                        }
                    }
                    else if (next_token == Keyword.VIA_KEEPOUT)
                    {
                        Shape.ReadAreaScopeResult keepout_area = Shape.read_area_scope(p_scanner,  p_layer_structure, false);
                        if (keepout_area != null)
                        {
                            via_keepouts.add(keepout_area);
                        }
                    }
                    else if (next_token == Keyword.PLACE_KEEPOUT)
                    {
                        Shape.ReadAreaScopeResult keepout_area = Shape.read_area_scope(p_scanner,  p_layer_structure, false);
                        if (keepout_area != null)
                        {
                            place_keepouts.add(keepout_area);
                        }
                    }
                    else
                    {
                        ScopeKeyword.skip_scope(p_scanner);
                    }
                }
            }
            PinInfo [] pin_info_arr = new PinInfo[pin_info_list.size()];
            Iterator<PinInfo> it = pin_info_list.iterator();
            for (int i = 0; i < pin_info_arr.length; ++i)
            {
                pin_info_arr[i] = it.next();
            }
            return new Package(package_name, pin_info_arr, outline, keepouts, via_keepouts, place_keepouts, is_front);
        }
        catch (java.io.IOException e)
        {
            System.out.println("Package.read_scope: IO error scanning file");
            System.out.println(e);
            return null;
        }
    }
    
    public static void write_scope(WriteScopeParameter p_par, library.Package p_package) throws java.io.IOException
    {
        p_par.file.start_scope();
        p_par.file.write("image ");
        p_par.identifier_type.write(p_package.name, p_par.file);
        // write the placement side of the package
        p_par.file.new_line();
        p_par.file.write("(side ");
        if (p_package.is_front)
        {
            p_par.file.write("front)");
        }
        else
        {
            p_par.file.write("back)");
        }
        // write the pins of the package
        for (int i = 0; i < p_package.pin_count(); ++i)
        {
            library.Package.Pin curr_pin = p_package.get_pin(i);
            p_par.file.new_line();
            p_par.file.write("(pin ");
            library.Padstack curr_padstack = p_par.board.library.padstacks.get(curr_pin.padstack_no);
            p_par.identifier_type.write(curr_padstack.name, p_par.file);
            p_par.file.write(" ");
            p_par.identifier_type.write(curr_pin.name, p_par.file);
            double [] rel_coor = p_par.coordinate_transform.board_to_dsn(curr_pin.relative_location);
            for(int j = 0; j < rel_coor.length; ++j)
            {
                p_par.file.write(" ");
                p_par.file.write((new Double(rel_coor[j])).toString());
            }
            int rotation = (int) Math.round(curr_pin.rotation_in_degree);
            if (rotation != 0)
            {
                p_par.file.write("(rotate ");
                p_par.file.write((new Integer(rotation)).toString());
                p_par.file.write(")");
            }
            p_par.file.write(")");
        }
        // write the keepouts belonging to  the package.
        for (int i = 0; i < p_package.keepout_arr.length; ++i)
        {
            write_package_keepout(p_package.keepout_arr[i], p_par, false);
        }
        for (int i = 0; i < p_package.via_keepout_arr.length; ++i)
        {
            write_package_keepout(p_package.via_keepout_arr[i], p_par, true);
        }
        // write the package outline.
        for (int i = 0; i < p_package.outline.length; ++i)
        {
            p_par.file.start_scope();
            p_par.file.write("outline");
            Shape curr_outline = p_par.coordinate_transform.board_to_dsn_rel(p_package.outline[i], Layer.SIGNAL);
            curr_outline.write_scope(p_par.file, p_par.identifier_type);
            p_par.file.end_scope();
        }
        p_par.file.end_scope();
    }
    
    private static void write_package_keepout(library.Package.Keepout p_keepout, WriteScopeParameter p_par,
            boolean p_is_via_keepout) throws java.io.IOException
    {
        Layer keepout_layer;
        if (p_keepout.layer >= 0)
        {
            board.Layer board_layer = p_par.board.layer_structure.arr[p_keepout.layer];
            keepout_layer = new Layer(board_layer.name, p_keepout.layer, board_layer.is_signal);
        }
        else
        {
            keepout_layer = Layer.SIGNAL;
        }
        geometry.planar.Shape boundary_shape;
        geometry.planar.Shape [] holes;
        if (p_keepout.area instanceof geometry.planar.Shape)
        {
            boundary_shape = (geometry.planar.Shape) p_keepout.area ;
            holes = new geometry.planar.Shape [0];
        }
        else
        {
            boundary_shape = p_keepout.area .get_border();
            holes = p_keepout.area .get_holes();
        }
        p_par.file.start_scope();
        if (p_is_via_keepout)
        {
            p_par.file.write("via_keepout");
        }
        else
        {
            p_par.file.write("keepout");
        }
        Shape dsn_shape = p_par.coordinate_transform.board_to_dsn(boundary_shape, keepout_layer);
        if (dsn_shape != null)
        {
            dsn_shape.write_scope(p_par.file, p_par.identifier_type);
        }
        for (int j = 0; j < holes.length; ++j)
        {
            Shape dsn_hole = p_par.coordinate_transform.board_to_dsn(holes[j], keepout_layer);
            dsn_hole.write_hole_scope(p_par.file, p_par.identifier_type);
        }
        p_par.file.end_scope();
    }
    
    /** Reads the information of a single pin in a package. */
    private static PinInfo read_pin_info(Scanner p_scanner)
    {
        try
        {
            // Read the padstack name.
            p_scanner.yybegin(SpecctraFileScanner.NAME);
            String padstack_name = null;
            Object next_token = p_scanner.next_token();
            if ( next_token instanceof String)
            {
                padstack_name = (String) next_token;
            }
            else if ( next_token instanceof Integer)
            {
                padstack_name = ((Integer) next_token).toString();
            }
            else
            {
                System.out.println("Package.read_pin_info: String or Integer expected");
                return null;
            }
            double rotation = 0;
            
            p_scanner.yybegin(SpecctraFileScanner.NAME); // to be able to handle pin names starting with a digit.
            next_token = p_scanner.next_token();
            if (next_token == Keyword.OPEN_BRACKET)
            {
                // read the padstack rotation
                next_token = p_scanner.next_token();
                if (next_token == Keyword.ROTATE)
                {
                    rotation = read_rotation(p_scanner);
                }
                else
                {
                    ScopeKeyword.skip_scope(p_scanner);
                }
                p_scanner.yybegin(SpecctraFileScanner.NAME);
                next_token = p_scanner.next_token();
            }
            // Read the pin name.
            String pin_name = null;
            if ( next_token instanceof String)
            {
                pin_name = (String) next_token;
            }
            else if ( next_token instanceof Integer)
            {
                pin_name = ((Integer) next_token).toString();
            }
            else
            {
                System.out.println("Package.read_pin_info: String or Integer expected");
                return null;
            }
            
            double []  pin_coor = new double [2];
            for (int i = 0; i < 2; ++i)
            {
                next_token = p_scanner.next_token();
                if (next_token instanceof Double)
                {
                    pin_coor[i] = ((Double) next_token).doubleValue();
                }
                else if (next_token instanceof Integer)
                {
                    pin_coor[i] = ((Integer) next_token).intValue();
                }
                else
                {
                    System.out.println("Package.read_pin_info: number expected");
                    return null;
                }
            }
            // Handle scopes at the end of the pin scope.
            for (;;)
            {
                Object prev_token = next_token;
                next_token = p_scanner.next_token();
                
                if (next_token == null)
                {
                    System.out.println("Package.read_pin_info: unexpected end of file");
                    return null;
                }
                if (next_token == Keyword.CLOSED_BRACKET)
                {
                    // end of scope
                    break;
                }
                if (prev_token == Keyword.OPEN_BRACKET)
                {
                    if (next_token == Keyword.ROTATE)
                    {
                        rotation = read_rotation(p_scanner);
                    }
                    else
                    {
                        ScopeKeyword.skip_scope(p_scanner);
                    }
                }
            }
            return new PinInfo(padstack_name, pin_name, pin_coor, rotation);
        }
        catch (java.io.IOException e)
        {
            System.out.println("Package.read_pin_info: IO error while scanning file");
            return null;
        }
    }
    
    private static double read_rotation(Scanner p_scanner)
    {
        double result = 0;
        try
        {
            Object next_token = p_scanner.next_token();
            if (next_token instanceof Integer)
            {
                result = ((Integer)next_token).intValue();
            }
            else if (next_token instanceof Double)
            {
                result = ((Double)next_token).doubleValue();
            }
            else
            {
                System.out.println("Package.read_rotation: number expected");
            }
            // Overread The closing bracket.
            next_token = p_scanner.next_token();
            if (next_token != Keyword.CLOSED_BRACKET)
            {
                System.out.println("Package.read_rotation: closing bracket expected");
            }
        }
        catch (java.io.IOException e)
        {
            System.out.println("Package.read_rotation: IO error while scanning file");
        }
        return result;
    }
    
    /**
     * Writes the placements of p_package to a Specctra dsn-file.
     */
    public static void write_placement_scope(WriteScopeParameter p_par, library.Package p_package)
    throws java.io.IOException
    {
        Collection<Item> board_items =  p_par.board.get_items();
        boolean component_found = false;
        for (int i = 1; i <= p_par.board.components.count(); ++i)
        {
            board.Component curr_component =  p_par.board.components.get(i);
            if (curr_component.get_package() == p_package)
            {
                // check, if not all items of the component are deleted
                boolean undeleted_item_found = false;
                Iterator<Item> it = board_items.iterator();
                while (it.hasNext())
                {
                    Item curr_item = it.next();
                    if (curr_item.get_component_no() == curr_component.no)
                    {
                        undeleted_item_found = true;
                        break;
                    }
                }
                if (undeleted_item_found || !curr_component.is_placed())
                {
                    if (!component_found)
                    {
                        // write the scope header
                        p_par.file.start_scope();
                        p_par.file.write("component ");
                        p_par.identifier_type.write(p_package.name, p_par.file);
                        component_found = true;
                    }
                    Component.write_scope(p_par, curr_component);
                }
            }
        }
        if (component_found)
        {
            p_par.file.end_scope();
        }
    }
    
    private static boolean read_placement_side(Scanner p_scanner) throws java.io.IOException
    {
        Object next_token = p_scanner.next_token();
        boolean result = (next_token != Keyword.BACK);
        
        next_token = p_scanner.next_token();
        if (next_token != Keyword.CLOSED_BRACKET)
        {
            System.out.println("Package.read_placement_side: closing bracket expected");
        }
        return result;
    }
    
    
    public final String name;
    /** List of objects of type PinInfo. */
    public final PinInfo[]  pin_info_arr;
    /** The outline of the package. */
    public final Collection<Shape> outline;
    /** Collection of keepoouts belonging to this package */
    public final Collection<Shape.ReadAreaScopeResult> keepouts;
    /** Collection of via keepoouts belonging to this package */
    public final Collection<Shape.ReadAreaScopeResult> via_keepouts;
    /** Collection of place keepoouts belonging to this package */
    public final Collection<Shape.ReadAreaScopeResult> place_keepouts;
    /** If false, the package is placed on the back side of the board */
    public final boolean is_front;
    
    
    /** Describes the Iinformation of a pin in a package. */
    static public class PinInfo
    {
        PinInfo(String p_padstack_name, String p_pin_name, double [] p_rel_coor, double p_rotation)
        {
            padstack_name = p_padstack_name;
            pin_name = p_pin_name;
            rel_coor = p_rel_coor;
            rotation = p_rotation;
        }
        /** Phe name of the pastack of this pin. */
        public final String padstack_name;
        /** Phe name of this pin. */
        public final String pin_name;
        /** The x- and y-coordinates relative to the package location. */
        public final double [] rel_coor;
        /** The rotation of the pin relative to the package. */
        public final double rotation;
    }
}
