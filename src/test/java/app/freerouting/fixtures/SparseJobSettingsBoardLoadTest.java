package app.freerouting.fixtures;

import static app.freerouting.util.gson.GsonProvider.GSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.core.RoutingJob;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.sources.DefaultSettings;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

/**
 * A job created through the API or MCP server only carries the settings the client sent; the
 * defaults are merged in by the scheduler. The board-level overrides applied while the board is
 * loaded (copper-to-edge clearance, minimum hole-to-hole distance) must still see the defaults, so
 * that such a job routes the same board as a CLI job, whose settings arrive already merged.
 */
class SparseJobSettingsBoardLoadTest extends RoutingFixtureTest {

  private static final String BOARD = "Issue508-DAC2020_bm01.dsn";

  /** Same settings as an API client that only sets the pass limits. */
  private static final String SPARSE_SETTINGS_JSON =
      "{\"job_timeout\":\"00:01:00\",\"autorouter\":{\"max_passes\":1},"
          + "\"optimizer\":{\"enabled\":false}}";

  private RoutingJob runWithSparseSettings() {
    RoutingJob job = getRoutingJob(BOARD);
    job.routerSettings = GSON.fromJson(SPARSE_SETTINGS_JSON, RouterSettings.class);
    return runRoutingJob(job);
  }

  /** The CLI path: every default merged in before the job reaches the scheduler. */
  private RoutingJob runWithMergedSettings() {
    // TestingSettings turns the edge clearance off for fixtures; put the real default back.
    var testingSettings = new TestingSettings();
    testingSettings.setCopperToEdgeClearanceUm(DefaultSettings.DEFAULT_COPPER_TO_EDGE_CLEARANCE_UM);
    RoutingJob job = getRoutingJob(BOARD, testingSettings);
    job.routerSettings.autorouter.maxPasses = 1;
    job.routerSettings.optimizer.enabled = false;
    return runRoutingJob(job);
  }

  @Test
  void sparseJobSettingsLoadTheSameBoardRulesAsMergedSettings() {
    RoutingJob merged = runWithMergedSettings();
    RoutingJob sparse = runWithSparseSettings();

    ClearanceMatrix mergedMatrix = merged.board.rules.clearanceMatrix;
    ClearanceMatrix sparseMatrix = sparse.board.rules.clearanceMatrix;
    int mergedEdgeClass = mergedMatrix.getNo("board_edge");
    int sparseEdgeClass = sparseMatrix.getNo("board_edge");
    assertTrue(mergedEdgeClass >= 0, "merged settings must create the board_edge class");
    assertTrue(
        sparseEdgeClass >= 0,
        "the default router.copper_to_edge_clearance_um must apply to a job with sparse settings");
    for (int layer = 0; layer < mergedMatrix.getLayerCount(); layer++) {
      assertEquals(
          mergedMatrix.getValue(mergedEdgeClass, mergedEdgeClass, layer, false),
          sparseMatrix.getValue(sparseEdgeClass, sparseEdgeClass, layer, false),
          "board edge clearance on layer " + layer);
    }

    assertTrue(merged.board.rules.getMinHoleToHole() > 0, "default hole-to-hole must apply");
    assertEquals(
        merged.board.rules.getMinHoleToHole(),
        sparse.board.rules.getMinHoleToHole(),
        "the default router.hole_to_hole_um must apply to a job with sparse settings");
  }
}
