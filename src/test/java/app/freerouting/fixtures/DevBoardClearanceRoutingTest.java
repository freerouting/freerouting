package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.Unit;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

public class DevBoardClearanceRoutingTest extends RoutingFixtureTest {

  @Test
  void test_Issue_558_Clearance_violation_at_board_edge() {
    final double testCopperToEdgeClearanceUm = 650.0;
    var testingSettings = new TestingSettings();
    testingSettings.setCopperToEdgeClearanceUm(testCopperToEdgeClearanceUm);
    testingSettings.setMaxPasses(300);
    testingSettings.setJobTimeoutString("00:03:00");
    var job = GetRoutingJob("Issue558-dev-board.dsn", testingSettings);

    job = RunRoutingJob(job);

    int boardEdgeClassNo = job.board.rules.clearance_matrix.get_no("board_edge");
    assertTrue(boardEdgeClassNo >= 0, "Expected board_edge clearance class to be created.");
    assertEquals(
        boardEdgeClassNo,
        job.board.get_outline().clearance_class_no(),
        "Board outline should be assigned to the board_edge clearance class.");

    int expectedBoardUnits = (int) Math.round(Unit.scale(
        testCopperToEdgeClearanceUm * Math.max(1, job.board.communication.resolution),
        Unit.UM,
        job.board.communication.unit));
    for (int layer = 0; layer < job.board.rules.clearance_matrix.get_layer_count(); layer++) {
      assertEquals(
          expectedBoardUnits,
          job.board.rules.clearance_matrix.get_value(boardEdgeClassNo, boardEdgeClassNo, layer, false),
          "board_edge self-clearance should match copperToEdgeClearanceUm on every layer.");
    }

    int lastLayer = job.board.get_layer_count() - 1;
    assertTrue(
        job.board.get_vias().stream().allMatch(via -> via.first_layer() == 0 && via.last_layer() == lastLayer),
        "All inserted vias should stay on the minimal board span for this 2-layer fixture (0->1).");
    assertTrue(
        job.board.get_vias().stream().allMatch(via -> "Via[0-1]_600:300_um".equals(via.get_padstack().name)),
        "All inserted vias should use the board's smallest configured via type.");
  }
}