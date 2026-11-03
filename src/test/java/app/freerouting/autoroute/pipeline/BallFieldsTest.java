package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import app.freerouting.geometry.planar.FloatPoint;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BallFieldsTest {

  private static List<FloatPoint> grid(
      int columns,
      int rows,
      double pitch,
      double x0,
      double y0,
      BiPredicate<Integer, Integer> keep) {
    List<FloatPoint> centres = new ArrayList<>();
    for (int c = 0; c < columns; c++) {
      for (int r = 0; r < rows; r++) {
        if (keep == null || keep.test(c, r)) {
          centres.add(new FloatPoint(x0 + c * pitch, y0 + r * pitch));
        }
      }
    }
    return centres;
  }

  @Test
  void anAreaArrayIsAFieldGrownByOnePitch() {
    BallFields.Field field = BallFields.fieldOf(grid(6, 8, 7500, 1000, 2000, null), 0);
    assertNotNull(field);
    assertEquals(
        new BallFields.Field(
            0, 1000 - 7500, 2000 - 7500, 1000 + 5 * 7500 + 7500, 2000 + 7 * 7500 + 7500),
        field);
  }

  @Test
  void anArrayWithDepopulatedBallsIsAField() {
    BiPredicate<Integer, Integer> kept =
        (c, r) -> !(c >= 8 && c < 12 && r >= 8 && r < 12) && !(c == 0 && r == 0);
    assertNotNull(BallFields.fieldOf(grid(20, 20, 8000, 0, 0, kept), 3));
  }

  @Test
  void aPerimeterPartIsNotAField() {
    BiPredicate<Integer, Integer> edge = (c, r) -> (c == 0 || c == 17) != (r == 0 || r == 17);
    List<FloatPoint> qfp = grid(18, 18, 5000, 0, 0, edge);
    assertEquals(64, qfp.size());
    assertNull(BallFields.fieldOf(qfp, 0));
  }

  @Test
  void aSmallArrayOrASingleRowIsNotAField() {
    assertNull(BallFields.fieldOf(grid(3, 5, 5000, 0, 0, null), 0));
    assertNull(BallFields.fieldOf(grid(2, 12, 5000, 0, 0, null), 0));
    assertNull(BallFields.fieldOf(grid(20, 1, 5000, 0, 0, null), 0));
  }

  @ParameterizedTest
  @CsvSource({
    "-10, 5, 30, 5, 10",
    "5, 5, 6, 5, 1",
    "-10, -10, -5, 30, 0",
    "5, -10, 5, 5, 5",
    "0, 0, 10, 10, 14.142135623730951"
  })
  void aTracePaysForItsLengthInsideTheField(
      double x0, double y0, double x1, double y1, double inside) {
    BallFields.Field field = new BallFields.Field(1, 0, 0, 10, 10);
    assertEquals(inside, BallFields.insideLength(field, x0, y0, x1, y1), 1e-9);
    BallFields fields = new BallFields(List.of(field), 2.5);
    assertEquals(2.5 * inside, fields.pathCost(1, x0, y0, x1, y1), 1e-9);
    assertEquals(0.0, fields.pathCost(0, x0, y0, x1, y1), 1e-9);
  }
}
