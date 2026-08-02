package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.BoardComparator;
import app.freerouting.board.RoutingBoard;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.kicad.KiCadJsonReader;
import app.freerouting.io.specctra.DsnReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class Issue733DsnJsonParityTest {

  private File findFixtureFile(String filename) {
    Path testDirectory = Path.of(".").toAbsolutePath();
    File testFile = Path.of(testDirectory.toString(), "fixtures", filename).toFile();
    while (!testFile.exists()) {
      testDirectory = testDirectory.getParent();
      if (testDirectory == null) {
        break;
      }
      testFile = Path.of(testDirectory.toString(), "fixtures", filename).toFile();
    }
    return testFile;
  }

  private RoutingBoard loadDsn(File file) throws Exception {
    try (InputStream is = new FileInputStream(file)) {
      BoardReadResult result = DsnReader.readBoard(is, null, null, file.getName());
      if (result instanceof BoardReadResult.Success success) {
        return (RoutingBoard) success.board();
      } else if (result instanceof BoardReadResult.OutlineMissing outlineMissing) {
        return (RoutingBoard) outlineMissing.board();
      }
    }
    throw new RuntimeException("Failed to load DSN file");
  }

  private RoutingBoard loadJson(File file) throws Exception {
    try (InputStream is = new FileInputStream(file);
         Reader r = new InputStreamReader(is, StandardCharsets.UTF_8)) {
      BoardReadResult result = KiCadJsonReader.readBoard(r, null, null);
      if (result instanceof BoardReadResult.Success success) {
        return (RoutingBoard) success.board();
      } else if (result instanceof BoardReadResult.OutlineMissing outlineMissing) {
        return (RoutingBoard) outlineMissing.board();
      }
    }
    throw new RuntimeException("Failed to load JSON file");
  }

  @Test
  @Disabled("Disabled due to known issue with DSN and JSON parity.")
  void testDsnJsonInputParityKiCadInterf() throws Exception {
    File dsnFile = findFixtureFile("Issue733-kicad_interf_u_input_design.dsn");
    File jsonFile = findFixtureFile("Issue733-kicad_interf_u_input_design.json");

    assertTrue(dsnFile.exists(), "DSN fixture file must exist");
    assertTrue(jsonFile.exists(), "JSON fixture file must exist");

    RoutingBoard dsnBoard = loadDsn(dsnFile);
    RoutingBoard jsonBoard = loadJson(jsonFile);

    BoardComparator.ComparisonResult result = BoardComparator.compare(dsnBoard, jsonBoard, 1e-3);
    System.out.println(result.report);

    assertTrue(result.areEqual, "Boards must be identical in representation:\n" + result.report);
  }

  @Test
  @Disabled("Disabled due to known issue with DSN and JSON parity.")
  void testDsnJsonInputParityKiCadComplexHierarchy() throws Exception {
    File dsnFile = findFixtureFile("Issue733-kicad_complex_hierarchy_input_design.dsn");
    File jsonFile = findFixtureFile("Issue733-kicad_complex_hierarchy_input_design.json");

    assertTrue(dsnFile.exists(), "DSN fixture file must exist");
    assertTrue(jsonFile.exists(), "JSON fixture file must exist");

    RoutingBoard dsnBoard = loadDsn(dsnFile);
    RoutingBoard jsonBoard = loadJson(jsonFile);

    BoardComparator.ComparisonResult result = BoardComparator.compare(dsnBoard, jsonBoard, 1e-3);
    System.out.println(result.report);

    assertTrue(result.areEqual, "Boards must be identical in representation:\n" + result.report);
  }

  @Test
  void testSesJsonOutputParityKiCadComplexHierarchy() throws Exception {
    File dsnFile = findFixtureFile("Issue733-kicad_complex_hierarchy_input_design.dsn");
    File sesFile = findFixtureFile("Issue733-kicad_complex_hierarchy_output_session.ses");
    File jsonSessionFile = findFixtureFile("Issue733-kicad_complex_hierarchy_output_session.json");

    assertTrue(dsnFile.exists(), "DSN fixture file must exist");
    assertTrue(sesFile.exists(), "SES fixture file must exist");
    assertTrue(jsonSessionFile.exists(), "JSON session fixture file must exist");

    // Load board from DSN for SES comparison
    RoutingBoard boardWithSes = loadDsn(dsnFile);
    try (FileInputStream sesStream = new FileInputStream(sesFile)) {
      app.freerouting.io.specctra.SesReader.read(sesStream, boardWithSes);
    }

    // Load board from DSN for JSON comparison
    RoutingBoard boardWithJson = loadDsn(dsnFile);
    try (InputStreamReader jsonReader = new InputStreamReader(new FileInputStream(jsonSessionFile), StandardCharsets.UTF_8)) {
      KiCadJsonReader.importSession(jsonReader, boardWithJson);
    }

    // Compare boards (SES is ground truth)
    BoardComparator.ComparisonResult result = BoardComparator.compare(boardWithSes, boardWithJson, 1e-3);
    System.out.println(result.report);

    assertTrue(result.areEqual, "SES and JSON routed outputs must be identical in representation:\n" + result.report);
  }
}