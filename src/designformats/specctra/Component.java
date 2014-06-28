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
 * Component.java
 *
 * Created on 20. Mai 2004, 07:32
 */

package designformats.specctra;


/**
 * Handels the placement bata of a library component.
 *
 * @author  alfons
 */
public class Component extends ScopeKeyword
{
    
    /** Creates a new instance of Component */
    public Component()
    {
        super("component");
    }
    
    /**
     * Overwrites the function read_scope in ScopeKeyword
     */
    public boolean read_scope(ReadScopeParameter p_par)
    {
        try
        {
            ComponentPlacement component_placement = read_scope(p_par.scanner);
            if(component_placement == null)
            {
                return false;
            }
            p_par.placement_list.add(component_placement);
        }
        catch (java.io.IOException e)
        {
            System.out.println("Component.read_scope: IO error scanning file");
            return false;
        }
        return true;
    }
    
    /**
     * Used also when reading a session file.
     */
    public static ComponentPlacement read_scope(Scanner p_scanner) throws java.io.IOException
    {
        Object next_token = p_scanner.next_token();
        if (!(next_token instanceof String))
        {
            System.out.println("Component.read_scope: component name expected");
            return null;
        }
        String name =  (String) next_token;
        ComponentPlacement component_placement = new ComponentPlacement(name);
        Object prev_token = next_token;
        next_token = p_scanner.next_token();
        while ( next_token != Keyword.CLOSED_BRACKET)
        {
            if (prev_token == Keyword.OPEN_BRACKET && next_token == Keyword.PLACE)
            {
                ComponentPlacement.ComponentLocation next_location = read_place_scope(p_scanner);
                if (next_location != null)
                {
                    component_placement.locations.add(next_location);
                }
            }
            prev_token = next_token;
            next_token = p_scanner.next_token();
        }
        return component_placement;
    }
    
    public static void write_scope(WriteScopeParameter p_par, board.Component p_component)
    throws java.io.IOException
    {
        p_par.file.start_scope();
        p_par.file.write("place ");
        p_par.file.new_line();
        p_par.identifier_type.write(p_component.name, p_par.file);
        if (p_component.is_placed())
        {
            double [] coor = p_par.coordinate_transform.board_to_dsn(p_component.get_location().to_float());
            for (int i = 0; i < coor.length; ++i)
            {
                p_par.file.write(" ");
                p_par.file.write((new Double(coor[i])).toString());
            }
            if (p_component.placed_on_front())
            {
                p_par.file.write(" front ");
            }
            else
            {
                p_par.file.write(" back ");
            }
            int rotation = (int) Math.round(p_component.get_rotation_in_degree());
            p_par.file.write((new Integer(rotation).toString()));
        }
        if (p_component.position_fixed)
        {
            p_par.file.new_line();
            p_par.file.write(" (lock_type position)");
        }
        int pin_count = p_component.get_package().pin_count();
        for (int i = 0; i < pin_count; ++i)
        {
            write_pin_info(p_par, p_component, i);
        }
        write_keepout_infos(p_par, p_component);
        p_par.file.end_scope();
    }
    
    private static void write_pin_info(WriteScopeParameter p_par, board.Component p_component, int p_pin_no)
    throws java.io.IOException
    {
        if (!p_component.is_placed())
        {
            return;
        }
        library.Package.Pin package_pin = p_component.get_package().get_pin(p_pin_no);
        if (package_pin == null)
        {
            System.out.println("Component.write_pin_info: package pin not found");
            return;
        }
        board.Pin component_pin = p_par.board.get_pin(p_component.no, p_pin_no);
        if (component_pin == null)
        {
            System.out.println("Component.write_pin_info: component pin not found");
            return;
        }
        String cl_class_name = p_par.board.rules.clearance_matrix.get_name(component_pin.clearance_class_no());
        if (cl_class_name == null)
        {
            System.out.println("Component.write_pin_info: clearance class  name not found");
            return;
        }
        p_par.file.new_line();
        p_par.file.write("(pin ");
        p_par.identifier_type.write(package_pin.name, p_par.file);
        p_par.file.write(" (clearance_class ");
        p_par.identifier_type.write(cl_class_name, p_par.file);
        p_par.file.write("))");
    }
    
