package app.freerouting.interactive;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.BoardObserverAdaptor;
import app.freerouting.board.ItemIdentificationNumberGenerator;
import app.freerouting.core.RoutingJob;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the {@link BoardManager} interface (sub-issue 03).
 *
 * <p>Verifies the invariant: if {@link BoardManager#isInteractiveModeSupported()} then
 * {@link BoardManager#getInteractiveSettings()} must return a non-null value after board
 * initialisation; otherwise it must return {@code null}.
 */
class BoardManagerContractTest {

  @BeforeEach
  void setUp() {
    InteractiveSettings.resetForTesting();
  }

  @AfterEach
  void tearDown() {
    InteractiveSettings.resetForTesting();
  }

  // ── Headless contract ─────────────────────────────────────────────────────

  @Test
  void headlessManager_isInteractiveModeSupported_returnsFalse() {
    BoardManager manager = new HeadlessBoardManager(new RoutingJob());
    assertFalse(manager.isInteractiveModeSupported(),
        "HeadlessBoardManager must report isInteractiveModeSupported() == false");
  }

  @Test
  void headlessManager_getInteractiveSettings_returnsNull() throws FileNotFoundException {
    var manager = new HeadlessBoardManager(new RoutingJob());
    manager.loadFromSpecctraDsn(
        new FileInputStream("fixtures/empty_board.dsn"),
        new BoardObserverAdaptor(),
        new ItemIdentificationNumberGenerator());

    assertNull(manager.getInteractiveSettings(),
        "HeadlessBoardManager.getInteractiveSettings() must return null");
  }

  @Test
  void headlessManager_deprecated_getSettings_alsoReturnsNull() throws FileNotFoundException {
    var manager = new HeadlessBoardManager(new RoutingJob());
    manager.loadFromSpecctraDsn(
        new FileInputStream("fixtures/empty_board.dsn"),
        new BoardObserverAdaptor(),
        new ItemIdentificationNumberGenerator());

    @SuppressWarnings("deprecation")
    InteractiveSettings settings = manager.get_settings();
    assertNull(settings,
        "Deprecated get_settings() must delegate to getInteractiveSettings() and return null in headless mode");
  }

  // ── GUI contract (static, no Swing needed) ────────────────────────────────

  @Test
  void guiBoardManager_overrides_isInteractiveModeSupported() throws Exception {
    // Verify at the method level without instantiating Swing.
    var method = GuiBoardManager.class.getMethod("isInteractiveModeSupported");
    // The method must be declared on GuiBoardManager itself, not inherited from the default.
    assertTrue(method.getDeclaringClass().equals(GuiBoardManager.class),
        "GuiBoardManager must override isInteractiveModeSupported()");
  }

  @Test
  void guiBoardManager_overrides_getInteractiveSettings() throws Exception {
    var method = GuiBoardManager.class.getMethod("getInteractiveSettings");
    assertTrue(method.getDeclaringClass().equals(GuiBoardManager.class),
        "GuiBoardManager must override getInteractiveSettings()");
  }
}


