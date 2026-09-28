package app.freerouting.autoroute.pipeline;

import app.freerouting.autoroute.AutorouteAttemptResult;
import app.freerouting.autoroute.AutorouteAttemptState;
import app.freerouting.geometry.planar.IntBox;

/**
 * Decides whether a maze searched on a board snapshot may replace the live board.
 *
 * <p>The prepared board is a full copy from before the earlier items in the window were committed.
 * Adopting it drops those commits, so it is allowed only when they changed no geometry. A non-empty
 * committed box blocks adoption even when it misses the prepared corridor: the prepared board does
 * not contain the earlier copper, and trace objects cannot be moved onto the live board. {@link
 * #corridorTouched} is the stricter overlap test a later transplant can use.
 */
final class SnapshotCommitGate {

  private SnapshotCommitGate() {}

  static boolean adoptPreparedBoard(IntBox committedChanges) {
    return committedChanges == null || committedChanges.isEmpty();
  }

  static boolean corridorTouched(IntBox committedChanges, IntBox routeCorridor) {
    if (adoptPreparedBoard(committedChanges)) {
      return false;
    }
    if (routeCorridor == null || routeCorridor.isEmpty()) {
      return false;
    }
    return committedChanges.intersects(routeCorridor);
  }

  /** A skip whose corridor is empty did not change the occupancy the next search will see. */
  static boolean isUnchanged(AutorouteAttemptResult result, IntBox corridor) {
    if (corridor != null && !corridor.isEmpty()) {
      return false;
    }
    if (result == null) {
      return false;
    }
    AutorouteAttemptState state = result.state;
    return state == AutorouteAttemptState.ALREADY_CONNECTED
        || state == AutorouteAttemptState.NO_UNCONNECTED_NETS
        || state == AutorouteAttemptState.CONNECTED_TO_PLANE;
  }

  static IntBox union(IntBox current, IntBox addition) {
    if (addition == null || addition.isEmpty()) {
      return current == null ? IntBox.EMPTY : current;
    }
    if (current == null || current.isEmpty()) {
      return addition;
    }
    return current.union(addition);
  }
}
