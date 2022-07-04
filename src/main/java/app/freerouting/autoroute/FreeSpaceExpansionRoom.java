package app.freerouting.autoroute;

import app.freerouting.geometry.planar.TileShape;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/** Expansion Areas used by the maze search algorithm. */
public abstract class FreeSpaceExpansionRoom implements ExpansionRoom {

  /** The layer of this room */
  private final int layer;
  /** The shape of this room */
  private TileShape shape;
  /** The list of doors to neighbour expansion rooms */
  private List<ExpansionDoor> doors;

  /**
   * Creates a new instance of FreeSpaceExpansionRoom. The shape is normally unbounded at
   * construction time of this room. The final (completed) shape will be a subshape of the start
   * shape, which does not overlap with any obstacle, and is as big as possible. p_contained_points
   * will remain contained in the shape, after it is completed.
   */
  public FreeSpaceExpansionRoom(TileShape p_shape, int p_layer) {
    shape = p_shape;
    layer = p_layer;
    doors = new LinkedList<ExpansionDoor>();
  }

  /** Adds p_door to the list of doors of this room. */
  public void add_door(ExpansionDoor p_door) {
    this.doors.add(p_door);
  }

  /** Returns the list of doors of this room to neighbour expansion rooms */
  public List<ExpansionDoor> get_doors() {
    return this.doors;
  }

  /** Removes all doors from this room. */
  public void clear_doors() {
    this.doors = new LinkedList<ExpansionDoor>();
  }

  public void reset_doors() {
    for (ExpandableObject curr_door : this.doors) {
      curr_door.reset();
    }
  }

  public boolean remove_door(ExpandableObject p_door) {
    return this.doors.remove(p_door);
  }

  /** Gets the shape of this room */
  public TileShape get_shape() {
    return this.shape;
  }

  /** sets the shape of this room */
  public void set_shape(TileShape p_shape) {
    this.shape = p_shape;
  }

  public int get_layer() {
    return this.layer;
  }

  /** Checks, if this room has already a door to p_other */
  public boolean door_exists(ExpansionRoom p_other) {
    if (doors == null) {
      return false;
    }
    Iterator<ExpansionDoor> it = doors.iterator();
    while (it.hasNext()) {
      ExpansionDoor curr_door = it.next();
      if (curr_door.first_room == p_other || curr_door.second_room == p_other) {
        return true;
      }
    }
    return false;
  }
}
