package app.freerouting.board;

import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.LinkedList;

/** Auxiliary class used by the shove functions. */
public class ShapeTraceEntries {

  private static final double c_offset_add = 1;
  final Collection<Via> shoveViaList;
  private final TileShape shape;
  private final int layer;
  private final int[] ownNetNos;
  private final int clClass;
  private final RoutingBoard board;
  private ShapeEntrySide fromSide;
  private EntryPoint listAnchor;
  private int tracePieceCount;
  private int maxStackLevel;
  private boolean shapeContainsTraceTails;
  private Item foundObstacle;

  /**
   * Used for shoving traces and vias out of the input shape. fromSide.no is the side of shape, from
   * where the shove comes. if fromSide.no < 0, it will be calculated internally.
   */
  ShapeTraceEntries(
      TileShape shape,
      int layer,
      int[] ownNetNos,
      int clType,
      ShapeEntrySide fromSide,
      RoutingBoard board) {
    this.shape = shape;
    this.layer = layer;
    this.ownNetNos = ownNetNos;
    this.clClass = clType;
    this.fromSide = fromSide;
    this.board = board;
    listAnchor = null;
    tracePieceCount = 0;
    maxStackLevel = 0;
    shoveViaList = new LinkedList<>();
  }

  /** Cutout trace. */
  public static void cutoutTrace(PolylineTrace trace, ConvexShape shape, int clClass) {
    if (!trace.isOnTheBoard()) {
      FRLogger.warn("ShapeTraceEntries.cutout_trace : trace is deleted");
      return;
    }
    ConvexShape offsetShape;
    BasicBoard board = trace.board;
    ShapeSearchTree searchTree = board.searchTreeManager.getDefaultTree();
    if (searchTree.isClearanceCompensationUsed()) {
      double currentOffset = trace.getCompensatedHalfWidth(searchTree) + c_offset_add;
      offsetShape = shape.offset(currentOffset);
    } else {
      // enlarge the shape in 2 steps  for symmetry reasons
      double clOffset =
          board.clearanceValue(trace.clearanceClassIndex(), clClass, trace.getLayer())
              + c_offset_add;
      offsetShape = shape.offset(trace.getHalfWidth());
      offsetShape = offsetShape.offset(clOffset);
    }
    Polyline traceLines = trace.polyline();
    Polyline[] pieces = offsetShape.cutout(traceLines);
    if (pieces.length == 1 && pieces[0] == traceLines) {
      // nothing cut off
      return;
    }
    if (pieces.length == 2
        && offsetShape.isOutside(pieces[0].firstCorner())
        && offsetShape.isOutside(pieces[1].lastCorner())) {
      fastCutoutTrace(trace, pieces[0], pieces[1]);
    } else {
      board.removeItem(trace);
      for (int i = 0; i < pieces.length; i++) {
        board.insertTraceWithoutCleaning(
            pieces[i],
            trace.getLayer(),
            trace.getHalfWidth(),
            trace.netNumbers,
            trace.clearanceClassIndex(),
            FixedState.UNFIXED);
      }
    }
  }

  /** Optimized function handling the performance critical standard cutout case. */
  private static void fastCutoutTrace(PolylineTrace trace, Polyline startPiece, Polyline endPiece) {
    BasicBoard board = trace.board;
    board.additionalUpdateAfterChange(trace);
    board.itemList.saveForUndo(trace);
    PolylineTrace startTrace =
        new PolylineTrace(
            startPiece,
            trace.getLayer(),
            trace.getHalfWidth(),
            trace.netNumbers,
            trace.clearanceClassIndex(),
            0,
            0,
            FixedState.UNFIXED,
            board);
    startTrace.board = board;
    board.itemList.insert(startTrace);
    startTrace.setOnTheBoard(true);

    PolylineTrace endTrace =
        new PolylineTrace(
            endPiece,
            trace.getLayer(),
            trace.getHalfWidth(),
            trace.netNumbers,
            trace.clearanceClassIndex(),
            0,
            0,
            FixedState.UNFIXED,
            board);
    endTrace.board = board;
    board.itemList.insert(endTrace);
    endTrace.setOnTheBoard(true);

    board.searchTreeManager.reuseEntriesAfterCutout(trace, startTrace, endTrace);
    board.removeItem(trace);

    if ((board.communication != null) && (board.communication.observers != null)) {
      board.communication.observers.notifyNew(startTrace);
      board.communication.observers.notifyNew(endTrace);
    }
  }

