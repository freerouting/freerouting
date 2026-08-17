package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.freerouting.autoroute.RoutingPipeline;
import app.freerouting.board.ItemIdGenerator;
import app.freerouting.board.Unit;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.gui.workspace.GuiRoutingJobWorker;
import app.freerouting.gui.workspace.RunGeneration;
import app.freerouting.gui.workspace.WorkspacePort;
import app.freerouting.management.HeadlessBoardManager;
import app.freerouting.settings.sources.TestingSettings;
import java.util.Collection;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Compares the GUI and headless adapters using the shared routing pipeline. */
class RoutingPipelineComparisonTest extends RoutingFixtureTest {

  @Test
  void guiAndHeadlessPipelinesMatchOnDac2020Fixture() {
    RoutingJob guiJob = getRoutingJob("Issue508-DAC2020_bm01.dsn", createTestingSettings());
    RoutingJob headlessJob = getRoutingJob("Issue508-DAC2020_bm01.dsn", createTestingSettings());
    loadBoard(guiJob);
    loadBoard(headlessJob);

    WorkspacePort workspacePort = mock(WorkspacePort.class);
    when(workspacePort.routingBoard()).thenReturn(guiJob.board);
    when(workspacePort.locale()).thenReturn(Locale.ENGLISH);
    when(workspacePort.displayUnit()).thenReturn(Unit.UM);

    new TestGuiRoutingJobWorker(workspacePort, guiJob).run();

    headlessJob.thread = new NoOpStoppableThread();
    RoutingPipeline.createForHeadless(headlessJob).run();

    BoardStatistics guiStatistics = new BoardStatistics(guiJob.board);
    BoardStatistics headlessStatistics = new BoardStatistics(headlessJob.board);
    assertEquals(
        guiStatistics.connections.incompleteCount, headlessStatistics.connections.incompleteCount);
    assertEquals(guiStatistics.items.viaCount, headlessStatistics.items.viaCount);

    Collection<?> guiViolations =
        new DesignRulesChecker(guiJob.board, null).getAllClearanceViolations();
    Collection<?> headlessViolations =
        new DesignRulesChecker(headlessJob.board, null).getAllClearanceViolations();
    assertEquals(guiViolations.size(), headlessViolations.size());
  }

  private static TestingSettings createTestingSettings() {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(1);
    settings.setMaxItems(2);
    settings.setOptimizerEnabled(true);
    settings.setOptimizerMaxPasses(1);
    settings.setOptimizerMaxItems(2);
    return settings;
  }

  private static void loadBoard(RoutingJob job) {
    HeadlessBoardManager boardManager = new HeadlessBoardManager(job);
    try {
      boardManager.loadFromSpecctraDsn(job.input.getData(), null, new ItemIdGenerator());
    } catch (Exception e) {
      throw new RuntimeException("Failed to load DSN board", e);
    }
    job.board = boardManager.getRoutingBoard();
    job.routerSettings.maxThreads = 1;
    job.routerSettings.optimizer.maxThreads = 1;
  }

  private static final class TestGuiRoutingJobWorker extends GuiRoutingJobWorker {
    private TestGuiRoutingJobWorker(WorkspacePort workspacePort, RoutingJob job) {
      super(workspacePort, new RunGeneration(1), job);
    }
  }

  private static final class NoOpStoppableThread extends StoppableThread {
    @Override
    protected void threadAction() {}
  }
}
