package app.freerouting.gui;

import app.freerouting.board.Item;
import app.freerouting.drc.AirLine;
import app.freerouting.interactive.RatsNest;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class WindowIncompletes extends WindowObjectListWithFilter {

  /** Creates a new instance of IncompletesWindow */
  public WindowIncompletes(BoardFrame p_board_frame) {
    super(p_board_frame);
    setLanguage(p_board_frame.get_locale());

    this.setTitle(tm.getText("incompletes"));
    this.listEmptyMessage.setText(tm.getText("routeCompleted"));
  }

  /** Fills the list with the board incompletes. */
  @Override
  protected void fill_list() {
    RatsNest ratsnest = boardFrame.boardPanel.boardHandling.get_ratsnest();
    AirLine[] sortedArr = ratsnest.get_airlines();

    Arrays.sort(sortedArr);
    for (int i = 0; i < sortedArr.length; i++) {
      this.add_to_list(new AirLineInfo(sortedArr[i]));
    }
    this.list.setVisibleRowCount(Math.min(sortedArr.length, DEFAULT_TABLE_SIZE));
  }

  @Override
  protected void select_instances() {
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
    boardFrame.boardPanel.boardHandling.select_items(selectedItems);
    boardFrame.boardPanel.boardHandling.zoom_selection();
  }
}
