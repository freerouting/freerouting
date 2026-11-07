package app.freerouting.board.facade;

import app.freerouting.board.model.items.Item;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.drc.ClearanceBaseline;
import app.freerouting.drc.NetRoutingLedger;
import app.freerouting.geometry.planar.PolygonShape;
import app.freerouting.geometry.planar.PolylineArea;
import app.freerouting.logger.FRLogger;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/** Owns routing-board undo/redo side effects and deep-copy restoration. */
public final class RoutingBoardUndoFacade {

  private final RoutingBoard board;

  RoutingBoardUndoFacade(RoutingBoard board) {
    this.board = board;
  }

  boolean undo(Set<Integer> changedNets) {
    board.components.undo(board.communication.observers);
    Collection<UndoableObjects.Storable> cancelledObjects = new LinkedList<>();
    Collection<UndoableObjects.Storable> restoredObjects = new LinkedList<>();
    boolean result = board.itemList.undo(cancelledObjects, restoredObjects);
    applyUndoRedoSideEffects(cancelledObjects, restoredObjects, changedNets);
    return result;
  }

  boolean redo(Set<Integer> changedNets) {
    board.components.redo(board.communication.observers);
    Collection<UndoableObjects.Storable> cancelledObjects = new LinkedList<>();
    Collection<UndoableObjects.Storable> restoredObjects = new LinkedList<>();
    boolean result = board.itemList.redo(cancelledObjects, restoredObjects);
    applyUndoRedoSideEffects(cancelledObjects, restoredObjects, changedNets);
    return result;
  }

  /**
   * Copies the board through serialization. Only writing the board reads it, so only that part
   * holds the board's lock: copies made at the same time, by the optimizer threads, read their own
   * bytes back in parallel, which is most of the work (the search trees are rebuilt, planes split
   * into convex tiles again).
   */
  RoutingBoard deepCopy() {
    byte[] serializedBoard;
    ClearanceBaseline clearanceBaseline;
    NetRoutingLedger.Counts ledgerCounts;
    // The areas and polygons in the order they are written, to give their copies their convex
    // pieces back.
    List<PolylineArea> writtenAreas = new ArrayList<>();
    List<PolygonShape> writtenPolygons = new ArrayList<>();
    synchronized (board) {
      try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
          ObjectOutputStream outputStream =
              new AreaRecordingOutputStream(byteArrayOutputStream, writtenAreas, writtenPolygons)) {
        outputStream.writeObject(board);
        outputStream.flush();
        serializedBoard = byteArrayOutputStream.toByteArray();
        clearanceBaseline = board.clearanceBaseline;
        // Taken with the board written: the copy has the same items, so the same counts.
        ledgerCounts = board.routingLedger().counts();
      } catch (Exception exception) {
        FRLogger.error("Exception in deep_copy_routing_board" + exception, exception);
        return null;
      }
    }
    try (ObjectInputStream inputStream =
        new AreaRestoringInputStream(
            new ByteArrayInputStream(serializedBoard), writtenAreas, writtenPolygons)) {
      RoutingBoard boardCopy = (RoutingBoard) inputStream.readObject();
      // Transient, so not serialized: without it every pre-existing violation counts as new.
      boardCopy.clearanceBaseline = clearanceBaseline;
      boardCopy.clearAllItemTemporaryAutorouteData();
      boardCopy.finishAutoroute();
      // The copy has the same incomplete counts: no need to count every net again.
      boardCopy.routingLedger().seed(ledgerCounts);
      return boardCopy;
    } catch (Exception exception) {
      FRLogger.error("Exception in deep_copy_routing_board" + exception, exception);
      return null;
    }
  }

  /** Records the areas and polygons written, in order. */
  private static final class AreaRecordingOutputStream extends ObjectOutputStream {
    private final List<PolylineArea> writtenAreas;
    private final List<PolygonShape> writtenPolygons;

    AreaRecordingOutputStream(
        OutputStream out, List<PolylineArea> writtenAreas, List<PolygonShape> writtenPolygons)
        throws IOException {
      super(out);
      this.writtenAreas = writtenAreas;
      this.writtenPolygons = writtenPolygons;
      enableReplaceObject(true);
    }

    @Override
    protected Object replaceObject(Object obj) {
      if (obj instanceof PolylineArea area) {
        writtenAreas.add(area);
      } else if (obj instanceof PolygonShape polygon) {
        writtenPolygons.add(polygon);
      }
      return obj;
    }
  }

  /**
   * Gives each area or polygon read back the convex pieces of the one written in the same position
   * among its kind, if both have the same geometry: the planes of a copy are not split into tiles
   * again. An area contains no area and a polygon no polygon, so each kind is read back in the
   * order it was written.
   */
  private static final class AreaRestoringInputStream
      extends app.freerouting.util.SafeObjectInputStream {
    private final List<PolylineArea> writtenAreas;
    private final List<PolygonShape> writtenPolygons;
    private int readAreaCount;
    private int readPolygonCount;

    AreaRestoringInputStream(
        InputStream in, List<PolylineArea> writtenAreas, List<PolygonShape> writtenPolygons)
        throws IOException {
      super(in);
      this.writtenAreas = writtenAreas;
      this.writtenPolygons = writtenPolygons;
      enableResolveObject(true);
    }

    @Override
    protected Object resolveObject(Object obj) {
      if (obj instanceof PolylineArea area && readAreaCount < writtenAreas.size()) {
        area.reuseConvexPiecesOf(writtenAreas.get(readAreaCount++));
      } else if (obj instanceof PolygonShape polygon && readPolygonCount < writtenPolygons.size()) {
        polygon.reuseConvexPiecesOf(writtenPolygons.get(readPolygonCount++));
      }
      return obj;
    }
  }

  private void applyUndoRedoSideEffects(
      Collection<UndoableObjects.Storable> cancelledObjects,
      Collection<UndoableObjects.Storable> restoredObjects,
      Set<Integer> changedNets) {
    for (UndoableObjects.Storable storable : cancelledObjects) {
      Item currentItem = (Item) storable;
      board.searchTreeManager.remove(currentItem);
      board.routingLedger().noteRemoved(currentItem);
      if (board.communication != null && board.communication.observers != null) {
        board.communication.observers.notifyDeleted(currentItem);
      }
      if (changedNets != null) {
        for (int i = 0; i < currentItem.netCount(); i++) {
          changedNets.add(currentItem.getNetNumber(i));
        }
      }
    }
    for (UndoableObjects.Storable storable : restoredObjects) {
      Item currentItem = (Item) storable;
      currentItem.board = board;
      board.searchTreeManager.insert(currentItem);
      board.routingLedger().noteInserted(currentItem);
      currentItem.clearAutorouteInfo();
      if (board.communication != null && board.communication.observers != null) {
        board.communication.observers.notifyNew(currentItem);
      }
      if (changedNets != null) {
        for (int i = 0; i < currentItem.netCount(); i++) {
          changedNets.add(currentItem.getNetNumber(i));
        }
      }
    }
  }
}
