package app.freerouting.gui.board;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.gui.a11y.A11y;
import app.freerouting.gui.a11y.GuiLocators;
import app.freerouting.gui.support.GuiTextManager;
import java.awt.Container;
import java.awt.Dimension;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JToolBar;

/** Describes the toolbar of the board frame, when it is in the inspected item state. */
public class BoardToolbarInspectedItem extends JToolBar {

  private final BoardFrame boardFrame;

  /** Creates a new instance of BoardToolbarInspectedItem. */
  public BoardToolbarInspectedItem(BoardFrame boardFrame) {
    this.boardFrame = boardFrame;

    setFloatable(false);
    setRollover(true);

    GuiTextManager tm = new GuiTextManager(this.getClass(), boardFrame.getLocale());

    addToolbarButton(
        tm,
        "cancel",
        GuiLocators.INSPECT_CANCEL,
        "toolbarCancelButton",
        () -> this.boardFrame.boardPanel.boardHandling.cancelState());

    addSeparator();

    addToolbarButton(
        tm,
        "info",
        GuiLocators.INSPECT_INFO,
        "toolbarInfoButton",
        () -> this.boardFrame.boardPanel.boardHandling.displaySelectedItemInfo());

    addSeparator();

    addToolbarButton(
        tm,
        "nets",
        GuiLocators.INSPECT_EXTEND_NETS,
        "toolbarWholeNetsButton",
        () -> this.boardFrame.boardPanel.boardHandling.extendSelectionToWholeNets());
    addToolbarButton(
        tm,
        "conn_sets",
        GuiLocators.INSPECT_EXTEND_CONNECTED_SETS,
        "toolbarWholeConnectedSetsButton",
        () -> this.boardFrame.boardPanel.boardHandling.extendSelectionToWholeConnectedSets());
    addToolbarButton(
        tm,
        "connections",
        GuiLocators.INSPECT_EXTEND_CONNECTIONS,
        "toolbarWholeConnectionsButton",
        () -> this.boardFrame.boardPanel.boardHandling.extendSelectionToWholeConnections());
    addToolbarButton(
        tm,
        "components",
        GuiLocators.INSPECT_EXTEND_COMPONENTS,
        "toolbarWholeGroupsButton",
        () -> this.boardFrame.boardPanel.boardHandling.extendSelectionToWholeComponents());

    addSeparator();

    addToolbarButton(
        tm,
        "violations",
        GuiLocators.INSPECT_VIOLATIONS,
        "toolbarViolationButton",
        () -> this.boardFrame.boardPanel.boardHandling.toggleSelectedItemViolations());

    addSeparator();

    addToolbarButton(
        tm,
        "zoom_selection",
        GuiLocators.INSPECT_ZOOM_SELECTION,
        "toolbarDisplaySelectionButton",
        () -> this.boardFrame.boardPanel.boardHandling.zoomSelection());
    addToolbarButton(
        tm,
        "zoom_all",
        GuiLocators.INSPECT_ZOOM_ALL,
        "toolbarDisplayAllButton",
        () -> this.boardFrame.zoomAll());
    addToolbarButton(
        tm,
        "zoom_region",
        GuiLocators.INSPECT_ZOOM_REGION,
        "toolbarDisplayRegionButton",
        () -> this.boardFrame.boardPanel.boardHandling.zoomRegion());
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
  public static JPanel createComponentOnly(Locale locale, Consumer<String> actionListener) {
    final Consumer<String> listener = actionListener == null ? _ -> {} : actionListener;
    GuiTextManager tm = new GuiTextManager(BoardToolbarInspectedItem.class, locale);
    JPanel toolbar = new JPanel();
    A11y.tag(toolbar, GuiLocators.TOOLBAR_ROOT);
    A11y.describe(toolbar, tm.getText("toolbar_accessible_name"), null);

    addComponentOnlyButton(toolbar, tm, "cancel", GuiLocators.INSPECT_CANCEL, listener);
    addComponentOnlyButton(toolbar, tm, "info", GuiLocators.INSPECT_INFO, listener);
    addComponentOnlyButton(toolbar, tm, "nets", GuiLocators.INSPECT_EXTEND_NETS, listener);
    addComponentOnlyButton(
        toolbar, tm, "conn_sets", GuiLocators.INSPECT_EXTEND_CONNECTED_SETS, listener);
    addComponentOnlyButton(
        toolbar, tm, "connections", GuiLocators.INSPECT_EXTEND_CONNECTIONS, listener);
    addComponentOnlyButton(
        toolbar, tm, "components", GuiLocators.INSPECT_EXTEND_COMPONENTS, listener);
    addComponentOnlyButton(toolbar, tm, "violations", GuiLocators.INSPECT_VIOLATIONS, listener);
    addComponentOnlyButton(
        toolbar, tm, "zoom_selection", GuiLocators.INSPECT_ZOOM_SELECTION, listener);
    addComponentOnlyButton(toolbar, tm, "zoom_all", GuiLocators.INSPECT_ZOOM_ALL, listener);
    addComponentOnlyButton(toolbar, tm, "zoom_region", GuiLocators.INSPECT_ZOOM_REGION, listener);
    return toolbar;
  }

  private void addToolbarButton(
      GuiTextManager tm, String textKey, String locator, String analyticsId, Runnable action) {
    JButton button = new JButton();
    tm.setText(button, textKey);
    tagInspectButton(button, locator);
    button.addActionListener(_ -> action.run());
    button.addActionListener(_ -> FRAnalytics.buttonClicked(analyticsId, button.getText()));
    this.add(button);
    // Small gap between buttons of the same group; group boundaries use the full separator.
    this.addSeparator(new Dimension(4, 1));
  }

  private static void addComponentOnlyButton(
      Container parent,
      GuiTextManager tm,
      String textKey,
      String locator,
      Consumer<String> listener) {
    JButton button = new JButton();
    tm.setText(button, textKey);
    tagInspectButton(button, locator);
    button.addActionListener(_ -> listener.accept(locator));
    parent.add(button);
  }

  private static void tagInspectButton(JButton button, String locator) {
    A11y.tag(button, locator);
    String accessibleName =
        button.getToolTipText() == null || button.getToolTipText().isBlank()
            ? button.getText()
            : button.getToolTipText();
    A11y.describe(button, accessibleName, button.getToolTipText());
  }
}
