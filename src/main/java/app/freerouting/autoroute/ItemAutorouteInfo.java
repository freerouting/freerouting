package app.freerouting.autoroute;

import app.freerouting.board.Item;
import app.freerouting.board.ShapeSearchTree;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.logger.FRLogger;
import java.awt.Graphics;

/** Temporary data stored in board Items used in the autoroute algorithm */
public class ItemAutorouteInfo {

  private final Item item;

  /** Defines, if this item belongs to the start or destination set of the maze search algorithm */
  private boolean startInfo;

  private Connection precalculatedConnection;

  /** ExpansionRoom for pushing or ripping this object for each tree shape. */
  private ObstacleExpansionRoom[] expansionRoomArr;

  public ItemAutorouteInfo(Item pItem) {
    this.item = pItem;
  }

  /**
   * Looks, if the corresponding item belongs to the start or destination set of the autoroute
   * algorithm. Only used, if the item belongs to the net, which will be currently routed.
   */
  public boolean isStartInfo() {
    return startInfo;
  }

  /**
   * Sets, if the corresponding item belongs to the start or destination set of the autoroute
   * algorithm. Only used, if the item belongs to the net, which will be currently routed.
   */
  public void setStartInfo(boolean pValue) {
    startInfo = pValue;
  }

  /** Returns the precalculated connection of this item or null, if it is not yet precalculated. */
  public Connection getPrecalculatedConnection() {
    return this.precalculatedConnection;
  }

  /** Sets the precalculated connection of this item. */
  public void setPrecalculatedConnection(Connection pConnection) {
    this.precalculatedConnection = pConnection;
  }

  /** Gets the ExpansionRoom of index p_index. Creates it, if it is not yet existing. */
  public ObstacleExpansionRoom getExpansionRoom(int pIndex, ShapeSearchTree pAutorouteTree) {
    int currentShapeCount = this.item.treeShapeCount(pAutorouteTree);

    if (expansionRoomArr == null) {
      expansionRoomArr = new ObstacleExpansionRoom[currentShapeCount];
    } else if (expansionRoomArr.length != currentShapeCount) {
      // Item's tree shape count has changed (e.g., trace modified during routing)
      // Resize the array and preserve existing rooms
      ObstacleExpansionRoom[] newArr = new ObstacleExpansionRoom[currentShapeCount];
      int copyLength = Math.min(expansionRoomArr.length, currentShapeCount);
      System.arraycopy(expansionRoomArr, 0, newArr, 0, copyLength);
      expansionRoomArr = newArr;
    }

    if (pIndex < 0 || pIndex >= expansionRoomArr.length) {
      FRLogger.warn(
          "ItemAutorouteInfo.get_expansion_room: p_index "
              + pIndex
              + " out of range [0, "
              + expansionRoomArr.length
              + ")");
      return null;
    }
    if (expansionRoomArr[pIndex] == null) {
      expansionRoomArr[pIndex] = new ObstacleExpansionRoom(this.item, pIndex, pAutorouteTree);
    }
    return expansionRoomArr[pIndex];
  }

  /** Resets the expansion rooms for autorouting the next connection. */
  public void resetDoors() {
    if (expansionRoomArr != null) {
      for (ObstacleExpansionRoom currRoom : expansionRoomArr) {
        if (currRoom != null) {
          currRoom.resetDoors();
        }
      }
    }
  }

  /** Draws the shapes of the expansion rooms of this info for testing purposes. */
  public void draw(Graphics pGraphics, GraphicsContext pGraphicsContext, double pIntensity) {
    if (expansionRoomArr == null) {
      return;
    }
    for (ObstacleExpansionRoom currRoom : expansionRoomArr) {
      if (currRoom != null) {
        currRoom.draw(pGraphics, pGraphicsContext, pIntensity);
      }
    }
  }
}
