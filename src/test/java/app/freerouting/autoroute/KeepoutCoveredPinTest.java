package app.freerouting.autoroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.maze.AutorouteControl;
import app.freerouting.autoroute.maze.AutorouteEngine;
import app.freerouting.autoroute.path.FoundConnectionLocator;
import app.freerouting.autoroute.pipeline.BatchAutorouter;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.ObstacleArea;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.CliSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A pin that a zero-clearance keepout partly covers (nan-15_top-plate's D1-1 in the KiCad
 * benchmark, 2026-10-20): a keyswitch cut-out grown by the copper-to-edge clearance crosses the
 * pad's right side, the LED's own hole band covers its left side, and the 0.2 mm trace must leave
 * through the 0.44 mm of the top edge between the two.
 */
class KeepoutCoveredPinTest {

  private static final String CUTOUT_4 =
      "1657620 -1244820 1657470 -1245120 1656790 -1246850 1656490 -1247990 1656230 -1249830 "
          + "1656200 -1251000 1656370 -1252850 1656610 -1254000 1657200 -1255760 1657700"
          + " -1256830 1658680 -1258410 1659410 -1259330 1660720 -1260640 1661640 -1261370"
          + " 1663220 -1262350 1664290 -1262850 1666040 -1263430 1667200 -1263680 1669040"
          + " -1263840 1670220 -1263810 1672060 -1263550 1673200 -1263260 1674920 -1262580"
          + " 1675230 -1262430 1807220 -1262430 1807220 -1112830 1657620 -1112830";

  private static final String KEEPOUTS =
      "(keepout \"exact_cutout_4\" (polygon signal 0 "
          + CUTOUT_4
          + "))\n"
          + "(keepout \"exact_cutout_6\" (circle signal 32000 1637220 -1187580))\n"
          + "(keepout \"keepout_1\" (circle signal 31000 1637220 -1259010))\n";

  private static String dsn(boolean keepouts) {
    return "(pcb covered (parser (string_quote \") (space_in_quoted_tokens on) (host_cad \"KiCad's"
        + " Pcbnew\")) (resolution um 10)\n"
        + "  (structure (layer F.Cu (type signal)) (layer B.Cu (type signal))\n"
        + "    (boundary (rect pcb 1500000 -1400000 1900000 -1000000))\n"
        + (keepouts ? KEEPOUTS : "")
        + "    (via V) (rule (width 2000) (clearance 2000)))\n"
        + "  (placement (component D (place D 1655221 -1259014 front 0)) (component W (place W"
        + " 1637221 -1187577 front 0)))\n"
        + "  (library (image D (pin PD 1 0 0)) (image W (pin PW 1 0 0))\n"
        + "    (padstack PD (shape (rect B.Cu -4500 -9004 4500 9004)))\n"
        + "    (padstack PW (shape (circle F.Cu 44000)) (shape (circle B.Cu 44000)))\n"
        + "    (padstack V (shape (circle F.Cu 600)) (shape (circle B.Cu 600)) (attach off)))\n"
        + "  (network (net GND (pins D-1 W-1)))\n"
        + "  (wiring (wire (path B.Cu 2000 1600000 -1275960 1645040 -1275960 1654990 -1266010)"
        + " (net GND) (type protect))))\n";
  }

  private static RouterSettings settings(RoutingBoard board, String... extraArgs) {
    List<String> args =
        new ArrayList<>(
            List.of(
                "--router.autorouter.fallback_strategies=none",
                "--router.optimizer.enabled=false",
                "--router.neck_width_um=200"));
    args.addAll(Arrays.asList(extraArgs));
    RouterSettings s =
        new SettingsMerger(new DefaultSettings(), new CliSettings(args.toArray(new String[0])))
            .merge();
    s.setLayerCount(board.getLayerCount());
    return s;
  }