  private static boolean netNosEqual(int[] netNos1, int[] netNos2) {
    if (netNos1.length != netNos2.length) {
      return false;
    }
    for (int currentNetNo1 : netNos1) {
      boolean netNoFound = false;
      for (int currentNetNo2 : netNos2) {
        if (currentNetNo1 == currentNetNo2) {
          netNoFound = true;
          break;
        }
      }
      if (!netNoFound) {
        return false;
      }
    }
    return true;
  }

  /**
   * Stores traces and vias in itemList. Returns false, if itemList contains obstacles, which cannot
   * be shoved aside. If isPadCheck. the check is for vias, otherwise it is for traces. If
   * copperSharingAllowed, overlaps with traces or pads of the own net are allowed.
   */
  boolean storeItems(Collection<Item> itemList, boolean isPadCheck, boolean copperSharingAllowed) {
    for (Item currentItem : itemList) {
      if (!isPadCheck && currentItem instanceof ViaObstacleArea
          || currentItem instanceof ComponentObstacleArea) {
        continue;
      }
      boolean containsOwnNet = currentItem.sharesNetNo(this.ownNetNos);
      if (currentItem instanceof ConductionArea area && (containsOwnNet || !area.getIsObstacle())) {
        continue;
      }
      if (currentItem.isShoveFixed() && !containsOwnNet) {
        this.foundObstacle = currentItem;
        return false;
      }
      if (currentItem instanceof Via via) {
        if (isPadCheck || !containsOwnNet) {
          shoveViaList.add(via);
        }
      } else if (currentItem instanceof PolylineTrace currentTrace) {

        if (!storeTrace(currentTrace)) {
          return false;
        }
      } else {
        if (containsOwnNet) {
          if (!copperSharingAllowed) {
            this.foundObstacle = currentItem;
            return false;
          }
          if (isPadCheck && !((currentItem instanceof Pin pin) && pin.drillAllowed())) {
            this.foundObstacle = currentItem;
            return false;
          }
        } else {
          this.foundObstacle = currentItem;
          return false;
        }
      }
    }
    searchFromSide();
    resort();
    return calculateStackLevels();
  }

  /**
   * Calculates the next substitute trace piece. Returns null at the end of the substitute trace.
   * list.
   */
  PolylineTrace nextSubstituteTracePiece() {

    EntryPoint[] entries = popPiece();
    if (entries == null) {
      return null;
    }
    PolylineTrace currentTrace = entries[0].trace;
    TileShape offsetShape;
    ShapeSearchTree searchTree = this.board.searchTreeManager.getDefaultTree();
    if (searchTree.isClearanceCompensationUsed()) {
      double currentOffset = currentTrace.getCompensatedHalfWidth(searchTree) + c_offset_add;
      offsetShape = (TileShape) shape.offset(currentOffset);
    } else {
      // enlarge the shape in 2 steps  for symmetry reasons
      offsetShape = (TileShape) shape.offset(currentTrace.getHalfWidth());
      double clOffset =
          board.clearanceValue(currentTrace.clearanceClassIndex(), clClass, layer) + c_offset_add;
      offsetShape = (TileShape) offsetShape.offset(clOffset);
    }
    int edgeCount = shape.borderLineCount();
    int edgeDiff = entries[1].edgeIndex - entries[0].edgeIndex;

    // calculate the polyline of the substitute trace

    Line[] pieceLines = new Line[edgeDiff + 3];
    // start with the intersecting line of the trace at the start entry.
    pieceLines[0] = entries[0].trace.polyline().lines[entries[0].traceLineNo];
    // end with the intersecting line of the trace at the end entry
    pieceLines[pieceLines.length - 1] = entries[1].trace.polyline().lines[entries[1].traceLineNo];
    // fill the interior lines of pieceLines with the appropriate edge
    // lines of the offset shape
    int currentEdgeNo = entries[0].edgeIndex % edgeCount;
    for (int i = 1; i < pieceLines.length - 1; i++) {
      pieceLines[i] = offsetShape.borderLine(currentEdgeNo);
      if (currentEdgeNo == edgeCount - 1) {
        currentEdgeNo = 0;
      } else {
        ++currentEdgeNo;
      }
    }
    Polyline piecePolyline = new Polyline(pieceLines);
    if (piecePolyline.isEmpty()) {
      // no valid trace piece, return the next one
      return nextSubstituteTracePiece();
    }
    return new PolylineTrace(
        piecePolyline,
        this.layer,
        currentTrace.getHalfWidth(),
        currentTrace.netNumbers,
        currentTrace.clearanceClassIndex(),
        0,
        0,
        FixedState.UNFIXED,
        this.board);
  }

