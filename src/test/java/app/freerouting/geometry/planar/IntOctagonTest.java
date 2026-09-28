package app.freerouting.geometry.planar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class IntOctagonTest {

  @Test
  void normalizeReturnsSameInstanceWhenBoundsAreAlreadyTight() {
    IntOctagon octagon = new IntOctagon(0, 0, 10, 10, -10, 10, 0, 20);

    assertSame(octagon, octagon.normalize());
  }

  @Test
  void normalizeStillAllocatesWhenBoundsNeedTightening() {
    IntOctagon octagon = new IntOctagon(0, 0, 10, 20, -10, 10, 0, 20);

    IntOctagon normalized = octagon.normalize();

    assertNotSame(octagon, normalized);
    assertEquals(15, normalized.topY);
  }
}
