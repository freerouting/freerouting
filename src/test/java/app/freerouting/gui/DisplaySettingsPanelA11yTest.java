package app.freerouting.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import app.freerouting.gui.a11y.GuiA11yHarness;
import app.freerouting.gui.a11y.GuiLocators;
import java.awt.Component;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.accessibility.AccessibleRole;
import javax.swing.JPanel;
import javax.swing.JSlider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Phase 11 coverage for reusable display/settings content without a top-level window. */
@Tag("gui")
class DisplaySettingsPanelA11yTest {

  @Test
  void visibilitySettingsExposeStateChangesAndResetAction() {
    Map<String, Integer> changes = new HashMap<>();
    JPanel panel =
        GuiA11yHarness.onEdt(
            () -> WindowVisibility.createComponentOnly(Locale.ENGLISH, changes::put));

    GuiA11yHarness.onEdt(
        () -> {
          Component layer =
              GuiA11yHarness.findByLocator(panel, GuiLocators.DISPLAY_LAYER_VISIBILITY);
          Component object =
              GuiA11yHarness.findByLocator(panel, GuiLocators.DISPLAY_OBJECT_VISIBILITY);
          GuiA11yHarness.requireRole(
              layer, GuiLocators.DISPLAY_LAYER_VISIBILITY, AccessibleRole.SLIDER);
          GuiA11yHarness.requireRole(
              object, GuiLocators.DISPLAY_OBJECT_VISIBILITY, AccessibleRole.SLIDER);
          GuiA11yHarness.requireAccessibleName(layer, GuiLocators.DISPLAY_LAYER_VISIBILITY);
          GuiA11yHarness.requireAccessibleName(object, GuiLocators.DISPLAY_OBJECT_VISIBILITY);
          GuiA11yHarness.requireEnabled(layer, GuiLocators.DISPLAY_LAYER_VISIBILITY);

          ((JSlider) layer).setValue(40);
          ((JSlider) object).setValue(20);
          assertEquals(40, changes.get("layer"));
          assertEquals(20, changes.get("object"));

          Component reset = GuiA11yHarness.findByLocator(panel, GuiLocators.DISPLAY_RESET);
          GuiA11yHarness.requireRole(reset, GuiLocators.DISPLAY_RESET, AccessibleRole.PUSH_BUTTON);
          GuiA11yHarness.invoke(reset, GuiLocators.DISPLAY_RESET);
          assertEquals(100, ((JSlider) layer).getValue());
          assertEquals(100, ((JSlider) object).getValue());
          GuiA11yHarness.requireUniqueSiblingNames(panel);
          GuiA11yHarness.requireNoLeakedGuiResources();
        });
  }

  @Test
  void visibilitySettingsKeepLocatorsAcrossEnglishAndHungarian() {
    JPanel english =
        GuiA11yHarness.onEdt(
            () -> WindowVisibility.createComponentOnly(Locale.ENGLISH, (_, _) -> {}));
    JPanel hungarian =
        GuiA11yHarness.onEdt(
            () -> WindowVisibility.createComponentOnly(Locale.forLanguageTag("hu"), (_, _) -> {}));

    GuiA11yHarness.onEdt(
        () -> {
          String englishName =
              GuiA11yHarness.accessibleName(
                  GuiA11yHarness.findByLocator(english, GuiLocators.DISPLAY_SETTINGS));
          String hungarianName =
              GuiA11yHarness.accessibleName(
                  GuiA11yHarness.findByLocator(hungarian, GuiLocators.DISPLAY_SETTINGS));
          assertNotEquals(englishName, hungarianName);
          GuiA11yHarness.findByLocator(hungarian, GuiLocators.DISPLAY_RESET);
          GuiA11yHarness.requireNoLeakedGuiResources();
        });
  }
}
