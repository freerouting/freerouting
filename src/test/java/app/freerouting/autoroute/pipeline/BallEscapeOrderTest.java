package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.freerouting.geometry.planar.FloatPoint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BallEscapeOrderTest {

  private static List<FloatPoint> grid(
      int columns, int rows, double pitch, BiPredicate<Integer, Integer> keep) {
    List<FloatPoint> centres = new ArrayList<>();
    for (int c = 0; c < columns; c++) {
      for (int r = 0; r < rows; r++) {
        if (keep == null || keep.test(c, r)) {
          centres.add(new FloatPoint(1000 + c * pitch, -2000 + r * pitch));
        }
      }
    }
    return centres;
  }

  @Test
  void aSixByEightGridHasThreeRings() {
    int[] rings = BallEscapeOrder.rings(grid(6, 8, 7500, null));
    assertEquals(48, rings.length);
    assertEquals(24, Arrays.stream(rings).filter(r -> r == 0).count());
    assertEquals(16, Arrays.stream(rings).filter(r -> r == 1).count());
    assertEquals(8, Arrays.stream(rings).filter(r -> r == 2).count());
  }

  @Test
  void aBallsRingIsItsDistanceToTheNearestOuterRowOrColumn() {
    int[] rings = BallEscapeOrder.rings(grid(6, 8, 7500, null));
    assertEquals(1, rings[1 * 8 + 3]);
    assertEquals(2, rings[2 * 8 + 4]);
    assertEquals(0, rings[0 * 8 + 0]);
    assertEquals(0, rings[3 * 8 + 7]);
  }

  @Test
  void aDepopulatedCentreKeepsTheRingsOfTheOuterGrid() {
    BiPredicate<Integer, Integer> kept = (c, r) -> !(c >= 3 && c < 7 && r >= 3 && r < 7);
    List<FloatPoint> grid = grid(10, 10, 8000, kept);
    int[] rings = BallEscapeOrder.rings(grid);
    assertEquals(84, rings.length);
    assertEquals(2, Arrays.stream(rings).max().orElse(0));
    assertEquals(36, Arrays.stream(rings).filter(r -> r == 0).count());
  }

  @ParameterizedTest
  @ValueSource(strings = {"on", "outer"})
  void anUnknownModeIsRefused(String mode) {
    assertThrows(IllegalArgumentException.class, () -> BallEscapeOrder.of(null, mode));
  }
}
