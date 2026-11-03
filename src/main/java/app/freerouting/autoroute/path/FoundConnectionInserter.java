package app.freerouting.autoroute.path;

import app.freerouting.autoroute.maze.AutorouteControl;
import app.freerouting.board.actions.ForcedViaInserter;
import app.freerouting.board.actions.ItemSelectionFilter;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.DrillItem;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.core.library.Padstack;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.ViaInfo;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/** Inserts the traces and vias of the connection found by the autoroute algorithm. */
public final class FoundConnectionInserter {

  private final RoutingBoard board;
  private final AutorouteControl ctrl;
  private IntPoint lastCorner;
  private IntPoint firstCorner;

  /** Creates a new instance of FoundConnectionInserter. */
  private FoundConnectionInserter(RoutingBoard board, AutorouteControl ctrl) {
    this.board = board;
    this.ctrl = ctrl;
  }

  /**
   * Creates a new instance of FoundConnectionInserter. Returns null if the insertion did not
   * succeed.
   */
  public static FoundConnectionInserter getInstance(
      FoundConnectionLocator connection, RoutingBoard board, AutorouteControl ctrl) {
    if (connection == null || connection.connectionItems == null) {
      return null;
    }
    PlannedConnection.Segment[] segments =
        new PlannedConnection.Segment[connection.connectionItems.size()];
    int segmentIndex = 0;
    for (FoundConnectionLocator.ResultItem item : connection.connectionItems) {
      segments[segmentIndex++] = new PlannedConnection.Segment(item.corners, item.layer);
    }
    return insertSegments(
        board,
        ctrl,
        connection.startItem,
        connection.targetItem,
        connection.startLayer,
        connection.targetLayer,
        segments);
  }

