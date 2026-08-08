package app.freerouting.autoroute;

import app.freerouting.board.ForcedViaAlgo;
import app.freerouting.board.Item;
import app.freerouting.board.ItemSelectionFilter;
import app.freerouting.board.Pin;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Trace;
import app.freerouting.core.Padstack;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.ViaInfo;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/** Inserts the traces and vias of the connection found by the autoroute algorithm. */
public final class InsertFoundConnectionAlgo {

  private final RoutingBoard board;
  private final AutorouteControl ctrl;
  private IntPoint lastCorner;
  private IntPoint firstCorner;

  /** Creates a new instance of InsertFoundConnectionAlgo */
  private InsertFoundConnectionAlgo(RoutingBoard p_board, AutorouteControl p_ctrl) {
    this.board = p_board;
    this.ctrl = p_ctrl;
  }

  /**
   * Creates a new instance of InsertFoundConnectionAlgo . Returns null, if the insertion did not
   * succeed.
   */
  public static InsertFoundConnectionAlgo getInstance(
      LocateFoundConnectionAlgo p_connection, RoutingBoard p_board, AutorouteControl p_ctrl) {
    if (p_connection == null || p_connection.connectionItems == null) {
      return null;
    }
    int currLayer = p_connection.targetLayer;
    InsertFoundConnectionAlgo newInstance = new InsertFoundConnectionAlgo(p_board, p_ctrl);
    for (LocateFoundConnectionAlgoAnyAngle.ResultItem curr_new_item :
        p_connection.connectionItems) {
      if (true) {
        Point startCorner = curr_new_item.corners.length > 0 ? curr_new_item.corners[0] : null;
        Point endCorner =
            curr_new_item.corners.length > 0
                ? curr_new_item.corners[curr_new_item.corners.length - 1]
                : null;
        FRLogger.trace(
            "compare_trace_connection_item_raw net="
                + p_ctrl.netNo
                + ", item_layer="
                + curr_new_item.layer
                + ", cornerCount="
                + curr_new_item.corners.length
                + ", start="
                + formatPoint(startCorner)
                + ", end="
                + formatPoint(endCorner));
      }
      if (!newInstance.insertVia(curr_new_item.corners[0], currLayer, curr_new_item.layer)) {
        return null;
      }
      currLayer = curr_new_item.layer;
      if (!newInstance.insertTrace(curr_new_item)) {
        return null;
      }
    }
    if (!newInstance.insertVia(newInstance.lastCorner, currLayer, p_connection.startLayer)) {
      return null;
    }
    if (p_connection.targetItem instanceof PolylineTrace to_trace) {
      if (newInstance.firstCorner != null) {
        p_board.connectToTrace(
            newInstance.firstCorner,
            to_trace,
            p_ctrl.traceHalfWidth[p_connection.startLayer],
            p_ctrl.traceClearanceClassNo);
      } else {
        FRLogger.warn(
            "InsertFoundConnectionAlgo: firstCorner is null for net #"
                + p_ctrl.netNo
                + ", skipping connect_to_trace for target item. This may indicate a degenerate route segment.");
      }
    }
    if (p_connection.startItem instanceof PolylineTrace to_trace) {
      if (newInstance.lastCorner != null) {
        p_board.connectToTrace(
            newInstance.lastCorner,
            to_trace,
            p_ctrl.traceHalfWidth[p_connection.targetLayer],
            p_ctrl.traceClearanceClassNo);
      } else {
        FRLogger.warn(
            "InsertFoundConnectionAlgo: lastCorner is null for net #"
                + p_ctrl.netNo
                + ", skipping connect_to_trace for start item. This may indicate a degenerate route segment.");
      }
    }

    p_board.normalizeTraces(p_ctrl.netNo);

    return newInstance;
  }

