package app.freerouting.autoroute.decoupling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.io.specctra.DsnTestFixtures;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class DecouplingIdentifierTest {

  @Test
  void testNullBoardReturnsEmptyResult() {
    IdentificationResult result = DecouplingIdentifier.identify(null);
    assertNotNull(result);
    assertTrue(result.candidates().isEmpty());
    assertEquals(0, result.summary().components());
    assertEquals(0, result.summary().twoPinCandidates());
  }

  @Test
  void testGoldenFixtureIdentification() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    int pinsBefore = board.getPinCount();

    IdentificationResult result = DecouplingIdentifier.identify(board);

    assertNotNull(result);
    assertFalse(result.candidates().isEmpty(), "Expected candidates on golden fixture");

    DiagnosticsSummary summary = result.summary();
    assertEquals(result.candidates().size(), summary.twoPinCandidates());
    assertEquals(
        result.candidates().size(), summary.decoupling() + summary.review() + summary.rejected());

    // Verify board immutability (board state is untouched)
    assertEquals(pinsBefore, board.getPinCount(), "Pin count must not change");

    // Verify candidate properties
    for (CapacitorCandidate c : result.candidates()) {
      assertTrue(c.pinIdA() < c.pinIdB(), "pinIdA must be strictly less than pinIdB");
      assertTrue(c.score() >= 0 && c.score() <= 100, "Score must be clamped between 0 and 100");
      if (c.verdict() == Verdict.DECOUPLING) {
        assertTrue(c.score() >= 60, "Decoupling verdict must meet or exceed threshold");
        assertTrue(c.railNetNumber() > 0, "Decoupling candidate must have resolved rail");
        assertTrue(c.groundNetNumber() > 0, "Decoupling candidate must have resolved ground");
      }
    }
  }

  @Test
  void testDeterminism() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");

    IdentificationResult run1 = DecouplingIdentifier.identify(board);
    IdentificationResult run2 = DecouplingIdentifier.identify(board);

    assertEquals(run1.candidates().size(), run2.candidates().size());
    for (int i = 0; i < run1.candidates().size(); i++) {
      CapacitorCandidate c1 = run1.candidates().get(i);
      CapacitorCandidate c2 = run2.candidates().get(i);

      assertEquals(c1.componentId(), c2.componentId());
      assertEquals(c1.pinIdA(), c2.pinIdA());
      assertEquals(c1.pinIdB(), c2.pinIdB());
      assertEquals(c1.netNumberA(), c2.netNumberA());
      assertEquals(c1.netNumberB(), c2.netNumberB());
      assertEquals(c1.railNetNumber(), c2.railNetNumber());
      assertEquals(c1.groundNetNumber(), c2.groundNetNumber());
      assertEquals(c1.score(), c2.score());
      assertEquals(c1.verdict(), c2.verdict());
      assertEquals(c1.reasonMask(), c2.reasonMask());
      assertEquals(c1.audit(), c2.audit());
      assertEquals(c1.owners().size(), c2.owners().size());
    }
  }

  @Test
  void testPerformanceRequirement() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");

    // Warm up
    DecouplingIdentifier.identify(board);

    // Measure
    long start = System.nanoTime();
    IdentificationResult result = DecouplingIdentifier.identify(board);
    long durationMs = (System.nanoTime() - start) / 1_000_000L;

    assertTrue(durationMs < 50, "Execution time should be under 50 ms, was " + durationMs + " ms");
  }
}