    private static void write_keepout_infos(WriteScopeParameter p_par, board.Component p_component)
    throws java.io.IOException
    {
        if (!p_component.is_placed())
        {
            return;
        }
        library.Package.Keepout[] curr_keepout_arr;
        String keepout_type;
        for (int j = 0; j < 3; ++j)
        {
            if (j == 0)
            {
                curr_keepout_arr = p_component.get_package().keepout_arr;
                keepout_type = "(keepout ";
            }
            else if (j == 1)
            {
                curr_keepout_arr = p_component.get_package().via_keepout_arr;
                keepout_type = "(via_keepout ";
            }
            else
            {
                curr_keepout_arr = p_component.get_package().place_keepout_arr;
                keepout_type = "(place_keepout ";
            }
            for (int i = 0; i < curr_keepout_arr.length; ++i)
            {
                library.Package.Keepout curr_keepout = curr_keepout_arr[i];
                board.ObstacleArea curr_obstacle_area = get_keepout(p_par.board, p_component.no, curr_keepout.name);
                if (curr_obstacle_area == null || curr_obstacle_area.clearance_class_no() == 0)
                {
                    continue;
                }
                String cl_class_name = p_par.board.rules.clearance_matrix.get_name(curr_obstacle_area.clearance_class_no());
                if (cl_class_name == null)
                {
                    System.out.println("Component.write_keepout_infos: clearance class name not found");
                    return;
                }
                p_par.file.new_line();
                p_par.file.write(keepout_type);
                p_par.identifier_type.write(curr_keepout.name, p_par.file);
                p_par.file.write(" (clearance_class ");
                p_par.identifier_type.write(cl_class_name, p_par.file);
                p_par.file.write("))");
            }
        }
    }
    
    private static board.ObstacleArea get_keepout(board.BasicBoard p_board, int p_component_no, String p_name)
    {
        java.util.Iterator<datastructures.UndoableObjects.UndoableObjectNode> it = p_board.item_list.start_read_object();
        for(;;)
        {
            board.Item curr_item = (board.Item)p_board.item_list.read_object(it);
            if (curr_item == null)
            {
                break;
            }
            if (curr_item.get_component_no() == p_component_no && curr_item instanceof board.ObstacleArea)
            {
                board.ObstacleArea curr_area = (board.ObstacleArea) curr_item;
                if (curr_area.name != null && curr_area.name.equals(p_name))
                {
                    return curr_area;
                }
            }
        }
        return null;
    }
    
    
    
