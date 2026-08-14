package app.freerouting.gui.session;

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
 * if (boardManager instanceof GuiSessionContract gui) {
 *     InteractiveSettings settings = gui.getInteractiveSettings(); // never null here
 * }
 * }</pre>
 *
 * <p>Splitting these methods off the shared {@code BoardManager} interface removes the previous
 * null-based GUI hooks ({@code getInteractiveSettings()} returning {@code null}) from the headless
 * API. {@link app.freerouting.management.HeadlessBoardManager} does not implement this contract, so
 * headless code can never reach {@link InteractiveSettings}.
 *
 * <p><strong>Note:</strong> this contract is the GUI-only board-management surface. The same
 * package also owns the Phase-9 session facade ({@code EditorStateHandle}/{@code EditorStateKind})
 * and lifecycle ports; concrete editor states remain in {@code gui.interactive}.
 */
public interface GuiSessionContract {

  /**
   * Returns the live GUI-session {@link InteractiveSettings} singleton.
   *
   * <p>The returned instance is also the {@link GuiSettingsSource} source (priority 65) registered
   * in the {@link app.freerouting.settings.SettingsMerger} pipeline. It is always non-null after a
   * board has been created or loaded in GUI mode. Callers must not cache it beyond the current
   * session; always obtain it through this accessor.
   *
   * @return the {@link InteractiveSettings} singleton; non-null after board initialisation
   */
  InteractiveSettings getInteractiveSettings();

  /**
   * Initialises manual trace half-widths from the board's default net-class rules (GUI-session
   * only, decision R10).
   *
   * <p>Copies the default trace width for each layer from the board's default net class into {@link
   * InteractiveSettings#manualTraceHalfWidthArr}. Must be called after the board is created or
   * loaded. This is a GUI-session concern only; headless mode has no {@link InteractiveSettings}
   * and therefore no manual trace widths to initialise.
   *
   * @see InteractiveSettings#manualTraceHalfWidthArr
   * @see app.freerouting.rules.NetClass#getTraceHalfWidth(int)
   */
  void initializeManualTraceHalfWidths();
}
