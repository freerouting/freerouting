package app.freerouting.autoroute.fanout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.fanout.BgaBufferMarshaller.MarshaledBgaData;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.structure.Component;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.board.state.BoardObserverAdaptor;
import app.freerouting.board.state.Communication;
import app.freerouting.core.library.Package;
import app.freerouting.core.library.Packages;
import app.freerouting.core.library.Padstack;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.io.CoordinateTransform;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.settings.RouterSettings;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BgaBufferMarshallerTest {

  private static RoutingBoard createTestBoard(double resUm) {
    Layer layer1 = new Layer("Top", true);
    Layer layer2 = new Layer("Bottom", true);
    LayerStructure layerStructure = new LayerStructure(new Layer[] {layer1, layer2});

    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(layerStructure, 10);
    BoardRules boardRules = new BoardRules(layerStructure, clearanceMatrix);
    boardRules.createDefaultNetClass();

    Communication communication =
        new Communication(
            Unit.UM,
            (int) Math.round(resUm),
            new Communication.SpecctraParserInfo("\"", null, null, null, null, false),
            new CoordinateTransform(resUm, 0, 0),
            new ItemIdGenerator(),
            new BoardObserverAdaptor());

    RoutingBoard board =
        new RoutingBoard(
            new IntBox(0, 0, 100_000, 100_000),
            layerStructure,
            new PolylineShape[] {TileShape.getInstance(0, 0, 100_000, 100_000)},
            0,
            boardRules,
            communication);

    board.library.padstacks = new app.freerouting.core.library.Padstacks(layerStructure);
    board.library.packages = new Packages(board.library.padstacks);
    board.rules.nets.add("SIG", 1, false);
    return board;
  }

  @Test
  void testBoundedGridStepQuantizationFinePitch() {
    RoutingBoard board = createTestBoard(1.0); // 1 unit = 1 um

    // 0.4 mm (400 um) fine-pitch BGA
    double pitchUm = 400.0;
    double pitchBoardUnits = 400.0;
    int gridStep = BgaBufferMarshaller.computeGridStep(board, pitchUm, pitchBoardUnits);

    // Delta min = max(5 um, 400 / 32 = 12) = 12 um
    // Delta max = max(12, 400 / 4 = 100) = 100 um
    // candidate = min(400/8=50, (wmin + cmin)/2)
    assertTrue(gridStep >= 12, "Fine pitch grid step must be >= deltaMin (12)");
    assertTrue(gridStep <= 100, "Fine pitch grid step must be <= deltaMax (100)");
  }

  @Test
  void testBoundedGridStepQuantizationStandardPitch() {
    RoutingBoard board = createTestBoard(1.0);

    // 0.8 mm (800 um) pitch BGA
    double pitchUm = 800.0;
    double pitchBoardUnits = 800.0;
    int gridStep = BgaBufferMarshaller.computeGridStep(board, pitchUm, pitchBoardUnits);

    // Delta min = max(10 um, 800 / 16 = 50) = 50 um
    // Delta max = max(50, 800 / 4 = 200) = 200 um
    assertTrue(gridStep >= 50, "Standard pitch grid step must be >= deltaMin (50)");
    assertTrue(gridStep <= 200, "Standard pitch grid step must be <= deltaMax (200)");
  }

  @Test
  void testBufferMarshallingAndAlignment() {
    RoutingBoard board = createTestBoard(1.0);

    // Build 8x8 BGA = 64 pins on 800 um pitch
    int pitch = 800;
    Padstack pad =
        board.library.padstacks.add(
            new ConvexShape[] {new Circle(Point.ZERO, 150), new Circle(Point.ZERO, 150)});

    List<Package.Pin> pkgPins = new ArrayList<>();
    for (int r = 0; r < 8; r++) {
      for (int c = 0; c < 8; c++) {
        double lx = (c - 3.5) * pitch;
        double ly = (r - 3.5) * pitch;
        pkgPins.add(new Package.Pin("P" + (r * 8 + c), pad.id, new FloatPoint(lx, ly), 0));
      }
    }

    Package pkg = board.library.packages.add(pkgPins.toArray(new Package.Pin[0]));
    Component comp =
        board.components.addPrecise(
            "U1", new FloatPoint(50000, 50000), 0.0, true, pkg, pkg, false, null);

    List<Pin> boardPins = new ArrayList<>();
    for (int i = 0; i < 64; i++) {
      Pin p = board.insertPin(comp.id, i, new int[] {1}, 1, FixedState.UNFIXED);
      boardPins.add(p);
    }

    RouterSettings settings = new RouterSettings();
    settings.fanout.gpuMinBgaPins = 64;
    settings.fanout.gpuMaxVramMb = 256;

    BgaEscapeDescriptor desc =
        BgaFootprintDetector.analyzeComponent(board, comp, boardPins, settings);
    assertNotNull(desc);

    try (MarshaledBgaData data = BgaBufferMarshaller.marshal(board, desc, settings)) {
      assertNotNull(data);
      assertEquals(64, data.pinCount());
      assertNotNull(data.pinBuffer());
      assertNotNull(data.obstacleBuffer());
      assertNotNull(data.ruleBuffer());

      // 64-byte alignment check
      assertEquals(0, data.pinBuffer().address() % 64, "PinBuffer must be 64-byte aligned");
      assertEquals(
          0, data.obstacleBuffer().address() % 64, "ObstacleBuffer must be 64-byte aligned");
      assertEquals(0, data.ruleBuffer().address() % 64, "RuleBuffer must be 64-byte aligned");

      // Verify pin payload layout (N * 12 * 4 bytes)
      long expectedPinBytes = 64L * 12 * 4;
      assertEquals(expectedPinBytes, data.pinBuffer().byteSize());

      // Read first pin targetLayer (offset 24) and quadrant U (offset 32)
      int pin0TargetLayer = data.pinBuffer().get(ValueLayout.JAVA_INT, 24);
      assertTrue(
          pin0TargetLayer >= 0 && pin0TargetLayer < data.layerCount(),
          "Target layer must be within valid layer bounds");
      int pin0Qu = data.pinBuffer().get(ValueLayout.JAVA_INT, 32);
      assertTrue(pin0Qu == 1 || pin0Qu == -1, "Quadrant U must be +1 or -1");
    }
  }
}
