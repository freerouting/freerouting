package app.freerouting.gui;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.Item;
import app.freerouting.board.Trace;
import app.freerouting.board.Via;
import app.freerouting.interactive.GuiBoardManager;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import app.freerouting.util.TextManager;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class WindowUnconnectedRoute extends CleanupWindows {

  private int maxUnconnectedRouteInfoIdNo;

  /** Creates a new instance of WindowUnconnectedRoute */
  public WindowUnconnectedRoute(BoardFrame p_board_frame) {
    super(p_board_frame);
    setLanguage(p_board_frame.get_locale());

    this.tm = new TextManager(CleanupWindows.class, p_board_frame.get_locale());

    this.setTitle(tm.getText("unconnected_route"));
    this.listEmptyMessage.setText(tm.getText("no_unconnected_route_found"));
  }

  @Override
  protected void fillList() {
    BasicBoard routingBoard = this.boardFrame.boardPanel.boardHandling.getRoutingBoard();

    Set<Item> handledItems = new TreeSet<>();

    SortedSet<UnconnectedRouteInfo> unconnectedRouteInfoSet = new TreeSet<>();

    Collection<Item> boardItems = routingBoard.getItems();
    for (Item currItem : boardItems) {
      if (!(currItem instanceof Trace || currItem instanceof Via)) {
        continue;
      }
      if (handledItems.contains(currItem)) {
        continue;
      }
      Collection<Item> currConnectedSet = currItem.getConnectedSet(-1);
      boolean terminalItemFound = false;
      for (Item curr_connnected_item : currConnectedSet) {
        handledItems.add(curr_connnected_item);
        if (!(curr_connnected_item instanceof Trace || curr_connnected_item instanceof Via)) {
          terminalItemFound = true;
        }
      }
      if (!terminalItemFound) {
        // We have found unconnected route
        if (currItem.netCount() == 1) {
          Net currNet = routingBoard.rules.nets.get(currItem.getNetNo(0));
          if (currNet != null) {
            UnconnectedRouteInfo currUnconnectedRouteInfo =
                new UnconnectedRouteInfo(currNet, currConnectedSet);
            unconnectedRouteInfoSet.add(currUnconnectedRouteInfo);
          }
        } else {
          FRLogger.warn("WindowUnconnectedRoute.fill_list: netCount 1 expected");
        }
      }
    }

    for (UnconnectedRouteInfo currInfo : unconnectedRouteInfoSet) {
      this.addToList(currInfo);
    }
    this.list.setVisibleRowCount(Math.min(unconnectedRouteInfoSet.size(), DEFAULT_TABLE_SIZE));
  }

  @Override
  protected void selectInstances() {
    List<Object> selectedListValues = list.getSelectedValuesList();
    if (selectedListValues.isEmpty()) {
      return;
    }
    Set<Item> selectedItems = new TreeSet<>();
    for (int i = 0; i < selectedListValues.size(); i++) {
      selectedItems.addAll(((UnconnectedRouteInfo) selectedListValues.get(i)).itemList);
    }
    GuiBoardManager boardHandling = boardFrame.boardPanel.boardHandling;
    boardHandling.selectItems(selectedItems);
    boardHandling.zoomSelection();
  }

  /** Describes information of a connected set of unconnected traces and vias. */
  private class UnconnectedRouteInfo implements Comparable<UnconnectedRouteInfo> {

    private final Net net;
    private final Collection<Item> itemList;
    private final int idNo;
    private final Integer traceCount;
    private final Integer viaCount;

    public UnconnectedRouteInfo(Net p_net, Collection<Item> p_item_list) {
      this.net = p_net;
      this.itemList = p_item_list;
      ++maxUnconnectedRouteInfoIdNo;
      this.idNo = maxUnconnectedRouteInfoIdNo;
      int currTraceCount = 0;
      int currViaCount = 0;
      for (Item currItem : p_item_list) {
        if (currItem instanceof Trace) {
          ++currTraceCount;
        } else if (currItem instanceof Via) {
          ++currViaCount;
        }
      }
      this.traceCount = currTraceCount;
      this.viaCount = currViaCount;
    }

    @Override
    public String toString() {
      return tm.getText(
          "unconnected_route_row_message",
          this.net.name,
          String.valueOf(this.traceCount),
          String.valueOf(this.viaCount));
    }

    @Override
    public int compareTo(UnconnectedRouteInfo p_other) {
      int result = this.net.name.compareTo(p_other.net.name);
      if (result == 0) {
        result = this.idNo - p_other.idNo;
      }
      return result;
    }
  }
}