  /**
   * Inserts an already found connection. {@code startItem} and {@code targetItem} belong to {@code
   * board}. Returns null when insertion or endpoint attachment fails, or when the final copper does
   * not connect both terminal components.
   */
  public static FoundConnectionInserter insertSegments(
      RoutingBoard board,
      AutorouteControl ctrl,
      Item startItem,
      Item targetItem,
      int startLayer,
      int targetLayer,
      PlannedConnection.Segment[] segments) {
    if (!isContinuousPlan(segments, board.getLayerCount())
        || startLayer < 0
        || startLayer >= board.getLayerCount()
        || targetLayer < 0
        || targetLayer >= board.getLayerCount()) {
      return null;
    }
    FoundConnectionInserter newInstance = new FoundConnectionInserter(board, ctrl);
    PlannedConnection.Segment lastSegment = segments[segments.length - 1];
    TerminalAnchor startAnchor =
        newInstance.captureTerminal(
            startItem, lastSegment.corners[lastSegment.corners.length - 1], startLayer, false);
    TerminalAnchor targetAnchor =
        newInstance.captureTerminal(targetItem, segments[0].corners[0], targetLayer, ctrl.isFanout);
    if (startAnchor == null || targetAnchor == null) {
      return null;
    }
    int currentLayer = targetLayer;
    for (PlannedConnection.Segment currentNewItem : segments) {
      if (true) {
        Point startCorner = currentNewItem.corners.length > 0 ? currentNewItem.corners[0] : null;
        Point endCorner =
            currentNewItem.corners.length > 0
                ? currentNewItem.corners[currentNewItem.corners.length - 1]
                : null;
        FRLogger.trace(
            "compare_trace_connection_item_raw net="
                + ctrl.netNumber
                + ", item_layer="
                + currentNewItem.layer
                + ", cornerCount="
                + currentNewItem.corners.length
                + ", start="
                + formatPoint(startCorner)
                + ", end="
                + formatPoint(endCorner));
      }
      if (!newInstance.insertVia(currentNewItem.corners[0], currentLayer, currentNewItem.layer)) {
        return null;
      }
      currentLayer = currentNewItem.layer;
      if (!newInstance.insertTrace(currentNewItem)) {
        return null;
      }
    }
    if (!newInstance.insertVia(newInstance.lastCorner, currentLayer, startLayer)) {
      return null;
    }
    if (targetItem instanceof PolylineTrace toTrace
        && !board.connectToTrace(
            newInstance.firstCorner,
            toTrace,
            ctrl.traceHalfWidth[targetLayer],
            ctrl.traceClearanceClassIndex)) {
      return null;
    }
    if (startItem instanceof PolylineTrace toTrace
        && !board.connectToTrace(
            newInstance.lastCorner,
            toTrace,
            ctrl.traceHalfWidth[startLayer],
            ctrl.traceClearanceClassIndex)) {
      return null;
    }

    // A new bridge can look like a tail until its exact endpoint attachments are complete.
    for (PlannedConnection.Segment segment : segments) {
      newInstance.removeTraceStubs(segment);
    }

    board.normalizeTraces(ctrl.netNumber);
    if (!newInstance.connectsTerminals(startAnchor, targetAnchor)) {
      FRLogger.trace(
          "FoundConnectionInserter.insertSegments",
          "connection_postcondition",
          "Inserted copper does not connect both terminal components",
          "Net #" + ctrl.netNumber,
          new Point[] {startAnchor.point(), targetAnchor.point()});
      return null;
    }
    return newInstance;
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

  /**
   * Inserts the trace by shoving aside obstacle traces and vias. Returns false, that was not
   * possible for the whole trace.
   */
  private boolean insertTrace(PlannedConnection.Segment trace) {
    if (trace.corners.length == 1) {
      // Single-point trace: the start and end are the same location (already at the target).
      // Set both firstCorner and lastCorner so that connect_to_trace is not called with null.
      if (this.firstCorner == null) {
        this.firstCorner = trace.corners[0];
      }
      this.lastCorner = trace.corners[0];
      return true;
    }

    // switch off correcting connection to pin because it may get wrong in inserting the polygon
    // line for line.
    final double savedEdgeToTurnDist = board.rules.getPinEdgeToTurnDist();
    board.rules.setPinEdgeToTurnDist(-1);

    // Look for pins att the start and the end of trace in case that neckdown is necessary.
    Pin startPin = null;
    Pin endPin = null;
    if (ctrl.withNeckdown) {
      ItemSelectionFilter itemFilter =
          new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.PINS);
      Point currentEndCorner = trace.corners[0];
      for (int i = 0; i < 2; i++) {
        Set<Item> pickedItems = this.board.pickItems(currentEndCorner, trace.layer, itemFilter);
        for (Item currentItem : pickedItems) {
          Pin currentPin = (Pin) currentItem;
          if (currentPin.containsNet(ctrl.netNumber)
              && currentPin.getCenter().equals(currentEndCorner)) {
            if (i == 0) {
              startPin = currentPin;
            } else {
              endPin = currentPin;
            }
          }
        }
        currentEndCorner = trace.corners[trace.corners.length - 1];
      }
    }
    int[] netNumbers = new int[1];
    netNumbers[0] = ctrl.netNumber;

    int fromCornerNo = 0;
    boolean result = true;
    for (int i = 1; i < trace.corners.length; i++) {
      Point[] currentCornerArr = Arrays.copyOfRange(trace.corners, fromCornerNo, i + 1);
      Polyline insertPolyline = new Polyline(currentCornerArr);
      int maxItemIdBeforeSeg = board.communication.idGenerator.maxGeneratedId();
      Point okPoint =
          board.insertForcedTracePolyline(
              insertPolyline,
              ctrl.traceHalfWidth[trace.layer],
              trace.layer,
              netNumbers,
              ctrl.traceClearanceClassIndex,
              ctrl.maxShoveTraceRecursionDepth,
              ctrl.maxShoveViaRecursionDepth,
              ctrl.maxSpringOverRecursionDepth,
              Integer.MAX_VALUE,
              ctrl.pullTightAccuracy,
              true,
              null);
      int maxItemIdAfterSeg = board.communication.idGenerator.maxGeneratedId();
      FRLogger.trace(
          "compare_trace_insert_segment_ids net="
              + ctrl.netNumber
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
          && currentCornerArr.length == 2) {
        neckdownInserted =
            insertNeckdown(okPoint, currentCornerArr[1], trace.layer, startPin, endPin);
      }
      // The micro-neckdown narrows the trace below its net-class width, so it is part of automatic
      // neckdown and must stay off when the user disabled it, fanout or not (issue #973).
      if (!neckdownInserted
          && okPoint != insertPolyline.lastCorner()
          && ctrl.withNeckdown
          && currentCornerArr.length == 2) {
        microNeckdownInserted =
            insertFanoutMicroNeckdown(
                okPoint, currentCornerArr[1], trace.layer, netNumbers, startPin, endPin);
      }
      if (okPoint == insertPolyline.lastCorner() || neckdownInserted || microNeckdownInserted) {
        fromCornerNo = i;
        if (true) {
          FRLogger.trace(
              "compare_trace_insert_segment_raw net="
                  + ctrl.netNumber
                  + ", layer="
                  + trace.layer
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
              "FoundConnectionInserter.insert_trace",
              "compare_trace_insert_segment",
              "net="
                  + ctrl.netNumber
                  + ", layer="
                  + trace.layer
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
              "Net #" + ctrl.netNumber,
              new Point[0]);
        }
      } else if (okPoint == insertPolyline.firstCorner() && i != trace.corners.length - 1) {
        // if okPoint == insertPolyline.firstCorner() the spring over may have failed.
        // Spring over may correct the situation because an insertion, which is ok with clearance
        // compensation
        // may cause violations without clearance compensation.
        // In this case repeating the insertion with more distant corners may allow the spring over
        // to correct the situation.
        if (fromCornerNo > 0) {
          // trace.corners[i] may be inside the offset for the substitute trace around
          // a spring_over obstacle (if clearance compensation is off).
          if (currentCornerArr.length < 3) {
            // first correction
            --fromCornerNo;
          }
        }
        FRLogger.trace("FoundConnectionInserter: violation corrected");
        if (true) {
          FRLogger.trace(
              "compare_trace_insert_segment_raw net="
                  + ctrl.netNumber
                  + ", layer="
                  + trace.layer
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
              "FoundConnectionInserter.insert_trace",
              "compare_trace_insert_segment",
              "net="
                  + ctrl.netNumber
                  + ", layer="
                  + trace.layer
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
              "Net #" + ctrl.netNumber,
              new Point[0]);
        }
      } else {
        FRLogger.debug(
            "FoundConnectionInserter: insert trace failed for net #"
                + ctrl.netNumber
                + " at corner "
                + i
                + "/"
                + (trace.corners.length - 1)
                + " on layer "
                + trace.layer
                + ", trace width: "
                + ctrl.traceHalfWidth[trace.layer]
                + ", from corner: "
                + fromCornerNo
                + ", okPoint: "
                + (okPoint != null ? okPoint.toString() : "null")
                + ", target: "
                + insertPolyline.lastCorner());
        traceFanoutDiagnostic(
            "trace_insert_failed",
            "layer="
                + trace.layer
                + ", corner_index="
                + i
                + ", from_corner_index="
                + fromCornerNo
                + ", traceHalfWidth="
                + ctrl.traceHalfWidth[trace.layer]
                + ", traceClearanceClass="
                + ctrl.traceClearanceClassIndex
                + ", start_pin_clearance_class="
                + (startPin != null ? startPin.clearanceClassIndex() : -1)
                + ", end_pin_clearance_class="
                + (endPin != null ? endPin.clearanceClassIndex() : -1)
                + ", okPoint="
                + formatPoint(okPoint)
                + ", target="
                + formatPoint(insertPolyline.lastCorner()));
        if (true) {
          FRLogger.trace(
              "compare_trace_insert_segment_raw net="
                  + ctrl.netNumber
                  + ", layer="
                  + trace.layer
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
              "FoundConnectionInserter.insert_trace",
              "compare_trace_insert_segment",
              "net="
                  + ctrl.netNumber
                  + ", layer="
                  + trace.layer
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
              "Net #" + ctrl.netNumber,
              new Point[0]);
        }
        result = false;
        break;
      }
    }

    board.rules.setPinEdgeToTurnDist(savedEdgeToTurnDist);
    if (this.firstCorner == null) {
      this.firstCorner = trace.corners[0];
    }
    this.lastCorner = trace.corners[trace.corners.length - 1];
    return result;
  }

