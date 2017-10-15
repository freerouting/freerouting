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
 * Parser.java
 *
 * Created on 24. Januar 2005, 08:29
 */
package designformats.specctra;

import board.Communication.SpecctraParserInfo;

/**
 * Class for reading and writing parser scopes from dsn-files.
 *
 * @author Alfons Wirtz
 */
public class Parser extends ScopeKeyword
{

    /** Creates a new instance of Parser */
    public Parser()
    {
        super("parser");
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
                System.out.println("Parser.read_scope: IO error scanning file");
                return false;
            }
            if (next_token == null)
            {
                System.out.println("Parser.read_scope: unexpected end of file");
                return false;
            }
            if (next_token == CLOSED_BRACKET)
            {
                // end of scope
                break;
            }
            boolean read_ok = true;
            if (prev_token == OPEN_BRACKET)
            {
                if (next_token == Keyword.STRING_QUOTE)
                {
                    String quote_char = read_quote_char(p_par.scanner);
                    if (quote_char == null)
                    {
                        return false;
                    }
                    p_par.string_quote = quote_char;
                }
                else if (next_token == Keyword.HOST_CAD)
                {
                    p_par.host_cad = DsnFile.read_string_scope(p_par.scanner);
                }
                else if (next_token == Keyword.HOST_VERSION)
                {
                    p_par.host_version = DsnFile.read_string_scope(p_par.scanner);
                }
                else if (next_token == Keyword.CONSTANT)
                {
                    String[] curr_constant = read_constant(p_par);
                    if (curr_constant != null)
                    {
                        p_par.constants.add(curr_constant);
                    }
                }
                else if (next_token == Keyword.WRITE_RESOLUTION)
                {
                    p_par.write_resolution = read_write_solution(p_par);
                }
                else if (next_token == Keyword.GENERATED_BY_FREEROUTE)
                {
                    p_par.dsn_file_generated_by_host = false;
                    // skip the closing bracket
                    skip_scope(p_par.scanner);
                }
                else
                {
                    skip_scope(p_par.scanner);
                }
            }
            if (!read_ok)
            {
                return false;
            }
        }
        return true;
    }

    private static SpecctraParserInfo.WriteResolution read_write_solution(ReadScopeParameter p_par)
    {
        try
        {
            Object next_token = p_par.scanner.next_token();
            if (!(next_token instanceof String))
            {
                System.out.println("Parser.read_write_solution: string expected");
                return null;
            }
            String resolution_string = (String) next_token;
            next_token = p_par.scanner.next_token();
            if (!(next_token instanceof Integer))
            {
                System.out.println("Parser.read_write_solution: integer expected expected");
                return null;
            }
            int resolution_value = (Integer) next_token;
            next_token = p_par.scanner.next_token();
            if (next_token != Keyword.CLOSED_BRACKET)
            {
                System.out.println("Parser.read_write_solution: closing_bracket expected");
                return null;
            }
            return new SpecctraParserInfo.WriteResolution(resolution_string, resolution_value);
        }
        catch (java.io.IOException e)
        {
            System.out.println("Parser.read_write_solution: IO error scanning file");
            return null;
        }
    }

    private static String[] read_constant(ReadScopeParameter p_par)
    {
        try
        {
            String[] result = new String[2];
            p_par.scanner.yybegin(SpecctraFileScanner.NAME);
            Object next_token = p_par.scanner.next_token();
            if (!(next_token instanceof String))
            {
                System.out.println("Parser.read_constant: string expected");
                return null;
            }
            result[0] = (String) next_token;
            p_par.scanner.yybegin(SpecctraFileScanner.NAME);
            next_token = p_par.scanner.next_token();
            if (!(next_token instanceof String))
            {
                System.out.println("Parser.read_constant: string expected");
                return null;
            }
            result[1] = (String) next_token;
            next_token = p_par.scanner.next_token();
            if (next_token != Keyword.CLOSED_BRACKET)
            {
                System.out.println("Parser.read_constant: closing_bracket expected");
                return null;
            }
            return result;
        }
        catch (java.io.IOException e)
        {
            System.out.println("Parser.read_constant: IO error scanning file");
            return null;
        }
    }

    /**
     * p_reduced is true if the scope is written to a session file.
     */
    public static void write_scope(datastructures.IndentFileWriter p_file, SpecctraParserInfo p_parser_info,
            datastructures.IdentifierType p_identifier_type, boolean p_reduced) throws java.io.IOException
    {
        p_file.start_scope();
        p_file.write("parser");
        if (!p_reduced)
        {
            p_file.new_line();
            p_file.write("(string_quote ");
            p_file.write(p_parser_info.string_quote);
            p_file.write(")");
            p_file.new_line();
            p_file.write("(space_in_quoted_tokens on)");
        }
        if (p_parser_info.host_cad != null)
        {
            p_file.new_line();
            p_file.write("(host_cad ");
            p_identifier_type.write(p_parser_info.host_cad, p_file);
            p_file.write(")");
        }
        if (p_parser_info.host_version != null)
        {
            p_file.new_line();
            p_file.write("(host_version ");
            p_identifier_type.write(p_parser_info.host_version, p_file);
            p_file.write(")");
        }
        if (p_parser_info.constants != null)
        {
            for (String[] curr_constant : p_parser_info.constants)
            {
                p_file.new_line();
                p_file.write("(constant ");
                for (int i = 0; i < curr_constant.length; ++i)
                {
                    p_identifier_type.write(curr_constant[i], p_file);
                    p_file.write(" ");
                }
                p_file.write(")");
            }
        }
        if (p_parser_info.write_resolution != null)
        {
            p_file.new_line();
            p_file.write("(write_resolution ");
            p_file.write(p_parser_info.write_resolution.char_name.substring(0, 1));
            p_file.write(" ");
            Integer positive_int = p_parser_info.write_resolution.positive_int;
            p_file.write(positive_int.toString());
            p_file.write(")");
        }
        if (!p_reduced)
        {
            p_file.new_line();
            p_file.write("(generated_by_freeroute)");
        }
        p_file.end_scope();
    }

    private static String read_quote_char(Scanner p_scanner)
    {
        try
        {
            Object next_token = p_scanner.next_token();
            if (!(next_token instanceof String))
            {
                System.out.println("Parser.read_quote_char: string expected");
                return null;
            }
            String result = (String) next_token;
            next_token = p_scanner.next_token();
            if (next_token != Keyword.CLOSED_BRACKET)
            {
                System.out.println("Parser.read_quote_char: closing bracket expected");
                return null;
            }
            return result;
        }
        catch (java.io.IOException e)
        {
            System.out.println("Parser.read_quote_char: IO error scanning file");
            return null;
        }
    }
}
