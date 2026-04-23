package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class DevBoardClearanceRoutingTest extends RoutingFixtureTest {

  @Test
  void test_Issue_558_Clearance_violation_at_board_edge() {
    var job = GetRoutingJob("Issue558-dev-board.dsn");

    job = RunRoutingJob(job);

    var statsAfter = GetBoardStatistics(job);

    assertEquals(0, statsAfter.connections.incompleteCount, "The incomplete count should be 0");
    assertEquals(0, statsAfter.clearanceViolations.totalCount, "The total count of clearance violations should be 0");
  }
}
