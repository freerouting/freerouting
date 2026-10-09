package app.freerouting.autoroute.pipeline;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.searchtree.SearchTreeObject;
import app.freerouting.drc.AirLine;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Removes a bounded set of unfixed foreign traces and vias that overlap the bounding boxes of the
 * remaining airlines. Used when the autorouter has only a few incomplete connections and the score
 * has stopped improving.
 */
final class LastMileBlockerRipup {

  static final int MAX_ITEMS = 24;

  private LastMileBlockerRipup() {}

  /** Rips blockers for the current incomplete airlines. Returns the number of items removed. */
  static int ripBlockers(RoutingBoard board) {
    DesignRulesChecker drc = new DesignRulesChecker(board, null);
    AirLine[] airlines = drc.getAllAirlines();
    if (airlines.length == 0) {
      return 0;
    }
    Set<Item> blockers = new LinkedHashSet<>();
    for (AirLine airline : airlines) {
      if (airline == null || airline.net == null) {
        continue;
      }
      IntBox corridor = corridor(airline.fromCorner, airline.toCorner);
      Set<SearchTreeObject> overlapping = board.overlappingObjects(corridor, -1);
      for (SearchTreeObject object : overlapping) {
        if (!(object instanceof Item item)) {
          continue;
        }
        if (blockers.size() >= MAX_ITEMS) {
          break;
        }
        if (isRemovableBlocker(item, airline.net.netNumber)) {
          blockers.add(item);
        }
      }
      if (blockers.size() >= MAX_ITEMS) {
        break;
      }
    }
    if (blockers.isEmpty()) {
      return 0;
    }
    List<Item> toRemove = new ArrayList<>(blockers);
    board.removeItems(toRemove);
    return toRemove.size();
  }

  static boolean isRemovableBlocker(Item item, int airlineNetNumber) {
    if (!(item instanceof Trace) && !(item instanceof Via)) {
      return false;
    }
    if (item.isDeletionForbidden() || item.isUserFixed()) {
      return false;
    }
    for (int i = 0; i < item.netCount(); i++) {
      if (item.getNetNumber(i) == airlineNetNumber) {
        return false;
      }
    }
    return item.netCount() > 0;
  }

  static IntBox corridor(FloatPoint from, FloatPoint to) {
    int x1 = (int) Math.round(from.x);
    int y1 = (int) Math.round(from.y);
    int x2 = (int) Math.round(to.x);
    int y2 = (int) Math.round(to.y);
    int minX = Math.min(x1, x2);
    int maxX = Math.max(x1, x2);
    int minY = Math.min(y1, y2);
    int maxY = Math.max(y1, y2);
    int span = Math.max(maxX - minX, maxY - minY);
    int margin = Math.max(span / 4, 1);
    return new IntBox(minX - margin, minY - margin, maxX + margin, maxY + margin);
  }
}
