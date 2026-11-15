package app.freerouting.autoroute.accelerator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import app.freerouting.autoroute.fanout.BgaEscapeDescriptor;
import app.freerouting.autoroute.fanout.BgaFootprintDetector;
import app.freerouting.autoroute.fanout.GpuBgaEscapeSolver;
import app.freerouting.autoroute.fanout.GpuBgaEscapeSolver.PinEscapeProposal;
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

class OpenClHardwareExecutionTest {

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
  void testRealOpenClHardwareExecutionOnSyntheticBga() {
    RouterSettings settings = new RouterSettings();
    settings.fanout.gpuMinBgaPins = 64;
    settings.fanout.gpuAccelerationEnabled = true;

    AcceleratorRegistry.reset();
    ComputeBackend backend = AcceleratorRegistry.discover(settings.fanout);
    System.out.println(
        "Discovered backend: "
            + backend.getClass().getSimpleName()
            + ", available="
            + backend.isAvailable()
            + ", fallbackReason="
            + backend.fallbackReason());
    assertNotNull(backend, "Accelerator backend must not be null");
    assumeTrue(
        backend.isAvailable(),
        "Skipping OpenCL hardware test: No OpenCL accelerator available on host ("
            + backend.fallbackReason()
            + ")");

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
            "U_BGA_64", new FloatPoint(50000, 50000), 0.0, true, pkg, pkg, false, null);

    List<Pin> boardPins = new ArrayList<>();
    for (int i = 0; i < 64; i++) {
      Pin p = board.insertPin(comp.id, i, new int[] {i + 1}, 1, FixedState.UNFIXED);
      boardPins.add(p);
    }

    BgaEscapeDescriptor desc =
        BgaFootprintDetector.analyzeComponent(board, comp, boardPins, settings);
    assertNotNull(desc, "Descriptor must be detected");

    List<PinEscapeProposal> proposals = GpuBgaEscapeSolver.solve(board, desc, settings);
    assertNotNull(proposals, "Proposals must not be null");
    assertFalse(proposals.isEmpty(), "Proposals must not be empty");

    long escapedCount = proposals.stream().filter(PinEscapeProposal::escaped).count();
    System.out.println(
        "Real OpenCL Hardware Execution: escaped "
            + escapedCount
            + " / 64 pins on backend "
            + backend.getClass().getSimpleName());
    assertTrue(escapedCount > 0, "Must escape at least some pins on GPU hardware");
  }
}
