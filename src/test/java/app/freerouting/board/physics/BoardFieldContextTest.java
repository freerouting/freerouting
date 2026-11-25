package app.freerouting.board.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.state.Communication;
import app.freerouting.geometry.planar.IntBox;
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

    PhysicsOptimizationPass.Result result = PhysicsOptimizationPass.run(board, settings, null);
    assertEquals(0.0, result.initialEnergy());
    assertEquals(0.0, result.finalEnergy());
    assertEquals(1, result.iterations());
    assertEquals(0, result.acceptedMoves());
  }
}
