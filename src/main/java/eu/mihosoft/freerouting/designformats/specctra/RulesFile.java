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
 * RulesFile.java
 *
 * Created on 18. Juli 2005, 07:07
 *
 */

package designformats.specctra;

import datastructures.IndentFileWriter;

import  board.BasicBoard;

/**
 * File for saving the board rules, so that they can be restored after the Board
 * is creates anew  from the host system.
 *
 * @author Alfons Wirtz
 */
public class RulesFile
{
    
    public static void write(interactive.BoardHandling p_board_handling, java.io.OutputStream p_output_stream, String p_design_name)
    {
        IndentFileWriter output_file = new IndentFileWriter(p_output_stream);
        BasicBoard routing_board = p_board_handling.get_routing_board();
        WriteScopeParameter write_scope_parameter =
                new WriteScopeParameter(routing_board, p_board_handling.settings.autoroute_settings,
                output_file, routing_board.communication.specctra_parser_info.string_quote,
                routing_board.communication.coordinate_transform, false);
        try
        {
            write_rules(write_scope_parameter, p_design_name);
        }
        catch (java.io.IOException e)
        {
            System.out.println("unable to write rules to file");
        }
        try
        {
            output_file.close();
        }
        catch (java.io.IOException e)
        {
            System.out.println("unable to close rules file");
        }
    }
    
    public static boolean read(java.io.InputStream p_input_stream, String p_design_name, 
            interactive.BoardHandling p_board_handling)
    {
        BasicBoard routing_board = p_board_handling.get_routing_board();
        Scanner scanner =  new SpecctraFileScanner(p_input_stream);
        try
        {
            Object curr_token = scanner.next_token();
            if (curr_token != Keyword.OPEN_BRACKET)
            {
                System.out.println("RulesFile.read: open bracket expected");
                return false;
            }
            curr_token = scanner.next_token();
            if (curr_token != Keyword.RULES)
            {
                System.out.println("RulesFile.read: keyword rules expected");
                return false;
            }
            curr_token = scanner.next_token();
            if (curr_token != Keyword.PCB_SCOPE)
            {
                System.out.println("RulesFile.read: keyword pcb expected");
                return false;
            }
            scanner.yybegin(SpecctraFileScanner.NAME);
            curr_token = scanner.next_token();
            if (!(curr_token instanceof String) || !((String) curr_token).equals(p_design_name))
            {
                System.out.println("RulesFile.read: design_name not matching");
                return false;
            }
        }
        catch (java.io.IOException e)
        {
            System.out.println("RulesFile.read: IO error scanning file");
            return false;
        }
        LayerStructure layer_structure = new LayerStructure(routing_board.layer_structure);
        CoordinateTransform coordinate_transform = routing_board.communication.coordinate_transform;
        Object next_token = null;
        for (;;)
        {
            Object prev_token = next_token;
            try
            {
                next_token = scanner.next_token();
            }
            catch (java.io.IOException e)
            {
                System.out.println("RulesFile.read: IO error scanning file");
                return false;
            }
            if (next_token == null)
            {
                System.out.println("Structure.read_scope: unexpected end of file");
                return false;
            }
            if (next_token == Keyword.CLOSED_BRACKET)
            {
                // end of scope
                break;
            }
            boolean read_ok = true;
            if (prev_token == Keyword.OPEN_BRACKET)
            {
                if (next_token == Keyword.RULE)
                {
                    add_rules(Rule.read_scope(scanner), routing_board, null);
                }
                else if (next_token == Keyword.LAYER)
                {
                    add_layer_rules(scanner, routing_board);
                }
                else if (next_token == Keyword.PADSTACK)
                {
                    Library.read_padstack_scope(scanner, layer_structure, coordinate_transform, routing_board.library.padstacks);
                }
                else if (next_token == Keyword.VIA)
                {
                    read_via_info(scanner, routing_board);
                }
                else if (next_token == Keyword.VIA_RULE)
                {
                    read_via_rule(scanner, routing_board);
                }
                else if (next_token == Keyword.CLASS)
                {
                    read_net_class(scanner, layer_structure, routing_board);
                }
                else if (next_token == Keyword.SNAP_ANGLE)
                {
                    
                    board.AngleRestriction snap_angle = Structure.read_snap_angle(scanner);
                    if (snap_angle != null)
                    {
                        routing_board.rules.set_trace_angle_restriction(snap_angle);
                    }
                }
                else if (next_token == Keyword.AUTOROUTE_SETTINGS)
                {
                      interactive.AutorouteSettings autoroute_settings 
                              = AutorouteSettings.read_scope(scanner, layer_structure);
                      if (autoroute_settings != null)
                      {
                          p_board_handling.settings.autoroute_settings = autoroute_settings;
                      }
                }
                else
                {
                    ScopeKeyword.skip_scope(scanner);
                }
            }
            if (!read_ok)
            {
                return false;
            }
        }
        return true;
    }
    
