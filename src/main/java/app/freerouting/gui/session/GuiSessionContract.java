package app.freerouting.gui.session;

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
 * null-based GUI hooks ({@code getInteractiveSettings()} returning {@code null}, {@code
 * isInteractiveModeSupported()}) from the headless API. {@link
 * app.freerouting.management.HeadlessBoardManager} does not implement this contract, so headless
 * code can never reach {@link InteractiveSettings}.
 *
 * <p><strong>Note:</strong> this is the Phase-3 GUI-session contract. It is distinct from the
 * Phase-9 {@code gui.session} facade ({@code EditorStateHandle}/{@code EditorStateKind}); see the
 * SoC plan (D12/D20, R19). The {@code gui.session} package and {@code EditorState*} names are
 * reserved for that later work.
 */
public interface GuiSessionContract {

  /**
   * Returns the live GUI-session {@link InteractiveSettings} singleton.
   *
   * <p>The returned instance is also the {@link app.freerouting.settings.sources.GuiSettings}
   * source (priority 50) registered in the {@link app.freerouting.settings.SettingsMerger}
   * pipeline. It is always non-null after a board has been created or loaded in GUI mode. Callers
   * must not cache it beyond the current session; always obtain it through this accessor.
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
