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

public class WindowRouteStubs extends CleanupWindows {

  /** Creates a new instance of WindowRouteStubs */
  public WindowRouteStubs(BoardFrame p_board_frame) {
    super(p_board_frame);
    setLanguage(p_board_frame.get_locale());

    this.tm = new TextManager(CleanupWindows.class, p_board_frame.get_locale());

    this.setTitle(tm.getText("route_stubs"));
    this.listEmptyMessage.setText(tm.getText("no_route_stubs_found"));
  }

  @Override
  protected void fill_list() {
    BasicBoard routingBoard = this.boardFrame.boardPanel.boardHandling.get_routing_board();

    SortedSet<RouteStubInfo> routeStubInfoSet = new TreeSet<>();

    Collection<Item> boardItems = routingBoard.get_items();
    for (Item currItem : boardItems) {
      if (!(currItem instanceof Trace || currItem instanceof Via)) {
        continue;
      }
      if (currItem.net_count() != 1) {
        continue;
      }

      FloatPoint stubLocation;
      int stubLayer;
      if (currItem instanceof Via via) {
        Collection<Item> contactList = currItem.get_all_contacts();
        if (contactList.isEmpty()) {
          stubLayer = currItem.first_layer();
        } else {
          Iterator<Item> it = contactList.iterator();
          Item currContactItem = it.next();
          int firstContactFirstLayer = currContactItem.first_layer();
          int firstContactLastLayer = currContactItem.last_layer();
          boolean allContactsOnOneLayer = true;
          while (it.hasNext()) {
            currContactItem = it.next();
            if (currContactItem.first_layer() != firstContactFirstLayer
                || currContactItem.last_layer() != firstContactLastLayer) {
              allContactsOnOneLayer = false;
              break;
            }
          }
          if (!allContactsOnOneLayer) {
            continue;
          }
          if (currItem.first_layer() >= firstContactFirstLayer
              && currItem.last_layer() <= firstContactFirstLayer) {
            stubLayer = firstContactFirstLayer;
          } else {
            stubLayer = firstContactLastLayer;
          }
        }
        stubLocation = via.get_center().to_float();
      } else {
        Trace currTrace = (Trace) currItem;
        if (currTrace.get_start_contacts().isEmpty()) {
          stubLocation = currTrace.first_corner().to_float();
        } else if (currTrace.get_end_contacts().isEmpty()) {
          stubLocation = currTrace.last_corner().to_float();
        } else {
          continue;
        }
        stubLayer = currTrace.get_layer();
      }
      RouteStubInfo currRouteStubInfo = new RouteStubInfo(currItem, stubLocation, stubLayer);
      routeStubInfoSet.add(currRouteStubInfo);
    }

    for (RouteStubInfo currInfo : routeStubInfoSet) {
      this.add_to_list(currInfo);
    }
    this.list.setVisibleRowCount(Math.min(routeStubInfoSet.size(), DEFAULT_TABLE_SIZE));
  }

  @Override
  protected void select_instances() {
    List<Object> selectedListValues = list.getSelectedValuesList();
    if (selectedListValues.isEmpty()) {
      return;
    }
    Set<Item> selectedItems = new TreeSet<>();
    for (int i = 0; i < selectedListValues.size(); i++) {
      selectedItems.add(((RouteStubInfo) selectedListValues.get(i)).stubItem);
    }
    GuiBoardManager boardHandling = boardFrame.boardPanel.boardHandling;
    boardHandling.select_items(selectedItems);
    boardHandling.zoom_selection();
  }

  /** Describes information of a route stub in the list. */
  private class RouteStubInfo implements Comparable<RouteStubInfo> {

    private final Item stubItem;
    private final Net net;
    private final FloatPoint location;
    private final int layerNo;

    public RouteStubInfo(Item p_stub, FloatPoint p_location, int p_layer_no) {
      GuiBoardManager boardHandling = boardFrame.boardPanel.boardHandling;
      this.stubItem = p_stub;
      this.location = boardHandling.coordinateTransform.board_to_user(p_location);
      this.layerNo = p_layer_no;
      int netNo = p_stub.get_net_no(0);
      this.net = boardHandling.get_routing_board().rules.nets.get(netNo);
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
          boardFrame.boardPanel.boardHandling.get_routing_board().layerStructure.arr[layerNo].name;
      return tm.getText(
          "route_stub_row_message",
          itemString,
          this.net.name,
          this.location.to_string(boardFrame.get_locale()),
          layerName);
    }

    @Override
    public int compareTo(RouteStubInfo p_other) {
      int result = this.net.name.compareTo(p_other.net.name);
      if (result == 0) {
        result = Signum.as_int(this.location.x - p_other.location.x);
      }
      if (result == 0) {
        result = Signum.as_int(this.location.y - p_other.location.y);
      }
      if (result == 0) {
        result = this.layerNo - p_other.layerNo;
      }
      return result;
    }
  }
}