  /** Returns the maximum recursion depth for shoving the obstacle traces. */
  int stackDepth() {
    return maxStackLevel;
  }

  /** Returns the number of substitute trace pieces. */
  int substituteTraceCount() {
    return tracePieceCount;
  }

  /**
   * Looks if an unconnected endpoint of a trace of a foreign net is contained in the interior of
   * the shape.
   */
  public boolean traceTailsInShape() {
    return this.shapeContainsTraceTails;
  }

  /**
   * Cuts out all traces in itemList out of the stored shape. Traces with net number exceptNetNo are
   * ignored
   */
  void cutoutTraces(Collection<Item> itemList) {
    for (Item currentItem : itemList) {
      if (currentItem instanceof PolylineTrace trace && !currentItem.sharesNetNo(this.ownNetNos)) {
        cutoutTrace(trace, this.shape, this.clClass);
      }
    }
  }

  /** Returns the item responsible for the failing, if the shove algorithm failed. */
  Item getFoundObstacle() {
    return this.foundObstacle;
  }

  /**
   * Stores all intersection points of trace with the border of the internal shape enlarged by the
   * half width and the clearance of the corresponding trace pen.
   */
  private boolean storeTrace(PolylineTrace trace) {
    ShapeSearchTree searchTree = this.board.searchTreeManager.getDefaultTree();
    TileShape offsetShape;
    if (searchTree.isClearanceCompensationUsed()) {
      double currentOffset = trace.getCompensatedHalfWidth(searchTree) + c_offset_add;
      offsetShape = (TileShape) shape.offset(currentOffset);
    } else {
      // enlarge the shape in 2 steps  for symmetry reasons
      double clOffset =
          board.clearanceValue(trace.clearanceClassIndex(), this.clClass, trace.getLayer())
              + c_offset_add;
      offsetShape = (TileShape) shape.offset(trace.getHalfWidth());
      offsetShape = (TileShape) offsetShape.offset(clOffset);
    }

    // using enlarge here instead offset causes problems because of a
    // comparison in the constructor of class EntryPoint
    int[][] entries = offsetShape.entrancePoints(trace.polyline());
    for (int i = 0; i < entries.length; i++) {
      int[] entryTuple = entries[i];
      FloatPoint entryApprox =
          trace
              .polyline()
              .lines[entryTuple[0]]
              .intersectionApprox(offsetShape.borderLine(entryTuple[1]));
      insertEntryPoint(trace, entryTuple[0], entryTuple[1], entryApprox);
    }

    // Look, if an end point of the trace lies in the interior of
    // the shape. This may be the case, if a via touches the shape

    if (!trace.sharesNetNo(ownNetNos)) {
      if (!trace.netsNormal()) {
        return false;
      }
      Point endCorner = trace.firstCorner();
      Collection<Item> contactList;
      for (int i = 0; i < 2; i++) {
        if (offsetShape.contains(endCorner)) {
          if (i == 0) {
            contactList = trace.getStartContacts();
          } else {
            contactList = trace.getEndContacts();
          }
          int contactCount = 0;
          boolean storeEndCorner = true;

          // check for contact object, which is not shovable
          for (Item contactItem : contactList) {
            if (!contactItem.isRoutable()) {
              this.foundObstacle = contactItem;
              return false;
            }
            if (contactItem instanceof Trace contactTrace) {

              if (contactItem.isShoveFixed()
                  || contactTrace.getHalfWidth() != trace.getHalfWidth()
                  || contactItem.clearanceClassIndex() != contactTrace.clearanceClassIndex()) {
                if (offsetShape.containsInside(endCorner)) {
                  this.foundObstacle = contactItem;
                  return false;
                }
              }
            } else if (contactItem instanceof Via via) {
              TileShape viaShape = via.getTileShapeOnLayer(layer);

              double viaTraceDiff =
                  viaShape.smallestRadius() - trace.getCompensatedHalfWidth(searchTree);
              if (!searchTree.isClearanceCompensationUsed()) {
                int viaClearance =
                    board.clearanceValue(
                        contactItem.clearanceClassIndex(), this.clClass, this.layer);
                int traceClearance =
                    board.clearanceValue(trace.clearanceClassIndex(), this.clClass, this.layer);
                if (traceClearance > viaClearance) {
                  viaTraceDiff += viaClearance - traceClearance;
                }
              }
              if (viaTraceDiff < 0) {
                // the via is smaller than the trace
                this.foundObstacle = contactItem;
                return false;
              }

              if (viaTraceDiff == 0 && !offsetShape.containsInside(endCorner)) {
                // the via need not to be shoved
                storeEndCorner = false;
              }
            }
            ++contactCount;
          }
          if (contactCount == 1 && storeEndCorner) {
            Point projection = offsetShape.nearestBorderPoint(endCorner);
            {
              int projectionSide = offsetShape.containsOnBorderLineNo(projection);
              int traceLineSegmentNo;
              // the following may not be correct because the trace may not contain a suitable
              // line for the construction of the end line of the substitute trace.
              if (i == 0) {
                traceLineSegmentNo = 0;
              } else {
                traceLineSegmentNo = trace.polyline().lines.length - 1;
              }

              if (projectionSide >= 0) {
                insertEntryPoint(trace, traceLineSegmentNo, projectionSide, projection.toFloat());
              }
            }
          } else if (contactCount == 0 && offsetShape.containsInside(endCorner)) {
            shapeContainsTraceTails = true;
          }
        }
        endCorner = trace.lastCorner();
      }
    }
    this.foundObstacle = trace;
    return true;
  }

