package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.core.RoutingJob;
import app.freerouting.gui.workspace.ports.WorkspacePort;
import app.freerouting.gui.workspace.progress.GuiRoutingJobWorker;
import app.freerouting.gui.workspace.session.RunGeneration;
import app.freerouting.management.HeadlessBoardManager;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.CliSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Regression test for Issue #1079: Verify that running with {@code -mt 0} or {@code
 * --router.optimizer.enabled=false} (which leaves {@code routerSettings.maxThreads} null) does not
 * abort with NullPointerException in {@link GuiRoutingJobWorker#threadAction()}.
 */
class Issue1079RoutingWithoutOptimizerTest extends RoutingFixtureTest {

  private static final String BOARD = "Issue508-DAC2020_bm01.dsn";

  private static RoutingBoard loadBoard(RoutingJob job) {
    HeadlessBoardManager boardManager = new HeadlessBoardManager(job);
    try {
      boardManager.loadFromSpecctraDsn(job.input.getData(), null, new ItemIdGenerator());
    } catch (Exception e) {
      throw new RuntimeException("Failed to load DSN board", e);
    }
    return boardManager.getRoutingBoard();
  }

  private static WorkspacePort mockWorkspacePort(RoutingBoard board) {
    WorkspacePort workspacePort = mock(WorkspacePort.class);
    when(workspacePort.routingBoard()).thenReturn(board);
    when(workspacePort.locale()).thenReturn(Locale.ENGLISH);
    when(workspacePort.displayUnit()).thenReturn(Unit.UM);
    return workspacePort;
  }

  @Test
  void legacyMtZeroDoesNotThrowNpeInGuiWorker() {
    RouterSettings merged =
        new SettingsMerger(
                new DefaultSettings(), new CliSettings(new String[] {"-mt", "0", "-mp", "1"}))
            .merge();
    merged.autorouter.maxItems = 2;

    assertNull(merged.maxThreads, "maxThreads must remain null when -mt 0 disables the optimizer");
    assertFalse(merged.getRunOptimizer(), "Optimizer must be disabled by -mt 0");

    RoutingJob job = getRoutingJob(BOARD);
    job.routerSettings = merged;
    job.board = loadBoard(job);

    WorkspacePort workspacePort = mockWorkspacePort(job.board);
    GuiRoutingJobWorker worker = new GuiRoutingJobWorker(workspacePort, new RunGeneration(1), job);

    assertDoesNotThrow(
        worker::run, "GuiRoutingJobWorker must not throw NPE when maxThreads is null");
  }

  @Test
  void disabledOptimizerOptionDoesNotThrowNpeInGuiWorker() {
    RouterSettings merged =
        new SettingsMerger(
                new DefaultSettings(),
                new CliSettings(new String[] {"--router.optimizer.enabled=false", "-mp", "1"}))
            .merge();
    merged.autorouter.maxItems = 2;

    assertNull(merged.maxThreads, "maxThreads must remain null when not specified");
    assertFalse(merged.getRunOptimizer(), "Optimizer must be disabled");

    RoutingJob job = getRoutingJob(BOARD);
    job.routerSettings = merged;
    job.board = loadBoard(job);

    WorkspacePort workspacePort = mockWorkspacePort(job.board);
    GuiRoutingJobWorker worker = new GuiRoutingJobWorker(workspacePort, new RunGeneration(1), job);

    assertDoesNotThrow(
        worker::run, "GuiRoutingJobWorker must not throw NPE when maxThreads is null");
  }
}
