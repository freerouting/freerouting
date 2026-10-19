package app.freerouting.io.specctra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.board.state.Communication;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.io.CoordinateTransform;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SesPrecisionTest {
  private static Polyline shortLine(int sign) {
    return new Polyline(
        new Line[] {
          new Line(new IntPoint(100, sign * 90), new IntPoint(100, sign * 110)),
          new Line(new IntPoint(100, sign * 100), new IntPoint(99, sign * 100 - 1)),
          new Line(new IntPoint(99, sign * 100), new IntPoint(100, sign * 100 - 1))
        });
  }

  private static String wire(Polyline line, int resolution, double scale) throws Exception {
    LayerStructure layers =
        new LayerStructure(new Layer[] {new Layer("F.Cu", true), new Layer("B.Cu", true)});
    BoardRules rules = new BoardRules(layers, ClearanceMatrix.getDefaultInstance(layers, 1));
    Communication defaults = new Communication();
    Communication communication =
        new Communication(
            Unit.UM,
            resolution,
            defaults.specctraParserInfo,
            new CoordinateTransform(1, 0, 0),
            defaults.idGenerator,
            defaults.observers);
    BasicBoard board =
        new BasicBoard(
            new IntBox(-10000, -10000, 10000, 10000),
            layers,
            new PolylineShape[0],
            0,
            rules,
            communication);
    PolylineTrace trace =
        new PolylineTrace(line, 0, 10, new int[] {1}, 0, 1, 0, FixedState.UNFIXED, board);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (IndentFileWriter file = new IndentFileWriter(bytes)) {
      CoordinateTransform transform = new CoordinateTransform(scale, 0, 0);
      SesTracePath.from(trace, transform)
          .write(
              "F.Cu",
              transform.boardToDsn(20),
              new IdentifierType(new String[] {"(", ")", " "}, "\""),
              file);
    }
    return bytes.toString(StandardCharsets.UTF_8);
  }

  @ParameterizedTest
  @CsvSource({"1,10,99.5 99.5", "-1,10,99.5 -100.5", "1,25,99.5 99.5"})
  void preservesNonzeroSegmentsBelowTheIntegerOutputGrid(int sign, int resolution, String endpoint)
      throws Exception {
    assertTrue(wire(shortLine(sign), resolution, 1).contains(endpoint));
  }

  @ParameterizedTest
  @ValueSource(ints = {10, 1000})
  void ordinarySegmentsKeepTheirEndpoints(int resolution) throws Exception {
    Polyline ordinary =
        new Polyline(new Point[] {new IntPoint(100, -100), new IntPoint(110, -100)});
    assertTrue(wire(ordinary, resolution, 1).contains("110 -100"));
  }

  @Test
  void pointCopperKeepsTwoEndpointsAndItsWidth() throws Exception {
    Polyline point =
        new Polyline(
            new Line[] {
              new Line(new IntPoint(100, 90), new IntPoint(100, 110)),
              new Line(new IntPoint(90, 100), new IntPoint(110, 100)),
              new Line(new IntPoint(90, 90), new IntPoint(110, 110))
            });
    String session = wire(point, 10, 1);
    assertTrue(session.contains("path F.Cu 20"));
    assertEquals(2, session.split("100 100", -1).length - 1);
  }

  @Test
  void rationalPointCopperIsNotRoundedToAnotherLocation() throws Exception {
    Polyline point =
        new Polyline(
            new Line[] {
              new Line(new IntPoint(99, 100), new IntPoint(100, 99)),
              new Line(new IntPoint(99, 99), new IntPoint(100, 100)),
              new Line(new IntPoint(98, 100), new IntPoint(101, 99))
            });
    String session = wire(point, 10, 1);
    assertEquals(2, session.split("99.5 99.5", -1).length - 1);
  }

  @Test
  void rejectsNonFiniteCoordinates() {
    assertThrows(IOException.class, () -> wire(shortLine(1), 10, Double.NaN));
  }

  @Test
  void preservesNonzeroCopperAtFinerOutputScales() throws Exception {
    // SES coordinates are decimal values; a KiCad-only integer grid must not truncate other hosts.
    assertTrue(wire(shortLine(1), 10, 1000).contains("0.0995 0.0995"));
  }
}