  private static RoutingBoard createBoard(boolean keepouts) {
    ByteArrayInputStream stream =
        new ByteArrayInputStream(dsn(keepouts).getBytes(StandardCharsets.UTF_8));
    BoardReadResult result = DsnReader.readBoard(stream, null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();
    for (Item item : board.getItems()) {
      if (item instanceof ObstacleArea keepout && !(item instanceof ConductionArea)) {
        keepout.setClearanceClassIndex(0);
        keepout.clearDerivedData();
      }
    }
    board.searchTreeManager.reinsertTreeItems();
    return board;
  }

  private static Pin pinOf(RoutingBoard board, String component) {
    return board.getPins().stream()
        .filter(
            p -> {
              var c = board.components.get(p.getComponentId());
              return c != null && component.equals(c.name);
            })
        .findFirst()
        .orElseThrow();
  }

  private record RouteResult(AutorouteAttemptResult search, AutorouteAttemptResult strict) {}

  private static RouteResult routeConnection(
      RoutingBoard board, RouterSettings settings, boolean fromPad) {
    Pin pad = pinOf(board, "D");
    int net = pad.getNetNumber(0);
    AutorouteControl control = new AutorouteControl(board, net, settings);
    control.ripupAllowed = true;
    AutorouteEngine engine =
        board.initAutoroute(
            net, control.traceClearanceClassIndex, null, new TimeLimit(20_000), false);
    int before = board.communication.idGenerator.maxGeneratedId();
    Set<Item> padSide = Set.of(pad);
    Set<Item> farSide = Set.of(pinOf(board, "W"));
    AutorouteAttemptResult search =
        fromPad
            ? engine.autorouteConnection(padSide, farSide, control, new TreeSet<>(), null)
            : engine.autorouteConnection(farSide, padSide, control, new TreeSet<>(), null);
    AutorouteAttemptResult strict =
        search.state == AutorouteAttemptState.ROUTED
            ? BatchAutorouter.enforceStrictDrc(board, net, before)
            : null;
    return new RouteResult(search, strict);
  }

  @Test
  void theKeepoutsCrossThePadAndLeaveItsTopEdge() {
    RoutingBoard board = createBoard(true);
    ObstacleArea cutout =
        board.getItems().stream()
            .filter(
                a -> a instanceof ObstacleArea && "exact_cutout_4".equals(((ObstacleArea) a).name))
            .map(a -> (ObstacleArea) a)
            .findFirst()
            .orElseThrow();
    assertEquals(0, cutout.clearanceClassIndex());
    Area area = cutout.getArea();
    assertNotNull(area);
    assertTrue(
        area.contains(new IntPoint(1659000, -1255000)),
        "the pad's right side is covered by the cut-out");
    final ObstacleArea hole =
        board.getItems().stream()
            .filter(a -> a instanceof ObstacleArea && "keepout_1".equals(((ObstacleArea) a).name))
            .map(a -> (ObstacleArea) a)
            .findFirst()
            .orElseThrow();
    assertNotNull(hole.getArea());
    assertTrue(
        hole.getArea().contains(new IntPoint(1651500, -1259000)),
        "the pad's left side is covered by the hole band");
    assertFalse(area.contains(new IntPoint(1654500, -1250500)), "the pad's top is free");
    Pin pad = pinOf(board, "D");
    assertEquals(new IntPoint(1655221, -1259014), pad.getCenter());
  }

  @Test
  void withoutTheKeepoutsTheConnectionRoutes() {
    RoutingBoard board = createBoard(false);
    RouterSettings settings = settings(board);
    RouteResult res = routeConnection(board, settings, true);
    assertEquals(AutorouteAttemptState.ROUTED, res.search().state, res.search().toString());
    assertNull(res.strict());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true})
  void theConnectionLeavesThePadThroughItsUncoveredPart(boolean fromPad) {
    RoutingBoard board = createBoard(true);
    RouterSettings settings = settings(board);
    RouteResult res = routeConnection(board, settings, fromPad);
    assertEquals(AutorouteAttemptState.ROUTED, res.search().state, res.search().toString());
    assertNull(res.strict());
    assertFalse(board.getTraces().isEmpty());
    for (Trace t : board.getTraces()) {
      assertTrue(t.clearanceViolations().isEmpty());
    }
  }

  private static boolean copperOverlaps(RoutingBoard board, Pin pad) {
    TileShape copper = (TileShape) pad.getTileShape(0);
    return FoundConnectionLocator.unshovableCopperOverlapsPad(
        board, pad, copper, pad.shapeLayer(0), pad.getNetNumber(0));
  }

  @Test
  void theStubCheckRunsOnlyWhereUnshovableCopperOverlapsThePad() {
    RoutingBoard covered = createBoard(true);
    assertTrue(copperOverlaps(covered, pinOf(covered, "D")));
    RoutingBoard free = createBoard(false);
    assertFalse(copperOverlaps(free, pinOf(free, "D")));
  }

  @Test
  void aFinePitchNeighbourWithinClearanceDoesNotOverlapThePad() {
    String dsn =
        """
        (pcb finepitch (parser (string_quote ") (space_in_quoted_tokens on) (host_cad "KiCad's Pcbnew")) (resolution um 10)
          (structure (layer F.Cu (type signal)) (layer B.Cu (type signal))
            (boundary (rect pcb 0 0 40000 40000))
            (via V) (rule (width 150) (clearance 202)))
          (placement (component Q (place Q1 20000 20000 front 0)) (component R (place R1 10000 10000 front 0)))
          (library (image Q (pin PQ 1 0 0) (pin PQ 2 449 0)) (image R (pin PR 1 0 0) (pin PR 2 1000 0))
            (padstack PQ (shape (rect F.Cu -125 -437.5 125 437.5)))
            (padstack PR (shape (rect F.Cu -300 -300 300 300)))
            (padstack V (shape (circle F.Cu 600)) (shape (circle B.Cu 600)) (attach off)))
          (network (net A (pins Q1-1 R1-1)) (net B (pins Q1-2 R1-2))))
        """;
    ByteArrayInputStream stream = new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8));
    BoardReadResult result = DsnReader.readBoard(stream, null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();
    List<Pin> qfn =
        board.getPins().stream()
            .filter(
                p -> {
                  var c = board.components.get(p.getComponentId());
                  return c != null && "Q1".equals(c.name);
                })
            .sorted(Comparator.comparingDouble(p -> p.getCenter().toFloat().x))
            .toList();
    assertEquals(2, qfn.size());
    IntBox left = ((TileShape) qfn.get(0).getTileShape(0)).boundingBox();
    IntBox right = ((TileShape) qfn.get(1).getTileShape(0)).boundingBox();
    assertEquals(1990, right.ll.x - left.ur.x); // 0.199 mm, below the 0.202 mm clearance
    assertFalse(copperOverlaps(board, qfn.get(0)));
    assertFalse(copperOverlaps(board, qfn.get(1)));
  }
}
