package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.TestFixtures;
import app.freerouting.autoroute.AutorouteAttemptState;
import app.freerouting.autoroute.maze.AutorouteControl;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.board.model.structure.BoardOutline;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.core.RoutingJob;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.PolygonShape;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.io.specctra.RulesReader;
import app.freerouting.management.HeadlessBoardManager;
import app.freerouting.settings.DesignRulesCheckerSettings;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.TreeSet;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The original DSN is reduced in memory: saved reduced DSNs recreate removed component pins. */
class Issue1014SlantedOutlineTest extends RoutingFixtureTest {

  private static final String FIXTURE = "Issue1014-Corney-Island-wireless.dsn";

  @ParameterizedTest
  @EnumSource(
      value = AngleRestriction.class,
      names = {"FORTYFIVE_DEGREE", "NINETY_DEGREE"})
  void narrowSlantedCorridorRoutesWithAdditionalBends(AngleRestriction angle) throws Exception {
    var board = DsnTestFixtures.loadBoard("Issue1014-slanted-corridor.dsn");
    board.rules.setTraceAngleRestriction(angle);
    board.getOutline().generateKeepoutOutside(true);
    assertConstrainedRoute(board, angle);
  }

  @ParameterizedTest
  @CsvSource({
    "FORTYFIVE_DEGREE,false",
    "NINETY_DEGREE,false",
    "FORTYFIVE_DEGREE,true",
    "NINETY_DEGREE,true"
  })
  void routesAroundReflexCornersAndSlantedCutouts(AngleRestriction angle, boolean cutout)
      throws Exception {
    var board = DsnTestFixtures.loadBoard("Issue1014-slanted-corridor.dsn");
    var outline = board.getOutline();
    outline.setFixedState(FixedState.UNFIXED);
    board.removeItem(outline);
    PolygonShape notch =
        new PolygonShape(
            new Point[] {
              new IntPoint(0, 0),
              new IntPoint(10000, 0),
              new IntPoint(10000, 5000),
              new IntPoint(6000, 5000),
              new IntPoint(5500, 2000),
              new IntPoint(4000, 5000),
              new IntPoint(0, 5000)
            });
    PolygonShape hole =
        new PolygonShape(
            new Point[] {
              new IntPoint(4000, 1000),
              new IntPoint(6000, 1500),
              new IntPoint(6000, 3500),
              new IntPoint(4000, 3000)
            });
    board.insertOutline(
        cutout
            ? new PolylineShape[] {new IntBox(0, 0, 10000, 5000), hole}
            : new PolylineShape[] {notch},
        1);
    if (!cutout) {
      board.getOutline().generateKeepoutOutside(true);
    }
    board.rules.setTraceAngleRestriction(angle);
    assertConstrainedRoute(board, angle);
  }

  private static void assertConstrainedRoute(RoutingBoard board, AngleRestriction angle) {
    var settings = new DefaultSettings().getSettings();
    settings.setNeckWidthUm(100);
    settings.setAutomaticNeckdown(false);
    settings.applyBoardSpecificOptimizations(board);
    var from =
        board.getPins().stream()
            .filter(p -> p.componentName().equals("A"))
            .findFirst()
            .orElseThrow();
    var control =
        new AutorouteControl(board, 1, settings, settings.getViaCosts(), settings.getTraceCosts());
    control.viasAllowed = false;
    control.ripupAllowed = false;
    var engine =
        board.initAutoroute(1, control.traceClearanceClassIndex, null, new TimeLimit(30000), false);
    board.startMarkingChangedArea();
    var destinations = from.getUnconnectedSet(1);
    var result =
        engine.autorouteConnection(
            from.getConnectedSet(1), destinations, control, new TreeSet<>(), new LinkedHashMap<>());
    assertEquals(AutorouteAttemptState.ROUTED, result.state, result.details);
    assertTrue(from.getConnectedSet(1).containsAll(destinations));
    assertTrue(
        new DesignRulesChecker(board, new DesignRulesCheckerSettings())
            .getAllClearanceViolations()
            .isEmpty());
    for (var trace : board.getTraces()) {
      var polyline = (PolylineTrace) trace;
      assertTrue(
          angle == AngleRestriction.NINETY_DEGREE
              ? polyline.polyline().isOrthogonal()
              : polyline.polyline().isMultipleOf45Degree());
      assertTrue(trace.getHalfWidth() >= 50);
    }
    board.finishAutoroute();
  }

