package app.freerouting.board;

import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.TileShape;

/** Used internally for marking changed areas on the board after shoving and optimizing items. */
public class ChangedArea {

  final int layerCount;
  MutableOctagon[] arr;

  public ChangedArea(int layerCount) {
    this.layerCount = layerCount;
    arr = new MutableOctagon[layerCount];
    // initialise all octagons to empty
    for (int i = 0; i < layerCount; i++) {
      arr[i] = new MutableOctagon();
      arr[i].setEmpty();
    }
  }

  /** Enlarges the octagon on layer, so that it contains point. */
  public void join(FloatPoint point, int layer) {
    MutableOctagon current = arr[layer];
    current.lx = Math.min(point.x, current.lx);
    current.ly = Math.min(point.y, current.ly);
    current.rx = Math.max(current.rx, point.x);
    current.uy = Math.max(current.uy, point.y);

    double tmp = point.x - point.y;
    current.ulx = Math.min(current.ulx, tmp);
    current.lrx = Math.max(current.lrx, tmp);

    tmp = point.x + point.y;
    current.llx = Math.min(current.llx, tmp);
    current.urx = Math.max(current.urx, tmp);
  }

  /** Enlarges the octagon on layer, so that it contains shape. */
  public void join(TileShape shape, int layer) {
    if (shape == null) {
      return;
    }
    int cornerCount = shape.borderLineCount();
    for (int i = 0; i < cornerCount; i++) {
      join(shape.cornerApprox(i), layer);
    }
  }

  /** Get the marking octagon on layer layer. */
  public IntOctagon getArea(int layer) {

    return arr[layer].toInt();
  }

  public IntBox surroundingBox() {
    int llx = Integer.MAX_VALUE;
    int lly = Integer.MAX_VALUE;
    int urx = Integer.MIN_VALUE;
    int ury = Integer.MIN_VALUE;
    for (int i = 0; i < layerCount; i++) {
      MutableOctagon current = arr[i];
      llx = Math.min(llx, (int) Math.floor(current.lx));
      lly = Math.min(lly, (int) Math.floor(current.ly));
      urx = Math.max(urx, (int) Math.ceil(current.rx));
      ury = Math.max(ury, (int) Math.ceil(current.uy));
    }
    if (llx > urx || lly > ury) {
      return IntBox.EMPTY;
    }
    return new IntBox(llx, lly, urx, ury);
  }

  /** Initializes the marking octagon on layer to empty. */
  public void setEmpty(int layer) {
    arr[layer].setEmpty();
  }

  /** Mutable octagon with double coordinates (see geometry.planar.IntOctagon). */
  private static class MutableOctagon {

    double lx;
    double ly;
    double rx;
    double uy;
    double ulx;
    double lrx;
    double llx;
    double urx;

    void setEmpty() {
      lx = Integer.MAX_VALUE;
      ly = Integer.MAX_VALUE;
      rx = Integer.MIN_VALUE;
      uy = Integer.MIN_VALUE;
      ulx = Integer.MAX_VALUE;
      lrx = Integer.MIN_VALUE;
      llx = Integer.MAX_VALUE;
      urx = Integer.MIN_VALUE;
    }

    /** Calculates the smallest IntOctagon containing this octagon. */
    IntOctagon toInt() {
      if (rx < lx || uy < ly || lrx < ulx || urx < llx) {
        return IntOctagon.EMPTY;
      }
      return new IntOctagon(
          (int) Math.floor(lx),
          (int) Math.floor(ly),
          (int) Math.ceil(rx),
          (int) Math.ceil(uy),
          (int) Math.floor(ulx),
          (int) Math.ceil(lrx),
          (int) Math.floor(llx),
          (int) Math.ceil(urx));
    }
  }
}
