package app.freerouting.autoroute.pipeline;

import app.freerouting.board.DrillItem;
import app.freerouting.board.Item;
import app.freerouting.board.PolylineTrace;
import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.FloatPoint;
import java.util.Collection;
import java.util.Set;

/** Calculates visual and diagnostic airline geometry for autoroute connections. */
final class AutorouteAirlineCalculator {

  private AutorouteAirlineCalculator() {}

  static FloatLine calculateAirline(Collection<Item> fromItems, Collection<Item> toItems) {
    FloatPoint fromCorner = null;
    FloatPoint toCorner = null;
    double minDistance = Double.MAX_VALUE;
    for (Item currentFromItem : fromItems) {
      if (!(currentFromItem instanceof DrillItem)) {
        continue;
      }
      FloatPoint currentFromCorner = ((DrillItem) currentFromItem).getCenter().toFloat();

      for (Item currentToItem : toItems) {
        if (!(currentToItem instanceof DrillItem)) {
          continue;
        }
        FloatPoint currentToCorner = ((DrillItem) currentToItem).getCenter().toFloat();
        double currentDistance = currentFromCorner.distanceSquare(currentToCorner);
        if (currentDistance < minDistance) {
          minDistance = currentDistance;
          fromCorner = currentFromCorner;
          toCorner = currentToCorner;
        }
      }
    }
    return new FloatLine(fromCorner, toCorner);
  }

  static FloatPoint nearestPointOnTrace(PolylineTrace trace, FloatPoint point) {
    double minDistance = Double.MAX_VALUE;
    FloatPoint nearestPoint = null;

    FloatPoint firstCorner = trace.firstCorner().toFloat();
    FloatPoint lastCorner = trace.lastCorner().toFloat();

    double distanceToFirst = point.distance(firstCorner);
    double distanceToLast = point.distance(lastCorner);

    if (distanceToFirst < minDistance) {
      minDistance = distanceToFirst;
      nearestPoint = firstCorner;
    }

    if (distanceToLast < minDistance) {
      minDistance = distanceToLast;
      nearestPoint = lastCorner;
    }

    for (int i = 0; i < trace.cornerCount() - 1; i++) {
      FloatPoint segmentStart = trace.polyline().cornerApprox(i);
      FloatPoint segmentEnd = trace.polyline().cornerApprox(i + 1);
      FloatLine segment = new FloatLine(segmentStart, segmentEnd);

      FloatPoint projection = segment.perpendicularProjection(point);
      if (projection.isContainedInBox(segmentStart, segmentEnd, 0.01)) {
        double distance = point.distance(projection);
        if (distance < minDistance) {
          minDistance = distance;
          nearestPoint = projection;
        }
      }
    }

    return nearestPoint;
  }