  @ParameterizedTest
  @EnumSource(
      value = AngleRestriction.class,
      names = {"FORTYFIVE_DEGREE", "NINETY_DEGREE"})
  void genuineBlockingWallStillPreventsRouting(AngleRestriction angle) throws Exception {
    var board = DsnTestFixtures.loadBoard("Issue1014-slanted-edge.dsn");
    board.rules.setTraceAngleRestriction(angle);
    var settings = new DefaultSettings().getSettings();
    settings.setNeckWidthUm(100);
    settings.applyBoardSpecificOptimizations(board);
    var from =
        board.getPins().stream()
            .filter(p -> p.componentName().equals("A"))
            .findFirst()
            .orElseThrow();
    var to =
        board.getPins().stream()
            .filter(p -> p.componentName().equals("B"))
            .findFirst()
            .orElseThrow();
    int x = (int) ((from.getCenter().toFloat().x + to.getCenter().toFloat().x) / 2);
    var bounds = board.getBoundingBox();
    board.insertObstacle(
        new IntBox(x - 1000, bounds.ll.y - 10000, x + 1000, bounds.ur.y + 10000),
        0,
        1,
        FixedState.USER_FIXED);
    var control =
        new AutorouteControl(board, 1, settings, settings.getViaCosts(), settings.getTraceCosts());
    control.viasAllowed = false;
    control.ripupAllowed = false;
    var engine =
        board.initAutoroute(1, control.traceClearanceClassIndex, null, new TimeLimit(30000), false);
    board.startMarkingChangedArea();
    var result =
        engine.autorouteConnection(
            from.getConnectedSet(1),
            from.getUnconnectedSet(1),
            control,
            new TreeSet<>(),
            new LinkedHashMap<>());
    assertEquals(AutorouteAttemptState.FAILED, result.state, result.details);
    assertFalse(from.getConnectedSet(1).contains(to));
    assertTrue(board.getTraces().isEmpty());
    board.finishAutoroute();
  }

  @ParameterizedTest
  @EnumSource(
      value = AngleRestriction.class,
      names = {"FORTYFIVE_DEGREE", "NINETY_DEGREE"})
  void generalRoomsStillProduceTheConfiguredTraceAngles(AngleRestriction angle) throws Exception {
    var board = DsnTestFixtures.loadBoard("Issue1014-slanted-edge.dsn");
    board.rules.setTraceAngleRestriction(angle);
    var settings = new DefaultSettings().getSettings();
    settings.setNeckWidthUm(100);
    settings.applyBoardSpecificOptimizations(board);
    var from =
        board.getPins().stream()
            .filter(p -> p.componentName().equals("A"))
            .findFirst()
            .orElseThrow();
    var control =
        new AutorouteControl(board, 1, settings, settings.getViaCosts(), settings.getTraceCosts());
    control.viasAllowed = false;
    var engine =
        board.initAutoroute(1, control.traceClearanceClassIndex, null, new TimeLimit(30000), false);
    board.startMarkingChangedArea();
    var destinations = from.getUnconnectedSet(1);
    var result =
        engine.autorouteConnection(
            from.getConnectedSet(1), destinations, control, new TreeSet<>(), new LinkedHashMap<>());
    assertEquals(AutorouteAttemptState.ROUTED, result.state, result.details);
    assertTrue(from.getConnectedSet(1).containsAll(destinations));
    assertTrue(
        new DesignRulesChecker(board, new DesignRulesCheckerSettings())
            .getAllClearanceViolations()
            .isEmpty());
    for (var trace : board.getTraces()) {
      var polyline = (PolylineTrace) trace;
      assertTrue(
          angle == AngleRestriction.NINETY_DEGREE
              ? polyline.polyline().isOrthogonal()
              : polyline.polyline().isMultipleOf45Degree());
    }
    board.finishAutoroute();
  }

