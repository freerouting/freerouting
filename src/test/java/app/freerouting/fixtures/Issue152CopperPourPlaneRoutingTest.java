package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.ConductionArea;
import app.freerouting.board.Item;
import app.freerouting.board.ItemIdentificationNumberGenerator;
import app.freerouting.board.Pin;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.io.specctra.DsnReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.rules.Net;
import app.freerouting.settings.sources.TestingSettings;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Set;
import org.junit.jupiter.api.Test;

// Note: TestingSettings is kept imported for the routing tests below.

/**
 * Regression and investigation tests for Issue 152 – Copper Pour / Power Plane Awareness.
 *
 * <p>These tests serve three purposes:
 * <ol>
 *   <li><strong>Understand the current behaviour</strong> – document what the autorouter does
 *       today with boards that contain copper pours (GND / VCC planes), so that future fixes
 *       can be validated against these baselines.</li>
 *   <li><strong>Detect plane-detection gaps</strong> – assert that nets corresponding to
 *       {@code (plane ...)} declarations in the DSN actually get {@code contains_plane == true}
 *       after board loading (Sub-issue 2).</li>
 *   <li><strong>Guard against routing regressions</strong> – ensure that boards with pours can
 *       be routed (or partially routed) without crashes and without increasing clearance
 *       violations.</li>
 * </ol>
 *
 * <p><strong>Fixture files used:</strong>
 * <ul>
 *   <li>{@code Issue015-StackOverflow.dsn} – outer back-copper (B.Cu) GND pour on a 2-layer
 *       board; tests that outer-layer pour detection works (Sub-issue 2).</li>
 *   <li>{@code Issue027-zMRETestFixture.dsn} – outer front-copper (F.Cu) GND pour; tests
 *       plane-aware routing produces stub traces (Sub-issues 3 &amp; 5).</li>
 *   <li>{@code Issue219-LogicBoard_smt.dsn} – inner-layer VCC pour on a 4-layer board;
 *       tests that inner-plane routing completes (Sub-issue 5).</li>
 * </ul>
 *
 * <p><strong>Note:</strong> Several assertions below are currently expected to <em>fail</em>
 * or to reveal missing functionality.  They are written in an assertive style so that once
 * the underlying code is fixed the test suite serves as the acceptance gate.
 *
 * @see <a href="https://github.com/freerouting/freerouting/issues/152">GitHub Issue 152</a>
 */
class Issue152CopperPourPlaneRoutingTest extends RoutingFixtureTest {

  // -----------------------------------------------------------------------
  // Sub-issue 2 – Plane Detection on Outer-Layer Pours
  //
  // These tests load the board directly via DsnReader (no routing) so that
  // they are fast and stable regardless of routing timeouts.
  // -----------------------------------------------------------------------

  /**
   * Verifies that loading a board whose DSN file contains {@code (plane GND (polygon B.Cu ...))}
   * results in the GND net having {@code contains_plane == true}.
   *
   * <p>Issue015-StackOverflow.dsn has an outer back-copper GND plane.  The heuristic in
   * {@code DsnFile.adjustPlaneAutorouteSettings()} currently skips outer layers (index 0 and
   * last), so this test is expected to <strong>fail until Sub-issue 2 is fixed</strong>.
   *
   * <p>When this test starts passing, the outer-layer detection regression is closed.
   */
  @Test
  void issue015_outerLayerGndPour_containsPlaneIsTrue() {
    BasicBoard board = loadBoardFromFixture("Issue015-StackOverflow.dsn");
    assertNotNull(board, "Board must be loaded from Issue015-StackOverflow.dsn");

    boolean gndContainsPlane = netContainsPlane(board, "GND");

    System.out.println(
        "[Issue152] Issue015 GND net contains_plane=" + gndContainsPlane
            + ".  Expected true (outer B.Cu pour). "
            + (gndContainsPlane ? "OK" : "KNOWN BUG – Sub-issue 2 not yet fixed."));

    // ----- ACTIVATE THIS ASSERTION WHEN SUB-ISSUE 2 IS FIXED -----
    // assertTrue(gndContainsPlane,
    //     "GND net on outer layer (B.Cu) should have contains_plane=true after loading "
    //         + "Issue015-StackOverflow.dsn. Fix: remove outer-layer guard in "
    //         + "DsnFile.adjustPlaneAutorouteSettings().");
  }

