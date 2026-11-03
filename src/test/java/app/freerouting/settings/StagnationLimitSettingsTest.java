package app.freerouting.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.freerouting.settings.sources.CliSettings;
import app.freerouting.settings.sources.DefaultSettings;
import app.freerouting.settings.sources.EnvironmentVariablesSource;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for the configurable stagnation pass limit setting. */
class StagnationLimitSettingsTest {

  @Test
  void defaultStagnationPassLimitIsTen() {
    RouterSettings defaults = new DefaultSettings().getSettings();
    assertNotNull(defaults.autorouter);
    assertEquals(10, defaults.autorouter.stagnationPassLimit);
  }

  @Test
  void cliNestedStagnationPassLimitIsParsed() {
    CliSettings cli = new CliSettings(new String[] {"--router.autorouter.stagnation_pass_limit=0"});
    assertEquals(0, cli.getSettings().autorouter.stagnationPassLimit);
  }

  @Test
  void cliShortFlagSplIsParsed() {
    CliSettings cli = new CliSettings(new String[] {"-spl", "25"});
    assertEquals(25, cli.getSettings().autorouter.stagnationPassLimit);
  }

  @Test
  void cliFlatDeprecatedStagnationPassLimitIsRedirected() {
    CliSettings cli = new CliSettings(new String[] {"--router.stagnation_pass_limit=0"});
    assertEquals(0, cli.getSettings().autorouter.stagnationPassLimit);
  }

  @Test
  void envNestedStagnationPassLimitIsParsed() {
    EnvironmentVariablesSource env =
        new EnvironmentVariablesSource(
            Map.of("FREEROUTING__ROUTER__AUTOROUTER__STAGNATION_PASS_LIMIT", "0"));
    assertEquals(0, env.getSettings().autorouter.stagnationPassLimit);
  }

  @Test
  void envFlatDeprecatedStagnationPassLimitIsRedirected() {
    EnvironmentVariablesSource env =
        new EnvironmentVariablesSource(Map.of("FREEROUTING__ROUTER__STAGNATION_PASS_LIMIT", "15"));
    assertEquals(15, env.getSettings().autorouter.stagnationPassLimit);
  }

  @Test
  void settingsMergerOverridesDefaultWithCli() {
    RouterSettings merged =
        new SettingsMerger(
                new DefaultSettings(),
                new CliSettings(new String[] {"--router.autorouter.stagnation_pass_limit=0"}))
            .merge();
    assertEquals(0, merged.autorouter.stagnationPassLimit);
  }

  @Test
  @SuppressWarnings("deprecation")
  void globalSettingsAppliesShortFlagSpl() {
    GlobalSettings gs = new GlobalSettings();
    gs.applyCommandLineArguments(new String[] {"-spl", "0"});
    assertEquals(0, gs.routerSettings.autorouter.stagnationPassLimit);
  }
}
