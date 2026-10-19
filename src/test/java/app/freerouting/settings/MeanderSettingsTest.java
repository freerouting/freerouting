package app.freerouting.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.settings.sources.CliSettings;
import app.freerouting.settings.sources.DefaultSettings;
import org.junit.jupiter.api.Test;

/** CLI plumbing, default values, and clone semantics for meander pattern settings. */
class MeanderSettingsTest {

  @Test
  void cliFlagsReachRouterSettings() {
    String[] args = {
      "--router.meander_min_amplitude_mm=0.35",
      "--router.meander_max_amplitude_mm=1.5",
      "--router.meander_spacing_mm=0.8",
      "--router.meander_corner_style=CHAMFER",
      "--router.meander_radius_percentage=65.0",
      "--router.meander_single_sided=true"
    };
    CliSettings cli = new CliSettings(args);
    RouterSettings settings = cli.getSettings();

    assertEquals(0.35, settings.meanderMinAmplitudeMm, 1e-9);
    assertEquals(1.5, settings.meanderMaxAmplitudeMm, 1e-9);
    assertEquals(0.8, settings.meanderSpacingMm, 1e-9);
    assertEquals("CHAMFER", settings.meanderCornerStyle);
    assertEquals(65.0, settings.meanderRadiusPercentage, 1e-9);
    assertTrue(settings.meanderSingleSided);
  }

  @Test
  void defaultSettingsProvideStandardValues() {
    RouterSettings settings = new DefaultSettings().getSettings();

    assertEquals(0.2, settings.meanderMinAmplitudeMm, 1e-9);
    assertEquals(1.0, settings.meanderMaxAmplitudeMm, 1e-9);
    assertEquals(0.6, settings.meanderSpacingMm, 1e-9);
    assertEquals("FILLET", settings.meanderCornerStyle);
    assertEquals(80.0, settings.meanderRadiusPercentage, 1e-9);
    assertFalse(settings.meanderSingleSided);
  }

  @Test
  void newRouterSettingsHasNullFields() {
    RouterSettings settings = new RouterSettings();

    assertNull(settings.meanderMinAmplitudeMm);
    assertNull(settings.meanderMaxAmplitudeMm);
    assertNull(settings.meanderSpacingMm);
    assertNull(settings.meanderCornerStyle);
    assertNull(settings.meanderRadiusPercentage);
    assertNull(settings.meanderSingleSided);
  }

  @Test
  void cloneCarriesMeanderFields() {
    RouterSettings original = new RouterSettings();
    original.meanderMinAmplitudeMm = 0.25;
    original.meanderMaxAmplitudeMm = 1.25;
    original.meanderSpacingMm = 0.75;
    original.meanderCornerStyle = "CHAMFER";
    original.meanderRadiusPercentage = 70.0;
    original.meanderSingleSided = true;

    RouterSettings copy = original.clone();

    assertEquals(0.25, copy.meanderMinAmplitudeMm, 1e-9);
    assertEquals(1.25, copy.meanderMaxAmplitudeMm, 1e-9);
    assertEquals(0.75, copy.meanderSpacingMm, 1e-9);
    assertEquals("CHAMFER", copy.meanderCornerStyle);
    assertEquals(70.0, copy.meanderRadiusPercentage, 1e-9);
    assertTrue(copy.meanderSingleSided);
  }

  @Test
  void alternateCamelCaseCliFlagsWork() {
    String[] args = {"--router.meanderMinAmplitudeMm=0.4", "--router.meanderCornerStyle=FILLET"};
    CliSettings cli = new CliSettings(args);
    RouterSettings settings = cli.getSettings();

    assertEquals(0.4, settings.meanderMinAmplitudeMm, 1e-9);
    assertEquals("FILLET", settings.meanderCornerStyle);
  }
}
