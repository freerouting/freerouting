package app.freerouting.i18n;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Hungarian resource-parity check for MVP GUI bundles (decision D19).
 *
 * <p>Verifies that every MVP bundle used by the accessibility workflows has a complete {@code _hu}
 * variant whose keys cover the English keys. This is a headless-safe resource check (no GUI
 * construction), so it runs in the default {@code test} task. Documented in the SoC plan §7
 * validation matrix.
 */
class HungarianResourceParityCheckTest {

  private static final Path RESOURCE_ROOT = Path.of("src/main/resources/app/freerouting");

  /** Bundles touched by component-only accessibility workflows and the shared Common fallback. */
  private static final String[] MVP_BUNDLES = {
    "gui/board/BoardPanelStatus",
    "gui/menus/BoardMenuFile",
    "gui/menus/BoardMenuDisplay",
    "gui/menus/BoardMenuParameter",
    "gui/menus/BoardMenuRules",
    "gui/menus/BoardMenuInfo",
    "gui/menus/BoardMenuHelp",
    "gui/menus/BoardMenuOther",
    "gui/board/BoardToolbar",
    "gui/board/BoardFrame",
    "gui/windows/board/WindowVisibility",
    "Common",
  };

  private static Properties load(String base, String localeTag) throws Exception {
    Path file = RESOURCE_ROOT.resolve(base + "_" + localeTag + ".properties");
    Properties properties = new Properties();
    try (InputStream in = Files.newInputStream(file)) {
      properties.load(in);
    }
    return properties;
  }

  @Test
  void mvpBundlesHaveCompleteHungarianVariants() throws Exception {
    for (String base : MVP_BUNDLES) {
      Properties en = load(base, "en");
      Properties hu = load(base, "hu");
      Set<String> missing = new TreeSet<>();
      for (String key : en.stringPropertyNames()) {
        if (!hu.containsKey(key) && !key.startsWith("text_manager_fallback_")) {
          missing.add(key);
        }
      }
      assertTrue(
          missing.isEmpty(),
          "Hungarian bundle '" + base + "_hu' is missing English keys: " + missing);
    }
  }
}
