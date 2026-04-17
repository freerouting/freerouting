package app.freerouting.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class Issue159Test extends TestBasedOnAnIssue {

  @Test
  void test_Issue_159_Out_of_memory_error() {
    // The original issue was an out-of-memory error during routing.
    // This test verifies that routing completes without crashing and produces no clearance violations.
    // The board has 4 connections that the auto-router cannot complete within its stopping criteria.
    var job = GetRoutingJob("Issue159-setonix_2hp-pcb.dsn");

    job = RunRoutingJob(job);

    var statsAfter = GetBoardStatistics(job);

    assertTrue(statsAfter.connections.incompleteCount <= 4,
            "The incomplete count should be at most 4");
    assertEquals(0, statsAfter.clearanceViolations.totalCount, "The total count of clearance violations should be 0");
  }
}