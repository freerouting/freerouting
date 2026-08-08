package app.freerouting.board;

import app.freerouting.datastructures.ShapeTree;

/** Common ShapeSearchTree functionality for board.Items and autoroute.ExpansionRooms */
public interface SearchTreeObject extends ShapeTree.Storable {

  /** Returns true if this object is an obstacle to objects containing the net number p_net_no */
  boolean isObstacle(int p_net_no);

  /** Returns true if this object is an obstacle to traces containing the net number p_net_no */
  boolean isTraceObstacle(int p_net_no);

  /** returns for this object the layer of the shape with index p_index. */
  int shapeLayer(int p_index);

  /** Returns a unique identification number of this object. */
  int getIdNo();
}
