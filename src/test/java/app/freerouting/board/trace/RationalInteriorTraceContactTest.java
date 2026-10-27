package app.freerouting.board.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.autoroute.pipeline.ExitStubs;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.optimize.TraceTightener;
import app.freerouting.board.state.Communication;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.settings.GlobalSettings;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RationalInteriorTraceContactTest {
  private RoutingBoard board;

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
    LayerStructure layers =
        new LayerStructure(new Layer[] {new Layer("Top", true), new Layer("Bottom", true)});
    BoardRules rules = new BoardRules(layers, ClearanceMatrix.getDefaultInstance(layers, 10));
    rules.createDefaultNetClass();
    board =
        new RoutingBoard(
            new IntBox(-3000000, -3000000, 3000000, 3000000),
            layers,
            new PolylineShape[0],
            0,
            rules,
            new Communication());
    rules.nets.add("Net1", 1, false);
    rules.nets.add("Net2", 2, false);
  }

  @ParameterizedTest
  @CsvSource({"false", "true"})
  void splitsMutableTraceAtExactRationalInterior(boolean keepPoint) {
    PolylineTrace trunk =
        insert(
            new Polyline(new IntPoint(-1000, -1000), new IntPoint(1000, 1000)),
            0,
            1,
            FixedState.UNFIXED);
    Point junction = Point.getInstance(BigInteger.ONE, BigInteger.ONE, BigInteger.TWO);
    if (keepPoint) {
      assertTrue(
          TraceTightener.getInstance(board, new int[] {1}, null, 500, null, -1, junction, 0)
              .splitTracesAtKeepPoint());
    } else {
      Trace[] pieces = trunk.split(junction);
      assertNotNull(pieces);
      assertEquals(2, pieces.length);
      assertEquals(junction, pieces[0].lastCorner());
      assertEquals(junction, pieces[1].firstCorner());
    }
    assertFalse(trunk.isOnTheBoard());
    assertEquals(2, board.getTraces().size());
    var livePieces = board.getTraces().stream().map(trace -> (PolylineTrace) trace).toList();
    assertTrue(livePieces.stream().allMatch(piece -> piece.cornerCount() == 2));
    assertEquals(
        Set.of(
            Set.of(new IntPoint(-1000, -1000), junction),
            Set.of(junction, new IntPoint(1000, 1000))),
        livePieces.stream()
            .map(piece -> Set.of(piece.firstCorner(), piece.lastCorner()))
            .collect(Collectors.toSet()));
    assertEquals(1, board.getConnectedSets(1).size());
    assertTrue(new DesignRulesChecker(board, null).getAllClearanceViolations().isEmpty());
  }

  @Test
  void safelyDeclinesRationalSplitWithoutRepresentableCap() {
    PolylineTrace trunk =
        insert(
            new Polyline(new IntPoint(-1000, 0), new IntPoint(1000, 0)), 0, 1, FixedState.UNFIXED);
    Point junction =
        Point.getInstance(BigInteger.ONE, BigInteger.ZERO, BigInteger.ONE.shiftLeft(100));
    assertTrue(trunk.contains(junction));
    assertNull(trunk.split(junction));
    assertTrue(trunk.isOnTheBoard());
    assertEquals(1, board.getTraces().size());
  }

  @ParameterizedTest
  @CsvSource({"false", "true"})
  void cleanupPreservesUnsplitTrunkWithInteriorContacts(boolean danglingBranch) {
    PolylineTrace left =
        insert(
            new Polyline(new IntPoint(10000, 0), new IntPoint(10000, 10000)),
            0,
            1,
            FixedState.USER_FIXED);
    PolylineTrace right =
        insert(
            new Polyline(new IntPoint(20000, 0), new IntPoint(20000, 10000)),
            0,
            1,
            FixedState.USER_FIXED);
    PolylineTrace trunk =
        insert(new Polyline(new IntPoint(0, 0), new IntPoint(30000, 0)), 0, 1, FixedState.UNFIXED);
    final PolylineTrace stub =
        danglingBranch
            ? insert(
                new Polyline(new IntPoint(15000, 0), new IntPoint(15000, -10000)),
                0,
                1,
                FixedState.UNFIXED)
            : null;
    assertTrue(left.getConnectedSet(1).contains(right));
    assertTrue(trunk.isTail());
    assertTrue(
        new DesignRulesChecker(board, null)
            .getAllUnconnectedItems().stream()
                .anyMatch(
                    issue -> "track_dangling".equals(issue.type) && issue.firstItem == trunk));
    assertNull(board.getTraceTail(trunk.firstCorner(), 0, new int[] {1}));
    assertNull(board.getTraceTail(trunk.lastCorner(), 0, new int[] {1}));
    board.removeTraceTails(1, Item.StopConnectionOption.NONE);
    assertTrue(trunk.isOnTheBoard());
    assertTrue(left.getConnectedSet(1).contains(right));
    if (stub != null) {
      assertFalse(stub.isOnTheBoard());
    }
  }

  @Test
  void endpointAndInteriorContactToSameItemPreventWholeTailRemoval() {
    PolylineTrace contact =
        insert(
            new Polyline(
                new Point[] {
                  new IntPoint(0, 0), new IntPoint(15000, 10000), new IntPoint(15000, 0)
                }),
            0,
            1,
            FixedState.USER_FIXED);
    PolylineTrace trunk =
        insert(new Polyline(new IntPoint(0, 0), new IntPoint(30000, 0)), 0, 1, FixedState.UNFIXED);
    assertTrue(trunk.getStartContacts().contains(contact));
    assertEquals(1, trunk.getNormalContacts().size());
    assertTrue(trunk.hasInteriorContacts());
    assertTrue(trunk.isTail());
    assertTrue(trunk.getConnectionItems().isEmpty());
    assertFalse(board.removeTraceTails(1, Item.StopConnectionOption.NONE));
    assertTrue(trunk.isOnTheBoard());
  }

  @Test
  void exitStubReleaseKeepsAnUnsplitInteriorJunction() {
    PolylineTrace contact =
        insert(
            new Polyline(
                new Point[] {
                  new IntPoint(0, 0), new IntPoint(15000, 10000), new IntPoint(15000, 0)
                }),
            0,
            1,
            FixedState.USER_FIXED);
    PolylineTrace trunk =
        insert(new Polyline(new IntPoint(0, 0), new IntPoint(30000, 0)), 0, 1, FixedState.UNFIXED);
    // A component trace cannot be split, so release must not delete it as a dangling tail.
    trunk.assignComponentId(1);
    trunk.setFixedState(ExitStubs.PLACED_STATE);
    assertTrue(trunk.isTail());
    assertTrue(trunk.hasInteriorContacts());
    String released = ExitStubs.release(board, List.of(trunk.getId()));
    assertNotNull(released);
    assertFalse(released.contains("would open"));
    assertTrue(trunk.isOnTheBoard());
    assertTrue(contact.isOnTheBoard());
    assertEquals(FixedState.UNFIXED, trunk.getFixedState());
  }

  @Test
  void splitsCapturedVerticalC4JunctionWithoutRounding() {
    PolylineTrace trunk =
        insert(
            new Polyline(new IntPoint(1846069, -1220000), new IntPoint(1846069, -1210000)),
            0,
            1,
            FixedState.UNFIXED);
    Point junction =
        Point.getInstance(
            BigInteger.valueOf(1846069L * 97),
            BigInteger.valueOf(-117835094),
            BigInteger.valueOf(97));
    Trace[] pieces = trunk.split(junction);
    assertNotNull(pieces);
    assertEquals(junction, pieces[0].lastCorner());
    assertEquals(junction, pieces[1].firstCorner());
    assertEquals(1, board.getConnectedSets(1).size());
    assertTrue(new DesignRulesChecker(board, null).getAllClearanceViolations().isEmpty());
  }

  @Test
  void cycleCleanupReportsNoChangeWhenInteriorJunctionCannotBeRemovedWhole() {
    PolylineTrace trunk =
        insert(
            new Polyline(new IntPoint(0, 5000), new IntPoint(30000, 5000)),
            0,
            1,
            FixedState.UNFIXED);
    insert(
        new Polyline(
            new Point[] {
              new IntPoint(0, 5000),
              new IntPoint(0, 30000),
              new IntPoint(30000, 30000),
              new IntPoint(30000, 5000)
            }),
        0,
        1,
        FixedState.USER_FIXED);
    insert(
        new Polyline(new IntPoint(15000, 5000), new IntPoint(15000, 15000)),
        0,
        1,
        FixedState.USER_FIXED);
    assertTrue(trunk.isCycle());
    assertTrue(trunk.getConnectionItems().isEmpty());
    var before = new ArrayList<>(board.getItems());
    assertFalse(board.removeIfCycle(trunk));
    assertTrue(trunk.isOnTheBoard());
    assertEquals(before, new ArrayList<>(board.getItems()));
  }

  @Test
  void stillRemovesOrdinaryEndpointTail() {
    final PolylineTrace fixed =
        insert(
            new Polyline(new IntPoint(0, 0), new IntPoint(10000, 0)), 0, 1, FixedState.USER_FIXED);
    final PolylineTrace stub =
        insert(
            new Polyline(new IntPoint(10000, 0), new IntPoint(20000, 0)), 0, 1, FixedState.UNFIXED);
    assertTrue(stub.isTail());
    assertSame(stub, board.getTraceTail(stub.lastCorner(), 0, new int[] {1}));
    assertTrue(board.removeTraceTails(1, Item.StopConnectionOption.NONE));
    assertFalse(stub.isOnTheBoard());
    assertTrue(fixed.isOnTheBoard());
  }

  @ParameterizedTest
  @CsvSource({
    "false, false, USER_FIXED",
    "false, true, USER_FIXED",
    "true, false, USER_FIXED",
    "true, true, USER_FIXED",
    "false, false, SYSTEM_FIXED",
    "false, true, SYSTEM_FIXED",
    "true, false, SYSTEM_FIXED",
    "true, true, SYSTEM_FIXED"
  })
  void rationalBranchContactsPreservedTrunkInterior(
      boolean reverseTrunk, boolean reverseBranch, FixedState fixedState) {
    Polyline trunkLine = new Polyline(new IntPoint(-1000, -1000), new IntPoint(1000, 1000));
    Polyline branchLine = branch(1);
    Point junction = branchLine.lastCorner();
    Polyline orientedTrunk = reverseTrunk ? trunkLine.reverse() : trunkLine;
    PolylineTrace trunk = insert(orientedTrunk, 0, 1, fixedState);
    PolylineTrace branch =
        insert(reverseBranch ? branchLine.reverse() : branchLine, 0, 1, FixedState.UNFIXED);

    assertTrue(trunk.contains(junction));
    assertTrue(trunk.getNormalContacts().contains(branch));
    assertTrue(branch.getNormalContacts().contains(trunk));
    assertEquals(junction, trunk.normalContactPoint((Item) branch));
    assertEquals(junction, branch.normalContactPoint((Item) trunk));
    assertTrue(trunk.getConnectedSet(1).contains(branch));
    assertTrue(branch.getConnectedSet(1).contains(trunk));
    assertEquals(1, board.getConnectedSets(1).size());
    assertSame(orientedTrunk, trunk.polyline());
    assertEquals(fixedState, trunk.getFixedState());
    assertTrue(new DesignRulesChecker(board, null).getAllClearanceViolations().isEmpty());
  }

  @Test
  void contactAtRationalInternalBendIsTraversable() {
    Line left = new Line(new IntPoint(-1000, 0), new IntPoint(-1000, 1));
    Line diagonal = new Line(new IntPoint(0, 0), new IntPoint(2, 2));
    Line cut = new Line(new IntPoint(0, 1), new IntPoint(1, 0));
    Line right = new Line(new IntPoint(1000, 0), new IntPoint(1000, 1));
    PolylineTrace trunk =
        insert(new Polyline(new Line[] {left, diagonal, cut, right}), 0, 1, FixedState.USER_FIXED);
    PolylineTrace branch =
        insert(
            new Polyline(new Line[] {left, new Line(new IntPoint(0, -1), new IntPoint(1, 2)), cut}),
            0,
            1,
            FixedState.UNFIXED);

    assertEquals(branch.lastCorner(), trunk.polyline().corner(1));
    assertTrue(trunk.contains(branch.lastCorner()));
    assertTrue(branch.getNormalContacts().contains(trunk));
    assertTrue(trunk.getNormalContacts().contains(branch));
    assertEquals(1, board.getConnectedSets(1).size());
  }

  @Test
  void differentNetLayerAndLineExtensionDoNotCreateContacts() {
    PolylineTrace trunk =
        insert(
            new Polyline(new IntPoint(-1000, -1000), new IntPoint(1000, 1000)),
            0,
            1,
            FixedState.USER_FIXED);
    PolylineTrace differentNet = insert(branch(1), 0, 2, FixedState.UNFIXED);
    PolylineTrace differentLayer = insert(branch(1), 1, 1, FixedState.UNFIXED);
    final PolylineTrace extension = insert(branch(3001), 0, 1, FixedState.UNFIXED);

    assertFalse(differentNet.getNormalContacts().contains(trunk));
    assertFalse(trunk.getNormalContacts().contains(differentNet));
    assertFalse(differentLayer.getNormalContacts().contains(trunk));
    assertFalse(trunk.getNormalContacts().contains(differentLayer));
    assertFalse(trunk.contains(extension.lastCorner()));
    assertFalse(extension.getNormalContacts().contains(trunk));
    assertNull(differentLayer.normalContactPoint((Item) trunk));
  }

  @Test
  void multipleExactInteriorContactsRemainAmbiguous() {
    PolylineTrace trunk =
        insert(new Polyline(new IntPoint(0, 0), new IntPoint(2, 2)), 0, 1, FixedState.USER_FIXED);
    PolylineTrace overlap =
        insert(
            new Polyline(
                new Line[] {
                  new Line(new IntPoint(0, 1), new IntPoint(1, 0)),
                  new Line(new IntPoint(0, 0), new IntPoint(2, 2)),
                  new Line(new IntPoint(0, 3), new IntPoint(3, 0))
                }),
            0,
            1,
            FixedState.UNFIXED);

    assertTrue(trunk.contains(overlap.firstCorner()));
    assertTrue(trunk.contains(overlap.lastCorner()));
    assertNull(trunk.normalContactPoint((Item) overlap));
    assertNull(overlap.normalContactPoint((Item) trunk));
  }

  @Test
  void capturedC4JunctionDoesNotSplitAndRecombineRepeatedly() {
    Polyline trunkLine =
        new Polyline(new IntPoint(1827966, -1225246), new IntPoint(1853380, -1210574));
    PolylineTrace trunk = insert(trunkLine, 0, 1, FixedState.USER_FIXED);
    PolylineTrace branch =
        insert(
            new Polyline(new IntPoint(1846069, -1214795), new IntPoint(1846069, -1213955)),
            0,
            1,
            FixedState.UNFIXED);
    var pieces = branch.split((IntOctagon) null);
    assertEquals(2, pieces.size());
    PolylineTrace first = pieces.iterator().next();
    assertTrue(trunk.contains(first.lastCorner()));
    assertTrue(first.getNormalContacts().contains(trunk));
    assertFalse(first.combine());

    for (int pass = 0; pass < 3; pass++) {
      for (var trace : new ArrayList<>(board.getTraces())) {
        if (!trace.isUserFixed()) {
          assertFalse(((PolylineTrace) trace).normalize(null));
        }
      }
    }
    assertEquals(1, board.getConnectedSets(1).size());
    assertSame(trunkLine, trunk.polyline());
    assertTrue(new DesignRulesChecker(board, null).getAllClearanceViolations().isEmpty());
  }

  private static Polyline branch(int sum) {
    return new Polyline(
        new Line[] {
          new Line(new IntPoint(-1000, 0), new IntPoint(-1000, 1)),
          new Line(new IntPoint(0, sum), new IntPoint(1, sum - 1)),
          new Line(new IntPoint(0, 0), new IntPoint(1, 1))
        });
  }

  private PolylineTrace insert(Polyline polyline, int layer, int net, FixedState fixedState) {
    return board.insertTraceWithoutCleaning(polyline, layer, 10, new int[] {net}, 1, fixedState);
  }
}
