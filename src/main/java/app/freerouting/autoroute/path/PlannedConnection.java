package app.freerouting.autoroute.path;

import app.freerouting.board.model.items.Item;
import app.freerouting.geometry.planar.IntPoint;
import java.util.Collection;

/** Geometry of one found connection. Corner arrays are copied so the plan outlives the search. */
public final class PlannedConnection {

  public final int netNumber;
  public final int startItemId;
  public final int targetItemId;
  public final int startLayer;
  public final int targetLayer;
  public final int[] rippedItemIds;
  public final Segment[] segments;

  private PlannedConnection(
      int netNumber,
      int startItemId,
      int targetItemId,
      int startLayer,
      int targetLayer,
      int[] rippedItemIds,
      Segment[] segments) {
    this.netNumber = netNumber;
    this.startItemId = startItemId;
    this.targetItemId = targetItemId;
    this.startLayer = startLayer;
    this.targetLayer = targetLayer;
    this.rippedItemIds = rippedItemIds;
    this.segments = segments;
  }

  /** Copies the locator result. Returns null when the connection has no start or target item. */
  public static PlannedConnection from(
      int netNumber, FoundConnectionLocator connection, Collection<Item> rippedItems) {
    if (connection == null
        || connection.connectionItems == null
        || connection.startItem == null
        || connection.targetItem == null) {
      return null;
    }
    Segment[] segments = new Segment[connection.connectionItems.size()];
    int index = 0;
    for (FoundConnectionLocator.ResultItem item : connection.connectionItems) {
      segments[index++] = new Segment(copy(item.corners), item.layer);
    }
    int[] rippedIds = new int[rippedItems == null ? 0 : rippedItems.size()];
    if (rippedItems != null) {
      int rippedIndex = 0;
      for (Item rippedItem : rippedItems) {
        rippedIds[rippedIndex++] = rippedItem.getId();
      }
    }
    return new PlannedConnection(
        netNumber,
        connection.startItem.getId(),
        connection.targetItem.getId(),
        connection.startLayer,
        connection.targetLayer,
        rippedIds,
        segments);
  }

  private static IntPoint[] copy(IntPoint[] corners) {
    if (corners == null) {
      return new IntPoint[0];
    }
    IntPoint[] copied = new IntPoint[corners.length];
    System.arraycopy(corners, 0, copied, 0, corners.length);
    return copied;
  }

  /** One trace of the planned connection. */
  public static final class Segment {
    public final IntPoint[] corners;
    public final int layer;

    public Segment(IntPoint[] corners, int layer) {
      this.corners = corners;
      this.layer = layer;
    }
  }
}
