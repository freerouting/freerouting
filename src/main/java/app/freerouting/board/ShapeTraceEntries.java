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

/** Auxiliary class used by the shove functions */
public class ShapeTraceEntries {

  private static final double c_offset_add = 1;
  final Collection<Via> shoveViaList;
  private final TileShape shape;
  private final int layer;
  private final int[] ownNetNos;
  private final int clClass;
  private final RoutingBoard board;
  private CalcFromSide fromSide;
  private EntryPoint listAnchor;
  private int tracePieceCount;
  private int maxStackLevel;
  private boolean shapeContainsTraceTails;
  private Item foundObstacle;

  /**
   * Used for shoving traces and vias out of the input shape. p_from_side.no is the side of p_shape,
   * from where the shove comes. if p_from_side.no < 0, it will be calculated internally.
   */
  ShapeTraceEntries(
      TileShape p_shape,
      int p_layer,
      int[] p_own_net_nos,
      int p_cl_type,
      CalcFromSide p_from_side,
      RoutingBoard p_board) {
    shape = p_shape;
    layer = p_layer;
    ownNetNos = p_own_net_nos;
    clClass = p_cl_type;
    fromSide = p_from_side;
    board = p_board;
    listAnchor = null;
    tracePieceCount = 0;
    maxStackLevel = 0;
    shoveViaList = new LinkedList<>();
  }

  public static void cutout_trace(PolylineTrace p_trace, ConvexShape p_shape, int p_cl_class) {
    if (!p_trace.is_on_the_board()) {
      FRLogger.warn("ShapeTraceEntries.cutout_trace : trace is deleted");
      return;
    }
    ConvexShape offsetShape;
    BasicBoard board = p_trace.board;
    ShapeSearchTree searchTree = board.searchTreeManager.get_default_tree();
    if (searchTree.is_clearance_compensation_used()) {
      double currOffset = p_trace.get_compensated_half_width(searchTree) + c_offset_add;
      offsetShape = p_shape.offset(currOffset);
    } else {
      // enlarge the shape in 2 steps  for symmetry reasons
      double clOffset =
          board.clearance_value(p_trace.clearance_class_no(), p_cl_class, p_trace.get_layer())
              + c_offset_add;
      offsetShape = p_shape.offset(p_trace.get_half_width());
      offsetShape = offsetShape.offset(clOffset);
    }
    Polyline traceLines = p_trace.polyline();
    Polyline[] pieces = offsetShape.cutout(traceLines);
    if (pieces.length == 1 && pieces[0] == traceLines) {
      // nothing cut off
      return;
    }
    if (pieces.length == 2
        && offsetShape.is_outside(pieces[0].first_corner())
        && offsetShape.is_outside(pieces[1].last_corner())) {
      fast_cutout_trace(p_trace, pieces[0], pieces[1]);
    } else {
      board.remove_item(p_trace);
      for (int i = 0; i < pieces.length; i++) {
        board.insert_trace_without_cleaning(
            pieces[i],
            p_trace.get_layer(),
            p_trace.get_half_width(),
            p_trace.netNoArr,
            p_trace.clearance_class_no(),
            FixedState.UNFIXED);
      }
    }
  }

  /** Optimized function handling the performance critical standard cutout case */
  private static void fast_cutout_trace(
      PolylineTrace p_trace, Polyline p_start_piece, Polyline p_end_piece) {
    BasicBoard board = p_trace.board;
    board.additional_update_after_change(p_trace);
    board.itemList.save_for_undo(p_trace);
    PolylineTrace startPiece =
        new PolylineTrace(
            p_start_piece,
            p_trace.get_layer(),
            p_trace.get_half_width(),
            p_trace.netNoArr,
            p_trace.clearance_class_no(),
            0,
            0,
            FixedState.UNFIXED,
            board);
    startPiece.board = board;
    board.itemList.insert(startPiece);
    startPiece.set_on_the_board(true);

    PolylineTrace endPiece =
        new PolylineTrace(
            p_end_piece,
            p_trace.get_layer(),
            p_trace.get_half_width(),
            p_trace.netNoArr,
            p_trace.clearance_class_no(),
            0,
            0,
            FixedState.UNFIXED,
            board);
    endPiece.board = board;
    board.itemList.insert(endPiece);
    endPiece.set_on_the_board(true);

    board.searchTreeManager.reuse_entries_after_cutout(p_trace, startPiece, endPiece);
    board.remove_item(p_trace);

    if ((board.communication != null) && (board.communication.observers != null)) {
      board.communication.observers.notify_new(startPiece);
      board.communication.observers.notify_new(endPiece);
    }
  }

