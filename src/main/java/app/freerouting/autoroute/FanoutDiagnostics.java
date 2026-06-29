package app.freerouting.autoroute;

import app.freerouting.board.ConductionArea;
import app.freerouting.board.Item;
import app.freerouting.board.Pin;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Trace;
import app.freerouting.board.Via;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.Set;

/**
 * Structured diagnostic logging for fanout routing. All messages use the {@code FANOUT_DIAG}
 * prefix so they can be filtered from trace logs for post-run validation.
 */
public final class FanoutDiagnostics {

  private static int escapeViaRollbackCount;
  private static int fanoutPassFailedCount;
  private static int routedButNotEscapedCount;

  private FanoutDiagnostics() {
  }

  public static void resetCounters() {
    escapeViaRollbackCount = 0;
    fanoutPassFailedCount = 0;
    routedButNotEscapedCount = 0;
  }

  public static void incrementEscapeViaRollback() {
    escapeViaRollbackCount++;
  }

  public static void incrementFanoutPassFailed() {
    fanoutPassFailedCount++;
  }

  public static void incrementRoutedButNotEscaped() {
    routedButNotEscapedCount++;
  }

  public static void logSessionSummary() {
    if (escapeViaRollbackCount == 0 && fanoutPassFailedCount == 0 && routedButNotEscapedCount == 0) {
      return;
    }
    FRLogger.info("FANOUT_DIAG event=session_summary"
        + ", escapeViaRollbacks=" + escapeViaRollbackCount
        + ", fanoutPassFailed=" + fanoutPassFailedCount
        + ", routedButNotEscaped=" + routedButNotEscapedCount);
  }

  public static boolean isEnabled(AutorouteControl ctrl) {
    return ctrl != null && ctrl.is_fanout;
  }

  public static String pinLabel(AutorouteControl ctrl) {
    if (ctrl == null || ctrl.fanout_start_pin_name == null) {
      return ctrl == null ? "unknown-pin" : "fanout-pin(net=" + ctrl.net_no + ")";
    }
    return ctrl.fanout_start_pin_name;
  }

  public static void log(AutorouteControl ctrl, String event, String message) {
    if (!isEnabled(ctrl)) {
      return;
    }
    FRLogger.trace("FANOUT_DIAG event=" + event
        + ", pin=" + pinLabel(ctrl)
        + ", net=" + ctrl.net_no
        + ", " + message);
  }

  public static void logInfo(AutorouteControl ctrl, String event, String message) {
    if (!isEnabled(ctrl)) {
      return;
    }
    FRLogger.info("FANOUT_DIAG event=" + event
        + ", pin=" + pinLabel(ctrl)
        + ", net=" + ctrl.net_no
        + ", " + message);
  }

  public static void logInfo(String pinName, int netNo, String event, String message) {
    FRLogger.info("FANOUT_DIAG event=" + event
        + ", pin=" + pinName
        + ", net=" + netNo
        + ", " + message);
  }

  public static String formatPoint(Point point) {
    if (point == null) {
      return "null";
    }
    if (point instanceof IntPoint intPoint) {
      return "(" + intPoint.x + "," + intPoint.y + ")";
    }
    return point.toString();
  }

  public static double polylineLength(IntPoint[] corners) {
    if (corners == null || corners.length < 2) {
      return 0.0;
    }
    double total = 0.0;
    for (int i = 0; i < corners.length - 1; i++) {
      total += corners[i].to_float().distance(corners[i + 1].to_float());
    }
    return total;
  }

  public static double polylineLengthFromPin(IntPoint[] corners, Point pinCenter) {
    if (corners == null || corners.length < 2 || pinCenter == null) {
      return polylineLength(corners);
    }
    double total = 0.0;
    for (int i = corners.length - 2; i >= 0; i--) {
      IntPoint p1 = corners[i + 1];
      IntPoint p2 = corners[i];
      total += p1.to_float().distance(p2.to_float());
    }
    return total;
  }

