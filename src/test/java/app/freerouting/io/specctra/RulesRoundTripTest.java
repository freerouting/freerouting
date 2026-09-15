package app.freerouting.io.specctra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.io.BoardReadResult;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.RouterSettings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
  void rulesRoundTripWithAutorouteSettings() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue029-hw48na.dsn");

    RouterSettings settings = new RouterSettings();
    settings.setLayerCount(board.getLayerCount());
    settings.setViaCosts(75);
    settings.setPlaneViaCosts(8);
    settings.setStartRipupCosts(120);
    settings.setLayerActive(0, true);
    settings.setLayerActive(1, true);
    settings.setPreferredDirectionIsHorizontal(0, true);
    settings.setPreferredDirectionIsHorizontal(1, false);
    settings.setPreferredDirectionTraceCosts(0, 1.2);
    settings.setAgainstPreferredDirectionTraceCosts(0, 2.8);
    settings.setPreferredDirectionTraceCosts(1, 1.0);
    settings.setAgainstPreferredDirectionTraceCosts(1, 3.1);

    // Write rules with autoroute settings
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    RulesWriter.write(board, settings, out, "Issue029-hw48na");

    String content = out.toString(StandardCharsets.UTF_8);
    assertTrue(
        content.contains("(autoroute_settings"),
        "Rules file must contain autoroute_settings scope");
    assertTrue(content.contains("(layer_rule"), "Rules file must contain layer_rule scopes");

    // Read back rules and verify settings are populated
    RouterSettings targetSettings = new RouterSettings();
    InputStream in = new ByteArrayInputStream(out.toByteArray());
    boolean ok = RulesReader.read(in, "Issue029-hw48na", board, targetSettings);
    assertTrue(ok, "RulesReader.read must return true on valid input");

    assertEquals(75, targetSettings.getViaCosts());
    assertEquals(8, targetSettings.getPlaneViaCosts());
    assertEquals(120, targetSettings.getStartRipupCosts());
    assertTrue(targetSettings.getPreferredDirectionIsHorizontal(0));
    assertFalse(targetSettings.getPreferredDirectionIsHorizontal(1));
    assertEquals(1.2, targetSettings.getPreferredDirectionTraceCosts(0));
    assertEquals(2.8, targetSettings.getAgainstPreferredDirectionTraceCosts(0));
    assertEquals(1.0, targetSettings.getPreferredDirectionTraceCosts(1));
    assertEquals(3.1, targetSettings.getAgainstPreferredDirectionTraceCosts(1));
  }

  @Test
  void invalidRulesFileReturnsFalse() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue029-hw48na.dsn");
    InputStream garbage =
        new ByteArrayInputStream("not a rules file".getBytes(StandardCharsets.UTF_8));
    boolean ok = RulesReader.read(garbage, "x", board);
    assertFalse(ok, "RulesReader.read must return false for garbage input");
  }

  @Test
  void readExistingRulesFixture() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue029-hw48na.dsn");
    InputStream in = DsnTestFixtures.openResource("Issue029-hw48na_valid.rules");
    assertTrue(
        RulesReader.read(in, "hw48na", board),
        "RulesReader.read must return true for a known-valid rules fixture");
  }

  /**
   * Loading Issue029-hw48na.dsn must complete quickly (not hang in an infinite loop) and expose at
   * least one warning about the degenerate zero-length wire that is present in the file.
   */
  @Test
  void loadingProducesWarningsForDegenerateWires() throws Exception {
    InputStream stream = DsnTestFixtures.openFixtureStream("Issue029-hw48na.dsn");
    BoardReadResult result = DsnReader.readBoard(stream, null, null);

    // Board must load successfully (or at least partially)
    assertInstanceOf(
        BoardReadResult.Success.class, result, "Expected a successful read; got: " + result);

    BoardReadResult.Success success = (BoardReadResult.Success) result;
    assertNotNull(success.board(), "Board must not be null after a successful read");

    // The DSN file contains at least one wire with duplicate/identical coordinates
    // (e.g. "path F.Cu 1066.8  42530 -100482  42530 -100482"). That wire must be
    // reported as a warning so the caller can diagnose the source file.
    boolean hasDegenerateWireWarning =
        success.warnings().stream()
            .anyMatch(w -> w.contains("degenerate wire") || w.contains("all corners identical"));
    assertTrue(
        hasDegenerateWireWarning,
        "Expected a 'degenerate wire' warning; got: " + success.warnings());
  }
}
