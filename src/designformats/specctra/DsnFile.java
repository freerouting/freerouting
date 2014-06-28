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
 * InputDsnFile.java
 *
 * Created on 10. Mai 2004, 07:43
 */
package designformats.specctra;

import datastructures.IndentFileWriter;

import board.BasicBoard;
import board.TestLevel;

/**
 * Class for reading and writing dsn-files.
 *
 * @author  alfons
 */
public class DsnFile
{

    public enum ReadResult
    {

        OK, OUTLINE_MISSING, ERROR
    }

    /**
     * Creates a routing board from a Specctra dns file.
     * The parameters p_item_observers and p_item_id_no_generator are used,
     * in case the board is embedded into a host system.
     * Returns false, if an error occured.
     */
    public static ReadResult read(java.io.InputStream p_input_stream, interactive.BoardHandling p_board_handling,
                                  board.BoardObservers p_observers, datastructures.IdNoGenerator p_item_id_no_generator, TestLevel p_test_level)
    {
        Scanner scanner = new SpecctraFileScanner(p_input_stream);
        Object curr_token = null;
        for (int i = 0; i < 3; ++i)
        {
            try
            {
                curr_token = scanner.next_token();
            }
            catch (java.io.IOException e)
            {
                System.out.println("DsnFile.read: IO error scanning file");
                System.out.println(e);
                return ReadResult.ERROR;
            }
            boolean keyword_ok = true;
            if (i == 0)
            {
                keyword_ok = (curr_token == Keyword.OPEN_BRACKET);
            }
            else if (i == 1)
            {
                keyword_ok = (curr_token == Keyword.PCB_SCOPE);
                scanner.yybegin(SpecctraFileScanner.NAME); // to overread the name of the pcb for i = 2
            }
            if (!keyword_ok)
            {
                System.out.println("DsnFile.read: specctra dsn file format expected");
                return ReadResult.ERROR;
            }
        }
        ReadScopeParameter read_scope_par =
                new ReadScopeParameter(scanner, p_board_handling, p_observers, p_item_id_no_generator, p_test_level);
        boolean read_ok = Keyword.PCB_SCOPE.read_scope(read_scope_par);
        ReadResult result;
        if (read_ok)
        {
            result = ReadResult.OK;
            if (read_scope_par.autoroute_settings == null)
            {
                // look for power planes with incorrect layer type and adjust autoroute parameters
                adjust_plane_autoroute_settings(p_board_handling);
            }
        }
        else if (!read_scope_par.board_outline_ok)
        {
            result = ReadResult.OUTLINE_MISSING;
        }
        else
        {
            result = ReadResult.ERROR;
        }
        //tests.Validate.check("after reading dsn", read_scope_par.board_handling.get_routing_board());
        return result;
    }

