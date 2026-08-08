package app.freerouting.drc;

import app.freerouting.board.Item;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.rules.Net;

/**
 * Represents an incomplete connection (airline) between two items on the board. Each airline is
 * associated with a net and connects two specific items (fromItem and toItem) at specific locations
 * (fromCorner and toCorner).
 */
public class AirLine implements Comparable<AirLine> {

  /** The net this airline belongs to. */
  public final Net net;

  /** The item where the airline starts. */
  public final Item fromItem;

  /** The exact starting coordinate of the airline. */
  public final FloatPoint fromCorner;

  /** The item where the airline ends. */
  public final Item toItem;

  /** The exact ending coordinate of the airline. */
  public final FloatPoint toCorner;

  public AirLine(
      Net pNet, Item pFromItem, FloatPoint pFromCorner, Item pToItem, FloatPoint pToCorner) {
    net = pNet;
    fromItem = pFromItem;
    fromCorner = pFromCorner;
    toItem = pToItem;
    toCorner = pToCorner;
  }

  @Override
  public int compareTo(AirLine pOther) {
    return this.net.name.compareTo(pOther.net.name);
  }

  @Override
  public String toString() {
    return this.net.name + ": " + fromItem + " - " + toItem;
  }
}
