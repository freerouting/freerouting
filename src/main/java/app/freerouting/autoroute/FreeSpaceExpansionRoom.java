package app.freerouting.autoroute;

import app.freerouting.geometry.planar.TileShape;
import java.util.ArrayList;
import java.util.List;

/** Expansion Areas used by the maze search algorithm. */
public abstract class FreeSpaceExpansionRoom implements ExpansionRoom {

  /** The layer of this room */
  private final int layer;

  /** The shape of this room */
  private TileShape shape;

  /**
   * The list of doors to neighbour expansion rooms Using ArrayList for better cache locality and
   * O(1) indexed access
   */
  private List<ExpansionDoor> doors;

  /**
   * Creates a new instance of FreeSpaceExpansionRoom. The shape is normally unbounded at
   * construction time of this room. The final (completed) shape will be a subshape of the start
   * shape, which does not overlap with any obstacle, and is as big as possible. p_contained_points
   * will remain contained in the shape, after it is completed.
   */
  protected FreeSpaceExpansionRoom(TileShape p_shape, int p_layer) {
    shape = p_shape;
    layer = p_layer;
    doors = new ArrayList<>(); // ArrayList for better performance
  }

  /** Adds p_door to the list of doors of this room. */
  @Override
  public void addDoor(ExpansionDoor p_door) {
    this.doors.add(p_door);
  }

  /** Returns the list of doors of this room to neighbour expansion rooms */
  @Override
  public List<ExpansionDoor> getDoors() {
    return this.doors;
  }

  /** Removes all doors from this room. */
  @Override
  public void clearDoors() {
    this.doors = new ArrayList<>(); // ArrayList for better performance
  }

  @Override
  public void resetDoors() {
    for (ExpandableObject currDoor : this.doors) {
      currDoor.reset();
    }
  }

  @Override
  public boolean removeDoor(ExpandableObject p_door) {
    return this.doors.remove(p_door);
  }

  /** Gets the shape of this room */
  @Override
  public TileShape getShape() {
    return this.shape;
  }

  /** sets the shape of this room */
  public void setShape(TileShape p_shape) {
    this.shape = p_shape;
  }

  @Override
  public int getLayer() {
    return this.layer;
  }

  /** Checks, if this room has already a door to p_other */
  @Override
  public boolean doorExists(ExpansionRoom p_other) {
    if (doors == null) {
      return false;
    }
    for (ExpansionDoor currDoor : doors) {
      if (currDoor.firstRoom == p_other || currDoor.secondRoom == p_other) {
        return true;
      }
    }
    return false;
  }
}
