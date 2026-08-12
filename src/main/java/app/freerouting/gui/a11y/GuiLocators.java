package app.freerouting.gui.a11y;

/**
 * Stable, locale-independent locator constants for GUI components (decision D22).
 *
 * <p>Tests locate components by these constants (via {@link A11y#findByLocator}), never by
 * translated visible text, so the same test runs in English and Hungarian (D19). Locators are a
 * public test API: apply them to controls with {@link A11y#tag} and do not rename without updating
 * every referencing test.
 *
 * <p>Naming: {@code area.subject[.qualifier]}, dot-separated lower snake case.
 */
public final class GuiLocators {

  private GuiLocators() {}

  // ---- Status bar (BoardPanelStatus) ----
  /** Main status message line. */
  public static final String STATUS_MESSAGE = "status.message";

  /** Secondary (additional) status message. */
  public static final String STATUS_ADDITIONAL_MESSAGE = "status.message.additional";

  /** Error count label. */
  public static final String STATUS_ERROR_COUNT = "status.error.count";

  /** Warning count label. */
  public static final String STATUS_WARNING_COUNT = "status.warning.count";

  /** Current routing layer indicator. */
  public static final String STATUS_CURRENT_LAYER = "status.layer.current";

  /** Current board score indicator. */
  public static final String STATUS_BOARD_SCORE = "status.board.score";

  /** Mouse cursor board position indicator. */
  public static final String STATUS_MOUSE_POSITION = "status.mouse.position";

  /** Current measurement unit indicator. */
  public static final String STATUS_UNIT = "status.unit";

  // ---- Toolbar ----
  /** Layer-selection combo box. */
  public static final String TOOLBAR_LAYER_SELECT = "toolbar.layer.select";

  // ---- Menus (wired in Phase 2 part B; declared now for a complete registry) ----
  public static final String MENU_FILE = "menu.file";
  public static final String MENU_FILE_OPEN = "menu.file.open";
  public static final String MENU_FILE_SAVE_AS = "menu.file.save_as";
  public static final String MENU_FILE_EXIT = "menu.file.exit";
  public static final String MENU_DISPLAY = "menu.display";
  public static final String MENU_PARAMETER = "menu.parameter";
  public static final String MENU_RULES = "menu.rules";
  public static final String MENU_INFO = "menu.info";
  public static final String MENU_HELP = "menu.help";

  // ---- Inspect / list windows (Phase 5): ratsnest (incompletes) + violations lists ----
  /** Clearance-violations list (WindowClearanceViolations). */
  public static final String INSPECT_CLEARANCE_VIOLATIONS = "inspect.clearance_violations";

  /** Incomplete-connections / ratsnest list (WindowIncompletes). */
  public static final String INSPECT_INCOMPLETES = "inspect.incompletes";

  /** Length-violations list (WindowLengthViolations). */
  public static final String INSPECT_LENGTH_VIOLATIONS = "inspect.length_violations";
}
