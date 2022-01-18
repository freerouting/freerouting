package eu.mihosoft.freerouting.designforms.specctra;

import eu.mihosoft.freerouting.datastructures.IndentFileWriter;
import eu.mihosoft.freerouting.datastructures.IdentifierType;

/**
 * Class for  writing path scopes from dsn-files.
 */
public abstract class Path extends Shape
{
    
    /** Creates a new instance of Path */
    Path(Layer p_layer, double p_width, double[] p_coordinate_arr)
    {
        super (p_layer);
        width = p_width;
        coordinate_arr = p_coordinate_arr;
    }
    
    /**
     * Writes this path as a scope to an output dsn-file.
     */
    public abstract void write_scope(IndentFileWriter p_file, IdentifierType p_identifier) throws java.io.IOException;
    
    public final double width;
    public final double [] coordinate_arr;
}
