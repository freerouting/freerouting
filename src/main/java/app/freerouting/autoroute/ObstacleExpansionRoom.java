package app.freerouting.autoroute;

import app.freerouting.board.Item;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.SearchTreeObject;
import app.freerouting.board.ShapeSearchTree;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.geometry.planar.TileShape;
import java.awt.Color;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Expansion Room used for pushing and ripping obstacles in the autoroute algorithm. */
public class ObstacleExpansionRoom implements CompleteExpansionRoom {

  private final Item item;
  private final int indexInItem;
  private final TileShape shape;

  /** The list of doors to neighbour expansion rooms. */
  private List<ExpansionDoor> doors;

  private boolean doorsCalculated;

  /** Creates a new instance of ObstacleExpansionRoom. */
  ObstacleExpansionRoom(Item item, int indexInItem, ShapeSearchTree shapeTree) {
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
  public int getIdNo() {
    return (this.item.getIdNo() << 10) | this.indexInItem;
  }

  /** Checks if this room already has a 1-dimensional door to other. */
  @Override
  public boolean doorExists(ExpansionRoom other) {
    if (doors != null) {
      for (ExpansionDoor currDoor : this.doors) {
        if (currDoor.firstRoom == other || currDoor.secondRoom == other) {
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
    ExpansionDoor newDoor = new ExpansionDoor(this, other, 2);
    this.addDoor(newDoor);
    other.addDoor(newDoor);
    return true;
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
    for (ExpandableObject currDoor : this.doors) {
      currDoor.reset();
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
  boolean allDoorsCalculated() {
    return this.doorsCalculated;
  }

  void setDoorsCalculated(boolean value) {
    this.doorsCalculated = value;
  }

  /** Draws the shape of this room. */
  @Override
  public void draw(Graphics graphics, GraphicsContext graphicsContext, double intensity) {
    Color drawColor = Color.WHITE;
    double layerVisibility = graphicsContext.getLayerVisibility(this.getLayer());
    graphicsContext.fillArea(this.getShape(), graphics, drawColor, intensity * layerVisibility);
    graphicsContext.drawBoundary(this.getShape(), 0, drawColor, graphics, layerVisibility);
  }
}
