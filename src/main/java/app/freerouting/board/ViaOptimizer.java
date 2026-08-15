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
public final class ViaOptimizer {

  private ViaOptimizer() {}

  /**
   * Optimizes the location of a via connected to at most 2 traces according to the trace costs on
   * the layers of the connected traces If traceCostArr == null, the horizontal and vertical trace
   * costs will be set to 1. Returns false, if the via was not changed.
   */
  public static boolean optViaLocation(
      RoutingBoard board,
      Via via,
      ExpansionCostFactor[] traceCostArr,
      int tracePullTightAccuracy,
      int maxRecursionDepth) {
    if (via.isShoveFixed()) {
      return false;
    }
    if (maxRecursionDepth <= 0) {
      FRLogger.debug("OptViaAlgo.opt_via_location: probably endless loop");
      return false;
    }
    Collection<Item> contacts = via.getNormalContacts();
    boolean isPlaneOrFanoutVia = contacts.size() == 1;
    PolylineTrace firstTrace = null;
    PolylineTrace secondTrace = null;
    if (!isPlaneOrFanoutVia) {
      if (contacts.size() != 2) {
        return false;
      }
      Iterator<Item> it = contacts.iterator();
      Item currentItem = it.next();
      if (currentItem.isShoveFixed() || !(currentItem instanceof PolylineTrace)) {
        if (currentItem instanceof ConductionArea) {
          isPlaneOrFanoutVia = true;
        } else {
          return false;
        }
      } else {
        firstTrace = (PolylineTrace) currentItem;
      }
      currentItem = it.next();
      if (currentItem.isShoveFixed() || !(currentItem instanceof PolylineTrace)) {
        if (currentItem instanceof ConductionArea) {
          isPlaneOrFanoutVia = true;
        } else {
          return false;
        }
      } else {
        secondTrace = (PolylineTrace) currentItem;
      }
    }
    if (isPlaneOrFanoutVia) {
      return optPlaneOrFanoutVia(board, via, tracePullTightAccuracy, maxRecursionDepth);
    }
    Point viaCenter = via.getCenter();
    int firstLayer = firstTrace.getLayer();
    int secondLayer = secondTrace.getLayer();
    Point firstTraceFromCorner;
    Point secondTraceFromCorner;

    // calculate firstTraceFromCorner and secondTraceFromCorner
    // Use tolerance-based comparison to match connectivity detection logic
    int tolerance = (int) (via.minWidth() / 2) + 1;

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
    if (traceCostArr != null) {
      firstLayerTraceCosts = traceCostArr[firstLayer];
      secondLayerTraceCosts = traceCostArr[secondLayer];
    } else {
      firstLayerTraceCosts = new ExpansionCostFactor(1, 1);
      secondLayerTraceCosts = firstLayerTraceCosts;
    }

    Point newLocation =
        repositionVia(
            board,
            via,
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
    if (!DrillItemMover.insert(via, delta, 9, 9, null, board)) {
      FRLogger.warn("OptViaAlgo.opt_via_location: move via failed");
      return false;
    }
    ItemSelectionFilter filter =
        new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.TRACES);
    Collection<Item> pickedItems = board.pickItems(newLocation, firstTrace.getLayer(), filter);
    for (Item currentItem : pickedItems) {
      ((PolylineTrace) currentItem).pullTight(true, tracePullTightAccuracy, null);
    }
    pickedItems = board.pickItems(newLocation, secondTrace.getLayer(), filter);
    for (Item currentItem : pickedItems) {
      ((PolylineTrace) currentItem).pullTight(true, tracePullTightAccuracy, null);
    }
    filter = new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.VIAS);
    pickedItems = board.pickItems(newLocation, firstTrace.getLayer(), filter);
    for (Item currentItem : pickedItems) {
      optViaLocation(
          board, (Via) currentItem, traceCostArr, tracePullTightAccuracy, maxRecursionDepth - 1);
      break;
    }
    return true;
  }

  /** Optimisations for vias with only 1 connected Trace (Plane or Fanout Vias). */
  private static boolean optPlaneOrFanoutVia(
      RoutingBoard board, Via via, int tracePullTightAccuracy, int maxRecursionDepth) {
    if (maxRecursionDepth <= 0) {
      FRLogger.debug("OptViaAlgo.opt_plane_or_fanout_via: probably endless loop");
      return false;
    }
    Collection<Item> contactList = via.getNormalContacts();
    if (contactList.isEmpty()) {
      return false;
    }
    ConductionArea contactPlane = null;
    PolylineTrace contactTrace = null;
    for (Item currentContact : contactList) {
      if (currentContact instanceof ConductionArea area) {
        if (contactPlane != null) {
          return false;
        }
        contactPlane = area;
      } else if (currentContact instanceof PolylineTrace trace) {
        if (currentContact.isShoveFixed() || contactTrace != null) {
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
    Point viaCenter = via.getCenter();

    // Use tolerance based on via size, matching the logic in opt_via_location
    int tolerance = (int) (via.minWidth() / 2) + 1;

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
        repositionVia(board, via, roundedCheckCorner, traceHalfWidth, traceLayer, traceClClassNo);
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
        FloatLine currentLine = new FloatLine(floatCheckCorner, floatPrevCorner);
        Point projection = currentLine.perpendicularProjection(floatViaCenter).round();
        Vector diffVector = projection.differenceBy(viaCenter);
        boolean projectionOk = true;
        AngleRestriction angleRestriction = board.rules.getTraceAngleRestriction();
        if (projection.equals(viaCenter)
            || angleRestriction == AngleRestriction.NINETY_DEGREE && !diffVector.isOrthogonal()
            || angleRestriction == AngleRestriction.FORTYFIVE_DEGREE
                && !diffVector.isMultipleOf45Degree()) {
          projectionOk = false;
        }
        if (projectionOk) {
          if (DrillItemMover.check(via, diffVector, 0, 0, null, board, null)) {
            double okLength =
                board.checkTraceSegment(
                    viaCenter,
                    projection,
                    traceLayer,
                    via.netNoArr,
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
          board.pickItems(newViaLocation, contactPlane.getLayer(), filter);
      boolean contactOk = false;
      for (Item currentItem : pickedItems) {
        if (currentItem == contactPlane) {
          contactOk = true;
          break;
        }
      }
      if (!contactOk) {
        return false;
      }
    }
    Vector diffVector = newViaLocation.differenceBy(viaCenter);
    if (!DrillItemMover.insert(via, diffVector, 9, 9, null, board)) {
      FRLogger.warn("OptViaAlgo.opt_plane_or_fanout_via: move via failed");
      return false;
    }
    ItemSelectionFilter filter =
        new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.TRACES);
    Collection<Item> pickedItems = board.pickItems(newViaLocation, contactTrace.getLayer(), filter);
    for (Item currentItem : pickedItems) {
      ((PolylineTrace) currentItem).pullTight(true, tracePullTightAccuracy, null);
    }
    if (newViaLocation.equals(checkCorner)) {
      optPlaneOrFanoutVia(board, via, tracePullTightAccuracy, maxRecursionDepth - 1);
    }
    return true;
  }

  /**
   * Tries to move the via into the direction of toLocation as far as possible Return the new
   * location of the via, or null, if no move was possible.
   */
  private static Point repositionVia(
      RoutingBoard board,
      Via via,
      IntPoint toLocation,
      int traceHalfWidth,
      int traceLayer,
      int traceClClass) {

    Point fromLocation = via.getCenter();

    if (fromLocation.equals(toLocation)) {
      return null;
    }

    double okLength =
        board.checkTraceSegment(
            fromLocation,
            toLocation,
            traceLayer,
            via.netNoArr,
            traceHalfWidth,
            traceClClass,
            false);
    if (okLength <= 0) {
      return null;
    }
    FloatPoint floatFromLocation = fromLocation.toFloat();
    FloatPoint floatToLocation = toLocation.toFloat();
    FloatPoint newFloatToLocation;
    if (okLength >= Integer.MAX_VALUE) {
      newFloatToLocation = floatToLocation;
    } else {
      newFloatToLocation = floatFromLocation.changeLength(floatToLocation, okLength);
    }
    Point newToLocation = newFloatToLocation.round();
    Vector delta = newToLocation.differenceBy(fromLocation);
    boolean checkOk = DrillItemMover.check(via, delta, 0, 0, null, board, null);

    if (checkOk) {
      return newToLocation;
    }

    final double minLength = 0.3 * traceHalfWidth + 1;

    okLength = Math.min(okLength, floatFromLocation.distance(floatToLocation));

    double currentLength = okLength / 2;

    okLength = 0;
    Point result = null;

    while (currentLength >= minLength) {
      Point checkPoint =
          floatFromLocation.changeLength(floatToLocation, okLength + currentLength).round();

      delta = checkPoint.differenceBy(fromLocation);
      if (DrillItemMover.check(via, delta, 0, 0, null, board, null)) {
        okLength += currentLength;
        result = checkPoint;
      }
      currentLength /= 2;
    }
    return result;
  }

  private static boolean repositionVia(
      RoutingBoard board,
      Via via,
      IntPoint toLocation,
      int traceHalfWidth1,
      int traceLayer1,
      int traceClClass1,
      IntPoint connectLocation,
      int traceHalfWidth2,
      int traceLayer2,
      int traceClClass2) {

    Point fromLocation = via.getCenter();

    if (fromLocation.equals(toLocation)) {
      FRLogger.trace("OptViaAlgo.reposition_via: fromLocation equal toLocation");
      return false;
    }

    Vector delta = toLocation.differenceBy(fromLocation);

    if (board.rules.getTraceAngleRestriction() == AngleRestriction.NONE
        && delta.lengthApprox() <= 1.5) {
      // PullTightAlgoAnyAngle.reduce_corners may not be able to remove the new
      // generated overlap
      // because of numerical stability problems
      // That would result in an endless loop with removing the generated acute angle
      // in
      // reposition_via.
      return false;
    }

    int[] netNoArr = via.netNoArr;

    double okLength =
        board.checkTraceSegment(
            fromLocation, toLocation, traceLayer1, netNoArr, traceHalfWidth1, traceClClass1, false);

    if (okLength < Integer.MAX_VALUE) {
      return false;
    }

    okLength =
        board.checkTraceSegment(
            toLocation,
            connectLocation,
            traceLayer2,
            netNoArr,
            traceHalfWidth2,
            traceClClass2,
            false);

    if (okLength < Integer.MAX_VALUE) {
      return false;
    }
    return DrillItemMover.check(via, delta, 0, 0, null, board, null);
  }

  /**
   * Tries to reposition the via to a better location according to the trace costs. Returns null, if
   * no better location was found.
   */
  private static Point repositionVia(
      RoutingBoard board,
      Via via,
      int firstTraceHalfWidth,
      int firstTraceClClass,
      int firstTraceLayer,
      ExpansionCostFactor firstTraceCosts,
      Point firstTraceFromCorner,
      int secondTraceHalfWidth,
      int secondTraceClClass,
      int secondTraceLayer,
      ExpansionCostFactor secondTraceCosts,
      Point secondTraceFromCorner) {
    Point viaLocation = via.getCenter();

    Vector firstDelta = firstTraceFromCorner.differenceBy(viaLocation);
    Vector secondDelta = secondTraceFromCorner.differenceBy(viaLocation);
    double scalarProduct = firstDelta.scalarProduct(secondDelta);

    FloatPoint floatViaLocation = viaLocation.toFloat();
    FloatPoint floatFirstTraceFromCorner = firstTraceFromCorner.toFloat();
    FloatPoint floatSecondTraceFromCorner = secondTraceFromCorner.toFloat();
    double firstTraceFromCornerDistance = floatViaLocation.distance(floatFirstTraceFromCorner);
    double secondTraceFromCornerDistance = floatViaLocation.distance(floatSecondTraceFromCorner);
    IntPoint roundedFirstTraceFromCorner = floatFirstTraceFromCorner.round();
    IntPoint roundedSecondTraceFromCorner = floatSecondTraceFromCorner.round();

    // handle case of overlapping lines first

    if (viaLocation.sideOf(firstTraceFromCorner, secondTraceFromCorner) == Side.COLLINEAR
        && scalarProduct > 0) {
      if (secondTraceFromCornerDistance < firstTraceFromCornerDistance) {
        return repositionVia(
            board,
            via,
            roundedSecondTraceFromCorner,
            firstTraceHalfWidth,
            firstTraceLayer,
            firstTraceClClass);
      }
      return repositionVia(
          board,
          via,
          roundedFirstTraceFromCorner,
          secondTraceHalfWidth,
          secondTraceLayer,
          secondTraceClClass);
    }
    Point result;

    double currentWeightedDistance1 =
        floatViaLocation.weightedDistance(
            floatFirstTraceFromCorner, firstTraceCosts.horizontal, firstTraceCosts.vertical);
    double currentWeightedDistance2 =
        floatViaLocation.weightedDistance(
            floatFirstTraceFromCorner, secondTraceCosts.horizontal, secondTraceCosts.vertical);

    if (currentWeightedDistance1 > currentWeightedDistance2) {
      // try to move the via in direction of firstTraceFromCorner
      result =
          repositionVia(
              board,
              via,
              roundedFirstTraceFromCorner,
              secondTraceHalfWidth,
              secondTraceLayer,
              secondTraceClClass);
      if (result != null) {
        return result;
      }
    }

    currentWeightedDistance1 =
        floatViaLocation.weightedDistance(
            floatSecondTraceFromCorner, secondTraceCosts.horizontal, secondTraceCosts.vertical);
    currentWeightedDistance2 =
        floatViaLocation.weightedDistance(
            floatSecondTraceFromCorner, firstTraceCosts.horizontal, firstTraceCosts.vertical);

    if (currentWeightedDistance1 > currentWeightedDistance2) {
      // try to move the via in direction of secondTraceFromCorner
      result =
          repositionVia(
              board,
              via,
              roundedSecondTraceFromCorner,
              firstTraceHalfWidth,
              firstTraceLayer,
              firstTraceClClass);
      if (result != null) {
        return result;
      }
    }
    if (scalarProduct > 0
        && board.rules.getTraceAngleRestriction() != AngleRestriction.NINETY_DEGREE) {
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
      currentWeightedDistance1 =
          floatToPoint1.weightedDistance(
              floatToPoint2, firstTraceCosts.horizontal, firstTraceCosts.vertical);
      currentWeightedDistance2 =
          floatToPoint1.weightedDistance(
              floatToPoint2, secondTraceCosts.horizontal, secondTraceCosts.vertical);

      if (currentWeightedDistance1 > currentWeightedDistance2) {
        // try moving the via first into the direction of toPoint1
        result =
            repositionVia(
                board, via, toPoint1, secondTraceHalfWidth, secondTraceLayer, secondTraceClClass);
        if (result == null) {
          result =
              repositionVia(
                  board, via, toPoint2, firstTraceHalfWidth, firstTraceLayer, firstTraceClClass);
        }
      } else {
        // try moving the via first into the direction of toPoint2
        result =
            repositionVia(
                board, via, toPoint2, firstTraceHalfWidth, firstTraceLayer, firstTraceClClass);
        if (result == null) {
          result =
              repositionVia(
                  board, via, toPoint1, secondTraceHalfWidth, secondTraceLayer, secondTraceClClass);
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

      currentWeightedDistance1 =
          floatViaLocation.weightedDistance(
              floatFirstTraceFromCorner, firstTraceCosts.horizontal, firstTraceCosts.vertical);
      currentWeightedDistance2 =
          floatViaLocation.weightedDistance(
              floatCheckLocation, secondTraceCosts.horizontal, secondTraceCosts.vertical);
      double currentWeightedDistance3 =
          floatCheckLocation.weightedDistance(
              floatFirstTraceFromCorner, firstTraceCosts.horizontal, firstTraceCosts.vertical);

      if (currentWeightedDistance1 > currentWeightedDistance2 + currentWeightedDistance3) {
        IntPoint checkLocation = floatCheckLocation.round();
        boolean checkOk =
            repositionVia(
                board,
                via,
                checkLocation,
                secondTraceHalfWidth,
                secondTraceLayer,
                secondTraceClClass,
                roundedFirstTraceFromCorner,
                firstTraceHalfWidth,
                firstTraceLayer,
                firstTraceClClass);
        if (checkOk) {
          return checkLocation;
        }
      }

      floatCheckLocation = new FloatPoint(floatFirstTraceFromCorner.x, floatViaLocation.y);

      currentWeightedDistance2 =
          floatViaLocation.weightedDistance(
              floatCheckLocation, secondTraceCosts.horizontal, secondTraceCosts.vertical);
      currentWeightedDistance3 =
          floatCheckLocation.weightedDistance(
              floatFirstTraceFromCorner, firstTraceCosts.horizontal, firstTraceCosts.vertical);

      if (currentWeightedDistance1 > currentWeightedDistance2 + currentWeightedDistance3) {
        IntPoint checkLocation = floatCheckLocation.round();
        boolean checkOk =
            repositionVia(
                board,
                via,
                checkLocation,
                secondTraceHalfWidth,
                secondTraceLayer,
                secondTraceClClass,
                roundedFirstTraceFromCorner,
                firstTraceHalfWidth,
                firstTraceLayer,
                firstTraceClClass);
        if (checkOk) {
          return checkLocation;
        }
      }
    }

    if (!secondDelta.isOrthogonal()) {
      FloatPoint floatCheckLocation =
          new FloatPoint(floatViaLocation.x, floatSecondTraceFromCorner.y);

      currentWeightedDistance1 =
          floatViaLocation.weightedDistance(
              floatSecondTraceFromCorner, secondTraceCosts.horizontal, secondTraceCosts.vertical);
      currentWeightedDistance2 =
          floatViaLocation.weightedDistance(
              floatCheckLocation, firstTraceCosts.horizontal, firstTraceCosts.vertical);
      double currentWeightedDistance3 =
          floatCheckLocation.weightedDistance(
              floatSecondTraceFromCorner, secondTraceCosts.horizontal, secondTraceCosts.vertical);

      if (currentWeightedDistance1 > currentWeightedDistance2 + currentWeightedDistance3) {
        IntPoint checkLocation = floatCheckLocation.round();
        boolean checkOk =
            repositionVia(
                board,
                via,
                checkLocation,
                firstTraceHalfWidth,
                firstTraceLayer,
                firstTraceClClass,
                roundedSecondTraceFromCorner,
                secondTraceHalfWidth,
                secondTraceLayer,
                secondTraceClClass);
        if (checkOk) {
          return checkLocation;
        }
      }

      floatCheckLocation = new FloatPoint(floatSecondTraceFromCorner.x, floatViaLocation.y);

      currentWeightedDistance2 =
          floatViaLocation.weightedDistance(
              floatCheckLocation, firstTraceCosts.horizontal, firstTraceCosts.vertical);
      currentWeightedDistance3 =
          floatCheckLocation.weightedDistance(
              floatSecondTraceFromCorner, secondTraceCosts.horizontal, secondTraceCosts.vertical);

      if (currentWeightedDistance1 > currentWeightedDistance2 + currentWeightedDistance3) {
        IntPoint checkLocation = floatCheckLocation.round();
        boolean checkOk =
            repositionVia(
                board,
                via,
                checkLocation,
                firstTraceHalfWidth,
                firstTraceLayer,
                firstTraceClClass,
                roundedSecondTraceFromCorner,
                secondTraceHalfWidth,
                secondTraceLayer,
                secondTraceClClass);
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
