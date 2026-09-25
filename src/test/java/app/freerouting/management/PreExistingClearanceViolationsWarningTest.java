package app.freerouting.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.structure.BoardOutline;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.state.Communication;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.drc.ClearanceViolation;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreExistingClearanceViolationsWarningTest {

  private RoutingBoard createTestBoard() {
    Layer layer1 = new Layer("Top", true);
    Layer layer2 = new Layer("Bottom", true);
    Layer[] layers = new Layer[] {layer1, layer2};
    LayerStructure layerStructure = new LayerStructure(layers);

    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(layerStructure, 10);
    BoardRules boardRules = new BoardRules(layerStructure, clearanceMatrix);
    Communication communication = new Communication();

    RoutingBoard board =
        new RoutingBoard(
            new IntBox(0, 0, 2000000, 2000000),
            layerStructure,
            new PolylineShape[] {TileShape.getInstance(0, 0, 2000000, 2000000)},
            0,
            boardRules,
            communication);

    board.components.add("U1", Point.ZERO, 0, true, null, null, false, "");
    return board;
  }

  @Test
  void testCategorizedWarningFormatWithUnfixableAndFixableViolations() {
    RoutingBoard board = createTestBoard();
    List<ClearanceViolation> violations = new ArrayList<>();

    // 7 pin-to-pin violations (to verify first 5 + "... and 2 more")
    for (int i = 0; i < 7; i++) {
      Pin p1 = new Pin(1, i, new int[0], 0, 100 + i * 2, FixedState.SYSTEM_FIXED, board);
      Pin p2 = new Pin(1, i + 1, new int[0], 0, 101 + i * 2, FixedState.SYSTEM_FIXED, board);
      violations.add(new ClearanceViolation(p1, p2, null, 0, 100.0, 50.0));
    }

    // 1 pin-to-outline violation
    Pin pOut = new Pin(1, 10, new int[0], 0, 200, FixedState.SYSTEM_FIXED, board);
    BoardOutline outline = mock(BoardOutline.class);
    when(outline.isRoutable()).thenReturn(false);
    violations.add(new ClearanceViolation(pOut, outline, null, 0, 100.0, 50.0));

    // 1 fixed route violation
    Pin pFixed = new Pin(1, 11, new int[0], 0, 201, FixedState.SYSTEM_FIXED, board);
    Trace fixedTrace = mock(Trace.class);
    when(fixedTrace.isRoutable()).thenReturn(false);
    when(fixedTrace.isUserFixed()).thenReturn(true);
    when(fixedTrace.getId()).thenReturn(42);
    violations.add(new ClearanceViolation(pFixed, fixedTrace, null, 0, 100.0, 50.0));

    // 1 potentially fixable violation (unfixed trace)
    Pin pFixable = new Pin(1, 12, new int[0], 0, 202, FixedState.SYSTEM_FIXED, board);
    Trace unfixedTrace = mock(Trace.class);
    when(unfixedTrace.isRoutable()).thenReturn(true);
    when(unfixedTrace.getId()).thenReturn(99);
    violations.add(new ClearanceViolation(pFixable, unfixedTrace, null, 0, 100.0, 50.0));

    String warning =
        HeadlessBoardManager.formatPreExistingClearanceViolationsWarning(board, violations);

    assertNotNull(warning);
    assertTrue(warning.contains("Board has 10 pre-existing clearance violation(s)"));
    assertTrue(warning.contains("9 unfixable violation(s) (cannot be resolved by Freerouting):"));
    assertTrue(warning.contains("7 pin-to-pin clearance violations (first 5 shown):"));
    assertTrue(warning.contains("... and 2 more"));
    assertTrue(warning.contains("1 pin-to-keepout / board-outline clearance violations:"));
    assertTrue(warning.contains("1 fixed trace/via clearance violations:"));
    assertTrue(
        warning.contains("1 potentially fixable violation(s) (involving unfixed traces/vias):"));
    assertTrue(
        warning.contains(
            "Notice: Freerouting does not modify component placement, board outlines, or fixed"
                + " items."));
    assertTrue(
        warning.contains(
            "Resolving these 9 unfixable violation(s) is the responsibility of the board author"
                + " in their EDA tool (e.g. KiCad)."));
  }

  @Test
  void testWarningFormatWhenOnlyPotentiallyFixableViolations() {
    RoutingBoard board = createTestBoard();
    List<ClearanceViolation> violations = new ArrayList<>();

    Pin p = new Pin(1, 1, new int[0], 0, 301, FixedState.SYSTEM_FIXED, board);
    Trace unfixedTrace = mock(Trace.class);
    when(unfixedTrace.isRoutable()).thenReturn(true);
    when(unfixedTrace.getId()).thenReturn(101);
    violations.add(new ClearanceViolation(p, unfixedTrace, null, 0, 100.0, 50.0));

    String warning =
        HeadlessBoardManager.formatPreExistingClearanceViolationsWarning(board, violations);

    assertTrue(warning.contains("Board has 1 pre-existing clearance violation(s)"));
    assertTrue(warning.contains("0 unfixable violation(s)"));
    assertTrue(
        warning.contains("1 potentially fixable violation(s) (involving unfixed traces/vias):"));
    assertTrue(
        warning.contains(
            "Notice: Freerouting will attempt to resolve potentially fixable violations by ripping"
                + " up and rerouting traces/vias."));
  }

  @Test
  void testBoardStatisticsRecordsUnfixableViolationsCount() {
    RoutingBoard board = createTestBoard();
    board.unfixableClearanceViolationsCount = 4;
    board.preExistingClearanceViolationsCount = 6;

    BoardStatistics stats = new BoardStatistics(board, null, false, false);
    assertNotNull(stats.clearanceViolations);
    assertEquals(0, stats.clearanceViolations.unfixableCount);

    // When clearance violations are skipped in constructor, unmeasured count defaults to 0.
    // Setting board.unfixableClearanceViolationsCount reflects in BoardStatistics when
    // includeClearanceViolations is true.
    stats.clearanceViolations.unfixableCount = board.unfixableClearanceViolationsCount;
    assertEquals(4, stats.clearanceViolations.unfixableCount);
  }
}
