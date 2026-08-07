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

  public ItemAutorouteInfo(Item p_item) {
    this.item = p_item;
  }

  /**
   * Looks, if the corresponding item belongs to the start or destination set of the autoroute
   * algorithm. Only used, if the item belongs to the net, which will be currently routed.
   */
  public boolean is_start_info() {
    return startInfo;
  }

  /**
   * Sets, if the corresponding item belongs to the start or destination set of the autoroute
   * algorithm. Only used, if the item belongs to the net, which will be currently routed.
   */
  public void set_start_info(boolean p_value) {
    startInfo = p_value;
  }

  /** Returns the precalculated connection of this item or null, if it is not yet precalculated. */
  public Connection get_precalculated_connection() {
    return this.precalculatedConnection;
  }

  /** Sets the precalculated connection of this item. */
  public void set_precalculated_connection(Connection p_connection) {
    this.precalculatedConnection = p_connection;
  }

  /** Gets the ExpansionRoom of index p_index. Creates it, if it is not yet existing. */
  public ObstacleExpansionRoom get_expansion_room(int p_index, ShapeSearchTree p_autoroute_tree) {
    int currentShapeCount = this.item.tree_shape_count(p_autoroute_tree);

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

    if (p_index < 0 || p_index >= expansionRoomArr.length) {
      FRLogger.warn(
          "ItemAutorouteInfo.get_expansion_room: p_index "
              + p_index
              + " out of range [0, "
              + expansionRoomArr.length
              + ")");
      return null;
    }
    if (expansionRoomArr[p_index] == null) {
      expansionRoomArr[p_index] = new ObstacleExpansionRoom(this.item, p_index, p_autoroute_tree);
    }
    return expansionRoomArr[p_index];
  }

  /** Resets the expansion rooms for autorouting the next connection. */
  public void reset_doors() {
    if (expansionRoomArr != null) {
      for (ObstacleExpansionRoom currRoom : expansionRoomArr) {
        if (currRoom != null) {
          currRoom.reset_doors();
        }
      }
    }
  }

  /** Draws the shapes of the expansion rooms of this info for testing purposes. */
  public void draw(Graphics p_graphics, GraphicsContext p_graphics_context, double p_intensity) {
    if (expansionRoomArr == null) {
      return;
    }
    for (ObstacleExpansionRoom currRoom : expansionRoomArr) {
      if (currRoom != null) {
        currRoom.draw(p_graphics, p_graphics_context, p_intensity);
      }
    }
  }
}
