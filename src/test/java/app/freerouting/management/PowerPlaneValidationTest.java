package app.freerouting.management;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.*;
import app.freerouting.geometry.planar.*;
import app.freerouting.rules.*;
import app.freerouting.logger.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PowerPlaneValidationTest {

  @BeforeEach
  void setUp() {
    FRLogger.getLogEntries().clear();
  }

  private RoutingBoard createTestBoard(boolean withPlaneLayer) {
    Layer layer1 = new Layer("Top", true);
    Layer layer2 = new Layer("GND", !withPlaneLayer);
    Layer[] layers = new Layer[] { layer1, layer2 };
    LayerStructure layerStructure = new LayerStructure(layers);

    ClearanceMatrix clearanceMatrix = ClearanceMatrix.get_default_instance(layerStructure, 10);
    BoardRules boardRules = new BoardRules(layerStructure, clearanceMatrix);
    boardRules.create_default_net_class();

    Communication communication = new Communication();

    return new RoutingBoard(
        new IntBox(0, 0, 2000000, 2000000),
        layerStructure,
        new PolylineShape[] { TileShape.get_instance(0, 0, 2000000, 2000000) },
        0,
        boardRules,
        communication);
  }

  @Test
  void testCleanPowerPlanePasses() {
    RoutingBoard board = createTestBoard(true);
    
    // Add net
    Net gndNet = board.rules.nets.add("GND", 1, true);
    int[] netNoArr = new int[]{gndNet.net_number};

    // Add a conduction area to GND layer (layer index 1)
    Area area = TileShape.get_instance(100, 100, 10000, 10000);
    board.insert_conduction_area(area, 1, netNoArr, 0, false, FixedState.UNFIXED);

    HeadlessBoardManager manager = new HeadlessBoardManager(null);
    manager.board = board;
    
    manager.validatePowerPlanes();
    
    String logs = FRLogger.getLogEntries().getAsString();
    assertFalse(logs.contains("Power-plane validation failed"), "Clean power plane should not trigger validation failures. Logs:\n" + logs);
  }

  @Test
  void testTracesOnPowerPlaneTriggerWarning() {
    RoutingBoard board = createTestBoard(true);
    
    // Add net
    Net gndNet = board.rules.nets.add("GND", 1, true);
    int[] netNoArr = new int[]{gndNet.net_number};

    // Add conduction area so that "at least one conduction area" check passes
    Area area = TileShape.get_instance(100, 100, 10000, 10000);
    board.insert_conduction_area(area, 1, netNoArr, 0, false, FixedState.UNFIXED);

    // Insert trace on GND layer (layer index 1) using direct PolylineTrace constructor
    Polyline polyline = new Polyline(new IntPoint(500, 500), new IntPoint(1000, 1000));
    PolylineTrace trace = new PolylineTrace(polyline, 1, 10, netNoArr, 0, 0, 0, FixedState.UNFIXED, board);
    board.insert_item(trace);

    HeadlessBoardManager manager = new HeadlessBoardManager(null);
    manager.board = board;
    
    manager.validatePowerPlanes();
    
    String logs = FRLogger.getLogEntries().getAsString();
    assertTrue(logs.contains("Power-plane validation failed"), "Traces on power plane should trigger validation failure.");
    assertTrue(logs.contains("contains 1 signal wire(s)/trace(s)"), "Should mention trace count. Logs:\n" + logs);
    assertTrue(logs.contains("Proper Definition and Best Practices for Power Planes:"), "Should include the best practices description.");
  }

  @Test
  void testNoConductionAreaOnPowerPlaneTriggerWarning() {
    RoutingBoard board = createTestBoard(true);
    // GND layer (index 1) has no conduction areas defined.

    HeadlessBoardManager manager = new HeadlessBoardManager(null);
    manager.board = board;
    
    manager.validatePowerPlanes();
    
    String logs = FRLogger.getLogEntries().getAsString();
    assertTrue(logs.contains("Power-plane validation failed"), "Missing conduction area on power plane should trigger validation failure.");
    assertTrue(logs.contains("has no conduction areas defined"), "Should mention missing conduction area.");
  }

  @Test
  void testOverlappingConductionAreasOnPowerPlaneTriggerWarning() {
    RoutingBoard board = createTestBoard(true);
    
    // Add two nets
    Net vcc3v3 = board.rules.nets.add("3.3V", 1, true);
    Net vcc5v = board.rules.nets.add("5V", 1, true);
    
    // Add two overlapping conduction areas on GND layer (layer index 1)
    // Area 1: [100, 100] to [1000, 1000]
    Area area1 = TileShape.get_instance(100, 100, 1000, 1000);
    // Area 2: [500, 500] to [1500, 1500] (overlaps with Area 1)
    Area area2 = TileShape.get_instance(500, 500, 1500, 1500);
    
    board.insert_conduction_area(area1, 1, new int[]{vcc3v3.net_number}, 0, false, FixedState.UNFIXED);
    board.insert_conduction_area(area2, 1, new int[]{vcc5v.net_number}, 0, false, FixedState.UNFIXED);

    HeadlessBoardManager manager = new HeadlessBoardManager(null);
    manager.board = board;
    
    manager.validatePowerPlanes();
    
    String logs = FRLogger.getLogEntries().getAsString();
    assertTrue(logs.contains("Power-plane validation failed"), "Overlapping conduction areas should trigger validation failure.");
    assertTrue(logs.contains("has overlapping conduction areas"), "Should mention overlapping conduction areas.");
    assertTrue(logs.contains("3.3V") && logs.contains("5V"), "Should list overlapping net names '3.3V' and '5V'. Logs:\n" + logs);
  }
}
