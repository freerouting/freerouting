package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.fanout.BgaEscapeCommitter;
import app.freerouting.autoroute.fanout.BgaEscapeCommitter.CommitResult;
import app.freerouting.autoroute.fanout.BgaEscapeDescriptor;
import app.freerouting.autoroute.fanout.BgaEscapeDescriptor.LatticeClass;
import app.freerouting.autoroute.fanout.BgaEscapeSolver;
import app.freerouting.autoroute.fanout.BgaFootprintDetector;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.Component;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.sources.TestingSettings;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Issue #1201: the BGA escape stage took SO-8 packages with an exposed pad for BGAs, fixed a
 * partial escape on the board and removed pins without an escape from the normal fanout.
 *
 * <p>Both garlands have a GND plane on In1.Cu and three signal layers.
 *
 * <p>{@code Bga-escape-false-positive-garland-4layer.dsn}: Q1 is an SO-8 with an exposed pad (the
 * {@code toni:SO8_WITH_EP} of the PCBench board TX5823), U2 an SO-8 PowerPAD whose pin 9 has 11
 * pads (8 thermal vias, the exposed pad twice on F.Cu and once on B.Cu), U3 a 5x5 BGA at 1.0 mm.
 * {@code Bga-partial-escape-garland-4layer.dsn}: the same BGA, closed by keepouts on three sides
 * and without via sites, so that only its north row can escape.
 */
class BgaEscapeFalsePositiveTest extends RoutingFixtureTest {

  private static final String GARLAND = "Bga-escape-false-positive-garland-4layer.dsn";
  private static final String PARTIAL_GARLAND = "Bga-partial-escape-garland-4layer.dsn";

  private static RouterSettings bgaSettings(RoutingBoard board) {
    RouterSettings settings = new RouterSettings();
    settings.setLayerCount(board.getLayerCount());
    settings.fanout.bgaEscapeEnabled = true;
    settings.fanout.bgaEscapeMinPins = 9;
    return settings;
  }

  private static TestingSettings fanoutOnly() {
    TestingSettings settings = new TestingSettings();
    settings.setJobTimeoutString("00:01:00");
    settings.setMaxPasses(100);
    settings.setMaxItems(0);
    settings.setFanoutEnabled(true);
    settings.setRouterEnabled(false);
    settings.setOptimizerEnabled(false);
    return settings;
  }

  private static List<String> names(List<BgaEscapeDescriptor> bgas) {
    List<String> result = new ArrayList<>();
    for (BgaEscapeDescriptor bga : bgas) {
      result.add(bga.component().name);
    }
    return result;
  }

  /** User-fixed traces and vias (the BGA escapes) farther than 4 mm from the centre of U3. */
  private static List<String> fixedItemsAwayFromU3(RoutingBoard board) {
    Component u3 = board.components.get("U3");
    FloatPoint centre = u3.getLocation().toFloat();
    double radius = 4000 * board.communication.getResolution(Unit.UM);
    List<String> result = new ArrayList<>();
    for (Item item : board.getItems()) {
      if (!item.isUserFixed()) {
        continue;
      }
      FloatPoint location;
      if (item instanceof Trace trace) {
        location = trace.firstCorner().toFloat();
      } else if (item instanceof Via via) {
        location = via.getCenter().toFloat();
      } else {
        continue;
      }
      if (location.distance(centre) > radius) {
        result.add(item.getClass().getSimpleName() + " at " + location);
      }
    }
    return result;
  }

  @Test
  void soPackagesWithAnExposedPadAreNotBgas() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard(GARLAND);

    List<BgaEscapeDescriptor> bgas = BgaFootprintDetector.detect(board, bgaSettings(board));

    assertEquals(List.of("U3"), names(bgas), "only the 5x5 BGA is a BGA");
  }

  @Test
  void aRealBgaIsStillDetectedAndEscaped() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard(GARLAND);
    List<BgaEscapeDescriptor> bgas = BgaFootprintDetector.detect(board, bgaSettings(board));
    BgaEscapeDescriptor bga =
        bgas.stream().filter(b -> "U3".equals(b.component().name)).findFirst().orElseThrow();
    assertEquals(25, bga.pins().size());
    assertEquals(LatticeClass.ORTHOGONAL_GRID, bga.latticeClass());

    CommitResult result =
        BgaEscapeCommitter.commit(board, bga, BgaEscapeSolver.solve(board, bga, null));

    assertTrue(
        2 * result.committedPins().size() >= bga.pins().size(),
        "at least half of the balls escape, committed " + result.committedPins().size());
    for (Pin pin : result.committedPins()) {
      assertFalse(pin.getNormalContacts().isEmpty(), "pin " + pin.name() + " has an escape");
    }
    assertEquals(
        0, new DesignRulesChecker(board, null).getAllClearanceViolations().size(), "clearance");
  }

  @Test
  void aPartialEscapeIsRemoved() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard(PARTIAL_GARLAND);
    List<BgaEscapeDescriptor> bgas = BgaFootprintDetector.detect(board, bgaSettings(board));
    assertEquals(List.of("U3"), names(bgas));
    BgaEscapeDescriptor bga = bgas.get(0);

    CommitResult result =
        BgaEscapeCommitter.commit(board, bga, BgaEscapeSolver.solve(board, bga, null));

    assertEquals(0, result.pinsCommitted(), "a partial escape is not kept");
    assertTrue(result.committedPins().isEmpty(), "no pin leaves the normal fanout");
    assertEquals(0, board.getTraces().size(), "no escape trace left on the board");
    assertEquals(0, board.getViaCount(), "no escape via left on the board");
  }

  @Test
  void theFanoutLeavesNoFixedPartialEscape() {
    RoutingJob job = getRoutingJob(PARTIAL_GARLAND, fanoutOnly());
    runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state);
    List<String> fixed = new ArrayList<>();
    for (Item item : job.board.getItems()) {
      if (item.isUserFixed() && (item instanceof Trace || item instanceof Via)) {
        fixed.add(item.getClass().getSimpleName() + " id " + item.getId());
      }
    }
    assertTrue(fixed.isEmpty(), "user-fixed escapes left by the BGA stage: " + fixed);
  }

  @Test
  void onlyTheRealBgaGetsFixedEscapes() {
    RoutingJob job = getRoutingJob(GARLAND, fanoutOnly());
    runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state);
    List<String> away = fixedItemsAwayFromU3(job.board);
    assertTrue(away.isEmpty(), "user-fixed escapes outside the BGA: " + away);
  }
}
