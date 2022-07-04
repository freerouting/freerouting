package app.freerouting.autoroute;

import app.freerouting.board.Item;
import app.freerouting.board.ShapeSearchTree;
import app.freerouting.geometry.planar.Simplex;
import app.freerouting.geometry.planar.TileShape;

/** An expansion door leading to a start or destination item of the autoroute algorithm. */
public class TargetItemExpansionDoor implements ExpandableObject {

  public final Item item;
  public final int tree_entry_no;
  public final CompleteExpansionRoom room;
  private final TileShape shape;
  private final MazeSearchElement maze_search_info;

  /** Creates a new instance of ItemExpansionInfo */
  public TargetItemExpansionDoor(
      Item p_item,
      int p_tree_entry_no,
      CompleteExpansionRoom p_room,
      ShapeSearchTree p_search_tree) {
    item = p_item;
    tree_entry_no = p_tree_entry_no;
    room = p_room;
    if (room == null) {
      this.shape = Simplex.EMPTY;
    } else {
      TileShape item_shape = item.get_tree_shape(p_search_tree, tree_entry_no);
      this.shape = item_shape.intersection(room.get_shape());
    }
    maze_search_info = new MazeSearchElement();
  }

  public TileShape get_shape() {
    return this.shape;
  }

  public int get_dimension() {
    return 2;
  }

  public boolean is_destination_door() {
    ItemAutorouteInfo item_info = this.item.get_autoroute_info();
    return !item_info.is_start_info();
  }

  public CompleteExpansionRoom other_room(CompleteExpansionRoom p_room) {
    return null;
  }

  public MazeSearchElement get_maze_search_element(int p_no) {
    return maze_search_info;
  }

  public int maze_search_element_count() {
    return 1;
  }

  public void reset() {
    maze_search_info.reset();
  }
}