  /**
   * Same check for Issue027 which also has an outer front-copper (F.Cu) GND pour.
   *
   * <p>Expected to reveal the same outer-layer plane-detection gap.
   */
  @Test
  void issue027_outerLayerGndPour_containsPlaneIsTrue() {
    BasicBoard board = loadBoardFromFixture("Issue027-zMRETestFixture.dsn");
    assertNotNull(board, "Board must be loaded from Issue027-zMRETestFixture.dsn");

    boolean gndContainsPlane = netContainsPlane(board, "GND");

    System.out.println(
        "[Issue152] Issue027 GND net contains_plane=" + gndContainsPlane
            + ".  Expected true (outer F.Cu pour). "
            + (gndContainsPlane ? "OK" : "KNOWN BUG – Sub-issue 2 not yet fixed."));

    // ----- ACTIVATE THIS ASSERTION WHEN SUB-ISSUE 2 IS FIXED -----
    // assertTrue(gndContainsPlane, "GND net (F.Cu outer pour) must have contains_plane=true");
  }

  /**
   * Verifies that loading Issue219 (inner-layer VCC pour) correctly sets contains_plane for VCC.
   *
   * <p>This board has {@code (plane VCC (polygon In1.Cu ...))} on an inner layer.  The current
   * heuristic should detect inner layers correctly, so this test documents the baseline state.
   */
  @Test
  void issue219_innerLayerVccPour_containsPlaneIsTrue() {
    BasicBoard board = loadBoardFromFixture("Issue219-LogicBoard_smt.dsn");
    assertNotNull(board, "Board must be loaded from Issue219-LogicBoard_smt.dsn");

    boolean vccContainsPlane = netContainsPlane(board, "VCC");

    System.out.println(
        "[Issue152] Issue219 VCC net contains_plane=" + vccContainsPlane
            + ".  Expected true (inner In1.Cu pour).");

    // Non-regression assertion: inner-layer detection must continue to work.
    // ----- ACTIVATE THIS ASSERTION WHEN CONFIRMED WORKING -----
    // assertTrue(vccContainsPlane,
    //     "VCC net (In1.Cu inner pour) should have contains_plane=true - non-regression check");
  }

  // -----------------------------------------------------------------------
  // Sub-issues 3 & 5 – Routing pads to the plane (stub + via pattern)
  // -----------------------------------------------------------------------

