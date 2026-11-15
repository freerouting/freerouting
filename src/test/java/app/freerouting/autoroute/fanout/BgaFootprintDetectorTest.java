package app.freerouting.autoroute.fanout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.fanout.BgaEscapeDescriptor.LatticeClass;
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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BgaFootprintDetectorTest {

  private static RoutingBoard createTestBoard() {
    Layer layer1 = new Layer("Top", true);
    Layer layer2 = new Layer("Bottom", true);
    LayerStructure layerStructure = new LayerStructure(new Layer[] {layer1, layer2});

    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(layerStructure, 10);
    BoardRules boardRules = new BoardRules(layerStructure, clearanceMatrix);
    boardRules.createDefaultNetClass();

    Communication communication =
        new Communication(
            Unit.UM,
            1,
            new Communication.SpecctraParserInfo("\"", null, null, null, null, false),
            new CoordinateTransform(1, 0, 0),
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

  @ParameterizedTest
  @ValueSource(doubles = {0.0, 45.0, 90.0, 180.0})
  void testSquareBgaClassification(double rotationDeg) {
    RoutingBoard board = createTestBoard();

    // 16x16 = 256 balls on 1000 um pitch
    int pitch = 1000;
    Padstack pad =
        board.library.padstacks.add(
            new ConvexShape[] {new Circle(Point.ZERO, 150), new Circle(Point.ZERO, 150)});

    List<Package.Pin> pkgPins = new ArrayList<>();
    for (int r = 0; r < 16; r++) {
      for (int c = 0; c < 16; c++) {
        double lx = (c - 7.5) * pitch;
        double ly = (r - 7.5) * pitch;
        pkgPins.add(new Package.Pin("P" + (r * 16 + c), pad.id, new FloatPoint(lx, ly), 0));
      }
    }

    Package pkg = board.library.packages.add(pkgPins.toArray(new Package.Pin[0]));
    Component comp =
        board.components.addPrecise(
            "U1", new FloatPoint(50000, 50000), rotationDeg, true, pkg, pkg, false, null);

    List<Pin> boardPins = new ArrayList<>();
    for (int i = 0; i < 256; i++) {
      Pin p = board.insertPin(comp.id, i, new int[] {1}, 1, FixedState.UNFIXED);
      boardPins.add(p);
    }

    RouterSettings settings = new RouterSettings();
    settings.fanout.gpuMinBgaPins = 64;

    BgaEscapeDescriptor desc =
        BgaFootprintDetector.analyzeComponent(board, comp, boardPins, settings);

    assertNotNull(desc, "16x16 BGA must be detected as BGA");
    assertEquals(256, desc.pins().size());
    assertEquals(LatticeClass.ORTHOGONAL_GRID, desc.latticeClass());
    assertTrue(Math.abs(desc.pitchBoardUnits() - pitch) < 5.0, "Pitch should match nominal pitch");
    assertEquals(7, desc.maxRing(), "16x16 BGA has rings 0 through 7 (8 rings total)");
  }

  @Test
  void testQfpNegativeControl() {
    RoutingBoard board = createTestBoard();

    // QFP perimeter pins only (16x16 perimeter = 60 pins, 0 interior)
    int pitch = 500;
    Padstack pad =
        board.library.padstacks.add(
            new ConvexShape[] {new Circle(Point.ZERO, 100), new Circle(Point.ZERO, 100)});

    List<Package.Pin> pkgPins = new ArrayList<>();
    for (int r = 0; r < 16; r++) {
      for (int c = 0; c < 16; c++) {
        if (r == 0 || r == 15 || c == 0 || c == 15) {
          double lx = (c - 7.5) * pitch;
          double ly = (r - 7.5) * pitch;
          pkgPins.add(new Package.Pin("P" + pkgPins.size(), pad.id, new FloatPoint(lx, ly), 0));
        }
      }
    }

    Package pkg = board.library.packages.add(pkgPins.toArray(new Package.Pin[0]));
    Component comp =
        board.components.addPrecise(
            "U2", new FloatPoint(50000, 50000), 0.0, true, pkg, pkg, false, null);

    List<Pin> boardPins = new ArrayList<>();
    for (int i = 0; i < pkgPins.size(); i++) {
      Pin p = board.insertPin(comp.id, i, new int[] {1}, 1, FixedState.UNFIXED);
      boardPins.add(p);
    }

    RouterSettings settings = new RouterSettings();
    settings.fanout.gpuMinBgaPins = 32;

    BgaEscapeDescriptor desc =
        BgaFootprintDetector.analyzeComponent(board, comp, boardPins, settings);
    assertNull(desc, "Perimeter-only QFP must not be classified as area-array BGA");
  }

  @Test
  void testDepopulatedCavityBga() {
    RoutingBoard board = createTestBoard();

    // 16x16 BGA with center 8x8 depopulated (256 - 64 = 192 balls)
    int pitch = 800;
    Padstack pad =
        board.library.padstacks.add(
            new ConvexShape[] {new Circle(Point.ZERO, 120), new Circle(Point.ZERO, 120)});

    List<Package.Pin> pkgPins = new ArrayList<>();
    for (int r = 0; r < 16; r++) {
      for (int c = 0; c < 16; c++) {
        if (r >= 4 && r < 12 && c >= 4 && c < 12) {
          continue; // center cavity
        }
        double lx = (c - 7.5) * pitch;
        double ly = (r - 7.5) * pitch;
        pkgPins.add(new Package.Pin("P" + pkgPins.size(), pad.id, new FloatPoint(lx, ly), 0));
      }
    }

    Package pkg = board.library.packages.add(pkgPins.toArray(new Package.Pin[0]));
    Component comp =
        board.components.addPrecise(
            "U3", new FloatPoint(50000, 50000), 0.0, true, pkg, pkg, false, null);

    List<Pin> boardPins = new ArrayList<>();
    for (int i = 0; i < pkgPins.size(); i++) {
      Pin p = board.insertPin(comp.id, i, new int[] {1}, 1, FixedState.UNFIXED);
      boardPins.add(p);
    }

    RouterSettings settings = new RouterSettings();
    settings.fanout.gpuMinBgaPins = 64;

    BgaEscapeDescriptor desc =
        BgaFootprintDetector.analyzeComponent(board, comp, boardPins, settings);
    assertNotNull(desc);
    assertEquals(192, desc.pins().size());
    assertEquals(LatticeClass.DEPOPULATED_CAVITY_GRID, desc.latticeClass());
  }

  @Test
  void testPouredLayerExclusionAndConductionAreaPlaneDrop() {
    Layer l0 = new Layer("F.Cu", true);
    Layer l1 = new Layer("In1.Cu", true); // Will contain BGA-spanning GND copper pour
    Layer l2 = new Layer("In2.Cu", true); // Open signal layer
    Layer l3 = new Layer("B.Cu", true);
    LayerStructure layerStructure = new LayerStructure(new Layer[] {l0, l1, l2, l3});

    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(layerStructure, 10);
    BoardRules boardRules = new BoardRules(layerStructure, clearanceMatrix);
    boardRules.createDefaultNetClass();

    Communication communication =
        new Communication(
            Unit.UM,
            1,
            new Communication.SpecctraParserInfo("\"", null, null, null, null, false),
            new CoordinateTransform(1, 0, 0),
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
    board.rules.nets.add("GND", 2, false);

    // Insert BGA-spanning GND ConductionArea on In1.Cu (layer 1)
    board.insertConductionArea(
        TileShape.getInstance(0, 0, 100_000, 100_000),
        1,
        new int[] {2},
        1,
        false,
        FixedState.SYSTEM_FIXED);

    // 8x8 BGA component on Top (layer 0)
    int pitch = 1000;
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
      // Pins 0..15 are GND (net 2); pins 16..63 are SIG (net 1)
      int netNo = (i < 16) ? 2 : 1;
      Pin p = board.insertPin(comp.id, i, new int[] {netNo}, 1, FixedState.UNFIXED);
      boardPins.add(p);
    }

    RouterSettings settings = new RouterSettings();
    settings.fanout.gpuMinBgaPins = 64;

    BgaEscapeDescriptor desc =
        BgaFootprintDetector.analyzeComponent(board, comp, boardPins, settings);
    assertNotNull(desc);

    // Verify GND pins classified as PLANE_DROP
    for (int i = 0; i < 16; i++) {
      BgaEscapeDescriptor.BgaPinDescriptor pd = desc.pins().get(i);
      if (pd.netNo() == 2) {
        assertEquals(
            BgaEscapeDescriptor.PinDisposition.PLANE_DROP,
            pd.disposition(),
            "GND pin matching ConductionArea must be classified as PLANE_DROP");
      }
    }

    // Verify In1.Cu (layer 1) excluded from peelable signalLayers for inner ring signal breakouts
    for (BgaEscapeDescriptor.BgaPinDescriptor pd : desc.pins()) {
      if (pd.netNo() == 1 && pd.ring() >= 2) {
        assertEquals(
            2,
            pd.targetLayer(),
            "Signal escape must bypass poured In1.Cu and route on open signal layer In2.Cu");
      }
    }
  }

  @Test
  void testSmallBgaWlcspDetection() {
    RoutingBoard board = createTestBoard();

    // 4x4 = 16 balls on 500 um pitch (typical small WLCSP / IoT micro-BGA)
    int pitch = 500;
    Padstack pad =
        board.library.padstacks.add(new ConvexShape[] {new Circle(Point.ZERO, 100), null});

    List<Package.Pin> pkgPins = new ArrayList<>();
    for (int r = 0; r < 4; r++) {
      for (int c = 0; c < 4; c++) {
        double lx = (c - 1.5) * pitch;
        double ly = (r - 1.5) * pitch;
        pkgPins.add(new Package.Pin("P" + (r * 4 + c), pad.id, new FloatPoint(lx, ly), 0));
      }
    }

    Package pkg = board.library.packages.add(pkgPins.toArray(new Package.Pin[0]));
    Component comp =
        board.components.addPrecise(
            "U_WLCSP16", new FloatPoint(50000, 50000), 0.0, true, pkg, pkg, false, null);

    for (int i = 0; i < 16; i++) {
      board.insertPin(comp.id, i, new int[] {1}, 1, FixedState.UNFIXED);
    }

    RouterSettings settings = new RouterSettings();
    settings.fanout.gpuMinBgaPins = 9;

    List<BgaEscapeDescriptor> detected = BgaFootprintDetector.detect(board, settings, null);
    assertEquals(1, detected.size(), "Small 16-pin 4x4 WLCSP must be detected as BGA");
    BgaEscapeDescriptor desc = detected.get(0);
    assertEquals(16, desc.pins().size());
    assertEquals(LatticeClass.ORTHOGONAL_GRID, desc.latticeClass());
    assertTrue(Math.abs(desc.pitchBoardUnits() - pitch) < 5.0, "Pitch should match 500 um");
    assertEquals(1, desc.maxRing(), "4x4 BGA has ring 0 (perimeter) and ring 1 (interior)");
  }
}