  /**
   * Finds the closest points between two traces.
   *
   * @return an array with two FloatPoints: [point_on_first_trace, point_on_second_trace]
   */
  static FloatPoint[] findClosestPointsBetweenTraces(
      PolylineTrace firstTrace, PolylineTrace secondTrace) {
    double minDistance = Double.MAX_VALUE;
    FloatPoint[] result = new FloatPoint[2];

    FloatPoint firstTraceStart = firstTrace.firstCorner().toFloat();
    final FloatPoint firstTraceEnd = firstTrace.lastCorner().toFloat();
    FloatPoint secondTraceStart = secondTrace.firstCorner().toFloat();
    FloatPoint secondTraceEnd = secondTrace.lastCorner().toFloat();

    double distance = firstTraceStart.distance(secondTraceStart);
    if (distance < minDistance) {
      minDistance = distance;
      result[0] = firstTraceStart;
      result[1] = secondTraceStart;
    }

    distance = firstTraceStart.distance(secondTraceEnd);
    if (distance < minDistance) {
      minDistance = distance;
      result[0] = firstTraceStart;
      result[1] = secondTraceEnd;
    }

    distance = firstTraceEnd.distance(secondTraceStart);
    if (distance < minDistance) {
      minDistance = distance;
      result[0] = firstTraceEnd;
      result[1] = secondTraceStart;
    }

    distance = firstTraceEnd.distance(secondTraceEnd);
    if (distance < minDistance) {
      minDistance = distance;
      result[0] = firstTraceEnd;
      result[1] = secondTraceEnd;
    }

    for (int i = 0; i < firstTrace.cornerCount() - 1; i++) {
      FloatPoint firstSegmentStart = firstTrace.polyline().cornerApprox(i);
      FloatPoint firstSegmentEnd = firstTrace.polyline().cornerApprox(i + 1);
      FloatLine firstSegment = new FloatLine(firstSegmentStart, firstSegmentEnd);

      for (int j = 0; j < secondTrace.cornerCount() - 1; j++) {
        FloatPoint secondSegmentStart = secondTrace.polyline().cornerApprox(j);
        FloatPoint secondSegmentEnd = secondTrace.polyline().cornerApprox(j + 1);
        FloatLine secondSegment = new FloatLine(secondSegmentStart, secondSegmentEnd);

        FloatPoint pointOnFirst = firstSegment.nearestSegmentPoint(secondSegmentStart);
        FloatPoint pointOnSecond = secondSegment.perpendicularProjection(pointOnFirst);

        if (!pointOnSecond.isContainedInBox(secondSegmentStart, secondSegmentEnd, 0.01)) {
          double distToStart = pointOnFirst.distance(secondSegmentStart);
          double distToEnd = pointOnFirst.distance(secondSegmentEnd);
          pointOnSecond = distToStart < distToEnd ? secondSegmentStart : secondSegmentEnd;
        }

        pointOnFirst = firstSegment.nearestSegmentPoint(pointOnSecond);

        distance = pointOnFirst.distance(pointOnSecond);
        if (distance < minDistance) {
          minDistance = distance;
          result[0] = pointOnFirst;
          result[1] = pointOnSecond;
        }
      }
    }

    return result;
  }

  /**
   * Calculates the shortest reference-point distance for an item and its incomplete connections.
   *
   * @param item the item to calculate distance for
   * @return the shortest distance, or {@link Double#MAX_VALUE} if no connections exist
   */
  static double calculateItemDistance(Item item) {
    if (item.netCount() == 0) {
      return Double.MAX_VALUE;
    }

    int netNumber = item.getNetNumber(0);
    Set<Item> unconnectedSet = item.getUnconnectedSet(netNumber);
    Set<Item> connectedSet = item.getConnectedSet(netNumber);

    if (unconnectedSet.isEmpty()) {
      return 0;
    }

    return calculateMinDistance(
        connectedSet.isEmpty() ? Set.of(item) : connectedSet, unconnectedSet);
  }

  private static double calculateMinDistance(
      Collection<Item> fromItems, Collection<Item> toItems) {
    double minDistance = Double.MAX_VALUE;

    for (Item fromItem : fromItems) {
      FloatPoint fromPoint = getItemReferencePoint(fromItem);
      if (fromPoint == null) {
        continue;
      }

      for (Item toItem : toItems) {
        FloatPoint toPoint = getItemReferencePoint(toItem);
        if (toPoint == null) {
          continue;
        }

        double distance = fromPoint.distance(toPoint);
        if (distance < minDistance) {
          minDistance = distance;
        }
      }
    }

    return minDistance;
  }

  private static FloatPoint getItemReferencePoint(Item item) {
    if (item instanceof DrillItem drillItem) {
      return drillItem.getCenter().toFloat();
    } else if (item instanceof PolylineTrace trace) {
      FloatPoint first = trace.firstCorner().toFloat();
      FloatPoint last = trace.lastCorner().toFloat();
      return new FloatPoint((first.x + last.x) / 2, (first.y + last.y) / 2);
    }
    return null;
  }
}
