package app.freerouting.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class Issue229Test extends TestBasedOnAnIssue {

  @Test
  @Disabled("Temporary disabled: Freerouting leaves 2 items unconnected.")
  void test_Issue_229_Keepout_zone_was_not_exported_correctly() {
    var job = GetRoutingJob("Issue229-display-8-digit-hc595.dsn");

    job = RunRoutingJob(job);

    var statsAfter = GetBoardStatistics(job);

    assertEquals(0, statsAfter.connections.incompleteCount, "The incomplete count should be 0");
    assertEquals(0, statsAfter.clearanceViolations.totalCount, "The total count of clearance violations should be 0");
  }
}