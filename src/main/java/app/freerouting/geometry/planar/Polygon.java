package app.freerouting.geometry.planar;

import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * A Polygon is a list of points in the plane, where no 2 consecutive points may be equal and no 3
 * consecutive points collinear.
 */
public class Polygon implements Serializable {

  private final Collection<Point> corners;

  /**
   * Creates a polygon from p_point_arr. Multiple points and points, which are collinear with its
   * previous and next point will be removed.
   */
  public Polygon(Point[] pointArr) {
    corners = new LinkedList<>();
    if (pointArr.length == 0) {
      return;
    }
    corners.addAll(Arrays.asList(pointArr));

    boolean cornerRemoved = true;
    while (cornerRemoved) {
      cornerRemoved = false;
      // remove multiple points

      if (corners.isEmpty()) {
        return;
      }
      Iterator<Point> i = corners.iterator();
      Point currOb = i.next();
      while (i.hasNext()) {
        Point nextOb = i.next();
        if (nextOb.equals(currOb)) {
          i.remove();
          cornerRemoved = true;
        } else {
          currOb = nextOb;
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
      Point curr = i.next();
      prevI.next();
      while (i.hasNext()) {
        Point next = i.next();
        prevI.next();

        if (curr.sideOf(prev, next) == Side.COLLINEAR) {
          prevI.remove();
          cornerRemoved = true;
          break;
        }
        prev = curr;
        curr = next;
      }
    }
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
    Point[] cornerArr = cornerArray();
    Point[] reverseCornerArr = new Point[cornerArr.length];
    for (int i = 0; i < cornerArr.length; i++) {
      reverseCornerArr[i] = cornerArr[cornerArr.length - i - 1];
    }
    return new Polygon(reverseCornerArr);
  }

  /**
   * Returns the winding number of this polygon, treated as closed. It will be {@literal >} 0, if
   * the corners are in counterclock sense, and {@literal <} 0, if the corners are in clockwise
   * sense.
   */
  public int windingNumberAfterClosing() {
    Point[] cornerArr = cornerArray();
    if (cornerArr.length < 2) {
      return 0;
    }
    Vector firstSideVector = cornerArr[1].differenceBy(cornerArr[0]);
    Vector prevSideVector = firstSideVector;
    int cornerCount = cornerArr.length;
    // Skip the last corner, if it is equal to the first corner.
    if (cornerArr[0].equals(cornerArr[cornerCount - 1])) {
      --cornerCount;
    }
    double angleSum = 0;
    for (int i = 1; i < cornerCount - 1; i++) {
      Vector nextSideVector = cornerArr[i + 1].differenceBy(cornerArr[i]);
      angleSum += prevSideVector.angleApprox(nextSideVector);
      prevSideVector = nextSideVector;
    }
    if (cornerCount > 1) {
      Vector nextSideVector = cornerArr[0].differenceBy(cornerArr[cornerCount - 1]);
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