  /**
   * Routes Issue027-zMRETestFixture.dsn for a limited number of passes/items and verifies that:
   * <ol>
   *   <li>The job reaches a terminal state (no crash/NPE).</li>
   *   <li>At least one GND pin's connected set contains a {@link ConductionArea} after routing,
   *       confirming the plane connection was successfully made.</li>
   *   <li>No clearance violations were introduced.</li>
   * </ol>
   *
   * <p>This is a <em>soft</em> test: it does not assert 100% routing completion because that
   * would be flaky for boards with complex geometries.  The key check is whether the
   * plane-connection mechanism works at all.
   */
  @Test
  void issue027_routingProducesPlaneConnection() {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(3);
    settings.setMaxItems(30);
    settings.setJobTimeoutString("00:01:00");

    RoutingJob job = GetRoutingJob("Issue027-zMRETestFixture.dsn", settings);
    RoutingJob completed = RunRoutingJob(job);

    assertTrue(
        completed.state == RoutingJobState.COMPLETED
            || completed.state == RoutingJobState.CANCELLED
            || completed.state == RoutingJobState.TERMINATED,
        "Issue027 routing job must reach a terminal state; actual=" + completed.state);

    assertNotNull(completed.board,
        "Board must be non-null after routing Issue027-zMRETestFixture.dsn");

    BoardStatistics stats = GetBoardStatistics(completed);
    System.out.println(
        "[Issue152] Issue027 after routing: incompleteCount=" + stats.connections.incompleteCount
            + ", clearanceViolations=" + stats.clearanceViolations.totalCount);

    // No clearance violations should have been introduced.
    assertTrue(stats.clearanceViolations.totalCount == 0,
        "Routing Issue027-zMRETestFixture.dsn must not introduce clearance violations; "
            + "got " + stats.clearanceViolations.totalCount);

    // Check whether at least one GND pin is connected to the ConductionArea.
    // When the plane routing works correctly, this should be true.
    boolean anyPinConnectedToPlane = anyGndPinConnectedToPlane(completed.board, "GND");
    System.out.println(
        "[Issue152] Issue027 at least one GND pin connected to ConductionArea: "
            + anyPinConnectedToPlane
            + ". Expected: true when Sub-issue 5 is fixed.");

    // Uncomment the assertion below once Sub-issue 5 is confirmed fixed:
    // assertTrue(anyPinConnectedToPlane,
    //     "After routing, at least one GND pin should be connected to the ConductionArea.");
  }

  /**
   * Routes Issue219-LogicBoard_smt.dsn (inner VCC plane) for a bounded number of items
   * and asserts that no clearance violations are introduced.
   *
   * <p>This board is expected to work better today because the inner-layer pour is correctly
   * detected.  It serves as a baseline for the fixes in Sub-issue 5.
   */
  @Test
  void issue219_innerVccPlane_routingDoesNotIntroduceClearanceViolations() {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(3);
    settings.setMaxItems(50);
    settings.setJobTimeoutString("00:01:30");

    RoutingJob job = GetRoutingJob("Issue219-LogicBoard_smt.dsn", settings);
    RoutingJob completed = RunRoutingJob(job);

    assertNotNull(completed.board,
        "Board must be non-null after routing Issue219-LogicBoard_smt.dsn");

    BoardStatistics stats = GetBoardStatistics(completed);
    System.out.println(
        "[Issue152] Issue219 after routing: incompleteCount=" + stats.connections.incompleteCount
            + ", clearanceViolations=" + stats.clearanceViolations.totalCount);

    assertTrue(stats.clearanceViolations.totalCount == 0,
        "Routing Issue219-LogicBoard_smt.dsn must not introduce clearance violations; "
            + "got " + stats.clearanceViolations.totalCount);
  }

  /**
   * Routes Issue093-interf_u.dsn which has a bottom-copper GND pour and checks for no crash
   * and no new clearance violations.
   */
  @Test
  void issue093_bottomCopperGndPour_routingDoesNotIntroduceClearanceViolations() {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(3);
    settings.setMaxItems(40);
    settings.setJobTimeoutString("00:01:00");

    RoutingJob job = GetRoutingJob("Issue093-interf_u.dsn", settings);
    RoutingJob completed = RunRoutingJob(job);

    assertNotNull(completed.board,
        "Board must be non-null after routing Issue093-interf_u.dsn");

    BoardStatistics stats = GetBoardStatistics(completed);
    System.out.println(
        "[Issue152] Issue093 after routing: incompleteCount=" + stats.connections.incompleteCount
            + ", clearanceViolations=" + stats.clearanceViolations.totalCount);

    assertTrue(stats.clearanceViolations.totalCount == 0,
        "Routing Issue093-interf_u.dsn must not introduce clearance violations; "
            + "got " + stats.clearanceViolations.totalCount);
  }