  private void searchFromSide() {
    if (this.fromSide != null && this.fromSide.no >= 0) {
      return; // from side is already legal
    }
    EntryPoint currentNode = this.listAnchor;
    int currentFromsideNo = 0;
    FloatPoint currentEntryApprox = null;
    while (currentNode != null) {
      if (currentNode.trace.sharesNetNo(this.ownNetNos)) {
        currentFromsideNo = currentNode.edgeIndex;
        currentEntryApprox = currentNode.entryApprox;
        break;
      }
      currentNode = currentNode.next;
    }
    this.fromSide = new ShapeEntrySide(currentFromsideNo, currentEntryApprox);
  }

  /** Resorts the intersection points according to fromSideIndex and removes redundant points. */
  private void resort() {
    int edgeCount = this.shape.borderLineCount();
    if (this.fromSide.no < 0 || fromSide.no >= edgeCount) {
      FRLogger.warn("ShapeTraceEntries.resort: from side not calculated");
      return;
    }
    // resort the intersection points, so that they start in the
    // middle of fromSide.
    FloatPoint compareCorner1 = shape.cornerApprox(this.fromSide.no);
    FloatPoint compareCorner2;
    if (fromSide.no == edgeCount - 1) {
      compareCorner2 = shape.cornerApprox(0);
    } else {
      compareCorner2 = shape.cornerApprox(fromSide.no + 1);
    }
    double fromPointDist = 0;
    FloatPoint fromPointProjection = null;
    if (fromSide.borderIntersection != null) {
      fromPointProjection =
          fromSide.borderIntersection.projectionApprox(shape.borderLine(fromSide.no));
      fromPointDist = fromPointProjection.distanceSquare(compareCorner1);
      if (fromPointDist >= compareCorner1.distanceSquare(compareCorner2)) {
        fromSide = new ShapeEntrySide(fromSide.no, null);
      }
    }
    // search the first intersection point between the side middle
    // and compareCorner2
    EntryPoint current = listAnchor;
    EntryPoint prev = null;

    while (current != null) {
      if (current.edgeIndex > this.fromSide.no) {
        break;
      }
      if (current.edgeIndex == fromSide.no) {
        if (fromSide.borderIntersection != null) {
          FloatPoint currentProjection =
              current.entryApprox.projectionApprox(shape.borderLine(fromSide.no));
          if (currentProjection.distanceSquare(compareCorner1) >= fromPointDist
              && currentProjection.distanceSquare(fromPointProjection)
                  <= currentProjection.distanceSquare(compareCorner1)) {
            break;
          }
        } else {
          if (current.entryApprox.distanceSquare(compareCorner2)
              <= current.entryApprox.distanceSquare(compareCorner1)) {
            break;
          }
        }
      }
      prev = current;
      current = prev.next;
    }
    if (current != null && current != listAnchor) {
      rotateEntryListAroundAnchor(current, edgeCount);
    }
    // remove intersections between two other intersections of the same
    // connected set, so that only first and last intersection is kept.
    if (listAnchor == null) {
      return;
    }
    prev = listAnchor;
    int[] prevNetNos = prev.trace.netNumbers;

    current = listAnchor.next;
    int[] currentNetNumbers;
    EntryPoint next;

    if (current != null) {
      currentNetNumbers = current.trace.netNumbers;
      next = current.next;
    } else {
      next = null;
      currentNetNumbers = new int[0];
    }
    EntryPoint beforePrev = null;
    while (next != null) {
      int[] nextNetNos = next.trace.netNumbers;
      if (netNosEqual(prevNetNos, currentNetNumbers)
          && netNosEqual(currentNetNumbers, nextNetNos)) {
        prev.next = next;
      } else {
        beforePrev = prev;
        prev = current;
        prevNetNos = currentNetNumbers;
      }
      currentNetNumbers = nextNetNos;
      current = next;
      next = current.next;
    }

    // remove nodes of own net at start and end of the list
    if (current != null && netNosEqual(currentNetNumbers, ownNetNos)) {
      prev.next = null;
      if (netNosEqual(prevNetNos, ownNetNos)) {
        if (beforePrev != null) {
          beforePrev.next = null;
        } else {
          listAnchor = null;
        }
      }
    }

    if (listAnchor != null && listAnchor.trace.netsEqual(ownNetNos)) {
      listAnchor = listAnchor.next;

      if (listAnchor != null && listAnchor.trace.netsEqual(ownNetNos)) {
        listAnchor = listAnchor.next;
      }
    }
  }

