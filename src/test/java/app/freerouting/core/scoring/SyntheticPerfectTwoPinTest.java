package app.freerouting.core.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.freerouting.Freerouting;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.rules.NetClass;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Named synthetic fixture from the scoring revision plan: one signal layer, one net, two
 * axis-aligned pins. The unit test inserts a straight centerline trace so both V2 scores are 1000.
 */
class SyntheticPerfectTwoPinTest {

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new app.freerouting.settings.GlobalSettings();
  }

  @Test
  void bothV2ScoresAreOneThousand() {
    BoardReadResult result =
        DsnReader.readBoard(
            DsnTestFixtures.openResource("scoring-perfect-two-pin.dsn"), null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();
    assertNotNull(board);

    List<Pin> pins = new ArrayList<>(board.getPins());
    assertEquals(2, pins.size());
    Point first = pins.get(0).getCenter();
    Point second = pins.get(1).getCenter();
    int netNumber = pins.get(0).getNetNumber(0);
    NetClass netClass = board.rules.nets.get(netNumber).getNetClass();
    int halfWidth = netClass.getTraceHalfWidth(0);
    int clearanceClass = netClass.getTraceClearanceClass();
    board.insertTraceWithoutCleaning(
        new Polyline(first, second),
        0,
        halfWidth,
        new int[] {netNumber},
        clearanceClass,
        FixedState.UNFIXED);

    BoardStatistics stats = new BoardStatistics(board);

    assertEquals(1, stats.layers.signalCount);
    assertEquals(0, stats.connections.incompleteCount, "straight trace must complete the net");
    assertEquals(0, stats.vias.totalCount);
    assertEquals(0, stats.bends.totalCount);
    assertEquals(0, stats.bounds.minViaCount);
    assertEquals(0, stats.bounds.minBendCount);
    assertEquals(
        stats.bounds.minTraceLengthMm,
        stats.traces.totalLengthMm,
        0.01f,
        "centerline must match Manhattan L_min");
    assertEquals(
        0,
        new DesignRulesChecker(board, null).getAllClearanceViolations().size(),
        "full DRC must be clean");
    assertEquals(0, stats.clearanceViolations.totalCount);

    RouterSettings settings = new DefaultSettings().getSettings();
    assertEquals(1000.0f, stats.getRouterScore(settings), 0.01f);
    assertEquals(1000.0f, stats.getOptimizerScore(settings), 0.01f);
  }
}
