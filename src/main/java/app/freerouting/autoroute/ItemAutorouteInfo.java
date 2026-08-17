package app.freerouting.autoroute;

import app.freerouting.autoroute.expansion.ObstacleExpansionRoom;
import app.freerouting.autoroute.path.Connection;
import app.freerouting.board.Item;
import app.freerouting.board.searchtree.ShapeSearchTree;
import app.freerouting.logger.FRLogger;

/** Temporary data stored in board Items used in the autoroute algorithm. */
public class ItemAutorouteInfo {

  private final Item item;

  /** Defines, if this item belongs to the start or destination set of the maze search algorithm. */
  private boolean startInfo;

  private Connection precalculatedConnection;

  /** ExpansionRoom for pushing or ripping this object for each tree shape. */
  private ObstacleExpansionRoom[] expansionRoomArr;

  /** Constructs an ItemAutorouteInfo for the given item. */
  public ItemAutorouteInfo(Item item) {
    this.item = item;
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
  public void setStartInfo(boolean value) {
    startInfo = value;
  }

  /** Returns the precalculated connection of this item or null, if it is not yet precalculated. */
  public Connection getPrecalculatedConnection() {
    return this.precalculatedConnection;
  }

  /** Sets the precalculated connection of this item. */
  public void setPrecalculatedConnection(Connection connection) {
    this.precalculatedConnection = connection;
  }

  /** Gets the ExpansionRoom of index index. Creates it, if it is not yet existing. */
  public ObstacleExpansionRoom getExpansionRoom(int index, ShapeSearchTree autorouteTree) {
    int currentShapeCount = this.item.treeShapeCount(autorouteTree);

    if (expansionRoomArr == null) {
      expansionRoomArr = new ObstacleExpansionRoom[currentShapeCount];
    } else if (expansionRoomArr.length != currentShapeCount) {
      // Item's tree shape count has changed (e.g., trace modified during routing)
      // Resize the array and preserve existing rooms
      ObstacleExpansionRoom[] newArray = new ObstacleExpansionRoom[currentShapeCount];
      int copyLength = Math.min(expansionRoomArr.length, currentShapeCount);
      System.arraycopy(expansionRoomArr, 0, newArray, 0, copyLength);
      expansionRoomArr = newArray;
    }

    if (index < 0 || index >= expansionRoomArr.length) {
      FRLogger.warn(
          "ItemAutorouteInfo.get_expansion_room: index "
              + index
              + " out of range [0, "
              + expansionRoomArr.length
              + ")");
      return null;
    }
    if (expansionRoomArr[index] == null) {
      expansionRoomArr[index] = new ObstacleExpansionRoom(this.item, index, autorouteTree);
    }
    return expansionRoomArr[index];
  }

  /** Resets the expansion rooms for autorouting the next connection. */
  public void resetDoors() {
    if (expansionRoomArr != null) {
      for (ObstacleExpansionRoom currentRoom : expansionRoomArr) {
        if (currentRoom != null) {
          currentRoom.resetDoors();
        }
      }
    }
  }

  /** Emits optional diagnostics for the expansion rooms of this info. */
  public void emitDiagnostics(AutorouteDiagnostic.Sink sink, double intensity) {
    if (sink == null || intensity <= 0 || expansionRoomArr == null) {
      return;
    }
    for (ObstacleExpansionRoom currentRoom : expansionRoomArr) {
      if (currentRoom != null) {
        currentRoom.emitDiagnostic(sink, intensity);
      }
    }
  }
}
