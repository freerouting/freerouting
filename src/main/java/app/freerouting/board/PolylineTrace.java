package app.freerouting.board;

import app.freerouting.datastructures.Signum;
import app.freerouting.datastructures.Stoppable;
import app.freerouting.geometry.planar.Direction;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.LineSegment;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/** Objects of class Trace, whose geometry is described by a Polyline. */
public class PolylineTrace extends Trace implements Serializable {

  private static final int MAX_NORMALIZATION_DEPTH = 16;
  // primary data
  private Polyline lines;

  /** Creates a new instance of a PolylineTrace with the input data. */
  public PolylineTrace(
      Polyline polyline,
      int layer,
      int halfWidth,
      int[] netNumbers,
      int clearanceType,
      int idNo,
      int groupNo,
      FixedState fixedState,
      BasicBoard board) {
    super(layer, halfWidth, netNumbers, clearanceType, idNo, groupNo, fixedState, board);
    if (polyline.lines.length < 3) {
      FRLogger.warn("PolylineTrace: polyline.lines.length >= 3 expected");
    }
    lines = polyline;
  }

  @Override
  public Item copy(int idNo) {
    int[] currentNetNumbers = new int[this.netCount()];
    for (int i = 0; i < currentNetNumbers.length; i++) {
      currentNetNumbers[i] = getNetNumber(i);
    }
    return new PolylineTrace(
        lines,
        getLayer(),
        getHalfWidth(),
        currentNetNumbers,
        clearanceClassIndex(),
        idNo,
        getComponentNo(),
        getFixedState(),
        board);
  }

  /** Checks, if this trace is on layer layer. */
  @Override
  public boolean isOnLayer(int layer) {
    return getLayer() == layer;
  }

  /**
   * Returns the first corner of this trace, which is the intersection of the first and second lines
   * of its polyline.
   */
  @Override
  public Point firstCorner() {
    return lines.corner(0);
  }

  /**
   * Returns the last corner of this trace, which is the intersection of the last two lines of its
   * polyline.
   */
  @Override
  public Point lastCorner() {
    return lines.corner(lines.lines.length - 2);
  }

  /**
   * Returns the number of corners of this trace, which is the number of lines of its polyline minus
   * one.
   */
  public int cornerCount() {
    return lines.lines.length - 1;
  }

  @Override
  public double getLength() {
    return lines.lengthApprox();
  }

  @Override
  public IntBox boundingBox() {
    IntBox result = this.lines.boundingBox();
    return result.offset(this.getHalfWidth());
  }

  /** Returns the polyline of this trace. */
  public Polyline polyline() {
    return lines;
  }

  @Override
  protected TileShape[] calculateTreeShapes(ShapeSearchTree searchTree) {
    return searchTree.calculateTreeShapes(this);
  }

  /** Returns the count of tile shapes of this polyline. */
  @Override
  public int tileShapeCount() {
    return Math.max(lines.lines.length - 2, 0);
  }

  @Override
  public void translateBy(Vector vector) {
    lines = lines.translateBy(vector);
    this.clearDerivedData();
  }

  @Override
  public void turn90Degree(int factor, IntPoint pole) {
    lines = lines.turn90Degree(factor, pole);
    this.clearDerivedData();
  }

  @Override
  public void rotateApprox(double angleInDegree, FloatPoint pole) {
    this.lines = this.lines.rotateApprox(Math.toRadians(angleInDegree), pole);
  }

  @Override
  public void changePlacementSide(IntPoint pole) {
    lines = lines.mirrorVertical(pole);

    if (this.board != null) {
      this.setLayer(board.getLayerCount() - this.getLayer() - 1);
    }
    this.clearDerivedData();
  }

  /**
   * Checks if other traces can be combined with this trace. Returns true, if something has been
   * combined. This trace will be the combined trace, so that only other traces may be deleted.
   */
  @Override
  public boolean combine() {
    // Iterative instead of recursive: a long chain of combinable traces
    // (e.g. many collinear segments from an SES round-trip) previously grew
    // the call stack by one frame per combined trace and crashed with a
    // StackOverflowError. The loop keeps the exact combining order
    // (retry at the start first, then at the end) and the per-combine
    // on-board re-check and observer notification of the recursive version.
    boolean somethingChanged = false;
    while (this.isOnTheBoard() && (this.combineAtStart(true) || this.combineAtEnd(true))) {
      somethingChanged = true;
      // let the observers synchronize the changes
      if ((board.communication != null) && (board.communication.observers != null)) {
        board.communication.observers.notifyChanged(this);
      }
      board.additionalUpdateAfterChange(this);
    }
    return somethingChanged;
  }

