package app.freerouting.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class Issue558Test extends TestBasedOnAnIssue {

  @Test
  @Disabled("Temporary disabled: Freerouting leaves 2 items unconnected.")
  void test_Issue_558_Clearance_violation_at_board_edge() {
    var job = GetRoutingJob("Issue558-dev-board.dsn");

    job = RunRoutingJob(job);

    var statsAfter = GetBoardStatistics(job);

    assertEquals(0, statsAfter.connections.incompleteCount, "The incomplete count should be 0");
    assertEquals(0, statsAfter.clearanceViolations.totalCount, "The total count of clearance violations should be 0");
  }
}