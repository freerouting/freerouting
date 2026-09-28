package app.freerouting.board.trace;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.searchtree.ShapeSearchTree;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.TileShape;

/**
 * Keeps {@link PolylineTrace} geometry changes synchronized with every active search tree.
 *
 * <p>The adapter is intentionally stateless. Search-tree ownership remains with {@link BasicBoard},
 * and all operations retain the existing manager call order.
 */
final class PolylineTraceSearchTreeAdapter {

  private PolylineTraceSearchTreeAdapter() {}

  static TileShape[] calculateTreeShapes(PolylineTrace trace, ShapeSearchTree searchTree) {
    return searchTree.calculateTreeShapes(trace);
  }

  static boolean hasDefaultEntries(PolylineTrace first, PolylineTrace second) {
    ShapeSearchTree defaultTree = first.board.searchTreeManager.getDefaultTree();
    return first.getSearchTreeEntries(defaultTree) != null
        && second.getSearchTreeEntries(defaultTree) != null;
  }

  /**
   * Replaces the trace geometry through the safe full-remove/reinsert path.
   *
   * <p>This order is significant: the old entries are removed before the geometry and derived data
   * are changed, and the new geometry is inserted only after both changes are complete.
   */
  static void replaceGeometry(PolylineTrace trace, Polyline newPolyline) {
    trace.board.searchTreeManager.remove(trace);
    trace.clearSearchTreeEntries();
    trace.setPolyline(newPolyline);
    trace.clearDerivedData();
    trace.board.searchTreeManager.insert(trace);
  }

  static void mergeEntriesInFront(
      PolylineTrace fromTrace,
      PolylineTrace toTrace,
      Polyline joinedPolyline,
      int fromEntryNo,
      int toEntryNo) {
    toTrace.board.searchTreeManager.mergeEntriesInFront(
        fromTrace, toTrace, joinedPolyline, fromEntryNo, toEntryNo);
  }

  static void mergeEntriesAtEnd(
      PolylineTrace fromTrace,
      PolylineTrace toTrace,
      Polyline joinedPolyline,
      int fromEntryNo,
      int toEntryNo) {
    toTrace.board.searchTreeManager.mergeEntriesAtEnd(
        fromTrace, toTrace, joinedPolyline, fromEntryNo, toEntryNo);
  }

  static void changeEntries(
      PolylineTrace trace, Polyline newPolyline, int keepAtStartCount, int keepAtEndCount) {
    trace.board.searchTreeManager.changeEntries(
        trace, newPolyline, keepAtStartCount, keepAtEndCount);
  }
}
