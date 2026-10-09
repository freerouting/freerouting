package app.freerouting.gui.board;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.gui.a11y.A11y;
import app.freerouting.gui.a11y.GuiLocators;
import app.freerouting.gui.support.GuiTextManager;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JToolBar;

/** Describes the toolbar of the board frame when it is in the inspected item state. */
public class BoardToolbarInspectedItem extends JToolBar {

  /** Creates a new instance of BoardToolbarInspectedItem for production frame. */
  public BoardToolbarInspectedItem(BoardFrame boardFrame) {
    this(
        boardFrame != null ? boardFrame.getLocale() : Locale.getDefault(),
        locator -> dispatchAction(boardFrame, locator));
  }

  /**
   * Internal constructor creating a configured inspect toolbar with the specified locale and action
   * dispatcher.
   */
  public BoardToolbarInspectedItem(Locale locale, Consumer<String> actionListener) {
    setFloatable(false);
    setRollover(true);

    GuiTextManager tm = new GuiTextManager(this.getClass(), locale);
    A11y.tag(this, GuiLocators.TOOLBAR_ROOT);
    A11y.describe(this, tm.getText("toolbar_accessible_name"), null);

    // Group 1: Cancel and Info
    addInspectButton(
        tm, "cancel", GuiLocators.INSPECT_CANCEL, "toolbarCancelButton", actionListener);
    addInspectButton(tm, "info", GuiLocators.INSPECT_INFO, "toolbarInfoButton", actionListener);

    addSeparator();

    // Group 2: Extend selection
    addInspectButton(
        tm, "nets", GuiLocators.INSPECT_EXTEND_NETS, "toolbarWholeNetsButton", actionListener);
    addInspectButton(
        tm,
        "conn_sets",
        GuiLocators.INSPECT_EXTEND_CONNECTED_SETS,
        "toolbarWholeConnectedSetsButton",
        actionListener);
    addInspectButton(
        tm,
        "connections",
        GuiLocators.INSPECT_EXTEND_CONNECTIONS,
        "toolbarWholeConnectionsButton",
        actionListener);
    addInspectButton(
        tm,
        "components",
        GuiLocators.INSPECT_EXTEND_COMPONENTS,
        "toolbarWholeGroupsButton",
        actionListener);

    addSeparator();

    // Group 3: Violations
    addInspectButton(
        tm, "violations", GuiLocators.INSPECT_VIOLATIONS, "toolbarViolationButton", actionListener);

    addSeparator();

    // Group 4: Zoom controls
    addInspectButton(
        tm,
        "zoom_selection",
        GuiLocators.INSPECT_ZOOM_SELECTION,
        "toolbarDisplaySelectionButton",
        actionListener);
    addInspectButton(
        tm, "zoom_all", GuiLocators.INSPECT_ZOOM_ALL, "toolbarDisplayAllButton", actionListener);
    addInspectButton(
        tm,
        "zoom_region",
        GuiLocators.INSPECT_ZOOM_REGION,
        "toolbarDisplayRegionButton",
        actionListener);
  }

  /**
   * Builds a component-only inspect toolbar for accessibility tests and headless embedders.
   *
   * <p>No board, frame, or session state is touched. Actions report their stable locator to the
   * supplied listener, which makes action paths observable without coupling a test to GUI session
   * state.
   *
   * @param locale locale for translated names and descriptions
   * @param actionListener receives the locator of an invoked control
   * @return a reusable inspect toolbar component
   */
  public static JToolBar createComponentOnly(Locale locale, Consumer<String> actionListener) {
    return new BoardToolbarInspectedItem(locale, actionListener);
  }

  private void addInspectButton(
      GuiTextManager tm,
      String textKey,
      String locator,
      String analyticsId,
      Consumer<String> actionListener) {
    JButton button = new JButton();
    tm.setText(button, textKey);
    tagInspectButton(button, locator);
    if (actionListener != null) {
      button.addActionListener(_ -> actionListener.accept(locator));
    }
    button.addActionListener(_ -> FRAnalytics.buttonClicked(analyticsId, button.getText()));
    this.add(button);
  }

  private static void tagInspectButton(JButton button, String locator) {
    A11y.tag(button, locator);
    String accessibleName =
        button.getToolTipText() == null || button.getToolTipText().isBlank()
            ? button.getText()
            : button.getToolTipText();
    A11y.describe(button, accessibleName, button.getToolTipText());
  }

  private static void dispatchAction(BoardFrame boardFrame, String locator) {
    if (boardFrame == null
        || boardFrame.boardPanel == null
        || boardFrame.boardPanel.boardHandling == null) {
      return;
    }
    switch (locator) {
      case GuiLocators.INSPECT_CANCEL -> boardFrame.boardPanel.boardHandling.cancelState();
      case GuiLocators.INSPECT_INFO ->
          boardFrame.boardPanel.boardHandling.displaySelectedItemInfo();
      case GuiLocators.INSPECT_EXTEND_NETS ->
          boardFrame.boardPanel.boardHandling.extendSelectionToWholeNets();
      case GuiLocators.INSPECT_EXTEND_CONNECTED_SETS ->
          boardFrame.boardPanel.boardHandling.extendSelectionToWholeConnectedSets();
      case GuiLocators.INSPECT_EXTEND_CONNECTIONS ->
          boardFrame.boardPanel.boardHandling.extendSelectionToWholeConnections();
      case GuiLocators.INSPECT_EXTEND_COMPONENTS ->
          boardFrame.boardPanel.boardHandling.extendSelectionToWholeComponents();
      case GuiLocators.INSPECT_VIOLATIONS ->
          boardFrame.boardPanel.boardHandling.toggleSelectedItemViolations();
      case GuiLocators.INSPECT_ZOOM_SELECTION ->
          boardFrame.boardPanel.boardHandling.zoomSelection();
      case GuiLocators.INSPECT_ZOOM_ALL -> boardFrame.zoomAll();
      case GuiLocators.INSPECT_ZOOM_REGION -> boardFrame.boardPanel.boardHandling.zoomRegion();
      default -> {}
    }
  }
}
