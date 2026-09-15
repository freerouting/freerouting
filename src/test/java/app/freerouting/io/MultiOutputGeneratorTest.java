package app.freerouting.io;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.core.RoutingJob;
import app.freerouting.fixtures.RoutingFixtureTest;
import app.freerouting.management.BoardLoader;
import app.freerouting.settings.GlobalSettings;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MultiOutputGeneratorTest extends RoutingFixtureTest {

  @BeforeEach
  protected void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  @Test
  void testGenerateMultipleOutputs() {
    RoutingJob sampleJob = getRoutingJob("Issue508-DAC2020_bm01.dsn");
    assertNotNull(sampleJob);
    BoardLoader.loadBoardIfNeeded(sampleJob);
    assertNotNull(sampleJob.board);

    Set<FileFormat> formats = Set.of(FileFormat.SES, FileFormat.KICAD_SESSION_JSON);
    MultiOutputGenerator.MultiOutputResult result =
        MultiOutputGenerator.generateOutputs(
            sampleJob.board, "bm01", formats, Freerouting.globalSettings.drcSettings, true);

    assertNotNull(result);
    assertTrue(result.getFiles().containsKey(FileFormat.SES));
    assertTrue(result.getFiles().containsKey(FileFormat.KICAD_SESSION_JSON));
    assertTrue(result.getFiles().get(FileFormat.SES).length > 0);
    assertTrue(result.getFiles().get(FileFormat.KICAD_SESSION_JSON).length > 0);

    assertNotNull(result.getDrcSummary(), "DRC summary should be included when requested");
    assertNotNull(result.getDrcSummary().hints);
  }
}
