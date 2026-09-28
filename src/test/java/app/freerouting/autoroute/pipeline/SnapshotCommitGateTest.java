package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.AutorouteAttemptResult;
import app.freerouting.autoroute.AutorouteAttemptState;
import app.freerouting.geometry.planar.IntBox;
import org.junit.jupiter.api.Test;

class SnapshotCommitGateTest {

  @Test
  void adoptsOnlyWhenNothingWasCommitted() {
    assertTrue(SnapshotCommitGate.adoptPreparedBoard(null));
    assertTrue(SnapshotCommitGate.adoptPreparedBoard(IntBox.EMPTY));
    assertFalse(SnapshotCommitGate.adoptPreparedBoard(new IntBox(0, 0, 10, 10)));
  }

  @Test
  void corridorTouchFollowsTheBoxes() {
    IntBox committed = new IntBox(0, 0, 10, 10);
    assertFalse(SnapshotCommitGate.corridorTouched(IntBox.EMPTY, committed));
    assertFalse(SnapshotCommitGate.corridorTouched(committed, new IntBox(11, 0, 20, 10)));
    assertTrue(SnapshotCommitGate.corridorTouched(committed, new IntBox(10, 0, 20, 10)));
    assertFalse(SnapshotCommitGate.corridorTouched(committed, IntBox.EMPTY));
  }

  @Test
  void skipWithAnEmptyCorridorDoesNotBlockTheNextSearch() {
    AutorouteAttemptResult skip =
        new AutorouteAttemptResult(AutorouteAttemptState.ALREADY_CONNECTED);
    assertTrue(SnapshotCommitGate.isUnchanged(skip, IntBox.EMPTY));
    assertFalse(
        SnapshotCommitGate.isUnchanged(
            new AutorouteAttemptResult(AutorouteAttemptState.ROUTED), IntBox.EMPTY));
    assertFalse(SnapshotCommitGate.isUnchanged(skip, new IntBox(0, 0, 1, 1)));
  }

  @Test
  void unionIgnoresEmptyBoxes() {
    IntBox box = new IntBox(1, 2, 3, 4);
    IntBox union = SnapshotCommitGate.union(IntBox.EMPTY, box);
    assertEquals(box.ll.x, union.ll.x);
    assertEquals(box.ll.y, union.ll.y);
    assertEquals(box.ur.x, union.ur.x);
    assertEquals(box.ur.y, union.ur.y);
    IntBox reverse = SnapshotCommitGate.union(box, IntBox.EMPTY);
    assertEquals(box.ll.x, reverse.ll.x);
    assertEquals(box.ur.y, reverse.ur.y);
  }
}
