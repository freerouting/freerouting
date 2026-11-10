package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.AutorouteAttemptState;
import app.freerouting.autoroute.maze.AutorouteControl;
import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.settings.DesignRulesCheckerSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.util.LinkedHashMap;
import java.util.TreeSet;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * A single connection through a narrow corridor between two slanted board edges (fixture and case
 * from #1015). With 90-degree routing, the maze search used to stop after the start room: the room
 * behind its first 2-dimensional door was dropped while being completed, because
 * ShapeSearchTree90Degree skipped an overlapping free space room without keeping the room it was
 * restraining. The 45-degree tree keeps it.
 */
class NinetyDegreeSlantedCorridorTest {

  @ParameterizedTest
  @EnumSource(
      value = AngleRestriction.class,
      names = {"FORTYFIVE_DEGREE", "NINETY_DEGREE"})
  void routesThroughNarrowSlantedCorridor(AngleRestriction angle) throws Exception {
    var board = DsnTestFixtures.loadBoard("Issue1014-slanted-corridor.dsn");
    board.rules.setTraceAngleRestriction(angle);
    board.getOutline().generateKeepoutOutside(true);

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
      var polyline = ((PolylineTrace) trace).polyline();
      assertTrue(
          angle == AngleRestriction.NINETY_DEGREE
              ? polyline.isOrthogonal()
              : polyline.isMultipleOf45Degree());
    }
    board.finishAutoroute();
  }
}
