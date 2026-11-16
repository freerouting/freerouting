package app.freerouting.board.searchtree;

import app.freerouting.datastructures.ShapeTree;

/** Common ShapeSearchTree functionality for board.Items and autoroute.ExpansionRooms. */
public interface SearchTreeObject extends ShapeTree.Storable {

  /** Returns true if this object is an obstacle to objects containing the net number netNumber. */
  boolean isObstacle(int netNumber);

  /** Returns true if this object is an obstacle to traces containing the net number netNumber. */
  boolean isTraceObstacle(int netNumber);

  /**
   * Returns true if the tree shape with index shapeIndex of this object is an obstacle to traces
   * containing the net number netNumber. By default the same for all shapes of the object.
   */
  default boolean isTraceObstacle(int netNumber, int shapeIndex) {
    return isTraceObstacle(netNumber);
  }

  /** Returns for this object the layer of the shape with index index. */
  int shapeLayer(int index);

  /** Returns a unique identification number of this object. */
  int getId();
}
