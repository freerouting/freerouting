package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
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
}
