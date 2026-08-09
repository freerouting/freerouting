package app.freerouting.autoroute;

/** Describes the structure of a section of an ExpandableObject. */
public class MazeSearchElement {

  /** True if this door is already occupied by the maze expanding algorithm. */
  public boolean isOccupied;

  /** Used for backtracking in the maze expanding algorithm. */
  public ExpandableObject backtrackDoor;

  public int sectionNoOfBacktrackDoor;
  public boolean roomRipped;
  public Adjustment adjustment = Adjustment.NONE;

  /**
   * The ripup cost paid to enter this door's room via the maze search. Zero when roomRipped is.
   * false.
   */
  public int ripupCost;

  /** Resets this MazeSearchElement for autorouting the next connection. */
  public void reset() {
    isOccupied = false;
    backtrackDoor = null;
    sectionNoOfBacktrackDoor = 0;
    roomRipped = false;
    adjustment = Adjustment.NONE;
    ripupCost = 0;
  }

  /** Adjustment directions for maze search expansion. */
  public enum Adjustment {
    NONE,
    RIGHT,
    LEFT
  }
}
