package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.AutorouteAttemptState;
import app.freerouting.autoroute.maze.AutorouteControl;
import app.freerouting.board.model.items.Item;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.settings.DesignRulesCheckerSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.TreeSet;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Protects goal selection when an unnecessary rip-up competes with an already paid via.
 *
 * <p>The front-side source and back-side destination straddle a connected back-side trace. A via on
 * the destination side preserves that trace. Counting the prepaid via again delays this route until
 * the search accepts the destructive alternative through the neighboring trace.
 */
class Issue1017ViaHeuristicTest extends RoutingFixtureTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void routesWithoutDisconnectingNeighbor(boolean allowRipup) throws IOException {
    var board = DsnTestFixtures.loadBoard("Issue1017-via-competing-goal.dsn");
    board.awaitPostLoad();
    int routeNet = board.rules.nets.get("ROUTE", 1).netNumber;
    int neighborNet = board.rules.nets.get("VICTIM", 1).netNumber;
    var before = new DesignRulesChecker(board, new DesignRulesCheckerSettings());
    before.calculateAllIncompletes();
    assertEquals(1, before.getIncompleteCount(routeNet));
    assertEquals(0, before.getIncompleteCount(neighborNet));
    assertTrue(before.getAllClearanceViolations().isEmpty());

    var settings = new DefaultSettings().getSettings();
    settings.applyBoardSpecificOptimizations(board);
    settings.setAutomaticNeckdown(false);
    settings.autorouter.smdNetViaCostFactor = 1.0;
    var costs =
        new AutorouteControl.ExpansionCostFactor[] {
          new AutorouteControl.ExpansionCostFactor(1, 1),
          new AutorouteControl.ExpansionCostFactor(1, 1)
        };
    var control = new AutorouteControl(board, routeNet, settings, 50, costs);
    control.ripupAllowed = allowRipup;
    control.ripupCosts = 50;
    control.removeUnconnectedVias = true;
    // Isolate the choice between a layer change and destructive rip-up, without shoving.
    control.maxShoveTraceRecursionDepth = 0;
    control.maxShoveViaRecursionDepth = 0;
    control.maxSpringOverRecursionDepth = 0;
    var source =
        board.getPins().stream()
            .filter(pin -> pin.componentName().equals("SOURCE"))
            .findFirst()
            .orElseThrow();

    board.startMarkingChangedArea();
    var engine =
        board.initAutoroute(
            routeNet, control.traceClearanceClassIndex, null, new TimeLimit(10000), false);
    var ripped = new TreeSet<Item>();
    var result =
        engine.autorouteConnection(
            source.getConnectedSet(routeNet),
            source.getUnconnectedSet(routeNet),
            control,
            ripped,
            new LinkedHashMap<>());
    assertEquals(AutorouteAttemptState.ROUTED, result.state);

    var after = new DesignRulesChecker(board, new DesignRulesCheckerSettings());
    after.calculateAllIncompletes();
    assertEquals(0, after.getIncompleteCount(routeNet));
    assertEquals(
        0,
        after.getIncompleteCount(neighborNet),
        "A feasible no-rip route must preserve the already connected neighboring net");
    assertTrue(ripped.isEmpty(), "The route must not remove the neighboring copper");
    assertEquals(1, board.getVias().size());
    assertTrue(after.getAllClearanceViolations().isEmpty());
  }
}
