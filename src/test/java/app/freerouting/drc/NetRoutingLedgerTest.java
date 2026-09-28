package app.freerouting.drc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import app.freerouting.Freerouting;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.rules.NetClass;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NetRoutingLedgerTest {

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new app.freerouting.settings.GlobalSettings();
  }

  @Test
  void incompleteCountMatchesFullScanAcrossInsertAndRemove() {
    BoardReadResult result =
        DsnReader.readBoard(
            DsnTestFixtures.openResource("scoring-perfect-two-pin.dsn"), null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();

    assertMatchesFullScan(board);

    List<Pin> pins = new ArrayList<>(board.getPins());
    Point first = pins.get(0).getCenter();
    Point second = pins.get(1).getCenter();
    int netNumber = pins.get(0).getNetNumber(0);
    NetClass netClass = board.rules.nets.get(netNumber).getNetClass();
    board.insertTraceWithoutCleaning(
        new Polyline(first, second),
        0,
        netClass.getTraceHalfWidth(0),
        new int[] {netNumber},
        netClass.getTraceClearanceClass(),
        FixedState.UNFIXED);

    assertMatchesFullScan(board);
    assertEquals(0, board.routingLedger().incompleteCount());

    Trace trace = board.getTraces().iterator().next();
    board.removeItem(trace);

    assertMatchesFullScan(board);
    assertEquals(1, board.routingLedger().incompleteCount());
  }

  private static void assertMatchesFullScan(RoutingBoard board) {
    DesignRulesChecker checker = new DesignRulesChecker(board, null);
    checker.calculateAllIncompletes();
    assertEquals(checker.getIncompleteCount(), board.routingLedger().incompleteCount());
    assertEquals(checker.maxConnections, board.routingLedger().maximumConnections());
    assertEquals(checker.incompleteNetNumbers(), board.routingLedger().incompleteNetNumbers());
  }
}
