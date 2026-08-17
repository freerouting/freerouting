package app.freerouting.gui.workspace;

import app.freerouting.settings.sources.GuiSettingsSource;

/**
 * GUI-session contract for board management (SoC plan Phase 3).
 *
 * <p>This interface holds the GUI-only operations that the headless {@link
 * app.freerouting.management.BoardManager} contract intentionally does <em>not</em> expose. It is
 * implemented only by {@link GuiBoardManager} and is the way a caller can tell, at the type level,
 * that a board manager has an active GUI session:
 *
 * <pre>{@code
 * if (boardManager instanceof WorkspaceContract gui) {
 *     WorkspaceSettings settings = gui.getWorkspaceSettings(); // never null here
 * }
 * }</pre>
 *
 * <p>Splitting these methods off the shared {@code BoardManager} interface removes the previous
 * null-based GUI hooks ({@code getWorkspaceSettings()} returning {@code null}) from the headless
 * API. {@link app.freerouting.management.HeadlessBoardManager} does not implement this contract, so
 * headless code can never reach {@link WorkspaceSettings}.
 *
 * <p><strong>Note:</strong> this contract is the GUI-only board-management surface. The same
 * package also owns the Phase-9 session facade ({@code EditorStateHandle}/{@code EditorStateKind})
 * and lifecycle ports; concrete editor states remain in {@code gui.interactive}.
 */
public interface WorkspaceContract {

  /**
   * Returns the live GUI-session {@link WorkspaceSettings} singleton.
   *
   * <p>The returned instance is also the {@link GuiSettingsSource} source (priority 65) registered
   * in the {@link app.freerouting.settings.SettingsMerger} pipeline. It is always non-null after a
   * board has been created or loaded in GUI mode. Callers must not cache it beyond the current
   * session; always obtain it through this accessor.
   *
   * @return the {@link WorkspaceSettings} singleton; non-null after board initialisation
   */
  WorkspaceSettings getWorkspaceSettings();

  /**
   * Initialises manual trace half-widths from the board's default net-class rules (GUI-session
   * only, decision R10).
   *
   * <p>Copies the default trace width for each layer from the board's default net class into {@link
   * WorkspaceSettings#manualTraceHalfWidthArr}. Must be called after the board is created or
   * loaded. This is a GUI-session concern only; headless mode has no {@link WorkspaceSettings} and
   * therefore no manual trace widths to initialise.
   *
   * @see WorkspaceSettings#manualTraceHalfWidthArr
   * @see app.freerouting.rules.NetClass#getTraceHalfWidth(int)
   */
  void initializeManualTraceHalfWidths();
}
