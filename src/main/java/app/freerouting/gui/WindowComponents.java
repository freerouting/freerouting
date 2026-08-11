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

  /** Creates a new instance of ComponentsWindow. */
  public WindowComponents(BoardFrame boardFrame) {
    super(boardFrame);
    setLanguage(boardFrame.get_locale());

    this.setTitle(tm.getText("components"));
  }

  /** Fills the list with the board components. */
  @Override
  protected void fillList() {
    Components components = this.boardFrame.boardPanel.boardHandling.getRoutingBoard().components;
    Component[] sortedArr = new Component[components.count()];
    for (int i = 0; i < sortedArr.length; i++) {
      sortedArr[i] = components.get(i + 1);
    }
    Arrays.sort(sortedArr);
    for (int i = 0; i < sortedArr.length; i++) {
      this.addToList(sortedArr[i]);
    }
    this.list.setVisibleRowCount(Math.min(components.count(), DEFAULT_TABLE_SIZE));
  }

  @Override
  protected void selectInstances() {
    List<Object> selectedComponents = list.getSelectedValuesList();
    if (selectedComponents.isEmpty()) {
      return;
    }
    RoutingBoard routingBoard = boardFrame.boardPanel.boardHandling.getRoutingBoard();
    Set<Item> selectedItems = new TreeSet<>();
    Collection<Item> boardItems = routingBoard.getItems();
    for (Item currItem : boardItems) {
      if (currItem.getComponentNo() > 0) {
        Component currComponent = routingBoard.components.get(currItem.getComponentNo());
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
    boardFrame.boardPanel.boardHandling.selectItems(selectedItems);
    boardFrame.boardPanel.boardHandling.zoomSelection();
  }
}
