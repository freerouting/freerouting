package app.freerouting.gui.menus;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.gui.a11y.A11y;
import app.freerouting.gui.a11y.GuiLocators;
import app.freerouting.gui.board.BoardFrame;
import app.freerouting.gui.board.BoardToolbarInspectedItem;
import app.freerouting.util.TextManager;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;

/** Popup menu used in the interactive inspected item state. */
public class PopupMenuInspectedItems extends PopupMenuDisplay {

  /** Creates a new instance of PopupMenuInspectedItems. */
  public PopupMenuInspectedItems(BoardFrame boardFrame) {
    super(boardFrame);

    // Reuse the inspect-toolbar bundle so popup labels cannot drift from toolbar labels.
    TextManager tm = new TextManager(BoardToolbarInspectedItem.class, boardFrame.getLocale());

    // Flat extend items (no submenu): one targeting operation per action. A disabled header
    // item provides the verb frame ("these extend your selection"); a separator inserted at
    // the group boundary (NOT appended - addSeparator() would land after the zoom submenu)
    // separates it from the inherited display items added by the superclass.
    JMenuItem extendHeader = new JMenuItem(tm.getText("extend_selection"));
    extendHeader.setEnabled(false);
    A11y.describe(extendHeader, extendHeader.getText(), null);
    this.add(extendHeader, 0);

    addExtendItem(
        1,
        tm,
        "nets",
        GuiLocators.INSPECT_EXTEND_NETS,
        "toolbarWholeNetsButton",
        () -> boardPanel.boardHandling.extendSelectionToWholeNets());
    addExtendItem(
        2,
        tm,
        "conn_sets",
        GuiLocators.INSPECT_EXTEND_CONNECTED_SETS,
        "toolbarWholeConnectedSetsButton",
        () -> boardPanel.boardHandling.extendSelectionToWholeConnectedSets());
    addExtendItem(
        3,
        tm,
        "connections",
        GuiLocators.INSPECT_EXTEND_CONNECTIONS,
        "toolbarWholeConnectionsButton",
        () -> boardPanel.boardHandling.extendSelectionToWholeConnections());
    addExtendItem(
        4,
        tm,
        "components",
        GuiLocators.INSPECT_EXTEND_COMPONENTS,
        "toolbarWholeGroupsButton",
        () -> boardPanel.boardHandling.extendSelectionToWholeComponents());

    this.insert(new JSeparator(), 5);
  }

  private void addExtendItem(
      int index,
      TextManager tm,
      String textKey,
      String locator,
      String analyticsId,
      Runnable action) {
    JMenuItem item = new JMenuItem();
    item.setText(tm.getText(textKey));
    item.setToolTipText(tm.getText(textKey + "_tooltip"));
    A11y.tag(item, locator);
    A11y.describe(item, item.getToolTipText(), item.getToolTipText());
    item.addActionListener(_ -> action.run());
    item.addActionListener(_ -> FRAnalytics.buttonClicked(analyticsId, item.getText()));
    this.add(item, index);
  }
}
