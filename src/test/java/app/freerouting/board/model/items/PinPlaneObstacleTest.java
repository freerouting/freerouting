package app.freerouting.board.model.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.state.Communication;
import app.freerouting.core.library.Package;
import app.freerouting.core.library.Packages;
import app.freerouting.core.library.Padstacks;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PinPlaneObstacleTest {
  private RoutingBoard board(boolean compensated) {
    var layers =
        new LayerStructure(new Layer[] {new Layer("Top", true), new Layer("Bottom", true)});
    var rules = new BoardRules(layers, ClearanceMatrix.getDefaultInstance(layers, 10));
    rules.createDefaultNetClass();
    var bounds = new IntBox(-1000, -1000, 1000, 1000);
    var board =
        new RoutingBoard(
            bounds, layers, new PolylineShape[] {bounds}, 0, rules, new Communication());
    rules.nets.add("GND", 1, true);
    rules.nets.add("SIGNAL", 1, false);
    board.library.padstacks = new Padstacks(layers);
    board.library.packages = new Packages(board.library.padstacks);
    var pad = board.library.padstacks.add(new ConvexShape[] {new IntBox(-20, -20, 20, 20), null});
    var pkg =
        board.library.packages.add(
            new Package.Pin[] {new Package.Pin("1", pad.id, Vector.ZERO, 0)});
    var component = board.components.add(new IntPoint(0, 0), 0, true, pkg);
    board.insertPin(component.id, 0, new int[] {2}, 1, FixedState.UNFIXED);
    board.searchTreeManager.setClearanceCompensationUsed(compensated);
    return board;
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void planeBlockingFlagControlsBothDirectionsAndComprehensiveDrc(boolean compensated) {
    var board = board(compensated);
    var pin = board.getPins().iterator().next();
    var plane =
        board.insertConductionArea(
            new IntBox(-100, -100, 100, 100), 0, new int[] {1}, 1, false, FixedState.UNFIXED);
    var drc = new DesignRulesChecker(board, null);
    for (boolean blocking : new boolean[] {false, true, false, true, false}) {
      board.changePlaneAsObstacle(blocking);
      assertEquals(blocking, plane.getIsObstacle());
      assertEquals(blocking, pin.isObstacle(plane), "Pin must honor the current plane flag");
      assertEquals(blocking, plane.isObstacle(pin));
      assertEquals(blocking, !drc.getAllClearanceViolations().isEmpty());
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void sameNetPlaneNeverObstructsPin(boolean compensated) {
    var board = board(compensated);
    var pin = board.getPins().iterator().next();
    var plane =
        board.insertConductionArea(
            new IntBox(-100, -100, 100, 100), 0, new int[] {2}, 1, false, FixedState.UNFIXED);
    for (boolean blocking : new boolean[] {false, true}) {
      board.changePlaneAsObstacle(blocking);
      assertFalse(pin.isObstacle(plane));
      assertFalse(plane.isObstacle(pin));
      assertTrue(new DesignRulesChecker(board, null).getAllClearanceViolations().isEmpty());
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void auxiliaryCopperAndNetlessKeepoutRetainTheirRules(boolean compensated) {
    for (int[] nets : new int[][] {new int[] {1}, new int[] {2}, new int[0]}) {
      var board = board(compensated);
      var pin = board.getPins().iterator().next();
      var area =
          board.insertObstacle(
              new IntBox(-100, -100, 100, 100), 0, nets, 1, 0, "pad_aux", FixedState.UNFIXED);
      boolean foreignCopper = nets.length > 0 && nets[0] == 1;
      assertEquals(foreignCopper, pin.isObstacle(area));
      assertEquals(foreignCopper, area.isObstacle(pin));
      assertEquals(
          foreignCopper,
          !new DesignRulesChecker(board, null).getAllClearanceViolations().isEmpty());
    }
  }
}
