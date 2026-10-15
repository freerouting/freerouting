package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.AutorouteAttemptResult;
import app.freerouting.autoroute.AutorouteAttemptState;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.core.StoppableThread;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.DefaultSettings;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** Erik Gavriluk (freerouting.net): router.autorouter.start_necked optimization tests. */
class StartNeckedTest {

  private static final String DSN =
      """
      (pcb startneck (resolution um 1)
        (structure (layer top (type signal))
          (boundary (rect pcb 0 0 20000 10000))
          (via V) (rule (width 400) (clearance 200)))
        (placement (component C (place A 3000 3000 front 0) (place B 17000 3000 front 0)
          (place D 10000 9500 front 0) (place E 12000 9500 front 0)))
        (library (image C (pin S 1 0 0)) (padstack S (shape (circle top 800)))
          (padstack V (shape (circle top 600)) (attach off)))
        (network (net X (pins A-1 B-1)) (net N (pins D-1 E-1))))
      """;

  private record Setup(
      RoutingBoard board,
      BatchAutorouter router,
      AutorouteConnectionRouter connection,
      Pin a,
      int x,
      int n) {}

  private static Setup prepare(Boolean startNeckedOverride) {
    BoardReadResult result =
        DsnReader.readBoard(
            new ByteArrayInputStream(DSN.getBytes(StandardCharsets.UTF_8)),
            null,
            null,
            "startneck");
    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();
    int x = board.rules.nets.get("X").iterator().next().netNumber;
    int n = board.rules.nets.get("N").iterator().next().netNumber;
    assertEquals(200, board.rules.getTraceHalfWidth(x, 0));

    Point dCenter = center(board, "D");
    Point eCenter = center(board, "E");
    IntPoint d = (IntPoint) dCenter;
    IntPoint e = (IntPoint) eCenter;

    assertNotNull(
        board.insertTraceWithoutCleaning(
            new Polyline(new Point[] {d, new IntPoint(10000, 300), new IntPoint(12000, 300), e}),
            0,
            75,
            new int[] {n},
            1,
            FixedState.UNFIXED));

    assertEquals(0, incomplete(board, n));
    assertEquals(1, incomplete(board, x));

    RouterSettings settings = new SettingsMerger(new DefaultSettings()).merge();
    settings.neckWidthUm = 150.0;
    settings.setLayerCount(board.getLayerCount());
    settings.applyBoardSpecificOptimizations(board);
    if (startNeckedOverride != null) {
      settings.autorouter.startNecked = startNeckedOverride;
    }

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

    Pin pinA =
        board.getPins().stream()
            .filter(p -> "A".equals(board.components.get(p.getComponentId()).name))
            .findFirst()
            .orElseThrow();

    return new Setup(board, router, new AutorouteConnectionRouter(router), pinA, x, n);
  }

  @Test
  void isOnByDefaultAndSettingTurnsItOff() {
    RouterSettings defaults = new SettingsMerger(new DefaultSettings()).merge();
    assertTrue(defaults.autorouter.startNecked);
    assertTrue(prepare(null).router().isStartNecked());
    assertFalse(prepare(false).router().isStartNecked());
  }

  @Test
  void netThatNeckedBeforeRoutesAtNeckWidth() {
    Setup s = prepare(null);
    s.router().getNeckedRouteNets().add(s.x());
    SortedSet<Item> ripped = new TreeSet<>();
    AutorouteAttemptResult result = s.connection().route(s.a(), s.x(), ripped, new HashMap<>(), 1);

    assertEquals(AutorouteAttemptState.ROUTED, result.state);
    assertEquals(1, s.router().getStartNeckedRouted());
    assertEquals(0, s.router().getStartNeckedFailed());
    assertEquals(0, incomplete(s.board(), s.x()));
    assertEquals(1, incomplete(s.board(), s.n()));
    assertTrue(ripped.stream().anyMatch(item -> item instanceof Trace t && t.containsNet(s.n())));
    assertXHalfWidths(s, 75);
    assertTrue(new DesignRulesChecker(s.board(), null).getAllClearanceViolations().isEmpty());
  }

  @Test
  void withStartNeckedOffNetSearchesAtClassWidth() {
    Setup s = prepare(false);
    s.router().getNeckedRouteNets().add(s.x());
    SortedSet<Item> ripped = new TreeSet<>();
    AutorouteAttemptResult result = s.connection().route(s.a(), s.x(), ripped, new HashMap<>(), 1);

    assertEquals(AutorouteAttemptState.ROUTED, result.state);
    assertEquals(0, s.router().getStartNeckedRouted() + s.router().getStartNeckedFailed());
    assertXHalfWidths(s, 200);
  }

  @Test
  void netThatHasNotNeckedStartsAtClassWidth() {
    Setup s = prepare(null);
    assertFalse(s.router().getNeckedRouteNets().contains(s.x()));
    SortedSet<Item> ripped = new TreeSet<>();
    AutorouteAttemptResult result = s.connection().route(s.a(), s.x(), ripped, new HashMap<>(), 1);

    assertEquals(AutorouteAttemptState.ROUTED, result.state);
    assertEquals(0, s.router().getStartNeckedRouted() + s.router().getStartNeckedFailed());
    assertXHalfWidths(s, 200);
  }

  @Test
  void neckNeverGoesBelowMinimumTrackWidth() {
    Setup s = prepare(null);
    s.board().rules.setMinAllowedTraceHalfWidth(100);
    s.router().getNeckedRouteNets().add(s.x());
    SortedSet<Item> ripped = new TreeSet<>();
    AutorouteAttemptResult result = s.connection().route(s.a(), s.x(), ripped, new HashMap<>(), 1);

    assertEquals(AutorouteAttemptState.ROUTED, result.state);
    assertEquals(1, s.router().getStartNeckedRouted());
    assertXHalfWidths(s, 100);
  }

  private static void assertXHalfWidths(Setup s, int halfWidth) {
    List<Trace> xTraces = s.board().getTraces().stream().filter(t -> t.containsNet(s.x())).toList();
    assertFalse(xTraces.isEmpty());
    for (Trace t : xTraces) {
      assertEquals(halfWidth, t.getHalfWidth());
    }
  }

  private static int incomplete(RoutingBoard board, int net) {
    DesignRulesChecker drc = new DesignRulesChecker(board, null);
    drc.calculateAllIncompletes();
    return drc.getIncompleteCount(net);
  }

  private static Point center(RoutingBoard board, String component) {
    return board.getPins().stream()
        .filter(p -> component.equals(board.components.get(p.getComponentId()).name))
        .findFirst()
        .orElseThrow()
        .getCenter();
  }
}
