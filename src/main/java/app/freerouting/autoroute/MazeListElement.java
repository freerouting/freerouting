package app.freerouting.autoroute;

import app.freerouting.geometry.planar.FloatLine;

/**
 * Information for the maze expand Algorithm contained in expansion doors and drills while the maze
 * expanding algorithm is in progress.
 */
public class MazeListElement implements Comparable<MazeListElement> {

  /** The door or drill belonging to this MazeListElement */
  final ExpandableObject door;

  /** The section number of the door (or the layer of the drill) */
  final int sectionNoOfDoor;

  /** The door, from which this door was expanded */
  final ExpandableObject backtrackDoor;

  /** The section number of the backtrack door */
  final int sectionNoOfBacktrackDoor;

  /** The weighted distance to the start of the expansion */
  final double expansionValue;

  /**
   * The expansion value plus the shortest distance to a destination. The list is sorted in
   * ascending order by this value.
   */
  final double sortingValue;

  /** The next room, which will be expanded from this maze search element */
  final CompleteExpansionRoom nextRoom;

  /**
   * Point of the region of the expansion door, which has the shortest distance to the backtrack
   * door.
   */
  final FloatLine shapeEntry;

  final boolean roomRipped;
  final MazeSearchElement.Adjustment adjustment;
  final boolean alreadyChecked;

  /**
   * The ripup cost paid to enter the nextRoom through this door. Non-zero only when roomRipped is
   * true and this element was directly created by expand_to_door_section with a positive add_costs.
   */
  int ripupCost;

  /** Creates a new instance of ExpansionInfo */
  public MazeListElement(
      ExpandableObject pDoor,
      int pSectionNoOfDoor,
      ExpandableObject pBacktrackDoor,
      int pSectionNoOfBacktrackDoor,
      double pExpansionValue,
      double pSortingValue,
      CompleteExpansionRoom pNextRoom,
      FloatLine pShapeEntry,
      boolean pRoomRipped,
      MazeSearchElement.Adjustment pAdjustment,
      boolean pAlreadyChecked) {
    door = pDoor;
    sectionNoOfDoor = pSectionNoOfDoor;
    backtrackDoor = pBacktrackDoor;
    sectionNoOfBacktrackDoor = pSectionNoOfBacktrackDoor;
    expansionValue = pExpansionValue;
    sortingValue = pSortingValue;
    nextRoom = pNextRoom;
    shapeEntry = pShapeEntry;
    roomRipped = pRoomRipped;
    adjustment = pAdjustment;
    alreadyChecked = pAlreadyChecked;
  }

  @Override
  public int compareTo(MazeListElement pOther) {
    if (this.sortingValue < pOther.sortingValue) {
      return -1;
    }
    if (this.sortingValue > pOther.sortingValue) {
      return 1;
    }
    // Tie-break 1: expansionValue
    if (this.expansionValue < pOther.expansionValue) {
      return -1;
    }
    if (this.expansionValue > pOther.expansionValue) {
      return 1;
    }
    // Tie-break 2: door id
    int id1 = this.door.getIdNo();
    int id2 = pOther.door.getIdNo();
    if (id1 < id2) {
      return -1;
    }
    if (id1 > id2) {
      return 1;
    }
    // Tie-break 3: sectionNo
    if (this.sectionNoOfDoor < pOther.sectionNoOfDoor) {
      return -1;
    }
    if (this.sectionNoOfDoor > pOther.sectionNoOfDoor) {
      return 1;
    }
    // If truly equal (same door, same section, same values), return 0 to avoid duplicates in the
    // set.
    return 0;
  }
}
