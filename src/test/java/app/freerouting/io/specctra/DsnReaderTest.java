package app.freerouting.io.specctra;

import app.freerouting.Freerouting;
import app.freerouting.board.RoutingBoard;
import app.freerouting.settings.GlobalSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DsnReaderTest {

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  // Happy-path

  @Test
  void readBoardReturnsSuccess() {
    InputStream in = DsnTestFixtures.openResource("Issue143-rpi_splitter.dsn");
    DsnReadResult result = DsnReader.readBoard(in, null, null);

    assertInstanceOf(DsnReadResult.Success.class, result,
        "Expected Success for a well-formed DSN file");
    DsnReadResult.Success success = (DsnReadResult.Success) result;
    assertNotNull(success.board(), "Board must not be null on success");
    assertEquals(2, success.board().get_layer_count(),
        "Issue143-rpi_splitter.dsn is a 2-layer board");
  }

  @Test
  void readBoardSetsHostCad() {
    InputStream in = DsnTestFixtures.openResource("Issue143-rpi_splitter.dsn");
    DsnReadResult result = DsnReader.readBoard(in, null, null);

    assertInstanceOf(DsnReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((DsnReadResult.Success) result).board();
    assertNotNull(board.communication.specctra_parser_info,
        "SpecctraParserInfo must be populated");
  }

  // Parse-error path

  @Test
  void readBoardReturnsParseErrorForGarbage() {
    InputStream in = new ByteArrayInputStream("not a dsn file".getBytes(StandardCharsets.UTF_8));
    DsnReadResult result = DsnReader.readBoard(in, null, null);

    assertInstanceOf(DsnReadResult.ParseError.class, result,
        "Garbage input must produce ParseError");
  }

  @Test
  void readBoardReturnsParseErrorForNullStream() {
    DsnReadResult result = DsnReader.readBoard(null, null, null);
    assertInstanceOf(DsnReadResult.ParseError.class, result);
  }

  // OutlineMissing path — synthetic DSN with no (boundary ...) scope

  private static final String DSN_NO_BOUNDARY =
      "(pcb test\n"
          + "  (parser (string_quote \"))\n"
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
    DsnReadResult result = DsnReader.readBoard(in, null, null);

    assertInstanceOf(DsnReadResult.OutlineMissing.class, result,
        "A DSN file with no (boundary ...) scope must produce OutlineMissing");
  }

  // empty_board.dsn has a boundary and should succeed

  @Test
  void readBoardSucceedsForEmptyBoard() {
    InputStream in = DsnTestFixtures.openResource("empty_board.dsn");
    DsnReadResult result = DsnReader.readBoard(in, null, null);

    assertInstanceOf(DsnReadResult.Success.class, result,
        "empty_board.dsn has a valid boundary and must succeed");
  }

  // Sealed-switch exhaustiveness check (compile-time guarantee)

  @Test
  void patternSwitchIsExhaustive() {
    InputStream in = new ByteArrayInputStream("not dsn".getBytes(StandardCharsets.UTF_8));
    DsnReadResult result = DsnReader.readBoard(in, null, null);

    String label = switch (result) {
      case DsnReadResult.Success _        -> "success";
      case DsnReadResult.OutlineMissing _ -> "outline";
      case DsnReadResult.ParseError _     -> "parse";
      case DsnReadResult.IoError _        -> "io";
    };
    assertNotNull(label);
  }
}

