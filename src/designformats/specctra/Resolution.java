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
 * Resolution.java
 *
 * Created on 30. Oktober 2004, 08:00
 */

package designformats.specctra;

/**
 * Class for reading resolution scopes from dsn-files.
 *
 * @author  alfons
 */
public class Resolution extends ScopeKeyword
{
    
    /** Creates a new instance of Resolution */
    public Resolution()
    {
        super("resolution");
    }
    
    public boolean read_scope(ReadScopeParameter p_par)
    {
        try
        {
            // read the unit
            Object next_token = p_par.scanner.next_token();
            if (!(next_token instanceof String))
            {
                System.out.println("Resolution.read_scope: string expected");
                return false;
            }
            p_par.unit = board.Unit.from_string((String) next_token);
            if (p_par.unit == null)
            {
                System.out.println("Resolution.read_scope: unit mil, inch or mm expected");
                return false;
            }
            // read the scale factor
            next_token = p_par.scanner.next_token();
            if (!(next_token instanceof Integer))
            {
                System.out.println("Resolution.read_scope: integer expected");
                return false;
            }
            p_par.resolution = ((Integer)next_token).intValue();
            // overread the closing bracket
            next_token = p_par.scanner.next_token();
            if (next_token != Keyword.CLOSED_BRACKET)
            {
                System.out.println("Resolution.read_scope: closing bracket expected");
                return false;
            }
            return true;
        }
        catch (java.io.IOException e)
        {
            System.out.println("Resolution.read_scope: IO error scanning file");
            return false;
        }
    }
    
    public static void write_scope(datastructures.IndentFileWriter p_file, board.Communication p_board_communication)  throws java.io.IOException
    {
        p_file.new_line();
        p_file.write("(resolution ");
        p_file.write(p_board_communication.unit.toString());
        p_file.write(" ");
        p_file.write((new Integer(p_board_communication.resolution)).toString());
        p_file.write(")");
    }
    
}
