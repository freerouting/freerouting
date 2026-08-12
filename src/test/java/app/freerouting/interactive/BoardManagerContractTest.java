package app.freerouting.interactive;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.core.RoutingJob;
import app.freerouting.management.BoardManager;
import app.freerouting.management.HeadlessBoardManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the headless {@link BoardManager} / GUI-session {@link GuiSessionContract}
 * split (SoC plan Phase 3).
 *
 * <p>Verifies the architectural invariant:
 *
 * <ul>
 *   <li>The headless {@link BoardManager} interface exposes <em>no</em> GUI-session methods (no
 *       null-based {@code getInteractiveSettings()} / {@code isInteractiveModeSupported()} / {@code
 *       initializeManualTraceHalfWidths()}).
 *   <li>{@link HeadlessBoardManager} does <em>not</em> implement {@link GuiSessionContract}, so
 *       {@link InteractiveSettings} is unreachable in headless mode.
 *   <li>{@link GuiBoardManager} implements {@link GuiSessionContract}, providing the GUI-session
 *       accessor (never {@code null} after board initialisation).
 * </ul>
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
  void headlessBoardManagerInterfaceHasNoGuiSessionMethods() {
    // The headless BoardManager interface must not declare any GUI-session methods.
    for (String guiMethod :
        new String[] {
          "getInteractiveSettings",
          "isInteractiveModeSupported",
          "getSettings",
          "initializeManualTraceHalfWidths"
        }) {
      boolean declared =
          java.util.Arrays.stream(BoardManager.class.getMethods())
              .anyMatch(m -> m.getName().equals(guiMethod));
      assertFalse(
          declared,
          "Headless BoardManager interface must not declare GUI-session method '"
              + guiMethod
              + "'");
    }
  }

  @Test
  void headlessManagerDoesNotImplementGuiSessionContract() {
    HeadlessBoardManager manager = new HeadlessBoardManager(new RoutingJob());
    assertFalse(
        manager instanceof GuiSessionContract,
        "HeadlessBoardManager must not implement GuiSessionContract; InteractiveSettings must be "
            + "unreachable in headless mode");
  }

  // ── GUI-session contract ──────────────────────────────────────────────────

  @Test
  void guiSessionContractExposesInteractiveSettings() throws Exception {
    // The GUI-session contract declares getInteractiveSettings() returning InteractiveSettings.
    var method = GuiSessionContract.class.getMethod("getInteractiveSettings");
    assertTrue(
        method.getReturnType().equals(InteractiveSettings.class),
        "GuiSessionContract.getInteractiveSettings() must return InteractiveSettings");
  }

  @Test
  void guiSessionContractExposesInitializeManualTraceHalfWidths() throws Exception {
    // R10: manual trace half-width initialisation is a GUI-session-only operation.
    var method = GuiSessionContract.class.getMethod("initializeManualTraceHalfWidths");
    assertTrue(
        method.getDeclaringClass().equals(GuiSessionContract.class),
        "GuiSessionContract must declare initializeManualTraceHalfWidths()");
  }

  @Test
  void guiBoardManagerImplementsGuiSessionContract() {
    assertTrue(
        GuiSessionContract.class.isAssignableFrom(GuiBoardManager.class),
        "GuiBoardManager must implement GuiSessionContract");
  }

  @Test
  void guiBoardManagerDeclaresGetInteractiveSettingsOverride() throws Exception {
    var method = GuiBoardManager.class.getMethod("getInteractiveSettings");
    assertTrue(
        method.getDeclaringClass().equals(GuiBoardManager.class),
        "GuiBoardManager must override getInteractiveSettings()");
  }
}
