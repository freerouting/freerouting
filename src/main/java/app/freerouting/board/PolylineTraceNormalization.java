package app.freerouting.board;

import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.logger.FRLogger;
import java.util.Collection;

/**
 * Normalizes a {@link PolylineTrace} after board edits.
 *
 * <p>The collaborator owns only the recursive split/combine normalization walk. The trace remains
 * the public façade and continues to own the geometry and board-facing mutation operations.
 */
final class PolylineTraceNormalization {

  private static final int MAX_NORMALIZATION_DEPTH = 16;

  private PolylineTraceNormalization() {}

  static boolean normalize(PolylineTrace trace, IntOctagon clipShape) {
    return normalize(trace, clipShape, 0);
  }

  private static boolean normalize(
      PolylineTrace trace, IntOctagon clipShape, int normalizationDepth) {
    if (normalizationDepth > MAX_NORMALIZATION_DEPTH) {
      // Return false (no further change at this depth level) rather than throwing an exception.
      // The outer normalizeTraces() loop treats a false return as "nothing changed" for this
      // trace, which is safe: the trace geometry may be slightly sub-optimal (extra corners), but
      // it remains structurally valid, its endpoint contacts are preserved and removeTails() will
      // not touch it.
      FRLogger.debug(
          "PolylineTrace.normalize: max normalization depth ("
              + MAX_NORMALIZATION_DEPTH
              + ") reached for trace #"
              + trace.getId()
              + " on net "
              + (trace.netCount() > 0 ? trace.netNumbers[0] : -1)
              + " — stopping recursion, trace kept as-is.");
      return false;
    }

    boolean debugNet49 =
        trace.netNumbers != null
            && trace.netNumbers.length > 0
            && trace.netNumbers[0] == 49
            && normalizationDepth == 0;

    boolean observersActivated = false;
    BasicBoard routingBoard = trace.board;
    if (trace.board != null) {
      // Let the observers know the trace changes.
      observersActivated = !routingBoard.observersActive();
      if (observersActivated) {
        routingBoard.startNotifyObservers();
      }
    }
    Collection<PolylineTrace> splitPieces = trace.split(clipShape);
    boolean result = splitPieces.size() != 1;
    if (debugNet49) {
      FRLogger.trace(
          "compare_trace_normalize_net49 depth="
              + normalizationDepth
              + ", thisId="
              + trace.getId()
              + ", thisOnBoard="
              + trace.isOnTheBoard()
              + ", thisFirst="
              + trace.firstCorner()
              + ", thisLast="
              + trace.lastCorner()
              + ", splitPieces="
              + splitPieces.size());
      for (PolylineTrace piece : splitPieces) {
        FRLogger.trace(
            "compare_trace_normalize_net49  piece id="
                + piece.getId()
                + ", onBoard="
                + piece.isOnTheBoard()
                + ", first="
                + piece.firstCorner()
                + ", last="
                + piece.lastCorner());
      }
    }
    for (PolylineTrace currentSplitTrace : splitPieces) {
      if (currentSplitTrace.isOnTheBoard()) {
        boolean traceCombined = currentSplitTrace.combine();
        if (debugNet49) {
          FRLogger.trace(
              "compare_trace_normalize_net49  after_combine id="
                  + currentSplitTrace.getId()
                  + ", onBoard="
                  + currentSplitTrace.isOnTheBoard()
                  + ", combined="
                  + traceCombined
                  + ", first="
                  + (currentSplitTrace.isOnTheBoard()
                      ? currentSplitTrace.firstCorner()
                      : "N/A")
                  + ", last="
                  + (currentSplitTrace.isOnTheBoard()
                      ? currentSplitTrace.lastCorner()
                      : "N/A"));
        }
        if (currentSplitTrace.cornerCount() == 2
            && currentSplitTrace.firstCorner().equals(currentSplitTrace.lastCorner())) {
          // remove trace with only 1 corner — only if deletion is allowed.
          // USER_FIXED traces cannot be removed; skipping silently prevents an infinite loop in
          // normalizeTraces (where 'result=true' would cause the outer while to spin forever).
          if (!currentSplitTrace.isDeletionForbidden()) {
            trace.board.removeItem(currentSplitTrace);
            result = true;
          } else {
            int netNumber =
                currentSplitTrace.netCount() > 0 ? currentSplitTrace.netNumbers[0] : -1;
            // A degenerate user-fixed trace (first==last corner) cannot be removed because
            // deletion is forbidden. This is expected for boards with malformed DSN geometry
            // (e.g. bad EDA export). The user was already warned at load time; suppress here.
            FRLogger.debug(
                "PolylineTrace.normalize: skipping removal of degenerate user-fixed trace #"
                    + currentSplitTrace.getId()
                    + " on net "
                    + netNumber
                    + " (first==last corner)");
          }
        } else if (traceCombined) {
          normalize(currentSplitTrace, clipShape, normalizationDepth + 1);
          result = true;
        }
      }
    }
    if (observersActivated) {
      routingBoard.endNotifyObservers();
    }
    return result;
  }
}
