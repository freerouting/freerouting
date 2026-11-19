package app.freerouting.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.logger.AllowErrorLogs;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.CliSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliRunnerRoutingTest {

  private static final String FIXTURE = "fixtures/Issue508-DAC2020_bm01.dsn";

  @Test
  void testInitializeCliRoutesBoardSuccessfully(@TempDir Path tempDir) {
    Path outputFile = tempDir.resolve("output.ses");
    GlobalSettings settings = new GlobalSettings();
    settings.initialInputFile = FIXTURE;
    settings.initialOutputFile = outputFile.toString();
    settings.routerSettings.fanout.enabled = false;
    settings.routerSettings.autorouter.maxPasses = 1;
    settings.routerSettings.autorouter.maxItems = 10;
    settings.routerSettings.optimizer.enabled = false;

    boolean result = CliRunner.initializeCli(settings);

    assertTrue(result, "initializeCli should succeed for valid DSN input");
    assertEquals(0, settings.cliExitCode);
    assertTrue(Files.exists(outputFile), "SES output file should be written");
    assertTrue(outputFile.toFile().length() > 0, "SES output file should not be empty");
  }

  @Test
  void testSettingsPrecedence(@TempDir Path tempDir) {
    Path outputFile = tempDir.resolve("precedence_out.ses");
    GlobalSettings settings = new GlobalSettings();
    settings.initialInputFile = FIXTURE;
    settings.initialOutputFile = outputFile.toString();

    // CLI args: priority 60 should take precedence over file settings
    String[] cliArgs = new String[] {"--router.max_passes=2", "--router.fanout.enabled=false"};
    settings.settingsMergerProtype =
        new SettingsMerger(new DefaultSettings(), new CliSettings(cliArgs));
    settings.routerSettings.autorouter.maxItems = 10;
    settings.routerSettings.optimizer.enabled = false;

    boolean result = CliRunner.initializeCli(settings);

    assertTrue(result, "initializeCli should succeed with settings precedence");
    assertEquals(0, settings.cliExitCode);
    assertTrue(Files.exists(outputFile));
  }

  @Test
  void testAdjacentRulesDiscovery(@TempDir Path tempDir) throws Exception {
    Path dsnFile = tempDir.resolve("test_board.dsn");
    try (InputStream in = Files.newInputStream(Path.of(FIXTURE))) {
      Files.copy(in, dsnFile, StandardCopyOption.REPLACE_EXISTING);
    }

    Path rulesFile = tempDir.resolve("test_board.rules");
    Files.writeString(
        rulesFile,
        "(rules PCB test_board (autoroute_settings (autoroute yes) (fanout no) (passes 1)))\n");

    Path outputFile = tempDir.resolve("adjacent_out.ses");
    GlobalSettings settings = new GlobalSettings();
    settings.initialInputFile = dsnFile.toString();
    settings.initialOutputFile = outputFile.toString();
    settings.initialRulesFile =
        null; // Do not provide explicit rules; auto-discovery should find test_board.rules
    settings.routerSettings.autorouter.maxItems = 10;
    settings.routerSettings.optimizer.enabled = false;

    boolean result = CliRunner.initializeCli(settings);

    assertTrue(result, "initializeCli should succeed when adjacent rules file is auto-discovered");
    assertEquals(0, settings.cliExitCode);
    assertTrue(Files.exists(outputFile));
    assertTrue(outputFile.toFile().length() > 0);
  }

  @Test
  void testCompoundInputWithSession(@TempDir Path tempDir) throws Exception {
    Path dsnFile = tempDir.resolve("session_test.dsn");
    try (InputStream in = Files.newInputStream(Path.of(FIXTURE))) {
      Files.copy(in, dsnFile, StandardCopyOption.REPLACE_EXISTING);
    }

    Path sesFile = tempDir.resolve("existing.ses");
    Files.writeString(
        sesFile,
        "(session existing_session (base_design session_test) (placement) (was_is) (routes (library_out)))\n");

    Path outputFile = tempDir.resolve("compound_out.ses");
    GlobalSettings settings = new GlobalSettings();
    settings.initialInputFile = dsnFile.toString();
    settings.designSessionFilename = sesFile.toString();
    settings.initialOutputFile = outputFile.toString();
    settings.routerSettings.fanout.enabled = false;
    settings.routerSettings.autorouter.maxPasses = 1;
    settings.routerSettings.autorouter.maxItems = 10;
    settings.routerSettings.optimizer.enabled = false;

    boolean result = CliRunner.initializeCli(settings);

    assertTrue(result, "initializeCli should succeed with compound DSN + SES input");
    assertEquals(0, settings.cliExitCode);
    assertTrue(Files.exists(outputFile));
  }

  @Test
  void testNonSesOutputFormatScr(@TempDir Path tempDir) {
    Path outputFile = tempDir.resolve("output.scr");
    GlobalSettings settings = new GlobalSettings();
    settings.initialInputFile = FIXTURE;
    settings.initialOutputFile = outputFile.toString();
    settings.routerSettings.fanout.enabled = false;
    settings.routerSettings.autorouter.maxPasses = 1;
    settings.routerSettings.autorouter.maxItems = 10;
    settings.routerSettings.optimizer.enabled = false;

    boolean result = CliRunner.initializeCli(settings);

    assertTrue(result, "initializeCli should succeed when requested output is .scr");
    assertEquals(0, settings.cliExitCode);
    assertTrue(Files.exists(outputFile), "SCR output file should be written");
    assertTrue(outputFile.toFile().length() > 0, "SCR output file should not be empty");
  }

  @Test
  @AllowErrorLogs("Missing input file causes an error log")
  void testInitializeCliFailsOnMissingInputFile(@TempDir Path tempDir) {
    GlobalSettings settings = new GlobalSettings();
    settings.initialInputFile = tempDir.resolve("non_existent_input.dsn").toString();
    settings.initialOutputFile = tempDir.resolve("output.ses").toString();

    boolean result = CliRunner.initializeCli(settings);

    assertFalse(result, "initializeCli should fail when input file is missing");
  }

  @Test
  @AllowErrorLogs("Null CLI files trigger error logging")
  void testInitializeCliFailsOnNullFiles() {
    GlobalSettings settings = new GlobalSettings();
    settings.initialInputFile = null;
    settings.initialOutputFile = null;

    boolean result = CliRunner.initializeCli(settings);

    assertFalse(result, "initializeCli should fail when input or output file is null");
  }

  @Test
  void testInitializeCliWithKiCadRulesAndProject(@TempDir Path tempDir) throws Exception {
    Path outputFile = tempDir.resolve("kicad_out.ses");
    Path druFile =
        Files.writeString(
            tempDir.resolve("custom.kicad_dru"),
            "(version 1)\n(rule test_clearance (constraint clearance (min 0.2mm)))\n");
    Path proFile =
        Files.writeString(
            tempDir.resolve("custom.kicad_pro"),
            "{\"board\": {\"design_settings\": {\"rules\": {\"min_track_width\": 0.15}}}}");

    GlobalSettings settings = new GlobalSettings();
    settings.initialInputFile = FIXTURE;
    settings.initialOutputFile = outputFile.toString();
    settings.initialKiCadDesignRulesFile = druFile.toString();
    settings.initialKiCadProjectFile = proFile.toString();
    settings.routerSettings.fanout.enabled = false;
    settings.routerSettings.autorouter.maxPasses = 1;
    settings.routerSettings.autorouter.maxItems = 10;
    settings.routerSettings.optimizer.enabled = false;

    boolean result = CliRunner.initializeCli(settings);

    assertTrue(result, "initializeCli should succeed with KiCad custom rules and project");
    assertEquals(0, settings.cliExitCode);
    assertTrue(Files.exists(outputFile));
    assertTrue(outputFile.toFile().length() > 0);
  }
}
