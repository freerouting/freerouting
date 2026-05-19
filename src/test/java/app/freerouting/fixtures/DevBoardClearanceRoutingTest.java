package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class DevBoardClearanceRoutingTest extends RoutingFixtureTest {

  @Test
  void test_Issue_558_Clearance_violation_at_board_edge() {
    var job = GetRoutingJob("Issue558-dev-board.dsn");

    job = RunRoutingJob(job);

    assertRoutingResult(job, "Issue558-dev-board.dsn")
        .exactIncompleteConnections(0)
        .maxClearanceViolations(2)
        .check();

    int lastLayer = job.board.get_layer_count() - 1;
    assertTrue(
        job.board.get_vias().stream().allMatch(via -> via.first_layer() == 0 && via.last_layer() == lastLayer),
        "All inserted vias should stay on the minimal board span for this 2-layer fixture (0->1).");
    assertTrue(
        job.board.get_vias().stream().allMatch(via -> "Via[0-1]_600:300_um".equals(via.get_padstack().name)),
        "All inserted vias should use the board's smallest configured via type.");
  }
}