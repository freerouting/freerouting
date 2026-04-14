package app.freerouting.designforms.specctra.io;

import app.freerouting.Freerouting;
import app.freerouting.board.RoutingBoard;
import app.freerouting.settings.GlobalSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip tests for {@link SesWriter} and {@link SesReader}.
 */
class SesRoundTripTest {

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  /**
   * Verifies that a SES file produced alongside the BBD Mars-64 design can be
   * imported and produces at least one wire with no errors.
   */
  @Test
  void sesRoundTripPreservesWireCount() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue593-BBD_Mars-64.dsn");

    // Load its companion SES
    InputStream sesIn = DsnTestFixtures.openResource("Issue593-BBD_Mars-64.ses");
    SesImportSummary imported = SesReader.read(sesIn, board);

    assertTrue(imported.wiresImported() > 0,
        "At least one wire should be imported from the SES file; got: " + imported.wiresImported());
    assertEquals(0, imported.errorsEncountered(),
        "No errors should occur importing a valid SES file; got: " + imported.errorsEncountered());
  }

  /**
   * Verifies that {@link SesWriter} produces a SES file whose header and
   * mandatory scopes are syntactically correct.
   */
  @Test
  void sesWriterProducesValidHeader() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue026-J2_reference.dsn");

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    SesWriter.write(board, out, "Issue026-J2_reference.dsn");

    String content = out.toString(StandardCharsets.UTF_8);
    assertTrue(content.startsWith("(session "),
        "SES output must start with '(session '; got: " + content.substring(0, Math.min(50, content.length())));
    assertTrue(content.contains("(routes"),
        "SES output must contain '(routes' scope");
  }

  /**
   * Verifies that feeding syntactically garbage bytes to {@link SesReader} throws
   * {@link IOException} (it should not silently return a summary with 0 items).
   */
  @Test
  void invalidSesThrowsOnRead() {
    RoutingBoard board;
    try {
      board = DsnTestFixtures.loadBoard("Issue143-rpi_splitter.dsn");
    } catch (IOException e) {
      fail("Failed to load board fixture: " + e.getMessage());
      return;
    }

    InputStream garbage = new ByteArrayInputStream("garbage".getBytes(StandardCharsets.UTF_8));
    assertThrows(IOException.class, () -> SesReader.read(garbage, board),
        "SesReader.read must throw IOException for non-SES input");
  }

  /**
   * Verifies that a SES file written by {@link SesWriter} can be read back by
   * {@link SesReader} without errors (writer → reader round-trip).
   */
  @Test
  void writerOutputCanBeReadBackBySesReader() throws Exception {
    // Load a board that has actual wires so the SES file is non-trivial
    RoutingBoard source = DsnTestFixtures.loadBoard("Issue593-BBD_Mars-64.dsn");

    // Import its companion SES first so the board has routing data
    try (InputStream sesIn = DsnTestFixtures.openFixtureStream("Issue593-BBD_Mars-64.ses")) {
      SesReader.read(sesIn, source);
    }

    // Write the board's routing state to a byte array
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    SesWriter.write(source, out, "round-trip.dsn");
    assertTrue(out.size() > 0, "SesWriter must produce non-empty output");

    // Load a fresh board and read the written SES back into it
    RoutingBoard target = DsnTestFixtures.loadBoard("Issue593-BBD_Mars-64.dsn");
    InputStream rereadStream = new ByteArrayInputStream(out.toByteArray());
    SesImportSummary summary = SesReader.read(rereadStream, target);

    assertTrue(summary.wiresImported() > 0,
        "Re-imported SES must contain at least one wire; got: " + summary.wiresImported());
    assertEquals(0, summary.errorsEncountered(),
        "No errors expected on re-import of self-written SES; got: " + summary.errorsEncountered());
  }

  /**
   * Verifies that passing a {@code null} stream to {@link SesReader#read} throws
   * {@link IOException} rather than a {@link NullPointerException}.
   */
  @Test
  void nullInputStreamThrowsIoException() {
    RoutingBoard board;
    try {
      board = DsnTestFixtures.loadBoard("Issue143-rpi_splitter.dsn");
    } catch (IOException e) {
      fail("Failed to load board fixture: " + e.getMessage());
      return;
    }

    assertThrows(IOException.class, () -> SesReader.read(null, board),
        "SesReader.read must throw IOException when stream is null");
  }

  /**
   * Verifies that passing a {@code null} board to {@link SesReader#read} throws
   * {@link IOException} rather than a {@link NullPointerException}.
   */
  @Test
  void nullBoardThrowsIoException() {
    InputStream in = new ByteArrayInputStream("(session x)".getBytes(StandardCharsets.UTF_8));
    assertThrows(IOException.class, () -> SesReader.read(in, null),
        "SesReader.read must throw IOException when board is null");
  }

  /**
   * Verifies that {@link SesWriter} flushes data to the stream (non-zero output size).
   */
  @Test
  void sesWriterOutputIsNonEmpty() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue143-rpi_splitter.dsn");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    SesWriter.write(board, out, "test.dsn");
    assertTrue(out.size() > 0, "SesWriter must write data to the stream");
  }
}