  private static boolean net_nos_equal(int[] p_net_nos_1, int[] p_net_nos_2) {
    if (p_net_nos_1.length != p_net_nos_2.length) {
      return false;
    }
    for (int curr_net_no_1 : p_net_nos_1) {
      boolean netNoFound = false;
      for (int curr_net_no_2 : p_net_nos_2) {
        if (curr_net_no_1 == curr_net_no_2) {
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
   * Stores traces and vias in p_item_list. Returns false, if p_item_list contains obstacles, which
   * cannot be shoved aside. If p_is_pad_check. the check is for vias, otherwise it is for traces.
   * If p_copper_sharing_allowed, overlaps with traces or pads of the own net are allowed.
   */
  boolean store_items(
      Collection<Item> p_item_list, boolean p_is_pad_check, boolean p_copper_sharing_allowed) {
    for (Item currItem : p_item_list) {
      if (!p_is_pad_check && currItem instanceof ViaObstacleArea
          || currItem instanceof ComponentObstacleArea) {
        continue;
      }
      boolean containsOwnNet = currItem.shares_net_no(this.ownNetNos);
      if (currItem instanceof ConductionArea area && (containsOwnNet || !area.get_is_obstacle())) {
        continue;
      }
      if (currItem.is_shove_fixed() && !containsOwnNet) {
        this.foundObstacle = currItem;
        return false;
      }
      if (currItem instanceof Via via) {
        if (p_is_pad_check || !containsOwnNet) {
          shoveViaList.add(via);
        }
      } else if (currItem instanceof PolylineTrace currTrace) {

        if (!store_trace(currTrace)) {
          return false;
        }
      } else {
        if (containsOwnNet) {
          if (!p_copper_sharing_allowed) {
            this.foundObstacle = currItem;
            return false;
          }
          if (p_is_pad_check && !((currItem instanceof Pin pin) && pin.drill_allowed())) {
            this.foundObstacle = currItem;
            return false;
          }
        } else {
          this.foundObstacle = currItem;
          return false;
        }
      }
    }
    search_from_side();
    resort();
    return calculate_stack_levels();
  }

  /**
   * calculates the next substitute trace piece. Returns null at the end of the substitute trace
   * list.
   */
  PolylineTrace next_substitute_trace_piece() {

    EntryPoint[] entries = pop_piece();
    if (entries == null) {
      return null;
    }
    PolylineTrace currTrace = entries[0].trace;
    TileShape offsetShape;
    ShapeSearchTree searchTree = this.board.searchTreeManager.get_default_tree();
    if (searchTree.is_clearance_compensation_used()) {
      double currOffset = currTrace.get_compensated_half_width(searchTree) + c_offset_add;
      offsetShape = (TileShape) shape.offset(currOffset);
    } else {
      // enlarge the shape in 2 steps  for symmetry reasons
      offsetShape = (TileShape) shape.offset(currTrace.get_half_width());
      double clOffset =
          board.clearance_value(currTrace.clearance_class_no(), clClass, layer) + c_offset_add;
      offsetShape = (TileShape) offsetShape.offset(clOffset);
    }
    int edgeCount = shape.border_line_count();
    int edgeDiff = entries[1].edgeNo - entries[0].edgeNo;

    // calculate the polyline of the substitute trace

    Line[] pieceLines = new Line[edgeDiff + 3];
    // start with the intersecting line of the trace at the start entry.
    pieceLines[0] = entries[0].trace.polyline().arr[entries[0].traceLineNo];
    // end with the intersecting line of the trace at the end entry
    pieceLines[pieceLines.length - 1] = entries[1].trace.polyline().arr[entries[1].traceLineNo];
    // fill the interior lines of pieceLines with the appropriate edge
    // lines of the offset shape
    int currEdgeNo = entries[0].edgeNo % edgeCount;
    for (int i = 1; i < pieceLines.length - 1; i++) {
      pieceLines[i] = offsetShape.border_line(currEdgeNo);
      if (currEdgeNo == edgeCount - 1) {
        currEdgeNo = 0;
      } else {
        ++currEdgeNo;
      }
    }
    Polyline piecePolyline = new Polyline(pieceLines);
    if (piecePolyline.is_empty()) {
      // no valid trace piece, return the next one
      return next_substitute_trace_piece();
    }
    return new PolylineTrace(
        piecePolyline,
        this.layer,
        currTrace.get_half_width(),
        currTrace.netNoArr,
        currTrace.clearance_class_no(),
        0,
        0,
        FixedState.UNFIXED,
        this.board);
  }

  /** returns the maximum recursion depth for shoving the obstacle traces */
  int stack_depth() {
    return maxStackLevel;
  }

  /** returns the number of substitute trace pieces. */
  int substitute_trace_count() {
    return tracePieceCount;
  }

  /**
   * Looks if an unconnected endpoint of a trace of a foreign net is contained in the interior of
   * the shape.
   */
  public boolean trace_tails_in_shape() {
    return this.shapeContainsTraceTails;
  }

  /**
   * Cuts out all traces in p_item_list out of the stored shape. Traces with net number
   * p_except_net_no are ignored
   */
  void cutout_traces(Collection<Item> p_item_list) {
    for (Item currItem : p_item_list) {
      if (currItem instanceof PolylineTrace trace && !currItem.shares_net_no(this.ownNetNos)) {
        cutout_trace(trace, this.shape, this.clClass);
      }
    }
  }

  /** Returns the item responsible for the failing, if the shove algorithm failed. */
  Item get_found_obstacle() {
    return this.foundObstacle;
  }

  /**
   * Stores all intersection points of p_trace with the border of the internal shape enlarged by the
   * half width and the clearance of the corresponding trace pen.
   */
  private boolean store_trace(PolylineTrace p_trace) {
    ShapeSearchTree searchTree = this.board.searchTreeManager.get_default_tree();
    TileShape offsetShape;
    if (searchTree.is_clearance_compensation_used()) {
      double currOffset = p_trace.get_compensated_half_width(searchTree) + c_offset_add;
      offsetShape = (TileShape) shape.offset(currOffset);
    } else {
      // enlarge the shape in 2 steps  for symmetry reasons
      double clOffset =
          board.clearance_value(p_trace.clearance_class_no(), this.clClass, p_trace.get_layer())
              + c_offset_add;
      offsetShape = (TileShape) shape.offset(p_trace.get_half_width());
      offsetShape = (TileShape) offsetShape.offset(clOffset);
    }

    // using enlarge here instead offset causes problems because of a
    // comparison in the constructor of class EntryPoint
    int[][] entries = offsetShape.entrance_points(p_trace.polyline());
    for (int i = 0; i < entries.length; i++) {
      int[] entryTuple = entries[i];
      FloatPoint entryApprox =
          p_trace
              .polyline()
              .arr[entryTuple[0]]
              .intersection_approx(offsetShape.border_line(entryTuple[1]));
      insert_entry_point(p_trace, entryTuple[0], entryTuple[1], entryApprox);
    }

    // Look, if an end point of the trace lies in the interior of
    // the shape. This may be the case, if a via touches the shape

    if (!p_trace.shares_net_no(ownNetNos)) {
      if (!p_trace.nets_normal()) {
        return false;
      }
      Point endCorner = p_trace.first_corner();
      Collection<Item> contactList;
      for (int i = 0; i < 2; i++) {
        if (offsetShape.contains(endCorner)) {
          if (i == 0) {
            contactList = p_trace.get_start_contacts();
          } else {
            contactList = p_trace.get_end_contacts();
          }
          int contactCount = 0;
          boolean storeEndCorner = true;

          // check for contact object, which is not shovable
          for (Item contact_item : contactList) {
            if (!contact_item.is_routable()) {
              this.foundObstacle = contact_item;
              return false;
            }
            if (contact_item instanceof Trace trace) {

              if (contact_item.is_shove_fixed()
                  || trace.get_half_width() != p_trace.get_half_width()
                  || contact_item.clearance_class_no() != p_trace.clearance_class_no()) {
                if (offsetShape.contains_inside(endCorner)) {
                  this.foundObstacle = contact_item;
                  return false;
                }
              }
            } else if (contact_item instanceof Via via) {
              TileShape viaShape = via.get_tile_shape_on_layer(layer);

              double viaTraceDiff =
                  viaShape.smallest_radius() - p_trace.get_compensated_half_width(searchTree);
              if (!searchTree.is_clearance_compensation_used()) {
                int viaClearance =
                    board.clearance_value(
                        contact_item.clearance_class_no(), this.clClass, this.layer);
                int traceClearance =
                    board.clearance_value(p_trace.clearance_class_no(), this.clClass, this.layer);
                if (traceClearance > viaClearance) {
                  viaTraceDiff += viaClearance - traceClearance;
                }
              }
              if (viaTraceDiff < 0) {
                // the via is smaller than the trace
                this.foundObstacle = contact_item;
                return false;
              }

              if (viaTraceDiff == 0 && !offsetShape.contains_inside(endCorner)) {
                // the via need not to be shoved
                storeEndCorner = false;
              }
            }
            ++contactCount;
          }
          if (contactCount == 1 && storeEndCorner) {
            Point projection = offsetShape.nearest_border_point(endCorner);
            {
              int projectionSide = offsetShape.contains_on_border_line_no(projection);
              int traceLineSegmentNo;
              // the following may not be correct because the trace may not contain a suitable
              // line for the construction of the end line of the substitute trace.
              if (i == 0) {
                traceLineSegmentNo = 0;
              } else {
                traceLineSegmentNo = p_trace.polyline().arr.length - 1;
              }

              if (projectionSide >= 0) {
                insert_entry_point(
                    p_trace, traceLineSegmentNo, projectionSide, projection.to_float());
              }
            }
          } else if (contactCount == 0 && offsetShape.contains_inside(endCorner)) {
            shapeContainsTraceTails = true;
          }
        }
        endCorner = p_trace.last_corner();
      }
    }
    this.foundObstacle = p_trace;
    return true;
  }

  private void search_from_side() {
    if (this.fromSide != null && this.fromSide.no >= 0) {
      return; // from side is already legal
    }
    EntryPoint currNode = this.listAnchor;
    int currFromsideNo = 0;
    FloatPoint currEntryApprox = null;
    while (currNode != null) {
      if (currNode.trace.shares_net_no(this.ownNetNos)) {
        currFromsideNo = currNode.edgeNo;
        currEntryApprox = currNode.entryApprox;
        break;
      }
      currNode = currNode.next;
    }
    this.fromSide = new CalcFromSide(currFromsideNo, currEntryApprox);
  }

  /** resorts the intersection points according to fromSideNo and removes redundant points */
  private void resort() {
    int edgeCount = this.shape.border_line_count();
    if (this.fromSide.no < 0 || fromSide.no >= edgeCount) {
      FRLogger.warn("ShapeTraceEntries.resort: from side not calculated");
      return;
    }
    // resort the intersection points, so that they start in the
    // middle of fromSide.
    FloatPoint compareCorner1 = shape.corner_approx(this.fromSide.no);
    FloatPoint compareCorner2;
    if (fromSide.no == edgeCount - 1) {
      compareCorner2 = shape.corner_approx(0);
    } else {
      compareCorner2 = shape.corner_approx(fromSide.no + 1);
    }
    double fromPointDist = 0;
    FloatPoint fromPointProjection = null;
    if (fromSide.borderIntersection != null) {
      fromPointProjection =
          fromSide.borderIntersection.projection_approx(shape.border_line(fromSide.no));
      fromPointDist = fromPointProjection.distance_square(compareCorner1);
      if (fromPointDist >= compareCorner1.distance_square(compareCorner2)) {
        fromSide = new CalcFromSide(fromSide.no, null);
      }
    }
    // search the first intersection point between the side middle
    // and compareCorner2
    EntryPoint curr = listAnchor;
    EntryPoint prev = null;

    while (curr != null) {
      if (curr.edgeNo > this.fromSide.no) {
        break;
      }
      if (curr.edgeNo == fromSide.no) {
        if (fromSide.borderIntersection != null) {
          FloatPoint currProjection =
              curr.entryApprox.projection_approx(shape.border_line(fromSide.no));
          if (currProjection.distance_square(compareCorner1) >= fromPointDist
              && currProjection.distance_square(fromPointProjection)
                  <= currProjection.distance_square(compareCorner1)) {
            break;
          }
        } else {
          if (curr.entryApprox.distance_square(compareCorner2)
              <= curr.entryApprox.distance_square(compareCorner1)) {
            break;
          }
        }
      }
      prev = curr;
      curr = prev.next;
    }
    if (curr != null && curr != listAnchor) {
      EntryPoint newAnchor = curr;

      while (curr != null) {
        prev = curr;
        curr = prev.next;
      }
      prev.next = listAnchor;
      curr = listAnchor;
      while (curr != newAnchor) {
        // add edgeCount to curr.side to differentiate points
        // before and after the middle of fromSide
        curr.edgeNo += edgeCount;
        prev = curr;
        curr = prev.next;
      }
      prev.next = null;
      listAnchor = newAnchor;
    }
    // remove intersections between two other intersections of the same
    // connected set, so that only first and last intersection is kept.
    if (listAnchor == null) {
      return;
    }
    prev = listAnchor;
    int[] prevNetNos = prev.trace.netNoArr;

    curr = listAnchor.next;
    int[] currNetNos;
    EntryPoint next;

    if (curr != null) {
      currNetNos = curr.trace.netNoArr;
      next = curr.next;
    } else {
      next = null;
      currNetNos = new int[0];
    }
    EntryPoint beforePrev = null;
    while (next != null) {
      int[] nextNetNos = next.trace.netNoArr;
      if (net_nos_equal(prevNetNos, currNetNos) && net_nos_equal(currNetNos, nextNetNos)) {
        prev.next = next;
      } else {
        beforePrev = prev;
        prev = curr;
        prevNetNos = currNetNos;
      }
      currNetNos = nextNetNos;
      curr = next;
      next = curr.next;
    }

    // remove nodes of own net at start and end of the list
    if (curr != null && net_nos_equal(currNetNos, ownNetNos)) {
      prev.next = null;
      if (net_nos_equal(prevNetNos, ownNetNos)) {
        if (beforePrev != null) {
          beforePrev.next = null;
        } else {
          listAnchor = null;
        }
      }
    }

    if (listAnchor != null && listAnchor.trace.nets_equal(ownNetNos)) {
      listAnchor = listAnchor.next;

      if (listAnchor != null && listAnchor.trace.nets_equal(ownNetNos)) {
        listAnchor = listAnchor.next;
      }
    }
  }

  private boolean calculate_stack_levels() {
    if (listAnchor == null) {
      return true;
    }
    EntryPoint currEntry = listAnchor;
    int[] currNetNos = currEntry.trace.netNoArr;
    int currLevel;
    if (net_nos_equal(currNetNos, this.ownNetNos)) {
      // ignore own net when calculating the stack level
      currLevel = 0;
    } else {
      currLevel = 1;
    }

    while (currEntry != null) {
      if (currEntry.stackLevel < 0) // not yet calculated
      {
        ++tracePieceCount;
        currEntry.stackLevel = currLevel;
        if (currLevel > maxStackLevel) {
          if (maxStackLevel > 1) {
            this.foundObstacle = currEntry.trace;
          }
          maxStackLevel = currLevel;
        }
      }

      // set stack level for all entries of the current net;
      EntryPoint checkEntry = currEntry.next;
      int indexOfNextForeignSet = 0;
      int indexOfLastOccurrenceOfSet = 0;
      int nextIndex = 0;
      EntryPoint lastOwnEntry = null;
      EntryPoint firstForeignEntry = null;

      while (checkEntry != null) {
        ++nextIndex;
        int[] checkNetNos = checkEntry.trace.netNoArr;
        if (net_nos_equal(checkNetNos, currNetNos)) {
          indexOfLastOccurrenceOfSet = nextIndex;
          lastOwnEntry = checkEntry;
          checkEntry.stackLevel = currEntry.stackLevel;
        } else if (indexOfNextForeignSet == 0) {
          // first occurrence of a foreign connected set
          indexOfNextForeignSet = nextIndex;
          firstForeignEntry = checkEntry;
        }
        checkEntry = checkEntry.next;
      }
      EntryPoint nextEntry;

      if (nextIndex != 0) {
        if (indexOfNextForeignSet != 0 && indexOfNextForeignSet < indexOfLastOccurrenceOfSet)
        // raise level
        {
          nextEntry = firstForeignEntry;
          if (nextEntry.stackLevel >= 0) // already calculated
          {
            // stack property fails
            return false;
          }
          ++currLevel;
        } else {
          if (indexOfLastOccurrenceOfSet != 0) {
            nextEntry = lastOwnEntry;
          } else {
            nextEntry = firstForeignEntry;
            if (nextEntry.stackLevel >= 0) // already calculated
            {
              --currLevel;
              if (nextEntry.stackLevel != currLevel) {
                return false;
              }
            }
          }
        }
        currNetNos = nextEntry.trace.netNoArr;
        // remove all entries between currEntry and nextEntry, because
        // they are irrelevant;
        checkEntry = currEntry.next;
        while (checkEntry != nextEntry) {
          checkEntry = checkEntry.next;
        }
        currEntry.next = nextEntry;
        currEntry = nextEntry;
      } else {
        currEntry = null;
      }
    }
    if (currLevel != 1) {
      FRLogger.warn("ShapeTraceEntries.calculate_stack_levels: currLevel inconsistent");
      return false;
    }
    return true;
  }

  /**
   * Pops the next piece with minimal level from the intersection list Returns null, if the stack is
   * empty. The returned array has 2 elements. The first is the first entry point, and the second is
   * the last entry point of the minimal level.
   */
  private EntryPoint[] pop_piece() {
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
        && afterLast.trace.nets_equal(first.trace)) {
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
    EntryPoint curr = listAnchor;
    while (curr != null) {
      if (curr.stackLevel > maxStackLevel) {
        maxStackLevel = curr.stackLevel;
      }
      curr = curr.next;
    }
    --tracePieceCount;
    if (first.trace.nets_equal(this.ownNetNos)) {
      // own net is ignored and may occur only at the lowest level
      result = pop_piece();
    }
    return result;
  }

  private void insert_entry_point(
      PolylineTrace p_trace, int p_trace_line_no, int p_edge_no, FloatPoint p_entry_approx) {
    EntryPoint newEntry = new EntryPoint(p_trace, p_trace_line_no, p_edge_no, p_entry_approx);
    EntryPoint currPrev = null;
    EntryPoint currNext = listAnchor;
    // insert the new entry into the sorted list
    while (currNext != null) {
      if (currNext.edgeNo > newEntry.edgeNo) {
        break;
      }
      if (currNext.edgeNo == newEntry.edgeNo) {
        FloatPoint prevCorner = shape.corner_approx(p_edge_no);
        FloatPoint nextCorner;
        if (p_edge_no == shape.border_line_count() - 1) {
          nextCorner = shape.corner_approx(0);
        } else {
          nextCorner = shape.corner_approx(newEntry.edgeNo + 1);
        }
        if (prevCorner.scalar_product(p_entry_approx, nextCorner)
            <= prevCorner.scalar_product(currNext.entryApprox, nextCorner))
        // the projection of the line from prevCorner to p_entry_approx
        // onto the line from prevCorner to nextCorner is smaller
        // than the projection of the line from prevCorner to
        // next.entryApprox onto the same line.
        {
          break;
        }
      }
      currPrev = currNext;
      currNext = currNext.next;
    }
    newEntry.next = currNext;
    if (currPrev != null) {
      currPrev.next = newEntry;
    } else {
      listAnchor = newEntry;
    }
  }

  /**
   * Information about an entry point of p_trace into the shape. The entry points are sorted around
   * the border of the shape
   */
  private static class EntryPoint {

    final PolylineTrace trace;
    final int traceLineNo;
    final FloatPoint entryApprox;
    int edgeNo;
    int stackLevel;
    EntryPoint next;

    EntryPoint(
        PolylineTrace p_trace, int p_trace_line_no, int p_edge_no, FloatPoint p_entry_approx) {
      trace = p_trace;
      edgeNo = p_edge_no;
      traceLineNo = p_trace_line_no;
      entryApprox = p_entry_approx;
      stackLevel = -1; // not yet calculated
    }
  }
}
