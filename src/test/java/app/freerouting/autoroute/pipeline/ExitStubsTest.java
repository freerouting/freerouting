package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.drc.NetIncompletes;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.CliSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ExitStubsTest {

  private static final String QUAD_DSN =
      "(pcb quad (resolution um 10) (unit um)\n"
          + "  (structure (layer top (type signal)) (layer bottom (type signal))\n"
          + "    (boundary (rect pcb -8000 -8000 8000 8000)) (via V) (rule (width 250) (clearance 200)))\n"
          + "  (placement\n"
          + "    (component R (place U 0 0 front 0))\n"
          + "    (component T\n"
          + "      (place A1 -3000 6000 front 0) (place A2 -1000 6000 front 0) (place A3 1000 6000 front 0) (place A4 3000 6000 front 0)\n"
          + "      (place B1 -3000 -6000 front 0) (place B2 -1000 -6000 front 0) (place B3 1000 -6000 front 0) (place B4 3000 -6000 front 0)))\n"
          + "  (library\n"
          + "    (image R (pin P 1 -3W 1500) (pin P 2 -1W 1500) (pin P 3 1W 1500) (pin P 4 3W 1500)\n"
          + "             (pin P 5 -3W -1500) (pin P 6 -1W -1500) (pin P 7 1W -1500) (pin P 8 3W -1500))\n"
          + "    (image T (pin Q 1 0 0))\n"
          + "    (padstack P (shape (rect top -100 -350 100 350)) (attach off))\n"
          + "    (padstack Q (shape (rect top -300 -300 300 300)) (attach off))\n"
          + "    (padstack V (shape (circle top 600)) (shape (circle bottom 600)) (attach off)))\n"
          + "  (network (net N1 (pins U-1 A1-1)) (net N2 (pins U-2 A2-1)) (net N3 (pins U-3 A3-1)) (net N4 (pins U-4 A4-1))\n"
          + "    (net N5 (pins U-5 B1-1)) (net N6 (pins U-6 B2-1)) (net N7 (pins U-7 B3-1)) (net N8 (pins U-8 B4-1))))";

  private static String at(double um) {
    return String.format(Locale.US, "%.3f", um).replaceAll("0+$", "").replaceAll("\\.$", "");
  }

  private static RoutingBoard board(
      double pitchUm,
      String padstack,
      int rotation,
      boolean blocker,
      String blockerPlace,
      String blockerShape) {
    String dsn = QUAD_DSN;
    if (blocker || blockerPlace != null) {
      String place = blockerPlace != null ? blockerPlace : "-200 2711.2";
      dsn =
          dsn.replace(
                  "    (component T",
                  "    (component S (place S1 " + place + " front 0))\n    (component T")
              .replace("(image T (pin Q 1 0 0))", "(image T (pin Q 1 0 0)) (image S (pin K 1 0 0))")
              .replace(
                  "(padstack Q ",
                  "(padstack K (shape (rect top " + blockerShape + ")) (attach off)) (padstack Q ");
    }
    dsn =
        dsn.replace("(pin P ", "(pin " + padstack + " ")
            .replace("(place U 0 0 front 0)", "(place U 0 0 front " + rotation + ")")
            .replace("(padstack P ", "(padstack " + padstack + " ")
            .replace("-3W", at(-1.5 * pitchUm))
            .replace("-1W", at(-0.5 * pitchUm))
            .replace(" 1W", " " + at(0.5 * pitchUm))
            .replace(" 3W", " " + at(1.5 * pitchUm));

    ByteArrayInputStream stream = new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8));
    BoardReadResult result = DsnReader.readBoard(stream, null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard b = (RoutingBoard) ((BoardReadResult.Success) result).board();
    b.rules.setMinAllowedTraceHalfWidth(1000);
    return b;
  }

  private static RoutingBoard board(double pitchUm) {
    return board(pitchUm, "P", 0, false, null, "-75 -75 75 75");
  }

  private static RoutingBoard maskBoard(
      double pitchUm, boolean blocker, String blockerPlace, String blockerShape) {
    RoutingBoard board = board(pitchUm, "P", 0, blocker, blockerPlace, blockerShape);
    ClearanceMatrix matrix = board.rules.clearanceMatrix;
    int traces = board.rules.getDefaultNetClass().getTraceClearanceClass();
    matrix.appendClass("pads");
    int pads = matrix.getNo("pads");
    for (int layer = 0; layer < matrix.getLayerCount(); layer++) {
      matrix.setValue(traces, traces, layer, 1778);
      matrix.setValue(pads, traces, layer, 2000);
      matrix.setValue(traces, pads, layer, 2000);
      matrix.setValue(pads, pads, layer, 2000);
    }
    for (Pin pin : board.getPins()) {
      pin.setClearanceClassIndex(pads);
      pin.clearDerivedData();
    }
    board.searchTreeManager.reinsertTreeItems();
    return board;
  }

  private static RoutingBoard maskBoard(double pitchUm) {
    return maskBoard(pitchUm, false, null, "-75 -75 75 75");
  }

  private static RouterSettings settings(boolean on) {
    return new SettingsMerger(
            new DefaultSettings(),
            new CliSettings(
                new String[] {"--router.autorouter.exit_stubs=" + (on ? "true" : "false")}))
        .merge();
  }

  @Test
  void theSettingIsOnByDefault() {
    assertTrue(
        new SettingsMerger(new DefaultSettings(), new CliSettings(new String[0]))
            .merge()
            .autorouter
            .exitStubs);
  }

  @Test
  void pinsWhoseWayOutMeetsTheClearancesExactlyGetAStubOutward() {
    RoutingBoard board = maskBoard(400);
    List<ExitStubs.Stub> stubs = ExitStubs.plan(board);
    assertEquals(8, stubs.size());
    for (ExitStubs.Stub stub : stubs) {
      IntPoint centre = ExitStubs.centre(stub.pin());
      assertEquals(centre.x, stub.end().x);
      assertEquals(Integer.signum(centre.y), Integer.signum(stub.end().y - centre.y));
      assertEquals(3500 + 2000 + 1000 + 16, Math.abs(stub.end().y - centre.y));
      assertEquals(1000, stub.halfWidth());
      assertFalse(stub.inward());
      assertEquals(0.0, stub.slack(), 1e-6);
    }
  }

  @Test
  void pinsWithRoomGetNone() {
    assertTrue(ExitStubs.plan(board(500)).isEmpty());
    assertTrue(ExitStubs.plan(board(380)).isEmpty());
  }

  @Test
  void copperMayComeShortOfTheClearanceByTheCheckersTolerance() {
    RoutingBoard board = board(399.8);
    assertTrue(ExitStubs.plan(board).isEmpty());
    RouterSettings settings =
        new SettingsMerger(
                new DefaultSettings(),
                new CliSettings(new String[] {"--router.drc_epsilon_um=0.5"}))
            .merge();
    assertEquals(4, ExitStubs.copperAllowance(board, settings));
    assertEquals(8, ExitStubs.plan(board, settings).size());
  }

  @Test
  void aRoundedPadsCopperIsMeasuredAsKiCadDrawsIt() {
    RoutingBoard rounded =
        board(
            399.8,
            "RoundRect_Pad_199.600000x699.600000_50.000000_um",
            0,
            false,
            null,
            "-75 -75 75 75");
    Pin pin =
        rounded.getPins().stream()
            .filter(p -> "U".equals(p.componentName()))
            .findFirst()
            .orElseThrow();
    assertEquals(2, ExitStubs.modelExcess(rounded, pin, pin.getTileShape(0)));
    assertEquals(8, ExitStubs.plan(rounded).size());
    assertTrue(
        ExitStubs.plan(
                board(399.8, "Rect_Pad_199.600000x699.600000_um", 0, false, null, "-75 -75 75 75"))
            .isEmpty());
  }

  @Test
  void theStubsArePlacedFixedAndReleasedWithoutDanglingPieces() {
    RoutingBoard board = maskBoard(400);
    assertNull(ExitStubs.apply(settings(false), board, new ArrayList<>()));
    List<Integer> ids = new ArrayList<>();
    String line = ExitStubs.apply(settings(true), board, ids);
    assertNotNull(line);
    assertEquals(8, ids.size());
    for (int id : ids) {
      assertEquals(ExitStubs.PLACED_STATE, board.getItem(id).getFixedState());
    }
    String released = ExitStubs.release(board, ids);
    assertNotNull(released);
    assertTrue(released.contains("8 unused pieces removed"));
    assertEquals(0, board.getItems().stream().filter(i -> i instanceof Trace).count());
  }

  @Test
  void aRouteEndedBesideABentStubIsMovedOntoIt() {
    Point[] stub =
        new Point[] {
          new IntPoint(804000, -816500),
          new IntPoint(814848, -816500),
          new IntPoint(817527, -819179)
        };
    assertEquals(
        new IntPoint(816188, -817840),
        ExitStubs.nearestOnLine(stub, new IntPoint(816188, -817839)));
    assertEquals(
        new IntPoint(810000, -816500),
        ExitStubs.nearestOnLine(stub, new IntPoint(810000, -816501)));
    assertNull(ExitStubs.nearestOnLine(stub, new IntPoint(810000, -816505)));
    assertNull(ExitStubs.nearestOnLine(stub, new IntPoint(810000, -816500)));
  }

  @Test
  void aRouteEndedBesideABentStubStaysConnectedThroughTheRelease() {
    RoutingBoard board = board(400);
    List<Integer> ids = new ArrayList<>();
    assertNotNull(ExitStubs.apply(settings(true), board, ids));
    Pin u1 =
        board.getPins().stream()
            .filter(p -> "U".equals(p.componentName()) && "1".equals(p.name()))
            .findFirst()
            .orElseThrow();
    int net = u1.getNetNumber(0);
    PolylineTrace stub =
        board.getConnectableItems(net).stream()
            .filter(i -> i instanceof PolylineTrace)
            .map(i -> (PolylineTrace) i)
            .findFirst()
            .orElseThrow();
    Point[] line = stub.polyline().corners();
    assertEquals(3, line.length);
    IntPoint bend = (IntPoint) line[1];
    IntPoint end = (IntPoint) line[2];
    int dx = Integer.signum(end.x - bend.x);
    int dy = Integer.signum(end.y - bend.y);

    Pin a1 =
        board.getPins().stream()
            .filter(p -> "A1".equals(p.componentName()))
            .findFirst()
            .orElseThrow();
    IntPoint from = (IntPoint) a1.getCenter();
    Line diagonal = new Line(bend, end);
    Polyline route =
        new Polyline(
            new Line[] {
              new Line(from, new IntPoint(from.x, from.y + 1)),
              new Line(from, new IntPoint(bend.x + 1000 * dx, bend.y + 1000 * dy + 1)),
              diagonal
            });
    assertFalse(route.lastCorner() instanceof IntPoint);
    board.insertTraceWithoutCleaning(
        route,
        stub.getLayer(),
        stub.getHalfWidth(),
        new int[] {net},
        stub.clearanceClassIndex(),
        FixedState.UNFIXED);
    assertEquals(0, new NetIncompletes(net, board.getConnectableItems(net), board).count());
    String released = ExitStubs.release(board, ids);
    assertNotNull(released);
    assertEquals(0, new NetIncompletes(net, board.getConnectableItems(net), board).count());
    assertTrue(
        board.getConnectableItems(net).stream()
            .filter(i -> i instanceof PolylineTrace)
            .map(i -> (PolylineTrace) i)
            .allMatch(t -> t.lastCorner() instanceof IntPoint));
  }

  @Test
  void rowsWhoseStraightStubsWouldWallEachOtherInFanOut() {
    RoutingBoard board = board(400);
    List<ExitStubs.Stub> stubs = ExitStubs.plan(board);
    assertEquals(8, stubs.size());
    ExitStubs.Stub outer =
        stubs.stream()
            .filter(s -> ExitStubs.centre(s.pin()).equals(new IntPoint(-6000, 15000)))
            .findFirst()
            .orElseThrow();
    ExitStubs.Stub inner =
        stubs.stream()
            .filter(s -> ExitStubs.centre(s.pin()).equals(new IntPoint(-2000, 15000)))
            .findFirst()
            .orElseThrow();
    assertEquals(new IntPoint(-6000, 18500 + 1259), outer.bend());
    assertEquals(new IntPoint(-6000 - 2149, 18500 + 1259 + 2149), outer.end());
    assertEquals(new IntPoint(-2000, 18500 + 1259 + 1725), inner.bend());
    assertEquals(
        new IntPoint(2000, 18500 + 1259 + 1725),
        stubs.stream()
            .filter(s -> ExitStubs.centre(s.pin()).equals(new IntPoint(2000, 15000)))
            .findFirst()
            .orElseThrow()
            .bend());

    List<Integer> ids = new ArrayList<>();
    String line = ExitStubs.apply(settings(true), board, ids);
    assertNotNull(line);
    assertFalse(line.contains("left out"));
    assertEquals(8, ids.size());
    for (ExitStubs.Stub s : stubs) {
      assertTrue(ExitStubs.endReachable(board, s));
    }
  }

  @Test
  void aStubEndIsProbedAsFarAsTheClearanceAroundIt() {
    RoutingBoard board = board(400);
    ClearanceMatrix matrix = board.rules.clearanceMatrix;
    int traces = board.rules.getDefaultNetClass().getTraceClearanceClass();
    matrix.appendClass("wide");
    int wide = matrix.getNo("wide");
    for (int layer = 0; layer < matrix.getLayerCount(); layer++) {
      matrix.setValue(wide, traces, layer, 50000);
      matrix.setValue(traces, wide, layer, 50000);
      matrix.setValue(wide, wide, layer, 50000);
    }
    List<ExitStubs.Stub> stubs = ExitStubs.plan(board);
    ExitStubs.Stub stub =
        stubs.stream()
            .filter(s -> ExitStubs.centre(s.pin()).equals(new IntPoint(-2000, 15000)))
            .findFirst()
            .orElseThrow();
    int net = stub.pin().getNetNumber(0);
    assertEquals(
        1000 + 2000 + 16, ExitStubs.endProbeLength(board, stub.end(), 0, 1000, net, traces));
    Pin a1 =
        board.getPins().stream()
            .filter(p -> "A1".equals(p.componentName()))
            .findFirst()
            .orElseThrow();
    a1.setClearanceClassIndex(wide);
    a1.clearDerivedData();
    board.searchTreeManager.reinsertTreeItems();
    assertEquals(
        1000 + 50000 + 16, ExitStubs.endProbeLength(board, stub.end(), 0, 1000, net, traces));
  }

  @Test
  void theEndPinsOfTwoRunsAPadApartDoNotBothBendIntoTheGap() {
    String dsn =
        QUAD_DSN
            .replace("-3W", at(-600))
            .replace("-1W", at(-200))
            .replace(" 1W", " " + at(600))
            .replace(" 3W", " " + at(1000));
    ByteArrayInputStream stream = new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8));
    BoardReadResult result = DsnReader.readBoard(stream, null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();
    board.rules.setMinAllowedTraceHalfWidth(1000);
    List<ExitStubs.Stub> stubs = ExitStubs.plan(board);
    assertEquals(8, stubs.size());
    for (ExitStubs.Stub s : stubs) {
      assertNotNull(s.bend());
    }
    List<Integer> ids = new ArrayList<>();
    String line = ExitStubs.apply(settings(true), board, ids);
    assertNotNull(line);
    assertFalse(line.contains("left out"));
    assertEquals(8, ids.size());

    List<PolylineTrace> traces =
        ids.stream().map(id -> (PolylineTrace) board.getItem(id)).collect(Collectors.toList());
    for (PolylineTrace t : traces) {
      assertEquals(3, t.polyline().corners().length);
    }
    for (int sign : new int[] {1, -1}) {
      Point[] mirrored =
          traces.stream()
              .filter(t -> t.firstCorner().equals(new IntPoint(6000, sign * 15000)))
              .findFirst()
              .orElseThrow()
              .polyline()
              .corners();
      Point[] neighbour =
          traces.stream()
              .filter(t -> t.firstCorner().equals(new IntPoint(10000, sign * 15000)))
              .findFirst()
              .orElseThrow()
              .polyline()
              .corners();
      assertTrue(((IntPoint) mirrored[2]).x > 6000);
      assertEquals(1725, sign * (((IntPoint) mirrored[1]).y - ((IntPoint) neighbour[1]).y));
    }
  }

  @Test
  void pinsTheRouterCanLeaveItselfGetNone() {
    RoutingBoard board = maskBoard(400);
    ClearanceMatrix matrix = board.rules.clearanceMatrix;
    int traces = board.rules.getDefaultNetClass().getTraceClearanceClass();
    int pads = matrix.getNo("pads");
    for (int layer = 0; layer < matrix.getLayerCount(); layer++) {
      matrix.setValue(pads, traces, layer, 1980);
      matrix.setValue(traces, pads, layer, 1980);
    }
    board.searchTreeManager.reinsertTreeItems();
    assertTrue(ExitStubs.plan(board).isEmpty());
  }

  @Test
  void pinsTheRulesCheckGaveTheTolerancesRoomGetStubsAnyway() {
    RoutingBoard board = board(400);
    ClearanceMatrix matrix = board.rules.clearanceMatrix;
    int traces = board.rules.getDefaultNetClass().getTraceClearanceClass();
    matrix.appendClass("pads");
    int pads = matrix.getNo("pads");
    for (int layer = 0; layer < matrix.getLayerCount(); layer++) {
      matrix.setValue(pads, traces, layer, 1980);
      matrix.setValue(traces, pads, layer, 1980);
      matrix.setValue(pads, pads, layer, 2000);
    }
    for (Pin pin : board.getPins()) {
      if ("U".equals(pin.componentName())) {
        pin.setClearanceClassIndex(pads);
        pin.clearDerivedData();
      }
    }
    board.searchTreeManager.reinsertTreeItems();
    RouterSettings settings =
        new SettingsMerger(
                new DefaultSettings(),
                new CliSettings(new String[] {"--router.drc_epsilon_um=0.5"}))
            .merge();
    assertTrue(ExitStubs.plan(board, settings).isEmpty());
    board.tolerancePins =
        board.getPins().stream()
            .filter(p -> "U".equals(p.componentName()))
            .map(Pin::getId)
            .collect(Collectors.toSet());
    List<ExitStubs.Stub> stubs = ExitStubs.plan(board, settings);
    assertEquals(8, stubs.size());
    assertEquals(8, stubs.stream().filter(s -> s.bend() != null).count());
  }

  @Test
  void aRowWhoseStraightStubEndIsWalledInFansOut() {
    RoutingBoard board = maskBoard(400, true, null, "-75 -75 75 75");
    List<ExitStubs.Stub> stubs = ExitStubs.plan(board);
    assertEquals(8, stubs.size());
    assertEquals(4, stubs.stream().filter(s -> s.bend() != null).count());
    for (ExitStubs.Stub s : stubs) {
      if (s.bend() != null) {
        assertTrue(ExitStubs.centre(s.pin()).y > 0);
      }
    }
    List<Integer> ids = new ArrayList<>();
    String line = ExitStubs.apply(settings(true), board, ids);
    assertNotNull(line);
    assertFalse(line.contains("left out"));
    assertEquals(8, ids.size());
    assertEquals(0, ExitStubs.plan(maskBoard(400)).stream().filter(s -> s.bend() != null).count());
  }

  @Test
  void aRowWhoseFanMeetsAPadAboveTurnsAlongTheRowInstead() {
    RoutingBoard board = maskBoard(400, false, "-400 2645", "-60 -75 60 75");
    List<ExitStubs.Stub> stubs = ExitStubs.plan(board);
    ExitStubs.Stub second =
        stubs.stream()
            .filter(s -> ExitStubs.centre(s.pin()).equals(new IntPoint(-2000, 15000)))
            .findFirst()
            .orElseThrow();
    IntPoint bend = second.bend();
    assertNotNull(bend);
    assertEquals(-2000, bend.x);
    assertEquals(bend.y, second.end().y);
    assertTrue(second.end().x < bend.x);
    ExitStubs.Stub outer =
        stubs.stream()
            .filter(s -> ExitStubs.centre(s.pin()).equals(new IntPoint(-6000, 15000)))
            .findFirst()
            .orElseThrow();
    assertEquals(18500 + 16, outer.bend().y);
    List<Integer> ids = new ArrayList<>();
    String line = ExitStubs.apply(settings(true), board, ids);
    assertNotNull(line);
    assertFalse(line.contains("left out"));
    assertEquals(8, ids.size());
  }

  @Test
  void padsTurned45DegreesGetStubsAlongTheirDiagonal() {
    RoutingBoard board = board(400, "P", 45, false, null, "-75 -75 75 75");
    assertTrue(ExitStubs.plan(board).isEmpty());
    RouterSettings settings =
        new SettingsMerger(
                new DefaultSettings(),
                new CliSettings(
                    new String[] {
                      "--router.drc_epsilon_um=0.5", "--router.autorouter.exit_stubs=true"
                    }))
            .merge();
    List<ExitStubs.Stub> stubs = ExitStubs.plan(board, settings);
    assertEquals(8, stubs.size());
    for (ExitStubs.Stub stub : stubs) {
      IntPoint centre = ExitStubs.centre(stub.pin());
      IntPoint first = stub.bend() != null ? stub.bend() : stub.end();
      assertNotEquals(0, first.x - centre.x);
      assertEquals(Math.abs(first.x - centre.x), Math.abs(first.y - centre.y));
      if (stub.bend() != null) {
        assertTrue((stub.end().x == stub.bend().x) != (stub.end().y == stub.bend().y));
      }
    }
    assertEquals(8, stubs.stream().filter(s -> s.bend() != null).count());
    List<Integer> ids = new ArrayList<>();
    String line = ExitStubs.apply(settings, board, ids);
    assertNotNull(line);
    assertFalse(line.contains("left out"));
    assertEquals(8, ids.size());
    for (ExitStubs.Stub s : stubs) {
      assertTrue(ExitStubs.endReachable(board, s));
    }
    assertNull(ExitStubs.diagonal(new IntBox(0, 0, 2000, 7000)));
    assertNull(ExitStubs.diagonal(new IntBox(0, 0, 2000, 2000)));
  }

  @Test
  void theDistanceToAShapeIsExact() {
    IntBox box = new IntBox(0, 0, 100, 50);
    assertEquals(
        30.0,
        ExitStubs.segmentDistance(new FloatPoint(-30, 10), new FloatPoint(-30, 40), box),
        1e-9);
    assertEquals(
        0.0,
        ExitStubs.segmentDistance(new FloatPoint(-30, 10), new FloatPoint(130, 10), box),
        1e-9);
    assertEquals(
        5.0,
        ExitStubs.segmentDistance(new FloatPoint(-30, 55), new FloatPoint(130, 55), box),
        1e-9);
    assertEquals(
        50.0,
        ExitStubs.segmentDistance(new FloatPoint(130, 90), new FloatPoint(200, 90), box),
        1e-9);
  }
}