  /**
   * Checks if this trace can be combined at its first point with another trace.
   *
   * <p>Returns true if something was combined. The corners of the other trace will be inserted in
   * front of this trace. In case of combine the other trace will be deleted and this trace will
   * remain.
   */
  private boolean combineAtStart(boolean ignoreAreas) {
    boolean debugNet49 =
        this.netNumbers != null && this.netNumbers.length > 0 && this.netNumbers[0] == 49;
    Point startCorner = firstCorner();
    Collection<Item> contacts = getNormalContacts(startCorner, false);
    if (ignoreAreas) {
      // remove conduction areas from the list
      contacts.removeIf(ConductionArea.class::isInstance);
    }
    if (debugNet49) {
      FRLogger.trace(
          "compare_trace_combine_at_start_net49 thisId="
              + this.getIdNo()
              + ", thisFixed="
              + this.getFixedState()
              + ", start="
              + startCorner
              + ", contacts="
              + contacts.size());
      for (Item c : contacts) {
        FRLogger.trace(
            "  contact id="
                + c.getIdNo()
                + ", type="
                + c.getClass().getSimpleName()
                + ", fixed="
                + c.getFixedState()
                + (c instanceof Trace t
                    ? ", first=" + t.firstCorner() + ", last=" + t.lastCorner()
                    : ""));
      }
    }
    if (contacts.size() != 1) {
      return false;
    }
    PolylineTrace otherTrace = null;
    boolean traceFound = false;
    boolean reverseOrder = false;
    for (Item currentObject : contacts) {
      if (currentObject instanceof PolylineTrace trace) {
        otherTrace = trace;
        if (otherTrace.getLayer() == getLayer()
            && otherTrace.netsEqual(this)
            && otherTrace.getHalfWidth() == getHalfWidth()
            && otherTrace.getFixedState() == this.getFixedState()
            && !otherTrace.isDeletionForbidden()
            && !this.isDeletionForbidden()) {
          if (startCorner.equals(otherTrace.lastCorner())) {
            traceFound = true;
            break;
          } else if (startCorner.equals(otherTrace.firstCorner())) {
            reverseOrder = true;
            traceFound = true;
            break;
          }
        } else if (debugNet49) {
          FRLogger.trace(
              "  combineAtStart REJECTED: layer="
                  + otherTrace.getLayer()
                  + "=="
                  + getLayer()
                  + ", nets="
                  + otherTrace.netsEqual(this)
                  + ", width="
                  + otherTrace.getHalfWidth()
                  + "=="
                  + getHalfWidth()
                  + ", fixed="
                  + otherTrace.getFixedState()
                  + "=="
                  + this.getFixedState());
        }
      }
    }
    if (!traceFound) {
      return false;
    }

    board.itemList.saveForUndo(this);
    // create the lines of the joined polyline
    Line[] thisLines = lines.lines;
    Line[] otherLines;
    if (reverseOrder) {
      otherLines = new Line[otherTrace.lines.lines.length];
      for (int i = 0; i < otherLines.length; i++) {
        otherLines[i] = otherTrace.lines.lines[otherLines.length - 1 - i].opposite();
      }
    } else {
      otherLines = otherTrace.lines.lines;
    }
    boolean skipLine = otherLines[otherLines.length - 2].isEqualOrOpposite(thisLines[1]);
    int newLineCount = thisLines.length + otherLines.length - 2;
    if (skipLine) {
      --newLineCount;
    }
    Line[] newLines = new Line[newLineCount];
    System.arraycopy(otherLines, 0, newLines, 0, otherLines.length - 1);
    int joinPos = otherLines.length - 1;
    if (skipLine) {
      --joinPos;
    }
    System.arraycopy(thisLines, 1, newLines, joinPos, thisLines.length - 1);
    Polyline joinedPolyline = new Polyline(newLines);
    // Determine whether both traces have entries in the default search tree.
    // If either is missing, the optimised merge_entries_in_front path will NPE,
    // so we fall back to the safe full-remove + re-insert path in that case.
    boolean hasTreeEntries =
        (this.getSearchTreeEntries(board.searchTreeManager.getDefaultTree()) != null)
            && (otherTrace.getSearchTreeEntries(board.searchTreeManager.getDefaultTree()) != null);
    if (joinedPolyline.lines.length != newLineCount || !hasTreeEntries) {
      // consecutive parallel lines were skipped at the join location OR a trace
      // lacks search-tree entries — combine without performance optimization
      board.searchTreeManager.remove(this);
      this.clearSearchTreeEntries();
      this.lines = joinedPolyline;
      this.clearDerivedData();
      board.searchTreeManager.insert(this);
    } else {
      // reuse the tree entries for better performance
      // create the changed line shape at the join location
      int toNo = otherLines.length;
      if (skipLine) {
        --toNo;
      }
      board.searchTreeManager.mergeEntriesInFront(
          otherTrace, this, joinedPolyline, otherLines.length - 3, toNo);
      otherTrace.clearSearchTreeEntries();
      this.lines = joinedPolyline;
    }
    if (this.lines.lines.length < 3) {
      board.removeItem(this);
    }
    board.removeItem(otherTrace);
    if (board instanceof RoutingBoard routingBoard) {
      routingBoard.joinChangedArea(startCorner.toFloat(), getLayer());
    }
    return true;
  }

  /**
   * Checks if this trace can be combined at its last point with another trace.
   *
   * <p>Returns true if something was combined. The corners of the other trace will be inserted at
   * the end of this trace. In case of combine the other trace will be deleted and this trace will
   * remain.
   */
  private boolean combineAtEnd(boolean ignoreAreas) {
    boolean debugNet49 =
        this.netNumbers != null && this.netNumbers.length > 0 && this.netNumbers[0] == 49;
    Point endCorner = lastCorner();
    Collection<Item> contacts = getNormalContacts(endCorner, false);
    if (ignoreAreas) {
      // remove conduction areas from the list
      contacts.removeIf(ConductionArea.class::isInstance);
    }
    if (debugNet49) {
      FRLogger.trace(
          "compare_trace_combine_at_end_net49 thisId="
              + this.getIdNo()
              + ", thisFixed="
              + this.getFixedState()
              + ", end="
              + endCorner
              + ", contacts="
              + contacts.size());
      for (Item c : contacts) {
        FRLogger.trace(
            "  contact id="
                + c.getIdNo()
                + ", type="
                + c.getClass().getSimpleName()
                + ", fixed="
                + c.getFixedState()
                + (c instanceof Trace t
                    ? ", first=" + t.firstCorner() + ", last=" + t.lastCorner()
                    : ""));
      }
    }
    if (contacts.size() != 1) {
      return false;
    }
    PolylineTrace otherTrace = null;
    boolean traceFound = false;
    boolean reverseOrder = false;
    for (Item currentObject : contacts) {
      if (currentObject instanceof PolylineTrace trace) {
        otherTrace = trace;
        if (otherTrace.getLayer() == getLayer()
            && otherTrace.netsEqual(this)
            && otherTrace.getHalfWidth() == getHalfWidth()
            && otherTrace.getFixedState() == this.getFixedState()
            && !otherTrace.isDeletionForbidden()
            && !this.isDeletionForbidden()) {
          if (endCorner.equals(otherTrace.firstCorner())) {
            traceFound = true;
            break;
          } else if (endCorner.equals(otherTrace.lastCorner())) {
            reverseOrder = true;
            traceFound = true;
            break;
          }
        }
      }
    }
    if (!traceFound) {
      return false;
    }

    board.itemList.saveForUndo(this);
    // create the lines of the joined polyline
    Line[] thisLines = lines.lines;
    Line[] otherLines;
    if (reverseOrder) {
      otherLines = new Line[otherTrace.lines.lines.length];
      for (int i = 0; i < otherLines.length; i++) {
        otherLines[i] = otherTrace.lines.lines[otherLines.length - 1 - i].opposite();
      }
    } else {
      otherLines = otherTrace.lines.lines;
    }
    boolean skipLine = thisLines[thisLines.length - 2].isEqualOrOpposite(otherLines[1]);
    int newLineCount = thisLines.length + otherLines.length - 2;
    if (skipLine) {
      --newLineCount;
    }
    Line[] newLines = new Line[newLineCount];
    System.arraycopy(thisLines, 0, newLines, 0, thisLines.length - 1);
    int joinPos = thisLines.length - 1;
    if (skipLine) {
      --joinPos;
    }
    System.arraycopy(otherLines, 1, newLines, joinPos, otherLines.length - 1);
    Polyline joinedPolyline = new Polyline(newLines);
    // Determine whether both traces have entries in the default search tree.
    // If either is missing, the optimised merge_entries_at_end path will NPE,
    // so we fall back to the safe full-remove + re-insert path in that case.
    boolean hasTreeEntries =
        (this.getSearchTreeEntries(board.searchTreeManager.getDefaultTree()) != null)
            && (otherTrace.getSearchTreeEntries(board.searchTreeManager.getDefaultTree()) != null);
    if (joinedPolyline.lines.length != newLineCount || !hasTreeEntries) {
      // consecutive parallel lines were skipped at the join location OR a trace
      // lacks search-tree entries — combine without performance optimization
      board.searchTreeManager.remove(this);
      this.clearSearchTreeEntries();
      this.lines = joinedPolyline;
      this.clearDerivedData();
      board.searchTreeManager.insert(this);
    } else {
      // reuse tree entries for better performance
      // create the changed line shape at the join location
      int toNo = thisLines.length;
      if (skipLine) {
        --toNo;
      }
      board.searchTreeManager.mergeEntriesAtEnd(
          otherTrace, this, joinedPolyline, thisLines.length - 3, toNo);
      otherTrace.clearSearchTreeEntries();
      this.lines = joinedPolyline;
    }
    if (this.lines.lines.length < 3) {
      board.removeItem(this);
    }
    board.removeItem(otherTrace);
    if (board instanceof RoutingBoard routingBoard) {
      routingBoard.joinChangedArea(endCorner.toFloat(), getLayer());
    }
    return true;
  }

