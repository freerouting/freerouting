package app.freerouting.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.core.RoutingJob;
import app.freerouting.logger.AllowErrorLogs;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.CliSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The minimum track width of a .kicad_pro given with -de becomes the default neck width. */
class CliKiCadProjectSettingsTest {

  private static final String PROJECT =
      "{\"board\": {\"design_settings\": {\"rules\": {\"min_track_width\": 0.2}}}}";

  @TempDir Path dir;

  @Test
  void theMinimumTrackWidthOfTheProjectIsTheNeckWidth() throws Exception {
    Path project = Files.writeString(dir.resolve("board.kicad_pro"), PROJECT);
    SettingsMerger merger = new SettingsMerger(new DefaultSettings());

    assertTrue(CliRunner.addKiCadProjectSettings(new RoutingJob(), project.toString(), merger));
    assertEquals(200.0, merger.merge().getNeckWidthUm(), 1e-9);
  }

  @Test
  void aLargerMinimumConnectionWidthIsTheNeckWidth() throws Exception {
    // KiCad's DRC checks min_connection on traces too: a 0.15 mm neck on a board with a 0.2032 mm
    // minimum connection width is a connection_width error.
    Path project =
        Files.writeString(
            dir.resolve("board.kicad_pro"),
            "{\"board\": {\"design_settings\": {\"rules\": "
                + "{\"min_track_width\": 0.15, \"min_connection\": 0.2032}}}}");
    SettingsMerger merger = new SettingsMerger(new DefaultSettings());

    assertTrue(CliRunner.addKiCadProjectSettings(new RoutingJob(), project.toString(), merger));
    assertEquals(203.2, merger.merge().getNeckWidthUm(), 1e-9);
  }

  @Test
  void anExplicitNeckWidthWins() throws Exception {
    Path project = Files.writeString(dir.resolve("board.kicad_pro"), PROJECT);
    SettingsMerger merger =
        new SettingsMerger(
            new DefaultSettings(), new CliSettings(new String[] {"--router.neck_width_um=100"}));

    assertTrue(CliRunner.addKiCadProjectSettings(new RoutingJob(), project.toString(), merger));
    assertEquals(100.0, merger.merge().getNeckWidthUm(), 1e-9);
  }

  @Test
  void withoutAProjectNothingChanges() {
    SettingsMerger merger = new SettingsMerger(new DefaultSettings());
    RoutingJob job = new RoutingJob();

    assertTrue(CliRunner.addKiCadProjectSettings(job, null, merger));
    assertNull(job.kicadProject);
    assertEquals(0.0, merger.merge().getNeckWidthUm(), 1e-9);
  }

  @Test
  @AllowErrorLogs("A .kicad_pro given with -de that cannot be read is reported as an error")
  void aProjectThatCannotBeReadStopsTheRun() throws Exception {
    SettingsMerger merger = new SettingsMerger(new DefaultSettings());
    assertFalse(
        CliRunner.addKiCadProjectSettings(
            new RoutingJob(), dir.resolve("missing.kicad_pro").toString(), merger));

    Path broken = Files.writeString(dir.resolve("broken.kicad_pro"), "(kicad_pcb)");
    assertFalse(CliRunner.addKiCadProjectSettings(new RoutingJob(), broken.toString(), merger));
    assertEquals(0.0, merger.merge().getNeckWidthUm(), 1e-9);
  }
}
