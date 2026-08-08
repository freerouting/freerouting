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
  public static boolean optViaLocation(
      RoutingBoard pBoard,
      Via pVia,
      ExpansionCostFactor[] pTraceCostArr,
      int pTracePullTightAccuracy,
      int pMaxRecursionDepth) {
    if (pVia.isShoveFixed()) {
      return false;
    }
    if (pMaxRecursionDepth <= 0) {
      FRLogger.debug("OptViaAlgo.opt_via_location: probably endless loop");
      return false;
    }
    Collection<Item> contacts = pVia.getNormalContacts();
    boolean isPlaneOrFanoutVia = contacts.size() == 1;
    PolylineTrace firstTrace = null;
    PolylineTrace secondTrace = null;
    if (!isPlaneOrFanoutVia) {
      if (contacts.size() != 2) {
        return false;
      }
      Iterator<Item> it = contacts.iterator();
      Item currItem = it.next();
      if (currItem.isShoveFixed() || !(currItem instanceof PolylineTrace)) {
        if (currItem instanceof ConductionArea) {
          isPlaneOrFanoutVia = true;
        } else {
          return false;
        }
      } else {
        firstTrace = (PolylineTrace) currItem;
      }
      currItem = it.next();
      if (currItem.isShoveFixed() || !(currItem instanceof PolylineTrace)) {
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
      return optPlaneOrFanoutVia(pBoard, pVia, pTracePullTightAccuracy, pMaxRecursionDepth);
    }
    Point viaCenter = pVia.getCenter();
    int firstLayer = firstTrace.getLayer();
    int secondLayer = secondTrace.getLayer();
    Point firstTraceFromCorner;
    Point secondTraceFromCorner;

    // calculate firstTraceFromCorner and secondTraceFromCorner
    // Use tolerance-based comparison to match connectivity detection logic
    int tolerance = (int) (pVia.minWidth() / 2) + 1;

    if (isWithinTolerance(firstTrace.firstCorner(), viaCenter, tolerance)) {
      firstTraceFromCorner = firstTrace.polyline().corner(1);
    } else if (isWithinTolerance(firstTrace.lastCorner(), viaCenter, tolerance)) {
      firstTraceFromCorner = firstTrace.polyline().corner(firstTrace.polyline().cornerCount() - 2);
    } else {
      // Via is not connected at trace endpoints - skip optimization
      return false;
    }

    if (isWithinTolerance(secondTrace.firstCorner(), viaCenter, tolerance)) {
      secondTraceFromCorner = secondTrace.polyline().corner(1);
    } else if (isWithinTolerance(secondTrace.lastCorner(), viaCenter, tolerance)) {
      secondTraceFromCorner =
          secondTrace.polyline().corner(secondTrace.polyline().cornerCount() - 2);
    } else {
      // Via is not connected at trace endpoints - skip optimization
      return false;
    }

    ExpansionCostFactor firstLayerTraceCosts;
    ExpansionCostFactor secondLayerTraceCosts;
    if (pTraceCostArr != null) {
      firstLayerTraceCosts = pTraceCostArr[firstLayer];
      secondLayerTraceCosts = pTraceCostArr[secondLayer];
    } else {
      firstLayerTraceCosts = new ExpansionCostFactor(1, 1);
      secondLayerTraceCosts = firstLayerTraceCosts;
    }

    Point newLocation =
        repositionVia(
            pBoard,
            pVia,
            firstTrace.getHalfWidth(),
            firstTrace.clearanceClassNo(),
            firstTrace.getLayer(),
            firstLayerTraceCosts,
            firstTraceFromCorner,
            secondTrace.getHalfWidth(),
            secondTrace.clearanceClassNo(),
            secondTrace.getLayer(),
            secondLayerTraceCosts,
            secondTraceFromCorner);
    if (newLocation == null || newLocation.equals(viaCenter)) {
      return false;
    }
    Vector delta = newLocation.differenceBy(viaCenter);
    if (!MoveDrillItemAlgo.insert(pVia, delta, 9, 9, null, pBoard)) {
      FRLogger.warn("OptViaAlgo.opt_via_location: move via failed");
      return false;
    }
    ItemSelectionFilter filter =
        new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.TRACES);
    Collection<Item> pickedItems = pBoard.pickItems(newLocation, firstTrace.getLayer(), filter);
    for (Item currItem : pickedItems) {
      ((PolylineTrace) currItem).pullTight(true, pTracePullTightAccuracy, null);
    }
    pickedItems = pBoard.pickItems(newLocation, secondTrace.getLayer(), filter);
    for (Item currItem : pickedItems) {
      ((PolylineTrace) currItem).pullTight(true, pTracePullTightAccuracy, null);
    }
    filter = new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.VIAS);
    pickedItems = pBoard.pickItems(newLocation, firstTrace.getLayer(), filter);
    for (Item currItem : pickedItems) {
      optViaLocation(
          pBoard, (Via) currItem, pTraceCostArr, pTracePullTightAccuracy, pMaxRecursionDepth - 1);
      break;
    }
    return true;
  }

  /** Optimisations for vias with only 1 connected Trace (Plane or Fanout Vias). */
  private static boolean optPlaneOrFanoutVia(
      RoutingBoard pBoard, Via pVia, int pTracePullTightAccuracy, int pMaxRecursionDepth) {
    if (pMaxRecursionDepth <= 0) {
      FRLogger.debug("OptViaAlgo.opt_plane_or_fanout_via: probably endless loop");
      return false;
    }
    Collection<Item> contactList = pVia.getNormalContacts();
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
        if (currContact.isShoveFixed() || contactTrace != null) {
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
    Point viaCenter = pVia.getCenter();

    // Use tolerance based on via size, matching the logic in opt_via_location
    int tolerance = (int) (pVia.minWidth() / 2) + 1;

    boolean atFirstCorner;
    if (isWithinTolerance(contactTrace.firstCorner(), viaCenter, tolerance)) {
      atFirstCorner = true;
    } else if (isWithinTolerance(contactTrace.lastCorner(), viaCenter, tolerance)) {
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
      checkCorner = tracePolyline.corner(tracePolyline.cornerCount() - 2);
    }
    IntPoint roundedCheckCorner = checkCorner.toFloat().round();
    int traceHalfWidth = contactTrace.getHalfWidth();
    int traceLayer = contactTrace.getLayer();
    int traceClClassNo = contactTrace.clearanceClassNo();
    Point newViaLocation =
        repositionVia(pBoard, pVia, roundedCheckCorner, traceHalfWidth, traceLayer, traceClClassNo);
    if (newViaLocation == null && tracePolyline.cornerCount() >= 3) {

      // try to project the via to the previous line
      Point prevCorner;

      if (atFirstCorner) {
        prevCorner = tracePolyline.corner(2);
      } else {
        prevCorner = tracePolyline.corner(tracePolyline.cornerCount() - 3);
      }
      FloatPoint floatCheckCorner = checkCorner.toFloat();
      FloatPoint floatViaCenter = viaCenter.toFloat();
      FloatPoint floatPrevCorner = prevCorner.toFloat();
      if (floatCheckCorner.scalarProduct(floatViaCenter, floatPrevCorner) != 0) {
        FloatLine currLine = new FloatLine(floatCheckCorner, floatPrevCorner);
        Point projection = currLine.perpendicularProjection(floatViaCenter).round();
        Vector diffVector = projection.differenceBy(viaCenter);
        boolean projectionOk = true;
        AngleRestriction angleRestriction = pBoard.rules.getTraceAngleRestriction();
        if (projection.equals(viaCenter)
            || angleRestriction == AngleRestriction.NINETY_DEGREE && !diffVector.isOrthogonal()
            || angleRestriction == AngleRestriction.FORTYFIVE_DEGREE
                && !diffVector.isMultipleOf45Degree()) {
          projectionOk = false;
        }
        if (projectionOk) {
          if (MoveDrillItemAlgo.check(pVia, diffVector, 0, 0, null, pBoard, null)) {
            double okLength =
                pBoard.checkTraceSegment(
                    viaCenter,
                    projection,
                    traceLayer,
                    pVia.netNoArr,
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
          pBoard.pickItems(newViaLocation, contactPlane.getLayer(), filter);
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
    Vector diffVector = newViaLocation.differenceBy(viaCenter);
    if (!MoveDrillItemAlgo.insert(pVia, diffVector, 9, 9, null, pBoard)) {
      FRLogger.warn("OptViaAlgo.opt_plane_or_fanout_via: move via failed");
      return false;
    }
    ItemSelectionFilter filter =
        new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.TRACES);
    Collection<Item> pickedItems =
        pBoard.pickItems(newViaLocation, contactTrace.getLayer(), filter);
    for (Item currItem : pickedItems) {
      ((PolylineTrace) currItem).pullTight(true, pTracePullTightAccuracy, null);
    }
    if (newViaLocation.equals(checkCorner)) {
      optPlaneOrFanoutVia(pBoard, pVia, pTracePullTightAccuracy, pMaxRecursionDepth - 1);
    }
    return true;
  }

  /**
   * Tries to move the via into the direction of p_to_location as far as possible Return the new
   * location of the via, or null, if no move was possible.
   */
  private static Point repositionVia(
      RoutingBoard pBoard,
      Via pVia,
      IntPoint pToLocation,
      int pTraceHalfWidth,
      int pTraceLayer,
      int pTraceClClass) {

    Point fromLocation = pVia.getCenter();

    if (fromLocation.equals(pToLocation)) {
      return null;
    }

    double okLength =
        pBoard.checkTraceSegment(
            fromLocation,
            pToLocation,
            pTraceLayer,
            pVia.netNoArr,
            pTraceHalfWidth,
            pTraceClClass,
            false);
    if (okLength <= 0) {
      return null;
    }
    FloatPoint floatFromLocation = fromLocation.toFloat();
    FloatPoint floatToLocation = pToLocation.toFloat();
    FloatPoint newFloatToLocation;
    if (okLength >= Integer.MAX_VALUE) {
      newFloatToLocation = floatToLocation;
    } else {
      newFloatToLocation = floatFromLocation.changeLength(floatToLocation, okLength);
    }
    Point newToLocation = newFloatToLocation.round();
    Vector delta = newToLocation.differenceBy(fromLocation);
    boolean checkOk = MoveDrillItemAlgo.check(pVia, delta, 0, 0, null, pBoard, null);

    if (checkOk) {
      return newToLocation;
    }

    final double cMinLength = 0.3 * pTraceHalfWidth + 1;

    okLength = Math.min(okLength, floatFromLocation.distance(floatToLocation));

    double currLength = okLength / 2;

    okLength = 0;
    Point result = null;

    while (currLength >= cMinLength) {
      Point checkPoint =
          floatFromLocation.changeLength(floatToLocation, okLength + currLength).round();

      delta = checkPoint.differenceBy(fromLocation);
      if (MoveDrillItemAlgo.check(pVia, delta, 0, 0, null, pBoard, null)) {
        okLength += currLength;
        result = checkPoint;
      }
      currLength /= 2;
    }
    return result;
  }

  private static boolean repositionVia(
      RoutingBoard pBoard,
      Via pVia,
      IntPoint pToLocation,
      int pTraceHalfWidth1,
      int pTraceLayer1,
      int pTraceClClass1,
      IntPoint pConnectLocation,
      int pTraceHalfWidth2,
      int pTraceLayer2,
      int pTraceClClass2) {

    Point fromLocation = pVia.getCenter();

    if (fromLocation.equals(pToLocation)) {
      FRLogger.trace("OptViaAlgo.reposition_via: fromLocation equal p_to_location");
      return false;
    }

    Vector delta = pToLocation.differenceBy(fromLocation);

    if (pBoard.rules.getTraceAngleRestriction() == AngleRestriction.NONE
        && delta.lengthApprox() <= 1.5) {
      // PullTightAlgoAnyAngle.reduce_corners may not be able to remove the new
      // generated overlap
      // because of numerical stability problems
      // That would result in an endless loop with removing the generated acute angle
      // in
      // reposition_via.
      return false;
    }

    int[] netNoArr = pVia.netNoArr;

    double okLength =
        pBoard.checkTraceSegment(
            fromLocation,
            pToLocation,
            pTraceLayer1,
            netNoArr,
            pTraceHalfWidth1,
            pTraceClClass1,
            false);

    if (okLength < Integer.MAX_VALUE) {
      return false;
    }

    okLength =
        pBoard.checkTraceSegment(
            pToLocation,
            pConnectLocation,
            pTraceLayer2,
            netNoArr,
            pTraceHalfWidth2,
            pTraceClClass2,
            false);

    if (okLength < Integer.MAX_VALUE) {
      return false;
    }
    return MoveDrillItemAlgo.check(pVia, delta, 0, 0, null, pBoard, null);
  }

  /**
   * Tries to reposition the via to a better location according to the trace costs. Returns null, if
   * no better location was found.
   */
  private static Point repositionVia(
      RoutingBoard pBoard,
      Via pVia,
      int pFirstTraceHalfWidth,
      int pFirstTraceClClass,
      int pFirstTraceLayer,
      ExpansionCostFactor pFirstTraceCosts,
      Point pFirstTraceFromCorner,
      int pSecondTraceHalfWidth,
      int pSecondTraceClClass,
      int pSecondTraceLayer,
      ExpansionCostFactor pSecondTraceCosts,
      Point pSecondTraceFromCorner) {
    Point viaLocation = pVia.getCenter();

    Vector firstDelta = pFirstTraceFromCorner.differenceBy(viaLocation);
    Vector secondDelta = pSecondTraceFromCorner.differenceBy(viaLocation);
    double scalarProduct = firstDelta.scalarProduct(secondDelta);

    FloatPoint floatViaLocation = viaLocation.toFloat();
    FloatPoint floatFirstTraceFromCorner = pFirstTraceFromCorner.toFloat();
    FloatPoint floatSecondTraceFromCorner = pSecondTraceFromCorner.toFloat();
    double firstTraceFromCornerDistance = floatViaLocation.distance(floatFirstTraceFromCorner);
    double secondTraceFromCornerDistance = floatViaLocation.distance(floatSecondTraceFromCorner);
    IntPoint roundedFirstTraceFromCorner = floatFirstTraceFromCorner.round();
    IntPoint roundedSecondTraceFromCorner = floatSecondTraceFromCorner.round();

    // handle case of overlapping lines first

    if (viaLocation.sideOf(pFirstTraceFromCorner, pSecondTraceFromCorner) == Side.COLLINEAR
        && scalarProduct > 0) {
      if (secondTraceFromCornerDistance < firstTraceFromCornerDistance) {
        return repositionVia(
            pBoard,
            pVia,
            roundedSecondTraceFromCorner,
            pFirstTraceHalfWidth,
            pFirstTraceLayer,
            pFirstTraceClClass);
      }
      return repositionVia(
          pBoard,
          pVia,
          roundedFirstTraceFromCorner,
          pSecondTraceHalfWidth,
          pSecondTraceLayer,
          pSecondTraceClClass);
    }
    Point result;

    double currWeightedDistance1 =
        floatViaLocation.weightedDistance(
            floatFirstTraceFromCorner, pFirstTraceCosts.horizontal, pFirstTraceCosts.vertical);
    double currWeightedDistance2 =
        floatViaLocation.weightedDistance(
            floatFirstTraceFromCorner, pSecondTraceCosts.horizontal, pSecondTraceCosts.vertical);

    if (currWeightedDistance1 > currWeightedDistance2) {
      // try to move the via in direction of p_first_trace_from_corner
      result =
          repositionVia(
              pBoard,
              pVia,
              roundedFirstTraceFromCorner,
              pSecondTraceHalfWidth,
              pSecondTraceLayer,
              pSecondTraceClClass);
      if (result != null) {
        return result;
      }
    }

    currWeightedDistance1 =
        floatViaLocation.weightedDistance(
            floatSecondTraceFromCorner, pSecondTraceCosts.horizontal, pSecondTraceCosts.vertical);
    currWeightedDistance2 =
        floatViaLocation.weightedDistance(
            floatSecondTraceFromCorner, pFirstTraceCosts.horizontal, pFirstTraceCosts.vertical);

    if (currWeightedDistance1 > currWeightedDistance2) {
      // try to move the via in direction of p_second_trace_from_corner
      result =
          repositionVia(
              pBoard,
              pVia,
              roundedSecondTraceFromCorner,
              pFirstTraceHalfWidth,
              pFirstTraceLayer,
              pFirstTraceClClass);
      if (result != null) {
        return result;
      }
    }
    if (scalarProduct > 0
        && pBoard.rules.getTraceAngleRestriction() != AngleRestriction.NINETY_DEGREE) {
      // acute angle
      IntPoint toPoint1;
      IntPoint toPoint2;
      FloatPoint floatToPoint1;
      FloatPoint floatToPoint2;
      if (firstTraceFromCornerDistance < secondTraceFromCornerDistance) {
        toPoint1 = roundedFirstTraceFromCorner;
        floatToPoint1 = floatFirstTraceFromCorner;
        floatToPoint2 =
            floatViaLocation.changeLength(floatSecondTraceFromCorner, firstTraceFromCornerDistance);
        toPoint2 = floatToPoint2.round();
      } else {
        floatToPoint1 =
            floatViaLocation.changeLength(floatFirstTraceFromCorner, secondTraceFromCornerDistance);
        toPoint1 = floatToPoint1.round();
        toPoint2 = roundedSecondTraceFromCorner;
        floatToPoint2 = floatSecondTraceFromCorner;
      }
      currWeightedDistance1 =
          floatToPoint1.weightedDistance(
              floatToPoint2, pFirstTraceCosts.horizontal, pFirstTraceCosts.vertical);
      currWeightedDistance2 =
          floatToPoint1.weightedDistance(
              floatToPoint2, pSecondTraceCosts.horizontal, pSecondTraceCosts.vertical);

      if (currWeightedDistance1 > currWeightedDistance2) {
        // try moving the via first into the direction of toPoint1
        result =
            repositionVia(
                pBoard,
                pVia,
                toPoint1,
                pSecondTraceHalfWidth,
                pSecondTraceLayer,
                pSecondTraceClClass);
        if (result == null) {
          result =
              repositionVia(
                  pBoard,
                  pVia,
                  toPoint2,
                  pFirstTraceHalfWidth,
                  pFirstTraceLayer,
                  pFirstTraceClClass);
        }
      } else {
        // try moving the via first into the direction of toPoint2
        result =
            repositionVia(
                pBoard, pVia, toPoint2, pFirstTraceHalfWidth, pFirstTraceLayer, pFirstTraceClClass);
        if (result == null) {
          result =
              repositionVia(
                  pBoard,
                  pVia,
                  toPoint1,
                  pSecondTraceHalfWidth,
                  pSecondTraceLayer,
                  pSecondTraceClClass);
        }
      }
      if (result != null) {
        return result;
      }
    }

    // try decomposition in axisparallel parts

    if (!firstDelta.isOrthogonal()) {
      FloatPoint floatCheckLocation =
          new FloatPoint(floatViaLocation.x, floatFirstTraceFromCorner.y);

      currWeightedDistance1 =
          floatViaLocation.weightedDistance(
              floatFirstTraceFromCorner, pFirstTraceCosts.horizontal, pFirstTraceCosts.vertical);
      currWeightedDistance2 =
          floatViaLocation.weightedDistance(
              floatCheckLocation, pSecondTraceCosts.horizontal, pSecondTraceCosts.vertical);
      double currWeightedDistance3 =
          floatCheckLocation.weightedDistance(
              floatFirstTraceFromCorner, pFirstTraceCosts.horizontal, pFirstTraceCosts.vertical);

      if (currWeightedDistance1 > currWeightedDistance2 + currWeightedDistance3) {
        IntPoint checkLocation = floatCheckLocation.round();
        boolean checkOk =
            repositionVia(
                pBoard,
                pVia,
                checkLocation,
                pSecondTraceHalfWidth,
                pSecondTraceLayer,
                pSecondTraceClClass,
                roundedFirstTraceFromCorner,
                pFirstTraceHalfWidth,
                pFirstTraceLayer,
                pFirstTraceClClass);
        if (checkOk) {
          return checkLocation;
        }
      }

      floatCheckLocation = new FloatPoint(floatFirstTraceFromCorner.x, floatViaLocation.y);

      currWeightedDistance2 =
          floatViaLocation.weightedDistance(
              floatCheckLocation, pSecondTraceCosts.horizontal, pSecondTraceCosts.vertical);
      currWeightedDistance3 =
          floatCheckLocation.weightedDistance(
              floatFirstTraceFromCorner, pFirstTraceCosts.horizontal, pFirstTraceCosts.vertical);

      if (currWeightedDistance1 > currWeightedDistance2 + currWeightedDistance3) {
        IntPoint checkLocation = floatCheckLocation.round();
        boolean checkOk =
            repositionVia(
                pBoard,
                pVia,
                checkLocation,
                pSecondTraceHalfWidth,
                pSecondTraceLayer,
                pSecondTraceClClass,
                roundedFirstTraceFromCorner,
                pFirstTraceHalfWidth,
                pFirstTraceLayer,
                pFirstTraceClClass);
        if (checkOk) {
          return checkLocation;
        }
      }
    }

    if (!secondDelta.isOrthogonal()) {
      FloatPoint floatCheckLocation =
          new FloatPoint(floatViaLocation.x, floatSecondTraceFromCorner.y);

      currWeightedDistance1 =
          floatViaLocation.weightedDistance(
              floatSecondTraceFromCorner, pSecondTraceCosts.horizontal, pSecondTraceCosts.vertical);
      currWeightedDistance2 =
          floatViaLocation.weightedDistance(
              floatCheckLocation, pFirstTraceCosts.horizontal, pFirstTraceCosts.vertical);
      double currWeightedDistance3 =
          floatCheckLocation.weightedDistance(
              floatSecondTraceFromCorner, pSecondTraceCosts.horizontal, pSecondTraceCosts.vertical);

      if (currWeightedDistance1 > currWeightedDistance2 + currWeightedDistance3) {
        IntPoint checkLocation = floatCheckLocation.round();
        boolean checkOk =
            repositionVia(
                pBoard,
                pVia,
                checkLocation,
                pFirstTraceHalfWidth,
                pFirstTraceLayer,
                pFirstTraceClClass,
                roundedSecondTraceFromCorner,
                pSecondTraceHalfWidth,
                pSecondTraceLayer,
                pSecondTraceClClass);
        if (checkOk) {
          return checkLocation;
        }
      }

      floatCheckLocation = new FloatPoint(floatSecondTraceFromCorner.x, floatViaLocation.y);

      currWeightedDistance2 =
          floatViaLocation.weightedDistance(
              floatCheckLocation, pFirstTraceCosts.horizontal, pFirstTraceCosts.vertical);
      currWeightedDistance3 =
          floatCheckLocation.weightedDistance(
              floatSecondTraceFromCorner, pSecondTraceCosts.horizontal, pSecondTraceCosts.vertical);

      if (currWeightedDistance1 > currWeightedDistance2 + currWeightedDistance3) {
        IntPoint checkLocation = floatCheckLocation.round();
        boolean checkOk =
            repositionVia(
                pBoard,
                pVia,
                checkLocation,
                pFirstTraceHalfWidth,
                pFirstTraceLayer,
                pFirstTraceClClass,
                roundedSecondTraceFromCorner,
                pSecondTraceHalfWidth,
                pSecondTraceLayer,
                pSecondTraceClClass);
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
    FloatPoint fp1 = p1.toFloat();
    FloatPoint fp2 = p2.toFloat();

    // Use Manhattan distance (|x1-x2| + |y1-y2|) which is faster than Euclidean
    // and sufficient for connectivity detection
    double dx = Math.abs(fp1.x - fp2.x);
    double dy = Math.abs(fp1.y - fp2.y);
    return (dx + dy) <= tolerance;
  }
}
