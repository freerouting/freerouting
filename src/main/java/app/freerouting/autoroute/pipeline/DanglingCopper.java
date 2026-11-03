package app.freerouting.autoroute.pipeline;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.drc.NetIncompletes;
import java.util.ArrayList;
import java.util.List;

/**
 * freerouting.net: removes the dangling copper the stages leave on the board that is written: a via
 * with copper of its net on one layer only and a trace with a free end, with the stub behind each
 * up to the next fork, pin or fixed item ({@link RoutingBoard#removeTraceTails}, the pass loop's
 * own cleanup). User-placed copper is fixed and stays. Undone whole if it opens a connection of a
 * tail's net; the open count cannot rise by construction, the check is the guard.
 */
public final class DanglingCopper {

  /**
   * At most this many removal passes: a removed stub can leave the via behind it with one layer of
   * copper.
   */
  private static final int MAX_PASSES = 16;

  private DanglingCopper() {}

  /**
   * Removes the dangling copper on {@code board}. Returns the line to log, or null when there was
   * none.
   */
  public static String remove(RoutingBoard board, String where) {
    List<Item> tails = new ArrayList<>();
    for (Item item : board.getItems()) {
      if (item.isRoutable() && item.netCount() == 1 && item.isTail()) {
        tails.add(item);
      }
    }
    if (tails.isEmpty()) {
      return null;
    }
    int[] nets = tails.stream().mapToInt(t -> t.getNetNumber(0)).distinct().sorted().toArray();
    int openBefore = open(board, nets);
    int viasBefore = board.getVias().size();
    int tracesBefore = board.getTraces().size();
    board.generateSnapshot();
    int passes = 0;
    while (passes < MAX_PASSES && board.removeTraceTails(-1, Item.StopConnectionOption.NONE)) {
      passes++;
    }
    int openAfter = open(board, nets);
    if (openAfter > openBefore) {
      board.undo(null);
      return "Dangling copper after "
          + where
          + ": "
          + tails.size()
          + " tail(s) left as routed, removing them would open "
          + (openAfter - openBefore)
          + " connection(s) of their nets ("
          + openBefore
          + " open before).";
    }
    board.popSnapshot();
    return "Dangling copper after "
        + where
        + ": removed "
        + (viasBefore - board.getVias().size())
        + " via(s) and "
        + (tracesBefore - board.getTraces().size())
        + " trace piece(s) behind "
        + tails.size()
        + " tail(s) in "
        + passes
        + " pass(es); "
        + openAfter
        + " connection(s) of their nets open, as before.";
  }

  private static int open(RoutingBoard board, int[] nets) {
    int count = 0;
    for (int net : nets) {
      count += new NetIncompletes(net, board.getConnectableItems(net), board).count();
    }
    return count;
  }
}
