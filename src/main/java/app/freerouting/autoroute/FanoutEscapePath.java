package app.freerouting.autoroute;

import app.freerouting.board.ForcedViaAlgo;
import app.freerouting.board.RoutingBoard;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.rules.ViaInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Combined pin-outward geometry for a fanout connection path. Connection items from the maze are
 * ordered from the routing target toward the pin; corners within each item run from the target end
 * toward the pin end. Escape length bounds apply to the cumulative distance from the pin center
 * along this combined path.
 */
final class FanoutEscapePath {

  private final List<LocateFoundConnectionAlgoAnyAngle.ResultItem> itemsTargetToStart;
  private final Point pinCenter;

  private FanoutEscapePath(List<LocateFoundConnectionAlgoAnyAngle.ResultItem> itemsTargetToStart, Point pinCenter) {
    this.itemsTargetToStart = itemsTargetToStart;
    this.pinCenter = pinCenter;
  }

  static FanoutEscapePath fromConnection(Collection<LocateFoundConnectionAlgoAnyAngle.ResultItem> connectionItems,
      Point pinCenter) {
    return new FanoutEscapePath(new ArrayList<>(connectionItems), pinCenter);
  }

  double totalLengthFromPin() {
    double total = 0.0;
    for (int itemIndex = itemsTargetToStart.size() - 1; itemIndex >= 0; itemIndex--) {
      total += segmentLengthPinOutward(itemsTargetToStart.get(itemIndex).corners);
    }
    return total;
  }

  /**
   * Returns the shortest distance from the pin center along the combined path where a landing via
   * passes clearance checks, searching from {@code minLen} upward to {@code maxLen}.
   */
  static LandingPlan planLanding(Collection<LocateFoundConnectionAlgoAnyAngle.ResultItem> connectionItems,
      Point pinCenter, double minLen, double maxLen, RoutingBoard board, AutorouteControl ctrl) {
    FanoutEscapePath path = fromConnection(connectionItems, pinCenter);
    double totalLen = path.totalLengthFromPin();
    double resolution = board.communication.get_resolution(app.freerouting.board.Unit.MM);

    FanoutDiagnostics.log(ctrl, "escape_path_plan",
        "totalLen=" + FanoutDiagnostics.formatLengthMm(totalLen, resolution)
            + ", minLen=" + FanoutDiagnostics.formatLengthMm(minLen, resolution)
            + ", maxLen=" + FanoutDiagnostics.formatLengthMm(maxLen, resolution)
            + ", itemCount=" + connectionItems.size());

    PathLocation chosen = path.findShortestValidLanding(minLen, maxLen, board, ctrl);
    if (chosen == null) {
      FanoutDiagnostics.log(ctrl, "escape_path_no_landing", "reason=no_valid_point_in_bounds");
      return null;
    }

    FanoutDiagnostics.log(ctrl, "escape_path_landing",
        "lengthFromPin=" + FanoutDiagnostics.formatLengthMm(chosen.lengthFromPin, resolution)
            + ", layer=" + chosen.layer
            + ", location=" + FanoutDiagnostics.formatPoint(chosen.point)
            + ", itemIndex=" + chosen.itemIndex
            + (chosen.extended ? ", extended=true" : ""));

    List<LocateFoundConnectionAlgoAnyAngle.ResultItem> truncatedItems =
        chosen.extended ? path.extendOuterItem(chosen) : path.truncateAtLengthFromPin(chosen);
    return new LandingPlan(truncatedItems, chosen.point, chosen.layer, chosen.itemIndex, chosen.lengthFromPin);
  }

  private PathLocation findShortestValidLanding(double minLen, double maxLen, RoutingBoard board,
      AutorouteControl ctrl) {
    double resolution = board.communication.get_resolution(app.freerouting.board.Unit.MM);
    double totalLen = totalLengthFromPin();
    double step = 0.2 * resolution;
    int[] netNoArr = new int[] { ctrl.net_no };

    if (totalLen < minLen) {
      return tryExtendToMin(minLen, maxLen, totalLen, board, ctrl, netNoArr, resolution);
    }

    double startTestLen = Math.max(minLen, Math.min(totalLen, minLen));
    int attempts = 0;
    int passed = 0;
    for (double testLen = minLen; testLen <= maxLen; testLen += step) {
      if (testLen > totalLen + 1e-3) {
        break;
      }
      attempts++;
      PathLocation location = pointAtLengthFromPin(testLen);
      if (location == null) {
        continue;
      }
      ViaInfo viaInfo = board.get_fanout_end_via_info(ctrl.net_no, location.layer, ctrl.settings);
      if (viaInfo == null) {
        continue;
      }
      if (ForcedViaAlgo.check(viaInfo, location.point, netNoArr, ctrl.max_shove_trace_recursion_depth,
          ctrl.max_shove_via_recursion_depth, board, ctrl.trace_half_width, ctrl.trace_clearance_class_no)) {
        passed++;
        FanoutDiagnostics.log(ctrl, "via_location_found",
            "strategy=full_path, testLen=" + FanoutDiagnostics.formatLengthMm(testLen, resolution)
                + ", location=" + FanoutDiagnostics.formatPoint(location.point)
                + ", layer=" + location.layer
                + ", attempts=" + attempts + ", passed=" + passed);
        return location;
      }
    }
    FanoutDiagnostics.log(ctrl, "via_location_exhausted",
        "strategy=full_path, attempts=" + attempts + ", passed=" + passed
            + ", totalLen=" + FanoutDiagnostics.formatLengthMm(totalLen, resolution));
    return null;
  }

