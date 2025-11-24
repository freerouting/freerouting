package app.freerouting.drc;

import app.freerouting.Freerouting;
import app.freerouting.board.ItemIdentificationNumberGenerator;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.Session;
import app.freerouting.management.SessionManager;
import app.freerouting.settings.GlobalSettings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class DesignRulesCheckerTest
{
  @BeforeEach
  protected void setUp()
  {
    Freerouting.globalSettings = new GlobalSettings();
  }

  @Test
  void test_DrcReport_Structure()
  {
    // Create a simple routing job with a DSN file
    RoutingJob job = getRoutingJobFromTestFile("BBD_Mars-64.dsn");

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
  void test_DrcReport_JsonFormat()
  {
    // Create a simple routing job with a DSN file
    RoutingJob job = getRoutingJobFromTestFile("BBD_Mars-64.dsn");

    assertNotNull(job, "Job should not be null");
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

  private RoutingJob getRoutingJobFromTestFile(String filename)
  {
    // Create a new session
    UUID sessionId = UUID.randomUUID();
    Session session = SessionManager
        .getInstance()
        .createSession(sessionId, "Freerouting/" + Freerouting.VERSION_NUMBER_STRING);

    // Create a new job
    RoutingJob job = new RoutingJob(session.id);

    // Look for the file in the tests directory
    Path testDirectory = Path
        .of(".")
        .toAbsolutePath();
    File testFile = Path
        .of(testDirectory.toString(), "tests", filename)
        .toFile();

    while (!testFile.exists())
    {
      testDirectory = testDirectory.getParent();
      if (testDirectory == null)
      {
        fail("Test file not found: " + filename);
      }

      testFile = Path
          .of(testDirectory.toString(), "tests", filename)
          .toFile();
    }

    // Load the file as input
    try
    {
      job.setInput(testFile);

      // Load the board
      if (job.input.format == app.freerouting.gui.FileFormat.DSN)
      {
        app.freerouting.interactive.HeadlessBoardManager boardManager = new app.freerouting.interactive.HeadlessBoardManager(null, job);
        boardManager.loadFromSpecctraDsn(job.input.getData(), null, new ItemIdentificationNumberGenerator());
        job.board = boardManager.get_routing_board();
      }
    } catch (Exception e)
    {
      fail("Failed to load test file: " + e.getMessage());
    }

    return job;
  }
}