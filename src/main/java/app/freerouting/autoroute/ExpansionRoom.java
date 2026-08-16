package app.freerouting.autoroute;

import app.freerouting.geometry.planar.TileShape;
import java.util.List;

/** Interface representing a room in the expansion tree during maze routing. */
public interface ExpansionRoom {

  /** Adds door to the list of doors of this room. */
  void addDoor(ExpansionDoor door);

  /** Returns the list of doors of this room to neighbour expansion rooms. */
  List<ExpansionDoor> getDoors();

  /** Removes all doors from this room. */
  void clearDoors();

  /** Clears the autorouting info of all doors for routing the next connection. */
  void resetDoors();

  /** Checks if this room already has a door to other. */
  boolean doorExists(ExpansionRoom other);

  /** Removes door from this room. Returns false if this room did not contain door. */
  boolean removeDoor(ExpandableObject door);

  /** Gets the shape of this room. */
  TileShape getShape();

  /** Returns the layer of this expansion room. */
  int getLayer();

  /** Returns a unique identification number for this expansion room. */
  int getId();
}
