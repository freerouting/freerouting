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
 * Rule.java
 *
 * Created on 1. Juni 2004, 09:27
 */

package designformats.specctra;

import java.util.Collection;
import java.util.LinkedList;


/**
 * Class for reading and writing rule scopes from dsn-files.
 *
 * @author  Alfons Wirtz
 */
public abstract class Rule
{
    /**
     * Returns a collection of objects of class Rule.
     */
    public static Collection<Rule> read_scope( Scanner p_scanner)
    {
        Collection<Rule> result = new LinkedList<Rule>();
        Object next_token = null;
        for (;;)
        {
            Object prev_token = next_token;
            try
            {
                next_token = p_scanner.next_token();
            }
            catch (java.io.IOException e)
            {
                System.out.println("Rule.read_scope: IO error scanning file");
                System.out.println(e);
                return null;
            }
            if (next_token == null)
            {
                System.out.println("Rule.read_scope: unexpected end of file");
                return null;
            }
            if (next_token == Keyword.CLOSED_BRACKET)
            {
                // end of scope
                break;
            }
            if (prev_token == Keyword.OPEN_BRACKET)
            {
                Rule curr_rule = null;
                if (next_token == Keyword.WIDTH)
                {
                    curr_rule = read_width_rule(p_scanner);
                }
                else if (next_token == Keyword.CLEARANCE)
                {
                    curr_rule = read_clearance_rule(p_scanner);
                }
                else
                {
                    ScopeKeyword.skip_scope(p_scanner);
                }
                if (curr_rule != null)
                {
                    result.add(curr_rule);
                }
                
            }
        }
        return result;
    }
    
    /**
     * Reads a LayerRule from dsn-file.
     */
    public static LayerRule read_layer_rule_scope( Scanner p_scanner)
    {
        try
        {
            Collection<String> layer_names = new LinkedList<String>();
            Collection<Rule> rule_list = new LinkedList<Rule>();
            for (;;)
            {
                p_scanner.yybegin(SpecctraFileScanner.LAYER_NAME);
                Object next_token = p_scanner.next_token();
                if (next_token == Keyword.OPEN_BRACKET)
                {
                    break;
                }
                if (!(next_token instanceof String))
                {
                    
                    System.out.println("Rule.read_layer_rule_scope: string expected");
                    return null;
                }
                layer_names.add((String) next_token);
            }
            for (;;)
            {
                Object next_token = p_scanner.next_token();
                if (next_token == Keyword.CLOSED_BRACKET)
                {
                    break;
                }
                if (next_token != Keyword.RULE)
                {
                    
                    System.out.println("Rule.read_layer_rule_scope: rule expected");
                    return null;
                }
                rule_list.addAll(read_scope(p_scanner));
            }
            return new LayerRule(layer_names, rule_list);
        }
        catch (java.io.IOException e)
        {
            System.out.println("Rule.read_layer_rule_scope: IO error scanning file");
            return null;
        }
    }
    
