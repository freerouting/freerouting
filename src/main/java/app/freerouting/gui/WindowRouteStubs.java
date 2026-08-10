package app.freerouting.gui;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.Item;
import app.freerouting.board.Trace;
import app.freerouting.board.Via;
import app.freerouting.datastructures.Signum;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.interactive.GuiBoardManager;
import app.freerouting.rules.Net;
import app.freerouting.util.TextManager;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/** Displays route stubs that can be removed from the board. */
public class WindowRouteStubs extends CleanupWindows {

  /** Creates a new instance of WindowRouteStubs. */
  public WindowRouteStubs(BoardFrame boardFrame) {
    super(boardFrame);
    setLanguage(boardFrame.get_locale());

    this.tm = new TextManager(CleanupWindows.class, boardFrame.get_locale());

    this.setTitle(tm.getText("route_stubs"));
    this.listEmptyMessage.setText(tm.getText("no_route_stubs_found"));
  }

  @Override
  protected void fillList() {
    BasicBoard routingBoard = this.boardFrame.boardPanel.boardHandling.getRoutingBoard();

    SortedSet<RouteStubInfo> routeStubInfoSet = new TreeSet<>();

    Collection<Item> boardItems = routingBoard.getItems();
    for (Item currItem : boardItems) {
      if (!(currItem instanceof Trace || currItem instanceof Via)) {
        continue;
      }
      if (currItem.netCount() != 1) {
        continue;
      }

      FloatPoint stubLocation;
      int stubLayer;
      if (currItem instanceof Via via) {
        Collection<Item> contactList = currItem.getAllContacts();
        if (contactList.isEmpty()) {
          stubLayer = currItem.firstLayer();
        } else {
          Iterator<Item> it = contactList.iterator();
          Item currContactItem = it.next();
          int firstContactFirstLayer = currContactItem.firstLayer();
          int firstContactLastLayer = currContactItem.lastLayer();
          boolean allContactsOnOneLayer = true;
          while (it.hasNext()) {
            currContactItem = it.next();
            if (currContactItem.firstLayer() != firstContactFirstLayer
                || currContactItem.lastLayer() != firstContactLastLayer) {
              allContactsOnOneLayer = false;
              break;
            }
          }
          if (!allContactsOnOneLayer) {
            continue;
          }
          if (currItem.firstLayer() >= firstContactFirstLayer
              && currItem.lastLayer() <= firstContactFirstLayer) {
            stubLayer = firstContactFirstLayer;
          } else {
            stubLayer = firstContactLastLayer;
          }
        }
        stubLocation = via.getCenter().toFloat();
      } else {
        Trace currTrace = (Trace) currItem;
        if (currTrace.getStartContacts().isEmpty()) {
          stubLocation = currTrace.firstCorner().toFloat();
        } else if (currTrace.getEndContacts().isEmpty()) {
          stubLocation = currTrace.lastCorner().toFloat();
        } else {
          continue;
        }
        stubLayer = currTrace.getLayer();
      }
      RouteStubInfo currRouteStubInfo = new RouteStubInfo(currItem, stubLocation, stubLayer);
      routeStubInfoSet.add(currRouteStubInfo);
    }

    for (RouteStubInfo currInfo : routeStubInfoSet) {
      this.addToList(currInfo);
    }
    this.list.setVisibleRowCount(Math.min(routeStubInfoSet.size(), DEFAULT_TABLE_SIZE));
  }

  @Override
  protected void selectInstances() {
    List<Object> selectedListValues = list.getSelectedValuesList();
    if (selectedListValues.isEmpty()) {
      return;
    }
    Set<Item> selectedItems = new TreeSet<>();
    for (int i = 0; i < selectedListValues.size(); i++) {
      selectedItems.add(((RouteStubInfo) selectedListValues.get(i)).stubItem);
    }
    GuiBoardManager boardHandling = boardFrame.boardPanel.boardHandling;
    boardHandling.selectItems(selectedItems);
    boardHandling.zoomSelection();
  }

  /** Describes information of a route stub in the list. */
  private class RouteStubInfo implements Comparable<RouteStubInfo> {

    private final Item stubItem;
    private final Net net;
    private final FloatPoint location;
    private final int layerNo;

    public RouteStubInfo(Item stub, FloatPoint location, int layerNo) {
      GuiBoardManager boardHandling = boardFrame.boardPanel.boardHandling;
      this.stubItem = stub;
      this.location = boardHandling.coordinateTransform.boardToUser(location);
      this.layerNo = layerNo;
      int netNo = stub.getNetNo(0);
      this.net = boardHandling.getRoutingBoard().rules.nets.get(netNo);
    }

    @Override
    public String toString() {
      String itemString;
      if (this.stubItem instanceof Trace) {
        itemString = tm.getText("trace");
      } else {
        itemString = tm.getText("via");
      }
      String layerName =
          boardFrame.boardPanel.boardHandling.getRoutingBoard().layerStructure.arr[layerNo].name;
      return tm.getText(
          "route_stub_row_message",
          itemString,
          this.net.name,
          this.location.toString(boardFrame.get_locale()),
          layerName);
    }

    @Override
    public int compareTo(RouteStubInfo other) {
      int result = this.net.name.compareTo(other.net.name);
      if (result == 0) {
        result = Signum.asInt(this.location.x - other.location.x);
      }
      if (result == 0) {
        result = Signum.asInt(this.location.y - other.location.y);
      }
      if (result == 0) {
        result = this.layerNo - other.layerNo;
      }
      return result;
    }
  }
}
