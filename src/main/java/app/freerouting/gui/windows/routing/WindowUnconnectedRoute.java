package app.freerouting.gui.windows.routing;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.gui.board.BoardFrame;
import app.freerouting.gui.windows.board.CleanupWindows;
import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import app.freerouting.util.TextManager;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/** Displays route segments that are not connected to their net. */
public class WindowUnconnectedRoute extends CleanupWindows {

  private int maxUnconnectedRouteInfoId;

  /** Creates a new instance of WindowUnconnectedRoute. */
  public WindowUnconnectedRoute(BoardFrame boardFrame) {
    super(boardFrame);
    setLanguage(boardFrame.getLocale());

    this.tm = new TextManager(CleanupWindows.class, boardFrame.getLocale());

    this.setTitle(tm.getText("unconnected_route"));
    this.listEmptyMessage.setText(tm.getText("no_unconnected_route_found"));
  }

  @Override
  protected void fillList() {
    BasicBoard routingBoard = this.boardFrame.boardPanel.boardHandling.getRoutingBoard();

    Set<Item> handledItems = new TreeSet<>();

    SortedSet<UnconnectedRouteInfo> unconnectedRouteInfoSet = new TreeSet<>();

    Collection<Item> boardItems = routingBoard.getItems();
    for (Item currentItem : boardItems) {
      if (!(currentItem instanceof Trace || currentItem instanceof Via)) {
        continue;
      }
      if (handledItems.contains(currentItem)) {
        continue;
      }
      Collection<Item> currentConnectedSet = currentItem.getConnectedSet(-1);
      boolean terminalItemFound = false;
      for (Item currentConnnectedItem : currentConnectedSet) {
        handledItems.add(currentConnnectedItem);
        if (!(currentConnnectedItem instanceof Trace || currentConnnectedItem instanceof Via)) {
          terminalItemFound = true;
        }
      }
      if (!terminalItemFound) {
        // We have found unconnected route
        if (currentItem.netCount() == 1) {
          Net currentNet = routingBoard.rules.nets.get(currentItem.getNetNumber(0));
          if (currentNet != null) {
            UnconnectedRouteInfo currentUnconnectedRouteInfo =
                new UnconnectedRouteInfo(currentNet, currentConnectedSet);
            unconnectedRouteInfoSet.add(currentUnconnectedRouteInfo);
          }
        } else {
          FRLogger.warn("WindowUnconnectedRoute.fill_list: netCount 1 expected");
        }
      }
    }

    for (UnconnectedRouteInfo currentInfo : unconnectedRouteInfoSet) {
      this.addToList(currentInfo);
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
    private final int id;
    private final Integer traceCount;
    private final Integer viaCount;

    public UnconnectedRouteInfo(Net net, Collection<Item> itemList) {
      this.net = net;
      this.itemList = itemList;
      ++maxUnconnectedRouteInfoId;
      this.id = maxUnconnectedRouteInfoId;
      int currentTraceCount = 0;
      int currentViaCount = 0;
      for (Item currentItem : itemList) {
        if (currentItem instanceof Trace) {
          ++currentTraceCount;
        } else if (currentItem instanceof Via) {
          ++currentViaCount;
        }
      }
      this.traceCount = currentTraceCount;
      this.viaCount = currentViaCount;
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
    public int compareTo(UnconnectedRouteInfo other) {
      int result = this.net.name.compareTo(other.net.name);
      if (result == 0) {
        result = this.id - other.id;
      }
      return result;
    }
  }
}
