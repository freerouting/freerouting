package app.freerouting.autoroute.decoupling;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.searchtree.SearchTreeObject;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.Polyline;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Scoped, lightweight checkpoint of local trace topology and ID sequence state prior to a
 * decoupling routing attempt.
 *
 * <p>Resolves Issue #1054: when a new route connects into an existing trace on the same net,
 * Freerouting splits the pre-existing trace into child segments with newly generated IDs (&gt;
 * maxIdBefore) and removes the parent trace. Rolling back solely by deleting items with ID &gt;
 * maxIdBefore accidentally destroys the pre-existing trace.
 *
 * <p>This checkpoint snapshots only the local same-net traces intersecting the routing bounding
 * box. If a rollback is needed, newly generated items are safely removed and any split or deleted
 * original traces are restored without loss of geometry or decoupling flags.
 */
public final class DecouplingRouteCheckpoint {

  /** Snapshot of a single pre-existing trace's geometric and logical state. */
  public record TraceSnapshot(
      Polyline polyline,
      int layer,
      int halfWidth,
      int[] netNumbers,
      int clearanceClassIndex,
      FixedState fixedState,
      byte decouplingFlags,
      int componentId,
      int originalId) {}

  public final int maxIdBefore;
  public final IntBox searchBox;
  public final int layer;
  public final int netNo;
  public final List<TraceSnapshot> snapshots;

  private DecouplingRouteCheckpoint(
      int maxIdBefore, IntBox searchBox, int layer, int netNo, List<TraceSnapshot> snapshots) {
    this.maxIdBefore = maxIdBefore;
    this.searchBox = searchBox;
    this.layer = layer;
    this.netNo = netNo;
    this.snapshots = List.copyOf(snapshots);
  }

  /**
   * Captures a local checkpoint for the given search region, layer, and net.
   *
   * @param board routing board context
   * @param searchBox bounding box covering the candidate strap and pad vicinity
   * @param layer routing layer of the strap, or negative to inspect all layers
   * @param netNo net number of the supply rail
   * @return captured checkpoint
   */
  public static DecouplingRouteCheckpoint capture(
      RoutingBoard board, IntBox searchBox, int layer, int netNo) {
    int maxIdBefore = board.communication.idGenerator.maxGeneratedId();
    List<TraceSnapshot> snapshots = new ArrayList<>();

    Set<SearchTreeObject> objs =
        board.searchTreeManager.getDefaultTree().overlappingObjects(searchBox, layer);
    for (SearchTreeObject obj : objs) {
      if (obj instanceof PolylineTrace trace && trace.containsNet(netNo)) {
        int[] netNumbers = new int[trace.netCount()];
        for (int i = 0; i < trace.netCount(); i++) {
          netNumbers[i] = trace.getNetNumber(i);
        }
        snapshots.add(
            new TraceSnapshot(
                trace.polyline(),
                trace.getLayer(),
                trace.getHalfWidth(),
                netNumbers,
                trace.clearanceClassIndex(),
                trace.getFixedState(),
                trace.getDecouplingFlags(),
                trace.getComponentId(),
                trace.getId()));
      }
    }

    return new DecouplingRouteCheckpoint(maxIdBefore, searchBox, layer, netNo, snapshots);
  }

  /**
   * Rolls back the routing attempt, safely removing newly generated items and restoring any split
   * or deleted pre-existing traces.
   *
   * @param board routing board to restore
   */
  public void rollback(RoutingBoard board) {
    int currentMaxId = board.communication.idGenerator.maxGeneratedId();

    // 1. Remove all items generated since this checkpoint was captured
    for (int id = maxIdBefore + 1; id <= currentMaxId; id++) {
      Item item = board.getItem(id);
      if (item != null) {
        // Unlock and clear component affiliation so isDeletionForbidden() does not block removal
        item.setFixedState(FixedState.UNFIXED);
        item.assignComponentId(0);
        board.removeItem(item);
      }
    }

    // 2. Re-insert any original trace that was deleted or split
    for (TraceSnapshot snapshot : snapshots) {
      if (board.getItem(snapshot.originalId()) == null) {
        PolylineTrace restored =
            board.insertTraceWithoutCleaning(
                snapshot.polyline(),
                snapshot.layer(),
                snapshot.halfWidth(),
                snapshot.netNumbers(),
                snapshot.clearanceClassIndex(),
                snapshot.fixedState());
        if (restored != null) {
          restored.setDecouplingFlags(snapshot.decouplingFlags());
          if (snapshot.componentId() > 0) {
            restored.assignComponentId(snapshot.componentId());
          }
        }
      }
    }

    // 3. Clear transient autoroute state
    board.clearTransientAutorouteState();
  }
}