    /**
     * Sets contains_plane to true for nets with a conduction_area covering a 
     * large part of a signal layer, if that layer does not contain any traces
     * This is useful in case the layer type was not set correctly to plane in the dsn-file.
     * Returns true, if something was changed.
     */
    private static boolean adjust_plane_autoroute_settings(interactive.BoardHandling p_board_handling)
    {
        BasicBoard routing_board = p_board_handling.get_routing_board();
        board.LayerStructure board_layer_structure = routing_board.layer_structure;
        if (board_layer_structure.arr.length <= 2)
        {
            return false;
        }
        for (board.Layer curr_layer : board_layer_structure.arr)
        {
            if (!curr_layer.is_signal)
            {
                return false;
            }
        }
        boolean[] layer_contains_wires_arr = new boolean[board_layer_structure.arr.length];
        boolean[] changed_layer_arr = new boolean[board_layer_structure.arr.length];
        for (int i = 0; i < layer_contains_wires_arr.length; ++i)
        {
            layer_contains_wires_arr[i] = false;
            changed_layer_arr[i] = false;
        }
        java.util.Collection<board.ConductionArea> conduction_area_list = new java.util.LinkedList<board.ConductionArea>();
        java.util.Collection<board.Item> item_list = routing_board.get_items();
        for (board.Item curr_item : item_list)
        {
            if (curr_item instanceof board.Trace)
            {
                int curr_layer = ((board.Trace) curr_item).get_layer();
                layer_contains_wires_arr[curr_layer] = true;
            }
            else if (curr_item instanceof board.ConductionArea)
            {
                conduction_area_list.add((board.ConductionArea) curr_item);
            }
        }
        boolean nothing_changed = true;

        board.BoardOutline board_outline = routing_board.get_outline();
        double board_area = 0;
        for (int i = 0; i < board_outline.shape_count(); ++i)
        {
            geometry.planar.TileShape[] curr_piece_arr = board_outline.get_shape(i).split_to_convex();
            if (curr_piece_arr != null)
            {
                for (geometry.planar.TileShape curr_piece : curr_piece_arr)
                {
                    board_area += curr_piece.area();
                }
            }
        }
        for (board.ConductionArea curr_conduction_area : conduction_area_list)
        {
            int layer_no = curr_conduction_area.get_layer();
            if (layer_contains_wires_arr[layer_no])
            {
                continue;
            }
            board.Layer curr_layer = routing_board.layer_structure.arr[layer_no];
            if (!curr_layer.is_signal || layer_no == 0 || layer_no == board_layer_structure.arr.length - 1)
            {
                continue;
            }
            geometry.planar.TileShape[] convex_pieces = curr_conduction_area.get_area().split_to_convex();
            double curr_area = 0;
            for (geometry.planar.TileShape curr_piece : convex_pieces)
            {
                curr_area += curr_piece.area();
            }
            if (curr_area < 0.5 * board_area)
            {
                // skip conduction areas not covering most of the board
                continue;
            }

            for (int i = 0; i < curr_conduction_area.net_count(); ++i)
            {
                rules.Net curr_net = routing_board.rules.nets.get(curr_conduction_area.get_net_no(i));
                curr_net.set_contains_plane(true);
                nothing_changed = false;
            }

            changed_layer_arr[layer_no] = true;
            if (curr_conduction_area.get_fixed_state().ordinal() < board.FixedState.USER_FIXED.ordinal())
            {
                curr_conduction_area.set_fixed_state(board.FixedState.USER_FIXED);
            }
        }
        if (nothing_changed)
        {
            return false;
        }
        // Adjust the layer prefered directions in the autoroute settings.
        // and deactivate the changed layers.
        interactive.AutorouteSettings autoroute_settings = p_board_handling.settings.autoroute_settings;
        int layer_count = routing_board.get_layer_count();
        boolean curr_preferred_direction_is_horizontal =
                autoroute_settings.get_preferred_direction_is_horizontal(0);
        for (int i = 0; i < layer_count; ++i)
        {
            if (changed_layer_arr[i])
            {
                autoroute_settings.set_layer_active(i, false);
            }
            else if (autoroute_settings.get_layer_active(i))
            {
                autoroute_settings.set_preferred_direction_is_horizontal(i, curr_preferred_direction_is_horizontal);
                curr_preferred_direction_is_horizontal = !curr_preferred_direction_is_horizontal;
            }
        }
        return true;
    }

    /**
     * Writes p_board to a text file in the Specctra dsn format.
     * Returns false, if the write failed.
     * If p_compat_mode is true, only standard speecctra dsn scopes are written, so that any
     * host system with an specctra interface can read them.
     */
    public static boolean write(interactive.BoardHandling p_board_handling, java.io.OutputStream p_file, String p_design_name, boolean p_compat_mode)
    {
        //tests.Validate.check("before writing dsn", p_board);
        IndentFileWriter output_file = new IndentFileWriter(p_file);
        if (output_file == null)
        {
            System.out.println("unable to write dsn file");
            return false;
        }

        try
        {
            write_pcb_scope(p_board_handling, output_file, p_design_name, p_compat_mode);
        }
        catch (java.io.IOException e)
        {
            System.out.println("unable to write dsn file");
            return false;
        }
        try
        {
            output_file.close();
        }
        catch (java.io.IOException e)
        {
            System.out.println("unable to close dsn file");
            return false;
        }
        return true;
    }

