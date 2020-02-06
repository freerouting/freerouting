/*
 *   Copyright (C) 2014  Alfons Wirtz
 *   website www.freerouting.net
 *
 *   Copyright (C) 2017 Michael Hoffer <info@michaelhoffer.de>
 *   Website www.freerouting.mihosoft.eu
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

package eu.mihosoft.freerouting.designforms.specctra;

import eu.mihosoft.freerouting.logger.FRLogger;

/**
 * Class for reading resolution scopes from dsn-files.
 *
 * @author Alfons Wirtz
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
                FRLogger.warn("Resolution.read_scope: string expected");
                return false;
            }
            p_par.unit = eu.mihosoft.freerouting.board.Unit.from_string((String) next_token);
            if (p_par.unit == null)
            {
                FRLogger.warn("Resolution.read_scope: unit mil, inch or mm expected");
                return false;
            }
            // read the scale factor
            next_token = p_par.scanner.next_token();
            if (!(next_token instanceof Integer))
            {
                FRLogger.warn("Resolution.read_scope: integer expected");
                return false;
            }
            p_par.resolution = ((Integer)next_token).intValue();
            // overread the closing bracket
            next_token = p_par.scanner.next_token();
            if (next_token != CLOSED_BRACKET)
            {
                FRLogger.warn("Resolution.read_scope: closing bracket expected");
                return false;
            }
            return true;
        }
        catch (java.io.IOException e)
        {
            FRLogger.error("Resolution.read_scope: IO error scanning file", e);
            return false;
        }
    }
    
    public static void write_scope(eu.mihosoft.freerouting.datastructures.IndentFileWriter p_file, eu.mihosoft.freerouting.board.Communication p_board_communication)  throws java.io.IOException
    {
        p_file.new_line();
        p_file.write("(resolution ");
        p_file.write(p_board_communication.unit.toString());
        p_file.write(" ");
        p_file.write((Integer.valueOf(p_board_communication.resolution)).toString());
        p_file.write(")");
    }
    
}
