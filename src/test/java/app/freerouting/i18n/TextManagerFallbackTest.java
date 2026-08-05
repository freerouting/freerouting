package app.freerouting.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import app.freerouting.gui.WindowClearanceViolations;
import app.freerouting.interactive.InteractiveState;
import app.freerouting.gui.BoardFrame;
import app.freerouting.util.TextManager;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class TextManagerFallbackTest {

  @Test
  void fallsBackToEnglishClassBundleWhenLocaleKeyMissing() {
    TextManager english = new TextManager(InteractiveState.class, Locale.forLanguageTag("en"));
    TextManager arabic = new TextManager(InteractiveState.class, Locale.forLanguageTag("ar"));

    assertEquals(
        english.getText("text_manager_fallback_class_probe"),
        arabic.getText("text_manager_fallback_class_probe"));
  }

  @Test
  void fallsBackToParentClassLocaleBundleWhenSubclassKeyMissing() {
    TextManager arabic = new TextManager(WindowClearanceViolations.class, Locale.forLanguageTag("ar"));

    assertEquals("تصفية:", arabic.getText("filter"));
  }

  @Test
  void resolvesItalianTranslationsFromLocaleBundle() {
    TextManager italian = new TextManager(BoardFrame.class, Locale.forLanguageTag("it-IT"));

    assertEquals("annulla (Esc)", italian.getText("cancel"));
  }

  @Test
  void fallsBackToEnglishCommonBundleWhenItalianKeyMissing() {
    TextManager english = new TextManager(BoardFrame.class, Locale.forLanguageTag("en"));
    TextManager italian = new TextManager(BoardFrame.class, Locale.forLanguageTag("it-IT"));

    assertEquals(
        english.getText("text_manager_fallback_common_probe"),
        italian.getText("text_manager_fallback_common_probe"));
  }

  @Test
  void fallsBackToEnglishCommonBundleWhenLocaleKeyMissing() {
    TextManager english = new TextManager(BoardFrame.class, Locale.forLanguageTag("en"));
    TextManager arabic = new TextManager(BoardFrame.class, Locale.forLanguageTag("ar"));

    assertEquals(
        english.getText("text_manager_fallback_common_probe"),
        arabic.getText("text_manager_fallback_common_probe"));
  }

  @Test
  void resolvesTranslatedClassBundleWhenLocaleKeyPresent() {
    TextManager english = new TextManager(InteractiveState.class, Locale.forLanguageTag("en"));
    TextManager arabic = new TextManager(InteractiveState.class, Locale.forLanguageTag("ar"));

    assertNotEquals(
        english.getText("autorouter_started"),
        arabic.getText("autorouter_started"));
  }

  @Test
  void resolvesTranslatedCommonBundleWhenLocaleKeyPresent() {
    TextManager english = new TextManager(BoardFrame.class, Locale.forLanguageTag("en"));
    TextManager arabic = new TextManager(BoardFrame.class, Locale.forLanguageTag("ar"));

    assertNotEquals(
        english.getText("net_hover_info"),
        arabic.getText("net_hover_info"));
  }
}
