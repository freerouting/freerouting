package app.freerouting.io.specctra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.datastructures.IdentifierType;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.FileFormat;
import app.freerouting.io.specctra.parser.IJFlexScanner;
import app.freerouting.io.specctra.parser.Keyword;
import app.freerouting.io.specctra.parser.SpecctraDsnStreamReader;
import app.freerouting.rules.NetClass;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests verifying fixes for Specctra DSN & SES format and via-rule defects. */
class SpecctraFormatDefectsTest {

  private static RoutingBoard loadBoard(String dsn) {
    BoardReadResult result =
        DsnReader.readBoard(
            new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8)), null, null);
    assertTrue(result instanceof BoardReadResult.Success);
    return (RoutingBoard) ((BoardReadResult.Success) result).board();
  }

  @Test
  void testIdentifierTypeStripsQuotesCorrectly_FR030() {
    IdentifierType idType = new IdentifierType(new String[] {" ", "-", ":"}, "\"");
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);

    idType.write("\"myNet\"", writer);
    try {
      writer.flush();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    assertEquals("myNet", baos.toString(StandardCharsets.UTF_8));
  }

  @ParameterizedTest
  @CsvSource({"1.1e+06, 1100000.0", "-1.1e+06, -1100000.0", "1E+3, 1000.0", "2e-1, 0.2"})
  void testNextDoubleSupportsScientificNotation_FR070(String token, double expected) {
    String input = "(" + token + " )";
    IJFlexScanner scanner =
        new SpecctraDsnStreamReader(
            new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    try {
      scanner.nextToken(); // consume '('
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    Double val = scanner.nextDouble();
    assertNotNull(val);
    assertEquals(expected, val, 1e-6);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1e", "1e+", "1e3junk", "1e999"})
  void testNextDoubleRejectsMalformedExponents_FR070(String token) {
    String input = "(" + token + " )";
    IJFlexScanner scanner =
        new SpecctraDsnStreamReader(
            new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    try {
      scanner.nextToken();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    Double val = scanner.nextDouble();
    assertNull(val);
  }

  @Test
  void testNextTokenParsesPaddedExponents_FR071() throws IOException {
    String input = "(1.1e+006)";
    IJFlexScanner scanner =
        new SpecctraDsnStreamReader(
            new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    assertEquals(Keyword.OPEN_BRACKET, scanner.nextToken());
    Object num = scanner.nextToken();
    assertTrue(num instanceof Double);
    assertEquals(1100000.0, ((Double) num).doubleValue(), 1e-6);
    assertEquals(Keyword.CLOSED_BRACKET, scanner.nextToken());
  }

  @Test
  void testNextStringHandlesTabDelimiters_FR037() {
    String input = "foo\tbar\tbaz";
    SpecctraDsnStreamReader scanner =
        new SpecctraDsnStreamReader(
            new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    assertEquals("foo", scanner.nextString());
    assertEquals("bar", scanner.nextString());
    assertEquals("baz", scanner.nextString());
  }

  @Test
  void testRoutingJobDetectsDsnWithLeadingLineBreaks_FR040() {
    byte[] content = "\r\n\r\n\r\n\r\n\r\n\r\n(pcb test)".getBytes(StandardCharsets.UTF_8);
    assertEquals(FileFormat.DSN, RoutingJob.getFileFormat(content));
  }

  @Test
  void testSesRoundTripUsesOwnRoutesResolution_FR068() throws IOException {
    String dsn =
        """
        (pcb units
          (resolution mm 100)
          (structure
            (layer top (type signal))
            (layer bottom (type signal))
            (boundary (rect pcb 0 0 100 100))
            (rule (width 0.2) (clearance 0.1)))
          (library (padstack V (shape (circle top 0.6)) (shape (circle bottom 0.6))))
          (network (net N)))
        """;
    BasicBoard board = loadBoard(dsn);

    String ses =
        """
        (session units
          (placement (resolution inch 1))
          (routes (resolution mm 1000)
            (network_out (net N
              (wire (path top 200 10000 10000 20000 10000))
              (via V 20000 10000)))))
        """;
    SesImportSummary summary =
        SesReader.read(new ByteArrayInputStream(ses.getBytes(StandardCharsets.UTF_8)), board);
    assertEquals(0, summary.errorsEncountered());
    assertEquals(1, summary.wiresImported());
    assertEquals(1, summary.viasImported());

    Trace trace = board.getTraces().iterator().next();
    assertEquals(1000, trace.getLength());
    assertEquals(10, trace.getHalfWidth());
  }

  @Test
  void testSesRejectsInvalidRoutesResolution_FR068() {
    String dsn =
        """
        (pcb units
          (resolution mm 100)
          (structure
            (layer top (type signal))
            (layer bottom (type signal))
            (boundary (rect pcb 0 0 100 100))
            (rule (width 0.2) (clearance 0.1)))
          (library (padstack V (shape (circle top 0.6)) (shape (circle bottom 0.6))))
          (network (net N)))
        """;
    BasicBoard board = loadBoard(dsn);

    String invalidSes = "(session units (routes (resolution mm 0)))";
    assertThrows(
        IOException.class,
        () ->
            SesReader.read(
                new ByteArrayInputStream(invalidSes.getBytes(StandardCharsets.UTF_8)), board));
  }

  @Test
  void testSesReaderAssignsNetClassClearance_FR075() throws IOException {
    String dsn =
        """
        (pcb classes
          (resolution um 10)
          (unit um)
          (structure
            (layer top (type signal))
            (layer bottom (type signal))
            (boundary (rect pcb 0 0 100000 100000))
            (via V)
            (rule (width 250) (clearance 200)))
          (library (padstack V (shape (circle top 600)) (shape (circle bottom 600))))
          (network (net N) (net D)
            (class diff D (circuit (use_via V)) (rule (width 160) (clearance 160)))))
        """;
    BasicBoard board = loadBoard(dsn);

    String ses =
        """
        (session classes
          (routes (resolution um 10)
            (network_out
              (net D (wire (path top 1600 100000 100000 200000 100000)) (via V 200000 100000))
              (net N (wire (path top 2500 100000 300000 200000 300000))))))
        """;
    SesImportSummary summary =
        SesReader.read(new ByteArrayInputStream(ses.getBytes(StandardCharsets.UTF_8)), board);
    assertEquals(0, summary.errorsEncountered());

    NetClass diffClass = board.rules.nets.get("D", 1).getNetClass();
    NetClass defaultClass = board.rules.getDefaultNetClass();
    int diffTraceCl =
        diffClass.defaultItemClearanceClasses.get(
            app.freerouting.rules.DefaultItemClearanceClasses.ItemClass.TRACE);
    int diffViaCl =
        diffClass.defaultItemClearanceClasses.get(
            app.freerouting.rules.DefaultItemClearanceClasses.ItemClass.VIA);
    int defaultTraceCl =
        defaultClass.defaultItemClearanceClasses.get(
            app.freerouting.rules.DefaultItemClearanceClasses.ItemClass.TRACE);

    int netDNumber = board.rules.nets.get("D", 1).netNumber;
    Trace traceD = null;
    Trace traceN = null;
    for (Trace t : board.getTraces()) {
      if (t.containsNet(netDNumber)) {
        traceD = t;
      } else {
        traceN = t;
      }
    }
    assertNotNull(traceD);
    assertNotNull(traceN);
    assertEquals(diffTraceCl, traceD.clearanceClassIndex());
    assertEquals(defaultTraceCl, traceN.clearanceClassIndex());
    assertEquals(diffViaCl, board.getVias().iterator().next().clearanceClassIndex());
  }

  @Test
  void testNetClassViaRuleWithDecimalViaName_FR074() {
    String dsn =
        """
        (pcb viatest
          (resolution um 1000)
          (structure
            (layer top (type signal))
            (layer bottom (type signal))
            (boundary (rect pcb 0 0 100 100))
            (rule (width 0.2) (clearance 0.1)))
          (library
            (padstack "Via[0-1]_635:304.8_um"
              (shape (circle top 0.635))
              (shape (circle bottom 0.635))))
          (network
            (net N)
            (class power N
              (circuit (use_via "Via[0-1]_635:304.8_um")))))
        """;
    BasicBoard board = loadBoard(dsn);

    NetClass powerClass = board.rules.netClasses.get("power");
    assertNotNull(powerClass);
    assertNotNull(powerClass.getViaRule());
    assertEquals(1, powerClass.getViaRule().viaCount());
  }

  @Test
  void testMultipleClassPairsInserted_FR036() {
    String dsn =
        """
        (pcb classpairstest
          (resolution um 1000)
          (structure
            (layer top (type signal))
            (layer bottom (type signal))
            (boundary (rect pcb 0 0 100 100))
            (rule (width 0.2) (clearance 0.1)))
          (library (padstack V (shape (circle top 0.6)) (shape (circle bottom 0.6))))
          (network
            (net N1) (net N2) (net N3)
            (class c1 N1 (circuit (use_via V)))
            (class c2 N2 (circuit (use_via V)))
            (class c3 N3 (circuit (use_via V)))
            (class_class (classes c1 c2 c3) (rule (clearance 0.35)))))
        """;
    BasicBoard board = loadBoard(dsn);

    NetClass c1 = board.rules.netClasses.get("c1");
    NetClass c2 = board.rules.netClasses.get("c2");
    NetClass c3 = board.rules.netClasses.get("c3");
    assertNotNull(c1);
    assertNotNull(c2);
    assertNotNull(c3);

    int cl1 = board.rules.clearanceMatrix.getNo(c1.getName());
    int cl2 = board.rules.clearanceMatrix.getNo(c2.getName());
    int cl3 = board.rules.clearanceMatrix.getNo(c3.getName());
    assertTrue(cl1 > 0);
    assertTrue(cl2 > 0);
    assertTrue(cl3 > 0);

    assertEquals(350, board.rules.clearanceMatrix.getValue(cl1, cl2, 0, false));
    assertEquals(350, board.rules.clearanceMatrix.getValue(cl1, cl3, 0, false));
    // Pair (c2, c3) was previously missing because the iterator was shared!
    assertEquals(350, board.rules.clearanceMatrix.getValue(cl2, cl3, 0, false));
  }

  @Test
  void testBoardStatisticsBoundingBox_FR041() {
    String dsn =
        """
        (pcb bboxtest
          (resolution mm 100)
          (structure
            (layer top (type signal))
            (layer bottom (type signal))
            (boundary (rect pcb 10 20 60 80))
            (rule (width 0.2) (clearance 0.1)))
          (network (net N)))
        """;
    RoutingBoard board = loadBoard(dsn);
    BoardStatistics stats = new BoardStatistics(board, Unit.MM, false, false);
    IntBox bb = board.getBoundingBox();

    assertEquals((float) bb.ll.x, stats.board.boundingBox.x, 1e-3);
    assertEquals((float) bb.ll.y, stats.board.boundingBox.y, 1e-3);
    assertEquals((float) (bb.ur.x - bb.ll.x), stats.board.boundingBox.width, 1e-3);
    assertEquals((float) (bb.ur.y - bb.ll.y), stats.board.boundingBox.height, 1e-3);
  }
}
