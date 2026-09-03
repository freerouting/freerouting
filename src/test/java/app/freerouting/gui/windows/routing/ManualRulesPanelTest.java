package app.freerouting.gui.windows.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.util.TextManager;
import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;

/**
 * Characterization tests for the {@link ManualRulesPanel} resource bundle. The panel replaces the
 * former {@code WindowManualRules} popup; its bundle was renamed with the class, so these tests
 * verify that every expected key still resolves for the English source and for representative
 * locales.
 */
class ManualRulesPanelTest {

  private static final String BUNDLE_BASE_NAME =
      "app.freerouting.gui.windows.routing.ManualRulesPanel";

  private static final String[] REQUIRED_KEYS = {
    "title",
    "via_rule",
    "via_rule_tooltip",
    "trace_clearance_class",
    "trace_clearance_class_tooltip",
    "trace_width",
    "trace_width_tooltip",
    "on_layer",
    "on_layer_tooltip"
  };

  @Test
  void englishBundleContainsAllRequiredKeys() {
    ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, Locale.forLanguageTag("en"));
    for (String key : REQUIRED_KEYS) {
      assertTrue(bundle.containsKey(key), () -> "English bundle is missing required key: " + key);
      assertNotNull(bundle.getString(key), () -> "Key must have a non-null value: " + key);
    }
  }

  @Test
  void titleKeyKeepsItsCanonicalEnglishValue() {
    ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, Locale.forLanguageTag("en"));
    // The former dialog title is now reused as the titled border of the inline panel.
    assertEquals("Manual Rules", bundle.getString("title"));
  }

  @Test
  void textManagerResolvesBundleForPanelClass() {
    TextManager tm = new TextManager(ManualRulesPanel.class, Locale.ENGLISH);
    // TextManager derives the bundle name from the class, so this fails if the
    // renamed bundles do not match the new class name.
    assertEquals("Manual Rules", tm.getText("title"));
  }

  @Test
  void representativeLocalesResolveTitledBorderKey() {
    for (String tag : new String[] {"de", "fr", "ja"}) {
      Locale locale = Locale.forLanguageTag(tag);
      ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, locale);
      // Fallback to English is acceptable, but the key must exist in some language.
      assertNotNull(bundle.getString("title"), () -> "Missing 'title' for locale " + tag);
    }
  }
}
