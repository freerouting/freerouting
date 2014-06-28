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
 * AutorouteSettings.java
 *
 * Created on 1. Maerz 2007, 07:10
 *
 */
package designformats.specctra;

import datastructures.IndentFileWriter;
import datastructures.IdentifierType;

/**
 *
 * @author Alfons Wirtz
 */
public class AutorouteSettings
{

    static interactive.AutorouteSettings read_scope(Scanner p_scanner, LayerStructure p_layer_structure)
    {
        interactive.AutorouteSettings result = new interactive.AutorouteSettings(p_layer_structure.arr.length);
        boolean with_fanout = false;
        boolean with_autoroute = true;
        boolean with_postroute = true;
        Object next_token = null;
        for (;;)
        {
            Object prev_token = next_token;
            try
            {
                next_token = p_scanner.next_token();
            } catch (java.io.IOException e)
            {
                System.out.println("AutorouteSettings.read_scope: IO error scanning file");
                return null;
            }
            if (next_token == null)
            {
                System.out.println("AutorouteSettings.read_scope: unexpected end of file");
                return null;
            }
            if (next_token == Keyword.CLOSED_BRACKET)
            {
                // end of scope
                break;
            }
            if (prev_token == Keyword.OPEN_BRACKET)
            {
                if (next_token == Keyword.FANOUT)
                {
                    with_fanout = DsnFile.read_on_off_scope(p_scanner);
                }
                else if (next_token == Keyword.AUTOROUTE)
                {
                    with_autoroute = DsnFile.read_on_off_scope(p_scanner);
                }
                else if (next_token == Keyword.POSTROUTE)
                {
                    with_postroute = DsnFile.read_on_off_scope(p_scanner);
                }
                else if (next_token == Keyword.VIAS)
                {
                    result.set_vias_allowed(DsnFile.read_on_off_scope(p_scanner));
                }
                else if (next_token == Keyword.VIA_COSTS)
                {
                    result.set_via_costs(DsnFile.read_integer_scope(p_scanner));
                }
                else if (next_token == Keyword.PLANE_VIA_COSTS)
                {
                    result.set_plane_via_costs(DsnFile.read_integer_scope(p_scanner));
                }
                else if (next_token == Keyword.START_RIPUP_COSTS)
                {
                    result.set_start_ripup_costs(DsnFile.read_integer_scope(p_scanner));
                }
                else if (next_token == Keyword.START_PASS_NO)
                {
                    result.set_pass_no(DsnFile.read_integer_scope(p_scanner));
                }
                else if (next_token == Keyword.LAYER_RULE)
                {
                    result = read_layer_rule(p_scanner, p_layer_structure, result);
                    if (result == null)
                    {
                        return null;
                    }
                }
                else
                {
                    ScopeKeyword.skip_scope(p_scanner);
                }
            }
        }
        result.set_with_fanout(with_fanout);
        result.set_with_autoroute(with_autoroute);
        result.set_with_postroute(with_postroute);
        return result;
    }

    static interactive.AutorouteSettings read_layer_rule(Scanner p_scanner, LayerStructure p_layer_structure,
            interactive.AutorouteSettings p_settings)
    {
        p_scanner.yybegin(SpecctraFileScanner.NAME);
        Object next_token;
        try
        {
            next_token = p_scanner.next_token();
        } catch (java.io.IOException e)
        {
            System.out.println("AutorouteSettings.read_layer_rule: IO error scanning file");
            return null;
        }
        if (!(next_token instanceof String))
        {
            System.out.println("AutorouteSettings.read_layer_rule: String expected");
            return null;
        }
        int layer_no = p_layer_structure.get_no((String) next_token);
        if (layer_no < 0)
        {
            System.out.println("AutorouteSettings.read_layer_rule: layer not found");
            return null;
        }
        for (;;)
        {
            Object prev_token = next_token;
            try
            {
                next_token = p_scanner.next_token();
            } catch (java.io.IOException e)
            {
                System.out.println("AutorouteSettings.read_layer_rule: IO error scanning file");
                return null;
            }
            if (next_token == null)
            {
                System.out.println("AutorouteSettings.read_layer_rule: unexpected end of file");
                return null;
            }
            if (next_token == Keyword.CLOSED_BRACKET)
            {
                // end of scope
                break;
            }
            if (prev_token == Keyword.OPEN_BRACKET)
            {
                if (next_token == Keyword.ACTIVE)
                {
                    p_settings.set_layer_active(layer_no, DsnFile.read_on_off_scope(p_scanner));
                }
                else if (next_token == Keyword.PREFERRED_DIRECTION)
                {
                    try
                    {
                        boolean pref_dir_is_horizontal = true;
                        next_token = p_scanner.next_token();
                        if (next_token == Keyword.VERTICAL)
                        {
                            pref_dir_is_horizontal = false;
                        }
                        else if (next_token != Keyword.HORIZONTAL)
                        {
                            System.out.println("AutorouteSettings.read_layer_rule: unexpected key word");
                            return null;
                        }
                        p_settings.set_preferred_direction_is_horizontal(layer_no, pref_dir_is_horizontal);
                        next_token = p_scanner.next_token();
                        if (next_token != Keyword.CLOSED_BRACKET)
                        {
                            System.out.println("AutorouteSettings.read_layer_rule: uclosing bracket expected");
                            return null;
                        }
                    } catch (java.io.IOException e)
                    {
                        System.out.println("AutorouteSettings.read_layer_rule: IO error scanning file");
                        return null;
                    }
                }
                else if (next_token == Keyword.PREFERRED_DIRECTION_TRACE_COSTS)
                {
                    p_settings.set_preferred_direction_trace_costs(layer_no, DsnFile.read_float_scope(p_scanner));
                }
                else if (next_token == Keyword.AGAINST_PREFERRED_DIRECTION_TRACE_COSTS)
                {
                    p_settings.set_against_preferred_direction_trace_costs(layer_no, DsnFile.read_float_scope(p_scanner));
                }
                else
                {
                    ScopeKeyword.skip_scope(p_scanner);
                }
            }
        }
        return p_settings;
    }

