package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.freerouting.board.model.items.Trace;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import java.util.List;
import org.junit.jupiter.api.Test;

class FanoutSliceAndLastMileTest {

  @Test
  void airlineCorridorCoversBothEndsAndAMargin() {
    IntBox corridor = LastMileBlockerRipup.corridor(new FloatPoint(0, 0), new FloatPoint(400, 100));

    assertTrue(corridor.ll.x < 0);
    assertTrue(corridor.ur.x > 400);
    assertTrue(corridor.ll.y < 0);
    assertTrue(corridor.ur.y > 100);
    assertEquals(100, corridor.ur.x - 400);
  }

  @Test
  void blockerRipupExcludesNetsKeptAwayByMemory() {
    RipupMemory memory = new RipupMemory();
    memory.recordRipupOfNets(1, List.of(2), 1);
    memory.recordRipupOfNets(1, List.of(2), 2);

    Trace traceNet1 = mock(Trace.class);
    when(traceNet1.netCount()).thenReturn(1);
    when(traceNet1.getNetNumber(0)).thenReturn(1);

    Trace traceNet3 = mock(Trace.class);
    when(traceNet3.netCount()).thenReturn(1);
    when(traceNet3.getNetNumber(0)).thenReturn(3);

    // For an airline of net 2 (which is kept away from net 1):
    // Net 1 is protected and must not be removed as a blocker
    assertFalse(LastMileBlockerRipup.isRemovableBlocker(traceNet1, 2, memory));

    // Net 3 is not protected and can be removed
    assertTrue(LastMileBlockerRipup.isRemovableBlocker(traceNet3, 2, memory));

    // Without memory, net 1 is removable as a blocker
    assertTrue(LastMileBlockerRipup.isRemovableBlocker(traceNet1, 2, null));
  }
}
