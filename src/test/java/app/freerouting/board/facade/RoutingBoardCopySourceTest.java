package app.freerouting.board.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.io.specctra.DsnTestFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * The optimizer reads all candidate boards of a pass back from one serialized baseline ({@link
 * RoutingBoard#prepareCopies}, {@link RoutingBoard#copyOf}) instead of serializing the baseline for
 * each candidate ({@link RoutingBoard#deepCopy}). Both must give the same board.
 */
class RoutingBoardCopySourceTest {

  private static RoutingBoard loadBoard(String fixture) {
    BoardReadResult result = DsnReader.readBoard(DsnTestFixtures.openResource(fixture), null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    return (RoutingBoard) ((BoardReadResult.Success) result).board();
  }

  @Test
  void copiesReadFromOneSourceEqualAFreshDeepCopy() throws Exception {
    RoutingBoard board = loadBoard("Issue508-DAC2020_bm01.dsn");
    String sourceHash = board.getHash();
    RoutingBoard deepCopy = board.deepCopy();
    assertNotNull(deepCopy);

    RoutingBoardUndoFacade.CopySource source = board.prepareCopies();
    assertNotNull(source);

    // Several threads read copies from the same source at once, as the optimizer workers do.
    ExecutorService pool = Executors.newFixedThreadPool(4);
    try {
      List<Future<RoutingBoard>> copies = new ArrayList<>();
      for (int i = 0; i < 8; i++) {
        copies.add(pool.submit(() -> RoutingBoard.copyOf(source)));
      }
      List<RoutingBoard> boards = new ArrayList<>();
      for (Future<RoutingBoard> copy : copies) {
        RoutingBoard copied = copy.get(60, TimeUnit.SECONDS);
        assertNotNull(copied);
        boards.add(copied);
        assertEquals(deepCopy.getHash(), copied.getHash());
        assertEquals(deepCopy.getItems().size(), copied.getItems().size());
        assertEquals(
            deepCopy.routingLedger().incompleteCount(), copied.routingLedger().incompleteCount());
      }
      // Every copy is a board of its own.
      assertNotSame(boards.get(0), boards.get(1));
      assertNotSame(boards.get(0).getItems().iterator().next(), board.getItems().iterator().next());
    } finally {
      pool.shutdownNow();
    }
    // Writing the source does not change the board it was written from.
    assertEquals(sourceHash, board.getHash());
  }
}
