package app.freerouting.board.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.state.Communication;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.settings.PhysicsSettings;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link BoardFieldContext} quantization, bounds clamping, and buffer reuse. */
class BoardFieldContextTest {

  private static RoutingBoard createSyntheticBoard(int layerCount, IntBox outlineBox) {
    Layer[] layers = new Layer[layerCount];
    for (int i = 0; i < layerCount; i++) {
      layers[i] = new Layer("L" + i, true);
    }
    LayerStructure layerStructure = new LayerStructure(layers);
    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(layerStructure, 100);
    BoardRules rules = new BoardRules(layerStructure, clearanceMatrix);
    Communication communication = new Communication();
    PolylineShape[] shapes =
        outlineBox != null ? new PolylineShape[] {outlineBox} : new PolylineShape[0];
    IntBox boundingBox = outlineBox != null ? outlineBox : IntBox.EMPTY;
    return new RoutingBoard(boundingBox, layerStructure, shapes, 0, rules, communication);
  }

  @Test
  void handlesSingleLayerAndNegativeOffsetCoordinates() {
    RoutingBoard board = createSyntheticBoard(1, new IntBox(-5000, -3000, 5000, 7000));
    BoardFieldContext ctx = BoardFieldContext.fromBoard(board, 0.5);

    assertEquals(1, ctx.getLayerCount());
    assertTrue(ctx.getGridWidth() >= 1);
    assertTrue(ctx.getGridHeight() >= 1);
    assertEquals(-5000L, ctx.getOriginX());
    assertEquals(-3000L, ctx.getOriginY());

    // Out-of-bounds coordinates clamp cleanly without negative or overflowing indices.
    assertEquals(0, ctx.toCellX(-1_000_000));
    assertEquals(0, ctx.toCellY(-1_000_000));
    assertEquals(ctx.getGridWidth() - 1, ctx.toCellX(1_000_000));
    assertEquals(ctx.getGridHeight() - 1, ctx.toCellY(1_000_000));
    assertEquals(0, ctx.clampLayer(-2));
    assertEquals(0, ctx.clampLayer(5));

    int idx = ctx.cellIndex(0, ctx.toCellX(0), ctx.toCellY(0));
    ctx.addEnergy(idx, 2.5f);
    ctx.markOccupancy(idx, (byte) 0x01);
    assertEquals(2.5f, ctx.getEnergy(idx));
    assertEquals((byte) 0x01, ctx.getOccupancy(idx));

    ctx.clearFields();
    assertEquals(0.0f, ctx.getEnergy(idx));
    assertEquals((byte) 0, ctx.getOccupancy(idx));
  }

  @Test
  void handlesMultiLayerMinimalAndEmptyBounds() {
    RoutingBoard emptyBoard = createSyntheticBoard(4, null);
    BoardFieldContext ctx = BoardFieldContext.fromBoard(emptyBoard, 0.5);

    assertEquals(4, ctx.getLayerCount());
    assertTrue(ctx.getGridWidth() >= 1);
    assertTrue(ctx.getGridHeight() >= 1);

    int idxL0 = ctx.cellIndex(0, 0, 0);
    int idxL3 = ctx.cellIndex(3, 0, 0);
    assertTrue(idxL3 > idxL0);
  }

  @Test
  void capsHugeBoardCellCountViaAdaptiveCoarsening() {
    // 500 mm x 500 mm at 10_000 units/mm = 5_000_000 x 5_000_000 board units
    RoutingBoard hugeBoard = createSyntheticBoard(6, new IntBox(0, 0, 5_000_000, 5_000_000));
    BoardFieldContext ctx = BoardFieldContext.fromBoard(hugeBoard, 0.05);

    long totalCells = (long) ctx.getLayerCount() * ctx.getCellsPerLayer();
    assertTrue(
        totalCells <= BoardFieldContext.MAX_CELLS_PER_LAYER,
        "Total grid cells must stay capped at MAX_CELLS_PER_LAYER, got " + totalCells);
    assertTrue(ctx.getEffectiveResolutionMm() > 0.05);
  }

  @Test
  void reusesScratchBuffersWithoutReallocation() {
    RoutingBoard board = createSyntheticBoard(2, new IntBox(0, 0, 1000, 1000));
    BoardFieldContext ctx = BoardFieldContext.fromBoard(board, 0.5);

    int[] buf1 = ctx.acquireScratchCellIndices(32);
    int[] buf2 = ctx.acquireScratchCellIndices(32);
    assertSame(buf1, buf2);
  }