  private boolean calculateStackLevels() {
    if (listAnchor == null) {
      return true;
    }
    EntryPoint currentEntry = listAnchor;
    int[] currentNetNumbers = currentEntry.trace.netNumbers;
    int currentLevel;
    if (netNosEqual(currentNetNumbers, this.ownNetNos)) {
      // ignore own net when calculating the stack level
      currentLevel = 0;
    } else {
      currentLevel = 1;
    }

    while (currentEntry != null) {
      if (currentEntry.stackLevel < 0) { // not yet calculated
        ++tracePieceCount;
        currentEntry.stackLevel = currentLevel;
        if (currentLevel > maxStackLevel) {
          if (maxStackLevel > 1) {
            this.foundObstacle = currentEntry.trace;
          }
          maxStackLevel = currentLevel;
        }
      }

      // set stack level for all entries of the current net;
      EntryPoint checkEntry = currentEntry.next;
      int indexOfNextForeignSet = 0;
      int indexOfLastOccurrenceOfSet = 0;
      int nextIndex = 0;
      EntryPoint lastOwnEntry = null;
      EntryPoint firstForeignEntry = null;

      while (checkEntry != null) {
        ++nextIndex;
        int[] checkNetNos = checkEntry.trace.netNumbers;
        if (netNosEqual(checkNetNos, currentNetNumbers)) {
          indexOfLastOccurrenceOfSet = nextIndex;
          lastOwnEntry = checkEntry;
          checkEntry.stackLevel = currentEntry.stackLevel;
        } else if (indexOfNextForeignSet == 0) {
          // first occurrence of a foreign connected set
          indexOfNextForeignSet = nextIndex;
          firstForeignEntry = checkEntry;
        }
        checkEntry = checkEntry.next;
      }
      EntryPoint nextEntry;

      if (nextIndex != 0) {
        if (indexOfNextForeignSet != 0 && indexOfNextForeignSet < indexOfLastOccurrenceOfSet) {
          // raise level
          nextEntry = firstForeignEntry;
          if (nextEntry.stackLevel >= 0) { // already calculated
            // stack property fails
            return false;
          }
          ++currentLevel;
        } else {
          if (indexOfLastOccurrenceOfSet != 0) {
            nextEntry = lastOwnEntry;
          } else {
            nextEntry = firstForeignEntry;
            if (nextEntry.stackLevel >= 0) { // already calculated
              --currentLevel;
              if (nextEntry.stackLevel != currentLevel) {
                return false;
              }
            }
          }
        }
        currentNetNumbers = nextEntry.trace.netNumbers;
        // remove all entries between currentEntry and nextEntry, because
        // they are irrelevant;
        checkEntry = currentEntry.next;
        while (checkEntry != nextEntry) {
          checkEntry = checkEntry.next;
        }
        currentEntry.next = nextEntry;
        currentEntry = nextEntry;
      } else {
        currentEntry = null;
      }
    }
    if (currentLevel != 1) {
      FRLogger.warn("ShapeTraceEntries.calculate_stack_levels: currentLevel inconsistent");
      return false;
    }
    return true;
  }

