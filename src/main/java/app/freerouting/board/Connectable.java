package app.freerouting.board;

import app.freerouting.geometry.planar.TileShape;
import java.util.Set;

/** Functionality required for items, which can be electrical connected to other items. */
public interface Connectable {

  /** Returns true if this item belongs to the net with number p_net_no. */
  boolean containsNet(int netNo);

  /** Returns true if the net number array of this and p_net_no_arr have a common number. */
  boolean sharesNetNo(int[] netNoArr);

  /** Returns a list of all connectable items overlapping and sharing a net with this item. */
  Set<Item> getAllContacts();

  /**
   * Returns a list of all connectable items overlapping with this item on the input layer and
   * sharing a net with this item.
   */
  Set<Item> getAllContacts(int layer);

  /**
   * Returns the list of all contacts of a connectable item located at defined connection points.
   * Connection points of traces are there endpoints, connection points of drill_items there center
   * points, and connection points of conduction areas are points on there border.
   */
  Set<Item> getNormalContacts();

  /**
   * Returns all connectable items of the net with number p_net_no, which can be reached recursively
   * from this item via normal contacts. if p_net_no {@literal <}= 0, the net number is ignored.
   */
  Set<Item> getConnectedSet(int netNo);

  /**
   * Returns for each convex shape of a connectable item the subshape of points, where traces can be
   * connected to that item.
   */
  TileShape getTraceConnectionShape(ShapeSearchTree tree, int index);
}
