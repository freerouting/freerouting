package app.freerouting.fixtures;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class DevBoardClearanceRoutingTest extends RoutingFixtureTest {

  @Test
  void test_Issue_558_Clearance_violation_at_board_edge() {
    var job = GetRoutingJob("Issue558-dev-board.dsn");

    job = RunRoutingJob(job);

    assertRoutingResult(job, "Issue558-dev-board.dsn")
        .exactIncompleteConnections(0)
        .exactClearanceViolations(0)
        .check();
  }
}