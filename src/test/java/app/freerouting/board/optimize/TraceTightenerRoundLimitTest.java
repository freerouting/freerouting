package app.freerouting.board.optimize;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.geometry.planar.Polyline;
import org.junit.jupiter.api.Test;

/** The fixpoint loops of the pull-tight must end even without a time limit or a stop request. */
class TraceTightenerRoundLimitTest {

  @Test
  void loopsStopAfterMaxRoundsWithoutTimeLimit() {
    TraceTightener tightener = new NoOpTightener();

    assertFalse(tightener.mustStopAfter(TraceTightener.MAX_ROUNDS - 1));
    assertTrue(tightener.mustStopAfter(TraceTightener.MAX_ROUNDS));
  }

  private static final class NoOpTightener extends TraceTightener {
    NoOpTightener() {
      super(null, new int[0], null, 0, null, -1);
    }

    @Override
    public Polyline pullTight(Polyline polyline) {
      return polyline;
    }

    @Override
    Polyline smoothenStartCornerAtTrace(PolylineTrace trace) {
      return null;
    }

    @Override
    Polyline smoothenEndCornerAtTrace(PolylineTrace trace) {
      return null;
    }
  }
}
