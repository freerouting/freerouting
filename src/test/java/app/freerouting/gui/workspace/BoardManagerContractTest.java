package app.freerouting.gui.workspace;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.core.RoutingJob;
import app.freerouting.management.BoardManager;
import app.freerouting.management.HeadlessBoardManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the headless {@link BoardManager} / GUI-workspace {@link WorkspaceContract}
 * split (SoC plan Phase 3).
 *
 * <p>Verifies the architectural invariant:
 *
 * <ul>
 *   <li>The headless {@link BoardManager} interface exposes <em>no</em> workspace methods,
 *       including the former {@code getWorkspaceSettings()}, {@code isInteractiveModeSupported()},
 *       {@code getSettings()}, and {@code initializeManualTraceHalfWidths()} hooks.
 *   <li>{@link HeadlessBoardManager} does <em>not</em> implement {@link WorkspaceContract}, so
 *       {@link WorkspaceSettings} is unreachable in headless mode.
 *   <li>{@link GuiBoardManager} implements {@link WorkspaceContract}, providing the workspace
 *       accessor (never {@code null} after board initialisation).
 * </ul>
 */
class BoardManagerContractTest {

  @BeforeEach
  void setUp() {
    WorkspaceSettings.resetForTesting();
  }

  @AfterEach
  void tearDown() {
    WorkspaceSettings.resetForTesting();
  }

  // ── Headless contract ─────────────────────────────────────────────────────

  @Test
  void headlessBoardManagerInterfaceHasNoWorkspaceMethods() {
    // The headless BoardManager interface must not declare any workspace methods.
    for (String guiMethod :
        new String[] {
          "getWorkspaceSettings",
          "isInteractiveModeSupported",
          "getSettings",
          "initializeManualTraceHalfWidths"
        }) {
      boolean declared =
          java.util.Arrays.stream(BoardManager.class.getMethods())
              .anyMatch(m -> m.getName().equals(guiMethod));
      assertFalse(
          declared,
          "Headless BoardManager interface must not declare workspace method '" + guiMethod + "'");
    }
  }

  @Test
  void headlessManagerDoesNotImplementWorkspaceContract() {
    HeadlessBoardManager manager = new HeadlessBoardManager(new RoutingJob());
    assertFalse(
        manager instanceof WorkspaceContract,
        "HeadlessBoardManager must not implement WorkspaceContract; WorkspaceSettings must be "
            + "unreachable in headless mode");
  }

  // ── Workspace contract ────────────────────────────────────────────────────

  @Test
  void workspaceContractExposesWorkspaceSettings() throws Exception {
    // The workspace contract declares getWorkspaceSettings() returning WorkspaceSettings.
    var method = WorkspaceContract.class.getMethod("getWorkspaceSettings");
    assertTrue(
        method.getReturnType().equals(WorkspaceSettings.class),
        "WorkspaceContract.getWorkspaceSettings() must return WorkspaceSettings");
  }

  @Test
  void workspaceContractExposesInitializeManualTraceHalfWidths() throws Exception {
    // R10: manual trace half-width initialisation is a workspace-only operation.
    var method = WorkspaceContract.class.getMethod("initializeManualTraceHalfWidths");
    assertTrue(
        method.getDeclaringClass().equals(WorkspaceContract.class),
        "WorkspaceContract must declare initializeManualTraceHalfWidths()");
  }

  @Test
  void guiBoardManagerImplementsWorkspaceContract() {
    assertTrue(
        WorkspaceContract.class.isAssignableFrom(GuiBoardManager.class),
        "GuiBoardManager must implement WorkspaceContract");
  }

  @Test
  void guiBoardManagerDeclaresGetWorkspaceSettingsOverride() throws Exception {
    var method = GuiBoardManager.class.getMethod("getWorkspaceSettings");
    assertTrue(
        method.getDeclaringClass().equals(GuiBoardManager.class),
        "GuiBoardManager must override getWorkspaceSettings()");
  }
}
