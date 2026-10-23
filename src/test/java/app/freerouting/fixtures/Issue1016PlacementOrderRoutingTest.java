package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.pipeline.AutoroutePassTestSupport;
import app.freerouting.autoroute.pipeline.BatchAutorouter;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.StoppableThread;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.Point;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReadOptions;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.io.specctra.SesReader;
import app.freerouting.io.specctra.SesWriter;
import app.freerouting.settings.DesignRulesCheckerSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Checks placement invariance through real representative selection, routing, and SES exchange. */
class Issue1016PlacementOrderRoutingTest extends RoutingFixtureTest {
  private static final String ORIGINAL = "Issue1016-placement-order.dsn";

  static Stream<Arguments> passesAndSeeds() {
    return Stream.of(
        Arguments.of(1, 42), Arguments.of(2, 42), Arguments.of(2, 0), Arguments.of(2, -1));
  }

  @ParameterizedTest
  @MethodSource("passesAndSeeds")
  void placementPermutationsPreserveSeededRoutesAndConnectedGroupSelection(int pass, int seed)
      throws IOException {
    Outcome expected = route(ORIGINAL, pass, seed);
    for (String fixture :
        List.of("Issue1016-placement-order-reversed.dsn", "Issue1016-placement-order-split.dsn")) {
      Outcome actual = route(fixture, pass, seed);
      assertEquals(expected.representatives(), actual.representatives());
      assertEquals(expected.copper(), actual.copper());
    }
  }

  private static Outcome route(String fixture, int pass, int seed) throws IOException {
    RoutingBoard board;
    try (var in = DsnTestFixtures.openResource(fixture)) {
      board =
          (RoutingBoard)
              ((BoardReadResult.Success)
                      DsnReader.readBoard(in, null, null, fixture, DsnReadOptions.REFERENCE_NAME))
                  .board();
    }
    board.awaitPostLoad();
    var before = drc(board);
    assertEquals(2, before.getIncompleteCount());
    assertTrue(before.getAllClearanceViolations().isEmpty());
    Pin a1 =
        board.getPins().stream()
            .filter(pin -> pin.componentName().equals("A1"))
            .findFirst()
            .orElseThrow();
    int net = a1.getNetNumber(0);
    assertEquals(
        2, a1.getConnectedSet(net).size(), "Overlapping A1/A2 form one preconnected group");

    var job = new RoutingJob();
    job.board = board;
    job.thread =
        new StoppableThread() {
          @Override
          protected void threadAction() {}
        };
    var settings = new DefaultSettings().getSettings();
    settings.setLayerCount(board.getLayerCount());
    settings.applyBoardSpecificOptimizations(board);
    settings.autorouter.itemOrderSeed = seed;
    settings.setAutomaticNeckdown(false);
    settings.fanout.enabled = false;
    settings.maxThreads = 1;
    job.routerSettings = settings;
    var router = new BatchAutorouter(job);
    List<String> representatives =
        AutoroutePassTestSupport.representatives(router).stream()
            .map(item -> item.componentName() + ":" + ((Pin) item).name())
            .toList();
    assertEquals(4, representatives.size(), "One endpoint per disconnected group");
    assertEquals(
        Set.of("A-group", "B1:1", "B2:1", "C1:1"),
        representatives.stream()
            .map(name -> name.equals("A1:1") || name.equals("A2:1") ? "A-group" : name)
            .collect(Collectors.toSet()));
    assertTrue(AutoroutePassTestSupport.runPass(router, pass));
    assertClean(board);
    Map<Object, Integer> copper = copper(board);
    assertFalse(copper.isEmpty(), "Routing must actually add copper");

    var out = new ByteArrayOutputStream();
    SesWriter.write(board, out, "placement-order");
    RoutingBoard reimported = DsnTestFixtures.loadBoard(fixture);
    reimported.awaitPostLoad();
    var imported = SesReader.read(new ByteArrayInputStream(out.toByteArray()), reimported);
    assertEquals(0, imported.errorsEncountered());
    assertTrue(imported.wiresImported() > 0);
    assertClean(reimported);
    assertEquals(copper, copper(reimported));
    assertEquals(poses(board), poses(reimported));
    return new Outcome(representatives, copper);
  }

  private static Map<String, Pose> poses(RoutingBoard board) {
    Map<String, Pose> result = new HashMap<>();
    for (int i = 1; i <= board.components.count(); i++) {
      var c = board.components.get(i);
      result.put(
          c.name,
          new Pose(
              c.getExactLocation().x,
              c.getExactLocation().y,
              c.getRotationInDegree(),
              c.placedOnFront()));
    }
    return result;
  }

  private static DesignRulesChecker drc(RoutingBoard board) {
    var drc = new DesignRulesChecker(board, new DesignRulesCheckerSettings());
    drc.calculateAllIncompletes();
    return drc;
  }

  private static void assertClean(RoutingBoard board) {
    var drc = drc(board);
    assertEquals(0, drc.getIncompleteCount());
    assertTrue(drc.getAllClearanceViolations().isEmpty());
  }

  private static Map<Object, Integer> copper(RoutingBoard board) {
    Map<Object, Integer> result = new HashMap<>();
    for (var trace : board.getTraces()) {
      var polyline = ((PolylineTrace) trace).polyline();
      List<Point> points = new ArrayList<>();
      for (int i = 0; i < polyline.cornerCount(); i++) points.add(polyline.corner(i));
      if (points.getFirst().compareXY(points.getLast()) > 0) Collections.reverse(points);
      var geometry =
          new TraceGeometry(
              nets(board, trace), trace.getLayer(), trace.getHalfWidth(), List.copyOf(points));
      result.merge(geometry, 1, Integer::sum);
    }
    for (var via : board.getVias()) {
      result.merge(
          new ViaGeometry(
              nets(board, via),
              via.getCenter(),
              via.getPadstack().name,
              via.firstLayer(),
              via.lastLayer()),
          1,
          Integer::sum);
    }
    return result;
  }

  private static List<String> nets(RoutingBoard board, Item item) {
    List<String> result = new ArrayList<>();
    for (int number : item.netNumbers) result.add(board.rules.nets.get(number).name);
    Collections.sort(result);
    return List.copyOf(result);
  }

  private record TraceGeometry(List<String> nets, int layer, int halfWidth, List<Point> points) {}

  private record ViaGeometry(
      List<String> nets, Point center, String padstack, int firstLayer, int lastLayer) {}

  private record Pose(double x, double y, double rotation, boolean front) {}

  private record Outcome(List<String> representatives, Map<Object, Integer> copper) {}
}
