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
 * NetClass.java
 *
 * Created on 13. April 2005, 06:55
 */

package designformats.specctra;

import java.util.Collection;
import java.util.LinkedList;

/**
 * Contains the information of a Specctra Class scope.
 *
 * @author alfons
 */
public class NetClass
{
    
    public static NetClass read_scope(Scanner p_scanner)
    {
        
        try
        {
            // read the class name
            p_scanner.yybegin(SpecctraFileScanner.NAME);
            Object next_token = p_scanner.next_token();
            if (!(next_token instanceof String))
            {
                System.out.println("NetClass.read_scope: String expected");
                return null;
            }
            String class_name = (String) next_token;
            Collection<String> net_list = new LinkedList<String>();
            boolean rules_missing = false;
            // read the nets belonging to the class
            for (;;)
            {
                p_scanner.yybegin(SpecctraFileScanner.NAME);
                next_token = p_scanner.next_token();
                if (next_token == Keyword.OPEN_BRACKET)
                {
                    break;
                }
                if (next_token == Keyword.CLOSED_BRACKET)
                {
                    rules_missing = true;
                    break;
                }
                if (!(next_token instanceof String))
                {
                    System.out.println("NetClass.read_scope: String expected");
                    return null;
                }
                net_list.add((String) next_token);
            }
            Collection<Rule> rules = new LinkedList<Rule>();
            Collection<Rule.LayerRule> layer_rules = new LinkedList<Rule.LayerRule>();
            Collection<String> use_via = new LinkedList<String>();
            Collection<String> use_layer = new LinkedList<String>();
            String via_rule = null;
            String trace_clearance_class = null;
            boolean pull_tight = true;
            boolean shove_fixed = false;
            double min_trace_length = 0;
            double max_trace_length = 0;
            if (!rules_missing)
            {
                Object prev_token = next_token;
                for (;;)
                {
                    next_token = p_scanner.next_token();
                    if (next_token == null)
                    {
                        System.out.println("NetClass.read_scope: unexpected end of file");
                        return null;
                    }
                    if (next_token == Keyword.CLOSED_BRACKET)
                    {
                        // end of scope
                        break;
                    }
                    if (prev_token == Keyword.OPEN_BRACKET)
                    {
                        if (next_token == Keyword.RULE)
                        {
                            rules.addAll(Rule.read_scope(p_scanner));
                        }
                        else if (next_token == Keyword.LAYER_RULE)
                        {
                            layer_rules.add(Rule.read_layer_rule_scope(p_scanner));
                        }
                        else if (next_token == Keyword.VIA_RULE)
                        {
                            via_rule = DsnFile.read_string_scope(p_scanner);
                        }
                        else if (next_token == Keyword.CIRCUIT)
                        {
                            Circuit.ReadScopeResult curr_rule = Circuit.read_scope(p_scanner);
                            if (curr_rule != null)
                            {
                                max_trace_length = curr_rule.max_length;
                                min_trace_length = curr_rule.min_length;
                                use_via.addAll(curr_rule.use_via);
                                use_layer.addAll(curr_rule.use_layer);
                            }
                        }
                        else if (next_token == Keyword.CLEARANCE_CLASS)
                        {
                            trace_clearance_class = DsnFile.read_string_scope(p_scanner);
                            if (trace_clearance_class == null)
                            {
                                return null;
                            }
                        }
                        else if (next_token == Keyword.SHOVE_FIXED)
                        {
                            shove_fixed = DsnFile.read_on_off_scope(p_scanner);
                        }
                        else if (next_token == Keyword.PULL_TIGHT)
                        {
                            pull_tight = DsnFile.read_on_off_scope(p_scanner);
                        }
                        else
                        {
                            ScopeKeyword.skip_scope(p_scanner);
                        }
                    }
                    prev_token = next_token;
                }
            }
            return new NetClass(class_name,  trace_clearance_class, net_list, rules, layer_rules,
                    use_via, use_layer, via_rule, shove_fixed, pull_tight, min_trace_length, max_trace_length);
        }
        catch (java.io.IOException e)
        {
            System.out.println("NetClass.read_scope: IO error while scanning file");
            return null;
        }
    }
    
    public static ClassClass read_class_class_scope(Scanner p_scanner)
    {
        try
        {
            Collection<String> classes = new LinkedList<String>();
            Collection<Rule> rules = new LinkedList<Rule>();
            Collection<Rule.LayerRule> layer_rules = new LinkedList<Rule.LayerRule>();
            Object prev_token = null;
            for (;;)
            {
                Object next_token = p_scanner.next_token();
                if (next_token == null)
                {
                    System.out.println("ClassClass.read_scope: unexpected end of file");
                    return null;
                }
                if (next_token == Keyword.CLOSED_BRACKET)
                {
                    // end of scope
                    break;
                }
                if (prev_token == Keyword.OPEN_BRACKET)
                {
                    if (next_token == Keyword.CLASSES)
                    {
                        classes.addAll(DsnFile.read_string_list_scope(p_scanner));
                    }
                    else if (next_token == Keyword.RULE)
                    {
                        rules.addAll(Rule.read_scope(p_scanner));
                    }
                    else if (next_token == Keyword.LAYER_RULE)
                    {
                        layer_rules.add(Rule.read_layer_rule_scope(p_scanner));
                    }
                }
                prev_token = next_token;
            }
            return new ClassClass(classes, rules, layer_rules);
        }
        catch (java.io.IOException e)
        {
            System.out.println("NetClass.read_scope: IO error while scanning file");
            return null;
        }
    }
    
    /** Creates a new instance of NetClass */
    public NetClass(String p_name, String p_trace_clearance_class, Collection<String> p_net_list,
            Collection<Rule> p_rules, Collection<Rule.LayerRule> p_layer_rules, Collection<String> p_use_via,
            Collection<String> p_use_layer, String p_via_rule, boolean p_shove_fixed, boolean p_pull_tight, 
            double  p_min_trace_length, double p_max_trace_length)
    {
        name = p_name;
        trace_clearance_class = p_trace_clearance_class;
        net_list = p_net_list;
        rules = p_rules;
        layer_rules = p_layer_rules;
        use_via = p_use_via;
        use_layer = p_use_layer;
        via_rule = p_via_rule;
        shove_fixed = p_shove_fixed;
        pull_tight = p_pull_tight;
        min_trace_length = p_min_trace_length;
        max_trace_length = p_max_trace_length;
    }
    
    public final String name;
    public final String trace_clearance_class;
    public final Collection<String> net_list;
    public final Collection<Rule> rules;
    public final Collection<Rule.LayerRule> layer_rules;
    public final Collection<String> use_via;
    public final Collection<String> use_layer;
    public final String via_rule;
    public final boolean shove_fixed;
    public final boolean pull_tight;
    public final double min_trace_length;
    public final double max_trace_length;
    
    public static class ClassClass
    {
        public ClassClass( Collection<String> p_class_names, Collection<Rule> p_rules,
                Collection<Rule.LayerRule> p_layer_rules)
        {
            class_names = p_class_names;
            rules = p_rules;
            layer_rules = p_layer_rules;
        }
        public final Collection<String> class_names;
        public final Collection<Rule> rules;
        public final Collection<Rule.LayerRule> layer_rules;
    }
}
