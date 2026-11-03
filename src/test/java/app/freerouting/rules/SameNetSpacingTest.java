package app.freerouting.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.actions.DrillItemMover;
import app.freerouting.board.actions.ForcedViaInserter;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.core.library.Padstack;
import app.freerouting.drc.ClearanceViolation;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.IntVector;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.settings.sources.DefaultSettings;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * KiCad keeps no copper clearance within a net: vias of one net keep only the hole-to-hole minimum
 * between their drills, a via may come as close to its own net's pad as it likes (overlapping it
 * only where via-in-pad allows), and different nets keep the full clearance. The router's via check
 * and the checker use the same rule.
 */
class SameNetSpacingTest {

  // Micrometres: 200 clearance, vias 600 across with a 300 drill, hole-to-hole 250.
  private static final double VIA_RADIUS = 300;
  private static final double DRILL_RADIUS = 150;
  private static final double HOLE_TO_HOLE = 250;
  private static final double CLEARANCE = 200;
  private static final double SMD_X = 20000;
  private static final double SMD_Y = 20000;
  private static final double SMD_HALF_WIDTH = 500;
  private static final double TH_X = 10000;
  private static final double TH_Y = 20000;
  private static final double TH_RADIUS = 600;
  private static final double TH_DRILL_RADIUS = 400;
  private static final double AREA_LEFT = 30000;
  private static final double AREA_Y = 7000;

