package app.freerouting.gui.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.state.BoardObserverAdaptor;
import app.freerouting.core.RoutingJob;
import app.freerouting.management.HeadlessBoardManager;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.DefaultSettings;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that GUI parameter edits (such as via cost adjustments and layer preferred direction
 * inversions) propagate through {@link WorkspaceSettings} and {@link SettingsMerger} without being
 * clobbered or resetting board-specific trace costs.
 */
class Issue1000GuiSettingsTest {

  private static final String TEST_DSN = "fixtures/Issue508-DAC2020_bm01.dsn";
  private RoutingBoard board;
  private RoutingJob job;

  @BeforeEach
  void setUp() throws FileNotFoundException {
    WorkspaceSettings.resetForTesting();
    job = new RoutingJob();
    HeadlessBoardManager manager = new HeadlessBoardManager(job);
    manager.loadFromSpecctraDsn(
        new FileInputStream(TEST_DSN), new BoardObserverAdaptor(), new ItemIdGenerator());
    board = manager.getRoutingBoard();
  }

  @AfterEach
  void tearDown() {
    WorkspaceSettings.resetForTesting();
  }

  @Test
  void testGuiSettingsFlow() {
    // 1. Initialise settings as BoardFrame does at startup
    int boardLayerCount = board.getLayerCount();
    job.routerSettings.setLayerCount(boardLayerCount);
    job.routerSettings.applyBoardSpecificOptimizations(board);

    WorkspaceSettings workspaceSettings = WorkspaceSettings.reset(board, job.routerSettings);
    SettingsMerger settingsMerger = new SettingsMerger(new DefaultSettings(), workspaceSettings);

    var mergedSettings = settingsMerger.merge();
    job.setSettings(mergedSettings);
    workspaceSettings.setSettings(job.routerSettings);

    assertEquals(50, job.routerSettings.getViaCosts());
    assertTrue(job.routerSettings.getPreferredDirectionIsHorizontal(0));
    assertFalse(job.routerSettings.getPreferredDirectionIsHorizontal(1));

    // 2. User modifies settings in WindowAutorouteParameter
    job.routerSettings.setViaCosts(1);
    job.routerSettings.setPreferredDirectionIsHorizontal(0, false);
    job.routerSettings.setPreferredDirectionIsHorizontal(1, true);

    // Sync to workspace as GUI listeners do
    workspaceSettings.setSettings(job.routerSettings);

    // 3. User clicks Autoroute button (reproducing BoardToolbar.java flow)
    var interactiveSettings = workspaceSettings;
    interactiveSettings.setSettings(job.routerSettings);
    var runMerged = settingsMerger.merge();
    job.setSettings(runMerged);
    interactiveSettings.setSettings(job.routerSettings);
    job.routerSettings.applyBoardSpecificOptimizationsIfNeeded(board);

    assertEquals(1, job.routerSettings.getViaCosts(), "Via costs should remain 1");
    assertFalse(
        job.routerSettings.getPreferredDirectionIsHorizontal(0), "Layer 0 should remain vertical");
    assertTrue(
        job.routerSettings.getPreferredDirectionIsHorizontal(1),
        "Layer 1 should remain horizontal");

    // Undesired direction trace cost should reflect inverted directions
    assertEquals(2.9, job.routerSettings.getHorizontalTraceCosts(0), 1e-6);
    assertEquals(1.0, job.routerSettings.getVerticalTraceCosts(0), 1e-6);
    assertEquals(1.0, job.routerSettings.getHorizontalTraceCosts(1), 1e-6);
    assertEquals(1.5, job.routerSettings.getVerticalTraceCosts(1), 1e-6);
  }
}
