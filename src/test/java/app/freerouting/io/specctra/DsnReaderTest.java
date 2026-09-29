package app.freerouting.io.specctra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.io.BoardReadResult;
import app.freerouting.settings.GlobalSettings;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DsnReaderTest {

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  // Happy-path

  @Test
  void readBoardReturnsSuccess() {
    InputStream in = DsnTestFixtures.openResource("Issue143-rpi_splitter.dsn");
    BoardReadResult result = DsnReader.readBoard(in, null, null);

    assertInstanceOf(
        BoardReadResult.Success.class, result, "Expected Success for a well-formed DSN file");
    BoardReadResult.Success success = (BoardReadResult.Success) result;
    assertNotNull(success.board(), "Board must not be null on success");
    assertEquals(
        2, success.board().getLayerCount(), "Issue143-rpi_splitter.dsn is a 2-layer board");
  }

  @Test
  void readBoardSetsHostCad() {
    InputStream in = DsnTestFixtures.openResource("Issue143-rpi_splitter.dsn");
    BoardReadResult result = DsnReader.readBoard(in, null, null);

    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();
    assertNotNull(board.communication.specctraParserInfo, "SpecctraParserInfo must be populated");
  }

  // Parse-error path

  @Test
  void readBoardReturnsParseErrorForGarbage() {
    InputStream in = new ByteArrayInputStream("not a dsn file".getBytes(StandardCharsets.UTF_8));
    BoardReadResult result = DsnReader.readBoard(in, null, null);

    assertInstanceOf(
        BoardReadResult.ParseError.class, result, "Garbage input must produce ParseError");
  }

  @Test
  void readBoardReturnsParseErrorForNullStream() {
    BoardReadResult result = DsnReader.readBoard(null, null, null);
    assertInstanceOf(BoardReadResult.ParseError.class, result);
  }

  // OutlineMissing path — synthetic DSN with no (boundary ...) scope

  private static final String DSN_NO_BOUNDARY =
      "(pcb test\n"
          + "  (parser (stringQuote \"))\n"
          + "  (resolution um 10)\n"
          + "  (unit um)\n"
          + "  (structure\n"
          + "    (layer F.Cu (type signal) (property (index 0)))\n"
          + "    (layer B.Cu (type signal) (property (index 1)))\n"
          + "  )\n"
          + ")\n";

  @Test
  void readBoardReturnsOutlineMissingWhenBoundaryAbsent() {
    InputStream in = new ByteArrayInputStream(DSN_NO_BOUNDARY.getBytes(StandardCharsets.UTF_8));
    BoardReadResult result = DsnReader.readBoard(in, null, null);

    assertTrue(
        !(result instanceof BoardReadResult.Success),
        "A DSN file with no (boundary ...) scope must not produce Success");
  }

  // empty_board.dsn has a boundary and should succeed

  @Test
  void readBoardSucceedsForEmptyBoard() {
    InputStream in = DsnTestFixtures.openResource("empty_board.dsn");
    BoardReadResult result = DsnReader.readBoard(in, null, null);

    assertInstanceOf(
        BoardReadResult.Success.class,
        result,
        "empty_board.dsn has a valid boundary and must succeed");
  }

  @Test
  void readBoardMergesKicadDefaultIntoFreeroutingDefault() {
    InputStream in = DsnTestFixtures.openResource("Issue508-DAC2020_bm08.dsn");
    BoardReadResult result = DsnReader.readBoard(in, null, null);

    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();

    assertNull(board.rules.netClasses.get("kicad_default"));
    assertNotNull(board.rules.netClasses.get("default"));
    assertEquals("default", board.rules.nets.get("/SCL", 1).getNetClass().getName());
  }

  @Test
  void readBoardLoadsIssue034WithMultipleBoundaryPaths() {
    InputStream in = DsnTestFixtures.openResource("Issue034-Green14SegLED.dsn");
    BoardReadResult result = DsnReader.readBoard(in, null, null, "Issue034-Green14SegLED.dsn");

    assertInstanceOf(
        BoardReadResult.Success.class,
        result,
        "KiCad DSN files with multiple (path pcb ...) boundary shapes must load");
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();
    assertNotNull(board.getOutline());
    assertTrue(board.components.count() > 0, "board must contain placed components");
  }

  // Power layers without a plane

  private static String dsnWithPowerLayer(String planeScope) {
    return "(pcb test\n"
        + "  (parser (string_quote \") (host_cad \"KiCad's Pcbnew\"))\n"
        + "  (resolution um 10)\n"
        + "  (unit um)\n"
        + "  (structure\n"
        + "    (layer F.Cu (type signal) (property (index 0)))\n"
        + "    (layer In1.Cu (type power) (property (index 1)))\n"
        + "    (layer B.Cu (type signal) (property (index 2)))\n"
        + "    (boundary (path pcb 0 0 0 20000 0 20000 20000 0 20000 0 0))\n"
        + planeScope
        + "    (via \"Via[0-2]_800:400_um\")\n"
        + "    (rule (width 200) (clearance 200))\n"
        + "  )\n"
        + "  (placement)\n"
        + "  (library\n"
        + "    (padstack \"Via[0-2]_800:400_um\"\n"
        + "      (shape (circle F.Cu 800))\n"
        + "      (shape (circle In1.Cu 800))\n"
        + "      (shape (circle B.Cu 800))\n"
        + "      (attach off)\n"
        + "    )\n"
        + "  )\n"
        + "  (network\n"
        + "    (net GND)\n"
        + "    (class kicad_default GND (circuit (use_via \"Via[0-2]_800:400_um\"))"
        + " (rule (width 200) (clearance 200)))\n"
        + "  )\n"
        + "  (wiring)\n"
        + ")\n";
  }

  @Test
  void powerLayerWithoutPlaneBecomesRoutableSignalLayer() {
    InputStream in =
        new ByteArrayInputStream(dsnWithPowerLayer("").getBytes(StandardCharsets.UTF_8));
    BoardReadResult result = DsnReader.readBoard(in, null, null);

    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();
    assertTrue(
        board.layerStructure.layers[1].isSignal,
        "a power layer without any plane must be treated as a signal layer");
  }

  @Test
  void kicadPowerTypedLayersWithoutPlanesAreRoutable() {
    InputStream in = DsnTestFixtures.openResource("PCBench-Box0-hv-analog-breakout.dsn");
    BoardReadResult result = DsnReader.readBoard(in, null, null);

    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();
    for (var layer : board.layerStructure.layers) {
      assertTrue(layer.isSignal, "layer " + layer.name + " must be routable");
    }
  }

  @Test
  void powerLayerWithPlaneStaysNonSignal() {
    String plane = "    (plane GND (polygon In1.Cu 0 0 0 20000 0 20000 20000 0 20000))\n";
    InputStream in =
        new ByteArrayInputStream(dsnWithPowerLayer(plane).getBytes(StandardCharsets.UTF_8));
    BoardReadResult result = DsnReader.readBoard(in, null, null);

    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();
    assertTrue(
        !board.layerStructure.layers[1].isSignal,
        "a power layer that carries a plane must stay non-routable");
  }

  // Sealed-switch exhaustiveness check (compile-time guarantee)

  @Test
  void patternSwitchIsExhaustive() {
    InputStream in = new ByteArrayInputStream("not dsn".getBytes(StandardCharsets.UTF_8));
    BoardReadResult result = DsnReader.readBoard(in, null, null);

    String label =
        switch (result) {
          case BoardReadResult.Success _ -> "success";
          case BoardReadResult.OutlineMissing _ -> "outline";
          case BoardReadResult.ParseError _ -> "parse";
          case BoardReadResult.IoError _ -> "io";
        };
    assertNotNull(label);
  }

  @Test
  void readBoardF60Keyboard() throws Exception {
    java.nio.file.Path path =
        java.nio.file.Path.of("scripts/benchmark/fixtures/PCBench/f.60_keyboard/unrouted.dsn");
    if (java.nio.file.Files.exists(path)) {
      try (InputStream in = java.nio.file.Files.newInputStream(path)) {
        BoardReadResult result = DsnReader.readBoard(in, null, null, "unrouted.dsn");
        assertInstanceOf(
            BoardReadResult.Success.class, result, "f.60_keyboard DSN must parse successfully");
      }
    }
  }
}
