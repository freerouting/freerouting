package app.freerouting.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.freerouting.board.Layer;
import app.freerouting.board.LayerStructure;
import app.freerouting.gui.a11y.GuiA11yHarness;
import app.freerouting.gui.a11y.GuiLocators;
import java.awt.Component;
import java.util.Locale;
import javax.accessibility.AccessibleRole;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * MVP a11y workflow #2 — select layer / change setting (component-only, forced headless via {@code
 * testGui}). The layer-selector combo resolves by stable locator, exposes a translated accessible
 * name/role/state, supports changing the selected layer, and holds no duplicate/empty sibling names
 * (D19).
 */
@Tag("gui")
class ComboBoxLayerA11yTest {

  private static LayerStructure fourSignalLayers() {
    // 4 signal layers -> ComboBoxLayer adds the "all" and "inner" entries.
    return new LayerStructure(
        new Layer[] {
          new Layer("F.Cu", true),
          new Layer("In1.Cu", true),
          new Layer("In2.Cu", true),
          new Layer("B.Cu", true)
        });
  }

  @Test
  void layerSelectorResolvesByLocatorAndIsAccessible() {
    ComboBoxLayer combo =
        GuiA11yHarness.onEdt(() -> new ComboBoxLayer(fourSignalLayers(), Locale.ENGLISH));

    GuiA11yHarness.onEdt(
        () -> {
          Component c = GuiA11yHarness.findByLocator(combo, GuiLocators.TOOLBAR_LAYER_SELECT);
          GuiA11yHarness.requireRole(c, GuiLocators.TOOLBAR_LAYER_SELECT, AccessibleRole.COMBO_BOX);
          GuiA11yHarness.requireAccessibleName(c, GuiLocators.TOOLBAR_LAYER_SELECT);
          GuiA11yHarness.requireEnabled(c, GuiLocators.TOOLBAR_LAYER_SELECT);
          GuiA11yHarness.requireUniqueSiblingNames(combo);
        });
  }

  @Test
  void selectingSignalLayerChangesTheSetting() {
    ComboBoxLayer combo =
        GuiA11yHarness.onEdt(() -> new ComboBoxLayer(fourSignalLayers(), Locale.ENGLISH));

    GuiA11yHarness.onEdt(
        () -> {
          assertEquals(
              ComboBoxLayer.ALL_LAYER_INDEX,
              combo.getSelectedLayer().index,
              "layer selector defaults to 'all layers'");
          // 0=all, 1=inner, 2..=signal layers (F.Cu, In1.Cu, In2.Cu, B.Cu).
          combo.setSelectedIndex(2);
          int selected = combo.getSelectedLayer().index;
          assertNotEquals(
              ComboBoxLayer.ALL_LAYER_INDEX, selected, "selection no longer 'all layers'");
          assertEquals(
              0,
              selected,
              "F.Cu (first signal layer) has board layer index 0 in the test LayerStructure");
        });
  }

  @Test
  void accessibleNameIsTranslatedAcrossLocales() {
    ComboBoxLayer en =
        GuiA11yHarness.onEdt(() -> new ComboBoxLayer(fourSignalLayers(), Locale.ENGLISH));
    ComboBoxLayer hu =
        GuiA11yHarness.onEdt(
            () -> new ComboBoxLayer(fourSignalLayers(), Locale.forLanguageTag("hu")));

    String enName =
        GuiA11yHarness.onEdt(
            () ->
                GuiA11yHarness.accessibleName(
                    GuiA11yHarness.findByLocator(en, GuiLocators.TOOLBAR_LAYER_SELECT)));
    String huName =
        GuiA11yHarness.onEdt(
            () ->
                GuiA11yHarness.accessibleName(
                    GuiA11yHarness.findByLocator(hu, GuiLocators.TOOLBAR_LAYER_SELECT)));

    assertNotNull(enName, "EN accessible name must be present");
    assertNotNull(huName, "HU accessible name must be present");
    assertNotEquals(enName, huName, "accessible name must be translated across locales (D19)");
  }
}