  @ParameterizedTest
  @ValueSource(strings = {"S20", "S21"})
  void connectsLegalPadAnchorsWithOutlineAndFortyfiveDegreeTraces(String component)
      throws Exception {
    RouterSettings settings = new DefaultSettings().getSettings();
    settings.setAutomaticNeckdown(false);
    RoutingBoard board;
    try (var input = Files.newInputStream(TestFixtures.resolvePath(FIXTURE));
        var rules =
            Files.newInputStream(TestFixtures.resolvePath("Issue1014-Corney-Island.rules"))) {
      var read = (BoardReadResult.Success) DsnReader.readBoard(input, null, null, FIXTURE);
      board = (RoutingBoard) read.board();
      board.awaitPostLoad();
      assertTrue(RulesReader.read(rules, "corney_island", board, settings));
      var job = new RoutingJob();
      job.routerSettings = settings;
      new HeadlessBoardManager(job).applyParsedBoardResult(read, FIXTURE, "dsn");
      board.awaitPostLoad();
    }
    Pin from =
        board.getPins().stream()
            .filter(p -> p.componentName().equals(component) && p.name().equals("1"))
            .findFirst()
            .orElseThrow();
    int componentId = from.getComponentId();
    // Retain the switch, its keepouts and the original outline. Use normal tree bookkeeping.
    for (Item item : new ArrayList<>(board.getItems())) {
      if (item.getComponentId() != componentId && !(item instanceof BoardOutline)) {
        item.setFixedState(FixedState.UNFIXED);
        board.detachItemForMove(item);
      }
    }
    assertEquals(39, board.getItems().size());
    assertTrue(board.getOutline().isOnTheBoard());
    assertEquals(AngleRestriction.FORTYFIVE_DEGREE, board.rules.getTraceAngleRestriction());
    board.clearTransientAutorouteState();
    int net = from.getNetNumber(0);
    var control =
        new AutorouteControl(
            board, net, settings, settings.getViaCosts(), settings.getTraceCosts());
    control.ripupAllowed = true;
    control.ripupCosts = 2500;
    control.removeUnconnectedVias = true;
    var destinations = from.getUnconnectedSet(net);
    assertFalse(destinations.isEmpty());
    var drc = new DesignRulesChecker(board, new DesignRulesCheckerSettings());
    assertTrue(drc.getAllClearanceViolations().isEmpty());
    board.startMarkingChangedArea();
    var engine =
        board.initAutoroute(
            net, control.traceClearanceClassIndex, null, new TimeLimit(30000), false);
    var ripped = new TreeSet<Item>();
    long started = System.nanoTime();
    var result =
        engine.autorouteConnection(
            from.getConnectedSet(net), destinations, control, ripped, new LinkedHashMap<>());
    System.out.printf(
        "Issue1014 %s result=%s seconds=%.6f%n",
        component, result, (System.nanoTime() - started) / 1e9);
    assertEquals(AutorouteAttemptState.ROUTED, result.state, result.details);
    assertTrue(from.getConnectedSet(net).containsAll(destinations));
    drc.calculateAllIncompletes();
    assertEquals(0, drc.getIncompleteCount(net));
    assertTrue(drc.getAllClearanceViolations().isEmpty());
    assertTrue(ripped.isEmpty());
    assertTrue(
        board.getTraces().stream()
            .allMatch(t -> t instanceof PolylineTrace p && p.polyline().isMultipleOf45Degree()));
    board.finishAutoroute();
  }
}
