package app.freerouting.autoroute.pipeline;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.board.searchtree.SearchTreeObject;
import app.freerouting.geometry.planar.IntBox;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Later exploration tries rip a wider neighbourhood around open pins. The first try does not rip on
 * radius alone; each later try widens the radius and removes other nets' traces and vias inside it.
 *
 * <p>Ported from freerouting.net (Erik Gavriluk, Experimental Television Center). The history of
 * which nets ripped each other is not available here, so the first try leaves copper in place and
 * later tries use the widened any-net neighbourhood.
 */
public final class RetryNeighbourhood {

  public static final double RADIUS_MM = 1.5;
  public static final double MAX_RADIUS_MM = 12;
  public static final int MAX_CONTENDERS = 3;
  public static final int MAX_WIDE_CONTENDERS = 12;

  private RetryNeighbourhood() {}

  /** Radius, contender cap, and whether any nearby net counts. Attempt numbers start at 1. */
  public record Reach(double radiusMm, int contenders, boolean anyNet) {

    public static Reach of(int attempt) {
      if (attempt <= 1) {
        return new Reach(RADIUS_MM, MAX_CONTENDERS, false);
      }
      return new Reach(
          Math.min(RADIUS_MM * attempt, MAX_RADIUS_MM),
          Math.min(MAX_CONTENDERS * attempt, MAX_WIDE_CONTENDERS),
          true);
    }
  }

  /**
   * Removes other nets' traces and vias near pins that still have an open connection. Returns how
   * many items were removed. The first try removes nothing.
   */
  public static int ripNearOpenPins(BasicBoard board, int attempt) {
    Reach reach = Reach.of(attempt);
    if (!reach.anyNet) {
      return 0;
    }
    int radius = radius(board, reach.radiusMm);
    Map<Integer, Item> found = new LinkedHashMap<>();
    for (Pin pin : board.getPins()) {
      if (pin.netCount() != 1 || pin.hasIgnoredNets()) {
        continue;
      }
      int net = pin.getNetNumber(0);
      if (pin.getUnconnectedSet(net).isEmpty()) {
        continue;
      }
      IntBox box = pin.boundingBox().offset(radius);
      for (int layer = 0; layer < board.getLayerCount(); layer++) {
        for (SearchTreeObject object : board.overlappingObjects(box, layer)) {
          if (!(object instanceof Item item)) {
            continue;
          }
          if (!(item instanceof Trace) && !(item instanceof Via)) {
            continue;
          }
          if (item.netCount() != 1 || item.containsNet(net)) {
            continue;
          }
          if (item.isUserFixed() || item.isDeletionForbidden()) {
            continue;
          }
          found.putIfAbsent(item.getId(), item);
        }
      }
    }
    List<Item> items = new ArrayList<>(found.values());
    items.sort(Comparator.comparingInt(Item::getId));
    int removed = 0;
    for (Item item : items) {
      board.removeItem(item);
      removed++;
    }
    return removed;
  }

  public static int radius(BasicBoard board, double mm) {
    return (int) Math.ceil(mm * 1000.0 * board.communication.getResolution(Unit.UM));
  }
}
