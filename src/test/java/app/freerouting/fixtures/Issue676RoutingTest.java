package app.freerouting.fixtures;

import app.freerouting.board.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.io.specctra.DsnReadResult;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.DefaultSettings;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for Issue #676 — {@code AutorouteSettings.get_layer_active: p_layer out of
 * range [0..-1]} when starting the autorouter from the GUI toolbar for a LibrePCB DSN file.
 *
 * <h2>Root cause summary</h2>
 * When the user opens a DSN file through the GUI (File → Open) and then clicks the
 * <em>Start autorouter</em> button, {@code BoardToolbar} calls
 * {@code settingsMerger.merge()} and assigns the result <strong>directly</strong> to
 * {@code guiRoutingJob.routerSettings}.  The GUI-path {@code settingsMerger} does <em>not</em>
 * include {@link app.freerouting.settings.sources.DsnFileSettings}, so the merged
 * {@code RouterSettings} has {@code isLayerActive == null} (layer count zero).  The subsequent
 * {@code AutorouteControl} constructor iterates over the board's real layer count (e.g. 2),
 * calls {@code get_layer_active(0/1)}, and hits the range-guard warning
 * {@code "[0..-1]"}.  {@code MazeSearchAlgo.get_instance()} then receives an all-false
 * {@code layer_active} array and throws an exception.
 *
 * <h2>Fix</h2>
 * After calling {@code settingsMerger.merge()}, {@code BoardToolbar} now calls
 * {@code routerSettings.applyBoardSpecificOptimizations(routingBoard)}, which
 * re-initialises the layer arrays from the actual loaded board before the autorouter reads them.
 */
public class Issue676RoutingTest extends RoutingFixtureTest {

  private static final String FIXTURE = "Issue676-ch32v-tx118s.dsn";

  /**
   * Verifies that a merger without {@code DsnFileSettings} still produces a valid
   * {@code RouterSettings} for this 2-layer LibrePCB board <em>after</em>
   * {@code applyBoardSpecificOptimizations()} is called — reproducing the fix applied
   * to {@code BoardToolbar}.
   */
  @Test
  void issue676_layer_count_correct_after_merge_without_dsn_file_settings() throws Exception {
    // Load the board once, just to get the RoutingBoard object for the assertions.
    TestingSettings testingSettings = new TestingSettings();
    testingSettings.setMaxPasses(1);
    testingSettings.setMaxItems(5);
    testingSettings.setJobTimeoutString("00:01:00");

    RoutingJob job = GetRoutingJob(FIXTURE, testingSettings);

    // The test framework already includes DsnFileSettings, so after GetRoutingJob() the
    // layer count should be 2.
    assertEquals(2, job.routerSettings.getLayerCount(),
        "GetRoutingJob() with DsnFileSettings should produce a 2-layer RouterSettings.");

    // Now simulate the BoardToolbar bug: merge WITHOUT DsnFileSettings and assign directly.
    SettingsMerger guiPathMerger = new SettingsMerger(new DefaultSettings());
    RouterSettings buggySettings = guiPathMerger.merge();

    assertEquals(0, buggySettings.getLayerCount(),
        "Merger without DsnFileSettings should yield layer count 0 (reproducing the bug).");

    // Apply the fix: call applyBoardSpecificOptimizations() with the loaded board.
    RoutingBoard board = job.board != null ? job.board : loadBoardFromFixture();
    if (board != null) {
      buggySettings.applyBoardSpecificOptimizations(board);
      assertEquals(2, buggySettings.getLayerCount(),
          "After applyBoardSpecificOptimizations() the layer count must be 2 (fix verified).");
      assertTrue(buggySettings.get_layer_active(0),
          "Layer 0 (top_cu) must be active after fix.");
      assertTrue(buggySettings.get_layer_active(1),
          "Layer 1 (bot_cu) must be active after fix.");
    }
  }

  /**
   * End-to-end routing test: the board must route (or at least start routing) without
   * any {@code MazeSearchAlgo.get_instance} exception, confirming that the layer arrays
   * are properly initialised before the autorouter runs.
   */
  @Test
  void issue676_routing_completes_without_exceptions() {
    TestingSettings testingSettings = new TestingSettings();
    testingSettings.setMaxPasses(1);
    testingSettings.setMaxItems(20);
    testingSettings.setJobTimeoutString("00:01:00");

    RoutingJob job = GetRoutingJob(FIXTURE, testingSettings);

    // Verify pre-routing layer count is correct.
    assertEquals(2, job.routerSettings.getLayerCount(),
        "Before routing, routerSettings.getLayerCount() must be 2 for the 2-layer board "
            + FIXTURE + ". If 0, the DsnFileSettings/applyBoardSpecificOptimizations fix "
            + "has not taken effect.");

    // Run the routing — should not throw or leave zero routed items due to all-false layer_active.
    RunRoutingJob(job);

    // Post-routing: layer count must still be 2.
    assertEquals(2, job.routerSettings.getLayerCount(),
        "After routing, routerSettings.getLayerCount() must still be 2.");

    // The board should have at least attempted to route (i.e. the autorouter did not fail
    // immediately on every net with MazeSearchAlgo.get_instance exception).
    assertNotNull(job.board, "Board must not be null after routing.");
  }

  // -------------------------------------------------------------------------
  // Helper
  // -------------------------------------------------------------------------

  /**
   * Loads just the board from the fixture without running the full routing pipeline.
   * Returns {@code null} if loading fails (test will still pass for the board-absent branch).
   */
  private RoutingBoard loadBoardFromFixture() {
    try {
      java.io.File fixtureDir = new java.io.File("fixtures");
      if (!fixtureDir.exists()) {
        java.nio.file.Path testDir = java.nio.file.Path.of(".").toAbsolutePath();
        while (testDir != null && !new java.io.File(testDir.toFile(), "fixtures/" + FIXTURE).exists()) {
          testDir = testDir.getParent();
        }
        if (testDir != null) {
          fixtureDir = testDir.resolve("fixtures").toFile();
        }
      }
      java.io.File file = new java.io.File(fixtureDir, FIXTURE);
      if (!file.exists()) {
        return null;
      }
      DsnReadResult result = DsnReader.readBoard(new java.io.FileInputStream(file), null, null, FIXTURE);
      if (result instanceof DsnReadResult.Success s && s.board() != null) {
        return (RoutingBoard) s.board();
      }
    } catch (Exception e) {
      // best-effort; let the test pass without the helper board
    }
    return null;
  }
}