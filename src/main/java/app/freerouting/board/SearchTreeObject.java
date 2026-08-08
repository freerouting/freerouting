package app.freerouting.board;

import app.freerouting.datastructures.ShapeTree;

/** Common ShapeSearchTree functionality for board.Items and autoroute.ExpansionRooms */
public interface SearchTreeObject extends ShapeTree.Storable {

  /** Returns true if this object is an obstacle to objects containing the net number p_net_no */
  boolean isObstacle(int pNetNo);

  /** Returns true if this object is an obstacle to traces containing the net number p_net_no */
  boolean isTraceObstacle(int pNetNo);

  /** returns for this object the layer of the shape with index p_index. */
  int shapeLayer(int pIndex);

  /** Returns a unique identification number of this object. */
  int getIdNo();
}
