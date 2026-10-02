package app.freerouting.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.state.Communication;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.TileShape;
import org.junit.jupiter.api.Test;

class NetLengthConstraintTest {

  private RoutingBoard createTestBoard() {
    Layer layer1 = new Layer("Top", true);
    Layer[] layers = new Layer[] {layer1};
    LayerStructure layerStructure = new LayerStructure(layers);

    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(layerStructure, 10);
    BoardRules boardRules = new BoardRules(layerStructure, clearanceMatrix);
    boardRules.createDefaultNetClass();

    Communication communication = new Communication();

    return new RoutingBoard(
        new IntBox(0, 0, 2000000, 2000000),
        layerStructure,
        new PolylineShape[] {TileShape.getInstance(0, 0, 2000000, 2000000)},
        0,
        boardRules,
        communication);
  }

  @Test
  void unconstrainedInstanceHasNoBounds() {
    NetLengthConstraint constraint = NetLengthConstraint.UNCONSTRAINED;
    assertFalse(constraint.hasMin());
    assertFalse(constraint.hasMax());
    assertFalse(constraint.isConstrained());
    assertEquals(0.0, constraint.targetLength(), 1e-6);
    assertTrue(constraint.isSatisfied(0.0));
    assertTrue(constraint.isSatisfied(500.0));
  }

  @Test
  void minOnlyConstraint() {
    NetLengthConstraint constraint = new NetLengthConstraint(25.0, 0.0);
    assertTrue(constraint.hasMin());
    assertFalse(constraint.hasMax());
    assertTrue(constraint.isConstrained());
    assertEquals(25.0, constraint.targetLength(), 1e-6);

    assertFalse(constraint.isSatisfied(24.9));
    assertTrue(constraint.isSatisfied(25.0));
    assertTrue(constraint.isSatisfied(100.0));
  }

  @Test
  void maxOnlyConstraint() {
    NetLengthConstraint constraint = new NetLengthConstraint(0.0, 50.0);
    assertFalse(constraint.hasMin());
    assertTrue(constraint.hasMax());
    assertTrue(constraint.isConstrained());
    assertEquals(50.0, constraint.targetLength(), 1e-6);

    assertTrue(constraint.isSatisfied(10.0));
    assertTrue(constraint.isSatisfied(50.0));
    assertFalse(constraint.isSatisfied(50.1));
  }

  @Test
  void boundedConstraintCalculatesMidpointTarget() {
    NetLengthConstraint constraint = new NetLengthConstraint(20.0, 60.0);
    assertTrue(constraint.hasMin());
    assertTrue(constraint.hasMax());
    assertTrue(constraint.isConstrained());
    assertEquals(40.0, constraint.targetLength(), 1e-6);

    assertFalse(constraint.isSatisfied(19.9));
    assertTrue(constraint.isSatisfied(20.0));
    assertTrue(constraint.isSatisfied(40.0));
    assertTrue(constraint.isSatisfied(60.0));
    assertFalse(constraint.isSatisfied(60.1));
  }

  @Test
  void netInheritsConstraintFromNetClassByDefault() {
    RoutingBoard board = createTestBoard();
    BoardRules rules = board.rules;
    Nets nets = rules.nets;

    NetClass netClass = rules.getDefaultNetClass();
    netClass.setMinimumTraceLength(15.0);
    netClass.setMaximumTraceLength(45.0);

    Net net = nets.add("TEST_NET", 1, false);
    net.setClass(netClass);

    assertFalse(net.hasExplicitLengthConstraint());
    assertEquals(15.0, net.getMinimumTraceLength(), 1e-6);
    assertEquals(45.0, net.getMaximumTraceLength(), 1e-6);

    NetLengthConstraint effective = net.getLengthConstraint();
    assertEquals(15.0, effective.minLength(), 1e-6);
    assertEquals(45.0, effective.maxLength(), 1e-6);
    assertEquals(30.0, effective.targetLength(), 1e-6);
  }

  @Test
  void netExplicitConstraintOverridesNetClass() {
    RoutingBoard board = createTestBoard();
    BoardRules rules = board.rules;
    Nets nets = rules.nets;

    NetClass netClass = rules.getDefaultNetClass();
    netClass.setMinimumTraceLength(10.0);
    netClass.setMaximumTraceLength(100.0);

    Net net = nets.add("TUNED_NET", 1, false);
    net.setClass(netClass);

    // Explicitly override only the minimum length
    net.setMinimumTraceLength(25.0);
    assertTrue(net.hasExplicitLengthConstraint());
    assertEquals(25.0, net.getMinimumTraceLength(), 1e-6);
    assertEquals(100.0, net.getMaximumTraceLength(), 1e-6);

    // Explicitly override the maximum length
    net.setMaximumTraceLength(75.0);
    assertEquals(25.0, net.getMinimumTraceLength(), 1e-6);
    assertEquals(75.0, net.getMaximumTraceLength(), 1e-6);

    // Reset explicit constraint to null -> restores class defaults
    net.setLengthConstraint(null);
    assertFalse(net.hasExplicitLengthConstraint());
    assertEquals(10.0, net.getMinimumTraceLength(), 1e-6);
    assertEquals(100.0, net.getMaximumTraceLength(), 1e-6);
  }

  @Test
  void netClassLengthConstraintConvenience() {
    RoutingBoard board = createTestBoard();
    BoardRules rules = board.rules;
    NetClass netClass = rules.getDefaultNetClass();

    netClass.setLengthConstraint(new NetLengthConstraint(12.5, 37.5));
    assertEquals(12.5, netClass.getMinimumTraceLength(), 1e-6);
    assertEquals(37.5, netClass.getMaximumTraceLength(), 1e-6);

    NetLengthConstraint constraint = netClass.getLengthConstraint();
    assertEquals(12.5, constraint.minLength(), 1e-6);
    assertEquals(37.5, constraint.maxLength(), 1e-6);

    netClass.setLengthConstraint(null);
    assertEquals(0.0, netClass.getMinimumTraceLength(), 1e-6);
    assertEquals(0.0, netClass.getMaximumTraceLength(), 1e-6);
  }
}
