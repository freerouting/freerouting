package app.freerouting.board;

import app.freerouting.autoroute.AutorouteControl.ExpansionCostFactor;
import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.Side;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.Iterator;

/** Contains functions for optimizing and improving via locations. */
public final class OptViaAlgo {

  private OptViaAlgo() {}

  /**
   * Optimizes the location of a via connected to at most 2 traces according to the trace costs on
   * the layers of the connected traces If p_trace_cost_arr == null, the horizontal and vertical
   * trace costs will be set to 1. Returns false, if the via was not changed.
   */
  public static boolean opt_via_location(
      RoutingBoard p_board,
      Via p_via,
      ExpansionCostFactor[] p_trace_cost_arr,
      int p_trace_pull_tight_accuracy,
      int p_max_recursion_depth) {
    if (p_via.is_shove_fixed()) {
      return false;
    }
    if (p_max_recursion_depth <= 0) {
      FRLogger.debug("OptViaAlgo.opt_via_location: probably endless loop");
      return false;
    }
    Collection<Item> contacts = p_via.get_normal_contacts();
    boolean isPlaneOrFanoutVia = contacts.size() == 1;
    PolylineTrace firstTrace = null;
    PolylineTrace secondTrace = null;
    if (!isPlaneOrFanoutVia) {
      if (contacts.size() != 2) {
        return false;
      }
      Iterator<Item> it = contacts.iterator();
      Item currItem = it.next();
      if (currItem.is_shove_fixed() || !(currItem instanceof PolylineTrace)) {
        if (currItem instanceof ConductionArea) {
          isPlaneOrFanoutVia = true;
        } else {
          return false;
        }
      } else {
        firstTrace = (PolylineTrace) currItem;
      }
      currItem = it.next();
      if (currItem.is_shove_fixed() || !(currItem instanceof PolylineTrace)) {
        if (currItem instanceof ConductionArea) {
          isPlaneOrFanoutVia = true;
        } else {
          return false;
        }
      } else {
        secondTrace = (PolylineTrace) currItem;
      }
    }
    if (isPlaneOrFanoutVia) {
      return opt_plane_or_fanout_via(
          p_board, p_via, p_trace_pull_tight_accuracy, p_max_recursion_depth);
    }
    Point viaCenter = p_via.get_center();
    int firstLayer = firstTrace.get_layer();
    int secondLayer = secondTrace.get_layer();
    Point firstTraceFromCorner;
    Point secondTraceFromCorner;

    // calculate firstTraceFromCorner and secondTraceFromCorner
    // Use tolerance-based comparison to match connectivity detection logic
    int tolerance = (int) (p_via.min_width() / 2) + 1;

    if (isWithinTolerance(firstTrace.first_corner(), viaCenter, tolerance)) {
      firstTraceFromCorner = firstTrace.polyline().corner(1);
    } else if (isWithinTolerance(firstTrace.last_corner(), viaCenter, tolerance)) {
      firstTraceFromCorner = firstTrace.polyline().corner(firstTrace.polyline().corner_count() - 2);
    } else {
      // Via is not connected at trace endpoints - skip optimization
      return false;
    }

    if (isWithinTolerance(secondTrace.first_corner(), viaCenter, tolerance)) {
      secondTraceFromCorner = secondTrace.polyline().corner(1);
    } else if (isWithinTolerance(secondTrace.last_corner(), viaCenter, tolerance)) {
      secondTraceFromCorner =
          secondTrace.polyline().corner(secondTrace.polyline().corner_count() - 2);
    } else {
      // Via is not connected at trace endpoints - skip optimization
      return false;
    }

    ExpansionCostFactor firstLayerTraceCosts;
    ExpansionCostFactor secondLayerTraceCosts;
    if (p_trace_cost_arr != null) {
      firstLayerTraceCosts = p_trace_cost_arr[firstLayer];
      secondLayerTraceCosts = p_trace_cost_arr[secondLayer];
    } else {
      firstLayerTraceCosts = new ExpansionCostFactor(1, 1);
      secondLayerTraceCosts = firstLayerTraceCosts;
    }

    Point newLocation =
        reposition_via(
            p_board,
            p_via,
            firstTrace.get_half_width(),
            firstTrace.clearance_class_no(),
            firstTrace.get_layer(),
            firstLayerTraceCosts,
            firstTraceFromCorner,
            secondTrace.get_half_width(),
            secondTrace.clearance_class_no(),
            secondTrace.get_layer(),
            secondLayerTraceCosts,
            secondTraceFromCorner);
    if (newLocation == null || newLocation.equals(viaCenter)) {
      return false;
    }
    Vector delta = newLocation.difference_by(viaCenter);
    if (!MoveDrillItemAlgo.insert(p_via, delta, 9, 9, null, p_board)) {
      FRLogger.warn("OptViaAlgo.opt_via_location: move via failed");
      return false;
    }
    ItemSelectionFilter filter =
        new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.TRACES);
    Collection<Item> pickedItems = p_board.pick_items(newLocation, firstTrace.get_layer(), filter);
    for (Item currItem : pickedItems) {
      ((PolylineTrace) currItem).pull_tight(true, p_trace_pull_tight_accuracy, null);
    }
    pickedItems = p_board.pick_items(newLocation, secondTrace.get_layer(), filter);
    for (Item currItem : pickedItems) {
      ((PolylineTrace) currItem).pull_tight(true, p_trace_pull_tight_accuracy, null);
    }
    filter = new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.VIAS);
    pickedItems = p_board.pick_items(newLocation, firstTrace.get_layer(), filter);
    for (Item currItem : pickedItems) {
      opt_via_location(
          p_board,
          (Via) currItem,
          p_trace_cost_arr,
          p_trace_pull_tight_accuracy,
          p_max_recursion_depth - 1);
      break;
    }
    return true;
  }

  /** Optimisations for vias with only 1 connected Trace (Plane or Fanout Vias). */
  private static boolean opt_plane_or_fanout_via(
      RoutingBoard p_board, Via p_via, int p_trace_pull_tight_accuracy, int p_max_recursion_depth) {
    if (p_max_recursion_depth <= 0) {
      FRLogger.debug("OptViaAlgo.opt_plane_or_fanout_via: probably endless loop");
      return false;
    }
    Collection<Item> contactList = p_via.get_normal_contacts();
    if (contactList.isEmpty()) {
      return false;
    }
    ConductionArea contactPlane = null;
    PolylineTrace contactTrace = null;
    for (Item currContact : contactList) {
      if (currContact instanceof ConductionArea area) {
        if (contactPlane != null) {
          return false;
        }
        contactPlane = area;
      } else if (currContact instanceof PolylineTrace trace) {
        if (currContact.is_shove_fixed() || contactTrace != null) {
          return false;
        }
        contactTrace = trace;
      } else {
        return false;
      }
    }
    if (contactTrace == null) {
      return false;
    }
    Point viaCenter = p_via.get_center();

    // Use tolerance based on via size, matching the logic in opt_via_location
    int tolerance = (int) (p_via.min_width() / 2) + 1;

    boolean atFirstCorner;
    if (isWithinTolerance(contactTrace.first_corner(), viaCenter, tolerance)) {
      atFirstCorner = true;
    } else if (isWithinTolerance(contactTrace.last_corner(), viaCenter, tolerance)) {
      atFirstCorner = false;
    } else {
      // Via is not connected at trace endpoints - skip optimization
      return false;
    }
    Polyline tracePolyline = contactTrace.polyline();
    Point checkCorner;
    if (atFirstCorner) {
      checkCorner = tracePolyline.corner(1);
    } else {
      checkCorner = tracePolyline.corner(tracePolyline.corner_count() - 2);
    }
    IntPoint roundedCheckCorner = checkCorner.to_float().round();
    int traceHalfWidth = contactTrace.get_half_width();
    int traceLayer = contactTrace.get_layer();
    int traceClClassNo = contactTrace.clearance_class_no();
    Point newViaLocation =
        reposition_via(
            p_board, p_via, roundedCheckCorner, traceHalfWidth, traceLayer, traceClClassNo);
    if (newViaLocation == null && tracePolyline.corner_count() >= 3) {

      // try to project the via to the previous line
      Point prevCorner;

      if (atFirstCorner) {
        prevCorner = tracePolyline.corner(2);
      } else {
        prevCorner = tracePolyline.corner(tracePolyline.corner_count() - 3);
      }
      FloatPoint floatCheckCorner = checkCorner.to_float();
      FloatPoint floatViaCenter = viaCenter.to_float();
      FloatPoint floatPrevCorner = prevCorner.to_float();
      if (floatCheckCorner.scalar_product(floatViaCenter, floatPrevCorner) != 0) {
        FloatLine currLine = new FloatLine(floatCheckCorner, floatPrevCorner);
        Point projection = currLine.perpendicular_projection(floatViaCenter).round();
        Vector diffVector = projection.difference_by(viaCenter);
        boolean projectionOk = true;
        AngleRestriction angleRestriction = p_board.rules.get_trace_angle_restriction();
        if (projection.equals(viaCenter)
            || angleRestriction == AngleRestriction.NINETY_DEGREE && !diffVector.is_orthogonal()
            || angleRestriction == AngleRestriction.FORTYFIVE_DEGREE
                && !diffVector.is_multiple_of_45_degree()) {
          projectionOk = false;
        }
        if (projectionOk) {
          if (MoveDrillItemAlgo.check(p_via, diffVector, 0, 0, null, p_board, null)) {
            double okLength =
                p_board.check_trace_segment(
                    viaCenter,
                    projection,
                    traceLayer,
                    p_via.netNoArr,
                    traceHalfWidth,
                    traceClClassNo,
                    false);
            if (okLength >= Integer.MAX_VALUE) {
              newViaLocation = projection;
            }
          }
        }
      }
    }
    if (newViaLocation == null) {
      return false;
    }
    if (contactPlane != null) {
      // check, that the new location is inside the contact plane
      ItemSelectionFilter filter =
          new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.CONDUCTION);
      Collection<Item> pickedItems =
          p_board.pick_items(newViaLocation, contactPlane.get_layer(), filter);
      boolean contactOk = false;
      for (Item currItem : pickedItems) {
        if (currItem == contactPlane) {
          contactOk = true;
          break;
        }
      }
      if (!contactOk) {
        return false;
      }
    }
    Vector diffVector = newViaLocation.difference_by(viaCenter);
    if (!MoveDrillItemAlgo.insert(p_via, diffVector, 9, 9, null, p_board)) {
      FRLogger.warn("OptViaAlgo.opt_plane_or_fanout_via: move via failed");
      return false;
    }
    ItemSelectionFilter filter =
        new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.TRACES);
    Collection<Item> pickedItems =
        p_board.pick_items(newViaLocation, contactTrace.get_layer(), filter);
    for (Item currItem : pickedItems) {
      ((PolylineTrace) currItem).pull_tight(true, p_trace_pull_tight_accuracy, null);
    }
    if (newViaLocation.equals(checkCorner)) {
      opt_plane_or_fanout_via(
          p_board, p_via, p_trace_pull_tight_accuracy, p_max_recursion_depth - 1);
    }
    return true;
  }

  /**
   * Tries to move the via into the direction of p_to_location as far as possible Return the new
   * location of the via, or null, if no move was possible.
   */
  private static Point reposition_via(
      RoutingBoard p_board,
      Via p_via,
      IntPoint p_to_location,
      int p_trace_half_width,
      int p_trace_layer,
      int p_trace_cl_class) {

    Point fromLocation = p_via.get_center();

    if (fromLocation.equals(p_to_location)) {
      return null;
    }

    double okLength =
        p_board.check_trace_segment(
            fromLocation,
            p_to_location,
            p_trace_layer,
            p_via.netNoArr,
            p_trace_half_width,
            p_trace_cl_class,
            false);
    if (okLength <= 0) {
      return null;
    }
    FloatPoint floatFromLocation = fromLocation.to_float();
    FloatPoint floatToLocation = p_to_location.to_float();
    FloatPoint newFloatToLocation;
    if (okLength >= Integer.MAX_VALUE) {
      newFloatToLocation = floatToLocation;
    } else {
      newFloatToLocation = floatFromLocation.change_length(floatToLocation, okLength);
    }
    Point newToLocation = newFloatToLocation.round();
    Vector delta = newToLocation.difference_by(fromLocation);
    boolean checkOk = MoveDrillItemAlgo.check(p_via, delta, 0, 0, null, p_board, null);

    if (checkOk) {
      return newToLocation;
    }

    final double cMinLength = 0.3 * p_trace_half_width + 1;

    okLength = Math.min(okLength, floatFromLocation.distance(floatToLocation));

    double currLength = okLength / 2;

    okLength = 0;
    Point result = null;

    while (currLength >= cMinLength) {
      Point checkPoint =
          floatFromLocation.change_length(floatToLocation, okLength + currLength).round();

      delta = checkPoint.difference_by(fromLocation);
      if (MoveDrillItemAlgo.check(p_via, delta, 0, 0, null, p_board, null)) {
        okLength += currLength;
        result = checkPoint;
      }
      currLength /= 2;
    }
    return result;
  }

  private static boolean reposition_via(
      RoutingBoard p_board,
      Via p_via,
      IntPoint p_to_location,
      int p_trace_half_width_1,
      int p_trace_layer_1,
      int p_trace_cl_class_1,
      IntPoint p_connect_location,
      int p_trace_half_width_2,
      int p_trace_layer_2,
      int p_trace_cl_class_2) {

    Point fromLocation = p_via.get_center();

    if (fromLocation.equals(p_to_location)) {
      FRLogger.trace("OptViaAlgo.reposition_via: fromLocation equal p_to_location");
      return false;
    }

    Vector delta = p_to_location.difference_by(fromLocation);

    if (p_board.rules.get_trace_angle_restriction() == AngleRestriction.NONE
        && delta.length_approx() <= 1.5) {
      // PullTightAlgoAnyAngle.reduce_corners may not be able to remove the new
      // generated overlap
      // because of numerical stability problems
      // That would result in an endless loop with removing the generated acute angle
      // in
      // reposition_via.
      return false;
    }

    int[] netNoArr = p_via.netNoArr;

    double okLength =
        p_board.check_trace_segment(
            fromLocation,
            p_to_location,
            p_trace_layer_1,
            netNoArr,
            p_trace_half_width_1,
            p_trace_cl_class_1,
            false);

    if (okLength < Integer.MAX_VALUE) {
      return false;
    }

    okLength =
        p_board.check_trace_segment(
            p_to_location,
            p_connect_location,
            p_trace_layer_2,
            netNoArr,
            p_trace_half_width_2,
            p_trace_cl_class_2,
            false);

    if (okLength < Integer.MAX_VALUE) {
      return false;
    }
    return MoveDrillItemAlgo.check(p_via, delta, 0, 0, null, p_board, null);
  }

  /**
   * Tries to reposition the via to a better location according to the trace costs. Returns null, if
   * no better location was found.
   */
  private static Point reposition_via(
      RoutingBoard p_board,
      Via p_via,
      int p_first_trace_half_width,
      int p_first_trace_cl_class,
      int p_first_trace_layer,
      ExpansionCostFactor p_first_trace_costs,
      Point p_first_trace_from_corner,
      int p_second_trace_half_width,
      int p_second_trace_cl_class,
      int p_second_trace_layer,
      ExpansionCostFactor p_second_trace_costs,
      Point p_second_trace_from_corner) {
    Point viaLocation = p_via.get_center();

    Vector firstDelta = p_first_trace_from_corner.difference_by(viaLocation);
    Vector secondDelta = p_second_trace_from_corner.difference_by(viaLocation);
    double scalarProduct = firstDelta.scalar_product(secondDelta);

    FloatPoint floatViaLocation = viaLocation.to_float();
    FloatPoint floatFirstTraceFromCorner = p_first_trace_from_corner.to_float();
    FloatPoint floatSecondTraceFromCorner = p_second_trace_from_corner.to_float();
    double firstTraceFromCornerDistance = floatViaLocation.distance(floatFirstTraceFromCorner);
    double secondTraceFromCornerDistance = floatViaLocation.distance(floatSecondTraceFromCorner);
    IntPoint roundedFirstTraceFromCorner = floatFirstTraceFromCorner.round();
    IntPoint roundedSecondTraceFromCorner = floatSecondTraceFromCorner.round();

    // handle case of overlapping lines first

    if (viaLocation.side_of(p_first_trace_from_corner, p_second_trace_from_corner) == Side.COLLINEAR
        && scalarProduct > 0) {
      if (secondTraceFromCornerDistance < firstTraceFromCornerDistance) {
        return reposition_via(
            p_board,
            p_via,
            roundedSecondTraceFromCorner,
            p_first_trace_half_width,
            p_first_trace_layer,
            p_first_trace_cl_class);
      }
      return reposition_via(
          p_board,
          p_via,
          roundedFirstTraceFromCorner,
          p_second_trace_half_width,
          p_second_trace_layer,
          p_second_trace_cl_class);
    }
    Point result;

    double currWeightedDistance1 =
        floatViaLocation.weighted_distance(
            floatFirstTraceFromCorner,
            p_first_trace_costs.horizontal,
            p_first_trace_costs.vertical);
    double currWeightedDistance2 =
        floatViaLocation.weighted_distance(
            floatFirstTraceFromCorner,
            p_second_trace_costs.horizontal,
            p_second_trace_costs.vertical);

    if (currWeightedDistance1 > currWeightedDistance2) {
      // try to move the via in direction of p_first_trace_from_corner
      result =
          reposition_via(
              p_board,
              p_via,
              roundedFirstTraceFromCorner,
              p_second_trace_half_width,
              p_second_trace_layer,
              p_second_trace_cl_class);
      if (result != null) {
        return result;
      }
    }

    currWeightedDistance1 =
        floatViaLocation.weighted_distance(
            floatSecondTraceFromCorner,
            p_second_trace_costs.horizontal,
            p_second_trace_costs.vertical);
    currWeightedDistance2 =
        floatViaLocation.weighted_distance(
            floatSecondTraceFromCorner,
            p_first_trace_costs.horizontal,
            p_first_trace_costs.vertical);

    if (currWeightedDistance1 > currWeightedDistance2) {
      // try to move the via in direction of p_second_trace_from_corner
      result =
          reposition_via(
              p_board,
              p_via,
              roundedSecondTraceFromCorner,
              p_first_trace_half_width,
              p_first_trace_layer,
              p_first_trace_cl_class);
      if (result != null) {
        return result;
      }
    }
    if (scalarProduct > 0
        && p_board.rules.get_trace_angle_restriction() != AngleRestriction.NINETY_DEGREE) {
      // acute angle
      IntPoint toPoint1;
      IntPoint toPoint2;
      FloatPoint floatToPoint1;
      FloatPoint floatToPoint2;
      if (firstTraceFromCornerDistance < secondTraceFromCornerDistance) {
        toPoint1 = roundedFirstTraceFromCorner;
        floatToPoint1 = floatFirstTraceFromCorner;
        floatToPoint2 =
            floatViaLocation.change_length(
                floatSecondTraceFromCorner, firstTraceFromCornerDistance);
        toPoint2 = floatToPoint2.round();
      } else {
        floatToPoint1 =
            floatViaLocation.change_length(
                floatFirstTraceFromCorner, secondTraceFromCornerDistance);
        toPoint1 = floatToPoint1.round();
        toPoint2 = roundedSecondTraceFromCorner;
        floatToPoint2 = floatSecondTraceFromCorner;
      }
      currWeightedDistance1 =
          floatToPoint1.weighted_distance(
              floatToPoint2, p_first_trace_costs.horizontal, p_first_trace_costs.vertical);
      currWeightedDistance2 =
          floatToPoint1.weighted_distance(
              floatToPoint2, p_second_trace_costs.horizontal, p_second_trace_costs.vertical);

      if (currWeightedDistance1 > currWeightedDistance2) {
        // try moving the via first into the direction of toPoint1
        result =
            reposition_via(
                p_board,
                p_via,
                toPoint1,
                p_second_trace_half_width,
                p_second_trace_layer,
                p_second_trace_cl_class);
        if (result == null) {
          result =
              reposition_via(
                  p_board,
                  p_via,
                  toPoint2,
                  p_first_trace_half_width,
                  p_first_trace_layer,
                  p_first_trace_cl_class);
        }
      } else {
        // try moving the via first into the direction of toPoint2
        result =
            reposition_via(
                p_board,
                p_via,
                toPoint2,
                p_first_trace_half_width,
                p_first_trace_layer,
                p_first_trace_cl_class);
        if (result == null) {
          result =
              reposition_via(
                  p_board,
                  p_via,
                  toPoint1,
                  p_second_trace_half_width,
                  p_second_trace_layer,
                  p_second_trace_cl_class);
        }
      }
      if (result != null) {
        return result;
      }
    }

    // try decomposition in axisparallel parts

    if (!firstDelta.is_orthogonal()) {
      FloatPoint floatCheckLocation =
          new FloatPoint(floatViaLocation.x, floatFirstTraceFromCorner.y);

      currWeightedDistance1 =
          floatViaLocation.weighted_distance(
              floatFirstTraceFromCorner,
              p_first_trace_costs.horizontal,
              p_first_trace_costs.vertical);
      currWeightedDistance2 =
          floatViaLocation.weighted_distance(
              floatCheckLocation, p_second_trace_costs.horizontal, p_second_trace_costs.vertical);
      double currWeightedDistance3 =
          floatCheckLocation.weighted_distance(
              floatFirstTraceFromCorner,
              p_first_trace_costs.horizontal,
              p_first_trace_costs.vertical);

      if (currWeightedDistance1 > currWeightedDistance2 + currWeightedDistance3) {
        IntPoint checkLocation = floatCheckLocation.round();
        boolean checkOk =
            reposition_via(
                p_board,
                p_via,
                checkLocation,
                p_second_trace_half_width,
                p_second_trace_layer,
                p_second_trace_cl_class,
                roundedFirstTraceFromCorner,
                p_first_trace_half_width,
                p_first_trace_layer,
                p_first_trace_cl_class);
        if (checkOk) {
          return checkLocation;
        }
      }

      floatCheckLocation = new FloatPoint(floatFirstTraceFromCorner.x, floatViaLocation.y);

      currWeightedDistance2 =
          floatViaLocation.weighted_distance(
              floatCheckLocation, p_second_trace_costs.horizontal, p_second_trace_costs.vertical);
      currWeightedDistance3 =
          floatCheckLocation.weighted_distance(
              floatFirstTraceFromCorner,
              p_first_trace_costs.horizontal,
              p_first_trace_costs.vertical);

      if (currWeightedDistance1 > currWeightedDistance2 + currWeightedDistance3) {
        IntPoint checkLocation = floatCheckLocation.round();
        boolean checkOk =
            reposition_via(
                p_board,
                p_via,
                checkLocation,
                p_second_trace_half_width,
                p_second_trace_layer,
                p_second_trace_cl_class,
                roundedFirstTraceFromCorner,
                p_first_trace_half_width,
                p_first_trace_layer,
                p_first_trace_cl_class);
        if (checkOk) {
          return checkLocation;
        }
      }
    }

    if (!secondDelta.is_orthogonal()) {
      FloatPoint floatCheckLocation =
          new FloatPoint(floatViaLocation.x, floatSecondTraceFromCorner.y);

      currWeightedDistance1 =
          floatViaLocation.weighted_distance(
              floatSecondTraceFromCorner,
              p_second_trace_costs.horizontal,
              p_second_trace_costs.vertical);
      currWeightedDistance2 =
          floatViaLocation.weighted_distance(
              floatCheckLocation, p_first_trace_costs.horizontal, p_first_trace_costs.vertical);
      double currWeightedDistance3 =
          floatCheckLocation.weighted_distance(
              floatSecondTraceFromCorner,
              p_second_trace_costs.horizontal,
              p_second_trace_costs.vertical);

      if (currWeightedDistance1 > currWeightedDistance2 + currWeightedDistance3) {
        IntPoint checkLocation = floatCheckLocation.round();
        boolean checkOk =
            reposition_via(
                p_board,
                p_via,
                checkLocation,
                p_first_trace_half_width,
                p_first_trace_layer,
                p_first_trace_cl_class,
                roundedSecondTraceFromCorner,
                p_second_trace_half_width,
                p_second_trace_layer,
                p_second_trace_cl_class);
        if (checkOk) {
          return checkLocation;
        }
      }

      floatCheckLocation = new FloatPoint(floatSecondTraceFromCorner.x, floatViaLocation.y);

      currWeightedDistance2 =
          floatViaLocation.weighted_distance(
              floatCheckLocation, p_first_trace_costs.horizontal, p_first_trace_costs.vertical);
      currWeightedDistance3 =
          floatCheckLocation.weighted_distance(
              floatSecondTraceFromCorner,
              p_second_trace_costs.horizontal,
              p_second_trace_costs.vertical);

      if (currWeightedDistance1 > currWeightedDistance2 + currWeightedDistance3) {
        IntPoint checkLocation = floatCheckLocation.round();
        boolean checkOk =
            reposition_via(
                p_board,
                p_via,
                checkLocation,
                p_first_trace_half_width,
                p_first_trace_layer,
                p_first_trace_cl_class,
                roundedSecondTraceFromCorner,
                p_second_trace_half_width,
                p_second_trace_layer,
                p_second_trace_cl_class);
        if (checkOk) {
          return checkLocation;
        }
      }
    }
    return null;
  }

  /**
   * Checks if two points are within the specified tolerance distance. Uses Manhattan distance for
   * efficiency, matching the logic in DrillItem.get_normal_contacts().
   */
  private static boolean isWithinTolerance(Point p1, Point p2, int tolerance) {
    if (p1 == null || p2 == null) {
      return false;
    }
    // Convert to FloatPoint for distance calculation
    FloatPoint fp1 = p1.to_float();
    FloatPoint fp2 = p2.to_float();

    // Use Manhattan distance (|x1-x2| + |y1-y2|) which is faster than Euclidean
    // and sufficient for connectivity detection
    double dx = Math.abs(fp1.x - fp2.x);
    double dy = Math.abs(fp1.y - fp2.y);
    return (dx + dy) <= tolerance;
  }
}
