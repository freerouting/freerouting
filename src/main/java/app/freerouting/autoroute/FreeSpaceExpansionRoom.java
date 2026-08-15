package app.freerouting.autoroute;

import app.freerouting.geometry.planar.TileShape;
import java.util.ArrayList;
import java.util.List;

/** Expansion Areas used by the maze search algorithm. */
public abstract class FreeSpaceExpansionRoom implements ExpansionRoom {

  /** The layer of this room. */
  private final int layer;

  /** The shape of this room. */
  private TileShape shape;

  /**
   * The list of doors to neighbour expansion rooms. Using ArrayList for better cache locality and
   * O(1) indexed access.
   */
  private List<ExpansionDoor> doors;

  /**
   * Creates a new instance of FreeSpaceExpansionRoom. The shape is normally unbounded at
   * construction time of this room. The final (completed) shape will be a subshape of the start
   * shape, which does not overlap with any obstacle, and is as big as possible.
   */
  protected FreeSpaceExpansionRoom(TileShape shape, int layer) {
    this.shape = shape;
    this.layer = layer;
    doors = new ArrayList<>(); // ArrayList for better performance
  }

  /** Adds door to the list of doors of this room. */
  @Override
  public void addDoor(ExpansionDoor door) {
    this.doors.add(door);
  }

  /** Returns the list of doors of this room to neighbour expansion rooms. */
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
    for (ExpandableObject currentDoor : this.doors) {
      currentDoor.reset();
    }
  }

  @Override
  public boolean removeDoor(ExpandableObject door) {
    return this.doors.remove(door);
  }

  /** Gets the shape of this room. */
  @Override
  public TileShape getShape() {
    return this.shape;
  }

  /** Sets the shape of this room. */
  public void setShape(TileShape shape) {
    this.shape = shape;
  }

  @Override
  public int getLayer() {
    return this.layer;
  }

  /** Checks if this room already has a door to other. */
  @Override
  public boolean doorExists(ExpansionRoom other) {
    if (doors == null) {
      return false;
    }
    for (ExpansionDoor currentDoor : doors) {
      if (currentDoor.firstRoom == other || currentDoor.secondRoom == other) {
        return true;
      }
    }
    return false;
  }
}
