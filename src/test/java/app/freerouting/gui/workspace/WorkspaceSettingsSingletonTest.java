package app.freerouting.gui.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.state.BoardObserverAdaptor;
import app.freerouting.core.RoutingJob;
import app.freerouting.management.HeadlessBoardManager;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link WorkspaceSettings} singleton contract introduced in sub-issue 02, and
 * the {@code reset}/{@code setInstance} methods introduced for per-load reinitialisation
 * (design-load reset requirement).
 */
class WorkspaceSettingsSingletonTest {

  private HeadlessBoardManager headlessManager;

  @BeforeEach
  void setUp() throws FileNotFoundException {
    // Reset singleton before each test to ensure isolation.
    WorkspaceSettings.resetForTesting();

    headlessManager = new HeadlessBoardManager(new RoutingJob());
    headlessManager.loadFromSpecctraDsn(
        new FileInputStream("fixtures/empty_board.dsn"),
        new BoardObserverAdaptor(),
        new ItemIdGenerator());
  }

  @AfterEach
  void tearDown() {
    WorkspaceSettings.resetForTesting();
  }

  @Test
  void getOrCreateReturnsSameInstance() {
    var board = headlessManager.getRoutingBoard();
    assertNotNull(board, "Board must be loaded");

    var first = WorkspaceSettings.getOrCreate(board);
    var second = WorkspaceSettings.getOrCreate(board);

    assertNotNull(first, "First getOrCreate must return non-null");
    assertSame(first, second, "Consecutive getOrCreate calls must return the same instance");
  }

  @Test
  void resetForTestingAllowsFreshCreation() {
    var board = headlessManager.getRoutingBoard();
    assertNotNull(board, "Board must be loaded");

    var first = WorkspaceSettings.getOrCreate(board);
    assertNotNull(first, "getOrCreate before reset must return non-null");

    WorkspaceSettings.resetForTesting();

    var second = WorkspaceSettings.getOrCreate(board);
    assertNotNull(second, "getOrCreate after reset must return non-null");
  }

  @Test
  void resetReplacesInstance() {
    var board = headlessManager.getRoutingBoard();
    assertNotNull(board, "Board must be loaded");

    var first = WorkspaceSettings.getOrCreate(board);
    assertNotNull(first);

    var second = WorkspaceSettings.reset(board);
    assertNotNull(second, "reset must return non-null");
    assertNotSame(first, second, "reset must produce a new instance");
    // getOrCreate must now return the new instance
    assertSame(
        second,
        WorkspaceSettings.getOrCreate(board),
        "getOrCreate after reset must return the reset instance");
  }

  @Test
  void resetRebindsToNewBoard() {
    var boardA = headlessManager.getRoutingBoard();
    assertNotNull(boardA);

    WorkspaceSettings.getOrCreate(boardA);

    // Simulate a second board with a different layer count (same board object here,
    // but the key invariant is that reset always replaces regardless of argument).
    var afterReset = WorkspaceSettings.reset(boardA);
    assertNotNull(afterReset);

    // After reset, getOrCreate with ANY board returns the already-reset singleton.
    assertSame(afterReset, WorkspaceSettings.getOrCreate(boardA));
  }

  @Test
  void setInstanceAdoptsProvidedInstance() {
    var board = headlessManager.getRoutingBoard();
    assertNotNull(board);

    // Simulate what loadFromBinary does: construct from copy constructor (deserialization).
    var original = WorkspaceSettings.getOrCreate(board);
    var deserialized = new WorkspaceSettings(original); // copy ctor simulates deserialization
    WorkspaceSettings.setInstance(deserialized);

    assertSame(
        deserialized,
        WorkspaceSettings.getOrCreate(board),
        "After setInstance the provided instance must be returned by getOrCreate");
  }

  @Test
  void secondLoadReinitializesWorkspaceSettings() {
    // Simulate what GuiBoardManager.loadFromSpecctraDsn does on every load:
    // it calls WorkspaceSettings.reset(board) unconditionally, so the singleton
    // is always fresh and bound to the current board.
    var board = headlessManager.getRoutingBoard();
    assertNotNull(board);

    // "First load" — reset produces a fresh singleton.
    var firstSettings = WorkspaceSettings.reset(board);
    firstSettings.setLayer(1); // simulate user selecting layer 1

    // "Second load" — reset again (same board object here; in real GUI it may differ).
    var secondSettings = WorkspaceSettings.reset(board);

    assertNotSame(
        firstSettings,
        secondSettings,
        "Second reset must produce a new WorkspaceSettings instance");
    assertEquals(0, secondSettings.getLayer(), "Layer must be reset to 0 after a new design load");
    // The singleton must now point at the second instance.
    assertSame(
        secondSettings,
        WorkspaceSettings.getOrCreate(board),
        "getOrCreate must return the most recent reset instance");
  }
}
