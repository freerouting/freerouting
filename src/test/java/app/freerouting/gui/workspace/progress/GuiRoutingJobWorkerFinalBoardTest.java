package app.freerouting.gui.workspace.progress;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.fixtures.RoutingFixtureTest;
import app.freerouting.gui.workspace.ports.WorkspacePort;
import app.freerouting.gui.workspace.session.RunGeneration;
import app.freerouting.management.HeadlessBoardManager;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * The routing fallback and the end-of-loop restore replace {@code job.board} with another board
 * instance. The workspace keeps the board it handed to the worker unless it is told otherwise, and
 * the GUI saves the output file from the workspace board.
 */
class GuiRoutingJobWorkerFinalBoardTest extends RoutingFixtureTest {

  private static final String BOARD = "Issue508-DAC2020_bm01.dsn";

  private static RoutingBoard load(RoutingJob job) {
    HeadlessBoardManager boardManager = new HeadlessBoardManager(job);
    try {
      boardManager.loadFromSpecctraDsn(job.input.getData(), null, new ItemIdGenerator());
    } catch (Exception e) {
      throw new RuntimeException("Failed to load DSN board", e);
    }
    return boardManager.getRoutingBoard();
  }

  private WorkspacePort workspaceWith(RoutingBoard board) {
    WorkspacePort workspacePort = mock(WorkspacePort.class);
    when(workspacePort.routingBoard()).thenReturn(board);
    when(workspacePort.locale()).thenReturn(Locale.ENGLISH);
    return workspacePort;
  }

  @Test
  void workspaceReceivesTheBoardTheJobEndedWith() {
    RoutingJob job = getRoutingJob(BOARD);
    RoutingBoard workspaceBoard = load(job);
    RoutingBoard chosenBoard = load(getRoutingJob(BOARD));
    WorkspacePort workspacePort = workspaceWith(workspaceBoard);
    RunGeneration generation = new RunGeneration(1);
    GuiRoutingJobWorker worker = new GuiRoutingJobWorker(workspacePort, generation, job);

    job.board = chosenBoard;
    worker.showFinalBoard();

    verify(workspacePort)
        .replaceBoard(argThat(r -> r.board() == chosenBoard && generation.equals(r.generation())));
  }

  @Test
  void workspaceIsLeftAloneWhenTheJobKeptItsBoard() {
    RoutingJob job = getRoutingJob(BOARD);
    WorkspacePort workspacePort = workspaceWith(load(job));
    GuiRoutingJobWorker worker = new GuiRoutingJobWorker(workspacePort, new RunGeneration(1), job);

    worker.showFinalBoard();

    verify(workspacePort, never()).replaceBoard(any());
  }
}