  @Test
  void identityPassEvaluatesZeroEnergyDeterministically() {
    RoutingBoard board = createSyntheticBoard(2, new IntBox(0, 0, 10_000, 10_000));
    PhysicsSettings settings = new PhysicsSettings();
    settings.enabled = true;
    settings.gridResolutionMm = 0.5;
    settings.maxIterations = 4;
    settings.identityWeight = 1.0;
    settings.repulsionWeight = 0.0;

    PhysicsOptimizationPass.Result result = PhysicsOptimizationPass.run(board, settings, null);
    assertEquals(0.0, result.initialEnergy());
    assertEquals(0.0, result.finalEnergy());
    assertEquals(0, result.iterations());
    assertEquals(0, result.acceptedMoves());
  }

  @Test
  void electrostaticRepulsionReducesEnergyBetweenBundledTraces() {
    RoutingBoard board = createSyntheticBoard(1, new IntBox(0, 0, 10_000, 10_000));
    int net1 = board.rules.nets.newNet(java.util.Locale.US).netNumber;
    int net2 = board.rules.nets.newNet(java.util.Locale.US).netNumber;
    // Fixed aggressor trace on net 1
    Polyline p1 =
        new Polyline(new Point[] {new IntPoint(1_000, 2_000), new IntPoint(9_000, 2_000)});
    board.insertTraceWithoutCleaning(p1, 0, 25, new int[] {net1}, 0, FixedState.USER_FIXED);

    // Movable 4-corner victim trace on net 2 hugging net 1 closely at y = 2_080
    Polyline p2 =
        new Polyline(
            new Point[] {
              new IntPoint(1_000, 5_000),
              new IntPoint(1_000, 2_080),
              new IntPoint(9_000, 2_080),
              new IntPoint(9_000, 5_000)
            });
    board.insertTraceWithoutCleaning(p2, 0, 25, new int[] {net2}, 0, FixedState.UNFIXED);

    PhysicsSettings settings = new PhysicsSettings();
    settings.enabled = true;
    settings.gridResolutionMm = 0.5;
    settings.maxIterations = 4;
    settings.identityWeight = 1.0;
    settings.repulsionWeight = 1.0;

    PhysicsOptimizationPass.Result result = PhysicsOptimizationPass.run(board, settings, null);
    assertTrue(result.acceptedMoves() > 0, "Victim trace should move away from aggressor");
    assertTrue(
        result.finalEnergy() < result.initialEnergy(),
        "Repulsion energy must decrease after relaxation");
    assertTrue(
        new DesignRulesChecker(board, null).getAllClearanceViolations().isEmpty(),
        "Relaxation must introduce 0 DRC violations");
  }

  @Test
  void straightTwoCornerTraceSpreadsViaTrapezoidalJog() {
    RoutingBoard board = createSyntheticBoard(1, new IntBox(0, 0, 10_000, 10_000));
    int net1 = board.rules.nets.newNet(java.util.Locale.US).netNumber;
    int net2 = board.rules.nets.newNet(java.util.Locale.US).netNumber;

    // Fixed straight aggressor trace on net 1
    Polyline p1 =
        new Polyline(new Point[] {new IntPoint(1_000, 2_000), new IntPoint(9_000, 2_000)});
    board.insertTraceWithoutCleaning(p1, 0, 25, new int[] {net1}, 0, FixedState.USER_FIXED);

    // Straight 2-corner victim trace on net 2 hugging net 1 closely at y = 2_060
    Polyline p2 =
        new Polyline(new Point[] {new IntPoint(1_000, 2_060), new IntPoint(9_000, 2_060)});
    board.insertTraceWithoutCleaning(p2, 0, 25, new int[] {net2}, 0, FixedState.UNFIXED);

    PhysicsSettings settings = new PhysicsSettings();
    settings.enabled = true;
    settings.gridResolutionMm = 0.5;
    settings.maxIterations = 2;
    settings.identityWeight = 1.0;
    settings.repulsionWeight = 1.0;

    PhysicsOptimizationPass.Result result = PhysicsOptimizationPass.run(board, settings, null);
    assertTrue(result.acceptedMoves() > 0, "Straight 2-corner trace should insert a 45-deg jog");
    assertTrue(
        result.finalEnergy() < result.initialEnergy(),
        "Repulsion energy must decrease after jog insertion");
    assertTrue(
        new DesignRulesChecker(board, null).getAllClearanceViolations().isEmpty(),
        "Jog insertion must introduce 0 DRC violations");
  }