  /**
   * Inserts the trace by shoving aside obstacle traces and vias. Returns false, that was not
   * possible for the whole trace.
   */
  private boolean insertTrace(LocateFoundConnectionAlgoAnyAngle.ResultItem p_trace) {
    if (p_trace.corners.length == 1) {
      // Single-point trace: the start and end are the same location (already at the target).
      // Set both firstCorner and lastCorner so that connect_to_trace is not called with null.
      if (this.firstCorner == null) {
        this.firstCorner = p_trace.corners[0];
      }
      this.lastCorner = p_trace.corners[0];
      return true;
    }
    boolean result = true;

    // switch off correcting connection to pin because it may get wrong in inserting the polygon
    // line for line.
    double savedEdgeToTurnDist = board.rules.getPinEdgeToTurnDist();
    board.rules.setPinEdgeToTurnDist(-1);

    // Look for pins att the start and the end of p_trace in case that neckdown is necessary.
    Pin startPin = null;
    Pin endPin = null;
    if (ctrl.withNeckdown) {
      ItemSelectionFilter itemFilter =
          new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.PINS);
      Point currEndCorner = p_trace.corners[0];
      for (int i = 0; i < 2; i++) {
        Set<Item> pickedItems = this.board.pickItems(currEndCorner, p_trace.layer, itemFilter);
        for (Item currItem : pickedItems) {
          Pin currPin = (Pin) currItem;
          if (currPin.containsNet(ctrl.netNo) && currPin.getCenter().equals(currEndCorner)) {
            if (i == 0) {
              startPin = currPin;
            } else {
              endPin = currPin;
            }
          }
        }
        currEndCorner = p_trace.corners[p_trace.corners.length - 1];
      }
    }
    int[] netNoArr = new int[1];
    netNoArr[0] = ctrl.netNo;

