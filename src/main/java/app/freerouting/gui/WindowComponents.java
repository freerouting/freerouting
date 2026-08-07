package app.freerouting.gui;

import app.freerouting.board.Component;
import app.freerouting.board.Components;
import app.freerouting.board.Item;
import app.freerouting.board.RoutingBoard;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Window displaying the components on the board. */
public class WindowComponents extends WindowObjectListWithFilter {

  /** Creates a new instance of ComponentsWindow */
  public WindowComponents(BoardFrame p_board_frame) {
    super(p_board_frame);
    setLanguage(p_board_frame.get_locale());

    this.setTitle(tm.getText("components"));
  }

  /** Fills the list with the board components. */
  @Override
  protected void fill_list() {
    Components components = this.boardFrame.boardPanel.boardHandling.get_routing_board().components;
    Component[] sortedArr = new Component[components.count()];
    for (int i = 0; i < sortedArr.length; i++) {
      sortedArr[i] = components.get(i + 1);
    }
    Arrays.sort(sortedArr);
    for (int i = 0; i < sortedArr.length; i++) {
      this.add_to_list(sortedArr[i]);
    }
    this.list.setVisibleRowCount(Math.min(components.count(), DEFAULT_TABLE_SIZE));
  }

  @Override
  protected void select_instances() {
    List<Object> selectedComponents = list.getSelectedValuesList();
    if (selectedComponents.isEmpty()) {
      return;
    }
    RoutingBoard routingBoard = boardFrame.boardPanel.boardHandling.get_routing_board();
    Set<Item> selectedItems = new TreeSet<>();
    Collection<Item> boardItems = routingBoard.get_items();
    for (Item currItem : boardItems) {
      if (currItem.get_component_no() > 0) {
        Component currComponent = routingBoard.components.get(currItem.get_component_no());
        boolean componentMatches = false;
        for (int i = 0; i < selectedComponents.size(); i++) {
          if (currComponent == selectedComponents.get(i)) {
            componentMatches = true;
            break;
          }
        }
        if (componentMatches) {
          selectedItems.add(currItem);
        }
      }
    }
    boardFrame.boardPanel.boardHandling.select_items(selectedItems);
    boardFrame.boardPanel.boardHandling.zoom_selection();
  }
}
