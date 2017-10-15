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
 * PlaceControl.java
 *
 * Created on 25. November 2004, 13:21
 */

package designformats.specctra;

/**
 * Class for reading place_control scopes from dsn-files.
 *
 * @author  Alfons Wirtz
 */
public class PlaceControl extends ScopeKeyword
{
    
    /** Creates a new instance of PlaceControl */
    public PlaceControl()
    {
        super("place_control");
    }
    
    /** Reads the flip_style */
    public boolean read_scope(ReadScopeParameter p_par)
    {
        boolean flip_style_rotate_first = false;
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
                System.out.println("PlaceControl.read_scope: IO error scanning file");
                return false;
            }
            if (next_token == null)
            {
                System.out.println("PlaceControl.read_scope: unexpected end of file");
                return false;
            }
            if (next_token == CLOSED_BRACKET)
            {
                // end of scope
                break;
            }
            if (prev_token == OPEN_BRACKET)
            {
                if (next_token == Keyword.FLIP_STYLE)
                {
                    flip_style_rotate_first = read_flip_style_rotate_first(p_par.scanner);
                }
            }
        }   
        if (flip_style_rotate_first)
        {
            p_par.board_handling.get_routing_board().components.set_flip_style_rotate_first(true);
        }
        return true;
    }
    
    /**
     * Returns true, if rotate_first is read, else false.
     */
    static boolean read_flip_style_rotate_first(Scanner p_scanner)
    {
        try
        {
            boolean result = false;
            Object next_token = p_scanner.next_token();
            if (next_token == Keyword.ROTATE_FIRST)
            {
                if (next_token == Keyword.ROTATE_FIRST)
                {
                    result = true;
                }
            }
            next_token = p_scanner.next_token();
            if (next_token != Keyword.CLOSED_BRACKET)
            {
                System.out.println("Structure.read_flip_style: closing bracket expected");
                return  false;
            }
            return result;
        }
        catch (java.io.IOException e)
        {
            System.out.println("Structure.read_flip_style: IO error scanning file");
            return  false;
        }
    }
    
}
