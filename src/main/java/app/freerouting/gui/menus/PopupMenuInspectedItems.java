package app.freerouting.gui.menus;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.gui.a11y.A11y;
import app.freerouting.gui.a11y.GuiLocators;
import app.freerouting.gui.board.BoardFrame;
import app.freerouting.gui.board.BoardToolbarInspectedItem;
import app.freerouting.util.TextManager;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

/** Popup menu used in the interactive inspected item state. */
public class PopupMenuInspectedItems extends PopupMenuDisplay {

  /** Creates a new instance of PopupMenuInspectedItems. */
  public PopupMenuInspectedItems(BoardFrame boardFrame) {
    super(boardFrame);

    // Reuse the inspect-toolbar bundle so popup labels cannot drift from toolbar labels.
    TextManager tm = new TextManager(BoardToolbarInspectedItem.class, boardFrame.getLocale());

    // Flat extend items (no submenu): one targeting operation per action. A non-interactive
    // section label provides the verb frame ("these extend your selection"); a separator
    // inserted at the group boundary (NOT appended - addSeparator() would land after the zoom
    // submenu) separates it from the inherited display items added by the superclass.
    // The label is skipped entirely when the locale has no translation, so the raw key never
    // shows in the menu.
    String headerText = tm.getText("extend_selection");
    Component extendHeader =
        "extend_selection".equals(headerText) ? null : createSectionLabel(headerText);
    int baseIndex = extendHeader == null ? 0 : 1;
    if (extendHeader != null) {
      this.add(extendHeader, 0);
    }
    addExtendItem(
        baseIndex++,
        tm,
        "nets",
        GuiLocators.INSPECT_EXTEND_NETS,
        "toolbarWholeNetsButton",
        () -> boardPanel.boardHandling.extendSelectionToWholeNets());
    addExtendItem(
        baseIndex++,
        tm,
        "conn_sets",
        GuiLocators.INSPECT_EXTEND_CONNECTED_SETS,
        "toolbarWholeConnectedSetsButton",
        () -> boardPanel.boardHandling.extendSelectionToWholeConnectedSets());
    addExtendItem(
        baseIndex++,
        tm,
        "connections",
        GuiLocators.INSPECT_EXTEND_CONNECTIONS,
        "toolbarWholeConnectionsButton",
        () -> boardPanel.boardHandling.extendSelectionToWholeConnections());
    addExtendItem(
        baseIndex++,
        tm,
        "components",
        GuiLocators.INSPECT_EXTEND_COMPONENTS,
        "toolbarWholeGroupsButton",
        () -> boardPanel.boardHandling.extendSelectionToWholeComponents());

    this.insert(new JSeparator(), baseIndex);
    clampHeaderToItemWidth(extendHeader);
    compactVerticalSpacing();
  }

  /**
   * Tightens row height across the whole popup (own items and the inherited display rows) so the
   * extended group and the display group stay visually uniform. LAF default margins read loose at
   * scaled font sizes.
   */
  private void compactVerticalSpacing() {
    Insets compact = new Insets(1, 2, 1, 2);
    for (Component child : this.getComponents()) {
      if (child instanceof AbstractButton button) {
        button.setMargin(compact);
      }
    }
  }

  /**
   * Caps the section label's preferred width at the widest menu row so the label can never drive
   * the popup's width; JPopupMenu sizes itself from the maximum child preferred width.
   */
  private static void clampHeaderToItemWidth(Component header) {
    if (header == null || !(header.getParent() instanceof JPopupMenu popup)) {
      return;
    }
    int maxRowWidth = 0;
    for (Component child : popup.getComponents()) {
      if (child != header && !(child instanceof JSeparator)) {
        maxRowWidth = Math.max(maxRowWidth, child.getPreferredSize().width);
      }
    }
    Dimension headerPref = header.getPreferredSize();
    if (maxRowWidth > 0 && headerPref.width > maxRowWidth) {
      header.setPreferredSize(new Dimension(maxRowWidth, headerPref.height));
    }
  }

  /**
   * Builds a non-interactive section heading for a popup menu group: small bold muted label, not
   * focusable, with padding so it floats above the group instead of reading as a disabled menu
   * item.
   */
  private static Component createSectionLabel(String text) {
    JLabel header = new JLabel(text);
    Font itemFont = UIManager.getFont("MenuItem.font");
    if (itemFont != null) {
      header.setFont(itemFont.deriveFont(Font.BOLD, itemFont.getSize2D() * 0.95f));
    } else {
      header.setFont(header.getFont().deriveFont(Font.BOLD));
    }
    Color foreground = UIManager.getColor("MenuItem.disabledForeground");
    if (foreground == null) {
      foreground = UIManager.getColor("Label.disabledForeground");
    }
    if (foreground != null) {
      header.setForeground(foreground);
    }
    header.setFocusable(false);
    header.setHorizontalAlignment(SwingConstants.LEFT);
    header.setBorder(new EmptyBorder(2, 14, 1, 12));
    A11y.describe(header, text, null);
    return header;
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
