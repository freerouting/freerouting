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

  /** The list of doors to neighbour expansion rooms */
  private List<ExpansionDoor> doors;

  private boolean doorsCalculated;

  /** Creates a new instance of ObstacleExpansionRoom */
  ObstacleExpansionRoom(Item pItem, int pIndexInItem, ShapeSearchTree pShapeTree) {
    this.item = pItem;
    this.indexInItem = pIndexInItem;
    this.shape = pItem.getTreeShape(pShapeTree, pIndexInItem);
    this.doors = new ArrayList<>();
  }

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

  /** Checks, if this room has already a 1-dimensional door to p_other */
  @Override
  public boolean doorExists(ExpansionRoom pOther) {
    if (doors != null) {
      for (ExpansionDoor currDoor : this.doors) {
        if (currDoor.firstRoom == pOther || currDoor.secondRoom == pOther) {
          return true;
        }
      }
    }
    return false;
  }

  /** Adds a door to the door list of this room. */
  @Override
  public void addDoor(ExpansionDoor pDoor) {
    this.doors.add(pDoor);
  }

  /**
   * Creates a 2-dim door with the other obstacle room, if that is useful for the autoroute
   * algorithm. It is assumed that this room and p_other have a 2-dimensional overlap. Returns
   * false, if no door was created.
   */
  public boolean createOverlapDoor(ObstacleExpansionRoom pOther) {
    if (this.doorExists(pOther)) {
      return false;
    }
    if (!(this.item.isRoutable() && pOther.item.isRoutable())) {
      return false;
    }
    if (!this.item.sharesNet(pOther.item)) {
      return false;
    }
    if (this.item == pOther.item) {
      if (!(this.item instanceof PolylineTrace)) {
        return false;
      }
      // create only doors between consecutive trace segments
      if (this.indexInItem != pOther.indexInItem + 1
          && this.indexInItem != pOther.indexInItem - 1) {
        return false;
      }
    }
    ExpansionDoor newDoor = new ExpansionDoor(this, pOther, 2);
    this.addDoor(newDoor);
    pOther.addDoor(newDoor);
    return true;
  }

  /** Returns the list of doors of this room to neighbour expansion rooms */
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

  public Item getItem() {
    return this.item;
  }

  @Override
  public SearchTreeObject getObject() {
    return this.item;
  }

  @Override
  public boolean removeDoor(ExpandableObject pDoor) {
    return this.doors.remove(pDoor);
  }

  /** Returns, if all doors to the neighbour rooms are calculated. */
  boolean allDoorsCalculated() {
    return this.doorsCalculated;
  }

  void setDoorsCalculated(boolean pValue) {
    this.doorsCalculated = pValue;
  }

  /** Draws the shape of this room. */
  @Override
  public void draw(Graphics pGraphics, GraphicsContext pGraphicsContext, double pIntensity) {
    Color drawColor = Color.WHITE;
    double layerVisibility = pGraphicsContext.getLayerVisibility(this.getLayer());
    pGraphicsContext.fillArea(this.getShape(), pGraphics, drawColor, pIntensity * layerVisibility);
    pGraphicsContext.drawBoundary(this.getShape(), 0, drawColor, pGraphics, layerVisibility);
  }
}