  /**
   * Looks up traces intersecting with this trace and splits them at the intersection points. In
   * case of an overlaps, the traces are split at their first and their last common point. Returns
   * the pieces resulting from splitting. Found cycles are removed. If nothing is split, the result
   * will contain just this Trace. If clipShape != null, the split may be restricted to clipShape.
   */
  @Override
  public Collection<PolylineTrace> split(IntOctagon clipShape) {
    Collection<PolylineTrace> result = new LinkedList<>();
    if (!this.netsNormal()) {
      // only normal nets are split
      result.add(this);
      return result;
    }
    boolean ownTraceSplit = false;
    ShapeSearchTree defaultTree = board.searchTreeManager.getDefaultTree();
    for (int i = 0; i < this.lines.lines.length - 2; i++) {
      if (clipShape != null) {
        LineSegment currentSegment = new LineSegment(this.lines, i + 1);
        if (!clipShape.intersects(currentSegment.boundingBox())) {
          continue;
        }
      }
      TileShape currentShape = this.getTreeShape(defaultTree, i);
      LineSegment currentLineSegment = new LineSegment(this.lines, i + 1);
      Collection<ShapeSearchTree.TreeEntry> overlappingTreeEntries = new LinkedList<>();
      // look for intersecting traces with the i-th line segment
      defaultTree.overlappingTreeEntries(currentShape, getLayer(), overlappingTreeEntries);
      Iterator<ShapeSearchTree.TreeEntry> it = overlappingTreeEntries.iterator();
      while (it.hasNext()) {
        if (!this.isOnTheBoard()) {
          // this trace has been deleted in a cleanup operation
          return result;
        }
        ShapeSearchTree.TreeEntry foundEntry = it.next();
        if (!(foundEntry.object instanceof Item foundItem)) {
          continue;
        }
        if (foundItem == this) {

          if (foundEntry.shapeIndexInObject >= i - 1 && foundEntry.shapeIndexInObject <= i + 1) {
            // don't split own trace at this line or at neighbour lines
            continue;
          }
          // try to handle intermediate segments of length 0 by comparing end corners
          if (i < foundEntry.shapeIndexInObject) {
            if (lines.corner(i + 1).equals(lines.corner(foundEntry.shapeIndexInObject))) {
              continue;
            }
          } else {
            if (lines.corner(foundEntry.shapeIndexInObject + 1).equals(lines.corner(i))) {
              continue;
            }
          }
        }
        if (!foundItem.sharesNet(this)) {
          continue;
        }
        if (foundItem instanceof PolylineTrace foundTrace) {
          LineSegment foundLineSegment =
              new LineSegment(foundTrace.lines, foundEntry.shapeIndexInObject + 1);
          Line[] intersectingLines = foundLineSegment.intersection(currentLineSegment);
          Collection<PolylineTrace> splitPieces = new LinkedList<>();

          boolean debugNet49 =
              this.netNumbers != null && this.netNumbers.length > 0 && this.netNumbers[0] == 49;
          if (debugNet49 && intersectingLines.length > 0) {
            FRLogger.trace(
                "compare_trace_split_found_trace net=49, this_id="
                    + this.getIdNo()
                    + ", this_seg="
                    + i
                    + ", this_first="
                    + this.firstCorner()
                    + ", this_last="
                    + this.lastCorner()
                    + ", found_id="
                    + foundTrace.getIdNo()
                    + ", found_seg="
                    + foundEntry.shapeIndexInObject
                    + ", found_first="
                    + foundTrace.firstCorner()
                    + ", found_last="
                    + foundTrace.lastCorner()
                    + ", intersections="
                    + intersectingLines.length);
          }

          // try splitting the found trace first
          boolean foundTraceSplit = false;

          if (foundTrace != this) {
            for (int j = 0; j < intersectingLines.length; j++) {
              int lineNo = foundEntry.shapeIndexInObject + 1;
              PolylineTrace[] currentSplitPieces = foundTrace.split(lineNo, intersectingLines[j]);
              if (currentSplitPieces != null) {

                for (int k = 0; k < 2; k++) {
                  if (currentSplitPieces[k] != null) {
                    foundTraceSplit = true;
                    if (this.netNumbers.length > 0 && this.netNumbers[0] == 94) {
                      FRLogger.trace(
                          "PolylineTrace.split",
                          "compare_trace_found_trace_split",
                          "foundTraceSplit=true at line index "
                              + foundEntry.shapeIndexInObject
                              + " with intersection "
                              + intersectingLines[j],
                          "Net #"
                              + this.netNumbers[0]
                              + ",Trace #"
                              + foundTrace.getIdNo()
                              + ",Layer #"
                              + foundTrace.getLayer(),
                          new Point[] {
                            foundTrace.firstCorner(),
                            foundTrace.lastCorner(),
                            lines.corner(i),
                            lines.corner(i + 1)
                          });
                    }
                    splitPieces.add(currentSplitPieces[k]);
                  }
                }
                if (foundTraceSplit) {
                  // reread the overlapping tree entries and reset the iterator,
                  // because the board has changed
                  defaultTree.overlappingTreeEntries(
                      currentShape, getLayer(), overlappingTreeEntries);
                  it = overlappingTreeEntries.iterator();
                  break;
                }
              }
            }
            if (!foundTraceSplit) {
              splitPieces.add(foundTrace);
            }
          }
          // now try splitting the own trace

          intersectingLines = currentLineSegment.intersection(foundLineSegment);
          for (int j = 0; j < intersectingLines.length; j++) {
            PolylineTrace[] currentSplitPieces = split(i + 1, intersectingLines[j]);
            if (currentSplitPieces != null) {
              ownTraceSplit = true;
              // this trace was split itself into 2.
              if (currentSplitPieces[0] != null) {
                result.addAll(currentSplitPieces[0].split(clipShape));
              }
              if (currentSplitPieces[1] != null) {
                result.addAll(currentSplitPieces[1].split(clipShape));
              }
              break;
            }
          }
          if (foundTraceSplit || ownTraceSplit) {
            // something was split,
            // remove cycles containing a split piece
            Iterator<PolylineTrace> it2 = splitPieces.iterator();
            for (int j = 0; j < 2; j++) {
              while (it2.hasNext()) {
                PolylineTrace currentPiece = it2.next();
                boolean debugThis =
                    this.netNumbers != null
                        && this.netNumbers.length > 0
                        && this.netNumbers[0] == 49;
                Point pieceFirst =
                    debugThis && currentPiece.isOnTheBoard() ? currentPiece.firstCorner() : null;
                Point pieceLast =
                    debugThis && currentPiece.isOnTheBoard() ? currentPiece.lastCorner() : null;
                int pieceId = currentPiece.getIdNo();
                boolean removedAsCycle = board.removeIfCycle(currentPiece);
                if (debugThis && removedAsCycle) {
                  FRLogger.trace(
                      "compare_trace_split_cycle_removed net=49, pass="
                          + j
                          + ", piece_id="
                          + pieceId
                          + ", piece_first="
                          + pieceFirst
                          + ", piece_last="
                          + pieceLast
                          + ", this_id="
                          + this.getIdNo());
                }
              }

              // remove cycles in the own split pieces last
              // to preserve them, if possible
              it2 = result.iterator();
            }
          }
          if (ownTraceSplit) {
            break;
          }
        } else if (foundItem instanceof DrillItem currentDrillItem) {
          Point splitPoint = currentDrillItem.getCenter();
          if (currentLineSegment.contains(splitPoint)) {
            Direction splitLineDirection = currentLineSegment.getLine().direction().turn45Degree(2);
            Line splitLine = new Line(splitPoint, splitLineDirection);
            split(i + 1, splitLine);
          }
        } else if (!this.isUserFixed() && (foundItem instanceof ConductionArea)) {
          boolean ignoreAreas = false;
          if (this.netNumbers.length > 0) {
            Net currentNet = this.board.rules.nets.get(this.netNumbers[0]);
            if (currentNet != null && currentNet.getNetClass() != null) {
              ignoreAreas = currentNet.getNetClass().getIgnoreCyclesWithAreas();
            }
          }
          if (!ignoreAreas
              && this.getStartContacts().contains(foundItem)
              && this.getEndContacts().contains(foundItem)) {
            // this trace can be removed because of cycle with conduction area
            board.removeItem(this);
            return result;
          }
        }
      }
      if (ownTraceSplit) {
        break;
      }
    }
    if (!ownTraceSplit) {
      result.add(this);
    }
    if (result.size() > 1) {
      for (Item currentItem : result) {
        board.additionalUpdateAfterChange(currentItem);
      }
    }
    return result;
  }

