package app.freerouting.autoroute.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.autoroute.maze.AutorouteControl;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.state.Communication;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.core.library.Padstack;
import app.freerouting.core.library.Padstacks;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.ViaInfo;
import app.freerouting.rules.ViaRule;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.sources.DefaultSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class FoundConnectionInsertionTest {
  private static final IntPoint SOURCE = new IntPoint(15000, 100000);
  private RoutingBoard board;
  private AutorouteControl control;
  private Padstack padstack;
  private Via source;

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
    createBoard(false);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void keepsBridgeUntilIntegerEndpointAttachment(boolean reverse) {
    Polyline line = new Polyline(new IntPoint(0, 5000), new IntPoint(30000, 5000));
    PolylineTrace target = insert(line, FixedState.USER_FIXED);

    assertNotNull(insert(target, new IntPoint(15000, 5001), reverse));
    assertTrue(source.getConnectedSet(1).contains(target));
    assertSame(line, target.polyline());
    assertEquals(FixedState.USER_FIXED, target.getFixedState());
    assertFalse(board.normalizeTraces(1));
    assertClean();
  }

  @Test
  void keepsBridgeUntilRationalEndpointAttachment() {
    Polyline line = new Polyline(new IntPoint(0, 0), new IntPoint(30000, 10000));
    PolylineTrace target = insert(line, FixedState.USER_FIXED);

    assertNotNull(insert(target, new IntPoint(15000, 5001), false));
    assertTrue(source.getConnectedSet(1).contains(target));
    assertSame(line, target.polyline());
    assertFalse(board.normalizeTraces(1));
    assertClean();
  }

  @ParameterizedTest
  @CsvSource({"false,false", "false,true", "true,false", "true,true"})
  void rejectsFragmentedTerminalComponentIncludingFixedTerminal(
      boolean fixedTerminal, boolean reverse) {
    createBoard(false, true);
    PolylineTrace target =
        insert(new Polyline(new IntPoint(0, 5000), new IntPoint(30000, 5000)), FixedState.UNFIXED);
    Via left =
        board.insertVia(
            padstack, new IntPoint(0, 5000), new int[] {1}, 1, FixedState.USER_FIXED, true);
    Via right =
        board.insertVia(
            padstack, new IntPoint(30000, 5000), new int[] {1}, 1, FixedState.USER_FIXED, true);
    PolylineTrace fixedTarget =
        insert(new Polyline(new IntPoint(0, 5000), new IntPoint(0, 10000)), FixedState.USER_FIXED);
    assertTrue(left.getConnectedSet(1).contains(right));
    ((FragmentingBoard) board).afterAttachment =
        () -> {
          for (Trace trace : board.getTraces()) {
            if (!trace.isUserFixed()) {
              board.removeItem(trace);
            }
          }
          board.insertTraceWithoutCleaning(
              new Polyline(SOURCE, left.getCenter()),
              0,
              500,
              new int[] {1},
              1,
              FixedState.USER_FIXED);
        };
    Trace terminal = fixedTerminal ? fixedTarget : target;
    IntPoint landing = fixedTerminal ? new IntPoint(0, 7501) : new IntPoint(15000, 5001);
    assertNull(
        FoundConnectionInserter.insertSegments(
            board,
            control,
            reverse ? terminal : source,
            reverse ? source : terminal,
            0,
            0,
            new PlannedConnection.Segment[] {
              new PlannedConnection.Segment(
                  reverse ? new IntPoint[] {SOURCE, landing} : new IntPoint[] {landing, SOURCE}, 0)
            }));
    assertTrue(source.getConnectedSet(1).contains(left));
    assertFalse(source.getConnectedSet(1).contains(right));
    assertClean();
  }

  @Test
  void rejectsFailedTargetAttachment() {
    PolylineTrace target =
        insert(new Polyline(new IntPoint(0, 0), new IntPoint(30000, 0)), FixedState.USER_FIXED);
    board.insertTraceWithoutCleaning(
        new Polyline(new IntPoint(14000, 2500), new IntPoint(16000, 2500)),
        0,
        500,
        new int[] {2},
        1,
        FixedState.USER_FIXED);

    assertNull(insert(target, new IntPoint(15000, 5000), false));
    assertFalse(source.getConnectedSet(1).contains(target));
    assertClean();
  }

  @Test
  void rejectsFailedStartAttachment() {
    PolylineTrace target =
        insert(new Polyline(new IntPoint(0, 0), new IntPoint(30000, 0)), FixedState.USER_FIXED);
    board.insertTraceWithoutCleaning(
        new Polyline(new IntPoint(14000, 2500), new IntPoint(16000, 2500)),
        0,
        500,
        new int[] {2},
        1,
        FixedState.USER_FIXED);

    assertNull(insert(target, new IntPoint(15000, 5000), true));
    assertClean();
  }

  @Test
  void rejectsDisconnectedInsertionEvenWhenAttachmentReportsSuccess() {
    createBoard(true);
    PolylineTrace target =
        insert(
            new Polyline(new IntPoint(0, 5000), new IntPoint(30000, 5000)), FixedState.USER_FIXED);

    assertNull(insert(target, new IntPoint(15000, 5001), false));
    assertFalse(source.getConnectedSet(1).contains(target));
    assertClean();
  }

  @Test
  void acceptsReplacementOfMutableTargetTrace() {
    PolylineTrace target =
        insert(new Polyline(new IntPoint(0, 5000), new IntPoint(30000, 5000)), FixedState.UNFIXED);
    Via left =
        board.insertVia(
            padstack, new IntPoint(0, 5000), new int[] {1}, 1, FixedState.USER_FIXED, true);
    final Via right =
        board.insertVia(
            padstack, new IntPoint(30000, 5000), new int[] {1}, 1, FixedState.USER_FIXED, true);

    assertNotNull(insert(target, new IntPoint(15000, 5001), false));
    assertFalse(target.isOnTheBoard());
    assertTrue(source.getConnectedSet(1).contains(left));
    assertTrue(source.getConnectedSet(1).contains(right));
    assertClean();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void resolvesExactAnchorAfterEntireMutableTerminalIsReplaced(boolean rational) {
    PolylineTrace target =
        insert(
            new Polyline(
                new IntPoint(0, rational ? 0 : 5000), new IntPoint(30000, rational ? 10000 : 5000)),
            FixedState.UNFIXED);
    IntPoint landing = new IntPoint(15000, 5001);
    Point anchor = target.polyline().projectionLine(landing).endPoint();

    assertNotNull(insert(target, landing, false));
    assertFalse(target.isOnTheBoard());
    assertTrue(
        source.getConnectedSet(1).stream()
            .anyMatch(item -> item instanceof Trace trace && trace.contains(anchor)));
    assertClean();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void attachesWithTerminalsLayerTraceWidth(boolean reverse) {
    board.rules.getDefaultNetClass().setTraceHalfWidth(1, 900);
    control = new AutorouteControl(board, 1, control.settings);
    PolylineTrace target =
        board.insertTraceWithoutCleaning(
            new Polyline(new IntPoint(0, 5000), new IntPoint(30000, 5000)),
            1,
            1000,
            new int[] {1},
            1,
            FixedState.USER_FIXED);

    IntPoint landing = new IntPoint(15000, 5001);
    IntPoint transition = new IntPoint(15000, 50000);
    PlannedConnection.Segment[] segments =
        reverse
            ? new PlannedConnection.Segment[] {
              new PlannedConnection.Segment(new IntPoint[] {SOURCE, transition}, 0),
              new PlannedConnection.Segment(new IntPoint[] {transition, landing}, 1)
            }
            : new PlannedConnection.Segment[] {
              new PlannedConnection.Segment(new IntPoint[] {landing, transition}, 1),
              new PlannedConnection.Segment(new IntPoint[] {transition, SOURCE}, 0)
            };
    assertNotNull(
        FoundConnectionInserter.insertSegments(
            board,
            control,
            reverse ? target : source,
            reverse ? source : target,
            reverse ? 1 : 0,
            reverse ? 0 : 1,
            segments));
    assertTrue(source.getConnectedSet(1).contains(target));
    var newTraces = board.getTraces().stream().filter(trace -> !trace.isUserFixed()).toList();
    assertFalse(newTraces.isEmpty());
    assertTrue(
        newTraces.stream()
            .allMatch(trace -> trace.getHalfWidth() == (trace.getLayer() == 1 ? 900 : 500)));
    assertClean();
  }

  @Test
  void acceptsSinglePointFanoutLayerTransition() {
    source.setFixedState(FixedState.UNFIXED);
    board.removeItem(source);
    assertFalse(source.isOnTheBoard());
    assertTrue(board.getVias().isEmpty());
    PolylineTrace terminal =
        insert(
            new Polyline(new IntPoint(5000, 100000), new IntPoint(25000, 100000)),
            FixedState.USER_FIXED);
    control.isFanout = true;
    assertNotNull(
        FoundConnectionInserter.insertSegments(
            board,
            control,
            terminal,
            null,
            0,
            1,
            new PlannedConnection.Segment[] {
              new PlannedConnection.Segment(new IntPoint[] {SOURCE}, 0)
            }));
    assertTrue(
        terminal.getConnectedSet(1).stream()
            .anyMatch(item -> item instanceof Via via && via.getCenter().equals(SOURCE)));
    assertClean();
  }

  @Test
  void acceptsFanoutEndingAtNewViaWithoutDestinationItem() {
    IntPoint fanout = new IntPoint(25000, 100000);
    control.isFanout = true;

    assertNotNull(
        FoundConnectionInserter.insertSegments(
            board,
            control,
            source,
            null,
            0,
            1,
            new PlannedConnection.Segment[] {
              new PlannedConnection.Segment(new IntPoint[] {fanout, SOURCE}, 0)
            }));
    assertTrue(
        source.getConnectedSet(1).stream()
            .anyMatch(item -> item instanceof Via via && via.getCenter().equals(fanout)));
    assertClean();
  }

  @Test
  void rejectsEmptyAndDisjointPlansBeforeMutatingBoard() {
    PolylineTrace target =
        insert(
            new Polyline(new IntPoint(0, 5000), new IntPoint(30000, 5000)), FixedState.USER_FIXED);
    int initialItems = board.getItems().size();

    assertNull(
        FoundConnectionInserter.insertSegments(
            board, control, source, target, 0, 0, new PlannedConnection.Segment[0]));
    assertNull(
        FoundConnectionInserter.insertSegments(
            board,
            control,
            source,
            target,
            0,
            0,
            new PlannedConnection.Segment[] {
              new PlannedConnection.Segment(
                  new IntPoint[] {new IntPoint(15000, 5001), new IntPoint(15000, 40000)}, 0),
              new PlannedConnection.Segment(new IntPoint[] {new IntPoint(15000, 50000), SOURCE}, 0)
            }));
    assertEquals(initialItems, board.getItems().size());
  }

  private FoundConnectionInserter insert(PolylineTrace target, IntPoint landing, boolean reverse) {
    return FoundConnectionInserter.insertSegments(
        board,
        control,
        reverse ? target : source,
        reverse ? source : target,
        0,
        0,
        new PlannedConnection.Segment[] {
          new PlannedConnection.Segment(
              reverse ? new IntPoint[] {SOURCE, landing} : new IntPoint[] {landing, SOURCE}, 0)
        });
  }

  private PolylineTrace insert(Polyline line, FixedState fixedState) {
    return board.insertTraceWithoutCleaning(line, 0, 750, new int[] {1}, 1, fixedState);
  }

  private void assertClean() {
    assertTrue(new DesignRulesChecker(board, null).getAllClearanceViolations().isEmpty());
  }

  private void createBoard(boolean ignoreAttachment) {
    createBoard(ignoreAttachment, false);
  }

  private void createBoard(boolean ignoreAttachment, boolean fragmentAttachment) {
    LayerStructure layers =
        new LayerStructure(new Layer[] {new Layer("Top", true), new Layer("Bottom", true)});
    BoardRules rules = new BoardRules(layers, ClearanceMatrix.getDefaultInstance(layers, 10));
    rules.createDefaultNetClass();
    rules.getDefaultNetClass().setTraceHalfWidth(500);
    board =
        ignoreAttachment
            ? new AttachmentIgnoringBoard(layers, rules)
            : fragmentAttachment
                ? new FragmentingBoard(layers, rules)
                : new RoutingBoard(
                    new IntBox(-200000, -200000, 200000, 200000),
                    layers,
                    new PolylineShape[0],
                    0,
                    rules,
                    new Communication());
    rules.nets.add("Net1", 1, false);
    rules.nets.add("Net2", 2, false);
    board.library.padstacks = new Padstacks(layers);
    ConvexShape shape = TileShape.getInstance(-1000, -1000, 1000, 1000);
    padstack = board.library.padstacks.add("Via", new ConvexShape[] {shape, shape}, true, false);
    board.library.addViaPadstack(padstack);
    ViaInfo via = new ViaInfo("Via", padstack, 1, true, rules);
    rules.viaInfos.add(via);
    ViaRule viaRule = new ViaRule("Default");
    viaRule.appendVia(via);
    rules.viaRules.add(viaRule);
    rules.getDefaultNetClass().setViaRule(viaRule);
    source = board.insertVia(padstack, SOURCE, new int[] {1}, 1, FixedState.USER_FIXED, true);
    RouterSettings settings = new DefaultSettings().getSettings();
    settings.applyBoardSpecificOptimizations(board);
    settings.setAutomaticNeckdown(false);
    control = new AutorouteControl(board, 1, settings);
    board.startMarkingChangedArea();
  }

  /** Models an insertion helper breaking a former component, to exercise the final public guard. */
  private static final class FragmentingBoard extends RoutingBoard {
    private Runnable afterAttachment;

    private FragmentingBoard(LayerStructure layers, BoardRules rules) {
      super(
          new IntBox(-200000, -200000, 200000, 200000),
          layers,
          new PolylineShape[0],
          0,
          rules,
          new Communication());
    }

    @Override
    public boolean connectToTrace(
        IntPoint fromPoint, Trace toTrace, int penHalfWidth, int clearanceClassIndex) {
      boolean attached =
          super.connectToTrace(fromPoint, toTrace, penHalfWidth, clearanceClassIndex);
      if (attached && afterAttachment != null) {
        Runnable action = afterAttachment;
        afterAttachment = null;
        action.run();
      }
      return attached;
    }
  }

  private static final class AttachmentIgnoringBoard extends RoutingBoard {
    private AttachmentIgnoringBoard(LayerStructure layers, BoardRules rules) {
      super(
          new IntBox(-200000, -200000, 200000, 200000),
          layers,
          new PolylineShape[0],
          0,
          rules,
          new Communication());
    }

    @Override
    public boolean connectToTrace(
        IntPoint fromPoint, Trace toTrace, int penHalfWidth, int clearanceClassIndex) {
      return true;
    }
  }
}
