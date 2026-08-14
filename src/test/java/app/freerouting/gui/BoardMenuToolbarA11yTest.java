package app.freerouting.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.gui.a11y.GuiA11yHarness;
import app.freerouting.gui.a11y.GuiLocators;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.accessibility.AccessibleRole;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Phase 11 coverage for the component-only board menu and toolbar seams. */
@Tag("gui")
class BoardMenuToolbarA11yTest {

  @Test
  void menuActionsHaveStableLocatorsAndKeyboardAlternatives() {
    List<String> actions = new ArrayList<>();
    Container menuBar =
        GuiA11yHarness.onEdt(
            () -> BoardMenuBar.createComponentOnly(Locale.ENGLISH, false, actions::add));

    GuiA11yHarness.onEdt(
        () -> {
          GuiA11yHarness.requireRole(
              GuiA11yHarness.findByLocator(menuBar, GuiLocators.MENU_FILE),
              GuiLocators.MENU_FILE,
              AccessibleRole.MENU);
          GuiA11yHarness.requireRole(
              GuiA11yHarness.findByLocator(menuBar, GuiLocators.MENU_DISPLAY),
              GuiLocators.MENU_DISPLAY,
              AccessibleRole.MENU);
          GuiA11yHarness.requireRole(
              GuiA11yHarness.findByLocator(menuBar, GuiLocators.MENU_PARAMETER),
              GuiLocators.MENU_PARAMETER,
              AccessibleRole.MENU);
          GuiA11yHarness.requireRole(
              GuiA11yHarness.findByLocator(menuBar, GuiLocators.MENU_RULES),
              GuiLocators.MENU_RULES,
              AccessibleRole.MENU);
          GuiA11yHarness.requireRole(
              GuiA11yHarness.findByLocator(menuBar, GuiLocators.MENU_INFO),
              GuiLocators.MENU_INFO,
              AccessibleRole.MENU);
          GuiA11yHarness.requireRole(
              GuiA11yHarness.findByLocator(menuBar, GuiLocators.MENU_HELP),
              GuiLocators.MENU_HELP,
              AccessibleRole.MENU);

          Component open = GuiA11yHarness.findByLocator(menuBar, GuiLocators.MENU_FILE_OPEN);
          GuiA11yHarness.requireRole(open, GuiLocators.MENU_FILE_OPEN, AccessibleRole.MENU_ITEM);
          GuiA11yHarness.requireAccessibleName(open, GuiLocators.MENU_FILE_OPEN);
          assertEquals(
              KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK),
              ((JMenuItem) open).getAccelerator());

          Component visibility =
              GuiA11yHarness.findByLocator(menuBar, GuiLocators.MENU_DISPLAY_VISIBILITY);
          GuiA11yHarness.invoke(visibility, GuiLocators.MENU_DISPLAY_VISIBILITY);
          GuiA11yHarness.invoke(open, GuiLocators.MENU_FILE_OPEN);
          assertEquals(
              List.of(GuiLocators.MENU_DISPLAY_VISIBILITY, GuiLocators.MENU_FILE_OPEN), actions);
          GuiA11yHarness.requireUniqueSiblingNames(menuBar);
          GuiA11yHarness.requireNoLeakedGuiResources();
        });
  }

  @Test
  void menuLocatorsRemainStableWhenAccessibleNamesAreHungarian() {
    Container english =
        GuiA11yHarness.onEdt(() -> BoardMenuBar.createComponentOnly(Locale.ENGLISH, false, null));
    Container hungarian =
        GuiA11yHarness.onEdt(
            () -> BoardMenuBar.createComponentOnly(Locale.forLanguageTag("hu"), false, null));

    GuiA11yHarness.onEdt(
        () -> {
          String englishName =
              GuiA11yHarness.accessibleName(
                  GuiA11yHarness.findByLocator(english, GuiLocators.MENU_DISPLAY));
          String hungarianName =
              GuiA11yHarness.accessibleName(
                  GuiA11yHarness.findByLocator(hungarian, GuiLocators.MENU_DISPLAY));
          assertNotEquals(englishName, hungarianName);
          assertEquals(
              "Megjelenítés",
              hungarianName,
              "the translated name must not change the stable locator");
          GuiA11yHarness.findByLocator(hungarian, GuiLocators.MENU_FILE_OPEN);
          GuiA11yHarness.requireNoLeakedGuiResources();
        });
  }

  @Test
  void toolbarModeUnitActionsAndEnablementAreAccessible() {
    List<String> actions = new ArrayList<>();
    JPanel toolbar =
        GuiA11yHarness.onEdt(
            () -> BoardToolbar.createComponentOnly(Locale.ENGLISH, true, actions::add));

    GuiA11yHarness.onEdt(
        () -> {
          GuiA11yHarness.requireAccessibleName(
              GuiA11yHarness.findByLocator(toolbar, GuiLocators.TOOLBAR_ROOT),
              GuiLocators.TOOLBAR_ROOT);
          GuiA11yHarness.requireRole(
              GuiA11yHarness.findByLocator(toolbar, GuiLocators.TOOLBAR_MODE_ROUTE),
              GuiLocators.TOOLBAR_MODE_ROUTE,
              AccessibleRole.TOGGLE_BUTTON);
          GuiA11yHarness.requireRole(
              GuiA11yHarness.findByLocator(toolbar, GuiLocators.TOOLBAR_UNIT_MM),
              GuiLocators.TOOLBAR_UNIT_MM,
              AccessibleRole.TOGGLE_BUTTON);
          GuiA11yHarness.invoke(
              GuiA11yHarness.findByLocator(toolbar, GuiLocators.TOOLBAR_MODE_DRAG),
              GuiLocators.TOOLBAR_MODE_DRAG);
          GuiA11yHarness.invoke(
              GuiA11yHarness.findByLocator(toolbar, GuiLocators.TOOLBAR_UNIT_MM),
              GuiLocators.TOOLBAR_UNIT_MM);
          assertTrue(actions.contains("drag_button"));
          assertTrue(actions.contains("unit_mm"));

          BoardToolbar.setComponentOnlyEnabled(toolbar, false);
          GuiA11yHarness.requireDisabled(
              GuiA11yHarness.findByLocator(toolbar, GuiLocators.TOOLBAR_MODE_ROUTE),
              GuiLocators.TOOLBAR_MODE_ROUTE);
          GuiA11yHarness.requireEnabled(
              GuiA11yHarness.findByLocator(toolbar, GuiLocators.TOOLBAR_CANCEL),
              GuiLocators.TOOLBAR_CANCEL);

          BoardToolbar.setComponentOnlyEnabled(toolbar, true);
          GuiA11yHarness.requireEnabled(
              GuiA11yHarness.findByLocator(toolbar, GuiLocators.TOOLBAR_MODE_ROUTE),
              GuiLocators.TOOLBAR_MODE_ROUTE);
          GuiA11yHarness.requireDisabled(
              GuiA11yHarness.findByLocator(toolbar, GuiLocators.TOOLBAR_CANCEL),
              GuiLocators.TOOLBAR_CANCEL);
          GuiA11yHarness.requireUniqueSiblingNames(toolbar);
          GuiA11yHarness.requireNoLeakedGuiResources();
        });
  }
}
