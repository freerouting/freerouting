package app.freerouting.interactive;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * Unit tests for the {@link InteractiveSettings} singleton contract introduced in sub-issue 02.
 */
class InteractiveSettingsSingletonTest {

  private HeadlessBoardManager headlessManager;

  @BeforeEach
  void setUp() throws FileNotFoundException {
    // Reset singleton before each test to ensure isolation.
    InteractiveSettings.resetForTesting();

    headlessManager = new HeadlessBoardManager(new RoutingJob());
    headlessManager.loadFromSpecctraDsn(
        new FileInputStream("tests/empty_board.dsn"),
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
  void headlessBoardManager_getSettings_returnsNull() {
    @SuppressWarnings("deprecation")
    InteractiveSettings settings = headlessManager.get_settings();
    assertNull(settings,
        "HeadlessBoardManager.get_settings() must return null in headless mode");
  }
}

