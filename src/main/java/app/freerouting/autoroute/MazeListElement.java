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
      ExpandableObject p_door,
      int p_section_no_of_door,
      ExpandableObject p_backtrack_door,
      int p_section_no_of_backtrack_door,
      double p_expansion_value,
      double p_sorting_value,
      CompleteExpansionRoom p_next_room,
      FloatLine p_shape_entry,
      boolean p_room_ripped,
      MazeSearchElement.Adjustment p_adjustment,
      boolean p_already_checked) {
    door = p_door;
    sectionNoOfDoor = p_section_no_of_door;
    backtrackDoor = p_backtrack_door;
    sectionNoOfBacktrackDoor = p_section_no_of_backtrack_door;
    expansionValue = p_expansion_value;
    sortingValue = p_sorting_value;
    nextRoom = p_next_room;
    shapeEntry = p_shape_entry;
    roomRipped = p_room_ripped;
    adjustment = p_adjustment;
    alreadyChecked = p_already_checked;
  }

  @Override
  public int compareTo(MazeListElement p_other) {
    if (this.sortingValue < p_other.sortingValue) {
      return -1;
    }
    if (this.sortingValue > p_other.sortingValue) {
      return 1;
    }
    // Tie-break 1: expansionValue
    if (this.expansionValue < p_other.expansionValue) {
      return -1;
    }
    if (this.expansionValue > p_other.expansionValue) {
      return 1;
    }
    // Tie-break 2: door id
    int id1 = this.door.get_id_no();
    int id2 = p_other.door.get_id_no();
    if (id1 < id2) {
      return -1;
    }
    if (id1 > id2) {
      return 1;
    }
    // Tie-break 3: sectionNo
    if (this.sectionNoOfDoor < p_other.sectionNoOfDoor) {
      return -1;
    }
    if (this.sectionNoOfDoor > p_other.sectionNoOfDoor) {
      return 1;
    }
    // If truly equal (same door, same section, same values), return 0 to avoid duplicates in the
    // set.
    return 0;
  }
}
