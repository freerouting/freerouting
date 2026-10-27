package app.freerouting.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.state.Communication;
import app.freerouting.core.library.Padstack;
import app.freerouting.core.library.Padstacks;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SameNetConductionAreaViaTest {

  private RoutingBoard board;
  private Padstack viaPadstack;

  @BeforeEach
  void setUp() {
    LayerStructure layers =
        new LayerStructure(new Layer[] {new Layer("Top", true), new Layer("Bottom", true)});
    BoardRules rules = new BoardRules(layers, ClearanceMatrix.getDefaultInstance(layers, 10));
    rules.createDefaultNetClass();
    IntBox bounds = new IntBox(-10000, -10000, 10000, 10000);
    board =
        new RoutingBoard(
            bounds, layers, new PolylineShape[] {bounds}, 0, rules, new Communication());
    rules.nets.add("GND", 1, true);
    rules.nets.add("SIGNAL", 1, false);

    board.library.padstacks = new Padstacks(layers);
    ConvexShape viaShape = new Circle(new IntPoint(0, 0), 400);
    viaPadstack =
        board.library.padstacks.add(
            "VIA1", new ConvexShape[] {viaShape, viaShape}, null, true, false);
  }

  @Test
  void sameNetConductionAreaIsNoObstacleForVia() {
    // Insert a fixed copper conduction area on net 1 (GND)
    ConductionArea area =
        board.insertConductionArea(
            new IntBox(0, 0, 2000, 2000), 0, new int[] {1}, 1, true, FixedState.SYSTEM_FIXED);

    // Insert a via of the same net overlapping the corner of the conduction area
    Via sameNetVia =
        board.insertVia(
            viaPadstack, new IntPoint(0, 0), new int[] {1}, 1, FixedState.USER_FIXED, true);

    assertFalse(
        sameNetVia.isObstacle(area), "Same net conduction area must not be an obstacle to via");
    assertFalse(
        area.isObstacle(sameNetVia), "Via must not be an obstacle to same net conduction area");

    DesignRulesChecker drc = new DesignRulesChecker(board, null);
    assertTrue(
        drc.getAllClearanceViolations().isEmpty(),
        "No DRC violations for same-net via touching conduction area");
  }

  @Test
  void differentNetConductionAreaIsObstacleForVia() {
    // Insert a fixed copper conduction area on net 1 (GND)
    ConductionArea area =
        board.insertConductionArea(
            new IntBox(0, 0, 2000, 2000), 0, new int[] {1}, 1, true, FixedState.SYSTEM_FIXED);

    // Insert a via of a different net (SIGNAL = net 2) overlapping the conduction area
    Via differentNetVia =
        board.insertVia(
            viaPadstack, new IntPoint(0, 0), new int[] {2}, 1, FixedState.USER_FIXED, true);

    assertTrue(
        differentNetVia.isObstacle(area),
        "Different net conduction area must be an obstacle to via");
    assertTrue(
        area.isObstacle(differentNetVia),
        "Different net via must be an obstacle to conduction area");

    DesignRulesChecker drc = new DesignRulesChecker(board, null);
    assertFalse(
        drc.getAllClearanceViolations().isEmpty(),
        "DRC must report violation for foreign net via overlapping conduction area");
  }
}