    int fromCornerNo = 0;
    for (int i = 1; i < p_trace.corners.length; i++) {
      Point[] currCornerArr = Arrays.copyOfRange(p_trace.corners, fromCornerNo, i + 1);
      Polyline insertPolyline = new Polyline(currCornerArr);
      int maxItemIdBeforeSeg = board.communication.idNoGenerator.maxGeneratedNo();
      Point okPoint =
          board.insertForcedTracePolyline(
              insertPolyline,
              ctrl.traceHalfWidth[p_trace.layer],
              p_trace.layer,
              netNoArr,
              ctrl.traceClearanceClassNo,
              ctrl.maxShoveTraceRecursionDepth,
              ctrl.maxShoveViaRecursionDepth,
              ctrl.maxSpringOverRecursionDepth,
              Integer.MAX_VALUE,
              ctrl.pullTightAccuracy,
              true,
              null);
      int maxItemIdAfterSeg = board.communication.idNoGenerator.maxGeneratedNo();
      FRLogger.trace(
          "compare_trace_insert_segment_ids net="
              + ctrl.netNo
              + ", i="
              + i
              + ", maxItemIdBefore="
              + maxItemIdBeforeSeg
              + ", maxItemIdAfter="
              + maxItemIdAfterSeg
              + ", delta="
              + (maxItemIdAfterSeg - maxItemIdBeforeSeg));
      boolean neckdownInserted = false;
      boolean microNeckdownInserted = false;
      if (okPoint != null
          && okPoint != insertPolyline.lastCorner()
          && ctrl.withNeckdown
          && currCornerArr.length == 2) {
        neckdownInserted =
            insertNeckdown(okPoint, currCornerArr[1], p_trace.layer, startPin, endPin);
      }
      if (!neckdownInserted
          && okPoint != insertPolyline.lastCorner()
          && ctrl.isFanout
          && currCornerArr.length == 2) {
        microNeckdownInserted =
            insertFanoutMicroNeckdown(
                okPoint, currCornerArr[1], p_trace.layer, netNoArr, startPin, endPin);
      }
      if (okPoint == insertPolyline.lastCorner() || neckdownInserted || microNeckdownInserted) {
        fromCornerNo = i;
        if (true) {
          FRLogger.trace(
              "compare_trace_insert_segment_raw net="
                  + ctrl.netNo
                  + ", layer="
                  + p_trace.layer
                  + ", i="
                  + i
                  + ", fromCornerNo="
                  + fromCornerNo
                  + ", decision=ADVANCE, neckdown="
                  + neckdownInserted
                  + ", micro_neckdown="
                  + microNeckdownInserted
                  + ", okPoint="
                  + formatPoint(okPoint)
                  + ", first="
                  + formatPoint(insertPolyline.firstCorner())
                  + ", last="
                  + formatPoint(insertPolyline.lastCorner()));
          FRLogger.trace(
              "InsertFoundConnectionAlgo.insert_trace",
              "compare_trace_insert_segment",
              "net="
                  + ctrl.netNo
                  + ", layer="
                  + p_trace.layer
                  + ", i="
                  + i
                  + ", fromCornerNo="
                  + fromCornerNo
                  + ", decision=ADVANCE, neckdown="
                  + neckdownInserted
                  + ", micro_neckdown="
                  + microNeckdownInserted
                  + ", okPoint="
                  + okPoint
                  + ", first="
                  + insertPolyline.firstCorner()
                  + ", last="
                  + insertPolyline.lastCorner(),
              "Net #" + ctrl.netNo,
              new Point[0]);
        }
      } else if (okPoint == insertPolyline.firstCorner() && i != p_trace.corners.length - 1) {
        // if okPoint == insertPolyline.first_corner() the spring over may have failed.
        // Spring over may correct the situation because an insertion, which is ok with clearance
        // compensation
        // may cause violations without clearance compensation.
        // In this case repeating the insertion with more distant corners may allow the spring_over
        // to correct the situation.
        if (fromCornerNo > 0) {
          // p_trace.corners[i] may be inside the offset for the substitute trace around
          // a spring_over obstacle (if clearance compensation is off).
          if (currCornerArr.length < 3) {
            // first correction
            --fromCornerNo;
          }
        }
        FRLogger.trace("InsertFoundConnectionAlgo: violation corrected");
        if (true) {
          FRLogger.trace(
              "compare_trace_insert_segment_raw net="
                  + ctrl.netNo
                  + ", layer="
                  + p_trace.layer
                  + ", i="
                  + i
                  + ", fromCornerNo="
                  + fromCornerNo
                  + ", decision=VIOLATION_CORRECTED, neckdown="
                  + neckdownInserted
                  + ", okPoint="
                  + formatPoint(okPoint)
                  + ", first="
                  + formatPoint(insertPolyline.firstCorner())
                  + ", last="
                  + formatPoint(insertPolyline.lastCorner()));
          FRLogger.trace(
              "InsertFoundConnectionAlgo.insert_trace",
              "compare_trace_insert_segment",
              "net="
                  + ctrl.netNo
                  + ", layer="
                  + p_trace.layer
                  + ", i="
                  + i
                  + ", fromCornerNo="
                  + fromCornerNo
                  + ", decision=VIOLATION_CORRECTED, neckdown="
                  + neckdownInserted
                  + ", okPoint="
                  + okPoint
                  + ", first="
                  + insertPolyline.firstCorner()
                  + ", last="
                  + insertPolyline.lastCorner(),
              "Net #" + ctrl.netNo,
              new Point[0]);
        }
      } else {
        FRLogger.debug(
            "InsertFoundConnectionAlgo: insert trace failed for net #"
                + ctrl.netNo
                + " at corner "
                + i
                + "/"
                + (p_trace.corners.length - 1)
                + " on layer "
                + p_trace.layer
                + ", trace width: "
                + ctrl.traceHalfWidth[p_trace.layer]
                + ", from corner: "
                + fromCornerNo
                + ", okPoint: "
                + (okPoint != null ? okPoint.toString() : "null")
                + ", target: "
                + insertPolyline.lastCorner());
        traceFanoutDiagnostic(
            "trace_insert_failed",
            "layer="
                + p_trace.layer
                + ", corner_index="
                + i
                + ", from_corner_index="
                + fromCornerNo
                + ", traceHalfWidth="
                + ctrl.traceHalfWidth[p_trace.layer]
                + ", traceClearanceClass="
                + ctrl.traceClearanceClassNo
                + ", start_pin_clearance_class="
                + (startPin != null ? startPin.clearanceClassNo() : -1)
                + ", end_pin_clearance_class="
                + (endPin != null ? endPin.clearanceClassNo() : -1)
                + ", okPoint="
                + formatPoint(okPoint)
                + ", target="
                + formatPoint(insertPolyline.lastCorner()));
        if (true) {
          FRLogger.trace(
              "compare_trace_insert_segment_raw net="
                  + ctrl.netNo
                  + ", layer="
                  + p_trace.layer
                  + ", i="
                  + i
                  + ", fromCornerNo="
                  + fromCornerNo
                  + ", decision=FAIL, neckdown="
                  + neckdownInserted
                  + ", micro_neckdown="
                  + microNeckdownInserted
                  + ", okPoint="
                  + formatPoint(okPoint)
                  + ", first="
                  + formatPoint(insertPolyline.firstCorner())
                  + ", last="
                  + formatPoint(insertPolyline.lastCorner()));
          FRLogger.trace(
              "InsertFoundConnectionAlgo.insert_trace",
              "compare_trace_insert_segment",
              "net="
                  + ctrl.netNo
                  + ", layer="
                  + p_trace.layer
                  + ", i="
                  + i
                  + ", fromCornerNo="
                  + fromCornerNo
                  + ", decision=FAIL, neckdown="
                  + neckdownInserted
                  + ", micro_neckdown="
                  + microNeckdownInserted
                  + ", okPoint="
                  + okPoint
                  + ", first="
                  + insertPolyline.firstCorner()
                  + ", last="
                  + insertPolyline.lastCorner(),
              "Net #" + ctrl.netNo,
              new Point[0]);
        }
        result = false;
        break;
      }
    }

