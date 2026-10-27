package app.freerouting.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.actions.ForcedPadRouter;
import app.freerouting.board.actions.ForcedViaInserter;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.searchtree.ExactCopper;
import app.freerouting.drc.ClearanceViolation;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.ViaInfo;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tests verifying exact copper clearance measurements against search tree bounding octagons. */
class ExactCopperClearanceTest {

  private static final int CLEARANCE = 100; // um, the rule of both boards
  private static final int CLEARANCE_UNITS = 1000;
  private static final int VIA_RADIUS_UNITS = 2750;
  private static final int MARGIN = ClearanceMatrix.clearance_safety_margin;

  private static RoutingBoard read(String dsn, boolean exact) {
    ByteArrayInputStream stream = new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8));
    BoardReadResult result = DsnReader.readBoard(stream, null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();
    board.rules.setExactCopper(exact);
    return board;
  }

  private static RoutingBoard viaBoard(boolean exact) {
    String dsn =
        """
        (pcb exact-vias
          (resolution um 10)
          (structure
            (layer top (type signal)) (layer bottom (type signal))
            (boundary (rect pcb 0 0 40000 30000))
            (via V) (rule (width 100) (clearance 100)))
          (placement)
          (library
            (padstack V (shape (circle top 550)) (shape (circle bottom 550))))
          (network (net N) (net M) (net T)))
        """;
    return read(dsn, exact);
  }

  private static IntPoint at(RoutingBoard board, double x, double y) {
    return new IntPoint(
        (int) board.communication.coordinateTransform.dsnToBoard(x),
        (int) board.communication.coordinateTransform.dsnToBoard(y));
  }

  private static ViaInfo via(RoutingBoard board) {
    return board.rules.viaInfos.get(0);
  }

  private static boolean checkVia(RoutingBoard board, IntPoint location, int net) {
    return ForcedViaInserter.check(
        via(board),
        location,
        new int[] {net},
        3,
        0,
        board,
        null,
        BoardRules.defaultClearanceClass());
  }

  @Test
  void copperDistanceMeasuresCirclesAndPolygons() {
    Circle circle = new Circle(new IntPoint(0, 0), 10);
    IntBox box = new IntBox(20, 20, 40, 40);
    Circle other = new Circle(new IntPoint(30, 0), 5);

    assertEquals(15, distance(circle, other), 1e-6);
    assertEquals(Math.sqrt(800) - 10, distance(circle, box), 1e-6);
    assertEquals(Math.sqrt(800) - 10, distance(box, circle), 1e-6);
    assertEquals(15, distance(other, box), 1e-6);
    assertEquals(10, distance(box, new IntBox(50, 0, 60, 60)), 1e-6);
    assertEquals(0, distance(box, new IntBox(30, 30, 60, 60)), 1e-6);
    assertEquals(0, distance(circle, new Circle(new IntPoint(12, 0), 3)), 1e-6);
  }

  private static double distance(Shape a, Shape b) {
    return ExactCopper.distance(ExactCopper.of(a), ExactCopper.of(b));
  }

  @ParameterizedTest
  @CsvSource({"373, 544", "544, 373", "-373, -544", "250, 603"})
  void viaCloserThanTheOctagonsAllowIsPlaced(double dx, double dy) {
    RoutingBoard board = viaBoard(true);
    Via first =
        board.insertVia(
            via(board).getPadstack(),
            at(board, 10000, 10000),
            new int[] {1},
            1,
            FixedState.UNFIXED,
            false);
    IntPoint location = at(board, 10000 + dx, 10000 + dy);
    TileShape firstOctagon = (TileShape) first.getTileShape(0);
    IntOctagon secondOctagon =
        new Circle(location, first.getShape(0) instanceof Circle c ? (int) c.radius : 0)
            .boundingOctagon();
    int half = (int) board.communication.coordinateTransform.dsnToBoard(CLEARANCE / 2.0);
    // The tiles alone would refuse the second via.
    assertTrue(firstOctagon.enlarge(half).intersects(secondOctagon.enlarge(half)));

    assertTrue(checkVia(board, location, 2));
    assertTrue(
        ForcedViaInserter.insert(
            via(board),
            location,
            new int[] {2},
            BoardRules.defaultClearanceClass(),
            new int[] {0, 0},
            3,
            0,
            board));
    assertEquals(2, board.getVias().size());
    assertEquals(at(board, 10000, 10000), first.getCenter());
    assertTrue(new DesignRulesChecker(board, null).getAllClearanceViolations().isEmpty());
  }

  @ParameterizedTest
  @CsvSource({"365, 530", "640, 0", "400, 0"})
  void viaShortOfTheRuleIsStillRefused(double dx, double dy) {
    RoutingBoard board = viaBoard(true);
    board.insertVia(
        via(board).getPadstack(),
        at(board, 10000, 10000),
        new int[] {1},
        1,
        FixedState.UNFIXED,
        false);
    assertFalse(checkVia(board, at(board, 10000 + dx, 10000 + dy), 2));
  }

  @Test
  void mazeViaCheckAgreesWithTheInsertion() {
    RoutingBoard board = viaBoard(true);
    board.insertVia(
        via(board).getPadstack(),
        at(board, 10000, 10000),
        new int[] {1},
        1,
        FixedState.UNFIXED,
        false);
    double viaRadius = board.communication.coordinateTransform.dsnToBoard(275);
    IntPoint legal = at(board, 10373, 10544);
    IntPoint illegal = at(board, 10365, 10530);
    TileShape room = new IntBox(at(board, 9000, 9000), at(board, 12000, 12000));

    assertEquals(
        ForcedPadRouter.CheckDrillResult.DRILLABLE,
        checkLayer(board, viaRadius, room, legal, true));
    // The octagon alone refuses it, as the search did before.
    assertEquals(
        ForcedPadRouter.CheckDrillResult.NOT_DRILLABLE,
        checkLayer(board, viaRadius, room, legal, false));
    assertEquals(
        ForcedPadRouter.CheckDrillResult.NOT_DRILLABLE,
        checkLayer(board, viaRadius, room, illegal, true));
    assertEquals(
        checkVia(board, legal, 2),
        checkLayer(board, viaRadius, room, legal, true)
            != ForcedPadRouter.CheckDrillResult.NOT_DRILLABLE);
  }

  private static ForcedPadRouter.CheckDrillResult checkLayer(
      RoutingBoard board, double viaRadius, TileShape room, IntPoint location, boolean circular) {
    return ForcedViaInserter.checkLayer(
        viaRadius,
        BoardRules.defaultClearanceClass(),
        false,
        room,
        location,
        0,
        new int[] {2},
        3,
        0,
        board,
        0,
        BoardRules.defaultClearanceClass(),
        circular);
  }

  @Test
  void exactCopperOffKeepsTheOctagons() {
    RoutingBoard vias = viaBoard(false);
    vias.insertVia(
        via(vias).getPadstack(),
        at(vias, 10000, 10000),
        new int[] {1},
        1,
        FixedState.UNFIXED,
        false);
    IntPoint legal = at(vias, 10373, 10544);
    assertFalse(checkVia(vias, legal, 2));
    TileShape room = new IntBox(at(vias, 9000, 9000), at(vias, 12000, 12000));
    assertEquals(
        ForcedPadRouter.CheckDrillResult.NOT_DRILLABLE,
        checkLayer(
            vias, vias.communication.coordinateTransform.dsnToBoard(275), room, legal, true));
  }

  private record ViaVerdicts(
      boolean maze, boolean insertion, boolean drcStrict, boolean drcDefault) {}

  private static ViaVerdicts judgeVia(RoutingBoard board, IntPoint location, int net) {
    int reach = 10 * VIA_RADIUS_UNITS;
    TileShape room =
        new IntBox(location.x - reach, location.y - reach, location.x + reach, location.y + reach);
    boolean maze =
        ForcedViaInserter.checkLayer(
                VIA_RADIUS_UNITS,
                BoardRules.defaultClearanceClass(),
                false,
                room,
                location,
                0,
                new int[] {net},
                3,
                0,
                board,
                0,
                BoardRules.defaultClearanceClass(),
                true)
            == ForcedPadRouter.CheckDrillResult.DRILLABLE;
    boolean insertion = checkVia(board, location, net);
    board.insertVia(
        via(board).getPadstack(),
        location,
        new int[] {net},
        BoardRules.defaultClearanceClass(),
        FixedState.UNFIXED,
        false);
    board.rules.clearanceToleranceUm = 0;
    Collection<ClearanceViolation> strict =
        new DesignRulesChecker(board, null).getAllClearanceViolations();
    board.rules.clearanceToleranceUm = 1.0;
    Collection<ClearanceViolation> lenient =
        new DesignRulesChecker(board, null).getAllClearanceViolations();
    return new ViaVerdicts(maze, insertion, strict.isEmpty(), lenient.isEmpty());
  }

  private static void assertAgree(ViaVerdicts v, double gapUnits, String what) {
    boolean placeable = gapUnits >= CLEARANCE_UNITS + MARGIN;
    boolean legal = gapUnits >= CLEARANCE_UNITS;
    boolean tolerated = (CLEARANCE_UNITS - gapUnits) * 0.1 <= 1.0;
    String context = what + ": copper gap " + gapUnits + " of " + CLEARANCE_UNITS + ", " + v;
    assertEquals(placeable, v.maze, "maze via check disagrees; " + context);
    assertEquals(placeable, v.insertion, "insertion check disagrees; " + context);
    assertEquals(legal, v.drcStrict, "internal DRC (0 tol) disagrees; " + context);
    assertEquals(tolerated, v.drcDefault, "internal DRC (1 um tol) disagrees; " + context);
  }

  @ParameterizedTest
  @CsvSource({"1020, legal", "1000, boundary", "995, tolerated", "985, illegal", "1050, placeable"})
  void twoRoundViasAgreement(double gapUnits, String caseName) {
    RoutingBoard board = viaBoard(true);
    board.insertVia(
        via(board).getPadstack(),
        new IntPoint(100000, 100000),
        new int[] {1},
        1,
        FixedState.UNFIXED,
        false);
    double distance = 2 * VIA_RADIUS_UNITS + gapUnits;
    IntPoint location = new IntPoint((int) Math.round(100000 + distance), 100000);
    ViaVerdicts verdicts = judgeVia(board, location, 2);
    assertAgree(verdicts, gapUnits, "two vias " + caseName);
  }
}