  private void removeTraceStubs(PlannedConnection.Segment trace) {
    int[] netNumbers = {ctrl.netNumber};
    int removedTraceStubs = 0;
    for (int i = 0; i < trace.corners.length - 1; i++) {
      Trace traceStub = board.getTraceTail(trace.corners[i], trace.layer, netNumbers);
      if (traceStub != null) {
        FRLogger.trace(
            "compare_trace_stub_found net="
                + ctrl.netNumber
                + ", corner_idx="
                + i
                + ", corner="
                + trace.corners[i]
                + ", stub_id="
                + traceStub.getId()
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
        "FoundConnectionInserter.insert_trace",
        "compare_trace_stub_cleanup",
        "net="
            + ctrl.netNumber
            + ", layer="
            + trace.layer
            + ", removed_stubs="
            + removedTraceStubs
            + ", trace_enabled="
            + FRLogger.isTraceEnabled(),
        "Net #" + ctrl.netNumber,
        new Point[0]);
  }

  private static boolean isContinuousPlan(PlannedConnection.Segment[] segments, int layerCount) {
    if (segments == null || segments.length == 0) {
      return false;
    }
    IntPoint previousEnd = null;
    for (PlannedConnection.Segment segment : segments) {
      if (segment == null
          || segment.corners == null
          || segment.corners.length == 0
          || segment.layer < 0
          || segment.layer >= layerCount) {
        return false;
      }
      for (IntPoint corner : segment.corners) {
        if (corner == null) {
          return false;
        }
      }
      if (previousEnd != null && !previousEnd.equals(segment.corners[0])) {
        return false;
      }
      previousEnd = segment.corners[segment.corners.length - 1];
    }
    return true;
  }

  private TerminalAnchor captureTerminal(
      Item item, IntPoint endpoint, int layer, boolean fanoutEnd) {
    if (item == null) {
      return fanoutEnd ? new TerminalAnchor(Set.of(), endpoint, layer, true) : null;
    }
    if (!item.isOnTheBoard() || !item.containsNet(ctrl.netNumber) || !item.isOnLayer(layer)) {
      return null;
    }
    Point anchor = endpoint;
    if (item instanceof PolylineTrace trace && !trace.contains(endpoint)) {
      var projection = trace.polyline().projectionLine(endpoint);
      if (projection == null) {
        return null;
      }
      anchor = projection.endPoint();
    } else if (item instanceof DrillItem drill) {
      anchor = drill.getCenter();
    }
    // Even a fixed terminal can have mutable branches. Preserve reachability of every surviving
    // member, while allowing trace splitting, combining and legitimate via movement.
    Set<Item> component = terminalComponent(item);
    return new TerminalAnchor(component, anchor, layer, false);
  }

  private Set<Item> resolveTerminal(TerminalAnchor anchor) {
    Set<Item> live = new TreeSet<>();
    for (Item item : anchor.component()) {
      if (item.isOnTheBoard() && item.containsNet(ctrl.netNumber)) {
        live.add(item);
      }
    }
    if (!live.isEmpty()) {
      return live;
    }
    for (var object :
        board.overlappingObjects(TileShape.getInstance(anchor.point()), anchor.layer())) {
      if (!(object instanceof Item item) || !item.containsNet(ctrl.netNumber)) {
        continue;
      }
      // Search-tree overlap includes trace widths and clearance compensation, not just contacts.
      if (anchor.fanoutEnd()) {
        if (item instanceof Via via && via.getCenter().equals(anchor.point())) {
          live.add(item);
        }
      } else if (item instanceof Trace trace && trace.contains(anchor.point())) {
        live.add(item);
      } else if (item instanceof DrillItem drill && drill.getCenter().equals(anchor.point())) {
        live.add(item);
      } else if (item instanceof ConductionArea area && area.getArea().contains(anchor.point())) {
        live.add(item);
      }
    }
    return live;
  }

  private boolean connectsTerminals(TerminalAnchor start, TerminalAnchor target) {
    Set<Item> starts = resolveTerminal(start);
    Set<Item> targets = resolveTerminal(target);
    if (starts.isEmpty() || targets.isEmpty()) {
      return false;
    }
    // Seeding from every former member would conceal a component fractured during insertion.
    Set<Item> reached = terminalComponent(starts.iterator().next());
    return reached.containsAll(starts) && reached.containsAll(targets);
  }

  /** Collects one live same-net component without recursion. */
  private Set<Item> terminalComponent(Item start) {
    Set<Item> visited = new HashSet<>();
    ArrayDeque<Item> pending = new ArrayDeque<>();
    pending.add(start);
    visited.add(start);
    while (!pending.isEmpty()) {
      Item item = pending.removeFirst();
      var contacts = item.getNormalContacts();
      if (contacts != null) {
        for (Item contact : contacts) {
          if (contact.isOnTheBoard()
              && contact.containsNet(ctrl.netNumber)
              && visited.add(contact)) {
            pending.addLast(contact);
          }
        }
      }
    }
    return visited;
  }

  private record TerminalAnchor(Set<Item> component, Point point, int layer, boolean fanoutEnd) {}

  private boolean insertFanoutMicroNeckdown(
      Point okPoint, Point targetPoint, int layer, int[] netNumbers, Pin startPin, Pin endPin) {
    Point fromPoint = okPoint != null ? okPoint : targetPoint;
    if (fromPoint == null || targetPoint == null || fromPoint.equals(targetPoint)) {
      return false;
    }
    // Automatic neckdown narrows a trace at a pin. Away from the pins of the connection, a
    // narrowed segment is only a way through a gap between foreign copper, below the net-class
    // width the board asked for.
    if (!reachesNeckdownZone(fromPoint, targetPoint, layer, startPin)
        && !reachesNeckdownZone(fromPoint, targetPoint, layer, endPin)) {
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
    // neck_width_um is the narrowest trace the user allows; the necked retry already routes at
    // that width, so percentage narrowing below it would produce traces the board house cannot
    // make. Pin-neckdown candidates above stay consistent with insertNeckdown so a trace can still
    // enter a pad whose neckdown width is below neck_width_um.
    int minHalfWidth = AutorouteControl.neckHalfWidth(board, ctrl.settings);
    for (int pctHalfWidth :
        new int[] {
          Math.max(1, (baseHalfWidth * 3) / 4),
          Math.max(1, (baseHalfWidth * 3) / 5),
          Math.max(1, baseHalfWidth / 2)
        }) {
      if (pctHalfWidth >= minHalfWidth) {
        candidateHalfWidths.add(pctHalfWidth);
      }
    }

    for (int candidateHalfWidth : candidateHalfWidths) {
      if (candidateHalfWidth <= 0 || candidateHalfWidth >= baseHalfWidth) {
        continue;
      }
      Point candidateOkPoint =
          board.insertForcedTraceSegment(
              fromPoint,
              targetPoint,
              candidateHalfWidth,
              layer,
              netNumbers,
              ctrl.traceClearanceClassIndex,
              ctrl.maxShoveTraceRecursionDepth,
              ctrl.maxShoveViaRecursionDepth,
              ctrl.maxSpringOverRecursionDepth,
              Integer.MAX_VALUE,
              ctrl.pullTightAccuracy,
              true,
              null);
      if (candidateOkPoint == targetPoint) {
        traceFanoutDiagnostic(
            "trace_insert_micro_neckdown_success",
            "layer="
                + layer
                + ", candidate_half_width="
                + candidateHalfWidth
                + ", baseHalfWidth="
                + baseHalfWidth
                + ", traceClearanceClass="
                + ctrl.traceClearanceClassIndex
                + ", from="
                + formatPoint(fromPoint)
                + ", to="
                + formatPoint(targetPoint));
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
            + ctrl.traceClearanceClassIndex
            + ", from="
            + formatPoint(fromPoint)
            + ", to="
            + formatPoint(targetPoint));
    return false;
  }

  /**
   * Whether the segment from {@code a} to {@code b} has an end within the distance of {@code pin}
   * at which {@link #tryNeckDown} narrows a trace to enter the pin.
   */
  private boolean reachesNeckdownZone(Point a, Point b, int layer, Pin pin) {
    if (pin == null || !pin.isOnLayer(layer)) {
      return false;
    }
    FloatPoint pinCenter = pin.getCenter().toFloat();
    double zone = pinNeckDownDistance(pin, layer);
    return pinCenter.distance(a.toFloat()) < zone || pinCenter.distance(b.toFloat()) < zone;
  }

  /** The distance from the centre of {@code pin} within which a trace is necked down to it. */
  private double pinNeckDownDistance(Pin pin, int layer) {
    double clearance =
        this.board.rules.clearanceMatrix.getValue(
            ctrl.traceClearanceClassIndex, pin.clearanceClassIndex(), layer, true);
    return 2 * (0.5 * pin.getMaxWidth(layer) + clearance);
  }

  boolean insertNeckdown(Point fromCorner, Point toCorner, int layer, Pin startPin, Pin endPin) {
    if (startPin != null) {
      Point okPoint = tryNeckDown(toCorner, fromCorner, layer, startPin, true);
      if (okPoint == fromCorner) {
        return true;
      }
    }
    if (endPin != null) {
      Point okPoint = tryNeckDown(fromCorner, toCorner, layer, endPin, false);
      return okPoint == toCorner;
    }
    return false;
  }

  private Point tryNeckDown(Point fromCorner, Point toCorner, int layer, Pin pin, boolean atStart) {
    if (!pin.isOnLayer(layer)) {
      return null;
    }
    FloatPoint pinCenter = pin.getCenter().toFloat();
    if (pinCenter.distance(toCorner.toFloat()) >= pinNeckDownDistance(pin, layer)) {
      return null;
    }

    int neckDownHalfwidth = pin.getTraceNeckdownHalfwidth(layer);
    if (neckDownHalfwidth >= ctrl.traceHalfWidth[layer]) {
      return null;
    }

    final FloatPoint floatFromCorner = fromCorner.toFloat();
    final FloatPoint floatToCorner = toCorner.toFloat();

    final int tolerance = 2;

    int[] netNumbers = new int[1];
    netNumbers[0] = ctrl.netNumber;

    double okLength =
        board.checkTraceSegment(
            fromCorner,
            toCorner,
            layer,
            netNumbers,
            ctrl.traceHalfWidth[layer],
            ctrl.traceClearanceClassIndex,
            true);
    if (okLength >= Integer.MAX_VALUE) {
      return fromCorner;
    }
    okLength -= tolerance;
    Point neckDownEndPoint;
    if (okLength <= tolerance) {
      neckDownEndPoint = fromCorner;
    } else {
      FloatPoint floatNeckDownEndPoint = floatFromCorner.changeLength(floatToCorner, okLength);
      neckDownEndPoint = floatNeckDownEndPoint.round();
      // add a corner in case  neckDownEndPoint is not exactly on the line from fromCorner to
      // toCorner
      boolean horizontalFirst =
          Math.abs(floatFromCorner.x - floatNeckDownEndPoint.x)
              >= Math.abs(floatFromCorner.y - floatNeckDownEndPoint.y);
      IntPoint addCorner =
          FoundConnectionLocator.calculateAdditionalCorner(
                  floatFromCorner,
                  floatNeckDownEndPoint,
                  horizontalFirst,
                  board.rules.getTraceAngleRestriction())
              .round();
      Point currentOkPoint =
          board.insertForcedTraceSegment(
              fromCorner,
              addCorner,
              ctrl.traceHalfWidth[layer],
              layer,
              netNumbers,
              ctrl.traceClearanceClassIndex,
              ctrl.maxShoveTraceRecursionDepth,
              ctrl.maxShoveViaRecursionDepth,
              ctrl.maxSpringOverRecursionDepth,
              Integer.MAX_VALUE,
              ctrl.pullTightAccuracy,
              true,
              null);
      if (currentOkPoint != addCorner) {
        return fromCorner;
      }
      currentOkPoint =
          board.insertForcedTraceSegment(
              addCorner,
              neckDownEndPoint,
              ctrl.traceHalfWidth[layer],
              layer,
              netNumbers,
              ctrl.traceClearanceClassIndex,
              ctrl.maxShoveTraceRecursionDepth,
              ctrl.maxShoveViaRecursionDepth,
              ctrl.maxSpringOverRecursionDepth,
              Integer.MAX_VALUE,
              ctrl.pullTightAccuracy,
              true,
              null);
      if (currentOkPoint != neckDownEndPoint) {
        return fromCorner;
      }
      addCorner =
          FoundConnectionLocator.calculateAdditionalCorner(
                  floatNeckDownEndPoint,
                  floatToCorner,
                  !horizontalFirst,
                  board.rules.getTraceAngleRestriction())
              .round();
      if (!addCorner.equals(toCorner)) {
        currentOkPoint =
            board.insertForcedTraceSegment(
                neckDownEndPoint,
                addCorner,
                ctrl.traceHalfWidth[layer],
                layer,
                netNumbers,
                ctrl.traceClearanceClassIndex,
                ctrl.maxShoveTraceRecursionDepth,
                ctrl.maxShoveViaRecursionDepth,
                ctrl.maxSpringOverRecursionDepth,
                Integer.MAX_VALUE,
                ctrl.pullTightAccuracy,
                true,
                null);
        if (currentOkPoint != addCorner) {
          return fromCorner;
        }
        neckDownEndPoint = addCorner;
      }
    }

    return board.insertForcedTraceSegment(
        neckDownEndPoint,
        toCorner,
        neckDownHalfwidth,
        layer,
        netNumbers,
        ctrl.traceClearanceClassIndex,
        ctrl.maxShoveTraceRecursionDepth,
        ctrl.maxShoveViaRecursionDepth,
        ctrl.maxSpringOverRecursionDepth,
        Integer.MAX_VALUE,
        ctrl.pullTightAccuracy,
        true,
        null);
  }

  /**
   * Searches the cheapest via masks containing fromLayer and toLayer, so that a forced via is
   * possible at location with this mask and inserts the via. Returns false, if no suitable via mask
   * was found or if the algorithm failed.
   */
  private boolean insertVia(Point location, int inputFromLayer, int inputToLayer) {
    if (inputFromLayer == inputToLayer) {
      return true; // no via necessary
    }
    int fromLayer;
    int toLayer;
    // sort the input layers
    if (inputFromLayer < inputToLayer) {
      fromLayer = inputFromLayer;
      toLayer = inputToLayer;
    } else {
      fromLayer = inputToLayer;
      toLayer = inputFromLayer;
    }
    int[] netNumbers = new int[1];
    netNumbers[0] = ctrl.netNumber;
    ViaInfo viaInfo = null;
    boolean foundSuitableSpan = false;
    for (int i = 0; i < this.ctrl.viaRule.viaCount(); i++) {
      ViaInfo currentViaInfo = this.ctrl.viaRule.getVia(i);
      Padstack currentViaPadstack = currentViaInfo.getPadstack();
      if (currentViaPadstack.fromLayer() > fromLayer || currentViaPadstack.toLayer() < toLayer) {
        continue;
      }
      foundSuitableSpan = true;
      if (ForcedViaInserter.check(
              currentViaInfo,
              location,
              netNumbers,
              this.ctrl.maxShoveTraceRecursionDepth,
              this.ctrl.maxShoveViaRecursionDepth,
              this.board,
              this.ctrl.traceHalfWidth,
              this.ctrl.traceClearanceClassIndex)
          && this.ctrl.viaHoleAllowed(this.board, currentViaInfo, location, netNumbers)) {
        viaInfo = currentViaInfo;
        break;
      }
    }
    if (viaInfo == null) {
      if (!foundSuitableSpan) {
        FRLogger.debug(
            "FoundConnectionInserter: via mask not found for net #"
                + ctrl.netNumber
                + " covering layers "
                + fromLayer
                + " to "
                + toLayer);
      } else {
        FRLogger.debug(
            "FoundConnectionInserter: via placement blocked by clearance/shove limits for net #"
                + ctrl.netNumber);
      }
      traceFanoutDiagnostic(
          "via_mask_not_found",
          "fromLayer="
              + fromLayer
              + ", toLayer="
              + toLayer
              + ", location="
              + formatPoint(location)
              + ", traceClearanceClass="
              + ctrl.traceClearanceClassIndex
              + ", viaClearanceClass="
              + ctrl.viaClearanceClass
              + ", trace_half_width_from="
              + ctrl.traceHalfWidth[fromLayer]
              + ", trace_half_width_to="
              + ctrl.traceHalfWidth[toLayer]);
      return false;
    }
    // insert the via
    if (!ForcedViaInserter.insert(
        viaInfo,
        location,
        netNumbers,
        this.ctrl.traceClearanceClassIndex,
        this.ctrl.traceHalfWidth,
        this.ctrl.maxShoveTraceRecursionDepth,
        this.ctrl.maxShoveViaRecursionDepth,
        this.board,
        this.ctrl.viaAttachAllowed(viaInfo))) {
      FRLogger.debug("FoundConnectionInserter: forced via failed for net #" + ctrl.netNumber);
      traceFanoutDiagnostic(
          "forced_via_insert_failed",
          "fromLayer="
              + fromLayer
              + ", toLayer="
              + toLayer
              + ", location="
              + formatPoint(location)
              + ", selected_via_clearance_class="
              + viaInfo.getClearanceClassIndex()
              + ", selected_via_padstack="
              + viaInfo.getPadstack().name
              + ", traceClearanceClass="
              + ctrl.traceClearanceClassIndex
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
            + ctrl.netNumber
            + ", "
            + message);
  }
}
