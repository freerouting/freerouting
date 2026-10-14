package app.freerouting.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.board.state.Communication;
import app.freerouting.core.RoutingJob;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.CornerStyle;
import app.freerouting.rules.MeanderPatternType;
import app.freerouting.rules.NetMeanderConstraint;
import app.freerouting.settings.sources.DefaultSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests that HeadlessBoardManager initializes and bridges meander pattern settings to BoardRules.
 */
class MeanderSettingsOverrideTest {

  private RoutingBoard testBoard;
  private RoutingJob routingJob;
  private HeadlessBoardManager manager;

  @BeforeEach
  void setUp() {
    Layer layer1 = new Layer("Top", true);
    Layer layer2 = new Layer("Bottom", true);
    LayerStructure layerStructure = new LayerStructure(new Layer[] {layer1, layer2});

    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(layerStructure, 10);
    BoardRules boardRules = new BoardRules(layerStructure, clearanceMatrix);
    boardRules.createDefaultNetClass();

    Communication communication =
        new Communication(
            Unit.MM,
            10000,
            new Communication.SpecctraParserInfo("\"", null, null, null, null, false),
            new app.freerouting.io.CoordinateTransform(1, 0, 0),
            new app.freerouting.board.actions.ItemIdGenerator(),
            new app.freerouting.board.state.BoardObserverAdaptor());

    testBoard =
        new RoutingBoard(
            new IntBox(0, 0, 1000000, 1000000),
            layerStructure,
            new PolylineShape[] {TileShape.getInstance(0, 0, 1000000, 1000000)},
            0,
            boardRules,
            communication);

    routingJob = new RoutingJob();
    routingJob.routerSettings = new DefaultSettings().getSettings();

    manager = new HeadlessBoardManager(routingJob);
    manager.board = testBoard;
  }

  @Test
  void testDefaultMeanderConstraintsInitializedAndScaled() {
    manager.applyRouterSettingsForLoadedBoard();

    // 1 mm = 10000 board units
    double expectedMinAmp = 0.2 * 10000;
    double expectedMaxAmp = 1.0 * 10000;
    double expectedSpacingSingleTrack = 0.6 * 10000;

    NetMeanderConstraint singleTrack =
        testBoard.rules.getDefaultMeanderConstraint(MeanderPatternType.SINGLE_TRACK);
    assertEquals(expectedMinAmp, singleTrack.minAmplitude(), 1e-4);
    assertEquals(expectedMaxAmp, singleTrack.maxAmplitude(), 1e-4);
    assertEquals(expectedSpacingSingleTrack, singleTrack.spacing(), 1e-4);
    assertEquals(CornerStyle.FILLET, singleTrack.cornerStyle());
    assertEquals(80.0, singleTrack.radiusPercentage(), 1e-4);
  }

  @Test
  void testExplicitRouterSettingsOverrideBoardDefault() {
    routingJob.routerSettings.meanderMinAmplitudeMm = 0.5;
    routingJob.routerSettings.meanderMaxAmplitudeMm = 2.0;
    routingJob.routerSettings.meanderSpacingMm = 1.2;
    routingJob.routerSettings.meanderCornerStyle = "CHAMFER";
    routingJob.routerSettings.meanderRadiusPercentage = 50.0;
    routingJob.routerSettings.meanderSingleSided = true;

    manager.applyRouterSettingsForLoadedBoard();

    NetMeanderConstraint singleTrack =
        testBoard.rules.getDefaultMeanderConstraint(MeanderPatternType.SINGLE_TRACK);
    assertEquals(0.5 * 10000, singleTrack.minAmplitude(), 1e-4);
    assertEquals(2.0 * 10000, singleTrack.maxAmplitude(), 1e-4);
    assertEquals(1.2 * 10000, singleTrack.spacing(), 1e-4);
    assertEquals(CornerStyle.CHAMFER, singleTrack.cornerStyle());
    assertEquals(50.0, singleTrack.radiusPercentage(), 1e-4);
    assertTrue(singleTrack.singleSided());
  }

  @Test
  void testExplicitDsnConstraintPreservedWhenOnlyDefaultsActive() {
    NetMeanderConstraint explicitDsnConstraint =
        new NetMeanderConstraint(3500.0, 15000.0, 7500.0, CornerStyle.CHAMFER, 60.0, true);
    testBoard.rules.setDefaultMeanderConstraint(
        MeanderPatternType.SINGLE_TRACK, explicitDsnConstraint);

    // Only global defaults active
    routingJob.routerSettings = new DefaultSettings().getSettings();

    manager.applyRouterSettingsForLoadedBoard();

    NetMeanderConstraint resolved =
        testBoard.rules.getDefaultMeanderConstraint(MeanderPatternType.SINGLE_TRACK);
    assertEquals(explicitDsnConstraint, resolved);
  }
}
