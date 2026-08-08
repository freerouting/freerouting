package app.freerouting.board;

import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.TileShape;

/** Used internally for marking changed areas on the board after shoving and optimizing items. */
class ChangedArea {

  final int layerCount;
  MutableOctagon[] arr;

  public ChangedArea(int p_layer_count) {
    layerCount = p_layer_count;
    arr = new MutableOctagon[layerCount];
    // initialise all octagons to empty
    for (int i = 0; i < layerCount; i++) {
      arr[i] = new MutableOctagon();
      arr[i].setEmpty();
    }
  }

  /** enlarges the octagon on p_layer, so that it contains p_point */
  public void join(FloatPoint p_point, int p_layer) {
    MutableOctagon curr = arr[p_layer];
    curr.lx = Math.min(p_point.x, curr.lx);
    curr.ly = Math.min(p_point.y, curr.ly);
    curr.rx = Math.max(curr.rx, p_point.x);
    curr.uy = Math.max(curr.uy, p_point.y);

    double tmp = p_point.x - p_point.y;
    curr.ulx = Math.min(curr.ulx, tmp);
    curr.lrx = Math.max(curr.lrx, tmp);

    tmp = p_point.x + p_point.y;
    curr.llx = Math.min(curr.llx, tmp);
    curr.urx = Math.max(curr.urx, tmp);
  }

  /** enlarges the octagon on p_layer, so that it contains p_shape */
  public void join(TileShape p_shape, int p_layer) {
    if (p_shape == null) {
      return;
    }
    int cornerCount = p_shape.borderLineCount();
    for (int i = 0; i < cornerCount; i++) {
      join(p_shape.cornerApprox(i), p_layer);
    }
  }

  /** get the marking octagon on layer p_layer */
  public IntOctagon getArea(int p_layer) {

    return arr[p_layer].toInt();
  }

  public IntBox surroundingBox() {
    int llx = Integer.MAX_VALUE;
    int lly = Integer.MAX_VALUE;
    int urx = Integer.MIN_VALUE;
    int ury = Integer.MIN_VALUE;
    for (int i = 0; i < layerCount; i++) {
      MutableOctagon curr = arr[i];
      llx = Math.min(llx, (int) Math.floor(curr.lx));
      lly = Math.min(lly, (int) Math.floor(curr.ly));
      urx = Math.max(urx, (int) Math.ceil(curr.rx));
      ury = Math.max(ury, (int) Math.ceil(curr.uy));
    }
    if (llx > urx || lly > ury) {
      return IntBox.EMPTY;
    }
    return new IntBox(llx, lly, urx, ury);
  }

  /** initializes the marking octagon on p_layer to empty */
  void setEmpty(int p_layer) {
    arr[p_layer].setEmpty();
  }

  /** mutable octagon with double coordinates (see geometry.planar.IntOctagon) */
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

    /** calculates the smallest IntOctagon containing this octagon. */
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