  public static String formatCorners(IntPoint[] corners) {
    if (corners == null || corners.length == 0) {
      return "[]";
    }
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < corners.length; i++) {
      if (i > 0) {
        sb.append("->");
      }
      sb.append(formatPoint(corners[i]));
    }
    sb.append("]");
    return sb.toString();
  }

  public static String formatConnectionItems(Collection<LocateFoundConnectionAlgoAnyAngle.ResultItem> items) {
    if (items == null || items.isEmpty()) {
      return "items=0";
    }
    StringBuilder sb = new StringBuilder("itemCount=" + items.size());
    int idx = 0;
    for (LocateFoundConnectionAlgoAnyAngle.ResultItem item : items) {
      double len = polylineLength(item.corners);
      sb.append(", item").append(idx)
          .append("_layer=").append(item.layer)
          .append(", corners=").append(item.corners.length)
          .append(", length=").append(FRLogger.defaultFloatFormat.format(len))
          .append(", path=").append(formatCorners(item.corners));
      idx++;
    }
    return sb.toString();
  }

  public static String formatLengthMm(double boardUnits, double resolution) {
    if (resolution <= 0) {
      return FRLogger.defaultFloatFormat.format(boardUnits);
    }
    return FRLogger.defaultFloatFormat.format(boardUnits / resolution) + "mm";
  }

  /**
   * Describes why {@link BatchFanout} would consider a pin not escaped.
   */
  public static String describePinEscapeFailure(Pin pin) {
    Set<Item> contacts = pin.get_normal_contacts();
    if (contacts.isEmpty()) {
      return "reason=no_contacts";
    }
    StringBuilder sb = new StringBuilder();
    int traceClean = 0;
    int traceViolations = 0;
    int viaCleanNoTrace = 0;
    int viaViolations = 0;
    int viaCleanWithTrace = 0;
    int planeContacts = 0;
    for (Item contact : contacts) {
      if (contact instanceof Trace trace) {
        if (trace.clearance_violations().isEmpty()) {
          traceClean++;
        } else {
          traceViolations++;
          appendViolationDetail(sb, "trace", contact);
        }
      } else if (contact instanceof Via via) {
        if (!via.clearance_violations().isEmpty()) {
          viaViolations++;
          appendViolationDetail(sb, "via", contact);
        } else {
          boolean hasTrace = false;
          boolean hasPlane = false;
          for (Item viaContact : via.get_normal_contacts()) {
            if (viaContact instanceof Trace) {
              hasTrace = true;
            } else if (viaContact instanceof ConductionArea) {
              hasPlane = true;
            }
          }
          if (hasTrace || hasPlane) {
            viaCleanWithTrace++;
          } else {
            viaCleanNoTrace++;
            sb.append(" via_id=").append(via.get_id_no()).append("(no_trace_attached)");
          }
        }
      } else if (contact instanceof ConductionArea) {
        planeContacts++;
      }
    }
    sb.insert(0, "reason=not_escaped"
        + ", contacts=" + contacts.size()
        + ", cleanTraces=" + traceClean
        + ", traceViolations=" + traceViolations
        + ", viaWithTrace=" + viaCleanWithTrace
        + ", viaNoTrace=" + viaCleanNoTrace
        + ", viaViolations=" + viaViolations
        + ", planeContacts=" + planeContacts
        + ", detail=");
    return sb.toString();
  }

  /**
   * Summarises escape-wire geometry on the board after a fanout attempt.
   */
  public static String describePinEscapeGeometry(Pin pin, RoutingBoard board) {
    double resolution = board.communication.get_resolution(app.freerouting.board.Unit.MM);
    FloatPoint pinCenter = pin.get_center().to_float();
    StringBuilder sb = new StringBuilder();
    Set<Item> contacts = pin.get_normal_contacts();
    sb.append("pinCenter=").append(formatPoint(pin.get_center()))
        .append(", pinLayer=").append(pin.first_layer());
    for (Item contact : contacts) {
      if (contact instanceof Trace trace) {
        double traceLen = trace.get_length();
        sb.append(", trace_id=").append(trace.get_id_no())
            .append(", layer=").append(trace.get_layer())
            .append(", traceLen=").append(formatLengthMm(traceLen, resolution))
            .append(", violations=").append(trace.clearance_violations().size());
        if (trace instanceof PolylineTrace polylineTrace) {
          double pinToEnd = pinCenter.distance(polylineTrace.polyline().last_corner().to_float());
          double pinToStart = pinCenter.distance(polylineTrace.polyline().first_corner().to_float());
          sb.append(", pinToNearestEnd=").append(formatLengthMm(Math.min(pinToEnd, pinToStart), resolution))
              .append(", pinToFarthestEnd=").append(formatLengthMm(Math.max(pinToEnd, pinToStart), resolution));
        }
      } else if (contact instanceof Via via) {
        double pinToVia = pinCenter.distance(via.get_center().to_float());
        sb.append(", via_id=").append(via.get_id_no())
            .append(", layers=").append(via.first_layer()).append("-").append(via.last_layer())
            .append(", pinToVia=").append(formatLengthMm(pinToVia, resolution))
            .append(", violations=").append(via.clearance_violations().size());
        for (Item viaContact : via.get_normal_contacts()) {
          if (viaContact instanceof Trace viaTrace) {
            double viaTraceLen = viaTrace.get_length();
            sb.append(", viaTrace_id=").append(viaTrace.get_id_no())
                .append(", viaTraceLen=").append(formatLengthMm(viaTraceLen, resolution));
          }
        }
      }
    }
    return sb.toString();
  }

  private static void appendViolationDetail(StringBuilder sb, String itemType, Item item) {
    sb.append(" ").append(itemType).append("_id=").append(item.get_id_no()).append("(violations=");
    int count = 0;
    for (var violation : item.clearance_violations()) {
      if (count > 0) {
        sb.append(";");
      }
      sb.append("layer=").append(violation.layer)
          .append(",other=").append(violation.second_item);
      count++;
      if (count >= 3) {
        sb.append("...");
        break;
      }
    }
    sb.append(")");
  }
}
