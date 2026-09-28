package app.freerouting.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.gui.a11y.A11y;
import app.freerouting.gui.a11y.GuiA11yHarness;
import app.freerouting.gui.a11y.GuiLocators;
import app.freerouting.gui.board.BoardFrame;
import app.freerouting.gui.menus.BoardMenuFile;
import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.accessibility.AccessibleRole;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * MVP a11y workflow #3 — menu action (component-only, forced headless).
 *
 * <p>The real {@link BoardMenuFile} is coupled to a {@link BoardFrame} (a {@code
 * javax.swing.JFrame}), which cannot be constructed in forced-headless mode. This test builds a
 * representative menu structure tagged with the standard {@link GuiLocators}, verifies
 * menu/menu-item roles and accessible names, and drives a menu item's accessible action through the
 * harness (D7/D8/D19). Wiring the real menus is forward-looking product work; a headless
 * construction seam is tracked for Phase 8/9.
 */
@Tag("gui")
class MenuActionA11yTest {

  /** The translated labels for the File menu, mirroring the {@code BoardMenuFile} bundle keys. */
  private record MenuLabels(String file, String open, String saveAs, String exit) {}

  private static final MenuLabels ENGLISH = new MenuLabels("File", "Open", "Save As", "Exit");
  private static final MenuLabels HUNGARIAN =
      new MenuLabels("Fájl", "Megnyitás...", "Mentés másként...", "Kilépés");

  private static Container buildFileMenu(MenuLabels labels, AtomicBoolean exitInvoked) {
    JMenu menu = new JMenu(labels.file());
    A11y.tag(menu, GuiLocators.MENU_FILE);
    A11y.describe(menu, labels.file(), null);

    JMenuItem open = new JMenuItem(labels.open());
    A11y.tag(open, GuiLocators.MENU_FILE_OPEN);
    A11y.describe(open, labels.open(), null);
    menu.add(open);

    JMenuItem saveAs = new JMenuItem(labels.saveAs());
    A11y.tag(saveAs, GuiLocators.MENU_FILE_SAVE_AS);
    A11y.describe(saveAs, labels.saveAs(), null);
    menu.add(saveAs);

    JMenuItem exit = new JMenuItem(labels.exit());
    A11y.tag(exit, GuiLocators.MENU_FILE_EXIT);
    A11y.describe(exit, labels.exit(), null);
    exit.addActionListener(_ -> exitInvoked.set(true));
    menu.add(exit);

    JMenuBar bar = new JMenuBar();
    bar.add(menu);
    return bar;
  }

  @Test
  void menuItemsResolveByLocatorAndInvokeAction() {
    AtomicBoolean exitInvoked = new AtomicBoolean(false);
    Container bar = GuiA11yHarness.onEdt(() -> buildFileMenu(ENGLISH, exitInvoked));

    GuiA11yHarness.onEdt(
        () -> {
          Component menu = GuiA11yHarness.findByLocator(bar, GuiLocators.MENU_FILE);
          GuiA11yHarness.requireRole(menu, GuiLocators.MENU_FILE, AccessibleRole.MENU);
          GuiA11yHarness.requireAccessibleName(menu, GuiLocators.MENU_FILE);

          Component open = GuiA11yHarness.findByLocator(bar, GuiLocators.MENU_FILE_OPEN);
          GuiA11yHarness.requireRole(open, GuiLocators.MENU_FILE_OPEN, AccessibleRole.MENU_ITEM);
          GuiA11yHarness.requireAccessibleName(open, GuiLocators.MENU_FILE_OPEN);

          Component exit = GuiA11yHarness.findByLocator(bar, GuiLocators.MENU_FILE_EXIT);
          GuiA11yHarness.requireRole(exit, GuiLocators.MENU_FILE_EXIT, AccessibleRole.MENU_ITEM);
          GuiA11yHarness.requireAccessibleName(exit, GuiLocators.MENU_FILE_EXIT);

          GuiA11yHarness.requireUniqueSiblingNames(bar);

          // Invoke the Exit item's accessible action and confirm its listener ran.
          GuiA11yHarness.invoke(exit, GuiLocators.MENU_FILE_EXIT);
          assertTrue(
              exitInvoked.get(),
              "invoking the menu item's accessible action should run its listener");
        });
  }

  @Test
  void menuAccessibleNamesAreTranslatedAcrossLocales() {
    Container enBar = GuiA11yHarness.onEdt(() -> buildFileMenu(ENGLISH, new AtomicBoolean(false)));
    Container huBar =
        GuiA11yHarness.onEdt(() -> buildFileMenu(HUNGARIAN, new AtomicBoolean(false)));

    GuiA11yHarness.onEdt(
        () -> {
          String enFile =
              GuiA11yHarness.accessibleName(
                  GuiA11yHarness.findByLocator(enBar, GuiLocators.MENU_FILE));
          String huFile =
              GuiA11yHarness.accessibleName(
                  GuiA11yHarness.findByLocator(huBar, GuiLocators.MENU_FILE));
          assertNotNull(enFile, "EN menu accessible name must be present");
          assertNotNull(huFile, "HU menu accessible name must be present");
          assertNotEquals(enFile, huFile, "menu accessible name must be translated (D19)");

          String enExit =
              GuiA11yHarness.accessibleName(
                  GuiA11yHarness.findByLocator(enBar, GuiLocators.MENU_FILE_EXIT));
          String huExit =
              GuiA11yHarness.accessibleName(
                  GuiA11yHarness.findByLocator(huBar, GuiLocators.MENU_FILE_EXIT));
          assertEquals("Exit", enExit, "EN exit item accessible name");
          assertEquals("Kilépés", huExit, "HU exit item accessible name (D19)");
        });
  }
}
