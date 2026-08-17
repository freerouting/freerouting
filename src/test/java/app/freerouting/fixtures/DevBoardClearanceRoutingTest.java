package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.Unit;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

class DevBoardClearanceRoutingTest extends RoutingFixtureTest {

  @Test
  void issue558ClearanceViolationAtBoardEdge() {
    final double testCopperToEdgeClearanceUm = 650.0;
    var testingSettings = new TestingSettings();
    testingSettings.setCopperToEdgeClearanceUm(testCopperToEdgeClearanceUm);
    testingSettings.setMaxPasses(300);
    testingSettings.setJobTimeoutString("00:03:00");
    var job = getRoutingJob("Issue558-dev-board.dsn", testingSettings);

    job = runRoutingJob(job);

    int boardEdgeClassNo = job.board.rules.clearanceMatrix.getNo("board_edge");
    assertTrue(boardEdgeClassNo >= 0, "Expected board_edge clearance class to be created.");
    assertEquals(
        boardEdgeClassNo,
        job.board.getOutline().clearanceClassIndex(),
        "Board outline should be assigned to the board_edge clearance class.");

    int expectedBoardUnits =
        (int)
            Math.round(
                Unit.scale(
                    testCopperToEdgeClearanceUm * Math.max(1, job.board.communication.resolution),
                    Unit.UM,
                    job.board.communication.unit));
    for (int layer = 0; layer < job.board.rules.clearanceMatrix.getLayerCount(); layer++) {
      assertEquals(
          expectedBoardUnits,
          job.board.rules.clearanceMatrix.getValue(
              boardEdgeClassNo, boardEdgeClassNo, layer, false),
          "board_edge self-clearance should match copperToEdgeClearanceUm on every layer.");
    }

    int lastLayer = job.board.getLayerCount() - 1;
    assertTrue(
        job.board.getVias().stream()
            .allMatch(via -> via.firstLayer() == 0 && via.lastLayer() == lastLayer),
        "All inserted vias should stay on the minimal board span for this 2-layer fixture (0->1).");
    assertTrue(
        job.board.getVias().stream()
            .allMatch(via -> "Via[0-1]_600:300_um".equals(via.getPadstack().name)),
        "All inserted vias should use the board's smallest configured via type.");
  }
}
