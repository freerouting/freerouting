package app.freerouting.gui.menus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.freerouting.gui.a11y.GuiLocators;
import app.freerouting.gui.board.BoardFrame;
import app.freerouting.gui.board.BoardPanel;
import app.freerouting.gui.workspace.GuiBoardManager;
import java.awt.Component;
import java.util.Locale;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("gui")
class PopupMenuInspectedItemsTest {

  @Test
  void popupContainsExtendItemsAndDispatchesActions() {
    BoardFrame mockFrame = mock(BoardFrame.class);
    BoardPanel mockPanel = mock(BoardPanel.class);
    GuiBoardManager mockHandling = mock(GuiBoardManager.class);
    mockFrame.boardPanel = mockPanel;
    mockPanel.boardHandling = mockHandling;
    when(mockFrame.getLocale()).thenReturn(Locale.ENGLISH);

    PopupMenuInspectedItems popup = new PopupMenuInspectedItems(mockFrame);

    // Verify section header exists
    Component firstComponent = popup.getComponent(0);
    assertTrue(
        firstComponent instanceof JLabel, "First component should be the section header label");
    assertEquals("Extend selection to:", ((JLabel) firstComponent).getText());

    // Verify extend items and actions
    JMenuItem netsItem = findItemByLocator(popup, GuiLocators.INSPECT_EXTEND_NETS);
    assertNotNull(netsItem, "Nets item should be present");
    netsItem.doClick();
    verify(mockHandling).extendSelectionToWholeNets();

    JMenuItem connSetsItem = findItemByLocator(popup, GuiLocators.INSPECT_EXTEND_CONNECTED_SETS);
    assertNotNull(connSetsItem, "Connected sets item should be present");
    connSetsItem.doClick();
    verify(mockHandling).extendSelectionToWholeConnectedSets();

    JMenuItem connectionsItem = findItemByLocator(popup, GuiLocators.INSPECT_EXTEND_CONNECTIONS);
    assertNotNull(connectionsItem, "Connections item should be present");
    connectionsItem.doClick();
    verify(mockHandling).extendSelectionToWholeConnections();

    JMenuItem componentsItem = findItemByLocator(popup, GuiLocators.INSPECT_EXTEND_COMPONENTS);
    assertNotNull(componentsItem, "Components item should be present");
    componentsItem.doClick();
    verify(mockHandling).extendSelectionToWholeComponents();

    // Verify separator exists at the boundary
    Component separatorComp = popup.getComponent(5);
    assertTrue(
        separatorComp instanceof JSeparator, "Separator should be inserted after extend items");
  }

  @Test
  void popupTranslatesHeaderInGerman() {
    BoardFrame mockFrame = mock(BoardFrame.class);
    BoardPanel mockPanel = mock(BoardPanel.class);
    mockFrame.boardPanel = mockPanel;
    when(mockFrame.getLocale()).thenReturn(Locale.GERMAN);

    PopupMenuInspectedItems popup = new PopupMenuInspectedItems(mockFrame);

    Component firstComponent = popup.getComponent(0);
    assertTrue(
        firstComponent instanceof JLabel, "First component should be the section header label");
    assertEquals("Auswahl erweitern auf:", ((JLabel) firstComponent).getText());
  }

  private static JMenuItem findItemByLocator(PopupMenuInspectedItems popup, String locator) {
    for (Component comp : popup.getComponents()) {
      if (comp instanceof JMenuItem item && locator.equals(item.getName())) {
        return item;
      }
    }
    return null;
  }
}
