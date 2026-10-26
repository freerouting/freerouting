package app.freerouting.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.gui.a11y.GuiA11yHarness;
import app.freerouting.gui.a11y.GuiLocators;
import app.freerouting.gui.menus.BoardMenuBar;
import app.freerouting.gui.windows.board.WindowAbout;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.accessibility.AccessibleRole;
import javax.swing.JButton;
import javax.swing.JTabbedPane;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Accessibility and component behavior test for WindowAbout and Community Patrons tab. */
@Tag("gui")
class WindowAboutA11yTest {

  @Test
  void aboutWindowTabsAndPatronButtonAreAccessible() {
    AtomicBoolean patronClicked = new AtomicBoolean(false);
    JTabbedPane tabbedPane =
        GuiA11yHarness.onEdt(
            () ->
                WindowAbout.createComponentOnly(
                    Locale.ENGLISH, "2.5.0-test", () -> patronClicked.set(true)));

    GuiA11yHarness.onEdt(
        () -> {
          Component tabbedPaneComp =
              GuiA11yHarness.findByLocator(tabbedPane, GuiLocators.WINDOW_ABOUT_TABBED_PANE);
          assertNotNull(tabbedPaneComp);
          GuiA11yHarness.requireRole(
              tabbedPaneComp, GuiLocators.WINDOW_ABOUT_TABBED_PANE, AccessibleRole.PAGE_TAB_LIST);

          assertEquals(2, tabbedPane.getTabCount());
          assertEquals("About", tabbedPane.getTitleAt(0));
          assertEquals("Community Patrons", tabbedPane.getTitleAt(1));
          assertEquals(0, tabbedPane.getSelectedIndex());

          // Switch to Patrons tab
          tabbedPane.setSelectedIndex(1);
          assertEquals(1, tabbedPane.getSelectedIndex());

          Component patronBtn =
              GuiA11yHarness.findByLocator(tabbedPane, GuiLocators.WINDOW_ABOUT_PATRON_BUTTON);
          assertNotNull(patronBtn);
          GuiA11yHarness.requireRole(
              patronBtn, GuiLocators.WINDOW_ABOUT_PATRON_BUTTON, AccessibleRole.PUSH_BUTTON);

          ((JButton) patronBtn).doClick();
          assertTrue(patronClicked.get());

          // Switch back to About tab
          tabbedPane.setSelectedIndex(0);
          assertEquals(0, tabbedPane.getSelectedIndex());

          GuiA11yHarness.requireNoLeakedGuiResources();
        });
  }

  @Test
  void helpMenuContainsPatronsItemWithStableLocator() {
    List<String> actions = new ArrayList<>();
    Container menuBar =
        GuiA11yHarness.onEdt(
            () -> BoardMenuBar.createComponentOnly(Locale.ENGLISH, false, actions::add));

    GuiA11yHarness.onEdt(
        () -> {
          Component patronsItem =
              GuiA11yHarness.findByLocator(menuBar, GuiLocators.MENU_HELP_PATRONS);
          assertNotNull(patronsItem);
          GuiA11yHarness.requireRole(
              patronsItem, GuiLocators.MENU_HELP_PATRONS, AccessibleRole.MENU_ITEM);
          GuiA11yHarness.requireAccessibleName(patronsItem, GuiLocators.MENU_HELP_PATRONS);

          GuiA11yHarness.invoke(patronsItem, GuiLocators.MENU_HELP_PATRONS);
          assertEquals(List.of(GuiLocators.MENU_HELP_PATRONS), actions);
          GuiA11yHarness.requireNoLeakedGuiResources();
        });
  }
}