    private static void write_pcb_scope(interactive.BoardHandling p_board_handling, IndentFileWriter p_file, String p_design_name, boolean p_compat_mode)
            throws java.io.IOException
    {
        BasicBoard routing_board = p_board_handling.get_routing_board();
        WriteScopeParameter write_scope_parameter =
                new WriteScopeParameter(routing_board, p_board_handling.settings.autoroute_settings, p_file,
                routing_board.communication.specctra_parser_info.string_quote,
                routing_board.communication.coordinate_transform, p_compat_mode);

        p_file.start_scope();
        p_file.write("PCB ");
        write_scope_parameter.identifier_type.write(p_design_name, p_file);
        Parser.write_scope(write_scope_parameter.file,
                write_scope_parameter.board.communication.specctra_parser_info, write_scope_parameter.identifier_type, false);
        Resolution.write_scope(p_file, routing_board.communication);
        Structure.write_scope(write_scope_parameter);
        Placement.write_scope(write_scope_parameter);
        Library.write_scope(write_scope_parameter);
        PartLibrary.write_scope(write_scope_parameter);
        Network.write_scope(write_scope_parameter);
        Wiring.write_scope(write_scope_parameter);
        p_file.end_scope();
    }

    static boolean read_on_off_scope(Scanner p_scanner)
    {
        try
        {
            Object next_token = p_scanner.next_token();
            boolean result = false;
            if (next_token == Keyword.ON)
            {
                result = true;
            }
            else if (next_token != Keyword.OFF)
            {
                System.out.println("DsnFile.read_boolean: Keyword.OFF expected");
            }
            ScopeKeyword.skip_scope(p_scanner);
            return result;
        }
        catch (java.io.IOException e)
        {
            System.out.println("DsnFile.read_boolean: IO error scanning file");
            return false;
        }
    }

    static int read_integer_scope(Scanner p_scanner)
    {
        try
        {
            int value;
            Object next_token = p_scanner.next_token();
            if (next_token instanceof Integer)
            {
                value = ((Integer) next_token).intValue();
            }
            else
            {
                System.out.println("DsnFile.read_integer_scope: number expected");
                return 0;
            }
            next_token = p_scanner.next_token();
            if (next_token != Keyword.CLOSED_BRACKET)
            {
                System.out.println("DsnFile.read_integer_scope: closing bracket expected");
                return 0;
            }
            return value;
        }
        catch (java.io.IOException e)
        {
            System.out.println("DsnFile.read_integer_scope: IO error scanning file");
            return 0;
        }
    }

    static double read_float_scope(Scanner p_scanner)
    {
        try
        {
            double value;
            Object next_token = p_scanner.next_token();
            if (next_token instanceof Double)
            {
                value = ((Double) next_token).doubleValue();
            }
            else if (next_token instanceof Integer)
            {
                value = ((Integer) next_token).intValue();
            }
            else
            {
                System.out.println("DsnFile.read_float_scope: number expected");
                return 0;
            }
            next_token = p_scanner.next_token();
            if (next_token != Keyword.CLOSED_BRACKET)
            {
                System.out.println("DsnFile.read_float_scope: closing bracket expected");
                return 0;
            }
            return value;
        }
        catch (java.io.IOException e)
        {
            System.out.println("DsnFile.read_float_scope: IO error scanning file");
            return 0;
        }
    }

    public static String read_string_scope(Scanner p_scanner)
    {
        try
        {
            p_scanner.yybegin(SpecctraFileScanner.NAME);
            Object next_token = p_scanner.next_token();
            if (!(next_token instanceof String))
            {
                System.out.println("DsnFile:read_string_scope: String expected");
                return null;
            }
            String result = (String) next_token;
            next_token = p_scanner.next_token();
            if (next_token != Keyword.CLOSED_BRACKET)
            {
                System.out.println("DsnFile.read_string_scope: closing bracket expected");
            }
            return result;
        }
        catch (java.io.IOException e)
        {
            System.out.println("DsnFile.read_string_scope: IO error scanning file");
            return null;
        }
    }

    public static java.util.Collection<String> read_string_list_scope(Scanner p_scanner)
    {
        java.util.Collection<String> result = new java.util.LinkedList<String>();
        try
        {
            for (;;)
            {
                p_scanner.yybegin(SpecctraFileScanner.NAME);
                Object next_token = p_scanner.next_token();
                if (next_token == Keyword.CLOSED_BRACKET)
                {
                    break;
                }
                if (!(next_token instanceof String))
                {
                    System.out.println("DsnFileread_string_list_scope: string expected");
                    return null;
                }
                result.add((String) next_token);
            }
        }
        catch (java.io.IOException e)
        {
            System.out.println("DsnFile.read_string_list_scope: IO error scanning file");
        }
        return result;
    }
    static final String CLASS_CLEARANCE_SEPARATOR = "-";
}
