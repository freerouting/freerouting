package app.freerouting.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.state.Communication;
import app.freerouting.core.RoutingJob;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.DifferentialPair;
import app.freerouting.rules.Net;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DifferentialPairPostLoadTest {

  private RoutingBoard testBoard;
  private HeadlessBoardManager manager;

  @BeforeEach
  void setUp() {
    Layer layer1 = new Layer("Top", true);
    LayerStructure layerStructure = new LayerStructure(new Layer[] {layer1});

    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(layerStructure, 10);
    BoardRules boardRules = new BoardRules(layerStructure, clearanceMatrix);
    boardRules.createDefaultNetClass();

    Communication communication = new Communication();

    testBoard =
        new RoutingBoard(
            new IntBox(0, 0, 1000000, 1000000),
            layerStructure,
            new PolylineShape[] {TileShape.getInstance(0, 0, 1000000, 1000000)},
            0,
            boardRules,
            communication);

    RoutingJob routingJob = new RoutingJob();
    manager = new HeadlessBoardManager(routingJob);
    manager.board = testBoard;
  }

  @Test
  void testDifferentialPairAutoDetectionOnPostLoad() {
    Net usbP = testBoard.rules.nets.add("USB_+", 1, false);
    Net usbN = testBoard.rules.nets.add("USB_-", 1, false);
    Net clkP = testBoard.rules.nets.add("CLK_P", 1, false);
    Net clkN = testBoard.rules.nets.add("CLK_N", 1, false);
    Net dP = testBoard.rules.nets.add("D+", 1, false);
    Net dN = testBoard.rules.nets.add("D-", 1, false);
    Net reset = testBoard.rules.nets.add("RESET", 1, false);

    assertTrue(testBoard.rules.differentialPairs.isEmpty());

    manager.applyImmediatePostLoadProcessing();

    assertEquals(3, testBoard.rules.differentialPairs.count());

    DifferentialPair usb = testBoard.rules.differentialPairs.get("USB");
    assertNotNull(usb);
    assertEquals(usbP, usb.positiveNet());
    assertEquals(usbN, usb.negativeNet());

    DifferentialPair clk = testBoard.rules.differentialPairs.get("CLK");
    assertNotNull(clk);
    assertEquals(clkP, clk.positiveNet());
    assertEquals(clkN, clk.negativeNet());

    DifferentialPair dPair = testBoard.rules.differentialPairs.get("D");
    assertNotNull(dPair);
    assertEquals(dP, dPair.positiveNet());
    assertEquals(dN, dPair.negativeNet());

    assertNull(testBoard.rules.differentialPairs.findForNet(reset));
  }
}
