package app.freerouting.autoroute;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.ForcedViaAlgo;
import app.freerouting.board.Pin;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Via;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.rules.ViaInfo;
import app.freerouting.settings.RouterSettings;
import java.util.ArrayList;
import java.util.List;

/**
 * Inserts a straight drill-end fanout stub when maze routing fails but clearance exists along a
 * simple pin-outward segment (typical for congested SMD pins with an escape via).
 */
final class FanoutForcedStub {

  private static final double EXTENSION_BUDGET_FACTOR = 4.0;
  private static final double DIRECTION_STEP_DEG = 45.0;
  private static final int MAX_FALLBACK_DIRECTIONS = 4;

  private FanoutForcedStub() {
  }

  static boolean tryInsertDrillEndStub(Pin pin, RoutingBoard board, RouterSettings settings,
      FloatPoint componentGravityCenter) {
    if (pin.net_count() != 1 || !(pin.get_center() instanceof IntPoint start)) {
      return false;
    }
    if (!hasEscapeVia(pin)) {
      return false;
    }
    int netNo = pin.get_net_no(0);
    AutorouteControl ctrl = new AutorouteControl(board, netNo, settings);
    ctrl.is_fanout = true;
    ctrl.fanout_start_pin_center = pin.get_center();
    ctrl.fanout_start_pin_layer = pin.first_layer();

    double resolution = board.communication.get_resolution(app.freerouting.board.Unit.MM);
    double minLen = FanoutDiagnostics.resolveMinLenBoardUnits(settings, resolution);
    double extensionLimit = minLen * EXTENSION_BUDGET_FACTOR;

    List<FloatPoint> directions = buildDirections(start, componentGravityCenter, board);
    int[] layers = layersToTry(pin, board);
    int[] netNoArr = new int[] { netNo };

    for (int layer : layers) {
      if (!ctrl.layer_active[layer]) {
        continue;
      }
      int halfWidth = ctrl.trace_half_width[layer];
      int directionLimit = Math.min(directions.size(), MAX_FALLBACK_DIRECTIONS);
      for (int dirIndex = 0; dirIndex < directionLimit; dirIndex++) {
        FloatPoint direction = directions.get(dirIndex);
        Double validLen = findLongestValidLength(start, direction, layer, halfWidth, netNoArr, ctrl, board, settings,
            minLen, extensionLimit, resolution);
        if (validLen == null) {
          continue;
        }
        if (insertStub(start, direction, validLen, layer, halfWidth, netNoArr, ctrl, board, settings)) {
          FanoutDiagnostics.trace(ctrl, "FanoutForcedStub", "forced_stub_inserted",
              "layer=" + layer
                  + ", lengthMm=" + FanoutDiagnostics.formatLengthMm(validLen, resolution)
                  + ", directionIndex=" + dirIndex);
          return true;
        }
      }
    }
    return false;
  }

  private static boolean hasEscapeVia(Pin pin) {
    for (app.freerouting.board.Item contact : pin.get_normal_contacts()) {
      if (contact instanceof Via via && via.isEscapeVia) {
        return true;
      }
    }
    return false;
  }

  private static Double findLongestValidLength(IntPoint start, FloatPoint direction, int layer, int halfWidth,
      int[] netNoArr, AutorouteControl ctrl, RoutingBoard board, RouterSettings settings, double minLen,
      double extensionLimit, double resolution) {
    double coarseStep = Math.max(minLen * 0.25, 0.5 * resolution);
    Double bestLen = null;
    for (double testLen = extensionLimit; testLen + 1e-3 >= minLen; testLen -= coarseStep) {
      if (isValidStub(start, direction, testLen, layer, halfWidth, netNoArr, ctrl, board, settings)) {
        bestLen = testLen;
      }
    }
    return bestLen;
  }

  private static boolean isValidStub(IntPoint start, FloatPoint direction, double length, int layer, int halfWidth,
      int[] netNoArr, AutorouteControl ctrl, RoutingBoard board, RouterSettings settings) {
    IntPoint end = offset(start, direction, length);
    Polyline polyline = new Polyline(new IntPoint[] { start, end });
    if (!board.check_forced_trace_polyline(polyline, halfWidth, layer, netNoArr, ctrl.trace_clearance_class_no,
        ctrl.max_shove_trace_recursion_depth, ctrl.max_shove_via_recursion_depth, ctrl.max_spring_over_recursion_depth)) {
      return false;
    }
    ViaInfo landingVia = board.get_fanout_end_via_info(netNoArr[0], layer, settings);
    if (landingVia == null) {
      return false;
    }
    return ForcedViaAlgo.check(landingVia, end, netNoArr, ctrl.max_shove_trace_recursion_depth,
        ctrl.max_shove_via_recursion_depth, board, ctrl.trace_half_width, ctrl.trace_clearance_class_no);
  }

