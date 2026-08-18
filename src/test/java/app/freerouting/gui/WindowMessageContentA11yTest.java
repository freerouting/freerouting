package app.freerouting.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.gui.a11y.GuiA11yHarness;
import app.freerouting.gui.a11y.GuiLocators;
import app.freerouting.gui.windows.board.WindowMessage;
import java.awt.Component;
import javax.accessibility.AccessibleRole;
import javax.swing.JPanel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Accessibility coverage for WindowMessage's extracted content seam.
 *
 * <p>The tests deliberately do not call {@link WindowMessage#show(String)}: JFrame construction is
 * not valid under the forced-headless GUI test task.
 */
@Tag("gui")
class WindowMessageContentA11yTest {

  @Test
  void englishMessageContentIsAccessibleWithoutCreatingTopLevelWindow() {
    JPanel content =
        GuiA11yHarness.onEdt(
            () -> WindowMessage.createContent(new String[] {"Loading board", "Please wait"}));

    GuiA11yHarness.onEdt(
        () -> {
          Component root =
              GuiA11yHarness.findByLocator(content, GuiLocators.WINDOW_MESSAGE_CONTENT);
          GuiA11yHarness.requireAccessibleName(root, GuiLocators.WINDOW_MESSAGE_CONTENT);
          requireMessageLabel(content, 0, "Loading board");
          requireMessageLabel(content, 1, "Please wait");
          GuiA11yHarness.requireUniqueSiblingNames(content);
          GuiA11yHarness.requireNoLeakedGuiResources();
        });
  }

  @Test
  void hungarianMessageContentKeepsStableLocatorsAndTranslatedNames() {
    JPanel content =
        GuiA11yHarness.onEdt(
            () -> WindowMessage.createContent(new String[] {"Terv betöltése", "Kérem, várjon"}));

    GuiA11yHarness.onEdt(
        () -> {
          requireMessageLabel(content, 0, "Terv betöltése");
          requireMessageLabel(content, 1, "Kérem, várjon");
          GuiA11yHarness.requireNoLeakedGuiResources();
        });
  }

  private static void requireMessageLabel(
      JPanel content, int index, String expectedAccessibleName) {
    String locator = GuiLocators.windowMessageLabel(index);
    Component label = GuiA11yHarness.findByLocator(content, locator);
    GuiA11yHarness.requireRole(label, locator, AccessibleRole.LABEL);
    assertEquals(expectedAccessibleName, GuiA11yHarness.accessibleName(label));
  }
}
