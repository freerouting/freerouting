package eu.mihosoft.freerouting.autoroute;

/**
 * Describes the structure of a section of an ExpandebleObject.
 */
public class MazeSearchElement
{
    /**
     * Resets this MazeSearchElement for autorouting the next connection.
     */
    public void reset()
    {
        is_occupied = false;
        backtrack_door = null;
        section_no_of_backtrack_door = 0;
        room_ripped = false;
        adjustment = Adjustment.NONE;
    }
    
    /** true, if this door is already occupied by the maze expanding algorithm */
    public boolean is_occupied = false;
    
    /** Used for backtracking in the maze expanding algorithm */
    public ExpandableObject backtrack_door = null;
    
    public int section_no_of_backtrack_door = 0;
    
    public boolean room_ripped = false;
    
    public Adjustment adjustment = Adjustment.NONE;
    
    public enum Adjustment
    { NONE, RIGHT, LEFT}
}

