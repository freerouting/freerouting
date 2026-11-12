package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.events.BoardUpdatedEvent;
import app.freerouting.autoroute.events.BoardUpdatedEventListener;
import app.freerouting.autoroute.pipeline.RoutingPipeline;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.StoppableThread;
import app.freerouting.io.specctra.SesWriter;
import app.freerouting.management.HeadlessBoardManager;
import app.freerouting.settings.sources.TestingSettings;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The router builds the board statistics of its events only for listeners that read them, and
 * routes the same either way.
 */
class BoardStatisticsReadersTest extends RoutingFixtureTest {

  private static final String FIXTURE = "Issue508-DAC2020_bm01.dsn";

  @Test
  void statisticsAreBuiltOnlyForListenersThatReadThem() throws Exception {
    List<BoardUpdatedEvent> withoutReader = new ArrayList<>();
    RoutingJob jobWithoutReader =
        route(List.of(BoardUpdatedEventListener.ignoringStatistics(withoutReader::add)));
    assertTrue(!withoutReader.isEmpty(), "no board updated event");
    for (BoardUpdatedEvent event : withoutReader) {
      assertNull(event.getBoardStatistics(), "statistics built for no reader");
    }

    List<BoardUpdatedEvent> withReader = new ArrayList<>();
    RoutingJob jobWithReader =
        route(
            List.of(
                BoardUpdatedEventListener.ignoringStatistics(event -> {}),
                event -> withReader.add(event)));
    assertTrue(!withReader.isEmpty(), "no board updated event");
    for (BoardUpdatedEvent event : withReader) {
      assertNotNull(event.getBoardStatistics(), "statistics missing for a reader");
    }

    assertArrayEquals(ses(jobWithReader), ses(jobWithoutReader), "the routing changed");
  }

  private RoutingJob route(List<BoardUpdatedEventListener> listeners) {
    TestingSettings settings = new TestingSettings();
    settings.setFanoutEnabled(false);
    settings.setOptimizerEnabled(false);
    settings.setMaxItems(30);
    RoutingJob job = getRoutingJob(FIXTURE, settings);
    HeadlessBoardManager boardManager = new HeadlessBoardManager(job);
    try {
      boardManager.loadFromSpecctraDsn(job.input.getData(), null, new ItemIdGenerator());
    } catch (Exception e) {
      throw new RuntimeException("Failed to load DSN board", e);
    }
    job.board = boardManager.getRoutingBoard();
    job.routerSettings.maxThreads = 1;
    job.thread = new NoOpStoppableThread();
    RoutingPipeline pipeline = RoutingPipeline.createForHeadless(job);
    listeners.forEach(pipeline::addBoardUpdatedEventListener);
    pipeline.run();
    return job;
  }

  private static byte[] ses(RoutingJob job) throws Exception {
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      SesWriter.write(job.board, outputStream, "board");
      return outputStream.toByteArray();
    }
  }

  private static final class NoOpStoppableThread extends StoppableThread {
    @Override
    protected void threadAction() {}
  }
}
