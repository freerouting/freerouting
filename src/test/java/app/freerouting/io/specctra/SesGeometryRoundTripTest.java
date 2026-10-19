package app.freerouting.io.specctra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.board.state.Communication;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.io.CoordinateTransform;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class SesGeometryRoundTripTest {
  @ParameterizedTest
  @EnumSource(Unit.class)
  void rationalPointCopperPreservesActualObjectPositionWidthAndNet(Unit unit) throws Exception {
    BasicBoard source = board(unit, 1000, 1000);
    Polyline point =
        new Polyline(
            new Line[] {
              new Line(99, 100, 100, 99), new Line(99, 99, 100, 100), new Line(98, 100, 101, 99)
            });
    final PolylineTrace original = insert(source, point);
    BasicBoard target = board(unit, 1000, 1000);
    SesImportSummary summary = SesReader.read(new ByteArrayInputStream(write(source)), target);
    assertEquals(1, summary.wiresImported());
    assertEquals(0, summary.errorsEncountered());
    List<PolylineTrace> imported = traces(target);
    assertEquals(1, imported.size(), "Reported imports must match created copper objects");
    assertEquals(99.5, imported.getFirst().firstCorner().toFloat().x);
    assertEquals(99.5, imported.getFirst().firstCorner().toFloat().y);
    assertEquals(original.getHalfWidth(), imported.getFirst().getHalfWidth());
    assertEquals(0, imported.getFirst().getLayer());
    assertEquals(1, imported.getFirst().getNetNumber(0));
    assertEquals(imported.getFirst().firstCorner(), imported.getFirst().lastCorner());
  }

  @ParameterizedTest
  @CsvSource({
    "UM,10,10",
    "UM,1000,1000",
    "UM,100,10",
    "MM,1000,1000",
    "MIL,1000,1000",
    "INCH,1000,1000"
  })
  void shortSegmentInsideLongPathAndSessionScalingRoundTrip(Unit unit, int resolution, int scale)
      throws Exception {
    BasicBoard source = board(unit, resolution, scale);
    Polyline line =
        new Polyline(
            new Line[] {
              new Line(100, 90, 100, 110), new Line(100, 100, 99, 99),
              new Line(99, 100, 100, 99), new Line(110, 89, 110, 100)
            });
    final PolylineTrace original = insert(source, line);
    BasicBoard target = board(unit, resolution, scale);
    SesImportSummary summary = SesReader.read(new ByteArrayInputStream(write(source)), target);
    assertEquals(0, summary.errorsEncountered());
    assertEquals(1, summary.wiresImported());
    List<PolylineTrace> imported = traces(target);
    assertEquals(1, imported.size());
    assertEquals(original.getHalfWidth(), imported.getFirst().getHalfWidth());
    Point[] before = original.polyline().corners();
    Point[] after = imported.getFirst().polyline().corners();
    assertEquals(
        before.length, after.length, "No short segment may disappear inside a longer path");
    for (int index = 0; index < before.length; index++) {
      assertEquals(before[index].toFloat().x, after[index].toFloat().x, 1e-9);
      assertEquals(before[index].toFloat().y, after[index].toFloat().y, 1e-9);
    }
  }

  @Test
  void integerPointCopperCreatesOneRealTrace() throws Exception {
    BasicBoard source = board(Unit.UM, 10, 10);
    insert(
        source,
        new Polyline(
            new Line[] {
              new Line(100, 90, 100, 110), new Line(90, 100, 110, 100), new Line(90, 90, 110, 110)
            }));
    BasicBoard target = board(Unit.UM, 10, 10);
    SesImportSummary summary = SesReader.read(new ByteArrayInputStream(write(source)), target);
    assertEquals(1, summary.wiresImported());
    assertEquals(0, summary.errorsEncountered());
    assertEquals(1, traces(target).size());
    assertEquals(100, traces(target).getFirst().firstCorner().toFloat().x);
  }

  @Test
  void rejectedGeometryIsNotReportedAsImported() throws Exception {
    BasicBoard target = board(Unit.UM, 10, 10);
    String session =
        "(session rejected (routes (resolution um 10) (network_out (net TEST (wire (path F.Cu 0 100"
            + " 100 110 110))))))";
    SesImportSummary summary =
        SesReader.read(new ByteArrayInputStream(session.getBytes(StandardCharsets.UTF_8)), target);
    assertEquals(0, summary.wiresImported());
    assertEquals(1, summary.errorsEncountered());
    assertTrue(traces(target).isEmpty());
  }

  @Test
  void sessionCanUseDifferentUnitsFromTheBoard() throws Exception {
    BasicBoard target = board(Unit.UM, 10, 10);
    String session =
        "(session converted (routes (resolution mm 1000000) (network_out (net TEST (wire (path F.Cu"
            + " 2000 9950 9950 9950 9950))))))";
    SesImportSummary summary =
        SesReader.read(new ByteArrayInputStream(session.getBytes(StandardCharsets.UTF_8)), target);
    assertEquals(0, summary.errorsEncountered());
    assertEquals(1, summary.wiresImported());
    assertEquals(1, traces(target).size());
    assertEquals(99.5, traces(target).getFirst().firstCorner().toFloat().x);
    assertEquals(10, traces(target).getFirst().getHalfWidth());
  }

  @Test
  void nonzeroSubgridSegmentIsRejectedInsteadOfBecomingPointCopper() throws Exception {
    BasicBoard target = board(Unit.UM, 10, 10);
    String session =
        "(session unsupported (routes (resolution um 10) (network_out (net TEST (wire (path F.Cu 20"
            + " 100 100 100.00000000000001 100))))))";
    SesImportSummary summary =
        SesReader.read(new ByteArrayInputStream(session.getBytes(StandardCharsets.UTF_8)), target);
    assertEquals(0, summary.wiresImported());
    assertEquals(1, summary.errorsEncountered());
    assertTrue(traces(target).isEmpty());
  }

  @Test
  void nonIntegerSupportingLineIsRejectedWithoutMovingCopper() throws Exception {
    BasicBoard target = board(Unit.UM, 10, 10);
    String session =
        "(session unsupported (routes (resolution um 10) (network_out (net TEST (wire (path F.Cu 20"
            + " 100.1 100.2 110.1 110.2))))))";
    SesImportSummary summary =
        SesReader.read(new ByteArrayInputStream(session.getBytes(StandardCharsets.UTF_8)), target);
    assertEquals(0, summary.wiresImported());
    assertEquals(1, summary.errorsEncountered());
    assertTrue(traces(target).isEmpty());
  }

  @ParameterizedTest
  @CsvSource({
    "0,3,false",
    "0,3,true",
    "-100,7,false",
    "-100,7,true",
    "100,17,false",
    "100,17,true",
    "33000000,3,false",
    "33000000,3,true"
  })
  void nonterminatingRationalCornersRoundTrip(int offset, int denominator, boolean pointCopper)
      throws Exception {
    BasicBoard source = board(Unit.UM, 1000, 1000);
    Line start = new Line(offset + 1, offset, offset + 1, offset + 1);
    Line support = new Line(offset, offset, offset + denominator, offset + 1);
    Line end = pointCopper ? start : new Line(offset + 4, offset, offset + 4, offset + 1);
    final PolylineTrace original = insert(source, new Polyline(new Line[] {start, support, end}));
    BasicBoard target = board(Unit.UM, 1000, 1000);
    SesImportSummary summary = SesReader.read(new ByteArrayInputStream(write(source)), target);
    assertEquals(0, summary.errorsEncountered());
    assertEquals(1, summary.wiresImported());
    assertEquals(1, traces(target).size());
    PolylineTrace imported = traces(target).getFirst();
    assertEquals(original.getHalfWidth(), imported.getHalfWidth());
    assertEquals(original.polyline().cornerCount(), imported.polyline().cornerCount());
    for (int index = 0; index < original.polyline().cornerCount(); index++) {
      var before = original.polyline().corner(index).toFloat();
      var after = imported.polyline().corner(index).toFloat();
      assertEquals(before.x, after.x, 4 * Math.ulp(before.x));
      assertEquals(before.y, after.y, 4 * Math.ulp(before.y));
    }
  }

  @Test
  void collinearReversalIsRejectedInsteadOfLosingCopper() throws Exception {
    BasicBoard target = board(Unit.UM, 10, 10);
    String session =
        "(session reversal (routes (resolution um 10) (network_out (net TEST (wire (path F.Cu 20"
            + " 100 100 110 100 105 100))))))";
    SesImportSummary summary =
        SesReader.read(new ByteArrayInputStream(session.getBytes(StandardCharsets.UTF_8)), target);
    assertEquals(0, summary.wiresImported());
    assertEquals(1, summary.errorsEncountered());
    assertTrue(traces(target).isEmpty());
  }

  @Test
  void monotoneCollinearCornersKeepTheSameCopper() throws Exception {
    BasicBoard target = board(Unit.UM, 10, 10);
    String session =
        "(session straight (routes (resolution um 10) (network_out (net TEST (wire (path F.Cu 20"
            + " 100 100 105 100 110 100))))))";
    SesImportSummary summary =
        SesReader.read(new ByteArrayInputStream(session.getBytes(StandardCharsets.UTF_8)), target);
    assertEquals(0, summary.errorsEncountered());
    assertEquals(1, summary.wiresImported());
    assertEquals(100, traces(target).getFirst().firstCorner().toFloat().x);
    assertEquals(110, traces(target).getFirst().lastCorner().toFloat().x);
  }

  @ParameterizedTest
  @CsvSource({"10000,true,1", "10000,false,1", "10000,true,-1", "10000,false,-1"})
  void largeIntersectionDenominatorDoesNotLimitRepresentableCopper(
      int slope, boolean pointCopper, int sign) throws Exception {
    BasicBoard source = board(Unit.UM, 1000, 1000);
    Line first = new Line(0, 0, sign, sign * slope);
    Line second = new Line(0, sign, sign * slope, 0);
    Line last = pointCopper ? first : new Line(sign, 0, sign, sign);
    final PolylineTrace original = insert(source, new Polyline(new Line[] {first, second, last}));
    BasicBoard target = board(Unit.UM, 1000, 1000);
    SesImportSummary summary = SesReader.read(new ByteArrayInputStream(write(source)), target);
    assertEquals(0, summary.errorsEncountered());
    assertEquals(1, summary.wiresImported());
    assertEquals(1, traces(target).size());
    PolylineTrace imported = traces(target).getFirst();
    assertEquals(original.getHalfWidth(), imported.getHalfWidth());
    assertEquals(original.polyline().cornerCount(), imported.polyline().cornerCount());
    for (int index = 0; index < original.polyline().cornerCount(); index++) {
      var before = original.polyline().corner(index).toFloat();
      var after = imported.polyline().corner(index).toFloat();
      assertEquals(before.x, after.x, 4 * Math.ulp(before.x));
      assertEquals(before.y, after.y, 4 * Math.ulp(before.y));
    }
  }

  @Test
  void negativeFractionNearZeroDoesNotLosePrecisionDuringRecovery() throws Exception {
    BasicBoard source = board(Unit.UM, 1000, 1000);
    Line horizontal = new Line(0, 0, 1, 0);
    Line inclined = new Line(0, 1, 1, 10000002);
    final PolylineTrace original =
        insert(source, new Polyline(new Line[] {horizontal, inclined, horizontal}));
    BasicBoard target = board(Unit.UM, 1000, 1000);
    SesImportSummary summary = SesReader.read(new ByteArrayInputStream(write(source)), target);
    assertEquals(0, summary.errorsEncountered());
    assertEquals(1, summary.wiresImported());
    assertEquals(1, traces(target).size());
    double before = original.firstCorner().toFloat().x;
    assertEquals(before, traces(target).getFirst().firstCorner().toFloat().x, 4 * Math.ulp(before));
    assertEquals(0, traces(target).getFirst().firstCorner().toFloat().y);
  }

  @Test
  void rationalCornersBetweenLatticePointsRoundTrip() throws Exception {
    BasicBoard source = board(Unit.UM, 1000, 1000);
    Line first = new Line(0, 2, 1, 0);
    Line middle = new Line(0, 0, 1, 2);
    Line last = new Line(3, 0, 2, 2);
    final PolylineTrace original = insert(source, new Polyline(new Line[] {first, middle, last}));
    BasicBoard target = board(Unit.UM, 1000, 1000);
    SesImportSummary summary = SesReader.read(new ByteArrayInputStream(write(source)), target);
    assertEquals(0, summary.errorsEncountered());
    assertEquals(1, summary.wiresImported());
    List<PolylineTrace> imported = traces(target);
    assertEquals(1, imported.size());
    assertEquals(0.5, imported.getFirst().firstCorner().toFloat().x, 1e-9);
    assertEquals(1.0, imported.getFirst().firstCorner().toFloat().y, 1e-9);
    assertEquals(1.5, imported.getFirst().lastCorner().toFloat().x, 1e-9);
    assertEquals(3.0, imported.getFirst().lastCorner().toFloat().y, 1e-9);
  }

  private static byte[] write(BasicBoard board) throws Exception {
    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      SesWriter.write(board, output, "geometry.dsn");
      return output.toByteArray();
    }
  }

  private static PolylineTrace insert(BasicBoard board, Polyline polyline) {
    PolylineTrace trace =
        board.insertTraceWithoutCleaning(polyline, 0, 10, new int[] {1}, 0, FixedState.USER_FIXED);
    assertNotNull(trace);
    return trace;
  }

  private static List<PolylineTrace> traces(BasicBoard board) {
    return board.getItems().stream()
        .filter(PolylineTrace.class::isInstance)
        .map(PolylineTrace.class::cast)
        .toList();
  }

  private static BasicBoard board(Unit unit, int resolution, int scale) {
    LayerStructure layers =
        new LayerStructure(new Layer[] {new Layer("F.Cu", true), new Layer("B.Cu", true)});
    BoardRules rules = new BoardRules(layers, ClearanceMatrix.getDefaultInstance(layers, 1));
    Communication defaults = new Communication();
    Communication communication =
        new Communication(
            unit,
            resolution,
            defaults.specctraParserInfo,
            new CoordinateTransform(scale, 0, 0),
            defaults.idGenerator,
            defaults.observers);
    BasicBoard board =
        new BasicBoard(
            new IntBox(-33554432, -33554432, 33554432, 33554432),
            layers,
            new PolylineShape[0],
            0,
            rules,
            communication);
    board.rules.nets.add("TEST", 1, false);
    return board;
  }
}
