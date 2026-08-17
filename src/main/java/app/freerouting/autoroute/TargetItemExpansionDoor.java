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

  /** Creates a new instance of ItemExpansionInfo. */
  public TargetItemExpansionDoor(
      Item item, int treeEntryNo, CompleteExpansionRoom room, ShapeSearchTree searchTree) {
    this.item = item;
    this.treeEntryNo = treeEntryNo;
    this.room = room;
    if (room == null) {
      this.shape = Simplex.EMPTY;
    } else {
      TileShape itemShape = item.getTreeShape(searchTree, treeEntryNo);
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

  /** Returns true if this door leads to a destination item rather than a start item. */
  public boolean isDestinationDoor() {
    ItemAutorouteInfo itemInfo = this.item.getAutorouteInfo();
    return !itemInfo.isStartInfo();
  }

  @Override
  public CompleteExpansionRoom otherRoom(CompleteExpansionRoom room) {
    return null;
  }

  @Override
  public MazeSearchElement getMazeSearchElement(int index) {
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
  public int getId() {
    // Unique ID for a target door: hash of target item ID and room ID.
    return 31 * item.getId() + (room != null ? room.getId() : 0);
  }
}
