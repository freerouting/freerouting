package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Pin;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.io.specctra.DsnTestFixtures;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class Issue230PlaneClearanceTest extends RoutingFixtureTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void importedNonblockingPlanesDoNotAddFalsePinViolations(boolean compensated) throws Exception {
    var board = DsnTestFixtures.loadBoard("Issue230-CNH_Functional_Tester_1.dsn");
    board.searchTreeManager.setClearanceCompensationUsed(compensated);
    assertEquals(2, board.getConductionAreas().size());
    for (var item : board.getConductionAreas()) {
      assertFalse(((ConductionArea) item).getIsObstacle(), "Preserve imported plane flags");
    }
    var violations = new DesignRulesChecker(board, null).getAllClearanceViolations();
    assertTrue(
        violations.stream()
            .noneMatch(
                v ->
                    v.firstItem instanceof ConductionArea
                        || v.secondItem instanceof ConductionArea),
        "Nonblocking planes must not add clearance reports in either query mode");
    long pinPairs =
        violations.stream()
            .filter(v -> v.firstItem instanceof Pin && v.secondItem instanceof Pin)
            .count();
    assertTrue(pinPairs >= 6, "Preserve pre-existing pin/pin reports");
    if (!compensated) {
      // Forced compensation currently adds unrelated reports (the separate Issue969 query defect).
      // The imported board's normal, uncompensated mode has exactly six pin/pin reports.
      assertEquals(6, pinPairs);
      assertEquals(6, violations.size());
    }
  }
}