    int removedTraceStubs = 0;
    for (int i = 0; i < p_trace.corners.length - 1; i++) {
      Trace traceStub = board.getTraceTail(p_trace.corners[i], p_trace.layer, netNoArr);
      if (traceStub != null) {
        FRLogger.trace(
            "compare_trace_stub_found net="
                + ctrl.netNo
                + ", corner_idx="
                + i
                + ", corner="
                + p_trace.corners[i]
                + ", stub_id="
                + traceStub.getIdNo()
                + ", stub_first="
                + traceStub.firstCorner()
                + ", stub_last="
                + traceStub.lastCorner()
                + ", startContacts="
                + traceStub.getStartContacts().size()
                + ", endContacts="
                + traceStub.getEndContacts().size());
        board.removeItem(traceStub);
        removedTraceStubs++;
      }
    }

    FRLogger.trace(
        "InsertFoundConnectionAlgo.insert_trace",
        "compare_trace_stub_cleanup",
        "net="
            + ctrl.netNo
            + ", layer="
            + p_trace.layer
            + ", removed_stubs="
            + removedTraceStubs
            + ", trace_enabled="
            + FRLogger.isTraceEnabled(),
        "Net #" + ctrl.netNo,
        new Point[0]);

    board.rules.setPinEdgeToTurnDist(savedEdgeToTurnDist);
    if (this.firstCorner == null) {
      this.firstCorner = p_trace.corners[0];
    }
    this.lastCorner = p_trace.corners[p_trace.corners.length - 1];
    return result;
  }

  private boolean insertFanoutMicroNeckdown(
      Point okPoint, Point target_point, int layer, int[] netNoArr, Pin startPin, Pin endPin) {
    Point fromPoint = okPoint != null ? okPoint : target_point;
    if (fromPoint == null || target_point == null || fromPoint.equals(target_point)) {
      return false;
    }
    int baseHalfWidth = ctrl.traceHalfWidth[layer];
    LinkedHashSet<Integer> candidateHalfWidths = new LinkedHashSet<>();
    if (startPin != null && startPin.isOnLayer(layer)) {
      candidateHalfWidths.add(startPin.getTraceNeckdownHalfwidth(layer));
    }
    if (endPin != null && endPin.isOnLayer(layer)) {
      candidateHalfWidths.add(endPin.getTraceNeckdownHalfwidth(layer));
    }
    candidateHalfWidths.add(Math.max(1, (baseHalfWidth * 3) / 4));
    candidateHalfWidths.add(Math.max(1, (baseHalfWidth * 3) / 5));
    candidateHalfWidths.add(Math.max(1, baseHalfWidth / 2));

    for (int candidate_half_width : candidateHalfWidths) {
      if (candidate_half_width <= 0 || candidate_half_width >= baseHalfWidth) {
        continue;
      }
      Point candidateOkPoint =
          board.insertForcedTraceSegment(
              fromPoint,
              target_point,
              candidate_half_width,
              layer,
              netNoArr,
              ctrl.traceClearanceClassNo,
              ctrl.maxShoveTraceRecursionDepth,
              ctrl.maxShoveViaRecursionDepth,
              ctrl.maxSpringOverRecursionDepth,
              Integer.MAX_VALUE,
              ctrl.pullTightAccuracy,
              true,
              null);
      if (candidateOkPoint == target_point) {
        traceFanoutDiagnostic(
            "trace_insert_micro_neckdown_success",
            "layer="
                + layer
                + ", candidate_half_width="
                + candidate_half_width
                + ", baseHalfWidth="
                + baseHalfWidth
                + ", traceClearanceClass="
                + ctrl.traceClearanceClassNo
                + ", from="
                + formatPoint(fromPoint)
                + ", to="
                + formatPoint(target_point));
        return true;
      }
    }
    traceFanoutDiagnostic(
        "trace_insert_micro_neckdown_failed",
        "layer="
            + layer
            + ", baseHalfWidth="
            + baseHalfWidth
            + ", traceClearanceClass="
            + ctrl.traceClearanceClassNo
            + ", from="
            + formatPoint(fromPoint)
            + ", to="
            + formatPoint(target_point));
    return false;
  }

  boolean insertNeckdown(
      Point p_from_corner, Point p_to_corner, int p_layer, Pin p_start_pin, Pin p_end_pin) {
    if (p_start_pin != null) {
      Point okPoint = tryNeckDown(p_to_corner, p_from_corner, p_layer, p_start_pin, true);
      if (okPoint == p_from_corner) {
        return true;
      }
    }
    if (p_end_pin != null) {
      Point okPoint = tryNeckDown(p_from_corner, p_to_corner, p_layer, p_end_pin, false);
      return okPoint == p_to_corner;
    }
    return false;
  }

  private Point tryNeckDown(
      Point p_from_corner, Point p_to_corner, int p_layer, Pin p_pin, boolean p_at_start) {
    if (!p_pin.isOnLayer(p_layer)) {
      return null;
    }
    FloatPoint pinCenter = p_pin.getCenter().toFloat();
    double currClearance =
        this.board.rules.clearanceMatrix.getValue(
            ctrl.traceClearanceClassNo, p_pin.clearanceClassNo(), p_layer, true);
    double pinNeckDownDistance = 2 * (0.5 * p_pin.getMaxWidth(p_layer) + currClearance);
    if (pinCenter.distance(p_to_corner.toFloat()) >= pinNeckDownDistance) {
      return null;
    }

    int neckDownHalfwidth = p_pin.getTraceNeckdownHalfwidth(p_layer);
    if (neckDownHalfwidth >= ctrl.traceHalfWidth[p_layer]) {
      return null;
    }

    FloatPoint floatFromCorner = p_from_corner.toFloat();
    FloatPoint floatToCorner = p_to_corner.toFloat();

    final int TOLERANCE = 2;

    int[] netNoArr = new int[1];
    netNoArr[0] = ctrl.netNo;

    double okLength =
        board.checkTraceSegment(
            p_from_corner,
            p_to_corner,
            p_layer,
            netNoArr,
            ctrl.traceHalfWidth[p_layer],
            ctrl.traceClearanceClassNo,
            true);
    if (okLength >= Integer.MAX_VALUE) {
      return p_from_corner;
    }
    okLength -= TOLERANCE;
    Point neckDownEndPoint;
    if (okLength <= TOLERANCE) {
      neckDownEndPoint = p_from_corner;
    } else {
      FloatPoint floatNeckDownEndPoint = floatFromCorner.changeLength(floatToCorner, okLength);
      neckDownEndPoint = floatNeckDownEndPoint.round();
      // add a corner in case  neckDownEndPoint is not exactly on the line from p_from_corner to
      // p_to_corner
      boolean horizontalFirst =
          Math.abs(floatFromCorner.x - floatNeckDownEndPoint.x)
              >= Math.abs(floatFromCorner.y - floatNeckDownEndPoint.y);
      IntPoint addCorner =
          LocateFoundConnectionAlgo.calculateAdditionalCorner(
                  floatFromCorner,
                  floatNeckDownEndPoint,
                  horizontalFirst,
                  board.rules.getTraceAngleRestriction())
              .round();
      Point currOkPoint =
          board.insertForcedTraceSegment(
              p_from_corner,
              addCorner,
              ctrl.traceHalfWidth[p_layer],
              p_layer,
              netNoArr,
              ctrl.traceClearanceClassNo,
              ctrl.maxShoveTraceRecursionDepth,
              ctrl.maxShoveViaRecursionDepth,
              ctrl.maxSpringOverRecursionDepth,
              Integer.MAX_VALUE,
              ctrl.pullTightAccuracy,
              true,
              null);
      if (currOkPoint != addCorner) {
        return p_from_corner;
      }
      currOkPoint =
          board.insertForcedTraceSegment(
              addCorner,
              neckDownEndPoint,
              ctrl.traceHalfWidth[p_layer],
              p_layer,
              netNoArr,
              ctrl.traceClearanceClassNo,
              ctrl.maxShoveTraceRecursionDepth,
              ctrl.maxShoveViaRecursionDepth,
              ctrl.maxSpringOverRecursionDepth,
              Integer.MAX_VALUE,
              ctrl.pullTightAccuracy,
              true,
              null);
      if (currOkPoint != neckDownEndPoint) {
        return p_from_corner;
      }
      addCorner =
          LocateFoundConnectionAlgo.calculateAdditionalCorner(
                  floatNeckDownEndPoint,
                  floatToCorner,
                  !horizontalFirst,
                  board.rules.getTraceAngleRestriction())
              .round();
      if (!addCorner.equals(p_to_corner)) {
        currOkPoint =
            board.insertForcedTraceSegment(
                neckDownEndPoint,
                addCorner,
                ctrl.traceHalfWidth[p_layer],
                p_layer,
                netNoArr,
                ctrl.traceClearanceClassNo,
                ctrl.maxShoveTraceRecursionDepth,
                ctrl.maxShoveViaRecursionDepth,
                ctrl.maxSpringOverRecursionDepth,
                Integer.MAX_VALUE,
                ctrl.pullTightAccuracy,
                true,
                null);
        if (currOkPoint != addCorner) {
          return p_from_corner;
        }
        neckDownEndPoint = addCorner;
      }
    }

    return board.insertForcedTraceSegment(
        neckDownEndPoint,
        p_to_corner,
        neckDownHalfwidth,
        p_layer,
        netNoArr,
        ctrl.traceClearanceClassNo,
        ctrl.maxShoveTraceRecursionDepth,
        ctrl.maxShoveViaRecursionDepth,
        ctrl.maxSpringOverRecursionDepth,
        Integer.MAX_VALUE,
        ctrl.pullTightAccuracy,
        true,
        null);
  }

  /**
   * Searches the cheapest via masks containing p_from_layer and p_to_layer, so that a forced via is
   * possible at p_location with this mask and inserts the via. Returns false, if no suitable via
   * mask was found or if the algorithm failed.
   */
  private boolean insertVia(Point p_location, int p_from_layer, int p_to_layer) {
    if (p_from_layer == p_to_layer) {
      return true; // no via necessary
    }
    int fromLayer;
    int toLayer;
    // sort the input layers
    if (p_from_layer < p_to_layer) {
      fromLayer = p_from_layer;
      toLayer = p_to_layer;
    } else {
      fromLayer = p_to_layer;
      toLayer = p_from_layer;
    }
    int[] netNoArr = new int[1];
    netNoArr[0] = ctrl.netNo;
    ViaInfo viaInfo = null;
    boolean foundSuitableSpan = false;
    for (int i = 0; i < this.ctrl.viaRule.viaCount(); i++) {
      ViaInfo currViaInfo = this.ctrl.viaRule.getVia(i);
      Padstack currViaPadstack = currViaInfo.getPadstack();
      if (currViaPadstack.fromLayer() > fromLayer || currViaPadstack.toLayer() < toLayer) {
        continue;
      }
      foundSuitableSpan = true;
      if (ForcedViaAlgo.check(
          currViaInfo,
          p_location,
          netNoArr,
          this.ctrl.maxShoveTraceRecursionDepth,
          this.ctrl.maxShoveViaRecursionDepth,
          this.board,
          this.ctrl.traceHalfWidth,
          this.ctrl.traceClearanceClassNo)) {
        viaInfo = currViaInfo;
        break;
      }
    }
    if (viaInfo == null) {
      if (!foundSuitableSpan) {
        FRLogger.debug(
            "InsertFoundConnectionAlgo: via mask not found for net #"
                + ctrl.netNo
                + " covering layers "
                + fromLayer
                + " to "
                + toLayer);
      } else {
        FRLogger.debug(
            "InsertFoundConnectionAlgo: via placement blocked by clearance/shove limits for net #"
                + ctrl.netNo);
      }
      traceFanoutDiagnostic(
          "via_mask_not_found",
          "fromLayer="
              + fromLayer
              + ", toLayer="
              + toLayer
              + ", location="
              + formatPoint(p_location)
              + ", traceClearanceClass="
              + ctrl.traceClearanceClassNo
              + ", viaClearanceClass="
              + ctrl.viaClearanceClass
              + ", trace_half_width_from="
              + ctrl.traceHalfWidth[fromLayer]
              + ", trace_half_width_to="
              + ctrl.traceHalfWidth[toLayer]);
      return false;
    }
    // insert the via
    if (!ForcedViaAlgo.insert(
        viaInfo,
        p_location,
        netNoArr,
        this.ctrl.traceClearanceClassNo,
        this.ctrl.traceHalfWidth,
        this.ctrl.maxShoveTraceRecursionDepth,
        this.ctrl.maxShoveViaRecursionDepth,
        this.board)) {
      FRLogger.debug("InsertFoundConnectionAlgo: forced via failed for net #" + ctrl.netNo);
      traceFanoutDiagnostic(
          "forced_via_insert_failed",
          "fromLayer="
              + fromLayer
              + ", toLayer="
              + toLayer
              + ", location="
              + formatPoint(p_location)
              + ", selected_via_clearance_class="
              + viaInfo.getClearanceClass()
              + ", selected_via_padstack="
              + viaInfo.getPadstack().name
              + ", traceClearanceClass="
              + ctrl.traceClearanceClassNo
              + ", trace_half_width_from="
              + ctrl.traceHalfWidth[fromLayer]
              + ", trace_half_width_to="
              + ctrl.traceHalfWidth[toLayer]);
      return false;
    }
    return true;
  }

  private boolean shouldTraceFanoutDiagnostics() {
    return ctrl.isFanout
        && ctrl.fanoutStartPinName != null
        && ctrl.fanoutStartPinName.startsWith("U27-");
  }

  private void traceFanoutDiagnostic(String event, String message) {
    if (!shouldTraceFanoutDiagnostics()) {
      return;
    }
    FRLogger.trace(
        "FANOUT_DIAG event="
            + event
            + ", pin="
            + ctrl.fanoutStartPinName
            + ", net="
            + ctrl.netNo
            + ", "
            + message);
  }

  private static String formatPoint(Point point) {
    if (point == null) {
      return "null";
    }
    if (point instanceof IntPoint intPoint) {
      return "(" + intPoint.x + "," + intPoint.y + ")";
    }
    return point.toString();
  }
}
