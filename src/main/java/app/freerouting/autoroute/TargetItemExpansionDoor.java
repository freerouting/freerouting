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
      TileShape itemShape = item.getTreeShape(p_search_tree, treeEntryNo);
      this.shape = itemShape.intersection(room.getShape());
    }
    mazeSearchInfo = new MazeSearchElement();
  }

  @Override
  public TileShape getShape() {
    return this.shape;
  }

  @Override
  public int getDimension() {
    return 2;
  }

  public boolean isDestinationDoor() {
    ItemAutorouteInfo itemInfo = this.item.getAutorouteInfo();
    return !itemInfo.isStartInfo();
  }

  @Override
  public CompleteExpansionRoom otherRoom(CompleteExpansionRoom p_room) {
    return null;
  }

  @Override
  public MazeSearchElement getMazeSearchElement(int p_no) {
    return mazeSearchInfo;
  }

  @Override
  public int mazeSearchElementCount() {
    return 1;
  }

  @Override
  public void reset() {
    mazeSearchInfo.reset();
  }

  @Override
  public int getIdNo() {
    // Unique ID for a target door: hash of target item ID and room ID.
    return 31 * item.getIdNo() + (room != null ? room.getIdNo() : 0);
  }
}
