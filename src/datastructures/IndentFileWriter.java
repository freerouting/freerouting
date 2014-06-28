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
 * IndentFileWriter.java
 *
 * Created on 21. Juni 2004, 09:36
 */

package datastructures;

/**
 * Handles the indenting  of scopes while writing to an output text file.
 *
 * @author  alfons
 */
public class IndentFileWriter extends java.io.OutputStreamWriter
{
    
    /** Creates a new instance of IndentFileWriter */
    public IndentFileWriter(java.io.OutputStream p_stream)
    {
        super(p_stream);
    }
    
    /**
     * Begins a new scope.
     */
    public void start_scope()
    {
        new_line();
        try
        {
            write(BEGIN_SCOPE);
        }
        catch (java.io.IOException e)
        {
            System.out.println("IndentFileWriter.start_scope: unable to write to file");
        }
        ++current_indent_level;
    }
    
    /**
     * Closes the latest open scope.
     */
    public void end_scope()
    {
        --current_indent_level;
        new_line();
        try
        {
            write(END_SCOPE);
        }
        catch (java.io.IOException e)
        {
            System.out.println("IndentFileWriter.end_scope: unable to write to file");
        }
    }
    
    /**
     * Starts a new line inside a scope.
     */
    public void new_line()
    {
        try
        {
            write("\n");
            for (int i = 0; i < current_indent_level; ++i)
            {
                write(INDENT_STRING);
            }
        }
        catch (java.io.IOException e)
        {
            System.out.println("IndentFileWriter.new_line: unable to write to file");
        }
    }
    
    private int current_indent_level = 0;
    
    private static final String INDENT_STRING = "  ";
    private static final String BEGIN_SCOPE = "(";
    private static final String END_SCOPE = ")";
}
