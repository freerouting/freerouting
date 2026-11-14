package app.freerouting.autoroute.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.autoroute.maze.AutorouteControl;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.state.Communication;
import app.freerouting.core.library.Package;
import app.freerouting.core.library.Packages;
import app.freerouting.core.library.Padstack;
import app.freerouting.core.library.Padstacks;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.ViaInfo;
import app.freerouting.rules.ViaRule;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * A narrowing attempt that does not reach its target must not stay on the board.
 *
 * <p>When a segment stops short of a pin, trace insertion retries it at 75 %, 60 % and 50 % of the
 * net-class width. Each retry that also stops short used to leave the piece it had inserted, and
 * the next retry was laid over it: three superposed pieces of one net, each ending on the others,
 * that tail removal never saw as tails. KiCad reports them as dangling tracks.
 */
class FailedNeckdownAttemptTest {
  private static final IntPoint PIN_CENTRE = new IntPoint(0, 0);
  private static final IntPoint SOURCE = new IntPoint(0, 40000);
  private static final int HALF_WIDTH = 500;
  private static final int BLOCKER_Y = 3000;

  @Test
  void failedNarrowingAttemptsLeaveNoCopper() {
    List<String> plain = insertTowardsBlockedPin(false);
    List<String> necked = insertTowardsBlockedPin(true);

    assertTrue(
        necked.stream().noneMatch(trace -> !trace.startsWith("w" + HALF_WIDTH + " ")),
        () -> "narrowed copper left by failed attempts: " + necked);
    assertEquals(plain, necked);
  }

  /**
   * Inserts a straight connection from a via down to a pin that no width can reach, and returns the
   * net 1 traces left on the board, as half width and corners. Without neckdown, only the piece of
   * the net-class width that stopped short is left.
   */
  private static List<String> insertTowardsBlockedPin(boolean neckdown) {
    Freerouting.globalSettings = new GlobalSettings();
    LayerStructure layers =
        new LayerStructure(new Layer[] {new Layer("Top", true), new Layer("Bottom", true)});
    BoardRules rules = new BoardRules(layers, ClearanceMatrix.getDefaultInstance(layers, 10));
    rules.createDefaultNetClass();
    rules.getDefaultNetClass().setTraceHalfWidth(HALF_WIDTH);
    RoutingBoard board =
        new RoutingBoard(
            new IntBox(-200000, -200000, 200000, 200000),
            layers,
            new PolylineShape[0],
            0,
            rules,
            new Communication());
    rules.nets.add("Net1", 1, false);
    rules.nets.add("Net2", 2, false);
    rules.nets.add("Net3", 3, false);
    board.library.padstacks = new Padstacks(layers);
    board.library.packages = new Packages(board.library.padstacks);
    ConvexShape viaShape = TileShape.getInstance(-1000, -1000, 1000, 1000);
    Padstack viaPadstack =
        board.library.padstacks.add("Via", new ConvexShape[] {viaShape, viaShape}, true, false);
    board.library.addViaPadstack(viaPadstack);
    ViaInfo viaInfo = new ViaInfo("Via", viaPadstack, 1, true, rules);
    rules.viaInfos.add(viaInfo);
    ViaRule viaRule = new ViaRule("Default");
    viaRule.appendVia(viaInfo);
    rules.viaRules.add(viaRule);
    rules.getDefaultNetClass().setViaRule(viaRule);

    // A pad wider than the trace, so the pin neckdown keeps the full width and only the
    // percentage retries narrow it.
    Padstack pad =
        board.library.padstacks.add(new ConvexShape[] {new IntBox(-1000, -1000, 1000, 1000), null});
    Package pkg =
        board.library.packages.add(
            new Package.Pin[] {new Package.Pin("1", pad.id, Vector.ZERO, 0)});
    var component = board.components.add(PIN_CENTRE, 0, true, pkg);
    Pin pin = board.insertPin(component.id, 0, new int[] {1}, 1, FixedState.USER_FIXED);
    Via source =
        board.insertVia(viaPadstack, SOURCE, new int[] {1}, 1, FixedState.USER_FIXED, true);
    // A trace of net 2 across the way, which the insertion may shove, but only towards a fixed
    // trace of net 3 that also crosses the way: every width stops short of the pin, part way down.
    // The fixed piece of net 3 above the shovable trace keeps the stop off the straight line.
    board.insertTraceWithoutCleaning(
        new Polyline(new IntPoint(-40000, BLOCKER_Y), new IntPoint(40000, BLOCKER_Y)),
        0,
        HALF_WIDTH,
        new int[] {2},
        1,
        FixedState.UNFIXED);
    board.insertTraceWithoutCleaning(
        new Polyline(new IntPoint(-40000, BLOCKER_Y - 1100), new IntPoint(40000, BLOCKER_Y - 1100)),
        0,
        HALF_WIDTH,
        new int[] {3},
        1,
        FixedState.USER_FIXED);
    board.insertTraceWithoutCleaning(
        new Polyline(new IntPoint(-40000, BLOCKER_Y + 1100), new IntPoint(-1600, BLOCKER_Y + 1100)),
        0,
        HALF_WIDTH,
        new int[] {3},
        1,
        FixedState.USER_FIXED);

    RouterSettings settings = new DefaultSettings().getSettings();
    settings.applyBoardSpecificOptimizations(board);
    settings.setAutomaticNeckdown(neckdown);
    AutorouteControl control = new AutorouteControl(board, 1, settings);
    board.startMarkingChangedArea();

    assertNull(
        FoundConnectionInserter.insertSegments(
            board,
            control,
            pin,
            source,
            0,
            0,
            new PlannedConnection.Segment[] {
              new PlannedConnection.Segment(new IntPoint[] {SOURCE, PIN_CENTRE}, 0)
            }),
        "the pin must stay out of reach, neckdown=" + neckdown);
    return board.getTraces().stream()
        .filter(trace -> trace.containsNet(1))
        .map(FailedNeckdownAttemptTest::describe)
        .sorted()
        .collect(Collectors.toList());
  }

  private static String describe(Trace trace) {
    return "w"
        + trace.getHalfWidth()
        + " "
        + trace.firstCorner().toFloat().round()
        + " "
        + trace.lastCorner().toFloat().round();
  }
}
