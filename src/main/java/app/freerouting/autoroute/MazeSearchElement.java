package app.freerouting.autoroute;

/**
 * Describes the structure of a section of an ExpandableObject.
 */
public class MazeSearchElement {

  /**
   * true, if this door is already occupied by the maze expanding algorithm
   */
  public boolean is_occupied;
  /**
   * Used for backtracking in the maze expanding algorithm
   */
  public ExpandableObject backtrack_door;
  public int section_no_of_backtrack_door;
  public boolean room_ripped;
  public Adjustment adjustment = Adjustment.NONE;
  /**
   * The ripup cost paid to enter this door's room via the maze search.
   * Zero when room_ripped is false.
   */
  public int ripup_cost;

  /**
   * Resets this MazeSearchElement for autorouting the next connection.
   */
  public void reset() {
    is_occupied = false;
    backtrack_door = null;
    section_no_of_backtrack_door = 0;
    room_ripped = false;
    adjustment = Adjustment.NONE;
    ripup_cost = 0;
  }

  public enum Adjustment {
    NONE, RIGHT, LEFT
  }
}