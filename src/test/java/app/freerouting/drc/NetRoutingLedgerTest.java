package app.freerouting.drc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import app.freerouting.Freerouting;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.state.Communication;
import app.freerouting.core.library.Package;
import app.freerouting.core.library.Packages;
import app.freerouting.core.library.Padstack;
import app.freerouting.core.library.Padstacks;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.Net;
import app.freerouting.rules.NetClass;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class NetRoutingLedgerTest {

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new app.freerouting.settings.GlobalSettings();
  }

  @Test
  void incompleteCountMatchesFullScanAcrossInsertAndRemove() {
    BoardReadResult result =
        DsnReader.readBoard(
            DsnTestFixtures.openResource("scoring-perfect-two-pin.dsn"), null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();

    assertMatchesFullScan(board);

    List<Pin> pins = new ArrayList<>(board.getPins());
    Point first = pins.get(0).getCenter();
    Point second = pins.get(1).getCenter();
    int netNumber = pins.get(0).getNetNumber(0);
    NetClass netClass = board.rules.nets.get(netNumber).getNetClass();
    board.insertTraceWithoutCleaning(
        new Polyline(first, second),
        0,
        netClass.getTraceHalfWidth(0),
        new int[] {netNumber},
        netClass.getTraceClearanceClass(),
        FixedState.UNFIXED);

    assertMatchesFullScan(board);
    assertEquals(0, board.routingLedger().incompleteCount());

    Trace trace = board.getTraces().iterator().next();
    board.removeItem(trace);

    assertMatchesFullScan(board);
    assertEquals(1, board.routingLedger().incompleteCount());
  }

  @ParameterizedTest
  @MethodSource("boardProviders")
  void undoOfARemovalRestoresTheIncompleteCount(Supplier<BasicBoard> boardSupplier) {
    BasicBoard board = boardSupplier.get();
    insertTrace(board);
    assertEquals(0, board.routingLedger().incompleteCount());

    board.generateSnapshot();
    board.removeItem(board.getTraces().iterator().next());
    assertEquals(1, board.routingLedger().incompleteCount());
    board.undo(null);

    assertMatchesFullScan(board);
    assertEquals(0, board.routingLedger().incompleteCount());
  }

  @ParameterizedTest
  @MethodSource("boardProviders")
  void undoAndRedoOfAnInsertionKeepTheIncompleteCount(Supplier<BasicBoard> boardSupplier) {
    BasicBoard board = boardSupplier.get();
    assertEquals(1, board.routingLedger().incompleteCount());

    board.generateSnapshot();
    insertTrace(board);
    assertEquals(0, board.routingLedger().incompleteCount());
    board.undo(null);

    assertMatchesFullScan(board);
    assertEquals(1, board.routingLedger().incompleteCount());

    board.redo(null);

    assertMatchesFullScan(board);
    assertEquals(0, board.routingLedger().incompleteCount());
  }

  @ParameterizedTest
  @MethodSource("boardProviders")
  void undoAndRedoOfInPlaceNetAssignmentRestoresMembership(Supplier<BasicBoard> boardSupplier) {
    BasicBoard board = boardSupplier.get();
    assertEquals(1, board.routingLedger().incompleteCount());
    Pin pin = board.getPins().iterator().next();
    Net extraNet = board.rules.nets.add("EXTRA_NET", 1, false);
    extraNet.setClass(board.rules.netClasses.get(0));
    int newNet = extraNet.netNumber;

    board.generateSnapshot();
    pin.assignNetNo(newNet);
    assertEquals(0, board.routingLedger().incompleteCount());

    board.undo(null);

    assertMatchesFullScan(board);
    assertEquals(1, board.routingLedger().incompleteCount());

    board.redo(null);

    assertMatchesFullScan(board);
    assertEquals(0, board.routingLedger().incompleteCount());
  }

  @ParameterizedTest
  @MethodSource("boardProviders")
  void undoOfInPlaceNetAssignmentWithoutIntermediateRead(Supplier<BasicBoard> boardSupplier) {
    BasicBoard board = boardSupplier.get();
    assertEquals(1, board.routingLedger().incompleteCount());
    Pin pin = board.getPins().iterator().next();
    Net extraNet = board.rules.nets.add("EXTRA_NET", 1, false);
    extraNet.setClass(board.rules.netClasses.get(0));
    int newNet = extraNet.netNumber;

    board.generateSnapshot();
    pin.assignNetNo(newNet);
    board.undo(null);

    assertMatchesFullScan(board);
    assertEquals(1, board.routingLedger().incompleteCount());
  }

  private static Stream<Supplier<BasicBoard>> boardProviders() {
    return Stream.of(NetRoutingLedgerTest::twoPinBoard, NetRoutingLedgerTest::twoPinBasicBoard);
  }

  private static RoutingBoard twoPinBoard() {
    BoardReadResult result =
        DsnReader.readBoard(
            DsnTestFixtures.openResource("scoring-perfect-two-pin.dsn"), null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    return (RoutingBoard) ((BoardReadResult.Success) result).board();
  }

  private static BasicBoard twoPinBasicBoard() {
    LayerStructure layerStructure = new LayerStructure(new Layer[] {new Layer("top", true)});
    IntBox bounds = new IntBox(0, 0, 10000, 10000);
    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(layerStructure, 10);
    BoardRules rules = new BoardRules(layerStructure, clearanceMatrix);
    rules.createDefaultNetClass();
    Communication communication = new Communication();
    BasicBoard board =
        new BasicBoard(
            bounds,
            layerStructure,
            new PolylineShape[] {bounds.toSimplex()},
            0,
            rules,
            communication);
    Net net = rules.nets.add("PERFECT", 1, false);
    net.setClass(rules.netClasses.get(0));
    board.library.padstacks = new Padstacks(layerStructure);
    board.library.packages = new Packages(board.library.padstacks);
    Padstack pad = board.library.padstacks.add(new ConvexShape[] {new Circle(Point.ZERO, 400)});
    Package pkg =
        board.library.packages.add(
            new Package.Pin[] {new Package.Pin("1", pad.id, Vector.ZERO, 0)});
    var c1 = board.components.add(new IntPoint(2000, 5000), 0, true, pkg);
    var c2 = board.components.add(new IntPoint(8000, 5000), 0, true, pkg);
    board.insertPin(c1.id, 0, new int[] {1}, 1, FixedState.SYSTEM_FIXED);
    board.insertPin(c2.id, 0, new int[] {1}, 1, FixedState.SYSTEM_FIXED);
    return board;
  }

  private static void insertTrace(BasicBoard board) {
    List<Pin> pins = new ArrayList<>(board.getPins());
    int netNumber = pins.get(0).getNetNumber(0);
    NetClass netClass = board.rules.nets.get(netNumber).getNetClass();
    board.insertTraceWithoutCleaning(
        new Polyline(pins.get(0).getCenter(), pins.get(1).getCenter()),
        0,
        netClass.getTraceHalfWidth(0),
        new int[] {netNumber},
        netClass.getTraceClearanceClass(),
        FixedState.UNFIXED);
  }

  @Test
  void deepCopyTakesTheCountsAndStaysInStepWithTheCopy() {
    BoardReadResult result =
        DsnReader.readBoard(DsnTestFixtures.openResource("Issue508-DAC2020_bm01.dsn"), null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();
    int sourceCount = board.routingLedger().incompleteCount();

    RoutingBoard copy = board.deepCopy();

    assertMatchesFullScan(copy);
    assertEquals(sourceCount, copy.routingLedger().incompleteCount());
    for (int netNumber = 1; netNumber <= copy.rules.nets.maxNetNumber(); netNumber++) {
      assertEquals(
          board.routingLedger().incompleteCount(netNumber),
          copy.routingLedger().incompleteCount(netNumber));
    }

    // The copy tracks its own changes, and the source is left as it was.
    List<Pin> pins = new ArrayList<>(copy.getPins());
    Pin first = pins.get(0);
    Pin second =
        pins.stream()
            .filter(pin -> pin != first && pin.getNetNumber(0) == first.getNetNumber(0))
            .findFirst()
            .orElseThrow();
    int netNumber = first.getNetNumber(0);
    NetClass netClass = copy.rules.nets.get(netNumber).getNetClass();
    copy.insertTraceWithoutCleaning(
        new Polyline(first.getCenter(), second.getCenter()),
        first.firstLayer(),
        netClass.getTraceHalfWidth(first.firstLayer()),
        new int[] {netNumber},
        netClass.getTraceClearanceClass(),
        FixedState.UNFIXED);
    assertMatchesFullScan(copy);
    assertMatchesFullScan(board);
    assertEquals(sourceCount, board.routingLedger().incompleteCount());
  }

  private static void assertMatchesFullScan(BasicBoard board) {
    DesignRulesChecker checker = new DesignRulesChecker(board, null);
    checker.calculateAllIncompletes();
    assertEquals(checker.getIncompleteCount(), board.routingLedger().incompleteCount());
    assertEquals(checker.maxConnections, board.routingLedger().maximumConnections());
    assertEquals(checker.incompleteNetNumbers(), board.routingLedger().incompleteNetNumbers());
  }
}
