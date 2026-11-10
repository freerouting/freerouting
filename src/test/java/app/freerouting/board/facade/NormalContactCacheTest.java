package app.freerouting.board.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.rules.NetClass;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The cached normal contacts follow every change of the board. */
class NormalContactCacheTest {

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new app.freerouting.settings.GlobalSettings();
  }

  @Test
  void cachedContactsFollowInsertRemoveAndUndo() {
    BoardReadResult result =
        DsnReader.readBoard(
            DsnTestFixtures.openResource("scoring-perfect-two-pin.dsn"), null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();
    List<Pin> pins = new ArrayList<>(board.getPins());
    Pin first = pins.get(0);
    Pin second = pins.get(1);

    assertCacheMatches(board, first);
    assertTrue(board.cachedNormalContacts(first).isEmpty());

    board.generateSnapshot();
    int netNumber = first.getNetNumber(0);
    NetClass netClass = board.rules.nets.get(netNumber).getNetClass();
    board.insertTraceWithoutCleaning(
        new Polyline(first.getCenter(), second.getCenter()),
        0,
        netClass.getTraceHalfWidth(0),
        new int[] {netNumber},
        netClass.getTraceClearanceClass(),
        FixedState.UNFIXED);
    Trace trace = board.getTraces().iterator().next();
    assertCacheMatches(board, first);
    assertTrue(board.cachedNormalContacts(first).contains(trace));
    assertFalse(trace.isCycle(), "a single trace between two pins is no cycle");
    assertEquals(2, trace.getConnectedSet(netNumber).size() - 1);

    board.removeItem(trace);
    assertCacheMatches(board, first);
    assertTrue(board.cachedNormalContacts(first).isEmpty());

    board.undo(null);
    assertCacheMatches(board, first);
  }

  private static void assertCacheMatches(BasicBoard board, Item item) {
    assertEquals(item.getNormalContacts(), board.cachedNormalContacts(item));
  }
}
