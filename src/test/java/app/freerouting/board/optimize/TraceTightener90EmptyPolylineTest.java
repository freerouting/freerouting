package app.freerouting.board.optimize;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Polyline;
import org.junit.jupiter.api.Test;

/**
 * A pull-tight round can collapse a polyline to no lines at all, for instance a small closed loop
 * that repositionLines turns into an empty polyline. The next round must leave it alone, as the
 * 45-degree tightener does, instead of reading its first line.
 */
class TraceTightener90EmptyPolylineTest {

  @Test
  void emptyPolylineIsReturnedUnchanged() {
    var tightener = new TraceTightener90(null, new int[0], null, 0, null, -1);
    Polyline empty = new Polyline(new Line[0]);

    Polyline result = tightener.pullTight(empty);

    assertEquals(0, result.lines.length);
  }
}