  private static RoutingBoard createBoard() {
    String dsn =
        """
        (pcb same-net-spacing
          (resolution um 10)
          (structure
            (layer top (type signal)) (layer bottom (type signal))
            (boundary (rect pcb 0 0 40000 30000))
            (via V) (rule (width 100) (clearance 200)))
          (placement
            (component S (place S1 20000 20000 front 0))
            (component T (place T1 10000 20000 front 0)))
          (library
            (image S (pin SMD 1 0 0))
            (image T (pin TH 1 0 0))
            (padstack SMD (shape (rect top -500 -300 500 300)) (attach off))
            (padstack TH (shape (circle top 1200)) (shape (circle bottom 1200)) (attach off))
            (padstack V (shape (circle top 600)) (shape (circle bottom 600)) (attach off)))
          (network (net N (pins S1-1 T1-1)) (net M)))
        """;
    ByteArrayInputStream stream = new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8));
    BoardReadResult result = DsnReader.readBoard(stream, null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();
    getPadstack(board, "V").setExplicitDrillRadius(units(board, DRILL_RADIUS));
    getPadstack(board, "TH").setExplicitDrillRadius(units(board, TH_DRILL_RADIUS));
    board.rules.setMinHoleToHole((int) units(board, HOLE_TO_HOLE));
    return board;
  }

  private static Padstack getPadstack(RoutingBoard board, String name) {
    Padstack padstack = board.library.padstacks.get(name);
    assertNotNull(padstack);
    return padstack;
  }

  private static double units(RoutingBoard board, double um) {
    return board.communication.coordinateTransform.dsnToBoard(um);
  }

  private static IntPoint at(RoutingBoard board, double x, double y) {
    return new IntPoint((int) units(board, x), (int) units(board, y));
  }

  private static int getNet(RoutingBoard board, String name) {
    return board.rules.nets.get(name, 1).netNumber;
  }

  private static Via insert(RoutingBoard board, double x, double y, String net, boolean attach) {
    return board.insertVia(
        getPadstack(board, "V"),
        at(board, x, y),
        new int[] {getNet(board, net)},
        1,
        FixedState.UNFIXED,
        attach);
  }

  private static Via insert(RoutingBoard board, double x, double y, String net) {
    return insert(board, x, y, net, false);
  }

  private static boolean canPlace(
      RoutingBoard board, double x, double y, String net, boolean attach) {
    return ForcedViaInserter.check(
        new ViaInfo("V", getPadstack(board, "V"), 1, attach, board.rules),
        at(board, x, y),
        new int[] {getNet(board, net)},
        3,
        0,
        board,
        null,
        1);
  }

  private static boolean canPlace(RoutingBoard board, double x, double y, String net) {
    return canPlace(board, x, y, net, false);
  }

  private static Collection<ClearanceViolation> violations(RoutingBoard board) {
    return new DesignRulesChecker(board, null).getAllClearanceViolations();
  }

  private static IntBox areaBox(RoutingBoard board) {
    return new IntBox(at(board, AREA_LEFT, 5000), at(board, 36000, 9000));
  }

  private static ConductionArea area(RoutingBoard board, String net, boolean fixedCopper) {
    ConductionArea area =
        board.insertConductionArea(
            areaBox(board),
            0,
            new int[] {getNet(board, net)},
            1,
            fixedCopper,
            FixedState.SYSTEM_FIXED);
    return area;
  }

  @ParameterizedTest
  @ValueSource(doubles = {700, 560})
  void sameNetViasNeedOnlyTheHoleToHoleMinimum(double distance) {
    RoutingBoard board = createBoard();
    insert(board, 5000, 5000, "N");
    assertTrue(canPlace(board, 5000 + distance, 5000, "N"));
    insert(board, 5000 + distance, 5000, "N");
    assertTrue(violations(board).isEmpty());
  }

  @ParameterizedTest
  @CsvSource({"540, 0", "300, 212.132"})
  void sameNetViasKeepTheHoleToHoleMinimum(double dx, double dy) {
    RoutingBoard board = createBoard();
    insert(board, 5000, 5000, "N");
    assertFalse(canPlace(board, 5000 + dx, 5000 + dy, "N"));
    insert(board, 5000 + dx, 5000 + dy, "N");
    Collection<ClearanceViolation> violations = violations(board);
    assertEquals(1, violations.size());
    ClearanceViolation violation = violations.iterator().next();
    assertTrue(violation.holeToHole);
    assertEquals(units(board, HOLE_TO_HOLE), violation.expectedClearance);
    double gap = Math.sqrt(dx * dx + dy * dy) - 2 * DRILL_RADIUS;
    assertEquals(
        gap, board.communication.coordinateTransform.boardToDsn(violation.actualClearance), 0.1);
  }

  @Test
  void holeToHoleAppliesBetweenNetsToo() {
    RoutingBoard board = createBoard();
    // A hole-to-hole minimum above what the copper clearance and the rings give (200 + 2 x 150 =
    // 500).
    board.rules.setMinHoleToHole((int) units(board, 600));
    insert(board, 5000, 5000, "N");
    assertFalse(canPlace(board, 5000 + 850, 5000, "M")); // copper 250 apart (clear), holes 550
    assertTrue(canPlace(board, 5000 + 950, 5000, "M")); // holes 650
    insert(board, 5000 + 850, 5000, "M");
    Collection<ClearanceViolation> violations = violations(board);
    assertEquals(1, violations.size());
    assertTrue(violations.iterator().next().holeToHole);
  }

  @Test
  void differentNetViasKeepTheFullClearance() {
    RoutingBoard board = createBoard();
    insert(board, 5000, 5000, "N");
    assertFalse(canPlace(board, 5700, 5000, "M"));
    assertTrue(canPlace(board, 5000 + 2 * VIA_RADIUS + CLEARANCE + 10, 5000, "M"));
    insert(board, 5700, 5000, "M");
    Collection<ClearanceViolation> violations = violations(board);
    assertEquals(2, violations.size()); // one on each layer
    for (ClearanceViolation v : violations) {
      assertFalse(v.holeToHole);
      assertEquals(units(board, CLEARANCE), v.expectedClearance);
    }
  }

  @Test
  void viaMaySitBesideItsOwnNetsSmdPadInsideTheClearance() {
    RoutingBoard board = createBoard();
    double x =
        SMD_X
            + SMD_HALF_WIDTH
            + VIA_RADIUS
            + 100; // 100 from pad: inside clearance, not overlapping
    assertTrue(canPlace(board, x, SMD_Y, "N"));
    insert(board, x, SMD_Y, "N");
    assertTrue(violations(board).isEmpty());
  }

  @Test
  void aViaInsideItsOwnPadsClearanceCrowdsThePad() {
    RoutingBoard board = createBoard();
    Padstack via = getPadstack(board, "V");
    IntPoint inside = at(board, SMD_X + SMD_HALF_WIDTH + VIA_RADIUS + 100, SMD_Y);
    IntPoint outside = at(board, SMD_X + SMD_HALF_WIDTH + VIA_RADIUS + CLEARANCE + 10, SMD_Y);
    assertTrue(ForcedViaInserter.crowdsOwnPad(board, via, 1, inside, getNet(board, "N")));
    assertFalse(ForcedViaInserter.crowdsOwnPad(board, via, 1, outside, getNet(board, "N")));
    assertFalse(ForcedViaInserter.crowdsOwnPad(board, via, 1, inside, getNet(board, "M")));
    assertTrue(canPlace(board, SMD_X + SMD_HALF_WIDTH + VIA_RADIUS + 100, SMD_Y, "N"));
  }

  @Test
  void viaDoesNotOverlapItsOwnNetsSmdPadUnlessViaInPadAllows() {
    RoutingBoard board = createBoard();
    double x = SMD_X + SMD_HALF_WIDTH + VIA_RADIUS - 100; // 100 into the pad
    assertFalse(canPlace(board, x, SMD_Y, "N"));
    assertTrue(canPlace(board, x, SMD_Y, "N", true));
    Via via = insert(board, x, SMD_Y, "N");
    Collection<ClearanceViolation> violations = violations(board);
    assertEquals(1, violations.size());
    assertEquals(0, violations.iterator().next().expectedClearance);
    board.removeItem(via);
    insert(board, x, SMD_Y, "N", true);
    assertTrue(violations(board).isEmpty());
  }

  @Test
  void viaKeepsTheFullClearanceFromAnotherNetsSmdPad() {
    RoutingBoard board = createBoard();
    double x = SMD_X + SMD_HALF_WIDTH + VIA_RADIUS + 100;
    assertFalse(canPlace(board, x, SMD_Y, "M"));
    assertTrue(canPlace(board, SMD_X + SMD_HALF_WIDTH + VIA_RADIUS + CLEARANCE + 10, SMD_Y, "M"));
    insert(board, x, SMD_Y, "M");
    assertFalse(violations(board).isEmpty());
  }

  @Test
  void viaMayOverlapItsOwnNetsFixedCopper() {
    RoutingBoard board = createBoard();
    area(board, "N", true);
    double x = AREA_LEFT - VIA_RADIUS + 50; // copper 50 into the area: connected, as KiCad has it
    assertTrue(canPlace(board, x, AREA_Y, "N"));
    insert(board, x, AREA_Y, "N");
    assertTrue(violations(board).isEmpty());
  }

  @Test
  void viaKeepsTheFullClearanceFromAnotherNetsFixedCopper() {
    RoutingBoard board = createBoard();
    area(board, "N", true);
    double x = AREA_LEFT - VIA_RADIUS - 100; // copper 100 apart, inside the 200 clearance
    assertFalse(canPlace(board, x, AREA_Y, "M"));
    assertTrue(canPlace(board, AREA_LEFT - VIA_RADIUS - CLEARANCE - 10, AREA_Y, "M"));
    insert(board, x, AREA_Y, "M");
    assertFalse(violations(board).isEmpty());
  }

  @Test
  void viaOfAnotherNetInsideAPourIsLegal() {
    RoutingBoard board = createBoard();
    area(board, "N", false);
    assertTrue(canPlace(board, AREA_LEFT + 3000, AREA_Y, "M"));
    insert(board, AREA_LEFT + 3000, AREA_Y, "M");
    assertTrue(violations(board).isEmpty());
  }

  @Test
  void viaInsideAKeepoutIsAViolation() {
    RoutingBoard board = createBoard();
    assertNotNull(board.insertObstacle(areaBox(board), 0, 1, FixedState.SYSTEM_FIXED));
    assertFalse(canPlace(board, AREA_LEFT + 3000, AREA_Y, "M"));
    insert(board, AREA_LEFT + 3000, AREA_Y, "M");
    assertFalse(violations(board).isEmpty());
  }

  @Test
  void viaMayTouchButNotOverlapItsOwnNetsThroughHolePad() {
    RoutingBoard board = createBoard();
    double beside = TH_X + TH_RADIUS + VIA_RADIUS + 50; // copper 50 apart, holes 400 apart
    assertTrue(canPlace(board, beside, TH_Y, "N"));
    double into = TH_X + TH_RADIUS + VIA_RADIUS - 50; // copper overlapping, holes 300 apart
    assertFalse(canPlace(board, into, TH_Y, "N", true)); // via-in-pad is for SMD pads only
    Via via = insert(board, beside, TH_Y, "N");
    assertTrue(violations(board).isEmpty());
    board.removeItem(via);
    insert(board, into, TH_Y, "N");
    Collection<ClearanceViolation> violations = violations(board);
    assertEquals(2, violations.size()); // the overlap, on each layer
    for (ClearanceViolation v : violations) {
      assertEquals(0, v.expectedClearance);
    }
  }

  @Test
  void viaKeepsTheHoleToHoleMinimumFromItsOwnNetsThroughHolePad() {
    RoutingBoard board = createBoard();
    board.rules.setMinHoleToHole((int) units(board, 500)); // the holes are 400 apart
    double beside = TH_X + TH_RADIUS + VIA_RADIUS + 50;
    assertFalse(canPlace(board, beside, TH_Y, "N"));
    insert(board, beside, TH_Y, "N");
    Collection<ClearanceViolation> violations = violations(board);
    assertEquals(1, violations.size());
    assertTrue(violations.iterator().next().holeToHole);
  }

  @Test
  void movedViaKeepsTheHoleToHoleMinimum() {
    RoutingBoard board = createBoard();
    insert(board, 5000, 5000, "N");
    Via moved = insert(board, 6000, 5000, "N");
    assertTrue(
        DrillItemMover.check(
            moved, new IntVector((int) units(board, -440), 0), 3, 2, null, board, null));
    assertFalse(
        DrillItemMover.check(
            moved, new IntVector((int) units(board, -460), 0), 3, 2, null, board, null));
  }

  @Test
  void aDsnGetsKiCadsDefaultHoleToHole() {
    assertEquals(250.0, new DefaultSettings().getSettings().holeToHoleUm);
  }
}
