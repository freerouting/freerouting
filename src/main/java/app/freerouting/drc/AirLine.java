package app.freerouting.drc;

import app.freerouting.board.model.items.Item;
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

  /**
   * Creates an airline between two net items at the given corner coordinates.
   *
   * @param net the net this airline belongs to
   * @param fromItem the starting item
   * @param fromCorner the starting coordinate
   * @param toItem the ending item
   * @param toCorner the ending coordinate
   */
  public AirLine(Net net, Item fromItem, FloatPoint fromCorner, Item toItem, FloatPoint toCorner) {
    this.net = net;
    this.fromItem = fromItem;
    this.fromCorner = fromCorner;
    this.toItem = toItem;
    this.toCorner = toCorner;
  }

  @Override
  public int compareTo(AirLine other) {
    return this.net.name.compareTo(other.net.name);
  }

  @Override
  public String toString() {
    return this.net.name + ": " + fromItem + " - " + toItem;
  }
}
