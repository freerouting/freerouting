package app.freerouting.autoroute;

import app.freerouting.geometry.planar.TileShape;

/** An object, which can be expanded by the maze expansion algorithm. */
public interface ExpandableObject {

  /** Calculates the intersection of the shapes of the 2 objects belonging to this door. */
  TileShape getShape();

  /**
   * Returns the dimension of the intersection of the shapes of the 2 objects belonging to this
   * door.
   */
  int getDimension();

  /**
   * Returns the other room to room if this is a door and the other room is a
   * CompleteExpansionRoom. Else null is returned.
   */
  CompleteExpansionRoom otherRoom(CompleteExpansionRoom room);

  /** Returns the count of MazeSearchElements in this expandable object. */
  int mazeSearchElementCount();

  /** Returns the index-th MazeSearchElements in this expandable object. */
  MazeSearchElement getMazeSearchElement(int index);

  /** Resets this ExpandableObject for autorouting the next connection. */
  void reset();

  /** Returns a unique identification number for this object to allow deterministic sorting. */
  int getIdNo();
}