    private static ComponentPlacement.ComponentLocation read_place_scope(Scanner p_scanner)
    {
        try
        {
            java.util.Map <String, ComponentPlacement.ItemClearanceInfo> pin_infos =
                    new java.util.TreeMap<String, ComponentPlacement.ItemClearanceInfo>();
            java.util.Map <String, ComponentPlacement.ItemClearanceInfo> keepout_infos =
                    new java.util.TreeMap<String, ComponentPlacement.ItemClearanceInfo>();
            java.util.Map <String, ComponentPlacement.ItemClearanceInfo> via_keepout_infos =
                    new java.util.TreeMap<String, ComponentPlacement.ItemClearanceInfo>();
            java.util.Map <String, ComponentPlacement.ItemClearanceInfo> place_keepout_infos =
                    new java.util.TreeMap<String, ComponentPlacement.ItemClearanceInfo>();
            Object next_token = p_scanner.next_token();
            if (!(next_token instanceof String))
            {
                System.out.println("Component.read_place_scope: String expected");
                return null;
            }
            String name = (String) next_token;
            double[] location = new double[2];
            for (int i = 0; i < 2; ++i)
            {
                next_token = p_scanner.next_token();
                if (next_token instanceof Double)
                {
                    location[i] = ((Double) next_token).doubleValue();
                }
                else if (next_token instanceof Integer)
                {
                    location[i] = ((Integer) next_token).intValue();
                }
                else if (next_token == Keyword.CLOSED_BRACKET)
                {
                    // component is not yet placed
                    return new ComponentPlacement.ComponentLocation(name, null, true, 0, false, pin_infos,
                            keepout_infos, via_keepout_infos, place_keepout_infos);
                }
                else
                {
                    System.out.println("Component.read_place_scope: number  expected");
                    return null;
                }
            }
            next_token = p_scanner.next_token();
            boolean is_front = true;
            if (next_token == Keyword.BACK)
            {
                is_front = false;
            }
            else if (next_token != Keyword.FRONT)
            {
                System.out.println("Component.read_place_scope: Keyword.FRONT expected");
            }
            double rotation;
            next_token = p_scanner.next_token();
            if (next_token instanceof Double)
            {
                rotation = ((Double) next_token).doubleValue();
            }
            else if (next_token instanceof Integer)
            {
                rotation = ((Integer) next_token).intValue();
            }
            else
            {
                System.out.println("Component.read_place_scope: number expected");
                return null;
            }
            boolean position_fixed = false;
            next_token = p_scanner.next_token();
            while (next_token == Keyword.OPEN_BRACKET)
            {
                next_token = p_scanner.next_token();
                if (next_token == Keyword.LOCK_TYPE)
                {
                    position_fixed = read_lock_type(p_scanner);
                }
                else if (next_token == Keyword.PIN)
                {
                    ComponentPlacement.ItemClearanceInfo curr_pin_info = read_item_clearance_info(p_scanner);
                    if (curr_pin_info == null)
                    {
                        return null;
                    }
                    pin_infos.put(curr_pin_info.name,  curr_pin_info);
                }
                else if (next_token == Keyword.KEEPOUT)
                {
                    ComponentPlacement.ItemClearanceInfo curr_keepout_info = read_item_clearance_info(p_scanner);
                    if (curr_keepout_info == null)
                    {
                        return null;
                    }
                    keepout_infos.put(curr_keepout_info.name,  curr_keepout_info);
                }
                else if (next_token == Keyword.VIA_KEEPOUT)
                {
                    ComponentPlacement.ItemClearanceInfo curr_keepout_info = read_item_clearance_info(p_scanner);
                    if (curr_keepout_info == null)
                    {
                        return null;
                    }
                    via_keepout_infos.put(curr_keepout_info.name,  curr_keepout_info);
                }
                else if (next_token == Keyword.PLACE_KEEPOUT)
                {
                    ComponentPlacement.ItemClearanceInfo curr_keepout_info = read_item_clearance_info(p_scanner);
                    if (curr_keepout_info == null)
                    {
                        return null;
                    }
                    place_keepout_infos.put(curr_keepout_info.name,  curr_keepout_info);
                }
                else
                {
                    skip_scope(p_scanner);
                }
                next_token = p_scanner.next_token();
            }
            if (next_token != Keyword.CLOSED_BRACKET)
            {
                System.out.println("Component.read_place_scope: ) expected");
                return null;
            }
            ComponentPlacement.ComponentLocation result =
                    new ComponentPlacement.ComponentLocation(name, location, is_front, rotation, position_fixed, pin_infos,
                    keepout_infos, via_keepout_infos, place_keepout_infos);
            return result;
        }
        catch (java.io.IOException e)
        {
            System.out.println("Component.read_scope: IO error scanning file");
            System.out.println(e);
            return  null;
        }
    }
    
    private static  ComponentPlacement.ItemClearanceInfo read_item_clearance_info(Scanner p_scanner) throws java.io.IOException
    {
        p_scanner.yybegin(SpecctraFileScanner.NAME);
        Object next_token = p_scanner.next_token();
        if (!(next_token instanceof String))
        {
            System.out.println("Component.read_item_clearance_info: String expected");
            return null;
        }
        String name = (String) next_token;
        String cl_class_name = null;
        next_token = p_scanner.next_token();
        while (next_token == Keyword.OPEN_BRACKET)
        {
            next_token = p_scanner.next_token();
            if (next_token == Keyword.CLEARANCE_CLASS)
            {
                cl_class_name = DsnFile.read_string_scope(p_scanner);
            }
            else
            {
                skip_scope(p_scanner);
            }
            next_token = p_scanner.next_token();
        }
        if (next_token != Keyword.CLOSED_BRACKET)
        {
            System.out.println("Component.read_item_clearance_info: ) expected");
            return null;
        }
        if (cl_class_name == null)
        {
            System.out.println("Component.read_item_clearance_info: clearance class name not found");
            return null;
        }
        return new ComponentPlacement.ItemClearanceInfo(name, cl_class_name);
    }
    
    private static boolean read_lock_type(Scanner p_scanner) throws java.io.IOException
    {
        boolean result = false;
        for (;;)
        {
            Object next_token = p_scanner.next_token();
            if (next_token == Keyword.CLOSED_BRACKET)
            {
                break;
            }
            if (next_token == Keyword.POSITION)
            {
                result = true;
            }
        }
        return result;
    }
}
