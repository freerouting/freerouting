package app.freerouting.drc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.core.RoutingJob;
import app.freerouting.fixtures.RoutingFixtureTest;
import app.freerouting.management.BoardLoader;
import app.freerouting.settings.GlobalSettings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DesignRulesCheckerTest extends RoutingFixtureTest {

  @BeforeEach
  protected void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  @Test
  void test_DrcReport_Structure() {
    // Create a simple routing job with a DSN file
    RoutingJob job = GetRoutingJob("Issue555-BBD_Mars-64.dsn");

    assertNotNull(job, "Job should not be null");

    BoardLoader.loadBoardIfNeeded(job);

    assertNotNull(job.board, "Board should be loaded");

    assertNotNull(job, "Job should not be null");
    assertNotNull(job.board, "Board should be loaded");

    // Create DRC checker
    DesignRulesChecker drcChecker =
        new DesignRulesChecker(job.board, Freerouting.globalSettings.drcSettings);

    // Generate report
    DrcReport report = drcChecker.generateReport("test.dsn", "mm");

    // Verify report structure
    assertNotNull(report, "Report should not be null");
    assertEquals(
        "https://schemas.kicad.org/drc.v1.json",
        report.$schema,
        "Schema should match KiCad format");
    assertEquals("mm", report.coordinateUnits, "Coordinate units should be mm");
    assertEquals("test.dsn", report.source, "Source should match");
    assertNotNull(report.violations, "Violations list should not be null");
    assertNotNull(report.unconnectedItems, "Unconnected items list should not be null");
    assertNotNull(report.schematicParity, "Schematic parity list should not be null");
    assertTrue(
        report.freeroutingVersion.contains("Freerouting"), "Version should contain Freerouting");
  }

  @Test
  void test_DrcReport_JsonFormat() {
    // Create a simple routing job with a DSN file
    RoutingJob job = GetRoutingJob("Issue555-BBD_Mars-64.dsn");

    assertNotNull(job, "Job should not be null");

    BoardLoader.loadBoardIfNeeded(job);

    assertNotNull(job.board, "Board should be loaded");

    // Create DRC checker
    DesignRulesChecker drcChecker =
        new DesignRulesChecker(job.board, Freerouting.globalSettings.drcSettings);

    // Generate JSON report
    String jsonReport = drcChecker.generateReportJson("test.dsn", "mm");

    // Verify JSON is valid
    assertNotNull(jsonReport, "JSON report should not be null");
    assertFalse(jsonReport.isEmpty(), "JSON report should not be empty");

    // Parse JSON to verify structure
    JsonObject json = JsonParser.parseString(jsonReport).getAsJsonObject();
    assertTrue(json.has("$schema"), "JSON should have $schema field");
    assertTrue(json.has("coordinateUnits"), "JSON should have coordinateUnits field");
    assertTrue(json.has("date"), "JSON should have date field");
    assertTrue(json.has("kicadVersion"), "JSON should have kicadVersion field");
    assertTrue(json.has("source"), "JSON should have source field");
    assertTrue(json.has("violations"), "JSON should have violations field");
    assertTrue(json.has("unconnectedItems"), "JSON should have unconnectedItems field");
    assertTrue(json.has("schematicParity"), "JSON should have schematicParity field");
  }
}