  private PathLocation tryExtendToMin(double minLen, double maxLen, double totalLen, RoutingBoard board,
      AutorouteControl ctrl, int[] netNoArr, double resolution) {
    LocateFoundConnectionAlgoAnyAngle.ResultItem outerItem = itemsTargetToStart.get(0);
    IntPoint[] corners = outerItem.corners;
    if (corners == null || corners.length < 2) {
      return null;
    }
    IntPoint farEnd = corners[0];
    IntPoint nextIn = corners[1];
    double dx = farEnd.x - nextIn.x;
    double dy = farEnd.y - nextIn.y;
    double dist = Math.sqrt(dx * dx + dy * dy);
    if (dist <= 0.1) {
      return null;
    }
    double step = 0.2 * resolution;
    int attempts = 0;
    for (double testLen = minLen; testLen <= maxLen; testLen += step) {
      attempts++;
      double needed = testLen - totalLen;
      int extX = (int) Math.round(farEnd.x + (needed / dist) * dx);
      int extY = (int) Math.round(farEnd.y + (needed / dist) * dy);
      IntPoint extendedPoint = new IntPoint(extX, extY);
      ViaInfo viaInfo = board.get_fanout_end_via_info(ctrl.net_no, outerItem.layer, ctrl.settings);
      if (viaInfo == null) {
        continue;
      }
      if (ForcedViaAlgo.check(viaInfo, extendedPoint, netNoArr, ctrl.max_shove_trace_recursion_depth,
          ctrl.max_shove_via_recursion_depth, board, ctrl.trace_half_width, ctrl.trace_clearance_class_no)) {
        FanoutDiagnostics.log(ctrl, "via_location_found",
            "strategy=extend_full_path, testLen=" + FanoutDiagnostics.formatLengthMm(testLen, resolution)
                + ", location=" + FanoutDiagnostics.formatPoint(extendedPoint)
                + ", attempts=" + attempts);
        return new PathLocation(0, outerItem.layer, extendedPoint, testLen, true);
      }
    }
    FanoutDiagnostics.log(ctrl, "via_location_exhausted",
        "strategy=extend_full_path, attempts=" + attempts
            + ", totalLen=" + FanoutDiagnostics.formatLengthMm(totalLen, resolution));
    return null;
  }

  private List<LocateFoundConnectionAlgoAnyAngle.ResultItem> extendOuterItem(PathLocation landing) {
    LocateFoundConnectionAlgoAnyAngle.ResultItem outerItem = itemsTargetToStart.get(0);
    IntPoint landingPoint = (IntPoint) landing.point;
    List<IntPoint> extendedCorners = new ArrayList<>();
    extendedCorners.add(landingPoint);
    if (outerItem.corners != null) {
      Collections.addAll(extendedCorners, outerItem.corners);
    }
    List<LocateFoundConnectionAlgoAnyAngle.ResultItem> result = new ArrayList<>();
    result.add(new LocateFoundConnectionAlgoAnyAngle.ResultItem(
        extendedCorners.toArray(new IntPoint[0]), outerItem.layer));
    for (int itemIndex = 1; itemIndex < itemsTargetToStart.size(); itemIndex++) {
      LocateFoundConnectionAlgoAnyAngle.ResultItem item = itemsTargetToStart.get(itemIndex);
      result.add(new LocateFoundConnectionAlgoAnyAngle.ResultItem(item.corners, item.layer));
    }
    return result;
  }

  private List<LocateFoundConnectionAlgoAnyAngle.ResultItem> truncateAtLengthFromPin(PathLocation landing) {
    Map<Integer, IntPoint[]> truncatedByIndex = new LinkedHashMap<>();
    double remaining = landing.lengthFromPin;

    for (int itemIndex = itemsTargetToStart.size() - 1; itemIndex >= 0; itemIndex--) {
      LocateFoundConnectionAlgoAnyAngle.ResultItem item = itemsTargetToStart.get(itemIndex);
      IntPoint[] corners = item.corners;
      if (corners == null || corners.length == 0) {
        continue;
      }
      double segLen = segmentLengthPinOutward(corners);
      if (remaining <= 1e-6) {
        break;
      }
      if (segLen <= remaining + 1e-6) {
        truncatedByIndex.put(itemIndex, corners);
        remaining -= segLen;
        continue;
      }
      IntPoint[] truncated = truncateSegmentPinOutward(corners, remaining);
      if (itemIndex == landing.itemIndex) {
        truncated = replaceFarEnd(truncated, landing.point);
      }
      truncatedByIndex.put(itemIndex, truncated);
      remaining = 0.0;
      break;
    }

    List<LocateFoundConnectionAlgoAnyAngle.ResultItem> result = new ArrayList<>();
    for (int itemIndex = 0; itemIndex < itemsTargetToStart.size(); itemIndex++) {
      IntPoint[] truncated = truncatedByIndex.get(itemIndex);
      if (truncated == null) {
        continue;
      }
      LocateFoundConnectionAlgoAnyAngle.ResultItem original = itemsTargetToStart.get(itemIndex);
      result.add(new LocateFoundConnectionAlgoAnyAngle.ResultItem(truncated, original.layer));
    }
    return result;
  }

