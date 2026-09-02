package app.freerouting.gui.menus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.freerouting.gui.a11y.GuiLocators;
import app.freerouting.gui.board.BoardFrame;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.event.WindowEvent;
import java.util.Locale;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BoardMenuFileTest {

  @Test
  void exitMenuItemDispatchesWindowClosingEventToBoardFrame() {
    BoardFrame mockFrame = mock(BoardFrame.class);
    when(mockFrame.getLocale()).thenReturn(Locale.ENGLISH);

    BoardMenuFile menuFile = new BoardMenuFile(mockFrame);

    JMenuItem exitItem = null;
    for (Component comp : menuFile.getMenuComponents()) {
      if (comp instanceof JMenuItem item && GuiLocators.MENU_FILE_EXIT.equals(item.getName())) {
        exitItem = item;
        break;
      }
    }

    assertNotNull(exitItem, "Exit menu item should be present with MENU_FILE_EXIT locator");

    exitItem.doClick();

    ArgumentCaptor<AWTEvent> captor = ArgumentCaptor.forClass(AWTEvent.class);
    verify(mockFrame).dispatchEvent(captor.capture());
    assertEquals(WindowEvent.WINDOW_CLOSING, captor.getValue().getID());
    verify(mockFrame, never()).dispose();
  }
}
