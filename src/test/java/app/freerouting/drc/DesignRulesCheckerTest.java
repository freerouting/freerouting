package app.freerouting.drc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import app.freerouting.Freerouting;
import app.freerouting.board.ItemIdentificationNumberGenerator;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.Session;
import app.freerouting.gui.FileFormat;
import app.freerouting.interactive.HeadlessBoardManager;
import app.freerouting.management.SessionManager;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.tests.TestBasedOnAnIssue;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DesignRulesCheckerTest extends TestBasedOnAnIssue {

  @BeforeEach
  protected void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  @Test
  void test_DrcReport_Structure() {
    // Create a simple routing job with a DSN file
    RoutingJob job = GetRoutingJob("BBD_Mars-64.dsn");

    assertNotNull(job, "Job should not be null");

    job.board = LoadDsnBoard(job);

    assertNotNull(job.board, "Board should be loaded");

    assertNotNull(job, "Job should not be null");
    assertNotNull(job.board, "Board should be loaded");

    // Create DRC checker
    DesignRulesChecker drcChecker = new DesignRulesChecker(job.board, Freerouting.globalSettings.drcSettings);

    // Generate report
    DrcReport report = drcChecker.generateReport("test.dsn", "mm");

    // Verify report structure
    assertNotNull(report, "Report should not be null");
    assertEquals("https://schemas.kicad.org/drc.v1.json", report.$schema, "Schema should match KiCad format");
    assertEquals("mm", report.coordinate_units, "Coordinate units should be mm");
    assertEquals("test.dsn", report.source, "Source should match");
    assertNotNull(report.violations, "Violations list should not be null");
    assertNotNull(report.unconnected_items, "Unconnected items list should not be null");
    assertNotNull(report.schematic_parity, "Schematic parity list should not be null");
    assertTrue(report.freerouting_version.contains("Freerouting"), "Version should contain Freerouting");
  }

  @Test
  void test_DrcReport_JsonFormat() {
    // Create a simple routing job with a DSN file
    RoutingJob job = GetRoutingJob("BBD_Mars-64.dsn");

    assertNotNull(job, "Job should not be null");

    job.board = LoadDsnBoard(job);

    assertNotNull(job.board, "Board should be loaded");

    // Create DRC checker
    DesignRulesChecker drcChecker = new DesignRulesChecker(job.board, Freerouting.globalSettings.drcSettings);

    // Generate JSON report
    String jsonReport = drcChecker.generateReportJson("test.dsn", "mm");

    // Verify JSON is valid
    assertNotNull(jsonReport, "JSON report should not be null");
    assertFalse(jsonReport.isEmpty(), "JSON report should not be empty");

    // Parse JSON to verify structure
    JsonObject json = JsonParser
        .parseString(jsonReport)
        .getAsJsonObject();
    assertTrue(json.has("$schema"), "JSON should have $schema field");
    assertTrue(json.has("coordinate_units"), "JSON should have coordinate_units field");
    assertTrue(json.has("date"), "JSON should have date field");
    assertTrue(json.has("kicad_version"), "JSON should have kicad_version field");
    assertTrue(json.has("source"), "JSON should have source field");
    assertTrue(json.has("violations"), "JSON should have violations field");
    assertTrue(json.has("unconnected_items"), "JSON should have unconnected_items field");
    assertTrue(json.has("schematic_parity"), "JSON should have schematic_parity field");
  }
}