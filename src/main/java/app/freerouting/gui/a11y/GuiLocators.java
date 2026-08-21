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
  /** Root panel for the component-only toolbar seam. */
  public static final String TOOLBAR_ROOT = "toolbar.root";

  /** Layer-selection combo box. */
  public static final String TOOLBAR_LAYER_SELECT = "toolbar.layer.select";

  /** Mode-selection segmented button group. */
  public static final String TOOLBAR_MODE_SELECT = "toolbar.mode.select";

  /** Individual mode buttons. */
  public static final String TOOLBAR_MODE_INSPECT = "toolbar.mode.inspect";

  public static final String TOOLBAR_MODE_ROUTE = "toolbar.mode.route";
  public static final String TOOLBAR_MODE_DRAG = "toolbar.mode.drag";

  /** Unit-selection segmented button group. */
  public static final String TOOLBAR_UNIT_SELECT = "toolbar.unit.select";

  /** Individual unit buttons. */
  public static final String TOOLBAR_UNIT_MIL = "toolbar.unit.mil";

  public static final String TOOLBAR_UNIT_INCH = "toolbar.unit.inch";
  public static final String TOOLBAR_UNIT_MM = "toolbar.unit.mm";
  public static final String TOOLBAR_UNIT_UM = "toolbar.unit.um";

  /** Common toolbar actions. */
  public static final String TOOLBAR_SETTINGS = "toolbar.settings";

  public static final String TOOLBAR_AUTOROUTE = "toolbar.autoroute";
  public static final String TOOLBAR_CANCEL = "toolbar.cancel";
  public static final String TOOLBAR_UNDO = "toolbar.undo";
  public static final String TOOLBAR_REDO = "toolbar.redo";
  public static final String TOOLBAR_INCOMPLETES = "toolbar.incompletes";
  public static final String TOOLBAR_VIOLATIONS = "toolbar.violations";
  public static final String TOOLBAR_DISPLAY_REGION = "toolbar.display.region";
  public static final String TOOLBAR_DISPLAY_ALL = "toolbar.display.all";
  public static final String TOOLBAR_DELETE_TRACKS = "toolbar.delete_tracks";

  // ---- Menus (wired in Phase 2 part B; declared now for a complete registry) ----
  public static final String MENU_BAR = "menu.bar";
  public static final String MENU_FILE = "menu.file";
  public static final String MENU_FILE_OPEN = "menu.file.open";
  public static final String MENU_FILE_SAVE_AS = "menu.file.save_as";
  public static final String MENU_FILE_EXIT = "menu.file.exit";
  public static final String MENU_DISPLAY = "menu.display";
  public static final String MENU_DISPLAY_VISIBILITY = "menu.display.visibility";
  public static final String MENU_DISPLAY_COLORS = "menu.display.colors";
  public static final String MENU_DISPLAY_MISCELLANEOUS = "menu.display.miscellaneous";
  public static final String MENU_PARAMETER = "menu.parameter";
  public static final String MENU_PARAMETER_SELECT = "menu.parameter.select";
  public static final String MENU_PARAMETER_ROUTE = "menu.parameter.route";
  public static final String MENU_PARAMETER_AUTOROUTE = "menu.parameter.autoroute";
  public static final String MENU_PARAMETER_MOVE = "menu.parameter.move";
  public static final String MENU_RULES = "menu.rules";
  public static final String MENU_RULES_CLEARANCE_MATRIX = "menu.rules.clearance_matrix";
  public static final String MENU_RULES_VIAS = "menu.rules.vias";
  public static final String MENU_RULES_NETS = "menu.rules.nets";
  public static final String MENU_RULES_NET_CLASSES = "menu.rules.net_classes";
  public static final String MENU_INFO = "menu.info";
  public static final String MENU_INFO_INCOMPLETES = "menu.info.incompletes";
  public static final String MENU_INFO_CLEARANCE_VIOLATIONS = "menu.info.clearance_violations";
  public static final String MENU_OTHER = "menu.other";
  public static final String MENU_HELP = "menu.help";
  public static final String MENU_HELP_ABOUT = "menu.help.about";
  public static final String MENU_HELP_SPONSOR = "menu.help.sponsor";
  public static final String MENU_PROFILE = "menu.profile";

  /** Component-only display/settings panel. */
  public static final String DISPLAY_SETTINGS = "display.settings";

  public static final String DISPLAY_LAYER_VISIBILITY = "display.layer.visibility";
  public static final String DISPLAY_OBJECT_VISIBILITY = "display.object.visibility";
  public static final String DISPLAY_RESET = "display.reset";

  // ---- Inspect / list windows (Phase 5): ratsnest (incompletes) + violations lists ----
  /** Clearance-violations list (WindowClearanceViolations). */
  public static final String INSPECT_CLEARANCE_VIOLATIONS = "inspect.clearance_violations";

  /** Incomplete-connections / ratsnest list (WindowIncompletes). */
  public static final String INSPECT_INCOMPLETES = "inspect.incompletes";

  /** Length-violations list (WindowLengthViolations). */
  public static final String INSPECT_LENGTH_VIOLATIONS = "inspect.length_violations";

  // ---- Progress and legacy window content ----
  /** Root panel for the reusable progress surface. */
  public static final String PROGRESS_ROOT = "progress.root";

  /** Current progress status text. */
  public static final String PROGRESS_STATUS = "progress.status";

  /** Current progress phase text. */
  public static final String PROGRESS_PHASE = "progress.phase";

  /** Completed/total counter text. */
  public static final String PROGRESS_COUNTERS = "progress.counters";

  /** Determinate or indeterminate progress bar. */
  public static final String PROGRESS_BAR = "progress.bar";

  /** Progress cancellation button. */
  public static final String PROGRESS_CANCEL = "progress.cancel";

  /** Root content panel extracted from WindowMessage. */
  public static final String WINDOW_MESSAGE_CONTENT = "window.message.content";

  /** Prefix used for labels in extracted WindowMessage content. */
  public static final String WINDOW_MESSAGE_LABEL_PREFIX = "window.message.label";

  // ---- Routing summary dialog ----
  public static final String ROUTING_SUMMARY_DIALOG = "dialog.routing_summary";
  public static final String ROUTING_SUMMARY_DONATE_BUTTON = "dialog.routing_summary.donate";
  public static final String ROUTING_SUMMARY_CLOSE_BUTTON = "dialog.routing_summary.close";
  public static final String ROUTING_SUMMARY_SHOW_CHECKBOX = "dialog.routing_summary.show_checkbox";

  /** Returns the stable locator for a message label at {@code index}. */
  public static String windowMessageLabel(int index) {
    if (index < 0) {
      throw new IllegalArgumentException("Message label index must not be negative");
    }
    return WINDOW_MESSAGE_LABEL_PREFIX + "." + index;
  }
}
