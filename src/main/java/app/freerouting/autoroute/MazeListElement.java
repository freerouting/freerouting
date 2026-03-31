package app.freerouting.autoroute;

import app.freerouting.geometry.planar.FloatLine;

/**
 * Information for the maze expand Algorithm contained in expansion doors and
 * drills while the maze expanding algorithm is in progress.
 */
public class MazeListElement implements Comparable<MazeListElement> {

  /**
   * The door or drill belonging to this MazeListElement
   */
  ExpandableObject door;
  /**
   * The section number of the door (or the layer of the drill)
   */
  int section_no_of_door;
  /**
   * The door, from which this door was expanded
   */
  ExpandableObject backtrack_door;
  /**
   * The section number of the backtrack door
   */
  int section_no_of_backtrack_door;
  /**
   * The weighted distance to the start of the expansion
   */
  double expansion_value;
  /**
   * The expansion value plus the shortest distance to a destination. The list is
   * sorted in ascending order by this value.
   */
  double sorting_value;
  /**
   * The next room, which will be expanded from this maze search element
   */
  CompleteExpansionRoom next_room;
  /**
   * Point of the region of the expansion door, which has the shortest distance to
   * the backtrack door.
   */
  FloatLine shape_entry;
  boolean room_ripped;
  MazeSearchElement.Adjustment adjustment;
  boolean already_checked;
  /** Creates a new instance of MazeListElement */
  private MazeListElement(ExpandableObject p_door, int p_section_no_of_door,
      ExpandableObject p_backtrack_door, int p_section_no_of_backtrack_door,
      double p_expansion_value, double p_sorting_value,
      CompleteExpansionRoom p_next_room, FloatLine p_shape_entry,
      boolean p_room_ripped, MazeSearchElement.Adjustment p_adjustment, boolean p_already_checked) {
    door = p_door;
    section_no_of_door = p_section_no_of_door;
    backtrack_door = p_backtrack_door;
    section_no_of_backtrack_door = p_section_no_of_backtrack_door;
    expansion_value = p_expansion_value;
    sorting_value = p_sorting_value;
    next_room = p_next_room;
    shape_entry = p_shape_entry;
    room_ripped = p_room_ripped;
    adjustment = p_adjustment;
    already_checked = p_already_checked;
  }

  /** Creates a new MazeListElement (v1.9-style allocation semantics). */
  public static MazeListElement obtain(ExpandableObject p_door, int p_section_no_of_door,
      ExpandableObject p_backtrack_door, int p_section_no_of_backtrack_door,
      double p_expansion_value, double p_sorting_value,
      CompleteExpansionRoom p_next_room, FloatLine p_shape_entry,
      boolean p_room_ripped, MazeSearchElement.Adjustment p_adjustment, boolean p_already_checked) {
    return new MazeListElement(
        p_door,
        p_section_no_of_door,
        p_backtrack_door,
        p_section_no_of_backtrack_door,
        p_expansion_value,
        p_sorting_value,
        p_next_room,
        p_shape_entry,
        p_room_ripped,
        p_adjustment,
        p_already_checked);
  }

  /** No-op for parity with v1.9 allocation behavior during investigations. */
  public static void recycle(MazeListElement _element) {
    if (_element == null) {
      return;
    }
    // Intentionally empty.
  }

  @Override
  @SuppressWarnings("NullableProblems")
  public int compareTo(MazeListElement p_other) {
    if (this == p_other) {
      return 0;
    }
    // v1.9 behavior: never return 0 for distinct entries so equal-cost elements are not dropped.
    return this.sorting_value >= p_other.sorting_value ? 1 : -1;
  }
}