package app.freerouting.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.model.structure.Unit;
import app.freerouting.gui.a11y.GuiA11yHarness;
import app.freerouting.gui.a11y.GuiLocators;
import app.freerouting.gui.windows.board.WindowRoutingSummary;
import app.freerouting.gui.workspace.progress.RoutingSummaryData;
import app.freerouting.settings.GlobalSettings;
import java.awt.Component;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.accessibility.AccessibleRole;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Accessibility and component behavior test for WindowRoutingSummary. */
@Tag("gui")
class WindowRoutingSummaryA11yTest {

  @Test
  void englishRoutingSummaryPanelIsAccessible() {
    GlobalSettings settings = new GlobalSettings();
    RoutingSummaryData data =
        new RoutingSummaryData(24, 0, 0, 0.0, 12, 142.5, Unit.MM, 8.4, 998.5f, false);
    AtomicBoolean closed = new AtomicBoolean(false);

    JPanel panel =
        GuiA11yHarness.onEdt(
            () ->
                WindowRoutingSummary.createPanel(
                    data, settings, Locale.ENGLISH, () -> closed.set(true)));

    GuiA11yHarness.onEdt(
        () -> {
          Component root = GuiA11yHarness.findByLocator(panel, GuiLocators.ROUTING_SUMMARY_DIALOG);
          assertNotNull(root);
          GuiA11yHarness.requireAccessibleName(root, GuiLocators.ROUTING_SUMMARY_DIALOG);

          Component donateBtn =
              GuiA11yHarness.findByLocator(panel, GuiLocators.ROUTING_SUMMARY_DONATE_BUTTON);
          GuiA11yHarness.requireRole(
              donateBtn, GuiLocators.ROUTING_SUMMARY_DONATE_BUTTON, AccessibleRole.PUSH_BUTTON);

          Component closeBtn =
              GuiA11yHarness.findByLocator(panel, GuiLocators.ROUTING_SUMMARY_CLOSE_BUTTON);
          GuiA11yHarness.requireRole(
              closeBtn, GuiLocators.ROUTING_SUMMARY_CLOSE_BUTTON, AccessibleRole.PUSH_BUTTON);

          Component showCheckbox =
              GuiA11yHarness.findByLocator(panel, GuiLocators.ROUTING_SUMMARY_SHOW_CHECKBOX);
          GuiA11yHarness.requireRole(
              showCheckbox, GuiLocators.ROUTING_SUMMARY_SHOW_CHECKBOX, AccessibleRole.CHECK_BOX);
          assertTrue(((JCheckBox) showCheckbox).isSelected());

          ((JButton) closeBtn).doClick();
          assertTrue(closed.get());

          GuiA11yHarness.requireNoLeakedGuiResources();
        });
  }

  @Test
  void hungarianRoutingSummaryPanelKeepsStableLocators() {
    GlobalSettings settings = new GlobalSettings();
    RoutingSummaryData data =
        new RoutingSummaryData(10, 2, 1, 0.15, 5, 55.2, Unit.MM, 3.1, 950.0f, true);

    JPanel panel =
        GuiA11yHarness.onEdt(
            () -> WindowRoutingSummary.createPanel(data, settings, Locale.of("hu"), () -> {}));

    GuiA11yHarness.onEdt(
        () -> {
          Component root = GuiA11yHarness.findByLocator(panel, GuiLocators.ROUTING_SUMMARY_DIALOG);
          assertNotNull(root);
          assertEquals("Huzalozási összefoglaló", GuiA11yHarness.accessibleName(root));
          GuiA11yHarness.requireNoLeakedGuiResources();
        });
  }
}
