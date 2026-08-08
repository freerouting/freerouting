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

  // ** identification number for implementing the Comparable interface */
  private final int idNo;

  /** The array of entries in the SearchTree. Consists of just one element */
  private ShapeTree.Leaf[] treeEntries;

  /** The list of doors to items of the own net */
  private Collection<TargetItemExpansionDoor> targetDoors;

  private boolean roomIsNetDependent;

  /** Creates a new instance of CompleteFreeSpaceExpansionRoom */
  public CompleteFreeSpaceExpansionRoom(TileShape pShape, int pLayer, int pIdNo) {
    super(pShape, pLayer);
    targetDoors = new LinkedList<>();
    idNo = pIdNo;
  }

  @Override
  public void setSearchTreeEntries(ShapeTree.Leaf[] pEntries, ShapeTree pTree) {
    treeEntries = pEntries;
  }

  @Override
  public int compareTo(Object pOther) {
    int result;
    if (pOther instanceof FreeSpaceExpansionRoom) {
      result = ((CompleteFreeSpaceExpansionRoom) pOther).idNo - this.idNo;
    } else {
      result = -1;
    }
    return result;
  }

  /** Removes the tree entries of this room from p_shape_tree. */
  public void removeFromTree(ShapeTree pShapeTree) {
    pShapeTree.remove(this.treeEntries);
  }

  @Override
  public int treeShapeCount(ShapeTree pShapeTree) {
    return 1;
  }

  @Override
  public TileShape getTreeShape(ShapeTree pShapeTree, int pIndex) {
    return this.getShape();
  }

  @Override
  public int shapeLayer(int pIndex) {
    return this.getLayer();
  }

  @Override
  public boolean isObstacle(int pNetNo) {
    return true;
  }

  @Override
  public boolean isTraceObstacle(int pNetNo) {
    return true;
  }

  /** Will be called, when the room overlaps with net dependent objects. */
  public void setNetDependent() {
    this.roomIsNetDependent = true;
  }

  /**
   * Returns, if the room overlaps with net dependent objects. In this case it cannot be retained,
   * when the net number changes in autorouting.
   */
  public boolean isNetDependent() {
    return this.roomIsNetDependent;
  }

  @Override
  public int getIdNo() {
    return idNo;
  }

  /** Returns the list doors to target items of this room */
  @Override
  public Collection<TargetItemExpansionDoor> getTargetDoors() {
    return this.targetDoors;
  }

  /** Adds p_door to the list of target doors of this room. */
  public void addTargetDoor(TargetItemExpansionDoor pDoor) {
    this.targetDoors.add(pDoor);
  }

  @Override
  public boolean removeDoor(ExpandableObject pDoor) {
    boolean result;
    if (pDoor instanceof TargetItemExpansionDoor) {
      result = this.targetDoors.remove(pDoor);
    } else {
      result = super.removeDoor(pDoor);
    }
    return result;
  }

  @Override
  public SearchTreeObject getObject() {
    return this;
  }

  /** Calculates the doors to the start and destination items of the autoroute algorithm. */
  public void calculateTargetDoors(
      ShapeTree.TreeEntry pOwnNetObject, int pNetNo, ShapeSearchTree pAutorouteSearchTree) {
    this.setNetDependent();

    if (pOwnNetObject.object instanceof Connectable currObject) {
      if (currObject.containsNet(pNetNo)) {
        TileShape currConnectionShape =
            currObject.getTraceConnectionShape(
                pAutorouteSearchTree, pOwnNetObject.shapeIndexInObject);
        if (currConnectionShape != null && this.getShape().intersects(currConnectionShape)) {
          Item currItem = (Item) currObject;
          TargetItemExpansionDoor newTargetDoor =
              new TargetItemExpansionDoor(
                  currItem, pOwnNetObject.shapeIndexInObject, this, pAutorouteSearchTree);
          this.addTargetDoor(newTargetDoor);
        }
      }
    }
  }

  /** Draws the shape of this room. */
  @Override
  public void draw(Graphics pGraphics, GraphicsContext pGraphicsContext, double pIntensity) {
    Color drawColor = pGraphicsContext.getTraceColors(false)[this.getLayer()];
    double layerVisibility = pGraphicsContext.getLayerVisibility(this.getLayer());
    pGraphicsContext.fillArea(this.getShape(), pGraphics, drawColor, pIntensity * layerVisibility);
    pGraphicsContext.drawBoundary(this.getShape(), 0, drawColor, pGraphics, layerVisibility);
  }

  /** Check, if this FreeSpaceExpansionRoom is valid. */
  public boolean validate(AutorouteEngine pAutorouteEngine) {
    boolean result = true;
    Collection<ShapeTree.TreeEntry> overlappingObjects = new LinkedList<>();
    int[] netNoArr = new int[1];
    netNoArr[0] = pAutorouteEngine.getNetNo();
    pAutorouteEngine.autorouteSearchTree.overlappingTreeEntries(
        this.getShape(), this.getLayer(), netNoArr, overlappingObjects);
    for (ShapeTree.TreeEntry currEntry : overlappingObjects) {
      if (currEntry.object == this) {
        continue;
      }
      SearchTreeObject currObject = (SearchTreeObject) currEntry.object;
      if (!currObject.isTraceObstacle(pAutorouteEngine.getNetNo())) {
        continue;
      }
      if (currObject.shapeLayer(currEntry.shapeIndexInObject) != getLayer()) {
        continue;
      }
      TileShape currShape =
          currObject.getTreeShape(
              pAutorouteEngine.autorouteSearchTree, currEntry.shapeIndexInObject);
      TileShape intersection = this.getShape().intersection(currShape);
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
