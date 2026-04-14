package app.freerouting.settings.sources;

import app.freerouting.Freerouting;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.settings.GlobalSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class DsnFileSettingsTest {

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  @Test
  void dsnFileSettingsNoLongerReturnsStubLayerCount() {
    // rpi_splitter.dsn is a 2-layer board; the old stub always returned 2 coincidentally.
    // Issue413-test.dsn has no (autoroute_settings ...) scope, so DsnFileSettings
    // falls back to a plain new RouterSettings() whose getLayerCount() returns 0.
    // This proves the old hardcoded setLayerCount(2) stub has been removed.
    InputStream in = DsnTestFixtures.openResource("Issue413-test.dsn");
    DsnFileSettings settings = new DsnFileSettings(in, "Issue413-test.dsn");

    // Old stub returned 2 regardless; correct implementation does not hard-code 2
    assertNotEquals(2, settings.getSettings().getLayerCount(),
        "DsnFileSettings must not return the old hard-coded stub layer count of 2");
  }

  @Test
  void dsnFileSettingsReturnsNonNullSettings() {
    InputStream in = DsnTestFixtures.openResource("Issue143-rpi_splitter.dsn");
    DsnFileSettings settings = new DsnFileSettings(in, "Issue143-rpi_splitter.dsn");

    assertNotNull(settings.getSettings(), "getSettings() must never return null");
  }

  @Test
  void dsnFileSettingsPriorityIs20() {
    InputStream in = DsnTestFixtures.openResource("Issue143-rpi_splitter.dsn");
    DsnFileSettings settings = new DsnFileSettings(in, "Issue143-rpi_splitter.dsn");

    assertEquals(20, settings.getPriority());
  }

  @Test
  void dsnFileSettingsSourceNameContainsFilename() {
    InputStream in = DsnTestFixtures.openResource("Issue143-rpi_splitter.dsn");
    DsnFileSettings settings = new DsnFileSettings(in, "Issue143-rpi_splitter.dsn");

    assertTrue(settings.getSourceName().contains("Issue143-rpi_splitter.dsn"));
  }
}

