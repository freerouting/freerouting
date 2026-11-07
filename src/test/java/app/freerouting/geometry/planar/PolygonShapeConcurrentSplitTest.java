package app.freerouting.geometry.planar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * Polygons split at the same time from several threads give the pieces of a split on one thread.
 */
class PolygonShapeConcurrentSplitTest {

  private static final int THREADS = 8;
  private static final int SPLITS_PER_THREAD = 300;

  @Test
  void concurrentSplitsMatchTheSequentialSplit() throws Exception {
    List<Point[]> polygons = new ArrayList<>();
    for (int teeth = 5; teeth < 13; teeth++) {
      polygons.add(comb(teeth));
    }
    List<List<List<Line>>> expected = new ArrayList<>();
    for (Point[] corners : polygons) {
      expected.add(describe(new PolygonShape(corners).splitToConvex()));
    }
    assertTrue(expected.get(0).size() > 1, "the test polygons must be concave");

    ExecutorService executor = Executors.newFixedThreadPool(THREADS);
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (int t = 0; t < THREADS; t++) {
        final int offset = t;
        futures.add(
            executor.submit(
                () -> {
                  for (int i = 0; i < SPLITS_PER_THREAD; i++) {
                    int p = (i + offset) % polygons.size();
                    // A new shape each time: the pieces of a shape are cached once split.
                    assertEquals(
                        expected.get(p),
                        describe(new PolygonShape(polygons.get(p)).splitToConvex()),
                        "polygon " + p);
                  }
                  return null;
                }));
      }
      for (Future<?> future : futures) {
        future.get();
      }
    } finally {
      executor.shutdownNow();
    }
  }

  /** A comb: a concave polygon whose split depends on the corner it starts from. */
  private static Point[] comb(int teeth) {
    List<Point> corners = new ArrayList<>();
    corners.add(new IntPoint(0, 0));
    corners.add(new IntPoint(teeth * 200, 0));
    for (int i = teeth - 1; i >= 0; i--) {
      corners.add(new IntPoint(i * 200 + 200, 1000));
      corners.add(new IntPoint(i * 200 + 100, 1000));
      corners.add(new IntPoint(i * 200 + 100, 300 + 37 * i));
      corners.add(new IntPoint(i * 200, 300 + 37 * i));
    }
    return corners.toArray(new Point[0]);
  }

  private static List<List<Line>> describe(TileShape[] pieces) {
    List<List<Line>> result = new ArrayList<>();
    for (TileShape piece : pieces) {
      List<Line> lines = new ArrayList<>();
      for (int i = 0; i < piece.borderLineCount(); i++) {
        lines.add(piece.borderLine(i));
      }
      result.add(lines);
    }
    return result;
  }
}
