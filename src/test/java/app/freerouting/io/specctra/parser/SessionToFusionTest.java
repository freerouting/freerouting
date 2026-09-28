package app.freerouting.io.specctra.parser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.io.specctra.SesReader;
import app.freerouting.io.specctra.SesWriter;
import app.freerouting.settings.GlobalSettings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link SessionToFusion} SCR script generation. */
class SessionToFusionTest {

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  @Test
  void testSessionToFusionScriptHeaderAndLayers() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue593-BBD_Mars-64.dsn");

    ByteArrayOutputStream sesOut = new ByteArrayOutputStream();
    SesWriter.write(board, sesOut, "Issue593-BBD_Mars-64.dsn");

    ByteArrayOutputStream scrOut = new ByteArrayOutputStream();
    InputStream sesIn = new ByteArrayInputStream(sesOut.toByteArray());

    boolean success = SesReader.saveSpecctraSessionSesAsFusionScriptScr(sesIn, scrOut, board);
    assertTrue(success, "SessionToFusion export should succeed");

    String script = scrOut.toString(StandardCharsets.UTF_8);

    // Verify grid and settings
    assertTrue(script.contains("GRID "), "Script must define GRID unit");
    assertTrue(script.contains("SET WIRE_BEND 2;\n"), "Script must set wire bend");
    assertTrue(script.contains("SET OPTIMIZING OFF;\n"), "Script must disable optimizing");

    // Verify standard layers are activated
    assertTrue(script.contains("LAYER 17;\n"), "Script must activate Pads layer 17");
    assertTrue(script.contains("LAYER 18;\n"), "Script must activate Vias layer 18");
    assertTrue(script.contains("LAYER 19;\n"), "Script must activate Unrouted layer 19");

    // Verify removed layers that previously caused Autodesk Fusion parser failures
    assertFalse(script.contains("LAYER 20;\n"), "Script must not activate non-copper layer 20");
    assertFalse(script.contains("LAYER 23;\n"), "Script must not activate non-copper layer 23");
    assertFalse(script.contains("LAYER 24;\n"), "Script must not activate non-copper layer 24");

    // Verify cleanup and finish commands
    assertTrue(script.contains("RIPUP;\n"), "Script must include RIPUP command");
    assertTrue(script.contains("RATSNEST;\n"), "Script must end with RATSNEST command");
  }

  @Test
  void testSessionToFusionThroughViaSyntax() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue593-BBD_Mars-64.dsn");

    ByteArrayOutputStream sesOut = new ByteArrayOutputStream();
    SesWriter.write(board, sesOut, "Issue593-BBD_Mars-64.dsn");

    ByteArrayOutputStream scrOut = new ByteArrayOutputStream();
    InputStream sesIn = new ByteArrayInputStream(sesOut.toByteArray());

    boolean success = SessionToFusion.getInstance(sesIn, scrOut, board);
    assertTrue(success, "SessionToFusion.getInstance should succeed");

    String script = scrOut.toString(StandardCharsets.UTF_8);

    // Through-vias must not emit invalid layer range like "1-304" or "1-16"
    assertFalse(
        script.contains(" 1-304 ("),
        "Standard through-vias must not emit extended layer range 1-304");
    assertFalse(
        script.contains(" 1-16 ("), "Standard through-vias must not emit legacy layer range 1-16");
  }

  @Test
  void testSessionToFusionWiresAndViasExported() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue593-BBD_Mars-64.dsn");

    // Load routed companion SES to populate wires on board
    InputStream sesInFixture = DsnTestFixtures.openResource("Issue593-BBD_Mars-64.ses");
    SesReader.read(sesInFixture, board);

    ByteArrayOutputStream sesOut = new ByteArrayOutputStream();
    SesWriter.write(board, sesOut, "Issue593-BBD_Mars-64.dsn");

    ByteArrayOutputStream scrOut = new ByteArrayOutputStream();
    InputStream sesIn = new ByteArrayInputStream(sesOut.toByteArray());

    boolean success = SesReader.saveSpecctraSessionSesAsFusionScriptScr(sesIn, scrOut, board);
    assertTrue(success, "SessionToFusion export should succeed");

    String script = scrOut.toString(StandardCharsets.UTF_8);

    // Verify wires and layer switches are emitted
    assertTrue(script.contains("CHANGE LAYER "), "Script must switch layers for wires");
    assertTrue(script.contains("WIRE '"), "Script must contain WIRE statements for routed nets");
    assertTrue(script.contains("VIA '"), "Script must contain VIA statements for routed vias");
  }

  @Test
  void testNullParametersReturnFalse() {
    assertFalse(SessionToFusion.getInstance(null, null, null));
  }
}
