package app.freerouting.io.specctra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import app.freerouting.Freerouting;
import app.freerouting.board.DrillItem;
import app.freerouting.board.RoutingBoard;
import app.freerouting.settings.GlobalSettings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Round-trip tests for {@link SesWriter} and {@link SesReader}. */
class SesRoundTripTest {

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  /**
   * Verifies that a SES file produced alongside the BBD Mars-64 design can be imported and produces
   * at least one wire with no errors.
   */
  @Test
  void sesRoundTripPreservesWireCount() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue593-BBD_Mars-64.dsn");

    // Load its companion SES
    InputStream sesIn = DsnTestFixtures.openResource("Issue593-BBD_Mars-64.ses");
    SesImportSummary imported = SesReader.read(sesIn, board);

    assertTrue(
        imported.wiresImported() > 0,
        "At least one wire should be imported from the SES file; got: " + imported.wiresImported());
    assertEquals(
        0,
        imported.errorsEncountered(),
        "No errors should occur importing a valid SES file; got: " + imported.errorsEncountered());
  }

  /**
   * Verifies that {@link SesWriter} produces a SES file whose header and mandatory scopes are
   * syntactically correct.
   */
  @Test
  void sesWriterProducesValidHeader() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue026-J2_reference.dsn");

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    SesWriter.write(board, out, "Issue026-J2_reference.dsn");

    String content = out.toString(StandardCharsets.UTF_8);
    assertTrue(
        content.startsWith("(session "),
        "SES output must start with '(session '; got: "
            + content.substring(0, Math.min(50, content.length())));
    assertTrue(content.contains("(routes"), "SES output must contain '(routes' scope");
  }

  /**
   * Verifies that feeding syntactically garbage bytes to {@link SesReader} throws {@link
   * IOException} (it should not silently return a summary with 0 items).
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
    assertThrows(
        IOException.class,
        () -> SesReader.read(garbage, board),
        "SesReader.read must throw IOException for non-SES input");
  }

  /**
   * Verifies that a SES file written by {@link SesWriter} can be read back by {@link SesReader}
   * without errors (writer → reader round-trip).
   */
  @Test
  void writerOutputCanBeReadBackBySesReader() throws Exception {
    // Load a board that has actual wires so the SES file is non-trivial
    RoutingBoard source = DsnTestFixtures.loadBoard("Issue593-BBD_Mars-64.dsn");

    // Import its companion SES first so the board has routing data
    SesImportSummary originalImport;
    try (InputStream sesIn = DsnTestFixtures.openFixtureStream("Issue593-BBD_Mars-64.ses")) {
      originalImport = SesReader.read(sesIn, source);
    }
    assertTrue(originalImport.wiresImported() > 0, "Fixture SES must contain at least one wire");

    // Write the board's routing state to a byte array
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    SesWriter.write(source, out, "round-trip.dsn");
    assertTrue(out.size() > 0, "SesWriter must produce non-empty output");

    // Load a fresh board and read the written SES back into it
    RoutingBoard target = DsnTestFixtures.loadBoard("Issue593-BBD_Mars-64.dsn");
    InputStream rereadStream = new ByteArrayInputStream(out.toByteArray());
    SesImportSummary summary = SesReader.read(rereadStream, target);

    assertTrue(
        summary.wiresImported() > 0,
        "Re-imported SES must contain at least one wire; got: " + summary.wiresImported());
    assertEquals(
        0,
        summary.errorsEncountered(),
        "No errors expected on re-import of self-written SES; got: " + summary.errorsEncountered());
    // Verify no wires were silently dropped due to normalization failures inside
    // BasicBoard.insert_trace. A mismatch here means the writer produced geometry
    // that the reader could not fully restore.
    assertEquals(
        originalImport.wiresImported(),
        summary.wiresImported(),
        "Round-trip wire count must match the original import count; lost "
            + (originalImport.wiresImported() - summary.wiresImported())
            + " wire(s)");
  }

  /**
   * Verifies that passing a {@code null} stream to {@link SesReader#read} throws {@link
   * IOException} rather than a {@link NullPointerException}.
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

    assertThrows(
        IOException.class,
        () -> SesReader.read(null, board),
        "SesReader.read must throw IOException when stream is null");
  }

  /**
   * Verifies that passing a {@code null} board to {@link SesReader#read} throws {@link IOException}
   * rather than a {@link NullPointerException}.
   */
  @Test
  void nullBoardThrowsIoException() {
    InputStream in = new ByteArrayInputStream("(session x)".getBytes(StandardCharsets.UTF_8));
    assertThrows(
        IOException.class,
        () -> SesReader.read(in, null),
        "SesReader.read must throw IOException when board is null");
  }

  /** Verifies that {@link SesWriter} flushes data to the stream (non-zero output size). */
  @Test
  void sesWriterOutputIsNonEmpty() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue143-rpi_splitter.dsn");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    SesWriter.write(board, out, "test.dsn");
    assertTrue(out.size() > 0, "SesWriter must write data to the stream");
  }

  /**
   * Endpoint snapping: wire endpoints already at a contacted drill item's center must not be moved,
   * endpoints inside the pad inradius snap to the exact center, and rewriting a board with snapping
   * enabled keeps the session importable with the same wire count.
   */
  @Test
  void endpointSnappingIsStableAndRoundTrips() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue593-BBD_Mars-64.dsn");
    java.io.InputStream sesIn = DsnTestFixtures.openResource("Issue593-BBD_Mars-64.ses");
    SesImportSummary imported = SesReader.read(sesIn, board);
    assertTrue(imported.wiresImported() > 0);

    int tracesWithDrillContacts = 0;
    for (app.freerouting.board.Item item : board.get_items()) {
      if (!(item instanceof app.freerouting.board.PolylineTrace trace)) {
        continue;
      }
      for (boolean startSide : new boolean[] {true, false}) {
        var contacts = startSide ? trace.get_start_contacts() : trace.get_end_contacts();
        boolean hasDrillContact = contacts.stream().anyMatch(DrillItem.class::isInstance);
        if (!hasDrillContact) {
          continue;
        }
        tracesWithDrillContacts++;
        var corner = (startSide ? trace.first_corner() : trace.last_corner()).to_float();
        var snapped = SesWriter.snappedEndpoint(trace, startSide);
        if (snapped != null) {
          // A snap may only move the endpoint to the center of a contacted drill item,
          // and never further than that item's pad inradius.
          boolean isDrillCenter =
              contacts.stream()
                  .filter(DrillItem.class::isInstance)
                  .map(c -> ((DrillItem) c).get_center().to_float())
                  .anyMatch(center -> center.distance(snapped) < 0.5);
          assertTrue(isDrillCenter, "snap target must be a contacted drill item center");
          assertTrue(
              corner.distance(snapped) > 0.5, "null is expected for already-centered endpoints");
        }
      }
    }
    assertTrue(tracesWithDrillContacts > 0, "fixture must exercise drill-contacted endpoints");

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    SesWriter.write(board, out, "Issue593-BBD_Mars-64.dsn");
    RoutingBoard fresh = DsnTestFixtures.loadBoard("Issue593-BBD_Mars-64.dsn");
    SesImportSummary reimported =
        SesReader.read(new ByteArrayInputStream(out.toByteArray()), fresh);
    assertEquals(
        imported.wiresImported(),
        reimported.wiresImported(),
        "snapping must not change the wire count on re-import");
    assertEquals(0, reimported.errorsEncountered());
  }

  /**
   * Issue 742: KiCad must be able to consume Freerouting SES output for the tastexx keyboard board.
   * Verifies balanced syntax, unique library padstacks, KiCad-style rotation formatting, and a
   * writer-reader round-trip without errors.
   */
  @Test
  void issue742SesRoundTripsWithoutErrors() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue742-tastexx-pcb.dsn");

    SesImportSummary imported;
    try (InputStream sesIn = DsnTestFixtures.openFixtureStream("Issue742-tastexx-pcb.ses")) {
      imported = SesReader.read(sesIn, board);
    }
    assertTrue(imported.wiresImported() > 0, "fixture SES must contain routed wires");
    assertEquals(0, imported.errorsEncountered());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    SesWriter.write(board, out, "Issue742-tastexx-pcb.dsn");
    String content = out.toString(StandardCharsets.UTF_8);

    assertBalancedScopes(content);
    assertUniqueLibraryPadstacks(content);
    assertFalse(content.contains("0.000"), "whole-degree rotations must not use trailing decimals");
    assertTrue(
        content.contains("338.5") || content.contains(" front 339"),
        "fractional or rounded component rotations must be preserved in placement records");

    RoutingBoard fresh = DsnTestFixtures.loadBoard("Issue742-tastexx-pcb.dsn");
    SesImportSummary reimported =
        SesReader.read(new ByteArrayInputStream(out.toByteArray()), fresh);
    assertEquals(
        imported.wiresImported(),
        reimported.wiresImported(),
        "round-trip must preserve wire count");
    assertEquals(0, reimported.errorsEncountered());
  }

  /** Placement rotation formatting must match KiCad Specctra export conventions. */
  @Test
  void placementRotationFormattingMatchesKicadStyle() {
    assertEquals("0", SesWriter.formatPlacementRotation(0.0));
    assertEquals("339", SesWriter.formatPlacementRotation(339.0));
    assertEquals("338.5", SesWriter.formatPlacementRotation(338.5));
    assertEquals("-45.25", SesWriter.formatPlacementRotation(-45.25));
  }

  private static void assertBalancedScopes(String content) {
    long opens = content.chars().filter(ch -> ch == '(').count();
    long closes = content.chars().filter(ch -> ch == ')').count();
    assertEquals(opens, closes, "SES scopes must be balanced");
  }

  private static void assertUniqueLibraryPadstacks(String content) {
    int libraryStart = content.indexOf("(library_out");
    assertTrue(libraryStart >= 0, "SES must contain library_out scope");
    int networkStart = content.indexOf("(network_out", libraryStart);
    assertTrue(networkStart > libraryStart, "SES must contain network_out scope after library_out");

    String librarySection = content.substring(libraryStart, networkStart);
    Matcher matcher = Pattern.compile("\\(padstack\\s+([^\\s()]+)").matcher(librarySection);
    Set<String> padstackNames = new HashSet<>();
    while (matcher.find()) {
      String padstackName = matcher.group(1);
      assertTrue(
          padstackNames.add(padstackName),
          "library_out must not contain duplicate padstack entries: " + padstackName);
    }
    assertFalse(padstackNames.isEmpty(), "library_out must declare at least one via padstack");
  }
}
