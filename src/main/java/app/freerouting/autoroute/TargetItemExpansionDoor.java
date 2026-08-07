package app.freerouting.autoroute;

import app.freerouting.board.Item;
import app.freerouting.board.ShapeSearchTree;
import app.freerouting.geometry.planar.Simplex;
import app.freerouting.geometry.planar.TileShape;

/** An expansion door leading to a start or destination item of the autoroute algorithm. */
public class TargetItemExpansionDoor implements ExpandableObject {

  public final Item item;
  public final int treeEntryNo;
  public final CompleteExpansionRoom room;
  private final TileShape shape;
  private final MazeSearchElement mazeSearchInfo;

  /** Creates a new instance of ItemExpansionInfo */
  public TargetItemExpansionDoor(
      Item p_item,
      int p_tree_entry_no,
      CompleteExpansionRoom p_room,
      ShapeSearchTree p_search_tree) {
    item = p_item;
    treeEntryNo = p_tree_entry_no;
    room = p_room;
    if (room == null) {
      this.shape = Simplex.EMPTY;
    } else {
      TileShape itemShape = item.get_tree_shape(p_search_tree, treeEntryNo);
      this.shape = itemShape.intersection(room.get_shape());
    }
    mazeSearchInfo = new MazeSearchElement();
  }

  @Override
  public TileShape get_shape() {
    return this.shape;
  }

  @Override
  public int get_dimension() {
    return 2;
  }

  public boolean is_destination_door() {
    ItemAutorouteInfo itemInfo = this.item.get_autoroute_info();
    return !itemInfo.is_start_info();
  }

  @Override
  public CompleteExpansionRoom other_room(CompleteExpansionRoom p_room) {
    return null;
  }

  @Override
  public MazeSearchElement get_maze_search_element(int p_no) {
    return mazeSearchInfo;
  }

  @Override
  public int maze_search_element_count() {
    return 1;
  }

  @Override
  public void reset() {
    mazeSearchInfo.reset();
  }

  @Override
  public int get_id_no() {
    // Unique ID for a target door: hash of target item ID and room ID.
    return 31 * item.get_id_no() + (room != null ? room.get_id_no() : 0);
  }
}
