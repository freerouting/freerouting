package app.freerouting.autoroute;

import app.freerouting.board.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.interactive.HeadlessBoardManager;
import app.freerouting.settings.RouterScoringSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class BoardHistoryTest {

    private RoutingBoard board1;
    private RoutingBoard board2;
    private RouterScoringSettings scoringSettings;

    @BeforeEach
    void setUp() throws FileNotFoundException {
        // Load a simple board for testing
        HeadlessBoardManager boardManager1 = new HeadlessBoardManager(Locale.US, new RoutingJob());
        FileInputStream inputStream1 = new FileInputStream("tests/empty_board.dsn");
        app.freerouting.designforms.specctra.DsnFile.ReadResult result1 = boardManager1.loadFromSpecctraDsn(inputStream1, new app.freerouting.board.BoardObserverAdaptor(), new app.freerouting.board.ItemIdentificationNumberGenerator());
        board1 = boardManager1.get_routing_board();

        // Load a more complex board
        HeadlessBoardManager boardManager2 = new HeadlessBoardManager(Locale.US, new RoutingJob());
        FileInputStream inputStream2 = new FileInputStream("tests/Issue159-setonix_2hp-pcb.dsn");
        app.freerouting.designforms.specctra.DsnFile.ReadResult result2 = boardManager2.loadFromSpecctraDsn(inputStream2, new app.freerouting.board.BoardObserverAdaptor(), new app.freerouting.board.ItemIdentificationNumberGenerator());
        board2 = boardManager2.get_routing_board();

        scoringSettings = new RouterScoringSettings();
    }

    @Test
    void addAndRestoreBoard() {
        BoardHistory history = new BoardHistory(scoringSettings);
        history.add(board1);

        RoutingBoard restoredBoard = history.restoreBestBoard();

        assertNotNull(restoredBoard);
        assertNotSame(board1, restoredBoard, "Restored board should be a new instance");
        assertEquals(board1.get_hash(), restoredBoard.get_hash(), "Restored board should be functionally identical to the original");
    }

    @Test
    void restoreBestBoardFromMultiple() {
        BoardHistory history = new BoardHistory(scoringSettings);

        // board2 has items, so it should have a worse (lower) score than the empty board1
        history.add(board1);
        history.add(board2);

        RoutingBoard bestBoard = history.restoreBestBoard();

        assertNotNull(bestBoard);
        // The empty board (board1) should have a better score
        assertEquals(board1.get_hash(), bestBoard.get_hash(), "Should restore the board with the best score");
    }

    @Test
    void contains() {
        BoardHistory history = new BoardHistory(scoringSettings);
        history.add(board1);

        assertTrue(history.contains(board1), "History should contain the added board");
        assertFalse(history.contains(board2), "History should not contain a board that was not added");
    }

    @Test
    void clear() {
        BoardHistory history = new BoardHistory(scoringSettings);
        history.add(board1);
        history.add(board2);
        assertEquals(2, history.size());

        history.clear();
        assertEquals(0, history.size(), "History should be empty after clear()");
    }
}
