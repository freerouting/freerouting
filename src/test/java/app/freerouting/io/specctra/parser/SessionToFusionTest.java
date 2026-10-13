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
  void testEaglePadstackDecimalDrillPreserved() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue143-rpi_splitter.dsn");
    app.freerouting.core.library.Padstack padstack =
        board.library.padstacks.get("Round1$13.779528");
    org.junit.jupiter.api.Assertions.assertNotNull(
        padstack, "Padstack decimal fraction must not be stripped when loading DSN");
    org.junit.jupiter.api.Assertions.assertEquals("Round1$13.779528", padstack.name);
  }

  @Test
  void testSessionToFusionEagleViaPreservesDrillAndNeverOutputsZero() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue143-rpi_splitter.dsn");

    // Add a metric Eagle via: Round1$0.35
    app.freerouting.core.library.Padstack defaultPadstack =
        board.library.padstacks.get("Round1$13.779528");
    org.junit.jupiter.api.Assertions.assertNotNull(defaultPadstack);

    app.freerouting.geometry.planar.ConvexShape[] shapes =
        new app.freerouting.geometry.planar.ConvexShape[board.layerStructure.layers.length];
    for (int i = 0; i < shapes.length; i++) {
      shapes[i] = defaultPadstack.getShape(i);
    }
    app.freerouting.core.library.Padstack metricViaPadstack =
        board.library.padstacks.add("Round1$0.35", shapes, true, false);

    // Insert vias on board: one with 13.779528 and one with 0.35
    board.insertVia(
        defaultPadstack,
        app.freerouting.geometry.planar.Point.getInstance(1000, 1000),
        new int[] {1},
        1,
        app.freerouting.board.model.structure.FixedState.USER_FIXED,
        true);
    board.insertVia(
        metricViaPadstack,
        app.freerouting.geometry.planar.Point.getInstance(2000, 2000),
        new int[] {1},
        1,
        app.freerouting.board.model.structure.FixedState.USER_FIXED,
        true);

    ByteArrayOutputStream sesOut = new ByteArrayOutputStream();
    SesWriter.write(board, sesOut, "Issue143-rpi_splitter.dsn");

    ByteArrayOutputStream scrOut = new ByteArrayOutputStream();
    InputStream sesIn = new ByteArrayInputStream(sesOut.toByteArray());

    boolean success = SesReader.saveSpecctraSessionSesAsFusionScriptScr(sesIn, scrOut, board);
    assertTrue(success, "SessionToFusion export should succeed");

    String script = scrOut.toString(StandardCharsets.UTF_8);

    // Drill from padstack names must be preserved exactly
    assertTrue(
        script.contains("CHANGE DRILL 13.779528;\n"), "Script must preserve mil drill 13.779528");
    assertTrue(script.contains("CHANGE DRILL 0.35;\n"), "Script must preserve metric drill 0.35");
    assertFalse(
        script.contains("CHANGE DRILL 0;\n"), "Script must never output invalid pad drill '0'");
    assertFalse(
        script.contains("CHANGE DRILL 0.0;\n"), "Script must never output invalid pad drill '0.0'");
  }

  @Test
  void testSessionToFusionFallbackWhenViaDrillInNameIsZero() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue143-rpi_splitter.dsn");

    app.freerouting.core.library.Padstack defaultPadstack =
        board.library.padstacks.get("Round1$13.779528");
    org.junit.jupiter.api.Assertions.assertNotNull(defaultPadstack);

    app.freerouting.geometry.planar.ConvexShape[] shapes =
        new app.freerouting.geometry.planar.ConvexShape[board.layerStructure.layers.length];
    for (int i = 0; i < shapes.length; i++) {
      shapes[i] = defaultPadstack.getShape(i);
    }
    // Via padstack explicitly named with drill 0: e.g. "Round1$0"
    app.freerouting.core.library.Padstack zeroDrillPadstack =
        board.library.padstacks.add("Round1$0", shapes, true, false);

    board.insertVia(
        zeroDrillPadstack,
        app.freerouting.geometry.planar.Point.getInstance(3000, 3000),
        new int[] {1},
        1,
        app.freerouting.board.model.structure.FixedState.USER_FIXED,
        true);

    ByteArrayOutputStream sesOut = new ByteArrayOutputStream();
    SesWriter.write(board, sesOut, "Issue143-rpi_splitter.dsn");

    ByteArrayOutputStream scrOut = new ByteArrayOutputStream();
    InputStream sesIn = new ByteArrayInputStream(sesOut.toByteArray());

    boolean success = SesReader.saveSpecctraSessionSesAsFusionScriptScr(sesIn, scrOut, board);
    assertTrue(success, "SessionToFusion export should succeed");

    String script = scrOut.toString(StandardCharsets.UTF_8);

    // Zero drill must be caught and replaced with positive drill diameter fallback
    assertFalse(
        script.contains("CHANGE DRILL 0;\n"), "Script must never output invalid pad drill '0'");
    assertFalse(
        script.contains("CHANGE DRILL 0.0;\n"), "Script must never output invalid pad drill '0.0'");
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile("CHANGE DRILL ([0-9.]+);").matcher(script);
    assertTrue(matcher.find(), "Script must emit a CHANGE DRILL command");
    double emittedDrill = Double.parseDouble(matcher.group(1));
    assertTrue(emittedDrill > 0, "Emitted fallback drill diameter must be strictly positive");
  }

  @Test
  void testSessionToFusionFallbackWhenViaDrillInNameIsNonFinite() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue143-rpi_splitter.dsn");

    app.freerouting.core.library.Padstack defaultPadstack =
        board.library.padstacks.get("Round1$13.779528");
    org.junit.jupiter.api.Assertions.assertNotNull(defaultPadstack);

    app.freerouting.geometry.planar.ConvexShape[] shapes =
        new app.freerouting.geometry.planar.ConvexShape[board.layerStructure.layers.length];
    for (int i = 0; i < shapes.length; i++) {
      shapes[i] = defaultPadstack.getShape(i);
    }
    app.freerouting.core.library.Padstack nonFiniteDrillPadstack =
        board.library.padstacks.add("Round1$Infinity", shapes, true, false);

    board.insertVia(
        nonFiniteDrillPadstack,
        app.freerouting.geometry.planar.Point.getInstance(3000, 3000),
        new int[] {1},
        1,
        app.freerouting.board.model.structure.FixedState.USER_FIXED,
        true);

    ByteArrayOutputStream sesOut = new ByteArrayOutputStream();
    SesWriter.write(board, sesOut, "Issue143-rpi_splitter.dsn");

    ByteArrayOutputStream scrOut = new ByteArrayOutputStream();
    InputStream sesIn = new ByteArrayInputStream(sesOut.toByteArray());

    boolean success = SesReader.saveSpecctraSessionSesAsFusionScriptScr(sesIn, scrOut, board);
    assertTrue(success, "SessionToFusion export should succeed");

    String script = scrOut.toString(StandardCharsets.UTF_8);

    assertFalse(script.contains("Infinity"), "Script must never output non-finite drill");
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile("CHANGE DRILL ([0-9.]+);").matcher(script);
    assertTrue(matcher.find(), "Script must emit a CHANGE DRILL command");
    double emittedDrill = Double.parseDouble(matcher.group(1));
    assertTrue(emittedDrill > 0, "Emitted fallback drill diameter must be strictly positive");
  }

  @Test
  void testNullParametersReturnFalse() {
    assertFalse(SessionToFusion.getInstance(null, null, null));
  }
}
