package app.freerouting.gui;

import app.freerouting.board.Item;
import app.freerouting.drc.AirLine;
import app.freerouting.gui.workspace.RatsNest;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Displays incomplete connections on the current board. */
public class WindowIncompletes extends WindowObjectListWithFilter {

  /** Creates a new instance of IncompletesWindow. */
  public WindowIncompletes(BoardFrame boardFrame) {
    super(boardFrame);
    setLanguage(boardFrame.get_locale());

    this.setTitle(tm.getText("incompletes"));
    this.listEmptyMessage.setText(tm.getText("routeCompleted"));
  }

  /** Fills the list with the board incompletes. */
  @Override
  protected void fillList() {
    RatsNest ratsnest = boardFrame.boardPanel.boardHandling.getRatsnest();
    AirLine[] sortedArr = ratsnest.getAirlines();

    Arrays.sort(sortedArr);
    for (int i = 0; i < sortedArr.length; i++) {
      this.addToList(new AirLineInfo(sortedArr[i]));
    }
    this.list.setVisibleRowCount(Math.min(sortedArr.length, DEFAULT_TABLE_SIZE));
  }

  @Override
  protected void selectInstances() {
    List<Object> selectedIncompletes = list.getSelectedValuesList();
    if (selectedIncompletes.isEmpty()) {
      return;
    }
    Set<Item> selectedItems = new TreeSet<>();
    for (int i = 0; i < selectedIncompletes.size(); i++) {
      AirLineInfo currInfo = (AirLineInfo) selectedIncompletes.get(i);
      AirLine currAirline = currInfo.airline;
      selectedItems.add(currAirline.fromItem);
      selectedItems.add(currAirline.toItem);
    }
    boardFrame.boardPanel.boardHandling.selectItems(selectedItems);
    boardFrame.boardPanel.boardHandling.zoomSelection();
  }
}
