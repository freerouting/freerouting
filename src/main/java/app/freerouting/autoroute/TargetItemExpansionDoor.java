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
      Item pItem, int pTreeEntryNo, CompleteExpansionRoom pRoom, ShapeSearchTree pSearchTree) {
    item = pItem;
    treeEntryNo = pTreeEntryNo;
    room = pRoom;
    if (room == null) {
      this.shape = Simplex.EMPTY;
    } else {
      TileShape itemShape = item.getTreeShape(pSearchTree, treeEntryNo);
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
  public CompleteExpansionRoom otherRoom(CompleteExpansionRoom pRoom) {
    return null;
  }

  @Override
  public MazeSearchElement getMazeSearchElement(int pNo) {
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
