package app.freerouting.autoroute.fanout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.fanout.BgaEscapeSolver.PinEscapeProposal;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BgaEscapeSolverTest {

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

  @Test
  void testBoundedGridStepQuantizationFinePitch() {
    RoutingBoard board = createTestBoard();

    // 0.4 mm (400 um) fine-pitch BGA
    double pitchUm = 400.0;
    double pitchBoardUnits = 400.0;
    int gridStep = BgaEscapeSolver.computeGridStep(board, pitchUm, pitchBoardUnits);

    // deltaMin = max(5 um, 400 / 32 = 12) = 12 um
    // deltaMax = max(12, 400 / 4 = 100) = 100 um
    assertTrue(gridStep >= 12, "Fine pitch grid step must be >= deltaMin (12)");
    assertTrue(gridStep <= 100, "Fine pitch grid step must be <= deltaMax (100)");
  }

  @Test
  void testBoundedGridStepQuantizationStandardPitch() {
    RoutingBoard board = createTestBoard();

    // 0.8 mm (800 um) pitch BGA
    double pitchUm = 800.0;
    double pitchBoardUnits = 800.0;
    int gridStep = BgaEscapeSolver.computeGridStep(board, pitchUm, pitchBoardUnits);

    // deltaMin = max(10 um, 800 / 16 = 50) = 50 um
    // deltaMax = max(50, 800 / 4 = 200) = 200 um
    assertTrue(gridStep >= 50, "Standard pitch grid step must be >= deltaMin (50)");
    assertTrue(gridStep <= 200, "Standard pitch grid step must be <= deltaMax (200)");
  }

  @Test
  void testEscapeWavefrontDeterminismAcrossConsecutiveRuns() {
    RoutingBoard board = createTestBoard();

    // 8x8 = 64 pin BGA on 800 um pitch
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
    settings.fanout.bgaEscapeMinPins = 64;

    BgaEscapeDescriptor desc =
        BgaFootprintDetector.analyzeComponent(board, comp, boardPins, settings);
    assertNotNull(desc);

    List<PinEscapeProposal> run1 = BgaEscapeSolver.solve(board, desc, settings);
    assertNotNull(run1);
    assertFalse(run1.isEmpty());

    // 50 consecutive runs must produce identical via targets and costs
    for (int run = 2; run <= 50; run++) {
      List<PinEscapeProposal> runK = BgaEscapeSolver.solve(board, desc, settings);
      assertEquals(run1.size(), runK.size(), "Run " + run + " size mismatch");

      for (int i = 0; i < run1.size(); i++) {
        PinEscapeProposal p1 = run1.get(i);
        PinEscapeProposal pK = runK.get(i);
        assertEquals(p1.escaped(), pK.escaped(), "Escaped mismatch at pin " + i);
        assertEquals(p1.cost(), pK.cost(), "Cost mismatch at pin " + i);
        if (p1.escaped() && p1.viaCenter() != null) {
          assertEquals(p1.viaCenter().x, pK.viaCenter().x, "X mismatch at pin " + i);
          assertEquals(p1.viaCenter().y, pK.viaCenter().y, "Y mismatch at pin " + i);
        }
      }
    }
  }

  @Test
  void testNoInterNetViaCollisionsInEscapeProposals() {
    RoutingBoard board = createTestBoard();

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
            "U2", new FloatPoint(50000, 50000), 0.0, true, pkg, pkg, false, null);

    List<Pin> boardPins = new ArrayList<>();
    for (int i = 0; i < 64; i++) {
      Pin p = board.insertPin(comp.id, i, new int[] {i + 1}, 1, FixedState.UNFIXED);
      boardPins.add(p);
    }

    RouterSettings settings = new RouterSettings();
    settings.fanout.bgaEscapeMinPins = 64;

    BgaEscapeDescriptor desc =
        BgaFootprintDetector.analyzeComponent(board, comp, boardPins, settings);
    assertNotNull(desc);

    List<PinEscapeProposal> proposals = BgaEscapeSolver.solve(board, desc, settings);

    Set<Point> usedViaPoints = new HashSet<>();
    for (PinEscapeProposal prop : proposals) {
      if (prop.escaped() && prop.viaCenter() != null) {
        Point pt = prop.viaCenter();
        assertFalse(usedViaPoints.contains(pt), "Via collision detected at: " + pt);
        usedViaPoints.add(pt);
      }
    }
    assertTrue(usedViaPoints.size() > 0, "Must escape at least some pins");
  }
}
