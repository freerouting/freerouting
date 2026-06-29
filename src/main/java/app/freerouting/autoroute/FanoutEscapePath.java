package app.freerouting.autoroute;

import app.freerouting.board.ForcedViaAlgo;
import app.freerouting.board.RoutingBoard;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
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
 * toward the pin end. {@link FanoutDiagnostics#resolveMinLenBoardUnits} is enforced along this path.
 */
final class FanoutEscapePath {

  /** Maximum extension beyond {@code minLen} when lengthening a path that is too short. */
  private static final double EXTENSION_BUDGET_FACTOR = 4.0;

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
   * Ensures the pin-outward path meets {@code minLen}. For drill-end fanout a landing via is placed
   * at the shortest valid point at or beyond {@code minLen}. For connect-to-target fanout the path
   * is extended when shorter than {@code minLen}.
   */
  static EscapePlan planEscape(Collection<LocateFoundConnectionAlgoAnyAngle.ResultItem> connectionItems,
      Point pinCenter, double minLen, boolean drillEnd, RoutingBoard board, AutorouteControl ctrl) {
    FanoutEscapePath path = fromConnection(connectionItems, pinCenter);
    double totalLen = path.totalLengthFromPin();
    double resolution = board.communication.get_resolution(app.freerouting.board.Unit.MM);

    FanoutDiagnostics.trace(ctrl, "FanoutEscapePath.planEscape", "escape_path_plan",
        "drillEnd=" + drillEnd
            + ", totalLen=" + FanoutDiagnostics.formatLengthMm(totalLen, resolution)
            + ", minLen=" + FanoutDiagnostics.formatLengthMm(minLen, resolution)
            + ", itemCount=" + connectionItems.size());

    if (drillEnd) {
      PathLocation chosen = null;
      if (totalLen + 1e-3 >= minLen) {
        PathLocation geometric = path.pointAtLengthFromPin(minLen);
        if (geometric != null && path.isClearanceValidLanding(geometric, board, ctrl)) {
          chosen = geometric;
          FanoutDiagnostics.trace(ctrl, "FanoutEscapePath.planEscape", "geometric_minLen_landing",
              "lengthFromPin=" + FanoutDiagnostics.formatLengthMm(chosen.lengthFromPin, resolution)
                  + ", location=" + FanoutDiagnostics.formatPoint(chosen.point));
        } else {
          chosen = path.findShortestValidLanding(minLen, board, ctrl);
          if (chosen == null && geometric != null) {
            chosen = geometric;
            FanoutDiagnostics.trace(ctrl, "FanoutEscapePath.planEscape", "geometric_minLen_landing",
                "lengthFromPin=" + FanoutDiagnostics.formatLengthMm(chosen.lengthFromPin, resolution)
                    + ", location=" + FanoutDiagnostics.formatPoint(chosen.point)
                    + ", clearanceUnchecked=true");
          }
        }
      }
      if (chosen == null) {
        chosen = path.tryExtendToMin(minLen, totalLen, board, ctrl);
      }
      if (chosen == null && totalLen + 1e-3 < minLen) {
        chosen = path.extendedPointAtLengthFromPin(minLen, resolution);
        if (chosen != null) {
          FanoutDiagnostics.trace(ctrl, "FanoutEscapePath.planEscape", "geometric_extended_minLen_landing",
              "lengthFromPin=" + FanoutDiagnostics.formatLengthMm(chosen.lengthFromPin, resolution)
                  + ", location=" + FanoutDiagnostics.formatPoint(chosen.point));
        }
      }
      if (chosen == null) {
        chosen = path.findBestEffortLandingAtOrAboveMin(minLen, board, ctrl);
      }
      if (chosen == null) {
        chosen = path.findBestEffortLandingBelowMin(minLen, board, ctrl);
      }
      if (chosen == null && totalLen > 0) {
        chosen = path.pointAtLengthFromPin(totalLen);
        if (chosen != null) {
          FanoutDiagnostics.trace(ctrl, "FanoutEscapePath.planEscape", "path_end_landing",
              "lengthFromPin=" + FanoutDiagnostics.formatLengthMm(chosen.lengthFromPin, resolution)
                  + ", location=" + FanoutDiagnostics.formatPoint(chosen.point));
        }
      }
      if (chosen == null && !connectionItems.isEmpty()) {
        FanoutDiagnostics.trace(ctrl, "FanoutEscapePath.planEscape", "escape_path_fallback",
            "reason=using_natural_drill_path"
                + ", totalLen=" + FanoutDiagnostics.formatLengthMm(totalLen, resolution)
                + ", minLen=" + FanoutDiagnostics.formatLengthMm(minLen, resolution));
        return new EscapePlan(new ArrayList<>(connectionItems), null, totalLen);
      }
      if (chosen == null) {
        FanoutDiagnostics.trace(ctrl, "FanoutEscapePath.planEscape", "escape_path_failed",
            "reason=no_landing_point"
                + ", totalLen=" + FanoutDiagnostics.formatLengthMm(totalLen, resolution)
                + ", minLen=" + FanoutDiagnostics.formatLengthMm(minLen, resolution));
        return null;
      }
      FanoutDiagnostics.trace(ctrl, "FanoutEscapePath.planEscape", "escape_path_landing",
          "lengthFromPin=" + FanoutDiagnostics.formatLengthMm(chosen.lengthFromPin, resolution)
              + ", layer=" + chosen.layer
              + ", location=" + FanoutDiagnostics.formatPoint(chosen.point)
              + ", itemIndex=" + chosen.itemIndex
              + (chosen.extended ? ", extended=true" : "")
              + (chosen.lengthFromPin + 1e-3 < minLen ? ", belowMin=true" : ""));
      List<LocateFoundConnectionAlgoAnyAngle.ResultItem> truncatedItems =
          chosen.extended ? path.extendPathAtLanding(chosen) : path.truncateAtLengthFromPin(chosen);
      LandingPlan landing = new LandingPlan(chosen.point, chosen.layer, chosen.itemIndex, chosen.lengthFromPin);
      return new EscapePlan(truncatedItems, landing, chosen.lengthFromPin);
    }

    if (totalLen + 1e-3 >= minLen) {
      return new EscapePlan(new ArrayList<>(connectionItems), null, totalLen);
    }
    PathLocation extended = path.tryExtendConnectToMin(minLen, totalLen, board, ctrl);
    if (extended == null) {
      extended = path.extendedPointAtLengthFromPin(minLen, resolution);
    }
    if (extended == null) {
      FanoutDiagnostics.trace(ctrl, "FanoutEscapePath.planEscape", "escape_path_fallback",
          "reason=using_natural_short_path"
              + ", totalLen=" + FanoutDiagnostics.formatLengthMm(totalLen, resolution)
              + ", minLen=" + FanoutDiagnostics.formatLengthMm(minLen, resolution));
      return new EscapePlan(new ArrayList<>(connectionItems), null, totalLen);
    }
    FanoutDiagnostics.trace(ctrl, "FanoutEscapePath.planEscape", "escape_path_extended",
        "lengthFromPin=" + FanoutDiagnostics.formatLengthMm(extended.lengthFromPin, resolution)
            + ", location=" + FanoutDiagnostics.formatPoint(extended.point));
    return new EscapePlan(path.extendPathAtLanding(extended), null, extended.lengthFromPin);
  }

  private boolean isClearanceValidLanding(PathLocation location, RoutingBoard board, AutorouteControl ctrl) {
    ViaInfo viaInfo = board.get_fanout_end_via_info(ctrl.net_no, location.layer, ctrl.settings);
    if (viaInfo == null) {
      return false;
    }
    int[] netNoArr = new int[] { ctrl.net_no };
    return ForcedViaAlgo.check(viaInfo, location.point, netNoArr, ctrl.max_shove_trace_recursion_depth,
        ctrl.max_shove_via_recursion_depth, board, ctrl.trace_half_width, ctrl.trace_clearance_class_no);
  }

  private PathLocation findShortestValidLanding(double minLen, RoutingBoard board, AutorouteControl ctrl) {
    double resolution = board.communication.get_resolution(app.freerouting.board.Unit.MM);
    double totalLen = totalLengthFromPin();
    double step = 0.2 * resolution;
    int[] netNoArr = new int[] { ctrl.net_no };
    int attempts = 0;
    int passed = 0;

    for (double testLen = minLen; testLen <= totalLen + 1e-3; testLen += step) {
      attempts++;
      PathLocation location = pointAtLengthFromPin(testLen);
      if (location == null) {
        continue;
      }
      if (isClearanceValidLanding(location, board, ctrl)) {
        passed++;
        FanoutDiagnostics.trace(ctrl, "FanoutEscapePath.findShortestValidLanding", "via_location_found",
            "strategy=full_path, testLen=" + FanoutDiagnostics.formatLengthMm(testLen, resolution)
                + ", location=" + FanoutDiagnostics.formatPoint(location.point)
                + ", layer=" + location.layer
                + ", attempts=" + attempts + ", passed=" + passed);
        return location;
      }
    }
    FanoutDiagnostics.trace(ctrl, "FanoutEscapePath.findShortestValidLanding", "via_location_exhausted",
        "strategy=full_path, attempts=" + attempts + ", passed=" + passed
            + ", totalLen=" + FanoutDiagnostics.formatLengthMm(totalLen, resolution));
    return null;
  }

  private PathLocation tryExtendToMin(double minLen, double totalLen, RoutingBoard board, AutorouteControl ctrl) {
    double resolution = board.communication.get_resolution(app.freerouting.board.Unit.MM);
    double extensionLimit = minLen * EXTENSION_BUDGET_FACTOR;
    PathLocation chosen = tryExtendOuterSegmentToMin(minLen, totalLen, extensionLimit, board, ctrl, resolution);
    if (chosen != null) {
      return chosen;
    }
    if (itemsTargetToStart.size() > 1) {
      chosen = tryExtendPinSideSegmentToMin(minLen, totalLen, extensionLimit, board, ctrl, resolution);
    }
    return chosen;
  }

  /**
   * Extends the path end (item 0, away from pin) and checks clearance for a landing via.
   */
  private PathLocation tryExtendOuterSegmentToMin(double minLen, double totalLen, double extensionLimit,
      RoutingBoard board, AutorouteControl ctrl, double resolution) {
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
    return scanExtendedLanding(minLen, totalLen, extensionLimit, board, ctrl, resolution,
        outerItem.layer, 0, farEnd, dx, dy, dist, "extend_outer_segment");
  }

  /**
   * Extends the pin-side segment on the last item when the outer segment cannot reach {@code minLen}.
   */
  private PathLocation tryExtendPinSideSegmentToMin(double minLen, double totalLen, double extensionLimit,
      RoutingBoard board, AutorouteControl ctrl, double resolution) {
    int pinItemIndex = itemsTargetToStart.size() - 1;
    LocateFoundConnectionAlgoAnyAngle.ResultItem pinItem = itemsTargetToStart.get(pinItemIndex);
    IntPoint[] corners = pinItem.corners;
    if (corners == null || corners.length < 2) {
      return null;
    }
    IntPoint pinEnd = corners[corners.length - 1];
    IntPoint nextOut = corners[corners.length - 2];
    double dx = nextOut.x - pinEnd.x;
    double dy = nextOut.y - pinEnd.y;
    double dist = Math.sqrt(dx * dx + dy * dy);
    if (dist <= 0.1) {
      return null;
    }
    IntPoint extendFrom = nextOut;
    return scanExtendedLanding(minLen, totalLen, extensionLimit, board, ctrl, resolution,
        pinItem.layer, pinItemIndex, extendFrom, dx, dy, dist, "extend_pin_side_segment");
  }

  private PathLocation scanExtendedLanding(double minLen, double totalLen, double extensionLimit,
      RoutingBoard board, AutorouteControl ctrl, double resolution, int layer, int itemIndex,
      IntPoint extendFrom, double dx, double dy, double segmentDist, String strategy) {
    double step = 0.2 * resolution;
    int[] netNoArr = new int[] { ctrl.net_no };
    int attempts = 0;

    for (double testLen = minLen; testLen <= extensionLimit; testLen += step) {
      attempts++;
      double needed = testLen - totalLen;
      int extX = (int) Math.round(extendFrom.x + (needed / segmentDist) * dx);
      int extY = (int) Math.round(extendFrom.y + (needed / segmentDist) * dy);
      IntPoint extendedPoint = new IntPoint(extX, extY);
      ViaInfo viaInfo = board.get_fanout_end_via_info(ctrl.net_no, layer, ctrl.settings);
      if (viaInfo == null) {
        continue;
      }
      if (ForcedViaAlgo.check(viaInfo, extendedPoint, netNoArr, ctrl.max_shove_trace_recursion_depth,
          ctrl.max_shove_via_recursion_depth, board, ctrl.trace_half_width, ctrl.trace_clearance_class_no)) {
        FanoutDiagnostics.trace(ctrl, "FanoutEscapePath.tryExtendToMin", "via_location_found",
            "strategy=" + strategy + ", testLen=" + FanoutDiagnostics.formatLengthMm(testLen, resolution)
                + ", location=" + FanoutDiagnostics.formatPoint(extendedPoint)
                + ", attempts=" + attempts);
        return new PathLocation(itemIndex, layer, extendedPoint, testLen, true);
      }
    }
    FanoutDiagnostics.trace(ctrl, "FanoutEscapePath.tryExtendToMin", "via_location_exhausted",
        "strategy=" + strategy + ", attempts=" + attempts
            + ", totalLen=" + FanoutDiagnostics.formatLengthMm(totalLen, resolution)
            + ", extensionLimit=" + FanoutDiagnostics.formatLengthMm(extensionLimit, resolution));
    return null;
  }

  /**
   * Finds the longest valid landing at or beyond {@code minLen} along the existing path or a modest
   * extension. Drill-end fanout never accepts a landing below {@code minLen}.
   */
  private PathLocation findBestEffortLandingAtOrAboveMin(double minLen, RoutingBoard board, AutorouteControl ctrl) {
    double totalLen = totalLengthFromPin();
    double resolution = board.communication.get_resolution(app.freerouting.board.Unit.MM);
    double step = 0.2 * resolution;
    double extensionLimit = minLen * EXTENSION_BUDGET_FACTOR;
    int[] netNoArr = new int[] { ctrl.net_no };
    PathLocation bestAtOrAboveMin = null;

    for (double testLen = Math.min(totalLen, extensionLimit); testLen + 1e-3 >= minLen; testLen -= step) {
      PathLocation location = pointAtLengthFromPin(testLen);
      if (location == null) {
        continue;
      }
      ViaInfo viaInfo = board.get_fanout_end_via_info(ctrl.net_no, location.layer, ctrl.settings);
      if (viaInfo == null) {
        continue;
      }
      if (!ForcedViaAlgo.check(viaInfo, location.point, netNoArr, ctrl.max_shove_trace_recursion_depth,
          ctrl.max_shove_via_recursion_depth, board, ctrl.trace_half_width, ctrl.trace_clearance_class_no)) {
        continue;
      }
      if (bestAtOrAboveMin == null || testLen > bestAtOrAboveMin.lengthFromPin) {
        bestAtOrAboveMin = location;
      }
    }

    for (double testLen = Math.max(totalLen + step, minLen); testLen <= extensionLimit; testLen += step) {
      PathLocation extended = extendedPointAtLengthFromPin(testLen, resolution);
      if (extended == null) {
        continue;
      }
      ViaInfo viaInfo = board.get_fanout_end_via_info(ctrl.net_no, extended.layer, ctrl.settings);
      if (viaInfo == null) {
        continue;
      }
      if (!ForcedViaAlgo.check(viaInfo, extended.point, netNoArr, ctrl.max_shove_trace_recursion_depth,
          ctrl.max_shove_via_recursion_depth, board, ctrl.trace_half_width, ctrl.trace_clearance_class_no)) {
        continue;
      }
      if (bestAtOrAboveMin == null || testLen > bestAtOrAboveMin.lengthFromPin) {
        bestAtOrAboveMin = extended;
      }
    }

    if (bestAtOrAboveMin != null) {
      FanoutDiagnostics.trace(ctrl, "FanoutEscapePath.findBestEffortLandingAtOrAboveMin", "via_location_found",
          "strategy=best_effort, testLen="
              + FanoutDiagnostics.formatLengthMm(bestAtOrAboveMin.lengthFromPin, resolution)
              + ", location=" + FanoutDiagnostics.formatPoint(bestAtOrAboveMin.point));
    }
    return bestAtOrAboveMin;
  }

  /**
   * Last-resort landing when no point at or beyond {@code minLen} is clearance-valid. Keeps a shorter
   * escape stub rather than abandoning the pin entirely.
   */
  private PathLocation findBestEffortLandingBelowMin(double minLen, RoutingBoard board, AutorouteControl ctrl) {
    double totalLen = totalLengthFromPin();
    double resolution = board.communication.get_resolution(app.freerouting.board.Unit.MM);
    double step = 0.2 * resolution;
    int[] netNoArr = new int[] { ctrl.net_no };
    PathLocation bestBelowMin = null;

    for (double testLen = Math.min(totalLen, minLen - step); testLen >= step; testLen -= step) {
      PathLocation location = pointAtLengthFromPin(testLen);
      if (location == null) {
        continue;
      }
      ViaInfo viaInfo = board.get_fanout_end_via_info(ctrl.net_no, location.layer, ctrl.settings);
      if (viaInfo == null) {
        continue;
      }
      if (!ForcedViaAlgo.check(viaInfo, location.point, netNoArr, ctrl.max_shove_trace_recursion_depth,
          ctrl.max_shove_via_recursion_depth, board, ctrl.trace_half_width, ctrl.trace_clearance_class_no)) {
        continue;
      }
      if (bestBelowMin == null || testLen > bestBelowMin.lengthFromPin) {
        bestBelowMin = location;
      }
    }

    if (bestBelowMin != null) {
      FanoutDiagnostics.trace(ctrl, "FanoutEscapePath.findBestEffortLandingBelowMin", "via_location_found",
          "strategy=best_effort_below_min, testLen="
              + FanoutDiagnostics.formatLengthMm(bestBelowMin.lengthFromPin, resolution)
              + ", location=" + FanoutDiagnostics.formatPoint(bestBelowMin.point));
    }
    return bestBelowMin;
  }

  private PathLocation extendedPointAtLengthFromPin(double targetLen, double resolution) {
    double totalLen = totalLengthFromPin();
    if (targetLen <= totalLen + 1e-6) {
      return pointAtLengthFromPin(targetLen);
    }
    PathLocation outer = tryExtendPathAlongOuterSegment(targetLen, totalLen);
    if (outer != null) {
      return outer;
    }
    if (itemsTargetToStart.size() > 1) {
      return tryExtendPathAlongPinSideSegment(targetLen, totalLen);
    }
    return null;
  }

  private PathLocation tryExtendPathAlongOuterSegment(double targetLen, double totalLen) {
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
    double needed = targetLen - totalLen;
    int extX = (int) Math.round(farEnd.x + (needed / dist) * dx);
    int extY = (int) Math.round(farEnd.y + (needed / dist) * dy);
    return new PathLocation(0, outerItem.layer, new IntPoint(extX, extY), targetLen, true);
  }

  private PathLocation tryExtendPathAlongPinSideSegment(double targetLen, double totalLen) {
    int pinItemIndex = itemsTargetToStart.size() - 1;
    LocateFoundConnectionAlgoAnyAngle.ResultItem pinItem = itemsTargetToStart.get(pinItemIndex);
    IntPoint[] corners = pinItem.corners;
    if (corners == null || corners.length < 2) {
      return null;
    }
    IntPoint pinEnd = corners[corners.length - 1];
    IntPoint nextOut = corners[corners.length - 2];
    double dx = nextOut.x - pinEnd.x;
    double dy = nextOut.y - pinEnd.y;
    double dist = Math.sqrt(dx * dx + dy * dy);
    if (dist <= 0.1) {
      return null;
    }
    double needed = targetLen - totalLen;
    int extX = (int) Math.round(nextOut.x + (needed / dist) * dx);
    int extY = (int) Math.round(nextOut.y + (needed / dist) * dy);
    return new PathLocation(pinItemIndex, pinItem.layer, new IntPoint(extX, extY), targetLen, true);
  }

  private PathLocation tryExtendConnectToMin(double minLen, double totalLen, RoutingBoard board, AutorouteControl ctrl) {
    double resolution = board.communication.get_resolution(app.freerouting.board.Unit.MM);
    double extensionLimit = minLen * EXTENSION_BUDGET_FACTOR;
    PathLocation chosen = scanExtendedConnectPath(minLen, totalLen, extensionLimit, board, ctrl, resolution, true);
    if (chosen != null) {
      return chosen;
    }
    if (itemsTargetToStart.size() > 1) {
      chosen = scanExtendedConnectPath(minLen, totalLen, extensionLimit, board, ctrl, resolution, false);
    }
    return chosen;
  }

  private PathLocation scanExtendedConnectPath(double minLen, double totalLen, double extensionLimit,
      RoutingBoard board, AutorouteControl ctrl, double resolution, boolean outerSegment) {
    int itemIndex;
    IntPoint extendFrom;
    double dx;
    double dy;
    double segmentDist;
    int layer;
    if (outerSegment) {
      LocateFoundConnectionAlgoAnyAngle.ResultItem outerItem = itemsTargetToStart.get(0);
      IntPoint[] corners = outerItem.corners;
      if (corners == null || corners.length < 2) {
        return null;
      }
      IntPoint farEnd = corners[0];
      IntPoint nextIn = corners[1];
      dx = farEnd.x - nextIn.x;
      dy = farEnd.y - nextIn.y;
      segmentDist = Math.sqrt(dx * dx + dy * dy);
      if (segmentDist <= 0.1) {
        return null;
      }
      extendFrom = farEnd;
      itemIndex = 0;
      layer = outerItem.layer;
    } else {
      itemIndex = itemsTargetToStart.size() - 1;
      LocateFoundConnectionAlgoAnyAngle.ResultItem pinItem = itemsTargetToStart.get(itemIndex);
      IntPoint[] corners = pinItem.corners;
      if (corners == null || corners.length < 2) {
        return null;
      }
      IntPoint pinEnd = corners[corners.length - 1];
      IntPoint nextOut = corners[corners.length - 2];
      dx = nextOut.x - pinEnd.x;
      dy = nextOut.y - pinEnd.y;
      segmentDist = Math.sqrt(dx * dx + dy * dy);
      if (segmentDist <= 0.1) {
        return null;
      }
      extendFrom = nextOut;
      layer = pinItem.layer;
    }

    double step = 0.2 * resolution;
    PathLocation bestBelowMin = null;
    for (double testLen = minLen; testLen <= extensionLimit; testLen += step) {
      PathLocation candidate = extendedPointFromSegment(itemIndex, layer, extendFrom, dx, dy, segmentDist, testLen,
          totalLen);
      if (candidate == null || !canInsertConnectItems(extendPathAtLanding(candidate), board, ctrl)) {
        continue;
      }
      if (testLen + 1e-3 >= minLen) {
        FanoutDiagnostics.trace(ctrl, "FanoutEscapePath.tryExtendConnectToMin", "connect_path_extended",
            "strategy=" + (outerSegment ? "outer_segment" : "pin_side_segment")
                + ", testLen=" + FanoutDiagnostics.formatLengthMm(testLen, resolution)
                + ", location=" + FanoutDiagnostics.formatPoint(candidate.point));
        return candidate;
      }
      if (bestBelowMin == null || testLen > bestBelowMin.lengthFromPin) {
        bestBelowMin = candidate;
      }
    }
    if (bestBelowMin != null) {
      FanoutDiagnostics.trace(ctrl, "FanoutEscapePath.tryExtendConnectToMin", "connect_path_extended",
          "strategy=" + (outerSegment ? "outer_segment" : "pin_side_segment")
              + ", belowMin=true, testLen="
              + FanoutDiagnostics.formatLengthMm(bestBelowMin.lengthFromPin, resolution)
              + ", location=" + FanoutDiagnostics.formatPoint(bestBelowMin.point));
    }
    return bestBelowMin;
  }

  private PathLocation extendedPointFromSegment(int itemIndex, int layer, IntPoint extendFrom, double dx, double dy,
      double segmentDist, double targetLen, double totalLen) {
    double needed = targetLen - totalLen;
    int extX = (int) Math.round(extendFrom.x + (needed / segmentDist) * dx);
    int extY = (int) Math.round(extendFrom.y + (needed / segmentDist) * dy);
    return new PathLocation(itemIndex, layer, new IntPoint(extX, extY), targetLen, true);
  }

  private boolean canInsertConnectItems(List<LocateFoundConnectionAlgoAnyAngle.ResultItem> items, RoutingBoard board,
      AutorouteControl ctrl) {
    int[] netNoArr = new int[] { ctrl.net_no };
    for (LocateFoundConnectionAlgoAnyAngle.ResultItem item : items) {
      IntPoint[] corners = item.corners;
      if (corners == null || corners.length < 2) {
        continue;
      }
      Polyline polyline = new Polyline(corners);
      if (!board.check_forced_trace_polyline(polyline, ctrl.trace_half_width[item.layer], item.layer, netNoArr,
          ctrl.trace_clearance_class_no, ctrl.max_shove_trace_recursion_depth, ctrl.max_shove_via_recursion_depth,
          ctrl.max_spring_over_recursion_depth)) {
        return false;
      }
    }
    return true;
  }

  private PathLocation tryExtendPathToMin(double minLen, double totalLen, double resolution) {
    PathLocation extended = tryExtendPathAlongOuterSegment(minLen, totalLen);
    if (extended != null) {
      return extended;
    }
    if (itemsTargetToStart.size() > 1) {
      return tryExtendPathAlongPinSideSegment(minLen, totalLen);
    }
    return null;
  }

  private List<LocateFoundConnectionAlgoAnyAngle.ResultItem> extendPathAtLanding(PathLocation landing) {
    int itemIndex = landing.itemIndex;
    LocateFoundConnectionAlgoAnyAngle.ResultItem extendedItem = itemsTargetToStart.get(itemIndex);
    IntPoint landingPoint = (IntPoint) landing.point;
    List<IntPoint> extendedCorners = new ArrayList<>();
    extendedCorners.add(landingPoint);
    if (extendedItem.corners != null) {
      Collections.addAll(extendedCorners, extendedItem.corners);
    }
    List<LocateFoundConnectionAlgoAnyAngle.ResultItem> result = new ArrayList<>();
    for (int idx = 0; idx < itemsTargetToStart.size(); idx++) {
      LocateFoundConnectionAlgoAnyAngle.ResultItem item = itemsTargetToStart.get(idx);
      if (idx == itemIndex) {
        result.add(new LocateFoundConnectionAlgoAnyAngle.ResultItem(
            extendedCorners.toArray(new IntPoint[0]), item.layer));
      } else {
        result.add(new LocateFoundConnectionAlgoAnyAngle.ResultItem(item.corners, item.layer));
      }
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

  record PathLocation(int itemIndex, int layer, Point point, double lengthFromPin, boolean extended) {
    PathLocation(int itemIndex, int layer, Point point, double lengthFromPin) {
      this(itemIndex, layer, point, lengthFromPin, false);
    }
  }

  record LandingPlan(Point landingPoint, int landingLayer, int landingItemIndex, double landingLengthFromPin) {
  }

  record EscapePlan(
      List<LocateFoundConnectionAlgoAnyAngle.ResultItem> items,
      LandingPlan landing,
      double escapeLengthFromPin) {
  }
}
