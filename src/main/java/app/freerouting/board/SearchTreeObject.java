package app.freerouting.board;

import app.freerouting.datastructures.ShapeTree;

/** Common ShapeSearchTree functionality for board.Items and autoroute.ExpansionRooms. */
public interface SearchTreeObject extends ShapeTree.Storable {

  /** Returns true if this object is an obstacle to objects containing the net number netNumber. */
  boolean isObstacle(int netNumber);

  /** Returns true if this object is an obstacle to traces containing the net number netNumber. */
  boolean isTraceObstacle(int netNumber);

  /** Returns for this object the layer of the shape with index index. */
  int shapeLayer(int index);

  /** Returns a unique identification number of this object. */
  int getIdNo();
}
