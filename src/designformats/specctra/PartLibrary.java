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
 * PartLibrary.java
 *
 * Created on 23. Maerz 2005, 08:36
 */

package designformats.specctra;

/**
 *
 * @author Alfons Wirtz
 */
public class PartLibrary  extends ScopeKeyword
{
    
    /** Creates a new instance of PartLibrary */
    public PartLibrary()
    {
        super("part_library");
    }
    
    public boolean read_scope(ReadScopeParameter p_par)
    {
        Object next_token = null;
        for (;;)
        {
            Object prev_token = next_token;
            try
            {
                next_token = p_par.scanner.next_token();
            }
            catch (java.io.IOException e)
            {
                System.out.println("PartLibrary.read_scope: IO error scanning file");
                System.out.println(e);
                return false;
            }
            if (next_token == null)
            {
                System.out.println("PartLibrary.read_scope: unexpected end of file");
                return false;
            }
            if (next_token == CLOSED_BRACKET)
            {
                // end of scope
                break;
            }
            if (prev_token == OPEN_BRACKET)
            {
                if (next_token == Keyword.LOGICAL_PART_MAPPING)
                {
                    LogicalPartMapping next_mapping = read_logical_part_mapping(p_par.scanner);
                    if (next_mapping == null)
                    {
                        return false;
                    }
                    p_par.logical_part_mappings.add(next_mapping);
                }
                else if (next_token == Keyword.LOGICAL_PART)
                {
                    LogicalPart next_part = read_logical_part(p_par.scanner);
                    if (next_part == null)
                    {
                        return false;
                    }
                    p_par.logical_parts.add(next_part);
                }
                else
                {
                    skip_scope(p_par.scanner);
                }
            }
        }
        return true;
    }
    
    public static void write_scope(WriteScopeParameter p_par) throws java.io.IOException
    {
        library.LogicalParts logical_parts = p_par.board.library.logical_parts;
        if (logical_parts.count() <= 0)
        {
            return;
        }
        p_par.file.start_scope();
        p_par.file.write("part_library");
        
        // write the logical part mappings
        
        for (int i = 1; i <=  logical_parts.count(); ++i)
        {
            library.LogicalPart curr_part =  logical_parts.get(i);
            p_par.file.start_scope();
            p_par.file.write("logical_part_mapping ");
            p_par.identifier_type.write(curr_part.name, p_par.file);
            p_par.file.new_line();
            p_par.file.write("(comp");
            for (int j  = 1; j <= p_par.board.components.count(); ++j)
            {
                board.Component curr_compomnent = p_par.board.components.get(j);
                if (curr_compomnent.get_logical_part() == curr_part)
                {
                    p_par.file.write(" ");
                    p_par.file.write(curr_compomnent.name);
                }
            }
            p_par.file.write(")");
            p_par.file.end_scope();
        }
        
        // write the logical parts.
        
        for (int i = 1; i <=  logical_parts.count(); ++i)
        {
            library.LogicalPart curr_part =  logical_parts.get(i);
            
            p_par.file.start_scope();
            p_par.file.write("logical_part ");
            p_par.identifier_type.write(curr_part.name, p_par.file);
            p_par.file.new_line();
            for (int j = 0; j < curr_part.pin_count(); ++j)
            {
                p_par.file.new_line();
                library.LogicalPart.PartPin curr_pin =  curr_part.get_pin(j);
                p_par.file.write("(pin ");
                p_par.identifier_type.write(curr_pin.pin_name, p_par.file);
                p_par.file.write(" 0 ");
                p_par.identifier_type.write(curr_pin.gate_name, p_par.file);
                p_par.file.write(" ");
                Integer gate_swap_code = curr_pin.gate_swap_code;
                p_par.file.write(gate_swap_code.toString());
                p_par.file.write(" ");
                p_par.identifier_type.write(curr_pin.gate_pin_name, p_par.file);
                p_par.file.write(" ");
                Integer gate_pin_swap_code = curr_pin.gate_pin_swap_code;
                p_par.file.write(gate_pin_swap_code.toString());
                p_par.file.write(")");
            }
            p_par.file.end_scope();
        }
        p_par.file.end_scope();
    }
    
    /**
     * Reads the component list of a logical part mapping.
     * Returns null, if an error occured.
     */
    private LogicalPartMapping read_logical_part_mapping(Scanner p_scanner)
    {
        try
        {
            Object next_token = p_scanner.next_token();
            if (!(next_token instanceof String))
            {
                System.out.println("PartLibrary.read_logical_part_mapping: string expected");
                return null;
            }
            String name = (String) next_token;
            next_token = p_scanner.next_token();
            if (next_token != Keyword.OPEN_BRACKET)
            {
                System.out.println("PartLibrary.read_logical_part_mapping: open bracket expected");
                return null;
            }
            next_token = p_scanner.next_token();
            if (next_token != Keyword.COMPONENT_SCOPE)
            {
                System.out.println("PartLibrary.read_logical_part_mapping: Keyword.COMPONENT_SCOPE expected");
                return null;
            }
            java.util.SortedSet <String> result = new java.util.TreeSet<String>();
            for(;;)
            {
                p_scanner.yybegin(SpecctraFileScanner.NAME);
                next_token = p_scanner.next_token();
                if (next_token == Keyword.CLOSED_BRACKET)
                {
                    break;
                }
                if (!(next_token instanceof String))
                {
                    System.out.println("PartLibrary.read_logical_part_mapping: string expected");
                    return null;
                }
                result.add((String) next_token);
            }
            next_token = p_scanner.next_token();
            if (next_token != Keyword.CLOSED_BRACKET)
            {
                System.out.println("PartLibrary.read_logical_part_mapping: closing bracket expected");
                return null;
            }
            return new LogicalPartMapping(name, result);
        }
        catch (java.io.IOException e)
        {
            System.out.println("PartLibrary.read_logical_part_mapping: IO error scanning file");
            return null;
        }
    }
    
