package app.freerouting.drc;

import app.freerouting.board.Item;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.rules.Net;

/**
 * Represents an incomplete connection (airline) between two items on the board.
 * Each airline is associated with a net and connects two specific items
 * (from_item and to_item) at specific locations (from_corner and to_corner).
 */
public class AirLine implements Comparable<AirLine> {

    /**
     * The net this airline belongs to.
     */
    public final Net net;
    /**
     * The item where the airline starts.
     */
    public final Item from_item;
    /**
     * The exact starting coordinate of the airline.
     */
    public final FloatPoint from_corner;
    /**
     * The item where the airline ends.
     */
    public final Item to_item;
    /**
     * The exact ending coordinate of the airline.
     */
    public final FloatPoint to_corner;

    public AirLine(Net p_net, Item p_from_item, FloatPoint p_from_corner, Item p_to_item, FloatPoint p_to_corner) {
        net = p_net;
        from_item = p_from_item;
        from_corner = p_from_corner;
        to_item = p_to_item;
        to_corner = p_to_corner;
    }

    @Override
    public int compareTo(AirLine p_other) {
        return this.net.name.compareTo(p_other.net.name);
    }

    @Override
    public String toString() {
        return this.net.name + ": " + from_item + " - " + to_item;
    }
}