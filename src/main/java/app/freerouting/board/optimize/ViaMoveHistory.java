package app.freerouting.board.optimize;

import app.freerouting.board.model.items.Via;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The locations each via has taken during one fixpoint loop of {@link
 * TraceTightener#optChangedArea}, used to stop a via that moves back and forth.
 *
 * <p>A move "returns" when it brings the via back within {@code accuracy} of a location it has
 * already held in this loop, other than the one it leaves. After two returns the via is proven to
 * cycle (for example A to B, back to A, then to B again): from then on a move to a location it has
 * already held is refused, and the via stays where it is. As long as no via returns twice, the
 * history refuses nothing, and the optimizer behaves exactly as without it.
 */
final class ViaMoveHistory {

  /** Number of returns that proves a cycle. */
  private static final int RETURNS_FOR_CYCLE = 2;

  private final Map<Integer, Track> tracks = new HashMap<>();

  /**
   * Returns false if moving the via from {@code from} to {@code to} would continue a proven cycle,
   * that is if the via has already returned twice and {@code to} is a location it has held.
   */
  boolean allowsMove(Via via, Point from, Point to, int accuracy) {
    Track track = tracks.get(via.getId());
    if (track == null || track.returns + 1 < RETURNS_FOR_CYCLE) {
      return true;
    }
    return !track.isReturn(from.toFloat(), to.toFloat(), accuracy);
  }

  /** Records that the via has moved from {@code from} to {@code to}. */
  void recordMove(Via via, Point from, Point to, int accuracy) {
    Track track = tracks.computeIfAbsent(via.getId(), id -> new Track());
    FloatPoint fromPoint = from.toFloat();
    FloatPoint toPoint = to.toFloat();
    if (track.locations.isEmpty()) {
      track.locations.add(fromPoint);
    }
    if (track.isReturn(fromPoint, toPoint, accuracy)) {
      ++track.returns;
    }
    track.locations.add(toPoint);
  }

  private static final class Track {
    private final List<FloatPoint> locations = new ArrayList<>();
    private int returns;

    /**
     * A move returns if it is longer than {@code accuracy} and ends within {@code accuracy} of a
     * location held before, other than {@code from}. Short moves never count: a via that creeps
     * along in small steps is not cycling.
     */
    private boolean isReturn(FloatPoint from, FloatPoint to, int accuracy) {
      if (to.distance(from) <= accuracy) {
        return false;
      }
      for (FloatPoint location : locations) {
        if (location.distance(to) <= accuracy && location.distance(from) > accuracy) {
          return true;
        }
      }
      return false;
    }
  }
}
