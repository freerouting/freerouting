package app.freerouting.autoroute.maze;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.autoroute.FailureReason;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.core.StoppableThread;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link MazeSearchEngine#classifyExhaustedFailure()}. */
class MazeSearchEngineFailureReasonTest {

  private static MazeSearchEngine createSearchEngine() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue143-rpi_splitter.dsn");
    RouterSettings settings = new DefaultSettings().getSettings();
    AutorouteControl ctrl = new AutorouteControl(board, 1, settings);
    AutorouteEngine engine =
        board.initAutoroute(1, ctrl.traceClearanceClassIndex, (StoppableThread) null, null, false);
    return new MazeSearchEngine(engine, ctrl);
  }

  @Test
  void classifiesViaPlacementBlocked() throws IOException {
    MazeSearchEngine engine = createSearchEngine();
    engine.viaExpansionAttempts = 5;
    engine.viaExpansionSuccesses = 0;

    FailureReason reason = engine.classifyExhaustedFailure();
    assertEquals(FailureReason.FailureType.VIA_PLACEMENT_BLOCKED, reason.type());
  }

  @Test
  void classifiesRipupDisallowed() throws IOException {
    MazeSearchEngine engine = createSearchEngine();
    engine.obstacleRoomsBlockedByRipupDisabled = 3;
    engine.rippableRoomsAccepted = 0;

    FailureReason reason = engine.classifyExhaustedFailure();
    assertEquals(FailureReason.FailureType.RIPUP_DISALLOWED, reason.type());
  }

  @Test
  void classifiesRipupBlocked() throws IOException {
    MazeSearchEngine engine = createSearchEngine();
    engine.obstacleRoomsBlockedByProtectedOrFixed = 4;
    engine.rippableRoomsAccepted = 0;

    FailureReason reason = engine.classifyExhaustedFailure();
    assertEquals(FailureReason.FailureType.RIPUP_BLOCKED, reason.type());
  }

  @Test
  void fallsBackToClearanceWalkedExhaustedWhenRippableRoomsAccepted() throws IOException {
    MazeSearchEngine engine = createSearchEngine();
    engine.obstacleRoomsBlockedByProtectedOrFixed = 4;
    engine.rippableRoomsAccepted = 1;

    FailureReason reason = engine.classifyExhaustedFailure();
    assertEquals(FailureReason.FailureType.CLEARANCE_WALKED_EXHAUSTED, reason.type());
  }
}
