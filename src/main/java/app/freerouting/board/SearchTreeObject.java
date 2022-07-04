package app.freerouting.board;

/** Common ShapeSearchTree functionality for board.Items and autoroute.ExpansionRooms */
public interface SearchTreeObject extends app.freerouting.datastructures.ShapeTree.Storable {
  /** Returns true if this object is an obstacle to objects containing the net number p_net_no */
  boolean is_obstacle(int p_net_no);

  /** Returns true if this object is an obstacle to traces containing the net number p_net_no */
  boolean is_trace_obstacle(int p_net_no);

  /** returns for this object the layer of the shape with index p_index. */
  int shape_layer(int p_index);
}