  private static boolean insertStub(IntPoint start, FloatPoint direction, double length, int layer, int halfWidth,
      int[] netNoArr, AutorouteControl ctrl, RoutingBoard board, RouterSettings settings) {
    IntPoint end = offset(start, direction, length);
    Polyline polyline = new Polyline(new IntPoint[] { start, end });
    ViaInfo landingVia = board.get_fanout_end_via_info(netNoArr[0], layer, settings);
    if (landingVia == null) {
      return false;
    }
    board.start_marking_changed_area();
    Point okPoint = board.insert_forced_trace_polyline(polyline, halfWidth, layer, netNoArr,
        ctrl.trace_clearance_class_no, ctrl.max_shove_trace_recursion_depth, ctrl.max_shove_via_recursion_depth,
        ctrl.max_spring_over_recursion_depth, Integer.MAX_VALUE, ctrl.pull_tight_accuracy, true, null);
    if (okPoint != end) {
      return false;
    }
    if (!ForcedViaAlgo.insert(landingVia, end, netNoArr, ctrl.trace_clearance_class_no, ctrl.trace_half_width,
        ctrl.max_shove_trace_recursion_depth, ctrl.max_shove_via_recursion_depth, board)) {
      return false;
    }
    board.normalize_traces(netNoArr[0]);
    return true;
  }

  private static int[] layersToTry(Pin pin, RoutingBoard board) {
    int pinLayer = pin.first_layer();
    int layerCount = board.get_layer_count();
    List<Integer> ordered = new ArrayList<>();
    for (int layer = 1; layer < layerCount; layer++) {
      if (layer != pinLayer) {
        ordered.add(layer);
      }
    }
    ordered.add(pinLayer);
    return ordered.stream().mapToInt(Integer::intValue).toArray();
  }

  private static List<FloatPoint> buildDirections(IntPoint start, FloatPoint componentGravityCenter,
      RoutingBoard board) {
    List<FloatPoint> result = new ArrayList<>();
    FloatPoint primary = directionFrom(start, componentGravityCenter);
    if (primary != null) {
      result.add(primary);
    }
    AngleRestriction restriction = board.rules.get_trace_angle_restriction();
    int steps = restriction == AngleRestriction.NINETY_DEGREE ? 4 : 8;
    double baseAngle = primary != null ? Math.atan2(primary.y, primary.x) : 0.0;
    for (int i = 1; i < steps; i++) {
      double angle = baseAngle + Math.toRadians(i * DIRECTION_STEP_DEG);
      FloatPoint dir = new FloatPoint(Math.cos(angle), Math.sin(angle));
      if (!containsDirection(result, dir)) {
        result.add(dir);
      }
    }
    return result;
  }

  private static FloatPoint directionFrom(IntPoint start, FloatPoint componentGravityCenter) {
    if (componentGravityCenter == null) {
      return new FloatPoint(1.0, 0.0);
    }
    FloatPoint pinCenter = start.to_float();
    double dx = pinCenter.x - componentGravityCenter.x;
    double dy = pinCenter.y - componentGravityCenter.y;
    double dist = Math.sqrt(dx * dx + dy * dy);
    if (dist <= 1e-3) {
      return new FloatPoint(1.0, 0.0);
    }
    return new FloatPoint(dx / dist, dy / dist);
  }

  private static IntPoint offset(IntPoint start, FloatPoint direction, double distance) {
    int x = (int) Math.round(start.x + direction.x * distance);
    int y = (int) Math.round(start.y + direction.y * distance);
    return new IntPoint(x, y);
  }

  private static boolean containsDirection(List<FloatPoint> directions, FloatPoint candidate) {
    for (FloatPoint dir : directions) {
      if (Math.abs(dir.x - candidate.x) < 1e-3 && Math.abs(dir.y - candidate.y) < 1e-3) {
        return true;
      }
    }
    return false;
  }
}