    static void write_scope(IndentFileWriter p_file, interactive.AutorouteSettings p_settings,
            board.LayerStructure p_layer_structure, IdentifierType p_identifier_type) throws java.io.IOException
    {
        p_file.start_scope();
        p_file.write("autoroute_settings");
        p_file.new_line();
        p_file.write("(fanout ");
        if (p_settings.get_with_fanout())
        {
            p_file.write("on)");
        }
        else
        {
            p_file.write("off)");
        }
        p_file.new_line();
        p_file.write("(autoroute ");
        if (p_settings.get_with_autoroute())
        {
            p_file.write("on)");
        }
        else
        {
            p_file.write("off)");
        }
        p_file.new_line();
        p_file.write("(postroute ");
        if (p_settings.get_with_postroute())
        {
            p_file.write("on)");
        }
        else
        {
            p_file.write("off)");
        }
        p_file.new_line();
        p_file.write("(vias ");
        if (p_settings.get_vias_allowed())
        {
            p_file.write("on)");
        }
        else
        {
            p_file.write("off)");
        }
        p_file.new_line();
        p_file.write("(via_costs ");
        {
            Integer via_costs = p_settings.get_via_costs();
            p_file.write(via_costs.toString());
        }
        p_file.write(")");
        p_file.new_line();
        p_file.write("(plane_via_costs ");
        {
            Integer via_costs = p_settings.get_plane_via_costs();
            p_file.write(via_costs.toString());
        }
        p_file.write(")");
        p_file.new_line();
        p_file.write("(start_ripup_costs ");
        {
            Integer ripup_costs = p_settings.get_start_ripup_costs();
            p_file.write(ripup_costs.toString());
        }
        p_file.write(")");
        p_file.new_line();
        p_file.write("(start_pass_no ");
        {
            Integer pass_no = p_settings.get_pass_no();
            p_file.write(pass_no.toString());
        }
        p_file.write(")");
        for (int i = 0; i < p_layer_structure.arr.length; ++i)
        {
            board.Layer curr_layer = p_layer_structure.arr[i];
            p_file.start_scope();
            p_file.write("layer_rule ");
            p_identifier_type.write(curr_layer.name, p_file);
            p_file.new_line();
            p_file.write("(active ");
            if (p_settings.get_layer_active(i))
            {
                p_file.write("on)");
            }
            else
            {
                p_file.write("off)");
            }
            p_file.new_line();
            p_file.write("(preferred_direction ");
            if (p_settings.get_preferred_direction_is_horizontal(i))
            {
                p_file.write("horizontal)");
            }
            else
            {
                p_file.write("vertical)");
            }
            p_file.new_line();
            p_file.write("(preferred_direction_trace_costs ");
            Float trace_costs = (float) p_settings.get_preferred_direction_trace_costs(i);
            p_file.write(trace_costs.toString());
            p_file.write(")");
            p_file.new_line();
            p_file.write("(against_preferred_direction_trace_costs ");
            trace_costs = (float) p_settings.get_against_preferred_direction_trace_costs(i);
            p_file.write(trace_costs.toString());
            p_file.write(")");
            p_file.end_scope();
        }
        p_file.end_scope();
    }
}
