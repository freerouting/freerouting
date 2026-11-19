package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.pipeline.BatchAutorouter;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.optimize.ViaNudger;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.datastructures.Stoppable;
import app.freerouting.geometry.planar.Point;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.management.HeadlessBoardManager;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.CliSettings;
import app.freerouting.settings.sources.DefaultSettings;
import app.freerouting.settings.sources.TestingSettings;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Regression test for Issue #1161: the via nudge pass moves vias only where the board as a whole
 * gets better, it gives the same board on every run, and it is off unless {@code
 * optimizer.via_nudge_enabled} is set.
 */
class Issue1161ViaNudgeTest extends RoutingFixtureTest {

  private static final String BOARD = "Issue508-DAC2020_bm01.dsn";

  private static RouterSettings mergeCli(String... args) {
    return new SettingsMerger(new DefaultSettings(), new CliSettings(args)).merge();
  }

  private RoutingBoard routedBoard() {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(2);
    settings.setJobTimeoutString("00:01:00");
    RoutingJob job = getRoutingJob(BOARD, settings);
    HeadlessBoardManager boardManager = new HeadlessBoardManager(job);
    try {
      boardManager.loadFromSpecctraDsn(job.input.getData(), null, new ItemIdGenerator());
    } catch (Exception e) {
      throw new RuntimeException("Failed to load DSN board", e);
    }
    job.board = boardManager.getRoutingBoard();
    job.thread = new NoOpStoppableThread();
    job.routerSettings.setMaxThreads(1);
    new BatchAutorouter(job).runBatchLoop();
    job.board.finishAutoroute();
    return job.board;
  }

  @Test
  void viaNudgeIsOffByDefault() {
    assertFalse(mergeCli().optimizer.viaNudgeEnabled);
  }

  @Test
  void cliFlagEnablesViaNudge() {
    assertTrue(mergeCli("--router.optimizer.via_nudge_enabled=true").optimizer.viaNudgeEnabled);
  }

  @Test
  void nudgedBoardIsNeverWorseAndTheSameOnEveryRun() {
    RoutingBoard routed = routedBoard();
    BoardStatistics before = routed.getStatistics();

    RoutingBoard first = routed.deepCopy();
    ViaNudger.Result result = ViaNudger.run(first, 500, null);
    BoardStatistics after = first.getStatistics();

    assertTrue(result.acceptedMoves() > 0, "the pass should find a via to move on this board");
    assertEquals(before.items.viaCount, after.items.viaCount);
    assertEquals(result.viasBefore(), result.viasAfter());
    assertTrue(after.connections.incompleteCount <= before.connections.incompleteCount);
    assertTrue(result.incompleteAfter() <= result.incompleteBefore());
    assertTrue(after.clearanceViolations.totalCount <= before.clearanceViolations.totalCount);
    assertTrue(result.violationsAfter() <= result.violationsBefore());
    assertTrue(result.bendsAfter() <= result.bendsBefore());
    assertTrue(result.lengthAfter() <= result.lengthBefore() * (1 + 1e-6));
    assertTrue(
        result.bendsAfter() < result.bendsBefore() || result.lengthAfter() < result.lengthBefore());

    RoutingBoard second = routed.deepCopy();
    ViaNudger.Result repeated = ViaNudger.run(second, 500, null);

    assertEquals(result, repeated);
    assertEquals(first.getHash(), second.getHash());
  }

  /**
   * {@code Issue1161-plane-stitching-via.dsn}: a pad reaches two GND planes through a trace with a
   * corner and a via. Moving the via towards the pad would shorten the trace, but the board only
   * knows the outline of a plane, not its copper once it is filled, so such a via must stay.
   */
  @Test
  void viaThatConnectsAPlaneStaysInPlace() throws IOException {
    assertTheViaStaysInPlace("Issue1161-plane-stitching-via.dsn");
  }

  /**
   * {@code Issue1161-via-in-foreign-zone.dsn}: the same trace and via on a signal net, inside the
   * outline of two GND planes that the via does not touch. Moving it would change how the planes
   * fill around it, so it must stay as well.
   */
  @Test
  void viaInsideAZoneOfAnotherNetStaysInPlace() throws IOException {
    assertTheViaStaysInPlace("Issue1161-via-in-foreign-zone.dsn");
  }

  /**
   * {@code Issue1161-shove-into-zone.dsn}: the via starts outside the GND zone and stays outside it
   * when shifted left, which would shorten both traces; however, shifting it left pulls the Top
   * trace into the zone's clearance envelope, so the move must be tried and rejected.
   */
  @Test
  void shoveThatPushesTraceIntoZoneIsRejected() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue1161-shove-into-zone.dsn");
    for (Item item : board.getItems()) {
      if (item instanceof Via || item instanceof PolylineTrace) {
        item.setFixedState(FixedState.UNFIXED);
      }
    }
    Point before = board.getVias().iterator().next().getCenter();

    ViaNudger.Result result = ViaNudger.run(board, 500, null);

    assertTrue(result.triedMoves() > 0, "candidate moves for a via outside a zone must be tried");
    assertEquals(0, result.acceptedMoves());
    assertEquals(1, board.getVias().size());
    assertEquals(before, board.getVias().iterator().next().getCenter());
  }

  /**
   * When a stop is requested mid-run after some moves have already been accepted, the pass rolls
   * back the whole board so no partial result is left behind.
   */
  @Test
  void stopRequestRevertsAcceptedMoves() {
    RoutingBoard board = routedBoard();
    Map<Integer, Point> viaCentersBefore = new HashMap<>();
    for (Via via : board.getVias()) {
      viaCentersBefore.put(via.getId(), via.getCenter());
    }
    final int[] checks = {0};
    Stoppable stopAfterFewVias =
        new Stoppable() {
          @Override
          public void requestStop() {}

          @Override
          public boolean isStopRequested() {
            return ++checks[0] > 20;
          }
        };

    ViaNudger.Result result = ViaNudger.run(board, 500, stopAfterFewVias);

    assertTrue(result.triedMoves() > 0);
    assertEquals(0, result.acceptedMoves());
    assertEquals(result.lengthBefore(), result.lengthAfter(), 1e-6);
    assertEquals(result.bendsBefore(), result.bendsAfter());
    assertEquals(result.viasBefore(), result.viasAfter());
    Map<Integer, Point> viaCentersAfter = new HashMap<>();
    for (Via via : board.getVias()) {
      viaCentersAfter.put(via.getId(), via.getCenter());
    }
    assertEquals(viaCentersBefore, viaCentersAfter);
  }

  private static void assertTheViaStaysInPlace(String fixture) throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard(fixture);
    // The DSN wiring loads fixed; the router's own connections are not.
    for (Item item : board.getItems()) {
      if (item instanceof Via || item instanceof PolylineTrace) {
        item.setFixedState(FixedState.UNFIXED);
      }
    }
    Point before = board.getVias().iterator().next().getCenter();

    ViaNudger.Result result = ViaNudger.run(board, 500, null);

    assertEquals(0, result.acceptedMoves());
    assertEquals(1, board.getVias().size());
    assertEquals(before, board.getVias().iterator().next().getCenter());
  }

  private static final class NoOpStoppableThread extends StoppableThread {
    @Override
    protected void threadAction() {
      // No-op: used only as a stop-request holder in tests.
    }
  }
}