  /**
   * Splits this trace into two at point. Returns the 2 pieces of the split trace, or null if
   * nothing was split because for example point is not located on a line segment of the polyline of
   * this trace.
   */
  @Override
  public Trace[] split(Point point) {
    for (int i = 0; i < this.lines.lines.length - 2; i++) {
      LineSegment currentLineSegment = new LineSegment(this.lines, i + 1);
      if (currentLineSegment.contains(point)) {
        Direction splitLineDirection = currentLineSegment.getLine().direction().turn45Degree(2);
        Line splitLine = new Line(point, splitLineDirection);
        Trace[] result = split(i + 1, splitLine);
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  /**
   * Splits this trace at the line with number lineNo into two by inserting endline as concluding
   * line of the first split piece and as the start line of the second split piece. Returns the 2
   * pieces of the split trace, or null, if nothing was split.
   */
  private PolylineTrace[] split(int lineNo, Line newEndLine) {
    if (!this.isOnTheBoard()) {
      return null;
    }
    // Guard: if this trace cannot be deleted (e.g. USER_FIXED / protect), splitting would leave
    // the original on the board AND insert duplicate pieces. The outer normalize_traces loop
    // would then keep finding the same "changed" state and never converge. Return null so the
    // caller knows the split is not possible.
    if (isDeletionForbidden()) {
      return null;
    }
    Polyline[] splitPolylines = lines.split(lineNo, newEndLine);
    if (splitPolylines == null) {
      return null;
    }
    if (splitPolylines.length != 2) {
      FRLogger.warn("PolylineTrace.split: array of length 2 expected for splitPolylines");
      return null;
    }
    if (splitInsideDrillPadProhibited(lineNo, newEndLine)) {
      return null;
    }
    board.removeItem(this);
    PolylineTrace[] result = new PolylineTrace[2];
    result[0] =
        board.insertTraceWithoutCleaning(
            splitPolylines[0],
            getLayer(),
            getHalfWidth(),
            netNumbers,
            clearanceClassIndex(),
            getFixedState());
    result[1] =
        board.insertTraceWithoutCleaning(
            splitPolylines[1],
            getLayer(),
            getHalfWidth(),
            netNumbers,
            clearanceClassIndex(),
            getFixedState());
    return result;
  }

  /**
   * Checks, if the intersection of the lineNo-th line of this trace with line is inside the pad of
   * a pin. In this case the trace will be split only, if the intersection is at the center of the
   * pin. Extending the function to vias led to broken connection problems when the autorouter
   * connected to a trace.
   */
  private boolean splitInsideDrillPadProhibited(int lineNo, Line line) {
    if (this.board == null) {
      return false;
    }
    Point intersection = this.lines.lines[lineNo].intersection(line);
    Collection<Item> overlapItems = this.board.pickItems(intersection, this.getLayer(), null);
    boolean padFound = false;
    for (Item currentItem : overlapItems) {
      if (!currentItem.sharesNet(this)) {
        continue;
      }
      if (currentItem instanceof Pin currentDrillItem) {
        if (currentDrillItem.getCenter().equals(intersection)) {
          return false; // split always at the center of a drill item.
        }
        padFound = true;
      } else if (currentItem instanceof Trace currentTrace) {
        if (currentTrace != this && currentTrace.firstCorner().equals(intersection)
            || currentTrace.lastCorner().equals(intersection)) {
          return false;
        }
      }
    }
    return padFound;
  }

  /**
   * Splits this trace and overlapping traces, and combines this trace. Returns true, if something
   * was changed. If clipShape != null, splitting is restricted to clipShape.
   *
   * @param clipShape the shape to clip the trace to
   * @return true, if something was changed
   */
  public boolean normalize(IntOctagon clipShape) {
    return normalize(clipShape, 0);
  }

  private boolean normalize(IntOctagon clipShape, int normalizationDepth) {
    if (normalizationDepth > MAX_NORMALIZATION_DEPTH) {
      // Return false (no further change at this depth level) rather than throwing an exception.
      // The outer normalize_traces() loop treats a false return as "nothing changed" for this
      // trace,
      // which is safe: the trace geometry may be slightly sub-optimal (extra corners), but it
      // remains
      // structurally valid, its endpoint contacts are preserved and remove_tails() will not touch
      // it.
      // Throwing here was causing two problems:
      //   1. Noisy WARN log in InsertFoundConnectionAlgo every routing pass for complex plane nets.
      //   2. Intermediate trace fragments created during deep-recursion split/combine were left in
      // an
      //      inconsistent state, causing them to be misidentified as tails and removed by
      // remove_tails(),
      //      which in turn forced GND routing to re-attempt the same connection on every subsequent
      // pass.
      FRLogger.debug(
          "PolylineTrace.normalize: max normalization depth ("
              + MAX_NORMALIZATION_DEPTH
              + ") reached for trace #"
              + this.getIdNo()
              + " on net "
              + (this.netCount() > 0 ? this.netNumbers[0] : -1)
              + " — stopping recursion, trace kept as-is.");
      return false;
    }

    boolean debugNet49 =
        this.netNumbers != null
            && this.netNumbers.length > 0
            && this.netNumbers[0] == 49
            && normalizationDepth == 0;

    boolean observersActivated = false;
    BasicBoard routingBoard = this.board;
    if (this.board != null) {
      // Let the observers know the trace changes.
      observersActivated = !routingBoard.observersActive();
      if (observersActivated) {
        routingBoard.startNotifyObservers();
      }
    }
    Collection<PolylineTrace> splitPieces = this.split(clipShape);
    boolean result = splitPieces.size() != 1;
    if (debugNet49) {
      FRLogger.trace(
          "compare_trace_normalize_net49 depth="
              + normalizationDepth
              + ", thisId="
              + this.getIdNo()
              + ", thisOnBoard="
              + this.isOnTheBoard()
              + ", thisFirst="
              + this.firstCorner()
              + ", thisLast="
              + this.lastCorner()
              + ", splitPieces="
              + splitPieces.size());
      for (PolylineTrace piece : splitPieces) {
        FRLogger.trace(
            "compare_trace_normalize_net49  piece id="
                + piece.getIdNo()
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
                  + currentSplitTrace.getIdNo()
                  + ", onBoard="
                  + currentSplitTrace.isOnTheBoard()
                  + ", combined="
                  + traceCombined
                  + ", first="
                  + (currentSplitTrace.isOnTheBoard() ? currentSplitTrace.firstCorner() : "N/A")
                  + ", last="
                  + (currentSplitTrace.isOnTheBoard() ? currentSplitTrace.lastCorner() : "N/A"));
        }
        if (currentSplitTrace.cornerCount() == 2
            && currentSplitTrace.firstCorner().equals(currentSplitTrace.lastCorner())) {
          // remove trace with only 1 corner — only if deletion is allowed.
          // USER_FIXED traces cannot be removed; skipping silently prevents an infinite loop in
          // normalize_traces (where 'result=true' would cause the outer while to spin forever).
          if (!currentSplitTrace.isDeletionForbidden()) {
            board.removeItem(currentSplitTrace);
            result = true;
          } else {
            int netNumber = currentSplitTrace.netCount() > 0 ? currentSplitTrace.netNumbers[0] : -1;
            // A degenerate user-fixed trace (first==last corner) cannot be removed because
            // deletion is forbidden. This is expected for boards with malformed DSN geometry
            // (e.g. bad EDA export). The user was already warned at load time; suppress here.
            FRLogger.debug(
                "PolylineTrace.normalize: skipping removal of degenerate user-fixed trace #"
                    + currentSplitTrace.getIdNo()
                    + " on net "
                    + netNumber
                    + " (first==last corner)");
          }
        } else if (traceCombined) {
          currentSplitTrace.normalize(clipShape, normalizationDepth + 1);
          result = true;
        }
      }
    }
    if (observersActivated) {
      routingBoard.endNotifyObservers();
    }
    return result;
  }

  /**
   * Tries to shorten this trace without creating clearance violations Returns true, if the trace
   * was changed.
   */
  @Override
  public boolean pullTight(PullTightAlgo pullTightAlgo) {
    if (!this.isOnTheBoard()) {
      // This trace may have been deleted in a trace split for example
      return false;
    }
    if (this.isShoveFixed()) {
      return false;
    }
    if (!this.netsNormal()) {
      return false;
    }
    if (pullTightAlgo.onlyNetNoArr.length > 0 && !this.netsEqual(pullTightAlgo.onlyNetNoArr)) {
      return false;
    }
    if (this.netNumbers.length > 0) {
      if (!this.board.rules.nets.get(this.netNumbers[0]).getNetClass().getPullTight()) {
        return false;
      }
    }
    Polyline newLines =
        pullTightAlgo.pullTight(
            lines,
            getLayer(),
            getHalfWidth(),
            netNumbers,
            clearanceClassIndex(),
            this.touchingPinsAtEndCorners());
    if (newLines != lines) {
      change(newLines);
      return true;
    }
    AngleRestriction angleRestriction = this.board.rules.getTraceAngleRestriction();
    if (angleRestriction != AngleRestriction.NINETY_DEGREE
        && this.board.rules.getPinEdgeToTurnDist() > 0) {
      if (this.swapConnectionToPin(true)) {
        pullTight(pullTightAlgo);
        return true;
      }
      if (this.swapConnectionToPin(false)) {
        pullTight(pullTightAlgo);
        return true;
      }
      // optimize algorithm could not improve the trace, try to remove acid traps
      if (this.correctConnectionToPin(true, angleRestriction)) {
        pullTight(pullTightAlgo);
        return true;
      }
      if (this.correctConnectionToPin(false, angleRestriction)) {
        pullTight(pullTightAlgo);
        return true;
      }
    }
    return false;
  }

  /**
   * Tries to pull this trace tight without creating clearance violations Returns true, if the trace
   * was changed.
   */
  public boolean pullTight(boolean ownNetOnly, int pullTightAccuracy, Stoppable stoppableThread) {
    if (!(this.board instanceof RoutingBoard)) {
      return false;
    }
    int[] optNetNoArr;
    if (ownNetOnly) {
      optNetNoArr = this.netNumbers;
    } else {
      optNetNoArr = new int[0];
    }
    PullTightAlgo pullTightAlgo =
        PullTightAlgo.getInstance(
            (RoutingBoard) this.board,
            optNetNoArr,
            null,
            pullTightAccuracy,
            stoppableThread,
            -1,
            null,
            -1);
    return pullTight(pullTightAlgo);
  }

  /** Tries to smoothen the end corners of this trace, which are at a fork with other traces. */
  public boolean smoothenEndCornersFork(
      boolean ownNetOnly, int pullTightAccuracy, Stoppable stoppableThread) {
    if (!(this.board instanceof RoutingBoard)) {
      return false;
    }
    int[] optNetNoArr;
    if (ownNetOnly) {
      optNetNoArr = this.netNumbers;
    } else {
      optNetNoArr = new int[0];
    }
    PullTightAlgo pullTightAlgo =
        PullTightAlgo.getInstance(
            (RoutingBoard) this.board,
            optNetNoArr,
            null,
            pullTightAccuracy,
            stoppableThread,
            -1,
            null,
            -1);
    return pullTightAlgo.smoothenEndCornersAtTrace(this);
  }

  @Override
  public TileShape getTraceConnectionShape(ShapeSearchTree searchTree, int index) {
    if (index < 0 || index >= this.tileShapeCount()) {
      FRLogger.warn("PolylineTrace.get_trace_connection_shape index out of range");
      return null;
    }
    LineSegment currentLineSegment = new LineSegment(this.lines, index + 1);
    return currentLineSegment.toSimplex().simplify();
  }

  @Override
  public boolean write(ObjectOutputStream stream) {
    try {
      stream.writeObject(this);
    } catch (IOException _) {
      return false;
    }
    return true;
  }

  /** Changes the geometry of this trace to newPolyline. */
  void change(Polyline newPolyline) {
    if (!this.isOnTheBoard()) {
      // Just change the polyline of this trace.
      lines = newPolyline;
      return;
    }

    board.additionalUpdateAfterChange(this);

    // The precalculated tile shapes must not be cleared here because they are used
    // and
    // modified
    // in ShapeSearchTree.change_entries.

    board.itemList.saveForUndo(this);

    // for performance reasons there is some effort to reuse
    // ShapeTree entries of the old trace in the changed trace

    // look for the first line in newPolyline different from
    // the lines of the existing trace
    int lastIndex = Math.min(newPolyline.lines.length, lines.lines.length);
    int indexOfFirstDifferentLine = lastIndex;
    for (int i = 0; i < lastIndex; i++) {
      if (newPolyline.lines[i] != lines.lines[i]) {
        indexOfFirstDifferentLine = i;
        break;
      }
    }
    if (indexOfFirstDifferentLine == lastIndex) {
      return; // both polylines are equal, no change necessary
    }
    // look for the last line in newPolyline different from
    // the lines of the existing trace
    int indexOfLastDifferentLine = -1;
    for (int i = 1; i <= lastIndex; i++) {
      if (newPolyline.lines[newPolyline.lines.length - i] != lines.lines[lines.lines.length - i]) {
        indexOfLastDifferentLine = newPolyline.lines.length - i;
        break;
      }
    }
    if (indexOfLastDifferentLine < 0) {
      return; // both polylines are equal, no change necessary
    }
    int keepAtStartCount = Math.max(indexOfFirstDifferentLine - 2, 0);
    int keepAtEndCount = Math.max(newPolyline.lines.length - indexOfLastDifferentLine - 3, 0);
    board.searchTreeManager.changeEntries(this, newPolyline, keepAtStartCount, keepAtEndCount);
    lines = newPolyline;

    // let the observers synchronize the changes
    if ((board.communication != null) && (board.communication.observers != null)) {
      board.communication.observers.notifyChanged(this);
    }

    IntOctagon clipShape = null;
    if (board instanceof RoutingBoard routingBoard) {
      ChangedArea changedArea = routingBoard.changedArea;
      if (changedArea != null) {
        clipShape = changedArea.getArea(this.getLayer());
      }
    }

    try {
      this.normalize(clipShape);
    } catch (Exception e) {
      FRLogger.error("Couldn't change the trace, because its normalization failed.", e);
    }
  }

  /**
   * Checks that the connection restrictions to the contact pins are satisfied.
   *
   * <p>If atStart, the start of this trace is checked, else the end. Returns false if a pin is at
   * that end where the connection is checked and the connection is not ok.
   */
  @Override
  public boolean checkConnectionToPin(boolean atStart) {
    if (this.board == null) {
      return true;
    }
    if (this.cornerCount() < 2) {
      return true;
    }
    Collection<Item> contactList;
    if (atStart) {
      contactList = this.getStartContacts();
    } else {
      contactList = this.getEndContacts();
    }
    Pin contactPin = null;
    for (Item currentContact : contactList) {
      if (currentContact instanceof Pin pin) {
        contactPin = pin;
        break;
      }
    }
    if (contactPin == null) {
      return true;
    }
    Collection<Pin.TraceExitRestriction> traceExitRestrictions =
        contactPin.getTraceExitRestrictions(this.getLayer());
    if (traceExitRestrictions.isEmpty()) {
      return true;
    }
    Point endCorner;
    Point prevEndCorner;
    if (atStart) {
      endCorner = this.firstCorner();
      prevEndCorner = this.lines.corner(1);
    } else {
      endCorner = this.lastCorner();
      prevEndCorner = this.lines.corner(this.lines.cornerCount() - 2);
    }
    Direction traceEndDirection = Direction.getInstance(endCorner, prevEndCorner);
    if (traceEndDirection == null) {
      return true;
    }
    Pin.TraceExitRestriction matchingExitRestriction = null;
    for (Pin.TraceExitRestriction currentExitRestriction : traceExitRestrictions) {
      if (currentExitRestriction.direction.equals(traceEndDirection)) {
        matchingExitRestriction = currentExitRestriction;
        break;
      }
    }
    if (matchingExitRestriction == null) {
      return false;
    }
    final double edgeToTurnDist = this.board.rules.getPinEdgeToTurnDist();
    if (edgeToTurnDist < 0) {
      return false;
    }
    double endLineLength = endCorner.toFloat().distance(prevEndCorner.toFloat());
    double currentClearance =
        board.clearanceValue(
            this.clearanceClassIndex(), contactPin.clearanceClassIndex(), this.getLayer());
    double addWidth = Math.max(edgeToTurnDist, currentClearance + 1);
    double preserveLength = matchingExitRestriction.minLength + this.getHalfWidth() + addWidth;
    return preserveLength <= endLineLength;
  }

  /**
   * Tries to correct a connection restriction of this trace. If atStart, the start of the trace
   * polygon is corrected, else the end. Returns true, if this trace was changed.
   */
  public boolean correctConnectionToPin(boolean atStart, AngleRestriction angleRestriction) {
    if (this.checkConnectionToPin(atStart)) {
      return false;
    }

    Polyline tracePolyline;
    Collection<Item> contactList;
    if (atStart) {
      tracePolyline = this.polyline();
      contactList = this.getStartContacts();
    } else {
      tracePolyline = this.polyline().reverse();
      contactList = this.getEndContacts();
    }
    Pin contactPin = null;
    for (Item currentContact : contactList) {
      if (currentContact instanceof Pin pin) {
        contactPin = pin;
        break;
      }
    }
    if (contactPin == null) {
      return false;
    }
    Collection<Pin.TraceExitRestriction> traceExitRestrictions =
        contactPin.getTraceExitRestrictions(this.getLayer());
    if (traceExitRestrictions.isEmpty()) {
      return false;
    }
    Shape pinShape = contactPin.getShape(this.getLayer() - contactPin.firstLayer());
    if (!(pinShape instanceof TileShape)) {
      return false;
    }
    final double edgeToTurnDist = this.board.rules.getPinEdgeToTurnDist();
    if (edgeToTurnDist < 0) {
      return false;
    }
    double currentClearance =
        board.clearanceValue(
            this.clearanceClassIndex(), contactPin.clearanceClassIndex(), this.getLayer());
    double addWidth = Math.max(edgeToTurnDist, currentClearance + 1);
    TileShape offsetPinShape =
        (TileShape) ((TileShape) pinShape).offset(this.getHalfWidth() + addWidth);
    if (angleRestriction == AngleRestriction.NINETY_DEGREE || offsetPinShape.isIntBox()) {
      offsetPinShape = offsetPinShape.boundingBox();
    } else if (angleRestriction == AngleRestriction.FORTYFIVE_DEGREE) {
      offsetPinShape = offsetPinShape.boundingOctagon();
    }
    int[][] entries = offsetPinShape.entrancePoints(tracePolyline);
    if (entries.length == 0) {
      return false;
    }
    int[] latestEntryTuple = entries[entries.length - 1];
    FloatPoint traceEntryLocationApprox =
        tracePolyline.lines[latestEntryTuple[0]].intersectionApprox(
            offsetPinShape.borderLine(latestEntryTuple[1]));
    // calculate the nearest legal pin exit point to traceEntryLocationApprox
    double minExitCornerDistance = Double.MAX_VALUE;
    Line nearestPinExitRay = null;
    int nearestBorderLineNo = -1;
    Direction pinExitDirection = null;
    FloatPoint nearestExitCorner = null;
    final double tolerance = 1;
    Point pinCenter = contactPin.getCenter();
    for (Pin.TraceExitRestriction currentExitRestriction : traceExitRestrictions) {
      int currentIntersectingBorderLineNo =
          offsetPinShape.intersectingBorderLineNo(pinCenter, currentExitRestriction.direction);
      Line currentPinExitRay = new Line(pinCenter, currentExitRestriction.direction);
      FloatPoint currentExitCorner =
          currentPinExitRay.intersectionApprox(
              offsetPinShape.borderLine(currentIntersectingBorderLineNo));
      double currentExitCornerDistance = currentExitCorner.distanceSquare(traceEntryLocationApprox);
      boolean newNearestCornerFound = false;
      if (currentExitCornerDistance + tolerance < minExitCornerDistance) {
        newNearestCornerFound = true;
      } else if (currentExitCornerDistance < minExitCornerDistance + tolerance) {
        // the distances are near equal, compare to the previous corners of
        // tracePolyline
        for (int i = 1; i < tracePolyline.cornerCount(); i++) {
          FloatPoint currentTraceCorner = tracePolyline.cornerApprox(i);
          double currentTraceCornerDistance = currentTraceCorner.distanceSquare(currentExitCorner);
          double oldTraceCornerDistance = currentTraceCorner.distanceSquare(nearestExitCorner);
          if (currentTraceCornerDistance + tolerance < oldTraceCornerDistance) {
            newNearestCornerFound = true;
            break;
          } else if (currentTraceCornerDistance > oldTraceCornerDistance + tolerance) {
            break;
          }
        }
      }
      if (newNearestCornerFound) {
        minExitCornerDistance = currentExitCornerDistance;
        nearestPinExitRay = currentPinExitRay;
        nearestBorderLineNo = currentIntersectingBorderLineNo;
        pinExitDirection = currentExitRestriction.direction;
        nearestExitCorner = currentExitCorner;
      }
    }

    // append the polygon piece around the border of the pin shape.

    Line[] currentLines;

    int cornerCount = offsetPinShape.borderLineCount();
    int clockWiseSideDiff = (nearestBorderLineNo - latestEntryTuple[1] + cornerCount) % cornerCount;
    int counterClockWiseSideDiff =
        (latestEntryTuple[1] - nearestBorderLineNo + cornerCount) % cornerCount;
    int currentBorderLineNo = nearestBorderLineNo;
    if (counterClockWiseSideDiff <= clockWiseSideDiff) {
      currentLines = new Line[counterClockWiseSideDiff + 3];
      for (int i = 0; i <= counterClockWiseSideDiff; i++) {
        currentLines[i + 1] = offsetPinShape.borderLine(currentBorderLineNo);
        currentBorderLineNo = (currentBorderLineNo + 1) % cornerCount;
      }
    } else {
      currentLines = new Line[clockWiseSideDiff + 3];
      for (int i = 0; i <= clockWiseSideDiff; i++) {
        currentLines[i + 1] = offsetPinShape.borderLine(currentBorderLineNo);
        currentBorderLineNo = (currentBorderLineNo - 1 + cornerCount) % cornerCount;
      }
    }
    currentLines[0] = nearestPinExitRay;
    currentLines[currentLines.length - 1] = tracePolyline.lines[latestEntryTuple[0]];

    Polyline borderPolyline = new Polyline(currentLines);
    if (!this.board.checkPolylineTrace(
        borderPolyline,
        this.getLayer(),
        this.getHalfWidth(),
        this.netNumbers,
        this.clearanceClassIndex())) {
      return false;
    }

    Line[] cutLines = new Line[tracePolyline.lines.length - latestEntryTuple[0] + 1];
    cutLines[0] = currentLines[currentLines.length - 2];
    System.arraycopy(tracePolyline.lines, latestEntryTuple[0], cutLines, 1, cutLines.length - 1);
    Polyline cutPolyline = new Polyline(cutLines);
    Polyline changedPolyline;
    if (cutPolyline.firstCorner().equals(cutPolyline.lastCorner())) {
      changedPolyline = borderPolyline;
    } else {
      changedPolyline = borderPolyline.combine(cutPolyline);
    }
    if (!atStart) {
      changedPolyline = changedPolyline.reverse();
    }
    this.change(changedPolyline);

    // create a shoveFixed exit line.
    currentLines = new Line[3];
    currentLines[0] = new Line(pinCenter, pinExitDirection.turn45Degree(2));
    currentLines[1] = nearestPinExitRay;
    currentLines[2] = offsetPinShape.borderLine(nearestBorderLineNo);
    Polyline exitLineSegment = new Polyline(currentLines);
    this.board.insertTrace(
        exitLineSegment,
        this.getLayer(),
        this.getHalfWidth(),
        this.netNumbers,
        this.clearanceClassIndex(),
        FixedState.SHOVE_FIXED);
    return true;
  }

  /**
   * Looks, if another pin connection restriction fits better than the current connection
   * restriction and changes this trace in this case. If atStart, the start of the trace polygon is
   * changed, else the end. Returns true, if this trace was changed.
   */
  public boolean swapConnectionToPin(boolean atStart) {
    Polyline tracePolyline;
    Collection<Item> contactList;
    if (atStart) {
      tracePolyline = this.polyline();
      contactList = this.getStartContacts();
    } else {
      tracePolyline = this.polyline().reverse();
      contactList = this.getEndContacts();
    }
    if (contactList.size() != 1) {
      return false;
    }
    Item currentContact = contactList.iterator().next();
    if (!(currentContact.getFixedState() == FixedState.SHOVE_FIXED
        && (currentContact instanceof PolylineTrace contactTrace))) {
      return false;
    }
    Polyline contactPolyline = contactTrace.polyline();
    Line contactLastLine = contactPolyline.lines[contactPolyline.lines.length - 2];
    // look, if this trace has a sharp angle with the contact trace.
    Line firstLine = tracePolyline.lines[1];
    // check for sharp angle
    boolean checkSwap =
        contactLastLine.direction().projection(firstLine.direction()) == Signum.NEGATIVE;
    if (!checkSwap) {
      double halfWidth = this.getHalfWidth();
      if (tracePolyline.lines.length > 3
          && tracePolyline.cornerApprox(0).distanceSquare(tracePolyline.cornerApprox(1))
              <= halfWidth * halfWidth) {
        // check also for sharp angle with the second line
        checkSwap =
            contactLastLine.direction().projection(tracePolyline.lines[2].direction())
                == Signum.NEGATIVE;
      }
    }
    if (!checkSwap) {
      return false;
    }
    Pin contactPin = null;
    Collection<Item> currentContacts = contactTrace.getStartContacts();
    for (Item tmpContact : currentContacts) {
      if (tmpContact instanceof Pin pin) {
        contactPin = pin;
        break;
      }
    }
    if (contactPin == null) {
      return false;
    }
    Polyline combinedPolyline = contactPolyline.combine(tracePolyline);
    Direction nearestPinExitDirection =
        contactPin.calcNearestExitRestrictionDirection(
            combinedPolyline, this.getHalfWidth(), this.getLayer());
    if (nearestPinExitDirection == null
        || nearestPinExitDirection.equals(contactPolyline.lines[1].direction())) {
      return false; // direction would not be changed
    }
    contactTrace.setFixedState(this.getFixedState());
    this.combine();
    return true;
  }
}