  private PathLocation pointAtLengthFromPin(double targetLen) {
    if (targetLen < 0) {
      return null;
    }
    double walked = 0.0;
    for (int itemIndex = itemsTargetToStart.size() - 1; itemIndex >= 0; itemIndex--) {
      LocateFoundConnectionAlgoAnyAngle.ResultItem item = itemsTargetToStart.get(itemIndex);
      IntPoint[] corners = item.corners;
      if (corners == null || corners.length < 2) {
        if (targetLen <= walked + 1e-6) {
          IntPoint only = corners != null && corners.length > 0 ? corners[corners.length - 1] : null;
          return only == null ? null : new PathLocation(itemIndex, item.layer, only, targetLen);
        }
        continue;
      }
      for (int i = corners.length - 1; i > 0; i--) {
        IntPoint p1 = corners[i];
        IntPoint p2 = corners[i - 1];
        double dist = p1.to_float().distance(p2.to_float());
        if (walked + dist >= targetLen - 1e-6) {
          double ratio = dist <= 1e-6 ? 0.0 : (targetLen - walked) / dist;
          int cutX = (int) Math.round(p1.x + ratio * (p2.x - p1.x));
          int cutY = (int) Math.round(p1.y + ratio * (p2.y - p1.y));
          return new PathLocation(itemIndex, item.layer, new IntPoint(cutX, cutY), targetLen);
        }
        walked += dist;
      }
    }
    if (itemsTargetToStart.isEmpty()) {
      return null;
    }
    LocateFoundConnectionAlgoAnyAngle.ResultItem lastItem = itemsTargetToStart.get(0);
    IntPoint[] corners = lastItem.corners;
    IntPoint far = corners != null && corners.length > 0 ? corners[0] : null;
    return far == null ? null : new PathLocation(0, lastItem.layer, far, targetLen);
  }

  private static double segmentLengthPinOutward(IntPoint[] corners) {
    if (corners == null || corners.length < 2) {
      return 0.0;
    }
    double total = 0.0;
    for (int i = corners.length - 1; i > 0; i--) {
      total += corners[i].to_float().distance(corners[i - 1].to_float());
    }
    return total;
  }

  private static IntPoint[] truncateSegmentPinOutward(IntPoint[] corners, double targetLen) {
    List<IntPoint> pinOutward = new ArrayList<>();
    pinOutward.add(corners[corners.length - 1]);
    double walked = 0.0;
    for (int i = corners.length - 1; i > 0; i--) {
      IntPoint p1 = corners[i];
      IntPoint p2 = corners[i - 1];
      double dist = p1.to_float().distance(p2.to_float());
      if (walked + dist >= targetLen - 1e-6) {
        double ratio = dist <= 1e-6 ? 0.0 : (targetLen - walked) / dist;
        int cutX = (int) Math.round(p1.x + ratio * (p2.x - p1.x));
        int cutY = (int) Math.round(p1.y + ratio * (p2.y - p1.y));
        pinOutward.add(new IntPoint(cutX, cutY));
        break;
      }
      pinOutward.add(p2);
      walked += dist;
    }
    Collections.reverse(pinOutward);
    return pinOutward.toArray(new IntPoint[0]);
  }

  private static IntPoint[] replaceFarEnd(IntPoint[] cornersTargetToStart, Point landingPoint) {
    if (!(landingPoint instanceof IntPoint landing) || cornersTargetToStart == null || cornersTargetToStart.length == 0) {
      return cornersTargetToStart;
    }
    IntPoint[] copy = Arrays.copyOf(cornersTargetToStart, cornersTargetToStart.length);
    copy[0] = landing;
    return copy;
  }

  static double distanceFromPin(Point pinCenter, Point point) {
    if (pinCenter == null || point == null) {
      return 0.0;
    }
    return pinCenter.to_float().distance(point.to_float());
  }

  record PathLocation(int itemIndex, int layer, Point point, double lengthFromPin, boolean extended) {
    PathLocation(int itemIndex, int layer, Point point, double lengthFromPin) {
      this(itemIndex, layer, point, lengthFromPin, false);
    }
  }

  record LandingPlan(
      List<LocateFoundConnectionAlgoAnyAngle.ResultItem> truncatedItems,
      Point landingPoint,
      int landingLayer,
      int landingItemIndex,
      double landingLengthFromPin) {
  }
}
