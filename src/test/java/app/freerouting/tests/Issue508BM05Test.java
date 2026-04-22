package app.freerouting.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.core.RoutingJob;
import app.freerouting.management.RoutingJobScheduler;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for {@code Issue508-DAC2020_bm05.dsn}.
 *
 * <p>The bm05 board is the <em>primary acceptance gate</em> for the SMD-pin
 * routing bug documented in {@code docs/issues/smd-pin-fanout-routing.md}.
 *
 * <h2>Board characteristics</h2>
 * <ul>
 *   <li>2 signal layers (Top / Bottom)</li>
 *   <li>48 components — ALL SMD, zero through-hole</li>
 *   <li>54 nets</li>
 *   <li>11 padstack types, every one with {@code (attach off)}</li>
 *   <li>Notable component: U27 — QFN50P700X700X100-49N (48 + 1 thermal pad)
 *       with 400 µm pitch.  Via escape from this chip is the primary failure
 *       point.</li>
 * </ul>
 *
 * <h2>Expected state</h2>
 * <ul>
 *   <li><b>Before fix</b>: 0 connections routed (all SMD, all attach_off).</li>
 *   <li><b>After Sub-issue #0</b> ({@code Pin.is_obstacle()} fix): significant
 *       improvement; bm05 should trend toward 0 unrouted.</li>
 *   <li><b>After Sub-issue #5</b> (BatchFanout integration): 0 unrouted.</li>
 * </ul>
 */
public class Issue508BM05Test extends TestBasedOnAnIssue {

  private RoutingJob job;

  /**
   * Ultra-fast smoke test: route only the first 2 items of bm05.
   * Runs in under 15 seconds on any hardware.
   * After the fix at least 1 of the 2 SMD connections should succeed.
   */
  @Test
  @Tag("smd-routing-issue")
  public void test_Issue_508_BM05_first_2_items() {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(1);
    settings.setMaxItems(2);
    settings.setJobTimeoutString("00:00:15");

    job = GetRoutingJob("Issue508-DAC2020_bm05.dsn", settings);
    RunRoutingJob(job);

    // After fix: at least 1 of the 2 items must route (53 or fewer incomplete)
    assertTrue(job.board.get_statistics().connections.incompleteCount <= 53,
        "bm05 first-2: at least 1 SMD connection should route after the fix, got incomplete="
            + job.board.get_statistics().connections.incompleteCount);
  }

  /**
   * Quick check: route the first 5 items of bm05 in a single pass.
   * After the fix at least 3 of the 5 should succeed.
   */
  @Test
  @Tag("smd-routing-issue")
  public void test_Issue_508_BM05_first_5_items() {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(1);
    settings.setMaxItems(5);
    settings.setJobTimeoutString("00:00:30");

    job = GetRoutingJob("Issue508-DAC2020_bm05.dsn", settings);
    RunRoutingJob(job);

    // After fix: at most 2 of 5 still incomplete
    assertTrue(job.board.get_statistics().connections.incompleteCount <= 51,
        "bm05 first-5: at least 3 of 5 SMD connections should route after the fix, got: "
            + job.board.get_statistics().connections.incompleteCount);
  }

  /**
   * Medium test: first pass over all 54 nets.
   * After the fix, at most half the nets should remain unrouted after pass 1.
   */
  @Test
  @Tag("smd-routing-issue")
  public void test_Issue_508_BM05_first_pass() {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(1);
    settings.setJobTimeoutString("00:02:00");

    job = GetRoutingJob("Issue508-DAC2020_bm05.dsn", settings);
    RunRoutingJob(job);

    // After fix: fewer than 27 incomplete after the first pass
    assertTrue(job.board.get_statistics().connections.incompleteCount <= 27,
        "bm05 first pass: expected <= 27 incomplete connections after the fix, got: "
            + job.board.get_statistics().connections.incompleteCount);
  }

  /**
   * Full routing of bm05.  This is the <em>primary acceptance gate</em>:
   * 0 unrouted connections.
   *
   * <p><b>This test is expected to FAIL before the fix is applied.</b>
   * It documents the goal state and serves as the final regression gate once
   * Sub-issues #0 and #5 are complete.
   */
  @Test
  @Tag("smd-routing-issue")
  public void test_Issue_508_BM05_full_routing() {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(20);
    settings.setJobTimeoutString("00:05:00");

    job = GetRoutingJob("Issue508-DAC2020_bm05.dsn", settings);
    RunRoutingJob(job);

    assertEquals(0, job.board.get_statistics().connections.incompleteCount,
        "bm05 should route to 0 unrouted connections");
  }

  @AfterEach
  public void tearDown() {
    if (job != null) {
      RoutingJobScheduler
          .getInstance()
          .clearJobs(job.sessionId.toString());
    }
  }
}
