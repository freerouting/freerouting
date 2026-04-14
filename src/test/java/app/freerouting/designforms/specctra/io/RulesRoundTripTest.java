package app.freerouting.designforms.specctra.io;

import app.freerouting.Freerouting;
import app.freerouting.board.RoutingBoard;
import app.freerouting.settings.GlobalSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class RulesRoundTripTest {

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  @Test
  void rulesRoundTrip() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue029-hw48na.dsn");

    // Write rules
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    RulesWriter.write(board, out, "Issue029-hw48na");

    // Verify file header
    String content = out.toString(StandardCharsets.UTF_8);
    assertTrue(content.contains("(rules PCB"), "Rules file must start with (rules PCB ...)");
    assertTrue(content.contains("(rule"), "Rules file must contain at least one (rule ...) scope");

    // Read back rules — should not throw
    InputStream in = new ByteArrayInputStream(out.toByteArray());
    boolean ok = RulesReader.read(in, "Issue029-hw48na", board);
    assertTrue(ok, "RulesReader.read must return true on valid input");
  }

  @Test
  void invalidRulesFileReturnsFalse() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue029-hw48na.dsn");
    InputStream garbage = new ByteArrayInputStream("not a rules file".getBytes(StandardCharsets.UTF_8));
    boolean ok = RulesReader.read(garbage, "x", board);
    assertFalse(ok, "RulesReader.read must return false for garbage input");
  }

  @Test
  void readExistingRulesFixture() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue029-hw48na.dsn");
    InputStream in = DsnTestFixtures.openResource("Issue029-hw48na_valid.rules");
    assertTrue(RulesReader.read(in, "hw48na", board),
        "RulesReader.read must return true for a known-valid rules fixture");
  }

  /**
   * Loading Issue029-hw48na.dsn must complete quickly (not hang in an infinite loop) and expose
   * at least one warning about the degenerate zero-length wire that is present in the file.
   */
  @Test
  void loadingProducesWarningsForDegenerateWires() throws Exception {
    InputStream stream = DsnTestFixtures.openFixtureStream("Issue029-hw48na.dsn");
    DsnReadResult result = DsnReader.readBoard(stream, null, null);

    // Board must load successfully (or at least partially)
    assertInstanceOf(DsnReadResult.Success.class, result,
        "Expected a successful read; got: " + result);

    DsnReadResult.Success success = (DsnReadResult.Success) result;
    assertNotNull(success.board(), "Board must not be null after a successful read");

    // The DSN file contains at least one wire with duplicate/identical coordinates
    // (e.g. "path F.Cu 1066.8  42530 -100482  42530 -100482"). That wire must be
    // reported as a warning so the caller can diagnose the source file.
    boolean hasDegenerateWireWarning = success.warnings().stream()
        .anyMatch(w -> w.contains("degenerate wire") || w.contains("all corners identical"));
    assertTrue(hasDegenerateWireWarning,
        "Expected a 'degenerate wire' warning; got: " + success.warnings());
  }
}
