package app.freerouting.autoroute;

import app.freerouting.geometry.planar.TileShape;
import java.util.List;

public interface ExpansionRoom {
  /** Adds p_door to the list of doors of this room. */
  void add_door(ExpansionDoor p_door);

  /** Returns the list of doors of this room to neighbour expansion rooms */
  List<ExpansionDoor> get_doors();

  /** Removes all doors from this room. */
  void clear_doors();

  /** Clears the autorouting info of all doors for routing the next connection. */
  void reset_doors();

  /** Checks, if this room has already a door to p_other */
  boolean door_exists(ExpansionRoom p_other);

  /** Removes p_door from this room. Returns false, if p_room did not contain p_door. */
  boolean remove_door(ExpandableObject p_door);

  /** Gets the shape of this room. */
  TileShape get_shape();

  /** Returns the layer of this expansion room. */
  int get_layer();
}
