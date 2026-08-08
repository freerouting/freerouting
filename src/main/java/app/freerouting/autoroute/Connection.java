package app.freerouting.autoroute;

import app.freerouting.board.Item;
import app.freerouting.board.Trace;
import app.freerouting.geometry.planar.Point;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/** Describes a routing connection ending at the next fork or terminal item. */
public final class Connection {

  private static final double DETOUR_ADD = 100;
  private static final double DETOUR_ITEM_COST = 0.1;

  /** If the connection ens in empty space, startPoint or endPoint may be null. */
  public final Point startPoint;

  public final int startLayer;
  public final Point endPoint;
  public final int endLayer;
  public final Set<Item> itemList;

  /** Creates a new instance of Connection */
  private Connection(
      Point pStartPoint, int pStartLayer, Point pEndPoint, int pEndLayer, Set<Item> pItemList) {
    startPoint = pStartPoint;
    startLayer = pStartLayer;
    endPoint = pEndPoint;
    endLayer = pEndLayer;
    itemList = pItemList;
  }

  /**
   * Gets the connection this item belongs to. A connection ends at the next fork or terminal item.
   * Returns null, if p_item is not a route item, or if it is a via belonging to more than 1
   * connection.
   */
  public static Connection get(Item pItem) {
    if (!pItem.isRoutable()) {
      return null;
    }
    Connection precalculatedConnection = pItem.getAutorouteInfo().getPrecalculatedConnection();
    if (precalculatedConnection != null) {
      return precalculatedConnection;
    }
    Set<Item> contacts = pItem.getNormalContacts();
    Set<Item> connectionItems = new TreeSet<>();
    connectionItems.add(pItem);

    Point startPoint = null;
    int startLayer = 0;
    Point endPoint = null;
    int endLayer = 0;

    for (Item currItem : contacts) {
      Point prevContactPoint = pItem.normalContactPoint(currItem);
      if (prevContactPoint == null) {
        // no unique contact point
        continue;
      }
      int prevContactLayer = pItem.firstCommonLayer(currItem);
      boolean forkFound = false;
      if (pItem instanceof Trace start_trace) {
        // Check, that there is only 1 contact at this location.
        // Only for pins and vias items of more than 1 connection
        // are collected
        Collection<Item> checkContacts = start_trace.getNormalContacts(prevContactPoint, false);
        if (checkContacts.size() != 1) {
          forkFound = true;
        }
      }
      // Search from currItem along the contacts
      // until the next fork or nonroute item.
      for (; ; ) {
        if (!currItem.isRoutable() || forkFound) {
          // connection ends
          if (startPoint == null) {
            startPoint = prevContactPoint;
            startLayer = prevContactLayer;
          } else if (!prevContactPoint.equals(startPoint)) {
            endPoint = prevContactPoint;
            endLayer = prevContactLayer;
          }
          break;
        }
        connectionItems.add(currItem);
        Collection<Item> currItemContacts = currItem.getNormalContacts();
        // filter the contacts at the previous contact point,
        // because we were already there.
        // If then there is not exactly 1 new contact left, there is
        // a stub or a fork.
        Point nextContactPoint = null;
        int nextContactLayer = -1;
        Item nextContact = null;
        for (Item tmpContact : currItemContacts) {
          int tmpContactLayer = currItem.firstCommonLayer(tmpContact);
          if (tmpContactLayer >= 0) {
            Point tmpContactPoint = currItem.normalContactPoint(tmpContact);
            if (tmpContactPoint == null) {
              // no unique contact point
              forkFound = true;
              break;
            }
            if (prevContactLayer != tmpContactLayer || !prevContactPoint.equals(tmpContactPoint)) {
              nextContactPoint = tmpContactPoint;
              nextContactLayer = tmpContactLayer;
              if (nextContact != null) {
                // second new contact found
                forkFound = true;
                break;
              }
              nextContact = tmpContact;
            }
          }
        }
        if (nextContact == null) {
          break;
        }
        currItem = nextContact;
        prevContactPoint = nextContactPoint;
        prevContactLayer = nextContactLayer;
      }
    }
    Connection result = new Connection(startPoint, startLayer, endPoint, endLayer, connectionItems);
    for (Item currItem : connectionItems) {
      currItem.getAutorouteInfo().setPrecalculatedConnection(result);
    }
    return result;
  }

  /** Returns the cumulative length of the traces in this connection. */
  public double traceLength() {
    double result = 0;
    for (Item currItem : itemList) {
      if (currItem instanceof Trace trace) {
        result += trace.getLength();
      }
    }
    return result;
  }

  /**
   * Returns an estimation of the actual length of the connection divided by the minimal possible
   * length.
   */
  public double getDetour() {
    if (startPoint == null || endPoint == null) {
      return Integer.MAX_VALUE;
    }
    double minTraceLength = startPoint.toFloat().distance(endPoint.toFloat());
    return (this.traceLength() + DETOUR_ADD) / (minTraceLength + DETOUR_ADD)
        + DETOUR_ITEM_COST * (itemList.size() - 1);
  }
}
