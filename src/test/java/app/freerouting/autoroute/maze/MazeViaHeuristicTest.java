package app.freerouting.autoroute.maze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.drill.DrillPage;
import app.freerouting.autoroute.drill.ExpansionDrill;
import app.freerouting.autoroute.expansion.CompleteFreeSpaceExpansionRoom;
import app.freerouting.autoroute.expansion.ExpansionDoor;
import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.settings.sources.DefaultSettings;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Protects the prepaid-via frontier priorities that caused the routing loop in issue #1017. */
class MazeViaHeuristicTest {
  private static final IntPoint POINT = new IntPoint(20000, 20000);
  private static final IntBox DRILL_SHAPE = new IntBox(0, 0, 40000, 40000);
  private static final double EPSILON = 1e-7;

  @ParameterizedTest
  @ValueSource(ints = {0, 1})
  void directDrillPriorityDoesNotExceedOneViaCompletion(int sourceLayer) throws IOException {
    Context context = context(sourceLayer, 50);
    var drill = drill(context);
    context.expansion.expandToDrill(drill, context.fromRoom(0), 0);

    MazeListElement queued = onlyQueued(context);
    double feasibleCompletionCost = context.control.minNormalViaCost;
    assertEquals(feasibleCompletionCost, queued.expansionValue, EPSILON);
    assertTrue(
        queued.sortingValue <= feasibleCompletionCost + EPSILON,
        "The destination is at the same XY on the opposite layer; one paid via completes it");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1})
  void drillPagePriorityDoesNotExceedOneViaCompletion(int sourceLayer) throws IOException {
    Context context = context(sourceLayer, 50);
    context.expansion.expandToDrillPage(
        new DrillPage(DRILL_SHAPE, context.search.autorouteEngine.board), context.fromRoom(0));

    MazeListElement queued = onlyQueued(context);
    double feasibleCompletionCost = context.control.minNormalViaCost;
    assertEquals(feasibleCompletionCost, queued.expansionValue, EPSILON);
    assertTrue(queued.sortingValue <= feasibleCompletionCost + EPSILON);
  }

  @Test
  void pageToDrillRetainsExactlyOneBaseCharge() throws IOException {
    Context context = context(0, 50);
    context.expansion.expandToDrillPage(
        new DrillPage(DRILL_SHAPE, context.search.autorouteEngine.board), context.fromRoom(0));
    MazeListElement page = onlyQueued(context);
    context.search.mazeExpansionList.clear();

    context.expansion.expandToDrill(drill(context), page, 17);
    MazeListElement queued = onlyQueued(context);
    assertEquals(context.control.minNormalViaCost + 17, queued.expansionValue, EPSILON);
    assertTrue(queued.sortingValue <= queued.expansionValue + EPSILON);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 17})
  void actualLayerCrossingPreservesPriorityBoundAndAdditionalCosts(int additionalCost)
      throws IOException {
    Context context = context(0, 50);
    context.control.addViaCosts[0].toLayer[1] = additionalCost;
    context.expansion.expandToDrill(drill(context), context.fromRoom(0), 0);
    MazeListElement entering = onlyQueued(context);
    context.search.mazeExpansionList.clear();

    context.expansion.expandToOtherLayers(entering);
    MazeListElement crossed = onlyQueued(context);
    assertEquals(1, crossed.sectionNoOfDoor);
    assertEquals(entering.expansionValue + additionalCost, crossed.expansionValue, EPSILON);
    assertTrue(
        entering.sortingValue <= crossed.expansionValue + EPSILON,
        "An already paid via must not defer a feasible completion behind a more expensive goal");
    assertEquals(crossed.expansionValue, crossed.sortingValue, EPSILON);
  }

  @Test
  void eachSuccessiveViaRetainsItsOwnBaseCharge() throws IOException {
    IntPoint secondPoint = new IntPoint(30000, 20000);
    Context context = context(0, 50, secondPoint, "maze-four-layer-via.dsn");
    context.expansion.expandToDrill(drill(context), context.fromRoom(0), 0);
    MazeListElement firstVia = onlyQueued(context);
    context.search.mazeExpansionList.clear();
    context.expansion.expandToOtherLayers(firstVia);
    MazeListElement firstCrossing =
        context.search.mazeExpansionList.stream()
            .filter(element -> element.sectionNoOfDoor == 1)
            .findFirst()
            .orElseThrow();
    context.search.mazeExpansionList.clear();

    Context innerLayer = new Context(context.control, context.search, context.expansion, 1);
    double travelCost = 10000 * context.control.traceCosts[1].horizontal();
    context.expansion.expandToDrill(
        drill(innerLayer, secondPoint),
        innerLayer.fromRoom(secondPoint, firstCrossing.expansionValue + travelCost),
        0);
    MazeListElement secondVia = onlyQueued(context);
    double feasibleCompletionCost = 2 * context.control.minNormalViaCost + travelCost;
    assertEquals(feasibleCompletionCost, secondVia.expansionValue, EPSILON);
    assertTrue(secondVia.sortingValue <= feasibleCompletionCost + EPSILON);
  }

  @Test
  void heuristicRemainsNonnegativeWhenDestinationAlsoExistsOnDepartureLayer() throws IOException {
    Context context = context(0, 50);
    context.search.destinationDistance.join(POINT.surroundingBox(), 0);
    context.expansion.expandToDrill(drill(context), context.fromRoom(0), 0);
    MazeListElement queued = onlyQueued(context);
    assertEquals(context.control.minNormalViaCost, queued.expansionValue, EPSILON);
    assertEquals(queued.expansionValue, queued.sortingValue, EPSILON);
  }

  @Test
  void zeroViaCostStillIncludesGeometricDistance() throws IOException {
    Context context = context(0, 0, new IntPoint(21000, 20000));
    context.expansion.expandToDrill(drill(context), context.fromRoom(0), 0);
    MazeListElement queued = onlyQueued(context);
    assertEquals(0, queued.expansionValue, EPSILON);
    double feasibleCompletionCost =
        1000
            * Math.min(
                context.control.traceCosts[0].horizontal(),
                context.control.traceCosts[1].horizontal());
    assertEquals(feasibleCompletionCost, queued.sortingValue, EPSILON);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1})
  void positiveViaCostRetainsDistanceAtDirectDrill(int sourceLayer) throws IOException {
    Context context = context(sourceLayer, 50, new IntPoint(23000, 20000));
    context.expansion.expandToDrill(drill(context), context.fromRoom(0), 0);
    MazeListElement queued = onlyQueued(context);

    // Horizontal travel can use the cheaper layer; the already paid via is a separate cost.
    double travel =
        3000
            * Math.min(
                context.control.traceCosts[0].horizontal(),
                context.control.traceCosts[1].horizontal());
    assertTrue(travel > 0);
    assertEquals(context.control.minNormalViaCost, queued.expansionValue, EPSILON);
    assertEquals(context.control.minNormalViaCost + travel, queued.sortingValue, EPSILON);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1})
  void positiveViaCostRetainsDistanceAndApproachAtDrillPage(int sourceLayer) throws IOException {
    Context context = context(sourceLayer, 50, new IntPoint(60000, 20000));
    context.expansion.expandToDrillPage(
        new DrillPage(new IntBox(40000, 0, 80000, 40000), context.search.autorouteEngine.board),
        context.fromRoom(0));
    MazeListElement queued = onlyQueued(context);

    double approach = 20000 * context.control.traceCosts[sourceLayer].horizontal();
    double remaining =
        20000
            * Math.min(
                context.control.traceCosts[0].horizontal(),
                context.control.traceCosts[1].horizontal());
    assertTrue(approach > 0 && remaining > 0);
    assertEquals(context.control.minNormalViaCost, queued.expansionValue, EPSILON);
    assertEquals(
        context.control.minNormalViaCost + approach + remaining, queued.sortingValue, EPSILON);
  }

  @Test
  void creditedPriorityDoesNotBypassViaMasks() throws IOException {
    Context context = context(0, 50);
    context.expansion.expandToDrill(drill(context), context.fromRoom(0), 0);
    MazeListElement entering = onlyQueued(context);
    context.search.mazeExpansionList.clear();
    context.control.viaInfos = new AutorouteControl.ViaMask[0];
    context.expansion.expandToOtherLayers(entering);
    assertTrue(context.search.mazeExpansionList.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3})
  void multilayerViaKeepsTheBoundThroughFeasibleLayerTransitions(int sourceLayer)
      throws IOException {
    Context context = context(sourceLayer, 50, POINT, "maze-four-layer-via.dsn");
    context.expansion.expandToDrill(drill(context), context.fromRoom(0), 0);
    MazeListElement entering = onlyQueued(context);
    context.search.mazeExpansionList.clear();
    context.expansion.expandToOtherLayers(entering);
    int destinationLayer = 3 - sourceLayer;
    MazeListElement completed =
        context.search.mazeExpansionList.stream()
            .filter(element -> element.sectionNoOfDoor == destinationLayer)
            .findFirst()
            .orElseThrow();
    assertEquals(3, context.search.mazeExpansionList.size());
    assertEquals(context.control.minNormalViaCost, completed.expansionValue, EPSILON);
    assertTrue(entering.sortingValue <= completed.expansionValue + EPSILON);
    assertEquals(completed.expansionValue, completed.sortingValue, EPSILON);
  }

  @Test
  void cheapViaEstimateDoesNotChangeLaterPrepaidPriorities() throws IOException {
    Context context = context(0, 50);
    double paidBase = context.control.minNormalViaCost;
    context.search.destinationDistance.calculateCheapDistance(POINT.surroundingBox(), 0);
    context.expansion.expandToDrill(drill(context), context.fromRoom(0), 0);
    MazeListElement queued = onlyQueued(context);
    assertEquals(paidBase, queued.expansionValue, EPSILON);
    assertEquals(paidBase, queued.sortingValue, EPSILON);
  }

  private static Context context(int sourceLayer, int viaCost) throws IOException {
    return context(sourceLayer, viaCost, POINT);
  }

  private static Context context(int sourceLayer, int viaCost, IntPoint destination)
      throws IOException {
    return context(sourceLayer, viaCost, destination, "scoring-zero-length.dsn");
  }

  private static Context context(int sourceLayer, int viaCost, IntPoint destination, String fixture)
      throws IOException {
    var board = DsnTestFixtures.loadBoard(fixture);
    board.awaitPostLoad();
    var settings = new DefaultSettings().getSettings();
    settings.applyBoardSpecificOptimizations(board);
    var control = new AutorouteControl(board, 1, settings, viaCost, settings.getTraceCosts());
    assertTrue(control.viaInfos.length > 0, "The fixture must provide a legal through via");
    var engine = new AutorouteEngine(board, control.traceClearanceClassIndex, false);
    var search = new MazeSearchEngine(engine, control);
    search.destinationDistance.join(
        destination.surroundingBox(), board.getLayerCount() - 1 - sourceLayer);
    return new Context(control, search, new MazeExpansionEngine(search), sourceLayer);
  }

  private static ExpansionDrill drill(Context context) {
    return drill(context, POINT);
  }

  private static ExpansionDrill drill(Context context, IntPoint point) {
    var shape = new IntBox(point.x - 20000, point.y - 20000, point.x + 20000, point.y + 20000);
    var drill = new ExpansionDrill(shape, point, 0, context.control.layerCount - 1);
    for (int layer = 0; layer < context.control.layerCount; layer++) {
      drill.roomArr[layer] = new CompleteFreeSpaceExpansionRoom(shape, layer, layer + 10);
    }
    return drill;
  }

  private static MazeListElement onlyQueued(Context context) {
    assertFalse(context.search.mazeExpansionList.isEmpty());
    assertEquals(1, context.search.mazeExpansionList.size());
    return context.search.mazeExpansionList.first();
  }

  private record Context(
      AutorouteControl control,
      MazeSearchEngine search,
      MazeExpansionEngine expansion,
      int sourceLayer) {
    MazeListElement fromRoom(double accumulatedCost) {
      return fromRoom(POINT, accumulatedCost);
    }

    MazeListElement fromRoom(IntPoint point, double accumulatedCost) {
      var room =
          new CompleteFreeSpaceExpansionRoom(
              new IntBox(point.x, point.y - 20000, point.x + 40000, point.y + 20000),
              sourceLayer,
              1);
      var neighbor =
          new CompleteFreeSpaceExpansionRoom(
              new IntBox(point.x - 20000, point.y - 20000, point.x, point.y + 20000),
              sourceLayer,
              2);
      return new MazeListElement(
          new ExpansionDoor(room, neighbor, 1),
          0,
          null,
          0,
          accumulatedCost,
          accumulatedCost,
          room,
          new FloatLine(point.toFloat(), point.toFloat()),
          false,
          MazeSearchElement.Adjustment.NONE,
          false);
    }
  }
}