    public static WidthRule read_width_rule(Scanner p_scanner)
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
                System.out.println("Rule.read_width_rule: number expected");
                return null;
            }
            next_token = p_scanner.next_token();
            if (next_token != Keyword.CLOSED_BRACKET)
            {
                System.out.println("Rule.read_width_rule: closing bracket expected");
                return null;
            }
            return new WidthRule(value);
        }
        catch (java.io.IOException e)
        {
            System.out.println("Rule.read_width_rule: IO error scanning file");
            return null;
        }
    }
    
    public static void write_scope(rules.NetClass p_net_class, WriteScopeParameter p_par) throws java.io.IOException
    {
        p_par.file.start_scope();
        p_par.file.write("rule");
        
        // write the trace width
        int default_trace_half_width = p_net_class.get_trace_half_width(0);
        double trace_width = 2 * p_par.coordinate_transform.board_to_dsn(default_trace_half_width);
        p_par.file.new_line();
        p_par.file.write("(width ");
        p_par.file.write((new Double(trace_width)).toString());
        p_par.file.write(")");
        p_par.file.end_scope();
        for (int i = 1; i < p_par.board.layer_structure.arr.length; ++i)
        {
            if (p_net_class.get_trace_half_width(i) != default_trace_half_width)
            {
                write_layer_rule(p_net_class, i, p_par);
            }
        }
    }
    
    private static void write_layer_rule(rules.NetClass p_net_class, int p_layer_no, WriteScopeParameter p_par) throws java.io.IOException
    {
        p_par.file.start_scope();
        p_par.file.write("layer_rule ");
        
        board.Layer curr_board_layer = p_par.board.layer_structure.arr[p_layer_no];
        
        p_par.file.write(curr_board_layer.name);
        p_par.file.start_scope();
        p_par.file.write("rule ");
        
        int curr_trace_half_width = p_net_class.get_trace_half_width(p_layer_no);
        
        // write the trace width
        double trace_width = 2 * p_par.coordinate_transform.board_to_dsn(curr_trace_half_width);
        p_par.file.new_line();
        p_par.file.write("(width ");
        p_par.file.write((new Double(trace_width)).toString());
        p_par.file.write(") ");
        p_par.file.end_scope();
        p_par.file.end_scope();
    }
    
    /**
     * Writes the default rule as a scope to an output dsn-file.
     */
    public static void write_default_rule(WriteScopeParameter p_par, int p_layer) throws java.io.IOException
    {
        p_par.file.start_scope();
        p_par.file.write("rule");
        // write the trace width
        double trace_width = 2 * p_par.coordinate_transform.board_to_dsn(p_par.board.rules.get_default_net_class().get_trace_half_width(0));
        p_par.file.new_line();
        p_par.file.write("(width ");
        p_par.file.write((new Double(trace_width)).toString());
        p_par.file.write(")");
        // write the default clearance rule
        int default_cl_no = rules.BoardRules.default_clearance_class();
        int default_board_clearance = p_par.board.rules.clearance_matrix.value(default_cl_no, default_cl_no, p_layer);
        double default_clearance = p_par.coordinate_transform.board_to_dsn(default_board_clearance);
        p_par.file.new_line();
        p_par.file.write("(clear ");
        p_par.file.write((new Double(default_clearance)).toString());
        p_par.file.write(")");
        // write the Smd_to_turn_gap
        Double smd_to_turn_dist = p_par.coordinate_transform.board_to_dsn(p_par.board.rules.get_pin_edge_to_turn_dist());
        p_par.file.new_line();
        p_par.file.write("(clear ");
        p_par.file.write(smd_to_turn_dist.toString());
        p_par.file.write(" (type smd_to_turn_gap))");
        int cl_count = p_par.board.rules.clearance_matrix.get_class_count();
        for (int i = 1; i <= cl_count; ++i)
        {
            write_clearance_rules(p_par, p_layer, i, cl_count, default_board_clearance);
        }
        p_par.file.end_scope();
    }
    
    /**
     * Write the clearance rules, which are different from the default clearance.
     */
    private static void write_clearance_rules(WriteScopeParameter p_par,
            int p_layer, int p_cl_class, int p_max_cl_class, int p_default_clearance) throws java.io.IOException
    {
        rules.ClearanceMatrix cl_matrix = p_par.board.rules.clearance_matrix;
        for (int i = p_cl_class; i < p_max_cl_class; ++i)
        {
            int curr_board_clearance = cl_matrix.value(p_cl_class, i, p_layer);
            if (curr_board_clearance == p_default_clearance)
            {
                continue;
            }
            double curr_clearance = p_par.coordinate_transform.board_to_dsn(curr_board_clearance);
            p_par.file.new_line();
            p_par.file.write("(clear ");
            p_par.file.write((new Double(curr_clearance)).toString());
            p_par.file.write(" (type ");
            p_par.identifier_type.write(cl_matrix.get_name(p_cl_class), p_par.file);
            p_par.file.write("_");
            p_par.identifier_type.write(cl_matrix.get_name(i), p_par.file);
            p_par.file.write("))");
        }
    }
    
    public static ClearanceRule read_clearance_rule(Scanner p_scanner)
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
                System.out.println("Rule.read_clearance_rule: number expected");
                return null;
            }
            Collection<String> class_pairs = new LinkedList<String> ();
            next_token = p_scanner.next_token();
            if (next_token != Keyword.CLOSED_BRACKET)
            {
                if (next_token != Keyword.OPEN_BRACKET)
                {
                    System.out.println("Rule.read_clearance_rule: ( expected");
                    return null;
                }
                next_token = p_scanner.next_token();
                if (next_token != Keyword.TYPE)
                {
                    System.out.println("Rule.read_clearance_rule: type expected");
                    return null;
                }
                for (;;)
                {
                    p_scanner.yybegin(SpecctraFileScanner.IGNORE_QUOTE);
                    next_token = p_scanner.next_token();
                    if (next_token == Keyword.CLOSED_BRACKET)
                    {
                        break;
                    }
                    if (!(next_token instanceof String))
                    {
                        System.out.println("Rule.read_clearance_rule: string expected");
                        return null;
                    }
                    class_pairs.add((String)next_token);
                }
                next_token = p_scanner.next_token();
                if (next_token != Keyword.CLOSED_BRACKET)
                {
                    System.out.println("Rule.read_clearance_rule: closing bracket expected");
                    return null;
                }
            }
            return new ClearanceRule(value, class_pairs);
        }
        catch (java.io.IOException e)
        {
            System.out.println("Rule.read_clearance_rule: IO error scanning file");
            return null;
        }
        
    }
    
    static public void write_item_clearance_class( String p_name, datastructures.IndentFileWriter p_file,
            datastructures.IdentifierType p_identifier_type) throws java.io.IOException
    {
        p_file.new_line();
        p_file.write("(clearance_class ");
        p_identifier_type.write(p_name, p_file);
        p_file.write(")");
    }
    
    public static class WidthRule extends Rule
    {
        public WidthRule(double p_value)
        {
            value = p_value;
        }
        final double value;
    }
    
    public static class ClearanceRule extends Rule
    {
        public ClearanceRule(double p_value, Collection<String> p_class_pairs)
        {
            value = p_value;
            clearance_class_pairs = p_class_pairs;
        }
        final double value;
        final Collection<String> clearance_class_pairs;
    }
    
    public static class LayerRule
    {
        LayerRule(Collection<String> p_layer_names, Collection<Rule> p_rules)
        {
            layer_names = p_layer_names;
            rules = p_rules;
        }
        final Collection<String> layer_names;
        final Collection<Rule> rules;
    }
}