  /**
   * Pops the next piece with minimal level from the intersection list Returns null, if the stack is
   * empty. The returned array has 2 elements. The first is the first entry point, and the second is
   * the last entry point of the minimal level.
   */
  private EntryPoint[] popPiece() {
    if (listAnchor == null) {
      if (this.tracePieceCount != 0) {
        FRLogger.warn("ShapeTraceEntries: tracePieceCount is inconsistent");
      }
      return null;
    }
    EntryPoint first = listAnchor;
    EntryPoint prevFirst = null;

    while (first != null && first.stackLevel != this.maxStackLevel) {
      prevFirst = first;
      first = first.next;
    }
    if (first == null) {
      FRLogger.warn("ShapeTraceEntries: maxStackLevel not found");
      return null;
    }
    EntryPoint[] result = new EntryPoint[2];
    result[0] = first;
    EntryPoint last = first;
    EntryPoint afterLast = first.next;

    while (afterLast != null
        && afterLast.stackLevel == maxStackLevel
        && afterLast.trace.netsEqual(first.trace)) {
      last = afterLast;
      afterLast = last.next;
    }
    result[1] = last;

    // remove the nodes from first to last inclusive

    if (prevFirst != null) {
      prevFirst.next = afterLast;
    } else {
      listAnchor = afterLast;
    }

    // recalculate maxStackLevel;
    maxStackLevel = 0;
    EntryPoint current = listAnchor;
    while (current != null) {
      if (current.stackLevel > maxStackLevel) {
        maxStackLevel = current.stackLevel;
      }
      current = current.next;
    }
    --tracePieceCount;
    if (first.trace.netsEqual(this.ownNetNos)) {
      // own net is ignored and may occur only at the lowest level
      result = popPiece();
    }
    return result;
  }

  private void insertEntryPoint(
      PolylineTrace trace, int traceLineNo, int edgeIndex, FloatPoint entryApprox) {
    EntryPoint newEntry = new EntryPoint(trace, traceLineNo, edgeIndex, entryApprox);
    EntryPoint currentPrev = null;
    EntryPoint currentNext = listAnchor;
    // insert the new entry into the sorted list
    while (currentNext != null) {
      if (currentNext.edgeIndex > newEntry.edgeIndex) {
        break;
      }
      if (currentNext.edgeIndex == newEntry.edgeIndex) {
        FloatPoint prevCorner = shape.cornerApprox(edgeIndex);
        FloatPoint nextCorner;
        if (edgeIndex == shape.borderLineCount() - 1) {
          nextCorner = shape.cornerApprox(0);
        } else {
          nextCorner = shape.cornerApprox(newEntry.edgeIndex + 1);
        }
        // than the projection of the line from prevCorner to
        // next.entryApprox onto the same line.
        if (prevCorner.scalarProduct(entryApprox, nextCorner)
            <= prevCorner.scalarProduct(currentNext.entryApprox, nextCorner)) {
          break;
        }
      }
      currentPrev = currentNext;
      currentNext = currentNext.next;
    }
    newEntry.next = currentNext;
    if (currentPrev != null) {
      currentPrev.next = newEntry;
    } else {
      listAnchor = newEntry;
    }
  }

  /** Rotates the entry list so that newAnchor becomes the list head. */
  private void rotateEntryListAroundAnchor(EntryPoint newAnchor, int edgeCount) {
    EntryPoint current = newAnchor;
    EntryPoint prev = null;
    while (current != null) {
      prev = current;
      current = prev.next;
    }
    prev.next = listAnchor;
    current = listAnchor;
    while (current != newAnchor) {
      // add edgeCount to current.side to differentiate points
      // before and after the middle of fromSide
      current.edgeIndex += edgeCount;
      prev = current;
      current = prev.next;
    }
    prev.next = null;
    listAnchor = newAnchor;
  }

  /**
   * Information about an entry point of trace into the shape. The entry points are sorted around
   * the border of the shape
   */
  private static class EntryPoint {

    final PolylineTrace trace;
    final int traceLineNo;
    final FloatPoint entryApprox;
    int edgeIndex;
    int stackLevel;
    EntryPoint next;

    EntryPoint(PolylineTrace trace, int traceLineNo, int edgeIndex, FloatPoint entryApprox) {
      this.trace = trace;
      this.edgeIndex = edgeIndex;
      this.traceLineNo = traceLineNo;
      this.entryApprox = entryApprox;
      stackLevel = -1; // not yet calculated
    }
  }
}