  @Test
  void straightTraceNearSmdPinIsNotDeflected() {
    RoutingBoard board = createSyntheticBoard(1, new IntBox(0, 0, 10_000, 10_000));
    int net1 = board.rules.nets.newNet(java.util.Locale.US).netNumber;
    int net2 = board.rules.nets.newNet(java.util.Locale.US).netNumber;

    // Place a foreign SMD pin on net 1 near (5_000, 2_000) with no parallel trace
    board.library.padstacks = new app.freerouting.core.library.Padstacks(board.layerStructure);
    board.library.packages = new app.freerouting.core.library.Packages(board.library.padstacks);
    var pad =
        board.library.padstacks.add(
            new app.freerouting.geometry.planar.ConvexShape[] {new IntBox(-200, -200, 200, 200)});
    var pkg =
        board.library.packages.add(
            new app.freerouting.core.library.Package.Pin[] {
              new app.freerouting.core.library.Package.Pin(
                  "1", pad.id, app.freerouting.geometry.planar.Vector.ZERO, 0)
            });
    var component = board.components.add(new IntPoint(5_000, 2_000), 0, true, pkg);
    board.insertPin(component.id, 0, new int[] {net1}, 0, FixedState.USER_FIXED);

    // Straight 2-corner trace on net 2 passing near the SMD pin at y = 2_300
    Polyline p2 =
        new Polyline(new Point[] {new IntPoint(1_000, 2_300), new IntPoint(9_000, 2_300)});
    board.insertTraceWithoutCleaning(p2, 0, 25, new int[] {net2}, 0, FixedState.UNFIXED);

    PhysicsSettings settings = new PhysicsSettings();
    settings.enabled = true;
    settings.gridResolutionMm = 0.5;
    settings.maxIterations = 4;
    settings.identityWeight = 1.0;
    settings.repulsionWeight = 1.0;

    PhysicsOptimizationPass.Result result = PhysicsOptimizationPass.run(board, settings, null);
    assertEquals(
        0, result.acceptedMoves(), "Trace near SMD pin without parallel trace must stay straight");
  }

  @Test
  void parallelTraceSpreadsWithoutSawtoothOrSub4wSegments() {
    RoutingBoard board = createSyntheticBoard(1, new IntBox(0, 0, 10_000, 10_000));
    int net1 = board.rules.nets.newNet(java.util.Locale.US).netNumber;
    int net2 = board.rules.nets.newNet(java.util.Locale.US).netNumber;

    // Fixed straight aggressor trace on net 1
    Polyline p1 =
        new Polyline(new Point[] {new IntPoint(1_000, 2_000), new IntPoint(9_000, 2_000)});
    board.insertTraceWithoutCleaning(p1, 0, 25, new int[] {net1}, 0, FixedState.USER_FIXED);

    // Parallel 2-corner victim trace on net 2
    Polyline p2 =
        new Polyline(new Point[] {new IntPoint(1_000, 2_060), new IntPoint(9_000, 2_060)});
    board.insertTraceWithoutCleaning(p2, 0, 25, new int[] {net2}, 0, FixedState.UNFIXED);

    PhysicsSettings settings = new PhysicsSettings();
    settings.enabled = true;
    settings.gridResolutionMm = 0.5;
    settings.maxIterations = 4;
    settings.identityWeight = 1.0;
    settings.repulsionWeight = 1.0;

    PhysicsOptimizationPass.Result result = PhysicsOptimizationPass.run(board, settings, null);
    assertTrue(result.acceptedMoves() > 0, "Parallel 2-corner trace should spread cleanly");
    assertTrue(
        result.finalEnergy() < result.initialEnergy(), "Energy must decrease after spreading");

    // Verify no segment on any trace is shorter than 4 * traceWidth (8 * halfWidth = 200)
    for (app.freerouting.board.model.items.Trace t : board.getTraces()) {
      if (t instanceof app.freerouting.board.trace.PolylineTrace pt && pt.getNetNumber(0) == net2) {
        Polyline poly = pt.polyline();
        assertTrue(poly.cornerCount() <= 6, "Must not re-split into sawtooth (>6 corners)");
        for (int i = 0; i < poly.cornerCount() - 1; i++) {
          var a = poly.corner(i).toFloat();
          var b = poly.corner(i + 1).toFloat();
          double len = Math.hypot(b.x - a.x, b.y - a.y);
          assertTrue(len >= 8.0 * pt.getHalfWidth(), "Segment length must be >= 4*w, got " + len);
        }
      }
    }
    assertTrue(
        new DesignRulesChecker(board, null).getAllClearanceViolations().isEmpty(),
        "Spread trace must have 0 DRC violations");
  }