    private LogicalPart read_logical_part(Scanner p_scanner)
    {
        java.util.Collection<PartPin> part_pins = new java.util.LinkedList<PartPin>();
        Object next_token = null;
        try
        {
            next_token = p_scanner.next_token();
        }
        catch (java.io.IOException e)
        {
            System.out.println("PartLibrary.read_logical_part: IO error scanning file");
            return null;
        }
        if (!(next_token instanceof String))
        {
            System.out.println("PartLibrary.read_logical_part: string expected");
            return null;
        }
        String part_name = (String) next_token;
        for (;;)
        {
            Object prev_token = next_token;
            try
            {
                next_token = p_scanner.next_token();
            }
            catch (java.io.IOException e)
            {
                System.out.println("PartLibrary.read_logical_part: IO error scanning file");
                return null;
            }
            if (next_token == null)
            {
                System.out.println("PartLibrary.read_logical_part: unexpected end of file");
                return null;
            }
            if (next_token == CLOSED_BRACKET)
            {
                // end of scope
                break;
            }
            boolean read_ok = true;
            if (prev_token == OPEN_BRACKET)
            {
                if (next_token == Keyword.PIN)
                {
                    PartPin curr_part_pin = read_part_pin(p_scanner);
                    if (curr_part_pin == null)
                    {
                        return null;
                    }
                    part_pins.add(curr_part_pin);
                }
                else
                {
                    skip_scope(p_scanner);
                }
            }
            if (!read_ok)
            {
                return null;
            }
        }
        return new LogicalPart(part_name, part_pins);
    }
    
    private PartPin read_part_pin(Scanner p_scanner)
    {
        try
        {
            p_scanner.yybegin(SpecctraFileScanner.NAME);
            Object next_token = p_scanner.next_token();
            if (!(next_token instanceof String))
            {
                System.out.println("PartLibrary.read_part_pin: string expected");
                return null;
            }
            String pin_name = (String) next_token;
            next_token = p_scanner.next_token();
            if (!(next_token instanceof Integer))
            {
                System.out.println("PartLibrary.read_part_pin: integer expected");
                return null;
            }
            p_scanner.yybegin(SpecctraFileScanner.NAME);
            next_token = p_scanner.next_token();
            if (!(next_token instanceof String))
            {
                System.out.println("PartLibrary.read_part_pin: string expected");
                return null;
            }
            String gate_name = (String) next_token;
            next_token = p_scanner.next_token();
            if (!(next_token instanceof Integer))
            {
                System.out.println("PartLibrary.read_part_pin: integer expected");
                return null;
            }
            int gate_swap_code = (Integer) next_token;
            p_scanner.yybegin(SpecctraFileScanner.NAME);
            next_token = p_scanner.next_token();
            if (!(next_token instanceof String))
            {
                System.out.println("PartLibrary.read_part_pin: string expected");
                return null;
            }
            String gate_pin_name = (String) next_token;
            next_token = p_scanner.next_token();
            if (!(next_token instanceof Integer))
            {
                System.out.println("PartLibrary.read_part_pin: integer expected");
                return null;
            }
            int gate_pin_swap_code = (Integer) next_token;
            // overread subgates
            for (;;)
            {
                next_token = p_scanner.next_token();
                if (next_token == Keyword.CLOSED_BRACKET)
                {
                    break;
                }
            }
            return new PartPin(pin_name, gate_name, gate_swap_code, gate_pin_name, gate_pin_swap_code);
        }
        catch (java.io.IOException e)
        {
            System.out.println("PartLibrary.read_part_pin: IO error scanning file");
            return null;
        }
    }
    
    public static class LogicalPartMapping
    {
        private LogicalPartMapping(String p_name, java.util.SortedSet<String> p_components)
        {
            name = p_name;
            components = p_components;
        }
        /** The name of the maopping. */
        public final String name;
        
        /** The conponents belonging to the mapping. */
        public final java.util.SortedSet<String> components;
    }
    
    public static class PartPin
    {
        private PartPin(String p_pin_name, String p_gate_name, int p_gate_swap_code,
                String p_gate_pin_name, int p_gate_pin_swap_code)
        {
            pin_name = p_pin_name;
            gate_name = p_gate_name;
            gate_swap_code = p_gate_swap_code;
            gate_pin_name = p_gate_pin_name;
            gate_pin_swap_code = p_gate_pin_swap_code;
        }
        
        public final String pin_name;
        public final String gate_name;
        public final int gate_swap_code;
        public final String gate_pin_name;
        public final int gate_pin_swap_code;
    }
    
    public static class LogicalPart
    {
        private LogicalPart(String p_name, java.util.Collection<PartPin> p_part_pins)
        {
            name = p_name;
            part_pins = p_part_pins;
        }
        /** The name of the maopping. */
        public final String name;
        
        /** The pins of this logical part */
        public final java.util.Collection<PartPin> part_pins;
    }
}
