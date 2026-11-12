package app.freerouting.board.model.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.TestFixtures;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.io.specctra.RulesReader;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/**
 * The slices of an oblique outline line follow the smallest trace width of the board when a rules
 * file read after the board lowers it.
 *
 * <p>The outline of this board has a 5:1 edge. Its trace width is 1 mm, so the slices of that edge
 * are cut for a 0.5 mm tolerance. A rules file that sets 0.1 mm traces must cut them again, or
 * their bounding octagons keep reaching into the board further than a 0.1 mm trace can pass. The
 * same holds for a width that only a net class sets, in the rules file or in the DSN itself.
 */
class OutlineSliceTraceWidthTest {

  private static final String NARROW_TRACES =
      "(rules PCB oblique-board-edge.dsn (rule (width 100)))";

  private static final String NARROW_CLASS =
      "(rules PCB oblique-board-edge.dsn (class narrow LINK (rule (width 100))))";

  @Test
  void rulesFileWithNarrowerTracesCutsTheOutlineAgain() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("oblique-board-edge.dsn");
    BoardOutline outline = board.getOutline();
    int slicesBefore = outline.lineSliceCount();
    assertTreeEntriesMatch(outline, board);

    boolean read =
        RulesReader.read(
            new ByteArrayInputStream(NARROW_TRACES.getBytes(StandardCharsets.UTF_8)),
            "oblique-board-edge.dsn",
            board,
            null);

    assertTrue(read);
    assertTrue(
        outline.lineSliceCount() > slicesBefore,
        () -> "slices before " + slicesBefore + ", after " + outline.lineSliceCount());
    assertTreeEntriesMatch(outline, board);
  }

  @Test
  void rulesFileWithNarrowerNetClassCutsTheOutlineAgain() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("oblique-board-edge.dsn");
    BoardOutline outline = board.getOutline();
    int slicesBefore = outline.lineSliceCount();

    boolean read =
        RulesReader.read(
            new ByteArrayInputStream(NARROW_CLASS.getBytes(StandardCharsets.UTF_8)),
            "oblique-board-edge.dsn",
            board,
            null);

    assertTrue(read);
    assertTrue(
        outline.lineSliceCount() > slicesBefore,
        () -> "slices before " + slicesBefore + ", after " + outline.lineSliceCount());
    assertTreeEntriesMatch(outline, board);
  }

  @Test
  void dsnNetClassNarrowerThanTheDefaultWidthCutsTheOutline() throws Exception {
    String dsn = Files.readString(TestFixtures.resolveFile("oblique-board-edge.dsn").toPath());
    int classStart = dsn.indexOf("(class kicad_default");
    String narrowDsn =
        dsn.substring(0, classStart)
            + dsn.substring(classStart).replaceFirst("\\(width 1000\\)", "(width 100)");
    RoutingBoard wide = DsnTestFixtures.loadBoard("oblique-board-edge.dsn");
    RoutingBoard narrow = loadBoard(narrowDsn);

    assertTrue(
        narrow.getOutline().lineSliceCount() > wide.getOutline().lineSliceCount(),
        () ->
            "slices with a 1 mm class "
                + wide.getOutline().lineSliceCount()
                + ", with a 0.1 mm class "
                + narrow.getOutline().lineSliceCount());
    assertTreeEntriesMatch(narrow.getOutline(), narrow);
  }

  @Test
  void unchangedTraceWidthsKeepTheSlices() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("oblique-board-edge.dsn");
    BoardOutline outline = board.getOutline();
    int slicesBefore = outline.lineSliceCount();
    var entriesBefore = outline.getSearchTreeEntries(board.searchTreeManager.getDefaultTree());

    outline.updateSlicesToTraceWidths();

    assertEquals(slicesBefore, outline.lineSliceCount());
    assertTrue(
        entriesBefore == outline.getSearchTreeEntries(board.searchTreeManager.getDefaultTree()),
        "the outline was reinserted although no trace width changed");
  }

  private static RoutingBoard loadBoard(String dsn) {
    BoardReadResult result =
        DsnReader.readBoard(
            new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8)), null, null);
    return (RoutingBoard) ((BoardReadResult.Success) result).board();
  }

  /** Every tree entry of the outline maps to a layer, which needs the slice count to be current. */
  private static void assertTreeEntriesMatch(BoardOutline outline, RoutingBoard board) {
    var entries = outline.getSearchTreeEntries(board.searchTreeManager.getDefaultTree());
    assertEquals(outline.tileShapeCount(), entries.length);
  }
}