  @Test
  void chamferBevelsAxisAlignedNinetyDegreeCornerToFortyFive() {
    RoutingBoard board = createSyntheticBoard(1, new IntBox(0, 0, 10_000, 10_000));
    int net1 = board.rules.nets.newNet(java.util.Locale.US).netNumber;
    Polyline p =
        new Polyline(
            new Point[] {
              new IntPoint(1_000, 1_000), new IntPoint(5_000, 1_000), new IntPoint(5_000, 5_000)
            });
    board.insertTraceWithoutCleaning(p, 0, 25, new int[] {net1}, 0, FixedState.UNFIXED);

    PhysicsSettings settings = new PhysicsSettings();
    settings.enabled = true;
    settings.gridResolutionMm = 0.5;
    settings.maxIterations = 2;
    settings.identityWeight = 1.0;
    settings.repulsionWeight = 0.0;
    settings.chamferEnabled = true;

    double lengthBefore = 8_000.0;
    PhysicsOptimizationPass.Result result = PhysicsOptimizationPass.run(board, settings, null);
    assertTrue(result.acceptedMoves() > 0, "90-degree corner should be chamfered");
    assertTrue(result.finalEnergy() < result.initialEnergy(), "Corner crowding energy must drop");

    var trace = (app.freerouting.board.trace.PolylineTrace) board.getTraces().iterator().next();
    assertEquals(4, trace.polyline().cornerCount(), "Chamfered L-trace should have 4 corners");
    assertTrue(trace.polyline().lengthApprox() < lengthBefore, "Chamfer must shorten trace");
    assertTrue(
        new DesignRulesChecker(board, null).getAllClearanceViolations().isEmpty(),
        "Chamfer must introduce 0 DRC violations");
  }

  @Test
  void chamferBevelsDiagonalNinetyDegreeCornerCleanly() {
    RoutingBoard board = createSyntheticBoard(1, new IntBox(0, 0, 10_000, 10_000));
    int net1 = board.rules.nets.newNet(java.util.Locale.US).netNumber;
    Polyline p =
        new Polyline(
            new Point[] {
              new IntPoint(1_000, 1_000), new IntPoint(3_000, 3_000), new IntPoint(5_000, 1_000)
            });
    board.insertTraceWithoutCleaning(p, 0, 25, new int[] {net1}, 0, FixedState.UNFIXED);

    PhysicsSettings settings = new PhysicsSettings();
    settings.enabled = true;
    settings.repulsionWeight = 0.0;
    settings.chamferEnabled = true;

    PhysicsOptimizationPass.Result result = PhysicsOptimizationPass.run(board, settings, null);
    assertTrue(result.acceptedMoves() > 0, "45/45 90-degree corner should be chamfered");
    var trace = (app.freerouting.board.trace.PolylineTrace) board.getTraces().iterator().next();
    assertEquals(4, trace.polyline().cornerCount());
    assertTrue(
        new DesignRulesChecker(board, null).getAllClearanceViolations().isEmpty(),
        "Diagonal chamfer must introduce 0 DRC violations");
  }

  @Test
  void chamferSkipsNinetyDegreeAngleRestrictionAndBlockedCorners() {
    RoutingBoard board = createSyntheticBoard(1, new IntBox(0, 0, 10_000, 10_000));
    board.rules.setTraceAngleRestriction(
        app.freerouting.board.model.structure.AngleRestriction.NINETY_DEGREE);
    int net1 = board.rules.nets.newNet(java.util.Locale.US).netNumber;
    Polyline p =
        new Polyline(
            new Point[] {
              new IntPoint(1_000, 1_000), new IntPoint(5_000, 1_000), new IntPoint(5_000, 5_000)
            });
    board.insertTraceWithoutCleaning(p, 0, 25, new int[] {net1}, 0, FixedState.UNFIXED);

    PhysicsSettings settings = new PhysicsSettings();
    settings.enabled = true;
    settings.repulsionWeight = 0.0;
    settings.chamferEnabled = true;

    PhysicsOptimizationPass.Result result = PhysicsOptimizationPass.run(board, settings, null);
    assertEquals(0, result.acceptedMoves(), "NINETY_DEGREE restriction must skip chamfering");
  }
}