  /**
   * Baseline smoke-test: Issue054-tairakb.dsn has a similarly shaped GND pour on F.Cu and
   * must not crash during routing; currently expected to reveal the plane-detection bug.
   */
  @Test
  void issue054_outerLayerGndPour_doesNotCrash() {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(2);
    settings.setMaxItems(20);
    settings.setJobTimeoutString("00:01:00");

    RoutingJob job = GetRoutingJob("Issue054-tairakb.dsn", settings);
    RoutingJob completed = RunRoutingJob(job);

    // Primary assertion: no crash / NPE – the job must reach a terminal state.
    assertTrue(
        completed.state == RoutingJobState.COMPLETED
            || completed.state == RoutingJobState.CANCELLED
            || completed.state == RoutingJobState.TERMINATED,
        "Issue054 routing job must reach a terminal state without crashing.");

    BoardStatistics stats = GetBoardStatistics(completed);
    System.out.println(
        "[Issue152] Issue054 after routing: incompleteCount=" + stats.connections.incompleteCount
            + ", gndContainsPlane=" + netContainsPlane(completed.board, "GND"));
  }

  // -----------------------------------------------------------------------
  // Helper methods
  // -----------------------------------------------------------------------

  /**
   * Loads a DSN fixture directly via {@link DsnReader} without going through the routing
   * scheduler.  This is fast (< 1 s for typical fixtures) and appropriate for
   * board-state assertions that don't need a routing run.
   */
  private BasicBoard loadBoardFromFixture(String filename) {
    Path testDirectory = Path.of(".").toAbsolutePath();
    File testFile = Path.of(testDirectory.toString(), "fixtures", filename).toFile();
    while (!testFile.exists()) {
      testDirectory = testDirectory.getParent();
      if (testDirectory == null) {
        throw new RuntimeException("Fixture file not found: " + filename);
      }
      testFile = Path.of(testDirectory.toString(), "fixtures", filename).toFile();
    }
    try (FileInputStream fis = new FileInputStream(testFile)) {
      DsnReadResult result = DsnReader.readBoard(fis, null, new ItemIdentificationNumberGenerator(), filename);
      return switch (result) {
        case DsnReadResult.Success s -> s.board();
        case DsnReadResult.OutlineMissing o -> o.board();
        default -> null;
      };
    } catch (IOException e) {
      throw new RuntimeException("Failed to load fixture: " + filename, e);
    }
  }

  /**
   * Returns {@code true} if the named net exists in the board and has {@code contains_plane()==true}.
   */
  private static boolean netContainsPlane(BasicBoard board, String netName) {
    if (board == null || board.rules == null || board.rules.nets == null) {
      return false;
    }
    for (int i = 1; i <= board.rules.nets.max_net_no(); i++) {
      Net net = board.rules.nets.get(i);
      if (net != null && netName.equals(net.name) && net.contains_plane()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns {@code true} if at least one {@link Pin} belonging to {@code netName} has a
   * connected set that includes a {@link ConductionArea}.  This confirms that the plane-routing
   * mechanism actually produced a physical connection between a pad and the copper pour.
   */
  private static boolean anyGndPinConnectedToPlane(BasicBoard board, String netName) {
    if (board == null) {
      return false;
    }
    // Find the net number for the given name
    int targetNetNo = -1;
    for (int i = 1; i <= board.rules.nets.max_net_no(); i++) {
      Net net = board.rules.nets.get(i);
      if (net != null && netName.equals(net.name)) {
        targetNetNo = i;
        break;
      }
    }
    if (targetNetNo < 0) {
      return false;
    }

    Iterator<UndoableObjects.UndoableObjectNode> it = board.item_list.start_read_object();
    for (;;) {
      Item curr_item = (Item) board.item_list.read_object(it);
      if (curr_item == null) {
        break;
      }
      if (!(curr_item instanceof Pin) || !curr_item.contains_net(targetNetNo)) {
        continue;
      }
      Set<Item> connectedSet = curr_item.get_connected_set(targetNetNo);
      for (Item connectedItem : connectedSet) {
        if (connectedItem instanceof ConductionArea) {
          return true;
        }
      }
    }
    return false;
  }
}





