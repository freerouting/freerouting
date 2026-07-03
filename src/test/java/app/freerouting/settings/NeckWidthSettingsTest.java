package app.freerouting.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.settings.sources.CliSettings;
import org.junit.jupiter.api.Test;

/** CLI plumbing and helper semantics for the neck_width_um setting. */
class NeckWidthSettingsTest {

  @Test
  void cliFlagReachesRouterSettings() {
    CliSettings cli = new CliSettings(new String[]{"--router.neck_width_um=250"});
    assertEquals(250.0, cli.getSettings().getNeckWidthUm(), 1e-9);
  }

  @Test
  void defaultIsOff() {
    assertEquals(0.0, new RouterSettings().getNeckWidthUm(), 1e-9);
  }

  @Test
  void cloneCarriesTheField() {
    RouterSettings settings = new RouterSettings();
    settings.neckWidthUm = 130.0;
    assertEquals(130.0, settings.clone().getNeckWidthUm(), 1e-9);
  }
}
