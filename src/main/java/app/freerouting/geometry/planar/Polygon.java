package app.freerouting.geometry.planar;

import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * A Polygon is a list of points in the plane, where no 2 consecutive points may be equal and no 3
 * consecutive points collinear.
 */
public class Polygon implements Serializable {

  private final Collection<Point> corners;

  /**
   * Creates a polygon from points. Multiple points and points, which are collinear with its
   * previous and next point will be removed.
   */
  public Polygon(Point[] points) {
    corners = new LinkedList<>();
    if (points.length == 0) {
      return;
    }
    corners.addAll(Arrays.asList(points));

    boolean cornerRemoved = true;
    while (cornerRemoved) {
      cornerRemoved = false;
      // remove multiple points

      if (corners.isEmpty()) {
        return;
      }
      Iterator<Point> i = corners.iterator();
      Point currentObject = i.next();
      while (i.hasNext()) {
        Point nextOb = i.next();
        if (nextOb.equals(currentObject)) {
          i.remove();
          cornerRemoved = true;
        } else {
          currentObject = nextOb;
        }
      }

      // remove points which are collinear with  the previous
      // and next point.
      i = corners.iterator();
      Point prev = i.next();
      Iterator<Point> prevI = corners.iterator();
      if (!i.hasNext()) {
        continue;
      }
      Point current = i.next();
      prevI.next();
      while (i.hasNext()) {
        Point next = i.next();
        prevI.next();

        if (current.sideOf(prev, next) == Side.COLLINEAR) {
          prevI.remove();
          cornerRemoved = true;
          break;
        }
        prev = current;
        current = next;
      }
    }
  }

  /**
   * Returns corners, the corners of a closed border, without the corners at which the border turns
   * back along itself, lying within one unit of the line through their neighbours: needles of no
   * width, such as a fill rounded to the resolution of the board leaves where its border steps back
   * by a unit. The border turns there by about 180 degrees, so that the sense of the polygon cannot
   * be told and its split into convex pieces fails. A last corner equal to the first is kept.
   */
  public static Point[] withoutSpikes(Point[] corners) {
    List<Point> result = new ArrayList<>(Arrays.asList(corners));
    boolean closed = result.size() > 1 && result.getFirst().equals(result.getLast());
    if (closed) {
      result.removeLast();
    }
    boolean removed = true;
    while (removed && result.size() > 3) {
      removed = false;
      int count = result.size();
      for (int i = 0; i < count; i++) {
        if (isSpike(
            result.get((i + count - 1) % count), result.get(i), result.get((i + 1) % count))) {
          result.remove(i);
          removed = true;
          break;
        }
      }
    }
    if (result.size() == corners.length - (closed ? 1 : 0)) {
      return corners;
    }
    if (closed) {
      result.add(result.getFirst());
    }
    return result.toArray(new Point[0]);
  }

  private static boolean isSpike(Point prevCorner, Point corner, Point nextCorner) {
    FloatPoint prev = prevCorner.toFloat();
    FloatPoint current = corner.toFloat();
    FloatPoint next = nextCorner.toFloat();
    double inX = current.x - prev.x;
    double inY = current.y - prev.y;
    double outX = next.x - current.x;
    double outY = next.y - current.y;
    if (inX * outX + inY * outY >= 0) {
      return false;
    }
    double chordX = next.x - prev.x;
    double chordY = next.y - prev.y;
    double cross = inX * chordY - inY * chordX;
    return cross * cross <= chordX * chordX + chordY * chordY;
  }

  /** Returns the array of corners of this polygon. */
  public Point[] cornerArray() {
    int cornerCount = corners.size();
    Point[] result = new Point[cornerCount];
    Iterator<Point> it = corners.iterator();
    for (int i = 0; i < cornerCount; i++) {
      result[i] = it.next();
    }
    return result;
  }

  /** Reverts the order of the corners of this polygon. */
  public Polygon revertCorners() {
    Point[] corners = cornerArray();
    Point[] reverseCornerArr = new Point[corners.length];
    for (int i = 0; i < corners.length; i++) {
      reverseCornerArr[i] = corners[corners.length - i - 1];
    }
    return new Polygon(reverseCornerArr);
  }

  /**
   * Returns the winding number of this polygon, treated as closed. It will be {@literal >} 0, if
   * the corners are in counterclock sense, and {@literal <} 0, if the corners are in clockwise
   * sense.
   */
  public int windingNumberAfterClosing() {
    Point[] corners = cornerArray();
    if (corners.length < 2) {
      return 0;
    }
    Vector firstSideVector = corners[1].differenceBy(corners[0]);
    Vector prevSideVector = firstSideVector;
    int cornerCount = corners.length;
    // Skip the last corner, if it is equal to the first corner.
    if (corners[0].equals(corners[cornerCount - 1])) {
      --cornerCount;
    }
    double angleSum = 0;
    for (int i = 1; i < cornerCount - 1; i++) {
      Vector nextSideVector = corners[i + 1].differenceBy(corners[i]);
      angleSum += prevSideVector.angleApprox(nextSideVector);
      prevSideVector = nextSideVector;
    }
    if (cornerCount > 1) {
      Vector nextSideVector = corners[0].differenceBy(corners[cornerCount - 1]);
      angleSum += prevSideVector.angleApprox(nextSideVector);
      prevSideVector = nextSideVector;
    }
    angleSum += prevSideVector.angleApprox(firstSideVector);
    angleSum /= 2.0 * Math.PI;
    if (Math.abs(angleSum) < 0.5) {
      FRLogger.warn("Polygon.winding_number_after_closing: winding number != 0 expected");
    }
    return (int) Math.round(angleSum);
  }
}
