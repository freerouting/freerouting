package app.freerouting.autoroute.decoupling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.io.specctra.DsnTestFixtures;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DecouplingAssignmentPlannerTest {

  @Test
  @DisplayName("Hungarian algorithm solves diagonal vs anti-diagonal min-cost matching")
  void testHungarianMatchingDirect() {
    // 2x2 where diagonal is optimal: (0->0, 1->1) with cost 10+20=30 vs 100+100=200
    long[][] cost1 = {
      {10, 100},
      {100, 20}
    };
    int[] match1 = DecouplingAssignmentPlanner.solveMinCostMatching(cost1, 2, 2);
    assertEquals(0, match1[0]);
    assertEquals(1, match1[1]);

    // 2x2 where anti-diagonal uncrosses connections: (0->1, 1->0) with cost 10+10=20
    long[][] cost2 = {
      {100, 10},
      {10, 100}
    };
    int[] match2 = DecouplingAssignmentPlanner.solveMinCostMatching(cost2, 2, 2);
    assertEquals(1, match2[0]);
    assertEquals(0, match2[1]);

    // Rectangular 2x3 matching: 2 caps, 3 pins
    long[][] cost3 = {
      {50, 10, 100},
      {20, 80, 15}
    };
    int[] match3 = DecouplingAssignmentPlanner.solveMinCostMatching(cost3, 2, 3);
    assertEquals(1, match3[0]); // cap 0 pairs with pin 1 (cost 10)
    assertEquals(2, match3[1]); // cap 1 pairs with pin 2 (cost 15)
  }

  @Test
  @DisplayName("Planner safely handles null and empty candidates")
  void testNullAndEmptySafety() {
    BasicBoard board = mock(BasicBoard.class);
    assertTrue(DecouplingAssignmentPlanner.plan(null, List.of(), 8.0, false).isEmpty());
    assertTrue(DecouplingAssignmentPlanner.plan(board, null, 8.0, false).isEmpty());
    assertTrue(DecouplingAssignmentPlanner.plan(board, List.of(), 8.0, false).isEmpty());
  }

  @Test
  @DisplayName(
      "Planner on golden fixture DAC2020_bm01 correctly generates primary and fallback tasks")
  void testGoldenFixturePlanning() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    IdentifierConfig config = IdentifierConfig.defaultConfig();
    IdentificationResult idResult = DecouplingIdentifier.identify(board, config);

    assertFalse(idResult.candidates().isEmpty(), "Expected candidates on DAC2020_bm01");

    List<DecouplingRouteTask> tasks =
        DecouplingAssignmentPlanner.plan(board, idResult.candidates(), 8.0, false);

    assertNotNull(tasks);
    assertFalse(tasks.isEmpty(), "Expected planned tasks");

    // All primary tasks must have a valid target supply pin
    for (DecouplingRouteTask task : tasks) {
      assertNotNull(task.candidate());
      assertTrue(task.supplyPinId() > 0);
      assertTrue(task.groundPinId() > 0);

      if (task.isPrimary()) {
        assertTrue(task.hasTargetSupply(), "Primary task must have target supply pin");
        assertTrue(task.targetSupplyItemId() > 0);
      } else if (task.isSecondary()) {
        assertTrue(task.hasTargetSupply());
        assertTrue(task.pairedPrimaryCapPinId() > 0);
      } else {
        assertTrue(task.isPlaneOnly());
      }
    }
  }

  @Test
  @DisplayName(
      "Multi-capacitor bank (M > N): Lowest ESL assigned primary, overflow assigned secondary")
  void testMultiCapacitorBankHierarchy() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    IdentifierConfig config = IdentifierConfig.defaultConfig();
    IdentificationResult idResult = DecouplingIdentifier.identify(board, config);

    // Pick two decoupling candidates from the board
    List<CapacitorCandidate> decaps =
        idResult.candidates().stream()
            .filter(c -> c.verdict() == Verdict.DECOUPLING && !c.owners().isEmpty())
            .toList();
    assertTrue(decaps.size() >= 2, "Expected at least 2 decoupling candidates with owners");

    CapacitorCandidate base1 = decaps.get(0);
    CapacitorCandidate base2 = decaps.get(1);

    // Construct a cluster sharing the exact same owner IC pin, but with different pad areas
    OwnerCandidate sharedOwner = base1.owners().getFirst();

    // cand1: larger pad area (e.g. 800)
    CapacitorCandidate cand1 =
        new CapacitorCandidate(
            base1.componentId(),
            base1.pinIdA(),
            base1.pinIdB(),
            base1.netNumberA(),
            base1.netNumberB(),
            base1.railNetNumber(),
            base1.groundNetNumber(),
            base1.layer(),
            400L,
            400L,
            90,
            Verdict.DECOUPLING,
            ReasonCode.PARALLEL_SIBLING.mask(),
            AuditResult.AGREE,
            List.of(sharedOwner));

    // cand2: smaller pad area (e.g. 100, lowest ESL)
    CapacitorCandidate cand2 =
        new CapacitorCandidate(
            base2.componentId(),
            base2.pinIdA(),
            base2.pinIdB(),
            base2.netNumberA(),
            base2.netNumberB(),
            base2.railNetNumber(),
            base2.groundNetNumber(),
            base2.layer(),
            50L,
            50L,
            95,
            Verdict.DECOUPLING,
            ReasonCode.PARALLEL_SIBLING.mask(),
            AuditResult.AGREE,
            List.of(sharedOwner));

    // Submit in order: cand1 (larger) then cand2 (smaller), with ample radius to cover both
    List<DecouplingRouteTask> tasks =
        DecouplingAssignmentPlanner.plan(board, List.of(cand1, cand2), 100.0, false);

    assertEquals(2, tasks.size());

    // Task 0 MUST be cand2 (smaller pad area / lowest ESL)
    DecouplingRouteTask primary = tasks.get(0);
    assertEquals(cand2.componentId(), primary.candidate().componentId());
    assertTrue(primary.isPrimary());
    assertEquals(sharedOwner.pinId(), primary.targetSupplyItemId());

    // Task 1 MUST be cand1 (larger pad area) as secondary parallel sibling
    DecouplingRouteTask secondary = tasks.get(1);
    assertEquals(cand1.componentId(), secondary.candidate().componentId());
    assertTrue(secondary.isSecondary());
    assertEquals(sharedOwner.pinId(), secondary.targetSupplyItemId()); // V-star to IC pin
  }

  @Test
  @DisplayName("Empty owners list falls back to PLANE_ONLY_FALLBACK")
  void testEmptyOwnersFallback() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    IdentifierConfig config = IdentifierConfig.defaultConfig();
    IdentificationResult idResult = DecouplingIdentifier.identify(board, config);

    CapacitorCandidate base =
        idResult.candidates().stream()
            .filter(c -> c.verdict() == Verdict.DECOUPLING)
            .findFirst()
            .orElseThrow();

    // Strip owners to test fallback
    CapacitorCandidate ownerless =
        new CapacitorCandidate(
            base.componentId(),
            base.pinIdA(),
            base.pinIdB(),
            base.netNumberA(),
            base.netNumberB(),
            base.railNetNumber(),
            base.groundNetNumber(),
            base.layer(),
            base.padAreaA(),
            base.padAreaB(),
            base.score(),
            Verdict.DECOUPLING,
            0L,
            AuditResult.AGREE,
            List.of());

    List<DecouplingRouteTask> tasks =
        DecouplingAssignmentPlanner.plan(board, List.of(ownerless), 8.0, false);

    assertEquals(1, tasks.size());
    assertTrue(tasks.get(0).isPlaneOnly());
    assertEquals(0, tasks.get(0).targetSupplyItemId());
  }

  @Test
  @DisplayName("Hungarian algorithm handles corner cases and INF_COST arithmetic")
  void testHungarianEdgeCasesAndOverflowSafety() {
    // 0 rows or 0 cols
    assertEquals(0, DecouplingAssignmentPlanner.solveMinCostMatching(new long[0][0], 0, 0).length);
    int[] matchMgtN = DecouplingAssignmentPlanner.solveMinCostMatching(new long[3][2], 3, 2);
    assertEquals(3, matchMgtN.length);
    assertEquals(-1, matchMgtN[0]);
    assertEquals(-1, matchMgtN[1]);
    assertEquals(-1, matchMgtN[2]);

    // INF_COST with 3x3 matrix where one edge is INF_COST
    long inf = 1_000_000_000_000_000L;
    long[][] cost = {
      {10, inf, 30},
      {inf, 20, inf},
      {40, 50, 10}
    };
    int[] match = DecouplingAssignmentPlanner.solveMinCostMatching(cost, 3, 3);
    assertEquals(0, match[0]); // Row 0 -> Col 0 (10)
    assertEquals(1, match[1]); // Row 1 -> Col 1 (20)
    assertEquals(2, match[2]); // Row 2 -> Col 2 (10)
  }

  @Test
  @DisplayName("Candidate with owners beyond maxOwnerRadiusMm falls back to PLANE_ONLY_FALLBACK")
  void testCandidateBeyondMaxRadiusFallback() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    IdentifierConfig config = IdentifierConfig.defaultConfig();
    IdentificationResult idResult = DecouplingIdentifier.identify(board, config);

    CapacitorCandidate base =
        idResult.candidates().stream()
            .filter(c -> c.verdict() == Verdict.DECOUPLING && !c.owners().isEmpty())
            .findFirst()
            .orElseThrow();

    // Plan with an extremely small search radius (0.001 mm) so all pins are unreachable
    List<DecouplingRouteTask> tasks =
        DecouplingAssignmentPlanner.plan(board, List.of(base), 0.001, false);

    assertEquals(1, tasks.size());
    assertTrue(
        tasks.get(0).isPlaneOnly(), "Candidate beyond max radius must be PLANE_ONLY_FALLBACK");
    assertEquals(0, tasks.get(0).targetSupplyItemId());
  }

  @Test
  @DisplayName("Planner resolves and populates targetGroundItemId when owner IC ground pins exist")
  void testPlannerPopulatesTargetGroundItemId() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    IdentifierConfig config = IdentifierConfig.defaultConfig();
    IdentificationResult idResult = DecouplingIdentifier.identify(board, config);

    List<CapacitorCandidate> caps =
        idResult.candidates().stream()
            .filter(c -> c.verdict() == Verdict.DECOUPLING && !c.owners().isEmpty())
            .toList();
    assertFalse(caps.isEmpty());

    List<DecouplingRouteTask> tasks = DecouplingAssignmentPlanner.plan(board, caps, 8.0, false);
    assertFalse(tasks.isEmpty());

    // Verify at least one task has targetGroundItemId populated (> 0)
    boolean hasGroundTarget = tasks.stream().anyMatch(t -> t.targetGroundItemId() > 0);
    assertTrue(
        hasGroundTarget, "Expected targetGroundItemId to be populated for ICs with ground pins");
  }
}
