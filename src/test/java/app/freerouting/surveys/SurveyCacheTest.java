package app.freerouting.surveys;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link SurveyCache}: round-trip, corruption recovery, and atomic persistence. */
class SurveyCacheTest {

  @TempDir Path tempDir;

  @Test
  void missingFileStartsEmpty() {
    SurveyCache cache = new SurveyCache(tempDir);

    assertFalse(cache.isHandled("any-survey"));
  }

  @Test
  void answeredSurveyPersistsAcrossReloads() {
    new SurveyCache(tempDir).markAnswered("survey-a");

    assertTrue(new SurveyCache(tempDir).isHandled("survey-a"));
    assertFalse(new SurveyCache(tempDir).isHandled("survey-b"));
  }

  @Test
  void dismissedSurveyPersistsAcrossReloads() {
    new SurveyCache(tempDir).markDismissed("survey-x");

    assertTrue(new SurveyCache(tempDir).isHandled("survey-x"));
  }

  @Test
  void bothSetsPersistInTheSameFile() {
    SurveyCache cache = new SurveyCache(tempDir);
    cache.markAnswered("a1");
    cache.markDismissed("d1");

    Path file = tempDir.resolve("surveys.json");
    assertTrue(Files.isRegularFile(file));
    SurveyCache reloaded = new SurveyCache(tempDir);
    assertTrue(reloaded.isHandled("a1"));
    assertTrue(reloaded.isHandled("d1"));
  }

  @Test
  void corruptedFileIsQuarantinedAndCacheStartsClean() throws Exception {
    Path file = tempDir.resolve("surveys.json");
    Files.writeString(file, "{ this is not valid json !!!");

    SurveyCache cache = new SurveyCache(tempDir);
    assertFalse(cache.isHandled("anything"));

    // Forensic copy kept, corrupt file removed from the way.
    assertTrue(Files.isRegularFile(tempDir.resolve("surveys.json.bad")));
    assertFalse(Files.isRegularFile(file));
  }

  @Test
  void emptyFileStartsCleanWithoutQuarantine() throws Exception {
    Path file = tempDir.resolve("surveys.json");
    Files.writeString(file, "");

    SurveyCache cache = new SurveyCache(tempDir);
    assertFalse(cache.isHandled("anything"));

    // An empty (not corrupt) file is replaced by a valid one on next save, not quarantined.
    cache.markAnswered("s1");
    assertTrue(Files.isRegularFile(file));
    assertTrue(new SurveyCache(tempDir).isHandled("s1"));
  }

  @Test
  void blankAndNullIdsAreIgnored() {
    SurveyCache cache = new SurveyCache(tempDir);
    cache.markAnswered(null);
    cache.markAnswered("  ");
    cache.markDismissed(null);

    assertFalse(cache.isHandled(null));
    assertFalse(cache.isHandled("  "));
    assertFalse(Files.exists(tempDir.resolve("surveys.json")));
  }

  @Test
  void saveAfterCorruptionSucceeds() throws Exception {
    Path file = tempDir.resolve("surveys.json");
    Files.writeString(file, "garbage");

    SurveyCache cache = new SurveyCache(tempDir);
    cache.markAnswered("fresh");

    assertTrue(Files.isRegularFile(file));
    assertTrue(new SurveyCache(tempDir).isHandled("fresh"));
  }

  @Test
  void clearResetsBothAnsweredAndDismissed() {
    SurveyCache cache = new SurveyCache(tempDir);
    cache.markAnswered("s1");
    cache.markDismissed("s2");
    assertTrue(cache.isHandled("s1"));
    assertTrue(cache.isHandled("s2"));

    cache.clear();

    assertFalse(cache.isHandled("s1"));
    assertFalse(cache.isHandled("s2"));
    SurveyCache reloaded = new SurveyCache(tempDir);
    assertFalse(reloaded.isHandled("s1"));
    assertFalse(reloaded.isHandled("s2"));
  }

  @Test
  void ignoreCacheBypassesHandledCheck() {
    SurveyCache cache = new SurveyCache(tempDir);
    cache.markAnswered("s-ignore");
    assertTrue(cache.isHandled("s-ignore"));

    System.setProperty(SurveyCache.IGNORE_CACHE_PROP, "true");
    try {
      assertFalse(cache.isHandled("s-ignore"));
    } finally {
      System.clearProperty(SurveyCache.IGNORE_CACHE_PROP);
    }
  }
}
