package app.freerouting.autoroute;

import app.freerouting.geometry.planar.TileShape;
import java.util.List;

public interface ExpansionRoom {

  /** Adds p_door to the list of doors of this room. */
  void addDoor(ExpansionDoor p_door);

  /** Returns the list of doors of this room to neighbour expansion rooms */
  List<ExpansionDoor> getDoors();

  /** Removes all doors from this room. */
  void clearDoors();

  /** Clears the autorouting info of all doors for routing the next connection. */
  void resetDoors();

  /** Checks, if this room has already a door to p_other */
  boolean doorExists(ExpansionRoom p_other);

  /** Removes p_door from this room. Returns false, if p_room did not contain p_door. */
  boolean removeDoor(ExpandableObject p_door);

  /** Gets the shape of this room. */
  TileShape getShape();

  /** Returns the layer of this expansion room. */
  int getLayer();

  /** Returns a unique identification number for this expansion room. */
  int getIdNo();
}
