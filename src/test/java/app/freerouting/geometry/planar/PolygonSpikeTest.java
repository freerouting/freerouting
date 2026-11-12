package app.freerouting.geometry.planar;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * A fill rounded to the resolution of the board can step back by a unit along its border: the
 * border turns by about 180 degrees at a corner that sticks out by less than a unit.
 */
class PolygonSpikeTest {

  @Test
  void aCornerSteppingBackByAUnitIsRemoved() {
    Point[] corners =
        Polygon.withoutSpikes(
            new Point[] {
              new IntPoint(0, 0),
              new IntPoint(500, 1),
              new IntPoint(499, 1),
              new IntPoint(1000, 0),
              new IntPoint(1000, 1000),
              new IntPoint(0, 1000),
              new IntPoint(0, 0)
            });

    assertArrayEquals(
        new Point[] {
          new IntPoint(0, 0),
          new IntPoint(499, 1),
          new IntPoint(1000, 0),
          new IntPoint(1000, 1000),
          new IntPoint(0, 1000),
          new IntPoint(0, 0)
        },
        corners);
    assertEquals(1, new Polygon(corners).windingNumberAfterClosing());
  }

  @Test
  void aSharpCornerIsKept() {
    Point[] corners = {
      new IntPoint(0, 0), new IntPoint(1000, 0), new IntPoint(0, 10), new IntPoint(0, 0)
    };

    assertSame(corners, Polygon.withoutSpikes(corners));
  }
}
