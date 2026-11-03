package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.AutorouteAttemptResult;
import app.freerouting.autoroute.AutorouteAttemptState;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.core.StoppableThread;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.DefaultSettings;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * A strict-DRC rejection must be all-or-nothing: the connections a rejected route ripped up have to
 * come back with it.
 */
class StrictDrcRollbackTest {

  /** A ripup pass late enough that the router used to apply strict DRC without being asked to. */
  private static final int LATE_RIPUP_PASS = 3;

  /**
   * Net X has to cross the routed U-shaped wire of net N, so its route rips N up. The imported X
   * wire already runs 100 um from the pad of net Y (clearance 200 um) and the route continues it,
   * so the new X wiring carries that clearance violation and strict DRC rejects it.
   */
  private static final String DSN =
      """
      (pcb strictdrc (resolution um 1)
        (structure (layer top (type signal))
          (boundary (rect pcb 0 0 20000 10000))
          (via V) (rule (width 300) (clearance 200)))
        (placement (component C (place A 3000 3000 front 0) (place B 17000 3000 front 0)
          (place D 10000 9500 front 0) (place E 12000 9500 front 0)
          (place F 5000 3400 front 0)))
        (library (image C (pin S 1 0 0)) (padstack S (shape (rect top -150 -150 150 150)))
          (padstack V (shape (circle top 600)) (attach off)))
        (network (net X (pins A-1 B-1)) (net N (pins D-1 E-1)) (net Y (pins F-1)))
        (wiring
          (wire (path top 300 3000 3000 6000 3000) (net X))
          (wire (path top 150 10000 9500 10000 600 12000 600 12000 9500) (net N))))
      """;

  private record Setup(BatchAutorouter router, Pin pinA, int netX, int netN) {}

  private static Setup prepare(boolean strictDrc) {
    BoardReadResult result =
        DsnReader.readBoard(
            new ByteArrayInputStream(DSN.getBytes(StandardCharsets.UTF_8)),
            null,
            null,
            "strictdrc");
    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();

    RouterSettings settings = new SettingsMerger(new DefaultSettings()).merge();
    settings.strictDrc = strictDrc;
    settings.setLayerCount(board.getLayerCount());
    settings.applyBoardSpecificOptimizations(board);
    BatchAutorouter router =
        new BatchAutorouter(
            new StoppableThread() {
              @Override
              protected void threadAction() {}
            },
            board,
            settings,
            true,
            true,
            settings.getStartRipupCosts(),
            500);

    int netX = board.rules.nets.get("X").iterator().next().netNumber;
    int netN = board.rules.nets.get("N").iterator().next().netNumber;
    assertEquals(1, incompletes(board, netX));
    assertEquals(0, incompletes(board, netN));
    return new Setup(router, pin(board, "A"), netX, netN);
  }

  private static AutorouteAttemptResult routeX(Setup s, SortedSet<Item> ripped) {
    return new AutorouteConnectionRouter(s.router())
        .route(s.pinA(), s.netX(), ripped, new HashMap<>(), LATE_RIPUP_PASS);
  }

  @Test
  void withoutStrictDrcLatePassesKeepTheRouteInsteadOfLosingWhatItRippedUp() {
    Setup s = prepare(false);
    SortedSet<Item> ripped = new TreeSet<>();

    AutorouteAttemptResult result = routeX(s, ripped);

    assertTrue(ripped.stream().anyMatch(item -> item.containsNet(s.netN())), "X must rip N");
    RoutingBoard board = s.router().board;
    assertEquals(1, incompletes(board), "routing X may only cost the N connection it ripped up");
    assertEquals(AutorouteAttemptState.ROUTED, result.state, result.details);
    assertEquals(0, incompletes(board, s.netX()));
  }

  @Test
  void strictDrcRejectionRestoresTheConnectionsTheRouteRippedUp() {
    Setup s = prepare(true);
    SortedSet<Item> ripped = new TreeSet<>();

    AutorouteAttemptResult result = routeX(s, ripped);

    assertTrue(ripped.stream().anyMatch(item -> item.containsNet(s.netN())), "X must rip N");
    assertEquals(AutorouteAttemptState.FAILED, result.state, result.details);
    RoutingBoard board = s.router().board;
    assertEquals(0, incompletes(board, s.netN()), "the ripped N wire must be restored");
    assertEquals(1, incompletes(board, s.netX()));
    assertEquals(1, traceCount(board, s.netX()), "the imported X wire must be kept");
  }

  private static int incompletes(RoutingBoard board) {
    DesignRulesChecker drc = new DesignRulesChecker(board, null);
    drc.calculateAllIncompletes();
    return drc.getIncompleteCount();
  }

  private static int incompletes(RoutingBoard board, int net) {
    DesignRulesChecker drc = new DesignRulesChecker(board, null);
    drc.calculateAllIncompletes();
    return drc.getIncompleteCount(net);
  }

  private static long traceCount(RoutingBoard board, int net) {
    return board.getTraces().stream().filter(t -> t.containsNet(net)).count();
  }

  private static Pin pin(RoutingBoard board, String component) {
    return board.getPins().stream()
        .filter(p -> component.equals(board.components.get(p.getComponentId()).name))
        .findFirst()
        .orElseThrow();
  }
}