    private static void write_rules( WriteScopeParameter p_par, String p_design_name) throws java.io.IOException
    {
        p_par.file.start_scope();
        p_par.file.write("rules PCB ");
        p_par.file.write(p_design_name);
        Structure.write_snap_angle(p_par.file, p_par.board.rules.get_trace_angle_restriction());
        AutorouteSettings.write_scope(p_par.file, p_par.autoroute_settings, 
                p_par.board.layer_structure, p_par.identifier_type);
        // write the default rule using 0 as default layer.
        Rule.write_default_rule(p_par, 0);
        // write the via padstacks
        for (int i = 1; i <= p_par.board.library.padstacks.count(); ++i)
        {
            library.Padstack curr_padstack = p_par.board.library.padstacks.get(i);
            if (p_par.board.library.get_via_padstack(curr_padstack.name )!= null)
            {
                Library.write_padstack_scope(p_par, curr_padstack);
            }
        }
        Network.write_via_infos(p_par.board.rules, p_par.file, p_par.identifier_type);
        Network.write_via_rules(p_par.board.rules, p_par.file, p_par.identifier_type);
        Network.write_net_classes(p_par);
        p_par.file.end_scope();
    }
    
    private static void add_rules(java.util.Collection<Rule>  p_rules, BasicBoard p_board, String p_layer_name)
    {
        int layer_no = -1;
        if (p_layer_name != null)
        {
            layer_no = p_board.layer_structure.get_no(p_layer_name);
            if (layer_no < 0)
            {
                System.out.println("RulesFile.add_rules: layer not found");
            }
        }
        CoordinateTransform coordinate_transform = p_board.communication.coordinate_transform;
        String string_quote = p_board.communication.specctra_parser_info.string_quote;
        for (Rule curr_rule : p_rules)
        {
            if (curr_rule instanceof Rule.WidthRule)
            {
                double wire_width = ((Rule.WidthRule)curr_rule).value;
                int trace_halfwidth = (int)  Math.round(coordinate_transform.dsn_to_board(wire_width) / 2);
                if (layer_no < 0)
                {
                    p_board.rules.set_default_trace_half_widths(trace_halfwidth);
                }
                else
                {
                    p_board.rules.set_default_trace_half_width(layer_no, trace_halfwidth);
                }
            }
            else if (curr_rule instanceof Rule.ClearanceRule)
            {
                Structure.set_clearance_rule(( Rule.ClearanceRule)curr_rule, layer_no, coordinate_transform, p_board.rules, string_quote);
            }
        }
    }
    
    private static boolean add_layer_rules(Scanner p_scanner, BasicBoard  p_board)
    {
        try
        {
            Object next_token = p_scanner.next_token();
            if (!(next_token instanceof String))
            {
                System.out.println("RulesFile.add_layer_rules: String expected");
                return false;
            }
            String layer_string = (String) next_token;
            next_token = p_scanner.next_token();
            while (next_token != Keyword.CLOSED_BRACKET)
            {
                if (next_token != Keyword.OPEN_BRACKET)
                {
                    System.out.println("RulesFile.add_layer_rules: ( expected");
                    return false;
                }
                next_token = p_scanner.next_token();
                if (next_token == Keyword.RULE)
                {
                    java.util.Collection<Rule> curr_rules = Rule.read_scope(p_scanner);
                    add_rules(curr_rules, p_board, layer_string);
                }
                else
                {
                    ScopeKeyword.skip_scope(p_scanner);
                }
                next_token = p_scanner.next_token();
            }
            return true;
        }
        catch (java.io.IOException e)
        {
            System.out.println("RulesFile.add_layer_rules: IO error scanning file");
            return false;
        }
    }
    private static boolean read_via_info(Scanner p_scanner, BasicBoard p_board)
    {
        rules.ViaInfo curr_via_info = Network.read_via_info(p_scanner, p_board);
        if (curr_via_info == null)
        {
            return false;
        }
        rules.ViaInfo existing_via = p_board.rules.via_infos.get(curr_via_info.get_name());
        if (existing_via != null)
        {
            // replace existing via info
             p_board.rules.via_infos.remove(existing_via);
        }
        p_board.rules.via_infos.add(curr_via_info);
        return true;
    }
    
    private static boolean read_via_rule(Scanner p_scanner, BasicBoard p_board)
    {
        java.util.Collection<String> via_rule = Network.read_via_rule(p_scanner, p_board);
        if (via_rule == null)
        {
            return false;
        }
        Network.add_via_rule(via_rule, p_board);
        return true;
    }
    
    private static boolean read_net_class(Scanner p_scanner, LayerStructure p_layer_structure, BasicBoard p_board)
    {
        NetClass curr_class = NetClass.read_scope(p_scanner);
        if (curr_class == null)
        {
            return false;
        }
        Network.insert_net_class(curr_class, p_layer_structure, p_board, p_board.communication.coordinate_transform, false);
        return true;
    }
}
