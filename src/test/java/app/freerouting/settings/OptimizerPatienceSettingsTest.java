package app.freerouting.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import app.freerouting.settings.sources.CliSettings;
import app.freerouting.settings.sources.DefaultSettings;
import app.freerouting.settings.sources.EnvironmentVariablesSource;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for optimizer patience presets and CLI mappings. */
class OptimizerPatienceSettingsTest {

  @Test
  void defaultSettingsHasBalancedPatience() {
    RouterSettings defaults = new DefaultSettings().getSettings();
    assertNotNull(defaults.optimizer);
    assertEquals(OptimizerPatience.BALANCED, defaults.optimizer.patience);
    assertNull(defaults.optimizer.maxConsecutiveFailures);
    assertNull(defaults.optimizer.maxConsecutiveFailuresPass1);
    assertEquals(2.50f, defaults.optimizer.patience.getDefaultImprovementThreshold());
    assertEquals(12, defaults.optimizer.patience.getDefaultMaxConsecutiveFailuresPass1());
    assertEquals(50, defaults.optimizer.patience.getDefaultMaxConsecutiveFailures());
  }

  @Test
  void cliNestedPatienceIsParsedCaseInsensitively() {
    CliSettings cli1 = new CliSettings(new String[] {"--router.optimizer.patience=patient"});
    assertEquals(OptimizerPatience.PATIENT, cli1.getSettings().optimizer.patience);

    CliSettings cli2 = new CliSettings(new String[] {"--router.optimizer.patience=EXHAUSTIVE"});
    assertEquals(OptimizerPatience.EXHAUSTIVE, cli2.getSettings().optimizer.patience);
  }

  @Test
  void cliNestedMaxPassesIsParsed() {
    CliSettings cli = new CliSettings(new String[] {"--router.optimizer.max_passes=25"});
    assertEquals(25, cli.getSettings().optimizer.maxPasses);
  }

  @Test
  void globalSettingsGenericLongParametersParsePatienceAndMaxPasses() {
    GlobalSettings globalSettings = new GlobalSettings();
    globalSettings.applyCommandLineArguments(
        new String[] {
          "--router.optimizer.patience=exhaustive", "--router.optimizer.max_passes=25"
        });
    assertEquals(OptimizerPatience.EXHAUSTIVE, globalSettings.routerSettings.optimizer.patience);
    assertEquals(25, globalSettings.routerSettings.optimizer.maxPasses);
  }

  @Test
  void envNestedPatienceIsParsed() {
    EnvironmentVariablesSource env =
        new EnvironmentVariablesSource(
            Map.of("FREEROUTING__ROUTER__OPTIMIZER__PATIENCE", "exhaustive"));
    assertEquals(OptimizerPatience.EXHAUSTIVE, env.getSettings().optimizer.patience);
  }

  @Test
  void settingsMergerOverridesDefaultPatienceAndMaxPassesWithCli() {
    RouterSettings merged =
        new SettingsMerger(
                new DefaultSettings(),
                new CliSettings(
                    new String[] {
                      "--router.optimizer.patience=patient", "--router.optimizer.max_passes=25"
                    }))
            .merge();
    assertEquals(OptimizerPatience.PATIENT, merged.optimizer.patience);
    assertEquals(25, merged.optimizer.maxPasses);
  }

  @Test
  void patienceEnumProfilesAreCorrect() {
    assertEquals(2.50f, OptimizerPatience.BALANCED.getDefaultImprovementThreshold());
    assertEquals(12, OptimizerPatience.BALANCED.getDefaultMaxConsecutiveFailuresPass1());
    assertEquals(50, OptimizerPatience.BALANCED.getDefaultMaxConsecutiveFailures());

    assertEquals(0.50f, OptimizerPatience.PATIENT.getDefaultImprovementThreshold());
    assertEquals(100, OptimizerPatience.PATIENT.getDefaultMaxConsecutiveFailuresPass1());
    assertEquals(250, OptimizerPatience.PATIENT.getDefaultMaxConsecutiveFailures());

    assertEquals(0.00f, OptimizerPatience.EXHAUSTIVE.getDefaultImprovementThreshold());
    assertEquals(0, OptimizerPatience.EXHAUSTIVE.getDefaultMaxConsecutiveFailuresPass1());
    assertEquals(0, OptimizerPatience.EXHAUSTIVE.getDefaultMaxConsecutiveFailures());
  }

  @Test
  void routerSettingsConvenienceAccessorsWork() {
    RouterSettings settings = new RouterSettings();
    assertNull(settings.getOptimizerPatience());
    assertNull(settings.getOptimizerMaxPasses());

    settings.setOptimizerPatience(OptimizerPatience.EXHAUSTIVE);
    assertEquals(OptimizerPatience.EXHAUSTIVE, settings.getOptimizerPatience());

    settings.setOptimizerMaxPasses(15);
    assertEquals(15, settings.getOptimizerMaxPasses());
  }

  @Test
  void jsonSettingsDeserializesLowercasePatiencePresets() {
    OptimizerSettings patient =
        app.freerouting.util.gson.GsonProvider.GSON.fromJson(
            "{\"patience\":\"patient\"}", OptimizerSettings.class);
    assertEquals(OptimizerPatience.PATIENT, patient.patience);

    OptimizerSettings exhaustive =
        app.freerouting.util.gson.GsonProvider.GSON.fromJson(
            "{\"patience\":\"exhaustive\"}", OptimizerSettings.class);
    assertEquals(OptimizerPatience.EXHAUSTIVE, exhaustive.patience);

    OptimizerSettings balanced =
        app.freerouting.util.gson.GsonProvider.GSON.fromJson(
            "{\"patience\":\"balanced\"}", OptimizerSettings.class);
    assertEquals(OptimizerPatience.BALANCED, balanced.patience);

    OptimizerSettings uppercase =
        app.freerouting.util.gson.GsonProvider.GSON.fromJson(
            "{\"patience\":\"PATIENT\"}", OptimizerSettings.class);
    assertEquals(OptimizerPatience.PATIENT, uppercase.patience);
  }
}
