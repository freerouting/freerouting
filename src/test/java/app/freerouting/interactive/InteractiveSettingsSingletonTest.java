package app.freerouting.interactive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import app.freerouting.board.BoardObserverAdaptor;
import app.freerouting.board.ItemIdentificationNumberGenerator;
import app.freerouting.core.RoutingJob;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link InteractiveSettings} singleton contract introduced in sub-issue 02,
 * and the {@code reset}/{@code setInstance} methods introduced for per-load reinitialisation
 * (design-load reset requirement).
 */
class InteractiveSettingsSingletonTest {

  private HeadlessBoardManager headlessManager;

  @BeforeEach
  void setUp() throws FileNotFoundException {
    // Reset singleton before each test to ensure isolation.
    InteractiveSettings.resetForTesting();

    headlessManager = new HeadlessBoardManager(new RoutingJob());
    headlessManager.loadFromSpecctraDsn(
        new FileInputStream("fixtures/empty_board.dsn"),
        new BoardObserverAdaptor(),
        new ItemIdentificationNumberGenerator());
  }

  @AfterEach
  void tearDown() {
    InteractiveSettings.resetForTesting();
  }

  @Test
  void getOrCreate_returnsSameInstance() {
    var board = headlessManager.get_routing_board();
    assertNotNull(board, "Board must be loaded");

    var first = InteractiveSettings.getOrCreate(board);
    var second = InteractiveSettings.getOrCreate(board);

    assertNotNull(first, "First getOrCreate must return non-null");
    assertSame(first, second, "Consecutive getOrCreate calls must return the same instance");
  }

  @Test
  void resetForTesting_allowsFreshCreation() {
    var board = headlessManager.get_routing_board();
    assertNotNull(board, "Board must be loaded");

    var first = InteractiveSettings.getOrCreate(board);
    assertNotNull(first, "getOrCreate before reset must return non-null");

    InteractiveSettings.resetForTesting();

    var second = InteractiveSettings.getOrCreate(board);
    assertNotNull(second, "getOrCreate after reset must return non-null");
  }

  @Test
  void reset_replacesInstance() {
    var board = headlessManager.get_routing_board();
    assertNotNull(board, "Board must be loaded");

    var first = InteractiveSettings.getOrCreate(board);
    assertNotNull(first);

    var second = InteractiveSettings.reset(board);
    assertNotNull(second, "reset must return non-null");
    assertNotSame(first, second, "reset must produce a new instance");
    // getOrCreate must now return the new instance
    assertSame(second, InteractiveSettings.getOrCreate(board),
        "getOrCreate after reset must return the reset instance");
  }

  @Test
  void reset_rebindsToNewBoard() {
    var boardA = headlessManager.get_routing_board();
    assertNotNull(boardA);

    InteractiveSettings.getOrCreate(boardA);

    // Simulate a second board with a different layer count (same board object here,
    // but the key invariant is that reset always replaces regardless of argument).
    var afterReset = InteractiveSettings.reset(boardA);
    assertNotNull(afterReset);

    // After reset, getOrCreate with ANY board returns the already-reset singleton.
    assertSame(afterReset, InteractiveSettings.getOrCreate(boardA));
  }

  @Test
  void setInstance_adoptsProvidedInstance() {
    var board = headlessManager.get_routing_board();
    assertNotNull(board);

    // Simulate what loadFromBinary does: construct from copy constructor (deserialization).
    var original = InteractiveSettings.getOrCreate(board);
    var deserialized = new InteractiveSettings(original); // copy ctor simulates deserialization
    InteractiveSettings.setInstance(deserialized);

    assertSame(deserialized, InteractiveSettings.getOrCreate(board),
        "After setInstance the provided instance must be returned by getOrCreate");
  }

  @Test
  void headlessBoardManager_getSettings_returnsNull() {
    @SuppressWarnings("deprecation")
    InteractiveSettings settings = headlessManager.get_settings();
    assertNull(settings,
        "HeadlessBoardManager.get_settings() must return null in headless mode");
  }

  @Test
  void headlessBoardManager_getInteractiveSettings_returnsNull() {
    assertNull(headlessManager.getInteractiveSettings(),
        "HeadlessBoardManager.getInteractiveSettings() must return null in headless mode");
  }

  @Test
  void secondLoad_reinitializesInteractiveSettings() {
    // Simulate what GuiBoardManager.loadFromSpecctraDsn does on every load:
    // it calls InteractiveSettings.reset(board) unconditionally, so the singleton
    // is always fresh and bound to the current board.
    var board = headlessManager.get_routing_board();
    assertNotNull(board);

    // "First load" — reset produces a fresh singleton.
    var firstSettings = InteractiveSettings.reset(board);
    firstSettings.set_layer(1); // simulate user selecting layer 1

    // "Second load" — reset again (same board object here; in real GUI it may differ).
    var secondSettings = InteractiveSettings.reset(board);

    assertNotSame(firstSettings, secondSettings,
        "Second reset must produce a new InteractiveSettings instance");
    assertEquals(0, secondSettings.get_layer(),
        "Layer must be reset to 0 after a new design load");
    // The singleton must now point at the second instance.
    assertSame(secondSettings, InteractiveSettings.getOrCreate(board),
        "getOrCreate must return the most recent reset instance");
  }
}
