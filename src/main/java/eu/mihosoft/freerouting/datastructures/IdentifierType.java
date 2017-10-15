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
 * Identifier.java
 *
 * Created on 25. Januar 2005, 09:50
 */

package datastructures;

import java.io.OutputStreamWriter;

/**
 * Describes legal identifiers together with the character used for string quotes.
 *
 * @author alfons
 */
public class IdentifierType
{
    /**
     * Defines the reserved characters and the string for quoting identifiers containing
     * reserved characters for a new instance of Identifier.
     */
    public IdentifierType(String [] p_reserved_chars, String p_string_quote)
    {
        reserved_chars = p_reserved_chars;
        string_quote = p_string_quote;
    }
    
    /**
     * Writes p_name after puttiong it into quotes, if it contains reserved characters or blanks.
     */
    public void write(String p_name, OutputStreamWriter p_file)
    {
        try
        {
            if (is_legal(p_name))
            {
                p_file.write(p_name);
            }
            else
            {
                p_file.write(quote(p_name));
            }
        }
        catch (java.io.IOException e)
        {
            System.out.println("IndentFileWriter.new_line: unable to write to file");
        }
    }
    
    /**
     * Looks, if p_string dous not contain reserved characters or blanks.
     */
    private boolean is_legal( String p_string)
    {
        if (p_string == null)
        {
            System.out.println("IdentifierType.is_legal: p_string is null");
            return false;
        }
        for (int i = 0; i < reserved_chars.length; ++i)
        {
            if (p_string.contains(reserved_chars[i]))
            {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Puts p_sting into quotes.
     */
    private String quote(String p_string)
    {
        return string_quote + p_string + string_quote;
    }
    private final String string_quote;
    private final String[] reserved_chars;
}
