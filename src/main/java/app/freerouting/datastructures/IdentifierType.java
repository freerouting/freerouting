package app.freerouting.datastructures;

import app.freerouting.logger.FRLogger;

import java.io.OutputStreamWriter;

/**
 * Describes legal identifiers together with the character used for string quotes.
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
            FRLogger.warn("IndentFileWriter.new_line: unable to write to file");
        }
    }
    
    /**
     * Looks, if p_string dous not contain reserved characters or blanks.
     */
    private boolean is_legal( String p_string)
    {
        if (p_string == null)
        {
            FRLogger.warn("IdentifierType.is_legal: p_string is null");
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
