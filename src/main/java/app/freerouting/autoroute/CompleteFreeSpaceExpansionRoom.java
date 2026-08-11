package app.freerouting.autoroute;

import app.freerouting.board.Connectable;
import app.freerouting.board.Item;
import app.freerouting.board.SearchTreeObject;
import app.freerouting.board.ShapeSearchTree;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.datastructures.ShapeTree;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.awt.Color;
import java.awt.Graphics;
import java.util.Collection;
import java.util.LinkedList;

/**
 * An expansion room, whose shape is completely calculated, so that it can be stored in a shape
 * tree.
 */
public class CompleteFreeSpaceExpansionRoom extends FreeSpaceExpansionRoom
    implements CompleteExpansionRoom, SearchTreeObject {

  /** Identification number for implementing the Comparable interface. */
  private final int idNo;

  /** The array of entries in the SearchTree. Consists of just one element. */
  private ShapeTree.Leaf[] treeEntries;

  /** The list of doors to items of the own net. */
  private Collection<TargetItemExpansionDoor> targetDoors;

  private boolean roomIsNetDependent;

  /** Creates a new instance of CompleteFreeSpaceExpansionRoom. */
  public CompleteFreeSpaceExpansionRoom(TileShape shape, int layer, int idNo) {
    super(shape, layer);
    targetDoors = new LinkedList<>();
    this.idNo = idNo;
  }

  @Override
  public void setSearchTreeEntries(ShapeTree.Leaf[] entries, ShapeTree tree) {
    treeEntries = entries;
  }

  @Override
  public int compareTo(Object other) {
    int result;
    if (other instanceof FreeSpaceExpansionRoom) {
      result = ((CompleteFreeSpaceExpansionRoom) other).idNo - this.idNo;
    } else {
      result = -1;
    }
    return result;
  }

  /** Removes the tree entries of this room from shapeTree. */
  public void removeFromTree(ShapeTree shapeTree) {
    shapeTree.remove(this.treeEntries);
  }

  @Override
  public int treeShapeCount(ShapeTree shapeTree) {
    return 1;
  }

  @Override
  public TileShape getTreeShape(ShapeTree shapeTree, int index) {
    return this.getShape();
  }

  @Override
  public int shapeLayer(int index) {
    return this.getLayer();
  }

  @Override
  public boolean isObstacle(int netNo) {
    return true;
  }

  @Override
  public boolean isTraceObstacle(int netNo) {
    return true;
  }

  /** Will be called when the room overlaps with net dependent objects. */
  public void setNetDependent() {
    this.roomIsNetDependent = true;
  }

  /**
   * Returns if the room overlaps with net dependent objects. In this case it cannot be retained
   * when the net number changes in autorouting.
   */
  public boolean isNetDependent() {
    return this.roomIsNetDependent;
  }

  @Override
  public int getIdNo() {
    return idNo;
  }

  /** Returns the list doors to target items of this room. */
  @Override
  public Collection<TargetItemExpansionDoor> getTargetDoors() {
    return this.targetDoors;
  }

  /** Adds door to the list of target doors of this room. */
  public void addTargetDoor(TargetItemExpansionDoor door) {
    this.targetDoors.add(door);
  }

  @Override
  public boolean removeDoor(ExpandableObject door) {
    boolean result;
    if (door instanceof TargetItemExpansionDoor) {
      result = this.targetDoors.remove(door);
    } else {
      result = super.removeDoor(door);
    }
    return result;
  }

  @Override
  public SearchTreeObject getObject() {
    return this;
  }

  /** Calculates the doors to the start and destination items of the autoroute algorithm. */
  public void calculateTargetDoors(
      ShapeTree.TreeEntry ownNetObject, int netNo, ShapeSearchTree autorouteSearchTree) {
    this.setNetDependent();

    if (ownNetObject.object instanceof Connectable currObject) {
      if (currObject.containsNet(netNo)) {
        TileShape currConnectionShape =
            currObject.getTraceConnectionShape(
                autorouteSearchTree, ownNetObject.shapeIndexInObject);
        if (currConnectionShape != null && this.getShape().intersects(currConnectionShape)) {
          Item currItem = (Item) currObject;
          TargetItemExpansionDoor newTargetDoor =
              new TargetItemExpansionDoor(
                  currItem, ownNetObject.shapeIndexInObject, this, autorouteSearchTree);
          this.addTargetDoor(newTargetDoor);
        }
      }
    }
  }

  /** Draws the shape of this room. */
  @Override
  public void draw(Graphics graphics, GraphicsContext graphicsContext, double intensity) {
    Color drawColor = graphicsContext.getTraceColors(false)[this.getLayer()];
    double layerVisibility = graphicsContext.getLayerVisibility(this.getLayer());
    graphicsContext.fillArea(this.getShape(), graphics, drawColor, intensity * layerVisibility);
    graphicsContext.drawBoundary(this.getShape(), 0, drawColor, graphics, layerVisibility);
  }

  /** Check if this FreeSpaceExpansionRoom is valid. */
  public boolean validate(AutorouteEngine autorouteEngine) {
    boolean result = true;
    Collection<ShapeTree.TreeEntry> overlappingObjects = new LinkedList<>();
    int[] netNoArr = new int[1];
    netNoArr[0] = autorouteEngine.getNetNo();
    autorouteEngine.autorouteSearchTree.overlappingTreeEntries(
        this.getShape(), this.getLayer(), netNoArr, overlappingObjects);
    for (ShapeTree.TreeEntry currentEntry : overlappingObjects) {
      if (currentEntry.object == this) {
        continue;
      }
      SearchTreeObject currentObject = (SearchTreeObject) currentEntry.object;
      if (!currentObject.isTraceObstacle(autorouteEngine.getNetNo())) {
        continue;
      }
      if (currentObject.shapeLayer(currentEntry.shapeIndexInObject) != getLayer()) {
        continue;
      }
      TileShape currentShape =
          currentObject.getTreeShape(
              autorouteEngine.autorouteSearchTree, currentEntry.shapeIndexInObject);
      TileShape intersection = this.getShape().intersection(currentShape);
      if (intersection.dimension() > 1) {
        FRLogger.warn("ExpansionRoom overlap conflict");
        result = false;
      }
    }
    return result;
  }

  /** Removes all doors and target doors from this room. */
  @Override
  public void clearDoors() {
    super.clearDoors();
    this.targetDoors = new LinkedList<>();
  }

  @Override
  public void resetDoors() {
    super.resetDoors();
    for (ExpandableObject currDoor : this.targetDoors) {
      currDoor.reset();
    }
  }
}
