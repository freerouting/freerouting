package eu.mihosoft.freerouting.board;

import eu.mihosoft.freerouting.logger.FRLogger;

/**
 * Creates unique Item identification numbers.
 */
public class ItemIdNoGenerator implements eu.mihosoft.freerouting.datastructures.IdNoGenerator, java.io.Serializable
{
    
    /**
     * Creates a new ItemIdNoGenerator
     */
    public ItemIdNoGenerator()
    {
    }
    
    /**
     * Create a new unique identification number.
     * Use eventually the id_no generater from the host system
     * for syncronisation
     */
    public int new_no()
    {
        if (last_generated_id_no >= c_max_id_no)
        {
            FRLogger.warn("IdNoGenerator: danger of overflow, please regenerate id numbers from scratch!");
        }
        ++last_generated_id_no;
        return last_generated_id_no;
    }
    
    /**
     * Return the maximum generated id number so far.
     */
    public int max_generated_no()
    {
        return last_generated_id_no;
    }
    
    private int last_generated_id_no = 0;
    static final private int c_max_id_no = Integer.MAX_VALUE / 2;
}
