package app.freerouting.autoroute.expansion;

import app.freerouting.autoroute.AutorouteDiagnostic;
import app.freerouting.board.actions.ItemSelectionFilter;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.searchtree.SearchTreeObject;
import app.freerouting.board.searchtree.ShapeSearchTree;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.TileShape;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Expansion Room used for pushing and ripping obstacles in the autoroute algorithm. */
public class ObstacleExpansionRoom implements CompleteExpansionRoom {

  private final Item item;
  private final int indexInItem;
  private final TileShape shape;

  /** The list of doors to neighbour expansion rooms. */
  private List<ExpansionDoor> doors;

  private boolean doorsCalculated;

  /** Creates a new instance of ObstacleExpansionRoom. */
  public ObstacleExpansionRoom(Item item, int indexInItem, ShapeSearchTree shapeTree) {
    this.item = item;
    this.indexInItem = indexInItem;
    this.shape = item.getTreeShape(shapeTree, indexInItem);
    this.doors = new ArrayList<>();
  }

  /** Returns the index of this shape within the item. */
  public int getIndexInItem() {
    return this.indexInItem;
  }

  @Override
  public int getLayer() {
    return this.item.shapeLayer(this.indexInItem);
  }

  @Override
  public TileShape getShape() {
    return this.shape;
  }

  @Override
  public int getId() {
    return (this.item.getId() << 10) | this.indexInItem;
  }

  /** Checks if this room already has a 1-dimensional door to other. */
  @Override
  public boolean doorExists(ExpansionRoom other) {
    if (doors != null) {
      for (ExpansionDoor currentDoor : this.doors) {
        if (currentDoor.firstRoom == other || currentDoor.secondRoom == other) {
          return true;
        }
      }
    }
    return false;
  }

  /** Adds a door to the door list of this room. */
  @Override
  public void addDoor(ExpansionDoor door) {
    this.doors.add(door);
  }

  /**
   * Creates a 2-dim door with the other obstacle room if that is useful for the autoroute
   * algorithm. It is assumed that this room and other have a 2-dimensional overlap. Returns false
   * if no door was created.
   */
  public boolean createOverlapDoor(ObstacleExpansionRoom other) {
    if (this.doorExists(other)) {
      return false;
    }
    if (!(this.item.isRoutable() && other.item.isRoutable())) {
      return false;
    }
    if (!this.item.sharesNet(other.item)) {
      return false;
    }
    if (this.item == other.item) {
      if (!(this.item instanceof PolylineTrace)) {
        return false;
      }
      // create only doors between consecutive trace segments
      if (this.indexInItem != other.indexInItem + 1 && this.indexInItem != other.indexInItem - 1) {
        return false;
      }
    }
    if (this.item != other.item && meetOnPin(this.item, other.item)) {
      return false;
    }
    ExpansionDoor newDoor = new ExpansionDoor(this, other, 2);
    this.addDoor(newDoor);
    other.addDoor(newDoor);
    return true;
  }

  /**
   * Whether two traces meet at a common end lying on a pin, as two traces of a net leaving the same
   * pad. Ripping both up does not remove the pin, so a path from one to the other through their
   * overlap would run through it.
   */
  private static boolean meetOnPin(Item first, Item second) {
    if (!(first instanceof PolylineTrace a) || !(second instanceof PolylineTrace b)) {
      return false;
    }
    for (Point end : new Point[] {a.firstCorner(), a.lastCorner()}) {
      if (end.equals(b.firstCorner()) || end.equals(b.lastCorner())) {
        ItemSelectionFilter pinFilter =
            new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.PINS);
        Set<Item> pins = a.board.pickItems(end, a.getLayer(), pinFilter);
        if (!pins.isEmpty()) {
          return true;
        }
      }
    }
    return false;
  }

  /** Returns the list of doors of this room to neighbour expansion rooms. */
  @Override
  public List<ExpansionDoor> getDoors() {
    return this.doors;
  }

  /** Removes all doors from this room. */
  @Override
  public void clearDoors() {
    this.doors = new ArrayList<>();
  }

  @Override
  public void resetDoors() {
    for (ExpandableObject currentDoor : this.doors) {
      currentDoor.reset();
    }
  }

  @Override
  public Collection<TargetItemExpansionDoor> getTargetDoors() {
    return new ArrayList<>();
  }

  /** Returns the item associated with this obstacle room. */
  public Item getItem() {
    return this.item;
  }

  @Override
  public SearchTreeObject getObject() {
    return this.item;
  }

  @Override
  public boolean removeDoor(ExpandableObject door) {
    return this.doors.remove(door);
  }

  /** Returns if all doors to the neighbour rooms are calculated. */
  public boolean allDoorsCalculated() {
    return this.doorsCalculated;
  }

  public void setDoorsCalculated(boolean value) {
    this.doorsCalculated = value;
  }

  /** Emits the shape of this room for an optional diagnostic consumer. */
  @Override
  public void emitDiagnostic(AutorouteDiagnostic.Sink sink, double intensity) {
    if (sink != null && intensity > 0) {
      sink.accept(
          new AutorouteDiagnostic(
              AutorouteDiagnostic.Kind.OBSTACLE_ROOM, this.getShape(), this.getLayer(), intensity));
    }
  }
}
