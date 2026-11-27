package app.freerouting.board.facade;

import app.freerouting.autoroute.plane.FastPlaneConnectivityGrid;
import app.freerouting.board.model.items.ConductionArea;
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
    CopySource source = snapshot();
    return source == null ? null : restore(source);
  }

  /**
   * The board written once, from which any number of copies can be read back, also by several
   * threads at the same time. It holds references to the written board's areas, polygons and
   * planes, so it is only valid while that board does not change.
   */
  public static final class CopySource {
    private final byte[] serializedBoard;
    private final ClearanceBaseline clearanceBaseline;
    private final NetRoutingLedger.Counts ledgerCounts;
    // The areas and polygons in the order they are written, to give their copies their convex
    // pieces back.
    private final List<PolylineArea> writtenAreas;
    private final List<PolygonShape> writtenPolygons;
    // The conduction areas in the order they are written, and what their copies may reuse of their
    // fracture analysis. A detached memo is only read when a copy attaches it.
    private final List<ConductionArea> writtenPlanes;
    private final List<FastPlaneConnectivityGrid.FractureMemo> planeMemos;

    private CopySource(
        byte[] serializedBoard,
        ClearanceBaseline clearanceBaseline,
        NetRoutingLedger.Counts ledgerCounts,
        List<PolylineArea> writtenAreas,
        List<PolygonShape> writtenPolygons,
        List<ConductionArea> writtenPlanes,
        List<FastPlaneConnectivityGrid.FractureMemo> planeMemos) {
      this.serializedBoard = serializedBoard;
      this.clearanceBaseline = clearanceBaseline;
      this.ledgerCounts = ledgerCounts;
      this.writtenAreas = List.copyOf(writtenAreas);
      this.writtenPolygons = List.copyOf(writtenPolygons);
      this.writtenPlanes = List.copyOf(writtenPlanes);
      // May hold nulls (a plane without a memo), which List.copyOf rejects.
      this.planeMemos = java.util.Collections.unmodifiableList(new ArrayList<>(planeMemos));
    }
  }

  /** Writes the board, under its lock, into a source for copies; null if it cannot be written. */
  CopySource snapshot() {
    List<PolylineArea> writtenAreas = new ArrayList<>();
    List<PolygonShape> writtenPolygons = new ArrayList<>();
    List<ConductionArea> writtenPlanes = new ArrayList<>();
    List<FastPlaneConnectivityGrid.FractureMemo> planeMemos = new ArrayList<>();
    synchronized (board) {
      try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
          ObjectOutputStream outputStream =
              new AreaRecordingOutputStream(
                  byteArrayOutputStream, writtenAreas, writtenPolygons, writtenPlanes)) {
        outputStream.writeObject(board);
        outputStream.flush();
        byte[] serializedBoard = byteArrayOutputStream.toByteArray();
        for (ConductionArea plane : writtenPlanes) {
          planeMemos.add(plane.fractureMemoForCopy());
        }
        // Taken with the board written: the copy has the same items, so the same counts.
        return new CopySource(
            serializedBoard,
            board.clearanceBaseline,
            board.routingLedger().counts(),
            writtenAreas,
            writtenPolygons,
            writtenPlanes,
            planeMemos);
      } catch (Exception exception) {
        FRLogger.error("Exception in deep_copy_routing_board" + exception, exception);
        return null;
      }
    }
  }

  /** Reads a new copy of the board back from {@code source}; null if it cannot be read. */
  static RoutingBoard restore(CopySource source) {
    List<ConductionArea> readPlanes = new ArrayList<>();
    try (ObjectInputStream inputStream =
        new AreaRestoringInputStream(
            new ByteArrayInputStream(source.serializedBoard),
            source.writtenAreas,
            source.writtenPolygons,
            readPlanes)) {
      RoutingBoard boardCopy = (RoutingBoard) inputStream.readObject();
      if (readPlanes.size() == source.writtenPlanes.size()) {
        for (int i = 0; i < readPlanes.size(); i++) {
          if (readPlanes.get(i).getId() == source.writtenPlanes.get(i).getId()) {
            readPlanes.get(i).adoptFractureMemo(source.planeMemos.get(i));
          }
        }
      }
      // Transient, so not serialized: without it every pre-existing violation counts as new.
      boardCopy.clearanceBaseline = source.clearanceBaseline;
      boardCopy.clearAllItemTemporaryAutorouteData();
      boardCopy.finishAutoroute();
      // The copy has the same incomplete counts: no need to count every net again.
      boardCopy.routingLedger().seed(source.ledgerCounts);
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
    private final List<ConductionArea> writtenPlanes;

    AreaRecordingOutputStream(
        OutputStream out,
        List<PolylineArea> writtenAreas,
        List<PolygonShape> writtenPolygons,
        List<ConductionArea> writtenPlanes)
        throws IOException {
      super(out);
      this.writtenAreas = writtenAreas;
      this.writtenPolygons = writtenPolygons;
      this.writtenPlanes = writtenPlanes;
      enableReplaceObject(true);
    }

    @Override
    protected Object replaceObject(Object obj) {
      if (obj instanceof PolylineArea area) {
        writtenAreas.add(area);
      } else if (obj instanceof PolygonShape polygon) {
        writtenPolygons.add(polygon);
      } else if (obj instanceof ConductionArea plane) {
        writtenPlanes.add(plane);
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
    private final List<ConductionArea> readPlanes;
    private int readAreaCount;
    private int readPolygonCount;

    AreaRestoringInputStream(
        InputStream in,
        List<PolylineArea> writtenAreas,
        List<PolygonShape> writtenPolygons,
        List<ConductionArea> readPlanes)
        throws IOException {
      super(in);
      this.writtenAreas = writtenAreas;
      this.writtenPolygons = writtenPolygons;
      this.readPlanes = readPlanes;
      enableResolveObject(true);
    }

    @Override
    protected Object resolveObject(Object obj) {
      if (obj instanceof PolylineArea area && readAreaCount < writtenAreas.size()) {
        area.reuseConvexPiecesOf(writtenAreas.get(readAreaCount++));
      } else if (obj instanceof PolygonShape polygon && readPolygonCount < writtenPolygons.size()) {
        polygon.reuseConvexPiecesOf(writtenPolygons.get(readPolygonCount++));
      } else if (obj instanceof ConductionArea plane) {
        readPlanes.add(plane);
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
      board.incrementRevision(currentItem);
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
      board.incrementRevision(currentItem);
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
