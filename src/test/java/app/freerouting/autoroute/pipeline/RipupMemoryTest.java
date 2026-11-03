package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Verifies how the rip-up memory waits for a repeated rip-up, keeps the moved net away, flips, and
 * finally lets go.
 */
class RipupMemoryTest {

  private static final int A = 3;
  private static final int B = 7;

  @Test
  void firstRipupIsOnlyRemembered() {
    RipupMemory memory = new RipupMemory();

    assertTrue(memory.recordRipupOfNets(A, List.of(B), 1).isEmpty());
    assertTrue(memory.recordRipupOfNets(A, List.of(B), 1).isEmpty());
    assertTrue(memory.recordRipupOfNets(B, List.of(A), 2).isEmpty());

    assertTrue(memory.netsNotToRip(B).isEmpty());
    assertTrue(memory.netsNotToRip(A).isEmpty());
    assertEquals(0, memory.activeCount());
  }

  @Test
  void repeatedRipupKeepsTheMovedNetAway() {
    RipupMemory memory = new RipupMemory();
    memory.recordRipupOfNets(A, List.of(B), 1);
    memory.recordRipupOfNets(B, List.of(A), 2);

    assertEquals(Set.of(B), memory.recordRipupOfNets(A, List.of(B, A, 0), 3));

    assertEquals(Set.of(A), memory.netsNotToRip(B));
    assertTrue(memory.isKeptAway(B));
    assertTrue(memory.netsNotToRip(A).isEmpty());
    assertFalse(memory.isKeptAway(A));
  }

  @Test
  void furtherRipupDoesNotStartASecondRelation() {
    RipupMemory memory = new RipupMemory();
    memory.recordRipupOfNets(A, List.of(B), 0);
    memory.recordRipupOfNets(A, List.of(B), 1);

    assertTrue(memory.recordRipupOfNets(A, List.of(B), 2).isEmpty());
    assertEquals(1, memory.activeCount());
  }

  @Test
  void routedNetEndsTheRelation() {
    RipupMemory memory = new RipupMemory();
    memory.recordRipupOfNets(A, List.of(B), 0);
    memory.recordRipupOfNets(A, List.of(B), 1);

    assertEquals(RipupMemory.Outcome.RESOLVED, memory.recordOutcome(B, A, true, 2));

    assertTrue(memory.netsNotToRip(B).isEmpty());
    assertEquals(0, memory.activeCount());
  }

  @Test
  void netThatKeepsFailingFlipsTheRelation() {
    RipupMemory memory = new RipupMemory();
    memory.recordRipupOfNets(A, List.of(B), 0);
    memory.recordRipupOfNets(A, List.of(B), 1);

    assertEquals(RipupMemory.Outcome.NONE, memory.recordOutcome(B, A, false, 2));
    assertEquals(Set.of(A), memory.netsNotToRip(B));
    assertEquals(RipupMemory.Outcome.FLIPPED, memory.recordOutcome(B, A, false, 3));

    assertTrue(memory.netsNotToRip(B).isEmpty());
    assertEquals(Set.of(B), memory.netsNotToRip(A));
  }

  @Test
  void flippedRelationThatFailsReleasesThePairForGood() {
    RipupMemory memory = new RipupMemory();
    memory.recordRipupOfNets(A, List.of(B), 0);
    memory.recordRipupOfNets(A, List.of(B), 1);
    memory.recordOutcome(B, A, false, 2);
    memory.recordOutcome(B, A, false, 3);

    memory.recordOutcome(A, B, false, 4);
    assertEquals(RipupMemory.Outcome.RETIRED, memory.recordOutcome(A, B, false, 5));

    assertEquals(0, memory.activeCount());
    assertTrue(memory.isRetired(A, B));
    assertTrue(memory.recordRipupOfNets(A, List.of(B), 6).isEmpty());
    assertTrue(memory.recordRipupOfNets(B, List.of(A), 6).isEmpty());
  }

  @Test
  void severalFailuresInOnePassCountOnce() {
    RipupMemory memory = new RipupMemory();
    memory.recordRipupOfNets(A, List.of(B), 0);
    memory.recordRipupOfNets(A, List.of(B), 1);

    memory.recordOutcome(B, A, false, 2);
    assertEquals(RipupMemory.Outcome.NONE, memory.recordOutcome(B, A, false, 2));

    assertEquals(Set.of(A), memory.netsNotToRip(B));
  }

  @Test
  void flippedPairIsNotRestartedByARipupWhileFlipped() {
    RipupMemory memory = new RipupMemory();
    memory.recordRipupOfNets(A, List.of(B), 0);
    memory.recordRipupOfNets(A, List.of(B), 1);
    memory.recordOutcome(B, A, false, 2);
    memory.recordOutcome(B, A, false, 3);

    assertTrue(memory.recordRipupOfNets(B, List.of(A), 3).isEmpty());
    assertEquals(Set.of(B), memory.netsNotToRip(A));
    assertEquals(1, memory.activeCount());
  }

  @Test
  void outcomeWithoutRelationChangesNothing() {
    RipupMemory memory = new RipupMemory();

    assertEquals(RipupMemory.Outcome.NONE, memory.recordOutcome(B, A, false, 1));
    assertEquals(0, memory.activeCount());
  }
}
