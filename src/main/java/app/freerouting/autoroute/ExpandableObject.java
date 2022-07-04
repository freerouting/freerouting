package app.freerouting.autoroute;

import app.freerouting.geometry.planar.TileShape;

/** An object, which can be expanded by the maze expansion algorithm. */
public interface ExpandableObject {

  /** Calculates the intersection of the shapes of the 2 objecta belonging to this door. */
  TileShape get_shape();

  /**
   * Returns the dimension ot the intersection of the shapes of the 2 objecta belonging to this
   * door.
   */
  int get_dimension();

  /**
   * Returns the other room to p_room if this is a door and the other room is a
   * CompleteExpansionRoom. Else null is returned.
   */
  CompleteExpansionRoom other_room(CompleteExpansionRoom p_room);

  /** Returns the count of MazeSearchElements in this expandable object */
  int maze_search_element_count();

  /** Returns the p_no-th MazeSearchElements in this expandable object */
  MazeSearchElement get_maze_search_element(int p_no);

  /** Resets this ExpandableObject for autorouting the next connection. */
  void reset();
}
