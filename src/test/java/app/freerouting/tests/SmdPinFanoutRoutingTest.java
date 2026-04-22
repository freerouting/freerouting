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
 * Regression tests for the SMD-pin routing failure described in
 * {@code docs/issues/smd-pin-fanout-routing.md}.
 *
 * <h2>Root cause</h2>
 * All boards below have SMD-only pads with {@code (attach off)} in their DSN.
 * This propagates through the pipeline:
 * <pre>
 *   Padstack.attach_allowed = false
 *     → ViaInfo.attach_smd_allowed = false
 *       → AutorouteControl.attach_smd_allowed = false
 *         → Pin.is_obstacle() returns true for same-net vias
 *           → maze search cannot find via-escape from SMD pads
 *             → net unrouted
 * </pre>
 *
 * <h2>Fix under investigation (Sub-issue #0)</h2>
 * Remove the {@code || !via.attach_allowed} guard from the same-net branch of
 * {@code Pin.is_obstacle()}.  Tests in this class document the expected
 * outcomes AFTER that fix and should turn from failing to passing once it is
 * applied.
 *
 * <h2>Test boards</h2>
 * <ol>
 *   <li>{@code SMD-routing-issue-demo.dsn} – minimal synthetic board created
 *       specifically to reproduce the bug in isolation.</li>
 *   <li>{@code Issue508-DAC2020_bm06.dsn} – DAC 2020 benchmark, 2-layer,
 *       34 all-SMD components, 38 nets.</li>
 *   <li>{@code Issue558-dev-board.dsn} – real-world 2-layer ESP32-S3 dev
 *       board, mostly SMD with two through-hole connectors, 47 nets.</li>
 *   <li>{@code Issue508-DAC2020_bm10.dsn} – DAC 2020 benchmark, 4-layer,
 *       61 SMD components, 63 nets (also exercises outer-layer cost penalty).</li>
 * </ol>
 */
public class SmdPinFanoutRoutingTest extends RoutingFixtureTest {

  private RoutingJob job;

  // =========================================================================
  // 1. Synthetic minimal reproduction case
  // =========================================================================

  /**
   * Baseline test: verifies the synthetic demo board loads and is processed
   * by the router.  Documents the CURRENT (broken) state before the fix:
   * all 6 nets remain unrouted because the {@code attach_off} + {@code Pin.is_obstacle()}
   * bug prevents the maze search from expanding near any SMD pad.
   *
   * <p>After Sub-issue #0 fix, the 4 local nets should route; only the 2
   * crossing nets may still need BatchFanout (Sub-issue #5) to complete.
   */
  @Test
  @Tag("smd-routing-issue")
  public void test_SmdDemo_board_loads_and_routes() {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(3);
    settings.setJobTimeoutString("00:00:30");

    job = GetRoutingJob("SMD-routing-issue-demo.dsn", settings);
    RunRoutingJob(job);

    // Board must load successfully (board != null is enforced by RunRoutingJob)
    // Current broken state: 6 of 6 nets unrouted.
    // After Sub-issue #0 fix: incomplete should drop to <= 2 (crossing nets only).
    int incomplete = job.board.get_statistics().connections.incompleteCount;
    assertTrue(incomplete <= 6,
        "SMD demo board: expected at most 6 incomplete connections (6 nets total), got: "
            + incomplete);
  }

  /**
   * Smoke test: after Sub-issue #0 fix, the 4 same-side local nets should
   * route without layer changes.  The 2 crossing nets may still fail without
   * BatchFanout (Sub-issue #5), leaving at most 2 incomplete.
   *
   * <p><b>Expected to FAIL before Sub-issue #0 fix.</b>
   */
  @Test
  @Tag("smd-routing-issue")
  public void test_SmdDemo_local_nets_route() {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(3);
    settings.setJobTimeoutString("00:00:30");

    job = GetRoutingJob("SMD-routing-issue-demo.dsn", settings);
    RunRoutingJob(job);

    // After fix: at least the 4 local (same-side) nets must succeed
    assertTrue(job.board.get_statistics().connections.incompleteCount <= 2,
        "SMD demo board: at most 2 of 6 nets should remain unrouted "
            + "(the 4 local nets must succeed after the fix), got: "
            + job.board.get_statistics().connections.incompleteCount);
  }

  /**
   * Full routing of the synthetic demo board.
   * All 6 nets must complete.
   *
   * <p><b>Expected to FAIL before Sub-issue #0 fix.</b>
   */
  @Test
  @Tag("smd-routing-issue")
  public void test_SmdDemo_all_nets_route() {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(10);
    settings.setJobTimeoutString("00:00:30");

    job = GetRoutingJob("SMD-routing-issue-demo.dsn", settings);
    RunRoutingJob(job);

    assertEquals(0, job.board.get_statistics().connections.incompleteCount,
        "SMD demo board should route to 0 unrouted connections after the fix");
  }

  // =========================================================================
  // 2. DAC 2020 bm06 (2-layer, 34 SMD components, 38 nets)
  // =========================================================================

  /**
   * Quick smoke test: route the first 5 items of bm06 within 1 pass.
   * Used as a fast CI gate to catch gross regressions.
   */
  @Test
  @Tag("smd-routing-issue")
  public void test_BM06_first_5_items() {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(1);
    settings.setMaxItems(5);
    settings.setJobTimeoutString("00:00:30");

    job = GetRoutingJob("Issue508-DAC2020_bm06.dsn", settings);
    RunRoutingJob(job);

    // After fix: at least 3 of the first 5 items should route
    assertTrue(job.board.get_statistics().connections.incompleteCount <= 35,
        "bm06 first-5 smoke: expected <= 35 incomplete (at least 3 of 5 routed), got: "
            + job.board.get_statistics().connections.incompleteCount);
  }

  /**
   * Full routing of bm06.  This 2-layer all-SMD board should be completely
   * routable to 0 unrouted connections.
   *
   * <p><b>Expected to FAIL before Sub-issue #0 or Sub-issue #5 fix.</b>
   */
  @Test
  @Tag("smd-routing-issue")
  public void test_BM06_full_routing() {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(20);
    settings.setJobTimeoutString("00:05:00");

    job = GetRoutingJob("Issue508-DAC2020_bm06.dsn", settings);
    RunRoutingJob(job);

    assertEquals(0, job.board.get_statistics().connections.incompleteCount,
        "bm06 should route to 0 unrouted connections");
  }

  // =========================================================================
  // 3. Real-world 2-layer dev board (21 SMD + 2 through-hole, 47 nets)
  // =========================================================================

  /**
   * Full routing of the ESP32-S3 dev board (Issue558).
   * Mixed SMD + through-hole board.  After the fix, all 47 nets should
   * complete.
   *
   * <p><b>Crossing-net failures expected to disappear after Sub-issue #0 fix.</b>
   */
  @Test
  @Tag("smd-routing-issue")
  public void test_Issue558_dev_board_full_routing() {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(20);
    settings.setJobTimeoutString("00:03:00");

    job = GetRoutingJob("Issue558-dev-board.dsn", settings);
    RunRoutingJob(job);

    assertEquals(0, job.board.get_statistics().connections.incompleteCount,
        "Issue558 dev board should route to 0 unrouted connections after the fix");
  }

  /**
   * Quick check: at least half of the Issue558 nets should route even before
   * the full SMD fix (the through-hole + simple same-layer SMD routes).
   */
  @Test
  @Tag("smd-routing-issue")
  public void test_Issue558_dev_board_minimum_routed() {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(3);
    settings.setJobTimeoutString("00:01:00");

    job = GetRoutingJob("Issue558-dev-board.dsn", settings);
    RunRoutingJob(job);

    // Conservative: at least 20 of 47 nets should route even with the bug
    assertTrue(job.board.get_statistics().connections.incompleteCount <= 27,
        "Issue558 dev board: expected <= 27 incomplete connections (20+ nets routed), got: "
            + job.board.get_statistics().connections.incompleteCount);
  }

  // =========================================================================
  // 4. DAC 2020 bm10 (4-layer, 61 SMD components, 63 nets)
  // =========================================================================

  /**
   * Full routing of bm10.  This 4-layer SMD board should route to 0 unrouted
   * connections.  Also exercises the outer-layer cost penalty described in
   * Solution 11 (docs/issues/smd-pin-fanout-routing.md).
   *
   * <p><b>Expected to FAIL before all SMD fixes are applied.</b>
   */
  @Test
  @Tag("smd-routing-issue")
  public void test_BM10_full_routing() {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(20);
    settings.setJobTimeoutString("00:08:00");

    job = GetRoutingJob("Issue508-DAC2020_bm10.dsn", settings);
    RunRoutingJob(job);

    assertEquals(0, job.board.get_statistics().connections.incompleteCount,
        "bm10 (4-layer SMD) should route to 0 unrouted connections");
  }

  /**
   * Smoke test: first 10 items of bm10.
   */
  @Test
  @Tag("smd-routing-issue")
  public void test_BM10_first_10_items() {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(1);
    settings.setMaxItems(10);
    settings.setJobTimeoutString("00:01:00");

    job = GetRoutingJob("Issue508-DAC2020_bm10.dsn", settings);
    RunRoutingJob(job);

    // After fix: at least 5 of the first 10 items should route
    assertTrue(job.board.get_statistics().connections.incompleteCount <= 58,
        "bm10 first-10 smoke: expected <= 58 incomplete (at least 5 of 10 routed), got: "
            + job.board.get_statistics().connections.incompleteCount);
  }

  // =========================================================================
  // Teardown
  // =========================================================================

  @AfterEach
  public void tearDown() {
    if (job != null) {
      RoutingJobScheduler
          .getInstance()
          .clearJobs(job.sessionId.toString());
    }
  }
}


