package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.optimize.StepSlider;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.CliSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * Regression test for Issue #1161: the step slide pass straightens a bundle routed at the clearance
 * without making the board worse, gives the same board on every run, leaves a zone alone, and is
 * off unless {@code optimizer.step_slide_enabled} is set.
 *
 * <p>{@code Issue1161-bundle-staircase-garland.dsn} is a bundle of 16 traces on one layer, as the
 * router of master left it: where two groups of traces join on a 45 degree run, each trace changes
 * lane with a short step and the next one copies it, 11 steps of 0.04 to 0.7 mm in all. {@code
 * Issue1161-bundle-staircase-in-zone-garland.dsn} is the same board with a copper zone of another
 * net over the steps.
 */
class Issue1161StepSlideTest {

  private static final String BUNDLE = "Issue1161-bundle-staircase-garland.dsn";
  private static final String BUNDLE_IN_ZONE = "Issue1161-bundle-staircase-in-zone-garland.dsn";

  private static RouterSettings mergeCli(String... args) {
    return new SettingsMerger(new DefaultSettings(), new CliSettings(args)).merge();
  }

  private static RoutingBoard load(String fixture) throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard(fixture);
    // The DSN wiring loads fixed; the router's own connections are not.
    for (Item item : board.getItems()) {
      if (item instanceof Via || item instanceof PolylineTrace) {
        item.setFixedState(FixedState.UNFIXED);
      }
    }
    return board;
  }

  @Test
  void stepSlideIsOnByDefault() {
    assertTrue(mergeCli().optimizer.stepSlideEnabled);
  }

  @Test
  void cliFlagDisablesStepSlide() {
    assertFalse(mergeCli("--router.optimizer.step_slide_enabled=false").optimizer.stepSlideEnabled);
  }

  @Test
  void staircaseIsStraightenedWithoutMakingTheBoardWorse() throws IOException {
    RoutingBoard board = load(BUNDLE);
    assertEquals(0, board.getStatistics().clearanceViolations.totalCount);
    assertEquals(11, steps(board));

    StepSlider.Result result = StepSlider.run(board, 500, null);

    assertEquals(0, steps(board));
    assertTrue(result.bendsAfter() < result.bendsBefore());
    assertTrue(result.lengthAfter() <= result.lengthBefore() * (1 + 1e-6));
    assertEquals(result.viasBefore(), result.viasAfter());
    assertEquals(0, result.incompleteAfter());
    assertEquals(0, board.routingLedger().incompleteCount());
    assertEquals(0, result.violationsAfter());
    assertEquals(0, board.getStatistics().clearanceViolations.totalCount);
  }

  @Test
  void stepSlideGivesTheSameBoardOnEveryRun() throws IOException {
    RoutingBoard first = load(BUNDLE);
    RoutingBoard second = load(BUNDLE);

    StepSlider.Result result = StepSlider.run(first, 500, null);
    StepSlider.Result repeated = StepSlider.run(second, 500, null);

    assertTrue(result.acceptedMoves() > 0);
    assertEquals(result, repeated);
    assertEquals(first.getHash(), second.getHash());
  }

  /**
   * The board only knows the outline of a zone, not its copper once it is filled around the traces:
   * moving a trace inside it can cut the fill, so no move may bring a trace into it.
   */
  @Test
  void stepsInsideAZoneStay() throws IOException {
    RoutingBoard board = load(BUNDLE_IN_ZONE);
    assertEquals(0, board.getStatistics().clearanceViolations.totalCount);

    StepSlider.Result result = StepSlider.run(board, 500, null);

    assertEquals(0, result.acceptedMoves());
    assertEquals(11, steps(board));
    assertEquals(result.bendsBefore(), result.bendsAfter());
    assertEquals(result.lengthBefore(), result.lengthAfter());
  }

  /**
   * The steps of the board shorter than 1.5 mm: segments between two segments of the same
   * direction.
   */
  private static int steps(RoutingBoard board) {
    double maxLength =
        1.5 * board.communication.getResolution(app.freerouting.board.model.structure.Unit.MM);
    int count = 0;
    for (Item item : board.getItems()) {
      if (!(item instanceof PolylineTrace trace)) {
        continue;
      }
      int n = trace.cornerCount();
      for (int k = 1; k + 2 < n; k++) {
        FloatPoint a = trace.polyline().corner(k - 1).toFloat();
        FloatPoint b = trace.polyline().corner(k).toFloat();
        FloatPoint c = trace.polyline().corner(k + 1).toFloat();
        FloatPoint d = trace.polyline().corner(k + 2).toFloat();
        double ax = b.x - a.x;
        double ay = b.y - a.y;
        double sx = c.x - b.x;
        double sy = c.y - b.y;
        double bx = d.x - c.x;
        double by = d.y - c.y;
        double scale = Math.hypot(ax, ay) * Math.hypot(bx, by);
        boolean sameDirection =
            Math.abs(ax * by - ay * bx) <= 1e-9 * scale && ax * bx + ay * by > 0;
        boolean sideways =
            Math.abs(ax * sy - ay * sx) > 1e-9 * Math.hypot(ax, ay) * Math.hypot(sx, sy);
        if (sameDirection && sideways && Math.hypot(sx, sy) < maxLength) {
          count++;
        }
      }
    }
    return count;
  }
}
